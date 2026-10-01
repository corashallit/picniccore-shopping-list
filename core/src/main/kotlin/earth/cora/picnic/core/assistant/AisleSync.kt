package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.catalog.BasketCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.applyAisleAssignments
import earth.cora.picnic.core.model.itemsNeedingAisle
import earth.cora.picnic.core.model.setAisleBaskets
import earth.cora.picnic.core.store.StateStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class AisleSyncReport(
    /** True if the model assigned the baskets (false: heuristic only, or baskets were not part of the run). */
    val basketsFromModel: Boolean,
    /** Items that got a real aisle from recategorize. */
    val recategorized: Int,
    /** Items that remain Unsorted (model said unsorted, or the call failed). */
    val leftUnsorted: Int,
    /** The first LlmError hit, if any (already handled; for a snackbar). */
    val error: LlmError?,
    /** Set when saving the results failed (StateStore.update threw); nothing was applied then. */
    val saveError: IOException? = null,
)

/**
 * Background aisle placement. Never throws LlmError (it is reported in [AisleSyncReport.error]);
 * items stay Unsorted offline. Run it in the app scope (not a ViewModel scope) so leaving a screen
 * doesn't cancel it. Calls are serialized: a call while one is running waits for it.
 */
class AisleSync(
    private val store: StateStore,
    private val assistant: GroceryAssistant,
    private val baskets: BasketCatalog,
) {
    private val mutex = Mutex()

    /**
     * After the aisle text changed (the caller already did store.update { setAislesText(text, baskets::guess) }):
     * 1. assignBasketsOrGuess(current aisles) -> store.update { setAisleBaskets(...) }.
     * 2. state.itemsNeedingAisle() -> assistant.recategorize(...) -> store.update { applyAisleAssignments(...) }.
     */
    suspend fun sync(): AisleSyncReport = mutex.withLock {
        val aisles = store.current().store.aisles()
        var error: LlmError? = null
        var basketsFromModel = false
        if (aisles.isNotEmpty()) {
            val assigned = try {
                assistant.assignBaskets(aisles).also { basketsFromModel = it.isNotEmpty() }
            } catch (e: LlmError) {
                error = e
                emptyMap()
            }
            val all: Map<String, BasketKind> = aisles.associate { it.id to (assigned[it.id] ?: baskets.guess(it)) }
            try {
                store.update { it.setAisleBaskets(all) }
            } catch (e: IOException) {
                return@withLock AisleSyncReport(basketsFromModel, 0, store.current().itemsNeedingAisle().size, error, e)
            }
        }
        val report = placeItems(onlyIds = null)
        report.copy(basketsFromModel = basketsFromModel, error = error ?: report.error)
    }

    /**
     * After typed / raw-lines items were committed with aisleId == null: recategorize just those
     * (ids whose item still needs an aisle; others are skipped) -> applyAisleAssignments. No baskets.
     */
    suspend fun sortItems(itemIds: Collection<String>): AisleSyncReport = mutex.withLock {
        if (itemIds.isEmpty()) return@withLock AisleSyncReport(false, 0, 0, null)
        placeItems(onlyIds = itemIds.toHashSet())
    }

    private suspend fun placeItems(onlyIds: Set<String>?): AisleSyncReport {
        val state = store.current()
        val needing = state.itemsNeedingAisle().filter { onlyIds == null || it.id in onlyIds }
        if (needing.isEmpty()) return AisleSyncReport(false, 0, 0, null)
        val aisles = state.store.aisles()
        val assignments = try {
            assistant.recategorize(needing, aisles, state.corrections)
        } catch (e: LlmError) {
            return AisleSyncReport(false, 0, needing.size, e)
        }
        // Only ids we asked about; anything the assistant skipped stays Unsorted.
        val applied = needing.associate { it.id to assignments[it.id] }
        return try {
            store.update { it.applyAisleAssignments(applied) }
            val placed = applied.values.count { it != null }
            AisleSyncReport(false, placed, needing.size - placed, null)
        } catch (e: IOException) {
            AisleSyncReport(false, 0, needing.size, null, e)
        }
    }
}

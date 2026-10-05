package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.catalog.BasketCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.applyAisleAssignments
import earth.cora.picnic.core.model.findItem
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
    /** Epoch millis for the trip reconcile at the end of applyAisleAssignments (§19.1). */
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /**
     * Items the model has answered for in this process (it placed them, or left them in Everything else): [sortPending]
     * does not ask about them again until the aisles change ([sync], [resortAll]) or the process restarts, so a
     * connection that comes and goes never sends the same names over and over. Guarded by [mutex].
     */
    private val settled = HashSet<String>()

    /**
     * After the aisle text changed (the caller already did store.update { setAislesText(text, baskets::guess) }):
     * 1. assignBasketsOrGuess(current aisles) -> store.update { setAisleBaskets(...) }.
     * 2. state.itemsNeedingAisle() -> assistant.recategorize(...) -> store.update { applyAisleAssignments(...) }.
     */
    suspend fun sync(): AisleSyncReport = mutex.withLock {
        settled.clear()
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
     * "Re-sort my list" (§6.7, AisleSyncTest.resortAll): re-files EVERY item not placed by hand, in every list and
     * whether or not it is checked (not only Everything else), with the fast model; learned corrections still win
     * (client-side, in recategorize). Items she placed by hand into an aisle that still exists, or deliberately into
     * Everything else, keep their place. The report counts the items that got a real aisle ([AisleSyncReport.recategorized])
     * and those left in Everything else ([AisleSyncReport.leftUnsorted]): "Sorted 6 items · 2 left in Everything else".
     * Baskets are not touched. Never throws LlmError (offline: nothing moves, nothing counts as sorted, and the error is
     * reported). The answer is applied to the state as it is then, not as it was when the request went out: an aisle
     * renamed or removed while the model was answering is not a target any more, and its items keep the aisle they have.
     */
    suspend fun resortAll(): AisleSyncReport = mutex.withLock {
        settled.clear()
        val state = store.current()
        val ids = state.store.aisles().mapTo(HashSet()) { it.id }
        val candidates = state.lists.flatMap { it.items }
            .filterNot { it.manuallyPlaced && (it.aisleId == null || it.aisleId in ids) }
            .distinctBy { it.id }
            .map { NamedItem(id = it.id, name = it.name) }
        if (candidates.isEmpty()) return@withLock AisleSyncReport(false, 0, 0, null)
        val assignments = try {
            assistant.recategorize(candidates, state.store.aisles(), state.corrections)
        } catch (e: LlmError) {
            // Nothing moved, so nothing was sorted: the screen says what went wrong instead of "Sorted n items".
            val left = candidates.count { c -> state.findItem(c.id)?.aisleId !in ids }
            return@withLock AisleSyncReport(false, 0, left, e)
        }
        // An item the model cannot place keeps the aisle it has (a re-sort never empties a good aisle into Everything else).
        val after = try {
            store.update { s -> s.applyAisleAssignments(current(s, candidates.map { it.id }, assignments), clock()) }
        } catch (e: IOException) {
            return@withLock AisleSyncReport(false, 0, candidates.size, null, e)
        }
        val now = after.store.aisles().mapTo(HashSet()) { it.id }
        val placed = candidates.count { c -> after.findItem(c.id)?.aisleId in now }
        AisleSyncReport(false, placed, candidates.size - placed, null)
    }

    /**
     * After typed / raw-lines items were committed with aisleId == null: recategorize just those
     * (ids whose item still needs an aisle; others are skipped) -> applyAisleAssignments. No baskets.
     */
    suspend fun sortItems(itemIds: Collection<String>): AisleSyncReport = mutex.withLock {
        if (itemIds.isEmpty()) return@withLock AisleSyncReport(false, 0, 0, null)
        placeItems(onlyIds = itemIds.toHashSet())
    }

    /**
     * Catch-up (the connection came back, or a cold start with a key; docs/DESIGN_STICKER.md §0.4, 5 October): every item
     * in every list that still needs an aisle ([itemsNeedingAisle]: no aisle, or one that is gone; never one she put in
     * Everything else by hand, checked or not) is recategorized exactly as [sortItems] does, so learned corrections and
     * her manual placements win. No baskets. Items the model already answered for in this process are not asked about
     * again (until the aisles change or the process restarts). Never throws LlmError (offline: nothing moves, the error
     * is reported, and the items are asked about at the next catch-up).
     */
    suspend fun sortPending(): AisleSyncReport = mutex.withLock {
        val waiting = store.current().itemsNeedingAisle().map { it.id }.filterNot { it in settled }
        if (waiting.isEmpty()) return@withLock AisleSyncReport(false, 0, 0, null)
        placeItems(onlyIds = waiting.toHashSet())
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
        needing.mapTo(settled) { it.id }
        // Only ids we asked about; anything the assistant skipped stays Unsorted.
        var placed = 0
        return try {
            store.update { s ->
                val applied = current(s, needing.map { it.id }, assignments)
                placed = applied.values.count { it != null }
                s.applyAisleAssignments(applied, clock())
            }
            AisleSyncReport(false, placed, needing.size - placed, null)
        } catch (e: IOException) {
            AisleSyncReport(false, 0, needing.size, null, e)
        }
    }

    /**
     * The model's [assignments] for [ids] as they can be written into [state] (the state now, not when the request went
     * out): a target that is no longer one of the store's aisles (renamed or removed meanwhile) is dropped, so the item
     * keeps the aisle it has; an item the model could not place keeps its aisle too; an item with no aisle it could keep
     * gets null (Everything else).
     */
    private fun current(state: AppState, ids: List<String>, assignments: Map<String, String?>): Map<String, String?> {
        val aisles = state.store.aisles().mapTo(HashSet()) { it.id }
        val has = state.lists.flatMap { it.items }.associate { it.id to it.aisleId }
        return ids.mapNotNull { id ->
            val target = assignments[id]
            when {
                target != null && target in aisles -> id to target
                has[id] !in aisles -> id to null
                else -> null
            }
        }.toMap()
    }
}

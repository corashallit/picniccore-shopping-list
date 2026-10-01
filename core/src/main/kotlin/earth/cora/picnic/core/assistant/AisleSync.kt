package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.catalog.BasketCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.store.StateStore

data class AisleSyncReport(
    /** True if the model assigned the baskets (false: heuristic only). */
    val basketsFromModel: Boolean,
    /** Items that got a real aisle from recategorize. */
    val recategorized: Int,
    /** Items that remain Unsorted (model said unsorted, or the call failed). */
    val leftUnsorted: Int,
    /** The first LlmError hit, if any (already handled; for a snackbar). */
    val error: LlmError?,
)

/**
 * Runs after the aisle text changed (the caller already did store.update { setAislesText(text, baskets::guess) }).
 * CONTRACT:
 * 1. assignBasketsOrGuess(current aisles) -> store.update { setAisleBaskets(...) }.
 * 2. state.itemsNeedingAisle() -> assistant.recategorize(...) -> store.update { applyAisleAssignments(...) }.
 * Never throws LlmError (it is reported in [AisleSyncReport.error]); items stay Unsorted offline.
 * Run it in the app scope (not a ViewModel scope) so leaving the Aisles screen doesn't cancel it.
 * Concurrent calls are conflated: a call while one is running waits and then runs once more.
 */
class AisleSync(
    private val store: StateStore,
    private val assistant: GroceryAssistant,
    private val baskets: BasketCatalog,
) {
    suspend fun sync(): AisleSyncReport = TODO("core: AisleSync.sync")
}

package earth.cora.picnic.core.model

/*
 * The trip completion invariant (amendment A5, docs/DESIGN_STICKER.md §7.1). Pure. Owner: WP3.
 * PHASE-0 STUB: the signature is the contract; WP3 fills in the body (TripOpsTest).
 */
object TripRules {
    /**
     * Runs at the end of EVERY StateOp that touches items (toggleChecked, addItems, removeItem,
     * restoreItem, moveItemToAisle, applyAisleAssignments, setAislesText / commitAislesText,
     * renameAisle, newTrip), so completion is a property of the state, not of the gesture that finished
     * the list. CONTRACT:
     * - `trip.completedAt` is non-null exactly when the list has >= 1 item and every item is checked
     *   (un-checking or adding clears it; re-completing sets it again, to [now]).
     * - `trip = null` (closed without filing) when its stickers (checked items) drop to 0.
     * - `trip.shopMinutes` is computed at the FIRST completion (from the first check-off of the last
     *   session, sessions breaking at gaps over 45 min, at least 1) and then frozen.
     * - Returns the same instance when nothing changed.
     *
     * Phase 0: trips never open yet (toggleChecked does not mint one until WP3), so returning [list]
     * unchanged already satisfies the invariant.
     */
    fun reconcile(list: GroceryList, now: Long): GroceryList = list

    /** A trip whose last activity is older than this is idle (§7.1). */
    const val IDLE_MILLIS: Long = 12L * 60 * 60 * 1000

    /**
     * Cora's answer to Q9 (§0.4): no question. The first check-off more than [IDLE_MILLIS] after the trip's
     * last activity starts a new book on its own: the caller files the old book (newTrip(CLEAR_GOTTEN)),
     * then checks the item onto the fresh trip, and offers Undo (TripRulesTest.autoNewBookAfterIdle).
     * False without a trip.
     */
    fun startsNewBook(list: GroceryList, now: Long): Boolean {
        val trip = list.trip ?: return false
        return now - trip.lastActivityAt > IDLE_MILLIS
    }
}

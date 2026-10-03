package earth.cora.picnic.core.model

/*
 * The trip completion invariant (amendment A5, docs/DESIGN_STICKER.md §7.1). Pure. Owner: WP3 (TripOpsTest).
 */
object TripRules {
    /**
     * Runs at the end of EVERY StateOp that touches items (toggleChecked, addItems, removeItem,
     * restoreItem, moveItemToAisle, applyAisleAssignments, setAislesText / commitAislesText,
     * renameAisle, newTrip), so completion is a property of the state, not of the gesture that finished
     * the list. CONTRACT:
     * - Items: a checked item has a checkedAt (a missing one becomes [now]); an unchecked one has neither
     *   checkedAt nor bookSlot; book spots are valid and unique (BookSlots.compact).
     * - A trip exists exactly when the list has a sticker (a checked item). It closes WITHOUT filing
     *   (`trip = null`) when its stickers drop to 0; a list that has stickers but no trip (a restored or
     *   revived sticker after the trip closed) gets one: startedAt = its earliest check-off, id
     *   "t-<listId>-<startedAt>", [storeName], lastActivityAt = its latest check-off.
     * - `trip.completedAt` is non-null exactly when the list has >= 1 item and every item is checked
     *   (un-checking or adding clears it; re-completing sets it again, to [now]).
     * - `trip.shopMinutes` is computed at the FIRST completion (TripStats.shopMinutes) and then frozen. A trip
     *   whose finale was already shown without minutes (a migrated trip that was complete on upgrade) keeps null.
     * - lastActivityAt is the caller's (check-off and un-check only); reconcile never moves it.
     * - Returns the same instance when nothing changed.
     */
    fun reconcile(list: GroceryList, now: Long, storeName: String = ""): GroceryList {
        val items = BookSlots.compact(repairItems(list.items, now))
        val stickers = items.count { it.checked }
        var trip = list.trip
        if (stickers == 0) {
            trip = null
        } else if (trip == null) {
            val times = items.mapNotNull { if (it.checked) it.checkedAt else null }
            val startedAt = times.min()
            trip = Trip(id = tripId(list.id, startedAt), startedAt = startedAt, storeName = storeName, lastActivityAt = times.max())
        }
        if (trip != null) {
            val complete = items.isNotEmpty() && items.all { it.checked }
            trip = when {
                complete && trip.completedAt == null -> {
                    val minutes = trip.shopMinutes ?: if (trip.finaleShownAt == null) TripStats.shopMinutes(items, now) else null
                    trip.copy(completedAt = now, shopMinutes = minutes)
                }
                !complete && trip.completedAt != null -> trip.copy(completedAt = null)
                else -> trip
            }
        }
        if (items === list.items && trip == list.trip) return list
        return list.copy(items = items, trip = trip)
    }

    /** The deterministic trip id (§7.1): the pure toggleChecked mints it, and filing is idempotent by it. */
    fun tripId(listId: String, startedAt: Long): String = "t-$listId-$startedAt"

    /** A trip whose last activity is older than this is idle (§7.1). */
    const val IDLE_MILLIS: Long = 12L * 60 * 60 * 1000

    /**
     * Cora's answer to Q9 (§0.4): no question. The first check-off more than [IDLE_MILLIS] after the trip's
     * last activity starts a new book on its own (toggleCheckedStartingNewBook files the old book, clears what
     * was got and checks the item onto the fresh trip; undoNewBook takes it back). False without a trip.
     */
    fun startsNewBook(list: GroceryList, now: Long): Boolean {
        val trip = list.trip ?: return false
        return now - trip.lastActivityAt > IDLE_MILLIS
    }

    /** The open trip is complete (the LCD hold prints the receipt, D21). */
    fun isComplete(list: GroceryList): Boolean = list.trip?.completedAt != null

    /**
     * The finale is still owed (§7.1): the trip is complete and its finale has not been shown. The app plays it
     * at once when the list opens within [FINALE_GRACE_MILLIS] of completion; later it only marks it shown and
     * presses the book's PERFECT in place ([finaleIsStale]).
     */
    fun finaleOwed(list: GroceryList): Boolean {
        val trip = list.trip ?: return false
        return trip.completedAt != null && trip.finaleShownAt == null
    }

    /** An owed finale is too old to play when the list opens (the app died after completion, §7.1). */
    fun finaleIsStale(list: GroceryList, now: Long): Boolean {
        val completedAt = list.trip?.completedAt ?: return false
        return finaleOwed(list) && now - completedAt > FINALE_GRACE_MILLIS
    }

    /** §7.1: an owed finale still plays when the list opens within 30 min of completion. */
    const val FINALE_GRACE_MILLIS: Long = 30L * 60 * 1000

    /**
     * The "new" star's anchor (§3.3, §7.1): the open trip's startedAt, else GroceryList.lastClosedAt; null for a
     * list that has never had a trip (it shows no new stars).
     */
    fun newAnchor(list: GroceryList): Long? = list.trip?.startedAt ?: list.lastClosedAt

    /** The item wears the "new" star: added after the list's [newAnchor]. */
    fun isNew(list: GroceryList, item: GroceryItem): Boolean {
        val anchor = newAnchor(list) ?: return false
        return item.addedAt > anchor
    }

    private fun repairItems(items: List<GroceryItem>, now: Long): List<GroceryItem> {
        var changed = false
        val repaired = items.map { item ->
            when {
                item.checked && item.checkedAt == null -> item.copy(checkedAt = now).also { changed = true }
                !item.checked && (item.checkedAt != null || item.bookSlot != null) ->
                    item.copy(checkedAt = null, bookSlot = null).also { changed = true }
                else -> item
            }
        }
        return if (changed) repaired else items
    }
}

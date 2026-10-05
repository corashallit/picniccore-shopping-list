package earth.cora.picnic.core.model

import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.store.BookSticker
import earth.cora.picnic.core.store.TripBook

/*
 * Pure state transitions. CONTRACT (docs/ARCHITECTURE.md §4.3): the app changes AppState ONLY through
 * these functions, inside StateStore.update { ... }. They never do I/O, never throw for unknown ids
 * (they return the state unchanged), and return the SAME instance when nothing changed (so the store
 * can skip the write).
 *
 * Trips (amendment A5, docs/DESIGN_STICKER.md §7.1, §19.1): every op that touches items takes `now`
 * and ends with TripRules.reconcile, so trip completion is an invariant of the state: a trip exists
 * exactly while its list has a sticker, and it is complete exactly while every item is checked.
 * Owner: WP3 (TripOpsTest, StateOpsTest, BookSlotsTest, RenameAisleTest, MoveAisleTest, AislesTextCommitTest).
 */

object StateOps {
    /** Cap for AppState.corrections (oldest dropped first). */
    const val MAX_CORRECTIONS: Int = 200

    /** Cap for AppState.iconOverrides (oldest dropped first). */
    const val MAX_ICON_OVERRIDES: Int = 200

    /** Corrections sent to the model per call (newest first, only those whose aisle still exists). */
    const val MAX_PROMPT_CORRECTIONS: Int = 60

    /** A fresh install: one empty list titled "Groceries", default store profile. */
    fun initialState(listId: String, now: Long): AppState = AppState(
        lists = listOf(GroceryList(id = listId, title = DEFAULT_LIST_TITLE, createdAt = now)),
        currentListId = listId,
    )
}

/** The current list (falls back to the first list if currentListId is stale). */
fun AppState.currentList(): GroceryList = lists.firstOrNull { it.id == currentListId } ?: lists.first()

fun AppState.list(listId: String): GroceryList? = lists.firstOrNull { it.id == listId }

/** Finds an item in any list. */
fun AppState.findItem(itemId: String): GroceryItem? = lists.firstNotNullOfOrNull { l -> l.items.firstOrNull { it.id == itemId } }

/** Applies [transform] to one list. Unknown id, or a transform that returns the same instance -> this. */
fun AppState.updateList(listId: String, transform: (GroceryList) -> GroceryList): AppState {
    val index = lists.indexOfFirst { it.id == listId }
    if (index < 0) return this
    val before = lists[index]
    val after = transform(before)
    if (after === before) return this
    return copy(lists = lists.toMutableList().also { it[index] = after })
}

/** Applies [transform] to every item of every list; keeps instances when nothing changed. */
private fun AppState.mapAllItems(transform: (GroceryItem) -> GroceryItem): AppState {
    var changed = false
    val newLists = lists.map { list ->
        var listChanged = false
        val items = list.items.map { item ->
            val next = transform(item)
            if (next !== item) listChanged = true
            next
        }
        if (listChanged) {
            changed = true
            list.copy(items = items)
        } else {
            list
        }
    }
    return if (changed) copy(lists = newLists) else this
}

private fun GroceryList.updateItem(itemId: String, transform: (GroceryItem) -> GroceryItem): GroceryList {
    val index = items.indexOfFirst { it.id == itemId }
    if (index < 0) return this
    val before = items[index]
    val after = transform(before)
    if (after === before || after == before) return this
    return copy(items = items.toMutableList().also { it[index] = after })
}

/** Applies [transform] to one list, then reconciles its trip (TripRules) when the transform changed it. */
private fun AppState.updateListReconciled(listId: String, now: Long, transform: (GroceryList) -> GroceryList): AppState =
    updateList(listId) { list ->
        val after = transform(list)
        if (after === list) list else TripRules.reconcile(after, now, store.name)
    }

/** Reconciles every list's trip (after an op that may touch items on any list). Keeps instances when nothing changed. */
private fun AppState.reconcileAll(now: Long): AppState {
    var changed = false
    val reconciled = lists.map { list -> TripRules.reconcile(list, now, store.name).also { if (it !== list) changed = true } }
    return if (changed) copy(lists = reconciled) else this
}

/** Applies [transform] to the list's open trip. No list, no trip, or no change -> this. */
private fun AppState.updateTrip(listId: String, transform: (Trip) -> Trip): AppState = updateList(listId) { list ->
    val trip = list.trip ?: return@updateList list
    val after = transform(trip)
    if (after == trip) list else list.copy(trip = after)
}

/**
 * What [addItems] did with each incoming item, as item ids: [added] = the new rows; [alreadyThere] = the
 * existing unchecked rows an incoming name matched (their stickers pop, "Milk is already on your list");
 * [revived] = existing checked rows an incoming name un-checked ("Milk is back on your list", Q8).
 */
data class AddOutcome(val added: List<String>, val alreadyThere: List<String>, val revived: List<String>) {
    companion object {
        val NONE: AddOutcome = AddOutcome(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Adds [items] to the list (§6.2 rules 4 and 7, Q8). Every incoming name is matched by normalizeName against
 * EVERY item already on the list:
 * - an UNCHECKED match drops the incoming item ([AddOutcome.alreadyThere]: its sticker pops, "Milk is already
 *   on your list");
 * - otherwise a CHECKED match is un-checked instead of adding a second row ([AddOutcome.revived]: "Milk is back
 *   on your list"; its twin peels out of the book and the trip's lastActivityAt moves to [now]); quantities are
 *   never rewritten. The add flows go through [addItemsStartingNewBook], which files a finished shop's book first,
 *   so a revive only ever peels a sticker out of the shop in progress;
 * - otherwise the item is appended ([AddOutcome.added]), unless an earlier item of [items] had the same name, its
 *   name is blank, or its id is already used anywhere.
 * Aisle ids are kept as given (callers already applied corrections; unknown ids render as Unsorted). Ends with
 * TripRules.reconcile: adding re-opens a complete trip, and reviving the last sticker closes the trip.
 */
fun AppState.addItems(listId: String, items: List<GroceryItem>, now: Long): Pair<AppState, AddOutcome> {
    val list = list(listId) ?: return this to AddOutcome.NONE
    if (items.isEmpty()) return this to AddOutcome.NONE
    val allIds = lists.flatMapTo(HashSet()) { l -> l.items.map { it.id } }
    val uncheckedByName = HashMap<String, String>()
    val checkedByName = HashMap<String, String>()
    for (item in list.items) {
        val key = normalizeName(item.name)
        if (item.checked) checkedByName.putIfAbsent(key, item.id) else uncheckedByName.putIfAbsent(key, item.id)
    }
    val seen = HashSet<String>()
    val alreadyThere = LinkedHashSet<String>()
    val revived = LinkedHashSet<String>()
    val toAdd = mutableListOf<GroceryItem>()
    for (item in items) {
        val key = normalizeName(item.name)
        if (key.isEmpty()) continue
        val unchecked = uncheckedByName[key]
        if (unchecked != null) {
            alreadyThere += unchecked
            continue
        }
        val checked = checkedByName[key]
        if (checked != null) {
            revived += checked
            continue
        }
        if (seen.add(key) && allIds.add(item.id)) toAdd += item
    }
    val outcome = AddOutcome(added = toAdd.map { it.id }, alreadyThere = alreadyThere.toList(), revived = revived.toList())
    if (toAdd.isEmpty() && revived.isEmpty()) return this to outcome
    val next = updateListReconciled(listId, now) { l ->
        val kept = if (revived.isEmpty()) {
            l.items
        } else {
            l.items.map { if (it.id in revived) it.copy(checked = false, checkedAt = null, bookSlot = null) else it }
        }
        val trip = if (revived.isEmpty()) l.trip else l.trip?.copy(lastActivityAt = now)
        l.copy(items = kept + toAdd, trip = trip)
    }
    return next to outcome
}

/**
 * Check or un-check one item: ONE atomic transition (§19.1, TripOpsTest.atomicToggle).
 * - Checking sets checkedAt = [now] and takes the lowest free book spot (BookSlots.assign); on a list without
 *   a trip it opens one: Trip(id = "t-<listId>-<now>", startedAt = now, storeName = store.name, lastActivityAt = now).
 * - Un-checking clears checkedAt and bookSlot (the spot becomes a hole the next check fills).
 * - Either way the trip's lastActivityAt becomes [now]; then TripRules.reconcile (completion; un-checking the
 *   last sticker closes the trip without filing).
 * This is the plain toggle. A check-off tap goes through [toggleCheckedStartingNewBook], which files an idle
 * trip's book first (§0.4 Q9); nothing ever asks.
 */
fun AppState.toggleChecked(listId: String, itemId: String, now: Long): AppState = updateList(listId) { list ->
    val item = list.items.firstOrNull { it.id == itemId } ?: return@updateList list
    val toggled = if (item.checked) {
        item.copy(checked = false, checkedAt = null, bookSlot = null)
    } else {
        item.copy(checked = true, checkedAt = now, bookSlot = BookSlots.assign(list.items, itemId))
    }
    val items = list.items.map { if (it.id == itemId) toggled else it }
    val trip = list.trip?.copy(lastActivityAt = now)
        ?: if (toggled.checked) Trip(id = TripRules.tripId(listId, now), startedAt = now, storeName = store.name, lastActivityAt = now) else null
    TripRules.reconcile(list.copy(items = items, trip = trip), now, store.name)
}

/**
 * A check-off or an add that started a new book on its own (§0.4 Q9): file [book] in the album, show the toast, keep
 * [undo].
 */
data class AutoNewBook(val book: TripBook, val undo: NewBookUndo)

/**
 * What [undoNewBook] needs to take an automatic new book back: the old [trip], the items "Clear what I got"
 * removed (with their indices) and the list's previous [lastClosedAt] and [openAisle]. [itemId] is the tapped item
 * of a check-off, or the item an un-check or a tear-off took out of a finished shop's book ([tookSticker]); null when
 * an add started the book (addItemsStartingNewBook).
 */
data class NewBookUndo(
    val listId: String,
    val itemId: String?,
    val trip: Trip,
    val cleared: List<IndexedValue<GroceryItem>>,
    val lastClosedAt: Long?,
    val openAisle: String?,
) {
    /** The gesture took [itemId]'s sticker out of a finished shop's book (an un-check or a tear-off, §7.1). */
    val tookSticker: Boolean get() = itemId != null && cleared.any { it.value.id == itemId }
}

/**
 * The automatic new book [auto] still stands (§0.4 Q9): its list still carries the fresh book it started (the tapped
 * item's trip, or no trip yet after an add or after that item was un-checked) and nothing was filed since. Its Undo
 * applies only then: after a New trip, a deleted list or a later book, the old book stays in the album and the album's
 * counters keep the newer filing (TripFiling.undoNewBook).
 */
fun AppState.newBookStands(auto: AutoNewBook): Boolean {
    val list = list(auto.undo.listId) ?: return false
    val filedAt = auto.book.closedAt
    if (list.lastClosedAt != filedAt) return false
    val trip = list.trip ?: return true
    return trip.id == TripRules.tripId(list.id, filedAt)
}

/**
 * Files [list]'s book as it stands and clears what was got (newTrip(CLEAR_GOTTEN)), then applies [then] to the fresh
 * list. The result carries the filed book and the [NewBookUndo] (with [itemId], the gesture's item, if any). Null when
 * the list has no book to file.
 */
private fun AppState.fileBookFirst(list: GroceryList, itemId: String?, now: Long, then: (AppState) -> AppState): Pair<AppState, AutoNewBook>? {
    val oldTrip = list.trip ?: return null
    val cleared = list.items.withIndex().filter { it.value.checked }
    val (fresh, book) = newTrip(list.id, NewTripMode.CLEAR_GOTTEN, now)
    if (book == null) return null
    return then(fresh) to AutoNewBook(book, NewBookUndo(list.id, itemId, oldTrip, cleared, list.lastClosedAt, list.openAisle))
}

/**
 * The check-off tap (§0.4 Q9, TripRulesTest.autoNewBookAfterIdle). When it CHECKS an item on a trip whose last
 * activity is more than 12 h old (TripRules.startsNewBook), the old book is filed and what was got last time is
 * cleared (newTrip(CLEAR_GOTTEN)), and the item is checked onto a fresh trip: the result carries the filed book
 * (write it to the album BEFORE this state, §7.2; TripFiling.toggle does both) and the [NewBookUndo] for the
 * toast's Undo. When that check-off also completes the fresh trip (its only item), the fresh trip's finale is marked
 * shown at once: no finale, no bonus (TripRulesTest.autoNewBookSameTapCompletion).
 * When it UN-CHECKS an item of a trip whose shop is over (TripRules.shopIsOver: idle, or more than 30 min after the
 * finale; she needs it again), the book is filed as it stands (the tapped sticker still in it, so a finished shop
 * keeps its PERFECT), what was got is cleared, and the tapped item comes back unchecked on the fresh list at its place
 * among the rows left (TripRulesTest.uncheckAfterTheShopFilesTheBook). Every other tap is [toggleChecked] with no
 * [AutoNewBook].
 */
fun AppState.toggleCheckedStartingNewBook(listId: String, itemId: String, now: Long): Pair<AppState, AutoNewBook?> {
    val list = list(listId) ?: return this to null
    val item = list.items.firstOrNull { it.id == itemId } ?: return this to null
    if (item.checked) {
        if (!TripRules.shopIsOver(list, now)) return toggleChecked(listId, itemId, now) to null
        // Its place among the rows that stay (the unchecked ones before it).
        val at = list.items.takeWhile { it.id != itemId }.count { !it.checked }
        val again = item.copy(checked = false, checkedAt = null, bookSlot = null)
        return fileBookFirst(list, itemId, now) { fresh -> fresh.restoreItem(listId, again, at, now) }
            ?: (toggleChecked(listId, itemId, now) to null)
    }
    if (list.trip == null || !TripRules.startsNewBook(list, now)) return toggleChecked(listId, itemId, now) to null
    return fileBookFirst(list, itemId, now) { fresh ->
        val toggled = fresh.toggleChecked(listId, itemId, now)
        // The same tap that started the book finished it (a one-thing top-up the evening after a shop): its finale counts
        // as shown, so no celebration plays, no bonus sticker is spent and the new book's Undo note stays readable; the
        // aisle's own in-place chain still plays (§0.4 Q9, §7.1).
        if (toggled.list(listId)?.trip?.completedAt != null) toggled.markFinaleShown(listId, now) else toggled
    } ?: (toggleChecked(listId, itemId, now) to null)
}

/**
 * The add (§0.4 Q9, the add-side twin of [toggleCheckedStartingNewBook]; TripRulesTest.autoNewBookOnAdd). When an add
 * would change a list whose trip's last activity is more than 12 h old (TripRules.startsNewBook), the old book is
 * filed exactly as it stands (TripRules.bookItems: a finished shop keeps its PERFECT) and what was got is cleared
 * (newTrip(CLEAR_GOTTEN)); then [items] are added to the fresh list, so a name that was got last time is a new row
 * rather than a sticker peeled out of last week's book. The book opens again at the next check-off. The result
 * carries the filed book (write it to the album BEFORE this state, §7.2; TripFiling.addItems does both) and the
 * [NewBookUndo] for the toast's Undo. Within 12 h the same happens when the add would revive a name that was got on
 * a shop that is over (TripRules.shopIsOver: more than 30 min after its finale), so the name becomes a fresh row and the
 * finished book keeps its PERFECT. An add that changes nothing (only rows already on the list), or any other add within
 * 12 h, is [addItems] with no [AutoNewBook].
 */
fun AppState.addItemsStartingNewBook(listId: String, items: List<GroceryItem>, now: Long): Triple<AppState, AddOutcome, AutoNewBook?> {
    val (plain, outcome) = addItems(listId, items, now)
    val list = list(listId) ?: return Triple(plain, outcome, null)
    if (plain === this || list.trip == null) return Triple(plain, outcome, null)
    // A name she got on a finished shop (more than 30 min after its finale) is wanted again: the shop's book is filed
    // first, so the revive never peels a sticker out of it (§7.1).
    val files = TripRules.startsNewBook(list, now) || (outcome.revived.isNotEmpty() && TripRules.shopIsOver(list, now))
    if (!files) return Triple(plain, outcome, null)
    var freshOutcome = outcome
    val (added, auto) = fileBookFirst(list, null, now) { fresh ->
        fresh.addItems(listId, items, now).also { freshOutcome = it.second }.first
    } ?: return Triple(plain, outcome, null)
    return Triple(added, freshOutcome, auto)
}

/**
 * Undo of an automatic new book (§0.4 Q9, TripRulesTest.autoNewBookUndo): the old trip comes back with the
 * items that were cleared (at their old indices and book spots), and everything checked since (the tapped item
 * first among them) is checked into the OLD book instead, taking its lowest free spots in check-off order. Items
 * added meanwhile stay. A check-off's undo is activity on the trip (lastActivityAt = [now]); an add's undo
 * ([NewBookUndo.itemId] null) checks nothing, so the trip keeps its old lastActivityAt and the items added stay out
 * of a finished book (TripRules.bookItems). The undo of an un-check or a tear-off on a finished shop
 * ([NewBookUndo.tookSticker]) drops the item's fresh unchecked copy, so the item comes back checked in its old spot,
 * and the trip keeps its old lastActivityAt too. The caller also takes the book back out of the album (Album.unfile)
 * after this state is saved, and only while [newBookStands] (TripFiling.undoNewBook). Unknown list -> this.
 */
fun AppState.undoNewBook(undo: NewBookUndo, now: Long): AppState {
    val oldStickers = undo.cleared.mapTo(HashSet()) { it.value.id }
    // An un-check's fresh unchecked copy gives way to the sticker it was.
    val dropped = undo.itemId.takeIf { undo.tookSticker }
    val present = lists.flatMapTo(HashSet()) { l -> l.items.mapNotNull { if (l.id == undo.listId && it.id == dropped) null else it.id } }
    return updateList(undo.listId) { list ->
        // Stickers of the fresh book give up their spots; they take the old book's lowest free ones (reconcile).
        val items = list.items
            .filterNot { it.id == dropped }
            .map { if (it.checked && it.id !in oldStickers) it.copy(bookSlot = null) else it }
            .toMutableList()
        for ((index, item) in undo.cleared.sortedBy { it.index }) {
            if (item.id !in present) items.add(index.coerceIn(0, items.size), item)
        }
        val checkedOne = undo.itemId != null && !undo.tookSticker
        val restored = list.copy(
            items = items,
            trip = if (checkedOne) undo.trip.copy(lastActivityAt = now) else undo.trip,
            lastClosedAt = undo.lastClosedAt,
            openAisle = undo.openAisle,
        )
        TripRules.reconcile(restored, now, store.name)
    }
}

/**
 * Removes one item. For undo, the caller remembers the item and its index first, then calls [restoreItem].
 * Ends with TripRules.reconcile: deleting the last unchecked item completes the trip, deleting the last sticker
 * closes it, and the book stays valid (a checked item's spot is freed; a spot past the new count moves into the
 * lowest hole, BookSlots.compact).
 */
fun AppState.removeItem(listId: String, itemId: String, now: Long): AppState = updateListReconciled(listId, now) { list ->
    if (list.items.none { it.id == itemId }) list else list.copy(items = list.items.filterNot { it.id == itemId })
}

/**
 * The tear-off (§8.6) on a finished shop (§7.1): tearing off a CHECKED row of a trip whose shop is over
 * (TripRules.shopIsOver) files the book as it stands first, so the shop keeps every sticker; "Clear what I got" then
 * takes the row off with the rest of what was got. The result carries the filed book and the [NewBookUndo] (its Undo
 * puts the book and the row back). Every other tear-off is [removeItem] with no [AutoNewBook].
 */
fun AppState.removeItemStartingNewBook(listId: String, itemId: String, now: Long): Pair<AppState, AutoNewBook?> {
    val list = list(listId) ?: return this to null
    val item = list.items.firstOrNull { it.id == itemId } ?: return this to null
    if (!item.checked || !TripRules.shopIsOver(list, now)) return removeItem(listId, itemId, now) to null
    return fileBookFirst(list, itemId, now) { it } ?: (removeItem(listId, itemId, now) to null)
}

/**
 * Re-inserts a removed item at [index] (clamped). No-op if an item with that id exists anywhere. A checked item
 * reclaims its book spot when it is still free, else takes the lowest free one (BookSlotsTest.deleteRestore); on a
 * list whose trip closed meanwhile it re-opens one (TripRules.reconcile). Ends with TripRules.reconcile.
 */
fun AppState.restoreItem(listId: String, item: GroceryItem, index: Int, now: Long): AppState {
    if (findItem(item.id) != null) return this
    return updateListReconciled(listId, now) { list ->
        val at = index.coerceIn(0, list.items.size)
        val restored = if (item.checked) {
            item.copy(bookSlot = BookSlots.reclaimable(list.items, item.bookSlot, list.items.size + 1))
        } else {
            item.copy(checkedAt = null, bookSlot = null)
        }
        list.copy(items = list.items.toMutableList().also { it.add(at, restored) })
    }
}

/**
 * Edit sheet "save": name (trimmed, blank = keep), quantity and note (blank = null).
 * When normalizeName(name) changes, the icon is re-resolved (icon cascade step 1):
 * customIconId = null; icon/iconFit = [localIcon] for the new name, else DEFAULT_ICON + PENDING.
 * The caller then passes the updated item to IconRepository.submit (steps 2 and 3).
 * Aisle changes use [moveItemToAisle]; icon choices use [setItemIcon].
 */
fun AppState.editItemDetails(
    listId: String,
    itemId: String,
    name: String,
    quantity: String?,
    note: String?,
    catalog: IconCatalog,
): AppState {
    val newName = name.trim().replace(WHITESPACE, " ")
    val newQuantity = quantity?.trim()?.ifEmpty { null }
    val newNote = note?.trim()?.ifEmpty { null }
    return updateList(listId) { list ->
        list.updateItem(itemId) { item ->
            val finalName = newName.ifEmpty { item.name }
            val renamed = normalizeName(finalName) != normalizeName(item.name)
            if (!renamed) {
                item.copy(name = finalName, quantity = newQuantity, note = newNote)
            } else {
                val local = localIcon(finalName, catalog)
                item.copy(
                    name = finalName,
                    quantity = newQuantity,
                    note = newNote,
                    icon = local ?: IconCatalog.DEFAULT_ICON,
                    iconFit = if (local != null) IconFit.EXACT else IconFit.PENDING,
                    customIconId = null,
                )
            }
        }
    }
}

/**
 * The user moved an item. Sets aisleId (null = Unsorted) and manuallyPlaced = true, and when
 * [aisleId] != null records a Correction(normalizeName(item.name), aisleId, now) (replacing any
 * correction for that name; capped at StateOps.MAX_CORRECTIONS). Moving to Unsorted (null) removes
 * a stale correction for that name, so new items with that name are no longer auto-placed there.
 * An [aisleId] that is not a current aisle is a no-op. Ends with TripRules.reconcile.
 */
fun AppState.moveItemToAisle(listId: String, itemId: String, aisleId: String?, now: Long): AppState {
    if (aisleId != null && store.aisles().none { it.id == aisleId }) return this
    val item = list(listId)?.items?.firstOrNull { it.id == itemId } ?: return this
    val moved = updateListReconciled(listId, now) { list ->
        list.updateItem(itemId) { it.copy(aisleId = aisleId, manuallyPlaced = true) }
    }
    val key = normalizeName(item.name)
    if (key.isEmpty()) return moved
    val others = corrections.filterNot { it.name == key }
    val newCorrections = if (aisleId == null) {
        others
    } else {
        (others + Correction(key, aisleId, now)).takeLast(StateOps.MAX_CORRECTIONS)
    }
    return if (newCorrections == corrections) moved else moved.copy(corrections = newCorrections)
}

/**
 * What a move changed (Cora, 5 October; §6.3, §4.15), for its toast's Undo ([undoMove]): the item's aisle and "placed by
 * hand" before it, where it went ([movedTo], null = Everything else), the learned corrections before and after, and the
 * normalized name the correction is about.
 */
data class MoveUndo(
    val listId: String,
    val itemId: String,
    val aisleId: String?,
    val manuallyPlaced: Boolean,
    val movedTo: String?,
    val name: String,
    val correctionsBefore: List<Correction>,
    val correctionsAfter: List<Correction>,
)

/**
 * [moveItemToAisle], with what its Undo needs. The undo is null when nothing moved (an unknown item or aisle, or the
 * aisle it is already in).
 */
fun AppState.moveItemToAisleWithUndo(listId: String, itemId: String, aisleId: String?, now: Long): Pair<AppState, MoveUndo?> {
    val item = list(listId)?.items?.firstOrNull { it.id == itemId } ?: return this to null
    val moved = moveItemToAisle(listId, itemId, aisleId, now)
    if (moved === this || moved.findItem(itemId)?.aisleId == item.aisleId && moved.corrections == corrections) return moved to null
    return moved to MoveUndo(listId, itemId, item.aisleId, item.manuallyPlaced, aisleId, normalizeName(item.name), corrections, moved.corrections)
}

/**
 * Undo of a move (§4.15, MoveUndoTest): the item goes back to its old aisle (as placed by hand or not), unless it was
 * moved again since or is gone; the learned correction the move recorded goes and the one it replaced, if any, comes
 * back. When nothing else touched the corrections meanwhile they are put back exactly as they were (so a correction the
 * 200 cap pushed out returns too). An aisle that no longer exists sends the item to Everything else. Ends with
 * TripRules.reconcile.
 */
fun AppState.undoMove(undo: MoveUndo, now: Long): AppState {
    val ids = store.aisles().mapTo(HashSet()) { it.id }
    val item = list(undo.listId)?.items?.firstOrNull { it.id == undo.itemId }
    val restored = if (item != null && item.aisleId == undo.movedTo) {
        updateListReconciled(undo.listId, now) { list ->
            list.updateItem(undo.itemId) { it.copy(aisleId = undo.aisleId?.takeIf { id -> id in ids }, manuallyPlaced = undo.manuallyPlaced) }
        }
    } else {
        this
    }
    val newCorrections = when {
        corrections == undo.correctionsAfter -> undo.correctionsBefore
        else -> {
            val added = undo.correctionsAfter.lastOrNull { it.name == undo.name }
            val previous = undo.correctionsBefore.lastOrNull { it.name == undo.name }
            var out = if (added != null && added !in undo.correctionsBefore) corrections - added else corrections
            if (previous != null && out.none { it.name == undo.name }) {
                val at = out.indexOfFirst { it.at > previous.at }.takeIf { it >= 0 } ?: out.size
                out = out.toMutableList().apply { add(at, previous) }
            }
            out
        }
    }
    return if (newCorrections == restored.corrections) restored else restored.copy(corrections = newCorrections)
}

/**
 * The user picked a catalog icon in the edit sheet: icon = key, iconFit = EXACT, customIconId = null,
 * and an IconOverride(normalizeName(item.name), key, now) is recorded (replacing any override for that
 * name; capped at StateOps.MAX_ICON_OVERRIDES). Keys that are not in the catalog are a no-op. The
 * caller also calls IconRepository.forgetBespoke(name) so a cached bespoke icon is not re-attached
 * to future items. Pass the catalog-validated key; [catalog] defaults to the bundled catalog.
 */
fun AppState.setItemIcon(
    listId: String,
    itemId: String,
    iconKey: String,
    now: Long,
    catalog: IconCatalog = IconCatalog.default,
): AppState {
    if (iconKey !in catalog) return this
    val item = list(listId)?.items?.firstOrNull { it.id == itemId } ?: return this
    val updated = updateList(listId) { list ->
        list.updateItem(itemId) { it.copy(icon = iconKey, iconFit = IconFit.EXACT, customIconId = null) }
    }
    val key = normalizeName(item.name)
    if (key.isEmpty()) return updated
    val overrides = (iconOverrides.filterNot { it.name == key } + IconOverride(key, iconKey, now))
        .takeLast(StateOps.MAX_ICON_OVERRIDES)
    return updated.copy(iconOverrides = overrides)
}

/**
 * Removes all checked items from the list. The trip closes WITHOUT filing its book (TripRules: no stickers left),
 * so this is only the v1 screen's "Clear checked"; New trip ([newTrip]) files the book.
 */
@Deprecated("Replaced by newTrip(listId, mode, now) (§19.1, D1). The v1 list screen still calls it until WP5's New trip lands; then WP3 deletes it.")
fun AppState.clearChecked(listId: String): AppState = updateList(listId) { list ->
    if (list.items.none { it.checked }) {
        list
    } else {
        TripRules.reconcile(list.copy(items = list.items.filterNot { it.checked }), list.trip?.lastActivityAt ?: 0L, store.name)
    }
}

@Deprecated("Replaced by setOpenAisle(listId, sectionId) (§19.1, D2). The v1 list screen still calls it until WP5 lands one open aisle; then WP3 deletes it.")
fun AppState.setSectionCollapsed(listId: String, sectionId: String, collapsed: Boolean): AppState =
    updateList(listId) { list ->
        when {
            collapsed && sectionId !in list.collapsedAisles -> list.copy(collapsedAisles = list.collapsedAisles + sectionId)
            !collapsed && sectionId in list.collapsedAisles -> list.copy(collapsedAisles = list.collapsedAisles - sectionId)
            else -> list
        }
    }

/** D2: which aisle is open. [sectionId] null = automatic (the current stop), "" = all folded, else a section id. */
fun AppState.setOpenAisle(listId: String, sectionId: String?): AppState =
    updateList(listId) { list -> if (list.openAisle == sectionId) list else list.copy(openAisle = sectionId) }

/**
 * New trip (§6.5, §7.1, §7.8): files the list's book and starts afresh. Returns the filed book (AppState.tripBook),
 * which the caller writes to the album FIRST, then commits this state (§7.2 write order; TripFiling.newTrip does
 * both). No-op (this, null) without a trip: New trip is disabled then. Afterwards: checked items removed
 * ([NewTripMode.CLEAR_GOTTEN], the default) or un-checked ([NewTripMode.UNCHECK_ALL]), trip = null,
 * lastClosedAt = [now] (the "new" star's anchor), openAisle = null (automatic). A printed receipt is filed as saved.
 * Replaces clearChecked.
 */
fun AppState.newTrip(listId: String, mode: NewTripMode, now: Long): Pair<AppState, TripBook?> {
    val list = list(listId) ?: return this to null
    if (list.trip == null) return this to null
    val book = tripBook(listId, now)
    val items = when (mode) {
        NewTripMode.CLEAR_GOTTEN -> list.items.filterNot { it.checked }
        NewTripMode.UNCHECK_ALL -> list.items.map { if (it.checked) it.copy(checked = false, checkedAt = null, bookSlot = null) else it }
    }
    val next = updateList(listId) {
        TripRules.reconcile(it.copy(items = items, trip = null, lastClosedAt = now, openAisle = null), now, store.name)
    }
    return next to book
}

/** §11.2: the celebration's bonus sticker, saved to the trip when the scene starts (filed as TripBook.bonus). The first one stays. */
fun AppState.setTripBonus(listId: String, bonus: BookSticker): AppState =
    updateTrip(listId) { trip -> if (trip.bonus != null) trip else trip.copy(bonus = bonus) }

/** Egg 8 / §7.1: the finale played for this trip (first time only; a re-completion does not replay it). No-op without a trip. */
fun AppState.markFinaleShown(listId: String, now: Long): AppState =
    updateTrip(listId) { trip -> if (trip.finaleShownAt != null) trip else trip.copy(finaleShownAt = now) }

/** Egg 15: the pixel bee moves into the LCD for this trip. No-op without a trip (it only visits then, §12.2). */
fun AppState.hatchPet(listId: String): AppState = updateTrip(listId) { it.copy(pet = true) }

/** Egg 18: the receipt printed. The first print's receipt is kept (its stats are frozen, §13.2). No-op without a trip. */
fun AppState.printReceipt(listId: String, receipt: Receipt): AppState =
    updateTrip(listId) { trip -> if (trip.receipt != null) trip else trip.copy(receipt = receipt) }

/** §13.2: the printed receipt is tucked into the book (tap, or the auto-tuck). No-op without a printed receipt. */
fun AppState.saveReceipt(listId: String): AppState =
    updateTrip(listId) { trip -> if (trip.receipt == null) trip else trip.copy(receiptSaved = true) }

/** Egg 8: the nap cat trotted off; it does not return this trip. */
fun AppState.dismissNap(listId: String): AppState = updateTrip(listId) { it.copy(napDismissed = true) }

/** Eggs 6 and 13: the item's sticker rarity. */
fun AppState.setRarity(listId: String, itemId: String, rarity: Rarity): AppState =
    updateList(listId) { list -> list.updateItem(itemId) { it.copy(rarity = rarity) } }

/** D17: the favourite heart (Edit sheet) for a normalized name. */
fun AppState.setFavourite(normalizedName: String, on: Boolean): AppState = when {
    normalizedName.isEmpty() -> this
    on && normalizedName !in favourites -> copy(favourites = favourites + normalizedName)
    !on && normalizedName in favourites -> copy(favourites = favourites - normalizedName)
    else -> this
}

/** §4.26: the aisle editor's look overrides. Null (or an all-default style) removes them; ids that are not current aisles are a no-op. */
fun AppState.setAisleStyle(aisleId: String, style: AisleStyle?): AppState {
    if (store.aisles().none { it.id == aisleId }) return this
    val styles = if (style == null || style == AisleStyle()) store.aisleStyles - aisleId else store.aisleStyles + (aisleId to style)
    return if (styles == store.aisleStyles) this else copy(store = store.copy(aisleStyles = styles))
}

/** §4.26: the aisle editor's basket choice for one aisle. Ids that are not current aisles are a no-op. */
fun AppState.setAisleBasket(aisleId: String, kind: BasketKind): AppState {
    if (store.aisles().none { it.id == aisleId } || store.aisleBaskets[aisleId] == kind) return this
    return copy(store = store.copy(aisleBaskets = store.aisleBaskets + (aisleId to kind)))
}

/** Adds an empty list and makes it current. Blank title -> "Groceries". An existing [listId] is a no-op. */
fun AppState.newList(listId: String, title: String, now: Long): AppState {
    if (lists.any { it.id == listId }) return this
    val list = GroceryList(id = listId, title = title.trim().ifEmpty { DEFAULT_LIST_TITLE }, createdAt = now)
    return copy(lists = lists + list, currentListId = listId)
}

fun AppState.switchList(listId: String): AppState =
    if (listId == currentListId || lists.none { it.id == listId }) this else copy(currentListId = listId)

/** Renames a list (trimmed; blank = no-op). */
fun AppState.renameList(listId: String, title: String): AppState {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return this
    return updateList(listId) { if (it.title == trimmed) it else it.copy(title = trimmed) }
}

/**
 * Deletes a list. Never deletes the last one. If it was current, the neighbouring list becomes current.
 * A list with a trip (>= 1 sticker) has its book filed first: the returned book (a printed receipt filed as
 * saved) goes to the album before this state is committed (§6.4, §7.2; TripFiling.deleteList). Replaces the
 * v1 deleteList(listId).
 */
fun AppState.deleteList(listId: String, now: Long): Pair<AppState, TripBook?> {
    val index = lists.indexOfFirst { it.id == listId }
    if (index < 0 || lists.size <= 1) return this to null
    return deleteListNow(index, listId) to tripBook(listId, now)
}

private fun AppState.deleteListNow(index: Int, listId: String): AppState {
    val remaining = lists.filterIndexed { i, _ -> i != index }
    val current = if (currentListId == listId || remaining.none { it.id == currentListId }) {
        remaining[index.coerceAtMost(remaining.size - 1)].id
    } else {
        currentListId
    }
    return copy(lists = remaining, currentListId = current)
}

/** Store name shown on the Aisles screen (trimmed; blank = keep). */
fun AppState.setStoreName(name: String): AppState {
    val trimmed = name.trim()
    return if (trimmed.isEmpty() || trimmed == store.name) this else copy(store = store.copy(name = trimmed))
}

/**
 * Replaces the aisle text. Drops aisleBaskets entries for vanished aisle ids and fills missing
 * ones with [guessBasket] (so the UI shows baskets immediately). Item aisle ids are NOT touched
 * here: vanished ids render as Unsorted until AisleSync recategorizes them (the aisle editor commits through
 * [commitAislesText] instead, which keeps items with their aisles). Ends with TripRules.reconcile.
 */
fun AppState.setAislesText(text: String, guessBasket: (Aisle) -> BasketKind, now: Long): AppState {
    val aisles = Aisles.parse(text)
    val baskets = aisles.associate { aisle -> aisle.id to (store.aisleBaskets[aisle.id] ?: guessBasket(aisle)) }
    if (text == store.aislesText && baskets == store.aisleBaskets) return reconcileAll(now)
    return copy(store = store.copy(aislesText = text, aisleBaskets = baskets)).reconcileAll(now)
}

/**
 * The aisle editor's raw text on Done (§6.7, AislesTextCommitTest): a line-level diff against the current text
 * (AisleDiff).
 * - Lines in the longest common subsequence are unchanged and keep their aisles (their ids may still renumber,
 *   e.g. "bakery-2" -> "bakery" when an earlier duplicate goes; everything follows).
 * - A line that only moved (the same name elsewhere) keeps its aisle too.
 * - Between two unchanged lines, changed lines in place of removed ones are renames, paired in order: items,
 *   baskets, styles, corrections and openAisle follow (as [renameAisle]).
 * - A removed line's items go to Everything else (aisleId = null, no longer "placed by hand", so a re-sort may
 *   file them); its corrections, basket and style go, and an openAisle naming it becomes automatic.
 * Baskets of new aisles are filled with [guessBasket]. Ends with TripRules.reconcile.
 */
fun AppState.commitAislesText(text: String, guessBasket: (Aisle) -> BasketKind, now: Long): AppState {
    if (text == store.aislesText) return setAislesText(text, guessBasket, now)
    val newAisles = Aisles.parse(text)
    val idMap = AisleDiff.idMap(store.aisles(), newAisles)
    val remapped = copy(store = store.copy(aislesText = text)).remapAisleIds(idMap)
    val baskets = newAisles.associate { aisle -> aisle.id to (remapped.store.aisleBaskets[aisle.id] ?: guessBasket(aisle)) }
    return remapped.copy(store = remapped.store.copy(aisleBaskets = baskets)).reconcileAll(now)
}

/**
 * Renames one aisle (§6.7, RenameAisleTest): rewrites its line in aislesText (its bullet, indentation and "Also here"
 * note kept; a [newName] typed with "(also: …)" brings that note instead) and
 * remaps item.aisleId (all lists), aisleBaskets, aisleStyles, corrections and every list's openAisle from [oldId]
 * to the new slug id; when duplicate names renumber their "-2" ids, those follow too. The new id is
 * store.aisles()[position - 1].id. A blank name, an unknown id or the same name -> this. Ends with TripRules.reconcile.
 */
fun AppState.renameAisle(oldId: String, newName: String, now: Long): AppState {
    val lines = Aisles.parseLines(store.aislesText)
    val target = lines.firstOrNull { it.aisle.id == oldId } ?: return this
    // A name typed with a note of its own ("Produce (also: soap)") brings that note; otherwise the aisle keeps its note.
    val (name, typedNote) = Aisles.splitNote(Aisles.cleanName(newName))
    if (name.isEmpty() || (name == target.aisle.name && typedNote == null)) return this
    val textLines = store.aislesText.lines().toMutableList()
    // Only the name changes: the indent and bullet before it and the "Also here" note after it stay.
    val raw = textLines[target.lineIndex]
    val renamed = (raw.substring(0, target.nameStart) + name + raw.substring(target.nameEnd)).trimEnd()
    textLines[target.lineIndex] = if (typedNote == null) renamed else Aisles.withNote(renamed, target.nameStart + name.length, typedNote)
    val text = textLines.joinToString("\n")
    val newAisles = Aisles.parse(text)
    if (newAisles.size != lines.size) return this
    val idMap = lines.indices.associate { lines[it].aisle.id to newAisles[it].id }
    return copy(store = store.copy(aislesText = text)).remapAisleIds(idMap).reconcileAll(now)
}

/**
 * The aisle editor's "Also here" field (Cora, 5 October; §6.7, AisleNotesTest): sets [aisleId]'s note to [note] by
 * rewriting the end of its line in aislesText ("Produce (also: toothpaste, floss)"; blank removes it). The note is kept
 * to Aisles.NOTE_MAX characters. Only the note changes: the name, every id, every item's aisle, the baskets, styles
 * and corrections stay exactly as they are (items already on the list are re-filed only by "Re-sort my list").
 * An unknown id or the same note -> this.
 */
fun AppState.setAisleNote(aisleId: String, note: String?): AppState {
    val target = Aisles.parseLines(store.aislesText).firstOrNull { it.aisle.id == aisleId } ?: return this
    val clean = note?.let(Aisles::cleanNote)?.ifEmpty { null }
    if (clean == target.aisle.note) return this
    val textLines = store.aislesText.lines().toMutableList()
    textLines[target.lineIndex] = Aisles.withNote(textLines[target.lineIndex], target.nameEnd, clean)
    val text = textLines.joinToString("\n")
    // Never a change of ids: a note that would turn into part of the name (it cannot) leaves the text as it was.
    if (Aisles.parse(text).map { it.id } != store.aisles().map { it.id }) return this
    return copy(store = store.copy(aislesText = text))
}

/**
 * Drag-reorder in the aisle editor (§6.7, MoveAisleTest): moves aisle [from] to [to] (0-based walking positions)
 * by rewriting the order of the aisle lines in aislesText (whole lines, so each "Also here" note goes with its aisle;
 * comments and blank lines stay where they are), then
 * remaps every reference (items, baskets, styles, corrections, every list's openAisle) through the old -> new id
 * map, so everything follows its aisle even when duplicate names renumber their "-2" ids. Aisles without a basket
 * get [guessBasket]. Out-of-range or equal positions -> this.
 */
fun AppState.moveAisle(from: Int, to: Int, guessBasket: (Aisle) -> BasketKind): AppState {
    val lines = Aisles.parseLines(store.aislesText)
    if (from !in lines.indices || to !in lines.indices || from == to) return this
    val textLines = store.aislesText.lines().toMutableList()
    val contents = lines.map { textLines[it.lineIndex] }
    val order = lines.indices.toMutableList().apply { add(to, removeAt(from)) }
    lines.forEachIndexed { position, line -> textLines[line.lineIndex] = contents[order[position]] }
    val text = textLines.joinToString("\n")
    val newAisles = Aisles.parse(text)
    if (newAisles.size != lines.size) return this
    val idMap = order.withIndex().associate { (position, oldIndex) -> lines[oldIndex].aisle.id to newAisles[position].id }
    val remapped = copy(store = store.copy(aislesText = text)).remapAisleIds(idMap)
    val baskets = newAisles.associate { aisle -> aisle.id to (remapped.store.aisleBaskets[aisle.id] ?: guessBasket(aisle)) }
    return remapped.copy(store = remapped.store.copy(aisleBaskets = baskets))
}

/**
 * Rewrites every reference to an aisle id through [idMap] (old id -> new id, or null = the aisle is gone), all at
 * once (a swap a <-> b works): item.aisleId in every list (a gone aisle's items go to Everything else and are no
 * longer "placed by hand"), every list's openAisle (gone -> null, automatic) and collapsedAisles, aisleBaskets and
 * aisleStyles (re-keyed; gone ones dropped; a remapped entry wins over a stale one under the same id), and
 * corrections (gone ones dropped). Ids that are not keys of [idMap] are left alone.
 */
private fun AppState.remapAisleIds(idMap: Map<String, String?>): AppState {
    val changes = idMap.filter { (old, new) -> old != new }
    if (changes.isEmpty()) return this
    val newLists = lists.map { list ->
        var itemsChanged = false
        val items = list.items.map { item ->
            val old = item.aisleId
            if (old == null || old !in changes) {
                item
            } else {
                itemsChanged = true
                val target = changes[old]
                if (target == null) item.copy(aisleId = null, manuallyPlaced = false) else item.copy(aisleId = target)
            }
        }
        val open = list.openAisle?.let { if (it in changes) changes[it] else it }
        val collapsed = list.collapsedAisles.mapNotNullTo(LinkedHashSet()) { if (it in changes) changes[it] else it }
        if (!itemsChanged && open == list.openAisle && collapsed == list.collapsedAisles) {
            list
        } else {
            list.copy(items = if (itemsChanged) items else list.items, openAisle = open, collapsedAisles = collapsed)
        }
    }

    fun <V> rekey(map: Map<String, V>): Map<String, V> {
        val out = LinkedHashMap<String, V>()
        map.forEach { (id, value) -> if (id in changes) changes[id]?.let { out[it] = value } }
        map.forEach { (id, value) -> if (id !in changes) out.putIfAbsent(id, value) }
        return out
    }
    val newCorrections = corrections.mapNotNull { c ->
        if (c.aisleId !in changes) c else changes[c.aisleId]?.let { c.copy(aisleId = it) }
    }
    return copy(
        lists = newLists,
        store = store.copy(aisleBaskets = rekey(store.aisleBaskets), aisleStyles = rekey(store.aisleStyles)),
        corrections = newCorrections,
    )
}

/** Merges model-assigned baskets (ignores ids that are not current aisles). */
fun AppState.setAisleBaskets(baskets: Map<String, BasketKind>): AppState {
    val ids = store.aisles().mapTo(HashSet()) { it.id }
    val merged = store.aisleBaskets + baskets.filterKeys { it in ids }
    return if (merged == store.aisleBaskets) this else copy(store = store.copy(aisleBaskets = merged))
}

/**
 * Applies recategorization results (item id -> aisle id or null) across ALL lists.
 * Skips items the user placed by hand into an aisle that still exists (or deliberately into Unsorted),
 * and treats ids that are not current aisles as null. Applied items are no longer "manually placed".
 * Ends with TripRules.reconcile.
 */
fun AppState.applyAisleAssignments(assignments: Map<String, String?>, now: Long): AppState {
    if (assignments.isEmpty()) return this
    val ids = store.aisles().mapTo(HashSet()) { it.id }
    val applied = mapAllItems { item ->
        if (item.id !in assignments) return@mapAllItems item
        val keptByUser = item.manuallyPlaced && (item.aisleId == null || item.aisleId in ids)
        if (keptByUser) return@mapAllItems item
        val target = assignments[item.id]?.takeIf { it in ids }
        if (target == item.aisleId && !item.manuallyPlaced) item else item.copy(aisleId = target, manuallyPlaced = false)
    }
    return if (applied === this) this else applied.reconcileAll(now)
}

/**
 * Icon cascade step 2 results, keyed by normalizeName(item name). Applies across ALL lists, only to
 * items whose iconFit is still PENDING (a user choice made meanwhile wins): icon/iconFit = the value.
 */
fun AppState.applyIconDecisions(decisions: Map<String, ResolvedIcon>): AppState {
    if (decisions.isEmpty()) return this
    return mapAllItems { item ->
        if (item.iconFit != IconFit.PENDING) return@mapAllItems item
        val decision = decisions[normalizeName(item.name)] ?: return@mapAllItems item
        item.copy(icon = decision.icon, iconFit = decision.fit)
    }
}

/**
 * A bespoke icon finished drawing. For every item in every list with normalizeName(name) ==
 * [normalizedName]: set customIconId when it is null, or always when [overwrite] (a user "redraw").
 */
fun AppState.attachCustomIcon(normalizedName: String, customIconId: String, overwrite: Boolean): AppState =
    mapAllItems { item ->
        val applies = (overwrite || item.customIconId == null) &&
            item.customIconId != customIconId &&
            normalizeName(item.name) == normalizedName
        if (applies) item.copy(customIconId = customIconId) else item
    }

/**
 * Icon cascade step 1 for a free-text name: the newest IconOverride for normalizeName(name) (if its key
 * is still in the catalog), else IconCatalog.match(name)?.key, else null. Pure; no network.
 */
fun AppState.localIcon(name: String, catalog: IconCatalog): String? {
    val key = normalizeName(name)
    val override = iconOverrides.lastOrNull { it.name == key }?.icon?.takeIf { it in catalog }
    return override ?: catalog.match(name)?.key
}

/** Newest override per normalized name, as a map (for IngestContext and OfflineParser). */
fun AppState.iconOverrideMap(): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    // Oldest first: later entries overwrite earlier ones; ties on `at` resolve to the later entry.
    iconOverrides.sortedBy { it.at }.forEach { map[it.name] = it.icon }
    return map
}

/**
 * Input for IconDecider: one entry per distinct normalizeName among items (ALL lists) whose
 * iconFit == PENDING. NamedItem.id = the normalized name, NamedItem.name = a display name.
 */
fun AppState.itemsNeedingIconDecision(): List<NamedItem> {
    val byKey = LinkedHashMap<String, String>()
    for (list in lists) for (item in list.items) {
        if (item.iconFit != IconFit.PENDING) continue
        val key = normalizeName(item.name)
        if (key.isNotEmpty() && key !in byKey) byKey[key] = item.name
    }
    return byKey.map { (key, name) -> NamedItem(id = key, name = name) }
}

/**
 * Input for recategorize: items in ALL lists (checked or not) whose aisleId is null or not a current
 * aisle id, except items the user deliberately moved to Unsorted (manuallyPlaced && aisleId == null).
 * Deduplicated by item id.
 */
fun AppState.itemsNeedingAisle(): List<NamedItem> {
    val ids = store.aisles().mapTo(HashSet()) { it.id }
    val seen = HashSet<String>()
    val out = mutableListOf<NamedItem>()
    for (list in lists) for (item in list.items) {
        val needs = item.aisleId == null || item.aisleId !in ids
        val deliberate = item.manuallyPlaced && item.aisleId == null
        if (needs && !deliberate && seen.add(item.id)) out += NamedItem(id = item.id, name = item.name)
    }
    return out
}

/**
 * Prompt context for [listId]: aisles, newest valid corrections (max StateOps.MAX_PROMPT_CORRECTIONS),
 * unchecked names, and [iconOverrideMap]. Unchanged in C: existingNames stays unchecked names only, so the
 * model may return a checked item, which addItems then revives (§6.2 rule 7).
 */
fun AppState.ingestContext(listId: String): IngestContext {
    val aisles = store.aisles()
    val ids = aisles.mapTo(HashSet()) { it.id }
    val seenNames = HashSet<String>()
    val corrections = corrections
        .sortedByDescending { it.at }
        .filter { it.aisleId in ids && seenNames.add(it.name) }
        .take(StateOps.MAX_PROMPT_CORRECTIONS)
    val existing = list(listId)?.items.orEmpty().filterNot { it.checked }.map { it.name }
    return IngestContext(
        aisles = aisles,
        corrections = corrections,
        existingNames = existing,
        iconOverrides = iconOverrideMap(),
        pantry = pantry.take(PANTRY_IN_CONTEXT).map { it.name },
    )
}

/** Title of a new list when none is given (also the first list's title). */
const val DEFAULT_LIST_TITLE: String = "Groceries"

private val WHITESPACE = Regex("\\s+")

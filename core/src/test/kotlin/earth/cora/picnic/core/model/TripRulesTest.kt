package earth.cora.picnic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** TripRules: the reconcile invariant and Cora's answer to Q9 (§0.4; checklist 132, 134, 315). */
class TripRulesTest {
    private val hour = 60L * 60 * 1000
    private val min = 60_000L

    private fun item(id: String, checked: Boolean = false, checkedAt: Long? = null, slot: Int? = null, addedAt: Long = 1) =
        GroceryItem(id = id, name = id, aisleId = "produce", icon = "apple", addedAt = addedAt, checked = checked, checkedAt = checkedAt, bookSlot = slot)

    private fun list(vararg items: GroceryItem, trip: Trip? = null) = GroceryList("l1", "Saturday shop", 0, items.toList(), trip = trip)

    @Test
    fun reconcileKeepsTheSameInstanceWhenNothingChanges() {
        val empty = list(item("a"))
        assertSame(empty, TripRules.reconcile(empty, 5))
        val open = TripRules.reconcile(list(item("a", checked = true, checkedAt = 3, slot = 0), item("b")), 5, "Store")
        assertSame(open, TripRules.reconcile(open, 99, "Store"))
    }

    @Test
    fun reconcileOpensATripForStickersWithoutOne() {
        // A restored sticker on a list whose trip closed: the trip comes back from its check-off times.
        val l = TripRules.reconcile(list(item("a", checked = true, checkedAt = 300, slot = 0), item("b", checked = true, checkedAt = 100, slot = 1), item("c")), 999, "Hillcrest")
        val trip = assertNotNull(l.trip)
        assertEquals("t-l1-100", trip.id)
        assertEquals(100, trip.startedAt)
        assertEquals(300, trip.lastActivityAt)
        assertEquals("Hillcrest", trip.storeName)
        assertNull(trip.completedAt)
    }

    @Test
    fun reconcileRepairsItemsAndSlots() {
        val l = TripRules.reconcile(
            list(
                item("a", checked = true, checkedAt = null, slot = null),
                item("b", checked = false, checkedAt = 5, slot = 3),
                item("c", checked = true, checkedAt = 7, slot = 7),
            ),
            50,
        )
        assertEquals(50, l.items[0].checkedAt, "a sticker without a check-off time gets now")
        assertNull(l.items[1].checkedAt)
        assertNull(l.items[1].bookSlot)
        assertEquals(listOf(1, null, 0), l.items.map { it.bookSlot }, "spots are valid and unique (c, out of range, takes the lowest hole first)")
    }

    @Test
    fun completionInvariant() {
        val trip = Trip("t-l1-1", 1, "S", 1)
        assertNull(TripRules.reconcile(list(item("a", true, 1, 0), item("b"), trip = trip), 9).trip!!.completedAt)
        val done = TripRules.reconcile(list(item("a", true, 1, 0), item("b", true, 6 * min, 1), trip = trip), 6 * min)
        assertEquals(6 * min, done.trip!!.completedAt)
        assertEquals(6, done.trip!!.shopMinutes)
        val stale = TripRules.reconcile(list(item("a", true, 1, 0), item("b"), trip = trip.copy(completedAt = 5)), 9)
        assertNull(stale.trip!!.completedAt, "a completedAt that no longer holds is cleared")
        assertNull(TripRules.reconcile(list(trip = trip), 9).trip, "no items, no stickers: no trip")
    }

    @Test
    fun aMigratedCompleteTripKeepsItsMinutesUnknown() {
        val migrated = Trip("t-l1-1", 1, "S", 1, completedAt = 10, finaleShownAt = 10, shopMinutes = null)
        val l = list(item("a", true, 1, 0), item("b", true, 2, 1), trip = migrated)
        val reopened = TripRules.reconcile(l.copy(items = listOf(l.items[0], item("b"))), 20)
        val again = TripRules.reconcile(reopened.copy(items = l.items), 30)
        assertEquals(30, again.trip!!.completedAt)
        assertNull(again.trip!!.shopMinutes, "its first completion was the migration: still \"--\"")
    }

    @Test
    fun autoNewBookSameTapCompletion() {
        // Saturday's shop is finished and its finale shown; that evening she writes down paper towels.
        val start = 1_000_000L
        var s = AppState(
            lists = listOf(GroceryList("l1", "Groceries", 0, listOf(item("bread"), item("milk")))),
            currentListId = "l1",
            store = StoreProfile(name = "Hillcrest Market"),
        ).toggleChecked("l1", "bread", start).toggleChecked("l1", "milk", start + 5 * min).markFinaleShown("l1", start + 6 * min)
        s = s.addItems("l1", listOf(item("towels", addedAt = start + 8 * hour)), start + 8 * hour).first
        assertNull(s.list("l1")!!.trip!!.completedAt, "more to get")
        // 13 h after the shop she checks them off: the book is filed, the fresh trip is finished by the same tap, and its
        // finale counts as shown then (no celebration, no bonus).
        val at = start + 5 * min + 13 * hour
        val (after, auto) = s.toggleCheckedStartingNewBook("l1", "towels", at)
        assertNotNull(auto)
        val trip = after.list("l1")!!.trip!!
        assertEquals(listOf("towels"), after.list("l1")!!.items.map { it.id })
        assertEquals(at, trip.completedAt)
        assertEquals(at, trip.finaleShownAt)
        assertNull(trip.bonus)
        assertFalse(TripRules.finaleOwed(after.list("l1")!!))
        // A fresh book with more to get is not touched: its finale is still to come.
        val more = s.addItems("l1", listOf(item("soap", addedAt = start + 9 * hour)), start + 9 * hour).first
        val (open, _) = more.toggleCheckedStartingNewBook("l1", "towels", at)
        assertNull(open.list("l1")!!.trip!!.finaleShownAt)
    }

    @Test
    fun autoNewBookAfterIdle() {
        val start = 1_000_000L
        val s = AppState(
            lists = listOf(GroceryList("l1", "Groceries", 0, listOf(item("bread"), item("milk"), item("eggs"), item("jam")))),
            currentListId = "l1",
            store = StoreProfile(name = "Hillcrest Market"),
        ).toggleChecked("l1", "bread", start).toggleChecked("l1", "milk", start + 5 * min).setOpenAisle("l1", "produce")
        val old = s.list("l1")!!.trip!!

        // Just under 12 h after the last activity: the same book.
        val (same, none) = s.toggleCheckedStartingNewBook("l1", "eggs", start + 5 * min + TripRules.IDLE_MILLIS)
        assertNull(none)
        assertEquals(old.id, same.list("l1")!!.trip!!.id)

        // A week later: no question; the old book is filed, what was got is cleared, the item lands in a fresh book.
        val later = start + 7 * 24 * hour
        val (after, auto) = s.toggleCheckedStartingNewBook("l1", "eggs", later)
        assertNotNull(auto)
        val list = after.list("l1")!!
        assertEquals(old.id, auto.book.id)
        assertEquals(listOf("bread", "milk"), auto.book.stickers.map { it.name })
        assertEquals(listOf("eggs", "jam"), list.items.map { it.id }, "Clear what I got")
        val fresh = list.trip!!
        assertEquals("t-l1-$later", fresh.id)
        assertEquals(later, fresh.startedAt)
        assertEquals(0, list.items[0].bookSlot, "the tapped item is the new book's first sticker")
        assertEquals(later, list.lastClosedAt)
        assertNull(list.openAisle)
        assertEquals(NewBookUndo("l1", "eggs", old, s.list("l1")!!.items.withIndex().filter { it.value.checked }, null, "produce"), auto.undo)
    }

    @Test
    fun autoNewBookUndo() {
        val start = 1_000_000L
        val s = AppState(
            lists = listOf(GroceryList("l1", "Groceries", 0, listOf(item("bread"), item("milk"), item("eggs"), item("jam"), item("tea")))),
            currentListId = "l1",
            store = StoreProfile(name = "Hillcrest Market"),
        ).toggleChecked("l1", "bread", start).toggleChecked("l1", "milk", start + 5 * min).toggleChecked("l1", "bread", start + 6 * min)
            .toggleChecked("l1", "bread", start + 7 * min).setOpenAisle("l1", "produce")
        // bread re-checked into the lowest hole: milk 1, bread 0.
        assertEquals(listOf(0, 1), listOf("bread", "milk").map { s.findItem(it)!!.bookSlot })
        val old = s.list("l1")!!.trip!!

        val later = start + 3 * 24 * hour
        val (after, auto) = s.toggleCheckedStartingNewBook("l1", "eggs", later)
        assertNotNull(auto)
        // Meanwhile she adds something and checks another item on the fresh book.
        val busy = after.addItems("l1", listOf(item("salt", addedAt = later + 1)), later + 1).first.toggleChecked("l1", "jam", later + 2 * min)

        val undone = busy.undoNewBook(auto.undo, later + 3 * min)
        val list = undone.list("l1")!!
        assertEquals(listOf("bread", "milk", "eggs", "jam", "tea", "salt"), list.items.map { it.id }, "the cleared items come back where they were")
        val trip = list.trip!!
        assertEquals(old.id, trip.id, "the old book is back")
        assertEquals(old.startedAt, trip.startedAt)
        assertEquals(later + 3 * min, trip.lastActivityAt)
        assertNull(list.lastClosedAt)
        assertEquals("produce", list.openAisle)
        assertEquals(
            mapOf("bread" to 0, "milk" to 1, "eggs" to 2, "jam" to 3),
            list.items.filter { it.checked }.associate { it.id to it.bookSlot },
            "the old stickers keep their spots; the tapped item (then the next) goes into the old book",
        )
        assertTrue(list.items.single { it.id == "eggs" }.checked)
        assertEquals(later, list.items.single { it.id == "eggs" }.checkedAt)
        val twice = undone.undoNewBook(auto.undo, later + 4 * min).list("l1")!!
        assertEquals(list.items, twice.items, "a second undo changes nothing in the book")
        assertSame(busy, busy.undoNewBook(auto.undo.copy(listId = "nope"), later))
    }

    @Test
    fun startsNewBookOnlyAfterTwelveIdleHours() {
        val l = list(item("a", true, 5, 0), trip = Trip("t-l1-5", 5, "S", lastActivityAt = 5))
        assertFalse(TripRules.startsNewBook(l, 5 + TripRules.IDLE_MILLIS))
        assertTrue(TripRules.startsNewBook(l, 6 + TripRules.IDLE_MILLIS))
        assertFalse(TripRules.startsNewBook(list(item("a")), Long.MAX_VALUE), "no trip, nothing to file")
    }

    /** A shop of [n] items, all checked between [start] and [start] + n min, finale shown a minute after. */
    private fun finishedShop(start: Long, vararg names: String): AppState {
        val base = AppState(
            lists = listOf(GroceryList("l1", "Groceries", 0, names.map { item(it) })),
            currentListId = "l1",
            store = StoreProfile(name = "Hillcrest Market"),
        )
        val done = names.foldIndexed(base) { i, s, n -> s.toggleChecked("l1", n, start + i * min) }
        return done.markFinaleShown("l1", start + names.size * min)
    }

    /**
     * §7.1, §7.8 (item 315): items she writes down after a finished shop are for the next one. The book keeps its
     * spots, stamps and PERFECT; the list has more to get.
     */
    @Test
    fun itemsAddedAfterTheFinaleStayOutOfTheBook() {
        val start = 1_000_000L
        val shop = finishedShop(start, "bread", "milk", "eggs")
        val evening = start + 9 * hour
        val more = shop.addItems("l1", listOf(item("salt", addedAt = evening), item("tea", addedAt = evening)), evening).first
        val list = more.list("l1")!!
        assertFalse(TripRules.isComplete(list), "the list has more to get")
        assertTrue(TripRules.bookComplete(list), "the shop's book is still complete")
        assertEquals(listOf("bread", "milk", "eggs"), TripRules.bookItems(list).map { it.id })
        val book = more.tripBook("l1", evening)!!
        assertEquals(3, book.total)
        assertTrue(book.perfect)
        assertEquals(start + 2 * min, book.completedAt, "when the shop was completed")
        assertTrue(book.stamps.all { it.done })
        // Before the finale every item counts: an item added mid-shop is a spot at once.
        val midShop = AppState(
            lists = listOf(GroceryList("l1", "Groceries", 0, listOf(item("bread"), item("milk")))),
            currentListId = "l1",
        ).toggleChecked("l1", "bread", start).addItems("l1", listOf(item("tea", addedAt = start + min)), start + min).first
        assertEquals(3, TripRules.bookItems(midShop.list("l1")!!).size)
        // Un-checking one of the shop's own items still costs the book its PERFECT.
        val oops = more.toggleChecked("l1", "milk", evening + min).list("l1")!!
        assertFalse(TripRules.bookComplete(oops))
    }

    /**
     * D21 (§13.2): the receipt belongs to the finished shop. Holding the LCD prints it while the trip is complete or once
     * its finale has played, so writing next week's items the same evening (which clears completedAt) never turns the
     * hold back into the pet's. Before the finale an incomplete trip hatches the pet; without a trip nothing prints.
     */
    @Test
    fun printsReceiptOnceTheShopIsFinished() {
        val start = 1_000_000L
        val base = AppState(lists = listOf(GroceryList("l1", "Groceries", 0, listOf(item("bread"), item("milk")))), currentListId = "l1")
        assertFalse(TripRules.printsReceipt(base.list("l1")!!), "no trip")
        val half = base.toggleChecked("l1", "bread", start)
        assertFalse(TripRules.printsReceipt(half.list("l1")!!), "mid-shop: the pet")
        val done = half.toggleChecked("l1", "milk", start + min)
        assertTrue(TripRules.printsReceipt(done.list("l1")!!), "complete, finale not played yet")
        val shown = done.markFinaleShown("l1", start + 2 * min)
        val later = shown.addItems("l1", listOf(item("tea", addedAt = start + 5 * hour)), start + 5 * hour).first.list("l1")!!
        assertNull(later.trip!!.completedAt, "the late add re-opens the list")
        assertTrue(TripRules.printsReceipt(later), "the finished shop still prints its receipt")
        // Before its finale a trip is finished only while it is complete: an un-check takes the receipt back to the pet.
        val unchecked = done.toggleChecked("l1", "milk", start + 3 * min).list("l1")!!
        assertFalse(TripRules.printsReceipt(unchecked), "un-checked before the finale: not finished")
    }

    /** §0.4 Q9's add-side twin (item 315): the first add more than 12 h after the trip's last activity files the book. */
    @Test
    fun autoNewBookOnAdd() {
        val start = 1_000_000L
        val shop = finishedShop(start, "bread", "milk", "eggs")
        val old = shop.list("l1")!!.trip!!

        // Within 12 h: the add joins the list; nothing is filed.
        val soon = old.lastActivityAt + TripRules.IDLE_MILLIS
        val (same, sameOutcome, none) = shop.addItemsStartingNewBook("l1", listOf(item("salt", addedAt = soon)), soon)
        assertNull(none)
        assertEquals(listOf("salt"), sameOutcome.added)
        assertEquals(old.id, same.list("l1")!!.trip!!.id)

        // Tuesday: the book is filed as it stands (PERFECT), what was got is cleared, and "milk" is a new row, not a
        // sticker peeled out of last week's book.
        val tuesday = start + 3 * 24 * hour
        val (after, outcome, auto) = shop.addItemsStartingNewBook("l1", listOf(item("milk-2").copy(name = "milk", addedAt = tuesday), item("jam", addedAt = tuesday)), tuesday)
        assertNotNull(auto)
        assertEquals(old.id, auto.book.id)
        assertTrue(auto.book.perfect)
        assertEquals(3, auto.book.total)
        assertEquals(listOf("milk-2", "jam"), outcome.added)
        assertTrue(outcome.revived.isEmpty())
        val list = after.list("l1")!!
        assertEquals(listOf("milk-2", "jam"), list.items.map { it.id })
        assertNull(list.trip, "the next book opens at the next check-off")
        assertEquals(tuesday, list.lastClosedAt)
        assertEquals(NewBookUndo("l1", null, old, shop.list("l1")!!.items.withIndex().toList(), null, null), auto.undo)
        assertTrue(after.newBookStands(auto))

        // An add that changes nothing (a row already on the list) files nothing.
        val waiting = shop.toggleChecked("l1", "eggs", start + 10 * min)
        val (unchanged, dup, nothing) = waiting.addItemsStartingNewBook("l1", listOf(item("eggs-again").copy(name = "eggs")), tuesday)
        assertNull(nothing)
        assertSame(waiting, unchanged)
        assertEquals(listOf("eggs"), dup.alreadyThere)

        // Undo: the old book and its stickers come back; the new rows stay, out of the finished book, and the trip
        // keeps its old activity time (an add checks nothing).
        val undone = after.undoNewBook(auto.undo, tuesday + min).list("l1")!!
        assertEquals(listOf("bread", "milk", "eggs", "milk-2", "jam"), undone.items.map { it.id })
        assertEquals(old.id, undone.trip!!.id)
        assertEquals(old.lastActivityAt, undone.trip!!.lastActivityAt)
        assertTrue(TripRules.bookComplete(undone))
    }

    /**
     * Cora's week (§0.4 Q9: build the list over the week, shop once; item 315). Saturday's finished shop, an add that
     * evening, more on Tuesday, the next Saturday's shop: last week's book is filed PERFECT and the new shop has its
     * own book.
     */
    @Test
    fun weeklyFlow() {
        val saturday = 1_000_000L
        val shop = finishedShop(saturday, "bread", "milk", "eggs")
        val evening = saturday + 8 * hour
        val (sat, _, none) = shop.addItemsStartingNewBook("l1", listOf(item("salt", addedAt = evening)), evening)
        assertNull(none)
        val tuesday = saturday + 3 * 24 * hour
        val (tue, _, auto) = sat.addItemsStartingNewBook("l1", listOf(item("tea", addedAt = tuesday), item("milk-2").copy(name = "milk", addedAt = tuesday)), tuesday)
        assertNotNull(auto)
        assertTrue(auto.book.perfect, "the evening's salt never joined Saturday's book")
        assertEquals(3, auto.book.total)
        assertEquals(3, auto.book.stickers.size)
        assertEquals(listOf("salt", "tea", "milk-2"), tue.list("l1")!!.items.map { it.id })
        val next = saturday + 7 * 24 * hour
        val (shopping, again) = tue.toggleCheckedStartingNewBook("l1", "tea", next)
        assertNull(again, "the old book is filed already: the first check-off just opens the new one")
        val trip = shopping.list("l1")!!.trip!!
        assertEquals(TripRules.tripId("l1", next), trip.id)
        assertEquals(1, shopping.tripBook("l1", next)!!.stickers.size)
    }

    /** The automatic new book's Undo applies only while that book stands (§0.4 Q9). */
    @Test
    fun newBookStandsUntilSomethingNewer() {
        val start = 1_000_000L
        val s = finishedShop(start, "bread", "milk").addItems("l1", listOf(item("eggs", addedAt = start + hour)), start + hour).first
        val later = start + 2 * 24 * hour
        val (fresh, auto) = s.toggleCheckedStartingNewBook("l1", "eggs", later)
        assertNotNull(auto)
        assertTrue(fresh.newBookStands(auto))
        assertTrue(fresh.toggleChecked("l1", "eggs", later + min).newBookStands(auto), "the tapped item un-checked: still the same book")
        assertFalse(fresh.newTrip("l1", NewTripMode.CLEAR_GOTTEN, later + min).first.newBookStands(auto), "a New trip filed the fresh book")
        val closed = fresh.toggleChecked("l1", "eggs", later + min)
        assertFalse(closed.toggleChecked("l1", "eggs", later + 2 * min).newBookStands(auto), "a later book")
        val twoLists = fresh.copy(lists = fresh.lists + GroceryList("l2", "Party", 0))
        assertFalse(twoLists.deleteList("l1", later + min).first.newBookStands(auto), "the list is gone")
    }
}

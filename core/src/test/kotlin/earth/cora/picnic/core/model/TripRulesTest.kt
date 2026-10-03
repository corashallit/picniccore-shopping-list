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
}

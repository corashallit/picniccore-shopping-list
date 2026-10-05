package earth.cora.picnic.core.model

import earth.cora.picnic.core.SaturdayShop
import earth.cora.picnic.core.SaturdayShop.LIST_ID
import earth.cora.picnic.core.SaturdayShop.NOW
import earth.cora.picnic.core.SaturdayShop.TRIP_START
import earth.cora.picnic.core.store.BookStamp
import earth.cora.picnic.core.store.BookSticker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Trips (amendment A5, docs/DESIGN_STICKER.md §7.1, §7.8; checklist 131–135, 141, 143, 144, 298). */
class TripOpsTest {
    private val min = 60_000L

    private fun item(id: String, aisle: String? = "produce", checked: Boolean = false, addedAt: Long = 1) =
        GroceryItem(id = id, name = id, aisleId = aisle, icon = "apple", addedAt = addedAt, checked = checked)

    private fun state(vararg items: GroceryItem, store: String = "Hillcrest Market") = AppState(
        lists = listOf(GroceryList("l1", "Saturday shop", 0, items.toList()), GroceryList("l2", "Party", 0, listOf(item("p1"), item("p2")))),
        currentListId = "l1",
        store = StoreProfile(name = store),
    )

    private fun AppState.trip(listId: String = "l1") = list(listId)!!.trip
    private fun AppState.item(id: String) = findItem(id)!!

    @Test
    fun opensOnFirstCheck() {
        val s = state(item("a"), item("b"))
        assertNull(s.trip(), "before the first check-off there is no trip")
        val checked = s.toggleChecked("l1", "a", 1_000)
        val trip = assertNotNull(checked.trip())
        assertEquals("t-l1-1000", trip.id, "deterministic id t-<listId>-<now>")
        assertEquals(1_000, trip.startedAt)
        assertEquals("Hillcrest Market", trip.storeName)
        assertEquals(1_000, trip.lastActivityAt)
        assertNull(trip.completedAt)
        assertNull(trip.shopMinutes)
        val second = checked.toggleChecked("l1", "b", 5_000).trip()!!
        assertEquals(trip.id, second.id, "the next check-off stays on the open trip")
        assertEquals(1_000, second.startedAt)
        assertEquals(5_000, second.lastActivityAt)
    }

    @Test
    fun atomicToggle() {
        val s = state(item("a"), item("b"), item("c"))
        val one = s.toggleChecked("l1", "b", 100)
        val b = one.item("b")
        assertTrue(b.checked)
        assertEquals(100, b.checkedAt)
        assertEquals(0, b.bookSlot, "the lowest free spot")
        assertEquals(100, one.trip()!!.lastActivityAt)
        val two = one.toggleChecked("l1", "a", 200)
        assertEquals(1, two.item("a").bookSlot)
        val three = two.toggleChecked("l1", "c", 300)
        val trip = three.trip()!!
        assertEquals(300, trip.completedAt, "the last check-off completes the trip in the same transition")
        assertEquals(1, trip.shopMinutes, "at least 1 min")
        assertEquals(2, three.item("c").bookSlot)

        val unchecked = three.toggleChecked("l1", "b", 400)
        assertFalse(unchecked.item("b").checked)
        assertNull(unchecked.item("b").checkedAt)
        assertNull(unchecked.item("b").bookSlot)
        assertEquals(400, unchecked.trip()!!.lastActivityAt, "an un-check is activity too")
        assertNull(unchecked.trip()!!.completedAt)
        assertEquals(listOf(null, 1, 2), listOf("b", "a", "c").map { unchecked.item(it).bookSlot }, "the hole stays; nothing shifts")
        assertSame(s, s.toggleChecked("l1", "nope", 1))
        assertSame(s, s.toggleChecked("nope", "a", 1))
    }

    @Test
    fun completion() {
        val s = state(item("a"), item("b"))
        val done = s.toggleChecked("l1", "a", 0).toggleChecked("l1", "b", 30 * min)
        assertEquals(30 * min, done.trip()!!.completedAt)
        assertEquals(30, done.trip()!!.shopMinutes)

        // Un-checking clears it; re-completing sets it again (to the new time) and the minutes stay frozen.
        val reopened = done.toggleChecked("l1", "b", 40 * min)
        assertNull(reopened.trip()!!.completedAt)
        assertEquals(30, reopened.trip()!!.shopMinutes)
        val again = reopened.toggleChecked("l1", "b", 95 * min)
        assertEquals(95 * min, again.trip()!!.completedAt)
        assertEquals(30, again.trip()!!.shopMinutes)

        // Adding clears it.
        val added = done.addItems("l1", listOf(item("c")), 31 * min).first
        assertNull(added.trip()!!.completedAt)
        assertEquals(30, added.trip()!!.shopMinutes)

        // The finale is marked once per trip; re-completion does not replay it.
        val shown = done.markFinaleShown("l1", 31 * min)
        assertEquals(31 * min, shown.trip()!!.finaleShownAt)
        assertTrue(TripRules.finaleOwed(done.list("l1")!!))
        assertFalse(TripRules.finaleOwed(shown.list("l1")!!))
        val recompleted = shown.toggleChecked("l1", "a", 32 * min).toggleChecked("l1", "a", 33 * min)
        assertEquals(31 * min, recompleted.trip()!!.finaleShownAt)
        assertFalse(TripRules.finaleOwed(recompleted.list("l1")!!))
        assertSame(shown, shown.markFinaleShown("l1", 99 * min))
    }

    @Test
    fun anOwedFinalePlaysWithinThirtyMinutesElseItIsMarkedStale() {
        val done = state(item("a")).toggleChecked("l1", "a", 0).list("l1")!!
        assertTrue(TripRules.finaleOwed(done))
        assertFalse(TripRules.finaleIsStale(done, TripRules.FINALE_GRACE_MILLIS))
        assertTrue(TripRules.finaleIsStale(done, TripRules.FINALE_GRACE_MILLIS + 1))
        assertTrue(TripRules.isComplete(done))
    }

    @Test
    fun completesOnDelete() {
        val s = state(item("a"), item("b")).toggleChecked("l1", "a", 0)
        val after = s.removeItem("l1", "b", 10 * min)
        assertEquals(10 * min, after.trip()!!.completedAt, "tearing off the last unchecked item completes the trip")
        assertTrue(TripRules.finaleOwed(after.list("l1")!!), "the finale plays whatever the cause")
        val undone = after.restoreItem("l1", s.item("b"), 1, 11 * min)
        assertNull(undone.trip()!!.completedAt, "undo of the tear-off re-opens it")
    }

    @Test
    fun completesOnMove() {
        // A stale state (all checked, completedAt missing, e.g. restored from a backup taken mid-write): any op that
        // touches items reconciles it.
        val stale = state(item("a", checked = true)).let { s ->
            s.updateList("l1") { it.copy(items = it.items.map { i -> i.copy(checkedAt = 5, bookSlot = 0) }, trip = Trip("t-l1-5", 5, "Hillcrest Market", 5)) }
        }
        assertNull(stale.trip()!!.completedAt)
        val moved = stale.moveItemToAisle("l1", "a", "bakery", 20)
        assertEquals(20, moved.trip()!!.completedAt)
        assertEquals("bakery", moved.item("a").aisleId)
    }

    @Test
    fun completesOnSync() {
        val stale = state(item("a", checked = true)).updateList("l1") { it.copy(trip = Trip("t-l1-5", 5, "Hillcrest Market", 5)) }
        val synced = stale.applyAisleAssignments(mapOf("a" to "bakery"), 30)
        assertEquals(30, synced.trip()!!.completedAt)
        assertEquals(30, synced.item("a").checkedAt, "a checked item without a check-off time gets one")
        assertEquals(0, synced.item("a").bookSlot)
    }

    @Test
    fun completesOnAislesEdit() {
        val stale = state(item("a", checked = true)).updateList("l1") { it.copy(trip = Trip("t-l1-5", 5, "Hillcrest Market", 5)) }
        assertEquals(40, stale.setAislesText(stale.store.aislesText, { BasketKind.DEFAULT }, 40).trip()!!.completedAt)
        assertEquals(41, stale.commitAislesText("Produce\nBakery", { BasketKind.DEFAULT }, 41).trip()!!.completedAt)
        assertEquals(42, stale.renameAisle("produce", "Fresh produce", 42).trip()!!.completedAt)

        // Aisle completion is derived the same way (AisleSection.complete): an edit that moves what was left out of
        // an aisle leaves that aisle done and Everything else with something to get.
        val s = state(item("a", aisle = "produce", checked = true), item("b", aisle = "frozen")).copy(store = StoreProfile(aislesText = "Produce\nFrozen"))
        val before = ListGrouping.group(s.list("l1")!!, s.store.aisles(), { BasketKind.DEFAULT })
        assertEquals(listOf("produce", "frozen"), before.sections.map { it.id })
        assertEquals("frozen", before.currentSectionId)
        val edited = s.commitAislesText("Produce", { BasketKind.DEFAULT }, 50)
        assertNull(edited.item("b").aisleId, "the removed aisle's item goes to Everything else")
        val after = ListGrouping.group(edited.list("l1")!!, edited.store.aisles(), { BasketKind.DEFAULT })
        assertEquals(listOf(SectionIds.UNSORTED, "produce"), after.sections.map { it.id })
        assertTrue(after.sections.single { it.id == "produce" }.complete)
        assertEquals(SectionIds.UNSORTED, after.currentSectionId)
    }

    @Test
    fun completesOnUndoQuickAdd() {
        val s = state(item("a"), item("b")).toggleChecked("l1", "a", 0).toggleChecked("l1", "b", 5 * min)
        assertEquals(5 * min, s.trip()!!.completedAt)
        val (added, outcome) = s.addItems("l1", listOf(item("milk", addedAt = 6 * min)), 6 * min)
        assertNull(added.trip()!!.completedAt, "a quick add re-opens the trip")
        val undone = outcome.added.fold(added) { acc, id -> acc.removeItem("l1", id, 7 * min) }
        assertEquals(7 * min, undone.trip()!!.completedAt, "Undo removes exactly the added items and the trip completes again")
        assertEquals(5, undone.trip()!!.shopMinutes, "minutes stay frozen from the first completion")
    }

    @Test
    fun emptyTripCloses() {
        val s = state(item("a"), item("b")).toggleChecked("l1", "a", 0).hatchPet("l1")
        assertTrue(s.trip()!!.pet)
        val unchecked = s.toggleChecked("l1", "a", 10)
        assertNull(unchecked.trip(), "un-checking the last sticker closes the trip without filing")
        assertNull(unchecked.list("l1")!!.lastClosedAt, "closing without filing is not a New trip")
        val deleted = s.removeItem("l1", "a", 10)
        assertNull(deleted.trip(), "so does deleting it (the pet leaves with it)")
        val reopened = unchecked.toggleChecked("l1", "b", 20)
        assertEquals("t-l1-20", reopened.trip()!!.id)
        assertFalse(reopened.trip()!!.pet)
        val revived = state(item("a"), item("b")).toggleChecked("l1", "a", 0).addItems("l1", listOf(item("x").copy(id = "a2", name = "a")), 3).first
        assertNull(revived.trip(), "reviving the only sticker closes the trip too")
    }

    @Test
    fun newTripModes() {
        val s = SaturdayShop.state()
            .printReceipt(LIST_ID, Receipt(NOW, "SAT 3 OCT", "HILLCREST MARKET", 10, 8, null, 1))
            .setTripBonus(LIST_ID, BookSticker(-1, "Croissants", IconRef("croissant"), Rarity.SHINY, NOW))
        val trip = s.trip(LIST_ID)!!

        val (cleared, book) = s.newTrip(LIST_ID, NewTripMode.CLEAR_GOTTEN, NOW + min)
        val list = cleared.list(LIST_ID)!!
        assertEquals(14, list.items.size, "Clear what I got removes the 10 checked items and keeps the rest")
        assertTrue(list.items.none { it.checked || it.checkedAt != null || it.bookSlot != null })
        assertNull(list.trip)
        assertEquals(NOW + min, list.lastClosedAt)
        assertNull(list.openAisle, "the open aisle resets to automatic")
        assertNotNull(book)
        assertEquals(trip.id, book.id)
        assertEquals("Saturday shop", book.listTitle)
        assertEquals("Hillcrest Market", book.storeName)
        assertEquals(TRIP_START, book.startedAt)
        assertNull(book.completedAt)
        assertEquals(NOW + min, book.closedAt)
        assertEquals(24, book.total)
        assertNull(book.minutes, "an unfinished trip has no shop time")
        assertFalse(book.perfect)
        assertEquals(trip.receipt, book.receipt, "a printed receipt is filed (as saved)")
        assertEquals("Croissants", book.bonus!!.name)
        assertEquals(
            SaturdayShop.CHECKS.map { it.first },
            book.stickers.map { sticker -> s.list(LIST_ID)!!.items.single { it.name == sticker.name }.id },
            "stickers in spot order = check-off order",
        )
        assertEquals((0 until 10).toList(), book.stickers.map { it.slot })
        assertEquals(IconRef("peameal_bacon"), book.stickers[5].icon)
        assertEquals(TRIP_START, book.stickers[0].at)
        assertEquals(
            listOf(
                BookStamp(1, "Produce", "strawberry", "gingham", "strawberry", false),
                BookStamp(2, "Bakery", "ramune", "polka", "bee", false),
                BookStamp(3, "Deli & meat", "lemon", "stripes", "star", true),
                BookStamp(4, "Baking", "melon", "hearts", "heart", false),
                BookStamp(5, "Breakfast", "grape", "stars", "cloud", false),
                BookStamp(6, "Dairy & eggs", "peach", "clouds", "flower", false),
                BookStamp(7, "Frozen", "mint", "check", "ladybug", false),
                BookStamp(8, "Household", "sakura", "waves", "teacup", false),
            ),
            book.stamps,
            "stamps with position, colour, pattern, pictogram and done",
        )
        assertEquals("frozen", book.napAisleId, "egg 8: the first aisle whose basket is still empty")

        val (kept, keptBook) = s.newTrip(LIST_ID, NewTripMode.UNCHECK_ALL, NOW + min)
        val keptList = kept.list(LIST_ID)!!
        assertEquals(24, keptList.items.size, "Keep everything un-checks all")
        assertTrue(keptList.items.none { it.checked || it.checkedAt != null || it.bookSlot != null })
        assertNull(keptList.trip)
        assertEquals(NOW + min, keptList.lastClosedAt)
        assertEquals(book, keptBook, "both modes file the same book")

        assertEquals(s to null, s.newTrip("nope", NewTripMode.CLEAR_GOTTEN, NOW))
        val noTrip = SaturdayShop.unchecked()
        assertEquals(noTrip to null, noTrip.newTrip(LIST_ID, NewTripMode.CLEAR_GOTTEN, NOW), "without a trip it does nothing")
    }

    @Test
    fun aCompleteTripFilesAPerfectBookWithItsMinutesAndNoNap() {
        val s = state(item("a"), item("b", aisle = "bakery")).toggleChecked("l1", "a", 0).toggleChecked("l1", "b", 41 * min).dismissNap("l1")
        val book = s.newTrip("l1", NewTripMode.CLEAR_GOTTEN, 50 * min).second!!
        assertTrue(book.perfect)
        assertEquals(41 * min, book.completedAt)
        assertEquals(41, book.minutes)
        assertNull(book.napAisleId)
        assertTrue(book.stamps.all { it.done })
    }

    @Test
    fun aDismissedCatNeverNapsInTheFiledBook() {
        val s = state(item("a"), item("b", aisle = "bakery")).toggleChecked("l1", "a", 0)
        assertEquals("bakery", s.tripBook("l1", 1)!!.napAisleId)
        assertNull(s.dismissNap("l1").tripBook("l1", 1)!!.napAisleId)
    }

    @Test
    fun pendingAndLiveStickersAreFiledAsTheyShow() {
        val s = state(
            item("a").copy(icon = "paper_bag", iconFit = IconFit.PENDING),
            item("b").copy(icon = "condiments_generic", iconFit = IconFit.APPROXIMATE, customIconId = "c-9"),
            item("c").copy(rarity = Rarity.JACKPOT),
        ).toggleChecked("l1", "a", 1).toggleChecked("l1", "b", 2).toggleChecked("l1", "c", 3)
        val icons = s.tripBook("l1", 4)!!.stickers.map { it.icon }
        assertEquals(listOf(IconRef("_capsule"), IconRef("condiments_generic", "c-9"), IconRef("apple")), icons)
        assertEquals(Rarity.JACKPOT, s.tripBook("l1", 4)!!.stickers[2].rarity)
    }

    @Test
    fun everythingElseIsTheFirstStamp() {
        val s = state(item("loose", aisle = null), item("a")).toggleChecked("l1", "loose", 1)
        val stamps = s.tripBook("l1", 2)!!.stamps
        assertEquals(BookStamp(0, "Everything else", "neutral", "plain", "capsule", true), stamps.first())
        assertEquals(2, stamps.size)
    }

    @Test
    fun styleOverridesKeepThePictogramOfThePosition() {
        val s = state(item("a")).setAisleStyle("produce", AisleStyle(family = "cocoa", pattern = "waves")).toggleChecked("l1", "a", 1)
        assertEquals(BookStamp(1, "Produce", "cocoa", "waves", "strawberry", true), s.tripBook("l1", 2)!!.stamps.single())
    }

    @Test
    fun perListTrips() {
        val s = state(item("a"), item("b"))
        val both = s.toggleChecked("l1", "a", 100).toggleChecked("l2", "p1", 200)
        assertEquals("t-l1-100", both.trip("l1")!!.id)
        assertEquals("t-l2-200", both.trip("l2")!!.id)
        val filed = both.newTrip("l1", NewTripMode.CLEAR_GOTTEN, 300).first
        assertNull(filed.trip("l1"))
        assertEquals(both.trip("l2"), filed.trip("l2"), "New trip on one list leaves the other's book alone")
        assertEquals(both.switchList("l2").trip("l2"), both.trip("l2"), "switching lists switches the book")
        val completedOther = both.toggleChecked("l2", "p2", 400)
        assertNull(completedOther.trip("l1")!!.completedAt)
        assertEquals(400, completedOther.trip("l2")!!.completedAt)
    }

    @Test
    fun idleTripFirstCheck() {
        // Cora's answer to Q9 replaces the prompt: the check-off itself starts the new book (TripRulesTest covers the
        // undo); a check-off within 12 h stays on the trip.
        val s = state(item("a"), item("b"), item("c")).toggleChecked("l1", "a", 0)
        val (soon, none) = s.toggleCheckedStartingNewBook("l1", "b", TripRules.IDLE_MILLIS)
        assertNull(none)
        assertEquals("t-l1-0", soon.trip()!!.id)
        val (later, auto) = s.toggleCheckedStartingNewBook("l1", "b", TripRules.IDLE_MILLIS + 1)
        assertNotNull(auto)
        assertEquals("t-l1-0", auto.book.id, "the old book is filed")
        assertEquals("t-l1-${TripRules.IDLE_MILLIS + 1}", later.trip()!!.id, "the item lands in a fresh book")
        assertNull(later.findItem("a"), "what was got last time is cleared")
        assertEquals(0, later.item("b").bookSlot)
        // An un-check on an idle trip files that book first too, with the sticker still in it (§7.1).
        val (unchecking, filed) = later.toggleCheckedStartingNewBook("l1", "b", 3 * TripRules.IDLE_MILLIS)
        assertEquals(1, assertNotNull(filed).book.stickers.size)
        assertNull(unchecking.trip())
        assertFalse(unchecking.item("b").checked)
    }

    @Test
    fun newAnchor() {
        val never = state(item("a", addedAt = 5))
        assertNull(TripRules.newAnchor(never.list("l1")!!))
        assertFalse(TripRules.isNew(never.list("l1")!!, never.item("a")), "a list that never had a trip shows no new stars")

        val open = never.toggleChecked("l1", "a", 10).addItems("l1", listOf(item("b", addedAt = 20)), 20).first
        assertEquals(10L, TripRules.newAnchor(open.list("l1")!!), "the open trip's start")
        assertTrue(TripRules.isNew(open.list("l1")!!, open.item("b")))
        assertFalse(TripRules.isNew(open.list("l1")!!, open.item("a")))

        val closed = open.newTrip("l1", NewTripMode.UNCHECK_ALL, 30).first
        assertEquals(30L, TripRules.newAnchor(closed.list("l1")!!), "else lastClosedAt")
        assertFalse(TripRules.isNew(closed.list("l1")!!, closed.item("b")))
        val fresh = closed.addItems("l1", listOf(item("c", addedAt = 40)), 40).first
        assertTrue(TripRules.isNew(fresh.list("l1")!!, fresh.item("c")))

        val fixture = SaturdayShop.state().list(LIST_ID)!!
        assertEquals(setOf("baking-powder", "eggs", "butter"), fixture.items.filter { TripRules.isNew(fixture, it) }.map { it.id }.toSet())
    }

    @Test
    fun deleteListFilesBook() {
        val s = state(item("a"), item("b")).toggleChecked("l2", "p1", 5).printReceipt("l2", Receipt(6, "SAT 3 OCT", "HILLCREST", 1, 1, null, 0))
        val (after, book) = s.deleteList("l2", 9)
        assertEquals(listOf("l1"), after.lists.map { it.id })
        assertNotNull(book)
        assertEquals("t-l2-5", book.id)
        assertEquals("Party", book.listTitle)
        assertEquals(9, book.closedAt)
        assertNotNull(book.receipt, "a printed receipt is filed as saved")
        val (noBook, nothing) = state(item("a")).deleteList("l2", 9)
        assertNull(nothing, "a list without stickers files nothing")
        assertEquals(1, noBook.lists.size)
        val single = AppState(lists = listOf(GroceryList("only", "Only", 0, listOf(item("x", checked = true)))), currentListId = "only")
        assertEquals(single to null, single.deleteList("only", 9), "the last list is never deleted")
    }

    @Test
    fun receiptPetAndNapOnAnOpenTrip() {
        val s = state(item("a"), item("b")).toggleChecked("l1", "a", 5)
        val receipt = Receipt(9, "SAT 3 OCT", "HILLCREST", 1, 1, 1, 0)
        assertTrue(s.hatchPet("l1").trip()!!.pet)
        assertTrue(s.dismissNap("l1").trip()!!.napDismissed)
        val printed = s.printReceipt("l1", receipt)
        assertEquals(receipt, printed.trip()!!.receipt)
        assertEquals(receipt, printed.printReceipt("l1", receipt.copy(stickers = 2)).trip()!!.receipt, "frozen at the first print")
        assertSame(s, s.saveReceipt("l1"), "nothing printed yet")
        assertTrue(printed.saveReceipt("l1").trip()!!.receiptSaved)
    }

    @Test
    fun tripExtrasNeedATrip() {
        val s = state(item("a"))
        val bonus = BookSticker(-1, "Croissants", IconRef("croissant"), Rarity.SHINY, 1)
        assertSame(s, s.hatchPet("l1"))
        assertSame(s, s.dismissNap("l1"))
        assertSame(s, s.saveReceipt("l1"))
        assertSame(s, s.setTripBonus("l1", bonus))
        assertSame(s, s.markFinaleShown("l1", 9))
        assertSame(s, s.printReceipt("l1", Receipt(9, "SAT 3 OCT", "HILLCREST", 1, 1, 1, 0)))
        val open = s.toggleChecked("l1", "a", 1)
        val withBonus = open.setTripBonus("l1", bonus)
        assertEquals(bonus, withBonus.trip()!!.bonus)
        assertSame(withBonus, withBonus.setTripBonus("l1", bonus.copy(name = "Other")), "the first bonus stays")
    }
}

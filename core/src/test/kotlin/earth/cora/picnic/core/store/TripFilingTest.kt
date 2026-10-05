package earth.cora.picnic.core.store

import earth.cora.picnic.core.SaturdayShop
import earth.cora.picnic.core.SaturdayShop.LIST_ID
import earth.cora.picnic.core.SaturdayShop.NOW
import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.TripRules
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.newList
import earth.cora.picnic.core.model.toggleChecked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The two-file operations in the §7.2 write order (checklist 135, 138, 144, 315). */
@OptIn(ExperimentalCoroutinesApi::class)
class TripFilingTest {
    private val catalog = TestSupport.catalog
    private val day = 24L * 60 * 60 * 1000

    /** Records the order of durable writes across both stores. */
    private class Journal {
        val events = mutableListOf<String>()
    }

    private class JournalStateStore(initial: AppState, private val journal: Journal) : StateStore {
        private val delegate = InMemoryStateStore(initial)
        override val states: Flow<AppState> = delegate.states
        override suspend fun current(): AppState = delegate.current()
        override suspend fun update(transform: (AppState) -> AppState): AppState {
            val before = delegate.current()
            val after = delegate.update(transform)
            if (after !== before) journal.events += "state"
            return after
        }
    }

    private class JournalAlbumStore(private val journal: Journal, var failFlush: Boolean = false) : AlbumStore {
        private val delegate = InMemoryAlbumStore()
        var durable: Album = Album()
        override val album: Flow<Album> = delegate.album
        override suspend fun current(): Album = delegate.current()
        override suspend fun update(transform: (Album) -> Album): Album = delegate.update(transform)
        override suspend fun flush() {
            if (failFlush) throw IOException("disk full")
            val now = delegate.current()
            if (now != durable) {
                durable = now
                journal.events += "album"
            }
        }
        override suspend fun referencedCustomIconIds(): Set<String> = delegate.current().customIconIds()
    }

    @Test
    fun albumIsWrittenBeforeState() = runTest {
        val journal = Journal()
        val state = JournalStateStore(SaturdayShop.state(), journal)
        val album = JournalAlbumStore(journal)
        val book = TripFiling.newTrip(state, album, LIST_ID, NewTripMode.CLEAR_GOTTEN, NOW + 1)
        assertNotNull(book)
        assertEquals(listOf("album", "state"), journal.events, "New trip writes the album first")
        assertEquals(listOf(book), album.durable.books)
        assertNull(state.current().list(LIST_ID)!!.trip)
        assertNull(TripFiling.newTrip(state, album, LIST_ID, NewTripMode.CLEAR_GOTTEN, NOW + 2), "no trip: nothing to file")
        assertEquals(listOf("album", "state"), journal.events)
    }

    @Test
    fun aFailedAlbumWriteLeavesTheStateAlone() = runTest {
        val journal = Journal()
        val state = JournalStateStore(SaturdayShop.state(), journal)
        val album = JournalAlbumStore(journal, failFlush = true)
        assertFailsWith<IOException> { TripFiling.newTrip(state, album, LIST_ID, NewTripMode.CLEAR_GOTTEN, NOW + 1) }
        assertTrue(journal.events.isEmpty())
        assertNotNull(state.current().list(LIST_ID)!!.trip, "the trip stays open: the book is never lost")
    }

    @Test
    fun deleteListFilesItsBookFirst() = runTest {
        val journal = Journal()
        val start = SaturdayShop.state().newList("other", "Other", NOW)
        val state = JournalStateStore(start, journal)
        val album = JournalAlbumStore(journal)
        val book = TripFiling.deleteList(state, album, LIST_ID, NOW + 1)
        assertEquals("Saturday shop", book!!.listTitle)
        assertEquals(listOf("album", "state"), journal.events)
        assertEquals(listOf("other"), state.current().lists.map { it.id })
    }

    @Test
    fun toggleCollectsTheSticker() = runTest {
        val state = InMemoryStateStore(SaturdayShop.unchecked())
        val album = InMemoryAlbumStore()
        val result = TripFiling.toggle(state, album, catalog, LIST_ID, "bananas", NOW)
        assertNull(result.autoNewBook)
        assertTrue(state.current().findItem("bananas")!!.checked)
        assertEquals(1, album.current().collected.getValue("banana").count)
        TripFiling.toggle(state, album, catalog, LIST_ID, "carrots", NOW + 1)
        TripFiling.toggle(state, album, catalog, LIST_ID, "bananas", NOW + 2) // un-check
        TripFiling.toggle(state, album, catalog, LIST_ID, "bananas", NOW + 3) // re-check on the same trip
        assertEquals("t-$LIST_ID-$NOW", state.current().list(LIST_ID)!!.trip!!.id)
        assertEquals(1, album.current().collected.getValue("banana").count, "never counted twice on one trip")
        assertEquals(1, album.current().collected.getValue("carrot").count)
    }

    @Test
    fun autoNewBookAfterIdleFilesFirstAndUndoTakesItBack() = runTest {
        val journal = Journal()
        val fixture = SaturdayShop.state()
        val state = JournalStateStore(fixture, journal)
        val album = JournalAlbumStore(journal)
        album.update { AlbumReconciler.reconcile(fixture, it, catalog, NOW) }
        album.flush()
        journal.events.clear()
        val countedBefore = album.current().collected

        val nextWeek = NOW + 7 * day
        val toggle = TripFiling.toggle(state, album, catalog, LIST_ID, "milk", nextWeek)
        val auto = assertNotNull(toggle.autoNewBook)
        assertEquals(listOf("album", "state"), journal.events, "the old book is filed before the state changes")
        assertEquals(fixture.list(LIST_ID)!!.trip!!.id, auto.book.id)
        assertEquals(1, album.current().finishedTrips)
        val list = state.current().list(LIST_ID)!!
        assertEquals(14, list.items.size, "what was got last time is cleared")
        assertEquals("t-$LIST_ID-$nextWeek", list.trip!!.id)
        assertEquals(0, list.items.single { it.id == "milk" }.bookSlot)
        assertEquals(1, album.current().collected.getValue("milk_bag").count)

        TripFiling.undoNewBook(state, album, toggle, nextWeek + 1000)
        val restored = state.current().list(LIST_ID)!!
        assertEquals(fixture.list(LIST_ID)!!.trip!!.id, restored.trip!!.id, "the old book is back")
        assertEquals(24, restored.items.size)
        assertEquals(11, restored.items.count { it.checked }, "and the tapped item is checked into it")
        assertEquals(10, restored.items.single { it.id == "milk" }.bookSlot, "the next spot of the old book")
        assertTrue(album.current().books.isEmpty(), "the book left the album")
        assertEquals(0, album.current().finishedTrips)
        assertEquals(countedBefore.getValue("banana"), album.current().collected.getValue("banana"))
        assertEquals(listOf("album", "state", "state", "album"), journal.events, "undo: the state first, then the album")
        val list2 = state.current().list(LIST_ID)!!
        assertSame(list2, TripRules.reconcile(list2, nextWeek + 2000, SaturdayShop.STORE), "the restored list satisfies the invariant")
    }

    /**
     * §0.4 Q9 (item 315): the automatic new book's Undo after a New trip changes nothing. The album keeps the newer
     * filing and its counters; the state keeps the fresh list.
     */
    @Test
    fun aLateUndoAfterNewTripChangesNothing() = runTest {
        val state = InMemoryStateStore(SaturdayShop.state())
        val album = InMemoryAlbumStore()
        val nextWeek = NOW + 7 * day
        val toggle = TripFiling.toggle(state, album, catalog, LIST_ID, "milk", nextWeek)
        assertNotNull(toggle.autoNewBook)
        TripFiling.newTrip(state, album, LIST_ID, NewTripMode.CLEAR_GOTTEN, nextWeek + 1000)
        val filed = album.current()
        val afterNewTrip = state.current()
        assertEquals(2, filed.finishedTrips)
        assertFalse(TripFiling.undoNewBook(state, album, toggle, nextWeek + 2000))
        assertSame(afterNewTrip, state.current(), "the state is untouched")
        assertEquals(filed, album.current(), "the album keeps both books and its counters")
    }

    /**
     * The add-side twin (§0.4 Q9, item 315): the first add more than 12 h after a finished shop files its book (PERFECT,
     * the album first) before the add is saved; [TripFiling.addItems]'s then runs in the same update; Undo takes it back.
     */
    @Test
    fun addAfterIdleFilesFirstAndUndoTakesItBack() = runTest {
        val journal = Journal()
        val done = SaturdayShop.state().let { s ->
            val left = s.list(LIST_ID)!!.items.filter { !it.checked }
            left.foldIndexed(s) { i, acc, item -> acc.toggleChecked(LIST_ID, item.id, NOW + i * 1000L) }
        }
        val finished = done.copy(lists = done.lists.map { l -> l.copy(trip = l.trip!!.copy(finaleShownAt = NOW + 60_000)) })
        val state = JournalStateStore(finished, journal)
        val album = JournalAlbumStore(journal)
        val tuesday = NOW + 3 * day
        val milk = GroceryItem(id = "milk-2", name = "Milk, 2 %", icon = "milk_bag", addedAt = tuesday)
        var seen: Int? = null
        val add = TripFiling.addItems(state, album, LIST_ID, listOf(milk), tuesday) { before, after, outcome ->
            seen = before.list(LIST_ID)!!.items.size
            assertEquals(listOf("milk-2"), outcome.added)
            after
        }
        assertEquals(24, seen, "then sees the state before the add")
        val toggle = assertNotNull(add.newBook)
        assertEquals(listOf("album", "state"), journal.events, "the old book is filed before the state changes")
        assertTrue(toggle.autoNewBook!!.book.perfect)
        assertEquals(24, toggle.autoNewBook!!.book.total)
        assertEquals(1, album.current().perfectStreak)
        assertEquals(listOf("milk-2"), state.current().list(LIST_ID)!!.items.map { it.id }, "what was got is cleared; milk is a new row")
        assertNull(state.current().list(LIST_ID)!!.trip)

        assertTrue(TripFiling.undoNewBook(state, album, toggle, tuesday + 1000))
        val restored = state.current().list(LIST_ID)!!
        assertEquals(25, restored.items.size)
        assertEquals(finished.list(LIST_ID)!!.trip!!.id, restored.trip!!.id)
        assertTrue(TripRules.bookComplete(restored), "the new row stays out of the finished book")
        assertTrue(album.current().books.isEmpty())
        assertEquals(0, album.current().perfectStreak)
    }

    @Test
    fun launchAlbumSyncCollectsAfterEveryState() = runTest {
        val start = AppState(
            lists = listOf(GroceryList("l", "List", 0, listOf(GroceryItem(id = "a", name = "Lemons", icon = "lemon", addedAt = 0)))),
            currentListId = "l",
            store = StoreProfile(name = "S"),
        )
        val state = JsonFileStateStore(java.nio.file.Files.createTempFile("state", ".json").toFile().also { it.delete() }, { start }, Dispatchers.Unconfined)
        val album = InMemoryAlbumStore()
        val job = TripFiling.launchAlbumSync(backgroundScope, state, album, catalog) { 5 }
        runCurrent()
        state.update { it.toggleChecked("l", "a", 3) }
        runCurrent()
        assertEquals(setOf("lemon"), album.current().collected.keys)
        val counted = album.current()
        state.update { it.toggleChecked("l", "a", 4) }
        runCurrent()
        assertEquals(1, album.current().collected.getValue("lemon").count, "collected stays collected")
        assertTrue(album.current().tally.isEmpty(), "the closed trip's tally goes")
        assertEquals(counted.collected, album.current().collected)
        job.cancel()
    }
}

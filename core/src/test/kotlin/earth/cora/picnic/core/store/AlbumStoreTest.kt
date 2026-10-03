package earth.cora.picnic.core.store

import earth.cora.picnic.core.CoreJson
import earth.cora.picnic.core.SaturdayShop
import earth.cora.picnic.core.SaturdayShop.LIST_ID
import earth.cora.picnic.core.SaturdayShop.NOW
import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.ResolvedIcon
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.applyIconDecisions
import earth.cora.picnic.core.model.attachCustomIcon
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.newTrip
import earth.cora.picnic.core.model.toggleChecked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** album.json (docs/DESIGN_STICKER.md §7.2, §7.8; checklist 138, 232). */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumStoreTest {
    private val dir: File = Files.createTempDirectory("picnic-album").toFile()
    private val file = File(dir, "album.json")
    private val catalog = TestSupport.catalog

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** A store whose coalesced writes run on the test scheduler (virtual time). */
    private fun TestScope.store(scope: CoroutineScope = backgroundScope) =
        JsonFileAlbumStore(file, scope = scope, ioDispatcher = StandardTestDispatcher(testScheduler))

    private fun onDisk(): Album = CoreJson.json.decodeFromString(Album.serializer(), file.readText())

    private fun item(id: String, icon: String = "lemon", fit: IconFit = IconFit.EXACT, custom: String? = null, rarity: Rarity = Rarity.NONE) =
        GroceryItem(id = id, name = id.replaceFirstChar { it.uppercase() }, icon = icon, iconFit = fit, customIconId = custom, addedAt = 0, rarity = rarity)

    private fun book(id: String, stickers: Int = 1, perfect: Boolean = false) = TripBook(
        id = id, listTitle = "Saturday shop", storeName = "Hillcrest Market", startedAt = 1, completedAt = null, closedAt = 2,
        total = 3, minutes = null, stickers = List(stickers) { BookSticker(it, "Lemons", IconRef("lemon"), Rarity.NONE, 1) },
        stamps = emptyList(), perfect = perfect, receipt = null,
    )

    @Test
    fun writesAtomicallyAndCoalescesBursts() = runTest {
        val s = store()
        assertEquals(Album(), s.current())
        assertFalse(file.exists(), "nothing is written until something changes")
        s.update { it.unlockSheet("sakura") }
        s.update { it.unlockSheet("sheet2") }
        s.update { it.unlockSheet("thanksgiving-2026") }
        assertEquals(setOf("sakura", "sheet2", "thanksgiving-2026"), s.current().sheets, "in memory at once")
        assertEquals(s.current(), s.album.first())
        runCurrent()
        assertFalse(file.exists(), "inside the coalesce window nothing is on disk yet")
        advanceTimeBy(JsonFileAlbumStore.COALESCE_MILLIS + 1)
        runCurrent()
        assertEquals(setOf("sakura", "sheet2", "thanksgiving-2026"), onDisk().sheets, "one write for the whole burst")
        assertFalse(File(dir, "album.json.tmp").exists(), "the temp file was moved into place")
        assertEquals(s.current(), store().current(), "a new store reads it back")
    }

    @Test
    fun flushWritesNow() = runTest {
        val s = store()
        s.update { it.copy(finishedTrips = 3) }
        s.flush()
        assertEquals(3, onDisk().finishedTrips)
        s.flush() // nothing pending: no error, no write
        assertSame(s.current(), s.update { it })
    }

    @Test
    fun recoversFromACorruptFile() = runTest {
        file.writeText("{ not json")
        val s = store()
        assertEquals(Album(), s.current())
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("album.json.corrupt-") })
        s.update { it.unlockSheet("sakura") }
        s.flush()
        assertEquals(setOf("sakura"), onDisk().sheets)
    }

    @Test
    fun aFailedFlushThrowsAndStaysPending() = runTest {
        val blocker = File(dir, "blocker").apply { writeText("x") }
        val s = JsonFileAlbumStore(File(blocker, "album.json"), scope = backgroundScope, ioDispatcher = StandardTestDispatcher(testScheduler))
        s.update { it.unlockSheet("sakura") }
        assertFailsWith<IOException> { s.flush() }
        advanceTimeBy(JsonFileAlbumStore.COALESCE_MILLIS + 1)
        runCurrent() // the background write fails quietly
        assertEquals(setOf("sakura"), s.current().sheets)
    }

    @Test
    fun fileTwiceIsNoOp() {
        val once = Album().file(book("t-1", perfect = true))
        assertEquals(1, once.finishedTrips)
        assertEquals(1, once.perfectStreak)
        assertSame(once, once.file(book("t-1", perfect = true)), "filing an id that is already filed is a no-op")
        val replaced = once.file(book("t-1", stickers = 2, perfect = true))
        assertEquals(2, replaced.books.single().stickers.size, "a fuller snapshot under the same id replaces it")
        assertEquals(1, replaced.finishedTrips, "without counting twice")
    }

    @Test
    fun filingCountsFinishedTripsAndThePerfectStreak() {
        var a = Album()
        a = a.file(book("t-1", perfect = true)).file(book("t-2", perfect = true)).file(book("t-3", perfect = true))
        assertEquals(3, a.perfectStreak)
        a = a.file(book("t-4", perfect = false))
        assertEquals(0, a.perfectStreak, "a finished-but-incomplete trip resets the streak")
        a = a.file(book("t-5", stickers = 0, perfect = false))
        assertEquals(0, a.perfectStreak)
        assertEquals(4, a.finishedTrips, "a book without stickers is not a finished trip")
        assertEquals(listOf("t-5", "t-4", "t-3", "t-2", "t-1"), a.books.map { it.id }, "newest first")
        val full = (1..Album.MAX_BOOKS + 5).fold(Album()) { acc, i -> acc.file(book("t-$i")) }
        assertEquals(Album.MAX_BOOKS, full.books.size)
        assertEquals("t-${Album.MAX_BOOKS + 5}", full.books.first().id)
        assertEquals("t-6", full.books.last().id, "the oldest drop off")
    }

    @Test
    fun unfileRestoresTheCounters() {
        val before = Album().file(book("t-1", perfect = true)).file(book("t-2", perfect = true))
            .collect(item("lemon").copy(checked = true, checkedAt = 5), "t-3", catalog, 5)
        val filed = before.file(book("t-3", perfect = false))
        assertEquals(0, filed.perfectStreak)
        assertTrue(filed.tally.none { it.startsWith("t-3|") })
        val undone = filed.unfile(book("t-3"), before)
        assertEquals(listOf("t-2", "t-1"), undone.books.map { it.id })
        assertEquals(2, undone.perfectStreak)
        assertEquals(2, undone.finishedTrips)
        assertEquals(before.tally, undone.tally, "its stickers stay counted for the trip that is open again")
        assertSame(undone, undone.unfile(book("t-3"), before))
    }

    @Test
    fun collectOnCheck() {
        val lemon = item("lemon").copy(checked = true, checkedAt = 42)
        val a = Album().collect(lemon, "t-1", catalog, 99)
        assertEquals(Collected(name = "Lemon", firstAt = 42, count = 1), a.collected["lemon"])
        assertSame(a, a.collect(lemon, "t-1", catalog, 100), "un-check and re-check on the same trip never counts twice")
        val nextTrip = a.collect(lemon, "t-2", catalog, 200)
        assertEquals(2, nextTrip.collected.getValue("lemon").count, "bought again on another trip")
        assertEquals(42, nextTrip.collected.getValue("lemon").firstAt)
        val shiny = nextTrip.collect(item("maple", icon = "maple_syrup", rarity = Rarity.SHINY).copy(checked = true), "t-2", catalog, 300)
        assertTrue(shiny.collected.getValue("maple_syrup").holo, "shiny stickers fill a holo pocket")
        assertSame(a, a.collect(lemon.copy(checked = false), "t-1", catalog, 1), "only checked items")
    }

    @Test
    fun collectsOnlyBought() {
        val pending = item("gochujang", icon = "paper_bag", fit = IconFit.PENDING).copy(checked = true)
        val approximate = item("nooch", icon = "condiments_generic", fit = IconFit.APPROXIMATE).copy(checked = true)
        val generic = item("bread", icon = "bakery_generic").copy(checked = true)
        var a = Album().collect(pending, "t-1", catalog, 1).collect(approximate, "t-1", catalog, 1).collect(generic, "t-1", catalog, 1)
        assertTrue(a.collected.isEmpty(), "the capsule and nearest-neighbour stickers are not collected")

        // Resolution later: the PENDING item resolves EXACT, the APPROXIMATE one gets its live sticker. Both are
        // collected then, through the reconciler that runs after every committed state.
        val state = AppState(
            lists = listOf(GroceryList("l", "List", 0, listOf(pending.copy(checked = false), approximate.copy(checked = false)))),
            currentListId = "l",
        ).toggleChecked("l", "gochujang", 10).toggleChecked("l", "nooch", 11)
        val trip = state.list("l")!!.trip!!.id
        a = AlbumReconciler.reconcile(state, a, catalog, 12)
        assertTrue(a.collected.isEmpty())
        val resolved = state.applyIconDecisions(mapOf("gochujang" to ResolvedIcon("hot_sauce", IconFit.EXACT))).attachCustomIcon("nooch", "c-77", overwrite = false)
        a = AlbumReconciler.reconcile(resolved, a, catalog, 13)
        assertEquals(setOf("hot_sauce", "c:c-77"), a.collected.keys)
        assertEquals(10, a.collected.getValue("hot_sauce").firstAt, "first collected at the check-off")
        assertSame(a, AlbumReconciler.reconcile(resolved, a, catalog, 14), "idempotent")
        assertTrue(a.tally.all { it.startsWith("$trip|") })

        // A redraw mints a new custom id: collected again; the old one stays collected.
        val redrawn = resolved.attachCustomIcon("nooch", "c-78", overwrite = true)
        a = AlbumReconciler.reconcile(redrawn, a, catalog, 15)
        assertEquals(setOf("hot_sauce", "c:c-77", "c:c-78"), a.collected.keys)
        assertEquals(setOf("c-77", "c-78"), a.customIconIds())

        // Un-check does not uncollect.
        a = AlbumReconciler.reconcile(redrawn.toggleChecked("l", "gochujang", 16), a, catalog, 16)
        assertTrue("hot_sauce" in a.collected)
    }

    @Test
    fun crashInsideCoalesceWindow() = runTest {
        // The check-off was saved to state.json, the collect was still waiting in the coalesce window, the process died.
        val state = SaturdayShop.state()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val first = store(scope)
        first.update { AlbumReconciler.reconcile(state, it, catalog, NOW) }
        assertEquals(10, first.current().collected.size, "the ten stickers of the fixture")
        runCurrent()
        scope.cancel() // process death before the write
        advanceTimeBy(JsonFileAlbumStore.COALESCE_MILLIS * 2)
        assertFalse(file.exists())

        // Next start: the album is empty on disk; the reconciler collects every checked item of the open trip again.
        val second = store()
        assertTrue(second.current().collected.isEmpty())
        val repaired = second.update { AlbumReconciler.reconcile(state, it, catalog, NOW + 1) }
        assertEquals(first.current().collected, repaired.collected)
        assertSame(repaired, second.update { AlbumReconciler.reconcile(state, it, catalog, NOW + 2) }, "nothing counts twice")
    }

    @Test
    fun crashBetweenAlbumAndState() = runTest {
        // New trip: the book went to album.json (flushed), then the process died before state.json was written.
        val state = SaturdayShop.state()
        val synced = store()
        synced.update { AlbumReconciler.reconcile(state, it, catalog, NOW) }
        val (_, book) = state.newTrip(LIST_ID, NewTripMode.CLEAR_GOTTEN, NOW + 1)
        synced.file(assertNotNull(book))
        assertEquals(book, onDisk().books.single())

        // Next start: state.json still has the trip open. The reconciler skips the filed trip (its stickers were
        // counted when it was filed), and New trip again files nothing twice.
        val restarted = store()
        val afterStart = restarted.update { AlbumReconciler.reconcile(state, it, catalog, NOW + 2) }
        assertEquals(1, afterStart.collected.getValue("banana").count, "not counted again")
        assertEquals(1, afterStart.finishedTrips)
        val (_, again) = state.newTrip(LIST_ID, NewTripMode.CLEAR_GOTTEN, NOW + 3)
        restarted.file(again!!)
        assertEquals(1, restarted.current().books.size)
        assertEquals(1, restarted.current().finishedTrips)
        assertEquals(NOW + 3, onDisk().books.single().closedAt, "the later snapshot replaced the first: nothing lost")

        // The other order cannot happen (the album is flushed first); were state.json written alone, the book would be
        // lost, which TripFilingTest.albumIsWrittenBeforeState rules out.
    }

    @Test
    fun reconcileDropsTalliesOfClosedTrips() {
        val state = AppState(
            lists = listOf(GroceryList("l", "List", 0, listOf(item("lemon"), item("lime")))),
            currentListId = "l",
            store = StoreProfile(name = "S"),
        ).toggleChecked("l", "lemon", 1)
        val a = AlbumReconciler.reconcile(state, Album(), catalog, 2)
        assertEquals(1, a.tally.size)
        val closed = state.toggleChecked("l", "lemon", 3)
        assertNull(closed.list("l")!!.trip)
        val pruned = AlbumReconciler.reconcile(closed, a, catalog, 4)
        assertTrue(pruned.tally.isEmpty())
        assertTrue("lemon" in pruned.collected, "collected stays collected")
    }

    @Test
    fun referencedCustomIconIds() = runTest {
        val sticker = BookSticker(0, "Gochujang", IconRef("condiments_generic", "c-123"), Rarity.NONE, 1)
        val withBook = book("t-1").copy(stickers = listOf(sticker), bonus = sticker.copy(icon = IconRef("cookie", "c-bonus")))
        val album = Album(collected = mapOf("c:c-456" to Collected("Kimchi", 1, 1), "lemon" to Collected("Lemons", 1, 2)), books = listOf(withBook))
        assertEquals(setOf("c-123", "c-456", "c-bonus"), InMemoryAlbumStore(album).referencedCustomIconIds())
        val s = store()
        s.update { album }
        assertEquals(setOf("c-123", "c-456", "c-bonus"), s.referencedCustomIconIds())
    }

    @Test
    fun bonusPicnicAndSeen() {
        val a = Album().addHolo("croissant", "Croissants", 5)
        assertEquals(Collected("Croissants", 5, 1, holo = true), a.collected["croissant"])
        assertSame(a, a.addHolo("croissant", "Croissants", 6))
        val withLemon = Album().collect(item("lemon").copy(checked = true, checkedAt = 1), "t", catalog, 1).addHolo("lemon", "Lemon", 9)
        assertEquals(Collected("Lemon", 1, 1, holo = true), withLemon.collected["lemon"], "an existing pocket turns holo")
        assertEquals(listOf("blanket"), Album().addPicnic("blanket").addPicnic("blanket").picnic)
        val seen = a.markSeen(listOf("croissant", "nope"))
        assertTrue(seen.collected.getValue("croissant").seen)
        assertSame(seen, seen.markSeen(listOf("croissant")))
    }

    @Test
    fun inMemoryStore() = runTest {
        val s = InMemoryAlbumStore()
        val updated = s.update { it.copy(finishedTrips = 1) }
        assertEquals(1, s.current().finishedTrips)
        assertSame(updated, s.current())
        s.flush()
        assertEquals(updated, s.album.first())
    }
}

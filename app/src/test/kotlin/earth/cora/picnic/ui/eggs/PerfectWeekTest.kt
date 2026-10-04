package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.BookSticker
import earth.cora.picnic.core.store.TripBook
import earth.cora.picnic.core.store.file
import earth.cora.picnic.core.store.unfile
import earth.cora.picnic.ui.album.PerfectWeeksCard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 253 (egg 14): four trips in a row filed complete (across lists, in filing order; a finished-but-incomplete one
 * resets the streak) make every book's stamp page holo for 7 days (`perfectUntil`) and unlock the sakura sheet; ten
 * finished trips (filed with at least one sticker) open the back pocket ("sticker sheet 2 of 2") with its toast.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class PerfectWeekTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = 24L * 60 * 60 * 1000

    private fun book(n: Int, list: String, perfect: Boolean, stickers: Int = 3, at: Long = PracticeFixture.NOW + n * day) = TripBook(
        id = "t-$list-$n",
        listTitle = list,
        storeName = PracticeFixture.STORE,
        startedAt = at - 3_600_000,
        completedAt = if (perfect) at - 60_000 else null,
        closedAt = at,
        total = stickers + if (perfect) 0 else 2,
        minutes = 30,
        stickers = (0 until stickers).map { BookSticker(it, "Item $it", IconRef("apple"), Rarity.NONE, at - 60_000) },
        stamps = emptyList(),
        perfect = perfect,
        receipt = null,
    )

    private fun Album.fileAll(vararg books: TripBook): Album = books.fold(this) { a, b -> EggRules.perfectWeek(a.file(b)) }

    @Test
    fun fourCompleteTripsInARowAcrossLists() {
        // Saturday shop, the cottage list, Saturday shop, the cottage list: four complete trips in filing order.
        val a = Album().fileAll(book(1, "saturday", true), book(2, "cottage", true), book(3, "saturday", true))
        assertEquals(3, a.perfectStreak)
        assertNull(a.perfectUntil)
        assertFalse(EggRules.SAKURA_SHEET in a.sheets)
        val fourth = book(4, "cottage", true)
        val b = a.fileAll(fourth)
        assertEquals(4, b.perfectStreak)
        assertEquals("holo for 7 days from the fourth filing", fourth.closedAt + EggRules.PERFECT_WEEK_MS, b.perfectUntil)
        assertTrue(EggRules.SAKURA_SHEET in b.sheets)
        // Idempotent: looking again grants nothing more.
        assertEquals(b, EggRules.perfectWeek(b))
    }

    @Test
    fun anIncompleteTripResetsTheStreak() {
        val a = Album().fileAll(book(1, "s", true), book(2, "s", true), book(3, "s", true), book(4, "s", false))
        assertEquals(0, a.perfectStreak)
        val b = a.fileAll(book(5, "s", true), book(6, "s", true), book(7, "s", true))
        assertEquals(3, b.perfectStreak)
        assertNull(b.perfectUntil)
        // A book with no sticker is not a finished trip: it neither counts nor resets.
        val c = b.fileAll(book(8, "s", false, stickers = 0))
        assertEquals(3, c.perfectStreak)
        val d = c.fileAll(book(9, "s", true))
        assertEquals(4, d.perfectStreak)
        assertTrue(EggRules.SAKURA_SHEET in d.sheets)
    }

    @Test
    fun theNextFourGrantAnotherWeek() {
        val four = Album().fileAll(book(1, "s", true), book(2, "s", true), book(3, "s", true), book(4, "s", true))
        val first = four.perfectUntil!!
        val eight = four.fileAll(book(5, "s", true), book(6, "s", true), book(7, "s", true))
        assertEquals("not at 7", first, eight.perfectUntil)
        val more = eight.fileAll(book(8, "s", true))
        assertEquals(book(8, "s", true).closedAt + EggRules.PERFECT_WEEK_MS, more.perfectUntil)
    }

    @Test
    fun tenFinishedTripsOpenTheBackPocket() {
        val nine = (1..9).fold(Album()) { a, n -> a.fileAll(book(n, "s", n % 3 == 0)) }
        assertEquals(9, nine.finishedTrips)
        assertFalse(nine.backPocketOpen)
        val ten = nine.fileAll(book(10, "s", false))
        assertTrue(ten.backPocketOpen)
        assertTrue(EggRules.SHEET_2 in ten.sheets)
    }

    /**
     * §0.4 Q9 × egg 14: the automatic new book's Undo takes the filed book back out (Album.unfile). What that filing
     * granted goes with it: the perfect week and the sakura sheet (the streak is 3 again), and the back pocket and
     * sheet 2 when it was the tenth finished trip.
     */
    @Test
    fun undoingTheFilingTakesBackWhatItGranted() {
        val three = Album().fileAll(book(1, "s", true), book(2, "s", true), book(3, "s", true))
        val fourth = book(4, "s", true)
        val four = three.fileAll(fourth)
        assertTrue(EggRules.SAKURA_SHEET in four.sheets)
        val undone = EggRules.perfectWeek(four.unfile(fourth, before = three))
        assertEquals(3, undone.perfectStreak)
        assertNull("the week goes with the book", undone.perfectUntil)
        assertFalse(EggRules.SAKURA_SHEET in undone.sheets)
        assertEquals(EggRules.perfectWeek(three), undone)

        // An earlier week stays: eight in a row, the eighth taken back, the fourth's week remains.
        val seven = four.fileAll(book(5, "s", true), book(6, "s", true), book(7, "s", true))
        val eighth = book(8, "s", true)
        val back = EggRules.perfectWeek(seven.fileAll(eighth).unfile(eighth, before = seven))
        assertEquals(fourth.closedAt + EggRules.PERFECT_WEEK_MS, back.perfectUntil)
        assertTrue(EggRules.SAKURA_SHEET in back.sheets)

        // The tenth finished trip opened the back pocket; taking it back closes it.
        val nine = (1..9).fold(Album()) { a, n -> a.fileAll(book(n, "p", false)) }
        val tenth = book(10, "p", false)
        val ten = nine.fileAll(tenth)
        assertTrue(ten.backPocketOpen)
        val shut = EggRules.perfectWeek(ten.unfile(tenth, before = nine))
        assertFalse(shut.backPocketOpen)
        assertFalse(EggRules.SHEET_2 in shut.sheets)
    }

    @Test
    fun theEngineTakesBackTheWeekWhenTheBookIsTakenBack() {
        val e = EngineRig()
        try {
            val books = (1..4).map { n -> book(n, "s", true, at = PracticeFixture.NOW - (5 - n) * day) }
            val three = books.take(3).fold(Album()) { a, b -> a.file(b) }
            runBlocking { e.albumStore.update { three.file(books[3]) } }
            assertTrue(e.eggs.perfectWeek)
            runBlocking { e.albumStore.update { it.unfile(books[3], before = three) } }
            assertFalse("the stamp pages are not holo any more", e.eggs.perfectWeek)
            assertFalse(EggRules.SAKURA_SHEET in runBlocking { e.albumStore.current() }.sheets)
        } finally {
            e.close()
        }
    }

    @Test
    fun theEngineAppliesItAndToastsTheBackPocket() {
        val e = EngineRig()
        try {
            runBlocking { e.albumStore.update { a -> (1..4).fold(a) { acc, n -> acc.file(book(n, "s", true, at = PracticeFixture.NOW - (5 - n) * day)) } } }
            val album = runBlocking { e.albumStore.current() }
            assertTrue(EggRules.SAKURA_SHEET in album.sheets)
            assertTrue("every book's stamp page is holo", e.eggs.perfectWeek)
            // Seven days after the fourth filing it is not holo any more.
            e.clock.advance(7 * day)
            e.engine.refresh()
            assertFalse(e.eggs.perfectWeek)
            // Ten finished trips: the back pocket opens, with its toast.
            assertTrue(e.toasts.isEmpty())
            runBlocking { e.albumStore.update { a -> (5..10).fold(a) { acc, n -> acc.file(book(n, "s", false)) } } }
            assertTrue(runBlocking { e.albumStore.current() }.backPocketOpen)
            assertEquals(listOf(EggToast.BackPocket), e.toasts)
        } finally {
            e.close()
        }
    }

    /**
     * Her tenth finished trip (and a fourth complete one in a row) filed while Easter eggs were off: turning them back on
     * opens the back pocket and the sakura sheet then, with the "Look" toast, not at some later filing (§12.2 egg 14).
     */
    @Test
    fun eggsOnAgainOpensThePocket() {
        val e = EngineRig(settings = earth.cora.picnic.settings.AppSettings(eggs = false, weather = false, sounds = false))
        try {
            runBlocking {
                e.albumStore.update { a ->
                    (1..10).fold(a) { acc, n -> acc.file(book(n, "s", n > 6, at = PracticeFixture.NOW - (11 - n) * day)) }
                }
            }
            val off = runBlocking { e.albumStore.current() }
            assertEquals(10, off.finishedTrips)
            assertFalse("eggs off: the pocket stays shut", off.backPocketOpen)
            assertFalse(EggRules.SAKURA_SHEET in off.sheets)
            assertTrue(e.toasts.isEmpty())
            runBlocking { e.settings.setSurprise(earth.cora.picnic.settings.Surprise.EasterEggs, true) }
            val on = runBlocking { e.albumStore.current() }
            assertTrue("eggs on again: the back pocket opens", on.backPocketOpen)
            assertTrue(EggRules.SHEET_2 in on.sheets)
            assertTrue("and the sakura sheet unlocks", EggRules.SAKURA_SHEET in on.sheets)
            assertEquals(listOf(EggToast.BackPocket), e.toasts)
            // Off and on again: nothing more to grant, no second toast.
            runBlocking { e.settings.setSurprise(earth.cora.picnic.settings.Surprise.EasterEggs, false) }
            runBlocking { e.settings.setSurprise(earth.cora.picnic.settings.Surprise.EasterEggs, true) }
            assertEquals(listOf(EggToast.BackPocket), e.toasts)
        } finally {
            e.close()
        }
    }

    @Test
    fun theLabFilesFourCompleteTrips() {
        val r = EggRig(compose)
        r.start {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { PracticeListScreen(r.sandbox) }
                val album by r.sandbox.album.album.collectAsState(initial = null)
                album?.let { a ->
                    PerfectWeeksCard(a.perfectStreak, earned = a.perfectStreak >= EggRules.PERFECT_WEEK_TRIPS, holo = (a.perfectUntil ?: 0L) > r.clock.now())
                }
            }
        }
        r.fire(14)
        r.advance(600)
        assertTrue(r.eggs.perfectWeek)
        compose.onNodeWithText("week 4 of 4 · sakura sheet unlocked").assertExists()
    }
}

package earth.cora.picnic.ui.list

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.core.model.TripRules
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.TOAST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The automatic new book on the real list (docs/DESIGN_STICKER.md §0.4 Q9, §7.1, §7.8; items 120, 162, 315): its Undo
 * note goes once a New trip or another list makes it moot, and a late Undo changes nothing; items she writes down after
 * a finished shop stay out of its book; the first add more than 12 h on files the book (PERFECT) and starts afresh,
 * with Undo.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class NewBookTest {
    @get:Rule
    val compose = createComposeRule()

    private val checkOffNote = "New sticker book for today"
    private val addNote = "Fresh sticker book"

    private fun album(rig: ListRig): Album = (rig.album.album as kotlinx.coroutines.flow.StateFlow<Album>).value

    private fun shows(text: String): Boolean =
        compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The first check-off 13 h on starts a new book; its note shows (after the nudge it retires). */
    private fun autoNewBook(rig: ListRig) {
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        compose.frames(5)
        compose.runOnIdle { rig.vm.toggle("baking-powder") }
        assertTrue("the new-book note shows", compose.framesUntil(3000) { shows(checkOffNote) })
        assertEquals(1, album(rig).books.size)
    }

    /** §6.5 with Q9: a New trip from the menu files the fresh book; the earlier book's Undo note lifts off at once. */
    @Test
    fun aNewTripRetiresTheNewBookUndo() {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        autoNewBook(rig)
        rig.now += ListFixture.MIN
        compose.runOnIdle { rig.vm.newTrip(NewTripMode.CLEAR_GOTTEN) }
        assertTrue("the Undo note lifts off at once", compose.framesUntil(600) { !shows(checkOffNote) })
        assertTrue(shows("New trip · a fresh sticker book"))
        assertEquals(2, album(rig).books.size)
        compose.frames(20)
        val state = rig.app.list(ListFixture.LIST_ID)!!
        val filed = album(rig)
        // A late Undo (a tap as the note lifted off) changes nothing: both books stay filed, the counters too.
        compose.runOnIdle { rig.vm.undoNewBook() }
        compose.frames(20)
        val after = rig.app.list(ListFixture.LIST_ID)!!
        assertNull("no trip came back", after.trip)
        assertEquals(state.items, after.items)
        assertEquals(state.lastClosedAt, after.lastClosedAt)
        assertEquals(filed, album(rig))
        assertEquals(2, album(rig).finishedTrips)
        compose.mainClock.autoAdvance = true
    }

    /** §6.4: another list on screen retires the last list's new-book note. */
    @Test
    fun aListSwitchRetiresTheNewBookUndo() {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        autoNewBook(rig)
        compose.runOnIdle { rig.vm.newList("Party") }
        assertTrue("the note lifts off", compose.framesUntil(600) { !shows(checkOffNote) })
        assertEquals("Party", rig.vm.state.value.title)
        compose.mainClock.autoAdvance = true
    }

    /**
     * §7.1, §7.8 (item 315): the evening after a finished shop she writes down what is out. The list has more to get
     * (the LCD says so), but the shop's book keeps its spots, its stamps and its PERFECT.
     */
    @Test
    fun addsAfterTheShopStayOutOfItsBook() {
        val rig = ListRig(ListFixture.done(), now = ListFixture.TRIP_START + 8 * ListFixture.HOUR)
        compose.show(rig)
        compose.runOnIdle {
            rig.addVm.setText("Salt")
            rig.addVm.addAsTyped()
        }
        compose.waitForIdle()
        val list = rig.app.list(ListFixture.LIST_ID)!!
        assertEquals(25, list.items.size)
        assertNotNull("the same trip", list.trip)
        assertTrue(album(rig).books.isEmpty())
        val ui = rig.vm.state.value
        val book = ui.book!!
        assertEquals(24, book.total)
        assertEquals(24, book.checked)
        assertTrue("PERFECT stays", book.perfect)
        assertTrue(book.full)
        assertTrue("every stamp stays stamped", book.stamps.all { it.stamped })
        assertEquals("the LCD counts the list", 25, ui.lcd.total)
        assertTrue(ui.tripBook!!.perfect)
        assertEquals(24, ui.tripBook!!.total)
    }

    /**
     * §0.4 Q9's add-side twin (item 315): Tuesday's first add files Saturday's book as it stands (PERFECT, the streak
     * counts it), clears what was got and starts afresh; the book turns to a fresh page and a note offers Undo, which
     * brings the finished book back with the new row kept out of it.
     */
    @Test
    fun theFirstAddAfterTwelveHoursFilesTheBook() {
        val rig = ListRig(ListFixture.done(), now = ListFixture.TRIP_START + 3 * 24 * ListFixture.HOUR)
        val old = rig.app.list(ListFixture.LIST_ID)!!.trip!!.id
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        compose.frames(5)
        compose.runOnIdle {
            rig.addVm.setText("Salt")
            rig.addVm.addAsTyped()
        }
        compose.frames(5)
        val list = rig.app.list(ListFixture.LIST_ID)!!
        assertEquals(listOf("Salt"), list.items.map { it.name })
        assertNull("the next book opens at the next check-off", list.trip)
        val filed = album(rig).books.single()
        assertEquals(old, filed.id)
        assertTrue(filed.perfect)
        assertEquals(24, filed.total)
        assertEquals(1, album(rig).perfectStreak)
        val ui = rig.vm.state.value
        assertEquals("the book turns to a fresh page", 1, ui.bookTurn)
        assertEquals(1, ui.book!!.total)
        assertTrue(ui.book!!.stickers.isEmpty())
        assertTrue("then its note, with Undo", compose.framesUntil(15_000) { shows(addNote) })
        assertTrue(shows("your last shop is in the album"))
        compose.onNode(hasText("Undo") and hasAnyAncestor(hasTestTag(TOAST_TAG)), useUnmergedTree = true).performClick()
        compose.frames(20)
        val back = rig.app.list(ListFixture.LIST_ID)!!
        assertEquals("the finished book is back", old, back.trip!!.id)
        assertEquals(25, back.items.size)
        assertTrue("Salt stays out of it", TripRules.bookComplete(back))
        assertTrue(album(rig).books.isEmpty())
        assertEquals(0, album(rig).perfectStreak)
        assertTrue(rig.vm.state.value.book!!.perfect)
        compose.mainClock.autoAdvance = true
    }

    /** Within 12 h of the shop an add never files anything (the book waits for her next shop). */
    @Test
    fun anAddWithinTwelveHoursFilesNothing() {
        val rig = ListRig(ListFixture.done(), now = ListFixture.TRIP_START + 11 * ListFixture.HOUR)
        compose.show(rig)
        compose.runOnIdle {
            rig.addVm.setText("Salt")
            rig.addVm.addAsTyped()
        }
        compose.waitForIdle()
        assertTrue(album(rig).books.isEmpty())
        assertFalse(shows(addNote))
        assertEquals(0, rig.vm.state.value.bookTurn)
    }
}

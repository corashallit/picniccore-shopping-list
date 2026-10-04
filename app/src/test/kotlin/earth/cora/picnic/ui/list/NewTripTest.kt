package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.BookTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * New trip (docs/DESIGN_STICKER.md §6.5, §7.1, §0.4 Q2 and Q9; items 142, 162): the sheet files the book in the album
 * and starts afresh in both modes, with the fresh book and the note; it is offered only with a trip; and the first
 * check-off after 12 h idle never asks: it starts the new book itself (item 315).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class NewTripTest {
    @get:Rule
    val compose = createComposeRule()

    private fun album(rig: ListRig): Album = (rig.album.album as kotlinx.coroutines.flow.StateFlow<Album>).value

    private fun openSheet() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New trip").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(NewTripTags.SHEET).assertExists()
        compose.onNodeWithText("Start a new trip?").assertExists()
        compose.onNodeWithText("This sticker book goes into your album.").assertExists()
    }

    private fun summary(): String? = compose.onNodeWithTag(BookTags.BOOK).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single()

    /**
     * §6.5 (item 162): the closing book in the sheet is drawn small, still and read-only: taps on its stamp page and its
     * sticker page boing nothing, show no name tag, feed no egg and open nothing; the sheet stays as it was.
     */
    @Test
    fun theSmallBookIsStill() {
        val rig = ListRig()
        val probe = RecordingProbe()
        compose.show(rig, probe)
        openSheet()
        val title = compose.onNodeWithText("Start a new trip?").fetchSemanticsNode().boundsInRoot
        val d = compose.density.density
        val y = title.bottom + (12 + 4 + 24) * d
        val sheet = compose.onNodeWithTag(NewTripTags.SHEET).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        for (fx in listOf(0.15f, 0.25f, 0.6f, 0.85f)) {
            compose.onNodeWithTag(NewTripTags.SHEET).performTouchInput { click(androidx.compose.ui.geometry.Offset(sheet.width * fx, y - sheet.top)) }
            compose.frames(20)
            assertTrue("no name tag ($fx)", probe.keys("nameTag:").isEmpty())
        }
        assertTrue("no stamp tap reached the eggs", rig.surprises.stampTaps.isEmpty())
        assertTrue("the sheet is still open", compose.exists(NewTripTags.SHEET))
        assertTrue(
            "the Trip book sheet did not open",
            compose.onAllNodes(androidx.compose.ui.test.hasText("aisles stamped", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
        compose.mainClock.autoAdvance = true
    }

    /** Item 162: "Clear what I got", then (on the next trip) "Keep everything, uncheck it all". */
    @Test
    fun endToEnd() {
        val rig = ListRig()
        compose.show(rig)
        val oldTrip = rig.app.list(ListFixture.LIST_ID)!!.trip!!.id
        openSheet()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Clear what I got").performScrollTo().performClick()
        compose.advanceMs(700)
        val cleared = rig.app.list(ListFixture.LIST_ID)!!
        assertEquals("the book is in the album", listOf(oldTrip), album(rig).books.map { it.id })
        assertEquals("what was got is gone, the rest stays", 14, cleared.items.size)
        assertTrue(cleared.items.none { it.checked })
        assertNull("no trip until the next check-off", cleared.trip)
        assertEquals(rig.now, cleared.lastClosedAt)
        compose.onNodeWithText("New trip · a fresh sticker book", substring = true, useUnmergedTree = true).assertExists()
        assertFalse("the sheet closed", compose.exists(NewTripTags.SHEET))
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("a fresh book with today's date", "Trip sticker book, Sat 3 Oct: 0 of 14 stickers, 0 of 7 aisles stamped", summary())

        // The next trip: one check-off opens it; "Keep everything" files it and un-checks all.
        rig.now += ListFixture.MIN
        rig.vm.toggle("strawberries")
        compose.waitForIdle()
        val second = rig.app.list(ListFixture.LIST_ID)!!.trip!!.id
        assertNotEquals(oldTrip, second)
        openSheet()
        compose.onNodeWithText("Keep everything, uncheck it all").performScrollTo().performClick()
        compose.waitForIdle()
        val kept = rig.app.list(ListFixture.LIST_ID)!!
        assertEquals("both books are filed, newest first", listOf(second, oldTrip), album(rig).books.map { it.id })
        assertEquals("everything stays", 14, kept.items.size)
        assertTrue("unchecked", kept.items.none { it.checked })
        assertNull(kept.trip)
    }

    /** Item 162: with no trip yet there is no book to file: New trip is disabled ("No trip yet"). */
    @Test
    fun disabledWithoutTrip() {
        val rig = ListRig(ListFixture.fresh())
        compose.show(rig)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New trip").assertIsNotEnabled()
        compose.onNodeWithText("New trip").performClick()
        compose.waitForIdle()
        assertFalse(compose.exists(NewTripTags.SHEET))
        assertTrue(album(rig).books.isEmpty())
    }

    /** "Not yet" closes the sheet and files nothing. */
    @Test
    fun notYetKeepsTheTrip() {
        val rig = ListRig()
        compose.show(rig)
        val trip = rig.app.list(ListFixture.LIST_ID)!!.trip
        openSheet()
        compose.onNodeWithText("Not yet").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(compose.exists(NewTripTags.SHEET))
        assertEquals(trip, rig.app.list(ListFixture.LIST_ID)!!.trip)
        assertTrue(album(rig).books.isEmpty())
    }

    /**
     * Item 142 as Cora answered it (§0.4 Q9 replaces the idle prompt): the first check-off 13 h after the trip's last
     * activity never opens the sheet; the item lands in a fresh book at once and the old book is filed.
     */
    @Test
    fun idleCheckStartsNewBookWithoutAsking() {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        compose.show(rig)
        val old = rig.app.list(ListFixture.LIST_ID)!!.trip!!.id
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("baking-powder")).performClick()
        repeat(60) {
            compose.frames(1)
            assertFalse("no question is asked", compose.exists(NewTripTags.SHEET))
        }
        val list = rig.app.list(ListFixture.LIST_ID)!!
        assertNotEquals("a fresh book", old, list.trip!!.id)
        assertTrue("the item is in it", rig.app.isChecked("baking-powder"))
        assertEquals(listOf(old), album(rig).books.map { it.id })
        compose.mainClock.autoAdvance = true
    }
}

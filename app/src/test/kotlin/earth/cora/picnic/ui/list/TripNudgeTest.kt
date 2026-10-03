package earth.cora.picnic.ui.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.TOAST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The nudge (docs/DESIGN_STICKER.md §6.1, §9; item 120): a list that opens on a trip idle for more than 12 h gets the
 * sticky note "New shop? Start a fresh sticker book" with "New trip", held at least 6 s; nothing is cleared.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TripNudgeTest {
    @get:Rule
    val compose = createComposeRule()

    private val nudge = "New shop? Start a fresh sticker book"

    @Test
    fun nudgeAfterTwelveHoursIdle() {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        val before = rig.app
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        assertTrue("the nudge shows", compose.framesUntil(1500) { compose.exists(TOAST_TAG) })
        compose.onNodeWithText(nudge, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("New trip", useUnmergedTree = true).assertExists()
        // It holds max(6000, readHold) (it carries an action, §9).
        compose.advanceMs(5700)
        compose.onNodeWithText(nudge, useUnmergedTree = true).assertExists()
        // Nothing was cleared or filed.
        assertEquals(before.list(ListFixture.LIST_ID), rig.app.list(ListFixture.LIST_ID))
        assertTrue((rig.album.album as kotlinx.coroutines.flow.StateFlow<earth.cora.picnic.core.store.Album>).value.books.isEmpty())
        // Its action opens the New trip sheet.
        compose.onNodeWithText("New trip", useUnmergedTree = true).performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(compose.exists(NewTripTags.SHEET))
    }

    @Test
    fun noNudgeWithinTwelveHours() {
        val rig = ListRig(now = ListFixture.NOW + 11 * ListFixture.HOUR)
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        compose.advanceMs(1500)
        assertFalse(compose.exists(TOAST_TAG))
    }

    @Test
    fun noNudgeWithoutATrip() {
        val rig = ListRig(ListFixture.fresh(), now = ListFixture.NOW + 13 * ListFixture.HOUR)
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        compose.advanceMs(1500)
        assertFalse(compose.exists(TOAST_TAG))
    }
}

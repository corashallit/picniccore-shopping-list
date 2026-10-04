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

    /** A lifecycle the test moves by hand (the app going to the background and coming back). */
    private class HandLifecycle : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    private fun showWith(rig: ListRig, owner: HandLifecycle) {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) { rig.Themed() }
        }
    }

    /** The app waits in the background, then comes back to the front [later] ms on. */
    private fun backgroundFor(rig: ListRig, owner: HandLifecycle, later: Long) {
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        }
        rig.now += later
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        }
    }

    /**
     * Items 120 and 143 (§6.1, §7.1): the app stays alive in the background and she comes back 13 h later. Returning to
     * the list checks the trip again: it is idle now, so the nudge shows, once for that idle period (another return
     * without a check-off in between says nothing more).
     */
    @Test
    fun comingBackAfterTwelveHoursNudges() {
        val rig = ListRig(now = ListFixture.NOW + ListFixture.HOUR)
        val owner = HandLifecycle()
        compose.mainClock.autoAdvance = false
        showWith(rig, owner)
        compose.advanceMs(1500)
        assertFalse("not idle yet", compose.exists(TOAST_TAG))
        backgroundFor(rig, owner, 13 * ListFixture.HOUR)
        assertTrue("the nudge shows on her return", compose.framesUntil(1500) { compose.exists(TOAST_TAG) })
        compose.onNodeWithText(nudge, useUnmergedTree = true).assertExists()
        assertTrue("it goes after its hold", compose.framesUntil(15_000) { !compose.exists(TOAST_TAG) })
        backgroundFor(rig, owner, ListFixture.HOUR)
        compose.advanceMs(1500)
        assertFalse("once per idle period", compose.exists(TOAST_TAG))
        compose.mainClock.autoAdvance = true
    }

    /** Item 143 (§7.1): a fresh book shows today; back from the background the next day, its date says the new day. */
    @Test
    fun aFreshBookRollsOverToToday() {
        val rig = ListRig(ListFixture.fresh(), now = ListFixture.NOW)
        val owner = HandLifecycle()
        showWith(rig, owner)
        compose.waitForIdle()
        assertEquals("Sat 3 Oct", rig.vm.state.value.book!!.dateLabel)
        backgroundFor(rig, owner, 24 * ListFixture.HOUR)
        compose.waitForIdle()
        assertEquals("the new day", "Sun 4 Oct", rig.vm.state.value.book!!.dateLabel)
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

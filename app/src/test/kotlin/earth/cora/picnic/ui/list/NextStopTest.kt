package earth.cora.picnic.ui.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.ui.add.ADD_BAR_TAG
import earth.cora.picnic.ui.add.ADD_FAB_TAG
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.motion.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real ListViewModel drives the open aisle through the chains (docs/DESIGN_STICKER.md D2, §8.4, §10; the list's
 * half of item 90): the next stop opens behind the Next card and scrolls into view (the add bar ignores that scroll,
 * item 116); a finished trip folds everything; an un-check re-opens its aisle.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class NextStopTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nextAisleAutoOpens() {
        val rig = ListRig()
        compose.show(rig)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("baking-powder")).performClick()
        val frames = compose.sample(rig, 2400)
        val land = landTime(rig, "baking-powder", frames.tapTime("baking-powder"))
        assertEquals("the store opens the next stop at once", "breakfast", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
        // Baking stays open (the engine's override) until it folds at L + 1100; the card rises at L + 1300.
        assertTrue("Baking still shows open before the fold", frames.filter { it.time < land + 1100 }.all { it.p.openSection(rig.vm.state.value.openSectionId) == "aisle-2-baking" })
        assertBeat("the Next card", land + 1300, frames.firstTime { it.nextCard != null })
        assertEquals("breakfast", frames.first { it.p.nextCard != null }.p.nextCard!!.sectionId)
        assertTrue(rig.engine.eventLog.contains(MotionEvent.NextShown("breakfast", card = true)))
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // Breakfast is open and in view; the programmatic scroll did not fold the add bar.
        val list = compose.boundsOf(ListTags.LIST)
        val header = compose.boundsOf(ListTags.header("breakfast"))
        assertTrue("the next stop is scrolled into view ($header in $list)", header.top >= list.top - 1f && header.bottom <= list.bottom)
        assertTrue("the add bar stays docked", compose.exists(ADD_BAR_TAG) && !compose.exists(ADD_FAB_TAG))
    }

    /** The trip's last item: everything folds after the finale; un-checking in a done aisle opens it again. */
    @Test
    fun tripCompleteFoldsAndUncheckReopens() {
        val rig = ListRig(ListFixture.allBut(listOf("toothpaste"), open = "household"))
        compose.show(rig)
        // Household, the last stop, is below the fold: bring its last row on screen.
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("toothpaste")
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.row("toothpaste")).performClick()
        compose.waitForIdle()
        assertEquals("all folded once the trip is done", "", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
        assertFalse(compose.exists(ListTags.header("household")))
        assertTrue(rig.engine.eventLog.any { it is MotionEvent.TripComplete })
        // Un-checking a done aisle's item keeps her there: that aisle opens.
        compose.runOnIdle { rig.vm.toggle("flour") }
        compose.waitForIdle()
        assertEquals("aisle-2-baking", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
        assertTrue(compose.exists(ListTags.header("aisle-2-baking")))
    }
}

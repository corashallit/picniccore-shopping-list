package earth.cora.picnic.ui.list

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Peeking into a folded basket (docs/DESIGN_STICKER.md §5.3, §4.23; item 114): holding it for the hold threshold opens
 * the lid and shows the peek note (below the strip, or above it in the lower 55 % of the list); release closes it; the
 * strip never opens from a hold; a short tap still opens it; egg 2 hears how long she held.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class PeekTest {
    @get:Rule
    val compose = createComposeRule()

    private fun hold(rig: ListRig, probe: RecordingProbe, id: String, ms: Long, check: () -> Unit) {
        val basket = probe["basket:$id"]
        val strip = compose.boundsOf(ListTags.strip(id))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.strip(id)).performTouchInput { down(Offset(basket.center.x - strip.left, basket.center.y - strip.top)) }
        compose.advanceMs(ms)
        check()
        compose.onNodeWithTag(ListTags.strip(id)).performTouchInput { up() }
        compose.advanceMs(100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFalse("release closes the note", compose.exists(ListTags.PEEK))
        assertEquals("the strip did not open", "aisle-2-baking", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
    }

    @Test
    fun holdPeeksAndSwallowsClick() {
        val rig = ListRig()
        val probe = RecordingProbe()
        compose.show(rig, probe)
        // Produce, near the top: the note hangs below the strip, 54 dp from the right.
        hold(rig, probe, "produce", 600) {
            assertTrue("the note shows after the hold threshold", compose.exists(ListTags.PEEK))
            val note = compose.boundsOf(ListTags.PEEK)
            val strip = compose.boundsOf(ListTags.strip("produce"))
            assertTrue("below the strip (${note.top} ≥ ${strip.bottom})", note.top >= strip.bottom - 4 * compose.density.density)
            val spoken = compose.onNodeWithTag(ListTags.PEEK).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.single()
            assertTrue(spoken, spoken.startsWith("Produce: Strawberries, Bananas, in the basket"))
        }
        assertEquals("egg 2 hears the hold", "produce", rig.surprises.basketHolds.single().first)
        assertTrue("held about 600 ms (${rig.surprises.basketHolds.single().second})", rig.surprises.basketHolds.single().second >= 440)

        // A short press shows nothing (and the strip's own tap still works).
        val basket = probe["basket:bakery"]
        val strip = compose.boundsOf(ListTags.strip("bakery"))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.strip("bakery")).performTouchInput { down(Offset(basket.center.x - strip.left, basket.center.y - strip.top)) }
        compose.advanceMs(200)
        assertFalse("no note before the threshold", compose.exists(ListTags.PEEK))
        compose.onNodeWithTag(ListTags.strip("bakery")).performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("a tap on the basket opens the strip", "bakery", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
    }

    /** In the lower 55 % of the list the note flips above the strip (Breakfast, under the open Baking tray). */
    @Test
    fun lowerStripFlipsAbove() {
        val rig = ListRig()
        val probe = RecordingProbe()
        compose.show(rig, probe)
        val list = compose.boundsOf(ListTags.LIST)
        val strip = compose.boundsOf(ListTags.strip("breakfast"))
        assertTrue("Breakfast is in the lower 55 % (${strip.center.y - list.top} of ${list.height})", strip.center.y - list.top > list.height * 0.45f)
        hold(rig, probe, "breakfast", 600) {
            val note = compose.boundsOf(ListTags.PEEK)
            assertTrue("above the strip (${note.bottom} ≤ ${strip.top})", note.bottom <= strip.top + 4 * compose.density.density)
            assertTrue("inside the list", note.top >= list.top)
        }
    }

    /**
     * A scroll that starts with the finger on a folded basket is a scroll (item 114): the list follows the finger, no
     * note opens and egg 2 hears of no hold, however long the drag takes.
     */
    @Test
    fun scrollFromBasketDoesNotPeek() {
        val rig = ListRig()
        val probe = RecordingProbe()
        compose.show(rig, probe)
        val dp = compose.density.density
        val basket = probe["basket:bakery"]
        val list = compose.boundsOf(ListTags.LIST)
        val before = compose.boundsOf(ListTags.strip("bakery"))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { down(Offset(basket.center.x - list.left, basket.center.y - list.top)) }
        compose.frames(2)
        var peeked = false
        // 144 dp of finger travel over about 600 ms: longer than the hold threshold, so a timeout would have fired.
        repeat(12) {
            compose.onNodeWithTag(ListTags.LIST).performTouchInput { moveBy(Offset(0f, -12 * dp)) }
            compose.advanceMs(48)
            if (compose.exists(ListTags.PEEK)) peeked = true
        }
        val during = compose.boundsOf(ListTags.strip("bakery"))
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { up() }
        compose.frames(30)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFalse("a scroll that starts on a basket never opens the peek note", peeked)
        assertTrue("egg 2 heard no hold (${rig.surprises.basketHolds})", rig.surprises.basketHolds.isEmpty())
        val moved = (before.top - during.top) / dp
        assertTrue("the list followed the finger ($moved dp of 144)", moved > 120f)
        assertEquals("the strip did not open", "aisle-2-baking", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
    }

    /**
     * A long aisle at font scale 2.0 (Household, 11 items): the note stays inside the list's viewport, clear of the add
     * bar and the top stack, and caps its height with "+n more" rather than running off.
     */
    @Test
    fun longAisleNoteStaysInsideTheList() {
        val rig = ListRig(ListFixture.longAisle().setOpenAisle(ListFixture.LIST_ID, "produce"))
        val probe = RecordingProbe()
        compose.show(rig, probe, fontScale = 2f)
        val id = "household"
        // Bring Household's strip into view first (the scroll collapses the top stack, so the list is measured after it).
        compose.onNodeWithTag(ListTags.LIST).performScrollToNode(androidx.compose.ui.test.hasTestTag(ListTags.strip(id)))
        compose.waitForIdle()
        val list = compose.boundsOf(ListTags.LIST)
        // The scroll folded the add bar into the FAB (§6.1): the note stays above whichever is docked.
        val dockTag = listOf(earth.cora.picnic.ui.add.ADD_BAR_TAG, earth.cora.picnic.ui.add.ADD_FAB_TAG).first { compose.exists(it, unmerged = true) }
        val dock = compose.boundsOf(dockTag, unmerged = true)
        val strip = compose.boundsOf(ListTags.strip(id))
        val basket = probe["basket:$id"]
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.strip(id)).performTouchInput { down(Offset(basket.center.x - strip.left, basket.center.y - strip.top)) }
        compose.advanceMs(700)
        assertTrue("the note shows", compose.exists(ListTags.PEEK))
        val note = compose.boundsOf(ListTags.PEEK)
        val slack = 3 * compose.density.density
        assertTrue("under the top of the list (${note.top} ≥ ${list.top})", note.top >= list.top - slack)
        assertTrue("above the $dockTag (${note.bottom} ≤ ${dock.top})", note.bottom <= dock.top + slack)
        assertTrue("clear of the strip it peeks into", note.bottom <= strip.top + slack || note.top >= strip.bottom - slack)
        compose.onNodeWithText(" more", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(ListTags.strip(id)).performTouchInput { up() }
        compose.advanceMs(100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }
}

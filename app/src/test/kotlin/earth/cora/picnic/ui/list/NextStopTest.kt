package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
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

    /**
     * Produce, long and pinned under its plate, finishes (item 87, §8.4, §10): at the fold (L + 1100) the list keeps its
     * place on Produce's strip, at the top of the viewport, in the same frame as the swap; no frame between the fold and
     * the Next card has an empty viewport or jumps to the list's end; then the next stop's band lands flush at the top.
     */
    @Test
    fun foldUnderPinnedPlateKeepsItsPlace() {
        val rig = ListRig(ListFixture.longProduce())
        compose.show(rig)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("limes")
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.LIST).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -110f * compose.density.density) }
        compose.waitForIdle()
        val list = compose.boundsOf(ListTags.LIST)
        assertTrue("Produce's plate is pinned", kotlin.math.abs(compose.boundsOf(ListTags.header("produce")).top - list.top) < 1f)
        assertFalse("its first rows are under the plate", compose.exists(ListTags.rowSlot("strawberries")))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("limes")).performClick()
        var tap = -1L
        var checked = 0
        repeat(130) {
            compose.frames(1)
            val p = rig.engine.current.value
            if (tap < 0) p.rowStarts["limes"]?.let { tap = it }
            if (tap < 0) return@repeat
            val l = landTime(rig, "limes", tap)
            val t = rig.engine.time
            if (t >= l + 1100 + 17 && t < l + 1300) {
                checked++
                assertFalse("folded at $t", compose.exists(ListTags.header("produce")))
                val strip = compose.boundsOf(ListTags.strip("produce"))
                assertTrue("Produce's strip at the top at ${t - l} (${strip.top} vs ${list.top})", strip.top >= list.top - 1f && strip.top <= list.top + 16f * compose.density.density)
                assertTrue("the next strips show at once at ${t - l}", compose.exists(ListTags.strip("bakery")) && compose.boundsOf(ListTags.strip("bakery")).bottom < list.bottom)
            }
        }
        assertTrue("frames between the fold and the card were checked ($checked)", checked >= 8)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val header = compose.boundsOf(ListTags.header("bakery"))
        assertEquals("the next stop lands flush at the viewport's top", list.top, header.top, 1.5f)
    }

    /**
     * Item 178 under reduced motion: the next stop is a jump (no smooth scroll), and it lands flush, never with the strip
     * before it sliced at the top, even when the list ends soon after it (Breakfast, two stops from the end).
     */
    @Test
    fun reducedMotionLandsFlush() {
        val rig = ListRig()
        compose.show(rig, reducedMotion = true)
        val list = compose.boundsOf(ListTags.LIST)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("baking-powder")).performClick()
        val tops = ArrayList<Float>()
        repeat(160) {
            compose.frames(1)
            if (compose.exists(ListTags.strip("aisle-2-baking"))) {
                val strip = compose.boundsOf(ListTags.strip("aisle-2-baking"))
                assertFalse("the Baking strip is never sliced at the top (${strip.top}..${strip.bottom} vs ${list.top})", strip.top < list.top - 1f && strip.bottom > list.top + 1f)
            }
            if (compose.exists(ListTags.header("breakfast"))) tops += compose.boundsOf(ListTags.header("breakfast")).top
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("Breakfast lands flush", list.top, compose.boundsOf(ListTags.header("breakfast")).top, 1.5f)
        // One jump: Breakfast's band takes at most two places (where it opened, then the top), never a glide between.
        assertTrue("a jump, not a glide (${tops.distinct()})", tops.map { kotlin.math.round(it) }.distinct().size <= 2)
    }
}

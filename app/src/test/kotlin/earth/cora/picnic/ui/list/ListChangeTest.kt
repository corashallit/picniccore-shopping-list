package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.motion.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Completions without a check-off, from the real ListViewModel's grouping (docs/DESIGN_STICKER.md §8.4; the list's half
 * of item 176): a delete or a move that finishes an aisle (and the trip) emits a ListChange, and the in-place chain, the
 * Next card and the finale play from it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListChangeTest {
    @get:Rule
    val compose = createComposeRule()

    /** Deleting the trip's last unchecked item (its TalkBack "Delete") completes Household and the trip. */
    @Test
    fun deleteCompletesAisleAndTrip() {
        val rig = ListRig(ListFixture.allBut(listOf("toothpaste"), open = "household"))
        compose.show(rig)
        // Household, the last stop, is below the fold: bring its last row on screen.
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("toothpaste")
        compose.waitForIdle()
        val delete = compose.onNodeWithTag(ListTags.row("toothpaste")).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Delete" }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { delete.action() }
        val before = rig.engine.time
        val frames = compose.sample(rig, 1700)
        val l = frames.first { it.p.baskets["household"]?.inPlace == true || it.p.baskets["household"]?.lidClosed != null }.time
        assertTrue("no hop, no burst: nothing was tapped", frames.all { it.p.flights.isEmpty() && it.p.bursts.isEmpty() })
        val lid = frames.firstTime { it.baskets["household"]?.lidClosed == true }
        assertNotNull("the lid shuts in place", lid)
        assertTrue("in place (fades)", frames.first { it.time == lid }.p.baskets["household"]!!.inPlace)
        val cele = frames.firstOrNull { it.p.celebration != null }?.p?.celebration
        assertNotNull("the finale starts", cele)
        assertNull("from the screen centre", cele!!.origin)
        // The lid shuts at L + 300 and the finale starts at L + 1350 (§8.4).
        assertTrue("1050 ms after the lid (${cele.t0 - lid!!})", cele.t0 - lid in 1020L..1090L)
        assertTrue(l >= before)
        assertTrue(rig.engine.eventLog.any { it is MotionEvent.TripComplete })
        assertEquals(1, rig.feedback.count(Cue.CELEBRATION))
        compose.mainClock.autoAdvance = true
    }

    /** Moving Baking powder to Breakfast (Edit → Move to aisle) leaves Baking done: in place, then the Next card. */
    @Test
    fun moveCompletesAisle() {
        val rig = ListRig()
        compose.show(rig)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { rig.vm.moveToAisle("baking-powder", "breakfast") }
        val start = rig.engine.time
        val frames = compose.sample(rig, 1900)
        assertTrue(frames.all { it.p.flights.isEmpty() })
        val lid = frames.firstTime { it.baskets["aisle-2-baking"]?.lidClosed == true }
        assertNotNull("Baking's lid shuts", lid)
        val fold = frames.firstTime { it.aisles["aisle-2-baking"]?.foldT0 != null }
        assertNotNull("Baking folds", fold)
        val card = frames.firstOrNull { it.p.nextCard != null }?.p?.nextCard
        assertNotNull("the Next card rises", card)
        assertEquals("breakfast", card!!.sectionId)
        // From L: the lid at L + 300, the fold at L + 1100, the card at L + 1300 (§8.4).
        assertTrue("the fold 800 ms after the lid (${fold!! - lid!!})", fold - lid in 770L..840L)
        assertTrue("the card 1000 ms after the lid (${card.t0 - lid})", card.t0 - lid in 970L..1040L)
        assertTrue(lid >= start)
        assertEquals("breakfast", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
        compose.mainClock.autoAdvance = true
    }
}

package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
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

    /**
     * Item 176, §8.4, §6.1: tearing off Bakery's last thing to get completes it (the lid shuts, the Next card names
     * Baking); Undo well after the chain has played (L + 2200) re-opens Bakery: the card pointing past it exits at once
     * (300 ms), and the restored row comes into view clear of the pinned plate, with no scroll of hers.
     */
    @Test
    fun undoTearAfterChain() {
        val rig = ListRig(ListFixture.state().toggleChecked(ListFixture.LIST_ID, "sourdough", ListFixture.TRIP_START + 7 * ListFixture.MIN).setOpenAisle(ListFixture.LIST_ID, "bakery"))
        compose.show(rig)
        val item = rig.item("baguette")
        val index = rig.app.items().indexOfFirst { it.id == "baguette" }
        compose.mainClock.autoAdvance = false
        // The TalkBack "Delete" (the tear-off's own beats are TearOffTest's): Bakery completes without a tap.
        compose.runOnIdle { rig.vm.delete("baguette") }
        val chain = compose.sample(rig, 2400)
        val card = chain.lastOrNull { it.p.nextCard != null }?.p?.nextCard
        assertNotNull("the Next card is up", card)
        assertEquals("aisle-2-baking", card!!.sectionId)
        assertTrue("well after the chain (card at ${card.t0})", rig.engine.time - card.t0 >= 900)
        assertTrue("the card still holds", rig.engine.current.value.nextCard?.exitT0 == null)
        // Undo: the row is back and Bakery has something to get again.
        compose.runOnIdle { rig.vm.undoDelete(item, index, ListFixture.LIST_ID) }
        val undo = compose.sample(rig, 600)
        val reopened = undo.first { it.p.nextCard?.exitT0 != null || it.p.nextCard == null }
        val exit = reopened.p.nextCard?.exitT0
        assertTrue("the card leaves at once", exit == null || exit - undo.first().time <= 34)
        assertTrue("gone within its 300 ms exit", undo.last().p.nextCard == null)
        assertEquals("Bakery is open again", "bakery", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
        // The restored row comes into view, clear of the plate and above the add bar (bounded wait).
        val list = compose.boundsOf(ListTags.LIST)
        val dock = compose.boundsOf(earth.cora.picnic.ui.add.ADD_BAR_TAG)
        var seen = ""
        val clear = compose.framesUntil(1500) {
            compose.exists(ListTags.rowSlot("baguette")) && run {
                val row = compose.boundsOf(ListTags.rowSlot("baguette"))
                val plate = compose.boundsOf(ListTags.header("bakery"))
                seen = "row $row, plate $plate, list $list, dock $dock"
                row.top >= plate.bottom - 1f && row.top >= list.top && row.bottom <= dock.top + 1f
            }
        }
        assertTrue("Baguette shows clear of the plate ($seen)", clear)
        compose.mainClock.autoAdvance = true
    }
}

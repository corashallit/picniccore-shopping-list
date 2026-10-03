package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.SectionIds
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.applyAisleAssignments
import earth.cora.picnic.core.model.removeItem
import earth.cora.picnic.core.model.setAislesText
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.platform.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Completions without a tap (docs/DESIGN_STICKER.md §8.4; item 176): the in-place chain, the card, the finale. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ListChangeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun deleteCompletesAisleAndTrip() {
        val rig = HarnessRig(compose, MotionFixture.allBut(listOf("toothpaste"), open = "household")).start()
        var change: ListChange? = null
        compose.runOnIdle { change = rig.model.delete("toothpaste") }
        rig.frame()
        val L = rig.engine.time
        assertEquals(setOf("household"), change!!.aisleCompleted)
        assertTrue(change!!.tripCompleted)
        assertEquals(ChangeCause.DELETE, change!!.cause)
        val frames = rig.sampleTo(L + 1600)
        // In place: no flight, no hop, no curl; 120 ms fades at the same beats; the fold at L + 1100.
        assertTrue(frames.all { it.second.flights.isEmpty() && it.second.bursts.isEmpty() })
        assertTrue(frames.all { it.second.aisles["household"]?.curlT0 == null })
        assertTrue(frames.all { it.second.baskets["household"]?.hopT0 == null })
        assertTrue(frames.first { it.first >= L + 300 }.second.baskets["household"]!!.inPlace)
        assertBeat("lid", L + 300, frames.firstTime { it.baskets["household"]?.lidClosed == true })
        assertBeat("stamp", L + 500, frames.firstTime { it.stamps["household"] is StampPhase.Thunking })
        // The stamp fades in place (no thunk, no burst) and the badge does not pop (§8.4).
        val stamp = frames.first { it.second.stamps["household"] is StampPhase.Thunking }.second.stamps["household"] as StampPhase.Thunking
        assertTrue("the stamp plays in place", stamp.inPlace)
        assertTrue(frames.filter { it.first >= L + 300 }.all { it.second.baskets["household"]?.inPlace != false })
        assertBeat("fold", L + 1100, frames.firstTime { it.aisles["household"]?.foldT0 != null })
        // The finale from the screen centre at L + 1350.
        assertBeat("celebration", L + 1350, frames.firstTime { it.celebration != null })
        val cele = frames.first { it.second.celebration != null }.second.celebration!!
        assertNull("from the screen centre", cele.origin)
        assertEquals(1, rig.feedback.count(Cue.CELEBRATION))
    }

    @Test
    fun moveCompletesAisle() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        var change: ListChange? = null
        compose.runOnIdle { change = rig.model.move("baking-powder", "breakfast") }
        rig.frame()
        val L = rig.engine.time
        assertEquals(setOf("aisle-2-baking"), change!!.aisleCompleted)
        assertEquals("breakfast", change!!.nextSectionId)
        val frames = rig.sampleTo(L + 1800)
        assertTrue(frames.all { it.second.flights.isEmpty() })
        assertBeat("lid", L + 300, frames.firstTime { it.baskets["aisle-2-baking"]?.lidClosed == true })
        assertBeat("fold", L + 1100, frames.firstTime { it.aisles["aisle-2-baking"]?.foldT0 != null })
        assertBeat("Next card", L + 1300, frames.firstTime { it.nextCard != null })
        assertEquals("breakfast", frames.first { it.second.nextCard != null }.second.nextCard!!.sectionId)
        assertNotNull(rig.engine.eventLog.firstOrNull { it == MotionEvent.NextShown("breakfast", card = true) })
    }

    /**
     * A re-sort (or applyAisleAssignments) files Breakfast's last item elsewhere: Breakfast, which was folded, completes
     * in place, and the Next card still comes at L + 1300 (§8.4 has no folded exception for a ListChange).
     */
    @Test
    fun syncCompletesAFoldedAisle() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        var change: ListChange? = null
        compose.runOnIdle { change = rig.model.apply(ChangeCause.SYNC) { it.applyAisleAssignments(mapOf("maple" to "frozen"), rig.model.now) } }
        rig.frame()
        val L = rig.engine.time
        assertEquals(setOf("breakfast"), change!!.aisleCompleted)
        assertEquals(ChangeCause.SYNC, change!!.cause)
        val frames = rig.sampleTo(L + 1800)
        assertTrue(frames.all { it.second.flights.isEmpty() && it.second.bursts.isEmpty() })
        assertBeat("lid", L + 300, frames.firstTime { it.baskets["breakfast"]?.lidClosed == true })
        assertBeat("stamp", L + 500, frames.firstTime { it.stamps["breakfast"] is StampPhase.Thunking })
        assertTrue((frames.first { it.second.stamps["breakfast"] is StampPhase.Thunking }.second.stamps["breakfast"] as StampPhase.Thunking).inPlace)
        assertBeat("Next card at L + 1300", L + 1300, frames.firstTime { it.nextCard != null })
    }

    /**
     * An aisle-text edit adds the aisle "Pantry", so jam (filed under an aisle id the text did not have, so it was in
     * "Everything else") moves into it and "Everything else" is left complete: it plays in place.
     */
    @Test
    fun aislesEditCompletesEverythingElse() {
        val base = MotionFixture.state()
        val jam = MotionFixture.item("maple").copy(id = "jam", name = "Strawberry jam", quantity = "500 mL jar", aisleId = "pantry", icon = "jam")
        val napkins = MotionFixture.item("maple").copy(id = "napkins", name = "Picnic napkins", quantity = "50 pack", aisleId = null, icon = "napkins")
        var s = base.addItems(MotionFixture.LIST_ID, listOf(jam, napkins), MotionFixture.NOW).first
        s = s.toggleChecked(MotionFixture.LIST_ID, "napkins", MotionFixture.NOW + 1)
        val rig = HarnessRig(compose, s).start()
        assertEquals("jam renders in Everything else", SectionIds.UNSORTED, rig.model.grouped().sections.first { sec -> sec.rows.any { it.id == "jam" } }.id)
        var change: ListChange? = null
        compose.runOnIdle {
            change = rig.model.apply(ChangeCause.AISLES_EDIT) { it.setAislesText(MotionFixture.AISLES + "\nPantry", { BasketKind.DEFAULT }, rig.model.now) }
        }
        rig.frame()
        val L = rig.engine.time
        assertEquals(setOf(SectionIds.UNSORTED), change!!.aisleCompleted)
        val frames = rig.sampleTo(L + 1500)
        assertTrue(frames.all { it.second.flights.isEmpty() })
        assertBeat("lid", L + 300, frames.firstTime { it.baskets[SectionIds.UNSORTED]?.lidClosed == true })
        assertTrue(frames.first { it.first >= L + 300 }.second.baskets[SectionIds.UNSORTED]!!.inPlace)
        assertBeat("stamp", L + 500, frames.firstTime { it.stamps[SectionIds.UNSORTED] is StampPhase.Thunking })
    }

    /** Undo of a quick add removes the household's last item to get: the aisle and the trip complete in place. */
    @Test
    fun undoCompletesAisleAndTrip() {
        var s = MotionFixture.allBut(listOf("toothpaste"), open = "household")
        val napkins = MotionFixture.item("toothpaste").copy(id = "napkins", name = "Picnic napkins", quantity = "50 pack", icon = "napkins")
        s = s.addItems(MotionFixture.LIST_ID, listOf(napkins), MotionFixture.NOW).first
        s = s.toggleChecked(MotionFixture.LIST_ID, "toothpaste", MotionFixture.NOW + 1)
        val rig = HarnessRig(compose, s).start()
        var change: ListChange? = null
        compose.runOnIdle { change = rig.model.apply(ChangeCause.UNDO) { it.removeItem(MotionFixture.LIST_ID, "napkins", rig.model.now) } }
        rig.frame()
        val L = rig.engine.time
        assertEquals(setOf("household"), change!!.aisleCompleted)
        assertTrue(change!!.tripCompleted)
        val frames = rig.sampleTo(L + 1500)
        assertTrue(frames.all { it.second.flights.isEmpty() && it.second.bursts.isEmpty() })
        assertBeat("lid", L + 300, frames.firstTime { it.baskets["household"]?.lidClosed == true })
        assertBeat("celebration", L + 1350, frames.firstTime { it.celebration != null })
        assertNull("from the screen centre", frames.first { it.second.celebration != null }.second.celebration!!.origin)
    }
}

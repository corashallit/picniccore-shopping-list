package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}

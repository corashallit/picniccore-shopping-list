package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.platform.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reduced motion keeps every beat time with 120 ms fades in place of movement (docs/DESIGN_STICKER.md §8.8; item 178):
 * hanko 0, mini 620, twin 940, lid L + 300, stamp L + 500, card L + 1300, summary L + 1350.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ReducedMotionChainTest {
    @get:Rule
    val compose = createComposeRule()

    private val baking = "aisle-2-baking"

    @Test
    fun chainKeepsItsBeats() {
        val rig = HarnessRig(compose, MotionFixture.state(), reduced = true).start()
        val t0 = rig.tap("baking-powder")
        val slot = rig.model.item("baking-powder").bookSlot!!
        val L = t0 + 620
        val frames = rig.sampleTo(L + 1500)
        // Hanko at 0: a 120 ms fade in, no slam.
        val first = frames.first().second
        assertEquals(RowPhase.Slam, first.rows["baking-powder"])
        assertEquals(1f, RowMotion.hankoScale(true, RowPhase.Slam, t0, t0, reduced = true), 0f)
        assertEquals(0.5f, RowMotion.hankoAlpha(true, RowPhase.Slam, t0, t0 + 60, reduced = true), 0.01f)
        assertBeat("hanko", t0, rig.feedback.times(Cue.HANKO).firstOrNull())
        // §8.8: the hanko fades in at 0, then nothing else moves: its alpha (as the row draws it, from the presentation's
        // phase and start) never drops on any frame to 900 ms, through the landing's ghost cross-fade at 620.
        var last = 0f
        for ((t, p) in frames.filter { it.first <= t0 + 900 }) {
            val a = RowMotion.hankoAlpha(true, p.rows["baking-powder"], p.rowStarts["baking-powder"], t, reduced = true)
            assertTrue("hanko alpha drops at +${t - t0}: $last -> $a", a >= last - 1e-4f)
            last = a
        }
        assertEquals("fully shown by the landing", 1f, last, 1e-4f)
        // The mini at 620: the sticker cross-fades to its ghost and the mini fades into the basket, in place.
        assertBeat("mini", t0 + 620, frames.firstTime { it.baskets[baking]?.squashT0 != null })
        val atLand = frames.first { it.second.baskets[baking]?.squashT0 != null }.second
        assertTrue(atLand.baskets[baking]!!.inPlace)
        assertEquals(t0 + 620, atLand.rowStarts["baking-powder"])
        assertEquals(1f, RowMotion.ghostAlpha(true, RowPhase.Away, t0 + 620, t0 + 620 + 120, reduced = true), 0f)
        // The twin at 940.
        assertBeat("twin", t0 + 940, frames.firstTime { it.spots[slot] is SpotPhase.Appearing })
        // The lid at L + 300 with its GOT IT at once (in place); the stamp at L + 500; the card at L + 1300.
        assertBeat("lid", L + 300, frames.firstTime { it.baskets[baking]?.lidClosed == true })
        assertEquals(L + 300, frames.first { it.second.baskets[baking]?.lidClosed == true }.second.baskets[baking]!!.lidHankoT0)
        assertBeat("stamp", L + 500, frames.firstTime { it.stamps[baking] is StampPhase.Thunking })
        assertBeat("card", L + 1300, frames.firstTime { it.nextCard != null })
        assertTrue(frames.first { it.second.nextCard != null }.second.nextCard!!.reduced)
        // Reduced motion silences movement, never sound or haptics (§14.3): the peel's "tk" at 200 and the tray curl's
        // "peel" at L + 800 still sound, at their beats.
        assertBeat("peel cue", t0 + Beats.PEEL, rig.feedback.times(Cue.PEEL).firstOrNull())
        assertBeat("curl cue", L + Beats.CURL, rig.feedback.times(Cue.UNCHECK).firstOrNull())
        // Nothing moves: no flight, burst, curl, page turn or lift.
        assertTrue(frames.all { (_, p) -> p.flights.isEmpty() && p.bursts.isEmpty() && p.bookFx.liftT0 == null && p.bookFx.pageTurn == null })
        assertTrue(frames.all { (_, p) -> p.aisles[baking]?.curlT0 == null && p.baskets[baking]?.hopT0 == null })
    }

    @Test
    fun summaryAtL1350() {
        val rig = HarnessRig(compose, MotionFixture.allBut(listOf("toothpaste"), open = "household"), reduced = true).start()
        val t0 = rig.tap("toothpaste")
        val L = t0 + 620
        val frames = rig.sampleTo(L + 1500)
        assertBeat("summary", L + 1350, frames.firstTime { it.celebration != null })
        val cele = frames.first { it.second.celebration != null }.second.celebration!!
        assertTrue(cele.reduced)
        assertEquals(0L, cele.plan!!.summaryAt)
        // max(6000, readHold(17 words, with the progress bar's label)).
        assertEquals(6300L, cele.plan!!.summaryHold)
    }
}

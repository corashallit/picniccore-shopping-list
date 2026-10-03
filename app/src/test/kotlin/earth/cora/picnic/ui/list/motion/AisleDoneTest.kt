package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.platform.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The aisle-done chain (docs/DESIGN_STICKER.md §8.4; items 177, 179). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class AisleDoneTest {
    @get:Rule
    val compose = createComposeRule()

    private val baking = "aisle-2-baking"

    /** Item 177: lid L + 300, stamp L + 500, curl L + 800, fold L + 1100 (ribbon 1200, bow 1400, card 1300). */
    @Test
    fun timeline() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        val frames = rig.sampleTo(L + 1800)
        assertBeat("lid", L + 300, frames.firstTime { it.baskets[baking]?.lidClosed == true })
        assertEquals(L + 300, frames.first { it.second.baskets[baking]?.lidClosed == true }.second.baskets[baking]!!.lidT0)
        assertBeat("lid cue", L + 300, rig.feedback.times(Cue.AISLE_DONE).firstOrNull())
        assertBeat("GOT IT on the lid", L + 440, frames.firstTime { it.baskets[baking]?.lidHankoT0 != null })
        assertBeat("stamp", L + 500, frames.firstTime { it.stamps[baking] is StampPhase.Thunking })
        assertBeat("stamp cue", L + 500, rig.feedback.times(Cue.STAMP).firstOrNull())
        assertBeat("curl", L + 800, frames.firstTime { it.aisles[baking]?.curlT0 != null })
        assertEquals(L + 800, frames.first { it.second.aisles[baking]?.curlT0 != null }.second.aisles[baking]!!.curlT0)
        assertBeat("fold", L + 1100, frames.firstTime { it.aisles[baking]?.foldT0 != null })
        assertTrue("the tray stays open until the fold", frames.filter { it.first < L + 1100 }.all { it.second.open?.sectionId == baking })
        assertTrue(rig.engine.eventLog.any { it == MotionEvent.AisleDone(baking) })
        assertBeat("ribbon", L + 1200, frames.firstTime { it.aisles[baking]?.ribbonT0 != null })
        assertEquals(L + 1400, frames.first { it.second.aisles[baking]?.ribbonT0 != null }.second.aisles[baking]!!.bowT0)
        assertBeat("Next card", L + 1300, frames.firstTime { it.nextCard != null })
        assertTrue(rig.announcer.messages.contains("Baking done"))
    }

    /** Item 177, the folded-aisle variant: lid, stamp, then the card at L + 700; no curl, no fold. */
    @Test
    fun foldedVariant() {
        val rig = HarnessRig(compose, MotionFixture.state().setOpenAisle(MotionFixture.LIST_ID, "produce")).start()
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        val frames = rig.sampleTo(L + 1500)
        assertBeat("lid", L + 300, rig.feedback.times(Cue.AISLE_DONE).firstOrNull())
        assertBeat("stamp", L + 500, frames.firstTime { it.stamps[baking] is StampPhase.Thunking })
        assertBeat("Next card at L + 700", L + 700, frames.firstTime { it.nextCard != null })
        assertTrue(frames.all { it.second.aisles[baking]?.curlT0 == null && it.second.aisles[baking]?.foldT0 == null })
    }

    /** Item 179: the stamp thunks into the stamp page in place and the "now" ring moves to the next stop. */
    @Test
    fun stampThunkInPlace() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val stampKey = AnchorRegistry.stampKey(baking)
        val before = rig.engine.anchors.bounds(stampKey)
        assertEquals(baking, rig.model.state.let { rig.model.grouped().nowSectionId })
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        val frames = rig.sampleTo(L + 1500) {
            assertEquals("the stamp never moves", before, rig.engine.anchors.bounds(stampKey))
        }
        // The "now" ring stays on Baking until the thunk; the next stop waits; then the thunk, in place.
        assertTrue(frames.filter { it.first in t0 until L + 500 }.all { it.second.stamps[baking] == StampPhase.Now })
        assertTrue(frames.filter { it.first in t0 until L + 500 }.all { it.second.stamps["breakfast"] == StampPhase.Waiting })
        val thunk = frames.first { it.second.stamps[baking] is StampPhase.Thunking }.second.stamps[baking] as StampPhase.Thunking
        assertEquals(L + 500, thunk.t0)
        assertNull("settled: the state's stamp", frames.last().second.stamps[baking])
        // Nothing flew to the stamp page; the ring is on the next stop (the open aisle with items left).
        assertTrue(rig.engine.flightLog.none { it.toKey.startsWith("stamp:") })
        assertEquals("breakfast", rig.model.grouped().nowSectionId)
    }
}

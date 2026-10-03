package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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

    /**
     * Items 176, 178 (§8.4 in-place form): a stamp that completes in place fades in with no thunk and no 4-pictogram
     * burst, and the badge's count changes with no pop; the tap form thunks with its burst and pops.
     */
    @Test
    fun inPlaceStampAndBadgeDoNotMove() {
        val clock = MotionClock()
        var phase by androidx.compose.runtime.mutableStateOf<StampPhase?>(StampPhase.Thunking(1000, inPlace = true))
        var basket by androidx.compose.runtime.mutableStateOf(BasketPresentation(badgePopT0 = 1000, inPlace = true))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            earth.cora.picnic.ui.theme.PicnicTheme {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(64.dp).testTag("stamp"), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        StampCircle(earth.cora.picnic.ui.theme.AisleLooks.of(4), 4, stamped = true, now = false, phase = phase, clock = clock)
                    }
                    // Room round the basket: its badge sits partly outside its box.
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("basket").padding(24.dp)) {
                        MotionBasket(
                            kind = earth.cora.picnic.core.model.BasketKind.MIXING_BOWL,
                            look = earth.cora.picnic.ui.theme.AisleLooks.of(4),
                            gotten = emptyList(),
                            toGet = 2,
                            complete = false,
                            presentation = basket,
                            clock = clock,
                        )
                    }
                }
            }
        }
        fun shot(tag: String) = compose.onNodeWithTag(tag).captureToImage().toPixelMap().let { m -> IntArray(m.width * m.height) { i -> m[i % m.width, i / m.width].hashCode() } }
        fun at(t: Long, tag: String): IntArray {
            compose.runOnIdle { clock.now = t }
            // A frame, so the layers and placements that read the clock redraw.
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            return shot(tag)
        }
        // The settled stamp, for comparison.
        compose.runOnIdle { phase = StampPhase.Stamped }
        val settled = at(1050, "stamp")
        compose.runOnIdle { phase = StampPhase.Thunking(1000, inPlace = true) }
        /** Pixels outside the 24 dp stamp circle (where the burst flies) that differ from the settled stamp's. */
        fun outsideCircle(px: IntArray): Int {
            val w = (64 * 3)
            val c = w / 2f
            val r = 12 * 3 + 2
            return px.indices.count { i -> val x = i % w; val y = i / w; (x - c) * (x - c) + (y - c) * (y - c) > r * r && px[i] != settled[i] }
        }
        // In place: a fade, nothing outside the circle, and the badge never changes size.
        assertEquals("no burst in place", 0, outsideCircle(at(1150, "stamp")))
        val badgeEarly = at(1050, "basket")
        val badgeLate = at(1500, "basket")
        assertTrue("no pop in place", badgeEarly.contentEquals(badgeLate))
        // The tap form: the thunk's burst flies outside the circle, and the badge pops.
        compose.runOnIdle {
            phase = StampPhase.Thunking(1000, inPlace = false)
            basket = BasketPresentation(badgePopT0 = 1000, inPlace = false)
        }
        assertTrue("the tap form bursts", outsideCircle(at(1150, "stamp")) > 0)
        assertTrue("the tap form pops", !at(1150, "basket").contentEquals(at(1500, "basket")))
    }

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

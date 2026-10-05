package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

/** MotionSpec matches §8.2 (checklist 192) and sparkles twinkle by the one rule (checklist 74). */
class MotionSpecTest {
    @Test
    fun tokens() {
        val m = PicnicMotion
        assertEquals(83, m.pose)
        assertEquals(375, m.sparkle.durationMillis)
        assertEquals(Stepped(3), m.sparkle.easing)
        assertEquals(240, m.layout.durationMillis)
        assertEquals(CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.2f), m.layout.easing)
        assertEquals(200, m.standard.durationMillis)
        assertEquals(CubicBezierEasing(0.4f, 0f, 0.2f, 1f), m.standard.easing)
        assertEquals(280, m.sheetIn.durationMillis)
        assertEquals(CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.1f), m.sheetIn.easing)
        assertEquals(260, m.sheetOut.durationMillis)
        assertEquals(220, m.toastIn.durationMillis)
        assertEquals(CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f), m.toastIn.easing)
        assertEquals(260, m.toastOut.durationMillis)
        assertEquals(300, m.hop.durationMillis)
        assertEquals(360, m.hopPersonality.durationMillis)
        assertEquals(CubicBezierEasing(0.3f, 0.9f, 0.5f, 1f), m.hop.easing)
        assertEquals(400, m.cardIn.durationMillis)
        assertEquals(CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f), m.cardIn.easing)
        assertEquals(300, m.cardOut.durationMillis)
        assertEquals(CubicBezierEasing(0.5f, 0f, 0.7f, 0.4f), m.cardOut.easing)
        assertEquals(1200, m.glint.durationMillis)
        assertEquals(Stepped(12), m.glint.easing)
        assertEquals(120, m.rmFade.durationMillis)
        assertEquals(2600L, m.floorCard)
        assertEquals(4000L, m.floorToast)
        assertEquals(1500L, m.floorBeat)
        assertEquals(1500L, m.floorLcdWord)
        // Component timings (§4).
        assertEquals(160, m.buttonPress.durationMillis)
        assertEquals(120, m.switchKnob.durationMillis)
        assertEquals(160, m.lcdTick.durationMillis)
        assertEquals(listOf(-2f, 1f, 0f), m.lcdTickOffsetsDp)
        assertEquals(listOf(1.4f, 1.15f, 1f), m.hankoSlamScales)
        assertEquals(listOf(1.6f, 1.2f, 1f), m.handleSlapScales)
        assertEquals(4000L, m.lcdBlinkPeriod)
        assertEquals(0.12f, m.lcdBlinkAlpha)
        assertEquals(m.lcdBlinkPeriod * 3 / 100, m.lcdBlinkOn)
    }

    @Test
    fun stepped() {
        for (n in listOf(1, 2, 3, 6, 12)) {
            val s = Stepped(n)
            for (i in 0..100) {
                val t = i / 100f
                assertEquals("steps($n) at $t", floor(t * n) / n, s.transform(t), 1e-6f)
            }
        }
    }

    @Test
    fun twinkle() {
        // Three poses, 125 ms each (8 fps), a 375 ms cycle; a shape change only (scale + rotation), never a flash.
        assertEquals(3, Twinkle.poses.size)
        assertEquals(375L, Twinkle.periodMillis)
        assertEquals(Pose(0.82f, -8f), Twinkle.at(0))
        assertEquals(Pose(0.82f, -8f), Twinkle.at(124))
        assertEquals(Pose(1.12f, 6f), Twinkle.at(125))
        assertEquals(Pose(0.95f, 0f), Twinkle.at(250))
        assertEquals(Pose(0.82f, -8f), Twinkle.at(375))
        // Offset by delay only.
        assertEquals(Twinkle.at(100), Twinkle.at(300, delayMillis = 200))
        // Nothing flashes faster than 3 Hz: a full cycle is a shape loop at 2.67 Hz.
        assertTrue(1000.0 / Twinkle.periodMillis < 3.0)
        for (p in Twinkle.poses) assertEquals(0f, p.dx)
    }

    @Test
    fun loopAndShotPoses() {
        assertEquals(0, loopPose(0, 1000, 4))
        assertEquals(3, loopPose(999, 1000, 4))
        assertEquals(0, loopPose(1000, 1000, 4))
        assertEquals(0, loopPose(50, 1000, 4, delayMillis = 100))
        assertEquals(0, shotPose(0, 160, 2))
        assertEquals(1, shotPose(80, 160, 2))
        assertEquals(1, shotPose(500, 160, 2))
    }

    @Test
    fun reducedMotionFromEitherScale() {
        assertTrue(reducedMotionFrom(0f, 1f))
        assertTrue(reducedMotionFrom(1f, 0f))
        assertTrue(reducedMotionFrom(0f, 0f))
        assertEquals(false, reducedMotionFrom(1f, 1f))
        assertEquals(false, reducedMotionFrom(0.5f, 2f))
        assertEquals(1f, FixedMotionScale.scaleFactor)
    }
}

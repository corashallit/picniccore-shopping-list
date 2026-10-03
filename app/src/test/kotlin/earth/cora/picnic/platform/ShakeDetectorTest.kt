package earth.cora.picnic.platform

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** Item 241 (egg 4's detection): two acceleration peaks over 2.7 g within 500 ms make a shake. */
class ShakeDetectorTest {
    private var shakes = 0
    private val d = ShakeDetector { shakes++ }

    /** A peak at [at] ms: one sample above [g], then back to rest 40 ms later. */
    private fun peak(at: Long, g: Float = 3.2f) {
        d.onMagnitude(g, at)
        d.onMagnitude(1f, at + 40)
    }

    @Test
    fun twoPeaksWithinHalfASecond() {
        peak(0)
        peak(480)
        assertEquals(1, shakes)
    }

    @Test
    fun peaksTooFarApart() {
        peak(0)
        peak(560)
        assertEquals("the second peak only starts a new count", 0, shakes)
        peak(900)
        assertEquals(1, shakes)
    }

    @Test
    fun oneLongPeakIsOnePeak() {
        // Staying above the threshold is one peak, however long.
        for (t in 0L..400L step 20) d.onMagnitude(3.5f, t)
        assertEquals(0, shakes)
    }

    @Test
    fun belowTheThresholdNothing() {
        peak(0, g = 2.6f)
        peak(200, g = 2.69f)
        assertEquals(0, shakes)
        peak(400, g = 2.71f)
        peak(600, g = 2.71f)
        assertEquals(1, shakes)
    }

    @Test
    fun afterAShakeItStartsOver() {
        peak(0)
        peak(200)
        peak(400)
        assertEquals("a third peak needs a new first", 1, shakes)
        peak(600)
        assertEquals(2, shakes)
    }

    @Test
    fun samplesInMetresPerSecondSquared() {
        val g = SensorManager.GRAVITY_EARTH
        d.onSample(0f, 0f, 3f * g, 0)
        d.onSample(0f, 0f, g, 50)
        d.onSample(3f * g, 0f, 0f, 300)
        assertEquals(1, shakes)
    }

    @Test
    fun resetForgetsAHalfSeenShake() {
        peak(0)
        d.reset()
        peak(300)
        assertEquals(0, shakes)
    }
}

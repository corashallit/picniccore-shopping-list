package earth.cora.picnic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/** Rule R2: exact dashes, no half dash at the seam, the seam mid-gap (§2.12; checklist 53). */
class DashSpecTest {
    private val densities = listOf(1f, 2f, 2.625f, 3f, 3.5f, 4f)

    private fun assertExact(spec: DashSpec, length: Float) {
        assertTrue("count", spec.count >= 1)
        assertEquals("n × interval = length", length, spec.count * spec.interval, length * 1e-5f)
        assertEquals("dash + gap = interval", spec.interval, spec.dash + spec.gap, 1e-3f)
        // The seam (the path's start, s = 0) sits in the middle of a gap: pattern position = phase.
        assertEquals("phase mid-gap", spec.dash + spec.gap / 2f, spec.phase, 1e-3f)
        // So the pattern position at the end (s = length) is the same: no half dash.
        val endPosition = (length + spec.phase) % spec.interval
        val startPosition = spec.phase % spec.interval
        assertTrue("seam matches", abs(endPosition - startPosition) < 1e-2f || abs(abs(endPosition - startPosition) - spec.interval) < 1e-2f)
    }

    @Test
    fun circumferenceDividesEvenly() {
        for (d in densities) {
            // The 30 dp checkbox spot: a 2 dp ring at r 14 dp, nominal 4 / 3 → 13 dashes.
            val r = 14f * d
            val spot = DashSpec.forCircle(r, 4f * d, 3f * d)
            assertExact(spot, (2 * PI * r).toFloat())
            assertEquals("13 dashes on the spot at ${d}×", 13, spot.count)
            // Stamp rings 3.4 / 2.6 (12 dp radius), the landing spot 3.2 / 2.4 (7 dp), book spots.
            for ((radius, dash, gap) in listOf(Triple(11f, 3.4f, 2.6f), Triple(7f, 3.2f, 2.4f), Triple(6.5f, 2.6f, 2f))) {
                val rr = radius * d
                assertExact(DashSpec.forCircle(rr, dash * d, gap * d), (2 * PI * rr).toFloat())
            }
        }
    }

    @Test
    fun pathDividesEvenly() {
        for (d in densities) {
            for (length in listOf(173.4f, 96f, 250.25f, 40f)) {
                val l = length * d
                assertExact(DashSpec.forPath(l, closed = true, nominalDash = 3f * d, nominalGap = 2f * d), l)
            }
            // Open paths end on a whole dash at both ends.
            val l = 120f * d
            val open = DashSpec.forPath(l, closed = false, nominalDash = 3f * d, nominalGap = 2f * d)
            assertEquals(l, open.count * open.dash + (open.count - 1) * open.gap, l * 1e-5f)
            assertEquals(0f, open.phase)
        }
    }

    @Test
    fun spotDashesKeepTheirShare() {
        val spec = DashSpec.forCircle(14f, 4f, 3f)
        assertEquals(4f / 7f, spec.dash / spec.interval, 1e-4f)
    }
}

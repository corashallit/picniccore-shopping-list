package earth.cora.picnic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Glow stars and Dusk sparkles sit in the binder gutters only, never on a page (§2.12; checklist 10). */
class GlowStarsTest {
    @Test
    fun gutterOnly() {
        for (density in listOf(1f, 2.625f, 3f)) {
            for (widthDp in listOf(360f, 411f)) {
                val width = widthDp * density
                val height = 760f * density
                val gutter = 8f * density
                for ((size, count) in listOf(12f * density to 6, 9f * density to 5)) {
                    val marks = gutterMarks(width, height, gutter, size, count)
                    assertEquals(count, marks.size)
                    for (m in marks) {
                        val left = m.center.x - m.size / 2f
                        val right = m.center.x + m.size / 2f
                        val inLeftGutter = right <= gutter + 0.01f
                        val inRightGutter = left >= width - gutter - 0.01f
                        assertTrue("mark at ${m.center} reaches a page", inLeftGutter || inRightGutter)
                        assertTrue(m.center.y in 0f..height)
                    }
                }
            }
        }
    }

    @Test
    fun chargeTiming() {
        // 85 % at rest, 100 % at 1.15× for 2 frames every 5 s.
        assertEquals(5000L, PicnicMotion.glowChargePeriod)
        assertEquals(2, PicnicMotion.glowChargeFrames)
        val marks = gutterMarks(1080f, 2280f, 24f, 36f, 6)
        assertTrue(marks.all { it.delayMillis in 0 until PicnicMotion.glowChargePeriod })
    }
}

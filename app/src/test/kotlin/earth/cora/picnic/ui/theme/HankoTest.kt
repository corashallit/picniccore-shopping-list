package earth.cora.picnic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** The hanko (§4.5, rule R2; checklist 55, 56). */
class HankoTest {
    @Test
    fun fitsTargetAtAllAngles() {
        // The 40 dp stamp sits at (4, 4) in the 48 dp target: its centre is the target's centre, so a rotation moves
        // only the speckles; every inked point must stay inside the 48 dp square at every angle from −16° to −8°.
        val scale = 40f / HankoGeometry.UNITS // dp per unit at 40 dp
        val centre = 24f
        for (tenth in -160..-80) {
            val angle = Math.toRadians(tenth / 10.0)
            // Ring: a circle, rotation-invariant.
            val ring = (HankoGeometry.RING_R + HankoGeometry.RING_W / 2f) * scale
            assertTrue(centre - ring >= 0f && centre + ring <= 48f)
            for ((deg, r, d) in HankoGeometry.speckles) {
                val a = Math.toRadians(deg.toDouble()) + angle
                val x = centre + r * scale * cos(a).toFloat()
                val y = centre + r * scale * sin(a).toFloat()
                val half = d / 2f
                assertTrue("speckle at ${tenth / 10f}°", x - half >= 0f && x + half <= 48f && y - half >= 0f && y + half <= 48f)
            }
        }
        assertTrue(HankoGeometry.inkRadius * scale <= 24f)
    }

    @Test
    fun angleStableAndInRange() {
        val ids = (0 until 500).map { "i-$it" } + listOf("", "i-strawberries", "milk", "Ωmega")
        for (id in ids) {
            val a = hankoAngle(id)
            assertTrue("$id: $a", a in -16f..-8f)
            assertEquals("stable", a, hankoAngle(String(id.toCharArray())))
        }
        // It really varies (seeded, not constant).
        assertTrue(ids.map(::hankoAngle).toSet().size >= 5)
    }

    @Test
    fun textAutoFits() {
        // min(10.4, 25.5 / (longest line × 0.7)) units on a 40-unit stamp.
        assertEquals(10.4f, HankoGeometry.textUnits(HankoText.GotIt), 1e-3f)
        assertEquals(25.5f / (5 * 0.7f), HankoGeometry.textUnits(HankoText.PerFect), 1e-3f)
        assertEquals(25.5f / (6 * 0.7f), HankoGeometry.textUnits(HankoText.HappyBday), 1e-3f)
        assertEquals(25.5f / (4 * 0.7f), HankoGeometry.textUnits(HankoText.JackPot), 1e-3f)
        // The widest line at that size stays inside the inner ring (≈0.6 em per Zen Maru 900 cap).
        for (t in HankoText.entries) {
            val width = maxOf(t.top.length, t.bottom.length) * HankoGeometry.textUnits(t) * 0.7f
            assertTrue("${t.name} $width", width <= 2 * HankoGeometry.INNER_R)
        }
    }
}

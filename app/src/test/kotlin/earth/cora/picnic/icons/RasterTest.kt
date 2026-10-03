package earth.cora.picnic.icons

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** The live-sticker raster toolkit against brute force and hand-made shapes. */
class RasterTest {
    private fun randomMask(w: Int, h: Int, density: Double, seed: Int): BooleanArray {
        val r = Random(seed)
        return BooleanArray(w * h) { r.nextDouble() < density }
    }

    @Test
    fun edtIsExactAndNamesTheNearestFeature() {
        for ((seed, density) in listOf(1 to 0.02, 2 to 0.2, 3 to 0.001, 4 to 0.6)) {
            val w = 37
            val h = 23
            val feature = randomMask(w, h, density, seed)
            val nearest = IntArray(w * h)
            val d = Raster.edt(feature, w, h, nearest)
            for (i in 0 until w * h) {
                var best = Float.MAX_VALUE
                for (j in 0 until w * h) {
                    if (!feature[j]) continue
                    val dx = (i % w - j % w).toFloat()
                    val dy = (i / w - j / w).toFloat()
                    best = minOf(best, dx * dx + dy * dy)
                }
                assertEquals("seed $seed pixel $i", best, d[i], 0f)
                if (best == Float.MAX_VALUE) {
                    assertEquals(-1, nearest[i])
                } else {
                    val j = nearest[i]
                    assertTrue(feature[j])
                    val dx = (i % w - j % w).toFloat()
                    val dy = (i / w - j / w).toFloat()
                    assertEquals(best, dx * dx + dy * dy, 0f)
                }
            }
        }
    }

    @Test
    fun diskMorphologyMatchesItsDefinition() {
        val w = 41
        val h = 41
        val disc = BooleanArray(w * h) { val x = it % w - 20; val y = it / w - 20; x * x + y * y <= 15 * 15 }
        val eroded = Raster.erodeDisk(disc, w, h, 5.0)
        val dilated = Raster.dilateDisk(disc, w, h, 3.0)
        for (i in 0 until w * h) {
            val x = i % w - 20
            val y = i / w - 20
            val r = kotlin.math.sqrt((x * x + y * y).toDouble())
            if (r <= 9.0) assertTrue("eroded keeps r=$r", eroded[i])
            if (r >= 11.0) assertTrue("eroded drops r=$r", !eroded[i])
            if (r <= 17.5) assertTrue("dilated adds r=$r", dilated[i])
            if (r >= 18.6) assertTrue("dilated stops r=$r", !dilated[i])
        }
    }

    @Test
    fun labelsAre4Connected() {
        // Two pixels touching only diagonally are two components.
        val mask = booleanArrayOf(
            true, false, false,
            false, true, true,
            false, false, true,
        )
        val labels = IntArray(9)
        assertEquals(2, Raster.label(mask, 3, 3, labels))
        assertEquals(labels[4], labels[8])
        assertTrue(labels[0] != labels[4])
        val values = intArrayOf(1, 1, 2, 1, -1, 2, 3, 3, 2)
        val l2 = IntArray(9)
        assertEquals(3, Raster.labelEqual(values, 3, 3, l2))
        assertEquals(0, l2[4])
    }

    @Test
    fun contoursCloseAndWindOuterBoundariesNegative() {
        // A ring: one outer boundary (negative) and one hole (positive).
        val w = 30
        val h = 30
        val field = FloatArray(w * h) { i ->
            val x = i % w - 14.5
            val y = i / w - 14.5
            val r = kotlin.math.sqrt(x * x + y * y)
            if (r in 5.0..11.0) 1f else 0f
        }
        val loops = Raster.contours(field, w, h, 0.5f)
        assertEquals(2, loops.size)
        val outer = loops.minBy { it.signedArea }
        val hole = loops.maxBy { it.signedArea }
        assertTrue(outer.signedArea < 0)
        assertTrue(hole.signedArea > 0)
        assertEquals(Math.PI * 11.0 * 11.0, -outer.signedArea, 30.0)
        assertEquals(Math.PI * 5.0 * 5.0, hole.signedArea, 15.0)
        // Simplifying keeps the shape within the tolerance and drops points.
        val s = Raster.simplifyClosed(outer, 0.5)
        assertTrue(s.size < outer.size)
        assertEquals(outer.signedArea, s.signedArea, 6.0)
    }

    @Test
    fun aSinglePixelAndTheGridEdgeStillClose() {
        val field = FloatArray(9).also { it[4] = 1f }
        val loops = Raster.contours(field, 3, 3, 0.5f)
        assertEquals(1, loops.size)
        assertEquals(-0.5, loops[0].signedArea, 1e-6)
        // Touching the border: values outside the grid count as below the level, so the loop closes around the grid.
        val full = FloatArray(4) { 1f }
        val edge = Raster.contours(full, 2, 2, 0.5f)
        assertEquals(1, edge.size)
        assertEquals(-3.5, edge[0].signedArea, 1e-6)
    }

    @Test
    fun fillHolesFillsOnlyEnclosedBackground() {
        val w = 7
        val mask = BooleanArray(w * w) { i ->
            val x = i % w; val y = i / w
            (x in 1..5 && (y == 1 || y == 5)) || (y in 1..5 && (x == 1 || x == 5))
        }
        val filled = Raster.fillHoles(mask, w, w)
        assertTrue(filled[3 * w + 3])
        assertTrue(!filled[0])
        assertEquals(25, filled.count { it })
    }

    @Test
    fun boxResamplingKeepsCoverage() {
        // A 4 x 4 opaque square scaled by 0.5 lands on 2 x 2 opaque pixels; at an offset of half a pixel the edges
        // are half covered.
        val src = FloatArray(16 * 4) { if (it % 4 == 3) 1f else 0.5f }
        val dst = FloatArray(4 * 4 * 4)
        Raster.resampleBox(src, 4, 4, dst, 4, 4, 0.5, 1.0, 1.0)
        val alpha = FloatArray(16) { dst[it * 4 + 3] }
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f), alpha, 1e-5f)
        val dst2 = FloatArray(4 * 4 * 4)
        Raster.resampleBox(src, 4, 4, dst2, 4, 4, 0.5, 1.5, 1.0)
        assertEquals(0.5f, dst2[(1 * 4 + 1) * 4 + 3], 1e-5f)
        assertEquals(0.5f, dst2[(1 * 4 + 3) * 4 + 3], 1e-5f)
        assertTrue(abs(dst2[(1 * 4 + 2) * 4 + 3] - 1f) < 1e-5f)
    }

    @Test
    fun labMatchesTheMockupFormula() {
        val out = DoubleArray(3)
        Raster.lab(0xFFFFFF, out)
        assertEquals(100.0, out[0], 0.05)
        assertEquals(0.0, out[1], 0.05)
        assertEquals(0.0, out[2], 0.05)
        Raster.lab(0xFF6F8E, out) // strawberry mid
        assertEquals(65.42, out[0], 0.05)
        assertEquals(57.48, out[1], 0.05)
        assertEquals(10.92, out[2], 0.05)
        assertTrue(out[1] > 50)
    }
}

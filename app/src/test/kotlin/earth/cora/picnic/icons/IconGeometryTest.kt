package earth.cora.picnic.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconGeometryTest {
    private fun pixels(w: Int, h: Int, opaque: (Int, Int) -> Boolean) =
        IntArray(w * h) { i -> if (opaque(i % w, i / w)) 0xFF336699.toInt() else 0x00FFFFFF }

    @Test
    fun boundsOfOpaquePixels() {
        val px = pixels(10, 8) { x, y -> x in 2..5 && y in 3..6 }
        assertEquals(IconGeometry.Bounds(2, 3, 6, 7), IconGeometry.opaqueBounds(px, 10, 8))
    }

    @Test
    fun faintHaloIsIgnored() {
        val px = IntArray(16) { 0x05000000 }.also { it[5] = 0xFF000000.toInt() }
        assertEquals(IconGeometry.Bounds(1, 1, 2, 2), IconGeometry.opaqueBounds(px, 4, 4))
    }

    @Test
    fun fullyTransparentHasNoBounds() {
        assertNull(IconGeometry.opaqueBounds(IntArray(9), 3, 3))
    }

    @Test
    fun fitKeepsAspectWithinTheBox() {
        assertEquals(176 to 88, IconGeometry.fitInside(800, 400, 176))
        assertEquals(88 to 176, IconGeometry.fitInside(300, 600, 176))
        assertEquals(176 to 176, IconGeometry.fitInside(50, 50, 176))
        assertEquals(176 to 1, IconGeometry.fitInside(5000, 1, 176))
    }
}

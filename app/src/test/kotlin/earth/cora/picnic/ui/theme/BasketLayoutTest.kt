package earth.cora.picnic.ui.theme

import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.list.motion.BasketMini
import earth.cora.picnic.ui.theme.gen.BasketManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The minis' layouts (docs/DESIGN_STICKER.md §5.3, §4.12; item 106): 1–5 in fill-order slots, newest on top, "+n" above 5. */
class BasketLayoutTest {
    private fun minis(n: Int) = (1..n).map { BasketMini("m$it", IconRef("strawberry")) }

    @Test
    fun layoutsForCounts() {
        assertEquals(0, BasketLayout.fill(emptyList()).shown.size)
        assertTrue("empty: no slots (the landing spot shows)", BasketLayout.fill(emptyList()).slots.isEmpty())
        for (n in 1..5) {
            val f = BasketLayout.fill(minis(n))
            assertEquals("$n gotten: all shown", n, f.shown.size)
            assertEquals("$n gotten: one slot each", n, f.slots.size)
            assertEquals(0, f.extra)
            assertEquals("the $n-layout is the manifest's", BasketManifest.MINI_SLOTS.getValue(n), f.slots)
        }
        // Slots spread left to right in a row, the raised back row (4 and 5) behind.
        val three = BasketLayout.fill(minis(3)).slots
        assertTrue(three[0].centreX < three[1].centreX && three[1].centreX < three[2].centreX)
        val five = BasketLayout.fill(minis(5)).slots
        assertTrue("the 4th and 5th sit in the raised back row", five[3].raise < 0 && five[4].raise < 0)
        for (n in 6..7) {
            val f = BasketLayout.fill(minis(n))
            assertEquals("$n gotten: the newest five", listOf("m${n - 4}", "m${n - 3}", "m${n - 2}", "m${n - 1}", "m$n"), f.shown.map { it.itemId })
            assertEquals("\"+${n - 5}\"", n - 5, f.extra)
        }
    }

    @Test
    fun newestIsDrawnLast() {
        val f = BasketLayout.fill(minis(4))
        assertEquals("check-off order: the newest is drawn on top", "m4", f.shown.last().itemId)
    }
}

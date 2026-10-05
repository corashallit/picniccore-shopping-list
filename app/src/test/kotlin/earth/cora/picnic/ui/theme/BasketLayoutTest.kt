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
        // §5.3's own numbers, in fill order (tilt in degrees; raise in viewport units, negative = up).
        fun tilts(n: Int) = BasketLayout.fill(minis(n)).slots.map { it.tilt }
        fun raises(n: Int) = BasketLayout.fill(minis(n)).slots.map { it.raise }
        assertEquals("1: upright", listOf(0f), tilts(1))
        assertEquals("2: tilted ±7°", listOf(-7f, 7f), tilts(2))
        assertEquals("2: level", listOf(0f, 0f), raises(2))
        assertEquals("3: fanned −9° / 0 / +9°", listOf(-9f, 0f, 9f), tilts(3))
        // The outer two sit 1.5 off the middle one's line. The spec says "raised"; WP2's art (and the frozen mockup)
        // drops them 1.5 instead, a fan held in the hand. The magnitude is the spec's; the sign is WP2's (to the lead).
        assertEquals("3: the outer two 1.5 off the middle", listOf(1.5f, 0f, 1.5f), raises(3).map { kotlin.math.abs(it) })
        assertEquals("4: two front (−9° / +9°) and two raised back (−4° / +4°)", listOf(-9f, 9f, -4f, 4f), tilts(4))
        assertEquals("4: the back row raised 6", listOf(-6f, -6f), raises(4).drop(2))
        assertEquals("5: three front and two back", listOf(-9f, 0f, 9f, -4f, 4f), tilts(5))
        assertEquals("5: the back row raised 7", listOf(-7f, -7f), raises(5).drop(3))
        // The back row sits behind and between the front stickers.
        val four = BasketLayout.fill(minis(4)).slots
        assertTrue(four[0].centreX < four[2].centreX && four[3].centreX < four[1].centreX)
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

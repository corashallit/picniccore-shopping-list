package earth.cora.picnic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §7.4's layout examples (item 145), each rechecked against the formula. */
class BookLayoutTest {
    @Test
    fun mockupPhone() {
        val l = BookLayout.of(336f, 8, 24)
        assertEquals(24f, l.stampSize)
        assertEquals(4, l.stampCols)
        assertEquals(2, l.stampRows)
        assertEquals("stamp page", 117f, l.leftWidth)
        assertEquals(201f, l.rightWidth)
        assertEquals(8, l.cols)
        assertEquals(3, l.rows)
        assertEquals(24, l.perPage)
        assertEquals(1, l.pages)
        assertEquals(76f, l.height)
    }

    @Test
    fun thirtySpotsTurnAPage() {
        val l = BookLayout.of(336f, 8, 30)
        assertEquals(2, l.pages)
        assertEquals("the 25th spot is on page 2", 1, l.pageOf(24))
        assertEquals("shows the page of the lowest free spot", 1, l.shownPage((0 until 25).toSet()))
        assertEquals("a full book shows its last page", 1, l.shownPage((0 until 30).toSet()))
        assertEquals(0, l.shownPage(setOf(0, 1, 2)))
    }

    @Test
    fun widePhone() {
        val l = BookLayout.of(BookLayout.widthFor(411f), 8, 24)
        assertEquals(387f, l.width)
        assertEquals(252f, l.rightWidth)
        assertEquals(10, l.cols)
        assertEquals(30, l.perPage)
    }

    @Test
    fun fourteenStops() {
        val l = BookLayout.of(336f, 14, 24)
        assertEquals(20f, l.stampSize)
        assertEquals(7, l.stampCols)
        assertEquals(170f, l.leftWidth)
        assertEquals(148f, l.rightWidth)
        assertEquals(5, l.cols)
        assertEquals(15, l.perPage)
        assertEquals(76f, l.height)
    }

    @Test
    fun twentyStopsTakeThreeRows() {
        val l = BookLayout.of(336f, 20, 24)
        assertEquals(3, l.stampRows)
        assertEquals(7, l.stampCols)
        assertEquals(170f, l.leftWidth)
        assertEquals(102f, l.height)
        assertEquals(5, l.cols)
        assertEquals(4, l.rows)
        assertEquals(20, l.perPage)
    }

    @Test
    fun oneStopKeepsFourColumns() {
        val l = BookLayout.of(336f, 1, 24)
        assertEquals(4, l.stampCols)
        assertEquals(117f, l.leftWidth)
        assertEquals(24, l.perPage)
    }

    @Test
    fun aboveTwentyFourStopsShowsPlusN() {
        val l = BookLayout.of(336f, 30, 40)
        assertEquals(24, l.circles)
        assertEquals("23 circles + \"+7\"", 7, l.moreStops)
        assertEquals(8, l.stampCols)
    }

    @Test
    fun widthIsCappedAndCentred() {
        assertEquals(336f, BookLayout.widthFor(360f))
        assertEquals(456f, BookLayout.widthFor(800f))
    }

    /**
     * The collapsed strip's stamps (§7.4, item 161): every stop as a 12 dp dot up to 8 stops when they fit, 10 dp dots
     * above 8 or when 12 dp ones do not fit, and otherwise a run that keeps the now stop with "+n" pills for the rest,
     * never wider than its room and never losing a stop.
     */
    @Test
    fun collapsedDotsFitTheirRoom() {
        val pill = 20f
        // 8 stops in plenty of room: the 12 dp dots on 3 dp gaps.
        val roomy = CollapsedDots.plan(8, 2, 200f, pill)
        assertEquals(CollapsedDots(12f, 3f, 0, 8, 0, 0), roomy)
        assertEquals(117f, roomy.width(pill))
        // 8 stops in 100 dp (large text): the 10 dp dots, all of them.
        assertEquals(CollapsedDots(10f, 2f, 0, 8, 0, 0), CollapsedDots.plan(8, 2, 100f, pill))
        // 14 stops always take the small dots; 166 dp holds all 14.
        assertEquals(CollapsedDots(10f, 2f, 0, 14, 0, 0), CollapsedDots.plan(14, 3, 166f, pill))
        for (stops in 1..30) for (anchor in 0 until stops) for (room in listOf(40f, 64f, 90f, 120f, 150f, 230f)) {
            val p = CollapsedDots.plan(stops, anchor, room, pill)
            val tag = "$stops stops, now $anchor, $room dp: $p"
            assertEquals("every stop is a dot or counted in a pill: $tag", stops, p.count + p.hiddenBefore + p.hiddenAfter)
            assertEquals("the run starts after the stops before it: $tag", p.hiddenBefore, p.first)
            assertTrue("the now stop is a dot: $tag", anchor in p.first until p.first + p.count)
            assertTrue("at least one dot: $tag", p.count >= 1)
            // Only a room too small for one dot and a pill on each side may overflow.
            if (room >= 2 * pill + 2 * 2f + 10f) assertTrue("fits its room: $tag (${p.width(pill)} dp)", p.width(pill) <= room + 0.01f)
        }
    }

    /** The windowed run in 120 dp: the first stops, or the now stop with the stop before it and pills either side, or the last. */
    @Test
    fun collapsedDotsWindowAroundNow() {
        val pill = 20f
        val start = CollapsedDots.plan(14, 0, 120f, pill)
        assertEquals("the first stops, then a pill for the rest", CollapsedDots(10f, 2f, 0, 8, 0, 6), start)
        val middle = CollapsedDots.plan(24, 12, 120f, pill)
        assertEquals("a pill, the stop before now, now, what is next, a pill", CollapsedDots(10f, 2f, 11, 6, 11, 7), middle)
        assertEquals(114f, middle.width(pill))
        val end = CollapsedDots.plan(14, 13, 120f, pill)
        assertEquals("a pill, then the last stops", CollapsedDots(10f, 2f, 6, 8, 6, 0), end)
    }
}

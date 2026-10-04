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
        assertEquals("the page number's two cells come off each page", 22, l.perPage)
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
        assertEquals(15, l.cells)
        assertEquals("24 spots run to two pages: each keeps its last two cells for the page number", 13, l.perPage)
        assertEquals(2, l.pages)
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
        assertEquals(20, l.cells)
        assertEquals(18, l.perPage)
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

    /**
     * The trip review (§7.3, item 146): after the finale the book shows its full first sheet; PER-FECT! is pressed where
     * it covers no sticker and no stamp (on spare release paper, else in the spine's gutter); the page number keeps its
     * own cells inside the page.
     */
    @Test
    fun afterTheFinaleTheFirstSheetAndAPerfectClearOfEverySticker() {
        // 25 stickers: two pages, the first full.
        val l = BookLayout.of(336f, 8, 25)
        assertEquals(2, l.pages)
        assertEquals("finished: the first sheet", 0, l.shownPage((0 until 25).toSet(), finished = true))
        assertEquals("not yet: the last page", 1, l.shownPage((0 until 25).toSet()))
        for ((book, page) in listOf(l to 0, l to 1, BookLayout.of(336f, 8, 24) to 0, BookLayout.of(336f, 8, 6) to 0, BookLayout.of(387f, 14, 30) to 0)) {
            val p = book.perfectSpot(page)
            val hanko = Rect(p.left, p.top, p.left + p.size, p.top + p.size)
            val onPage = minOf(book.spots, (page + 1) * book.perPage) - page * book.perPage
            for (i in 0 until onPage) {
                val col = i % book.cols
                val row = i / book.cols
                // The twin as drawn: 26 dp, centred in its 23 x 22 dp cell.
                val cx = book.leftWidth + BookLayout.SPINE + 7f + col * BookLayout.SPOT_W + BookLayout.SPOT_W / 2f
                val cy = book.spotsTop + row * BookLayout.SPOT_H + BookLayout.SPOT_H / 2f
                val twin = Rect(cx - 13f, cy - 13f, cx + 13f, cy + 13f)
                assertTrue("${book.spots} spots, page $page: PERFECT $hanko clear of sticker $i $twin", !hanko.overlaps(twin))
            }
            val top = stampTop(true, book)
            for (i in 0 until book.circles) {
                val (x, y) = stampCentre(i, book, top)
                val stamp = Rect(x - book.stampSize / 2f, y - book.stampSize / 2f, x + book.stampSize / 2f, y + book.stampSize / 2f)
                assertTrue("${book.spots} spots: PERFECT $hanko clear of stamp $i $stamp", !hanko.overlaps(stamp))
            }
            assertTrue("inside the page (${p.top}, ${p.size})", p.top >= 0f && p.top + p.size <= book.height - BookLayout.BORDERS)
            assertTrue("a readable size (${p.size} dp)", p.size >= 20f)
        }
        assertEquals("spare paper on a short one-page book: the full 40 dp", 40f, BookLayout.of(336f, 8, 6).perfectSpot(0).size, 0f)
    }

    private fun Rect.overlaps(o: Rect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    private data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float)

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
        // 8 stops in 112 dp (a date tag with its season mark): the 12 dp dots on 2 dp gaps.
        assertEquals(CollapsedDots(12f, 2f, 0, 8, 0, 0), CollapsedDots.plan(8, 2, 112f, pill))
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

    /** §7.4: above font scale 1.3 the page LCD's pitch follows the font scale, up to 1.6×; at 1.3 and below it stays 1.2 dp. */
    @Test
    fun pageLcdPitchFollowsLargeText() {
        assertEquals(1f, pageLcdScale(1f))
        assertEquals(1f, pageLcdScale(1.3f))
        assertEquals(1.5f, pageLcdScale(1.5f))
        assertEquals(1.6f, pageLcdScale(2f))
    }
}

package earth.cora.picnic.ui.theme

import org.junit.Assert.assertEquals
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
}

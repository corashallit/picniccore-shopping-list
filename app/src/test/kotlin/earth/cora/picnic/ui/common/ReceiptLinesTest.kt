package earth.cora.picnic.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The receipt's lines (§13.2): 21 columns, dot leaders, line 2 fitted, the store word. */
class ReceiptLinesTest {
    private val oct3 = LocalDate.of(2026, 10, 3)
    private val sep26 = LocalDate.of(2026, 9, 26)

    @Test
    fun theSpecReceipt() {
        val lines = ReceiptLines.lines("Kirakira Basket", oct3, "Hillcrest Market", "Saturday shop", stickers = 24, baskets = 8, minutes = 41, shiny = 1)
        assertEquals(
            listOf(
                "KIRAKIRA ★ BASKET",
                "SAT 3 OCT · HILLCREST",
                "---------------------",
                "STICKERS ......... 24",
                "BASKETS ........... 8",
                "TIME ......... 41 MIN",
                "SHINY ............. 1",
                "---------------------",
                "THANK YOU ♪",
            ),
            lines,
        )
        assertTrue("no line is wider than 21 columns", lines.all { it.length <= ReceiptLines.COLUMNS })
    }

    /** A two-digit day leaves no room for "HILLCREST" with the weekday: the weekday goes first. */
    @Test
    fun twoDigitDayDropsTheWeekday() {
        assertEquals("26 SEP · HILLCREST", ReceiptLines.dateLine(sep26, "Hillcrest"))
        assertEquals("SAT 26 SEP · ZEHRS", ReceiptLines.dateLine(sep26, "Zehrs"))
    }

    /** A long store word is cut to the budget once the weekday is gone. */
    @Test
    fun longNamesAreCut() {
        val line = ReceiptLines.dateLine(sep26, "Supercalifragilistic")
        assertEquals("26 SEP · SUPERCALIFRA", line)
        assertEquals(ReceiptLines.COLUMNS, line.length)
    }

    /** "My store" has no store word: the list title's first word stands in; nothing at all, the date alone. */
    @Test
    fun myStoreUsesTheListTitle() {
        assertEquals("Saturday", ReceiptLines.storeWord("My store", "Saturday shop"))
        assertEquals("Hillcrest", ReceiptLines.storeWord("The Hillcrest Market", "Saturday shop"))
        assertEquals("Lapointe", ReceiptLines.storeWord("Épicerie Lapointe", ""))
        assertNull(ReceiptLines.storeWord("My store", ""))
        assertEquals("SAT 3 OCT", ReceiptLines.dateLine(oct3, null))
    }

    /** Accents fold to the LCD's alphabet, one character per column. */
    @Test
    fun accentsFold() {
        assertEquals("SAT 3 OCT · ECOLE", ReceiptLines.dateLine(oct3, "École"))
        assertEquals("OEUFS", ReceiptLines.fold("Œufs"))
    }

    /** No shop time (a migrated trip): "--"; long times keep 21 columns. */
    @Test
    fun timeAndLeaders() {
        assertEquals("TIME ............. --", ReceiptLines.leader("TIME", ReceiptLines.time(null)))
        assertEquals("TIME ...... 1 H 5 MIN", ReceiptLines.leader("TIME", ReceiptLines.time(65)))
        for (n in listOf(0, 1, 9, 24, 140, 2000)) assertEquals(ReceiptLines.COLUMNS, ReceiptLines.leader("STICKERS", n.toString()).length)
    }
}

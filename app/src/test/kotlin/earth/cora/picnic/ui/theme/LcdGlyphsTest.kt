package earth.cora.picnic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** LcdText prints every string the app can produce with no missing glyph; accents fold (§13.1; checklist 262). */
class LcdGlyphsTest {
    private fun assertPrints(text: String) = assertTrue("cannot print '$text' (${lcdFold(text)})", lcdCanPrint(text))

    @Test
    fun countsTimersAndPages() {
        for (total in listOf(0, 1, 9, 10, 24, 99, 100, 999)) for (count in 0..total step maxOf(1, total / 7)) {
            assertPrints("$count/$total★")
        }
        for (m in 0..59 step 7) for (s in 0..59 step 5) assertPrints("$m:%02d".format(s))
        for (p in 1..9) assertPrints("$p/9")
    }

    @Test
    fun words() {
        for (w in listOf("FED", "SNACK?", "BONUS★", "SYNC", "PRINT★", "OK", "OK.", "OK..", "PERFECT!", "REC", "BONUS STAGE! ♪")) assertPrints(w)
    }

    @Test
    fun receiptLines() {
        val weekdays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        for (d in weekdays) for (m in months) assertPrints("$d 31 $m · HILLCREST")
        for (line in listOf(
            "KIRAKIRA ★ BASKET", "---------------------", "STICKERS ......... 24", "BASKETS ........... 8",
            "TIME ......... 41 MIN", "TIME ............. --", "SHINY ............. 1", "THANK YOU ♪",
            "TIME ....... 1 H 5 MIN",
        )) assertPrints(line)
        // Any store name, folded: accents, ligatures, punctuation.
        for (store in listOf(
            "Épicerie Dépanneur St-Hubert", "Œufs & Cie", "L'Ami's #1 Market (east)", "Marché Jean-Talon",
            "Hillcrest Market, Inc.", "Façade 100% bio", "Ünïcode Straße", "café “déjà vu” — 24h",
        )) assertPrints(store)
    }

    @Test
    fun folding() {
        assertEquals("EPICERIE", lcdFold("épicerie"))
        assertEquals("CAFE", lcdFold("Café"))
        assertEquals("OEUFS", lcdFold("Œufs"))
        assertEquals("FACADE", lcdFold("façade"))
        assertEquals("STRASSE", lcdFold("Straße"))
        assertEquals("A-B", lcdFold("a–b"))
        // Anything else prints as a space (and never as a missing glyph).
        val cells = lcdCells("A☃B")
        assertEquals(3, cells.size)
        assertTrue(cells[1].glyph != null && cells[1].extra == null)
    }

    @Test
    fun theAppBarsRoomIsTheSameBeforeAndAfterThePetHatches() {
        val words = listOf("SYNC", "FED", "SNACK?", "PRINT★", "BONUS★")
        for ((c, t) in listOf(0 to 1, 3 to 24, 10 to 24, 100 to 120)) {
            val before = lcdSlot(LcdState(LcdMode.Count, count = c, total = t), words, petRoom = true)
            assertEquals("$c/$t", before, lcdSlot(LcdState(LcdMode.Pet, count = c, total = t), words, petRoom = true))
            assertEquals("$c/$t", before, lcdSlot(LcdState(LcdMode.Text, count = c, total = t, text = "SYNC", petLives = true), words, petRoom = true))
        }
    }

    @Test
    fun extrasAreVectors() {
        assertEquals(LcdExtra.Star, lcdCells("★").single().extra)
        assertEquals(LcdExtra.Heart, lcdCells("♡").single().extra)
        assertEquals(LcdExtra.Moon, lcdCells("●").single().extra)
        assertEquals(LcdExtra.Star, lcdCells("☆").single().extra)
    }
}

package earth.cora.picnic.assets

import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checklist items 19 and 21 (WP2, docs/DESIGN_STICKER.md §2.7): every bundled face covers the Latin + French subset,
 * Zen Maru 500 and 700 cover キ ラ き ら (so "キラキラ" echoes in the field), `res/font` stays within 1.5 MB, and the five
 * OFL texts (the four bundled families and DotGothic16, the source of the LCD glyphs) are in `assets/licenses` with
 * the index the About page lists. (The About page itself is WP8's `AboutScreenTest.licencesListed`.)
 */
@RunWith(RobolectricTestRunner::class)
class FontSubsetTest {
    private val fontDir = File("src/main/res/font")
    private val licenceDir = File("src/main/assets/licenses")

    @Test
    fun everyFaceCoversLatinAndFrench() {
        for (file in fontDir.listFiles().orEmpty()) {
            val have = TrueTypeFile(file).codePoints
            val missing = REQUIRED.filter { it !in have }.map { "U+%04X %s".format(it, String(Character.toChars(it))) }
            assertTrue("${file.name} lacks $missing", missing.isEmpty())
        }
    }

    @Test
    fun zenMaruCoversTheKiraKana() {
        for (name in listOf("zen_maru_gothic_medium.ttf", "zen_maru_gothic_bold.ttf")) {
            val have = TrueTypeFile(File(fontDir, name)).codePoints
            assertTrue(name, "キラきら".codePoints().toArray().all { it in have })
        }
    }

    @Test
    fun fontsStayWithinBudget() {
        val total = fontDir.listFiles().orEmpty().sumOf { it.length() }
        assertTrue("res/font is $total bytes", total <= 1_500_000)
    }

    @Test
    fun theFiveOflTextsAreBundled() {
        val index = JSONArray(File(licenceDir, "licenses.json").readText())
        val families = (0 until index.length()).map { index.getJSONObject(it).getString("family") }
        assertEquals(listOf("DynaPuff", "Zen Maru Gothic", "Klee One", "Hachi Maru Pop", "DotGothic16"), families)
        for (i in 0 until index.length()) {
            val entry = index.getJSONObject(i)
            val text = File(licenceDir, entry.getString("file")).readText()
            val family = entry.getString("family")
            // "Copyright 2020 The Klee Project Authors (…)": the family's first word names the project
            assertTrue("$family copyright", text.startsWith("Copyright") && family.split(" ").first() in text.lineSequence().first())
            assertTrue("$family licence", "SIL OPEN FONT LICENSE Version 1.1" in text)
            assertEquals("SIL Open Font License 1.1", entry.getString("licence"))
            assertTrue(entry.getString("use").isNotBlank())
        }
        assertEquals(6, licenceDir.listFiles().orEmpty().size)
    }

    companion object {
        private const val FRENCH = "ÀÂÄÇÈÉÊËÎÏÔÖÙÛÜàâäçèéêëîïôöùûüÿŒœ«»"

        /** printable ASCII, NBSP, French letters, guillemets, curly quotes, dashes, …, •, ·, × (§2.7) */
        val REQUIRED: List<Int> = ((0x20..0x7E) + 0xA0 + FRENCH.codePoints().toArray().toList() +
            listOf(0x2018, 0x2019, 0x201C, 0x201D, 0x2013, 0x2014, 0x2026, 0x2022, 0xB7, 0xD7)).distinct()
    }
}

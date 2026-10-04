package earth.cora.picnic.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
 * Source scans for the theme rules (docs/DESIGN_STICKER.md §2.10, §2.11, §2.7; checklist 11, 13, 27).
 */

private val mainSources: File = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).first { it.isDirectory }
private val mainRes: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

private fun kotlinUnder(dir: String): List<File> =
    File(mainSources, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

/** No elevation, `shadow()` or blur anywhere under ui/: every shadow is a hard offset (checklist 11). */
class NoElevationLintTest {
    @Test
    fun noSoftShadows() {
        val offenders = mutableListOf<String>()
        // An explicit zero (`tonalElevation = 0.dp`, turning Material's off) is allowed.
        val elevation = Regex("""\b\w*[Ee]levation\s*=(?!\s*0\.dp\b)""")
        val banned = listOf(Regex("""\.shadow\("""), Regex("""\bblur\("""), Regex("""RenderEffect"""), Regex("""BlurMaskFilter"""))
        for (f in kotlinUnder("earth/cora/picnic/ui")) {
            f.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//")
                if (elevation.containsMatchIn(code) || banned.any { it.containsMatchIn(code) }) offenders += "${f.name}:${i + 1}: ${line.trim()}"
            }
        }
        assertTrue("soft shadows / blur:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}

/** Holo is referenced only by the reward surfaces of §2.11 (checklist 13). */
class HoloUsageTest {
    /** The files allowed to draw holo: the theme's reward pieces and the reward surfaces other packages own. */
    private val allowed = setOf(
        "Holo.kt", // the brushes themselves
        "ItemIcon.kt", // shiny / jackpot stickers, holo album pockets, the kira decor sticker
        "Components.kt", // the "shiny!" / "jackpot!" chip
        "ThemePreviews.kt", // the holo reward-card sample (holo_card_frames)
        "StickerBook.kt", // WP5: the next sticker spot, the full book's release paper and fore-edge, the PERFECT page
        "TripBookSheet.kt", // WP5: the same book at 2× (its PERFECT page and full release paper), shiny stickers
        "NextStopCard.kt", // WP6: the Next card's static foil
        "Celebration.kt", // WP6: title glint, stamp card back, bonus card
        "AlbumScreen.kt", "AlbumPieces.kt", // WP8: holo pockets, the perfect-week card
        "BonusStage.kt", "Jackpot.kt", // WP9
    )

    @Test
    fun onlyRewardSurfaces() {
        val call = Regex("""\b(holoBrush|stickerHoloBrush)\(""")
        val offenders = kotlinUnder("earth/cora/picnic/ui")
            .filter { it.name !in allowed && it.readLines().any { l -> call.containsMatchIn(l.substringBefore("//")) } }
            .map { it.path }
        assertTrue("holo outside the reward surfaces: $offenders", offenders.isEmpty())
    }
}

/** Hearts, stars and notes are inline vectors, never glyphs; no circled digits; no monospace (checklist 27). */
class GlyphUsageTest {
    @Test
    fun noCircledDigitsOrMonospace() {
        val circled = Regex("[①-⑳⓪⓵-⓾❶-➓]")
        val offenders = mutableListOf<String>()
        mainRes.walkTopDown().filter { it.isFile && it.extension == "xml" && it.parentFile.name.startsWith("values") }.forEach { f ->
            if (circled.containsMatchIn(f.readText())) offenders += f.name
        }
        kotlinUnder("earth/cora/picnic").forEach { f ->
            val text = f.readText()
            if (circled.containsMatchIn(text)) offenders += f.name
            if ("FontFamily.Monospace" in text) offenders += "${f.name} (monospace)"
        }
        assertTrue("circled digits / monospace: $offenders", offenders.isEmpty())
    }

    @Test
    fun themeNeverTypesAGlyphIntoText() {
        // Theme code renders copy through PicnicText (inline vectors); a literal ♡ ☆ ★ ♪ inside a Text( call is a glyph.
        val literal = Regex("""\bText\(\s*"[^"]*[♡☆★♪]""")
        val offenders = kotlinUnder("earth/cora/picnic/ui/theme").filter { literal.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("glyph literals in Text(): $offenders", offenders.isEmpty())
    }

    @Test
    fun inlineGlyphsAreDroppedForTalkBack() {
        assertTrue(spokenWithoutGlyphs("♡ let's go shopping ♡") == "let's go shopping")
        assertTrue(spokenWithoutGlyphs("Added 5 items to Saturday shop ♡") == "Added 5 items to Saturday shop")
    }
}

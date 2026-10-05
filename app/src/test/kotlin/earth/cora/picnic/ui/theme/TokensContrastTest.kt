package earth.cora.picnic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text/background pair the theme uses meets its §2 ratio (WCAG 2.x; docs/DESIGN_STICKER.md §2, §15, checklist 5
 * and 81). Expected values are the spec's, with a 0.02 rounding allowance.
 */
class TokensContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun assertAtLeast(name: String, fg: Color, bg: Color, min: Double) {
        val r = ratio(fg, bg)
        assertTrue("$name contrast %.2f < %.2f".format(r, min), r >= min - 0.02)
    }

    @Test
    fun dayTextPairs() {
        val c = DayColors
        assertAtLeast("ballpoint/pad", c.ballpoint, c.pad, 11.72)
        assertAtLeast("ballpoint/binder", c.ballpoint, c.binder, 10.48)
        assertAtLeast("ballpoint/highlighter", c.ballpoint, c.highlighter, 10.39)
        assertAtLeast("ballpoint/lcd", c.ballpoint, c.lcd, 8.58)
        assertAtLeast("ballpoint/backing", c.ballpoint, c.backing, 10.31)
        assertAtLeast("ballpointSoft/pad", c.ballpointSoft, c.pad, 6.21)
        assertAtLeast("ballpointSoft/binder", c.ballpointSoft, c.binder, 5.56)
        assertAtLeast("hanko/pad", c.hanko, c.pad, 5.34)
        assertAtLeast("hanko/binder", c.hanko, c.binder, 4.78)
        assertAtLeast("spot dashes/pad (a UI edge)", c.spotDash, c.pad, 7.40)
        assertAtLeast("hard shadow/binder (a UI edge)", c.stickerShadow, c.binder, 3.47)
    }

    @Test
    fun ballpointOnEveryPastel() {
        for (f in Families.all) assertAtLeast("ballpoint/${f.name} pastel", DayColors.ballpoint, f.pastel, 7.8)
    }

    @Test
    fun traceRules() {
        for (f in Families.all) {
            assertAtLeast("${f.name} trace/white (any size)", f.trace, Color.White, 4.81)
            // Trace on its own pastel only at ≥20 sp bold: the large-text rule.
            assertAtLeast("${f.name} trace/own pastel (large text)", f.trace, f.pastel, 3.0)
        }
    }

    @Test
    fun ballpointSoftNeverOnAPastel() {
        // It falls below 4.5 on the strawberry pastel: the reason the rule exists.
        assertTrue(ratio(DayColors.ballpointSoft, Families.strawberry.pastel) < 4.5)
    }

    @Test
    fun glowTextPairs() {
        val c = GlowColors
        assertAtLeast("ink/page", c.ink, c.pad, 13.60)
        assertAtLeast("ink/binder", c.ink, c.binder, 16.78)
        assertAtLeast("inkSoft/page", c.inkSoft, c.pad, 8.89)
        assertAtLeast("glowStar/binder", c.glowStar, c.binder, 15.06)
        assertAtLeast("hanko/page", c.hanko, c.pad, 5.94)
        assertAtLeast("doodle/page", c.doodle, c.pad, 7.15)
        assertAtLeast("white border/binder", c.stickerWhite, c.binder, 16.78)
        for (f in Families.all) assertAtLeast("ballpoint/${f.name} pastel in Glow", c.ballpoint, f.pastel, 7.8)
    }

    @Test
    fun variantBinders() {
        assertAtLeast("ballpoint/dusk binder", DuskColors.ballpoint, DuskColors.binder, 9.01)
        assertAtLeast("ballpoint/golden binder", GoldenColors.ballpoint, GoldenColors.binder, 9.74)
        assertAtLeast("ballpoint/golden binder bottom", GoldenColors.ballpoint, GoldenColors.binderBottom!!, 7.8)
    }

    @Test
    fun specialSurfaces() {
        val day = DayColors
        assertAtLeast("ballpoint/book release paper", day.ballpoint, day.book.releasePaper, 10.63)
        assertAtLeast("white/glow book paper", Color.White, GlowColors.book.releasePaper, 12.49)
        assertAtLeast("ballpoint/album vinyl", day.ballpoint, day.albumVinyl, 10.15)
        assertAtLeast("ballpoint/receipt", day.ballpoint, day.receiptPaper, 11.14)
        assertAtLeast("hanko/receipt", day.hanko, day.receiptPaper, 5.08)
        assertAtLeast("ballpoint/toast note", day.ballpoint, day.toastNote, 10.39)
        // The pad patch keeps the subtitle readable on every ground.
        for (c in listOf(DayColors, DuskColors, GoldenColors)) assertAtLeast("onPadSoft/patch ${c.variant}", c.onPadSoft, c.patch, 4.5)
        assertAtLeast("onPadSoft/patch Glow", GlowColors.onPadSoft, GlowColors.patch, 4.5)
    }

    @Test
    fun glowBackingPairs() {
        // Glow's sticker-sheet backing is the release paper #2C2A72 (Cora, 4 October; §2.4, §2.4.1).
        val c = GlowColors
        val b = c.backing
        assertAtLeast("ink/backing", c.ink, b, 12.49)
        assertAtLeast("inkSoft/backing", c.inkSoft, b, 8.16)
        assertAtLeast("hanko/backing", c.hanko, b, 5.45)
        assertAtLeast("link/backing", c.link, b, 10.05)
        assertAtLeast("doodle/backing", c.doodle, b, 6.57)
        // UI edges (3 : 1): the add spot's dashes and the switch's off ring, the selected sticker cell's lemon ring, the
        // focused field's strawberry line, the white border and focus ring.
        assertAtLeast("periwinkle-mid dashes/backing", Families.periwinkle.mid, b, 5.53)
        assertAtLeast("lemon-mid ring/backing", Families.lemon.mid, b, 9.15)
        assertAtLeast("strawberry-mid focus/backing", Families.strawberry.mid, b, 4.71)
        assertAtLeast("white border/backing", c.stickerWhite, b, 12.49)
        // On the page and the rule navy (a pad card on the backing, a busy Add tab).
        assertAtLeast("ink/page", c.ink, c.pad, 13.60)
        assertAtLeast("inkSoft/page", c.inkSoft, c.pad, 8.89)
        assertAtLeast("strawberry-mid focus/page", Families.strawberry.mid, c.pad, 5.13)
        assertAtLeast("ink/disabled face", c.ink, c.disabledFace, 8.94)
        assertAtLeast("onDisabledFace/disabled face", c.onDisabledFace, c.disabledFace, 5.84)
        // A slab, not a hole: the backing reads apart from the page and binder by its white frame, not its own edge.
        assertTrue("backing/page stays decorative", ratio(b, c.pad) < 1.2)
        assertTrue("backing/binder stays decorative", ratio(b, c.binder) < 1.5)
    }

    @Test
    fun currentAisleTab() {
        // The open tray's tab, the pinned plate and the Next card name are ballpoint: ≥7.85 on every pastel, the
        // strongest text on the list (§2.7, §15).
        for (f in Families.rotation) assertAtLeast("current tab ${f.name}", DayColors.ballpoint, f.pastel, 7.85)
    }

    @Test
    fun pressedBandsKeepText() {
        assertAtLeast("ballpoint/pressed band", DayColors.ballpoint, DayColors.pressedBand, 10.39)
        assertAtLeast("ink/glow pressed band", GlowColors.ink, GlowColors.pressedBand, 7.0)
        assertAtLeast("inkSoft/glow pressed band", GlowColors.inkSoft, GlowColors.pressedBand.compositeOver(GlowColors.pad), 4.5)
    }

    @Test
    fun stampPictogramsReadOnEveryPage() {
        // §4.13: a stamp is a graphic (3 : 1). On the Glow page (#222861) the traces fall to 1.7–2.8 : 1, so the
        // pictograms ink in the aisle mid there.
        val looks = (1..10).map { AisleLooks.of(it) } + AisleLooks.everythingElse
        for (look in looks) {
            assertAtLeast("stamp ${look.family.name} on the Day page", look.stampInk(ThemeVariant.Day), DayColors.book.page, 3.0)
            assertAtLeast("stamp ${look.family.name} on the Glow page", look.stampInk(ThemeVariant.Glow), GlowColors.book.page, 3.0)
        }
    }

    @Test
    fun basketLidHankoKeepsTheDayVermilion() {
        // §5.3: the lid stamp keeps its Day colour in every variant (baskets keep their colours, §2.4).
        assertEquals(DayColors.hanko, BasketHanko.ink)
    }
}

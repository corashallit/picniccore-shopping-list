package earth.cora.picnic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "The paper decides the ink" (docs/DESIGN_STICKER.md §2.4.1): on every paper a variant's content can stand on, the ink
 * set [paperColors] gives reads (WCAG 2.x), Day keeps its own colours, and Glow's light paper takes the Day inks on
 * Glow's ground and night shadows. Pure JVM.
 */
class PaperTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun assertAtLeast(name: String, fg: Color, bg: Color, min: Double) {
        val r = ratio(fg, bg)
        assertTrue("$name contrast %.2f < %.2f".format(r, min), r >= min - 0.02)
    }

    /** Light papers every variant shares: white, the aisle pastels and mid faces, the notes, the LCD, the vinyl, the receipt. */
    private val shared: List<Pair<String, Color>> = buildList {
        add("white" to Color.White)
        Families.rotation.forEach { add("${it.name} pastel" to it.pastel) }
        add("neutral pastel" to Families.neutral.pastel)
        add("lemon mid" to Families.lemon.mid)
        add("ramune mid" to Families.ramune.mid)
        add("sakura mid" to Families.sakura.mid)
        add("lcd" to DayColors.lcd)
        add("vinyl" to DayColors.albumVinyl)
        add("receipt" to DayColors.receiptPaper)
        add("toast note" to DayColors.toastNote)
    }

    /** A root's own binder, pad and backing, then the shared papers (Glow also has Day's lavender, on the vinyl). */
    private fun papers(root: PicnicColors): List<Pair<String, Color>> = buildList {
        add("binder" to root.binder)
        add("pad" to root.pad)
        add("backing" to root.backing)
        if (root.isDark) add("Day backing" to DayColors.backing)
        addAll(shared)
    }

    private val mids = setOf(Families.ramune.mid, Families.sakura.mid)

    @Test
    fun everyPaperReads() {
        for (root in listOf(DayColors, GlowColors)) {
            for ((name, paper) in papers(root)) {
                val c = paperColors(root, paper)
                val where = "${root.variant} on $name"
                val pastel = paper in FamilyPastels
                assertAtLeast("ink, $where", c.ink, paper, if (pastel) 7.0 else 4.5)
                assertAtLeast("inkSoft, $where", c.inkSoft, paper, 4.5)
                assertAtLeast("onPad, $where", c.onPad, paper, 4.5)
                assertAtLeast("focus ink, $where", paperFocusInk(root, paper), paper, 3.0)
                // A stamp is a graphic (3 : 1); where words are struck or stamped on paper it reads as text (4.5 : 1). No
                // hanko is ever pressed on a mid face (the selected Add tab), where the vermilion falls to 2.8 : 1.
                if (paper !in mids) assertAtLeast("hanko, $where", c.hanko, paper, 3.0)
            }
        }
        val text = listOf(Color.White, DayColors.toastNote, DayColors.receiptPaper)
        for (paper in text) assertAtLeast("hanko on $paper", paperColors(GlowColors, paper).hanko, paper, 4.5)
        for (paper in listOf(GlowColors.pad, GlowColors.backing, GlowColors.binder)) {
            assertAtLeast("Glow hanko on $paper", paperColors(GlowColors, paper).hanko, paper, 4.5)
        }
    }

    @Test
    fun lightAndDarkPaper() {
        for ((name, paper) in shared + ("Day binder" to DayColors.binder) + ("Day backing" to DayColors.backing)) {
            assertTrue("$name is light", isLightPaper(paper))
        }
        for (paper in listOf(GlowColors.binder, GlowColors.pad, GlowColors.backing, GlowColors.disabledFace)) {
            assertFalse("$paper is dark", isLightPaper(paper))
        }
    }

    @Test
    fun dayKeepsItsColours() {
        for ((name, paper) in papers(DayColors)) {
            val c = paperColors(DayColors, paper)
            if (paper in FamilyPastels || paper in FamilyMids) {
                // Only the three fields the aisle labels always took: ballpoint words, soft words ballpoint, pastel rules.
                assertEquals("Day on $name", DayColors.copy(ink = DayColors.ballpoint, inkSoft = DayColors.ballpoint, rule = Families.periwinkle.pastel), c)
            } else {
                assertSame("Day on $name", DayColors, c)
            }
            assertEquals("Day focus ink on $name", DayColors.ballpoint, paperFocusInk(DayColors, paper))
        }
        assertSame(DayColors, paperColors(DayColors, null))
        assertSame(DuskColors, paperColors(DuskColors, Color.White))
        assertSame(GoldenColors, paperColors(GoldenColors, GoldenColors.pad))
    }

    @Test
    fun glowLightPaperKeepsTheNight() {
        for ((name, paper) in shared + ("Day backing" to DayColors.backing)) {
            val c = paperColors(GlowColors, paper)
            assertEquals("variant on $name", ThemeVariant.Glow, c.variant)
            assertFalse("Day inks on $name", c.isDark)
            assertEquals("ballpoint on $name", DayColors.ballpoint, c.ink)
            assertEquals("Day hanko on $name", DayColors.hanko, c.hanko)
            // The ground stays the night's; shadows on light paper are Day's periwinkle (§2.4.1, Cora 5 October).
            assertEquals(GlowColors.binder, c.binder)
            assertEquals(GlowColors.background, c.background)
            assertEquals(GlowColors.binderStripe, c.binderStripe)
            assertEquals(GlowColors.glowStar, c.glowStar)
            assertEquals(GlowColors.scrim, c.scrim)
            assertEquals(DayColors.hardShadow, c.hardShadow)
            assertEquals(DayColors.stickerShadow, c.stickerShadow)
            assertEquals(DayColors.primaryShadow, c.primaryShadow)
            assertSame("Day's shadows on $name", DayShadows, paperShadows(GlowColors, paper))
        }
        // Dark paper and the binder keep Glow's own set (the same object: nothing is provided again).
        assertSame(GlowColors, paperColors(GlowColors, null))
        assertSame(GlowColors, paperColors(GlowColors, GlowColors.pad))
        assertSame(GlowColors, paperColors(GlowColors, GlowColors.backing))
        assertEquals(Color.White, paperFocusInk(GlowColors, GlowColors.backing))
        assertEquals(GlowColors.ink, paperFocusInk(GlowColors, null))
        // ... and the night's shadows; Day, Dusk and Golden keep their own on any paper.
        assertSame(GlowShadows, paperShadows(GlowColors, null))
        assertSame(GlowShadows, paperShadows(GlowColors, GlowColors.backing))
        assertSame(DayShadows, paperShadows(DayColors, Color.White))
        assertSame(GoldenShadows, paperShadows(GoldenColors, Color.White))
    }
}

package earth.cora.picnic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/** Guards the WCAG AA text pairs every palette (placeholder or final art) must keep. */
class TokensContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun assertAa(name: String, fg: Color, bg: Color, min: Double = 4.5) {
        val r = ratio(fg, bg)
        assertTrue("$name contrast %.2f < %.1f".format(r, min), r >= min)
    }

    private fun check(label: String, c: PicnicColors) {
        for ((bgName, bg) in listOf("background" to c.background, "surface" to c.surface, "surfaceRaised" to c.surfaceRaised, "surfaceSunken" to c.surfaceSunken)) {
            assertAa("$label ink/$bgName", c.ink, bg)
            assertAa("$label inkSoft/$bgName", c.inkSoft, bg)
        }
        assertAa("$label inkFaint/surfaceSunken (placeholders)", c.inkFaint, c.surfaceSunken)
        assertAa("$label onPrimary/primary", c.onPrimary, c.primary)
        assertAa("$label onHighlight/highlight", c.onHighlight, c.highlight)
        assertAa("$label error/surface", c.error, c.surface)
        assertAa("$label link/surface", c.link, c.surface)
        // Aisle headers draw their title, "All picked up!" and chevron in deep on pastel, in both
        // palettes (the pastel fills stay light at dusk). Index -1 is the neutral Unsorted/basket family.
        for (i in -1 until c.aisles.size) {
            val a = c.aisle(i)
            assertAa("$label aisle[$i] deep/pastel", a.deep, a.pastel)
        }
        // Outlines and hard shadows are graphics: 3:1 against the ground.
        assertAa("$label outline/background", c.outline, c.background, min = 3.0)
        // The CheckMark box is a UI component: its ink ring needs 3:1 on the row surface (unchecked),
        // and the tick 3:1 on the highlight fill (checked; the AA text pair above already covers it).
        assertAa("$label check ring outline/surface", c.outline, c.surface, min = 3.0)
        // Section inner edge and row rules must stay visible separators on the row surface, measured as
        // drawn (sectionLine carries its alpha, composited over the surface).
        for (i in -1 until c.aisles.size) {
            assertAa("$label aisle[$i] sectionLine/surface", c.sectionLine(i).compositeOver(c.surface), c.surface, min = 2.0)
        }
        // The neutral line sits inside the container's own ink outline: soft enough not to read as a
        // second border.
        val neutralLine = ratio(c.sectionLine(-1).compositeOver(c.surface), c.surface)
        assertTrue("$label neutral sectionLine %.2f > 3.0 (double border)".format(neutralLine), neutralLine <= 3.0)
        // A pressed row keeps its text readable (rows show ink names, inkSoft details and checked names).
        for (i in -1 until c.aisles.size) {
            assertAa("$label aisle[$i] ink/rowPressed", c.ink, c.rowPressed(i))
            assertAa("$label aisle[$i] inkSoft/rowPressed", c.inkSoft, c.rowPressed(i))
            assertAa("$label aisle[$i] check ring/rowPressed", c.outline, c.rowPressed(i), min = 3.0)
        }
        // Unsorted / In the basket must never pass for an aisle: the neutral pastel stays low-chroma and
        // clearly apart from every rotation pastel (CIE76 delta E; the rotation's closest pair is ~4.6).
        val neutral = c.aisle(-1).pastel
        assertTrue("$label neutral pastel chroma %.1f >= 6".format(chroma(neutral)), chroma(neutral) < 6.0)
        for (a in c.aisles) {
            val e = deltaE(neutral, a.pastel)
            assertTrue("$label neutral pastel vs %s: delta E %.1f < 8".format(a.pastel, e), e >= 8.0)
        }
    }

    private fun lab(color: Color): DoubleArray {
        val rgb = color.convert(ColorSpaces.CieLab)
        return doubleArrayOf(rgb.red.toDouble(), rgb.green.toDouble(), rgb.blue.toDouble())
    }

    private fun chroma(color: Color): Double = lab(color).let { hypot(it[1], it[2]) }

    private fun deltaE(a: Color, b: Color): Double {
        val x = lab(a)
        val y = lab(b)
        return sqrt((x[0] - y[0]).pow(2) + (x[1] - y[1]).pow(2) + (x[2] - y[2]).pow(2))
    }

    @Test
    fun noonPalette() = check("noon", PlaceholderLightColors)

    @Test
    fun duskPalette() = check("dusk", PlaceholderDarkColors)
}

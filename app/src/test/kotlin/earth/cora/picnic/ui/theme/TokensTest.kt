package earth.cora.picnic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/** The token values are exactly the spec's (docs/DESIGN_STICKER.md §2.1, §2.2, §2.4, §2.5; checklist 1–4). */
class TokensTest {
    private fun hex(c: Color): String = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private fun assertHex(name: String, expected: String, actual: Color) = assertEquals(name, expected, hex(actual))

    @Test
    fun dayPaletteMatchesSpec() {
        val c = DayColors
        assertHex("binder", "#E6F6EE", c.binder)
        assertHex("binderStripe", "#CDEEDD", c.binderStripe)
        assertHex("pad", "#FFFFFF", c.pad)
        assertHex("rule", "#BCD0FF", c.rule)
        assertHex("margin", "#FF9CC2", c.margin)
        assertHex("stickerWhite", "#FFFFFF", c.stickerWhite)
        assertHex("stickerShadow", "#6E7BD0", c.stickerShadow)
        assertHex("ballpoint", "#25307C", c.ballpoint)
        assertHex("ballpointSoft", "#4F5BA6", c.ballpointSoft)
        assertHex("hanko", "#C8321F", c.hanko)
        assertHex("lcd", "#C9E5B8", c.lcd)
        assertHex("lcdGrid", "#B6D4A4", c.lcdGrid)
        assertHex("highlighter", "#FFF3A6", c.highlighter)
        assertHex("backing", "#EEF0FA", c.backing)
        assertHex("periwinkleTrace (spot)", "#3E4BB0", c.spotDash)
        // v1 names map onto the C values.
        assertEquals(c.binder, c.background)
        assertEquals(c.ballpoint, c.ink)
        assertEquals(c.ballpointSoft, c.inkSoft)
        assertEquals(1f, c.binderStripeAlpha)
        assertEquals(null, c.binderBottom)
    }

    @Test
    fun familiesMatchSpec() {
        val expected = mapOf(
            "strawberry" to listOf("#FFC4D1", "#FF6F8E", "#EC587A", "#C3264F"),
            "peach" to listOf("#FFDCC6", "#FFA477", "#E5885A", "#AE4D1C"),
            "lemon" to listOf("#FFF3A6", "#FFDA4A", "#DAB632", "#8A6800"),
            "melon" to listOf("#D3F4C5", "#8EDB76", "#6FBF62", "#2E8239"),
            "mint" to listOf("#C9F2E3", "#6DD3B0", "#53B594", "#1C7558"),
            "ramune" to listOf("#CCEBFF", "#7CC6F5", "#5EA9DB", "#1E6AA3"),
            "periwinkle" to listOf("#DCE1FF", "#9AA8F5", "#7D8ADF", "#3E4BB0"),
            "grape" to listOf("#E4D6FF", "#B597F2", "#9B7CDE", "#6542B3"),
            "sakura" to listOf("#FFD6E8", "#FF9FC8", "#E67BAA", "#B02E6B"),
            "cocoa" to listOf("#F1DEC9", "#CB9C73", "#B1825B", "#7A4A28"),
            "neutral" to listOf("#EEF0FA", "#C9CFF5", "#AEB6E8", "#4F5BA6"),
        )
        assertEquals(expected.keys, Families.all.map { it.name }.toSet())
        for ((name, hexes) in expected) {
            val f = Families.byName(name)!!
            assertEquals(name, hexes, listOf(hex(f.pastel), hex(f.mid), hex(f.deep), hex(f.trace)))
        }
        assertEquals(
            listOf("strawberry", "ramune", "lemon", "melon", "grape", "peach", "mint", "sakura", "cocoa", "periwinkle"),
            Families.rotation.map { it.name },
        )
    }

    @Test
    fun deepIsMix32() {
        // The ten sticker families; the neutral family's deep (#AEB6E8) is specified as is (§2.2) and is not a 32 % mix.
        for (f in Families.rotation) {
            val mid = f.mid.toArgb()
            val trace = f.trace.toArgb()
            val deep = f.deep.toArgb()
            for (shift in listOf(16, 8, 0)) {
                val m = (mid shr shift) and 0xFF
                val t = (trace shr shift) and 0xFF
                val d = (deep shr shift) and 0xFF
                val mix = (m + 0.32 * (t - m)).roundToInt()
                assertTrue("${f.name} deep channel $shift: $d vs mix $mix", abs(d - mix) <= 1)
            }
        }
    }

    @Test
    fun glowPaletteMatchesSpec() {
        val c = GlowColors
        assertTrue(c.isDark)
        assertHex("binder", "#161A3E", c.binder)
        assertHex("binderStripe", "#1E2350", c.binderStripe)
        assertHex("pad (page)", "#222861", c.pad)
        assertHex("rule", "#3A438A", c.rule)
        assertHex("margin", "#FF7FB0", c.margin)
        assertHex("ink", "#FFFFFF", c.ink)
        assertHex("inkSoft", "#C9CFF5", c.inkSoft)
        assertHex("glowStar", "#C9FFDE", c.glowStar)
        assertHex("hanko", "#FF8A7A", c.hanko)
        assertHex("stickerShadow", "#0B0D24", c.stickerShadow)
        assertHex("puffyStroke", "#3A438A", c.puffyStroke)
        assertHex("doodle", "#FF9FC8", c.doodle)
        assertHex("pressedBand", "#3A438A", c.pressedBand)
        assertHex("spot dashes", "#C9CFF5", c.spotDash)
        assertEquals("the unchecked spot is hollow", 0f, c.spotFill.alpha)
        assertHex("ghost fill", "#3A438A", c.ghostFill)
        assertHex("ghost dashes", "#C9CFF5", c.ghostDash)
        // Text on tray pastels stays ballpoint in the dark; the LCD stays light.
        assertHex("ballpoint", "#25307C", c.ballpoint)
        assertHex("lcd", "#C9E5B8", c.lcd)
    }

    @Test
    fun duskAndGoldenMatchSpec() {
        val d = DuskColors
        assertEquals(ThemeVariant.Dusk, d.variant)
        assertHex("dusk binder", "#E9DDF7", d.binder)
        assertHex("dusk stripes", "#FFFFFF", d.binderStripe)
        assertEquals(0.4f, d.binderStripeAlpha)
        assertEquals(listOf("#FFDCC6", "#E4D6FF", "#DCE1FF"), d.duskBands.map(::hex))

        val g = GoldenColors
        assertEquals(ThemeVariant.Golden, g.variant)
        assertHex("golden binder top", "#FFE7C2", g.binder)
        assertHex("golden binder bottom", "#FFD6E8", g.binderBottom!!)
        assertHex("golden stripes", "#FFDCC6", g.binderStripe)
        assertHex("golden patch", "#FFF1E6", g.patch)
        assertHex("golden shadow", "#E59A7A", g.stickerShadow)
        assertEquals(DpOffset(3.dp, 4.dp), GoldenShadows.hs)
        assertEquals(DpOffset(3.dp, 4.dp), GoldenShadows.hs2)
        assertHex("golden shadow colour", "#E59A7A", GoldenShadows.color)
    }

    @Test
    fun variantsResolveTheirTokens() {
        assertEquals(DayColors, colorsFor(ThemeVariant.Day))
        assertEquals(GlowColors, colorsFor(ThemeVariant.Glow))
        assertEquals(DuskColors, colorsFor(ThemeVariant.Dusk))
        assertEquals(GoldenColors, colorsFor(ThemeVariant.Golden))
        assertEquals(GlowShadows, shadowsFor(ThemeVariant.Glow))
        assertEquals(GoldenShadows, shadowsFor(ThemeVariant.Golden))
        assertEquals(DayShadows, shadowsFor(ThemeVariant.Dusk))
    }

    @Test
    fun dimensMatchSpec() {
        val d = PicnicDimens()
        assertEquals(52.dp, d.sticker)
        assertEquals(52.dp, d.itemIcon)
        assertEquals(30.dp, d.spot)
        assertEquals(40.dp, d.hanko)
        assertEquals(48.dp, d.touch)
        assertEquals(56.dp, d.rowMinHeight)
        assertEquals(64.dp, d.rowMinLarge)
        assertEquals(72.dp, d.appBar)
        assertEquals(56.dp, d.appBarCollapsed)
        assertEquals(84.dp, d.addBar)
        assertEquals(64.dp, d.fab)
        assertEquals(220.dp, d.toast)
        assertEquals(236.dp, d.nextCard)
    }
}

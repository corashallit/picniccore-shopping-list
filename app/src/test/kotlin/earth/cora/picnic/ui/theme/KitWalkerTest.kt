package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.IconRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Walkers over every WP4 component fixture (the kit), in Day and Glow: text colours (checklist 6, 25), inline glyphs
 * (27), decor without semantics (73), and the tab pattern clear of the name (14). Screen fixtures are walked by the
 * same rules in WP10's audits.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class TextColourUsageTest {
    @get:Rule
    val compose = createComposeRule()

    private val kits: List<Pair<String, @Composable ColumnScope.() -> Unit>> = listOf(
        "buttons" to { ButtonsKit() },
        "chips" to { ChipsKit(); SwitchKit() },
        "checkbox" to { CheckboxKit() },
        "fields" to { FieldsKit() },
        "paper" to { ToastState("undo"); ToastState("merged"); DividersKit(); PostageKit(); PunchHolesKit() },
        "lcd" to { LcdKit(); LoadersKit() },
        "empty" to { EmptyKit() },
        "bars" to { AppBarKit(false); SecondaryBarKit(); MenuKit(); SheetKit() },
        "icons" to { ItemIconsKit() },
        "tabs" to { TabPatternsKit(); PuffyKit() },
    )

    private val screen by lazy { SwappableContent(compose) }

    private fun walk(variant: ThemeVariant, kit: @Composable ColumnScope.() -> Unit): List<SemanticsNode> {
        screen.show {
            PicnicTheme(variant) {
                PicnicBackground {
                    Column(Modifier.verticalScroll(rememberScrollState()), content = kit)
                }
            }
        }
        val out = mutableListOf<SemanticsNode>()
        fun visit(n: SemanticsNode) {
            out += n
            n.children.forEach(::visit)
        }
        visit(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out
    }

    private fun layoutOf(n: SemanticsNode): TextLayoutResult? {
        val action = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val results = mutableListOf<TextLayoutResult>()
        return if (action(results)) results.firstOrNull() else null
    }

    // The ten sticker families' fills (the neutral family's mid is Glow's inkSoft #C9CFF5, a text token by design).
    private val pastelsAndMids: Set<Color> = Families.rotation.flatMap { listOf(it.pastel, it.mid) }.toSet()
    // The ten families' traces (the neutral trace is ballpointSoft, governed by its own rule).
    private val traces: Set<Color> = Families.rotation.map { it.trace }.toSet()

    @Test
    fun textColourUsage() {
        for (variant in listOf(ThemeVariant.Day, ThemeVariant.Glow)) for ((name, kit) in kits) {
            for (n in walk(variant, kit)) {
                val layout = layoutOf(n) ?: continue
                val style = layout.layoutInput.style
                val text = layout.layoutInput.text.text
                val color = style.color
                assertTrue("$variant/$name: '$text' in a pastel or mid colour", color !in pastelsAndMids || color == Color.White)
                if (color in traces) {
                    val big = style.fontSize.value >= 20f && (style.fontWeight ?: FontWeight.Normal) >= FontWeight.Bold
                    // Below 20 sp, trace text sits only on white (the drawn number circles repeat a numeral).
                    assertTrue("$variant/$name: trace text '$text' at ${style.fontSize}", big || text.all { it.isDigit() })
                }
            }
        }
    }

    @Test
    fun itemNamesUseBodyFace() {
        for (variant in listOf(ThemeVariant.Day, ThemeVariant.Glow)) {
            walk(variant) { CheckboxKit() }
            for (name in listOf("Strawberry jam", "Orange pekoe tea", "Coffee beans", "Maple syrup")) {
                val layout = layoutOf(compose.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode())!!
                val style = layout.layoutInput.style
                assertEquals(PicnicFonts.zenMaru, style.fontFamily)
                assertEquals(FontWeight.Bold, style.fontWeight)
                assertTrue(style.fontSize.value >= 16f)
            }
        }
    }

    // GlyphUsageTest (walker)
    @Test
    fun noGlyphsRenderedAsText() {
        for ((name, kit) in kits) for (n in walk(ThemeVariant.Day, kit)) {
            val texts = n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                listOfNotNull(layoutOf(n)?.layoutInput?.text?.text)
            for (t in texts) assertTrue("$name: glyph in '$t'", t.none { it in "♡☆★♪" } || n.config.getOrNull(SemanticsActions.GetTextLayoutResult) == null)
        }
    }

    // DecorBudgetTest (the theme's part): decor carries no semantics at all.
    @Test
    fun decorHasNoSemantics() {
        compose.setContent {
            PicnicTheme(ThemeVariant.Glow) {
                PicnicBackground(Modifier.testTag("ground")) {
                    Column {
                        DecorSticker("bee")
                        DecorSticker("ladybug")
                        TwinkleSparkle(Modifier.width(14.dp))
                        BusySparkle(Modifier.width(14.dp))
                        WashiTape(WashiPattern.SakuraStripe)
                        StickerSpot()
                        LandingSpot()
                        SheetHandle()
                        HankoStamp(HankoText.GotIt)
                        CheckMark(true, "seed")
                        ItemIcon(IconRef("apple"), contentDescription = null)
                        FlatStar(Modifier.width(13.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        val ground = compose.onNodeWithTag("ground", useUnmergedTree = true).fetchSemanticsNode()
        val meaningful = listOf(
            SemanticsProperties.Text, SemanticsProperties.ContentDescription, SemanticsProperties.Role,
            SemanticsProperties.StateDescription, SemanticsProperties.ToggleableState, SemanticsProperties.Heading,
        )
        fun offenders(n: SemanticsNode): List<String> = n.children.flatMap { child ->
            val hit = meaningful.filter { child.config.getOrNull(it) != null }.map { it.name } +
                listOfNotNull(child.config.getOrNull(SemanticsActions.OnClick)?.let { "OnClick" })
            hit.map { "${child.id}:$it" } + offenders(child)
        }
        assertEquals("decorative pieces expose no semantics", emptyList<String>(), offenders(ground))
    }

}

/** The tab pattern prints on the right 26 dp, clear of the name (checklist 14). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class TabLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val screen by lazy { SwappableContent(compose) }
    @Test
    fun patternNeverUnderText() {
        for (variant in listOf(ThemeVariant.Day, ThemeVariant.Glow)) {
            screen.show {
                PicnicTheme(variant) {
                    Column {
                        listOf("Produce", "Deli & meat", "Pasta, rice & international").forEachIndexed { i, n ->
                            Box(Modifier.width(260.dp)) { TabSample(i + 1, n, Modifier.testTag("tab$i")) }
                        }
                    }
                }
            }
            compose.waitForIdle()
            listOf("Produce", "Deli & meat", "Pasta, rice & international").forEachIndexed { i, n ->
                val tab = compose.onNodeWithTag("tab$i").getBoundsInRoot()
                val label = compose.onNodeWithText(n, useUnmergedTree = true).getBoundsInRoot()
                // The pattern prints on the right 26 dp of the tab's face (inside its 3 dp border).
                assertTrue("$n runs under the pattern (${label.right} vs ${tab.right})", label.right <= tab.right - 3.dp - 26.dp + 0.5.dp)
            }
        }
    }
}

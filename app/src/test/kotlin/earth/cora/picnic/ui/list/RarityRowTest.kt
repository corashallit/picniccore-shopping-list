package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "shiny!" / "jackpot!" chip in the shipping row (§4.7, §4.11, item 337): it follows the detail and drops under it
 * when the two cannot share a line, so the detail keeps the line breaks it has alone at the text column's width (never
 * "1 L jug / · grade / A / amber" squeezed beside a chip measured first), and the chip keeps its size inside the column.
 * At 1.0 a short detail and its chip share a line.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xxhdpi")
class RarityRowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun layoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    private fun showAt(fontScale: Float) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                PicnicTheme(ThemeVariant.Day) { Box(Modifier.padding(16.dp)) { TrayOf(rarityRows()) } }
            }
        }
        compose.waitForIdle()
    }

    private fun check(fontScale: Float) {
        showAt(fontScale)
        for (id in listOf("jackpot", "shiny", "maple")) {
            val what = "$id at $fontScale"
            val detail = layoutOf(ListTags.detail(id))
            // The text column's width: what the name is laid out in (it sits alone on its line).
            val column = layoutOf(ListTags.name(id)).layoutInput.constraints.maxWidth
            val input = detail.layoutInput
            val alone = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
                .measure(input.text, input.style, constraints = Constraints(maxWidth = column))
            assertEquals("$what: \"${input.text}\" keeps its own lines at the column's $column px", alone.lineCount, detail.lineCount)
            val d = compose.onNodeWithTag(ListTags.detail(id), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val chip = compose.onNodeWithTag(ListTags.rarityChip(id), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val name = compose.onNodeWithTag(ListTags.name(id), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$what: the chip ($chip) stays in the text column (from ${name.left}, $column px)", chip.left >= name.left - 0.5f && chip.right <= name.left + column + 0.5f)
            val sameLine = chip.top < d.bottom && chip.bottom > d.top
            assertTrue("$what: the chip ($chip) overlaps the detail ($d)", !sameLine || chip.left >= d.right - 0.5f)
        }
    }

    @Test
    fun atOneAShortDetailSharesItsLine() {
        check(1f)
        val d = compose.onNodeWithTag(ListTags.detail("shiny"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val chip = compose.onNodeWithTag(ListTags.rarityChip("shiny"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("\"500 g jar\" and its chip share a line at 1.0 ($d, $chip)", chip.top < d.bottom && chip.bottom > d.top)
    }

    @Test
    fun at130TheDetailKeepsItsLines() = check(1.3f)

    @Test
    fun at200TheDetailKeepsItsLines() = check(2f)
}

/**
 * Breakfast with three reward rows (§4.7, §4.11): maple syrup shiny, a jackpot copy with a long detail, and honey made
 * shiny ("500 g jar"). For the rarity row's tests and goldens.
 */
internal fun rarityRows(): SectionUi {
    val base = ListFixture.ui(ListFixture.rowStates()).sections.first { it.id == "breakfast" }
    val maple = base.rows.first { it.id == "maple" }
    val honey = base.rows.first { it.id == "honey" }
    return base.copy(
        rows = listOf(
            maple.copy(id = "jackpot", name = "Pure maple syrup", detail = "1 L jug · grade A amber", rarity = Rarity.JACKPOT),
            honey.copy(id = "shiny", rarity = Rarity.SHINY),
            maple,
        ),
    )
}

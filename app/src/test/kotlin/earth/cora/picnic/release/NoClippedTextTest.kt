package earth.cora.picnic.release

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import earth.cora.picnic.ui.add.lostWords
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.PHONE_411
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 281 (docs/DESIGN_STICKER.md §15, §2.7): every screen of the walker, in every state, shows every word whole at font
 * scale 1.3 and 2.0, the phone's own (non-linear) scaling and the goldens' linear one, Day and Glow, and on the 411 dp
 * phone; and at 1.0. A text is clipped when its layout lost words (a line cut by maxLines, lines taller than the box, an
 * ellipsis, ink past the box: the visual overflow the layout reports, measured against the box the text really has),
 * when it reports TextOverflowed (PuffyText, LcdText and every kit piece that ellipsizes in a drawing of its own), or
 * when a drawn run ends in an ellipsis after a space ("Deli & …": an ellipsis keeps no gap before it). The index tabs
 * never cut a name: one too long for a line wraps (§4.10, §0.4).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class NoClippedTextTest(private val fixture: Fixture) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fontScale100() = check(Combo())

    @Test
    @Config(qualifiers = PHONE_360, fontScale = 1.3f)
    fun fontScale130() = check(Combo(fontScale = 1.3f))

    @Test
    @Config(qualifiers = PHONE_360, fontScale = 2.0f)
    fun fontScale200() = check(Combo(fontScale = 2f))

    @Test
    @Config(qualifiers = PHONE_360, fontScale = 2.0f)
    fun fontScale200Glow() = check(Combo(variant = ThemeVariant.Glow, fontScale = 2f))

    /** The `*_fs200` goldens' linear scale (a density override: big text grows as much as small). */
    @Test
    fun fontScale200Linear() = check(Combo(fontScale = 2f, linear = true))

    /** The wider phone at 200 % (the list's strips and tabs lay out again at 411 dp). */
    @Test
    @Config(qualifiers = PHONE_411, fontScale = 2.0f)
    fun fontScale200Wide() {
        if (fixture.surface == Surface.List) check(Combo(fontScale = 2f))
    }

    private fun check(combo: Combo) {
        val capture = captureFixture(compose, fixture, combo, pixels = false)
        val clipped = clippedTexts(capture)
        assertTrue("${capture.where}: clipped text\n  ${clipped.joinToString("\n  ")}", clipped.isEmpty())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ScreenFixtures.params()

        /** "Deli & …": a space (or a no-break one) left before an ellipsis. */
        private val SPACE_THEN_ELLIPSIS = Regex("""[\s ]…$""")

        /** Every clipped text of [capture], as "words: why". */
        fun clippedTexts(capture: Capture): List<String> {
            val d = capture.density
            val out = ArrayList<String>()
            for (n in capture.nodes) {
                // Name a node with no words of its own (a tab, a puffy title) by the words drawn inside it.
                val label = n.label.takeUnless { it.startsWith("node ") }
                    ?: capture.screen.runs.filter { n.bounds.contains(Offset(it.bounds.centerX(), it.bounds.centerY())) }.joinToString(" ") { it.text }.ifEmpty { n.label }
                for (r in n.layouts) {
                    lostWords(r, d)?.let { out += "\"$label\": $it" }
                    // The layout's lines measured against the box the text really has (its node's size).
                    val widest = (0 until r.lineCount).maxOfOrNull { r.getLineRight(it) - r.getLineLeft(it) } ?: 0f
                    if (widest > n.width + d) out += "\"$label\": a line ${widest.toInt()} px wide in its ${n.width} px box"
                }
                if (n.overflowed) out += "\"$label\": TextOverflowed"
            }
            for (run in capture.screen.runs) {
                // A line Android ellipsizes ends in "…" and zero-width no-break spaces for the letters it dropped.
                val shown = run.text.replace("﻿", "").trimEnd()
                if (SPACE_THEN_ELLIPSIS.containsMatchIn(shown)) out += "\"${run.text.replace("﻿", "")}\": a space before its ellipsis"
            }
            return out.distinct()
        }
    }
}

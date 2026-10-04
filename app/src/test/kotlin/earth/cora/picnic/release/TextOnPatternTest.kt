package earth.cora.picnic.release

import androidx.compose.ui.test.junit4.createComposeRule
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 291 (docs/DESIGN_STICKER.md §4.1, §15): no information text sits straight on a pattern. On every fixture of the
 * walker, Day and Glow, and at the phone's 2.0 (where the words move and grow): every run of text the screen shows is
 * looked at against what was painted under its own letters before it (DrawnScreen.behindOf). A pattern (the binder's
 * and the album's pinstripes, gingham, dots, art) runs three or more separate lines under the letters; a pad, a patch
 * or a card runs none, and words that overlap a patch's edge cross one or two (the edge is not a pattern). The ruled
 * lines a pad's words sit on, and a checked row's strike, are the words' own and left out.
 *
 * Exempt, as §4.1 says: puffy lettering (titles, the wordmark, the hanko's "GOT IT!"), which carries its own white
 * stroke, a halo that is its pad; and a mark with no words (the empty album pocket's "?", which has no semantics).
 * The subtitles §4.1 names (the List's app bar, the album's, My store's, My pantry's) and the end note must be among the
 * runs looked at, so the check cannot pass by not seeing them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TextOnPatternTest(private val fixture: Fixture) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun day() = check(Combo())

    @Test
    fun glow() = check(Combo(variant = ThemeVariant.Glow))

    @Test
    @Config(qualifiers = PHONE_360, fontScale = 2.0f)
    fun fontScale200() = check(Combo(fontScale = 2f))

    private fun check(combo: Combo) {
        val capture = captureFixture(compose, fixture, combo)
        val looked = information(capture)
        assertEquals("${capture.where}: the walker sees text set in a font", fixture.fontText, looked.isNotEmpty())
        // The information text §4.1 names, looked at where the fixture shows it (the bar's subtitle hides as the bar
        // collapses on a scroll, so it is required at 1.0, where these fixtures leave the bar open).
        REQUIRED[fixture.name]?.takeIf { combo.fontScale == 1f }?.let { words ->
            assertTrue("${capture.where}: \"$words\" is looked at (${looked.map { it.text }})", looked.any { words in it.text })
        }
        val bad = onPattern(capture, looked)
        assertTrue("${capture.where}: information text straight on a pattern\n  ${bad.joinToString("\n  ")}", bad.isEmpty())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ScreenFixtures.params()

        /** A pattern runs at least this many separate lines under the letters (a patch's edges are one or two). */
        const val PATTERN_LINES = 3

        /** §4.1's named information text, by the fixture that shows it. */
        private val REQUIRED = mapOf(
            "list_midtrip" to "Hillcrest Market",
            "list_end" to "end of the list",
            "album" to "collected",
            "mystore" to "walking order",
            "pantry" to "things you keep at home",
        )

        /** The runs of [capture] that carry information: seen, with words, not puffy. */
        fun information(capture: Capture): List<DrawnRun> = capture.screen.seen.filter { run ->
            // §4.1: puffy lettering carries its own stroke (its stroke pass, or its twin drawn under it).
            val puffy = capture.isPuffy(run) || run.drawnBy("PuffyText")
            // A mark ("?", "★") has no words to read.
            val words = run.text.any { it.isLetterOrDigit() }
            !puffy && words
        }

        /** The runs of [looked] that sit on a pattern, as "words: lines". */
        fun onPattern(capture: Capture, looked: List<DrawnRun>): List<String> = looked.mapNotNull { run ->
            val behind = capture.screen.behindOf(run)
            if (behind.lines < PATTERN_LINES) {
                null
            } else {
                "\"${run.text}\" (${run.family}): ${behind.lines} lines of a pattern under its letters" +
                    if (run.stack.isNotEmpty()) ", drawn by ${run.stack.first()}" else ""
            }
        }.distinct()
    }
}

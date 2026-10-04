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
 * Item 292 (docs/DESIGN_STICKER.md §2.7, §15): no text the screen shows is under 13 sp, on every screen of the walker in
 * every state, Day and Glow, at font scale 1.0, and at the phone's 1.3 and 2.0, where no text may be drawn smaller than
 * it is at 1.0 (§4.7, item 337: "never drawn under its 1.0 size, so never under 13 sp"), so 13 sp at 1.0, 13 dp. The
 * size is what the screen really draws: every run of text (a Text, a puffy title's passes, a numeral painted in a
 * circle, a doodle with no semantics), its font size times every transform it is drawn under, and only the runs that
 * are seen (a run wholly under something opaque is not on the screen).
 *
 * The exceptions are §2.7's, each a numeral that repeats a number shown at 13 sp or more elsewhere and is set at 11 sp:
 * the stamp numerals (drawn by the stamp circles) with the book's "+n" stop counters, and the aisle chip's number
 * circle (the counters' and the chip's nodes marked RepeatedNumeral). And the hanko's lettering, which §2.7 sizes in
 * the stamp's own units (auto-fit on a 40-unit stamp, §4.5): it is the stamp's art, the row saying "got it" in words
 * for TalkBack.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class MinTextSizeTest(private val fixture: Fixture) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun day() = check(Combo())

    @Test
    fun glow() = check(Combo(variant = ThemeVariant.Glow))

    @Test
    @Config(qualifiers = PHONE_360, fontScale = 1.3f)
    fun fontScale130() = check(Combo(fontScale = 1.3f))

    @Test
    @Config(qualifiers = PHONE_360, fontScale = 2.0f)
    fun fontScale200() = check(Combo(fontScale = 2f))

    private fun check(combo: Combo) {
        val capture = captureFixture(compose, fixture, combo)
        assertEquals("${capture.where}: the walker sees text set in a font", fixture.fontText, capture.screen.seen.isNotEmpty())
        val small = tooSmall(capture)
        assertTrue("${capture.where}: text under 13 sp\n  ${small.joinToString("\n  ")}", small.isEmpty())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ScreenFixtures.params()

        /** §2.7's floor (dp at the scale the size was designed at). */
        const val FLOOR_DP = 13f

        /** The repeated numerals' size (§2.7: 11 sp). */
        const val NUMERAL_DP = 11f

        /** Every seen run of [capture] under the floor, as "words: size". */
        fun tooSmall(capture: Capture): List<String> {
            val out = ArrayList<String>()
            for (run in capture.screen.seen) {
                val dp = run.dp(capture.density)
                if (dp >= FLOOR_DP - 0.05f) continue
                // The hanko's lettering is its stamp's art, sized in stamp units (§2.7 "Hanko text", §4.5).
                if (run.drawnBy("drawHanko")) continue
                // §2.7's two repeated numerals, never under their own 11 sp.
                val numeral = run.text.trim().matches(Regex("""\+?\d+"""))
                val repeated = numeral && (run.drawnBy("StampCircle") || capture.inside(run, capture.repeatedNumerals))
                if (repeated && dp >= NUMERAL_DP - 0.05f) continue
                out += "\"${run.text}\" (${run.family}) at ${"%.1f".format(dp)} dp" + if (run.stack.isNotEmpty()) ", drawn by ${run.stack.first()}" else ""
            }
            return out.distinct()
        }
    }
}

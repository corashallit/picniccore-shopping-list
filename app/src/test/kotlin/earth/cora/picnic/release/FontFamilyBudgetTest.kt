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
 * Item 26 (docs/DESIGN_STICKER.md §2.7): no surface uses more than three font families, a sheet being a surface of its
 * own and the LCD's vector lettering not counting (it is drawn as paths, not text). Every fixture of the walker, Day and
 * Glow: the family of every run of text the screen really draws and shows (its typeface told apart by its metrics, so
 * a style that lost its family and fell back to the phone's own sans is caught as one), grouped by the surface it lies
 * on. Each surface keeps to the kit's two (DynaPuff, Zen Maru Gothic) and its own third (§2.7: the list's Hachi Maru
 * doodles, the Add sheet's Klee One transcripts, the aisles and pantry text in Klee One), never the phone's.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class FontFamilyBudgetTest(private val fixture: Fixture) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun day() = check(Combo())

    @Test
    fun glow() = check(Combo(variant = ThemeVariant.Glow))

    private fun check(combo: Combo) {
        val capture = captureFixture(compose, fixture, combo)
        assertEquals("${capture.where}: the walker sees text set in a font", fixture.fontText, capture.screen.seen.isNotEmpty())
        val problems = overBudget(capture)
        assertTrue("${capture.where}: font families\n  ${problems.joinToString("\n  ")}", problems.isEmpty())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ScreenFixtures.params()

        /** The kit's two families, on every surface (§2.7). */
        private val KIT = setOf(KitFonts.DYNAPUFF, KitFonts.ZEN_MARU)

        /** Every surface of [capture] that uses a family it may not, or more than three, as "surface: families (words)". */
        fun overBudget(capture: Capture): List<String> {
            val out = ArrayList<String>()
            for ((surface, runs) in capture.screen.seen.groupBy { capture.surfaceOf(it) }) {
                val families = runs.map { it.family }.toSet()
                val allowed = KIT + listOfNotNull(surface.third)
                val strays = runs.filter { it.family !in allowed }
                if (families.size > 3 || strays.isNotEmpty()) {
                    out += "$surface uses ${families.sorted()}" +
                        strays.distinctBy { it.family to it.text }.take(6).joinToString("", prefix = "; not its own: ") { "\n    \"${it.text}\" in ${it.family}" }
                }
            }
            return out
        }
    }
}

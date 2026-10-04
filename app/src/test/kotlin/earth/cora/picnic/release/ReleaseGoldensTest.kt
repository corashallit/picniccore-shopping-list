package earth.cora.picnic.release

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.StripsOf
import earth.cora.picnic.ui.list.TrayOf
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens Appendix C names that no package recorded (item 304's gaps), in Day and Glow:
 * - `glyphs_all` (item 48, §3.4): every toolbar glyph at its 24 dp size and at 48 dp, ballpoint 2u lines with round caps,
 *   no border and no gloss;
 * - `row_states_greyscale` (item 288, §15): the row states (to get, got, drawing, shiny, new) with the colour taken out,
 *   so checked and unchecked still read apart by shape, mark, strike and ghost alone;
 * - `strips_greyscale` (item 289, §15): the fixture's eight aisle strips with the colour taken out, told apart by pattern,
 *   number and name.
 * Owner: WP10.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ReleaseGoldensTest(private val variant: ThemeVariant) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = listOf(arrayOf(ThemeVariant.Day), arrayOf(ThemeVariant.Glow))

        private const val TAG = "release_golden"
    }

    private val v: String get() = variant.name.lowercase()

    /** The whole piece, binder ground included, drawn through a colour matrix with no saturation. */
    private fun Modifier.greyscale(): Modifier = drawWithContent {
        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }

    private fun record(name: String, grey: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            PicnicTheme(variant = variant) {
                Box(Modifier.fillMaxWidth().wrapContentHeight().testTag(TAG).then(if (grey) Modifier.greyscale() else Modifier)) {
                    PicnicBackground(Modifier.matchParentSize()) {}
                    Box(Modifier.padding(16.dp)) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        compose.onNodeWithTag(TAG).captureRoboImage("src/test/screenshots/$name.png")
    }

    /** A taller window than the default 760 dp: the 48 dp set alone runs to ten rows. */
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    @Config(qualifiers = "w360dp-h1200dp-xxhdpi")
    fun glyphsAll() = record("glyphs_all_$v", grey = false) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            for (size in listOf(24, 48)) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (g in PicnicGlyph.entries) {
                        Box(Modifier.size((size + 12).dp), contentAlignment = Alignment.Center) { Glyph(g, contentDescription = null, size = size.dp) }
                    }
                }
            }
        }
    }

    @Test
    fun rowStatesGreyscale() = record("row_states_greyscale_$v", grey = true) {
        val ui = ListFixture.ui(ListFixture.rowStates(), busy = ListFixture.DRAWING)
        TrayOf(ui.sections.first { it.id == "breakfast" })
    }

    @Test
    fun stripsGreyscale() = record("strips_greyscale_$v", grey = true) {
        StripsOf(ListFixture.ui().sections)
    }
}

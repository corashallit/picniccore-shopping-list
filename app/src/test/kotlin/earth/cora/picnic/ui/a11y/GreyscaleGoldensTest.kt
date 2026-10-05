package earth.cora.picnic.ui.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.StripsOf
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 289 (docs/DESIGN_STICKER.md §15 "Colour"): with the colour taken away (greyscale, as a colour-blind or greyscale
 * display shows it), the default 14 aisles' strips stay apart by their tab's pattern, their number circle and their
 * name. Golden `strips_greyscale`: the strips of `strips_default_aisles_day`, desaturated.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1100dp-xxhdpi")
class GreyscaleGoldensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun stripsGreyscale() = record("strips_greyscale") {
        StripsOf(ListFixture.ui(ListFixture.everyDefaultAisle()).sections)
    }

    /** Records [content] on the page, everything drawn through a zero-saturation filter. */
    private fun record(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            PicnicTheme(variant = ThemeVariant.Day) {
                Box(Modifier.fillMaxWidth().wrapContentHeight().testTag(GREY_TAG).greyscale()) {
                    PicnicBackground(Modifier.matchParentSize()) {}
                    Box(Modifier.padding(12.dp)) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        compose.onNodeWithTag(GREY_TAG).captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun Modifier.greyscale(): Modifier = drawWithContent {
        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }

    private companion object {
        const val GREY_TAG = "a11y_greyscale"
    }
}

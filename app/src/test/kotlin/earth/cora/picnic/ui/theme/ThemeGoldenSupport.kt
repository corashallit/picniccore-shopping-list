package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage

/*
 * WP4 component goldens (docs/DESIGN_STICKER.md §20, Appendix C): rendered at w360dp-h760dp-xxhdpi into
 * app/src/test/screenshots/<name>.png. Record: ./gradlew :app:recordRoborazziDebug --tests '*ThemeGoldens*'.
 */

const val GOLDEN_TAG = "wp4_golden"

/** The variants a component golden is recorded in (Appendix C: Day and Glow unless a name says otherwise). */
fun dayAndGlow(): List<Array<Any>> = listOf(arrayOf(ThemeVariant.Day, "day"), arrayOf(ThemeVariant.Glow, "glow"))

/**
 * Renders [content] in [variant] on the binder ground (a 14 dp gutter) and records the framed node as [name].png.
 * [density] overrides the drawing density (the R2 density goldens: 1×, 2×, 2.625×, 3×, 3.5×, 4×) so the content is
 * drawn at that many px per dp.
 */
fun ComposeContentTestRule.recordGolden(
    name: String,
    variant: ThemeVariant,
    density: Float? = null,
    fontScale: Float? = null,
    reducedMotion: Boolean = false,
    gutter: Boolean = true,
    settleMillis: Long = 600,
    content: @Composable ColumnScope.() -> Unit,
) {
    setContent {
        val base = LocalDensity.current
        val d = Density(density ?: base.density, fontScale ?: base.fontScale)
        CompositionLocalProvider(LocalDensity provides d) {
            PicnicTheme(variant = variant, reducedMotion = reducedMotion) {
                PicnicBackground(Modifier.testTag(GOLDEN_TAG)) {
                    Column(
                        Modifier.padding(if (gutter) 14.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content,
                    )
                }
            }
        }
    }
    waitForIdle()
    // Let stepped state changes (press, slam, knob) settle on the frame clock; decorative loops are off in tests.
    mainClock.advanceTimeBy(settleMillis)
    waitForIdle()
    onNodeWithTag(GOLDEN_TAG).captureRoboImage("src/test/screenshots/$name.png")
}

/** A full-width column helper for goldens that want the 360 dp width. */
@Composable
fun FullWidth(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

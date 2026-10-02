package earth.cora.picnic.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import earth.cora.picnic.ui.theme.PicnicTheme

/*
 * Screen screenshot tests (docs/ARCHITECTURE.md §14): render the STATELESS screens with hand-built
 * states and record goldens to app/src/test/screenshots/<screen>_<state>_<light|dark>.png.
 * Record: ./gradlew :app:recordRoborazziDebug. Plain testDebugUnitTest runs them without writing.
 */

/** Both palettes, for ParameterizedRobolectricTestRunner (the second value names the test). */
fun themeParameters(): List<Array<Any>> = listOf(arrayOf(false, "light"), arrayOf(true, "dark"))

/**
 * Renders [content] inside PicnicTheme and records it. [wholeScreen] captures every window, so
 * modal bottom sheets (a separate dialog window) appear over the screen behind them. [before] runs
 * after the first frame (e.g. a scroll).
 */
@OptIn(ExperimentalRoborazziApi::class) // captureScreenRoboImage: every window, sheets included
fun ComposeContentTestRule.snap(
    name: String,
    dark: Boolean,
    wholeScreen: Boolean = false,
    before: ComposeContentTestRule.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    setContent { PicnicTheme(darkTheme = dark) { content() } }
    waitForIdle()
    before()
    waitForIdle()
    val path = "src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png"
    if (wholeScreen) captureScreenRoboImage(path) else onRoot().captureRoboImage(path)
}

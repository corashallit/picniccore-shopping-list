package earth.cora.picnic.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant

/*
 * WP8 screen goldens (docs/DESIGN_STICKER.md §20, Appendix C): the stateless screens rendered whole at
 * w360dp-h760dp-xxhdpi (the test class's @Config) into app/src/test/screenshots/<name>.png, Day and Glow unless the
 * name says otherwise; `_fs130` / `_fs200` at those font scales, Day only.
 * Record: ./gradlew :app:recordRoborazziDebug --tests '<class>'.
 */

/** The phone the goldens render on (§19.3): the mockup's 360 dp width. */
const val PHONE_360: String = "w360dp-h760dp-xxhdpi"

/** The wider phone for `_w411` goldens. */
const val PHONE_411: String = "w411dp-h891dp-xxhdpi"

/** Day and Glow, for ParameterizedRobolectricTestRunner (the second value names the test). */
fun dayAndGlowScreens(): List<Array<Any>> = listOf(arrayOf(ThemeVariant.Day, "day"), arrayOf(ThemeVariant.Glow, "glow"))

/** The golden name for a variant: `<name>_day` / `<name>_glow`. */
fun goldenName(name: String, variant: ThemeVariant): String = "${name}_${variant.name.lowercase()}"

/**
 * Renders [content] (a whole screen) in [variant] at [fontScale] and records [name].png. [before] runs after the first
 * frame (a scroll, a click) on the auto-advancing clock; then the clock freezes and [settleMillis] lets stepped motion
 * settle (decorative loops stay still). [wholeScreen] also captures popups and dialogs.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.recordScreen(
    name: String,
    variant: ThemeVariant = ThemeVariant.Day,
    fontScale: Float? = null,
    reducedMotion: Boolean = false,
    settleMillis: Long = 800,
    wholeScreen: Boolean = false,
    frozen: Boolean = false,
    before: ComposeContentTestRule.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    // [frozen]: the clock stands still from the first frame, for states that pass (a toast's drop and hold).
    if (frozen) mainClock.autoAdvance = false
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale ?: base.fontScale)) {
            PicnicTheme(variant = variant, reducedMotion = reducedMotion) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
    if (!frozen) waitForIdle()
    // Scrolls and clicks run on the auto-advancing clock (a programmatic scroll animates and would never end on a
    // frozen one); then the clock freezes and stepped motion settles for [settleMillis].
    before()
    if (!frozen) waitForIdle()
    mainClock.autoAdvance = false
    mainClock.advanceTimeBy(settleMillis)
    waitForIdle()
    val path = "src/test/screenshots/$name.png"
    if (wholeScreen) captureScreenRoboImage(path) else onRoot().captureRoboImage(path)
}

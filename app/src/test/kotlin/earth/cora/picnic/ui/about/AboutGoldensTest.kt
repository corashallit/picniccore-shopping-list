package earth.cora.picnic.ui.about

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * About & licences and the Surprise lab shell (checklist 21, 229): `about_{day,glow}`, `about_licences_day` (a licence
 * open), `surprise_lab_{day,glow}`, and `surprise_lab_bottom_day` (the last tiles) and `surprise_lab_reduced_day` (reduced
 * motion's "needs animations on" notes).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class AboutGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    private val state: AboutUiState
        get() = AboutUiState(
            appVersion = "0.4.0",
            licences = Licences.load(ApplicationProvider.getApplicationContext()),
            labVisible = true,
            canUnlock = false,
        )

    @Test
    fun about() {
        val s = state
        compose.recordScreen(goldenName("about", variant), variant) { AboutScreen(s, AboutActions()) }
    }

    @Test
    fun licenceOpen() {
        if (variant != ThemeVariant.Day) return
        val s = state
        compose.recordScreen("about_licences_day", variant, before = {
            onNode(hasText("Zen Maru Gothic", substring = true) and hasClickAction()).performScrollTo()
        }) { AboutScreen(s, AboutActions(), initiallyOpen = setOf("Zen Maru Gothic")) }
    }

    @Test
    fun surpriseLab() {
        compose.recordScreen(goldenName("surprise_lab", variant), variant) { SurpriseLabScreen(onBack = {}) }
    }

    /** The last tiles (the pixel strawberry of 10, the flower of 14, the receipt slip of 18 in its 48 dp box). */
    @Test
    fun surpriseLabBottom() {
        if (variant != ThemeVariant.Day) return
        // Scrolled to the end (the state clamps to the page's height).
        compose.recordScreen("surprise_lab_bottom_day", variant) { SurpriseLabScreen(onBack = {}, scroll = rememberScrollState(Int.MAX_VALUE)) }
    }

    /** Reduced motion (tiles 4 to 10): the pure-motion eggs carry the flat dashed "needs animations on" note instead of Try it. */
    @Test
    fun surpriseLabReducedMotion() {
        if (variant != ThemeVariant.Day) return
        compose.recordScreen("surprise_lab_reduced_day", variant, reducedMotion = true, before = {
            onNode(hasText("Within 6 seconds", substring = true)).performScrollTo()
        }) { SurpriseLabScreen(onBack = {}) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

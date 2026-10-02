package earth.cora.picnic.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.ui.common.snap
import earth.cora.picnic.ui.common.themeParameters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens: settings_no_key (first run), settings_key_typed (a key pasted and shown, not saved yet),
 * settings_key_saved (key saved, test OK), settings_failed (the key lacks a permission),
 * settings_about_key (scrolled down to the key advice and what is sent).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest(private val dark: Boolean, @Suppress("unused") private val theme: String) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun noKey() = compose.snap("settings_no_key", dark) {
        SettingsScreen(SettingsUiState(appVersion = "0.1.0-dev", loading = false), SettingsActions())
    }

    @Test
    fun keyTyped() = compose.snap("settings_key_typed", dark) {
        SettingsScreen(
            SettingsUiState(
                tokenInput = "sk-proj-7Hq2xWv9Lm3Ks8Rt",
                tokenVisible = true,
                drawIcons = false,
                canSave = true,
                appVersion = "0.1.0-dev",
                loading = false,
            ),
            SettingsActions(),
        )
    }

    @Test
    fun keySaved() = compose.snap("settings_key_saved", dark) {
        SettingsScreen(
            SettingsUiState(
                hasToken = true,
                tokenHint = "sk-…a1b2",
                test = ConnectionTestUi.Ok(412),
                appVersion = "0.1.0-dev",
                loading = false,
            ),
            SettingsActions(),
        )
    }

    @Test
    fun failedTest() = compose.snap("settings_failed", dark) {
        SettingsScreen(
            SettingsUiState(
                hasToken = true,
                tokenHint = "sk-…a1b2",
                test = ConnectionTestUi.Failed(LlmError.Unauthorized(401, missingScopes = listOf("api.model.read")).friendlyMessage),
                appVersion = "0.1.0-dev",
                loading = false,
            ),
            SettingsActions(),
        )
    }

    @Test
    fun aboutTheKey() = compose.snap(
        "settings_about_key",
        dark,
        // The version footer is the last thing on the screen: the whole advice card is in view.
        before = { onNodeWithText("0.1.0-dev", substring = true).performScrollTo() },
    ) {
        SettingsScreen(
            SettingsUiState(hasToken = true, tokenHint = "sk-…a1b2", appVersion = "0.1.0-dev", loading = false),
            SettingsActions(),
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = themeParameters()
    }
}

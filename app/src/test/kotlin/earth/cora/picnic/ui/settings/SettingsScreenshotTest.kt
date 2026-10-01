package earth.cora.picnic.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.ui.common.ScreenText
import earth.cora.picnic.ui.common.snap
import earth.cora.picnic.ui.common.themeParameters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens: settings_own_key (default endpoint, key saved, test OK), settings_proxy (new token typed),
 * settings_failed, settings_new_host (the address changed host, so the old token was dropped).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest(private val dark: Boolean, @Suppress("unused") private val theme: String) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ownKey() = compose.snap("settings_own_key", dark) {
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
    fun proxyWithANewToken() = compose.snap("settings_proxy", dark) {
        SettingsScreen(
            SettingsUiState(
                baseUrlInput = "https://picnic.cora.earth/v1",
                usesDefaultEndpoint = false,
                tokenInput = "pk_live_7Hq2xWv9Lm3Ks8Rt",
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
    fun failedTest() = compose.snap("settings_failed", dark) {
        SettingsScreen(
            SettingsUiState(
                baseUrlInput = "https://picnic.cora.earth/v1",
                usesDefaultEndpoint = false,
                hasToken = true,
                tokenHint = "pk_…Rt",
                test = ConnectionTestUi.Failed(LlmError.Unauthorized(401).friendlyMessage),
                appVersion = "0.1.0-dev",
                loading = false,
            ),
            SettingsActions(),
        )
    }

    @Test
    fun newHostAsksForItsToken() = compose.snap("settings_new_host", dark) {
        SettingsScreen(
            SettingsUiState(
                baseUrlInput = "https://picnic.cora.earth/v1",
                usesDefaultEndpoint = false,
                hasToken = false,
                notice = ScreenText.TOKEN_FOR_NEW_SERVER,
                appVersion = "0.1.0-dev",
                loading = false,
            ),
            SettingsActions(),
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = themeParameters()
    }
}

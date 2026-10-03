package earth.cora.picnic.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.Surprise
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
 * Settings goldens (checklist 226, Appendix C): `settings_{nokey,typed,ok,failed,surprises_off}_{day,glow}`, plus
 * `settings_save_error_*` (the §6.8 notice state) and, Day only, `settings_fs130` and `settings_fs200`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class SettingsGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    private fun record(
        name: String,
        state: SettingsUiState,
        scrollTo: String? = null,
        fontScale: Float? = null,
        exact: String? = null,
        frozen: Boolean = false,
    ) =
        compose.recordScreen(
            exact ?: goldenName(name, variant),
            variant,
            fontScale = fontScale,
            frozen = frozen,
            before = { if (scrollTo != null) onNodeWithText(scrollTo).performScrollTo() },
        ) { SettingsScreen(state, SettingsActions()) }

    @Test
    fun noKey() = record("settings_nokey", SettingsSamples.noKey)

    @Test
    fun typed() = record("settings_typed", SettingsSamples.typed)

    @Test
    fun ok() = record("settings_ok", SettingsSamples.ok)

    @Test
    fun failed() = record("settings_failed", SettingsSamples.failed)

    @Test
    fun surprisesOff() = record("settings_surprises_off", SettingsSamples.surprisesOff, scrollTo = "Use my area")

    @Test
    fun saveError() = record("settings_save_error", SettingsSamples.ok.copy(notice = "Couldn't save. Try again."), frozen = true)

    @Test
    fun fontScale130() {
        if (variant == ThemeVariant.Day) record("", SettingsSamples.ok, fontScale = 1.3f, exact = "settings_fs130")
    }

    @Test
    fun fontScale200() {
        if (variant == ThemeVariant.Day) record("", SettingsSamples.ok, fontScale = 2f, exact = "settings_fs200")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

/** Hand-built Settings states for goldens and interaction tests. */
object SettingsSamples {
    private val base = SettingsUiState(loading = false, appVersion = "0.4.0", listTitle = "Saturday shop")

    val noKey = base

    val typed = base.copy(tokenInput = "sk-proj-4fGhJk2LmNp7QrSt9VwX3f9a", canSave = true)

    val ok = base.copy(hasToken = true, tokenHint = "sk-…3f9a", test = ConnectionTestUi.Ok(412), settings = AppSettings(hasToken = true, tokenHint = "sk-…3f9a"))

    val failed = ok.copy(
        test = ConnectionTestUi.Failed("This OpenAI key is missing a permission the app needs (api.responses.write). Check the key's permissions."),
    )

    val surprisesOff = ok.copy(
        test = ConnectionTestUi.Idle,
        settings = Surprise.entries.fold(ok.settings) { s, surprise -> s.with(surprise, false) },
    )
}

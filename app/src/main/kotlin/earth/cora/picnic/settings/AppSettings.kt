package earth.cora.picnic.settings

import earth.cora.picnic.core.llm.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Device-local settings (NOT in state.json, not backed up). CONTRACT (docs/ARCHITECTURE.md §9).
 * The token itself is never part of this class: only whether one is set and a masked hint.
 */
data class AppSettings(
    val llm: LlmConfig = LlmConfig(),
    /** "Draw new icons with AI" (about 10 s and 1.5 US cents per icon). */
    val drawIcons: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Extra switch on top of the system "remove animations" setting. */
    val reduceMotion: Boolean = false,
    /** Read-only for UI: a token is stored. Ignored by [SettingsRepository.update]. */
    val hasToken: Boolean = false,
    /** Read-only for UI: masked token, e.g. "sk-…a1b2". Ignored by [SettingsRepository.update]. */
    val tokenHint: String? = null,
)

interface SettingsRepository {
    /** Emits the persisted settings (defaults for anything the user never changed), then every change. */
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    /** Persists only values that differ from the defaults (LlmDefaults / AppSettings()), so new defaults reach old installs. */
    suspend fun update(transform: (AppSettings) -> AppSettings)

    /** Stores the token encrypted (Android Keystore AES-GCM); null or blank clears it. Never logs it. */
    suspend fun setToken(token: String?)

    /** Decrypts on demand for the HTTP layer only (core TokenSource). Null if unset or undecryptable. */
    suspend fun token(): String?

    /** Forgets every model/URL override (keeps the token and the non-LLM settings). */
    suspend fun resetLlmToDefaults()
}

/** For previews, screenshot tests and ViewModel tests. */
class InMemorySettingsRepository(initial: AppSettings = AppSettings(), private var storedToken: String? = null) : SettingsRepository {
    private val state = MutableStateFlow(initial.withToken(storedToken))
    override val settings: Flow<AppSettings> = state.asStateFlow()

    override suspend fun current(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value).withToken(storedToken)
    }

    override suspend fun setToken(token: String?) {
        storedToken = token?.trim()?.takeIf { it.isNotEmpty() }
        state.value = state.value.withToken(storedToken)
    }

    override suspend fun token(): String? = storedToken

    override suspend fun resetLlmToDefaults() {
        state.value = state.value.copy(llm = LlmConfig())
    }

    private fun AppSettings.withToken(token: String?) =
        copy(hasToken = token != null, tokenHint = token?.let(::maskToken))
}

/** "sk-proj-abc...wxyz" -> "sk-…wxyz". Never show more than the last 4 characters. */
fun maskToken(token: String): String {
    val prefix = token.substringBefore('-', missingDelimiterValue = "").let { if (it.isNotEmpty() && it.length <= 4) "$it-" else "" }
    return prefix + "…" + token.takeLast(4)
}

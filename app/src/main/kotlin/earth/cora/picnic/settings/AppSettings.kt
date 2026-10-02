package earth.cora.picnic.settings

import earth.cora.picnic.core.llm.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-local settings (NOT in state.json, not backed up). CONTRACT (docs/ARCHITECTURE.md §9).
 * Amendment A1: the ONLY user settings are the user's OpenAI key (with "Test connection") and
 * "Draw new icons with AI". Requests always go to LlmConfig.DEFAULT_BASE_URL (api.openai.com); models
 * are fixed in core's ModelDefaults; theme and motion follow the system. The key itself is never part
 * of this class: only whether one is set and a masked hint.
 */
data class AppSettings(
    /** "Draw new icons with AI" (icon cascade step 3; about 10 s and 1.5 US cents per icon). */
    val drawIcons: Boolean = true,
    /** Read-only for UI: a key is stored and decryptable. */
    val hasToken: Boolean = false,
    /** Read-only for UI: masked key, e.g. "sk-…a1b2" (see [maskToken]). */
    val tokenHint: String? = null,
)

interface SettingsRepository {
    /** Emits the persisted settings (defaults for anything never changed), then every change. */
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun setDrawIcons(enabled: Boolean)

    /** Stores the OpenAI key encrypted (Android Keystore AES-GCM); null or blank clears it. Never logs it. */
    suspend fun setToken(token: String?)

    /** Decrypts on demand for the HTTP layer only (core TokenSource). Null if unset or undecryptable. */
    suspend fun token(): String?

    /** For core's ConfigSource: the OpenAI base URL + safetyIdentifier (first 32 hex chars of sha256(install id)). */
    suspend fun llmConfig(): LlmConfig
}

/** For previews, screenshot tests and ViewModel tests. */
class InMemorySettingsRepository(
    initial: AppSettings = AppSettings(),
    private var storedToken: String? = null,
) : SettingsRepository {
    private val state = MutableStateFlow(initial.withToken(storedToken))
    override val settings: Flow<AppSettings> = state.asStateFlow()

    override suspend fun current(): AppSettings = state.value

    override suspend fun setDrawIcons(enabled: Boolean) {
        state.value = state.value.copy(drawIcons = enabled)
    }

    override suspend fun setToken(token: String?) {
        storedToken = token?.trim()?.takeIf { it.isNotEmpty() }
        state.value = state.value.withToken(storedToken)
    }

    override suspend fun token(): String? = storedToken

    override suspend fun llmConfig(): LlmConfig = LlmConfig(safetyIdentifier = "test-install")

    private fun AppSettings.withToken(token: String?) =
        copy(hasToken = token != null, tokenHint = token?.let(::maskToken))
}

/**
 * Write-only hint for the Settings key field. Shows a short known prefix and at most the last 4
 * characters: "sk-proj-abc…wxyz" -> "sk-…wxyz" (an underscore prefix such as "ab_…wxyz" works the same).
 * Short keys reveal less (2 characters under 16, none under 8), so the hint never gives away a
 * meaningful part.
 */
fun maskToken(token: String): String {
    val t = token.trim()
    val prefixWord = t.takeWhile { it != '-' && it != '_' }
    val prefix = if (prefixWord.length in 1..4 && t.length > prefixWord.length) prefixWord + t[prefixWord.length] else ""
    val tail = when {
        t.length >= 16 -> t.takeLast(4)
        t.length >= 8 -> t.takeLast(2)
        else -> ""
    }
    return prefix + "…" + tail
}

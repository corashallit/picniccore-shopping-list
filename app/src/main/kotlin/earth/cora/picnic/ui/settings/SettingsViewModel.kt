package earth.cora.picnic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.common.ScreenText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URI

/*
 * Settings (amendment A1): the connection (base URL + token, "Test connection") and "Draw new icons
 * with AI". Nothing about models. Names are binding (docs/ARCHITECTURE.md §10.3); fields may be added.
 */

data class SettingsUiState(
    /** What the base URL field shows: the user's edit, else the saved URL. */
    val baseUrlInput: String = LlmConfig.DEFAULT_BASE_URL,
    val baseUrlError: String? = null,
    /** The SAVED base URL is OpenAI's (not a proxy). */
    val usesDefaultEndpoint: Boolean = true,
    /** Write-only token field: never pre-filled with the stored token. */
    val tokenInput: String = "",
    val hasToken: Boolean = false,
    /** Masked stored token, e.g. "sk-…a1b2". */
    val tokenHint: String? = null,
    val drawIcons: Boolean = true,
    val test: ConnectionTestUi = ConnectionTestUi.Idle,
    val appVersion: String = "",
    /** The token field shows its text (show/hide toggle). */
    val tokenVisible: Boolean = false,
    /** Unsaved edits in the connection fields. */
    val canSave: Boolean = false,
    val loading: Boolean = true,
    /** A one-line note about the last save (a failed write, or "paste the token for this server"). */
    val notice: String? = null,
)

sealed interface ConnectionTestUi {
    data object Idle : ConnectionTestUi
    data object Running : ConnectionTestUi
    data class Ok(val latencyMs: Long) : ConnectionTestUi
    data class Failed(val message: String) : ConnectionTestUi
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val assistant: GroceryAssistant,
    private val appVersion: String,
) : ViewModel() {
    private data class Inputs(
        /** Null = not edited (the field mirrors the saved URL). */
        val baseUrl: String? = null,
        val token: String = "",
        val baseUrlError: String? = null,
        val tokenVisible: Boolean = false,
        val test: ConnectionTestUi = ConnectionTestUi.Idle,
        val notice: String? = null,
    )

    private val inputs = MutableStateFlow(Inputs())
    private var testJob: Job? = null

    val state: StateFlow<SettingsUiState> = combine(settings.settings, inputs) { s, i -> build(s, i) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(appVersion = appVersion))

    fun setBaseUrlInput(value: String) = inputs.update { it.copy(baseUrl = value, baseUrlError = null) }

    fun setTokenInput(value: String) = inputs.update { it.copy(token = value) }

    fun toggleTokenVisible() = inputs.update { it.copy(tokenVisible = !it.tokenVisible) }

    /** Saves the edited URL and/or token, then checks the connection when a token is set. */
    fun saveConnection() {
        launchWrite {
            if (save() && settings.current().hasToken) runTest()
        }
    }

    /** Removes the stored token. */
    fun clearToken() {
        testJob?.cancel()
        launchWrite {
            settings.setToken(null)
            inputs.update { it.copy(token = "", test = ConnectionTestUi.Idle, notice = null) }
        }
    }

    /** Back to api.openai.com (for people with their own OpenAI key). A token for another host is dropped. */
    fun useOpenAiDirectly() {
        testJob?.cancel()
        launchWrite {
            val dropped = dropTokenIfHostChanges(LlmConfig.DEFAULT_BASE_URL)
            settings.setBaseUrl("")
            inputs.update {
                it.copy(baseUrl = null, baseUrlError = null, test = ConnectionTestUi.Idle, notice = if (dropped) ScreenText.TOKEN_FOR_NEW_SERVER else null)
            }
        }
    }

    fun setDrawIcons(on: Boolean) {
        launchWrite { settings.setDrawIcons(on) }
    }

    /** "Test connection": saves pending edits first (the test always uses what is saved). */
    fun testConnection() {
        launchWrite {
            if (state.value.canSave && !save()) return@launchWrite
            // A new host without a new token: the notice already says what to do.
            if (!settings.current().hasToken && inputs.value.notice != null) return@launchWrite
            runTest()
        }
    }

    /**
     * Settings writes can fail (DataStore throws IOException, e.g. on a full disk): say so instead of
     * crashing the Settings screen.
     */
    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: IOException) {
                PicnicLog.w("settings write failed", e)
                inputs.update { it.copy(test = ConnectionTestUi.Idle, notice = ScreenText.SAVE_FAILED) }
            }
        }
    }

    private suspend fun save(): Boolean {
        val i = inputs.value
        var dropped = false
        i.baseUrl?.let { url ->
            val target = if (url.isBlank()) LlmConfig.DEFAULT_BASE_URL else LlmConfig.normalizeBaseUrl(url)
            if (target == null) {
                inputs.update { it.copy(baseUrlError = ScreenText.BASE_URL_INVALID) }
                return false
            }
            // A key or token belongs to the server it was entered for: it is never sent to another
            // host, not even between these two writes. A token typed now is saved for the new host below.
            dropped = dropTokenIfHostChanges(target) && i.token.isBlank()
            if (!settings.setBaseUrl(url)) {
                inputs.update { it.copy(baseUrlError = ScreenText.BASE_URL_INVALID) }
                return false
            }
            inputs.update { if (it.baseUrl == url) it.copy(baseUrl = null, baseUrlError = null) else it }
        }
        if (i.token.isNotBlank()) {
            settings.setToken(i.token.trim())
            inputs.update { if (it.token == i.token) it.copy(token = "", tokenVisible = false) else it }
        }
        inputs.update { it.copy(test = ConnectionTestUi.Idle, notice = if (dropped) ScreenText.TOKEN_FOR_NEW_SERVER else null) }
        return true
    }

    /** Deletes the stored token when [newBaseUrl] is on another host (or port). Returns true if it did. */
    private suspend fun dropTokenIfHostChanges(newBaseUrl: String): Boolean {
        val current = settings.current()
        if (!current.hasToken || sameHost(newBaseUrl, current.baseUrl)) return false
        settings.setToken(null)
        return true
    }

    private fun runTest() {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            inputs.update { it.copy(test = ConnectionTestUi.Running) }
            val result = try {
                ConnectionTestUi.Ok(assistant.testConnection().meta.latencyMs)
            } catch (e: LlmError) {
                ConnectionTestUi.Failed(e.friendlyMessage)
            }
            inputs.update { it.copy(test = result) }
        }
    }

    private fun build(s: AppSettings, i: Inputs): SettingsUiState {
        val urlInput = i.baseUrl ?: s.baseUrl
        val urlEdited = i.baseUrl != null && normalizedOrSelf(i.baseUrl) != s.baseUrl
        return SettingsUiState(
            baseUrlInput = urlInput,
            baseUrlError = i.baseUrlError,
            usesDefaultEndpoint = s.usesDefaultEndpoint,
            tokenInput = i.token,
            hasToken = s.hasToken,
            tokenHint = s.tokenHint,
            drawIcons = s.drawIcons,
            test = i.test,
            appVersion = appVersion,
            tokenVisible = i.tokenVisible,
            canSave = urlEdited || i.token.isNotBlank(),
            loading = false,
            notice = i.notice,
        )
    }

    /** Blank means "the default"; a valid URL compares in its normalized form. */
    private fun normalizedOrSelf(url: String): String =
        if (url.isBlank()) LlmConfig.DEFAULT_BASE_URL else LlmConfig.normalizeBaseUrl(url) ?: url

    companion object {
        /** Same scheme-less authority (host and port); unparsable URLs never match. */
        internal fun sameHost(a: String, b: String): Boolean {
            fun authority(url: String) = runCatching {
                val uri = URI(url)
                val host = uri.host?.lowercase() ?: return@runCatching null
                val port = if (uri.port != -1) uri.port else if (uri.scheme.equals("http", true)) 80 else 443
                "$host:$port"
            }.getOrNull()
            val x = authority(a)
            return x != null && x == authority(b)
        }

        fun factory(c: AppContainer, appVersion: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(c.settings, c.assistant, appVersion) }
        }
    }
}

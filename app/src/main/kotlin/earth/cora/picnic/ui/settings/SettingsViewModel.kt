package earth.cora.picnic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.core.assistant.GroceryAssistant
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

/*
 * Settings (amendment A1): "Your OpenAI key" (paste, Save, Remove key, Test connection) and "Draw new
 * icons with AI". Requests always go to OpenAI; nothing about models or server addresses. Names are
 * binding (docs/ARCHITECTURE.md §10.3); fields may be added.
 */

data class SettingsUiState(
    /** Write-only key field: never pre-filled with the stored key. */
    val tokenInput: String = "",
    val hasToken: Boolean = false,
    /** Masked stored key, e.g. "sk-…a1b2". */
    val tokenHint: String? = null,
    val drawIcons: Boolean = true,
    val test: ConnectionTestUi = ConnectionTestUi.Idle,
    val appVersion: String = "",
    /** The key field shows its text (show/hide toggle). */
    val tokenVisible: Boolean = false,
    /** A key is typed and not saved yet. */
    val canSave: Boolean = false,
    val loading: Boolean = true,
    /** A one-line note about the last save (a failed write). */
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
        val token: String = "",
        val tokenVisible: Boolean = false,
        val test: ConnectionTestUi = ConnectionTestUi.Idle,
        val notice: String? = null,
    )

    private val inputs = MutableStateFlow(Inputs())
    private var testJob: Job? = null

    val state: StateFlow<SettingsUiState> = combine(settings.settings, inputs) { s, i -> build(s, i) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(appVersion = appVersion))

    fun setTokenInput(value: String) = inputs.update { it.copy(token = value, notice = null) }

    fun toggleTokenVisible() = inputs.update { it.copy(tokenVisible = !it.tokenVisible) }

    /** Saves the typed key, then checks the connection with it. */
    fun saveConnection() {
        launchWrite {
            if (save()) runTest()
        }
    }

    /** Removes the stored key. */
    fun clearToken() {
        testJob?.cancel()
        launchWrite {
            settings.setToken(null)
            inputs.update { it.copy(token = "", test = ConnectionTestUi.Idle, notice = null) }
        }
    }

    fun setDrawIcons(on: Boolean) {
        launchWrite { settings.setDrawIcons(on) }
    }

    /** "Test connection": saves a typed key first (the test always uses what is saved). */
    fun testConnection() {
        launchWrite {
            val ready = if (inputs.value.token.isNotBlank()) save() else settings.current().hasToken
            if (ready) runTest()
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

    /** Stores the typed key. False when nothing was typed or the key couldn't be stored (the field keeps it). */
    private suspend fun save(): Boolean {
        val typed = inputs.value.token
        if (typed.isBlank()) return false
        settings.setToken(typed.trim())
        if (!settings.current().hasToken) {
            // The Keystore refused it (DataStoreSettingsRepository.setToken): keep the text to try again.
            inputs.update { it.copy(test = ConnectionTestUi.Idle, notice = ScreenText.SAVE_FAILED) }
            return false
        }
        inputs.update {
            val cleared = if (it.token == typed) it.copy(token = "", tokenVisible = false) else it
            cleared.copy(test = ConnectionTestUi.Idle, notice = null)
        }
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

    private fun build(s: AppSettings, i: Inputs): SettingsUiState = SettingsUiState(
        tokenInput = i.token,
        hasToken = s.hasToken,
        tokenHint = s.tokenHint,
        drawIcons = s.drawIcons,
        test = i.test,
        appVersion = appVersion,
        tokenVisible = i.tokenVisible,
        canSave = i.token.isNotBlank(),
        loading = false,
        notice = i.notice,
    )

    companion object {
        fun factory(c: AppContainer, appVersion: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(c.settings, c.assistant, appVersion) }
        }
    }
}

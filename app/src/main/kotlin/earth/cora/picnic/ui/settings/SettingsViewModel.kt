package earth.cora.picnic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.calendar.Place
import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.llm.transport
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.platform.AreaLookup
import earth.cora.picnic.platform.AreaPlaceSource
import earth.cora.picnic.platform.EcSites
import earth.cora.picnic.platform.NetworkStatus
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.settings.Surprise
import earth.cora.picnic.ui.common.ScreenText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/*
 * Settings (docs/DESIGN_STICKER.md §6.8, amendments A1 and A4): "Your OpenAI key" (paste, Save, Remove key, Test
 * connection), "Draw new stickers with AI", the Surprises switches (with "Use my area", §0.4) and the About row.
 * Requests always go to OpenAI; nothing about models or server addresses. Names are binding (docs/ARCHITECTURE.md
 * §10.3); fields may be added. Owner: WP8.
 */

data class SettingsUiState(
    /** Write-only key field: never pre-filled with the stored key. */
    val tokenInput: String = "",
    val hasToken: Boolean = false,
    /** Masked stored key, e.g. "sk-…a1b2". */
    val tokenHint: String? = null,
    val drawIcons: Boolean = true,
    val test: ConnectionTestUi = ConnectionTestUi.Idle,
    /** The key field shows its text (the eye button). */
    val tokenVisible: Boolean = false,
    /** A key is typed and not saved yet. */
    val canSave: Boolean = false,
    val loading: Boolean = true,
    /** A one-line note about the last save (a failed write), shown as a toast once. */
    val notice: String? = null,
    /** Every Surprises switch (and the rest of the persisted settings). */
    val settings: AppSettings = AppSettings(),
    /** "Use my area": off, looking, found (its forecast city), or no fix yet (Toronto meanwhile). */
    val area: AreaLookup = AreaLookup.Off,
    /** The current list's title, for the menu's "Back to <list>". */
    val listTitle: String = "",
) {
    /** "Use my area" on: the forecast city it found (null while it looks, without a fix, or when off). */
    val areaCity: String? get() = (area as? AreaLookup.Found)?.city
}

sealed interface ConnectionTestUi {
    data object Idle : ConnectionTestUi
    data object Running : ConnectionTestUi
    data class Ok(val latencyMs: Long) : ConnectionTestUi
    data class Failed(val message: String) : ConnectionTestUi

    /**
     * The key is saved but could not be checked (§0.4, 5 October): [offline] (the phone has no network; it is checked
     * again by itself when the connection comes back while Settings is open), else OpenAI could not be reached or took
     * too long. A neutral line, never an error: nothing says the key is wrong.
     */
    data class Unchecked(val offline: Boolean) : ConnectionTestUi
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val assistant: GroceryAssistant,
    private val places: PlaceSource = PlaceSource.Toronto,
    listTitle: Flow<String> = flowOf(""),
    /** Whether the phone has a network: offline the key is saved and left unchecked, then checked once it is back. */
    private val network: NetworkStatus = NetworkStatus.AlwaysOnline,
) : ViewModel() {
    private data class Inputs(
        val token: String = "",
        val tokenVisible: Boolean = false,
        val test: ConnectionTestUi = ConnectionTestUi.Idle,
        val notice: String? = null,
        val area: AreaLookup = AreaLookup.Off,
    )

    private val inputs = MutableStateFlow(Inputs())
    private var testJob: Job? = null
    private var areaJob: Job? = null

    val state: StateFlow<SettingsUiState> = combine(settings.settings, inputs, listTitle) { s, i, title -> build(s, i, title) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    init {
        // "Use my area": the nearest forecast city whenever the switch is (or turns) on; off, the stored area is deleted
        // at once (PRIVACY.md), not at the next sunset or forecast check.
        viewModelScope.launch {
            settings.settings.map { it.useMyArea }.distinctUntilChanged().collect { on ->
                if (on) {
                    refreshArea()
                } else {
                    areaJob?.cancel()
                    inputs.update { it.copy(area = AreaLookup.Off) }
                    forgetArea()
                }
            }
        }
        // A key left unchecked for want of a connection is checked by itself once one arrives (§0.4, 5 October).
        viewModelScope.launch {
            combine(network.online, network.arrivals) { online, _ -> online }.collect { online ->
                if (online && inputs.value.test is ConnectionTestUi.Unchecked && settings.current().hasToken) runTest()
            }
        }
    }

    fun setTokenInput(value: String) = inputs.update { it.copy(token = value, notice = null) }

    fun toggleTokenVisible() = inputs.update { it.copy(tokenVisible = !it.tokenVisible) }

    fun setTokenVisible(visible: Boolean) = inputs.update { it.copy(tokenVisible = visible) }

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

    /** A Surprises switch (§6.8). "Use my area" reaches here only after its rationale and a granted permission. */
    fun setSurprise(surprise: Surprise, on: Boolean) {
        launchWrite { settings.setSurprise(surprise, on) }
    }

    /** "Use my area" was asked for, and coarse location was not allowed: it stays off, the surprises use Toronto. */
    fun onAreaDenied() {
        launchWrite {
            settings.setSurprise(Surprise.UseMyArea, false)
            forgetArea()
        }
    }

    /** Looks up the place again (the switch turned on, the permission came back, or Settings resumed without a fix). */
    fun refreshArea() {
        areaJob?.cancel()
        areaJob = viewModelScope.launch {
            inputs.update { it.copy(area = AreaLookup.Finding) }
            val area = (places as? AreaPlaceSource)?.lookup() ?: places.place().let { place ->
                if (place == Place.TORONTO) AreaLookup.NoFix else AreaLookup.Found(place, EcSites.byCode(place.cityCode)?.name ?: place.cityCode)
            }
            inputs.update { it.copy(area = area) }
        }
    }

    /** Deletes the stored rounded point and city (the switch went off, or location was refused). */
    private suspend fun forgetArea() {
        (places as? AreaPlaceSource)?.forget()
    }

    /** The toast for [SettingsUiState.notice] was shown. */
    fun noticeShown() = inputs.update { it.copy(notice = null) }

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
            // Offline the key stays saved and unchecked: no request, no error on a key that may be fine.
            if (!network.online.value) {
                inputs.update { it.copy(test = ConnectionTestUi.Unchecked(offline = true)) }
                return@launch
            }
            inputs.update { it.copy(test = ConnectionTestUi.Running) }
            val result = try {
                ConnectionTestUi.Ok(assistant.testConnection().meta.latencyMs)
            } catch (e: LlmError) {
                // A connection problem says nothing about the key: "Saved." stays, with a neutral line.
                if (e.transport) ConnectionTestUi.Unchecked(offline = e is LlmError.Offline) else ConnectionTestUi.Failed(e.friendlyMessage)
            }
            inputs.update { it.copy(test = result) }
        }
    }

    private fun build(s: AppSettings, i: Inputs, title: String): SettingsUiState = SettingsUiState(
        tokenInput = i.token,
        hasToken = s.hasToken,
        tokenHint = s.tokenHint,
        drawIcons = s.drawIcons,
        test = i.test,
        tokenVisible = i.tokenVisible,
        canSave = i.token.isNotBlank(),
        loading = false,
        notice = i.notice,
        settings = s,
        area = if (s.useMyArea) i.area else AreaLookup.Off,
        listTitle = title,
    )

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(c.settings, c.assistant, c.places, c.store.states.map { it.currentList().title }, c.network)
            }
        }
    }
}

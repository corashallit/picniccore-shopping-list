package earth.cora.picnic.ui.aisles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.assistant.AisleSyncReport
import earth.cora.picnic.core.catalog.BasketCatalog
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.itemsNeedingAisle
import earth.cora.picnic.core.model.setAislesText
import earth.cora.picnic.core.model.setStoreName
import earth.cora.picnic.core.store.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/*
 * UI state of the Aisles screen. Names are binding (docs/ARCHITECTURE.md §10.3); fields may be added.
 */

data class AislesUiState(
    val storeName: String = "",
    /** The editable aisle text (one aisle per line, walking order). */
    val text: String = "",
    /** Live parse of [text] with each aisle's basket (saved one, else the keyword guess). */
    val parsed: List<AislePreviewUi> = emptyList(),
    /** The draft differs from what is saved. */
    val dirty: Boolean = false,
    /** Aisle sync (baskets + placing unsorted items) is running. */
    val syncing: Boolean = false,
    val lastReport: AisleSyncReport? = null,
    val loading: Boolean = true,
    /** Items (all lists) that have no aisle yet: what "Re-sort my list" would place. */
    val needingAisle: Int = 0,
)

data class AislePreviewUi(
    val aisle: Aisle,
    val basket: BasketKind,
    /** Unchecked items of the current list in this aisle (0 for a new or renamed aisle). */
    val itemCount: Int = 0,
)

sealed interface AislesEvent {
    data object SaveFailed : AislesEvent

    /** A sync finished: the screen turns the report into a snackbar. */
    data class Synced(val report: AisleSyncReport) : AislesEvent
}

/**
 * Store name + aisle text editor with a live preview. Saving runs setStoreName + setAislesText
 * (heuristic baskets show at once), then AisleSync in the app scope: the fast model assigns
 * baskets and places items whose aisle vanished. "Re-sort my list" saves pending edits, then syncs.
 */
class AislesViewModel(
    private val store: StateStore,
    private val aisleSync: AisleSync,
    private val baskets: BasketCatalog,
    private val appScope: CoroutineScope,
    /** Epoch millis for the trip reconcile at the end of setAislesText (§19.1). */
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private data class Draft(val storeName: String, val text: String)

    /** Null until the user types: the fields then mirror what is saved. */
    private val draft = MutableStateFlow<Draft?>(null)
    private val syncing = MutableStateFlow(false)
    private val report = MutableStateFlow<AisleSyncReport?>(null)
    private val _events = Channel<AislesEvent>(Channel.BUFFERED)

    val events: Flow<AislesEvent> = _events.receiveAsFlow()

    val state: StateFlow<AislesUiState> = combine(store.states, draft, syncing, report) { s, d, sy, r ->
        build(s, d, sy, r)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AislesUiState())

    fun setStoreName(name: String) = editDraft { it.copy(storeName = name) }

    fun setText(text: String) = editDraft { it.copy(text = text) }

    /** Throws the draft away. */
    fun discard() {
        draft.value = null
    }

    /** Saves the draft and starts the background sync (baskets + recategorizing vanished aisles). */
    fun save() {
        val d = draft.value
        if (d == null) {
            resort()
            return
        }
        appScope.launch {
            try {
                store.update { it.setStoreName(d.storeName).setAislesText(d.text, baskets::guess, clock()) }
            } catch (e: IOException) {
                _events.send(AislesEvent.SaveFailed)
                return@launch
            }
            // Keep typing that happened while saving; otherwise the fields mirror the store again.
            draft.update { if (it == d) null else it }
            sync()
        }
    }

    /** "Re-sort my list": save pending edits (which syncs), or just sync. */
    fun resort() {
        if (draft.value != null) save() else appScope.launch { sync() }
    }

    private suspend fun sync() {
        syncing.value = true
        val result = try {
            aisleSync.sync()
        } finally {
            syncing.value = false
        }
        report.value = result
        _events.send(AislesEvent.Synced(result))
    }

    private fun editDraft(change: (Draft) -> Draft) {
        val current = state.value
        draft.update { change(it ?: Draft(current.storeName, current.text)) }
    }

    private fun build(s: AppState, d: Draft?, syncing: Boolean, report: AisleSyncReport?): AislesUiState {
        val name = d?.storeName ?: s.store.name
        val text = d?.text ?: s.store.aislesText
        val counts = s.currentList().items.filterNot { it.checked }.groupingBy { it.aisleId }.eachCount()
        val parsed = Aisles.parse(text).map { aisle ->
            AislePreviewUi(aisle, s.store.aisleBaskets[aisle.id] ?: baskets.guess(aisle), counts[aisle.id] ?: 0)
        }
        val dirty = d != null && (d.text != s.store.aislesText || d.storeName.trim().ifEmpty { s.store.name } != s.store.name)
        return AislesUiState(
            storeName = name,
            text = text,
            parsed = parsed,
            dirty = dirty,
            syncing = syncing,
            lastReport = report,
            loading = false,
            needingAisle = s.itemsNeedingAisle().size,
        )
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AislesViewModel(c.store, c.aisleSync, c.baskets, c.appScope, c.clock) }
        }
    }
}

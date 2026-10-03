package earth.cora.picnic.ui.aisles

import androidx.compose.runtime.Immutable
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
import earth.cora.picnic.core.model.AisleStyle
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.commitAislesText
import earth.cora.picnic.core.model.moveAisle
import earth.cora.picnic.core.model.renameAisle
import earth.cora.picnic.core.model.setAisleBasket
import earth.cora.picnic.core.model.setAisleStyle
import earth.cora.picnic.core.model.setStoreName
import earth.cora.picnic.core.model.storeAisleNumber
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.platform.PicnicLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/*
 * "My store" (docs/DESIGN_STICKER.md §6.7, §4.26, checklist 8, 220–225). Owner: WP8 (AisleEditorTest). The store name,
 * the sticker-sheet labels (drag to reorder through moveAisle; pencil → rename through renameAisle, store aisle number,
 * colour, pattern and basket kind), "Add aisle", "Aisles as text" (previewed live at 400 ms, committed on Done or blur
 * through commitAislesText's line diff) and "Re-sort my list" (AisleSync.resortAll). Every write runs in the app scope,
 * one at a time, in the order she made them, and a pending text edit is committed before any label change.
 */

/** One label of the sticker sheet (§4.26). [storeAisle] = the editor's number, else the one in the name. */
@Immutable
data class AisleLabelUi(
    val aisle: Aisle,
    val style: AisleStyle?,
    val storeAisle: Int?,
    val basket: BasketKind,
)

/** UI state of "My store". Names are binding (docs/ARCHITECTURE.md §10.3); fields may be added. */
@Immutable
data class AislesUiState(
    val storeName: String = "",
    /** The "Aisles as text" field: the draft while she types, else the saved text. */
    val text: String = "",
    /** The labels: the saved aisles, or the debounced preview of the draft text while she types. */
    val labels: List<AisleLabelUi> = emptyList(),
    /** The text field differs from what is saved. */
    val dirty: Boolean = false,
    /** The labels show a preview of unsaved text (labels can't be edited until it is committed). */
    val previewing: Boolean = false,
    /** The aisle id whose label is open for editing (§4.26), if any. */
    val editing: String? = null,
    /** "Re-sort my list" is running: the ladybug ring circles the button. */
    val syncing: Boolean = false,
    val lastReport: AisleSyncReport? = null,
    val loading: Boolean = true,
)

sealed interface AislesEvent {
    data object SaveFailed : AislesEvent

    /** A re-sort finished: the screen turns the report into a toast. */
    data class Synced(val report: AisleSyncReport) : AislesEvent
}

class AislesViewModel(
    private val store: StateStore,
    private val aisleSync: AisleSync,
    private val baskets: BasketCatalog,
    private val appScope: CoroutineScope,
    /** Epoch millis for the trip reconcile (§19.1). */
    private val clock: () -> Long = System::currentTimeMillis,
    previewDelayMillis: Long = PREVIEW_MILLIS,
) : ViewModel() {
    private val textDraft = MutableStateFlow<String?>(null)
    private val nameDraft = MutableStateFlow<String?>(null)
    private val editing = MutableStateFlow<String?>(null)
    private val syncing = MutableStateFlow(false)
    private val report = MutableStateFlow<AisleSyncReport?>(null)
    private val writes = Mutex()
    private val _events = Channel<AislesEvent>(Channel.BUFFERED)

    val events: Flow<AislesEvent> = _events.receiveAsFlow()

    @OptIn(FlowPreview::class)
    private val previewText: Flow<String?> = textDraft.debounce { if (it == null) 0L else previewDelayMillis }.distinctUntilChanged()

    private data class Ui(val name: String?, val editing: String?, val syncing: Boolean, val report: AisleSyncReport?)

    private val ui = combine(nameDraft, editing, syncing, report) { n, e, s, r -> Ui(n, e, s, r) }

    val state: StateFlow<AislesUiState> = combine(store.states, textDraft, previewText, ui) { s, draft, preview, u ->
        build(s, draft, preview, u)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AislesUiState())

    /* ---------------- store name ---------------- */

    fun setStoreName(name: String) {
        nameDraft.value = name
    }

    /** The store field lost focus (or Done): saves a non-blank name. */
    fun commitStoreName() {
        if (nameDraft.value != null) write { it }
    }

    /* ---------------- aisles as text ---------------- */

    fun setText(text: String) {
        textDraft.value = text
    }

    /** Done or the text field lost focus: the line diff (an edited line keeps its items, a removed one's go to Everything else). */
    fun commitText() {
        if (textDraft.value == null) return
        write { it }
    }

    /* ---------------- labels ---------------- */

    /** The pencil: opens [aisleId]'s label for editing (one at a time). */
    fun startEditing(aisleId: String) {
        commitText()
        editing.value = aisleId
    }

    fun stopEditing() {
        editing.value = null
    }

    /** A new name for the label (renameAisle: its items, basket, style, corrections and the open aisle follow). */
    fun rename(aisleId: String, name: String) = writeThen(
        op = { it.renameAisle(aisleId, name, clock()) },
        then = { before, after ->
            val position = before.store.aisles().indexOfFirst { it.id == aisleId }
            if (position >= 0 && editing.value == aisleId) editing.value = after.store.aisles().getOrNull(position)?.id
        },
    )

    /** The store's own aisle number ("store aisle 2"); null = none of her own (the name's prefix, if any, shows). */
    fun setStoreAisle(aisleId: String, number: Int?) = restyle(aisleId) { it.copy(storeAisle = number) }

    fun setFamily(aisleId: String, family: String) = restyle(aisleId) { it.copy(family = family) }

    fun setPattern(aisleId: String, pattern: String) = restyle(aisleId) { it.copy(pattern = pattern) }

    fun setBasket(aisleId: String, kind: BasketKind) = write { it.setAisleBasket(aisleId, kind) }

    /** A label dropped at [to] (0-based walking positions): moveAisle; everything follows its aisle. */
    fun move(from: Int, to: Int) = writeThen(
        op = { it.moveAisle(from, to, baskets::guess) },
        then = { before, after ->
            val editedAt = before.store.aisles().indexOfFirst { it.id == editing.value }
            if (editedAt >= 0) {
                val now = when {
                    editedAt == from -> to
                    from < to && editedAt in (from + 1)..to -> editedAt - 1
                    to < from && editedAt in to until from -> editedAt + 1
                    else -> editedAt
                }
                editing.value = after.store.aisles().getOrNull(now)?.id
            }
        },
    )

    /** "Add aisle": a new label at the end, open for editing (its name field focused). */
    fun addAisle(name: String) = writeThen(
        op = { s -> s.commitAislesText(s.store.aislesText.trimEnd('\n', ' ') + "\n" + name, baskets::guess, clock()) },
        then = { _, after -> editing.value = after.store.aisles().lastOrNull()?.id },
    )

    /* ---------------- re-sort, leaving ---------------- */

    /** "Re-sort my list": commits pending edits, then re-files every item not placed by hand (AisleSync.resortAll). */
    fun resort() {
        if (syncing.value) return
        syncing.value = true
        appScope.launch {
            try {
                writes.withLock { commitPending() }
                val result = aisleSync.resortAll()
                report.value = result
                _events.send(AislesEvent.Synced(result))
            } catch (e: IOException) {
                PicnicLog.w("couldn't save the aisles before a re-sort", e)
                _events.send(AislesEvent.SaveFailed)
            } finally {
                syncing.value = false
            }
        }
    }

    /** Done or Back: commits the store name and the text, closes the label being edited. */
    fun leave() {
        editing.value = null
        if (nameDraft.value != null || textDraft.value != null) write { it }
    }

    /* ---------------- internals ---------------- */

    private fun restyle(aisleId: String, change: (AisleStyle) -> AisleStyle) = write { s ->
        s.setAisleStyle(aisleId, change(s.store.aisleStyles[aisleId] ?: AisleStyle()))
    }

    /**
     * Runs [op] after committing whatever is pending, in the app scope, one write at a time and in order; [then] sees
     * the state before and after it (to keep the open label on its aisle when ids renumber).
     */
    private fun write(op: (AppState) -> AppState) = writeThen(op) { _, _ -> }

    private fun writeThen(op: (AppState) -> AppState, then: (before: AppState, after: AppState) -> Unit) {
        appScope.launch {
            writes.withLock {
                try {
                    commitPending()
                    var before: AppState? = null
                    val after = store.update { s -> op(s).also { before = s } }
                    before?.let { then(it, after) }
                } catch (e: IOException) {
                    PicnicLog.w("couldn't save the aisles", e)
                    _events.send(AislesEvent.SaveFailed)
                }
            }
        }
    }

    /** Commits the store-name and text drafts (in that order). The drafts clear only once saved. */
    private suspend fun commitPending() {
        val name = nameDraft.value
        val text = textDraft.value
        if (name == null && text == null) return
        store.update { s ->
            var next = s
            if (name != null) next = next.setStoreName(name)
            if (text != null) next = next.commitAislesText(text, baskets::guess, clock())
            next
        }
        nameDraft.compareAndSet(name, null)
        textDraft.compareAndSet(text, null)
    }

    private fun build(s: AppState, draft: String?, preview: String?, u: Ui): AislesUiState =
        stateOf(s, baskets, draft, preview, u.name, u.editing, u.syncing, u.report)

    companion object {
        /** The labels preview the text 400 ms after she stops typing (§6.7). */
        const val PREVIEW_MILLIS: Long = 400

        /**
         * The screen's state for [s] (pure: the ViewModel, the goldens and the tests build it the same way): the labels of
         * the saved aisles, or of the [preview] of the [draft] text while she types.
         */
        fun stateOf(
            s: AppState,
            baskets: BasketCatalog,
            draft: String? = null,
            preview: String? = draft,
            name: String? = null,
            editing: String? = null,
            syncing: Boolean = false,
            report: AisleSyncReport? = null,
        ): AislesUiState {
            val previewing = draft != null && preview != null && preview != s.store.aislesText
            val aisles = if (previewing) Aisles.parse(preview!!) else s.store.aisles()
            val labels = aisles.map { aisle ->
                val style = s.store.aisleStyles[aisle.id]
                AisleLabelUi(
                    aisle = aisle,
                    style = style,
                    storeAisle = style?.storeAisle ?: storeAisleNumber(aisle.name),
                    basket = s.store.aisleBaskets[aisle.id] ?: baskets.guess(aisle),
                )
            }
            return AislesUiState(
                storeName = name ?: s.store.name,
                text = draft ?: s.store.aislesText,
                labels = labels,
                dirty = draft != null && draft != s.store.aislesText,
                previewing = previewing,
                editing = editing?.takeIf { id -> !previewing && labels.any { it.aisle.id == id } },
                syncing = syncing,
                lastReport = report,
                loading = false,
            )
        }

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AislesViewModel(c.store, c.aisleSync, c.baskets, c.appScope, c.clock) }
        }
    }
}

/** The basket kinds in the editor's sticker row (§4.26: 17 kinds), in catalog order. */
val EditorBaskets: List<BasketKind> = BasketKind.entries.toList()

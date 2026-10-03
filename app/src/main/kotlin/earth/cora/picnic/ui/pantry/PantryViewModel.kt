package earth.cora.picnic.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.PANTRY_IN_CONTEXT
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.model.PantryNames
import earth.cora.picnic.core.model.addToPantry
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.pantryText
import earth.cora.picnic.core.model.removeFromPantry
import earth.cora.picnic.core.model.setPantryIcon
import earth.cora.picnic.core.model.setPantryText
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.settings.SettingsRepository
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/*
 * My pantry (docs/DESIGN_STICKER.md §6.13, checklist 325–328). Owner: WP8 (PantryScreenTest, PantryIconsTest).
 * One pantry for the app. Every change goes through core's pantry ops inside store.update, in [appScope], so leaving the
 * screen never cancels a save; the text field previews the shelves (400 ms debounce) and commits on Done or blur.
 */

/** One sticker on the shelves. [key] = normalizeName(name). */
data class ShelfSticker(val key: String, val name: String, val icon: IconRef, val busy: Boolean)

data class PantryUiState(
    val loading: Boolean = true,
    /** The shelves, in the text's order: the store's, or the live preview of the text being edited. */
    val stickers: List<ShelfSticker> = emptyList(),
    /** The "Add to pantry:" field. */
    val addInput: String = "",
    /** "Pantry as text:": the draft while she edits it, else the stored text. */
    val text: String = "",
    /** The text differs from what is stored (a commit is due on Done or blur). */
    val textDirty: Boolean = false,
    /** Stickers that arrived and play the magic appear once (keys). */
    val fresh: Set<String> = emptySet(),
    /** The sticker that pops because its name was added again (key + a counter so a repeat pops again). */
    val popped: Pair<String, Int>? = null,
    /** More than 200 entries: "Recipes check the first 200". */
    val overLimit: Boolean = false,
    /** A key is set: unknown names get a sticker from the IconDecider. */
    val hasToken: Boolean = false,
)

/** What the screen shows once (toasts). */
sealed interface PantryEvent {
    data class AlreadyThere(val name: String) : PantryEvent
    data class TookOff(val entry: PantryEntry, val index: Int) : PantryEvent
    data object SaveFailed : PantryEvent
}

@OptIn(FlowPreview::class)
class PantryViewModel(
    private val store: StateStore,
    private val settings: SettingsRepository,
    private val iconDecider: IconDecider,
    private val catalog: IconCatalog,
    private val clock: () -> Long,
    private val appScope: CoroutineScope,
    private val previewDelayMillis: Long = PREVIEW_MILLIS,
) : ViewModel() {
    private val draft = MutableStateFlow<String?>(null)
    private val addInput = MutableStateFlow("")
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val fresh = MutableStateFlow<Set<String>>(emptySet())
    private val popped = MutableStateFlow<Pair<String, Int>?>(null)
    private val _events = Channel<PantryEvent>(Channel.BUFFERED)

    val events: Flow<PantryEvent> = _events.receiveAsFlow()

    /** The draft, 400 ms after the last keystroke (null = not editing). */
    private val previewText: Flow<String?> = draft.debounce { if (it == null) 0L else previewDelayMillis }.onStart { emit(null) }.distinctUntilChanged()

    private data class Local(val draft: String?, val add: String, val busy: Set<String>, val fresh: Set<String>, val popped: Pair<String, Int>?)

    private val local = combine(draft, addInput, busy, fresh, popped) { d, a, b, f, p -> Local(d, a, b, f, p) }

    val state: StateFlow<PantryUiState> = combine(
        store.states,
        local,
        previewText,
        settings.settings.map { it.hasToken }.distinctUntilChanged(),
    ) { s, l, preview, hasToken ->
        val shown = if (preview != null) s.setPantryText(preview, clock()) else s
        PantryUiState(
            loading = false,
            stickers = shown.pantry.map { e ->
                val key = normalizeName(e.name)
                ShelfSticker(key, e.name, PantryIcons.display(e.icon), busy = key in l.busy)
            },
            addInput = l.add,
            text = l.draft ?: s.pantryText(),
            textDirty = l.draft != null && l.draft.trim() != s.pantryText(),
            fresh = l.fresh,
            popped = l.popped,
            overLimit = shown.pantry.size > PANTRY_IN_CONTEXT,
            hasToken = hasToken,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PantryUiState())

    fun setAddInput(text: String) = addInput.update { text }

    /**
     * The add field (Enter or the Add button): commas, new lines and " and " split entries, quantities are dropped,
     * names are capitalised; a name already on the shelves pops its sticker and says so instead.
     */
    fun add() {
        val text = addInput.value
        if (PantryNames.split(text).isEmpty()) return
        addInput.value = ""
        addNames(PantryNames.split(text))
    }

    /** One of the empty shelf's suggestion chips ("Salt", "Pepper", "Oil", "Flour"). */
    fun addSuggestion(name: String) = addNames(listOf(name))

    private fun addNames(names: List<String>) {
        val now = clock()
        commit({ s ->
            val there = s.pantry.mapTo(HashSet()) { normalizeName(it.name) }
            val fresh = names.filterNot { normalizeName(it) in there }
            if (fresh.isEmpty()) s else s.addToPantry(fresh, now)
        }) { before, _ ->
            // A name already on the shelves pops its sticker and says so (the last one, if several were).
            val there = before.pantry.associateBy { normalizeName(it.name) }
            names.mapNotNull { there[normalizeName(it)] }.lastOrNull()?.let { entry ->
                popped.update { normalizeName(entry.name) to ((it?.second ?: 0) + 1) }
                _events.send(PantryEvent.AlreadyThere(entry.name))
            }
        }
    }

    /** "Pantry as text:" changed: the shelves preview it 400 ms later; nothing is stored yet. */
    fun setText(text: String) {
        draft.value = text
    }

    /**
     * Done, or the text field lost focus: the text becomes the shelves (setPantryText: split, cleaned, de-duplicated).
     * The draft stays until the write has landed (the store fsyncs before it emits), so the field and the shelves never
     * fall back to the old pantry meanwhile; it is cleared only if she has not typed on since. A failed write keeps it:
     * her text stays in the field, still unsaved, and the toast says "Couldn't save. Try again."
     */
    fun commitText() {
        val text = draft.value ?: return
        val now = clock()
        commit({ it.setPantryText(text, now) }) { _, _ -> draft.compareAndSet(text, null) }
    }

    /** "Take off the shelf": removes it; the toast offers Undo ([undoTakeOff]). */
    fun takeOff(key: String) {
        commit({ it.removeFromPantry(key) }) { before, _ ->
            val index = before.pantry.indexOfFirst { normalizeName(it.name) == key }
            if (index >= 0) _events.send(PantryEvent.TookOff(before.pantry[index], index))
        }
    }

    /** Undo of [takeOff]: the sticker goes back exactly where it was, with its sticker and date. */
    fun undoTakeOff(entry: PantryEntry, index: Int) = commit({ it.restoreToShelf(entry, index) })

    /** "Change sticker": the shelf sticker shows [iconKey] from now on. */
    fun changeSticker(key: String, iconKey: String) = commit({ it.setPantryIcon(key, IconRef(iconKey)) })

    /** The magic appear of [key] has played. */
    fun appeared(key: String) = fresh.update { it - key }

    /** Leaving the screen: a pending text edit is committed (it lost focus). */
    fun leave() = commitText()

    /**
     * Writes [transform] (with local sticker resolution), marks the new names fresh, runs [then] with the states before
     * and after, then asks the IconDecider ONCE about what is still unknown (with a key only; never a drawing).
     */
    private fun commit(transform: (AppState) -> AppState, then: suspend (before: AppState, after: AppState) -> Unit = { _, _ -> }) {
        appScope.launch {
            var before: AppState? = null
            val after = try {
                store.update { s ->
                    before = s
                    PantryIcons.resolveLocally(transform(s), catalog)
                }
            } catch (e: IOException) {
                _events.send(PantryEvent.SaveFailed)
                return@launch
            }
            val old = before ?: return@launch
            val had = old.pantry.mapTo(HashSet()) { normalizeName(it.name) }
            val added = after.pantry.map { normalizeName(it.name) }.filterNot { it in had }
            if (added.isNotEmpty()) fresh.update { it + added }
            then(old, after)
            decideUnknown(after)
        }
    }

    private suspend fun decideUnknown(state: AppState) {
        val unknown = PantryIcons.unresolved(state).filterNot { it.id in busy.value }
        if (unknown.isEmpty() || !settings.current().hasToken) return
        val ids = unknown.mapTo(HashSet()) { it.id }
        busy.update { it + ids }
        try {
            val decided = PantryIcons.decide(iconDecider, unknown, catalog)
            if (decided.isNotEmpty()) store.update { PantryIcons.apply(it, decided) }
        } catch (e: IOException) {
            _events.send(PantryEvent.SaveFailed)
        } finally {
            busy.update { it - ids }
        }
    }

    companion object {
        /** The text field previews the shelves this long after the last keystroke (§6.13). */
        const val PREVIEW_MILLIS: Long = 400

        /** The empty shelf's suggestions (§6.13). */
        val SUGGESTIONS: List<String> = listOf("Salt", "Pepper", "Oil", "Flour")

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PantryViewModel(c.store, c.settings, c.iconDecider, c.catalog, c.clock, c.appScope) }
        }
    }
}

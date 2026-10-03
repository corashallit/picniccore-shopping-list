package earth.cora.picnic.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.clearChecked
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.editItemDetails
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.moveItemToAisle
import earth.cora.picnic.core.model.newList
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.removeItem
import earth.cora.picnic.core.model.renameList
import earth.cora.picnic.core.model.restoreItem
import earth.cora.picnic.core.model.setItemIcon
import earth.cora.picnic.core.model.setSectionCollapsed
import earth.cora.picnic.core.model.switchList
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.core.store.TripFiling
import earth.cora.picnic.icons.IconRepository
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.common.ScreenText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The shopping list (docs/ARCHITECTURE.md §10.3). Every change goes through StateOps inside
 * store.update, launched in [appScope] so leaving the screen never cancels a save. Item actions
 * look up the list that holds the item, so they stay correct if the current list changes meanwhile.
 */
class ListViewModel(
    private val store: StateStore,
    private val album: AlbumStore,
    private val icons: IconRepository,
    private val settings: SettingsRepository,
    private val catalog: IconCatalog,
    private val clock: () -> Long,
    private val newId: () -> String,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val editingId = MutableStateFlow<String?>(null)
    private val _events = Channel<ListEvent>(Channel.BUFFERED)

    /** One-off UI events (snackbars). Collect once, from the screen. */
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    val state: StateFlow<ListUiState> = combine(
        store.states,
        icons.busy,
        settings.settings.map { it.hasToken }.distinctUntilChanged(),
        editingId,
    ) { appState, busy, hasToken, editing ->
        buildListUiState(appState, busy, hasToken, editing, catalog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ListUiState())

    /** Tap on a row: checked <-> unchecked. */
    fun toggle(itemId: String) {
        val now = clock()
        mutate { s -> s.listIdOf(itemId)?.let { s.toggleChecked(it, itemId, now) } ?: s }
    }

    /** Swipe to delete. Emits [ListEvent.Deleted] so the screen can offer Undo. */
    fun delete(itemId: String) {
        if (editingId.value == itemId) editingId.value = null
        val now = clock()
        appScope.launch {
            var removed: ListEvent.Deleted? = null
            val ok = save {
                store.update { s ->
                    val listId = s.listIdOf(itemId) ?: return@update s
                    val items = s.list(listId)?.items.orEmpty()
                    val index = items.indexOfFirst { it.id == itemId }
                    removed = ListEvent.Deleted(items[index], index, listId)
                    s.removeItem(listId, itemId, now)
                }
            }
            if (ok) removed?.let { _events.send(it) }
        }
    }

    /** Undo of a swipe-to-delete: re-inserts the item at its old position. */
    fun undoDelete(item: GroceryItem, index: Int, listId: String? = null) {
        val now = clock()
        mutate { s -> s.restoreItem(listId?.takeIf { s.list(it) != null } ?: s.currentList().id, item, index, now) }
    }

    /** Header tap: collapse/expand a section of the current list (persisted per list). */
    @Suppress("DEPRECATION") // v1 collapse until WP5 lands one open aisle (setOpenAisle, D2).
    fun toggleSection(sectionId: String) = mutate { s ->
        val list = s.currentList()
        s.setSectionCollapsed(list.id, sectionId, sectionId !in list.collapsedAisles)
    }

    /** Removes the checked items of the current list. Emits [ListEvent.Cleared] for Undo. */
    @Suppress("DEPRECATION") // v1 "Clear checked" until WP5's New trip (newTrip, D1).
    fun clearChecked() {
        appScope.launch {
            var cleared: ListEvent.Cleared? = null
            val ok = save {
                store.update { s ->
                    val list = s.currentList()
                    val removed = list.items.withIndex().filter { it.value.checked }
                    if (removed.isEmpty()) return@update s
                    cleared = ListEvent.Cleared(removed, list.id)
                    s.clearChecked(list.id)
                }
            }
            if (ok) cleared?.let { _events.send(it) }
        }
    }

    /** Undo of [clearChecked]: puts the items back where they were (ascending index order). */
    fun undoClear(removed: List<IndexedValue<GroceryItem>>, listId: String) {
        val now = clock()
        mutate { s -> removed.sortedBy { it.index }.fold(s) { acc, (index, item) -> acc.restoreItem(listId, item, index, now) } }
    }

    fun newList(title: String) {
        val id = newId()
        val now = clock()
        mutate { it.newList(id, title, now) }
    }

    fun switchList(id: String) {
        editingId.value = null
        mutate { it.switchList(id) }
    }

    fun renameList(id: String, title: String) = mutate { it.renameList(id, title) }

    /**
     * Deletes a list (§6.4). A list with a trip has its sticker book filed in the album first (a printed receipt as
     * saved), and only then leaves state.json (TripFiling.deleteList, §7.2, checklist 144): a failed album write keeps
     * the list.
     */
    fun deleteList(id: String) {
        val now = clock()
        appScope.launch { save { TripFiling.deleteList(store, album, id, now) } }
    }

    fun openEdit(itemId: String) {
        editingId.value = itemId
    }

    fun closeEdit() {
        editingId.value = null
    }

    /**
     * Edit sheet "Save": name, quantity, note of the item being edited, then closes the sheet. A rename
     * re-resolves the icon locally (StateOps.editItemDetails) and hands the item to the icon cascade.
     */
    fun saveEdit(name: String, quantity: String?, note: String?) {
        val itemId = editingId.value ?: return
        editingId.value = null
        appScope.launch {
            val before = store.current().findItem(itemId) ?: return@launch
            var after: AppState? = null
            save {
                after = store.update { s ->
                    s.listIdOf(itemId)?.let { s.editItemDetails(it, itemId, name, quantity, note, catalog) } ?: s
                }
            }
            val updated = after?.findItem(itemId) ?: return@launch
            if (normalizeName(updated.name) != normalizeName(before.name)) icons.submit(listOf(updated))
        }
    }

    /** "Move to aisle" (null = Unsorted): also teaches the app where this item lives (a learned correction). */
    fun moveToAisle(itemId: String, aisleId: String?) {
        val now = clock()
        mutate { s -> s.listIdOf(itemId)?.let { s.moveItemToAisle(it, itemId, aisleId, now) } ?: s }
    }

    /** "Change icon": a learned icon override; the bespoke icon cached for the name is forgotten. */
    fun setIcon(itemId: String, key: String) {
        appScope.launch {
            val name = store.current().findItem(itemId)?.name ?: return@launch
            val now = clock()
            val ok = save {
                store.update { s -> s.listIdOf(itemId)?.let { s.setItemIcon(it, itemId, key, now, catalog) } ?: s }
            }
            if (ok) icons.forgetBespoke(name)
        }
    }

    /** "Draw a new icon": a forced live drawing that replaces any bespoke icon for this name. */
    fun redrawIcon(itemId: String) {
        appScope.launch {
            val name = store.current().findItem(itemId)?.name ?: return@launch
            icons.redraw(name)
        }
    }

    private fun mutate(transform: (AppState) -> AppState) {
        appScope.launch { save { store.update(transform) } }
    }

    /** Runs a store write; a failed write keeps the old state and tells the user. */
    private suspend fun save(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: IOException) {
        _events.send(ListEvent.Message(ScreenText.SAVE_FAILED))
        false
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ListViewModel(c.store, c.album, c.icons, c.settings, c.catalog, c.clock, c::newId, c.appScope) }
        }
    }
}

/** Id of the list that holds [itemId], or null. */
internal fun AppState.listIdOf(itemId: String): String? = lists.firstOrNull { l -> l.items.any { it.id == itemId } }?.id

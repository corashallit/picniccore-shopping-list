package earth.cora.picnic.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.PicnicApp
import earth.cora.picnic.R
import earth.cora.picnic.core.model.AisleStyle
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.removeFromPantry
import earth.cora.picnic.core.model.setFavourite
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.ui.common.ScreenText
import earth.cora.picnic.ui.list.EditItemUi
import earth.cora.picnic.ui.pantry.pantryEntryFor
import earth.cora.picnic.ui.pantry.putOnShelf
import earth.cora.picnic.ui.theme.ToastHost
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/*
 * What the Edit sheet needs beyond the list's EditItemUi (§6.3): the favourite heart (D17), the "In my pantry" toggle
 * (§0.4, §6.13, checklist 329) and the aisle colours the editor set (§4.26). Owner: WP8 (EditSheetTest).
 */

/** The Edit sheet's store-backed extras. */
data class EditExtrasUi(
    val favourites: Set<String> = emptySet(),
    val state: AppState? = null,
    val aisleStyles: Map<String, AisleStyle> = emptyMap(),
    /** The store has answered: until then the toggles show off without a magic appear when the real value arrives. */
    val loaded: Boolean = false,
) {
    fun favourite(name: String): Boolean = normalizeName(name) in favourites

    fun inPantry(name: String): Boolean = state?.pantryEntryFor(name) != null
}

/** One sticky-note toast the toggles ask for. */
data class EditToast(val text: String)

/**
 * The favourite and pantry toggles (writes run in [appScope], so closing the sheet never cancels them). Pantry toasts:
 * "Flour is in your pantry · recipes will leave it out" / "Flour is off your pantry shelf" (§16).
 */
class EditExtrasViewModel(
    private val store: StateStore,
    private val clock: () -> Long,
    private val appScope: CoroutineScope,
) : ViewModel() {
    // No replay: a toast that lands after the sheet has closed (nobody collecting) is dropped, never shown stale the next
    // time a sheet opens.
    private val _toasts = MutableSharedFlow<EditToast>(extraBufferCapacity = 8)

    /** Toast texts to show (once each, to whoever is showing the sheet at the time). */
    val toasts: Flow<EditToast> = _toasts.asSharedFlow()

    val extras: StateFlow<EditExtrasUi> = store.states
        .map { EditExtrasUi(favourites = it.favourites, state = it, aisleStyles = it.store.aisleStyles, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditExtrasUi())

    fun setFavourite(itemName: String, on: Boolean) {
        appScope.launch { save { store.update { it.setFavourite(normalizeName(itemName), on) } } }
    }

    /** On: the item's name goes on the shelf as one entry. Off: the entry that covers it comes off. */
    fun setInPantry(itemName: String, on: Boolean, onText: (String) -> String, offText: (String) -> String) {
        val now = clock()
        appScope.launch {
            var label: String? = null
            val ok = save {
                store.update { s ->
                    if (on) {
                        val next = s.putOnShelf(itemName, now)
                        label = next.pantryEntryFor(itemName)?.name
                        next
                    } else {
                        val entry = s.pantryEntryFor(itemName) ?: return@update s
                        label = entry.name
                        s.removeFromPantry(normalizeName(entry.name))
                    }
                }
            }
            val name = label ?: return@launch
            if (ok) _toasts.emit(EditToast(if (on) onText(name) else offText(name)))
        }
    }

    private suspend fun save(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: IOException) {
        _toasts.emit(EditToast(ScreenText.SAVE_FAILED))
        false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as PicnicApp).container
                EditExtrasViewModel(c.store, c.clock, c.appScope)
            }
        }
    }
}

/**
 * The Edit sheet as the List screen mounts it (§6.3, checklist 217, 329): [EditItemRoute] with a sticky-note host of its
 * own, so the favourite heart and "In my pantry" work and say so ("Flour is in your pantry · recipes will leave it out")
 * whatever the host. The toasts sit above the sheet's Save / Delete footer. A host with its own toast host (WP5's list,
 * once it has one) can mount [EditItemRoute] instead and pass its `show`.
 */
@Composable
fun EditItemSheet(
    state: EditItemUi,
    onSave: (name: String, quantity: String?, note: String?) -> Unit,
    onClose: () -> Unit,
    onMoveToAisle: (aisleId: String?) -> Unit,
    onSetIcon: (key: String) -> Unit,
    onRedraw: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EditExtrasViewModel = viewModel(factory = EditExtrasViewModel.Factory),
    toasts: ToastHostState = remember { ToastHostState() },
) {
    val bars = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize()) {
        EditItemRoute(
            state = state,
            onSave = onSave,
            onClose = onClose,
            onMoveToAisle = onMoveToAisle,
            onSetIcon = onSetIcon,
            onRedraw = onRedraw,
            onDelete = onDelete,
            onToast = toasts::show,
            vm = vm,
        )
        ToastHost(toasts, bottomInset = bars + SHEET_FOOTER, bottomGap = 8.dp)
    }
}

/** The Edit sheet's sticky footer (Delete, Save: a 48 dp row with 8 dp above and 18 dp below), which toasts clear. */
private val SHEET_FOOTER = 74.dp

/**
 * The Edit sheet with its store-backed toggles, for a host that shows the toasts itself ([onToast]). The list's own actions (save,
 * move, sticker, draw, delete) stay the host's; the favourite heart and "In my pantry" are handled here, their toasts
 * go to [onToast] (the host's sticky-note host).
 */
@Composable
fun EditItemRoute(
    state: EditItemUi,
    onSave: (name: String, quantity: String?, note: String?) -> Unit,
    onClose: () -> Unit,
    onMoveToAisle: (aisleId: String?) -> Unit,
    onSetIcon: (key: String) -> Unit,
    onRedraw: () -> Unit,
    onDelete: () -> Unit,
    onToast: (ToastMessage) -> Unit,
    modifier: Modifier = Modifier,
    vm: EditExtrasViewModel = viewModel(factory = EditExtrasViewModel.Factory),
) {
    val extras by vm.extras.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val toast by androidx.compose.runtime.rememberUpdatedState(onToast)
    LaunchedEffect(vm) { vm.toasts.collect { toast(ToastMessage(it.text, kind = "pantry")) } }
    val name = state.item.name
    val onText = remember(resources) { { n: String -> resources.getString(R.string.edit_pantry_on, n) } }
    val offText = remember(resources) { { n: String -> resources.getString(R.string.edit_pantry_off, n) } }
    EditItemSheetContent(
        state = state,
        onSave = onSave,
        onClose = onClose,
        onMoveToAisle = onMoveToAisle,
        onSetIcon = onSetIcon,
        onRedraw = onRedraw,
        onDelete = onDelete,
        favourite = extras.favourite(name),
        inPantry = extras.inPantry(name),
        onFavouriteChange = { vm.setFavourite(name, it) },
        onPantryChange = { vm.setInPantry(name, it, onText, offText) },
        aisleStyles = extras.aisleStyles,
        togglesReady = extras.loaded,
        modifier = modifier,
    )
}

package earth.cora.picnic.ui.list

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.ui.a11y.ClearOfCovers
import earth.cora.picnic.ui.a11y.ModalLayersHost
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.a11y.modalLayer
import earth.cora.picnic.ui.a11y.underModal
import earth.cora.picnic.ui.add.AddBar
import earth.cora.picnic.ui.add.AddBarCollapseState
import earth.cora.picnic.ui.add.AddBarUi
import earth.cora.picnic.ui.add.AddCommit
import earth.cora.picnic.ui.add.AddDock
import earth.cora.picnic.ui.add.AddEventsEffect
import earth.cora.picnic.ui.add.AddRules
import earth.cora.picnic.ui.add.AddSheetHost
import earth.cora.picnic.ui.add.AddViewModel
import earth.cora.picnic.ui.add.rememberAddFlows
import earth.cora.picnic.ui.common.NoMaterialPress
import earth.cora.picnic.ui.edit.EditItemSheet
import earth.cora.picnic.ui.eggs.EggAnchors
import earth.cora.picnic.ui.eggs.EggListHost
import earth.cora.picnic.ui.eggs.EggListScope
import earth.cora.picnic.ui.eggs.LocalListRoom
import earth.cora.picnic.ui.eggs.NoOpSurpriseEngine
import earth.cora.picnic.ui.eggs.SurpriseEngine
import earth.cora.picnic.ui.eggs.SurpriseState
import earth.cora.picnic.ui.list.motion.AmbientBasket
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.CelebrationHost
import earth.cora.picnic.ui.list.motion.FoldFocusEffect
import earth.cora.picnic.ui.list.motion.FoldToggles
import earth.cora.picnic.ui.list.motion.ListPresentation
import earth.cora.picnic.ui.list.motion.MotionChoreographer
import earth.cora.picnic.ui.list.motion.MotionEngineHost
import earth.cora.picnic.ui.list.motion.MotionOverlay
import earth.cora.picnic.ui.list.motion.ambientVisible
import earth.cora.picnic.ui.list.motion.rememberAmbient
import earth.cora.picnic.ui.list.motion.trayCurl
import earth.cora.picnic.ui.list.motion.visibleFraction
import earth.cora.picnic.ui.nav.LocalAppNav
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.EmptyState
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.LocalPieceProbe
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.MenuItem
import earth.cora.picnic.ui.theme.OverflowMenu
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.SheetFor
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import earth.cora.picnic.ui.theme.TrayMetrics
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.ui.theme.holdOnFrameClock
import earth.cora.picnic.ui.theme.probe
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The List screen (docs/DESIGN_STICKER.md §6.1). Top to bottom: the top stack (title, LCD, the trip's sticker book),
 * the list viewport (one entry per stop in walking order, "Everything else" first: folded strips and the one open tray,
 * its band pinned as the plate while its rows scroll), the docked add bar or the FAB, the overlay (hops, the Next card,
 * toasts, peek notes, eggs), the sheets and the celebration. Z order: list < add bar < overlay < sheets < celebration.
 * Owner: WP5.
 */

/** Everything the List screen (and its sheets) can ask for. Defaults do nothing. */
@Stable
class ListActions(
    val onToggle: (itemId: String) -> Unit = {},
    val onDelete: (itemId: String) -> Unit = {},
    val onOpenSection: (sectionId: String) -> Unit = {},
    val onFoldAll: () -> Unit = {},
    val onOpenEdit: (itemId: String) -> Unit = {},
    /** "Start my list" (§4.21): focuses the add field and opens the keyboard. */
    val onStartList: () -> Unit = {},
    val onNewTrip: (NewTripMode) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenAisles: () -> Unit = {},
    val onOpenPantry: () -> Unit = {},
    val onOpenAlbum: () -> Unit = {},
    /** Egg 14's toast "Look" (§12.2): the album at its back pocket. */
    val onOpenBackPocket: () -> Unit = onOpenAlbum,
    val onOpenSettings: () -> Unit = {},
    val onOpenLists: () -> Unit = {},
    val onCloseEdit: () -> Unit = {},
    val onSaveEdit: (name: String, quantity: String?, note: String?) -> Unit = { _, _, _ -> },
    val onMoveToAisle: (itemId: String, aisleId: String?) -> Unit = { _, _ -> },
    /** The add note's "Move" (§6.2): the Edit sheet for the item, opened at "Move to aisle". */
    val onOpenMove: (itemId: String) -> Unit = onOpenEdit,
    val onSetIcon: (itemId: String, key: String) -> Unit = { _, _ -> },
    val onRedrawIcon: (itemId: String) -> Unit = {},
    val onCloseLists: () -> Unit = {},
    val onSwitchList: (listId: String) -> Unit = {},
    val onNewList: (title: String) -> Unit = {},
    val onRenameList: (listId: String, title: String) -> Unit = { _, _ -> },
    val onDeleteList: (listId: String) -> Unit = {},
    // Egg hooks (§19.2): WP9's SurpriseEngine.
    val onSunTap: () -> Unit = {},
    val onMoonTap: () -> Unit = {},
    val onStampTap: (position: Int) -> Unit = {},
    val onLcdHold: () -> Unit = {},
    val onBasketHold: (sectionId: String, heldMs: Long) -> Unit = { _, _ -> },
    /** A sound and haptic for the screen's own gestures (§14.3). */
    val onCue: (Cue) -> Unit = {},
)

/**
 * What the eggs show on this list (WP9's SurpriseState, handed down by [EggListHost] in [ListScreen]): the pieces of the
 * list read it (the LCD, the title's holo, the hankos, the pixel and gold stickers, the nap cat, the snack doodle, the
 * basket's hello). Eggs off outside a list screen.
 */
internal val LocalListEggs = compositionLocalOf { SurpriseState.Off }

/** WP9's engine on this list (the nap cat's tap, §12.2 egg 8); the no-op outside a list screen. */
internal val LocalListSurprises = staticCompositionLocalOf<SurpriseEngine> { NoOpSurpriseEngine }

/** The screen's transient state: the menu, the sheets, a peek, the pull to refresh, the pops. */
@Stable
class ListScreenState(
    menuOpen: Boolean = false,
    newTripOpen: Boolean = false,
    tripBookOpen: Boolean = false,
    peek: String? = null,
) {
    var menuOpen by mutableStateOf(menuOpen)
    var newTripOpen by mutableStateOf(newTripOpen)
    var tripBookOpen by mutableStateOf(tripBookOpen)

    /** The book spot the Trip book sheet opens at (§6.6): where the list's book was when she opened it. */
    var tripBookSlot by mutableIntStateOf(0)

    /** The folded strip whose basket is held open (§5.3), or null. */
    var peek by mutableStateOf(peek)

    /** The LCD reads SYNC for 1500 ms after a refresh (§6.1). */
    var syncing by mutableStateOf(false)

    /** Bumped by each refresh: the sun spins once. */
    var sunSpin by mutableIntStateOf(0)

    /** Pull to refresh: how far the list follows the finger (px, at most 70 dp). */
    var pull by mutableFloatStateOf(0f)

    /** The scroll collapsed the app bar and the book. */
    var scrolledDown by mutableStateOf(false)

    /**
     * Item id -> its pop (a new row or a duplicate, §6.2). A pop plays once: the row hands it back when it has played
     * ([popped]), and one whose row was not on screen in time (a folded aisle, a row scrolled away) lapses
     * ([expirePops]), so no row ever flashes again when it comes back, or for the first time hours later.
     */
    val pops = mutableStateMapOf<String, PopRequest>()

    /** The latest commit's pops (bumped by [pop]); the list lets them lapse [PopRequest.window] ms on. */
    internal var popGeneration by mutableIntStateOf(0)
        private set
    private val popsStarted = HashSet<String>()

    /** Her own fold and unfold taps (FoldFocusEffect). */
    val toggles = FoldToggles()

    /**
     * Her last fold or open (a tap on a strip or on the open tray's chevron, Enter, TalkBack's actions) until the list
     * shows it: that change glides (§8.5, [TapGlide]) instead of swapping in one frame.
     */
    internal var foldTap: FoldTap? = null

    internal data class FoldTap(val sectionId: String, val open: Boolean)

    /**
     * The Edit sheet's sticky notes (its pantry notes, and a move's note while the sheet is up). When the sheet closes, a
     * move's note carries on over the list ([ToastHostState.handOver]), so its Undo is never lost with the sheet (§4.15).
     */
    val sheetToasts = ToastHostState()

    /** Her last tap that opened a folded strip: its tray scrolls into view once it is laid out (§6.1). */
    internal var openTap by mutableStateOf<OpenTap?>(null)
    private var openTaps = 0

    /**
     * A commit's first new row, brought into view once it is laid out (§6.2: the highlighter flash and the sticker pop
     * happen where she can see them), unless she is scrolling or it already shows.
     */
    internal var revealTap by mutableStateOf<RevealTap?>(null)
    private var reveals = 0

    internal data class RevealTap(val itemId: String, val n: Int)

    /** Brings [itemId] into view (its row, or the strip of its folded aisle) once the list shows it. */
    internal fun reveal(itemId: String) {
        revealTap = RevealTap(itemId, ++reveals)
    }

    /**
     * While above 0, the list's rows and strips take their new places at once instead of sliding (item 87): a tray
     * folding under the pinned plate (the list keeps its place on the strip that replaces it, so the strips below appear
     * where they belong rather than sliding up from below the viewport), and a torn row's slot closing (the rows below
     * follow its shrinking height frame by frame, so no gap opens in the tray).
     */
    internal var layoutSnaps by mutableIntStateOf(0)

    /** She opened [sectionId] herself (a tap on its strip, Enter, or TalkBack's "Expand"). */
    internal fun openedByHer(sectionId: String) {
        openTap = OpenTap(sectionId, ++openTaps)
    }

    internal data class OpenTap(val sectionId: String, val n: Int)

    internal val stripBounds = mutableStateMapOf<String, Rect>()
    internal var moreBounds by mutableStateOf<Rect?>(null)

    /** The list viewport's window bounds (the peek note stays inside it). */
    internal var listBounds by mutableStateOf<Rect?>(null)
    internal val focus = HashMap<String, Pair<FocusRequester, FocusRequester>>()

    /** Pops the stickers of a commit's rows, the new ones 80 ms apart (§6.2), the ones already there at once. */
    fun pop(commit: AddCommit) {
        val gen = popGeneration + 1
        commit.added.forEachIndexed { i, item -> pops[item.id] = PopRequest(gen, commit.popDelayMillis(i)) }
        commit.alreadyThere.forEach { pops[it.id] = PopRequest(gen, 0) }
        popGeneration = gen
    }

    /** [itemId]'s pop of [generation] has started to play (it is no longer lapsed by [expirePops]). */
    internal fun popStarted(itemId: String, generation: Int) {
        popsStarted += "$itemId:$generation"
    }

    /** [itemId]'s pop of [generation] has played: it never plays again. */
    internal fun popped(itemId: String, generation: Int) {
        popsStarted -= "$itemId:$generation"
        if (pops[itemId]?.generation == generation) pops.remove(itemId)
    }

    /** The pops up to [generation] that have not started by now lapse: their rows were not on screen in time. */
    internal fun expirePops(generation: Int) {
        val lapsed = pops.filter { (id, p) -> p.generation <= generation && "$id:${p.generation}" !in popsStarted }.keys
        lapsed.forEach { pops.remove(it) }
    }

    internal fun focusOf(sectionId: String): Pair<FocusRequester, FocusRequester> = focus.getOrPut(sectionId) { FocusRequester() to FocusRequester() }

    /** The list item that last took the keyboard's focus (its lazy key) and its section (§15: a fold hands focus on). */
    internal var keyFocus: Pair<String, String>? = null

    /** A section whose strip or chevron takes the keyboard's focus once laid out: the row that had it folded away. */
    internal var refocus by mutableStateOf<String?>(null)

    private val rowFocus = HashMap<String, FocusRequester>()

    /** The docked add bar (or its FAB): where Tab goes on from the list's last item (§15). */
    internal val dockFocus = FocusRequester()

    /** Row [id]'s focus requester (a sheet it opened gives focus back to it, §15). */
    internal fun rowFocus(id: String): FocusRequester = rowFocus.getOrPut(id) { FocusRequester() }

    /**
     * Gives the keyboard's focus back to the list item that last had it (a row, a strip, the open tray's chevron), when
     * it is still laid out: a sheet it opened has closed (§15). False when it is not.
     */
    internal fun restoreKeyFocus(): Boolean {
        val (key, section) = keyFocus ?: return false
        val requester = when {
            key.startsWith("strip:") -> focusOf(section).second
            key.startsWith("header:") -> focusOf(section).first
            else -> rowFocus[key] ?: return false
        }
        return runCatching { requester.requestFocus() }.getOrDefault(false)
    }

    /**
     * The re-sync (§6.1, item 119), however she asks for it (the pull, the overflow menu's Refresh, the list's custom
     * action): the cue, the re-sync itself, the sun's one spin and "SYNC" on the LCD for 1500 ms. Offline nothing
     * syncs, so the LCD keeps its word; the toast says what waits (§6.1).
     */
    fun refresh(actions: ListActions, online: Boolean = true) {
        actions.onCue(Cue.PULL_REFRESH)
        actions.onRefresh()
        sunSpin++
        syncing = online
    }

    companion object {
        /**
         * What survives turning the phone or a dark-mode switch (§6.5, §6.6): the open menu and sheets, and whether the
         * scroll had folded the bar and the book (the list keeps its own scroll).
         */
        val Saver: Saver<ListScreenState, Any> = listSaver(
            save = { listOf(it.menuOpen, it.newTripOpen, it.tripBookOpen, it.scrolledDown, it.tripBookSlot) },
            restore = {
                ListScreenState(menuOpen = it[0] as Boolean, newTripOpen = it[1] as Boolean, tripBookOpen = it[2] as Boolean).apply {
                    scrolledDown = it[3] as Boolean
                    tripBookSlot = it[4] as Int
                }
            },
        )
    }
}

@Composable
fun rememberListScreenState(): ListScreenState = rememberSaveable(saver = ListScreenState.Saver) { ListScreenState() }

/**
 * Stateful List destination: wires [ListViewModel] (its motion engine) and [AddViewModel] (the docked add bar, the FAB,
 * the Add sheet), the sticky notes, the list switcher, the New trip and Trip book sheets.
 */
@Composable
fun ListRoute(
    vm: ListViewModel,
    addVm: AddViewModel,
    onOpenAisles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Back from the background (maybe hours later): today's date on a fresh book, the idle trip's nudge (§6.1, §7.1).
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResumed() }
    val state by vm.state.collectAsStateWithLifecycle()
    val addState by addVm.state.collectAsStateWithLifecycle()
    val surprises by vm.surprisesState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val nav = LocalAppNav.current
    val toasts = remember { ToastHostState() }
    val screen = rememberListScreenState()
    var showLists by rememberSaveable { mutableStateOf(false) }
    val addFlows = rememberAddFlows(addVm)
    val tears = remember(resources) { TearToasts(resources, vm::undoDeletes) }
    val moves = remember(resources) { MoveToasts(resources) }
    SideEffect {
        vm.strings = ListStrings(
            addedToBook = { resources.getString(R.string.list_book_added_spoken, it) },
            peeledFromBook = { resources.getString(R.string.list_book_peeled_spoken, it) },
        )
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ListEvent.Deleted -> toasts.show(tears.toast(event))
                is ListEvent.NewBook -> {
                    // The nudge asked for exactly this; once the fresh book has started it has nothing left to offer.
                    toasts.dismiss(NUDGE_KIND)
                    toasts.show(
                        ToastMessage(
                            resources.getString(if (event.byAdd) R.string.list_toast_new_book_add else R.string.list_toast_new_book),
                            kind = BOOK_KIND,
                            actionLabel = resources.getString(R.string.list_undo),
                            onAction = event.undo,
                        ),
                    )
                }
                ListEvent.NewTrip -> {
                    // A New trip filed the book: the nudge has nothing left to offer, and an earlier automatic book's
                    // Undo no longer applies (§6.1, §6.5, §0.4 Q9).
                    toasts.dismiss(NUDGE_KIND)
                    toasts.dismiss(BOOK_KIND)
                    toasts.show(ToastMessage(resources.getString(R.string.list_toast_new_trip), kind = "trip"))
                }
                is ListEvent.Switched -> {
                    // Another list: the last one's nudge and new-book Undo are about a book no longer on screen (§6.4).
                    toasts.dismiss(NUDGE_KIND)
                    toasts.dismiss(BOOK_KIND)
                }
                is ListEvent.Nudge -> toasts.show(
                    ToastMessage(
                        resources.getString(R.string.list_toast_nudge),
                        kind = NUDGE_KIND,
                        actionLabel = resources.getString(R.string.list_toast_nudge_action),
                        // An offer, not an Undo: its chip wears no undo arrow (§4.15).
                        undo = false,
                        // Only while the list it was offered on still has a trip to close: never a sheet left waiting.
                        onAction = {
                            val now = vm.state.value
                            if (now.listId == event.listId && now.canStartNewTrip) screen.newTripOpen = true
                        },
                        minHoldMs = NUDGE_FLOOR,
                    ),
                )
                is ListEvent.ListDeleted -> toasts.show(ToastMessage(resources.getString(R.string.list_toast_list_deleted, event.title), kind = "list"))
                is ListEvent.Refreshed -> refreshedToast(resources, event)?.let { toasts.show(it) }
                ListEvent.RefreshWaits -> toasts.show(ToastMessage(resources.getString(R.string.list_toast_refresh_offline), kind = SYNC_KIND))
                is ListEvent.Message -> toasts.show(ToastMessage(event.text, kind = "save"))
                // Over the Edit sheet while it is up (the list's notes are under it), and over the list once it has
                // closed; a note still up when the sheet closes carries on over the list (ListScreenContent).
                is ListEvent.Moved -> (if (vm.state.value.editing != null) screen.sheetToasts else toasts).show(moves.toast(event))
            }
        }
    }
    // The one-time hold hint (§6.1, Cora 5 October): the first time her list shows items sorted into aisles, at a quiet
    // moment (no other note, nothing over the list, no check-off hop, Next card or celebration playing), through the
    // notes' own channel, so it takes their place, timing, reduced motion and polite announcement, and the eggs keep
    // clear of it as of any note. Shown once; a held row retires it (onOpenEdit).
    val hint = stringResource(R.string.list_hint_hold)
    val hintOk = stringResource(R.string.list_hint_hold_ok)
    LaunchedEffect(state.holdHint) {
        if (!state.holdHint) return@LaunchedEffect
        snapshotFlow {
            val p = vm.engine.current.value
            toasts.current == null && toasts.queue.isEmpty() && state.editing == null && !showLists && !screen.menuOpen &&
                !screen.newTripOpen && !screen.tripBookOpen && !addState.sheetOpen && p.nextCard == null && p.celebration == null &&
                p.flights.isEmpty()
        }.first { it }
        toasts.show(ToastMessage(hint, kind = HINT_KIND, actionLabel = hintOk, undo = false, onAction = {}))
        vm.holdHintShown()
    }
    AddEventsEffect(
        addFlows,
        toasts,
        // The add flows tell the eggs about the commit themselves (AddViewModel → SurpriseEngine.onCommitted, §19.2):
        // the list pops the rows, and turns the book when the add started a new one (§0.4 Q9, its add-side twin), so
        // eggs 3, 6, 9 and 17 fire once per commit.
        onCommitted = { commit ->
            screen.pop(commit)
            // The first new row comes into view, so its pop and highlighter flash are seen (§6.2).
            commit.added.firstOrNull()?.let { screen.reveal(it.id) }
            commit.newBook?.let { vm.addStartedNewBook(commit.listId, it) }
        },
        // "Move" on a one-item note (§6.2): the Edit sheet at "Move to aisle", on the list the item went to.
        onMove = { item -> vm.openMove(item.id) },
    )
    // The add flows send to the list on screen.
    LaunchedEffect(state.listId) {
        if (state.listId.isNotEmpty()) addVm.showList(state.listId)
    }

    val actions = remember(vm, addFlows, nav) {
        ListActions(
            onToggle = vm::toggle,
            onDelete = vm::delete,
            onOpenSection = vm::openSection,
            onFoldAll = vm::foldAll,
            // A held row: she knows now, so the hold hint goes if it is up (§6.1).
            onOpenEdit = { id ->
                vm.openEdit(id)
                toasts.dismiss(HINT_KIND)
            },
            onOpenMove = vm::openMove,
            onStartList = addFlows::focusField,
            onNewTrip = vm::newTrip,
            onRefresh = vm::refresh,
            onOpenAisles = onOpenAisles,
            onOpenPantry = nav.openPantry,
            onOpenAlbum = nav.openAlbum,
            onOpenBackPocket = { nav.openAlbumAt(earth.cora.picnic.ui.nav.AlbumTarget.BackPocket) },
            onOpenSettings = onOpenSettings,
            onOpenLists = { showLists = true },
            onCloseEdit = vm::closeEdit,
            onSaveEdit = vm::saveEdit,
            onMoveToAisle = vm::moveToAisle,
            onSetIcon = vm::setIcon,
            onRedrawIcon = vm::redrawIcon,
            onCloseLists = { showLists = false },
            onSwitchList = { id ->
                vm.switchList(id)
                showLists = false
            },
            onNewList = { title ->
                vm.newList(title)
                showLists = false
            },
            onRenameList = vm::renameList,
            onDeleteList = vm::deleteList,
            onSunTap = vm::onSunTap,
            onMoonTap = vm::onMoonTap,
            onStampTap = vm::onStampTap,
            onLcdHold = vm::onLcdHold,
            onBasketHold = vm::onBasketHold,
            onCue = vm::cue,
        )
    }
    val tape = AddRules.addBarTape(surprises)
    val sheetUp = state.editing != null || showLists || screen.newTripOpen || screen.tripBookOpen
    // The list's sheets and WP7's Add sheet beside it share one set of modal layers: the list hands the keyboard to
    // whichever is up (§15).
    ModalLayersHost {
        ListScreen(
            state = state,
            actions = actions,
            engine = vm.engine,
            toasts = toasts,
            showLists = showLists,
            screen = screen,
            dockHeight = addFlows.dockHeight,
            collapse = addFlows.collapse,
            dock = { bar ->
                AddDock(
                    addFlows, addState, tape = tape, otherSheetOpen = sheetUp,
                    // Egg 16: the umbrella stands at the bar's left end; the washi moves along after the doodle.
                    tapeAfterDoodle = surprises.umbrella,
                    modifier = bar,
                )
            },
            surprises = vm.surpriseEngine,
            feedback = vm.feedback,
            addSheetUp = addState.sheetOpen,
        )
        // The sheet's state waits in AddViewModel while Settings or My pantry is open: Back returns to the same sheet.
        NoMaterialPress { AddSheetHost(addFlows, addState, onOpenSettings = onOpenSettings, onEditPantry = nav.onEditPantry) }
    }
}

/** The nudge's toast kind. */
internal const val NUDGE_KIND: String = "nudge"

/** The automatic new book's toast kind (its Undo, §0.4 Q9). */
internal const val BOOK_KIND: String = "book"

/** The nudge's reading floor (§6.1, §9: it carries an action): 6 s. */
private const val NUDGE_FLOOR: Long = 6000

/** The refresh notes' toast kind. */
internal const val SYNC_KIND: String = "sync"

/** The one-time hold hint's toast kind (§6.1). */
internal const val HINT_KIND: String = "hold-hint"

/**
 * A move's sticky notes (Cora, 5 October; §4.15, §6.3): "Toothpaste goes in Produce from now on" with Undo, which puts
 * the old aisle and the previous correction back; the first move to an aisle ever says a little more, once. Each item's
 * notes are a kind of their own: moving it again during the hold merges into one note that says where it goes now (the
 * fuller words stay while they apply) and whose Undo takes all those moves back, newest first; another item's note waits
 * its turn with its own Undo. The list and the Edit sheet share one instance, so a note handed from the sheet to the
 * list still merges.
 */
internal class MoveToasts(private val resources: Resources) {
    private val moves = HashMap<Long, List<ListEvent.Moved>>()

    fun toast(e: ListEvent.Moved): ToastMessage {
        val note = note(listOf(e), null)
        moves[note.id] = listOf(e)
        return note
    }

    private fun note(all: List<ListEvent.Moved>, id: Long?): ToastMessage {
        val last = all.last()
        val base = ToastMessage(
            text = moveText(resources, last, first = all.any { it.firstNote }),
            kind = kindOf(last.itemId),
            actionLabel = resources.getString(R.string.list_undo),
            onAction = { all.asReversed().forEach { it.undo() } },
            merge = ::merge,
        )
        return if (id == null) base else base.copy(id = id)
    }

    private fun merge(current: ToastMessage, incoming: ToastMessage): ToastMessage {
        val all = moves.remove(current.id).orEmpty() + moves.remove(incoming.id).orEmpty()
        if (all.isEmpty()) return incoming.copy(id = current.id)
        moves[current.id] = all
        return note(all, current.id)
    }

    companion object {
        /** Every move note's kind starts with this (the sheet hands these over to the list). */
        const val KIND_PREFIX: String = "move:"

        fun kindOf(itemId: String): String = KIND_PREFIX + itemId
    }
}

/**
 * A move's words (§16): "Toothpaste goes in Produce from now on"; [first] (the first move to an aisle ever) adds
 * "Smart sorting puts other kinds of toothpaste there too."; a move to Everything else is "Toothpaste moved to Everything
 * else" (nothing about from now on: no aisle is learned for it).
 */
internal fun moveText(resources: Resources, e: ListEvent.Moved, first: Boolean): String {
    val aisle = e.aisleName ?: return resources.getString(R.string.list_toast_moved_unsorted, e.itemName)
    return if (first) {
        resources.getString(R.string.list_toast_moved_first, e.itemName, aisle, kindWord(e.itemName))
    } else {
        resources.getString(R.string.list_toast_moved, e.itemName, aisle)
    }
}

/**
 * The item's kind inside a sentence: the name before any variety after a comma ("Eggs, large" → "eggs"), its first letter
 * in lower case unless its first word is written with capitals inside it ("McIntosh apples", "PC burgers" stay).
 */
internal fun kindWord(name: String): String {
    val kind = name.substringBefore(',').trim().ifEmpty { name.trim() }
    val firstWord = kind.substringBefore(' ')
    return if (firstWord.drop(1).any { it.isUpperCase() }) kind else kind.replaceFirstChar { it.lowercase(java.util.Locale.ROOT) }
}

/**
 * The refresh's sticky note (§6.1), or null when nothing changed. One refresh says it all on one note: the stickers it
 * drew arrive after the sorting (their event carries both counts), and fold into the sorting's note while that is still
 * up ("Sorted 2 items · 1 new sticker drawn", its hold starting again); once that note has gone, a note of their own
 * says only what is new ("1 new sticker drawn").
 */
internal fun refreshedToast(resources: Resources, e: ListEvent.Refreshed): ToastMessage? {
    val full = refreshedText(resources, e) ?: return null
    if (e.drawn == 0 || e.sorted == 0) return ToastMessage(full, kind = SYNC_KIND)
    val alone = refreshedText(resources, e.copy(sorted = 0, left = 0)) ?: return null
    return ToastMessage(alone, kind = SYNC_KIND, merge = { current, _ -> ToastMessage(full, kind = SYNC_KIND, id = current.id) })
}

/** "Sorted 2 items · 1 new sticker drawn" (§6.1), or null when nothing changed. */
internal fun refreshedText(resources: Resources, e: ListEvent.Refreshed): String? {
    val parts = buildList {
        if (e.sorted > 0) add(resources.getQuantityString(R.plurals.list_toast_sorted, e.sorted, e.sorted))
        if (e.drawn > 0) add(resources.getQuantityString(R.plurals.list_toast_drawn, e.drawn, e.drawn))
        if (e.sorted > 0 && e.left > 0) add(resources.getQuantityString(R.plurals.list_toast_left, e.left, e.left))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * The tear-off's sticky notes (§8.6, §4.15): "Oat cereal torn out" with Undo; a second tear-off during its hold merges
 * into "2 items torn out", whose Undo restores both.
 */
internal class TearToasts(private val resources: Resources, private val undoAll: (List<ListEvent.Deleted>) -> Unit) {
    private val torn = HashMap<Long, List<ListEvent.Deleted>>()

    fun toast(e: ListEvent.Deleted): ToastMessage {
        val note = note(listOf(e), null)
        torn[note.id] = listOf(e)
        return note
    }

    private fun note(all: List<ListEvent.Deleted>, id: Long?): ToastMessage {
        val text = if (all.size == 1) {
            resources.getString(R.string.list_toast_torn_one, all.single().item.name)
        } else {
            resources.getQuantityString(R.plurals.list_toast_torn_many, all.size, all.size)
        }
        val base = ToastMessage(
            text = text,
            kind = TEAR_KIND,
            actionLabel = resources.getString(R.string.list_undo),
            // All in one write, newest first, so each goes back at the index it had when it was torn out.
            onAction = { undoAll(all) },
            merge = ::merge,
        )
        return if (id == null) base else base.copy(id = id)
    }

    private fun merge(current: ToastMessage, incoming: ToastMessage): ToastMessage {
        val all = torn.remove(current.id).orEmpty() + torn.remove(incoming.id).orEmpty()
        if (all.isEmpty()) return incoming.copy(id = current.id)
        torn[current.id] = all
        return note(all, current.id)
    }

    companion object {
        const val TEAR_KIND: String = "tear"
    }
}

/**
 * The stateless List screen. [engine] plays the chains ([MotionEngineHost] runs it here); [dock] is the docked add bar
 * (the app: WP7's AddDock; previews and goldens: the bar at rest, wearing the tape of the season the book shows),
 * [dockHeight] its measured height (the toasts sit 20 dp above it, the list's bottom padding is it + 26 dp) and
 * [collapse] its fold rules (programmatic scrolls are ignored). [surprises] is WP9's engine: the screen is wrapped in its
 * [EggListHost], which mounts the egg overlays in MotionOverlay's `eggs` slot and hands the pieces the eggs' state.
 */
@Composable
fun ListScreen(
    state: ListUiState,
    actions: ListActions,
    modifier: Modifier = Modifier,
    engine: MotionChoreographer = remember { MotionChoreographer() },
    toasts: ToastHostState = remember { ToastHostState() },
    showLists: Boolean = false,
    screen: ListScreenState = rememberListScreenState(),
    listState: LazyListState? = null,
    dockHeight: Dp = 84.dp,
    collapse: AddBarCollapseState? = null,
    /** The docked add bar; [bar] goes on it (the eggs' `addbar` anchor, its words' keep-out, its measured height). */
    dock: @Composable BoxScope.(bar: Modifier) -> Unit = { bar ->
        // The bar wears the eggs' tape (the season's, the rain's) when the eggs run here, else the list's season (the
        // same on an empty list, which has no book to show it, §4.14).
        val eggs = LocalListEggs.current
        StaticDock(
            tape = AddRules.addBarTape(if (eggs.enabled) eggs else SurpriseState(season = state.season)),
            modifier = bar,
            tapeAfterDoodle = eggs.umbrella,
        )
    },
    /** WP9's engine: [EggListHost] mounts its overlays, shake, keyboard observer and scene scrolls on this list (§19.2). */
    surprises: SurpriseEngine = NoOpSurpriseEngine,
    feedback: Feedback = NoOpFeedback,
    /** Test hook: the keyboard's visibility for egg 17 (null: the phone's own insets). */
    imeVisible: Boolean? = null,
    talkBackOverride: Boolean? = null,
    /**
     * The Surprise lab's practice list: the nap cat's basket and the frozen aisle scroll into view as their scenes start
     * (EggListHost). Her own list never scrolls by itself for them (§12.1).
     */
    sceneScrolls: Boolean = false,
    /** WP7's Add sheet is up over the list (it is hosted beside the screen): the list holds no keyboard focus meanwhile. */
    addSheetUp: Boolean = false,
) {
    MotionEngineHost(engine, nextStopCard = state.nextStopCard, celebration = state.celebration, eggs = state.eggs, talkBackOverride = talkBackOverride)
    val p = engine.current.value
    // A commit's pops play while their rows are on screen; the ones whose rows were not (a folded aisle, a row scrolled
    // away) lapse once the last would have finished, on the frame clock (§6.2: a pop belongs to the commit's moment).
    LaunchedEffect(screen.popGeneration) {
        val gen = screen.popGeneration
        if (gen == 0) return@LaunchedEffect
        val window = screen.pops.values.filter { it.generation == gen }.maxOfOrNull { it.window } ?: 0L
        withContext(FixedMotionScale) { holdOnFrameClock(window) }
        screen.expirePops(gen)
    }
    // SYNC holds on the LCD for the LCD-word floor after each refresh, on the frame clock (§6.1, §9).
    LaunchedEffect(screen.sunSpin) {
        if (!screen.syncing) return@LaunchedEffect
        withContext(FixedMotionScale) { holdOnFrameClock(PicnicMotion.floorLcdWord) }
        screen.syncing = false
    }
    // Each list keeps its own scroll: another list opens at its top, never at the old one's place (§6.4, item 164).
    val lazyState = listState ?: key(state.listId) { rememberLazyListState() }
    // A different list, or this one emptied (the trip cleared, its last item torn out): the scroll that folded the app
    // bar, the book and the add bar went with it, so they come back (§4.2, §4.21, §6.1; items 116, 164).
    val emptied = state.total == 0 && !state.loading
    val seen = remember { arrayOfNulls<Pair<String, Boolean>>(1) }
    LaunchedEffect(state.listId, emptied) {
        val before = seen[0]
        seen[0] = state.listId to emptied
        if (before != null && (before.first != state.listId || emptied)) {
            screen.scrolledDown = false
            collapse?.atTop(true)
        }
    }
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density) > 0
    val shortWindow = LocalConfiguration.current.screenHeightDp < SHORT_WINDOW_DP
    // The lazy items (one plan for the list and for the eggs' scrolls to a section, §12.2 eggs 3, 5 and 8).
    val openId = p.openSection(state.openSectionId)
    val planned = remember { arrayOfNulls<Planned>(1) }
    // The finished tray folds into its strip as one sheet (§8.4 L + 1100): the strip takes its band's place and its rows
    // and cap roll up under it over the 240 ms `layout`, everything below moving with them in the same frames. Under
    // reduced motion, or with its band pinned over its own rows (the list then keeps its place on the strip, item 87),
    // it is the one swap.
    val reduced = LocalReducedMotion.current
    val foldId = state.sections.firstOrNull { it.id != openId && p.aisles[it.id]?.foldT0 != null }?.id
    val foldT0 = foldId?.let { p.aisles[it]?.foldT0 }
    val folding = remember(foldId, foldT0) { foldId != null && !reduced && foldsInView(planned[0], state.listId, foldId, lazyState) }
    // Her own fold or open (§8.5) glides the same way: the tray she leaves rolls up into its strip (unless its band is
    // pinned over its rows: item 87's one swap) and the one she opens unfolds, from the first frame the list shows them.
    val shownOpen = remember { arrayOfNulls<Pair<String, String?>>(1) }
    val wasOpen = shownOpen[0]?.takeIf { it.first == state.listId }
    // Her last glide: a tap while it still moves its trays carries them on from where they are (TapGlide.carried).
    val lastGlide = remember { arrayOfNulls<TapGlide>(1) }
    val glide = remember(state.listId, openId) {
        val tap = screen.foldTap
        val hers = tap != null && wasOpen != null && openId != wasOpen.second &&
            if (tap.open) openId == tap.sectionId else wasOpen.second == tap.sectionId && openId == null
        if (hers) screen.foldTap = null
        if (!hers || reduced) {
            null
        } else {
            val before = lastGlide[0]
            val moving = if (before == null) emptySet() else Snapshot.withoutReadObservation { before.moving }
            val leaving = wasOpen.second?.takeIf { id -> state.sections.any { it.id == id && it.rows.isNotEmpty() } && foldsInView(planned[0], state.listId, id, lazyState) }
            val opening = openId?.takeIf { id -> state.sections.any { it.id == id && it.rows.isNotEmpty() } }
            // A tray her last glide is still rolling up goes on rolling up (unless she opens it again).
            val folding = buildSet {
                if (leaving != null) add(leaving)
                before?.folding?.forEach { id -> if (id in moving && id != opening && state.sections.any { it.id == id }) add(id) }
            }
            if (folding.isEmpty() && opening == null) null else TapGlide(folding = folding, opening = opening, carried = moving)
        }
    }
    if (glide != null) lastGlide[0] = glide
    shownOpen[0] = state.listId to openId
    LaunchedEffect(glide) { glide?.play() }
    val tapGlide = glide?.takeIf { !it.done }
    val autoFold = foldId.takeIf { folding }
    val tapFolds = tapGlide?.takeIf { !it.rolledUp }?.folding
    val folds = remember(autoFold, tapFolds) { if (autoFold == null) tapFolds.orEmpty() else tapFolds.orEmpty() + autoFold }
    val entries = remember(state.sections, openId, folds) { planEntries(state.sections, openId, folds) }
    // A tray folding under its pinned plate keeps the list's place on its strip (§8.4 L + 1100, item 87), in the same
    // frame as the swap: no frame shows an empty viewport or a jump to the list's end.
    val anchorGen = remember { IntArray(1) }
    if (keepPlace(planned[0], state.listId, entries, lazyState)) {
        anchorGen[0]++
        screen.layoutSnaps++
    }
    planned[0] = Planned(state.listId, entries, lazyState)
    LaunchedEffect(anchorGen[0]) {
        if (anchorGen[0] == 0) return@LaunchedEffect
        try {
            // The swap's frame and the next: the strips below take their places, then sliding resumes.
            withFrameMillis { }
            withFrameMillis { }
        } finally {
            screen.layoutSnaps--
        }
    }
    val latestEntries by rememberUpdatedState(entries)
    val indexOfSection = remember { { id: String -> latestEntries.indexOfFirst { it.sectionId == id && (it is Entry.Strip || it is Entry.Header) } } }
    val host: @Composable EggListScope.() -> Unit = {
        CompositionLocalProvider(LocalListEggs provides eggs, LocalListSurprises provides surprises) {
            // No Material default survives (§0.2): anything on the list that falls back on LocalIndication (inside
            // PicnicTheme that is Material's ripple) shows no ripple here; the kit draws its own pressed states.
            // Its sheets and menu hold the keyboard while up (§15); ListRoute's set when it hosts the screen.
            NoMaterialPress {
                ModalLayersHost {
                    ListScreenContent(
                        state, actions, modifier, engine, p, toasts, showLists, screen, lazyState, entries, dockHeight, collapse, dock,
                        // Egg 18: while a printed receipt waits for its tap, a scroll leaves the book open under it (§13.2).
                        bookCollapsed = (screen.scrolledDown && !receiptWaits) || ime || shortWindow,
                        addSheetUp = addSheetUp,
                        glide = tapGlide,
                    )
                }
            }
        }
    }
    if (imeVisible != null) {
        EggListHost(
            surprises, state.listId, engine.anchors, lazyState, indexOfSection, toasts, engine, feedback,
            onOpenAlbum = actions.onOpenBackPocket, imeVisible = imeVisible, sceneScrolls = sceneScrolls, content = host,
        )
    } else {
        EggListHost(
            surprises, state.listId, engine.anchors, lazyState, indexOfSection, toasts, engine, feedback,
            onOpenAlbum = actions.onOpenBackPocket, sceneScrolls = sceneScrolls, content = host,
        )
    }
}

/** The docked add bar's anchors: the eggs' `addbar` (the umbrella, the ants, the scenes' room) and its words' keep-out. */
internal fun dockAnchors(anchors: AnchorRegistry): Modifier =
    with(anchors) { Modifier.anchor(AnchorRegistry.ADD_BAR).anchor(EggAnchors.TEXT_PREFIX + "addbar") }

/** The screen inside its [EggListHost]: the top stack, the list, the dock, the overlay, the sheets, the celebration. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EggListScope.ListScreenContent(
    state: ListUiState,
    actions: ListActions,
    modifier: Modifier,
    engine: MotionChoreographer,
    p: ListPresentation,
    toasts: ToastHostState,
    showLists: Boolean,
    screen: ListScreenState,
    lazyState: LazyListState,
    entries: List<Entry>,
    dockHeight: Dp,
    collapse: AddBarCollapseState?,
    dock: @Composable BoxScope.(bar: Modifier) -> Unit,
    bookCollapsed: Boolean,
    addSheetUp: Boolean,
    glide: TapGlide?,
) {
    // The New trip sheet is only ever up over a trip it can file: once the list has none (a New trip from the menu, the
    // last sticker peeled), a request still pending is dropped, so the sheet never opens unasked later and the add bar
    // is free to fold (§6.5, item 116).
    LaunchedEffect(state.canStartNewTrip, state.loading) {
        if (!state.loading && !state.canStartNewTrip) screen.newTripOpen = false
    }
    // A sheet or the menu over the list (§15 Focus): the keyboard stays in it, and the control that opened it gets focus
    // back when it closes.
    val covered = screen.menuOpen || state.editing != null || showLists || (screen.newTripOpen && state.canStartNewTrip) ||
        screen.tripBookOpen || addSheetUp
    // Back with the Next card up skips it, as a tap does (§15: Back closes overlays), instead of leaving the list; a sheet
    // over it keeps Back for itself, and the celebration has its own.
    BackHandler(enabled = p.nextCard?.let { it.exitT0 == null } == true && p.celebration == null && !covered) { engine.skipNextCard() }
    Box(
        modifier
            .fillMaxSize()
            // Escape closes the Next card or the celebration as a tap or Back does (§15: Escape / Back closes sheets and
            // overlays); neither ever takes the keyboard's focus, so it reaches them through the list's.
            .onKeyEvent { e ->
                if (e.key != Key.Escape) return@onKeyEvent false
                val close: (() -> Unit)? = when {
                    p.celebration != null -> engine::closeCelebration
                    p.nextCard != null -> engine::skipNextCard
                    else -> null
                }
                if (close != null && e.type == KeyEventType.KeyUp) close()
                close != null
            },
    ) {
        Box(Modifier.fillMaxSize().underModal(covered, restore = screen::restoreKeyFocus)) {
            PicnicBackground(Modifier.fillMaxSize()) {
                // Egg 1: the ground being left fades out over the new one, beneath everything else.
                Wash()
                Column(Modifier.fillMaxSize()) {
                    TopStack(
                        state = state,
                        presentation = p,
                        engine = engine,
                        ui = TopStackUi(
                            // Egg 18: the binder top stays open while a printed receipt hangs from its LCD (§13.2).
                            barCollapsed = screen.scrolledDown && !receiptWaits,
                            bookCollapsed = bookCollapsed,
                            syncing = screen.syncing,
                            sunSpin = screen.sunSpin,
                        ),
                        actions = actions,
                        onMore = { screen.menuOpen = true },
                        onMoreBounds = { screen.moreBounds = it },
                        onOpenBook = { slot ->
                            screen.tripBookSlot = slot
                            screen.tripBookOpen = true
                        },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.loading -> LoadingList()
                            state.total == 0 -> EmptyList(state, actions, dockHeight)
                            // What the keyboard focuses lands clear of the pinned plate and the dock (§15).
                            else -> CompositionLocalProvider(LocalBringIntoViewSpec provides keyboardView(lazyState, entries, dockHeight)) {
                                AisleList(
                                    state, p, engine, actions, screen, lazyState, entries, dockHeight, collapse,
                                    // Egg 16: the umbrella's room is only at the list's end (listBottomPadding), so the last
                                    // strip can scroll clear of its canopy; the viewport itself stays as it is (§12.2).
                                    topExtra = listTopExtra, bottomPadding = listBottomPadding(dockHeight),
                                    glide = glide,
                                )
                            }
                        }
                    }
                }
            }
            // The dock (the bar, or the FAB it folds into): the list hands the keyboard on to it after its last item (§15).
            Box(Modifier.matchParentSize().focusRequester(screen.dockFocus)) {
                // The eggs follow the docked bar (egg 16's umbrella stands on it, and steps off while it is folded into the
                // FAB).
                dock(dockAnchors(engine.anchors).eggAddBar(folded = collapse?.collapsed == true))
            }
            // The toasts sit 20 dp above the bar's top (above the umbrella's canopy while it rains); the eggs' overlays ride
            // in the overlay's `eggs` slot (§12, §19.2).
            MotionOverlay(engine, p, toasts, bottomInset = overlayInset(dockHeight), eggs = overlays)
        }
        PeekLayer(state, screen, dockHeight)
        if (screen.menuOpen) ListMenu(state, actions, screen)
        // The Edit sheet and the list switcher slide away as she saw them (§4.16), then leave.
        SheetFor(state.editing) { editing, open ->
            EditItemSheet(
                state = editing,
                onSave = actions.onSaveEdit,
                onClose = actions.onCloseEdit,
                onMoveToAisle = { aisleId -> actions.onMoveToAisle(editing.item.id, aisleId) },
                onSetIcon = { key -> actions.onSetIcon(editing.item.id, key) },
                onRedraw = { actions.onRedrawIcon(editing.item.id) },
                onDelete = { actions.onDelete(editing.item.id) },
                toasts = screen.sheetToasts,
                visible = open,
            )
        }
        // The Edit sheet closed: a move's note still up (or waiting) carries on over the list with its full hold, so its
        // Undo is never lost with the sheet (§4.15); the sheet's other notes go with it.
        val sheetOpen = state.editing != null
        LaunchedEffect(sheetOpen) {
            if (!sheetOpen) screen.sheetToasts.handOver(toasts) { it.kind.startsWith(MoveToasts.KIND_PREFIX) }
        }
        SheetFor(state.lists.takeIf { showLists }) { lists, open ->
            ListSwitcherSheet(
                lists = lists,
                onDismiss = actions.onCloseLists,
                onSwitch = actions.onSwitchList,
                onNew = actions.onNewList,
                onRename = actions.onRenameList,
                onDelete = actions.onDeleteList,
                visible = open,
            )
        }
        NewTripSheet(
            visible = screen.newTripOpen && state.canStartNewTrip,
            book = state.book,
            onClear = {
                screen.newTripOpen = false
                actions.onNewTrip(NewTripMode.CLEAR_GOTTEN)
            },
            onKeep = {
                screen.newTripOpen = false
                actions.onNewTrip(NewTripMode.UNCHECK_ALL)
            },
            onDismiss = { screen.newTripOpen = false },
        )
        TripBookSheet(
            book = if (screen.tripBookOpen) state.tripBook else null,
            dateLabel = state.book?.dateLabel.orEmpty(),
            onClose = { screen.tripBookOpen = false },
            initialSlot = screen.tripBookSlot,
        )
        CelebrationHost(engine, p)
    }
}

/** Windows shorter than this keep the book collapsed (§7.4). */
private const val SHORT_WINDOW_DP = 600

/** The add bar at rest, docked at the bottom (previews and goldens; the app docks WP7's AddDock). */
@Composable
fun BoxScope.StaticDock(tape: WashiPattern = WashiPattern.SakuraStripe, modifier: Modifier = Modifier, tapeAfterDoodle: Boolean = false) {
    val c = PicnicTheme.colors
    Column(modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        AddBar(AddBarUi(), onTextChange = {}, onSend = {}, onSnap = {}, onChoosePhoto = {}, onSpeak = {}, tape = tape, tapeAfterDoodle = tapeAfterDoodle)
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.ime.union(WindowInsets.navigationBars)).background(c.pad))
    }
}

/** The loading state (§6.1): skeleton strips with spots. */
@Composable
private fun LoadingList() {
    Column(
        Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 11.dp).testTag(ListTags.LOADING),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        repeat(5) { SkeletonStrip() }
    }
}

/**
 * The empty list (§4.21): the fresh sticker sheet, the copy, "Start my list"; no book. It scrolls to clear the docked
 * add bar ([dockHeight], which grows with large text and the navigation bar), so the button is never left under it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmptyList(state: ListUiState, actions: ListActions, dockHeight: Dp) {
    // Its button, reached with Tab under a large font, scrolls up clear of the dock over the page's foot (§15).
    val dockPx by rememberUpdatedState(with(LocalDensity.current) { dockHeight.toPx() })
    val clear = remember { ClearOfCovers(top = { 0f }, bottom = { dockPx }) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides clear) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = dockHeight)
                .testTag(ListTags.EMPTY),
            contentAlignment = Alignment.TopCenter,
        ) {
            EmptyState(
                title = stringResource(R.string.list_empty_title),
                body = stringResource(R.string.list_empty_body, state.title),
                action = { PicnicButton(stringResource(R.string.list_empty_action), onClick = actions.onStartList, leadingStar = true) },
            )
        }
    }
}

/**
 * Where the list brings what the keyboard focuses (§15): wholly clear of the plate pinned at its top and of the dock
 * over its bottom, so a row reached with Tab or the D-pad is never hidden under either.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun keyboardView(list: LazyListState, entries: List<Entry>, dockHeight: Dp): BringIntoViewSpec {
    val latest by rememberUpdatedState(entries)
    val dockPx by rememberUpdatedState(with(LocalDensity.current) { dockHeight.toPx() })
    return remember(list) { ClearOfCovers(top = { pinnedPlateBottom(list, latest) }, bottom = { dockPx }) }
}

/** The list's keyboard stops in its order: each strip, the open tray's chevron, each of its rows (index, requester). */
private fun ListScreenState.focusTargets(entries: List<Entry>): List<Pair<Int, FocusRequester>> =
    entries.mapIndexedNotNull { i, e ->
        when (e) {
            is Entry.Strip -> i to focusOf(e.s.id).second
            is Entry.Header -> i to focusOf(e.s.id).first
            // A folding tray's rows are going: the keyboard goes on to its strip.
            is Entry.RowItem -> if (e.folding) null else i to rowFocus(e.row.id)
            else -> null
        }
    }

/** Notes [key] (an item of section [sectionId]) as where the keyboard's focus is when it takes focus. */
private fun Modifier.keyFocusIn(screen: ListScreenState, key: String, sectionId: String): Modifier =
    onFocusChanged { if (it.hasFocus) screen.keyFocus = key to sectionId }

/** The pinned plate's bottom in the list's viewport (px from its start), or 0 while no plate is pinned (§4.10). */
private fun pinnedPlateBottom(list: LazyListState, entries: List<Entry>): Float {
    val info = list.layoutInfo
    val header = info.visibleItemsInfo.firstOrNull { entries.getOrNull(it.index) is Entry.Header } ?: return 0f
    val pinned = list.firstVisibleItemIndex > header.index ||
        (list.firstVisibleItemIndex == header.index && list.firstVisibleItemScrollOffset > 0)
    return if (pinned) (header.offset + header.size - info.viewportStartOffset).toFloat() else 0f
}

/** One lazy item of the list. */
private sealed interface Entry {
    val key: String
    val sectionId: String?

    data class Strip(val s: SectionUi) : Entry {
        override val key get() = "strip:${s.id}"
        override val sectionId get() = s.id
    }

    data class Header(val s: SectionUi) : Entry {
        override val key get() = "header:${s.id}"
        override val sectionId get() = s.id
    }

    /** [folding]: a row of the finished tray rolling up into its strip (§8.4 L + 1100), no longer a row to visit. */
    data class RowItem(val s: SectionUi, val row: ItemRowUi, val first: Boolean, val last: Boolean, val folding: Boolean = false) : Entry {
        override val key get() = row.id
        override val sectionId get() = s.id
    }

    data class Cap(val s: SectionUi, val folding: Boolean = false) : Entry {
        override val key get() = "cap:${s.id}"
        override val sectionId get() = s.id
    }

    data class End(val s: SectionUi) : Entry {
        override val key get() = "end:${s.id}"
        override val sectionId get() = s.id
    }

    data class Gap(val s: SectionUi) : Entry {
        override val key get() = "gap:${s.id}"
        override val sectionId get() = s.id
    }

    data object Note : Entry {
        override val key get() = "end-note"
        override val sectionId get() = null
    }
}

/** The list's last plan: its entries for [listId] on [state]. */
private class Planned(val listId: String, val entries: List<Entry>, val state: LazyListState)

/**
 * The list keeps its place by the first visible item's key. When a tray folds while its rows are under the pinned plate
 * (the first visible item is one of its rows, its cap or its band scrolled under), that key is gone and the list would
 * keep only the index, which now points far down the list (the strips after it, or the list's end). Instead the strip
 * that replaced the tray is put at the top of the viewport, in the same remeasure as the swap
 * ([LazyListState.requestScrollToItem]); the next stop's scroll then continues from there (§8.4, §10). Returns whether
 * it re-anchored.
 */
private fun keepPlace(before: Planned?, listId: String, entries: List<Entry>, list: LazyListState): Boolean {
    if (before == null || before.listId != listId || before.state !== list || before.entries === entries) return false
    val (index, offset) = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
        list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
    }
    val first = before.entries.getOrNull(index) ?: return false
    if (first !is Entry.Header && first !is Entry.RowItem && first !is Entry.Cap && first !is Entry.End) return false
    if (entries.any { it.key == first.key }) return false
    val id = first.sectionId ?: return false
    val strip = entries.indexOfFirst { it is Entry.Strip && it.sectionId == id }
    if (strip < 0) return false
    // A band at the very top whose strip takes its very index needs nothing (another tray opening above moves it).
    if (first is Entry.Header && offset == 0 && strip == index) return false
    list.requestScrollToItem(strip, 0)
    return true
}

/**
 * Whether the tray [sectionId], open in the last plan [before], can fold as one sheet where it is: it was open, and its
 * band was not pinned over its own rows (the list's first visible item is not one of its rows, its cap or its end, nor
 * its band scrolled part way under the top).
 */
private fun foldsInView(before: Planned?, listId: String, sectionId: String, list: LazyListState): Boolean {
    if (before == null || before.listId != listId || before.state !== list) return false
    if (before.entries.none { it is Entry.Header && it.sectionId == sectionId }) return false
    val (index, offset) = Snapshot.withoutReadObservation { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
    val first = before.entries.getOrNull(index) ?: return true
    if (first.sectionId != sectionId) return true
    return first is Entry.Header && offset == 0
}

/**
 * The lazy items for [sections] with [openId] open: strips, or the tray's band, rows, cap and end marker; gaps; the end
 * note. [folding]: a tray folding into its strip (the finished one, §8.4 L + 1100, or the ones she leaves, §8.5) is its
 * strip with its rows and cap still under it, rolling up (the rows keep their keys, so the list keeps them in place while
 * they go).
 */
private fun planEntries(sections: List<SectionUi>, openId: String?, folding: Set<String> = emptySet()): List<Entry> = buildList {
    for (s in sections) {
        if (s.id == openId && s.rows.isNotEmpty()) {
            add(Entry.Header(s))
            s.rows.forEachIndexed { i, r -> add(Entry.RowItem(s, r, i == 0, i == s.rows.lastIndex)) }
            add(Entry.Cap(s))
            add(Entry.End(s))
        } else if (s.id in folding && s.rows.isNotEmpty()) {
            add(Entry.Strip(s))
            s.rows.forEachIndexed { i, r -> add(Entry.RowItem(s, r, i == 0, i == s.rows.lastIndex, folding = true)) }
            add(Entry.Cap(s, folding = true))
        } else {
            add(Entry.Strip(s))
        }
        add(Entry.Gap(s))
    }
    add(Entry.Note)
}

/** Whether this is a piece of a tray folding into its strip (see [planEntries]). */
private val Entry.isFolding: Boolean get() = (this is Entry.RowItem && folding) || (this is Entry.Cap && folding)

/** The list viewport (§6.1 item 2): 11 dp top padding, 8 dp sides, the add bar + 26 dp at the bottom. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AisleList(
    state: ListUiState,
    p: ListPresentation,
    engine: MotionChoreographer,
    actions: ListActions,
    screen: ListScreenState,
    listState: LazyListState,
    entries: List<Entry>,
    dockHeight: Dp,
    collapse: AddBarCollapseState?,
    /**
     * Egg 18: while a printed receipt hangs down into the list's room, the viewport starts this much lower, under the
     * paper (EggListScope.listTopExtra), so no row can scroll beneath it.
     */
    topExtra: Dp = 0.dp,
    /** The add bar + 26 dp, + the umbrella's room while it rains (EggListScope.listBottomPadding). */
    bottomPadding: Dp = dockHeight + 26.dp,
    /** Her own fold or open gliding now (§8.5), or null. */
    glide: TapGlide? = null,
) {
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val clock = engine.clock
    val anchors = engine.anchors
    val openId = p.openSection(state.openSectionId)
    // The current aisle wears the lemon ring, the bee and the ladybug (§4.10, D2). While a chain holds a finished aisle
    // open (the engine's override), it keeps them until it folds; a finished aisle she reopens herself is not current.
    val currentId = if (openId != null && openId != state.openSectionId) openId else state.nowSectionId
    // The next stop opening behind the Next card (§8.5, §10) unfolds by height, as one sheet: its rows and cap grow from
    // nothing to their height over the fold's 240 ms while everything below moves with them in the same frames, so no
    // strip ever slides under the new tray. Timed on the engine's clock from the frame it opened (the hand-off's chain
    // runs it).
    val openedAt = remember { HashMap<String, Long>() }
    val lastOpen = remember { arrayOfNulls<String>(1) }
    if (openId != lastOpen[0]) {
        if (openId != null) openedAt[openId] = Snapshot.withoutReadObservation { clock.now }
        lastOpen[0] = openId
    }
    val handOff = p.scroll?.takeIf { it.itemId == null && it.sectionId == openId }
    val unfoldT0 = if (reduced || handOff == null) null else openedAt[handOff.sectionId]?.takeIf { it >= handOff.t0 - UNFOLD_SLACK_MS }
    // The tray she opened herself unfolds the same way, on its glide's clock, from where its strip ended (§8.5).
    val tapOpen = glide?.takeIf { it.opening != null && it.opening == openId }
    val unfoldId = if (unfoldT0 != null || tapOpen != null) openId else null
    // The bands', strips' and tray slices' heights as last laid out: an unfold reads its band's in the same pass, a fold
    // its band's after it, and a sheet's first frame the heights of the slices measured after the one it lays out.
    val bands = remember { HashMap<String, Int>() }
    val strips = remember { HashMap<String, Int>() }
    val slices = remember { HashMap<String, Int>() }
    // Where each tray's sheet last laid it out, and where her glide found the trays her last one was still moving when
    // she tapped: it carries them on from there (taken once, as its first frame is composed).
    val moved = remember { HashMap<String, TrayShown>() }
    val carriedFrom = remember(glide) { glide?.carried?.mapNotNull { id -> moved[id]?.let { id to it.copy() } }?.toMap().orEmpty() }
    // Its rows and cap unfold as one sheet (one per unfold), which keeps their whole heights as they are measured.
    val sheet = remember(unfoldT0, unfoldId, tapOpen) {
        when {
            unfoldId == null -> null
            unfoldT0 != null -> TraySheet({ clock.now - unfoldT0 }, unfoldId, opening = true, full = slices, moved = moved)
            tapOpen != null -> TraySheet(
                tapOpen::elapsed, unfoldId, opening = true, full = slices, bands = bands, strips = strips, moved = moved,
                carried = carriedFrom[unfoldId],
            )
            else -> null
        }
    }
    if (sheet != null) SideEffect { sheet.rows = entries.filter { it is Entry.RowItem && it.sectionId == unfoldId }.map { it.key } }
    // The finished tray folding into its strip (§8.4 L + 1100, planEntries): its rows and cap roll up as one sheet on the
    // engine's clock from the fold's beat, starting from where the band's tray ended (the strip may be taller than the
    // band it replaces, ListPieces). The trays she folds herself roll up the same way on their glide's clock (§8.5), each
    // from where it is when her tap catches it in the middle of a glide.
    val foldIds = remember(entries) { entries.mapNotNull { e -> e.sectionId.takeIf { e is Entry.Cap && e.isFolding } } }
    val foldT0 = foldIds.firstNotNullOfOrNull { p.aisles[it]?.foldT0 }
    val folds = remember(foldIds, foldT0, glide) {
        foldIds.mapNotNull { id ->
            val t0 = p.aisles[id]?.foldT0
            val elapsed: (() -> Long)? = when {
                t0 != null -> { { clock.now - t0 } }
                glide != null && id in glide.folding -> glide::elapsed
                else -> null
            }
            elapsed?.let {
                id to TraySheet(
                    it, id, opening = false, full = slices, band = bands[id], strips = strips, moved = moved,
                    carried = carriedFrom[id]?.takeIf { t0 == null },
                )
            }
        }.toMap()
    }
    if (folds.isNotEmpty()) SideEffect { for ((id, fold) in folds) fold.rows = entries.filter { it is Entry.RowItem && it.sectionId == id }.map { it.key } }
    val lastFold = remember { LongArray(1) { Long.MIN_VALUE / 2 } }
    if (foldT0 != null) lastFold[0] = foldT0
    // The curl (L + 800) keeps its last pose on the folding tray: the engine's curl ends where the fold begins.
    val curls = remember { HashMap<String, Long>() }
    for ((id, a) in p.aisles) a.curlT0?.let { curls[id] = it }
    // While it unfolds or folds every item keeps step with the layout, frame by frame: no placement animation at all
    // (even one that lands at once shows its start for a frame, and the lazy list swaps it for a soft spring when it
    // interrupts a slide still running, which left the strips below lagging under the growing tray).
    val now = Snapshot.withoutReadObservation { clock.now }
    val unfolding = unfoldT0 != null && now < unfoldT0 + UNFOLD_MS + UNFOLD_SETTLE_MS
    val folding = now < lastFold[0] + UNFOLD_MS + UNFOLD_SETTLE_MS
    val still = reduced || screen.layoutSnaps > 0 || unfolding || folding || glide != null
    // Tests read where each slice of an unfolding or folding tray is laid out ("slice:<key>").
    val probe = LocalPieceProbe.current
    // WP6's check-off chains have played out (no hop in flight, no burst, no celebration): egg 8's cat may curl up.
    val chainsDone = p.flights.isEmpty() && p.bursts.isEmpty() && p.celebration == null
    val ladybugStrip = LocalListEggs.current.ladybug?.sectionId
    val scope = rememberCoroutineScope()
    val act by rememberUpdatedState(actions)
    val rowActions = remember(actions, screen) {
        RowActions(
            onToggle = actions.onToggle,
            onDelete = actions.onDelete,
            onEdit = actions.onOpenEdit,
            onTearCue = { actions.onCue(Cue.TEAR_OFF) },
            // Released a few frames after the row has left the list, so the rows below never start a placement
            // animation in the very pass where their index changes (they would slide in from below the viewport).
            onSlotClosing = { closing ->
                if (closing) {
                    screen.layoutSnaps++
                } else {
                    scope.launch {
                        try {
                            repeat(3) { withFrameMillis { } }
                        } finally {
                            screen.layoutSnaps--
                        }
                    }
                }
            },
        )
    }

    // Focus after her own folds and unfolds (§15, item 193): the chevron when it opened, the strip when it folded.
    for (s in state.sections) {
        key(s.id) {
            val (chevron, strip) = screen.focusOf(s.id)
            FoldFocusEffect(open = s.id == openId, anyOpen = openId != null, userToggles = screen.toggles.of(s.id), chevron = chevron, strip = strip)
        }
    }

    val latestEntries by rememberUpdatedState(entries)
    val latestSections by rememberUpdatedState(state.sections)
    /** Tab and Shift+Tab from a list item to the next one in the list's order (see the list's modifier). */
    fun tabInListOrder(e: KeyEvent): Boolean {
        if (e.key != Key.Tab) return false
        val key = screen.keyFocus?.first ?: return false
        val entries = latestEntries
        val targets = screen.focusTargets(entries)
        val at = targets.indexOfFirst { entries[it.first].key == key }
        if (at < 0) return false
        val back = e.isShiftPressed
        val next = targets.getOrNull(if (back) at - 1 else at + 1)
        // Up and out of the list: the top stack's own order takes it from there.
        if (next == null && back) return false
        if (e.type != KeyEventType.KeyDown) return true
        scope.launch {
            if (next == null) {
                // On from the list's last item to the dock (never back up to a plate pinned above it).
                runCatching { screen.dockFocus.requestFocus() }
                return@launch
            }
            val (index, requester) = next
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                listState.scrollToItem(index)
                withFrameMillis { }
            }
            runCatching { requester.requestFocus() }
        }
        return true
    }

    // The row that held the keyboard's focus folded away with its aisle (§15): its strip takes focus once it is laid out
    // (the open tray's chevron, had the aisle opened again meanwhile), so she goes on from where she was.
    val inputModes = LocalInputModeManager.current
    LaunchedEffect(screen.refocus) {
        val id = screen.refocus ?: return@LaunchedEffect
        var waited = 0
        while (latestEntries.none { it.sectionId == id && (it is Entry.Strip || it is Entry.Header) } && waited++ < OPEN_WAIT_FRAMES) withFrameMillis { }
        withFrameMillis { }
        val (chevron, strip) = screen.focusOf(id)
        runCatching { if (latestEntries.any { it is Entry.Header && it.sectionId == id }) chevron.requestFocus() else strip.requestFocus() }
        screen.refocus = null
    }
    val dockPx by rememberUpdatedState(with(density) { dockHeight.roundToPx() })
    val snap by rememberUpdatedState(reduced)
    val gapPx = with(density) { REVEAL_MARGIN.roundToPx() }
    val topPadPx = with(density) { LIST_TOP_PAD.roundToPx() }
    // Room added after the list's end so a stop's band can land flush at the top, or a plate be pushed off whole, when
    // the list ends too soon after it (items 178, 313). It goes when another section opens, never as one folds: at the
    // list's end that would pull everything down at once while the tray rolls up.
    val roomFor = remember { arrayOfNulls<String>(1) }
    if (openId != null) roomFor[0] = openId
    var endRoom by remember(roomFor[0]) { mutableIntStateOf(0) }
    // Her own drag or fling moved the list since it last came to rest (programmatic scrolls never pass the nested
    // scroll connection).
    val userScrolled = remember { BooleanArray(1) }
    val platePx = with(density) { PLATE_FALLBACK.roundToPx() }

    /**
     * Runs a programmatic scroll: the add bar ignores it (item 116), and the rows keep still while it runs and as it
     * lands (the scroll's last remeasure would otherwise start every row's placement animation from where the scroll
     * left it, so the rows would drop below the plate and slide back up).
     */
    suspend fun programmatic(scroll: suspend () -> Unit) {
        collapse?.ignoreProgrammaticScroll()
        screen.layoutSnaps++
        try {
            scroll()
            withFrameMillis { }
            withFrameMillis { }
        } finally {
            screen.layoutSnaps--
        }
    }

    /** A programmatic scroll to [index]: a jump under reduced motion (§8.8), else smooth. */
    suspend fun scrollTo(index: Int, offset: Int = 0) = programmatic {
        if (snap) listState.scrollToItem(index, offset) else listState.animateScrollToItem(index, offset)
    }

    suspend fun scrollBy(px: Float) {
        if (px == 0f) return
        programmatic {
            if (snap) listState.scrollBy(px) else listState.animateScrollBy(px, tween(PicnicMotion.standard.durationMillis, easing = PicnicMotion.standard.easing))
        }
    }

    /**
     * Scrolls [index] flush with the viewport's top edge (past the 11 dp top padding, where the plate pins), so no
     * sliver of the strip before it shows above it (items 178, 313). A stop near the list's end may not have enough list
     * after it to get there: the end first gains a viewport's height of room (out of sight below), the scroll lands flush
     * in one move (one jump under reduced motion), and the room is then trimmed to exactly what the landing needs, which
     * moves nothing. So no frame ever shows the strip above sliced. The trim lands inside the programmatic scroll, the
     * rows held still: the remeasure it causes never starts a slide (the open tray's rows would drop to the screen's foot
     * and slide back up under their band). A stop already laid out glides there over the Next card's rise ([FLUSH_GLIDE]);
     * one further off takes the list's own scroll to it.
     */
    suspend fun scrollFlush(index: Int) {
        val base = endRoom
        val view = listState.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
        endRoom = base + view
        var trimmed = false
        fun trim() {
            trimmed = true
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.maxByOrNull { it.index }
            endRoom = if (last == null || last.index != info.totalItemsCount - 1) {
                base
            } else {
                val surplus = last.offset + last.size + info.afterContentPadding - info.viewportEndOffset
                (endRoom - surplus).coerceIn(base, base + view)
            }
        }
        try {
            withFrameMillis { }
            programmatic {
                val info = listState.layoutInfo
                val laid = info.visibleItemsInfo.firstOrNull { it.index == index }
                when {
                    snap -> listState.scrollToItem(index, topPadPx)
                    laid != null -> {
                        listState.animateScrollBy((laid.offset - info.viewportStartOffset).toFloat(), FLUSH_GLIDE)
                        // Exactly flush, whatever moved meanwhile (a pixel or two at most).
                        listState.scrollToItem(index, topPadPx)
                    }
                    else -> listState.animateScrollToItem(index, topPadPx)
                }
                // The landing laid out, then the trim, with the rows held still.
                withFrameMillis { }
                trim()
            }
        } finally {
            if (!trimmed) trim()
        }
    }

    /**
     * Brings [itemId] into view, clear of the pinned plate above and the dock below (§6.1, §6.2, §8.4): its row, or the
     * strip of its folded aisle. It waits (bounded) until the list lays it out, scrolls no further than it must when it
     * is partly on screen, and does nothing when it already shows.
     */
    suspend fun reveal(itemId: String, rowOnly: Boolean) {
        fun row() = latestEntries.indexOfFirst { it is Entry.RowItem && it.key == itemId && !it.folding }
        fun strip(): Int {
            val id = latestSections.firstOrNull { s -> s.rows.any { it.id == itemId } }?.id ?: return -1
            return latestEntries.indexOfFirst { it is Entry.Strip && it.sectionId == id }
        }
        // [rowOnly]: its aisle is opening (the change re-opened it), so the row itself is awaited, not the strip.
        var waited = 0
        while (row() < 0 && (rowOnly || strip() < 0) && waited++ < OPEN_WAIT_FRAMES) withFrameMillis { }
        // One more frame: the layout with it in has run.
        withFrameMillis { }
        val row = row()
        val target = if (row >= 0) row else strip()
        if (target < 0) return
        val sectionId = latestEntries[target].sectionId
        val header = if (row >= 0) latestEntries.indexOfFirst { it is Entry.Header && it.sectionId == sectionId } else -1

        /** Where [target] sits now: null when it is clear of the plate and the dock, else what it needs. */
        fun need(): Need? {
            val info = listState.layoutInfo
            val plate = info.visibleItemsInfo.firstOrNull { it.index == header }
            // What may cover it at the top: its own tray's band when pinned there, else nothing.
            val clearTop = maxOf(info.viewportStartOffset, if (plate != null && plate.index != target) plate.offset + plate.size else info.viewportStartOffset)
            val clearBottom = info.viewportEndOffset - dockPx
            val item = info.visibleItemsInfo.firstOrNull { it.index == target } ?: return Need.Away(plate?.size ?: platePx)
            return when {
                item.offset < clearTop -> Need.Away(plate?.size ?: platePx)
                item.offset + item.size > clearBottom -> Need.Lift(minOf(item.offset + item.size - clearBottom + gapPx, item.offset - clearTop))
                else -> null
            }
        }

        // Each placement is absolute (by index), so a layout still settling (an aisle opening, the list keeping its
        // place) never leaves it short; then one more look, in case it is still under the plate or the dock.
        repeat(2) { pass ->
            when (val n = need()) {
                null -> return
                is Need.Lift -> scrollBy(n.px.toFloat())
                is Need.Away -> when {
                    // A row near the top of its tray: the whole tray from its band, so she sees where it came back.
                    pass == 0 && row >= 0 && target - header <= OPEN_ROWS_IN_VIEW -> scrollFlush(header)
                    // A row further down lands right under its pinned plate (the row above it wholly under the plate).
                    row >= 0 -> scrollTo(target, -(n.plate - topPadPx))
                    else -> scrollTo(target)
                }
            }
        }
    }

    // The next stop opening behind the Next card scrolls into view (§10), or a row an Undo brought back (§8.4).
    LaunchedEffect(p.scroll?.t0) {
        val req = p.scroll ?: return@LaunchedEffect
        req.itemId?.let {
            reveal(it, rowOnly = true)
            return@LaunchedEffect
        }
        // The scroll lands on the layout after the fold: it waits (bounded) until no tray but the stop's is laid out (a
        // folding one rolled up and gone), then a frame, so the stop's band lands flush at the top (item 178).
        var waited = 0
        while (latestEntries.any { (it is Entry.Header && it.sectionId != req.sectionId) || it.isFolding } && waited++ < OPEN_WAIT_FRAMES) withFrameMillis { }
        withFrameMillis { }
        val index = latestEntries.indexOfFirst { it.sectionId == req.sectionId && (it is Entry.Header || it is Entry.Strip) }
        if (index < 0) return@LaunchedEffect
        // Flush with the viewport's top edge (past the 11 dp top padding), where the plate pins: no sliver of the strip
        // before it shows above it.
        scrollFlush(index)
    }
    // A commit's first new row comes into view (§6.2), unless she is scrolling.
    LaunchedEffect(screen.revealTap) {
        val tap = screen.revealTap ?: return@LaunchedEffect
        if (listState.isScrollInProgress) return@LaunchedEffect
        reveal(tap.itemId, rowOnly = false)
    }
    // A plate never rests partly pushed (R6, item 313): when the list comes to rest at its end with the open tray's band
    // half pushed off by the tray's end, the list's end gains the room to push it off whole, and the list moves there.
    LaunchedEffect(listState, openId) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            // Only where her own scroll came to rest: the list's own scrolls land where they mean to (and one of them may
            // be about to start).
            if (scrolling || !userScrolled[0]) return@collect
            userScrolled[0] = false
            val info = listState.layoutInfo
            val end = info.visibleItemsInfo.firstOrNull { (it.key as? String) == "end:$openId" } ?: return@collect
            val plate = info.visibleItemsInfo.firstOrNull { (it.key as? String) == "header:$openId" } ?: return@collect
            val shown = end.offset - info.viewportStartOffset
            if (shown <= 0 || shown >= plate.size || listState.canScrollForward) return@collect
            endRoom += shown + 1
            withFrameMillis { }
            scrollBy((shown + 1).toFloat())
        }
    }
    // Her own open scrolls the tray into view (§6.1: smooth, programmatic; the add bar ignores it, item 116) unless its
    // band and its first three rows already show above the add bar. It waits for the tray to be laid out: a bounded
    // number of frames, as the engine may hold another aisle open a moment.
    val latestGlide by rememberUpdatedState(glide)
    LaunchedEffect(screen.openTap) {
        val tap = screen.openTap ?: return@LaunchedEffect
        fun headerIndex() = latestEntries.indexOfFirst { it is Entry.Header && it.sectionId == tap.sectionId }
        var waited = 0
        while (headerIndex() < 0 && waited++ < OPEN_WAIT_FRAMES) withFrameMillis { }
        // The tray unfolds first (§8.5): whether it needs a scroll is judged at its whole height.
        while (latestGlide != null && waited++ < OPEN_WAIT_FRAMES) withFrameMillis { }
        // One more frame: the layout with the tray in it has run.
        withFrameMillis { }
        val index = headerIndex()
        if (index < 0) return@LaunchedEffect
        val cap = latestEntries.indexOfFirst { it is Entry.Cap && it.sectionId == tap.sectionId }
        val info = listState.layoutInfo
        val header = info.visibleItemsInfo.firstOrNull { it.index == index }
        val last = info.visibleItemsInfo.firstOrNull { it.index == minOf(index + OPEN_ROWS_IN_VIEW, cap - 1).coerceAtLeast(index) }
        val inView = header != null && header.offset >= info.viewportStartOffset && last != null && last.offset + last.size <= info.viewportEndOffset - dockPx
        if (inView) return@LaunchedEffect
        scrollTo(index)
    }
    // At the top, the bar and the book come back (§4.2, §6.1).
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }.collect { top ->
            collapse?.atTop(top)
            if (top) screen.scrolledDown = false
        }
    }

    // Ambient loops (§8.5): a folded basket wobbles, a sticker gets a gloss sweep; only while on screen and resumed.
    var fraction by remember { mutableFloatStateOf(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val sections by rememberUpdatedState(state.sections)
    val open by rememberUpdatedState(openId)
    val ambient = rememberAmbient(
        visible = ambientVisible(lifecycleState.isAtLeast(Lifecycle.State.RESUMED), fraction),
        reduced = reduced,
        baskets = { sections.filter { it.id != open && !it.complete && it.gotten.isNotEmpty() }.map { AmbientBasket(it.id, it.gotten.map { m -> m.itemId }) } },
        stickers = { sections.firstOrNull { it.id == open }?.rows?.filter { !it.checked }?.map { it.id }.orEmpty() },
    )
    val beat = ambient.state.value

    val refreshLabel = stringResource(R.string.list_action_refresh)
    val pullPx = with(density) { PULL_MAX.toPx() }
    val triggerPx = with(density) { PULL_TRIGGER.toPx() }
    val pullBack = remember { Animatable(0f) }
    val online by rememberUpdatedState(state.online)
    val refresh: () -> Unit = { screen.refresh(act, online) }
    val connection = remember(screen, pullPx, triggerPx, reduced) {
        object : NestedScrollConnection, PullGate {
            private var raw = 0f

            /** A finger is on the list: only her own drag pulls the bunting, never a programmatic scroll (a focus
             * hand-off's bring-into-view, the next stop opening) that runs into the top. */
            override var touching = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pushing back up while pulled: the bunting rises first.
                if (raw > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -raw)
                    raw += used
                    screen.pull = (raw * PULL_FOLLOW).coerceAtMost(pullPx)
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f) userScrolled[0] = true
                // The scroll collapses the app bar and the book (and expands them on the way back).
                if (consumed.y < -COLLAPSE_SLOP * density.density) screen.scrolledDown = true
                if (consumed.y > COLLAPSE_SLOP * density.density) screen.scrolledDown = false
                if (touching && source == NestedScrollSource.UserInput && available.y > 0f) {
                    raw += available.y
                    screen.pull = (raw * PULL_FOLLOW).coerceAtMost(pullPx)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (raw <= 0f) return Velocity.Zero
                val pulled = screen.pull
                raw = 0f
                if (pulled >= triggerPx) refresh()
                if (reduced) {
                    screen.pull = 0f
                } else {
                    pullBack.snapTo(pulled)
                    withContext(FixedMotionScale) { pullBack.animateTo(0f, tween(220)) { screen.pull = value } }
                }
                return available
            }
        }
    }

    val listRoom = LocalListRoom.current
    // Clipped to the viewport: the bunting starts above it (−34 dp) and comes out from under the top stack (R6).
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onGloballyPositioned {
                val b = it.boundsInWindow()
                screen.listBounds = b
                // Egg 18: where the viewport starts before any inset (the receipt asks for room below its paper).
                listRoom?.listTop = b.top
            }
            .pointerInput(connection) {
                // Watches (never consumes) whether a finger is down, for the pull to refresh.
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        connection.touching = e.changes.any { it.pressed }
                    }
                }
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Egg 18: the viewport starts under a waiting receipt's paper (its inset, not the content's).
                .padding(top = topExtra)
                .testTag(ListTags.LIST)
                // Tab and Shift+Tab go through the list in its own order (§15): strips, the open tray's chevron and its
                // rows by their place in the list. The lazy list places a pinned plate after the rows it covers, and
                // Compose orders Tab by placement, so Tab alone would loop through a long tray's rows and plate.
                .onPreviewKeyEvent(::tabInListOrder)
                .focusProperties {
                    onEnter = {
                        // Entering the list, Tab starts at its first item laid out in its order (the open tray's
                        // chevron before its rows), Shift+Tab at its last.
                        val laid = listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
                        val ends = screen.focusTargets(latestEntries).filter { it.first in laid }
                        val pick = when (requestedFocusDirection) {
                            FocusDirection.Next -> ends.firstOrNull()
                            FocusDirection.Previous -> ends.lastOrNull()
                            else -> null
                        }
                        pick?.let { runCatching { it.second.requestFocus() } }
                    }
                }
                .focusGroup()
                // A row holding the keyboard's focus that a fold takes away (its aisle done): focus goes on to its strip
                // instead of back to the top of the screen (§15).
                .onFocusChanged { f ->
                    if (f.hasFocus) return@onFocusChanged
                    val (key, section) = screen.keyFocus ?: return@onFocusChanged
                    if (inputModes.inputMode == InputMode.Keyboard && latestEntries.none { it.key == key }) {
                        screen.keyFocus = null
                        screen.refocus = section
                    }
                }
                // The eggs' room: the list's viewport (the scenes are brought into it, the ladybug flies off its sides).
                .then(with(anchors) { Modifier.anchor(EggAnchors.LIST) })
                .visibleFraction { fraction = it }
                .then(if (collapse != null) Modifier.nestedScroll(collapse.nestedScrollConnection) else Modifier)
                .nestedScroll(connection)
                .semantics { customActions = listOf(CustomAccessibilityAction(refreshLabel) { refresh(); true }) }
                .graphicsLayer { translationY = if (reduced) 0f else screen.pull },
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = LIST_TOP_PAD,
                bottom = bottomPadding + with(density) { endRoom.toDp() },
            ),
        ) {
            entries.forEachIndexed { index, e ->
                when (e) {
                    is Entry.Strip -> item(key = e.key, contentType = "strip") {
                        val s = e.s
                        val (_, stripFocus) = screen.focusOf(s.id)
                        FoldedStrip(
                            section = s,
                            basket = p.baskets[s.id],
                            aisle = p.aisles[s.id],
                            clock = clock,
                            anchors = anchors,
                            focus = stripFocus,
                            onOpen = {
                                screen.toggles.bump(s.id)
                                screen.openedByHer(s.id)
                                screen.foldTap = ListScreenState.FoldTap(s.id, open = true)
                                act.onOpenSection(s.id)
                            },
                            onPeek = { held, ms ->
                                screen.peek = if (held) s.id else null
                                if (!held) act.onBasketHold(s.id, ms)
                            },
                            // Egg 2: the ladybug sets off while she still holds (threshold + 560 ms), and walks on after.
                            onHeld = { ms -> act.onBasketHold(s.id, ms) },
                            chainsDone = chainsDone,
                            peeked = screen.peek == s.id,
                            ambient = beat,
                            ambientClock = ambient.clock,
                            modifier = Modifier
                                .itemMotion(this, still, placementMs = PicnicMotion.layout.durationMillis)
                                .keyFocusIn(screen, e.key, s.id)
                                // A peeked basket, or egg 2's ladybug walking under this strip, draws over the strip below
                                // (and over the open tray's band beside it).
                                .zIndex(if (screen.peek == s.id || ladybugStrip == s.id) Z_LIFTED else 0f)
                                .onGloballyPositioned { screen.stripBounds[s.id] = it.boundsInWindow() }
                                // Its height as laid out, read by a fold in the same pass (TraySheet).
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    strips[s.id] = placeable.height
                                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                },
                        )
                    }
                    is Entry.Header -> stickyHeader(key = e.key, contentType = "header") {
                        val s = e.s
                        val pinned by remember(listState, index) {
                            derivedStateOf {
                                listState.firstVisibleItemIndex > index ||
                                    (listState.firstVisibleItemIndex == index && listState.firstVisibleItemScrollOffset > 0)
                            }
                        }
                        val (chevron, _) = screen.focusOf(s.id)
                        TrayHeader(
                            section = s,
                            basket = p.baskets[s.id],
                            clock = clock,
                            anchors = anchors,
                            pinned = pinned,
                            current = s.id == currentId,
                            chevronFocus = chevron,
                            chainsDone = chainsDone,
                            onFold = {
                                screen.toggles.bump(s.id)
                                screen.foldTap = ListScreenState.FoldTap(s.id, open = false)
                                act.onFoldAll()
                            },
                            // The band draws over its tray's rows when it pins (R6), and over the strips that slide
                            // past the tray as it opens or closes (§4.10).
                            modifier = Modifier
                                .zIndex(Z_PLATE)
                                .keyFocusIn(screen, e.key, s.id)
                                // Its height as laid out, read by its own unfold in the same pass (it is measured before
                                // its rows) and by its fold after it (TraySheet).
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    bands[s.id] = placeable.height
                                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                },
                        )
                    }
                    is Entry.RowItem -> item(key = e.key, contentType = "row") {
                        val curl = when {
                            !e.last -> null
                            e.folding -> p.aisles[e.s.id]?.let { a -> a.curlT0 ?: curls[e.s.id]?.takeIf { a.foldT0 != null && a.foldT0 - it in 0..CURL_TO_FOLD } }
                            else -> p.aisles[e.s.id]?.curlT0
                        }
                        TrayRow(
                            row = e.row,
                            section = e.s,
                            first = e.first,
                            last = e.last,
                            presentation = p,
                            clock = clock,
                            anchors = anchors,
                            actions = rowActions,
                            pop = screen.pops[e.row.id],
                            onPop = { generation, done -> if (done) screen.popped(e.row.id, generation) else screen.popStarted(e.row.id, generation) },
                            sweepT0 = beat?.sweepT0.takeIf { beat?.sweepItem == e.row.id },
                            ambientClock = ambient.clock,
                            // A folding tray keeps its ring to the end of its fold.
                            current = e.folding || e.s.id == currentId,
                            modifier = Modifier
                                .itemMotion(this, still, placementMs = SLOT_CLOSE_MS)
                                .probe(probe, "slice:${e.key}")
                                .sheetSlice(if (e.folding) folds[e.s.id] else sheet.takeIf { unfoldId == e.s.id }, e.key, slices)
                                .then(if (e.folding) Modifier.goingAway() else Modifier)
                                .keyFocusIn(screen, e.key, e.s.id)
                                .focusRequester(screen.rowFocus(e.row.id))
                                // The open tray draws over the folded strips that slide past it as it opens (the strip
                                // below never covers its rows or its cap, §4.10).
                                .zIndex(Z_TRAY)
                                // The curl starts at the panel's inner corner and stays in the row's check column (§8.4).
                                .trayCurl(
                                    curl, clock,
                                    flap = e.s.look.family.pastel,
                                    shadow = PicnicTheme.shadows.color,
                                    reduced = reduced,
                                    inset = TrayMetrics.panelInset,
                                    maxLeg = ROW_CHECK_COLUMN,
                                    under = PicnicTheme.colors.backing,
                                ),
                        )
                    }
                    is Entry.Cap -> item(key = e.key, contentType = "cap") {
                        TrayCap(
                            e.s,
                            current = e.folding || e.s.id == currentId,
                            modifier = Modifier
                                .itemMotion(this, still, placementMs = SLOT_CLOSE_MS)
                                .probe(probe, "slice:${e.key}")
                                .sheetSlice(if (e.folding) folds[e.s.id] else sheet.takeIf { unfoldId == e.s.id }, e.key, slices)
                                .then(if (e.folding) Modifier.goingAway() else Modifier)
                                .zIndex(Z_TRAY),
                        )
                    }
                    // One pixel tall: the list ignores zero-size items when it picks the header to pin; this pushes the
                    // plate off as its tray ends.
                    is Entry.End -> stickyHeader(key = e.key, contentType = "end") {
                        Spacer(Modifier.fillMaxWidth().height(with(LocalDensity.current) { 1.toDp() }))
                    }
                    is Entry.Gap -> item(key = e.key, contentType = "gap") {
                        Spacer(Modifier.itemMotion(this, still, placementMs = PicnicMotion.layout.durationMillis).fillMaxWidth().height(7.dp))
                    }
                    Entry.Note -> item(key = e.key, contentType = "note") {
                        EndNote(state.total, Modifier.itemMotion(this, still, placementMs = PicnicMotion.layout.durationMillis), anchors = anchors)
                    }
                }
            }
        }
        // Reduced motion: no sag, no bunting; "SYNC" on the LCD only (§8.5). The pull still counts towards the 40 dp.
        if (screen.pull > 0f && !reduced) Bunting(screen.pull / pullPx, Modifier.align(Alignment.TopCenter))
    }
}

/** Whether a finger is on the list (the pull to refresh follows only her own drags). */
private interface PullGate {
    var touching: Boolean
}

/** Pull to refresh (§6.1): the list follows at 0.6×, at most 70 dp; a release past 40 dp re-syncs. */
private val PULL_MAX: Dp = 70.dp
private val PULL_TRIGGER: Dp = 40.dp
private const val PULL_FOLLOW: Float = 0.6f
private const val COLLAPSE_SLOP: Float = 4f

/** What a row brought into view needs: to come back from under the plate or off screen, or to rise [px] above the dock. */
private sealed interface Need {
    data class Away(val plate: Int) : Need

    data class Lift(val px: Int) : Need
}

/** The list's top padding (§6.1). */
private val LIST_TOP_PAD: Dp = 11.dp

/** A row brought into view keeps this far from the plate above it and the dock below it. */
private val REVEAL_MARGIN: Dp = 8.dp

/** The pinned plate's height when its band is not laid out yet (§4.10: 53 dp, its ring and shadow). */
private val PLATE_FALLBACK: Dp = 56.dp

/**
 * Draw order in the list: the folded strips, then the open tray's rows and cap (the strips sliding past the tray as it
 * opens or closes go under it), then the band (pinned, it hides the rows, R6), then a peeked basket or egg 2's strip.
 */
private const val Z_TRAY: Float = 1f
private const val Z_PLATE: Float = 2f
private const val Z_LIFTED: Float = 3f

/** How long her open waits for its tray to be laid out (frames, about 1.5 s), and the rows it brings into view. */
private const val OPEN_WAIT_FRAMES: Int = 90
private const val OPEN_ROWS_IN_VIEW: Int = 3
private const val SLOT_CLOSE_MS: Int = 380

/**
 * Placement only: rows and strips move smoothly when the layout changes. [still] (reduced motion, a programmatic scroll
 * or the list keeping its place, a tray unfolding): they take their new places at once, keeping step with the layout.
 */
private fun Modifier.itemMotion(scope: LazyItemScope, still: Boolean, placementMs: Int): Modifier = with(scope) {
    if (still) {
        this@itemMotion.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        this@itemMotion.animateItem(fadeInSpec = null, placementSpec = tween(placementMs, easing = PicnicMotion.layout.easing), fadeOutSpec = null)
    }
}

/**
 * The rows and cap of a tray moving as one sheet, [elapsed] ms in (§8.4, §8.5): unfolding ([opening]: behind the Next
 * card, or as she opens it), or rolling up into its strip (the finished tray, or the one she folds). [full] holds each
 * slice's whole height (px) as the list last measured it (the list's own map, so a sheet starting in the middle of a pass
 * already knows the slices measured after the one being laid out); [rows] are the tray's row keys in list order, [cap]
 * its cap's key. A fold starts where the band's tray ended: from the rows and cap less what its strip took beyond the
 * band's [band] height ([strips] holds the strips' heights as laid out, its own by [sectionId]), so the strips below
 * never step down as the strip takes the band's place. An unfold she starts herself starts where her strip ended (its
 * band, laid out in the same pass, is in [bands]): the rows show the strip's extra height at once, so the strips below
 * never step up either. A tray her tap catches in the middle of a glide starts where that glide had got it to
 * ([carried], from [moved], where every sheet keeps how it last laid its tray out), never back at its strip or its whole
 * height.
 */
private class TraySheet(
    val elapsed: () -> Long,
    val sectionId: String,
    val opening: Boolean,
    val full: MutableMap<String, Int>,
    private val band: Int? = null,
    private val strips: Map<String, Int>? = null,
    private val bands: Map<String, Int>? = null,
    private val moved: MutableMap<String, TrayShown>? = null,
    carried: TrayShown? = null,
) {
    val cap: String = "cap:$sectionId"
    var rows: List<String> = emptyList()

    // Where [carried] had the tray when its glide began.
    private val carriedPx: Int? = carried?.px
    private val carriedUnderBand: Boolean = carried?.underBand == true

    /** How much of [whole] px of rows and cap shows [elapsed] ms in. */
    fun shown(whole: Int, elapsed: Long): Int {
        val e = PicnicMotion.layout.easing.transform((elapsed.toFloat() / UNFOLD_MS).coerceIn(0f, 1f))
        if (opening) {
            val strip = strips?.get(sectionId)
            val band = bands?.get(sectionId)
            // What the strip took beyond its band: the rows show it at once, so nothing below steps up.
            val extra = if (strip == null || band == null) 0 else strip - band
            val from = when {
                carriedPx == null -> extra
                carriedUnderBand -> carriedPx
                else -> carriedPx + extra
            }.coerceIn(0, whole)
            return (from + (whole - from) * e).roundToInt().coerceIn(0, whole)
        }
        val strip = strips?.get(sectionId) ?: band ?: 0
        // What the strip takes beyond the band it replaces: the rows give it up at once, so nothing below steps down.
        val extra = strip - (band ?: strip)
        val from = when {
            carriedPx == null -> whole - extra
            carriedUnderBand -> carriedPx - extra
            else -> carriedPx
        }.coerceIn(0, whole)
        return (from * (1f - e)).roundToInt().coerceIn(0, whole)
    }

    /** Keeps where this sheet lays its tray out: [px] of its rows and cap shown (under its band while it opens). */
    fun laidOut(px: Int) {
        val at = moved?.getOrPut(sectionId) { TrayShown() } ?: return
        at.px = px
        at.underBand = opening
    }

    /** The tray is whole and still: a tap that catches it now starts from its whole height. */
    fun settled() {
        moved?.remove(sectionId)
    }
}

/** How much of a tray's rows and cap a sheet last showed (px), and whether under its band (else under its strip). */
private class TrayShown(var px: Int = 0, var underBand: Boolean = false) {
    fun copy(): TrayShown = TrayShown(px, underBand)
}

/**
 * A slice of a tray's [sheet] (§8.4): laid out whole, the sheet's rows and cap together take [TraySheet.shown] of their
 * summed height on the sheet's clock ([TraySheet.elapsed]), over [PicnicMotion.layout]. The cap keeps its share and its
 * bottom edge (the end cap, the ring and the shadow), so the sheet is closed in every frame; the rows unroll from the top
 * down (or roll up from the bottom), so only the row at the moving edge is cut and the others show whole as the tray
 * opens. A row not reached yet keeps a pixel (its panel and wall), so the list never drops it. Only the slice's top and
 * bottom are clipped: the current aisle's lemon ring and shadow run on outside its sides, as on a still tray. With no
 * sheet the slice is laid out whole, its height kept in [heights] for the next sheet's first frame.
 */
private fun Modifier.sheetSlice(sheet: TraySheet?, key: String, heights: MutableMap<String, Int>): Modifier =
    if (sheet == null) {
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            heights[key] = placeable.height
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    } else {
        this
            .drawWithContent { clipRect(-size.width, 0f, size.width * 2f, size.height) { this@drawWithContent.drawContent() } }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                sheet.full[key] = placeable.height
                val elapsed = sheet.elapsed()
                val cap = key == sheet.cap
                if (cap && sheet.opening && elapsed >= UNFOLD_MS) sheet.settled()
                if ((sheet.opening && elapsed >= UNFOLD_MS) || (!cap && key !in sheet.rows)) {
                    return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                val capH = sheet.full[sheet.cap] ?: 0
                var rowsH = 0
                var before = 0
                for (k in sheet.rows) {
                    if (k == key) before = rowsH
                    rowsH += sheet.full[k] ?: 0
                }
                val whole = capH + rowsH
                val shown = sheet.shown(whole, elapsed)
                sheet.laidOut(shown)
                val capShown = if (whole == 0) 0 else (capH.toLong() * shown / whole).toInt()
                // A folded slice takes no room at all: it leaves the list as the fold ends, with nothing left to close.
                val least = if (sheet.opening) 1 else 0
                if (cap) {
                    val h = capShown.coerceAtLeast(least)
                    layout(placeable.width, h) { placeable.place(0, h - placeable.height) }
                } else {
                    val reach = shown - capShown - before
                    val h = reach.coerceIn(least, placeable.height)
                    layout(placeable.width, h) { placeable.place(0, if (reach <= 0) h - placeable.height else 0) }
                }
            }
    }

/**
 * A slice of a tray rolling up into its strip (§8.4, §8.5): it is going, so a touch on it neither checks nor edits its row
 * (nor puts the finished tray back open halfway through its fold), and TalkBack no longer reads it, as the keyboard
 * passes it by (focusTargets).
 */
private fun Modifier.goingAway(): Modifier = clearAndSetSemantics { }.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

/** The tray's unfold and fold (§8.4: 240 ms `layout`), and the frames the list keeps step after them. */
private val UNFOLD_MS: Int = PicnicMotion.layout.durationMillis
private const val UNFOLD_SETTLE_MS: Int = 48

/**
 * Her own fold or open (§8.5): [folding] (the tray she leaves, none when its band was pinned over its rows, and any her
 * last glide was still rolling up) roll up into their strips and [opening] (the tray she opens) unfolds, over the 240 ms
 * `layout` from the first frame the list shows them, everything below moving in the same frames, as the aisle-done fold
 * and the next stop do (§8.4). [carried]: the trays her last glide was still moving when she tapped, which start from
 * where it had got them (TraySheet), so a tap in the middle of a glide never snaps a tray whole, shut or gone. Timed on
 * its own frame clock ([play]): the engine's sleeps while no chain plays.
 */
@Stable
internal class TapGlide(val folding: Set<String>, val opening: String?, val carried: Set<String> = emptySet()) {
    private val clock = earth.cora.picnic.ui.list.motion.MotionClock()
    private var t0 = Long.MIN_VALUE

    /** How far in it is (ms): 0 until its first frame. Read in layout, it moves the slices on every frame. */
    val elapsed: Long
        get() {
            val now = clock.now
            return if (t0 == Long.MIN_VALUE) 0 else now - t0
        }

    /** The folding trays' rows and caps have rolled up: the list drops them. */
    var rolledUp by mutableStateOf(false)
        private set

    /** Played out, and the list has kept step a frame or two after it. */
    var done by mutableStateOf(false)
        private set

    /** The trays it still moves: the folding ones until they have rolled up, the one it opens until it is open. */
    val moving: Set<String>
        get() = when {
            done || rolledUp -> emptySet()
            opening == null || elapsed >= UNFOLD_MS -> folding
            else -> folding + opening
        }

    suspend fun play() {
        withFrameMillis {
            t0 = it
            clock.now = it
        }
        while (clock.now - t0 < UNFOLD_MS) withFrameMillis { clock.now = it }
        rolledUp = true
        while (clock.now - t0 < UNFOLD_MS + UNFOLD_SETTLE_MS) withFrameMillis { clock.now = it }
        done = true
    }
}

/**
 * The scroll to a stop laid out on screen (the next stop behind the Next card, §10): over the card's 400 ms rise on
 * the `standard` curve, so the list glides there instead of leaping half the way in one frame.
 */
private val FLUSH_GLIDE = tween<Float>(PicnicMotion.cardIn.durationMillis, easing = PicnicMotion.standard.easing)

/** The curl (L + 800) comes this long before the fold (L + 1100), §8.4. */
private const val CURL_TO_FOLD: Long = earth.cora.picnic.ui.list.motion.Beats.FOLD - earth.cora.picnic.ui.list.motion.Beats.CURL

/** The hand-off's tray opens with its scroll request, within a frame or two of it. */
private const val UNFOLD_SLACK_MS: Long = 50

/**
 * The sticker bunting (§6.1): hearts and stars on a dashed 1.6 dp cord that sags as she pulls (−34 → +10 dp).
 * Decorative.
 */
@Composable
private fun Bunting(fraction: Float, modifier: Modifier) {
    val c = PicnicTheme.colors
    val f = fraction.coerceIn(0f, 1f)
    val sag = -34f + 44f * f
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().height(60.dp).testTag(ListTags.BUNTING).decorative()) {
        val width = maxWidth
        Canvas(Modifier.fillMaxWidth().height(60.dp)) {
            val y0 = sag.dp.toPx() + 16.dp.toPx()
            val dip = 14.dp.toPx() * f
            val path = Path().apply {
                moveTo(0f, y0 - dip)
                quadraticTo(size.width / 2f, y0 + dip, size.width, y0 - dip)
            }
            drawPath(path, c.ballpointSoft, style = Stroke(1.6.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))))
        }
        val flags = listOf("heart", "star", "heart", "star4", "heart", "star")
        flags.forEachIndexed { i, name ->
            val x = (i + 0.5f) / flags.size
            val t = (x - 0.5f) * 2f
            val y = sag + 16f + 14f * f * (1f - t * t) - 4f - 14f * f
            DecorSticker(name, size = 16.dp, modifier = Modifier.offset(width * x - 8.dp, y.dp))
        }
    }
}

/**
 * The overlay's peek note (§4.23): 54 dp from the right, below the strip (above it in the lower 55 % of the list). It
 * stays inside the list's viewport, clear of the add bar: it takes the side the rule picks unless that side is too
 * cramped and the other one roomier, and a long aisle or large text caps its height to that side's room (the first
 * lines, then "+n more").
 */
@Composable
private fun PeekLayer(state: ListUiState, screen: ListScreenState, dockHeight: Dp) {
    val id = screen.peek ?: return
    val section = state.sections.firstOrNull { it.id == id } ?: return
    val bounds = screen.stripBounds[id] ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    var noteHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }) {
        val list = screen.listBounds ?: return@Box
        val gap = with(density) { 4.dp.toPx() }
        // The room the note may use, in window px: the list's viewport above the add bar.
        val top = list.top
        val bottom = maxOf(top, list.bottom - with(density) { dockHeight.toPx() })
        val above = bounds.top - gap - top
        val below = bottom - (bounds.bottom + gap)
        val ruleAbove = (bounds.center.y - list.top) > list.height * 0.45f
        val wanted = noteHeight.toFloat()
        val least = with(density) { PEEK_MIN_ROOM.toPx() }
        val flip = if (ruleAbove) !(above < least && below > above) else below < least && above > below
        val room = (if (flip) above else below).coerceAtLeast(least)
        val yWindow = if (flip) bounds.top - gap - minOf(wanted, room) else bounds.bottom + gap
        val y = yWindow.coerceIn(top, maxOf(top, bottom - minOf(wanted, room))) - origin.y
        PeekNote(
            section,
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(-54.dp.roundToPx(), y.roundToInt()) }
                .onGloballyPositioned { noteHeight = it.size.height },
            maxHeight = with(density) { room.toDp() },
        )
    }
}

/** The least room the peek note is given (its title and a line or two), even on a cramped screen. */
private val PEEK_MIN_ROOM: Dp = 72.dp

/**
 * The overflow menu (§4.24) dropping from the More button. A tap outside, Back or Escape closes it (§15); with TalkBack
 * the area outside is a "Close menu" button read after the items, so she never has to pick one to leave.
 */
@Composable
private fun ListMenu(state: ListUiState, actions: ListActions, screen: ListScreenState) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val anchor = screen.moreBounds
    val close = { screen.menuOpen = false }
    val closeLabel = stringResource(R.string.list_menu_close)
    // The menu is part of the List screen (the root destination): without this, Back would leave the app.
    BackHandler(onBack = close)
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            // A tap outside closes the menu; never a keyboard stop of its own (Escape closes it, §15).
            .pointerInput(Unit) { detectTapGestures { close() } }
            .testTag(LIST_MENU_SCRIM_TAG)
            .clearAndSetSemantics {
                contentDescription = closeLabel
                role = Role.Button
                traversalIndex = 1f
                onClick(label = closeLabel) {
                    close()
                    true
                }
            },
    )
    val items = listOf(
        MenuItem(
            stringResource(R.string.list_menu_new_trip), PicnicGlyph.Trip,
            onClick = { screen.newTripOpen = true },
            enabled = state.canStartNewTrip,
            disabledState = stringResource(R.string.list_menu_no_trip),
        ),
        MenuItem(stringResource(R.string.list_menu_switch_list), PicnicGlyph.Lists, actions.onOpenLists),
        MenuItem(stringResource(R.string.list_menu_refresh), PicnicGlyph.Refresh, onClick = { screen.refresh(actions, state.online) }),
        MenuItem(stringResource(R.string.menu_my_store), PicnicGlyph.Aisles, actions.onOpenAisles),
        MenuItem(stringResource(R.string.menu_my_pantry), PicnicGlyph.Jar, actions.onOpenPantry),
        MenuItem(stringResource(R.string.menu_album), PicnicGlyph.Album, actions.onOpenAlbum),
        MenuItem(stringResource(R.string.menu_settings), PicnicGlyph.Settings, actions.onOpenSettings),
    )
    val density = LocalDensity.current
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // It drops from the More button and stops 8 dp above the window's bottom (and the navigation bar); its rows
        // scroll when they need more room. Where less than about three rows would fit under the button (landscape, a
        // short pane), it rises instead so that it ends 8 dp above the bottom (§4.24, §15: every item reachable).
        val top = with(density) { ((anchor?.bottom ?: 0f) - origin.y).toDp() }
        val room = maxHeight - navBottom - MENU_MARGIN - top
        val rises = room < MENU_MIN_ROOM
        OverflowMenu(
            items,
            onDismissRequest = close,
            maxHeight = if (rises) (maxHeight - navBottom - statusTop - MENU_MARGIN * 2).coerceAtLeast(MENU_MIN_ROOM) else room,
            modifier = Modifier
                .align(if (rises) Alignment.BottomEnd else Alignment.TopEnd)
                .then(
                    if (rises) {
                        Modifier.padding(end = 8.dp, bottom = navBottom + MENU_MARGIN)
                    } else {
                        Modifier.offset { IntOffset(-8.dp.roundToPx(), top.roundToPx()) }
                    },
                )
                .testTag(LIST_MENU_TAG)
                // Opened from the keyboard, the menu takes focus and keeps it; Escape closes it (§15).
                .modalLayer(close),
        )
    }
}

/** The overflow menu keeps this far from the window's bottom edge (and from the top when it rises). */
private val MENU_MARGIN: Dp = 8.dp

/** Under the More button the menu needs room for about three rows, else it rises to fit (§4.24). */
private val MENU_MIN_ROOM: Dp = 156.dp

/** The overflow menu's test tag. */
const val LIST_MENU_TAG: String = "picnic_list_menu"

/** The area outside the open overflow menu (a tap there, or its TalkBack button, closes the menu). */
const val LIST_MENU_SCRIM_TAG: String = "picnic_list_menu_scrim"

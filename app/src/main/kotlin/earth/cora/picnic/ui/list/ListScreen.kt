package earth.cora.picnic.ui.list

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.a11y.decorative
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
import earth.cora.picnic.ui.edit.EditItemSheet
import earth.cora.picnic.ui.list.motion.AmbientBasket
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
import earth.cora.picnic.ui.list.motion.unfoldMotion
import earth.cora.picnic.ui.list.motion.visibleFraction
import earth.cora.picnic.ui.nav.LocalAppNav
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.EmptyState
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.MenuItem
import earth.cora.picnic.ui.theme.OverflowMenu
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.ui.theme.holdOnFrameClock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
    val onOpenSettings: () -> Unit = {},
    val onOpenLists: () -> Unit = {},
    val onCloseEdit: () -> Unit = {},
    val onSaveEdit: (name: String, quantity: String?, note: String?) -> Unit = { _, _, _ -> },
    val onMoveToAisle: (itemId: String, aisleId: String?) -> Unit = { _, _ -> },
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

    /** Item id -> its pop (a new row or a duplicate, §6.2). */
    val pops = mutableStateMapOf<String, PopRequest>()
    private var popGen = 0

    /** Her own fold and unfold taps (FoldFocusEffect). */
    val toggles = FoldToggles()

    internal val stripBounds = mutableStateMapOf<String, Rect>()
    internal var moreBounds by mutableStateOf<Rect?>(null)
    internal val focus = HashMap<String, Pair<FocusRequester, FocusRequester>>()

    /** Pops the stickers of a commit's rows, the new ones 80 ms apart (§6.2), the ones already there at once. */
    fun pop(commit: AddCommit) {
        popGen++
        commit.added.forEachIndexed { i, item -> pops[item.id] = PopRequest(popGen, commit.popDelayMillis(i)) }
        commit.alreadyThere.forEach { pops[it.id] = PopRequest(popGen, 0) }
    }

    internal fun focusOf(sectionId: String): Pair<FocusRequester, FocusRequester> = focus.getOrPut(sectionId) { FocusRequester() to FocusRequester() }
}

@Composable
fun rememberListScreenState(): ListScreenState = remember { ListScreenState() }

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
    val state by vm.state.collectAsStateWithLifecycle()
    val addState by addVm.state.collectAsStateWithLifecycle()
    val surprises by vm.surprisesState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val nav = LocalAppNav.current
    val toasts = remember { ToastHostState() }
    val screen = rememberListScreenState()
    var showLists by rememberSaveable { mutableStateOf(false) }
    val addFlows = rememberAddFlows(addVm)
    val tears = remember(resources) { TearToasts(resources, vm::undoDelete) }
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
                is ListEvent.NewBook -> toasts.show(
                    ToastMessage(
                        resources.getString(R.string.list_toast_new_book),
                        kind = "book",
                        actionLabel = resources.getString(R.string.list_undo),
                        onAction = event.undo,
                    ),
                )
                ListEvent.NewTrip -> toasts.show(ToastMessage(resources.getString(R.string.list_toast_new_trip), kind = "trip"))
                is ListEvent.Nudge -> toasts.show(
                    ToastMessage(
                        resources.getString(R.string.list_toast_nudge),
                        kind = "nudge",
                        actionLabel = resources.getString(R.string.list_toast_nudge_action),
                        onAction = { screen.newTripOpen = true },
                        minHoldMs = NUDGE_FLOOR,
                    ),
                )
                is ListEvent.Refreshed -> refreshedText(resources, event)?.let { toasts.show(ToastMessage(it, kind = "sync")) }
                is ListEvent.Message -> toasts.show(ToastMessage(event.text, kind = "save"))
            }
        }
    }
    AddEventsEffect(
        addFlows,
        toasts,
        onCommitted = { commit ->
            screen.pop(commit)
            vm.onCommitted(commit.added)
        },
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
            onOpenEdit = vm::openEdit,
            onStartList = addFlows::focusField,
            onNewTrip = vm::newTrip,
            onRefresh = vm::refresh,
            onOpenAisles = onOpenAisles,
            onOpenPantry = nav.openPantry,
            onOpenAlbum = nav.openAlbum,
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
    ListScreen(
        state = state,
        actions = actions,
        engine = vm.engine,
        toasts = toasts,
        showLists = showLists,
        screen = screen,
        dockHeight = addFlows.dockHeight,
        collapse = addFlows.collapse,
        dock = { AddDock(addFlows, addState, tape = tape, otherSheetOpen = sheetUp) },
    )
    // The sheet's state waits in AddViewModel while Settings or My pantry is open: Back returns to the same sheet.
    AddSheetHost(addFlows, addState, onOpenSettings = onOpenSettings, onEditPantry = nav.onEditPantry)
}

/** The nudge's reading floor (§6.1, §9: it carries an action): 6 s. */
private const val NUDGE_FLOOR: Long = 6000

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
internal class TearToasts(private val resources: Resources, private val undo: (GroceryItem, Int, String?) -> Unit) {
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
            // Restore newest first, so each goes back at the index it had when it was torn out.
            onAction = { all.asReversed().forEach { undo(it.item, it.index, it.listId) } },
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
 * (the app: WP7's AddDock; previews and goldens: the bar at rest), [dockHeight] its measured height (the toasts sit 20 dp
 * above it, the list's bottom padding is it + 26 dp) and [collapse] its fold rules (programmatic scrolls are ignored).
 * [eggs] is WP9's overlay slot.
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
    listState: LazyListState = rememberLazyListState(),
    dockHeight: Dp = 84.dp,
    collapse: AddBarCollapseState? = null,
    dock: @Composable BoxScope.() -> Unit = { StaticDock() },
    eggs: @Composable BoxScope.() -> Unit = {},
    talkBackOverride: Boolean? = null,
) {
    MotionEngineHost(engine, nextStopCard = state.nextStopCard, celebration = state.celebration, eggs = state.eggs, talkBackOverride = talkBackOverride)
    val p = engine.current.value
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density) > 0
    val shortWindow = LocalConfiguration.current.screenHeightDp < SHORT_WINDOW_DP
    Box(modifier.fillMaxSize()) {
        PicnicBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopStack(
                    state = state,
                    presentation = p,
                    engine = engine,
                    ui = TopStackUi(
                        barCollapsed = screen.scrolledDown,
                        bookCollapsed = screen.scrolledDown || ime || shortWindow,
                        syncing = screen.syncing,
                        sunSpin = screen.sunSpin,
                    ),
                    actions = actions,
                    onMore = { screen.menuOpen = true },
                    onMoreBounds = { screen.moreBounds = it },
                    onOpenBook = { screen.tripBookOpen = true },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.loading -> LoadingList()
                        state.total == 0 -> EmptyList(state, actions)
                        else -> AisleList(state, p, engine, actions, screen, listState, dockHeight, collapse)
                    }
                }
            }
        }
        dock()
        MotionOverlay(engine, p, toasts, bottomInset = dockHeight, eggs = eggs)
        PeekLayer(state, screen)
        if (screen.menuOpen) ListMenu(state, actions, screen)
        state.editing?.let { editing ->
            EditItemSheet(
                state = editing,
                onSave = actions.onSaveEdit,
                onClose = actions.onCloseEdit,
                onMoveToAisle = { aisleId -> actions.onMoveToAisle(editing.item.id, aisleId) },
                onSetIcon = { key -> actions.onSetIcon(editing.item.id, key) },
                onRedraw = { actions.onRedrawIcon(editing.item.id) },
                onDelete = { actions.onDelete(editing.item.id) },
            )
        }
        if (showLists) {
            ListSwitcherSheet(
                lists = state.lists,
                onDismiss = actions.onCloseLists,
                onSwitch = actions.onSwitchList,
                onNew = actions.onNewList,
                onRename = actions.onRenameList,
                onDelete = actions.onDeleteList,
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
        )
        CelebrationHost(engine, p)
    }
}

/** Windows shorter than this keep the book collapsed (§7.4). */
private const val SHORT_WINDOW_DP = 600

/** The add bar at rest, docked at the bottom (previews and goldens; the app docks WP7's AddDock). */
@Composable
fun BoxScope.StaticDock(tape: WashiPattern = WashiPattern.SakuraStripe) {
    val c = PicnicTheme.colors
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        AddBar(AddBarUi(), onTextChange = {}, onSend = {}, onSnap = {}, onChoosePhoto = {}, onSpeak = {}, tape = tape)
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

/** The empty list (§4.21): the fresh sticker sheet, the copy, "Start my list"; no book. */
@Composable
private fun EmptyList(state: ListUiState, actions: ListActions) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 96.dp)
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

    data class RowItem(val s: SectionUi, val row: ItemRowUi, val first: Boolean, val last: Boolean) : Entry {
        override val key get() = row.id
        override val sectionId get() = s.id
    }

    data class Cap(val s: SectionUi) : Entry {
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

/** The lazy items for [sections] with [openId] open: strips, or the tray's band, rows, cap and end marker; gaps; the end note. */
private fun planEntries(sections: List<SectionUi>, openId: String?): List<Entry> = buildList {
    for (s in sections) {
        if (s.id == openId && s.rows.isNotEmpty()) {
            add(Entry.Header(s))
            s.rows.forEachIndexed { i, r -> add(Entry.RowItem(s, r, i == 0, i == s.rows.lastIndex)) }
            add(Entry.Cap(s))
            add(Entry.End(s))
        } else {
            add(Entry.Strip(s))
        }
        add(Entry.Gap(s))
    }
    add(Entry.Note)
}

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
    dockHeight: Dp,
    collapse: AddBarCollapseState?,
) {
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val clock = engine.clock
    val anchors = engine.anchors
    val openId = p.openSection(state.openSectionId)
    val entries = remember(state.sections, openId) { planEntries(state.sections, openId) }
    // The current aisle wears the lemon ring, the bee and the ladybug (§4.10, D2). While a chain holds a finished aisle
    // open (the engine's override), it keeps them until it folds; a finished aisle she reopens herself is not current.
    val currentId = if (openId != null && openId != state.openSectionId) openId else state.nowSectionId
    val scope = rememberCoroutineScope()
    val act by rememberUpdatedState(actions)
    val rowActions = remember(actions) {
        RowActions(onToggle = actions.onToggle, onDelete = actions.onDelete, onEdit = actions.onOpenEdit, onTearCue = { actions.onCue(Cue.TEAR_OFF) })
    }

    // Focus after her own folds and unfolds (§15, item 193): the chevron when it opened, the strip when it folded.
    for (s in state.sections) {
        key(s.id) {
            val (chevron, strip) = screen.focusOf(s.id)
            FoldFocusEffect(open = s.id == openId, anyOpen = openId != null, userToggles = screen.toggles.of(s.id), chevron = chevron, strip = strip)
        }
    }

    // The next stop opening behind the Next card scrolls into view (§10); the add bar ignores it (item 116).
    LaunchedEffect(p.scroll?.t0) {
        val req = p.scroll ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.sectionId == req.sectionId && (it is Entry.Header || it is Entry.Strip) }
        if (index < 0) return@LaunchedEffect
        collapse?.ignoreProgrammaticScroll()
        listState.animateScrollToItem(index)
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
    val refresh: () -> Unit = {
        actions.onCue(Cue.PULL_REFRESH)
        act.onRefresh()
        screen.sunSpin++
        screen.syncing = true
    }
    LaunchedEffect(screen.syncing) {
        if (!screen.syncing) return@LaunchedEffect
        withContext(FixedMotionScale) { holdOnFrameClock(PicnicMotion.floorLcdWord) }
        screen.syncing = false
    }
    val connection = remember(screen, pullPx, triggerPx, reduced) {
        object : NestedScrollConnection {
            private var raw = 0f

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
                // The scroll collapses the app bar and the book (and expands them on the way back).
                if (consumed.y < -COLLAPSE_SLOP * density.density) screen.scrolledDown = true
                if (consumed.y > COLLAPSE_SLOP * density.density) screen.scrolledDown = false
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(ListTags.LIST)
                .visibleFraction { fraction = it }
                .then(if (collapse != null) Modifier.nestedScroll(collapse.nestedScrollConnection) else Modifier)
                .nestedScroll(connection)
                .semantics { customActions = listOf(CustomAccessibilityAction(refreshLabel) { refresh(); true }) }
                .graphicsLayer { translationY = if (reduced) 0f else screen.pull },
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 11.dp, bottom = dockHeight + 26.dp),
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
                                act.onOpenSection(s.id)
                            },
                            onPeek = { held, ms ->
                                screen.peek = if (held) s.id else null
                                if (!held) act.onBasketHold(s.id, ms)
                            },
                            peeked = screen.peek == s.id,
                            ambient = beat,
                            ambientClock = ambient.clock,
                            modifier = Modifier
                                .itemMotion(this, reduced, placementMs = PicnicMotion.layout.durationMillis)
                                .zIndex(if (screen.peek == s.id) 1f else 0f)
                                .onGloballyPositioned { screen.stripBounds[s.id] = it.boundsInWindow() }
                                .unfoldMotion(p.aisles[s.id]?.foldT0, clock, reduced),
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
                            onFold = {
                                screen.toggles.bump(s.id)
                                act.onFoldAll()
                            },
                            modifier = Modifier.unfoldMotion(p.aisles[s.id]?.foldT0, clock, reduced),
                        )
                    }
                    is Entry.RowItem -> item(key = e.key, contentType = "row") {
                        val curl = if (e.last) p.aisles[e.s.id]?.curlT0 else null
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
                            sweepT0 = beat?.sweepT0.takeIf { beat?.sweepItem == e.row.id },
                            ambientClock = ambient.clock,
                            current = e.s.id == currentId,
                            modifier = Modifier
                                .itemMotion(this, reduced, placementMs = SLOT_CLOSE_MS)
                                .unfoldMotion(p.aisles[e.s.id]?.foldT0, clock, reduced)
                                .trayCurl(curl, clock, flap = e.s.look.family.pastel, shadow = PicnicTheme.shadows.color, reduced = reduced),
                        )
                    }
                    is Entry.Cap -> item(key = e.key, contentType = "cap") {
                        TrayCap(e.s, current = e.s.id == currentId, modifier = Modifier.itemMotion(this, reduced, placementMs = SLOT_CLOSE_MS).unfoldMotion(p.aisles[e.s.id]?.foldT0, clock, reduced))
                    }
                    // One pixel tall: the list ignores zero-size items when it picks the header to pin; this pushes the
                    // plate off as its tray ends.
                    is Entry.End -> stickyHeader(key = e.key, contentType = "end") {
                        Spacer(Modifier.fillMaxWidth().height(with(LocalDensity.current) { 1.toDp() }))
                    }
                    is Entry.Gap -> item(key = e.key, contentType = "gap") {
                        Spacer(Modifier.itemMotion(this, reduced, placementMs = PicnicMotion.layout.durationMillis).fillMaxWidth().height(7.dp))
                    }
                    Entry.Note -> item(key = e.key, contentType = "note") {
                        EndNote(state.total, Modifier.itemMotion(this, reduced, placementMs = PicnicMotion.layout.durationMillis))
                    }
                }
            }
        }
        if (screen.pull > 0f) Bunting(screen.pull / pullPx, Modifier.align(Alignment.TopCenter))
    }
}

/** Pull to refresh (§6.1): the list follows at 0.6×, at most 70 dp; a release past 40 dp re-syncs. */
private val PULL_MAX: Dp = 70.dp
private val PULL_TRIGGER: Dp = 40.dp
private const val PULL_FOLLOW: Float = 0.6f
private const val COLLAPSE_SLOP: Float = 4f
private const val SLOT_CLOSE_MS: Int = 380

/** Placement only: rows and strips move smoothly when the layout changes; reduced motion swaps at once. */
private fun Modifier.itemMotion(scope: LazyItemScope, reduced: Boolean, placementMs: Int): Modifier = with(scope) {
    if (reduced) {
        this@itemMotion.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        this@itemMotion.animateItem(fadeInSpec = null, placementSpec = tween(placementMs, easing = PicnicMotion.layout.easing), fadeOutSpec = null)
    }
}

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

/** The overlay's peek note (§4.23): 54 dp from the right, below the strip (above it in the lower 55 % of the list). */
@Composable
private fun PeekLayer(state: ListUiState, screen: ListScreenState) {
    val id = screen.peek ?: return
    val section = state.sections.firstOrNull { it.id == id } ?: return
    val bounds = screen.stripBounds[id] ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    var height by remember { mutableFloatStateOf(0f) }
    var noteHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.boundsInWindow().topLeft
                height = it.size.height.toFloat()
            },
    ) {
        val lower = (bounds.center.y - origin.y) > height * 0.45f
        val y = if (lower) bounds.top - origin.y - noteHeight - with(density) { 4.dp.toPx() } else bounds.bottom - origin.y + with(density) { 4.dp.toPx() }
        PeekNote(
            section,
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(-54.dp.roundToPx(), y.roundToInt()) }
                .onGloballyPositioned { noteHeight = it.size.height },
        )
    }
}

/** The overflow menu (§4.24) dropping from the More button; a tap outside closes it. */
@Composable
private fun ListMenu(state: ListUiState, actions: ListActions, screen: ListScreenState) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val anchor = screen.moreBounds
    val close = { screen.menuOpen = false }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close)
            .clearAndSetSemantics { },
    )
    val items = listOf(
        MenuItem(
            stringResource(R.string.list_menu_new_trip), PicnicGlyph.Trip,
            onClick = { screen.newTripOpen = true },
            enabled = state.canStartNewTrip,
            disabledState = stringResource(R.string.list_menu_no_trip),
        ),
        MenuItem(stringResource(R.string.list_menu_switch_list), PicnicGlyph.Lists, actions.onOpenLists),
        MenuItem(stringResource(R.string.list_menu_refresh), PicnicGlyph.Refresh, actions.onRefresh),
        MenuItem(stringResource(R.string.menu_my_store), PicnicGlyph.Aisles, actions.onOpenAisles),
        MenuItem(stringResource(R.string.menu_my_pantry), PicnicGlyph.Jar, actions.onOpenPantry),
        MenuItem(stringResource(R.string.menu_album), PicnicGlyph.Album, actions.onOpenAlbum),
        MenuItem(stringResource(R.string.menu_settings), PicnicGlyph.Settings, actions.onOpenSettings),
    )
    Box(Modifier.fillMaxSize()) {
        OverflowMenu(
            items,
            onDismissRequest = close,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val top = ((anchor?.bottom ?: 0f) - origin.y).roundToInt()
                    IntOffset(-8.dp.roundToPx(), top)
                }
                .testTag(LIST_MENU_TAG),
        )
    }
}

/** The overflow menu's test tag. */
const val LIST_MENU_TAG: String = "picnic_list_menu"

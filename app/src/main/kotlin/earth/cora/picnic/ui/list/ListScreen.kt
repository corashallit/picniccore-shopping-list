package earth.cora.picnic.ui.list

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.ui.add.AddEvent
import earth.cora.picnic.ui.add.AddSheetRoute
import earth.cora.picnic.ui.add.AddViewModel
import earth.cora.picnic.ui.common.HintText
import earth.cora.picnic.ui.common.ScreenScaffold
import earth.cora.picnic.ui.edit.EditItemSheet
import earth.cora.picnic.ui.theme.AisleHeader
import earth.cora.picnic.ui.theme.AisleSectionRow
import earth.cora.picnic.ui.theme.EmptyState
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.ItemRowCard
import earth.cora.picnic.ui.theme.LoadingSparkle
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.PicnicTopBar
import earth.cora.picnic.ui.theme.TripProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the List screen (and its Edit and list-switcher sheets) can ask for. Defaults do nothing. */
@Stable
class ListActions(
    val onToggle: (itemId: String) -> Unit = {},
    val onDelete: (itemId: String) -> Unit = {},
    val onToggleSection: (sectionId: String) -> Unit = {},
    val onClearChecked: () -> Unit = {},
    val onOpenEdit: (itemId: String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpenAisles: () -> Unit = {},
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
)

/**
 * Stateful List destination: wires [ListViewModel] and [AddViewModel], owns the Add sheet, the
 * list switcher and the snackbars (undo for deletes and "Clear checked", "Added N items").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListRoute(
    vm: ListViewModel,
    addVm: AddViewModel,
    onOpenAisles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showLists by rememberSaveable { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun snack(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            // A new message replaces the current one instead of queueing behind it.
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    fun closeAddSheet() {
        scope.launch { addSheetState.hide() }.invokeOnCompletion {
            showAdd = false
            addVm.close()
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ListEvent.Deleted -> snack(
                    resources.getString(R.string.list_removed, event.item.name),
                    resources.getString(R.string.list_undo),
                ) { vm.undoDelete(event.item, event.index, event.listId) }
                is ListEvent.Cleared -> snack(
                    resources.getQuantityString(R.plurals.list_cleared, event.removed.size, event.removed.size),
                    resources.getString(R.string.list_undo),
                ) { vm.undoClear(event.removed, event.listId) }
                is ListEvent.Message -> snack(event.text)
            }
        }
    }
    LaunchedEffect(addVm) {
        addVm.events.collect { event ->
            when (event) {
                is AddEvent.Added -> {
                    closeAddSheet()
                    snack(resources.getQuantityString(R.plurals.list_added, event.count, event.count))
                }
                is AddEvent.Message -> {
                    closeAddSheet()
                    snack(event.text)
                }
                is AddEvent.SortFailed -> snack(
                    resources.getQuantityString(R.plurals.list_sort_failed, event.leftUnsorted, event.friendlyMessage, event.leftUnsorted),
                )
            }
        }
    }
    // After process death the sheet comes back open with a fresh AddViewModel (its draft restored):
    // give it its list without resetting it, since a camera result may already be being read.
    LaunchedEffect(showAdd, state.listId) {
        if (showAdd && state.listId.isNotEmpty()) addVm.attachList(state.listId)
    }

    val actions = remember(vm, addVm) {
        ListActions(
            onToggle = vm::toggle,
            onDelete = vm::delete,
            onToggleSection = vm::toggleSection,
            onClearChecked = vm::clearChecked,
            onOpenEdit = vm::openEdit,
            onAdd = {
                addVm.open(vm.state.value.listId)
                showAdd = true
            },
            onOpenAisles = onOpenAisles,
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
        )
    }

    ListScreen(state = state, actions = actions, snackbarHostState = snackbar, showLists = showLists)

    if (showAdd) {
        AddSheetRoute(
            vm = addVm,
            sheetState = addSheetState,
            onDismiss = {
                showAdd = false
                addVm.close()
            },
            onOpenSettings = {
                closeAddSheet()
                onOpenSettings()
            },
        )
    }
}

/**
 * Stateless List screen: aisles in walking order, each one container (collapsible basket header band
 * owning its rows), check-off rows
 * (tap), edit (long-press or the accessibility action), swipe to delete, "Clear checked", and the
 * Edit / list-switcher sheets when [ListUiState.editing] / [showLists] say so.
 */
@Composable
fun ListScreen(
    state: ListUiState,
    actions: ListActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    showLists: Boolean = false,
) {
    ScreenScaffold(
        modifier = modifier,
        topBar = { ListTopBar(state, actions) },
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (!state.loading && state.total > 0) {
                PicnicButton(stringResource(R.string.list_add), onClick = actions.onAdd, glyph = PicnicGlyph.Add)
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingSparkle()
            }
            state.total == 0 -> EmptyList(actions, Modifier.padding(padding))
            else -> ListContent(state, actions, padding)
        }
    }

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
}

@Composable
private fun ListTopBar(state: ListUiState, actions: ListActions) {
    PicnicTopBar(
        title = state.title.ifEmpty { stringResource(R.string.app_name) },
        subtitle = when {
            state.loading -> null
            state.total > 0 -> stringResource(R.string.list_progress, state.checked, state.total)
            else -> state.storeName
        },
        navigation = { PicnicIconButton(PicnicGlyph.Lists, stringResource(R.string.list_switch_lists), actions.onOpenLists) },
        actions = {
            PicnicIconButton(PicnicGlyph.Aisles, stringResource(R.string.list_open_aisles), actions.onOpenAisles)
            PicnicIconButton(PicnicGlyph.Settings, stringResource(R.string.list_open_settings), actions.onOpenSettings)
        },
        bottom = if (state.total > 0) {
            { TripProgress(state.checked, state.total) }
        } else {
            null
        },
    )
}

@Composable
private fun EmptyList(actions: ListActions, modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            title = stringResource(R.string.list_empty_title),
            body = stringResource(R.string.list_empty_body),
            action = { PicnicButton(stringResource(R.string.list_empty_action), onClick = actions.onAdd, glyph = PicnicGlyph.Add) },
        )
    }
}

@Composable
private fun ListContent(state: ListUiState, actions: ListActions, padding: PaddingValues) {
    val d = PicnicTheme.dimens
    val direction = LocalLayoutDirection.current
    val sections = rememberTickDwell(state.sections)
    val listState = rememberLazyListState()
    val onePixel = with(LocalDensity.current) { 1.toDp() }
    LazyColumn(
        state = listState,
        // The viewport starts under the top bar (not behind it, via content padding) so a pinned
        // section band sits just below the bar. Scrolled rows vanish at the same line either way: the
        // bar repaints the ground behind itself.
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding()),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction) + d.gutter,
            end = padding.calculateEndPadding(direction) + d.gutter,
            top = d.xs,
            bottom = padding.calculateBottomPadding() + FAB_CLEARANCE,
        ),
    ) {
        // Each section is ONE container drawn slice by slice (theme ListPieces.kt): the header is its
        // top band, every row its own keyed item inside, the last row the cap. Slices must touch, so
        // there is no list-wide spacing: a spacer item sits between sections instead.
        // Section bands are sticky headers: once an open section's rows scroll under the top, its band
        // stays pinned (drawn closed, a card over its own rows, backed by the ground from the viewport
        // top across the full width), so a long aisle never becomes rows without a name and no row
        // peeks out above or beside the pinned band. A one-pixel sticky marker closes every section and pushes its band off as
        // the section ends (a collapsed band, followed by its marker straight away, just scrolls).
        // Every header stays a sticky item, collapsed or not, so expanding or collapsing keeps the
        // same composable and the band's closure animates.
        var position = 0 // lazy-list index of the next item (the pinned check needs the header's)
        sections.forEachIndexed { index, section ->
            val open = !section.collapsed && section.items.isNotEmpty()
            if (index > 0) {
                item(key = "gap:${section.id}", contentType = "gap") {
                    Spacer(Modifier.itemMotion(this).fillMaxWidth().height(d.sectionGap))
                }
                position++
            }
            val headerPosition = position
            val header: @Composable LazyItemScope.(pinned: Boolean) -> Unit = { pinned ->
                AisleHeader(
                    title = sectionTitle(section),
                    number = section.number,
                    aisleIndex = section.aisleIndex,
                    basket = section.basket,
                    inBasket = section.inBasket,
                    count = section.count,
                    // The checked section lists the gotten items itself: it is announced as "<n> items".
                    gotten = section.gotten.takeIf { section.kind != SectionKind.CHECKED },
                    collapsed = section.collapsed,
                    cleared = section.cleared,
                    onToggle = { actions.onToggleSection(section.id) },
                    rowsBelow = open && !pinned,
                    // Pinned over its rows: backed by the ground up to the viewport top, so no row
                    // shows above or beside the band (its rounded corners included).
                    pinned = pinned,
                    modifier = Modifier.itemMotion(this),
                )
            }
            stickyHeader(key = "header:${section.id}", contentType = "header") {
                val pinned by remember(listState, headerPosition) {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > headerPosition ||
                            (listState.firstVisibleItemIndex == headerPosition && listState.firstVisibleItemScrollOffset > 0)
                    }
                }
                header(pinned)
            }
            position++
            if (open) {
                // Basket rows get their own keys: ticking or unticking fades a row out of one section and
                // into the other (after the dwell) instead of sliding its slice across the sections between.
                val keyOf: (ItemRowUi) -> String = if (section.kind == SectionKind.CHECKED) { row -> "basket:${row.id}" } else { row -> row.id }
                itemsIndexed(section.items, key = { _, row -> keyOf(row) }, contentType = { _, _ -> "row" }) { i, row ->
                    AisleSectionRow(
                        aisleIndex = section.aisleIndex,
                        first = i == 0,
                        last = i == section.items.lastIndex,
                        modifier = Modifier.itemMotion(this),
                    ) {
                        ItemRow(row, actions)
                    }
                }
                position += section.items.size
            }
            // One pixel tall: the list ignores zero-size items when choosing which header to pin.
            stickyHeader(key = "end:${section.id}", contentType = "end") { Spacer(Modifier.fillMaxWidth().height(onePixel)) }
            position++
            if (section.kind == SectionKind.CHECKED && section.count > 0) {
                item(key = "clear-checked", contentType = "button") {
                    Box(
                        Modifier
                            .itemMotion(this)
                            .fillMaxWidth()
                            .padding(top = d.m),
                        contentAlignment = Alignment.Center,
                    ) {
                        PicnicButton(
                            pluralStringResource(R.plurals.list_clear_checked, section.count, section.count),
                            onClick = actions.onClearChecked,
                            style = PicnicButtonStyle.Secondary,
                            glyph = PicnicGlyph.Delete,
                        )
                    }
                }
                position++
            }
        }
        item(key = "gesture-hint", contentType = "hint") {
            HintText(
                stringResource(R.string.list_gesture_hint),
                Modifier
                    .itemMotion(this)
                    .fillMaxWidth()
                    .padding(top = d.l),
            )
        }
    }
}

/** Where a just-ticked row was shown: its aisle section and position there. */
private class TickDwell(val sectionId: String, val index: Int)

/**
 * [sections] as shown: a row just ticked in an aisle stays there, ticked, for [TICK_DWELL_MS] so its
 * check stamp plays in place (the state already lists it in the basket, which leaves it out
 * meanwhile). Then it fades out of the aisle and into the basket. Unticking it during the dwell,
 * removing it, or its section disappearing ends the dwell at once. Presentation only: the state and
 * its counts are untouched.
 */
@Composable
private fun rememberTickDwell(sections: List<SectionUi>): List<SectionUi> {
    // Unchecked rows of open sections as last shown (plain map: written after composition).
    val shown = remember { HashMap<String, TickDwell>() }
    val dwelling = remember { mutableStateMapOf<String, TickDwell>() }
    val basket = sections.firstOrNull { it.kind == SectionKind.CHECKED }?.items.orEmpty()
    val fresh = basket.mapNotNull { row -> shown[row.id]?.takeIf { row.id !in dwelling }?.let { row.id to it } }.toMap()
    // Only while the row is still in the basket and its aisle is still listed (an emptied section
    // may vanish, Unsorted does): otherwise the row goes straight to where the state puts it.
    val active = (dwelling + fresh).filter { (id, at) -> basket.any { it.id == id } && sections.any { it.id == at.sectionId } }
    SideEffect {
        dwelling.putAll(fresh)
        dwelling.keys.filter { it !in active }.forEach { dwelling.remove(it) }
        shown.clear()
        sections.forEach { s ->
            if (s.kind != SectionKind.CHECKED && !s.collapsed) s.items.forEachIndexed { i, row -> if (!row.checked) shown[row.id] = TickDwell(s.id, i) }
        }
    }
    active.keys.forEach { id ->
        key(id) {
            LaunchedEffect(Unit) {
                delay(TICK_DWELL_MS)
                dwelling.remove(id)
            }
        }
    }
    if (active.isEmpty()) return sections
    val rows = basket.associateBy { it.id }
    return sections.map { s ->
        if (s.kind == SectionKind.CHECKED) {
            s.copy(items = s.items.filterNot { it.id in active })
        } else {
            val mine = active.entries.filter { it.value.sectionId == s.id }.sortedBy { it.value.index }
            if (mine.isEmpty()) s else s.copy(items = s.items.toMutableList().apply { mine.forEach { (id, at) -> add(at.index.coerceAtMost(size), rows.getValue(id)) } })
        }
    }
}

/** Item placement/fade animations from [scope]; none with reduced motion. */
@Composable
private fun Modifier.itemMotion(scope: LazyItemScope): Modifier {
    val motion = PicnicTheme.motion
    return with(scope) {
        if (motion.reduced) {
            this@itemMotion.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
        } else {
            this@itemMotion.animateItem(
                fadeInSpec = tween(motion.shortMillis),
                placementSpec = tween(motion.mediumMillis),
                fadeOutSpec = tween(motion.shortMillis),
            )
        }
    }
}

@Composable
private fun sectionTitle(section: SectionUi): String = when (section.kind) {
    SectionKind.UNSORTED -> stringResource(R.string.list_section_unsorted)
    SectionKind.CHECKED -> stringResource(R.string.list_section_checked)
    SectionKind.AISLE -> section.title
}

/**
 * One item: tap toggles, long-press opens the editor, swipe either way removes (Undo in the
 * snackbar). TalkBack gets the checked state and "Edit" / "Remove" actions (swipes aren't accessible).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(row: ItemRowUi, actions: ListActions, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val editLabel = stringResource(R.string.list_row_edit)
    val removeLabel = stringResource(R.string.list_row_remove)
    val stateText = stringResource(if (row.checked) R.string.list_row_checked else R.string.list_row_unchecked)
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackground(dismissState) },
        modifier = modifier,
        onDismiss = {
            actions.onDelete(row.id)
            // If the delete didn't happen (save failed), bring the card back instead of leaving a gap.
            scope.launch {
                delay(UNDISMISS_DELAY_MS)
                dismissState.reset()
            }
        },
    ) {
        ItemRowCard(
            name = row.name,
            detail = row.detail,
            icon = row.icon,
            checked = row.checked,
            drawing = row.drawing,
            // Press feedback: the row takes its section's tint at once (it has no card edge to move).
            pressed = pressed,
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Checkbox,
                    onLongClickLabel = editLabel,
                    onLongClick = { actions.onOpenEdit(row.id) },
                    onClick = { actions.onToggle(row.id) },
                )
                .semantics {
                    stateDescription = stateText
                    customActions = listOf(
                        CustomAccessibilityAction(editLabel) {
                            actions.onOpenEdit(row.id)
                            true
                        },
                        CustomAccessibilityAction(removeLabel) {
                            actions.onDelete(row.id)
                            true
                        },
                    )
                },
        )
    }
}

/**
 * Revealed under a swiped row: a "Remove" strip on the side the swipe started from. It fills the
 * row's slot inside the section (AisleSectionRow clips it to the inner surface).
 */
@Composable
private fun RowScope.SwipeBackground(state: SwipeToDismissBoxState) {
    val direction = state.dismissDirection
    if (direction == SwipeToDismissBoxValue.Settled) return
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    PicnicSurface(Modifier.fillMaxSize(), shape = RectangleShape, color = c.error, outlined = false, shadow = false) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = d.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (direction == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
        ) {
            Glyph(PicnicGlyph.Delete, contentDescription = null, tint = c.onError)
            Text(
                stringResource(R.string.list_row_remove),
                style = PicnicTheme.type.label,
                color = c.onError,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = d.s),
            )
        }
    }
}

/** Room under the last row so the floating Add button never hides it. */
private val FAB_CLEARANCE = 96.dp

private const val UNDISMISS_DELAY_MS = 700L

/** How long a just-ticked row stays in its aisle (the check stamp settles well within it). */
private const val TICK_DWELL_MS = 600L

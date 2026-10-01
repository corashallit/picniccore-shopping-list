package earth.cora.picnic.ui.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
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
 * Stateless List screen: aisles in walking order with collapsible basket headers, check-off rows
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction) + d.gutter,
            end = padding.calculateEndPadding(direction) + d.gutter,
            top = padding.calculateTopPadding() + d.xs,
            bottom = padding.calculateBottomPadding() + FAB_CLEARANCE,
        ),
        verticalArrangement = Arrangement.spacedBy(d.rowGap),
    ) {
        state.sections.forEachIndexed { index, section ->
            item(key = "header:${section.id}", contentType = "header") {
                AisleHeader(
                    title = sectionTitle(section),
                    number = section.number,
                    aisleIndex = section.aisleIndex,
                    basket = section.basket,
                    miniIcons = section.miniIcons,
                    count = section.count,
                    collapsed = section.collapsed,
                    cleared = section.cleared,
                    onToggle = { actions.onToggleSection(section.id) },
                    modifier = Modifier
                        .itemMotion(this)
                        .padding(top = if (index > 0) d.sectionGap - d.rowGap else 0.dp),
                )
            }
            if (!section.collapsed) {
                items(section.items, key = { it.id }, contentType = { "row" }) { row ->
                    ItemRow(row, actions, Modifier.itemMotion(this))
                }
            }
            if (section.kind == SectionKind.CHECKED && section.count > 0) {
                item(key = "clear-checked", contentType = "button") {
                    Box(Modifier.itemMotion(this).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PicnicButton(
                            pluralStringResource(R.plurals.list_clear_checked, section.count, section.count),
                            onClick = actions.onClearChecked,
                            style = PicnicButtonStyle.Secondary,
                            glyph = PicnicGlyph.Delete,
                        )
                    }
                }
            }
        }
        item(key = "gesture-hint", contentType = "hint") {
            HintText(
                stringResource(R.string.list_gesture_hint),
                Modifier
                    .itemMotion(this)
                    .fillMaxWidth()
                    .padding(top = d.s),
            )
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
    val motion = PicnicTheme.motion
    val squish by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = if (motion.reduced) snap() else tween(motion.shortMillis),
        label = "rowPress",
    )
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
            modifier = Modifier
                .graphicsLayer {
                    scaleX = squish
                    scaleY = squish
                }
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

/** Revealed under a swiped row: a "Remove" strip on the side the swipe started from. */
@Composable
private fun RowScope.SwipeBackground(state: SwipeToDismissBoxState) {
    val direction = state.dismissDirection
    if (direction == SwipeToDismissBoxValue.Settled) return
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    PicnicSurface(Modifier.fillMaxSize(), shape = PicnicTheme.shapes.row, color = c.error, shadow = false) {
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

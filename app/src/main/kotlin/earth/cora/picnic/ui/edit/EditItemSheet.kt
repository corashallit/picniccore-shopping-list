package earth.cora.picnic.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.common.HintText
import earth.cora.picnic.ui.common.PicnicSheet
import earth.cora.picnic.ui.common.SheetTitle
import earth.cora.picnic.ui.common.storeAisleNumber
import earth.cora.picnic.ui.list.EditItemUi
import earth.cora.picnic.ui.theme.AisleMedallion
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalIconCatalog
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicChip
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.SectionDivider
import kotlinx.coroutines.launch

/** The two pages of the Edit sheet. */
enum class EditPage { Details, Icons }

/**
 * Edit sheet: rename, amount and note (saved with Save, or when the sheet is swiped away with
 * changes), "Move to aisle" (applies at once and becomes a learned correction), "Change icon"
 * (applies at once and becomes a learned icon override), "Draw a new icon" and "Remove".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemSheet(
    state: EditItemUi,
    onSave: (name: String, quantity: String?, note: String?) -> Unit,
    onClose: () -> Unit,
    onMoveToAisle: (aisleId: String?) -> Unit,
    onSetIcon: (key: String) -> Unit,
    onRedraw: () -> Unit,
    onDelete: () -> Unit,
    initialPage: EditPage = EditPage.Details,
) {
    val item = state.item
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var quantity by rememberSaveable(item.id) { mutableStateOf(item.quantity.orEmpty()) }
    var note by rememberSaveable(item.id) { mutableStateOf(item.note.orEmpty()) }
    var page by rememberSaveable(item.id) { mutableStateOf(initialPage) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val changed = name.trim() != item.name || quantity.trim() != item.quantity.orEmpty() || note.trim() != item.note.orEmpty()

    fun hideThen(block: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { block() }
    }

    PicnicSheet(
        // Swiping the sheet away keeps typed changes rather than silently dropping them.
        onDismissRequest = { if (changed) onSave(name, quantity, note) else onClose() },
        sheetState = sheetState,
    ) {
        BackHandler(enabled = page == EditPage.Icons) { page = EditPage.Details }
        when (page) {
            EditPage.Details -> EditDetails(
                state = state,
                name = name,
                quantity = quantity,
                note = note,
                onNameChange = { name = it },
                onQuantityChange = { quantity = it },
                onNoteChange = { note = it },
                onMoveToAisle = onMoveToAisle,
                onSetIcon = onSetIcon,
                onShowAllIcons = { page = EditPage.Icons },
                onRedraw = onRedraw,
                onSave = { hideThen { onSave(name, quantity, note) } },
                onDelete = { hideThen(onDelete) },
            )
            EditPage.Icons -> IconPicker(
                state = state,
                onPick = { key ->
                    onSetIcon(key)
                    page = EditPage.Details
                },
                onBack = { page = EditPage.Details },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditDetails(
    state: EditItemUi,
    name: String,
    quantity: String,
    note: String,
    onNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onMoveToAisle: (String?) -> Unit,
    onSetIcon: (String) -> Unit,
    onShowAllIcons: () -> Unit,
    onRedraw: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = PicnicTheme.dimens
    val catalog = LocalIconCatalog.current
    val item = state.item
    val current = state.currentAisleId
    val quickPicks = remember(item.icon, state.iconChoices) { quickPicks(item.icon, state.iconChoices, catalog) }

    Column(modifier.verticalScroll(rememberScrollState())) {
        // Save sits in the title row: the aisle chips below can be long.
        SheetTitle(stringResource(R.string.edit_title)) {
            PicnicButton(stringResource(R.string.edit_save), onClick = onSave, enabled = name.isNotBlank(), glyph = PicnicGlyph.Check)
        }
        Column(
            Modifier.padding(start = d.gutter, end = d.gutter, bottom = d.l),
            verticalArrangement = Arrangement.spacedBy(d.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.m)) {
                ItemIcon(state.icon, contentDescription = null, size = 64.dp, drawing = state.drawing, lifted = true)
                PicnicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.edit_name),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(d.s)) {
                PicnicTextField(
                    value = quantity,
                    onValueChange = onQuantityChange,
                    label = stringResource(R.string.edit_quantity),
                    placeholder = stringResource(R.string.edit_quantity_placeholder),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(0.42f),
                )
                PicnicTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = stringResource(R.string.edit_note),
                    placeholder = stringResource(R.string.edit_note_placeholder),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(0.58f),
                )
            }

            SectionDivider()
            SubTitle(stringResource(R.string.edit_aisle))
            HintText(stringResource(R.string.edit_aisle_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(d.xs)) {
                PicnicChip(
                    stringResource(R.string.edit_aisle_unsorted),
                    selected = current == null,
                    onClick = { if (current != null) onMoveToAisle(null) },
                )
                state.aisles.forEach { aisle ->
                    PicnicChip(
                        aisle.name,
                        selected = aisle.id == current,
                        onClick = { if (aisle.id != current) onMoveToAisle(aisle.id) },
                        leading = { AisleMedallion(storeAisleNumber(aisle.name), aisle.position - 1, size = 20.dp) },
                    )
                }
            }

            SectionDivider()
            SubTitle(stringResource(R.string.edit_icon))
            if (quickPicks.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(d.xs), contentPadding = PaddingValues(vertical = d.xxs)) {
                    items(quickPicks, key = { it }) { key ->
                        IconChoice(
                            key,
                            catalog[key]?.label ?: key,
                            selected = key == item.icon && item.customIconId == null,
                            modifier = Modifier.width(LABELLED_CELL),
                        ) { onSetIcon(key) }
                    }
                }
            }
            PicnicButton(
                stringResource(R.string.edit_all_icons),
                onClick = onShowAllIcons,
                style = PicnicButtonStyle.Secondary,
                glyph = PicnicGlyph.Gallery,
            )
            if (state.canDraw) {
                PicnicButton(
                    stringResource(if (state.drawing) R.string.edit_drawing else R.string.edit_draw),
                    onClick = onRedraw,
                    style = PicnicButtonStyle.Secondary,
                    enabled = !state.drawing,
                    glyph = PicnicGlyph.Sparkle,
                )
                HintText(stringResource(R.string.edit_draw_hint, item.name))
            }

            SectionDivider()
            PicnicButton(stringResource(R.string.edit_remove), onClick = onDelete, style = PicnicButtonStyle.Text, glyph = PicnicGlyph.Delete)
        }
    }
}

/** Full catalog grid, the item's category first, with category headings. */
@Composable
internal fun IconPicker(state: EditItemUi, onPick: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val d = PicnicTheme.dimens
    val catalog = LocalIconCatalog.current
    val groups = remember(state.iconChoices) {
        state.iconChoices.groupBy { catalog[it]?.category.orEmpty() }.toList()
    }
    val labels = remember(catalog) { catalog.categories.associate { it.key to it.label } }
    Column(modifier.fillMaxHeight()) {
        SheetTitle(stringResource(R.string.edit_icons_title, state.item.name)) {
            PicnicIconButton(PicnicGlyph.Back, stringResource(R.string.screens_back), onBack)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(LABELLED_CELL),
            contentPadding = PaddingValues(start = d.gutter, end = d.gutter, bottom = d.xl),
            horizontalArrangement = Arrangement.spacedBy(d.xs),
            verticalArrangement = Arrangement.spacedBy(d.s),
            modifier = Modifier.weight(1f),
        ) {
            groups.forEach { (category, keys) ->
                item(key = "category:$category", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                    Text(
                        labels[category] ?: category,
                        style = PicnicTheme.type.label,
                        color = PicnicTheme.colors.inkSoft,
                        modifier = Modifier
                            .padding(top = d.s)
                            .semantics { heading() },
                    )
                }
                items(keys, key = { it }, contentType = { "icon" }) { key ->
                    IconChoice(
                        key,
                        catalog[key]?.label ?: key,
                        selected = key == state.item.icon && state.item.customIconId == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { onPick(key) }
                }
            }
        }
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        style = PicnicTheme.type.aisleTitle,
        color = PicnicTheme.colors.ink,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * One pickable catalog icon: a 56 dp tile (the selected one on a highlight tile) with its catalog
 * label underneath, so icons that look alike (or share placeholder art) can still be told apart.
 */
@Composable
private fun IconChoice(
    key: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = PicnicTheme.colors
    Column(
        modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PicnicSurface(
            Modifier.size(ICON_CELL),
            shape = PicnicTheme.shapes.row,
            color = if (selected) c.highlight else c.surfaceSunken,
            outlined = selected,
            shadow = false,
        ) {
            ItemIcon(IconRef(key), contentDescription = null, modifier = Modifier.align(Alignment.Center), size = 40.dp)
        }
        Text(
            label,
            style = PicnicTheme.type.caption,
            color = if (selected) c.ink else c.inkSoft,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Up to 12 icons of the item's own category (its current icon first); none for the paper bag. */
private fun quickPicks(current: String, choices: List<String>, catalog: IconCatalog): List<String> {
    if (current == IconCatalog.DEFAULT_ICON) return emptyList()
    val category = catalog[current]?.category ?: return emptyList()
    val same = choices.filter { catalog[it]?.category == category }
    return (listOf(current) + same.filterNot { it == current }).take(MAX_QUICK_PICKS)
}

private val ICON_CELL = 56.dp

/** Icon tile plus room for a two-line label. */
private val LABELLED_CELL = 76.dp
private const val MAX_QUICK_PICKS = 12

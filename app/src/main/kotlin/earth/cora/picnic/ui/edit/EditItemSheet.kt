package earth.cora.picnic.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AisleStyle
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.displayName
import earth.cora.picnic.ui.a11y.spaceToggles
import earth.cora.picnic.ui.common.HankoTextButton
import earth.cora.picnic.ui.common.MagicAppearBox
import earth.cora.picnic.ui.common.NoMaterialPress
import earth.cora.picnic.ui.common.highlighterPress
import earth.cora.picnic.ui.list.EditItemUi
import earth.cora.picnic.ui.theme.AisleChip
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.LoaderKind
import earth.cora.picnic.ui.theme.LocalIconCatalog
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicLoader
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.SectionDivider
import earth.cora.picnic.ui.theme.StickerSpot
import earth.cora.picnic.ui.theme.hardShadow

/** The two pages of the Edit sheet's sticker grid: the item's own category, or every sticker. */
enum class EditPage { Details, Icons }

/**
 * The Edit item sheet (docs/DESIGN_STICKER.md §6.3, checklist 217, 329): a letter-set sheet with the 120 dp hero sticker
 * (lifted, hs2), the favourite heart toggle and the "In my pantry" jar toggle beside it (both Role.Switch; they apply at
 * once), ruled fields Name / Amount / Note (saved with Save, or when the sheet is dismissed with changes), "Move to aisle"
 * (aisle chips with their number circles, the current one selected; applies at once and is learned), "Change sticker"
 * (40 dp stickers in kiss-cut cells, the item's category first, the chosen one ringed in lemon; applies at once and is
 * learned), "Draw a new sticker" (with a key; the ladybug ring while it draws), Save and Delete (hanko).
 *
 * Stateless: [favourite], [inPantry] and [aisleStyles] come from the store. The host places it in a full-size box (it is
 * an overlay with its own scrim, §4.16). [EditItemSheet] (what the List screen mounts) and [EditItemRoute] read them
 * from the store and handle the toggles; tests and goldens draw this one.
 */
@Composable
fun EditItemSheetContent(
    state: EditItemUi,
    onSave: (name: String, quantity: String?, note: String?) -> Unit,
    onClose: () -> Unit,
    onMoveToAisle: (aisleId: String?) -> Unit,
    onSetIcon: (key: String) -> Unit,
    onRedraw: () -> Unit,
    onDelete: () -> Unit,
    initialPage: EditPage = EditPage.Details,
    favourite: Boolean = false,
    inPantry: Boolean = false,
    onFavouriteChange: (Boolean) -> Unit = {},
    onPantryChange: (Boolean) -> Unit = {},
    aisleStyles: Map<String, AisleStyle> = emptyMap(),
    visible: Boolean = true,
    /**
     * [favourite] and [inPantry] are the stored values (false while the store loads): when it turns true, the toggles take
     * them as they are, without the magic appear that marks switching one on.
     */
    togglesReady: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val item = state.item
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var quantity by rememberSaveable(item.id) { mutableStateOf(item.quantity.orEmpty()) }
    var note by rememberSaveable(item.id) { mutableStateOf(item.note.orEmpty()) }
    var page by rememberSaveable(item.id) { mutableStateOf(initialPage) }
    val changed = name.trim() != item.name || quantity.trim() != item.quantity.orEmpty() || note.trim() != item.note.orEmpty()

    // No Material ripple anywhere in the sheet, kit chips included (§0.2).
    NoMaterialPress {
        LetterSetSheet(
            visible = visible,
            // Dismissing the sheet keeps typed changes rather than silently dropping them.
            onDismissRequest = { if (changed && name.isNotBlank()) onSave(name, quantity, note) else onClose() },
            modifier = modifier.imePadding(),
            footer = {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HankoTextButton(stringResource(R.string.edit_delete), onClick = onDelete, glyph = PicnicGlyph.Delete)
                    PicnicButton(stringResource(R.string.edit_save), onClick = { onSave(name, quantity, note) }, enabled = changed && name.isNotBlank())
                }
            },
        ) {
            EditDetails(
                state = state,
                name = name,
                quantity = quantity,
                note = note,
                onNameChange = { name = it },
                onQuantityChange = { quantity = it },
                onNoteChange = { note = it },
                onMoveToAisle = onMoveToAisle,
                onSetIcon = onSetIcon,
                allStickers = page == EditPage.Icons,
                onAllStickersChange = { page = if (it) EditPage.Icons else EditPage.Details },
                onRedraw = onRedraw,
                favourite = favourite,
                inPantry = inPantry,
                onFavouriteChange = onFavouriteChange,
                onPantryChange = onPantryChange,
                aisleStyles = aisleStyles,
                togglesReady = togglesReady,
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
    allStickers: Boolean,
    onAllStickersChange: (Boolean) -> Unit,
    onRedraw: () -> Unit,
    favourite: Boolean,
    inPantry: Boolean,
    onFavouriteChange: (Boolean) -> Unit,
    onPantryChange: (Boolean) -> Unit,
    aisleStyles: Map<String, AisleStyle>,
    togglesReady: Boolean = true,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val item = state.item
    val current = state.currentAisleId
    val heroLabel = stringResource(R.string.edit_hero, item.name)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Hero + the two toggles: side by side, or the toggles under the hero when large text needs the width.
        val hero: @Composable () -> Unit = {
            Box(Modifier.size(128.dp).semantics { contentDescription = heroLabel }, contentAlignment = Alignment.Center) {
                ItemIcon(state.icon, contentDescription = null, size = 120.dp, drawing = state.drawing, lifted = true)
            }
        }
        val toggles: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StickerToggle(
                    label = stringResource(R.string.edit_favourite),
                    checked = favourite,
                    onCheckedChange = onFavouriteChange,
                    offGlyph = PicnicGlyph.Heart,
                    ready = togglesReady,
                ) { DecorSticker("heart", size = 30.dp) }
                StickerToggle(
                    label = stringResource(R.string.edit_in_pantry),
                    checked = inPantry,
                    onCheckedChange = onPantryChange,
                    offGlyph = PicnicGlyph.Jar,
                    ready = togglesReady,
                ) { ItemIcon(IconRef(PANTRY_JAR), contentDescription = null, size = 30.dp) }
            }
        }
        if (LocalDensity.current.fontScale >= LARGE_TEXT) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hero()
                toggles(Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                hero()
                toggles(Modifier.weight(1f))
            }
        }

        PicnicTextField(
            value = name,
            onValueChange = onNameChange,
            label = stringResource(R.string.edit_name),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        )
        // Amount and Note side by side; with large text one under the other, so the hints stay whole.
        val amount: @Composable (Modifier) -> Unit = { m ->
            PicnicTextField(
                value = quantity,
                onValueChange = onQuantityChange,
                label = stringResource(R.string.edit_quantity),
                placeholder = stringResource(R.string.edit_quantity_placeholder),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = m,
            )
        }
        val noteField: @Composable (Modifier) -> Unit = { m ->
            PicnicTextField(
                value = note,
                onValueChange = onNoteChange,
                label = stringResource(R.string.edit_note),
                placeholder = stringResource(R.string.edit_note_placeholder),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = m,
            )
        }
        if (LocalDensity.current.fontScale >= FIELDS_STACK) {
            amount(Modifier.fillMaxWidth())
            noteField(Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                amount(Modifier.weight(0.42f))
                noteField(Modifier.weight(0.58f))
            }
        }

        SectionDivider()
        SubTitle(stringResource(R.string.edit_aisle))
        Text(stringResource(R.string.edit_aisle_hint), style = t.caption.copy(color = c.inkSoft))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AisleChip(
                number = null,
                name = stringResource(R.string.edit_everything_else),
                look = AisleLooks.everythingElse,
                selected = current == null,
                onClick = { if (current != null) onMoveToAisle(null) },
            )
            state.aisles.forEach { aisle ->
                AisleChip(
                    number = aisle.position,
                    name = aisle.displayName(),
                    look = AisleLooks.of(aisle.position, aisleStyles[aisle.id]),
                    selected = aisle.id == current,
                    onClick = { if (aisle.id != current) onMoveToAisle(aisle.id) },
                )
            }
        }

        SectionDivider()
        SubTitle(stringResource(R.string.edit_sticker))
        StickerGrid(state, all = allStickers, onPick = onSetIcon)
        PicnicButton(
            stringResource(if (allStickers) R.string.edit_fewer_stickers else R.string.edit_more_stickers),
            onClick = { onAllStickersChange(!allStickers) },
            style = PicnicButtonStyle.Text,
            glyph = if (allStickers) PicnicGlyph.Collapse else PicnicGlyph.Expand,
        )

        SectionDivider()
        when {
            !state.canDraw -> Text(stringResource(R.string.edit_draw_no_key), style = t.caption.copy(color = c.inkSoft))
            // The ladybug walks its ring round the sticker being drawn (the current one waits inside, sparkling).
            state.drawing -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                PicnicLoader(
                    LoaderKind.LadybugRing,
                    label = stringResource(R.string.edit_drawing),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                    ItemIcon(state.icon, contentDescription = null, size = 56.dp, drawing = true)
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PicnicButton(
                    stringResource(R.string.edit_draw),
                    onClick = onRedraw,
                    style = PicnicButtonStyle.Secondary,
                    glyph = PicnicGlyph.Sparkle,
                )
                Text(stringResource(R.string.edit_draw_hint), style = t.caption.copy(color = c.inkSoft))
            }
        }
        // Room for the last line to scroll clear of the footer's fade.
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * A sticker toggle (§6.3): a dashed sticker spot with a line [offGlyph] when off; switched on, the sticker is pressed
 * onto the spot with the magic appear. The row (spot + [label]) is one Role.Switch node, ≥48 dp tall.
 */
@Composable
private fun StickerToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    offGlyph: PicnicGlyph,
    ready: Boolean = true,
    on: @Composable () -> Unit,
) {
    val c = PicnicTheme.colors
    // The magic appear plays when she switches it on, not when the sheet opens with it on (nor when the stored value
    // arrives a frame after the sheet).
    var played by remember(ready) { mutableStateOf(checked) }
    LaunchedEffect(checked) { if (!checked) played = false }
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .spaceToggles(checked, onValueChange = onCheckedChange)
            .toggleable(value = checked, interactionSource = null, indication = highlighterPress(), role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            StickerSpot(size = 34.dp)
            if (checked) {
                MagicAppearBox(key = checked, play = !played, onPlayed = { played = true }) { on() }
            } else {
                Glyph(offGlyph, contentDescription = null, tint = c.inkSoft, size = 18.dp)
            }
        }
        // The label merges into the switch node: TalkBack reads "Favourite, switch, on".
        Text(label, style = PicnicTheme.type.fieldValue.copy(color = c.ink))
    }
}

/**
 * "Change sticker" (§6.3): the item's own category first; [all] adds every other category. The chosen sticker (a catalog
 * key, not a drawn one) has the lemon ring.
 */
@Composable
private fun StickerGrid(state: EditItemUi, all: Boolean, onPick: (String) -> Unit) {
    val catalog = LocalIconCatalog.current
    val keys = remember(state.iconChoices, state.item.icon, all) { stickerChoices(state, catalog, all) }
    StickerPicker(keys, selected = state.item.icon.takeIf { state.item.customIconId == null }, onPick = onPick)
}

/** The keys the grid shows: the item's category (its current sticker first), then, with [all], every other key. */
internal fun stickerChoices(state: EditItemUi, catalog: IconCatalog, all: Boolean): List<String> {
    val category = catalog[state.item.icon]?.category?.takeIf { state.item.icon != IconCatalog.DEFAULT_ICON }
    val own = if (category == null) state.iconChoices.take(MIN_GRID) else state.iconChoices.filter { catalog[it]?.category == category }
    val first = (listOfNotNull(state.item.icon.takeIf { it in own }) + own).distinct()
    return if (all) (first + state.iconChoices).distinct() else first
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = PicnicTheme.type.smallTitle.copy(color = PicnicTheme.colors.ink), modifier = Modifier.semantics { heading() })
}

/** The jar sticker of the "In my pantry" toggle. */
internal const val PANTRY_JAR: String = "spice_jar"

/** From this font scale the toggles stand under the hero, so their labels keep whole words. */
private const val LARGE_TEXT = 1.5f

/** From this font scale Amount and Note stand one under the other (side by side, Note's hint would be cut at 1.3). */
private const val FIELDS_STACK = 1.15f

/** Stickers shown for an item with no category (the paper bag): the first ones of the catalog. */
internal const val MIN_GRID = 15

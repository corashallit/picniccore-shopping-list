package earth.cora.picnic.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.ui.common.DashedAddSpot
import earth.cora.picnic.ui.common.NoMaterialPress
import earth.cora.picnic.ui.common.highlighterPress
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.LcdChip
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.OnPaper
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.hardShadow

/*
 * The list switcher (docs/DESIGN_STICKER.md §6.4, checklist 144, 219). Owner: WP8 (ListSwitcherTest). The List screen
 * (WP5) hosts it in a full-size box: it is a letter-set sheet with its own scrim (§4.16).
 */

/**
 * "Your lists" (§6.4): one kiss-cut label per list with an LCD chip of what is left, the current list selected (lemon,
 * with a star); tap a label to switch; the pencil renames in place (the field takes focus with the old name selected);
 * the bin asks first on a small confirm sheet, which says "Its 9 items go with it." and adds "Its sticker book goes into
 * your album." when that list has stickers in its book ([ListSummary.bookStickers]) — the book is then filed before the
 * list goes (the host's deleteList, TripFiling). The dashed "New list" spot starts a new list (its field takes focus).
 * The last list can't be deleted.
 */
@Composable
fun ListSwitcherSheet(
    lists: List<ListSummary>,
    onDismiss: () -> Unit,
    onSwitch: (listId: String) -> Unit,
    onNew: (title: String) -> Unit,
    onRename: (listId: String, title: String) -> Unit,
    onDelete: (listId: String) -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    initialConfirmDelete: String? = null,
) {
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var confirmDeleteId by rememberSaveable { mutableStateOf(initialConfirmDelete) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var newTitle by rememberSaveable { mutableStateOf("") }

    // No Material ripple anywhere in the two sheets (§0.2).
    NoMaterialPress {
        LetterSetSheet(visible = visible, onDismissRequest = onDismiss, modifier = modifier.imePadding()) {
            Text(
                stringResource(R.string.lists_title),
                style = PicnicTheme.type.cardTitle.copy(color = PicnicTheme.colors.ink),
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                lists.forEach { summary ->
                    if (summary.id == renamingId) {
                        RenameRow(
                            text = renameText,
                            onTextChange = { renameText = it },
                            onDone = {
                                onRename(summary.id, renameText.text)
                                renamingId = null
                            },
                            onCancel = { renamingId = null },
                        )
                    } else {
                        ListLabel(
                            summary = summary,
                            canDelete = lists.size > 1,
                            onClick = { onSwitch(summary.id) },
                            onRename = {
                                // The whole old name selected: typing replaces it, a tap puts the cursor in it.
                                renameText = TextFieldValue(summary.title, selection = TextRange(0, summary.title.length))
                                renamingId = summary.id
                            },
                            onDelete = {
                                renamingId = null
                                confirmDeleteId = summary.id
                            },
                        )
                    }
                }
                if (adding) {
                    NewListRow(
                        text = newTitle,
                        onTextChange = { newTitle = it },
                        onDone = {
                            if (newTitle.isNotBlank()) onNew(newTitle.trim())
                            newTitle = ""
                            adding = false
                        },
                        onCancel = { adding = false },
                    )
                } else {
                    DashedAddSpot(stringResource(R.string.lists_new_label), onClick = { adding = true }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        val confirming = lists.firstOrNull { it.id == confirmDeleteId }
        LetterSetSheet(
            visible = confirming != null,
            onDismissRequest = { confirmDeleteId = null },
            footer = {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PicnicButton(stringResource(R.string.lists_delete_no), onClick = { confirmDeleteId = null }, style = PicnicButtonStyle.Text)
                    PicnicButton(
                        stringResource(R.string.lists_delete_yes),
                        onClick = {
                            confirmDeleteId?.let(onDelete)
                            confirmDeleteId = null
                        },
                        glyph = PicnicGlyph.Delete,
                    )
                }
            },
        ) {
            if (confirming != null) {
                Text(
                    stringResource(R.string.lists_delete_title, confirming.title),
                    style = PicnicTheme.type.cardTitle.copy(color = PicnicTheme.colors.ink),
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(10.dp))
                // What goes with it, so a delete is never silent about the list's items (§6.4).
                val body = listOfNotNull(
                    if (confirming.items > 0) pluralStringResource(R.plurals.list_delete_items, confirming.items, confirming.items) else null,
                    if (confirming.bookStickers > 0) stringResource(R.string.lists_delete_book) else null,
                )
                if (body.isNotEmpty()) {
                    Text(body.joinToString(" "), style = PicnicTheme.type.body.copy(color = PicnicTheme.colors.ink))
                }
                // Room above the sticky footer's fade, so the last line is never washed out.
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/**
 * One list's kiss-cut label: backing paper (lemon when current, with a star), title, what is left on an LCD, pencil, bin.
 * The count is said once: the LCD while something is left (TalkBack hears "9 to get"), "All picked up" in its place
 * when nothing is.
 */
@Composable
private fun ListLabel(summary: ListSummary, canDelete: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shape = PicnicTheme.shapes.card
    val paper = if (summary.current) Families.lemon.pastel else c.backing
    val spokenLeft = pluralStringResource(R.plurals.lists_remaining, summary.remaining, summary.remaining)
    Row(
        Modifier
            .fillMaxWidth()
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(3.dp)
            .clip(shape)
            .background(paper)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label's content stands on its own paper (§2.4.1): the lemon label is light in both themes (ballpoint words,
        // white-faced pencil and bin, the highlighter band), the backing is Glow's violet (white words, navy buttons).
        OnPaper(paper) {
            val ink = PicnicTheme.colors.ink
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clickable(interactionSource = null, indication = highlighterPress(), role = Role.Button, onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        selected = summary.current
                        if (summary.remaining > 0) stateDescription = spokenLeft
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (summary.current) DecorSticker("star", size = 16.dp, shadow = false)
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(summary.title, style = t.itemName.copy(color = ink))
                    if (summary.remaining == 0) Text(stringResource(R.string.lists_all_done), style = t.caption.copy(color = ink))
                }
                if (summary.remaining > 0) {
                    Box(Modifier.clearAndSetSemantics { }) {
                        LcdChip(LcdState(mode = LcdMode.Text, text = summary.remaining.toString()), pitch = 1.6.dp)
                    }
                }
            }
            // The pencil and bin stand on the label's own paper, not the sheet's: their faces have an edge there already,
            // so no rim (§17).
            PicnicIconButton(PicnicGlyph.Edit, stringResource(R.string.lists_rename, summary.title), onRename)
            PicnicIconButton(PicnicGlyph.Delete, stringResource(R.string.lists_delete, summary.title), onDelete, enabled = canDelete)
        }
    }
}

/** The field takes focus as it appears, so the keyboard comes up with it (as the aisle editor's inline edit does). */
@Composable
private fun rememberFocusOnShow(): FocusRequester {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
    }
    return focus
}

@Composable
private fun RenameRow(text: TextFieldValue, onTextChange: (TextFieldValue) -> Unit, onDone: () -> Unit, onCancel: () -> Unit) {
    val focus = rememberFocusOnShow()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        PicnicTextField(
            value = text,
            onValueChange = onTextChange,
            label = stringResource(R.string.lists_rename_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.text.isNotBlank()) onDone() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        PicnicIconButton(PicnicGlyph.Check, stringResource(R.string.lists_rename_save), onDone, enabled = text.text.isNotBlank())
        PicnicIconButton(PicnicGlyph.Close, stringResource(R.string.screens_cancel), onCancel)
    }
}

@Composable
private fun NewListRow(text: String, onTextChange: (String) -> Unit, onDone: () -> Unit, onCancel: () -> Unit) {
    val focus = rememberFocusOnShow()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        PicnicTextField(
            value = text,
            onValueChange = onTextChange,
            label = stringResource(R.string.lists_new_label),
            placeholder = stringResource(R.string.lists_new_placeholder),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        PicnicIconButton(PicnicGlyph.Add, stringResource(R.string.lists_new_action), onDone, enabled = text.isNotBlank(), emphasized = true)
        PicnicIconButton(PicnicGlyph.Close, stringResource(R.string.screens_cancel), onCancel)
    }
}

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import earth.cora.picnic.ui.theme.LocalPaperColor
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
 * with a star); tap a label to switch; the pencil renames in place; the bin asks first on a small confirm sheet, which
 * adds "Its sticker book goes into your album." when that list has stickers in its book ([ListSummary.bookStickers]) — the
 * book is then filed before the list goes (the host's deleteList, TripFiling). The dashed
 * "New list" spot starts a new list. The last list can't be deleted.
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
    var renameText by rememberSaveable { mutableStateOf("") }
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
                                onRename(summary.id, renameText)
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
                                renameText = summary.title
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
                if (confirming.bookStickers > 0) {
                    Text(stringResource(R.string.lists_delete_book), style = PicnicTheme.type.body.copy(color = PicnicTheme.colors.ink))
                }
                // Room above the sticky footer's fade, so the last line is never washed out.
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/** One list's kiss-cut label: backing paper (lemon when current, with a star), title, what is left on an LCD, pencil, bin. */
@Composable
private fun ListLabel(summary: ListSummary, canDelete: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shape = PicnicTheme.shapes.card
    val paper = if (summary.current) Families.lemon.pastel else c.backing
    val left = if (summary.remaining == 0) {
        stringResource(R.string.lists_all_done)
    } else {
        pluralStringResource(R.plurals.lists_remaining, summary.remaining, summary.remaining)
    }
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
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(interactionSource = null, indication = highlighterPress(), role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { selected = summary.current },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (summary.current) DecorSticker("star", size = 16.dp, shadow = false)
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                // Text on backing / lemon paper is ballpoint in every variant (§2.4: pastels never darken).
                Text(summary.title, style = t.itemName.copy(color = c.ballpoint))
                Text(left, style = t.caption.copy(color = c.ballpoint))
            }
            Box(Modifier.clearAndSetSemantics { }) {
                LcdChip(LcdState(mode = LcdMode.Text, text = summary.remaining.toString()), pitch = 1.6.dp)
            }
        }
        // The pencil and bin stand on the label's own paper, not the sheet's white: their white faces have an edge there
        // already, so no rim (§17).
        CompositionLocalProvider(LocalPaperColor provides paper) {
            PicnicIconButton(PicnicGlyph.Edit, stringResource(R.string.lists_rename, summary.title), onRename)
            PicnicIconButton(PicnicGlyph.Delete, stringResource(R.string.lists_delete, summary.title), onDelete, enabled = canDelete)
        }
    }
}

@Composable
private fun RenameRow(text: String, onTextChange: (String) -> Unit, onDone: () -> Unit, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        PicnicTextField(
            value = text,
            onValueChange = onTextChange,
            label = stringResource(R.string.lists_rename_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone() }),
            modifier = Modifier.weight(1f),
        )
        PicnicIconButton(PicnicGlyph.Check, stringResource(R.string.lists_rename_save), onDone, enabled = text.isNotBlank())
        PicnicIconButton(PicnicGlyph.Close, stringResource(R.string.screens_cancel), onCancel)
    }
}

@Composable
private fun NewListRow(text: String, onTextChange: (String) -> Unit, onDone: () -> Unit, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        PicnicTextField(
            value = text,
            onValueChange = onTextChange,
            label = stringResource(R.string.lists_new_label),
            placeholder = stringResource(R.string.lists_new_placeholder),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.weight(1f),
        )
        PicnicIconButton(PicnicGlyph.Add, stringResource(R.string.lists_new_action), onDone, enabled = text.isNotBlank(), emphasized = true)
        PicnicIconButton(PicnicGlyph.Close, stringResource(R.string.screens_cancel), onCancel)
    }
}

package earth.cora.picnic.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.ui.common.PicnicSheet
import earth.cora.picnic.ui.common.SheetTitle
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.SectionDivider

/** "Your lists": switch, rename, delete (with an inline confirmation) and start a new list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListSwitcherSheet(
    lists: List<ListSummary>,
    onDismiss: () -> Unit,
    onSwitch: (listId: String) -> Unit,
    onNew: (title: String) -> Unit,
    onRename: (listId: String, title: String) -> Unit,
    onDelete: (listId: String) -> Unit,
) {
    PicnicSheet(onDismissRequest = onDismiss) {
        ListSwitcherContent(lists, onSwitch, onNew, onRename, onDelete)
    }
}

@Composable
internal fun ListSwitcherContent(
    lists: List<ListSummary>,
    onSwitch: (listId: String) -> Unit,
    onNew: (title: String) -> Unit,
    onRename: (listId: String, title: String) -> Unit,
    onDelete: (listId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = PicnicTheme.dimens
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var confirmDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var newTitle by rememberSaveable { mutableStateOf("") }

    Column(modifier.verticalScroll(rememberScrollState())) {
        SheetTitle(stringResource(R.string.lists_title))
        Column(
            Modifier.padding(start = d.gutter, end = d.gutter, bottom = d.l),
            verticalArrangement = Arrangement.spacedBy(d.rowGap),
        ) {
            lists.forEach { summary ->
                when (summary.id) {
                    renamingId -> RenameRow(
                        text = renameText,
                        onTextChange = { renameText = it },
                        onDone = {
                            onRename(summary.id, renameText)
                            renamingId = null
                        },
                        onCancel = { renamingId = null },
                    )
                    confirmDeleteId -> ConfirmDeleteRow(
                        summary = summary,
                        onConfirm = {
                            onDelete(summary.id)
                            confirmDeleteId = null
                        },
                        onCancel = { confirmDeleteId = null },
                    )
                    else -> ListRow(
                        summary = summary,
                        canDelete = lists.size > 1,
                        onClick = { onSwitch(summary.id) },
                        onRename = {
                            confirmDeleteId = null
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
            SectionDivider()
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(d.s)) {
                PicnicTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = stringResource(R.string.lists_new_label),
                    placeholder = stringResource(R.string.lists_new_placeholder),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newTitle.isNotBlank()) {
                            onNew(newTitle)
                            newTitle = ""
                        }
                    }),
                    modifier = Modifier.weight(1f),
                )
                PicnicIconButton(
                    PicnicGlyph.Add,
                    stringResource(R.string.lists_new_action),
                    onClick = {
                        onNew(newTitle)
                        newTitle = ""
                    },
                    enabled = newTitle.isNotBlank(),
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
private fun ListRow(
    summary: ListSummary,
    canDelete: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    PicnicSurface(
        Modifier.fillMaxWidth(),
        shape = PicnicTheme.shapes.row,
        color = if (summary.current) c.surfaceSunken else c.surface,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = d.m, end = d.xs, top = d.xs, bottom = d.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.s),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
                    .semantics { selected = summary.current }
                    .padding(vertical = d.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.m),
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (summary.current) Glyph(PicnicGlyph.Check, contentDescription = null, tint = c.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(summary.title, style = PicnicTheme.type.itemName, color = c.ink)
                    Text(
                        if (summary.remaining == 0) {
                            stringResource(R.string.lists_all_done)
                        } else {
                            pluralStringResource(R.plurals.lists_remaining, summary.remaining, summary.remaining)
                        },
                        style = PicnicTheme.type.detail,
                        color = c.inkSoft,
                    )
                }
            }
            PicnicIconButton(PicnicGlyph.Edit, stringResource(R.string.lists_rename, summary.title), onRename)
            PicnicIconButton(PicnicGlyph.Delete, stringResource(R.string.lists_delete, summary.title), onDelete, enabled = canDelete)
        }
    }
}

@Composable
private fun RenameRow(text: String, onTextChange: (String) -> Unit, onDone: () -> Unit, onCancel: () -> Unit) {
    val d = PicnicTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.xs)) {
        PicnicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone() }),
            modifier = Modifier.weight(1f),
        )
        PicnicIconButton(PicnicGlyph.Check, stringResource(R.string.lists_rename_save), onDone, enabled = text.isNotBlank(), emphasized = true)
        PicnicIconButton(PicnicGlyph.Close, stringResource(R.string.screens_cancel), onCancel)
    }
}

@Composable
private fun ConfirmDeleteRow(summary: ListSummary, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    PicnicSurface(Modifier.fillMaxWidth(), shape = PicnicTheme.shapes.row, color = c.surfaceSunken) {
        Column(Modifier.padding(d.m), verticalArrangement = Arrangement.spacedBy(d.s)) {
            Text(
                if (summary.remaining == 0) {
                    stringResource(R.string.lists_delete_confirm_empty, summary.title)
                } else {
                    pluralStringResource(R.plurals.lists_delete_confirm, summary.remaining, summary.title, summary.remaining)
                },
                style = PicnicTheme.type.body,
                color = c.ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(d.s)) {
                PicnicButton(stringResource(R.string.lists_delete_yes), onClick = onConfirm, glyph = PicnicGlyph.Delete)
                PicnicButton(stringResource(R.string.lists_delete_no), onClick = onCancel, style = PicnicButtonStyle.Secondary)
            }
        }
    }
}

package earth.cora.picnic.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.theme.CheckMark
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.ToastHost
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.WashiPattern

/*
 * WP7's harness: a stand-in List screen with the add flows wired exactly as WP5 places them (the list under the bar, the
 * docked bar or the FAB, the toasts, the sheet on top), for the previews and the interaction tests. Owner: WP7.
 */

/** One row of the harness's stand-in list. */
@Immutable
data class HarnessRow(val id: String, val name: String, val icon: String, val checked: Boolean)

/** The test tag of a harness row. */
fun harnessRowTag(id: String): String = "harness_row:$id"

/** The harness list's test tag. */
const val HARNESS_LIST_TAG: String = "harness_list"

/**
 * A stand-in List screen around the add flows: [rows] in a LazyColumn whose scroll folds the bar into the FAB, the
 * [AddDock], the toasts ([AddEventsEffect]) and the [AddSheetHost]. [onCommitted] sees every commit (the list's pops).
 */
@Composable
fun AddFlowsHarness(
    flows: AddFlows,
    rows: List<HarnessRow>,
    modifier: Modifier = Modifier,
    tape: WashiPattern = WashiPattern.SakuraStripe,
    onCommitted: (AddCommit) -> Unit = {},
    onEditPantry: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    toasts: ToastHostState = remember { ToastHostState() },
) {
    val state by flows.vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    AddEventsEffect(flows, toasts, onCommitted)
    LaunchedEffect(listState, flows) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .collect { flows.collapse.atTop(it) }
    }
    Box(modifier.fillMaxSize()) {
        PicnicBackground {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HARNESS_LIST_TAG)
                    .nestedScroll(flows.collapse.nestedScrollConnection),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(rows, key = { it.id }) { row -> HarnessRowView(row) }
            }
        }
        AddDock(flows, state, tape = tape)
        ToastHost(toasts, bottomInset = flows.collapse.dockHeight())
        AddSheetHost(flows, state, onOpenSettings = onOpenSettings, onEditPantry = onEditPantry)
    }
}

@Composable
private fun HarnessRowView(row: HarnessRow) {
    val c = PicnicTheme.colors
    PicnicSurface(Modifier.fillMaxWidth().testTag(harnessRowTag(row.id))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ItemIcon(IconRef(row.icon), contentDescription = null, size = 40.dp, ghost = row.checked)
            Text(
                row.name,
                style = PicnicTheme.type.itemName.copy(color = if (row.checked) c.inkSoft else c.ink),
                textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            CheckMark(checked = row.checked, seed = row.id)
        }
    }
}

/** The add bar and the FAB on the binder, for Android Studio's preview (no ViewModel). */
@Composable
internal fun AddBarPreviewFrame(variant: ThemeVariant) {
    PicnicTheme(variant = variant) {
        PicnicBackground {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.CenterEnd) {
                    AddFab(onClick = {}, modifier = Modifier.padding(end = 6.dp))
                }
                AddBar(
                    AddBarUi(), onTextChange = {}, onSend = {}, onSnap = {}, onChoosePhoto = {}, onSpeak = {},
                    tape = WashiPattern.Thanksgiving,
                )
            }
        }
    }
}

@Preview(name = "Add bar and FAB - day", widthDp = 360, heightDp = 220)
@Composable
private fun AddBarDayPreview() = AddBarPreviewFrame(ThemeVariant.Day)

@Preview(name = "Add bar and FAB - glow", widthDp = 360, heightDp = 220)
@Composable
private fun AddBarGlowPreview() = AddBarPreviewFrame(ThemeVariant.Glow)

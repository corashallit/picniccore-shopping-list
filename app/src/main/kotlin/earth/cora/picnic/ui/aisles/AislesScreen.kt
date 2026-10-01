package earth.cora.picnic.ui.aisles

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.core.assistant.AisleSyncReport
import earth.cora.picnic.ui.common.BackButton
import earth.cora.picnic.ui.common.HintText
import earth.cora.picnic.ui.common.ScreenScaffold
import earth.cora.picnic.ui.common.SectionCard
import earth.cora.picnic.ui.common.storeAisleNumber
import earth.cora.picnic.ui.theme.AisleBasket
import earth.cora.picnic.ui.theme.AisleMedallion
import earth.cora.picnic.ui.theme.LoadingSparkle
import earth.cora.picnic.ui.theme.LocalIconCatalog
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.PicnicTopBar
import kotlinx.coroutines.Dispatchers

@Stable
class AislesActions(
    val onBack: () -> Unit = {},
    val onStoreNameChange: (String) -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onResort: () -> Unit = {},
    val onSaveAndLeave: () -> Unit = {},
    val onDiscardAndLeave: () -> Unit = {},
    val onKeepEditing: () -> Unit = {},
)

/** Stateful Aisles destination. Leaving with unsaved edits asks first (save / discard / keep editing). */
@Composable
fun AislesRoute(vm: AislesViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            val text = when (event) {
                AislesEvent.SaveFailed -> resources.getString(R.string.aisles_save_failed)
                is AislesEvent.Synced -> syncReportText(resources, event.report)
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
    // Stays on while the prompt shows: a second Back dismisses the prompt instead of leaving and
    // silently dropping the draft (only "Discard" drops it).
    BackHandler(enabled = state.dirty || confirmLeave) { confirmLeave = !confirmLeave }

    val actions = remember(vm) {
        AislesActions(
            onBack = { if (vm.state.value.dirty) confirmLeave = true else onBack() },
            onStoreNameChange = vm::setStoreName,
            onTextChange = vm::setText,
            onSave = vm::save,
            onResort = vm::resort,
            onSaveAndLeave = {
                vm.save()
                confirmLeave = false
                onBack()
            },
            onDiscardAndLeave = {
                vm.discard()
                confirmLeave = false
                onBack()
            },
            onKeepEditing = { confirmLeave = false },
        )
    }
    AislesScreen(state, actions, snackbarHostState = snackbar, confirmLeave = confirmLeave)
}

/** Stateless Aisles screen: store name, the free-text aisle list, a live preview with baskets, save and re-sort. */
@Composable
fun AislesScreen(
    state: AislesUiState,
    actions: AislesActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    confirmLeave: Boolean = false,
) {
    val d = PicnicTheme.dimens
    ScreenScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBar = {
            PicnicTopBar(
                title = stringResource(R.string.aisles_title),
                subtitle = state.storeName.ifBlank { null },
                navigation = { BackButton(actions.onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PaddingValues(start = d.gutter, end = d.gutter, top = d.xs, bottom = d.xxl)),
                verticalArrangement = Arrangement.spacedBy(d.m),
            ) {
                AislesEditor(state, actions)
                // Actions before the (long) preview, so Save never needs a scroll past every aisle.
                SyncControls(state, actions)
                AislesPreview(state)
            }
            if (confirmLeave) {
                LeaveConfirmation(actions, Modifier.align(Alignment.BottomCenter).padding(d.gutter))
            }
        }
    }
}

@Composable
private fun AislesEditor(state: AislesUiState, actions: AislesActions) {
    SectionCard(stringResource(R.string.aisles_store_card), glyph = PicnicGlyph.Aisles) {
        PicnicTextField(
            value = state.storeName,
            onValueChange = actions.onStoreNameChange,
            label = stringResource(R.string.aisles_store_name),
            singleLine = true,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        PicnicTextField(
            value = state.text,
            onValueChange = actions.onTextChange,
            label = stringResource(R.string.aisles_text_label),
            placeholder = stringResource(R.string.aisles_text_placeholder),
            minLines = 6,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        HintText(stringResource(R.string.aisles_text_hint))
    }
}

@Composable
private fun AislesPreview(state: AislesUiState) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val baskets = LocalIconCatalog.current.baskets
    SectionCard(pluralStringResource(R.plurals.aisles_preview_title, state.parsed.size, state.parsed.size)) {
        if (state.parsed.isEmpty()) HintText(stringResource(R.string.aisles_preview_empty))
        state.parsed.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.s)) {
                AisleBasket(kind = p.basket, miniIcons = emptyList(), count = 0, size = 44.dp)
                AisleMedallion(number = storeAisleNumber(p.aisle.name), aisleIndex = p.aisle.position - 1, size = 28.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.aisle.name, style = PicnicTheme.type.label, color = c.ink)
                    val basket = baskets.entry(p.basket)?.label ?: p.basket.key
                    Text(
                        if (p.itemCount > 0) {
                            pluralStringResource(R.plurals.aisles_preview_detail, p.itemCount, basket, p.itemCount)
                        } else {
                            basket
                        },
                        style = PicnicTheme.type.caption,
                        color = c.inkSoft,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncControls(state: AislesUiState, actions: AislesActions) {
    val resources = LocalResources.current
    val d = PicnicTheme.dimens
    FlowRow(horizontalArrangement = Arrangement.spacedBy(d.s), verticalArrangement = Arrangement.spacedBy(d.xs)) {
        PicnicButton(
            stringResource(R.string.aisles_save),
            onClick = actions.onSave,
            enabled = state.dirty && !state.syncing,
            glyph = PicnicGlyph.Check,
        )
        PicnicButton(
            stringResource(R.string.aisles_resort),
            onClick = actions.onResort,
            enabled = !state.syncing && !state.loading,
            style = PicnicButtonStyle.Secondary,
            glyph = PicnicGlyph.Refresh,
        )
    }
    HintText(
        if (state.needingAisle > 0) {
            pluralStringResource(R.plurals.aisles_resort_hint_count, state.needingAisle, state.needingAisle)
        } else {
            stringResource(R.string.aisles_resort_hint)
        },
    )
    if (state.syncing) {
        LoadingSparkle(label = stringResource(R.string.aisles_syncing))
    } else {
        state.lastReport?.let { report ->
            Text(
                syncReportText(resources, report),
                style = PicnicTheme.type.body,
                color = PicnicTheme.colors.inkSoft,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun LeaveConfirmation(actions: AislesActions, modifier: Modifier = Modifier) {
    val d = PicnicTheme.dimens
    PicnicSurface(modifier.fillMaxWidth(), color = PicnicTheme.colors.surfaceRaised) {
        Column(Modifier.padding(d.l), verticalArrangement = Arrangement.spacedBy(d.s)) {
            Text(stringResource(R.string.aisles_leave_title), style = PicnicTheme.type.aisleTitle, color = PicnicTheme.colors.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(d.s)) {
                PicnicButton(stringResource(R.string.aisles_leave_save), onClick = actions.onSaveAndLeave, glyph = PicnicGlyph.Check)
                PicnicButton(stringResource(R.string.aisles_leave_discard), onClick = actions.onDiscardAndLeave, style = PicnicButtonStyle.Secondary)
            }
            PicnicButton(stringResource(R.string.aisles_leave_keep), onClick = actions.onKeepEditing, style = PicnicButtonStyle.Text)
        }
    }
}

/** "Sorted 6 items · 2 left in Unsorted", or the error's friendly message plus what is left. */
internal fun syncReportText(res: Resources, report: AisleSyncReport): String {
    val left = report.leftUnsorted
    val leftText = res.getQuantityString(R.plurals.aisles_report_left, left, left)
    val error = report.error
    return when {
        report.saveError != null -> res.getString(R.string.aisles_save_failed)
        error != null -> if (left > 0) "${error.friendlyMessage} $leftText" else error.friendlyMessage
        report.recategorized == 0 && left == 0 -> res.getString(R.string.aisles_report_nothing)
        left == 0 -> res.getQuantityString(R.plurals.aisles_report_sorted, report.recategorized, report.recategorized)
        else -> res.getString(
            R.string.aisles_report_both,
            res.getQuantityString(R.plurals.aisles_report_sorted, report.recategorized, report.recategorized),
            leftText,
        )
    }
}

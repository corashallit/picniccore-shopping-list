package earth.cora.picnic.ui.add

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.platform.rememberAddSources
import earth.cora.picnic.ui.common.HintText
import earth.cora.picnic.ui.common.PicnicSheet
import earth.cora.picnic.ui.common.SheetTitle
import earth.cora.picnic.ui.common.storeAisleNumber
import earth.cora.picnic.ui.theme.AisleSectionRow
import earth.cora.picnic.ui.theme.AisleSectionTitle
import earth.cora.picnic.ui.theme.CheckMark
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LoadingSparkle
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicChip
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the Add sheet can ask for. Defaults do nothing (screenshot tests pass only what they need). */
@Stable
class AddActions(
    val onModeChange: (AddMode) -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onSubmitText: () -> Unit = {},
    val onAddTyped: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onStopRecording: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onToggleKeep: (key: Int) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUsePlainLines: () -> Unit = {},
    val onCommit: () -> Unit = {},
    val onStartOver: () -> Unit = {},
    val onEditTranscript: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * Stateful Add sheet: [AddViewModel] plus the platform launchers (camera via TakePicture, the photo
 * picker, the RECORD_AUDIO request). Recording stops on ON_STOP (foreground only), except across a
 * configuration change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheetRoute(
    vm: AddViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Main.immediate: text-field edits round-trip through the VM within the same frame.
    val state by vm.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val scope = rememberCoroutineScope()
    val sources = rememberAddSources(
        onImage = vm::onPhoto,
        onMicGranted = vm::startRecording,
        onMicDenied = vm::micDenied,
        onCameraUnavailable = vm::cameraUnavailable,
    )
    // Recording is foreground-only, but a rotation also passes through ON_STOP: the recorder lives
    // in the VM and survives the recreation, so only a real trip to the background stops it.
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) vm.onStop()
    }
    val actions = remember(vm, sources) {
        AddActions(
            onModeChange = vm::setMode,
            onTextChange = vm::setText,
            onSubmitText = vm::submitText,
            onAddTyped = vm::addTyped,
            onTakePhoto = sources.takePhoto,
            onPickPhoto = sources.pickPhoto,
            onRecord = sources.recordVoice,
            onStopRecording = vm::stopRecording,
            onCancel = vm::cancel,
            onToggleKeep = vm::toggleKeep,
            onRetry = vm::retry,
            onUsePlainLines = vm::usePlainLines,
            onCommit = vm::commit,
            onStartOver = vm::startOver,
            onEditTranscript = vm::editTranscript,
            onOpenSettings = onOpenSettings,
            onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
        )
    }
    AddSheet(state, actions, onDismiss = onDismiss, sheetState = sheetState)
}

/** Stateless Add sheet (ModalBottomSheet + [AddSheetContent]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    state: AddUiState,
    actions: AddActions,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    PicnicSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AddSheetContent(state, actions)
    }
}

/** The sheet's content for every mode and phase (docs/ARCHITECTURE.md §10.4). */
@Composable
fun AddSheetContent(state: AddUiState, actions: AddActions, modifier: Modifier = Modifier) {
    val d = PicnicTheme.dimens
    val phase = state.phase
    Column(modifier.verticalScroll(rememberScrollState())) {
        SheetTitle(stringResource(if (phase == AddPhase.Preview) R.string.add_title_preview else R.string.add_title)) {
            PicnicIconButton(PicnicGlyph.Close, stringResource(R.string.screens_close), actions.onClose)
        }
        Column(
            Modifier.padding(start = d.gutter, end = d.gutter, bottom = d.l),
            verticalArrangement = Arrangement.spacedBy(d.m),
        ) {
            // Tabs only while the user can switch: hidden during a call, a recording and the preview.
            if (phase != AddPhase.Preview && !phase.busy) ModeTabs(state.mode, enabled = !state.committing, onModeChange = actions.onModeChange)
            when (phase) {
                AddPhase.Preview -> PreviewContent(state, actions)
                AddPhase.Transcribing, is AddPhase.Thinking -> BusyContent(state, actions)
                is AddPhase.Recording -> RecordingContent(phase, actions)
                AddPhase.Idle, is AddPhase.Failed -> when (state.mode) {
                    AddMode.WRITE -> WriteContent(state, actions)
                    AddMode.SNAP -> SnapContent(state, actions)
                    AddMode.SPEAK -> SpeakContent(state, actions)
                }
            }
        }
    }
}

@Composable
private fun ModeTabs(mode: AddMode, enabled: Boolean, onModeChange: (AddMode) -> Unit) {
    val d = PicnicTheme.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(d.xs)) {
        ModeTab(stringResource(R.string.add_mode_write), PicnicGlyph.Edit, mode == AddMode.WRITE, enabled) { onModeChange(AddMode.WRITE) }
        ModeTab(stringResource(R.string.add_mode_snap), PicnicGlyph.Camera, mode == AddMode.SNAP, enabled) { onModeChange(AddMode.SNAP) }
        ModeTab(stringResource(R.string.add_mode_speak), PicnicGlyph.Mic, mode == AddMode.SPEAK, enabled) { onModeChange(AddMode.SPEAK) }
    }
}

@Composable
private fun ModeTab(text: String, glyph: PicnicGlyph, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = PicnicTheme.colors
    PicnicChip(
        text,
        selected = selected,
        onClick = { if (enabled) onClick() },
        leading = { Glyph(glyph, contentDescription = null, tint = if (selected) c.onHighlight else c.ink, size = 18.dp) },
    )
}

/* ---------------- Write ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteContent(state: AddUiState, actions: AddActions) {
    val d = PicnicTheme.dimens
    PicnicTextField(
        value = state.text,
        onValueChange = actions.onTextChange,
        placeholder = stringResource(R.string.add_write_placeholder),
        minLines = 3,
        maxLines = 8,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    val failed = state.phase as? AddPhase.Failed
    if (failed != null) {
        FailureCard(failed, actions)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(d.s), verticalArrangement = Arrangement.spacedBy(d.xs)) {
            PicnicButton(
                stringResource(R.string.add_write_ai),
                onClick = actions.onSubmitText,
                enabled = state.text.isNotBlank() && !state.committing,
                glyph = PicnicGlyph.Sparkle,
            )
            PicnicButton(
                stringResource(R.string.add_write_typed),
                onClick = actions.onAddTyped,
                enabled = state.text.isNotBlank() && !state.committing,
                style = PicnicButtonStyle.Secondary,
            )
        }
        HintText(stringResource(R.string.add_write_hint))
    }
}

/* ---------------- Snap ---------------- */

@Composable
private fun SnapContent(state: AddUiState, actions: AddActions) {
    (state.phase as? AddPhase.Failed)?.let { FailureCard(it, actions) }
    PicnicButton(
        stringResource(R.string.add_snap_camera),
        onClick = actions.onTakePhoto,
        glyph = PicnicGlyph.Camera,
        modifier = Modifier.fillMaxWidth(),
    )
    PicnicButton(
        stringResource(R.string.add_snap_gallery),
        onClick = actions.onPickPhoto,
        style = PicnicButtonStyle.Secondary,
        glyph = PicnicGlyph.Gallery,
        modifier = Modifier.fillMaxWidth(),
    )
    HintText(stringResource(R.string.add_snap_hint))
}

/* ---------------- Speak ---------------- */

@Composable
private fun SpeakContent(state: AddUiState, actions: AddActions) {
    state.transcript?.let { TranscriptCard(it, onEdit = actions.onEditTranscript) }
    (state.phase as? AddPhase.Failed)?.let { FailureCard(it, actions) }
    PicnicButton(
        stringResource(if (state.phase is AddPhase.Failed) R.string.add_speak_again else R.string.add_speak_start),
        onClick = actions.onRecord,
        glyph = PicnicGlyph.Mic,
        modifier = Modifier.fillMaxWidth(),
    )
    HintText(stringResource(R.string.add_speak_hint))
}

@Composable
private fun RecordingContent(phase: AddPhase.Recording, actions: AddActions) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val elapsed = formatElapsed(phase.elapsedMs)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.s)) {
        Glyph(PicnicGlyph.Mic, contentDescription = null, tint = c.primary)
        Text(
            stringResource(R.string.add_speak_listening),
            style = PicnicTheme.type.body,
            color = c.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            elapsed,
            style = PicnicTheme.type.counter.copy(fontSize = PicnicTheme.type.screenTitle.fontSize),
            color = c.ink,
            modifier = Modifier.semantics { stateDescription = elapsed },
        )
    }
    LevelMeter(phase.levels, Modifier.fillMaxWidth().height(56.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.s)) {
        PicnicButton(
            stringResource(R.string.add_speak_done),
            onClick = actions.onStopRecording,
            glyph = PicnicGlyph.Stop,
            modifier = Modifier.weight(1f),
        )
        PicnicButton(stringResource(R.string.screens_cancel), onClick = actions.onCancel, style = PicnicButtonStyle.Text)
    }
    HintText(stringResource(R.string.add_speak_limit))
}

/** Recent input levels as rounded bars (newest on the right). Decorative: the timer carries the state. */
@Composable
private fun LevelMeter(levels: List<Float>, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    PicnicSurface(modifier, shape = PicnicTheme.shapes.field, color = c.surfaceSunken, shadow = false) {
        Canvas(
            Modifier
                .matchParentSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clearAndSetSemantics { },
        ) {
            val bars = METER_BARS
            val gap = 3.dp.toPx()
            val w = (size.width - gap * (bars - 1)) / bars
            val padded = List(bars - levels.size.coerceAtMost(bars)) { 0f } + levels.takeLast(bars)
            padded.forEachIndexed { i, level ->
                val h = (0.08f + 0.92f * level.coerceIn(0f, 1f)) * size.height
                drawRoundRect(
                    color = if (level > 0.75f) c.highlight else c.primary,
                    topLeft = Offset(i * (w + gap), (size.height - h) / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2f),
                )
            }
        }
    }
}

/* ---------------- Busy (transcribing / thinking) ---------------- */

@Composable
private fun BusyContent(state: AddUiState, actions: AddActions) {
    val d = PicnicTheme.dimens
    val phase = state.phase
    var slow by remember(phase) { mutableStateOf(false) }
    LaunchedEffect(phase) {
        delay(SLOW_AFTER_MS)
        slow = true
    }
    if (phase is AddPhase.Thinking && phase.kind == ItemSource.VOICE) state.transcript?.let { TranscriptCard(it, onEdit = null) }
    PicnicSurface(Modifier.fillMaxWidth().padding(bottom = d.hardShadow.y), color = PicnicTheme.colors.surfaceSunken, shadow = false) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = d.xl, horizontal = d.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(d.m),
        ) {
            LoadingSparkle(label = busyLabel(phase))
            // Echo the request, so it's clear what is being worked on.
            if (phase is AddPhase.Thinking && phase.kind == ItemSource.AI_TEXT && state.text.isNotBlank()) {
                Text(
                    stringResource(R.string.add_transcript_quoted, state.text.trim()),
                    style = PicnicTheme.type.handwriting,
                    color = PicnicTheme.colors.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (slow) {
                HintText(stringResource(R.string.add_busy_slow), Modifier.fillMaxWidth())
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        PicnicButton(stringResource(R.string.screens_cancel), onClick = actions.onCancel, style = PicnicButtonStyle.Secondary, glyph = PicnicGlyph.Close)
    }
}

@Composable
private fun busyLabel(phase: AddPhase): String = when (phase) {
    AddPhase.Transcribing -> stringResource(R.string.add_busy_transcribing)
    is AddPhase.Thinking -> when {
        phase.kind == ItemSource.PHOTO -> stringResource(R.string.add_busy_photo)
        phase.recipe -> stringResource(R.string.add_busy_recipe)
        phase.kind == ItemSource.VOICE -> stringResource(R.string.add_busy_voice)
        else -> stringResource(R.string.add_busy_list)
    }
    else -> ""
}

/* ---------------- Failure ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FailureCard(failed: AddPhase.Failed, actions: AddActions) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    PicnicSurface(Modifier.fillMaxWidth().padding(bottom = d.hardShadow.y), color = c.surfaceSunken) {
        Column(Modifier.padding(d.m), verticalArrangement = Arrangement.spacedBy(d.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(d.s)) {
                Glyph(PicnicGlyph.Sparkle, contentDescription = null, tint = c.error, size = 20.dp)
                Text(failed.message, style = PicnicTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(d.s), verticalArrangement = Arrangement.spacedBy(d.xs)) {
                if (failed.retryable) {
                    PicnicButton(stringResource(R.string.add_retry), onClick = actions.onRetry, glyph = PicnicGlyph.Refresh)
                }
                if (failed.needsSettings) {
                    PicnicButton(stringResource(R.string.add_open_settings), onClick = actions.onOpenSettings, glyph = PicnicGlyph.Settings)
                }
                if (failed.canUsePlainLines) {
                    PicnicButton(
                        stringResource(R.string.add_plain_lines),
                        onClick = actions.onUsePlainLines,
                        style = PicnicButtonStyle.Secondary,
                        glyph = PicnicGlyph.Edit,
                    )
                }
            }
        }
    }
}

/* ---------------- Preview ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PreviewContent(state: AddUiState, actions: AddActions) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val uriHandler = LocalUriHandler.current

    if (state.previewSource == ItemSource.VOICE) state.transcript?.let { TranscriptCard(it, onEdit = actions.onEditTranscript) }
    if (state.previewSource == ItemSource.TYPED) HintText(stringResource(R.string.add_preview_plain_note))
    state.message?.let { message ->
        Row(horizontalArrangement = Arrangement.spacedBy(d.s)) {
            Glyph(PicnicGlyph.Sparkle, contentDescription = null, tint = c.primary, size = 20.dp)
            Text(message, style = PicnicTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
        }
    }
    if (state.preview.isEmpty()) {
        Text(stringResource(R.string.add_preview_empty), style = PicnicTheme.type.body, color = c.inkSoft)
    }

    // Consecutive items share an aisle (the VM sorts by walking order, Unsorted first).
    val groups = state.preview.fold(mutableListOf<MutableList<PreviewItemUi>>()) { acc, item ->
        if (acc.lastOrNull()?.first()?.aisleIndex == item.aisleIndex) acc.last() += item else acc += mutableListOf(item)
        acc
    }
    // Each aisle group is one section container, like the list: title band, rows inside its tray.
    groups.forEach { group ->
        val first = group.first()
        Column(Modifier.fillMaxWidth().padding(bottom = d.hardShadow.y)) {
            AisleSectionTitle(
                title = if (first.aisleIndex < 0) stringResource(R.string.list_section_unsorted) else first.aisleTitle,
                number = if (first.aisleIndex >= 0) storeAisleNumber(first.aisleTitle) else null,
                aisleIndex = first.aisleIndex,
            )
            group.forEachIndexed { i, row ->
                AisleSectionRow(first.aisleIndex, first = i == 0, last = i == group.lastIndex) {
                    PreviewRow(row, onToggle = { actions.onToggleKeep(row.key) })
                }
            }
        }
    }

    if (state.skipped.isNotEmpty()) {
        HintText(stringResource(R.string.add_preview_skipped, state.skipped.joinToString(", ")))
    }
    if (state.sources.isNotEmpty()) {
        Text(
            stringResource(R.string.add_preview_sources),
            style = PicnicTheme.type.label,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(d.xs)) {
            state.sources.forEach { source ->
                PicnicChip(
                    text = source.title?.takeIf { it.length <= MAX_SOURCE_TITLE } ?: hostOf(source.url),
                    selected = false,
                    onClick = { runCatching { uriHandler.openUri(source.url) } },
                    leading = { Glyph(PicnicGlyph.Link, contentDescription = null, size = 16.dp) },
                )
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.s)) {
        PicnicButton(
            pluralStringResource(R.plurals.add_commit, state.keptCount, state.keptCount),
            onClick = actions.onCommit,
            enabled = state.keptCount > 0 && !state.committing,
            glyph = PicnicGlyph.Add,
            modifier = Modifier.weight(1f),
        )
        PicnicButton(stringResource(R.string.add_start_over), onClick = actions.onStartOver, style = PicnicButtonStyle.Text)
    }
}

@Composable
private fun PreviewRow(row: PreviewItemUi, onToggle: () -> Unit) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val stateText = stringResource(if (row.keep) R.string.add_preview_keep else R.string.add_preview_skip)
    val detail = listOfNotNull(row.item.quantity, row.item.note).joinToString(" · ").ifEmpty { null }
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = row.keep,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics { stateDescription = stateText }
            .padding(start = d.s, end = d.xs, top = d.xxs, bottom = d.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.s),
    ) {
        ItemIcon(row.icon, contentDescription = null, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(
                row.item.name,
                style = PicnicTheme.type.itemName.copy(textDecoration = if (row.keep) null else TextDecoration.LineThrough),
                color = if (row.keep) c.ink else c.inkFaint,
            )
            if (detail != null) Text(detail, style = PicnicTheme.type.detail, color = c.inkSoft)
        }
        CheckMark(checked = row.keep)
    }
}

/** What the user said, in the handwriting face. [onEdit] moves it to the Write tab for fixing. */
@Composable
private fun TranscriptCard(transcript: String, onEdit: (() -> Unit)?) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    PicnicSurface(Modifier.fillMaxWidth().padding(bottom = d.hardShadow.y), color = c.surfaceSunken, shadow = false) {
        Column(Modifier.padding(d.m), verticalArrangement = Arrangement.spacedBy(d.xs)) {
            Text(stringResource(R.string.add_transcript_label), style = PicnicTheme.type.caption, color = c.inkSoft)
            Text(stringResource(R.string.add_transcript_quoted, transcript), style = PicnicTheme.type.handwriting, color = c.ink)
            if (onEdit != null) {
                PicnicButton(stringResource(R.string.add_transcript_edit), onClick = onEdit, style = PicnicButtonStyle.Text, glyph = PicnicGlyph.Edit)
            }
        }
    }
}

/* ---------------- helpers ---------------- */

/** "1:05" for 65 s. */
internal fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** "allrecipes.com" for a source chip. */
internal fun hostOf(url: String): String =
    runCatching { url.toUri().host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url

private const val METER_BARS = 28
private const val SLOW_AFTER_MS = 15_000L
private const val MAX_SOURCE_TITLE = 28

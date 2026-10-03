package earth.cora.picnic.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.theme.ChipFace
import earth.cora.picnic.ui.theme.DayColors
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.SectionDivider
import earth.cora.picnic.ui.theme.StickerFamily
import earth.cora.picnic.ui.theme.hardShadow

/*
 * The letter-set Add sheet (docs/DESIGN_STICKER.md §4.16, §6.2, items 202–208, 210, 215, 323): Write · Snap · Speak
 * index tabs, the Write tab's note, thinking box and recipe card, the Snap tab's polaroid, lines and stickers (Speak can
 * follow, D19), the Speak tab's cassette, equaliser and Heard tray, the error state, and the sticky footer ("★ Stick all
 * n on my list", "Reading… n of m"). Owner: WP7.
 */

const val ADD_TABS_TAG: String = "add_tabs"
const val ADD_FOOTER_TAG: String = "add_footer"
const val ADD_ERROR_TAG: String = "add_error"
const val CHOOSE_PHOTO_CHIP_TAG: String = "add_choose_photo"

/** Everything the Add sheet can ask for. Defaults do nothing (screenshot tests pass only what they need). */
@Stable
class AddActions(
    val onModeChange: (AddMode) -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onAsTyped: () -> Unit = {},
    val onEditNote: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onChoosePhoto: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onStopRecording: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onToggleKeep: (key: Int) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUsePlainLines: () -> Unit = {},
    val onCommit: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    /** "Edit my pantry" (WP8's navigation); null hides the button. */
    val onEditPantry: (() -> Unit)? = null,
    val onRevealed: (Long) -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The Add sheet wired to [flows] (WP5 places it above the overlay layer): Back and the scrim close it, "Open Settings"
 * and "Edit my pantry" leave it to their screens (the review waits in the ViewModel: Back returns to the same card).
 */
@Composable
fun AddSheetHost(flows: AddFlows, state: AddUiState, onOpenSettings: () -> Unit, onEditPantry: (() -> Unit)?) {
    val vm = flows.vm
    val actions = remember(flows, onOpenSettings, onEditPantry) {
        AddActions(
            onModeChange = vm::setMode,
            onTextChange = vm::setText,
            onSend = { vm.send(AddOrigin.SHEET) },
            onAsTyped = vm::addAsTyped,
            onEditNote = vm::editNote,
            onTakePhoto = flows.sources.takePhoto,
            onChoosePhoto = flows.sources.pickPhoto,
            onRecord = flows.sources.recordVoice,
            onStopRecording = vm::stopRecording,
            onCancel = vm::cancel,
            onToggleKeep = vm::toggleKeep,
            onRetry = vm::retry,
            onUsePlainLines = vm::usePlainLines,
            onCommit = vm::commit,
            onOpenSettings = onOpenSettings,
            onEditPantry = onEditPantry,
            onRevealed = vm::revealFinished,
            onClose = vm::closeSheet,
        )
    }
    AddSheet(state, actions, feedback = flows.feedback)
}

/** The stateless sheet: [LetterSetSheet] (visible while [AddUiState.sheetOpen]) with the tabs, the content and the footer. */
@Composable
fun AddSheet(state: AddUiState, actions: AddActions, modifier: Modifier = Modifier, feedback: Feedback? = null) {
    val reveals = rememberReveals(state, actions)
    LetterSetSheet(
        visible = state.sheetOpen,
        onDismissRequest = actions.onClose,
        modifier = modifier,
        footer = footerFor(state, reveals)?.let { footer -> { AddFooter(footer, state, actions) } },
    ) {
        AddSheetContent(state, actions, reveals, feedback)
    }
}

/** The running reveals of the review: the answer's ([main]) and the voice note's after a photo ([heard], D19). */
@Stable
class Reveals(val main: RevealClock?, val heard: RevealClock?)

@Composable
private fun rememberReveals(state: AddUiState, actions: AddActions): Reveals {
    val review = state.review ?: return Reveals(null, null)
    val reduced = LocalReducedMotion.current
    val plan = remember(review.id, reduced) {
        if (review.source == ItemSource.PHOTO) {
            RevealPlan.photo(review.lines, review.mainRows.sortedBy { it.key }.map { it.key }, reduced)
        } else {
            RevealPlan.thinking(review.steps.size, displayOrder(review), reduced)
        }
    }
    val main = rememberRevealClock(review.id, plan, played = review.id in review.revealed, onDone = actions.onRevealed)
    val heardId = review.heardId
    val heard = if (heardId != null) {
        val heardPlan = remember(heardId, reduced) { RevealPlan.thinking(review.heardSteps.size, review.heardRows.map { it.key }, reduced) }
        rememberRevealClock(heardId, heardPlan, played = heardId in review.revealed, onDone = actions.onRevealed)
    } else {
        null
    }
    return Reveals(main, heard)
}

/** The rows of a text or voice review in the order they fan out: the card's trays, top to bottom. */
internal fun displayOrder(review: AddReview): List<Int> =
    if (review.source == ItemSource.VOICE) review.mainRows.map { it.key } else groupByAisle(review.mainRows).flatten().map { it.key }

/** The review shows on [mode]'s tab: a note's on Write, a photo's on Snap and Speak (D19), a voice note's on Speak. */
private fun AddReview.showsOn(mode: AddMode): Boolean = when (source) {
    ItemSource.PHOTO -> mode != AddMode.WRITE
    ItemSource.VOICE -> mode == AddMode.SPEAK
    else -> mode == AddMode.WRITE
}

/* ------------------------------------------------------------------------------------------------
 * Content.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun ColumnScope.AddSheetContent(state: AddUiState, actions: AddActions, reveals: Reveals, feedback: Feedback?) {
    AddTabs(state.mode, recording = state.recording, onModeChange = actions.onModeChange)
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (state.mode) {
            AddMode.WRITE -> WriteTab(state, actions, reveals, feedback)
            AddMode.SNAP -> SnapTab(state, actions, reveals, feedback)
            AddMode.SPEAK -> SpeakTab(state, actions, reveals, feedback)
        }
    }
}

/**
 * The index tabs (§4.16): Write · Snap · Speak in lemon, ramune and sakura pastels, 38 dp tall, Zen Maru 700 14 sp with
 * a 20 dp glyph; the selected tab is 6 dp taller (44 dp), mid fill, DynaPuff 16 sp. A 2 dp rule under them. Role.Tab.
 * A REC dot sits on Speak while recording.
 */
@Composable
fun AddTabs(mode: AddMode, recording: Boolean, onModeChange: (AddMode) -> Unit, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    Box(modifier.fillMaxWidth().testTag(ADD_TABS_TAG)) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .drawBehind {
                    val w = 2.dp.toPx()
                    drawRect(c.rule, Offset(-16.dp.toPx(), size.height - w), Size(size.width + 34.dp.toPx(), w))
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AddTab(stringResource(R.string.add_tab_write), PicnicGlyph.Edit, Families.lemon, mode == AddMode.WRITE, false, { onModeChange(AddMode.WRITE) }, Modifier.weight(1f))
            AddTab(stringResource(R.string.add_tab_snap), PicnicGlyph.Polaroid, Families.ramune, mode == AddMode.SNAP, false, { onModeChange(AddMode.SNAP) }, Modifier.weight(1f))
            AddTab(stringResource(R.string.add_tab_speak), PicnicGlyph.Cassette, Families.sakura, mode == AddMode.SPEAK, recording, { onModeChange(AddMode.SPEAK) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AddTab(
    label: String,
    glyph: PicnicGlyph,
    family: StickerFamily,
    selected: Boolean,
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    val recordingText = stringResource(R.string.add_tab_recording)
    val large = LocalDensity.current.fontScale >= 1.5f
    val style = if (selected) t.secondaryButton.copy(color = c.ballpoint) else t.label.copy(fontSize = 14.sp, lineHeight = 18.sp, color = c.ballpoint)
    Box(
        modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { if (recording) stateDescription = recordingText },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (selected) 44.dp else 38.dp)
                .hardShadow(shape, PicnicTheme.shadows)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(start = 3.dp, end = 3.dp, top = 3.dp)
                .clip(shape)
                .background(if (selected) family.mid else family.pastel)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            if (!large) Glyph(glyph, null, tint = c.ballpoint, size = 20.dp)
            BasicText(
                label,
                style = style,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
            )
        }
        if (recording) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 4.dp)
                    .size(12.dp)
                    .decorative()
                    .clip(CircleShape)
                    .background(c.stickerWhite)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(DayColors.hanko),
            )
        }
    }
}

/* ---------------- Write ---------------- */

@Composable
private fun WriteTab(state: AddUiState, actions: AddActions, reveals: Reveals, feedback: Feedback?) {
    val review = state.review?.takeIf { it.source == ItemSource.AI_TEXT }
    val waiting = state.phase is AddPhase.Thinking && (state.phase as AddPhase.Thinking).kind == ItemSource.AI_TEXT
    val failed = (state.phase as? AddPhase.Failed)?.takeIf { review == null }
    if (review != null || waiting) {
        SentNote(state.sentText ?: state.text, onEdit = actions.onEditNote, enabled = !waiting)
        val clock = reveals.main
        ThinkingBox(
            steps = review?.steps,
            recipe = review?.recipe != null,
            clock = if (review != null) clock else null,
            family = targetFamily(review),
            feedback = feedback,
        )
        if (waiting) {
            PicnicButton(stringResource(R.string.add_stop), onClick = actions.onCancel, style = PicnicButtonStyle.Text, glyph = PicnicGlyph.Close)
        }
        if (review != null && (clock?.popped() != false)) {
            RecipeCard(review, review.mainRows, clock, onToggleKeep = actions.onToggleKeep, onEditPantry = actions.onEditPantry)
        }
    } else {
        NoteField(state.text, actions.onTextChange)
        if (failed != null) ErrorBlock(failed, actions)
    }
}

/** The note that was sent, read-only under review, with a pencil to change it (the review is dropped then). */
@Composable
private fun SentNote(text: String, onEdit: () -> Unit, enabled: Boolean) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.add_note_label), style = t.fieldLabel.copy(color = c.inkSoft))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = t.fieldValue.copy(color = c.ink), modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            if (enabled) PicnicIconButton(PicnicGlyph.Edit, stringResource(R.string.add_edit_note), onEdit)
        }
        FieldRule(focused = enabled)
    }
}

/** The thinking box's capsule wears the target aisle's mid: the aisle of the first kept row (melon while waiting). */
private fun targetFamily(review: AddReview?): StickerFamily {
    val row = review?.rows?.firstOrNull { it.keep && it.aisleIndex >= 0 } ?: return Families.melon
    return earth.cora.picnic.ui.theme.AisleLooks.of(row.aisleIndex + 1, row.style).family
}

/* ---------------- Snap ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnapTab(state: AddUiState, actions: AddActions, reveals: Reveals, feedback: Feedback?) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val review = state.review?.takeIf { it.source == ItemSource.PHOTO }
    val reading = state.phase is AddPhase.Thinking && (state.phase as AddPhase.Thinking).kind == ItemSource.PHOTO
    val failed = state.phase as? AddPhase.Failed
    val stacked = LocalDensity.current.fontScale >= 1.3f
    if (state.photo == null && review == null) {
        // No photo yet: an empty polaroid, the hint and the "Choose a photo" chip (the picker never needs a gesture).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!stacked) Polaroid(photo = null, reading = false)
            Text(
                stringResource(R.string.add_snap_hint),
                style = t.body.copy(fontSize = 15.sp, lineHeight = 21.sp, color = c.ink),
                modifier = Modifier.weight(1f),
            )
        }
        ChoosePhotoChip(actions.onChoosePhoto)
        if (failed != null) ErrorBlock(failed, actions)
        return
    }
    val clock = reveals.main
    if (stacked) {
        Polaroid(state.photo, reading = reading)
        PhotoLines(review?.lines.orEmpty(), reading = reading, clock = clock, modifier = Modifier.fillMaxWidth())
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Polaroid(state.photo, reading = reading)
            PhotoLines(review?.lines.orEmpty(), reading = reading, clock = clock, modifier = Modifier.weight(1f).padding(top = 6.dp))
        }
    }
    if (review == null) {
        if (failed != null) ErrorBlock(failed, actions)
        return
    }
    val order = AddRules.photoPopOrder(review.lines, review.mainRows.sortedBy { it.key }.map { it.key })
    PoppedTray(review.mainRows, order, clock, actions.onToggleKeep)
    if (clock?.done != false) {
        SectionDivider(major = true)
        SpeakAfterPhoto(state, actions, reveals, feedback)
    }
}

/** The visible "Choose a photo" chip (item 208): the picker never needs the long-press. */
@Composable
private fun ChoosePhotoChip(onClick: () -> Unit) {
    val c = PicnicTheme.colors
    Box(
        Modifier
            .testTag(CHOOSE_PHOTO_CHIP_TAG)
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChipFace(fill = Families.ramune.pastel) {
            Glyph(PicnicGlyph.Gallery, null, tint = c.ballpoint, size = 16.dp)
            Text(stringResource(R.string.add_snap_choose), style = PicnicTheme.type.label.copy(color = c.ballpoint), maxLines = 1)
        }
    }
}

/** Speak after Snap (D19): the prompt, the cassette, and the heard rows joining the same preview. */
@Composable
private fun SpeakAfterPhoto(state: AddUiState, actions: AddActions, reveals: Reveals, feedback: Feedback?) {
    val review = state.review ?: return
    val voicePhase = state.phase.takeIf { it is AddPhase.Recording || it is AddPhase.Transcribing } ?: AddPhase.Idle
    SpeakSection(
        phase = voicePhase,
        transcript = state.transcript,
        afterPhoto = true,
        heardNames = review.heardRows.map { it.item.name },
        onRecord = actions.onRecord,
        onStop = actions.onStopRecording,
    )
    HeardPart(state, actions, reveals, feedback, afterPhoto = true)
}

/** The voice note's thinking box and Heard tray (after a photo: the heard batch; alone: the review itself). */
@Composable
private fun HeardPart(state: AddUiState, actions: AddActions, reveals: Reveals, feedback: Feedback?, afterPhoto: Boolean) {
    val review = state.review
    val thinking = state.phase is AddPhase.Thinking && (state.phase as AddPhase.Thinking).kind == ItemSource.VOICE
    val failed = state.phase as? AddPhase.Failed
    if (thinking) {
        ThinkingBox(steps = null, recipe = false, clock = null, family = Families.melon, feedback = feedback)
        PicnicButton(stringResource(R.string.add_stop), onClick = actions.onCancel, style = PicnicButtonStyle.Text, glyph = PicnicGlyph.Close)
    } else if (afterPhoto && review?.heardId != null) {
        val clock = reveals.heard
        ThinkingBox(steps = review.heardSteps, recipe = false, clock = clock, family = targetFamily(review), feedback = feedback)
        if (clock?.popped() != false) HeardTray(review.heardRows, afterPhoto = true, clock = clock, onToggleKeep = actions.onToggleKeep)
    } else if (!afterPhoto && review != null && review.source == ItemSource.VOICE) {
        val clock = reveals.main
        ThinkingBox(steps = review.steps, recipe = review.recipe != null, clock = clock, family = targetFamily(review), feedback = feedback)
        if (clock?.popped() != false) HeardTray(review.mainRows, afterPhoto = false, clock = clock, onToggleKeep = actions.onToggleKeep)
    }
    if (failed != null) ErrorBlock(failed, actions)
}

/* ---------------- Speak ---------------- */

@Composable
private fun SpeakTab(state: AddUiState, actions: AddActions, reveals: Reveals, feedback: Feedback?) {
    val review = state.review
    val afterPhoto = review?.source == ItemSource.PHOTO
    val voicePhase = state.phase.takeIf { it is AddPhase.Recording || it is AddPhase.Transcribing } ?: AddPhase.Idle
    SpeakSection(
        phase = voicePhase,
        transcript = state.transcript,
        afterPhoto = afterPhoto,
        heardNames = (if (afterPhoto) review?.heardRows else review?.mainRows).orEmpty().map { it.item.name },
        onRecord = actions.onRecord,
        onStop = actions.onStopRecording,
    )
    HeardPart(state, actions, reveals, feedback, afterPhoto = afterPhoto)
}

/* ---------------- Failure ---------------- */

/**
 * The error state (rule 6, §6.12): the friendly message in hanko with the torn-paper glyph, inline (never a system
 * dialog), with "Try again", "Add as plain lines" and "Open Settings" as they apply.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorBlock(failed: AddPhase.Failed, actions: AddActions) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val message = failed.message ?: stringResource(failed.problem.copy())
    Column(Modifier.fillMaxWidth().testTag(ADD_ERROR_TAG), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Glyph(PicnicGlyph.TornPaper, null, tint = c.hanko, size = 20.dp, modifier = Modifier.padding(top = 1.dp))
            Text(message, style = t.label.copy(fontSize = 15.sp, lineHeight = 21.sp, color = c.hanko), modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (failed.retryable) PicnicButton(stringResource(R.string.add_retry), onClick = actions.onRetry, glyph = PicnicGlyph.Refresh)
            if (failed.canUsePlainLines) {
                PicnicButton(stringResource(R.string.add_plain_lines), onClick = actions.onUsePlainLines, style = PicnicButtonStyle.Secondary, glyph = PicnicGlyph.Edit)
            }
            if (failed.needsSettings) {
                PicnicButton(stringResource(R.string.add_open_settings), onClick = actions.onOpenSettings, style = PicnicButtonStyle.Secondary, glyph = PicnicGlyph.Settings)
            }
        }
    }
}

/** The app's own copy for each problem (core's AI errors bring their own friendly message). */
private fun AddProblem.copy(): Int = when (this) {
    AddProblem.AI -> R.string.add_problem_ai
    AddProblem.PHOTO_UNREADABLE -> R.string.add_problem_photo
    AddProblem.CAMERA_UNAVAILABLE -> R.string.add_problem_camera
    AddProblem.MIC_UNAVAILABLE -> R.string.add_problem_mic
    AddProblem.MIC_DENIED -> R.string.add_problem_mic_denied
    AddProblem.TOO_SHORT -> R.string.add_problem_too_short
    AddProblem.NOTHING_HEARD -> R.string.add_problem_nothing
    AddProblem.SAVE_FAILED -> R.string.add_problem_save
}

/* ------------------------------------------------------------------------------------------------
 * Footer.
 * ------------------------------------------------------------------------------------------------ */

/** What the sticky footer shows. */
private sealed interface FooterKind {
    /** "★ Stick all n on my list", or "Reading… n of m" while the reveal runs. */
    data class Stick(val popped: Int, val total: Int, val revealing: Boolean) : FooterKind

    /** Write: "As typed" and Send. */
    data object Write : FooterKind

    /** Snap with no photo yet: "Take a photo". */
    data object TakePhoto : FooterKind
}

private fun footerFor(state: AddUiState, reveals: Reveals): FooterKind? {
    val review = state.review
    if (review != null && review.showsOn(state.mode)) {
        val clocks = listOfNotNull(reveals.main, reveals.heard)
        val revealing = clocks.any { !it.done }
        val rows = if (reveals.heard != null && reveals.heard.done.not()) review.heardRows else review.mainRows
        val clock = if (reveals.heard != null && !reveals.heard.done) reveals.heard else reveals.main
        val popped = rows.count { clock?.rowShown(it.key) ?: true }
        return FooterKind.Stick(popped, rows.size, revealing)
    }
    return when (state.mode) {
        AddMode.WRITE -> if (state.phase is AddPhase.Failed) null else FooterKind.Write
        AddMode.SNAP -> when {
            state.photo == null && review == null -> FooterKind.TakePhoto
            // The photo is being read: "Reading…" waits where "Stick all" will be (§4.19).
            state.phase is AddPhase.Thinking -> FooterKind.Stick(0, 0, revealing = true)
            else -> null
        }
        AddMode.SPEAK -> null
    }
}

@Composable
private fun AddFooter(kind: FooterKind, state: AddUiState, actions: AddActions) {
    val busy = state.phase.busy || state.committing
    Row(
        Modifier.fillMaxWidth().testTag(ADD_FOOTER_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        when (kind) {
            is FooterKind.Stick -> {
                val kept = state.keptCount
                val label = when {
                    kind.revealing && kind.total == 0 -> stringResource(R.string.add_reading)
                    kind.revealing -> stringResource(R.string.add_reading_progress, kind.popped, kind.total)
                    kept == 0 -> stringResource(R.string.add_stick_none)
                    else -> pluralStringResource(R.plurals.add_stick, kept, kept)
                }
                PicnicButton(
                    label,
                    onClick = actions.onCommit,
                    enabled = !kind.revealing && !busy && kept > 0,
                    busy = kind.revealing || busy,
                    leadingStar = !kind.revealing,
                    modifier = Modifier.weight(1f),
                )
            }
            FooterKind.Write -> {
                val waiting = state.phase is AddPhase.Thinking
                if (!waiting) {
                    PicnicButton(
                        stringResource(R.string.add_as_typed),
                        onClick = actions.onAsTyped,
                        style = PicnicButtonStyle.Text,
                        enabled = state.text.isNotBlank() && !busy,
                    )
                    Spacer(Modifier.weight(1f))
                }
                PicnicButton(
                    if (waiting) stringResource(R.string.add_reading) else stringResource(R.string.add_send),
                    onClick = actions.onSend,
                    enabled = state.text.isNotBlank() && !busy,
                    busy = waiting,
                    glyph = if (waiting) null else PicnicGlyph.Envelope,
                    modifier = if (waiting) Modifier.weight(1f) else Modifier,
                )
            }
            FooterKind.TakePhoto -> PicnicButton(
                stringResource(R.string.add_snap_take),
                onClick = actions.onTakePhoto,
                glyph = PicnicGlyph.Polaroid,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

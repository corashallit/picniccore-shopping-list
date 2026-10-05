package earth.cora.picnic.ui.add

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.platform.CaptureKind
import earth.cora.picnic.ui.common.HankoTextButton
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.a11y.focusRingIndication
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.OnPaper
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicDimens
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
    /** "Open phone settings" (the microphone was turned off for good): Android's page for this app. */
    val onOpenAppSettings: () -> Unit = {},
    /** "Edit my pantry" (WP8's navigation); null hides the button. */
    val onEditPantry: (() -> Unit)? = null,
    val onRevealed: (Long) -> Unit = {},
    /** The thinking box's capsule popped for a reveal ("pon!" + CONFIRM, through the ViewModel's Feedback). */
    val onCapsulePop: (Long) -> Unit = {},
    /** "OK" under a review with nothing to switch on. */
    val onAcknowledge: () -> Unit = {},
    val onClose: () -> Unit = {},
    /** "Delete photo" / "Delete voice note" under a capture that could not be sent (§0.4, 5 October). */
    val onDiscardKept: () -> Unit = {},
)

/**
 * The Add sheet wired to [flows] (WP5 places it above the overlay layer): Back and the scrim close it, "Open Settings"
 * and "Edit my pantry" leave it to their screens (the review waits in the ViewModel: Back returns to the same card).
 * [onOpenSettings] must not close the sheet first: after a key error the photo or the recording waits there, and "Try
 * again" picks it up once the key is fixed. The camera, the picker and the microphone need the key first
 * ([AddFlows.takePhoto]).
 */
@Composable
fun AddSheetHost(flows: AddFlows, state: AddUiState, onOpenSettings: () -> Unit, onEditPantry: (() -> Unit)?) {
    val vm = flows.vm
    val context = LocalContext.current
    val actions = remember(flows, onOpenSettings, onEditPantry, context) {
        AddActions(
            onModeChange = vm::setMode,
            onTextChange = vm::setText,
            onSend = { vm.send(AddOrigin.SHEET) },
            onAsTyped = vm::addAsTyped,
            onEditNote = vm::editNote,
            onTakePhoto = flows::takePhoto,
            onChoosePhoto = flows::pickPhoto,
            onRecord = flows::recordVoice,
            onStopRecording = vm::stopRecording,
            onCancel = vm::cancel,
            onToggleKeep = vm::toggleKeep,
            onRetry = vm::retry,
            onUsePlainLines = vm::usePlainLines,
            onCommit = vm::commit,
            onOpenSettings = onOpenSettings,
            onOpenAppSettings = { openAppSettings(context) },
            onEditPantry = onEditPantry,
            onRevealed = vm::revealFinished,
            onCapsulePop = vm::capsulePopped,
            onAcknowledge = vm::acknowledge,
            onClose = vm::closeSheet,
            onDiscardKept = vm::discardKept,
        )
    }
    AddSheet(state, actions)
}

/** Android's settings page for this app (where a microphone turned off for good can be allowed again). */
private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // A phone without that page: the error's words still say where the switch is.
    }
}

/**
 * The height of the screen's app bar under the status bar, as laid out. WP5 provides the List screen's measured app bar
 * (it grows with large text, §15); without it the sheet keeps clear of the 72 dp app bar ([PicnicDimens.appBar]).
 */
val LocalAppBarHeight = compositionLocalOf<Dp?> { null }

/**
 * The stateless sheet: [LetterSetSheet] (visible while [AddUiState.sheetOpen]) with the tabs, the content and the footer.
 * A tall sheet stops under the app bar ([LocalAppBarHeight]), so the list's title, LCD and More stay visible behind the
 * scrim (§4.16).
 */
@Composable
fun AddSheet(state: AddUiState, actions: AddActions, modifier: Modifier = Modifier) {
    val reveals = rememberReveals(state, actions)
    LetterSetSheet(
        visible = state.sheetOpen,
        onDismissRequest = actions.onClose,
        modifier = modifier,
        footer = footerFor(state, reveals)?.let { footer -> { AddFooter(footer, state, actions) } },
        topClearance = LocalAppBarHeight.current ?: PicnicTheme.dimens.appBar,
    ) {
        AddSheetContent(state, actions, reveals)
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
        val heardPlan = remember(heardId, reduced) {
            // Only the latest batch fans out; heard rows from an earlier voice note stay where they were.
            val fresh = review.heardRows.map { it.key }.filter { it in review.heardFresh }
            val plan = RevealPlan.thinking(review.heardSteps.size, fresh, reduced)
            plan.copy(rowAt = review.heardRows.filter { it.key !in review.heardFresh }.associate { it.key to 0L } + plan.rowAt)
        }
        rememberRevealClock(heardId, heardPlan, played = heardId in review.revealed, onDone = actions.onRevealed)
    } else {
        null
    }
    return Reveals(main, heard)
}

/** The rows of a text or voice review in the order they fan out: the card's trays, top to bottom. */
internal fun displayOrder(review: AddReview): List<Int> =
    if (review.source == ItemSource.VOICE) review.mainRows.map { it.key } else groupByAisle(review.mainRows).flatten().map { it.key }

/* ------------------------------------------------------------------------------------------------
 * Content.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun ColumnScope.AddSheetContent(state: AddUiState, actions: AddActions, reveals: Reveals) {
    AddTabs(state.mode, recording = state.recording, onModeChange = actions.onModeChange, enabled = state::tabEnabled)
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (state.mode) {
            AddMode.WRITE -> WriteTab(state, actions, reveals)
            AddMode.SNAP -> SnapTab(state, actions, reveals)
            AddMode.SPEAK -> SpeakTab(state, actions, reveals)
        }
    }
}

/**
 * The index tabs (§4.16): Write · Snap · Speak in lemon, ramune and sakura pastels, 38 dp tall, Zen Maru 700 14 sp with
 * a 20 dp glyph; the selected tab is 6 dp taller (44 dp), mid fill, DynaPuff 16 sp. A 2 dp rule under them. Role.Tab.
 * A REC dot sits on Speak while recording. The faces grow with the text (never below 38 / 44 dp) and the labels never go
 * under 13 sp (§2.7, §15); the glyph steps aside from font scale 1.5. A tab that would do nothing (something runs, or
 * its mode would replace an open review) is dimmed like a disabled pill ([enabled]). Pressed: the highlighter band
 * behind the label. Each tab is a 48 dp target (§15): the faces stay as drawn at the bottom and the target reaches up
 * into the blank sheet above them, so the row sits where 44 dp tabs sit.
 */
@Composable
fun AddTabs(
    mode: AddMode,
    recording: Boolean,
    onModeChange: (AddMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (AddMode) -> Boolean = { true },
) {
    val c = PicnicTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .testTag(ADD_TABS_TAG)
            // The targets are 4 dp taller than the selected face (48 vs 44 dp): that part reaches above the row's place
            // in the sheet (its blank top margin) instead of pushing the sheet's content down.
            .layout { measurable, constraints ->
                val reach = TAB_REACH.roundToPx()
                val p = measurable.measure(constraints)
                layout(p.width, (p.height - reach).coerceAtLeast(0)) { p.place(0, -reach) }
            },
    ) {
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
            for ((m, tab) in TABS) {
                AddTab(
                    label = stringResource(tab.label),
                    glyph = tab.glyph,
                    family = tab.family,
                    selected = mode == m,
                    recording = recording && m == AddMode.SPEAK,
                    enabled = mode == m || enabled(m),
                    onClick = { onModeChange(m) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private class TabLook(val label: Int, val glyph: PicnicGlyph, val family: StickerFamily)

private val TABS: List<Pair<AddMode, TabLook>> = listOf(
    AddMode.WRITE to TabLook(R.string.add_tab_write, PicnicGlyph.Edit, Families.lemon),
    AddMode.SNAP to TabLook(R.string.add_tab_snap, PicnicGlyph.Polaroid, Families.ramune),
    AddMode.SPEAK to TabLook(R.string.add_tab_speak, PicnicGlyph.Cassette, Families.sakura),
)

/** Test tag of a tab's label (its pressed band and size checks). */
const val ADD_TAB_LABEL_TAG: String = "add_tab_label"

@Composable
private fun AddTab(
    label: String,
    glyph: PicnicGlyph,
    family: StickerFamily,
    selected: Boolean,
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    val recordingText = stringResource(R.string.add_tab_recording)
    val large = LocalDensity.current.fontScale >= VERY_LARGE_TEXT
    // A dimmed tab looks like a disabled pill (§4.6): the disabled face and its soft words (Day: the backing and
    // ballpointSoft; Glow: the rule navy and lavender), its white frame, no shadow.
    val ink = if (enabled) c.ballpoint else c.onDisabledFace
    val face = when {
        selected -> family.mid
        enabled -> family.pastel
        else -> c.disabledFace
    }
    val style = if (selected) t.secondaryButton.copy(color = ink) else t.label.copy(fontSize = 14.sp, lineHeight = 18.sp, color = ink)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            // §15: a 48 dp target; the face below keeps its drawn height at the bottom.
            .heightIn(min = TAB_TARGET)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, interactionSource = interaction, indication = focusRingIndication(shape), onClick = onClick)
            .semantics { if (recording) stateDescription = recordingText },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (enabled) Modifier.hardShadow(shape, PicnicTheme.shadows) else Modifier)
                    .clip(shape)
                    .background(c.stickerWhite)
                    .padding(start = 3.dp, end = 3.dp, top = 3.dp)
                    .clip(shape)
                    .background(face)
                    .padding(horizontal = if (large) 2.dp else 6.dp)
                    // 38 dp at rest (3 dp border + 35 dp); the selected tab is 6 dp taller, its label centred. Both grow
                    // with the label (never clipped) and keep the 6 dp step.
                    .padding(vertical = if (selected) TAB_RISE / 2 else 0.dp)
                    .heightIn(min = TAB_FACE),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            ) {
                // The face is the label's paper: its pressed band is the face's own (the highlighter on a pastel or mid face).
                OnPaper(face) {
                    if (!large) Glyph(glyph, null, tint = ink, size = 20.dp)
                    BasicText(
                        label,
                        style = style,
                        maxLines = 1,
                        // Large text shrinks the label to fit its tab, never under the 13 sp floor (§2.7).
                        autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                        modifier = Modifier.testTag(ADD_TAB_LABEL_TAG).pressedBand(pressed, PicnicTheme.colors.pressedBand),
                    )
                }
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
                        .background(StickerHanko),
                )
            }
        }
    }
}

private val TAB_FACE = 35.dp
private val TAB_RISE = 6.dp

/** §15: every target is ≥ 48 dp. */
private val TAB_TARGET = 48.dp

/** How far the targets reach above the selected face (3 dp border + 35 + 6 = 44 dp) into the sheet's top margin. */
private val TAB_REACH = TAB_TARGET - (TAB_FACE + 3.dp + TAB_RISE)

/* ---------------- Write ---------------- */

@Composable
private fun WriteTab(state: AddUiState, actions: AddActions, reveals: Reveals) {
    // A note's review: the answer's card, or its plain lines after a failed request (TYPED, no AI ran).
    val review = state.review?.takeIf { it.source == ItemSource.AI_TEXT || it.source == ItemSource.TYPED }
    val waiting = (state.phase as? AddPhase.Thinking)?.kind == ItemSource.AI_TEXT
    val failed = (state.phase as? AddPhase.Failed)?.takeIf { review == null }
    if (review != null || waiting) {
        SentNote(state.sentText ?: state.text, onEdit = actions.onEditNote, enabled = !waiting)
        val clock = reveals.main
        // Plain lines claim no reading: no thinking box, the rows fan straight into the card.
        if (review?.plain != true) {
            ThinkingBox(
                steps = review?.steps,
                recipe = review?.recipe != null,
                clock = if (review != null) clock else null,
                family = targetFamily(review),
                onPop = actions.onCapsulePop,
            )
        }
        if (waiting) {
            PicnicButton(stringResource(R.string.add_stop), onClick = actions.onCancel, style = PicnicButtonStyle.Text, glyph = PicnicGlyph.Close)
        }
        if (review != null && (clock?.popped() != false)) {
            RecipeCard(review, review.mainRows, clock, onToggleKeep = actions.onToggleKeep, onEditPantry = actions.onEditPantry)
            // A voice note spoken after the note joined the same review (D19): its rows, under the card.
            if (review.heardRows.isNotEmpty()) {
                HeardTray(review.heardRows, afterReview = true, clock = reveals.heard, onToggleKeep = actions.onToggleKeep)
            }
        }
    } else {
        NoteField(state.text, actions.onTextChange)
        if (failed != null) ErrorBlock(failed, actions, kept = null)
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
        // Read-only under review: the resting underline (the strawberry trace means the field has focus).
        FieldRule(focused = false)
    }
}

/** The thinking box's capsule wears the target aisle's mid: the aisle of the first kept row (melon while waiting). */
private fun targetFamily(review: AddReview?): StickerFamily {
    val row = review?.rows?.firstOrNull { it.keep && it.aisleIndex >= 0 } ?: return Families.melon
    return earth.cora.picnic.ui.theme.AisleLooks.of(row.aisleIndex + 1, row.style).family
}

/**
 * What the review says above its rows: the model's own note when it is worth showing (rule 4), else, when nothing came
 * back to add, a plain line saying so ("Nothing to add from that note."), so the card above "OK" is never blank.
 */
@Composable
internal fun reviewWords(review: AddReview): String? = review.message ?: if (review.mainRows.isEmpty()) {
    stringResource(if (review.source == ItemSource.PHOTO) R.string.add_nothing_photo else R.string.add_nothing_note)
} else {
    null
}

/** The model's own note on the answer, when it is worth showing (rule 4): one quiet line above the stickers. */
@Composable
private fun ReviewMessage(message: String?) {
    if (message.isNullOrBlank()) return
    val c = PicnicTheme.colors
    Text(message, style = PicnicTheme.type.body.copy(fontSize = 15.sp, lineHeight = 21.sp, color = c.inkSoft), modifier = Modifier.fillMaxWidth())
}

/* ---------------- Snap ---------------- */

@Composable
private fun SnapTab(state: AddUiState, actions: AddActions, reveals: Reveals) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val review = state.review?.takeIf { it.source == ItemSource.PHOTO }
    val reading = (state.phase as? AddPhase.Thinking)?.kind == ItemSource.PHOTO
    val failed = state.phase as? AddPhase.Failed
    val stacked = LocalDensity.current.fontScale >= LARGE_TEXT
    if (state.photo == null && review == null) {
        // No photo yet (or one that could not be opened): an empty polaroid, the hint and the "Choose a photo" chip (the
        // picker never needs a gesture).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!stacked) Polaroid(photo = null, reading = false)
            Text(
                stringResource(R.string.add_snap_hint),
                style = t.body.copy(fontSize = 15.sp, lineHeight = 21.sp, color = c.ink),
                modifier = Modifier.weight(1f),
            )
        }
        ChoosePhotoChip(actions.onChoosePhoto)
        if (failed != null) ErrorBlock(failed, actions, kept = null)
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
        if (failed != null) {
            ErrorBlock(failed, actions, kept = state.kept?.takeIf { it == CaptureKind.PHOTO })
            // The photo could not be read: another one is a tap away (the footer offers the camera).
            ChoosePhotoChip(actions.onChoosePhoto)
        }
        return
    }
    if (clock?.done != false) ReviewMessage(reviewWords(review))
    if (review.mainRows.isNotEmpty()) {
        val order = AddRules.photoPopOrder(review.lines, review.mainRows.sortedBy { it.key }.map { it.key })
        PoppedTray(review.mainRows, order, clock, actions.onToggleKeep)
    }
    if (clock?.done != false) {
        SectionDivider(major = true)
        SpeakAfterReview(state, actions, reveals)
    }
}

/** The visible "Choose a photo" chip (item 208): the picker never needs the long-press. */
@Composable
private fun ChoosePhotoChip(onClick: () -> Unit) {
    AddChip(
        stringResource(R.string.add_snap_choose),
        PicnicGlyph.Gallery,
        onClick = onClick,
        modifier = Modifier.testTag(CHOOSE_PHOTO_CHIP_TAG),
    )
}

/** Speak after a review (D19): the prompt, the cassette, and the heard rows joining the same preview. */
@Composable
private fun SpeakAfterReview(state: AddUiState, actions: AddActions, reveals: Reveals) {
    val review = state.review ?: return
    val voicePhase = state.phase.takeIf { it is AddPhase.Recording || it is AddPhase.Transcribing } ?: AddPhase.Idle
    SpeakSection(
        phase = voicePhase,
        transcript = state.transcript,
        afterReview = true,
        heardNames = heardNames(review),
        onRecord = actions.onRecord,
        onStop = actions.onStopRecording,
        onCancel = actions.onCancel,
    )
    HeardPart(state, actions, reveals)
}

/** The names the transcript highlights: a voice review's rows, else the heard rows that joined the review. */
private fun heardNames(review: AddReview): List<String> =
    (if (review.source == ItemSource.VOICE) review.rows else review.heardRows).map { it.item.name }

/**
 * The voice part of the sheet: while a transcript is read, the thinking box (and Stop); a voice review's own thinking
 * box and Heard tray; then the latest voice batch that joined a review (D19: "Heard · 2 more stickers").
 */
@Composable
private fun HeardPart(state: AddUiState, actions: AddActions, reveals: Reveals) {
    val review = state.review
    val thinking = (state.phase as? AddPhase.Thinking)?.kind == ItemSource.VOICE
    val failed = state.phase as? AddPhase.Failed
    if (review != null && review.source == ItemSource.VOICE) {
        val clock = reveals.main
        // A voice note's plain lines (after a failed request) claim no reading: no thinking box.
        if (!review.plain) {
            ThinkingBox(steps = review.steps, recipe = review.recipe != null, clock = clock, family = targetFamily(review), onPop = actions.onCapsulePop)
        }
        if (clock?.popped() != false) {
            ReviewMessage(reviewWords(review))
            if (review.mainRows.isNotEmpty()) HeardTray(review.mainRows, afterReview = false, clock = clock, onToggleKeep = actions.onToggleKeep)
        }
    }
    if (thinking) {
        ThinkingBox(steps = null, recipe = false, clock = null, family = Families.melon)
        PicnicButton(stringResource(R.string.add_stop), onClick = actions.onCancel, style = PicnicButtonStyle.Text, glyph = PicnicGlyph.Close)
    } else if (review?.heardId != null) {
        val clock = reveals.heard
        // Heard rows from plain lines (a failed request after a review) claim no reading either.
        val plainHeard = review.heardRows.filter { it.key in review.heardFresh }.let { fresh -> fresh.isNotEmpty() && fresh.all { it.typed } }
        if (!plainHeard) {
            ThinkingBox(steps = review.heardSteps, recipe = false, clock = clock, family = targetFamily(review), onPop = actions.onCapsulePop)
        }
        if (clock?.popped() != false && review.heardRows.isNotEmpty()) {
            HeardTray(review.heardRows, afterReview = true, clock = clock, onToggleKeep = actions.onToggleKeep)
        }
    }
    if (failed != null) ErrorBlock(failed, actions, kept = state.kept?.takeIf { it == CaptureKind.VOICE })
}

/* ---------------- Speak ---------------- */

@Composable
private fun SpeakTab(state: AddUiState, actions: AddActions, reveals: Reveals) {
    val review = state.review
    val voicePhase = state.phase.takeIf { it is AddPhase.Recording || it is AddPhase.Transcribing } ?: AddPhase.Idle
    SpeakSection(
        phase = voicePhase,
        transcript = state.transcript,
        afterReview = review != null,
        heardNames = review?.let(::heardNames).orEmpty(),
        onRecord = actions.onRecord,
        onStop = actions.onStopRecording,
        onCancel = actions.onCancel,
    )
    HeardPart(state, actions, reveals)
}

/* ---------------- Failure ---------------- */

/**
 * The error state (rule 6, §6.12): the friendly message in hanko with the torn-paper glyph, inline (never a system
 * dialog), with "Try again", "Add as plain lines" and "Open Settings" as they apply. At a large font a pill's label
 * wraps and the pill grows round it (§4.6, item 337): the same words at every size. Under a photo or voice note that is
 * [kept] on the phone (§0.4, 5 October), a hanko text button deletes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorBlock(failed: AddPhase.Failed, actions: AddActions, kept: CaptureKind?) {
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
            // The microphone was turned off: the words say where to allow it, and this takes her there (the way on, so
            // the main pill; there is no Record footer then).
            if (failed.problem == AddProblem.MIC_DENIED) {
                PicnicButton(stringResource(R.string.add_open_phone_settings), onClick = actions.onOpenAppSettings, glyph = PicnicGlyph.Settings)
            }
            if (kept != null) {
                HankoTextButton(
                    stringResource(if (kept == CaptureKind.PHOTO) R.string.add_kept_delete_photo else R.string.add_kept_delete_voice),
                    onClick = actions.onDiscardKept,
                    glyph = PicnicGlyph.Delete,
                    modifier = Modifier.testTag(ADD_DISCARD_KEPT_TAG),
                )
            }
        }
    }
}

/** The "Delete photo" / "Delete voice note" button under a kept capture (tests). */
const val ADD_DISCARD_KEPT_TAG: String = "add_discard_kept"

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
    AddProblem.KEPT_PHOTO -> R.string.add_problem_kept_photo
    AddProblem.KEPT_VOICE -> R.string.add_problem_kept_voice
}

/* ------------------------------------------------------------------------------------------------
 * Footer.
 * ------------------------------------------------------------------------------------------------ */

/** What the sticky footer shows. */
private sealed interface FooterKind {
    /**
     * "★ Stick all n on my list". While a photo's reveal runs, "Reading… n of m" ([popped] of [total], §4.19); while a
     * note's or a voice note's stickers fan out, the same "Stick all" pill, disabled until they have landed.
     */
    data class Stick(val popped: Int, val total: Int, val revealing: Boolean, val reading: Boolean) : FooterKind

    /** A review with nothing to switch on (an empty answer, or only names already on her list): "OK". */
    data object Acknowledge : FooterKind

    /** Write: "As typed" and Send. */
    data object Write : FooterKind

    /**
     * Write after a request that never reached OpenAI (offline, rule 2b): "As typed" alone, so the note can still go on
     * the list at once (the error above offers Try again and Add as plain lines).
     */
    data object AsTyped : FooterKind

    /** Snap with no photo yet, or after a photo that could not be read: "Take a photo". */
    data object TakePhoto : FooterKind

    /** Speak with nothing recorded yet, or after a voice note that failed: "Record a voice note" (the corner sticker's twin). */
    data object Record : FooterKind
}

private fun footerFor(state: AddUiState, reveals: Reveals): FooterKind? {
    val review = state.review
    if (review != null && review.showsOn(state.mode)) {
        if (review.nothingToKeep) return FooterKind.Acknowledge
        val clocks = listOfNotNull(reveals.main, reveals.heard)
        val revealing = clocks.any { !it.done }
        val heardRunning = reveals.heard != null && !reveals.heard.done
        val rows = if (heardRunning) review.heardRows else review.mainRows
        val clock = if (heardRunning) reveals.heard else reveals.main
        val popped = rows.count { clock?.rowShown(it.key) ?: true }
        // Only a photo is read line by line (§4.19); a note's answer is already here, so its footer never says "Reading".
        val reading = revealing && review.source == ItemSource.PHOTO && !heardRunning
        return FooterKind.Stick(popped, rows.size, revealing, reading)
    }
    return when (state.mode) {
        AddMode.WRITE -> when {
            review != null -> null
            (state.phase as? AddPhase.Failed)?.offline == true -> FooterKind.AsTyped
            state.phase is AddPhase.Failed -> null
            else -> FooterKind.Write
        }
        AddMode.SNAP -> when {
            // No camera app: "Take a photo" could only fail again ("Choose a photo" stays in the tab).
            (state.phase as? AddPhase.Failed)?.problem == AddProblem.CAMERA_UNAVAILABLE -> null
            state.photo == null && review == null -> FooterKind.TakePhoto
            // A photo that could not be read (or an answer that failed): another one.
            review == null && state.phase is AddPhase.Failed -> FooterKind.TakePhoto
            // The photo is being read: "Reading…" waits where "Stick all" will be (§4.19).
            state.phase is AddPhase.Thinking -> FooterKind.Stick(0, 0, revealing = true, reading = true)
            else -> null
        }
        AddMode.SPEAK -> when (val phase = state.phase) {
            AddPhase.Idle -> FooterKind.Record
            // A key problem ("Open Settings") or the microphone turned off ("Open phone settings"): that is the way on;
            // recording again would only be refused (Android may not even ask again).
            is AddPhase.Failed -> if (phase.needsSettings || phase.problem == AddProblem.MIC_DENIED) null else FooterKind.Record
            else -> null
        }
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
                    kind.reading && kind.total == 0 -> stringResource(R.string.add_reading)
                    kind.reading -> stringResource(R.string.add_reading_progress, kind.popped, kind.total)
                    kept == 0 -> stringResource(R.string.add_stick_none)
                    else -> pluralStringResource(R.plurals.add_stick, kept, kept)
                }
                // The whole label at any font scale: the pill wraps it onto 2 lines rather than shorten it (§4.6).
                PicnicButton(
                    label,
                    onClick = actions.onCommit,
                    enabled = !kind.revealing && !busy && kept > 0,
                    busy = kind.reading || busy,
                    leadingStar = !kind.reading,
                    modifier = Modifier.weight(1f),
                )
            }
            FooterKind.Acknowledge -> PicnicButton(
                stringResource(R.string.add_ok),
                onClick = actions.onAcknowledge,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
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
            FooterKind.AsTyped -> PicnicButton(
                stringResource(R.string.add_as_typed),
                onClick = actions.onAsTyped,
                style = PicnicButtonStyle.Text,
                enabled = state.text.isNotBlank() && !busy,
            )
            FooterKind.TakePhoto -> PicnicButton(
                stringResource(R.string.add_snap_take),
                onClick = actions.onTakePhoto,
                glyph = PicnicGlyph.Polaroid,
                modifier = Modifier.weight(1f),
            )
            FooterKind.Record -> PicnicButton(
                stringResource(R.string.add_speak_record),
                onClick = actions.onRecord,
                enabled = !busy,
                glyph = PicnicGlyph.Cassette,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

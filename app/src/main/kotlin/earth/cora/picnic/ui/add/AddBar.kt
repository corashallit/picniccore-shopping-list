package earth.cora.picnic.ui.add

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.theme.AddBarButton
import earth.cora.picnic.ui.theme.AddBarButtonKind
import earth.cora.picnic.ui.theme.BittenEdgeShape
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicFab
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.PicnicText
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.ui.theme.WashiTape
import earth.cora.picnic.ui.theme.drawPerforationRims
import earth.cora.picnic.ui.theme.rememberLoopTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The docked envelope add bar and the FAB it folds into (docs/DESIGN_STICKER.md §6.2, §4.6, §6.1 "Collapsed add bar",
 * items 115, 116, 199, 200, 208). Owner: WP7; WP5 places [AddDock] (or [AddBar] and [AddFab]) under the list.
 */

/** Test tags. */
const val ADD_BAR_TAG: String = "add_bar"
const val ADD_FIELD_TAG: String = "add_bar_field"
const val ADD_FAB_TAG: String = "add_fab"
const val ADD_CAPSULE_TAG: String = "add_bar_capsule"

/** What the add bar shows (a slice of [AddUiState]). */
@Stable
data class AddBarUi(val text: String = "", val waiting: Boolean = false, val enabled: Boolean = true)

/** The bar's slice of the add flows' state. */
fun AddUiState.barUi(): AddBarUi = AddBarUi(text = text, waiting = waiting && origin == AddOrigin.BAR, enabled = !committing)

/**
 * The 84 dp envelope add bar (§6.2): pad white with a perforated top edge (4 dp bites every 10 dp, a 55 % periwinkle rim
 * under each), the season's washi at the left end (56 × 18 dp, −4°, fluttering), the doodle "♡ picnic day ☆", the ruled
 * field ("Write a note…" over the example line "“pancakes from scratch”") and three 52 dp round stickers with line
 * glyphs: polaroid camera (ramune, "Snap a photo of a list"; long-press or its TalkBack action "Choose a photo" opens
 * the picker), cassette (sakura, "Speak a voice note"), envelope (strawberry, "Send"). Enter or Send submits. While
 * waiting (rule 3) Send is a wobbling gashapon capsule ([onStop] cancels) and the field is read-only. A 12 dp pad fade
 * sits above it. It grows with the font (never clips).
 */
@Composable
fun AddBar(
    state: AddBarUi,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSnap: () -> Unit,
    onChoosePhoto: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    onStop: () -> Unit = {},
    tape: WashiPattern = WashiPattern.SakuraStripe,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
) {
    val c = PicnicTheme.colors
    val choose = stringResource(R.string.add_bar_choose_photo)
    val shape = remember { BittenEdgeShape(bite = 4.dp, every = 10.dp) }
    val keyboard = LocalSoftwareKeyboardController.current
    val longPress = LocalViewConfiguration.current.longPressTimeoutMillis
    val haptic = LocalHapticFeedback.current
    val choosePhotoNow by rememberUpdatedState(onChoosePhoto)
    val choosePhoto = remember { { choosePhotoNow() } }
    LaunchedEffect(state.waiting) {
        // Read-only while waiting: the keyboard goes away (the capsule says what is happening).
        if (state.waiting) keyboard?.hide()
    }
    Box(
        modifier
            .fillMaxWidth()
            .testTag(ADD_BAR_TAG)
            .drawBehind {
                // The 12 dp pad fade above the bar (§6.1).
                val fade = 12.dp.toPx()
                drawRect(Brush.verticalGradient(listOf(c.pad.copy(alpha = 0f), c.pad), startY = -fade, endY = 0f), Offset(0f, -fade), Size(size.width, fade))
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PicnicTheme.dimens.addBar)
                .clip(shape)
                .background(c.pad)
                .drawBehind { drawPerforationRims() },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PicnicTheme.dimens.addBar)
                    // 20 + 60 dp touch targets + 4 = the 84 dp bar (the doodle sits in the top 20 dp).
                    .padding(start = 14.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AddBarField(
                    text = state.text,
                    readOnly = state.waiting || !state.enabled,
                    onTextChange = onTextChange,
                    onSend = onSend,
                    focusRequester = focusRequester,
                    onFocusChange = onFocusChange,
                    modifier = Modifier.weight(1f).padding(end = 6.dp, bottom = 4.dp),
                )
                AddBarButton(
                    AddBarButtonKind.Snap,
                    stringResource(R.string.add_bar_snap),
                    onClick = onSnap,
                    enabled = !state.waiting,
                    modifier = Modifier
                        .longPressShortcut(enabled = !state.waiting, timeoutMillis = longPress) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            choosePhoto()
                        }
                        .semantics { customActions = listOf(CustomAccessibilityAction(choose) { choosePhoto(); true }) },
                )
                AddBarButton(AddBarButtonKind.Speak, stringResource(R.string.add_bar_speak), onClick = onSpeak, enabled = !state.waiting)
                if (state.waiting) {
                    WaitingCapsule(onStop)
                } else {
                    AddBarButton(AddBarButtonKind.Send, stringResource(R.string.add_bar_send), onClick = onSend, enabled = state.enabled)
                }
            }
            // The doodle sits above the field, clear of every text (decorative, Hachi Maru: the list's third family).
            PicnicText(
                stringResource(R.string.add_bar_doodle),
                style = PicnicTheme.type.doodle.copy(fontSize = PicnicTheme.type.caption.fontSize, lineHeight = PicnicTheme.type.caption.lineHeight),
                maxLines = 1,
                modifier = Modifier
                    .decorative()
                    .padding(start = 76.dp, top = 5.dp)
                    .graphicsLayer { rotationZ = -4f },
            )
        }
        // The season's washi at the left end, over the perforation.
        WashiTape(
            tape,
            Modifier.offset(x = 4.dp, y = (-5).dp),
            length = 56.dp,
            height = 18.dp,
            rotation = -4f,
            alpha = 0.9f,
            flutter = true,
        )
    }
}

/**
 * The add-bar field (§4.8): ruled, no box; "Write a note…" over the example line (13.5 sp, ballpointSoft); a 2 dp
 * ballpointSoft underline that becomes 3 dp strawberry trace with a 13 dp puffy heart at its left end on focus. One line;
 * Enter sends. TalkBack: "Write a note". Read-only while waiting.
 */
@Composable
private fun AddBarField(
    text: String,
    readOnly: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val label = stringResource(R.string.add_bar_field)
    var focused by remember { mutableStateOf(false) }
    val focusNow by rememberUpdatedState(onFocusChange)
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        readOnly = readOnly,
        singleLine = true,
        textStyle = t.fieldValue.copy(color = if (readOnly) c.inkSoft else c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        modifier = modifier
            .testTag(ADD_FIELD_TAG)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                focusNow(it.isFocused)
            }
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Column {
                Box(Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Column(Modifier.clearAndSetSemantics { }) {
                            Text(stringResource(R.string.add_bar_placeholder), style = t.fieldValue.copy(color = c.inkSoft), maxLines = 1)
                            Text(
                                stringResource(R.string.add_bar_example),
                                style = t.itemDetail.copy(color = c.inkSoft),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    inner()
                }
                FieldRule(focused = focused && !readOnly)
            }
        },
    )
}

/** The ruled field's underline: 2 dp ballpointSoft at rest, 3 dp strawberry trace + a 13 dp puffy heart on focus. */
@Composable
internal fun FieldRule(focused: Boolean, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    Box(modifier.fillMaxWidth().height(12.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val w = (if (focused) 3.dp else 2.dp).toPx()
            val y = 3.dp.toPx() + w / 2f
            drawLine(if (focused) Families.strawberry.trace else c.inkSoft, Offset(0f, y), Offset(size.width, y), w, cap = StrokeCap.Round)
        }
        if (focused) DecorSticker("heart", Modifier.offset(x = (-4).dp, y = (-3).dp), size = 13.dp, shadow = false)
    }
}

/**
 * The Write tab's note (§6.2, §4.8): "Note:" over a ruled field that wraps (no box, no extra rules over the sheet's own
 * lines), "Write a note…" and the example line while empty, the underline (focus: 3 dp strawberry trace + a puffy
 * heart). TalkBack: "Note".
 */
@Composable
internal fun NoteField(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val label = stringResource(R.string.add_note_field)
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.add_note_label), style = t.fieldLabel.copy(color = c.inkSoft), modifier = Modifier.clearAndSetSemantics { })
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = t.fieldValue.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            minLines = 1,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(NOTE_FIELD_TAG)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Column {
                    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Column(Modifier.clearAndSetSemantics { }) {
                                Text(stringResource(R.string.add_bar_placeholder), style = t.fieldValue.copy(color = c.inkSoft))
                                Text(stringResource(R.string.add_bar_example), style = t.itemDetail.copy(color = c.inkSoft))
                            }
                        }
                        inner()
                    }
                    FieldRule(focused = focused)
                }
            },
        )
    }
}

/** Test tag of the Write tab's note field. */
const val NOTE_FIELD_TAG: String = "add_note_field"

/**
 * Rule 3: while the request is out, Send is a 52 dp gashapon capsule wobbling ±8° on threes (still under reduced
 * motion). Tapping it stops the request (TalkBack "Stop reading", state "Reading your note…").
 */
@Composable
private fun WaitingCapsule(onStop: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val pose = if (reduced) 1 else ((t / PicnicMotion.gashaponPoseMillis) % 3).toInt()
    val stop = stringResource(R.string.add_bar_stop)
    val reading = stringResource(R.string.add_thinking_reading)
    Box(
        Modifier
            .size(60.dp)
            .testTag(ADD_CAPSULE_TAG)
            .clickable(role = Role.Button, onClick = onStop)
            .semantics {
                contentDescription = stop
                stateDescription = reading
            },
        contentAlignment = Alignment.Center,
    ) {
        ItemIcon(
            IconRef(IconCatalog.CAPSULE),
            contentDescription = null,
            size = 52.dp,
            aisleIndex = 0,
            modifier = Modifier.graphicsLayer { rotationZ = PicnicMotion.gashaponPoses[pose].rotation },
        )
    }
}

/**
 * A long-press shortcut that never steals the tap (§6.2, item 208: the picker's shortcut on the camera button; the
 * chip and the TalkBack action are the ways that need no gesture). Watches the Initial pass: past [timeoutMillis] it
 * fires [onLongPress] and consumes the rest of the gesture, so the button's own click is cancelled.
 */
internal fun Modifier.longPressShortcut(enabled: Boolean, timeoutMillis: Long, onLongPress: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(timeoutMillis) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val lifted = withTimeoutOrNull(timeoutMillis) {
                    var up = false
                    while (!up) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        up = change == null || !change.pressed
                    }
                    true
                }
                if (lifted == null) {
                    onLongPress()
                    // Swallow the rest so the click never fires as well.
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }
    }

/* ------------------------------------------------------------------------------------------------
 * The FAB and the collapse rules (§6.1 "Collapsed add bar", item 116).
 * ------------------------------------------------------------------------------------------------ */

/**
 * When the add bar folds into the FAB (§6.1): scrolling down ≥ 70 dp accumulated past 140 dp from the top folds it;
 * scrolling up 40 dp, reaching the top or tapping the FAB brings it back. Programmatic scrolls (the auto-open of the next
 * aisle) are ignored for 1.2 s ([ignoreProgrammaticScroll]). It never folds while the field is focused or a sheet is
 * open. Put [nestedScrollConnection] on the list's parent; [atTop] reports the list at its top.
 */
@Stable
class AddBarCollapseState(private val density: Float, private val clock: () -> Long = SystemClock::uptimeMillis) {
    var collapsed by mutableStateOf(false)
        private set

    /** The bar's field has focus (it never folds then). */
    var fieldFocused by mutableStateOf(false)

    /** A sheet is open (it never folds then). */
    var sheetOpen by mutableStateOf(false)

    private var fromTop by mutableFloatStateOf(0f)
    private var downRun = 0f
    private var upRun = 0f
    private var ignoreUntil = Long.MIN_VALUE

    private val foldAfter get() = FOLD_AFTER_DP * density
    private val foldPast get() = FOLD_PAST_DP * density
    private val unfoldAfter get() = UNFOLD_AFTER_DP * density

    /** The list scrolled by [dy] px: positive = towards the end (content moving up). */
    fun onScrolled(dy: Float) {
        if (dy == 0f) return
        val before = fromTop
        fromTop = (fromTop + dy).coerceAtLeast(0f)
        if (clock() < ignoreUntil) return
        if (dy > 0f) {
            upRun = 0f
            // Only the scroll beyond 140 dp from the top counts.
            downRun += (fromTop - maxOf(before, foldPast)).coerceAtLeast(0f)
            if (!collapsed && canFold() && downRun >= foldAfter) collapsed = true
        } else {
            downRun = 0f
            upRun += -dy
            if (collapsed && upRun >= unfoldAfter) expand()
        }
    }

    /** The list is (or is not) at its top: at the top the bar comes back. */
    fun atTop(top: Boolean) {
        if (!top) return
        fromTop = 0f
        expand()
    }

    /** WP5 calls this as it starts a programmatic scroll: scrolls in the next 1.2 s never fold or unfold the bar. */
    fun ignoreProgrammaticScroll() {
        ignoreUntil = clock() + IGNORE_MS
    }

    /** The FAB was tapped (or the field must show): the bar comes back. */
    fun expand() {
        collapsed = false
        downRun = 0f
        upRun = 0f
    }

    private fun canFold(): Boolean = !fieldFocused && !sheetOpen

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // consumed.y < 0: the content moved up (she scrolled down the list).
            onScrolled(-consumed.y)
            // Pulling down with nothing left to scroll: the list is at its top.
            if (available.y > 0.5f && consumed.y == 0f) atTop(true)
            return Offset.Zero
        }
    }

    companion object {
        const val FOLD_AFTER_DP: Float = 70f
        const val FOLD_PAST_DP: Float = 140f
        const val UNFOLD_AFTER_DP: Float = 40f
        const val IGNORE_MS: Long = 1200
    }
}

@Composable
fun rememberAddBarCollapseState(clock: () -> Long = SystemClock::uptimeMillis): AddBarCollapseState {
    val density = LocalDensity.current.density
    return remember(density) { AddBarCollapseState(density, clock) }
}

/**
 * The FAB the bar folds into (§4.6, §6.1): PicnicFab (64 dp strawberry circle, white plus, the lemon star badge, bobbing
 * −4 dp) that pops in 0 → 1.4 → 1 in 3 steps; instant and still under reduced motion. TalkBack "Add to your list".
 */
@Composable
fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier, visible: Boolean = true) {
    val reduced = LocalReducedMotion.current
    var scale by remember { mutableFloatStateOf(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        withContext(FixedMotionScale) {
            if (!visible) {
                scale = 0f
                return@withContext
            }
            if (reduced || scale == 1f) {
                scale = 1f
                return@withContext
            }
            for (s in FAB_POP) {
                scale = s
                delay(PicnicMotion.pose.toLong())
            }
            scale = 1f
        }
    }
    if (!visible && scale == 0f) return
    PicnicFab(
        stringResource(R.string.add_fab),
        onClick = onClick,
        bob = !reduced,
        modifier = modifier
            .testTag(ADD_FAB_TAG)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    )
}

/** The FAB's pop-in poses (§6.1: 0 → 1.4 → 1, 3 steps). */
private val FAB_POP = listOf(0f, 1.4f, 1f)

/** The bar's slide (§6.1): 220 ms cubic-bezier(.3, 1.2, .5, 1). */
private val BarSlide = CubicBezierEasing(0.3f, 1.2f, 0.5f, 1f)
private const val BAR_SLIDE_MS = 220

/**
 * The docked add bar or, folded, the FAB at the bottom-right (§6.1 item 3, §2.9 `addBar` / `fab`), placed by WP5 at the
 * bottom of the List screen. The bar slides down 220 ms as the FAB pops in; tapping the FAB brings the bar back and
 * focuses the field. The bar's pad runs under the navigation bar and it rides above the IME.
 */
@Composable
fun BoxScope.AddDock(
    flows: AddFlows,
    state: AddUiState,
    modifier: Modifier = Modifier,
    tape: WashiPattern = WashiPattern.SakuraStripe,
) {
    val collapse = flows.collapse
    val reduced = LocalReducedMotion.current
    val slide = remember { Animatable(if (collapse.collapsed) 1f else 0f) }
    LaunchedEffect(collapse.collapsed) {
        withContext(FixedMotionScale) {
            val target = if (collapse.collapsed) 1f else 0f
            if (reduced) slide.snapTo(target) else slide.animateTo(target, tween(BAR_SLIDE_MS, easing = BarSlide))
        }
    }
    LaunchedEffect(state.sheetOpen) { collapse.sheetOpen = state.sheetOpen }
    val c = PicnicTheme.colors
    if (slide.value < 1f) {
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = slide.value * size.height * 1.02f }
                .background(c.pad)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
        ) {
            AddBar(
                state = state.barUi(),
                onTextChange = flows.vm::setText,
                onSend = { flows.vm.send(AddOrigin.BAR) },
                onSnap = flows.sources.takePhoto,
                onChoosePhoto = flows.sources.pickPhoto,
                onSpeak = flows.sources.recordVoice,
                onStop = flows.vm::cancel,
                tape = tape,
                focusRequester = flows.focusRequester,
                onFocusChange = { collapse.fieldFocused = it },
            )
            // The FAB (or an empty Send) asked for the field: the bar is composed now, so focus it.
            LaunchedEffect(flows.focusPending) { if (flows.focusPending) flows.focusNow() }
        }
    }
    AddFab(
        onClick = {
            collapse.expand()
            flows.focusField()
        },
        visible = collapse.collapsed,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
            .padding(end = 6.dp, bottom = 12.dp),
    )
}

/** The dock's height for toasts and the list's bottom padding: the bar (84 dp) or the FAB's room. */
fun AddBarCollapseState.dockHeight(): Dp = if (collapsed) 96.dp else 84.dp

package earth.cora.picnic.ui.aisles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.core.assistant.AisleSyncReport
import earth.cora.picnic.core.model.AisleStyle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.displayName
import earth.cora.picnic.ui.a11y.focusRingIndication
import earth.cora.picnic.ui.common.DashedAddSpot
import earth.cora.picnic.ui.common.DoneButton
import earth.cora.picnic.ui.common.PadCard
import earth.cora.picnic.ui.common.ScreenColumn
import earth.cora.picnic.ui.common.SecondaryScreen
import earth.cora.picnic.ui.theme.AisleBasket
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.LocalPicnicColors
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.NumberCircle
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.RoundStickerButton
import earth.cora.picnic.ui.theme.TabPattern
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import earth.cora.picnic.ui.theme.drawDashedPath
import earth.cora.picnic.ui.theme.patternFill
import earth.cora.picnic.ui.theme.rememberLoopTime
import earth.cora.picnic.ui.theme.rememberPatternBrush
import kotlinx.coroutines.Dispatchers
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Everything "My store" can ask for. Defaults do nothing. */
@Stable
class AislesActions(
    val onBack: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onStoreName: (String) -> Unit = {},
    val onCommitStoreName: () -> Unit = {},
    val onText: (String) -> Unit = {},
    val onCommitText: () -> Unit = {},
    val onEdit: (aisleId: String) -> Unit = {},
    val onStopEditing: () -> Unit = {},
    val onRename: (aisleId: String, name: String) -> Unit = { _, _ -> },
    val onStoreAisle: (aisleId: String, number: Int?) -> Unit = { _, _ -> },
    val onFamily: (aisleId: String, family: String) -> Unit = { _, _ -> },
    val onPattern: (aisleId: String, pattern: String) -> Unit = { _, _ -> },
    val onBasket: (aisleId: String, kind: BasketKind) -> Unit = { _, _ -> },
    val onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onAdd: (name: String) -> Unit = {},
    val onResort: () -> Unit = {},
)

@Composable
fun AislesRoute(vm: AislesViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val toasts = remember { ToastHostState() }
    val resources = LocalResources.current
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                AislesEvent.SaveFailed -> toasts.show(ToastMessage(resources.getString(R.string.screens_save_failed), kind = "notice"))
                is AislesEvent.Synced -> toasts.show(ToastMessage(resortSummary(resources, event.report), kind = "resort"))
                // "Aisles as text" removed aisles: say so, with Undo (their colours, baskets and numbers come back).
                is AislesEvent.Removed -> toasts.show(
                    ToastMessage(
                        resources.getQuantityString(R.plurals.aisles_removed, event.count, event.count),
                        kind = "aisles-removed",
                        actionLabel = resources.getString(R.string.screens_undo),
                        onAction = { vm.undoRemoved(event.before) },
                    ),
                )
            }
        }
    }
    val focus = LocalFocusManager.current
    val actions = remember(vm) {
        // Done and Back leave, unless the text she typed removes aisles: that commits here and she stays, so the toast
        // with Undo shows (the keyboard closes so she sees it and the labels); the next Done or Back leaves.
        val leave = { if (vm.leave()) onBack() else focus.clearFocus() }
        AislesActions(
            onBack = leave,
            onDone = leave,
            onStoreName = vm::setStoreName,
            onCommitStoreName = vm::commitStoreName,
            onText = vm::setText,
            onCommitText = vm::commitText,
            onEdit = vm::startEditing,
            onStopEditing = vm::stopEditing,
            onRename = vm::rename,
            onStoreAisle = vm::setStoreAisle,
            onFamily = vm::setFamily,
            onPattern = vm::setPattern,
            onBasket = vm::setBasket,
            onMove = vm::move,
            onAdd = vm::addAisle,
            onResort = vm::resort,
        )
    }
    // The system's Back goes the same way while the text has unsaved edits (else it would leave without committing them).
    BackHandler(enabled = state.dirty) { actions.onBack() }
    AislesScreen(state, actions, toasts = toasts)
}

/** "Sorted 6 items · 2 left in Everything else" (§6.7), or the friendly error. */
internal fun resortSummary(resources: android.content.res.Resources, report: AisleSyncReport): String {
    report.saveError?.let { return resources.getString(R.string.screens_save_failed) }
    // A failed call moved nothing: say what went wrong, never "Sorted n items".
    report.error?.let { return it.friendlyMessage }
    if (report.recategorized == 0 && report.leftUnsorted == 0) return resources.getString(R.string.aisles_report_nothing)
    val sorted = resources.getQuantityString(R.plurals.aisles_report_sorted, report.recategorized, report.recategorized)
    if (report.leftUnsorted == 0) return sorted
    return resources.getString(R.string.aisles_report_both, sorted, resources.getString(R.string.aisles_report_left, report.leftUnsorted))
}

/** A label held mid-drag (goldens and tests): label [index] lifted [offsetSlots] slots down (negative = up). */
@Immutable
data class DragPreview(val index: Int, val offsetSlots: Float)

/** Test tags. */
object AislesTags {
    const val TEXT: String = "aisles_text"
    const val STORE: String = "aisles_store"
    const val RESORT_RING: String = "aisles_resort_ring"
    fun label(position: Int): String = "aisles_label_$position"
    fun handle(position: Int): String = "aisles_handle_$position"
    fun nameField(): String = "aisles_name_field"
}

/** The gap between two labels on the sheet. */
private val LabelGap: Dp = 6.dp

/** A lifted label held this close to the page's top or bottom edge scrolls the page (§4.26). */
private val AutoScrollEdge: Dp = 64.dp

/** The auto-scroll's speed at the very edge, per frame (about 720 dp/s at 60 fps); it ramps up from 0 across the edge. */
private val AutoScrollMax: Dp = 12.dp

/** The label name's line height. */
private val LabelLine = 20.sp

/** From this font scale a label stacks: its handle, number, swatch and pencil on top, the name under them at full width. */
private const val LARGE_TEXT = 1.5f

/**
 * My store (§6.7): Back, "My store", "walking order · colours · baskets" on a pad patch, "Done"; the "Store:" field; the
 * sticker sheet of aisle labels on backing paper (drag the dots to reorder; the pencil opens a label: name, store aisle
 * number, colour, pattern, basket kind), the dashed "Add aisle" spot and "Re-sort my list" (the ladybug ring circles it
 * while it works); "Aisles as text:" (Klee One, ruled), previewing the labels as she types and committed on Done or
 * when it loses focus. No AI card and no model names here (that is Settings).
 */
@Composable
fun AislesScreen(
    state: AislesUiState,
    actions: AislesActions,
    modifier: Modifier = Modifier,
    toasts: ToastHostState = remember { ToastHostState() },
    scroll: ScrollState = rememberScrollState(),
    initialDrag: DragPreview? = null,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type

    SecondaryScreen(
        title = stringResource(R.string.aisles_title),
        subtitle = stringResource(R.string.aisles_subtitle),
        onBack = actions.onBack,
        modifier = modifier,
        toasts = toasts,
        actions = { DoneButton(actions.onDone) },
    ) {
        // The scrolling page's window bounds: a lifted label scrolls the page when held near its top or bottom edge.
        var viewport by remember { mutableStateOf(Rect.Zero) }
        ScreenColumn(modifier = Modifier.onGloballyPositioned { viewport = it.boundsInWindow() }, state = scroll, spacing = 18.dp) {
            PadCard {
                var focused by remember { mutableStateOf(false) }
                val commit by rememberUpdatedState(actions.onCommitStoreName)
                PicnicTextField(
                    value = state.storeName,
                    onValueChange = actions.onStoreName,
                    label = stringResource(R.string.aisles_store_label),
                    placeholder = stringResource(R.string.aisles_store_hint),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    trailing = { Glyph(PicnicGlyph.Edit, contentDescription = null, tint = c.inkSoft, size = 20.dp) },
                    modifier = Modifier
                        .testTag(AislesTags.STORE)
                        .onFocusChanged {
                            if (focused && !it.isFocused) commit()
                            focused = it.isFocused
                        },
                )
            }
            StickerSheet(state, actions, initialDrag, scroll) { viewport }
            PadCard {
                var focused by remember { mutableStateOf(false) }
                val commit by rememberUpdatedState(actions.onCommitText)
                PicnicTextField(
                    value = state.text,
                    onValueChange = actions.onText,
                    label = stringResource(R.string.aisles_text_label),
                    minLines = 5,
                    textStyle = t.aislesText,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = stringResource(R.string.aisles_text_hint),
                    modifier = Modifier
                        .testTag(AislesTags.TEXT)
                        .onFocusChanged {
                            if (focused && !it.isFocused) commit()
                            focused = it.isFocused
                        },
                )
            }
        }
    }
}

/**
 * The labels on their backing sheet, then "Add aisle" and "Re-sort my list". Labels grow with their names (§15: names
 * wrap, never clip), so the drag measures each label: the lifted label's centre moves with the finger and every label
 * whose centre it passes moves one lifted-label slot the other way.
 */
@Composable
private fun StickerSheet(
    state: AislesUiState,
    actions: AislesActions,
    initialDrag: DragPreview?,
    scroll: ScrollState,
    viewport: () -> Rect,
) {
    val c = PicnicTheme.colors
    val density = LocalDensity.current
    val gap = with(density) { LabelGap.toPx() }
    val count = state.labels.size
    // Each label's measured height in px, by position (re-measured whenever the labels change).
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var dragging by remember { mutableIntStateOf(initialDrag?.index ?: -1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The finger's window y while a label is lifted, and where it went down (NaN when nothing is lifted).
    var fingerY by remember { mutableFloatStateOf(Float.NaN) }
    var fingerStartY by remember { mutableFloatStateOf(Float.NaN) }
    val bottomInset = WindowInsets.navigationBars.union(WindowInsets.ime).getBottom(density)
    // Auto-scroll (§4.26 with 14 to 24 aisles the sheet is taller than the screen): while a label is held within 64 dp
    // of the page's top or bottom edge, having moved that way, the page scrolls (faster the deeper into the edge) and the
    // label moves with it, so it stays under the finger and one gesture can carry it the whole length of the store.
    val held = dragging >= 0 && !fingerY.isNaN()
    LaunchedEffect(held) {
        if (!held) return@LaunchedEffect
        val zone = with(density) { AutoScrollEdge.toPx() }
        val fastest = with(density) { AutoScrollMax.toPx() }
        while (true) {
            withFrameNanos { }
            val y = fingerY
            val v = viewport()
            if (y.isNaN() || v.height <= 0f) continue
            val top = v.top
            val bottom = v.bottom - bottomInset
            val speed = when {
                y > bottom - zone && y > fingerStartY -> fastest * ((y - (bottom - zone)) / zone).coerceIn(0f, 1f)
                y < top + zone && y < fingerStartY -> -fastest * ((top + zone - y) / zone).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                val moved = scroll.scrollBy(speed)
                if (moved != 0f) dragOffset += moved
            }
        }
    }
    // A golden's held drag ([DragPreview]) is given in slots of the label it moves over; it becomes px once measured.
    var previewSlots by remember { mutableStateOf(initialDrag?.offsetSlots) }
    fun slot(i: Int): Float = (heights[i] ?: 0) + gap
    fun top(i: Int): Float = (0 until i).fold(0f) { y, j -> y + slot(j) }
    fun offsetNow(): Float = previewSlots?.let { s ->
        val over = if (s >= 0f) (dragging + 1).coerceAtMost(count - 1) else (dragging - 1).coerceAtLeast(0)
        s * slot(over)
    } ?: dragOffset
    // Where the lifted label would drop, read at call time (the drop can come before any recomposition of the drag): it
    // passes a label once its centre crosses the middle of the gap before that label (for equal labels: half a slot).
    fun targetNow(): Int {
        if (dragging !in 0 until count) return -1
        val offset = offsetNow()
        val y = top(dragging) + (heights[dragging] ?: 0) / 2f + offset
        var t = dragging
        if (offset > 0f) {
            for (k in dragging + 1 until count) if (y > top(k) - gap / 2f) t = k
        } else {
            for (k in dragging - 1 downTo 0) if (y < top(k) + (heights[k] ?: 0) + gap / 2f) t = k
        }
        return t
    }
    val target = targetNow()
    val lift = if (dragging in 0 until count) slot(dragging) else 0f
    val newName = stringResource(R.string.aisles_new_name)

    PicnicSurface(Modifier.fillMaxWidth(), color = c.backing, large = true) {
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 14.dp)) {
            if (state.labels.isEmpty()) {
                Text(
                    stringResource(R.string.aisles_empty),
                    style = PicnicTheme.type.body.copy(color = c.ballpoint),
                    modifier = Modifier.padding(8.dp),
                )
            }
            state.labels.forEachIndexed { i, label ->
                val shift = when {
                    dragging < 0 || i == dragging -> 0f
                    i in (dragging + 1)..target -> -lift
                    i in target until dragging -> lift
                    else -> 0f
                }
                val reduced = LocalReducedMotion.current
                val animatedShift by animateFloatAsState(shift, if (reduced) snap() else tween(120), label = "labelShift")
                val lifted = i == dragging
                val offset = if (lifted) offsetNow() else 0f
                AisleLabel(
                    label = label,
                    position = i + 1,
                    count = count,
                    editing = state.editing == label.aisle.id,
                    enabled = !state.previewing,
                    lifted = lifted,
                    liftedPastHalf = lifted && abs(offset) > slot(if (offset >= 0f) (i + 1).coerceAtMost(count - 1) else (i - 1).coerceAtLeast(0)) / 2f,
                    actions = actions,
                    modifier = Modifier
                        .padding(bottom = LabelGap)
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) offset else animatedShift }
                        .onSizeChanged { heights[i] = it.height },
                    onDragStart = { y ->
                        if (state.editing != null) actions.onStopEditing()
                        previewSlots = null
                        dragging = i
                        dragOffset = 0f
                        fingerY = y
                        fingerStartY = y
                    },
                    onDrag = { dy, y ->
                        dragOffset += dy
                        fingerY = y
                    },
                    onDragEnd = {
                        val to = targetNow()
                        val from = dragging
                        dragging = -1
                        dragOffset = 0f
                        previewSlots = null
                        fingerY = Float.NaN
                        fingerStartY = Float.NaN
                        if (from >= 0 && to >= 0 && to != from) actions.onMove(from, to)
                    },
                )
            }
            if (state.previewing) {
                Text(
                    stringResource(R.string.aisles_preview_note),
                    style = PicnicTheme.type.caption.copy(color = c.ballpoint),
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                )
            }
            // "Add aisle" is the empty sticker spot where the next label goes (the end of the column); "Re-sort my list"
            // sits under it, its whole label always readable (side by side they don't both fit at 360 dp).
            DashedAddSpot(
                stringResource(R.string.aisles_add),
                onClick = { actions.onAdd(newName) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                onBacking = true,
            )
            Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                ResortButton(state.syncing, actions.onResort)
            }
            if (state.syncing) {
                Text(
                    stringResource(R.string.aisles_resorting),
                    style = PicnicTheme.type.caption.copy(color = c.ballpoint),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * "Re-sort my list" (§6.7): the secondary pill with the resort glyph; while it works, the ladybug walks a dashed ring
 * round it (3 s a lap; still under reduced motion).
 */
@Composable
private fun ResortButton(syncing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val loop by rememberLoopTime(enabled = syncing && !reduced)
    Box(modifier, contentAlignment = Alignment.Center) {
        // While it works the pill keeps its face (a disabled face would vanish into the sheet behind it, leaving faded
        // words in the ladybug's ring); a tap meanwhile does nothing.
        PicnicButton(
            stringResource(R.string.aisles_resort),
            onClick = { if (!syncing) onClick() },
            style = PicnicButtonStyle.Secondary,
            glyph = PicnicGlyph.Refresh,
        )
        if (syncing) {
            val dash = Families.periwinkle.mid
            Canvas(Modifier.matchParentSize().testTag(AislesTags.RESORT_RING).clearAndSetSemantics { }) {
                val pad = 5.dp.toPx()
                val path = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(-pad, -pad, size.width + pad, size.height + pad, CornerRadius(size.height / 2f + pad)),
                    )
                }
                drawDashedPath(path, dash, 2.dp.toPx(), 4.dp.toPx(), 3.dp.toPx())
            }
            val phase = if (reduced) 0.12f else (loop % PicnicMotion.ladybugRing).toFloat() / PicnicMotion.ladybugRing
            LadybugOnRing(phase, Modifier.matchParentSize())
        }
    }
}

/** The ladybug at [phase] (0..1) of its lap round the button's stadium ring, facing the way it walks. */
@Composable
private fun LadybugOnRing(phase: Float, modifier: Modifier) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(Size.Zero) }
    Box(modifier.onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }.clearAndSetSemantics { }) {
        if (size != Size.Zero) {
            val pad = with(density) { 5.dp.toPx() }
            val (p, angle) = stadiumPoint(phase, size.width + pad * 2, size.height + pad * 2)
            val bug = with(density) { 20.dp.toPx() }
            DecorSticker(
                "ladybug",
                Modifier
                    .offset { IntOffset((p.x - pad - bug / 2f).roundToInt(), (p.y - pad - bug / 2f).roundToInt()) }
                    .rotate(angle + 90f),
                size = 20.dp,
            )
        }
    }
}

/** A point at [t] (0..1, clockwise from the top-left of the straight) on a stadium [w] × [h], and its heading (degrees). */
internal fun stadiumPoint(t: Float, w: Float, h: Float): Pair<Offset, Float> {
    val r = h / 2f
    val straight = (w - h).coerceAtLeast(0f)
    val arc = (PI * r).toFloat()
    val total = straight * 2 + arc * 2
    var d = (t - kotlin.math.floor(t)) * total
    if (d < straight) return Offset(r + d, 0f) to 0f
    d -= straight
    if (d < arc) {
        val a = -PI / 2 + d / r
        return Offset((w - r + r * cos(a)).toFloat(), (r + r * sin(a)).toFloat()) to Math.toDegrees(a + PI / 2).toFloat()
    }
    d -= arc
    if (d < straight) return Offset(w - r - d, h) to 180f
    d -= straight
    val a = PI / 2 + d / r
    val p = Offset((r + r * cos(a)).toFloat(), (r + r * sin(a)).toFloat())
    return p to Math.toDegrees(atan2(cos(a), -sin(a))).toFloat()
}

/** Colours for content on a pastel label (§2.7: text on pastels is always ballpoint, in every variant). */
@Composable
private fun OnPastel(content: @Composable () -> Unit) {
    val c = PicnicTheme.colors
    CompositionLocalProvider(LocalPicnicColors provides c.copy(ink = c.ballpoint, inkSoft = c.ballpoint, rule = Families.periwinkle.pastel), content = content)
}

/**
 * One aisle label (§4.26): a kiss-cut label (40 dp for a one-line name) on the backing sheet: the drag handle (2 × 3
 * dots, 48 dp target), the number circle, the name (Zen Maru 700 16 sp) and "store aisle 2" (13 sp), the 26 dp colour +
 * pattern swatch and the pencil. The name wraps onto as many lines as it needs and the label grows with it (§15); "store
 * aisle 2" follows the name on its line when it fits there, else on a line of its own, so the number she set is always
 * there. From font scale 1.5 the label stacks: handle, number, swatch and pencil on top, the name under them at the
 * label's full width. Editing expands it: the name field (in focus), the store-aisle number, 10 colours, 10 patterns
 * and the 17 basket stickers. Lifted while dragged (tilt −2.4° → +2°, hsLift). TalkBack: one node with "Move up" /
 * "Move down".
 */
@Composable
private fun AisleLabel(
    label: AisleLabelUi,
    position: Int,
    count: Int,
    editing: Boolean,
    enabled: Boolean,
    lifted: Boolean,
    liftedPastHalf: Boolean,
    actions: AislesActions,
    modifier: Modifier,
    onDragStart: (windowY: Float) -> Unit,
    onDrag: (dy: Float, windowY: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    val look = AisleLooks.of(position, label.style)
    val name = label.aisle.displayName()
    val description = if (label.storeAisle != null) {
        stringResource(R.string.aisles_label_store, label.aisle.name, position, label.storeAisle)
    } else {
        stringResource(R.string.aisles_label, label.aisle.name, position)
    }
    val moveUp = stringResource(R.string.aisles_move_up)
    val moveDown = stringResource(R.string.aisles_move_down)
    val shadow = if (lifted) shadows.hsLift else shadows.hs
    val tilt = when {
        !lifted -> 0f
        liftedPastHalf -> 2f
        else -> -2.4f
    }
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val large = LocalDensity.current.fontScale >= LARGE_TEXT
    // The handle's coordinates, to give the finger's window position (lifted-label translation included).
    val handleAt = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun windowY(local: Offset): Float = handleAt[0]?.takeIf { it.isAttached }?.localToWindow(local)?.y ?: Float.NaN
    Column(
        modifier
            .fillMaxWidth()
            .testTag(AislesTags.label(position))
            .graphicsLayer { rotationZ = tilt }
            .drawBehind {
                val top = 4.dp.toPx()
                val h = size.height - 8.dp.toPx()
                val r = 10.dp.toPx()
                val b = 3.dp.toPx()
                drawRoundRect(shadows.color, Offset(shadow.x.toPx(), top + shadow.y.toPx()), Size(size.width, h), CornerRadius(r))
                drawRoundRect(c.stickerWhite, Offset(0f, top), Size(size.width, h), CornerRadius(r))
                drawRoundRect(look.family.pastel, Offset(b, top + b), Size(size.width - 2 * b, h - 2 * b), CornerRadius(r - b))
            },
    ) {
        OnPastel {
            val handle: @Composable () -> Unit = {
                Box(
                    Modifier
                        .size(48.dp)
                        .testTag(AislesTags.handle(position))
                        .onGloballyPositioned { handleAt[0] = it }
                        .then(
                            if (enabled) {
                                Modifier.pointerInput(position) {
                                    detectDragGestures(
                                        onDragStart = { at -> dragStart(windowY(at)) },
                                        onDragEnd = { dragEnd() },
                                        onDragCancel = { dragEnd() },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            drag(amount.y, windowY(change.position))
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    DragDots(look.family.trace)
                }
            }
            // The numeral repeats the label's number (TalkBack reads it from the label). It scales with the font like
            // every numeral, and the circle grows round it (§2.7, §15: the kit's NumberCircle never cuts its numeral).
            val number: @Composable () -> Unit = { NumberCircle(position, look, size = 26.dp) }
            val tools: @Composable () -> Unit = {
                Swatch(look, Modifier.size(26.dp))
                // The round button keeps the page's own colours (its face is the pad: white in Day, navy in Glow).
                CompositionLocalProvider(LocalPicnicColors provides c) {
                    RoundStickerButton(
                        glyph = if (editing) PicnicGlyph.Check else PicnicGlyph.Edit,
                        contentDescription = if (editing) stringResource(R.string.aisles_edit_done, name) else stringResource(R.string.aisles_edit, name),
                        onClick = { if (editing) actions.onStopEditing() else actions.onEdit(label.aisle.id) },
                        enabled = enabled,
                        faceSize = 32.dp,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
            }
            val labelSemantics = Modifier.semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = buildList {
                    if (position > 1) add(CustomAccessibilityAction(moveUp) { actions.onMove(position - 1, position - 2); true })
                    if (position < count) add(CustomAccessibilityAction(moveDown) { actions.onMove(position - 1, position); true })
                }
            }
            if (large) {
                Column(Modifier.fillMaxWidth().then(labelSemantics)) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        handle()
                        number()
                        Spacer(Modifier.weight(1f))
                        tools()
                    }
                    LabelName(name, label.storeAisle, Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .then(labelSemantics),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    handle()
                    number()
                    LabelName(name, label.storeAisle, Modifier.weight(1f).padding(start = 8.dp, end = 6.dp, top = 10.dp, bottom = 10.dp))
                    tools()
                }
            }
            if (editing) LabelEditor(label, look, actions)
        }
    }
}

/**
 * The label's name (Zen Maru 700 16 sp, wrapping onto balanced lines, never cut) and its "store aisle 2" (13 sp): on
 * the name's last line when it fits there, else on the next line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelName(name: String, storeAisle: Int?, modifier: Modifier) {
    val c = PicnicTheme.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Balanced lines, "&" kept with the word after it (§0.4), as on the list's tabs.
        Text(
            earth.cora.picnic.ui.theme.keepWithNextWord(name),
            style = PicnicTheme.type.label.copy(color = c.ballpoint, fontSize = 16.sp, lineHeight = LabelLine, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
            modifier = Modifier.alignByBaseline(),
        )
        if (storeAisle != null) {
            Text(
                stringResource(R.string.aisles_store_aisle, storeAisle),
                style = PicnicTheme.type.caption.copy(color = c.ballpoint),
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** The handle's 2 × 3 grid of 3 dp dots. */
@Composable
private fun DragDots(color: Color) {
    Canvas(Modifier.size(width = 11.dp, height = 17.dp)) {
        val r = 1.5.dp.toPx()
        for (col in 0..1) for (row in 0..2) {
            drawCircle(color, r, Offset(r + col * 5.dp.toPx(), r + row * 7.dp.toPx()))
        }
    }
}

/** The 26 dp colour + pattern swatch: the pastel, the pattern in the mid, a 2 dp trace ring. Decorative. */
@Composable
private fun Swatch(look: AisleLook, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(look.family.pastel)
            .patternFill(look, 0.6f)
            .border(2.dp, look.family.trace, CircleShape)
            .clearAndSetSemantics { },
    )
}

/** The open label: name, store aisle number, 10 colours, 10 patterns, 17 baskets (§4.26). */
@Composable
private fun LabelEditor(label: AisleLabelUi, look: AisleLook, actions: AislesActions) {
    val id = label.aisle.id
    // A just-added label still called "New aisle" opens with its name field empty (the default as its placeholder), so
    // she types the name straight away; left blank, it stays "New aisle".
    val defaultName = stringResource(R.string.aisles_new_name)
    val unnamed = label.aisle.name == defaultName
    var name by remember(id) { mutableStateOf(if (unnamed) "" else label.aisle.name) }
    var number by remember(id) { mutableStateOf(label.storeAisle?.toString().orEmpty()) }
    val nameFocus = remember { FocusRequester() }
    // The name field takes focus once it is on screen: a frame later, so its focus underline (a strawberry line and the
    // heart, §4.8) is already listening for the focus it is given.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        nameFocus.requestFocus()
    }
    val commitName = { if (name.isNotBlank() && name.trim() != label.aisle.name) actions.onRename(id, name) }
    val commitNumber = {
        val n = number.trim().toIntOrNull()?.takeIf { it > 0 }
        if (n != label.storeAisle) actions.onStoreAisle(id, n)
    }
    val familyNames = stringArrayResource(R.array.aisles_family_names)
    val patternNames = stringArrayResource(R.array.aisles_pattern_names)
    val basketNames = stringArrayResource(R.array.aisles_basket_names)
    Column(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            var nameFocused by remember { mutableStateOf(false) }
            PicnicTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.aisles_name_field),
                placeholder = if (unnamed) defaultName else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitName() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag(AislesTags.nameField())
                    .focusRequester(nameFocus)
                    .onFocusChanged {
                        if (nameFocused && !it.isFocused) commitName()
                        nameFocused = it.isFocused
                    },
            )
            var numberFocused by remember { mutableStateOf(false) }
            PicnicTextField(
                value = number,
                onValueChange = { v -> number = v.filter(Char::isDigit).take(3) },
                label = stringResource(R.string.aisles_store_aisle_field),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitNumber() }),
                modifier = Modifier
                    .width(84.dp)
                    .onFocusChanged {
                        if (numberFocused && !it.isFocused) commitNumber()
                        numberFocused = it.isFocused
                    },
            )
        }
        val white = PicnicTheme.colors.stickerWhite
        SwatchGrid(stringResource(R.string.aisles_colour), Families.rotation) { i, family ->
            SwatchCell(look.family == family, familyNames.getOrElse(i) { family.name }, onClick = { actions.onFamily(id, family.name) }) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(family.mid).border(1.5.dp, white, CircleShape))
            }
        }
        SwatchGrid(stringResource(R.string.aisles_pattern), TabPattern.entries) { i, pattern ->
            val sample = AisleLook(look.position, look.family, pattern, look.pictogram)
            SwatchCell(look.pattern == pattern, patternNames.getOrElse(i) { pattern.key }, onClick = { actions.onPattern(id, pattern.key) }) {
                PatternDot(sample)
            }
        }
        Text(stringResource(R.string.aisles_basket), style = PicnicTheme.type.caption.copy(color = PicnicTheme.colors.ballpoint))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EditorBaskets.forEachIndexed { i, kind ->
                val selected = label.basket == kind
                Box(
                    Modifier
                        .size(52.dp)
                        // No ripple: the chosen basket answers with its lemon ring.
                        .selectable(selected = selected, interactionSource = null, indication = focusRingIndication(), role = Role.RadioButton, onClick = { actions.onBasket(id, kind) })
                        .semantics { contentDescription = basketNames.getOrElse(i) { kind.key } }
                        .drawBehind {
                            if (selected) {
                                drawCircle(Families.lemon.pastel, size.minDimension / 2f - 1.dp.toPx())
                                drawCircle(Families.lemon.mid, size.minDimension / 2f - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AisleBasket(kind, inBasket = emptyList(), count = 0, size = 40.dp)
                }
            }
        }
    }
}

/**
 * A titled set of 10 swatches (§4.26) in two rows of five 48 × 48 dp cells (§15 names swatches among the 48 dp
 * targets: ten in one row at 360 dp would give each only 29 dp).
 */
@Composable
private fun <T> SwatchGrid(title: String, entries: List<T>, cell: @Composable (index: Int, entry: T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = PicnicTheme.type.caption.copy(color = PicnicTheme.colors.ballpoint))
        Column(Modifier.selectableGroup()) {
            entries.chunked(SWATCHES_PER_ROW).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { j, entry -> cell(r * SWATCHES_PER_ROW + j, entry) }
                }
            }
        }
    }
}

/** Swatches in one row of a [SwatchGrid]. */
private const val SWATCHES_PER_ROW = 5

/** One 24 dp swatch in a 48 × 48 dp cell; selected = a 2.5 dp ballpoint ring (§4.26). No ripple: the ring answers. */
@Composable
private fun SwatchCell(selected: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ring = PicnicTheme.colors.ballpoint
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, interactionSource = null, indication = focusRingIndication(CircleShape), role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .drawBehind {
                if (selected) drawCircle(ring, 14.5.dp.toPx(), style = Stroke(2.5.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { content() }
    }
}

/** A pattern swatch: the pattern in the label's mid on sticker white, a 1.5 dp mid ring. */
@Composable
private fun PatternDot(look: AisleLook) {
    val brush = rememberPatternBrush(look.pattern)
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(PicnicTheme.colors.stickerWhite)
            .then(if (brush != null) Modifier.patternFill(look, 0.9f) else Modifier)
            .border(1.5.dp, look.family.mid, CircleShape),
    )
}

/** The AisleStyle of [label] (for tests): its override or the default. */
internal fun AisleLabelUi.styleOrDefault(): AisleStyle = style ?: AisleStyle()

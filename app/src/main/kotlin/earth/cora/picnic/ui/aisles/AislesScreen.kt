package earth.cora.picnic.ui.aisles

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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
            }
        }
    }
    val actions = remember(vm) {
        AislesActions(
            onBack = {
                vm.leave()
                onBack()
            },
            onDone = {
                vm.leave()
                onBack()
            },
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
    AislesScreen(state, actions, toasts = toasts)
}

/** "Sorted 6 items · 2 left in Everything else" (§6.7), or the friendly error. */
internal fun resortSummary(resources: android.content.res.Resources, report: AisleSyncReport): String {
    report.saveError?.let { return resources.getString(R.string.screens_save_failed) }
    report.error?.let { if (report.recategorized == 0) return it.friendlyMessage }
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

/** One label's slot: the 48 dp row (its face is 40 dp) and the 6 dp between labels. */
private val LabelRow: Dp = 48.dp
private val LabelGap: Dp = 6.dp

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
    focusText: Boolean = false,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val textFocus = remember { FocusRequester() }
    LaunchedEffect(focusText) { if (focusText) textFocus.requestFocus() }

    SecondaryScreen(
        title = stringResource(R.string.aisles_title),
        subtitle = stringResource(R.string.aisles_subtitle),
        onBack = actions.onBack,
        modifier = modifier,
        toasts = toasts,
        actions = { DoneButton(actions.onDone) },
    ) {
        ScreenColumn(state = scroll, spacing = 18.dp) {
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
            StickerSheet(state, actions, initialDrag)
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
                        .focusRequester(textFocus)
                        .onFocusChanged {
                            if (focused && !it.isFocused) commit()
                            focused = it.isFocused
                        },
                )
            }
        }
    }
}

/** The labels on their backing sheet, then "Add aisle" and "Re-sort my list". */
@Composable
private fun StickerSheet(state: AislesUiState, actions: AislesActions, initialDrag: DragPreview?) {
    val c = PicnicTheme.colors
    val density = LocalDensity.current
    val slot = with(density) { (LabelRow + LabelGap).toPx() }
    var dragging by remember { mutableIntStateOf(initialDrag?.index ?: -1) }
    var offset by remember { mutableFloatStateOf((initialDrag?.offsetSlots ?: 0f) * slot) }
    val count = state.labels.size
    // Read at call time (the drop can come before any recomposition of the drag).
    fun targetNow(): Int = if (dragging < 0) -1 else (dragging + (offset / slot).roundToInt()).coerceIn(0, (count - 1).coerceAtLeast(0))
    val target = targetNow()
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
                    i in (dragging + 1)..target -> -slot
                    i in target until dragging -> slot
                    else -> 0f
                }
                val reduced = LocalReducedMotion.current
                val animatedShift by animateFloatAsState(shift, if (reduced) snap() else tween(120), label = "labelShift")
                val lifted = i == dragging
                AisleLabel(
                    label = label,
                    position = i + 1,
                    count = count,
                    editing = state.editing == label.aisle.id,
                    enabled = !state.previewing,
                    lifted = lifted,
                    liftedPastHalf = lifted && abs(offset) > slot / 2f,
                    actions = actions,
                    modifier = Modifier
                        .padding(bottom = LabelGap)
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) offset else animatedShift },
                    onDragStart = {
                        if (state.editing != null) actions.onStopEditing()
                        dragging = i
                        offset = 0f
                    },
                    onDrag = { dy -> offset += dy },
                    onDragEnd = {
                        val to = targetNow()
                        val from = dragging
                        dragging = -1
                        offset = 0f
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
                    modifier = Modifier.padding(start = 6.dp, top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
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
        PicnicButton(
            stringResource(R.string.aisles_resort),
            onClick = onClick,
            style = PicnicButtonStyle.Secondary,
            glyph = PicnicGlyph.Refresh,
            enabled = !syncing,
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
 * One aisle label (§4.26): a kiss-cut label, 40 dp, on the backing sheet: the drag handle (2 × 3 dots, 48 dp target),
 * the number circle, the name (Zen Maru 700 16 sp) and "store aisle 2" (13 sp), the 26 dp colour + pattern swatch and the
 * pencil. Editing expands it: the name field (in focus), the store-aisle number, 10 colours, 10 patterns and the 17
 * basket stickers. Lifted while dragged (tilt −2.4° → +2°, hsLift). TalkBack: one node with "Move up" / "Move down".
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
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(LabelRow)
                    .semantics(mergeDescendants = true) {
                        contentDescription = description
                        customActions = buildList {
                            if (position > 1) add(CustomAccessibilityAction(moveUp) { actions.onMove(position - 1, position - 2); true })
                            if (position < count) add(CustomAccessibilityAction(moveDown) { actions.onMove(position - 1, position); true })
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp, 48.dp)
                        .testTag(AislesTags.handle(position))
                        .then(
                            if (enabled) {
                                Modifier.pointerInput(position) {
                                    detectDragGestures(
                                        onDragStart = { dragStart() },
                                        onDragEnd = { dragEnd() },
                                        onDragCancel = { dragEnd() },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            drag(amount.y)
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
                NumberCircle(position, look, size = 26.dp)
                Row(
                    Modifier.weight(1f).padding(start = 8.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // One line: the name first, then "store aisle 2" in 13 sp; a long name pushes the number out
                    // (the label's TalkBack text always has both).
                    val storeAisle = label.storeAisle?.let { stringResource(R.string.aisles_store_aisle, it) }
                    val caption = PicnicTheme.type.caption
                    val line = remember(name, storeAisle, caption) {
                        buildAnnotatedString {
                            append(name)
                            if (storeAisle != null) {
                                append("  ")
                                withStyle(SpanStyle(fontSize = caption.fontSize, fontWeight = caption.fontWeight, fontFamily = caption.fontFamily)) {
                                    append(storeAisle)
                                }
                            }
                        }
                    }
                    Text(
                        line,
                        style = PicnicTheme.type.label.copy(color = c.ballpoint, fontSize = 16.sp, lineHeight = 20.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
            if (editing) LabelEditor(label, look, actions)
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
    var name by remember(id) { mutableStateOf(label.aisle.name) }
    var number by remember(id) { mutableStateOf(label.storeAisle?.toString().orEmpty()) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
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
        SwatchRow(stringResource(R.string.aisles_colour)) {
            Families.rotation.forEachIndexed { i, family ->
                val selected = look.family == family
                SwatchCell(selected, familyNames.getOrElse(i) { family.name }, onClick = { actions.onFamily(id, family.name) }, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(family.mid).border(1.5.dp, Color.White, CircleShape))
                }
            }
        }
        SwatchRow(stringResource(R.string.aisles_pattern)) {
            TabPattern.entries.forEachIndexed { i, pattern ->
                val selected = look.pattern == pattern
                val sample = AisleLook(look.position, look.family, pattern, look.pictogram)
                SwatchCell(selected, patternNames.getOrElse(i) { pattern.key }, onClick = { actions.onPattern(id, pattern.key) }, modifier = Modifier.weight(1f)) {
                    PatternDot(sample)
                }
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
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { actions.onBasket(id, kind) })
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

@Composable
private fun SwatchRow(title: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = PicnicTheme.type.caption.copy(color = PicnicTheme.colors.ballpoint))
        Row(Modifier.fillMaxWidth().selectableGroup(), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** One 24 dp swatch in a ≥30 dp cell; selected = a 2.5 dp ballpoint ring (§4.26). */
@Composable
private fun SwatchCell(selected: Boolean, description: String, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val ring = PicnicTheme.colors.ballpoint
    Box(
        modifier
            .heightIn(min = 36.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .drawBehind {
                if (selected) drawCircle(ring, 14.5.dp.toPx(), style = Stroke(2.5.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { content() }
    }
}

/** A pattern swatch: the pattern in the label's mid on white, a 1 dp mid ring. */
@Composable
private fun PatternDot(look: AisleLook) {
    val brush = rememberPatternBrush(look.pattern)
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.White)
            .then(if (brush != null) Modifier.patternFill(look, 0.9f) else Modifier)
            .border(1.5.dp, look.family.mid, CircleShape),
    )
}

/** The AisleStyle of [label] (for tests): its override or the default. */
internal fun AisleLabelUi.styleOrDefault(): AisleStyle = style ?: AisleStyle()

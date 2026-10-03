package earth.cora.picnic.ui.list

import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import earth.cora.picnic.R
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.ListPresentation
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.list.motion.RowMotion
import earth.cora.picnic.ui.list.motion.glossSweep
import earth.cora.picnic.ui.list.motion.rowHanko
import earth.cora.picnic.ui.list.motion.rowStickerPeel
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.HankoStamp
import earth.cora.picnic.ui.theme.LocalPieceProbe
import earth.cora.picnic.ui.theme.probe
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.RarityChip
import earth.cora.picnic.ui.theme.StickerSpot
import earth.cora.picnic.ui.theme.StruckText
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.TrayMetrics
import earth.cora.picnic.ui.theme.TrayPart
import earth.cora.picnic.ui.theme.hankoAngle
import earth.cora.picnic.ui.theme.highlighterBand
import earth.cora.picnic.ui.theme.traySlice
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/*
 * A row in its tray (docs/DESIGN_STICKER.md §4.11, §8.3, §8.6): the 52 dp sticker (its ghost once checked), the name over
 * the detail (the item text NEVER moves during the chains), the 48 dp checkbox (the dashed spot, or the GOT IT hanko).
 * The whole row is one checkbox for TalkBack; long-press edits; swipe right checks, swipe left tears it off (with Undo).
 * New rows and duplicates pop only their sticker, with a 400 ms highlighter flash on the text band. Owner: WP5.
 */

/** What a row can ask for. */
internal class RowActions(
    val onToggle: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onEdit: (String) -> Unit,
    val onTearCue: () -> Unit = {},
)

/** A pop asked for by a commit (§6.2): the sticker pops and the text band flashes, [delayMs] after [generation] changed. */
data class PopRequest(val generation: Int, val delayMs: Long)

/** How far a swipe must go to act (§4.11): 56 dp. */
internal val SWIPE_THRESHOLD: Dp = 56.dp

/** The row's minimum height (§2.9): 56 dp, 64 dp at font scale ≥ 1.3. */
@Composable
internal fun rowMinHeight(): Dp = if (LocalDensity.current.fontScale >= 1.3f) 64.dp else 56.dp

/** The slice of the open tray that holds one row. */
@Composable
internal fun TrayRow(
    row: ItemRowUi,
    section: SectionUi,
    first: Boolean,
    last: Boolean,
    presentation: ListPresentation,
    clock: MotionClock,
    anchors: AnchorRegistry,
    actions: RowActions,
    modifier: Modifier = Modifier,
    pop: PopRequest? = null,
    sweepT0: Long? = null,
    ambientClock: MotionClock? = null,
    current: Boolean = true,
) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    val ring = currentRing(current)
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }
    var tearT0 by remember { mutableStateOf<Long?>(null) }
    var tearNow by remember { mutableStateOf(0L) }
    val act by rememberUpdatedState(actions)
    val thresholdPx = with(density) { SWIPE_THRESHOLD.toPx() }
    val minH = rowMinHeight()
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minH)
            .testTag(ListTags.rowSlot(row.id))
            // The torn halves tumble over the rows and the cap below (§8.6).
            .zIndex(if (tearT0 != null) 1f else 0f)
            .traySlice(TrayPart.Row, section.look, ring, shadows.color, c.pad, c.rule, first = first, last = last),
    ) {
        val inset = TrayMetrics.panelInset
        val slotShape = RoundedCornerShape(topStart = if (first) 8.dp else 0.dp, topEnd = if (first) 8.dp else 0.dp, bottomStart = if (last) 8.dp else 0.dp, bottomEnd = if (last) 8.dp else 0.dp)
        val tearing = tearT0 != null
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = inset)
                .then(if (swipe.value != 0f || tearing) Modifier.clip(slotShape) else Modifier),
        ) {
            if (tearing) {
                // The slot left behind shows the backing paper while the halves tumble off (§8.6).
                Box(Modifier.matchParentSize().background(c.backing))
            } else if (swipe.value != 0f) {
                SwipeBand(right = swipe.value > 0f, progress = abs(swipe.value) / thresholdPx, Modifier.matchParentSize())
            }
        }
        val content: @Composable (Modifier) -> Unit = { m ->
            RowContent(
                row = row,
                presentation = presentation,
                clock = clock,
                anchors = anchors,
                actions = actions,
                pop = pop,
                sweepT0 = sweepT0,
                ambientClock = ambientClock,
                modifier = m
                    .padding(horizontal = inset)
                    .pointerInput(row.id, reduced) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val v = swipe.value
                                scope.launch {
                                    withContext(FixedMotionScale) {
                                        when {
                                            v >= thresholdPx -> {
                                                act.onToggle(row.id)
                                                swipe.animateTo(0f, tween(220))
                                            }
                                            v <= -thresholdPx -> {
                                                act.onTearCue()
                                                if (reduced) {
                                                    act.onDelete(row.id)
                                                    swipe.snapTo(0f)
                                                } else {
                                                    swipe.snapTo(0f)
                                                    val start = withFrameMillis { it }
                                                    tearT0 = start
                                                    while (true) {
                                                        val n = withFrameMillis { it }
                                                        tearNow = n - start
                                                        if (n - start >= TEAR_TOTAL) break
                                                    }
                                                    act.onDelete(row.id)
                                                }
                                            }
                                            else -> swipe.animateTo(0f, tween(200))
                                        }
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { swipe.animateTo(0f, tween(200)) } },
                        ) { change, amount ->
                            change.consume()
                            scope.launch { swipe.snapTo((swipe.value + amount).coerceIn(-thresholdPx * 1.6f, thresholdPx * 1.6f)) }
                        }
                    },
            )
        }
        if (!tearing) {
            content(Modifier.graphicsLayer { translationX = swipe.value })
        } else {
            val e = tearNow
            // The perforation prints in at 62 % (2 frames, 120 ms), then the halves tumble off (600 ms).
            Perforation(e, Modifier.matchParentSize().padding(horizontal = inset))
            val f = if (e < TEAR_PERFORATE) 0f else TearEasing.transform(((e - TEAR_PERFORATE).toFloat() / TEAR_FALL).coerceIn(0f, 1f))
            content(
                Modifier.graphicsLayer {
                    clip = true
                    shape = HalfShape(left = true)
                    translationX = -40.dp.toPx() * f
                    translationY = 80.dp.toPx() * f
                    rotationZ = -18f * f
                    alpha = 1f - f * 0.6f
                    transformOrigin = TransformOrigin(0.31f, 0.5f)
                },
            )
            content(
                Modifier.graphicsLayer {
                    clip = true
                    shape = HalfShape(left = false)
                    translationX = 30.dp.toPx() * f
                    translationY = 90.dp.toPx() * f
                    rotationZ = 24f * f
                    alpha = 1f - f * 0.6f
                    transformOrigin = TransformOrigin(0.81f, 0.5f)
                },
            )
        }
    }
}

/** The tear-off's beats (§8.6), ms. */
internal const val TEAR_PERFORATE: Long = 120
internal const val TEAR_FALL: Long = 600
internal const val TEAR_TOTAL: Long = TEAR_PERFORATE + TEAR_FALL
private val TearEasing = CubicBezierEasing(0.5f, 0f, 0.7f, 1f)

/** One half of a torn row: the left 62 % or the right 38 %. */
private class HalfShape(private val left: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val x = size.width * 0.62f
        return Outline.Rectangle(if (left) androidx.compose.ui.geometry.Rect(-size.width, -size.height, x, size.height * 2) else androidx.compose.ui.geometry.Rect(x, -size.height, size.width * 2, size.height * 2))
    }
}

/** The perforation printing in at 62 % of the row (2 frames). */
@Composable
private fun Perforation(elapsed: Long, modifier: Modifier) {
    val c = PicnicTheme.colors
    Box(
        modifier
            .decorative()
            .drawBehind {
                val frames = if (elapsed < TEAR_PERFORATE / 2) 1 else 2
                val x = size.width * 0.62f
                val step = 6.dp.toPx()
                val r = 1.dp.toPx()
                var y = step / 2f
                val until = if (frames == 1) size.height / 2f else size.height
                while (y < until) {
                    drawCircle(c.ballpointSoft, r, Offset(x, y))
                    y += step
                }
            },
    )
}

/**
 * The swipe hint (§4.11): right = a lemon-pastel band with a GOT IT hanko growing on the left; left = a backing band with
 * scissors and a perforation growing on the right.
 */
@Composable
private fun SwipeBand(right: Boolean, progress: Float, modifier: Modifier) {
    val c = PicnicTheme.colors
    val p = progress.coerceIn(0f, 1f)
    Box(modifier.decorative().background(if (right) Families.lemon.pastel else c.backing)) {
        if (right) {
            HankoStamp(
                HankoText.GotIt,
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .graphicsLayer {
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                        rotationZ = -12f
                    },
                size = 34.dp,
            )
        } else {
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp)
                        .drawBehind {
                            var y = 0f
                            val step = 6.dp.toPx()
                            while (y < size.height) {
                                drawCircle(c.ballpointSoft, 1.dp.toPx(), Offset(size.width / 2f, y + 1.dp.toPx()))
                                y += step
                            }
                        },
                )
                Spacer(Modifier.width(8.dp))
                Glyph(PicnicGlyph.Scissors, null, tint = c.ballpoint, size = (18 + 6 * p).dp)
            }
        }
    }
}

/**
 * The row's content: the sticker (its ghost once checked), the name and detail (never moving during the chains), the
 * checkbox. One TalkBack node: "Strawberries, 1 litre basket", a checkbox, with Check / Uncheck, Delete and Edit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowContent(
    row: ItemRowUi,
    presentation: ListPresentation,
    clock: MotionClock,
    anchors: AnchorRegistry,
    actions: RowActions,
    modifier: Modifier = Modifier,
    pop: PopRequest? = null,
    sweepT0: Long? = null,
    ambientClock: MotionClock? = null,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val reduced = LocalReducedMotion.current
    val phase = presentation.rows[row.id]
    val start = presentation.rowStarts[row.id]
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val gotIt = stringResource(R.string.list_row_got_it)
    val notChecked = stringResource(R.string.list_row_not_checked)
    val checkLabel = stringResource(if (row.checked) R.string.list_row_uncheck else R.string.list_row_check)
    val deleteLabel = stringResource(R.string.list_row_delete)
    val editLabel = stringResource(R.string.list_row_edit)
    val act by rememberUpdatedState(actions)
    // The pop (§6.2): scale .9 → 1.1 → 1 over 400 ms on the sticker, and a 400 ms highlighter flash on the text band.
    val popScale = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    val probe = LocalPieceProbe.current
    LaunchedEffect(pop?.generation) {
        val p = pop ?: return@LaunchedEffect
        withContext(FixedMotionScale) {
            if (p.delayMs > 0) kotlinx.coroutines.delay(p.delayMs)
            if (reduced) {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(400))
            } else {
                launch {
                    flash.snapTo(1f)
                    flash.animateTo(0f, tween(400))
                }
                popScale.snapTo(0.9f)
                popScale.animateTo(1.1f, tween(160))
                popScale.animateTo(1f, tween(240))
            }
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight())
            .testTag(ListTags.row(row.id))
            .onKeyEvent { e ->
                // Space toggles like Enter (§4.4); Enter is the clickable's own.
                if (e.key == Key.Spacebar && e.type == KeyEventType.KeyUp) {
                    act.onToggle(row.id)
                    true
                } else {
                    e.key == Key.Spacebar
                }
            }
            // The whole row is the toggle; long-press edits. The click and its focus sit outside the clearing semantics,
            // so the one node TalkBack and the keyboard reach keeps both (§4.4, §15).
            .focusProperties { canFocus = true }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onClickLabel = checkLabel,
                onLongClickLabel = editLabel,
                onLongClick = { act.onEdit(row.id) },
                onClick = { act.onToggle(row.id) },
            )
            .clearAndSetSemantics {
                contentDescription = row.spoken
                role = Role.Checkbox
                toggleableState = ToggleableState(row.checked)
                stateDescription = if (row.checked) gotIt else notChecked
                customActions = listOf(
                    CustomAccessibilityAction(checkLabel) { act.onToggle(row.id); true },
                    CustomAccessibilityAction(deleteLabel) { act.onDelete(row.id); true },
                    CustomAccessibilityAction(editLabel) { act.onEdit(row.id); true },
                )
            }
            .padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The sticker box: its anchor first, so it records where the sticker sits, not where a pose moved it.
        with(anchors) {
            Box(
                Modifier
                    .size(52.dp)
                    .probe(probe, "sticker:${row.id}")
                    .anchor(AnchorRegistry.rowKey(row.id))
                    .graphicsLayer {
                        scaleX = popScale.value
                        scaleY = popScale.value
                    },
            ) {
                val ghostAlpha = RowMotion.ghostAlpha(row.checked, phase, start, clock.now, reduced)
                if (ghostAlpha > 0f) {
                    ItemIcon(
                        row.icon, null,
                        Modifier.graphicsLayer { alpha = RowMotion.ghostAlpha(row.checked, phase, start, clock.now, reduced) },
                        ghost = true, aisleIndex = row.aisleIndex,
                    )
                }
                if (ghostAlpha < 1f) {
                    ItemIcon(
                        row.icon, null,
                        Modifier
                            .rowStickerPeel(phase, start, clock)
                            .graphicsLayer { alpha = 1f - RowMotion.ghostAlpha(row.checked, phase, start, clock.now, reduced) }
                            .then(if (ambientClock != null) Modifier.glossSweep(sweepT0, ambientClock) else Modifier),
                        holo = row.holo,
                        flags = row.flags,
                        drawing = row.drawing,
                        aisleIndex = row.aisleIndex,
                        jackpot = row.rarity == earth.cora.picnic.core.model.Rarity.JACKPOT,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
                .highlighterBand(true, c.pressedBand) { if (pressed) 1f else flash.value },
        ) {
            StruckText(
                row.name,
                style = t.itemName.copy(color = if (row.checked) c.inkSoft else c.ink),
                struck = row.checked,
                strike = c.hanko,
                modifier = Modifier.testTag(ListTags.name(row.id)).probe(probe, "name:${row.id}"),
            )
            if (row.detail != null || row.holo) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.detail?.let {
                        Text(it, style = t.itemDetail.copy(color = c.inkSoft), modifier = Modifier.weight(1f, fill = false).testTag(ListTags.detail(row.id)).probe(probe, "detail:${row.id}"))
                    }
                    if (row.holo) RarityChip(jackpot = row.rarity == earth.cora.picnic.core.model.Rarity.JACKPOT)
                }
            }
        }
        with(anchors) {
            Box(Modifier.size(48.dp).anchor(AnchorRegistry.checkKey(row.id)).testTag(ListTags.check(row.id)).probe(probe, "check:${row.id}"), contentAlignment = Alignment.Center) {
                val lifting = RowMotion.liftsHanko(phase)
                if (!row.checked || lifting) StickerSpot()
                if (row.checked || lifting) {
                    val hanko = RowMotion.hankoScale(row.checked, phase, start, clock.now, reduced)
                    Box(Modifier.size(48.dp).rowHanko(row.checked, phase, start, clock, reduced)) {
                        if (hanko > 0f || phase == null) {
                            HankoStamp(row.hanko, Modifier.offset(4.dp, 4.dp).graphicsLayer { rotationZ = hankoAngle(row.id) }, color = c.hanko)
                        }
                    }
                }
            }
        }
    }
}

package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import earth.cora.picnic.R
import earth.cora.picnic.core.model.BasketKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * DECORATIVE SLOTS: CONTRACT (signatures) between app-shell and app-screens (docs/ARCHITECTURE.md §11.3).
 * Placeholder picnic art: gingham ground, bunting, patterned medallions, a stamped check box, a ribbon
 * progress bar, a sleepy-bee empty state, sparkle loaders. All decoration is semantics-free unless a
 * parameter says otherwise. Decorative loops respect PicnicTheme.motion.reduced (static frames).
 */

/**
 * Full-bleed screen ground: gingham at noon, gingham plus a few stars at dusk. Draws under the system bars.
 * Pieces inside it can repaint the ground in line with it (a pinned list band's backing: groundBacking).
 */
@Composable
fun PicnicBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = PicnicTheme.colors
    val anchor = remember { GroundAnchor() }
    Box(
        modifier
            .onPlaced { anchor.coordinates = it }
            .drawBehind { drawGround(c, size) },
    ) {
        CompositionLocalProvider(LocalGround provides anchor) { content() }
    }
}

/** Where the nearest PicnicBackground lies, so a piece can repaint its ground in line with it. */
internal class GroundAnchor {
    var coordinates: LayoutCoordinates? = null
}

/** The nearest PicnicBackground's anchor, or null outside one. */
internal val LocalGround = staticCompositionLocalOf<GroundAnchor?> { null }

/**
 * The ground of a PicnicBackground of size [ground], in that ground's own coordinates, over [area]
 * only. The bands and stars keep the ground's own grid, so any area lines up with the whole.
 */
private fun DrawScope.drawGround(c: PicnicColors, ground: Size, area: Rect = Rect(Offset.Zero, ground)) {
    val cell = GROUND_CELL.dp.toPx()
    val repeat = cell * 2
    clipRect(area.left, area.top, area.right, area.bottom) {
        drawRect(c.background, area.topLeft, area.size)
        drawGingham(
            c.backgroundPattern,
            cell,
            Rect(floor(area.left / repeat) * repeat, floor(area.top / repeat) * repeat, area.right, area.bottom),
        )
        if (c.isDark) drawDuskStars(c.highlight.copy(alpha = 0.35f), ground)
    }
}

private const val GROUND_CELL = 14

/**
 * Backs an element that floats over scrolling content (a pinned list band) with the screen ground of
 * [anchor], in line with it: from the ground's top edge down to the element's bottom edge, across the
 * ground's whole width, behind the element. The scrolling container's clip (a LazyColumn's viewport)
 * trims it to the viewport, so nothing scrolling under the element shows above or beside it, while
 * the element's own drawing (outline, rounded corners, hard shadow below it) stays as it is. No-op
 * outside a PicnicBackground.
 */
internal fun Modifier.groundBacking(anchor: GroundAnchor?, colors: PicnicColors): Modifier =
    if (anchor == null) this else this then GroundBackingElement(anchor, colors)

private data class GroundBackingElement(val anchor: GroundAnchor, val colors: PicnicColors) :
    ModifierNodeElement<GroundBackingNode>() {
    override fun create() = GroundBackingNode(anchor, colors)

    override fun update(node: GroundBackingNode) {
        node.anchor = anchor
        node.colors = colors
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "groundBacking"
    }
}

private class GroundBackingNode(var anchor: GroundAnchor, var colors: PicnicColors) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    /** Where the element sat on the ground when last drawn: moving (being pushed off) redraws it in line. */
    private var drawnAt: Offset? = null

    private fun positionOnGround(element: LayoutCoordinates): Offset? =
        anchor.coordinates?.takeIf { it.isAttached && element.isAttached }?.localPositionOf(element, Offset.Zero)

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (positionOnGround(coordinates) != drawnAt) invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val ground = anchor.coordinates
        val at = positionOnGround(requireLayoutCoordinates())
        drawnAt = at
        if (ground != null && at != null) {
            val groundSize = ground.size.toSize()
            translate(-at.x, -at.y) {
                drawGround(colors, groundSize, Rect(0f, 0f, groundSize.width, at.y + size.height))
            }
        }
        drawContent()
    }
}

/** Deterministic scatter of small twinkles over a ground of size [ground] (same layout every frame and every screenshot). */
private fun DrawScope.drawDuskStars(color: Color, ground: Size) {
    val step = 96.dp.toPx()
    var row = 0
    var y = step * 0.4f
    while (y < ground.height) {
        var x = if (row % 2 == 0) step * 0.3f else step * 0.8f
        var i = 0
        while (x < ground.width) {
            val r = (if ((row + i) % 3 == 0) 4.dp else 2.5.dp).toPx()
            drawPath(sparklePath(Offset(x, y + ((i * 37) % 23).dp.toPx()), r), color)
            x += step
            i++
        }
        y += step * 0.9f
        row++
    }
}

/** Divider between list regions. [major] = between top-level sections (bunting), else a dashed ink line. */
@Composable
fun SectionDivider(modifier: Modifier = Modifier, major: Boolean = false) {
    val c = PicnicTheme.colors
    if (!major) {
        Canvas(
            modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(2.dp),
        ) {
            drawLine(
                c.inkFaint,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        return
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(26.dp),
    ) {
        val sag = 6.dp.toPx()
        val top = 3.dp.toPx()
        // String: a gentle parabola across the width.
        fun stringY(x: Float): Float {
            val t = x / size.width
            return top + sag * 4f * t * (1f - t)
        }
        val flagW = 18.dp.toPx()
        val gap = 6.dp.toPx()
        val count = ((size.width - gap) / (flagW + gap)).toInt().coerceAtLeast(1)
        val start = (size.width - count * (flagW + gap) + gap) / 2f
        val ink = c.outline
        val line = 1.5.dp.toPx()
        for (i in 0 until count) {
            val x0 = start + i * (flagW + gap)
            val x1 = x0 + flagW
            val mid = (x0 + x1) / 2f
            val flag = path {
                moveTo(x0, stringY(x0)); lineTo(x1, stringY(x1)); lineTo(mid, stringY(mid) + 16.dp.toPx()); close()
            }
            fillAndInk(flag, c.aisle(i).base, ink, line)
        }
        val string = path {
            moveTo(0f, stringY(0f))
            var x = 0f
            while (x <= size.width) {
                lineTo(x, stringY(x)); x += 4f
            }
        }
        drawPath(string, ink, style = Stroke(line, cap = StrokeCap.Round))
    }
}

/**
 * Aisle header ornament: a numbered medallion in the aisle's rotation colour with a pattern ring
 * (dots / ticks / scallops / stars by aisle, so aisles never differ by colour alone) and a cel crescent.
 * [number] null = special section (Unsorted / In the basket): a star instead of a number.
 * [aisleIndex] = position - 1, or -1.
 */
@Composable
fun AisleMedallion(number: Int?, aisleIndex: Int, modifier: Modifier = Modifier, size: Dp = PicnicTheme.dimens.medallion) {
    val c = PicnicTheme.colors
    val colors = c.aisle(aisleIndex)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = this.size.minDimension / 2f
            val ink = c.outline
            val line = (r * 0.09f).coerceAtLeast(1.5f)
            drawCircle(colors.pastel, r - line / 2)
            drawPatternRing(aisleIndex, center, r * 0.80f, colors.deep, r)
            // Inner disc with a lower-right cel crescent.
            val inner = r * 0.64f
            drawCircle(colors.base, inner)
            clipPath(path { addOval(androidx.compose.ui.geometry.Rect(center, inner)) }) {
                drawCircle(colors.shadow, inner, center + Offset(inner * 0.35f, inner * 0.35f), style = Stroke(inner * 0.35f))
            }
            drawCircle(ink, inner, style = Stroke(line))
            drawCircle(ink, r - line / 2, style = Stroke(line))
            if (number == null) drawPath(starPath(center, inner * 0.62f), Color.White)
        }
        if (number != null) {
            // The number scales with the medallion (chips use a 20 dp one); never above the token size.
            val base = PicnicTheme.type.aisleNumber
            val fontSize = minOf(base.fontSize.value, size.value * 0.42f).sp
            OutlinedLabel(
                text = number.toString(),
                style = base.copy(fontSize = fontSize, lineHeight = fontSize),
                fill = Color.White,
                stroke = c.outline.takeIf { !c.isDark } ?: colors.deep,
                strokeWidth = (size * 0.08f).coerceIn(1.5.dp, 3.5.dp),
            )
        }
    }
}

/** The ring pattern between the medallion's rim and its disc; varies by aisle so colour is never the only cue. */
private fun DrawScope.drawPatternRing(aisleIndex: Int, c: Offset, radius: Float, color: Color, outer: Float) {
    val n = 12
    when (if (aisleIndex < 0) -1 else aisleIndex % 4) {
        0 -> for (i in 0 until n) drawCircle(color, outer * 0.07f, polar(c, radius, i * 360f / n))
        1 -> for (i in 0 until n) {
            drawLine(color, polar(c, radius - outer * 0.09f, i * 360f / n), polar(c, radius + outer * 0.09f, i * 360f / n), outer * 0.06f, StrokeCap.Round)
        }
        2 -> for (i in 0 until n) drawCircle(color, outer * 0.1f, polar(c, radius + outer * 0.02f, i * 360f / n), style = Stroke(outer * 0.04f))
        3 -> for (i in 0 until n / 2) drawPath(starPath(polar(c, radius, i * 360f / (n / 2)), outer * 0.11f), color)
        else -> for (i in 0 until n) drawPath(sparklePath(polar(c, radius, i * 360f / n), outer * 0.07f), color)
    }
}

private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = deg / 180f * PI.toFloat()
    return Offset(c.x + r * cos(a), c.y + r * sin(a))
}

/**
 * The check control's visual (the row provides the touch target): a rounded box with a bold ink
 * outline and no shadow; checked = filled with the highlight, a heavy tick and a little sparkle
 * flourish, stamped in with a short springy pop (instant with reduced motion). Fixed 48 dp
 * (touch-target) footprint so rows don't jump.
 */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val motion = PicnicTheme.motion
    val stamp by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (motion.reduced) snap() else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "checkStamp",
    )
    Canvas(modifier.size(PicnicTheme.dimens.touchTarget)) {
        val side = CHECK_BOX.toPx()
        val line = 2.5.dp.toPx()
        val topLeft = center - Offset(side / 2, side / 2)
        val corner = CornerRadius(side * 0.3f)
        drawRoundRect(c.surface, topLeft, Size(side, side), corner)
        if (stamp > 0f) {
            // The fill grows from the centre, clipped to the box; the tick overshoots (the "stamp").
            val fill = stamp.coerceAtMost(1f)
            clipRect(topLeft.x, topLeft.y, topLeft.x + side, topLeft.y + side) {
                scale(fill, pivot = center) { drawRoundRect(c.highlight, topLeft, Size(side, side), corner) }
            }
        }
        drawRoundRect(
            c.outline,
            topLeft + Offset(line / 2, line / 2),
            Size(side - line, side - line),
            CornerRadius(corner.x - line / 2),
            style = Stroke(line),
        )
        if (stamp > 0f) {
            scale(stamp, pivot = center) {
                inkLine(
                    c.onHighlight,
                    3.2.dp.toPx(),
                    center + Offset(-6.dp.toPx(), 0.5.dp.toPx()),
                    center + Offset(-1.5.dp.toPx(), 5.dp.toPx()),
                    center + Offset(6.5.dp.toPx(), -4.5.dp.toPx()),
                )
            }
            // Flourish: a twinkle popping off the box's top-right corner.
            drawPath(
                sparklePath(Offset(topLeft.x + side - 1.dp.toPx(), topLeft.y + 1.dp.toPx()), 6.dp.toPx() * stamp.coerceAtMost(1f)),
                c.primary,
            )
        }
    }
}

/**
 * The C checkbox (§4.4, §4.5; rule R2): unchecked = the dashed "place sticker here" spot, checked = the
 * hanko reading [stampText] at an angle seeded from [seed] (the item id; `hankoAngle(id)`), stable across
 * recompositions and restarts. PHASE-0 STUB (WP4): draws the v1 box until WP4's spot and hanko land.
 * Parameter order follows DESIGN_STICKER §19.2 ([stampText] before [modifier]).
 */
@Suppress("ModifierParameter")
@Composable
fun CheckMark(checked: Boolean, seed: String, stampText: HankoText = HankoText.GotIt, modifier: Modifier = Modifier) {
    CheckMark(checked = checked, modifier = modifier)
}

/** Side of the CheckMark box (inside its 48 dp footprint). */
private val CHECK_BOX: Dp = 26.dp

/** Trip progress metaphor (a satin ribbon with a heart charm at its leading end). Announces "done of total". */
@Composable
fun TripProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val description = stringResource(R.string.theme_trip_progress, done, total)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(22.dp)
            .clearAndSetSemantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                stateDescription = description
            },
    ) {
        val ink = c.outline
        val line = 1.6.dp.toPx()
        val h = 12.dp.toPx()
        val top = (size.height - h) / 2
        val r = CornerRadius(h / 2)
        val charm = 9.dp.toPx()
        val trackLeft = 0f
        val trackWidth = size.width - charm
        drawRoundRect(c.surface, Offset(trackLeft, top), Size(trackWidth, h), r)
        val fillW = trackWidth * fraction
        if (fillW > 0f) {
            clipRect(trackLeft, top, trackLeft + fillW, top + h) {
                drawRoundRect(c.primary, Offset(trackLeft, top), Size(trackWidth, h), r)
                // Gingham tint on the filled part.
                drawGingham(c.onPrimary.copy(alpha = 0.18f), 4.dp.toPx(), androidx.compose.ui.geometry.Rect(trackLeft, top, trackLeft + fillW, top + h))
            }
        }
        drawRoundRect(ink, Offset(trackLeft, top), Size(trackWidth, h), r, style = Stroke(line))
        // Heart charm riding the leading end.
        val cx = (trackLeft + fillW).coerceIn(charm, trackWidth)
        val cy = size.height / 2
        val heart = path {
            moveTo(cx, cy + charm * 0.85f)
            cubicTo(cx - charm * 1.3f, cy - charm * 0.1f, cx - charm * 0.6f, cy - charm * 1.1f, cx, cy - charm * 0.4f)
            cubicTo(cx + charm * 0.6f, cy - charm * 1.1f, cx + charm * 1.3f, cy - charm * 0.1f, cx, cy + charm * 0.85f)
            close()
        }
        fillAndInk(heart, if (done >= total && total > 0) c.highlight else c.aisle(6).base, ink, line)
    }
}

/** Empty list / empty screen: an open hamper with a bee asleep on the handle, then title, body and optional action. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    Column(
        modifier.padding(d.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d.m),
    ) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.BottomCenter) {
            AisleBasket(kind = BasketKind.DEFAULT, inBasket = emptyList(), count = 0, size = 120.dp)
            SleepyBee(Modifier.align(Alignment.TopEnd).size(44.dp))
        }
        Text(title, style = PicnicTheme.type.screenTitle, color = c.ink, textAlign = TextAlign.Center)
        Text(body, style = PicnicTheme.type.body, color = c.inkSoft, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
private fun SleepyBee(modifier: Modifier) {
    val c = PicnicTheme.colors
    Canvas(modifier) {
        inGrid(44f) {
            val ink = c.outline
            val line = 1.6f
            // wings
            fillAndInk(path { addOval(androidx.compose.ui.geometry.Rect(14f, 10f, 24f, 22f)) }, Color.White.copy(alpha = 0.9f), ink, line)
            fillAndInk(path { addOval(androidx.compose.ui.geometry.Rect(21f, 9f, 31f, 21f)) }, Color.White.copy(alpha = 0.9f), ink, line)
            // body with stripes
            val body = path { addOval(androidx.compose.ui.geometry.Rect(8f, 18f, 34f, 34f)) }
            drawPath(body, Art.lemon)
            clipPath(body) {
                drawRect(Art.ink, Offset(17f, 16f), Size(3.5f, 20f))
                drawRect(Art.ink, Offset(24f, 16f), Size(3.5f, 20f))
            }
            drawPath(body, ink, style = Stroke(line))
            // closed eye + cheek
            drawArc(Art.ink, 20f, 140f, false, Offset(10f, 23f), Size(5f, 4f), style = Stroke(1.2f, cap = StrokeCap.Round))
            drawCircle(Art.blush, 1.6f, Offset(13f, 29.5f))
            // "z z"
            inkLine(c.inkSoft, 1.4f, Offset(34f, 6f), Offset(38f, 6f), Offset(34f, 10f), Offset(38f, 10f))
            inkLine(c.inkSoft, 1.2f, Offset(39f, 13f), Offset(41.5f, 13f), Offset(39f, 15.5f), Offset(41.5f, 15.5f))
        }
    }
}

/** "The assistant is thinking": three sparkles twinkling in turn (stepped "on threes"). [label] is announced. */
@Composable
fun LoadingSparkle(modifier: Modifier = Modifier, label: String? = null) {
    val c = PicnicTheme.colors
    val phase by rememberDecorPhase(periodMillis = 1_200)
    Row(
        modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            if (label != null) contentDescription = label
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(width = 44.dp, height = 24.dp)) {
            val colors = listOf(c.highlight, c.primary, c.secondary)
            for (i in 0 until 3) {
                val local = ((phase * 3f) - i).let { it - floor(it) }
                // Each sparkle swells then settles; never flashes (scale only, ~0.8 Hz per sparkle).
                val pulse = 0.6f + 0.4f * sin(local * PI.toFloat())
                val cx = 7.dp.toPx() + i * 15.dp.toPx()
                drawPath(sparklePath(Offset(cx, size.height / 2), 7.dp.toPx() * pulse), colors[i])
            }
        }
        if (label != null) {
            Text(label, style = PicnicTheme.type.body, color = c.inkSoft, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/** Small sparkle badge overlaid on an ItemIcon while its icon is being decided or drawn. */
@Composable
fun DrawingSparkle(modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val phase by rememberDecorPhase(periodMillis = 1_600)
    Canvas(modifier.size(16.dp)) {
        rotate(phase * 90f) {
            val r = size.minDimension / 2f
            drawPath(sparklePath(center, r), c.outline)
            drawPath(sparklePath(center, r * 0.72f), c.highlight)
        }
    }
}

/**
 * 0..1 looping phase for decorative motion, stepped at PicnicTheme.motion.stepFps ("on threes").
 * Static (0) with reduced motion and in previews/screenshots.
 */
@Composable
private fun rememberDecorPhase(periodMillis: Int): State<Float> {
    val motion = PicnicTheme.motion
    if (motion.reduced || LocalInspectionMode.current) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "decor")
    val raw = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "decorPhase",
    )
    val steps = (periodMillis / 1000f * motion.stepFps).coerceAtLeast(1f)
    return remember(raw, steps) {
        object : State<Float> {
            override val value: Float get() = floor(raw.value * steps) / steps
        }
    }
}

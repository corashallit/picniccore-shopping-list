package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Drawing toolkit for the placeholder art (internal to the theme layer). Everything is flat fills,
 * ink outlines and HARD offset shadows: no blur, no elevation, no gradients, so it renders the same
 * on device and in Robolectric screenshots. The art pass may replace any of it.
 */

/** Hard cel shadow: the shape's outline drawn once, offset (no blur). Place before clip/background. */
internal fun Modifier.hardShadow(shape: Shape, color: Color, offset: DpOffset): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(offset.x.toPx(), offset.y.toPx()) { drawOutline(outline, color) }
}

/** Draws [block] in a [units] x [units] design grid scaled to fit the current draw area (centred). */
internal inline fun DrawScope.inGrid(units: Float, block: DrawScope.() -> Unit) {
    val s = min(size.width, size.height) / units
    val dx = (size.width - units * s) / 2f
    val dy = (size.height - units * s) / 2f
    withTransform({
        translate(dx, dy)
        scale(s, s, pivot = Offset.Zero)
    }) { block() }
}

/** Fill + ink outline with round joins (the house style for every placeholder object). */
internal fun DrawScope.fillAndInk(path: Path, fill: Color, ink: Color, width: Float) {
    drawPath(path, fill)
    drawPath(path, ink, style = Stroke(width = width, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

/** A round-capped polyline. (Offset is a value class, so no vararg: fixed-arity overloads below.) */
internal fun DrawScope.inkLine(ink: Color, width: Float, points: List<Offset>) {
    val p = Path()
    points.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
    drawPath(p, ink, style = Stroke(width = width, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

internal fun DrawScope.inkLine(ink: Color, width: Float, a: Offset, b: Offset) = inkLine(ink, width, listOf(a, b))

internal fun DrawScope.inkLine(ink: Color, width: Float, a: Offset, b: Offset, c: Offset) = inkLine(ink, width, listOf(a, b, c))

internal fun DrawScope.inkLine(ink: Color, width: Float, a: Offset, b: Offset, c: Offset, d: Offset) =
    inkLine(ink, width, listOf(a, b, c, d))

internal fun DrawScope.inkLine(ink: Color, width: Float, a: Offset, b: Offset, c: Offset, d: Offset, e: Offset) =
    inkLine(ink, width, listOf(a, b, c, d, e))

internal fun path(block: Path.() -> Unit): Path = Path().apply(block)

/** Five-point (or n-point) star centred at [c]. */
internal fun starPath(c: Offset, outer: Float, inner: Float = outer * 0.48f, points: Int = 5, rotationDeg: Float = -90f): Path {
    val p = Path()
    val step = PI / points
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val a = Math.toRadians(rotationDeg.toDouble()) + i * step
        val x = c.x + (r * cos(a)).toFloat()
        val y = c.y + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/** Four-point "twinkle" sparkle with concave sides (never a Unicode glyph). */
internal fun sparklePath(c: Offset, r: Float, pinch: Float = 0.22f): Path {
    val k = r * pinch
    return Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + k, c.y - k, c.x + r, c.y)
        quadraticTo(c.x + k, c.y + k, c.x, c.y + r)
        quadraticTo(c.x - k, c.y + k, c.x - r, c.y)
        quadraticTo(c.x - k, c.y - k, c.x, c.y - r)
        close()
    }
}

/**
 * Gingham: vertical bands then horizontal bands of [color] (translucent), so crossings double up.
 * [cell] is the band width in px; bands repeat every 2 cells.
 */
internal fun DrawScope.drawGingham(color: Color, cell: Float, area: Rect = Rect(Offset.Zero, size)) {
    var x = area.left
    while (x < area.right) {
        drawRect(color, Offset(x, area.top), Size(min(cell, area.right - x), area.height))
        x += cell * 2
    }
    var y = area.top
    while (y < area.bottom) {
        drawRect(color, Offset(area.left, y), Size(area.width, min(cell, area.bottom - y)))
        y += cell * 2
    }
}

/**
 * Text with a hard ink outline ("paint-order: stroke fill" done as two passes), for wordmarks and
 * medallion numbers. Decorative copy only: the stroke pass is hidden from accessibility.
 */
@Composable
internal fun OutlinedLabel(
    text: String,
    style: TextStyle,
    fill: Color,
    stroke: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    textAlign: TextAlign? = null,
) {
    Box(modifier) {
        Text(
            text,
            style = style.copy(drawStyle = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { strokeWidth.toPx() }, join = StrokeJoin.Round)),
            color = stroke,
            textAlign = textAlign,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(text, style = style, color = fill, textAlign = textAlign)
    }
}

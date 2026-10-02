package earth.cora.picnic.ui.theme

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Drawing toolkit (internal to the theme layer, owner WP4). Everything is flat fills and HARD offset shadows: no blur,
 * no elevation, no live filter (§2.10, item 307), so it renders the same on device and in Robolectric screenshots.
 */

/** Hard cel shadow: the shape's outline drawn once at [offset], no blur (§2.10). Place before clip/background. */
fun Modifier.hardShadow(shape: Shape, color: Color, offset: DpOffset): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(offset.x.toPx(), offset.y.toPx()) { drawOutline(outline, color) }
}

/** The variant's small (hs) or large (hs2) hard shadow under [shape]. */
fun Modifier.hardShadow(shape: Shape, shadows: PicnicShadows, large: Boolean = false): Modifier =
    hardShadow(shape, shadows.color, if (large) shadows.hs2 else shadows.hs)

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

/** Fill + outline with round joins. */
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

/** Five-point (or n-point) star centred at [c], with rounded-feeling proportions (inner = 0.48 outer by default). */
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

/** A puffy heart centred at [c], [w] wide. */
internal fun heartPath(c: Offset, w: Float): Path {
    val h = w * 0.9f
    val top = c.y - h * 0.42f
    return Path().apply {
        moveTo(c.x, c.y + h * 0.5f)
        cubicTo(c.x - w * 0.62f, c.y + h * 0.02f, c.x - w * 0.52f, top - h * 0.28f, c.x, top + h * 0.06f)
        cubicTo(c.x + w * 0.52f, top - h * 0.28f, c.x + w * 0.62f, c.y + h * 0.02f, c.x, c.y + h * 0.5f)
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
 * v1 outlined label (two passes, the stroke hidden from accessibility). Superseded by [PuffyText], which reserves room
 * for its stroke and shadow; kept for v1 call sites.
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

/* ------------------------------------------------------------------------------------------------
 * Vector art: the generated sticker, decor, pictogram, glyph and pattern drawables (§3.2), flattened once into
 * paths so the theme can draw any named group, tinted or recoloured, without a Painter per variant.
 * ------------------------------------------------------------------------------------------------ */

/** One flattened path of a generated drawable, in viewport units. */
@Immutable
class ArtPath(
    val name: String,
    /** The top-level named group it belongs to ("cut", "fills", "lines", "gloss", "dominant", or "" for none). */
    val group: String,
    val path: Path,
    val fill: Brush?,
    val fillAlpha: Float,
    val stroke: Brush?,
    val strokeAlpha: Float,
    val strokeWidth: Float,
    val cap: StrokeCap,
    val join: StrokeJoin,
    val miter: Float,
    /** Clip paths of the enclosing groups (viewport units), innermost last. */
    val clips: List<Path>,
)

/** A generated drawable, flattened: every path in drawing order, plus each named group's clip path. */
@Immutable
class VectorArt(val viewport: Size, val paths: List<ArtPath>, val groupClips: Map<String, Path>) {
    fun has(group: String): Boolean = paths.any { it.group == group } || group in groupClips
}

/** Loads and flattens drawables once per process (they are immutable resources). */
object VectorArtCache {
    private val cache = ConcurrentHashMap<Int, VectorArt>()

    fun get(resources: Resources, @DrawableRes id: Int): VectorArt =
        cache.getOrPut(id) { flatten(ImageVector.vectorResource(null, resources, id)) }

    private fun flatten(vector: ImageVector): VectorArt {
        val out = mutableListOf<ArtPath>()
        val clips = mutableMapOf<String, Path>()
        // The emitter never writes group transforms (translate / scale / rotate), so paths are used as they are.
        fun walk(group: VectorGroup, top: String, outerClips: List<Path>) {
            val groupClips = if (group.clipPathData.isNotEmpty()) {
                val clip = group.clipPathData.toPath()
                if (top.isNotEmpty() && top !in clips) clips[top] = clip
                outerClips + clip
            } else {
                outerClips
            }
            for (node in group) {
                when (node) {
                    is VectorGroup -> walk(node, top.ifEmpty { node.name }, groupClips)
                    is VectorPath -> {
                        val p = node.pathData.toPath().also { it.fillType = node.pathFillType }
                        out += ArtPath(
                            name = node.name, group = top, path = p,
                            fill = node.fill, fillAlpha = node.fillAlpha,
                            stroke = node.stroke, strokeAlpha = node.strokeAlpha, strokeWidth = node.strokeLineWidth,
                            cap = node.strokeLineCap, join = node.strokeLineJoin, miter = node.strokeLineMiter,
                            clips = groupClips,
                        )
                    }
                }
            }
        }
        walk(vector.root, "", emptyList())
        return VectorArt(Size(vector.viewportWidth, vector.viewportHeight), out, clips)
    }
}

/** The flattened art of a drawable, loaded once. */
@Composable
internal fun rememberVectorArt(@DrawableRes id: Int): VectorArt = VectorArtCache.get(LocalResources.current, id)

/** How one path is painted: return null to keep its own paint, or a colour to replace both fill and stroke. */
internal typealias ArtPaint = (ArtPath, isStroke: Boolean) -> Brush?

/**
 * Draws [art] scaled to [size] at [topLeft]: only the paths whose top-level group is in [groups] (null = all except
 * the clip-only `dominant`), each fill then stroke, with its own clips. [paint] may replace a path's paint; [strokeExtra]
 * widens every stroke by that many viewport units (the ghost's 11u outline round the 8u cut).
 */
internal fun DrawScope.drawArt(
    art: VectorArt,
    size: Size = this.size,
    topLeft: Offset = Offset.Zero,
    groups: Set<String>? = null,
    paint: ArtPaint? = null,
    alpha: Float = 1f,
    fills: Boolean = true,
    strokes: Boolean = true,
) {
    val sx = size.width / art.viewport.width
    val sy = size.height / art.viewport.height
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(sx, sy, pivot = Offset.Zero)
    }) {
        for (p in art.paths) {
            if (groups != null && p.group !in groups) continue
            if (groups == null && p.group == "dominant") continue
            drawClipped(p.clips) {
                if (fills) {
                    // An override only repaints what the path already paints (a stroke-only path stays unfilled).
                    val fill = p.fill?.let { paint?.invoke(p, false) ?: it }
                    if (fill != null) drawPath(p.path, fill, alpha = p.fillAlpha * alpha, style = Fill)
                }
                if (strokes && p.strokeWidth > 0f) {
                    val stroke = p.stroke?.let { paint?.invoke(p, true) ?: it }
                    if (stroke != null) {
                        drawPath(
                            p.path, stroke, alpha = p.strokeAlpha * alpha,
                            style = Stroke(width = p.strokeWidth, cap = p.cap, join = p.join, miter = p.miter),
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawClipped(clips: List<Path>, block: DrawScope.() -> Unit) {
    when (clips.size) {
        0 -> block()
        1 -> clipPath(clips[0]) { block() }
        else -> clipPath(clips[0]) { drawClipped(clips.drop(1), block) }
    }
}

/** Every path of [art] painted [color] (fill and stroke): a tinted silhouette (shadows, mono). */
internal fun solid(color: Color): ArtPaint {
    val brush = SolidColor(color)
    return { _, _ -> brush }
}

/** Runs [block] in an offscreen layer composited at [alpha], so overlapping paths do not double up. */
internal inline fun DrawScope.inLayer(alpha: Float, bounds: Rect = Rect(Offset.Zero, size), block: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
        block()
        canvas.restore()
    }
}

/** True if [color] is pure, opaque white. */
internal fun Color.isOpaqueWhite(): Boolean = this == Color.White

/** The fill type a die-cut silhouette needs (its pathData is a union outline). */
internal val CutFill: PathFillType = PathFillType.NonZero

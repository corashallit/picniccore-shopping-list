package earth.cora.picnic.ui.theme

import android.content.res.Resources
import android.graphics.BitmapShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalResources
import androidx.core.graphics.set
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import earth.cora.picnic.ui.theme.gen.PatternTiles
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Patterns and textures (docs/DESIGN_STICKER.md §2.12). The pinstripe binder and the tab patterns are cached
 * `ImageBitmap` tiles drawn through repeating shaders, never redrawn stripe by stripe per frame (item 306,
 * ShaderCacheTest counts tile creations). The ground is aligned to the WINDOW origin, so a pinned piece's backing
 * ([groundBacking]) lines up with the real ground pixel for pixel (R6, GroundTest).
 */

/** The tile cache. [tileCreations] counts every tile ever rasterised (ShaderCacheTest). */
object PatternCache {
    val tileCreations = AtomicInteger(0)
    private val tiles = ConcurrentHashMap<String, ImageBitmap>()

    /** The pinstripe tile: 10 dp × 1 dp, a 2 dp [stripe] stripe at its left, transparent elsewhere. */
    fun pinstripe(density: Float, stripe: Color): ImageBitmap = tiles.getOrPut("pin:$density:${stripe.toArgb()}") {
        tileCreations.incrementAndGet()
        val w = max(1, (10f * density).roundToInt())
        val s = max(1, (2f * density).roundToInt())
        val bmp = androidx.core.graphics.createBitmap(w, 1)
        for (x in 0 until s) bmp[x, 0] = stripe.toArgb()
        bmp.asImageBitmap()
    }

    /** A tab pattern mask tile (white on transparent), rasterised from `sk_t_<name>` at [density]. Null for plain. */
    fun tabTile(resources: Resources, pattern: TabPattern, density: Float): ImageBitmap? {
        if (pattern == TabPattern.Plain) return null
        val tile = PatternTiles.tiles[pattern.key] ?: return null
        return tiles.getOrPut("tab:${pattern.key}:$density") {
            tileCreations.incrementAndGet()
            val art = VectorArtCache.get(resources, tile.drawable)
            val w = max(1, (tile.widthDp * density).roundToInt())
            val h = max(1, (tile.heightDp * density).roundToInt())
            val image = ImageBitmap(w, h)
            val canvas = androidx.compose.ui.graphics.Canvas(image)
            CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                drawArt(art)
            }
            image
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * The binder ground (§4.1): binder colour (Golden: gradient; Dusk: + three bands) + window-aligned pinstripes.
 * ------------------------------------------------------------------------------------------------ */

/**
 * Paints the binder ground behind this element, aligned to the window origin. [extendToWindowTop] paints from the
 * window's top edge down to this element's bottom (a pinned piece hides everything scrolling under and above it,
 * R6); [fullWidth] paints across the whole window width (so the rounded corners and side gaps of a pinned plate show
 * ground, never a row). Parents' clips (a list viewport) still trim it.
 */
fun Modifier.binderGround(colors: PicnicColors, extendToWindowTop: Boolean = false, fullWidth: Boolean = false): Modifier =
    this then GroundElement(colors, extendToWindowTop, fullWidth)

/**
 * Repaints the binder ground behind a pinned piece (the top stack, the pinned aisle plate): window-aligned, so the
 * stripes line up with the real ground, from the window top down to the piece's bottom, across the full width (R6).
 */
@Composable
fun Modifier.groundBacking(): Modifier = binderGround(PicnicTheme.colors, extendToWindowTop = true, fullWidth = true)

/** v1 form (WP5's ListPieces): the anchor is no longer needed, the ground is window-aligned. */
internal fun Modifier.groundBacking(@Suppress("UNUSED_PARAMETER") anchor: GroundAnchor?, colors: PicnicColors): Modifier =
    binderGround(colors, extendToWindowTop = true, fullWidth = true)

private data class GroundElement(val colors: PicnicColors, val toTop: Boolean, val fullWidth: Boolean) :
    ModifierNodeElement<GroundNode>() {
    override fun create() = GroundNode(colors, toTop, fullWidth)

    override fun update(node: GroundNode) {
        node.colors = colors
        node.toTop = toTop
        node.fullWidth = fullWidth
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "binderGround"
    }
}

private class GroundNode(var colors: PicnicColors, var toTop: Boolean, var fullWidth: Boolean) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var windowPos: Offset = Offset.Zero
    private var windowSize: Size = Size.Zero

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val pos = coordinates.positionInWindow()
        val root = coordinates.findRootCoordinates().size.toSize()
        if (pos != windowPos || root != windowSize) {
            windowPos = pos
            windowSize = root
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val top = if (toTop) -windowPos.y else 0f
        val left = if (fullWidth) -windowPos.x else 0f
        val right = if (fullWidth) max(size.width, windowSize.width - windowPos.x) else size.width
        drawBinder(colors, windowPos, windowSize, Rect(left, top, right, size.height))
        drawContent()
    }
}

/**
 * The binder over [area] (local coordinates) of an element at [windowPos] in a window of [windowSize]: everything
 * computed in window coordinates, so any two elements agree pixel for pixel where they overlap.
 */
internal fun DrawScope.drawBinder(c: PicnicColors, windowPos: Offset, windowSize: Size, area: Rect) {
    if (area.isEmpty) return
    clipRect(area.left, area.top, area.right, area.bottom) {
        val bottom = c.binderBottom
        if (bottom != null && windowSize.height > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(c.binder, bottom), startY = -windowPos.y, endY = windowSize.height - windowPos.y),
                area.topLeft, area.size,
            )
        } else {
            drawRect(c.binder, area.topLeft, area.size)
        }
        c.duskBands.forEachIndexed { i, band ->
            val y0 = (DUSK_BAND * i).dp.toPx() - windowPos.y
            drawRect(band, Offset(area.left, y0), Size(area.width, DUSK_BAND.dp.toPx()))
        }
        val tile = PatternCache.pinstripe(density, c.binderStripe.copy(alpha = c.binderStripe.alpha * c.binderStripeAlpha))
        drawIntoCanvas { canvas ->
            val shader = BitmapShader(tile.asAndroidBitmap(), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            val m = android.graphics.Matrix()
            // Local x maps to window x + windowPos.x: shift the tile so stripes sit on window multiples of 10 dp.
            m.setTranslate(-windowPos.x, 0f)
            shader.setLocalMatrix(m)
            val paint = android.graphics.Paint().apply { this.shader = shader }
            canvas.nativeCanvas.drawRect(area.left, area.top, area.right, area.bottom, paint)
        }
    }
}

private const val DUSK_BAND = 40

/** v1: where the nearest PicnicBackground lies. The ground is window-aligned now, so it only marks "inside one". */
internal class GroundAnchor {
    var coordinates: LayoutCoordinates? = null
}

/** The nearest PicnicBackground's anchor, or null outside one. */
internal val LocalGround = androidx.compose.runtime.staticCompositionLocalOf<GroundAnchor?> { null }

/* ------------------------------------------------------------------------------------------------
 * Glow stars and Dusk sparkles: texture in the binder GUTTERS only (§2.12), never on a page.
 * ------------------------------------------------------------------------------------------------ */

/** One gutter decoration: its centre (px) and size (px), and its loop delay. */
@Immutable
data class GutterMark(val center: Offset, val size: Float, val delayMillis: Long)

/**
 * Deterministic gutter positions for a ground of [width] × [height] (px) with side gutters [gutter] (px) wide: marks of
 * [markSize] sit with their inner edge on the gutter line or outside it (they may run off the screen edge), so none
 * ever reaches a page. [count] marks alternate sides down the ground.
 */
fun gutterMarks(width: Float, height: Float, gutter: Float, markSize: Float, count: Int, seed: Int = 7): List<GutterMark> {
    if (width <= 0f || height <= 0f || count <= 0) return emptyList()
    val out = mutableListOf<GutterMark>()
    val step = height / count
    for (i in 0 until count) {
        val left = (i + seed) % 2 == 0
        val jitter = ((i * 37 + seed * 11) % 23) / 23f
        val y = step * (i + 0.25f + jitter * 0.5f)
        val inner = gutter - markSize / 2f - (jitter * gutter * 0.2f)
        val x = if (left) inner else width - inner
        out += GutterMark(Offset(x, y), markSize, delayMillis = (i * 1300L) % PicnicMotion.glowChargePeriod)
    }
    return out
}

/* ------------------------------------------------------------------------------------------------
 * Tab patterns (§2.12): white mask tiles printed in the aisle mid on the right 26 dp of a tab.
 * ------------------------------------------------------------------------------------------------ */

/** A repeating brush of [pattern]'s tile (white on transparent); draw it with a tint colour filter. Null for plain. */
@Composable
fun rememberPatternBrush(pattern: TabPattern): ShaderBrush? {
    val resources = LocalResources.current
    val density = LocalDensity.current.density
    return remember(pattern, density) {
        PatternCache.tabTile(resources, pattern, density)?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
    }
}

/**
 * Prints [look]'s tab pattern in its mid colour at [alpha] on the right [width] of this element (never under the
 * name: the caller pads its label clear of that strip, TabLayoutTest). Tab 50 %, the Next card's tab 45 %.
 */
@Composable
fun Modifier.tabPattern(look: AisleLook, width: Dp = 26.dp, alpha: Float = 0.5f): Modifier {
    val brush = rememberPatternBrush(look.pattern) ?: return this
    val tint = ColorFilter.tint(look.family.mid, BlendMode.SrcIn)
    return drawBehind {
        val w = width.toPx().coerceAtMost(size.width)
        drawRect(brush, Offset(size.width - w, 0f), Size(w, size.height), alpha = alpha, colorFilter = tint)
    }
}

/** Fills this element with [look]'s pattern in its mid at [alpha] (tray end caps 50–55 %). */
@Composable
fun Modifier.patternFill(look: AisleLook, alpha: Float = 0.55f): Modifier {
    val brush = rememberPatternBrush(look.pattern) ?: return this
    val tint = ColorFilter.tint(look.family.mid, BlendMode.SrcIn)
    return drawBehind { drawRect(brush, alpha = alpha, colorFilter = tint) }
}

/* ------------------------------------------------------------------------------------------------
 * Pad textures: ruled lines and margin, punch holes, perforated and postage edges.
 * ------------------------------------------------------------------------------------------------ */

/**
 * Ruled pad (§2.12): 1 dp [color] lines every [pitch] starting at [firstLine], and an optional 2 dp [margin] line at
 * [marginX]. Drawn behind the content.
 */
fun Modifier.ruledLines(pitch: Dp, color: Color, firstLine: Dp = pitch, margin: Color? = null, marginX: Dp = 26.dp): Modifier =
    drawBehind {
        val step = pitch.toPx()
        var y = firstLine.toPx()
        val w = 1.dp.toPx()
        while (y <= size.height + 0.5f) {
            drawRect(color, Offset(0f, y - w), Size(size.width, w))
            y += step
        }
        if (margin != null) drawRect(margin, Offset(marginX.toPx() - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }

/**
 * Punch holes (§2.12): 8 dp [hole]-coloured circles (the binder) with a 35 % periwinkle rim, centred at x 13 dp every
 * 30 dp. ONLY on pad cards (settings cards, About, the empty-state card); never on trays, never under text.
 */
fun Modifier.punchHoles(hole: Color, rim: Color = Families.periwinkle.mid.copy(alpha = 0.35f)): Modifier = drawWithContent {
    drawContent()
    for (c in punchHoleCenters(size.height, density)) {
        drawCircle(hole, 4.dp.toPx(), c)
        drawCircle(rim, 4.dp.toPx(), c, style = Stroke(1.2.dp.toPx()))
    }
}

/** Punch hole centres (px) down a card [height] px tall: x 13 dp, every 30 dp, from 18 dp, clear of the bottom edge. */
fun punchHoleCenters(height: Float, density: Float): List<Offset> {
    val out = mutableListOf<Offset>()
    var y = 18f * density
    while (y <= height - 12f * density) {
        out += Offset(13f * density, y)
        y += 30f * density
    }
    return out
}

/**
 * A rounded rectangle whose top edge (or all four edges, [allSides]) is bitten by semicircles of [bite] diameter every
 * [every]: the perforated top of the add bar and sheets (4 dp every 10 dp) and the postage edge (6 dp every 10 dp).
 */
@Immutable
class BittenEdgeShape(
    private val bite: Dp,
    private val every: Dp,
    private val allSides: Boolean = false,
    private val topCorners: Dp = 0.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { bite.toPx() } / 2f
        val step = with(density) { every.toPx() }
        val corner = with(density) { topCorners.toPx() }
        val body = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner), CornerRadius(corner), CornerRadius.Zero, CornerRadius.Zero))
        }
        val bites = Path()
        for (c in biteCenters(size, r, step, allSides, corner)) bites.addOval(Rect(c, r))
        return Outline.Generic(Path.combine(PathOperation.Difference, body, bites))
    }

    override fun equals(other: Any?): Boolean =
        other is BittenEdgeShape && other.bite == bite && other.every == every && other.allSides == allSides && other.topCorners == topCorners

    override fun hashCode(): Int = ((bite.hashCode() * 31 + every.hashCode()) * 31 + allSides.hashCode()) * 31 + topCorners.hashCode()
}

/** The bite centres of a [size] edge: evenly spaced, the run centred, clear of rounded [corner]s. */
internal fun biteCenters(size: Size, r: Float, step: Float, allSides: Boolean, corner: Float = 0f): List<Offset> {
    fun along(length: Float, inset: Float): List<Float> {
        val usable = length - inset * 2
        if (usable <= step) return emptyList()
        val n = floor(usable / step).toInt()
        val start = inset + (usable - (n - 1) * step) / 2f
        return List(n) { start + it * step }
    }
    val out = mutableListOf<Offset>()
    along(size.width, max(corner, r * 1.5f)).forEach { out += Offset(it, 0f) }
    if (allSides) {
        along(size.width, r * 1.5f).forEach { out += Offset(it, size.height) }
        along(size.height, r * 1.5f).forEach { out += Offset(0f, it); out += Offset(size.width, it) }
    }
    return out
}

/** The 55 % periwinkle rim under each top bite of a perforated edge (§2.12). */
fun DrawScope.drawPerforationRims(bite: Dp = 4.dp, every: Dp = 10.dp, corner: Dp = 0.dp, color: Color = Families.periwinkle.mid.copy(alpha = 0.55f)) {
    val r = bite.toPx() / 2f
    for (c in biteCenters(size, r, every.toPx(), allSides = false, corner = corner.toPx())) {
        drawArc(color, 0f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(1.dp.toPx()))
    }
}

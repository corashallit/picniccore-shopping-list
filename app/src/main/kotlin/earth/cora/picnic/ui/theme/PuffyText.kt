package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import earth.cora.picnic.ui.a11y.textOverflowed
import kotlin.math.ceil
import kotlin.math.max

/*
 * Puffy lettering (docs/DESIGN_STICKER.md §2.7, items 23 and 334): the stroke pass first (white; Glow #3A438A), then
 * the fill, all on a hard shadow of the stroked word. Screen titles, the wordmark, NEXT, the celebration title.
 *
 * PUFFY TEXT NEVER CLIPS. The box is laid out from the STROKE pass, not the fill: half the stroke on every side plus
 * the shadow offset on the right and bottom, and the last glyph's overhang (from the layout's bounding boxes, not the
 * advance width). Nothing here clips; an ellipsized title is ellipsized inside that padded box.
 *
 * NO PINHOLES. Each glyph's stroke is its own halo, half the pen wide. Where two glyphs sit closer than a pen width
 * without touching (DynaPuff kerns "XT" in NEXT that tight), their halos meet everywhere around a point that neither
 * reaches: a pinhole in the white, through which the hard shadow showed as a dark speck. The same happens in a counter
 * a little wider than the pen, and where two halos nearly meet they leave a hairline crack. A die-cut never leaves a
 * hole or a slit narrower than its blade, so after laying out, the stroke pass is rasterised once and every uncovered
 * region the open ground cannot reach through a neck wider than 2 px, no bigger than the pen ([PuffyPinholes]), is
 * filled in the stroke colour (and, offset, in the shadow colour). Larger counters and the gaps between words stay open.
 */

/** The fitted font size (sp) a PuffyText chose (tests: AppBarTest, PuffyTextBoundsTest). */
val PuffyFontSize: SemanticsPropertyKey<Float> = SemanticsPropertyKey("PuffyFontSize")
internal var SemanticsPropertyReceiver.puffyFontSize: Float by PuffyFontSize

/** The drawn ink bounds (stroke and shadow included), in the node's own px (PuffyTextBoundsTest). */
val PuffyInkBounds: SemanticsPropertyKey<Rect> = SemanticsPropertyKey("PuffyInkBounds")
internal var SemanticsPropertyReceiver.puffyInkBounds: Rect by PuffyInkBounds

/**
 * Puffy lettering. [stroke] is the full stroke width (titles 6 dp, the wordmark 7 dp, NEXT 6 dp, the celebration 9 dp).
 * When [minFontSize] is below the style's size, a text that does not fit shrinks in 1 sp steps down to it, then
 * ellipsizes (and sets the TextOverflowed semantics key). [heading] marks screen titles for TalkBack.
 */
@Composable
fun PuffyText(
    text: String,
    style: TextStyle,
    fill: Color,
    stroke: Dp,
    modifier: Modifier = Modifier,
    strokeColor: Color = PicnicTheme.colors.puffyStroke,
    shadow: DpOffset? = PicnicTheme.shadows.hs,
    shadowColor: Color = PicnicTheme.shadows.color,
    minFontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
    heading: Boolean = false,
) {
    PuffyText(
        AnnotatedString(text), style.copy(color = fill), stroke, modifier, strokeColor, shadow, shadowColor,
        minFontSize, maxLines, textAlign, heading,
    )
}

/** Puffy lettering with coloured spans (the wordmark: "Kirakira" strawberry mid, "Basket" ramune mid). */
@Composable
fun PuffyText(
    text: AnnotatedString,
    style: TextStyle,
    stroke: Dp,
    modifier: Modifier = Modifier,
    strokeColor: Color = PicnicTheme.colors.puffyStroke,
    shadow: DpOffset? = PicnicTheme.shadows.hs,
    shadowColor: Color = PicnicTheme.shadows.color,
    minFontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
    heading: Boolean = false,
) {
    // No layout cache: TextMeasurer's cache ignores colour, and each pass is laid out in its own colour.
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val fit = remember(text, style, stroke, shadow, minFontSize, maxLines, textAlign, maxWidth, density, strokeColor, shadowColor) {
            with(density) {
                fitPuffy(
                    measurer, text, style, stroke.toPx(),
                    shadow?.x?.toPx()?.coerceAtLeast(0f) ?: 0f, shadow?.y?.toPx()?.coerceAtLeast(0f) ?: 0f,
                    minFontSize, maxLines, textAlign, maxWidth, strokeColor, shadowColor,
                )
            }
        }
        val widthDp = with(density) { fit.width.toDp() }
        val heightDp = with(density) { fit.height.toDp() }
        val pinholes = remember(fit, density) { PuffyPinholes.find(fit, density) }
        Box(
            Modifier
                .size(widthDp, heightDp)
                .semantics {
                    this.text = text
                    puffyFontSize = fit.fontSize
                    puffyInkBounds = fit.ink
                    if (fit.overflowed) textOverflowed = true
                    if (heading) this.heading()
                }
                .drawBehind {
                    val strokeStyle = Stroke(width = fit.half * 2, join = StrokeJoin.Round)
                    if (fit.sx > 0f || fit.sy > 0f) {
                        translate(fit.half + fit.sx, fit.half + fit.sy) {
                            drawText(fit.shadow, shadowColor, drawStyle = strokeStyle)
                            drawText(fit.shadow, shadowColor, drawStyle = Fill)
                        }
                        if (pinholes != null) {
                            drawImage(pinholes, Offset(fit.sx, fit.sy), colorFilter = ColorFilter.tint(shadowColor, BlendMode.SrcIn))
                        }
                    }
                    translate(fit.half, fit.half) { drawText(fit.outline, strokeColor, drawStyle = strokeStyle) }
                    if (pinholes != null) drawImage(pinholes, colorFilter = ColorFilter.tint(strokeColor, BlendMode.SrcIn))
                    translate(fit.half, fit.half) { drawText(fit.fill, drawStyle = Fill) }
                },
        )
    }
}

/** The measured, fitted puffy text: layouts and the padded box (px). */
@Immutable
internal class PuffyFit(
    val fill: TextLayoutResult,
    /** The stroke pass, laid out in the stroke colour (a layout's own colour wins over a draw-time colour). */
    val outline: TextLayoutResult,
    /** The shadow pass, in the shadow colour. */
    val shadow: TextLayoutResult,
    val half: Float,
    val sx: Float,
    val sy: Float,
    val fontSize: Float,
    val overflowed: Boolean,
    val width: Int,
    val height: Int,
    val ink: Rect,
)

/**
 * Fits [text]: the style's size if it fits in [maxWidth] minus the stroke and shadow room, else 1 sp smaller each step
 * down to [minFontSize], else ellipsized at [minFontSize]: the longest start of the text that fits with "…" after it,
 * spaces before the "…" dropped ("Everything…", never "Everything …"), and the box as wide as what is drawn, so an
 * ellipsized title never trails a gap before what follows it (the sun sticker).
 */
internal fun fitPuffy(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    strokePx: Float,
    sx: Float,
    sy: Float,
    minFontSize: TextUnit,
    maxLines: Int,
    textAlign: TextAlign,
    maxWidth: Int,
    strokeColor: Color = Color.White,
    shadowColor: Color = Color.Black,
): PuffyFit {
    val half = strokePx / 2f
    val room = half * 2 + sx
    val maxText = if (maxWidth == Constraints.Infinity) Constraints.Infinity else max(0, (maxWidth - room).toInt())
    val from = style.fontSize.value
    val to = if (minFontSize.isSpecified) minFontSize.value.coerceAtMost(from) else from
    var size = from
    var layout: TextLayoutResult
    var overflowed: Boolean
    var shown = text
    fun fits(l: TextLayoutResult) = !l.hasVisualOverflow && inkRight(l) <= maxText + 0.5f
    while (true) {
        layout = measurePuffy(measurer, text, style, size, maxText, maxLines, TextOverflow.Clip, textAlign)
        if (fits(layout)) {
            overflowed = false
            break
        }
        if (size - 1f < to - 0.001f) {
            size = to
            // The longest start that fits with its "…" (binary search: a longer start never fits where a shorter fails).
            var lo = 0
            var hi = text.length
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (fits(measurePuffy(measurer, ellipsized(text, mid), style, size, maxText, maxLines, TextOverflow.Clip, textAlign))) lo = mid else hi = mid - 1
            }
            shown = ellipsized(text, lo)
            layout = measurePuffy(measurer, shown, style, size, maxText, maxLines, TextOverflow.Clip, textAlign)
            overflowed = true
            break
        }
        size -= 1f
    }
    val plain = AnnotatedString(shown.text)
    val outline = measurePuffy(measurer, plain, style.copy(color = strokeColor), size, maxText, maxLines, TextOverflow.Clip, textAlign)
    val shadowLayout = measurePuffy(measurer, plain, style.copy(color = shadowColor), size, maxText, maxLines, TextOverflow.Clip, textAlign)
    val inkW = max(inkRight(layout), layout.size.width.toFloat())
    val w = ceil(inkW + room).toInt()
    val h = ceil(layout.size.height + half * 2 + sy).toInt()
    return PuffyFit(layout, outline, shadowLayout, half, sx, sy, size, overflowed, w, h, Rect(0f, 0f, inkW + room, layout.size.height + half * 2 + sy))
}

/**
 * The first [n] characters of [text] (whole characters, trailing spaces dropped) and "…", spans kept; the "…" wears the
 * span of the character before it (the wordmark's "Basket" ramune).
 */
internal fun ellipsized(text: AnnotatedString, n: Int): AnnotatedString {
    var end = n.coerceIn(0, text.length)
    if (end in 1 until text.length && text[end - 1].isHighSurrogate()) end--
    while (end > 0 && text[end - 1].isWhitespace()) end--
    val last = text.spanStyles.lastOrNull { end > 0 && it.start <= end - 1 && end - 1 < it.end }?.item
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text.subSequence(0, end))
        if (last != null) pushStyle(last)
        append("\u2026")
        if (last != null) pop()
    }
}

private fun measurePuffy(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    sizeSp: Float,
    maxWidth: Int,
    maxLines: Int,
    overflow: TextOverflow,
    align: TextAlign,
): TextLayoutResult {
    val lineScale = if (style.lineHeight.isSpecified && style.fontSize.isSpecified) style.lineHeight.value / style.fontSize.value else 1.25f
    return measurer.measure(
        text,
        style.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * lineScale).sp, textAlign = align),
        overflow = overflow,
        softWrap = maxLines > 1,
        maxLines = maxLines,
        constraints = Constraints(maxWidth = maxWidth),
    )
}

/** The rightmost ink of [layout]: each line's right, or its last glyph's bounding box when that overhangs it. */
private fun inkRight(layout: TextLayoutResult): Float {
    var right = 0f
    for (line in 0 until layout.lineCount) {
        right = max(right, layout.getLineRight(line))
        val end = layout.getLineEnd(line, visibleEnd = true) - 1
        if (end >= 0 && end < layout.layoutInput.text.length) right = max(right, layout.getBoundingBox(end).right)
    }
    return right
}

/**
 * The stroke pass's pinholes and cracks (see the file header). [find] rasterises the silhouette (stroke and fill) once
 * and returns what to fill as an opaque mask the size of the box (null when there is nothing, the usual case), drawn
 * tinted in the stroke colour between the stroke and fill passes.
 */
internal object PuffyPinholes {
    /** A pixel counts as covered from this alpha up (the stroke's antialiased rim is half covered). */
    private const val COVERED = 128

    /** The open ground reaches this far (px) into the gaps it borders: shallow dips between halos stay as drawn. */
    const val SHORE = 3

    fun find(fit: PuffyFit, density: androidx.compose.ui.unit.Density): ImageBitmap? {
        val w = fit.width
        val h = fit.height
        if (w <= 2 || h <= 2 || fit.half <= 0f) return null
        val mask = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(mask), Size(w.toFloat(), h.toFloat())) {
            translate(fit.half, fit.half) {
                drawText(fit.outline, Color.Black, drawStyle = Stroke(width = fit.half * 2, join = StrokeJoin.Round))
                drawText(fit.fill, Color.Black, drawStyle = Fill)
            }
        }
        val argb = IntArray(w * h)
        mask.readPixels(argb)
        val alpha = IntArray(w * h) { (argb[it] ushr 24) and 0xFF }
        val fill = holes(alpha, w, h, pen = ceil(fit.half * 2).toInt()) ?: return null
        val out = IntArray(w * h)
        for (i in fill.indices) if (fill[i]) out[i] = 0xFF000000.toInt()
        val patch = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
        patch.asAndroidBitmap().setPixels(out, 0, w, 0, 0, w, h)
        return patch
    }

    /**
     * Which pixels of a [w] × [h] coverage [alpha] map to fill, or null when none: the uncovered pixels (alpha below
     * [COVERED]) the open ground cannot reach, in regions no bigger than the pen ([pen] × [pen] in area, 2 pens across),
     * each with its partly covered rim. The ground floods in from the edges of the box but never through a neck 2 px wide
     * or less (it keeps 1 px clear of every covered pixel), then reaches [shore] px further into what it borders. So an
     * enclosed pinhole and the far end of a hairline crack between two halos are filled; open ground, the gaps between
     * words, shallow dips along the outline and counters bigger than the pen are not. Pure (unit-tested).
     */
    fun holes(alpha: IntArray, w: Int, h: Int, pen: Int, shore: Int = SHORE): BooleanArray? {
        val n = w * h
        val covered = BooleanArray(n) { alpha[it] >= COVERED }
        // A pixel the ground cannot pass: covered, or next to a covered pixel (8 neighbours).
        val blocked = BooleanArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            if (!covered[y * w + x]) continue
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) blocked[ny * w + nx] = true
            }
        }
        // The ground: flooded (4 neighbours) from every unblocked pixel on the box's edge.
        val ground = BooleanArray(n)
        val queue = IntArray(n)
        var head = 0
        var tail = 0
        fun seed(i: Int) {
            if (!blocked[i] && !ground[i]) {
                ground[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) seed(i - 1)
            if (x < w - 1) seed(i + 1)
            if (y > 0) seed(i - w)
            if (y < h - 1) seed(i + w)
        }
        // Its shore: every pixel not fully covered within [shore] px (8 neighbours) of it (the outline's antialiased rim
        // included), and the not fully covered edge of the box.
        val partial = BooleanArray(n) { alpha[it] < 255 }
        val open = ground.copyOf()
        for (step in 0 until shore) {
            val grown = open.copyOf()
            for (y in 0 until h) for (x in 0 until w) {
                if (!open[y * w + x]) continue
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && partial[ny * w + nx]) grown[ny * w + nx] = true
                }
            }
            grown.copyInto(open)
        }
        for (x in 0 until w) for (y in intArrayOf(0, h - 1)) if (partial[y * w + x]) open[y * w + x] = true
        for (y in 0 until h) for (x in intArrayOf(0, w - 1)) if (partial[y * w + x]) open[y * w + x] = true
        // What is left not fully covered and unreached, region by region (4 neighbours). Its partly covered pixels go
        // too: another renderer may cover them a little less (the stroke's own threshold is half).
        val seen = BooleanArray(n)
        val result = BooleanArray(n)
        var any = false
        for (start in 0 until n) {
            if (seen[start] || !partial[start] || open[start]) continue
            head = 0
            tail = 0
            queue[tail++] = start
            seen[start] = true
            var minX = w
            var maxX = -1
            var minY = h
            var maxY = -1
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
                fun visit(j: Int) {
                    if (!seen[j] && partial[j] && !open[j]) {
                        seen[j] = true
                        queue[tail++] = j
                    }
                }
                if (x > 0) visit(i - 1)
                if (x < w - 1) visit(i + 1)
                if (y > 0) visit(i - w)
                if (y < h - 1) visit(i + w)
            }
            if (tail > pen * pen || maxX - minX + 1 > pen * 2 || maxY - minY + 1 > pen * 2) continue
            any = true
            for (k in 0 until tail) {
                val i = queue[k]
                result[i] = true
                val x = i % w
                val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && alpha[ny * w + nx] < 255 && !open[ny * w + nx]) result[ny * w + nx] = true
                }
            }
        }
        return if (any) result else null
    }
}

/** Where a drawn puffy text's ink starts inside its box (the stroke's half-width). */
internal fun PuffyFit.textOrigin(): Offset = Offset(half, half)

/**
 * The wordmark (§2.7): "Kirakira" strawberry mid, "Basket" ramune mid, a 7 dp white stroke, hs2, twinkling stars beside
 * it. It shrinks to fit one line (down to 80 % of the compact size); when a large font leaves it no room even then, it
 * stacks, "Basket" under "Kirakira", in a box as wide as its longer word, so the stars stay beside it: the app's name
 * is never ellipsized.
 */
@Composable
fun Wordmark(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val split = text.indexOf(' ').let { if (it < 0) text.length else it }
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(androidx.compose.ui.text.SpanStyle(color = Families.strawberry.mid))
        append(text.substring(0, split))
        pop()
        pushStyle(androidx.compose.ui.text.SpanStyle(color = Families.ramune.mid))
        append(text.substring(split))
        pop()
    }
    val t = PicnicTheme.type
    val style = if (compact) t.wordmarkCompact else t.wordmarkLarge
    val minSize = t.wordmarkCompact.fontSize * 0.8f
    val stroke = 7.dp
    val shadow = PicnicTheme.shadows.hs2
    val stars = 34.dp
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val room = with(density) { (stroke + shadow.x).toPx() }
        val textRoom = constraints.maxWidth - with(density) { stars.roundToPx() }
        // One line at the smallest size it may shrink to, or the widest word at full size when it has to stack.
        val stackWidth: Int? = remember(annotated, style, density, textRoom) {
            fun width(s: String, size: TextUnit) = measurer.measure(s, style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width
            when {
                constraints.maxWidth == Constraints.Infinity -> null
                width(annotated.text, minSize) + room <= textRoom -> null
                else -> kotlin.math.ceil(annotated.text.split(' ').maxOf { width(it, style.fontSize) } + room).toInt().coerceAtMost(textRoom)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PuffyText(
                annotated,
                style = style,
                stroke = stroke,
                strokeColor = Color.White,
                shadow = shadow,
                heading = true,
                minFontSize = minSize,
                maxLines = if (stackWidth == null) 1 else 2,
                modifier = if (stackWidth == null) Modifier.weight(1f, fill = false) else Modifier.width(with(density) { stackWidth.toDp() }),
            )
            Box(Modifier.size(width = stars, height = 44.dp)) {
            TwinkleSparkle(Modifier.size(if (compact) 21.dp else 28.dp).align(Alignment.TopStart), color = Families.lemon.mid, trace = Families.lemon.trace)
            DecorSticker("star", Modifier.align(Alignment.BottomEnd), size = if (compact) 15.dp else 20.dp)
            }
        }
    }
}

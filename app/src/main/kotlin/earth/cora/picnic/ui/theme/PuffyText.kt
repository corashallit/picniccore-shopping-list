package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import earth.cora.picnic.ui.a11y.textOverflowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
 * region the open ground cannot reach through a crack of about 1 dp ([PuffyPinholes]) is filled in the stroke colour
 * (and, offset, in the shadow colour): inside a glyph's counter only when it is no bigger than the pen, between glyphs
 * (a hole only their halos close) always. Larger counters and the gaps between words stay open.
 * The rule is in dp, so a title closes the same cracks on every phone. The patch is worked out once per title, size and
 * density in the process ([PuffyPatchCache]); in the app the pass over the pixels runs off the main thread
 * ([LocalPinholeWork]), so a title that appears mid-animation (NEXT, the celebration's) never costs a frame.
 */

/** The fitted font size (sp) a PuffyText chose (tests: AppBarTest, PuffyTextBoundsTest). */
val PuffyFontSize: SemanticsPropertyKey<Float> = SemanticsPropertyKey("PuffyFontSize")
internal var SemanticsPropertyReceiver.puffyFontSize: Float by PuffyFontSize

/** The drawn ink bounds (stroke and shadow included), in the node's own px (PuffyTextBoundsTest). */
val PuffyInkBounds: SemanticsPropertyKey<Rect> = SemanticsPropertyKey("PuffyInkBounds")
internal var SemanticsPropertyReceiver.puffyInkBounds: Rect by PuffyInkBounds

/**
 * Where a PuffyText works out its pinhole patch (§2.7, item 338). [Inline]: in composition, so the first frame already
 * carries it (previews and tests, where a golden must not depend on a worker's timing). [Background] (the app's root
 * provides it, MainActivity): the silhouette is rasterised on the main thread (two text draws into a mask; the layouts'
 * paints are never shared with another thread) and the pass over its pixels runs on a worker; the title draws without
 * the patch for the frame or two that takes (it only closes specks). Either way a patch is worked out once per title,
 * size and density in the process ([PuffyPatchCache]).
 */
@Immutable
class PinholeWork private constructor(internal val context: CoroutineContext?) {
    companion object {
        val Inline: PinholeWork = PinholeWork(null)
        val Background: PinholeWork = PinholeWork(Dispatchers.Default)
    }
}

/** Where PuffyText works out its pinhole patch ([PinholeWork.Inline] unless the app's root says otherwise). */
val LocalPinholeWork = staticCompositionLocalOf { PinholeWork.Inline }

/**
 * Puffy lettering. [stroke] is the full stroke width (titles 6 dp, the wordmark 7 dp, NEXT 6 dp, the celebration 9 dp).
 * When [minFontSize] is below the style's size, a text that does not fit shrinks in 1 sp steps down to it, then
 * ellipsizes (and sets the TextOverflowed semantics key). With [maxLines] above 1 it wraps at the style's size; with
 * [wrapLast] it first shrinks to stay on one line and wraps only at [minFontSize] (the candy pill's label: its size
 * only ever shrinks as its slot narrows). [heading] marks screen titles for TalkBack.
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
    wrapLast: Boolean = false,
) {
    PuffyText(
        AnnotatedString(text), style.copy(color = fill), stroke, modifier, strokeColor, shadow, shadowColor,
        minFontSize, maxLines, textAlign, heading, wrapLast,
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
    wrapLast: Boolean = false,
) {
    // No layout cache: TextMeasurer's cache ignores colour, and each pass is laid out in its own colour.
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val work = LocalPinholeWork.current
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val fit = remember(text, style, stroke, shadow, minFontSize, maxLines, textAlign, maxWidth, density, strokeColor, shadowColor, wrapLast) {
            with(density) {
                fitPuffy(
                    measurer, text, style, stroke.toPx(),
                    shadow?.x?.toPx()?.coerceAtLeast(0f) ?: 0f, shadow?.y?.toPx()?.coerceAtLeast(0f) ?: 0f,
                    minFontSize, maxLines, textAlign, maxWidth, strokeColor, shadowColor, wrapLast,
                )
            }
        }
        val widthDp = with(density) { fit.width.toDp() }
        val heightDp = with(density) { fit.height.toDp() }
        // The patch depends on the shapes only: colours are left out of its key.
        val key = PuffyPatchKey(
            text, style.copy(color = Color.Unspecified), stroke, shadow, minFontSize, maxLines, textAlign, maxWidth,
            density.density, density.fontScale, direction, wrapLast,
        )
        val pinholes = rememberPinholes(fit, key, density, work)
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
                    val patch = pinholes?.image
                    val strokeStyle = Stroke(width = fit.half * 2, join = StrokeJoin.Round)
                    if (fit.sx > 0f || fit.sy > 0f) {
                        translate(fit.half + fit.sx, fit.half + fit.sy) {
                            drawText(fit.shadow, shadowColor, drawStyle = strokeStyle)
                            drawText(fit.shadow, shadowColor, drawStyle = Fill)
                        }
                        if (patch != null) {
                            drawImage(patch, pinholes.topLeft + Offset(fit.sx, fit.sy), colorFilter = ColorFilter.tint(shadowColor, BlendMode.SrcIn))
                        }
                    }
                    translate(fit.half, fit.half) { drawText(fit.outline, strokeColor, drawStyle = strokeStyle) }
                    if (patch != null) drawImage(patch, pinholes.topLeft, colorFilter = ColorFilter.tint(strokeColor, BlendMode.SrcIn))
                    translate(fit.half, fit.half) { drawText(fit.fill, drawStyle = Fill) }
                },
        )
    }
}

/**
 * The pinhole patch for [fit]: from [PuffyPatchCache] when this title was seen before; else worked out in composition
 * ([PinholeWork.Inline]) or, with [PinholeWork.Background], rasterised here on the main thread and scanned on a worker
 * (null until it is ready, so the title draws without it for a frame or two).
 */
@Composable
private fun rememberPinholes(fit: PuffyFit, key: PuffyPatchKey, density: Density, work: PinholeWork): PinholePatch? {
    val context = work.context
    val state = remember(fit, key) {
        mutableStateOf(PuffyPatchCache[key] ?: if (context == null) PuffyPatchCache.put(key, PuffyPinholes.find(fit, density)) else null)
    }
    if (state.value == null && context != null) {
        LaunchedEffect(fit, key) {
            // On the main thread (this effect's dispatcher): the draw into the mask uses the layouts' own paints.
            val silhouette = PuffyPinholes.silhouette(fit, density)
            val patch = if (silhouette == null) PinholePatch.None else withContext(context) { PuffyPinholes.patch(silhouette) }
            state.value = PuffyPatchCache.put(key, patch)
        }
    }
    return state.value
}

/** What a pinhole patch depends on: the shapes of a fitted title (never its colours). */
internal data class PuffyPatchKey(
    val text: AnnotatedString,
    val style: TextStyle,
    val stroke: Dp,
    val shadow: DpOffset?,
    val minFontSize: TextUnit,
    val maxLines: Int,
    val textAlign: TextAlign,
    val maxWidth: Int,
    val density: Float,
    val fontScale: Float,
    val direction: LayoutDirection,
    val wrapLast: Boolean,
)

/** The pixels a title's pinholes fill: an opaque mask [image] cropped to them, at [topLeft] in the title's box. */
@Immutable
internal class PinholePatch(val image: ImageBitmap?, val topLeft: Offset) {
    companion object {
        /** Nothing to fill (the usual case). */
        val None = PinholePatch(null, Offset.Zero)
    }
}

/**
 * Pinhole patches already worked out in this process, the most recently used [SIZE] kept: the Next card's NEXT, the
 * celebration's title and the app bar's titles are scanned once, not each time they appear. A patch is a small cropped
 * mask (most titles have none), so the cache stays small.
 */
internal object PuffyPatchCache {
    private const val SIZE = 64
    private val map = object : LinkedHashMap<PuffyPatchKey, PinholePatch>(SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PuffyPatchKey, PinholePatch>?): Boolean = size > SIZE
    }

    @Synchronized
    operator fun get(key: PuffyPatchKey): PinholePatch? = map[key]

    @Synchronized
    fun put(key: PuffyPatchKey, patch: PinholePatch): PinholePatch {
        map[key] = patch
        return patch
    }

    /** Tests: forget everything (a probe that counts passes starts from nothing). */
    @Synchronized
    fun clear() = map.clear()
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
 * ellipsized title never trails a gap before what follows it (the sun sticker). With [maxLines] above 1 the text wraps
 * at each size it tries; with [wrapLast] it tries one line at every size first and wraps only at [minFontSize], so its
 * size never grows back as its slot narrows.
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
    wrapLast: Boolean = false,
): PuffyFit {
    val half = strokePx / 2f
    val room = half * 2 + sx
    val maxText = if (maxWidth == Constraints.Infinity) Constraints.Infinity else max(0, (maxWidth - room).toInt())
    val from = style.fontSize.value
    val to = if (minFontSize.isSpecified) minFontSize.value.coerceAtMost(from) else from
    fun fits(l: TextLayoutResult) = !l.hasVisualOverflow && inkRight(l) <= maxText + 0.5f
    fun measure(t: AnnotatedString, size: Float, lines: Int) = measurePuffy(measurer, t, style, size, maxText, lines, TextOverflow.Clip, textAlign)
    // The largest size from [start] down to [to] (1 sp steps, then [to] itself) at which [lines] lines hold the text.
    fun shrink(start: Float, lines: Int): Triple<Float, TextLayoutResult, Int>? {
        var size = start
        while (true) {
            val l = measure(text, size, lines)
            if (fits(l)) return Triple(size, l, lines)
            if (size <= to + 0.001f) return null
            size = max(size - 1f, to)
        }
    }
    val found = if (wrapLast && maxLines > 1) shrink(from, 1) ?: shrink(to, maxLines) else shrink(from, maxLines)
    val size: Float
    val layout: TextLayoutResult
    val overflowed: Boolean
    // The passes are laid out exactly as the fill was (one line unwrapped, or wrapped in the box), so they align.
    val lines: Int
    var shown = text
    if (found != null) {
        size = found.first
        layout = found.second
        lines = found.third
        overflowed = false
    } else {
        size = to
        // The longest start that fits with its "…" (binary search: a longer start never fits where a shorter fails).
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (fits(measure(ellipsized(text, mid), size, maxLines))) lo = mid else hi = mid - 1
        }
        shown = ellipsized(text, lo)
        layout = measure(shown, size, maxLines)
        lines = maxLines
        overflowed = true
    }
    val plain = AnnotatedString(shown.text)
    val outline = measurePuffy(measurer, plain, style.copy(color = strokeColor), size, maxText, lines, TextOverflow.Clip, textAlign)
    val shadowLayout = measurePuffy(measurer, plain, style.copy(color = shadowColor), size, maxText, lines, TextOverflow.Clip, textAlign)
    val inkW = max(inkRight(layout), layout.size.width.toFloat())
    val w = ceil(inkW + room).toInt()
    val h = ceil(layout.size.height + half * 2 + sy).toInt()
    return PuffyFit(layout, outline, shadowLayout, half, sx, sy, size, overflowed, w, h, Rect(0f, 0f, inkW + room, layout.size.height + half * 2 + sy))
}

/**
 * The narrowest box (px, a PuffyText's maxWidth) that holds [text] on one line at [sizeSp] with a [strokePx] stroke and
 * an [sx] shadow: exactly where [fitPuffy] stops fitting it on one line. A caller that lays out around a PuffyText that
 * may wrap (the candy pill) knows from it whether it will.
 */
internal fun puffyLineWidth(measurer: TextMeasurer, text: AnnotatedString, style: TextStyle, sizeSp: Float, strokePx: Float, sx: Float): Int {
    val l = measurePuffy(measurer, text, style, sizeSp, Constraints.Infinity, 1, TextOverflow.Clip, TextAlign.Start)
    // fitPuffy keeps one line while the floored text width holds the layout's width and its ink to half a px.
    val needed = max(l.size.width, ceil(inkRight(l) - 0.5f).toInt())
    return ceil(needed + strokePx + sx).toInt()
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
 * A title's rasterised silhouette (stroke and fill): coverage [alpha] per px, the rule's sizes in px, and [inGlyph]: the
 * px inside a glyph's own counters (enclosed by the fill alone, the inside of an "o"), told apart from the spaces between
 * glyphs that only the stroke's halos close.
 */
internal class Silhouette(val alpha: IntArray, val w: Int, val h: Int, val pen: Int, val neck: Int, val shore: Int, val inGlyph: BooleanArray?)

/**
 * The stroke pass's pinholes and cracks (see the file header). [silhouette] rasterises the silhouette (stroke and fill)
 * once, on the thread that draws the title (it uses the layouts' paints); [patch] is the pure pass over its pixels,
 * safe on any thread, and returns what to fill as an opaque mask cropped to it ([PinholePatch.None] when there is
 * nothing, the usual case), drawn tinted in the stroke colour between the stroke and fill passes. The rule's sizes
 * are in dp (§2.7): the ground never passes a crack [CRACK_DP] wide or narrower, at any density, and reaches one
 * antialiased pixel past its clearance into what it borders, so a notch between two halos ends rounded, at the blade's
 * width, instead of in a hairline.
 */
internal object PuffyPinholes {
    /** A pixel counts as covered from this alpha up (the stroke's antialiased rim is half covered). */
    private const val COVERED = 128

    /** A crack this wide (dp) or narrower always closes: a hairline between two halos, never a gap between words. */
    const val CRACK_DP = 1f

    /** The default shore (px) for [holes] at xxhdpi. */
    const val SHORE = 3

    /** A hole between glyphs (never a counter) is filled up to this many pens across. */
    const val BETWEEN_PENS = 4

    /**
     * The neck rule in px at [density]: the ground keeps this far from every covered pixel, so it never passes a crack
     * twice as wide. Half of [CRACK_DP], rounded up to whole px, so a 1 dp crack closes at every density (2 px each side
     * from 2.5 to 4 px per dp: what closes is 1 to 1.6 dp wide, never more).
     */
    fun neckPx(density: Float): Int = max(1, ceil(CRACK_DP * density / 2f - 0.001f).toInt())

    /** The shore in px at [density]: the ground's clearance and one antialiased pixel. */
    fun shorePx(density: Float): Int = neckPx(density) + 1

    /** Tests: how many scans have run, whether the last one ran on the main thread, and how many px it filled. */
    @Volatile internal var scans = 0

    @Volatile internal var lastScanOnMain = false

    @Volatile internal var lastScanFilled = 0

    /** Rasterises and scans in one go ([PinholeWork.Inline]). */
    fun find(fit: PuffyFit, density: Density): PinholePatch = silhouette(fit, density)?.let(::patch) ?: PinholePatch.None

    /** The silhouette of [fit] (null when it has no stroke): drawn on the calling thread, which must be the one that draws [fit]. */
    fun silhouette(fit: PuffyFit, density: Density): Silhouette? {
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
        val alpha = IntArray(w * h)
        mask.readPixels(alpha)
        for (i in alpha.indices) alpha[i] = (alpha[i] ushr 24) and 0xFF
        // The fill alone: what it encloses by itself is a glyph's counter.
        val fillMask = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(fillMask), Size(w.toFloat(), h.toFloat())) {
            translate(fit.half, fit.half) { drawText(fit.fill, Color.Black, drawStyle = Fill) }
        }
        val fill = IntArray(w * h)
        fillMask.readPixels(fill)
        val inGlyph = enclosed(BooleanArray(w * h) { ((fill[it] ushr 24) and 0xFF) < COVERED }, w, h)
        return Silhouette(alpha, w, h, pen = ceil(fit.half * 2).toInt(), neck = neckPx(density.density), shore = shorePx(density.density), inGlyph = inGlyph)
    }

    /** The [open] px of a [w] × [h] grid the box's edge cannot reach through other open px (4 neighbours). */
    fun enclosed(open: BooleanArray, w: Int, h: Int): BooleanArray {
        val reached = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun seed(i: Int) {
            if (open[i] && !reached[i]) {
                reached[i] = true
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
        return BooleanArray(w * h) { open[it] && !reached[it] }
    }

    /** The patch for [s]: pure, any thread. */
    fun patch(s: Silhouette): PinholePatch {
        scans++
        lastScanOnMain = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
        val fill = holes(s.alpha, s.w, s.h, s.pen, s.neck, s.shore, s.inGlyph)
        lastScanFilled = fill?.count { it } ?: 0
        if (fill == null) return PinholePatch.None
        var minX = s.w
        var minY = s.h
        var maxX = -1
        var maxY = -1
        for (i in fill.indices) if (fill[i]) {
            val x = i % s.w
            val y = i / s.w
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val out = IntArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) if (fill[(minY + y) * s.w + minX + x]) out[y * bw + x] = 0xFF000000.toInt()
        val image = ImageBitmap(bw, bh, ImageBitmapConfig.Argb8888)
        image.asAndroidBitmap().setPixels(out, 0, bw, 0, 0, bw, bh)
        return PinholePatch(image, Offset(minX.toFloat(), minY.toFloat()))
    }

    /**
     * Which pixels of a [w] × [h] coverage [alpha] map to fill, or null when none: the uncovered pixels (alpha below
     * [COVERED]) the open ground cannot reach, each region with its partly covered rim. A region inside a glyph's own
     * counter ([inGlyph]; every region when it is null) is filled only when it is no bigger than the pen ([pen] × [pen]
     * in area, 2 pens across): a larger counter stays open. A region between glyphs, closed only by their halos, is
     * always a stroke artefact and is filled up to [BETWEEN_PENS] pens across. The ground floods in from the edges of the box but never through a neck
     * 2 × [neck] px wide or less (it keeps [neck] px clear of every covered pixel), then reaches [shore] px further into
     * what it borders (from the box's edge too, which borders the air outside). So an enclosed pinhole and the far end of a hairline crack between two halos are filled; open
     * ground, the gaps between words, shallow dips along the outline and counters bigger than the pen are not. Every
     * pass is linear in the pixels (the clearance is a running window along rows, then columns). Pure (unit-tested).
     */
    fun holes(alpha: IntArray, w: Int, h: Int, pen: Int, neck: Int = 1, shore: Int = SHORE, inGlyph: BooleanArray? = null): BooleanArray? {
        val n = w * h
        if (n == 0) return null
        // A pixel the ground cannot pass: within [neck] px (both ways) of a covered pixel. Rows first...
        val nearRow = BooleanArray(n)
        for (y in 0 until h) {
            val row = y * w
            var count = 0
            for (x in 0..min(neck, w - 1)) if (alpha[row + x] >= COVERED) count++
            for (x in 0 until w) {
                nearRow[row + x] = count > 0
                val out = x - neck
                if (out >= 0 && alpha[row + out] >= COVERED) count--
                val next = x + neck + 1
                if (next < w && alpha[row + next] >= COVERED) count++
            }
        }
        // ...then columns, a row at a time.
        val blocked = BooleanArray(n)
        val counts = IntArray(w)
        for (y in 0..min(neck, h - 1)) for (x in 0 until w) if (nearRow[y * w + x]) counts[x]++
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) blocked[row + x] = counts[x] > 0
            val out = y - neck
            if (out >= 0) for (x in 0 until w) if (nearRow[out * w + x]) counts[x]--
            val next = y + neck + 1
            if (next < h) for (x in 0 until w) if (nearRow[next * w + x]) counts[x]++
        }
        // The ground: flooded (4 neighbours) from every unblocked pixel on the box's edge.
        val open = BooleanArray(n)
        val queue = IntArray(n)
        var head = 0
        var tail = 0
        fun seed(i: Int) {
            if (!blocked[i] && !open[i]) {
                open[i] = true
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
        // The box's edge borders the open air outside it: its not fully covered pixels are ground too, even where a
        // halo comes nearer to the edge than the neck.
        fun edge(i: Int) {
            if (!open[i] && alpha[i] < 255) {
                open[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until w) { edge(x); edge((h - 1) * w + x) }
        for (y in 0 until h) { edge(y * w); edge(y * w + w - 1) }
        // Its shore: every pixel not fully covered within [shore] px (8 neighbours) of it (the outline's antialiased rim
        // included), grown a ring at a time from the ground.
        var from = 0
        var until = tail
        for (step in 0 until shore) {
            if (from == until) break
            for (k in from until until) {
                val i = queue[k]
                val x = i % w
                val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h) {
                        val j = ny * w + nx
                        if (!open[j] && alpha[j] < 255) {
                            open[j] = true
                            queue[tail++] = j
                        }
                    }
                }
            }
            from = until
            until = tail
        }
        // What is left not fully covered and unreached, region by region (4 neighbours). Its partly covered pixels go
        // too: another renderer may cover them a little less (the stroke's own threshold is half).
        val seen = BooleanArray(n)
        val result = BooleanArray(n)
        var any = false
        for (start in 0 until n) {
            if (seen[start] || alpha[start] >= 255 || open[start]) continue
            head = 0
            tail = 0
            queue[tail++] = start
            seen[start] = true
            var minX = w
            var maxX = -1
            var minY = h
            var maxY = -1
            var counter = inGlyph == null
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                if (!counter && inGlyph!![i]) counter = true
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                fun visit(j: Int) {
                    if (!seen[j] && alpha[j] < 255 && !open[j]) {
                        seen[j] = true
                        queue[tail++] = j
                    }
                }
                if (x > 0) visit(i - 1)
                if (x < w - 1) visit(i + 1)
                if (y > 0) visit(i - w)
                if (y < h - 1) visit(i + w)
            }
            val across = max(maxX - minX + 1, maxY - minY + 1)
            if (counter && (tail > pen * pen || across > pen * 2)) continue
            if (!counter && across > pen * BETWEEN_PENS) continue
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
 * stacks, "Basket" under "Kirakira", at the size the one line had shrunk to, in a box as wide as its longer word, so
 * the stars stay beside it. Its size only ever shrinks as its box narrows (never a bigger stacked wordmark in a
 * narrower box), and the app's name is never ellipsized: a stack too wide even then shrinks further.
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
        val strokePx = with(density) { stroke.toPx() }
        val sx = with(density) { shadow.x.toPx() }
        val textRoom = constraints.maxWidth - with(density) { stars.roundToPx() }
        // Null while one line holds it at the smallest size it may shrink to; else the stack's width: its longer word
        // at that same size (the size the one line had reached), so stacking never makes it bigger.
        val stackWidth: Int? = remember(annotated, style, density, textRoom) {
            fun width(s: AnnotatedString) = puffyLineWidth(measurer, s, style, minSize.value, strokePx, sx)
            when {
                constraints.maxWidth == Constraints.Infinity -> null
                width(annotated) <= textRoom -> null
                else -> annotated.text.split(' ').filter { it.isNotEmpty() }.maxOf { width(AnnotatedString(it)) }.coerceAtMost(textRoom)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PuffyText(
                annotated,
                style = if (stackWidth == null) style else style.copy(fontSize = minSize, lineHeight = style.lineHeight * (minSize.value / style.fontSize.value)),
                stroke = stroke,
                strokeColor = Color.White,
                shadow = shadow,
                heading = true,
                minFontSize = if (stackWidth == null) minSize else minSize * STACK_FLOOR,
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

/** A stacked wordmark too wide for its box even at the one-line minimum shrinks on, down to this share of it. */
private const val STACK_FLOOR = 0.75f

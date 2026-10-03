package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    /** Paints the fill with this brush instead of [fill] (egg 7's holo title, read at draw time). */
    fillBrush: (() -> Brush)? = null,
) {
    PuffyText(
        AnnotatedString(text), style.copy(color = fill), stroke, modifier, strokeColor, shadow, shadowColor,
        minFontSize, maxLines, textAlign, heading, fillBrush,
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
    /** Paints the fill with this brush instead of the style's colours (egg 7's holo title, read at draw time). */
    fillBrush: (() -> Brush)? = null,
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
                    translate(fit.half, fit.half) {
                        if (fit.sx > 0f || fit.sy > 0f) {
                            translate(fit.sx, fit.sy) {
                                drawText(fit.shadow, shadowColor, drawStyle = strokeStyle)
                                drawText(fit.shadow, shadowColor, drawStyle = Fill)
                            }
                        }
                        drawText(fit.outline, strokeColor, drawStyle = strokeStyle)
                        val brush = fillBrush?.invoke()
                        if (brush != null) drawText(fit.fill, brush, drawStyle = Fill) else drawText(fit.fill, drawStyle = Fill)
                    }
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
 * down to [minFontSize], else ellipsized at [minFontSize].
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
    while (true) {
        layout = measurePuffy(measurer, text, style, size, maxText, maxLines, TextOverflow.Clip, textAlign)
        if (!layout.hasVisualOverflow && inkRight(layout) <= maxText + 0.5f) {
            overflowed = false
            break
        }
        if (size - 1f < to - 0.001f) {
            layout = measurePuffy(measurer, text, style, to, maxText, maxLines, TextOverflow.Ellipsis, textAlign)
            size = to
            overflowed = true
            break
        }
        size -= 1f
    }
    val plain = AnnotatedString(text.text)
    val over = if (overflowed) TextOverflow.Ellipsis else TextOverflow.Clip
    val outline = measurePuffy(measurer, plain, style.copy(color = strokeColor), size, maxText, maxLines, over, textAlign)
    val shadowLayout = measurePuffy(measurer, plain, style.copy(color = shadowColor), size, maxText, maxLines, over, textAlign)
    val inkW = max(inkRight(layout), layout.size.width.toFloat())
    val w = ceil(inkW + room).toInt()
    val h = ceil(layout.size.height + half * 2 + sy).toInt()
    return PuffyFit(layout, outline, shadowLayout, half, sx, sy, size, overflowed, w, h, Rect(0f, 0f, inkW + room, layout.size.height + half * 2 + sy))
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

/** Where a drawn puffy text's ink starts inside its box (the stroke's half-width). */
internal fun PuffyFit.textOrigin(): Offset = Offset(half, half)

/** The wordmark (§2.7): "Kirakira" strawberry mid, "Basket" ramune mid, a 7 dp white stroke, hs2, twinkling stars beside it. */
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
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PuffyText(
            annotated,
            style = if (compact) t.wordmarkCompact else t.wordmarkLarge,
            stroke = 7.dp,
            strokeColor = Color.White,
            shadow = PicnicTheme.shadows.hs2,
            heading = true,
            minFontSize = t.wordmarkCompact.fontSize * 0.8f,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(Modifier.size(width = 34.dp, height = 44.dp)) {
            TwinkleSparkle(Modifier.size(if (compact) 21.dp else 28.dp).align(Alignment.TopStart), color = Families.lemon.mid, trace = Families.lemon.trace)
            DecorSticker("star", Modifier.align(Alignment.BottomEnd), size = if (compact) 15.dp else 20.dp)
        }
    }
}

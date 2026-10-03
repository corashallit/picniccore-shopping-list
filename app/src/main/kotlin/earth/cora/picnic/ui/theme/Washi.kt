package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.theme.gen.DecorManifest
import kotlin.math.tan

/*
 * Washi tape (docs/DESIGN_STICKER.md §2.12, §4.14): an 18 dp strip at 75–90 %, rotated ±3–5°, zig-zag torn ends (3 dp
 * teeth). Never over text. The add bar wears the season's tape (egg 11) or the default sakura stripes. Owner: WP4.
 */

/** The washi tapes: the four everyday patterns, the six seasonal sheets (§12, egg 11) and the raindrop (egg 16). */
enum class WashiPattern {
    SakuraStripe, PinkGingham, LemonDots, MintStripes,
    CanadaDay, Thanksgiving, Halloween, FirstSnow, Valentines, LunarNewYear,
    Raindrop,
}

/**
 * A washi strip [length] long and [height] tall, rotated [rotation]°, at [alpha]. [flutter] skews it 10° → −6° for 10 %
 * of every 6 s (the add bar's tape; off under reduced motion). Decorative.
 */
@Composable
fun WashiTape(
    pattern: WashiPattern,
    modifier: Modifier = Modifier,
    length: Dp = 90.dp,
    height: Dp = 18.dp,
    rotation: Float = -3f,
    alpha: Float = 0.85f,
    flutter: Boolean = false,
) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = flutter && !reduced)
    val period = PicnicMotion.washiFlutterPeriod
    val phase = (t % period).toFloat() / period
    val skew = if (flutter && !reduced && phase < 0.1f) (if (phase < 0.05f) 10f else -6f) else 0f
    val resources = LocalResources.current
    val motif = motifFor(pattern)?.let { DecorManifest.pictograms[it] ?: DecorManifest.stickers[it] }?.let { VectorArtCache.get(resources, it) }
    Canvas(
        modifier
            .size(length, height)
            .clearAndSetSemantics { }
            .graphicsLayer {
                rotationZ = rotation
                this.alpha = alpha
            },
    ) {
        if (skew == 0f) {
            drawWashi(pattern, motif)
        } else {
            val m = androidx.compose.ui.graphics.Matrix()
            m.values[androidx.compose.ui.graphics.Matrix.SkewX] = skewOf(skew)
            translate(size.width / 2f, size.height / 2f) {
                withTransform({ transform(m) }) {
                    translate(-size.width / 2f, -size.height / 2f) { drawWashi(pattern, motif) }
                }
            }
        }
    }
}

private fun motifFor(p: WashiPattern): String? = when (p) {
    WashiPattern.CanadaDay -> "maple"
    WashiPattern.Thanksgiving -> "pumpkin"
    WashiPattern.Halloween -> "ghost"
    WashiPattern.FirstSnow -> "snowflake"
    WashiPattern.Valentines -> "heart"
    WashiPattern.LunarNewYear -> "redenvelope"
    else -> null
}

/** The torn-ended strip outline: straight long edges, zig-zag ends with 3 dp teeth. */
fun DrawScope.washiOutline(): Path {
    val tooth = 3.dp.toPx()
    val h = size.height
    val w = size.width
    val teeth = (h / tooth).toInt().coerceAtLeast(2)
    val step = h / teeth
    return Path().apply {
        moveTo(tooth, 0f)
        lineTo(w - tooth, 0f)
        for (i in 0 until teeth) {
            lineTo(w - if (i % 2 == 0) 0f else tooth, step * (i + 0.5f))
            lineTo(w - tooth, step * (i + 1))
        }
        lineTo(tooth, h)
        for (i in teeth - 1 downTo 0) {
            lineTo(if (i % 2 == 0) 0f else tooth, step * (i + 0.5f))
            lineTo(tooth, step * i)
        }
        close()
    }
}

/** Draws [pattern] filling this strip, clipped to its torn outline. */
fun DrawScope.drawWashi(pattern: WashiPattern, motif: VectorArt? = null) {
    val outline = washiOutline()
    clipPath(outline) {
        when (pattern) {
            WashiPattern.SakuraStripe -> {
                drawRect(Color.White)
                diagonalStripes(Families.sakura.mid.copy(alpha = 0.78f), 6.dp.toPx(), 12.dp.toPx())
            }
            WashiPattern.PinkGingham -> {
                drawRect(Color.White)
                val cell = 4.dp.toPx()
                drawGingham(Families.sakura.mid.copy(alpha = 0.45f), cell)
            }
            WashiPattern.LemonDots -> {
                drawRect(Color(0xFFFFF8D6))
                dots(Families.lemon.mid, 1.2.dp.toPx(), 10.dp.toPx())
            }
            WashiPattern.MintStripes -> {
                drawRect(Color.White)
                var x = 0f
                val w = 5.dp.toPx()
                while (x < size.width) {
                    drawRect(Families.mint.mid.copy(alpha = 0.7f), Offset(x, 0f), Size(w, size.height))
                    x += w * 2
                }
            }
            WashiPattern.CanadaDay -> {
                drawRect(Families.strawberry.pastel)
                motifs(motif, Families.strawberry.mid, 13.dp.toPx())
            }
            WashiPattern.Thanksgiving -> {
                drawRect(Families.peach.pastel)
                motifs(motif, Families.peach.mid, 13.dp.toPx(), accent = Families.strawberry.mid)
            }
            WashiPattern.Halloween -> {
                drawRect(Families.grape.pastel)
                motifs(motif, Families.grape.trace, 13.dp.toPx(), accent = Families.peach.mid)
            }
            WashiPattern.FirstSnow -> {
                drawRect(Families.ramune.pastel)
                motifs(motif, Color.White, 12.dp.toPx())
            }
            WashiPattern.Valentines -> {
                drawRect(Families.sakura.pastel)
                motifs(motif, Families.strawberry.mid, 12.dp.toPx(), accent = Color.White)
            }
            WashiPattern.LunarNewYear -> {
                drawRect(Families.strawberry.mid)
                motifs(motif, Families.strawberry.trace, 13.dp.toPx(), accent = Families.lemon.mid)
            }
            WashiPattern.Raindrop -> {
                drawRect(Families.ramune.pastel)
                var x = 6.dp.toPx()
                var i = 0
                while (x < size.width) {
                    val y = if (i % 2 == 0) size.height * 0.35f else size.height * 0.65f
                    drawPath(dropPath(Offset(x, y), 3.dp.toPx()), Families.ramune.mid)
                    x += 10.dp.toPx()
                    i++
                }
            }
        }
    }
}

private fun DrawScope.diagonalStripes(color: Color, width: Float, period: Float) {
    val h = size.height
    var x = -h
    while (x < size.width + h) {
        val p = Path().apply {
            moveTo(x, h); lineTo(x + width, h); lineTo(x + width + h, 0f); lineTo(x + h, 0f); close()
        }
        drawPath(p, color)
        x += period
    }
}

private fun DrawScope.dots(color: Color, r: Float, every: Float) {
    var x = every / 2f
    var row = 0
    while (x < size.width) {
        drawCircle(color, r, Offset(x, size.height * 0.33f))
        drawCircle(color, r, Offset(x + every / 2f, size.height * 0.68f))
        x += every
        row++
    }
}

/** Repeats a pictogram motif (WP2's seasonal pictograms) along the strip, every [every] px, tinted [ink]. */
private fun DrawScope.motifs(art: VectorArt?, ink: Color, every: Float, accent: Color? = null) {
    val s = size.height * 0.62f
    var x = every * 0.35f
    var i = 0
    while (x < size.width) {
        val y = (size.height - s) / 2f
        if (art != null) {
            translate(x, y) {
                val inkBrush = SolidColor(if (accent != null && i % 2 == 1) accent else ink)
                val paper = SolidColor(Color.White)
                drawArt(art, Size(s, s), paint = { p, _ -> if (p.name == "paper") paper else inkBrush })
            }
        } else {
            drawCircle(ink, s / 3f, Offset(x + s / 2f, size.height / 2f))
        }
        x += every
        i++
    }
}

private fun dropPath(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y - r * 1.8f)
    cubicTo(c.x + r * 0.9f, c.y - r * 0.4f, c.x + r, c.y + r, c.x, c.y + r)
    cubicTo(c.x - r, c.y + r, c.x - r * 0.9f, c.y - r * 0.4f, c.x, c.y - r * 1.8f)
    close()
}

/** The tan of a skew angle (for callers that shear the tape themselves). */
internal fun skewOf(degrees: Float): Float = tan(Math.toRadians(degrees.toDouble())).toFloat()

/** An outline stroke helper for washi previews. */
internal fun DrawScope.strokeWashiEdge(color: Color) = drawPath(washiOutline(), color, style = Stroke(1.dp.toPx()))

/** Rotates [block] about the strip's centre. */
internal inline fun DrawScope.rotatedWashi(degrees: Float, block: DrawScope.() -> Unit) = rotate(degrees) { block() }

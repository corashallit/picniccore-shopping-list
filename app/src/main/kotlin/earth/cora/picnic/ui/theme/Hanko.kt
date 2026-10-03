package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * The hanko stamp and the checkbox (docs/DESIGN_STICKER.md §4.4, §4.5, rule R2). Owner: WP4 (HankoTest).
 */

/** The two lines a hanko stamp can read (§4.5). Stamp lettering is art (Zen Maru 900, auto-fitted), not copy. */
enum class HankoText(val top: String, val bottom: String) {
    /** Rows (40 dp) and basket lids (34 dp). */
    GotIt("GOT", "IT!"),

    /** The celebration stamp card, the book's spine, the album's perfect-weeks card. */
    PerFect("PER-", "FECT!"),

    /** Egg 9: a birthday item's check-off stamp. */
    HappyBday("HAPPY", "B-DAY!"),

    /** Egg 13: the gashapon jackpot. */
    JackPot("JACK", "POT!"),
}

/** The stamp's seeded rotation (§4.5): −12° ± 4°, from the item id, stable across recompositions and restarts. */
fun hankoAngle(id: String): Float = -12f + (Math.floorMod(id.hashCode(), 9) - 4)

/**
 * The stamp's geometry on its 40-unit grid (§4.5), shared by the drawing and HankoTest: a 2u ring broken by two 3.2u
 * gaps, an inner 0.8u ring broken twice, three 1 dp speckles outside the ring, and the auto-fitted text size.
 */
object HankoGeometry {
    const val UNITS: Float = 40f
    const val RING_R: Float = 17.6f
    const val RING_W: Float = 2f
    const val RING_GAP: Float = 3.2f
    /** Where the outer ring's two gaps are centred (degrees, clockwise from 3 o'clock). */
    val ringGapsDeg: List<Float> = listOf(-38f, 148f)
    const val INNER_R: Float = 15.2f
    const val INNER_W: Float = 0.8f
    const val INNER_GAP: Float = 2.4f
    val innerGapsDeg: List<Float> = listOf(62f, 244f)

    /** The three speckles (centre angle in degrees, radius in units, diameter in dp at 40 dp). */
    val speckles: List<Triple<Float, Float, Float>> = listOf(Triple(-62f, 20.3f, 1f), Triple(-48f, 19.9f, 1f), Triple(128f, 20.1f, 1f))

    /** The farthest any ink reaches from the stamp's centre, in units (ring or speckle edge). */
    val inkRadius: Float = maxOf(RING_R + RING_W / 2f, speckles.maxOf { it.second + it.third / 2f })

    /** The text size in units: min(10.4, 25.5 / (longest line × 0.7)). */
    fun textUnits(text: HankoText): Float = min(10.4f, 25.5f / (maxOf(text.top.length, text.bottom.length) * 0.7f))
}

/**
 * A hanko stamp (§4.5) [size] across, in the variant's hanko colour: a broken double ring, two auto-fitted lines of
 * Zen Maru 900 and three speckles. Unrotated; the caller rotates it. Decorative.
 */
@Composable
fun HankoStamp(text: HankoText, modifier: Modifier = Modifier, size: Dp = 40.dp, color: Color = PicnicTheme.colors.hanko) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val style = PicnicTheme.type.hanko
    Canvas(modifier.size(size).clearAndSetSemantics { }) { drawHanko(text, color, measurer, style) }
}

/**
 * Draws the stamp into this draw area (square). A [halo] colour first lays a backing ring under the outer ring
 * ([BasketHanko.BACKING_W] wide) and a 1.5u halo round the lettering, so the stamp reads on a mid-coloured surface.
 */
fun DrawScope.drawHanko(text: HankoText, color: Color, measurer: TextMeasurer, style: TextStyle, halo: Color? = null) {
    val u = min(size.width, size.height) / HankoGeometry.UNITS
    val c = Offset(size.width / 2f, size.height / 2f)
    if (halo != null) drawCircle(halo, HankoGeometry.RING_R * u, c, style = Stroke(BasketHanko.BACKING_W * u))
    val px = HankoGeometry.textUnits(text) * u
    val sp = (px / (density * fontScale)).sp
    val textStyle = style.copy(color = color, fontSize = sp, lineHeight = sp * 0.98f, letterSpacing = 0.02.em, textAlign = TextAlign.Center)
    val layout = measurer.measure("${text.top}\n${text.bottom}", textStyle)
    val textAt = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f)
    if (halo != null) {
        val haloStyle = textStyle.copy(color = halo, drawStyle = Stroke(3f * u, join = StrokeJoin.Round))
        // Its own layout (skipCache): a cached paragraph shared with the fill would keep the stroke paint.
        drawText(measurer.measure("${text.top}\n${text.bottom}", haloStyle, skipCache = true), topLeft = textAt)
    }
    drawBrokenRing(color, c, HankoGeometry.RING_R * u, HankoGeometry.RING_W * u, HankoGeometry.RING_GAP * u, HankoGeometry.ringGapsDeg)
    drawBrokenRing(color, c, HankoGeometry.INNER_R * u, HankoGeometry.INNER_W * u, HankoGeometry.INNER_GAP * u, HankoGeometry.innerGapsDeg)
    for ((deg, r, d) in HankoGeometry.speckles) {
        val a = Math.toRadians(deg.toDouble())
        drawCircle(color, d.dp.toPx() * (u * HankoGeometry.UNITS / 40.dp.toPx()) / 2f, Offset(c.x + (r * u * cos(a)).toFloat(), c.y + (r * u * sin(a)).toFloat()))
    }
    // Text: units → px, then px → sp at this density with the font scale removed (the stamp never scales with text).
    drawText(layout, topLeft = textAt)
}

/**
 * The GOT IT stamp pressed onto a basket's closed lid (§5.3). Baskets keep their colours on every ground (§2.4), so the
 * lid stamp keeps the Day vermilion in every variant: the Glow hanko (`#FF8A7A`) falls to 1.1–2.0 : 1 on the lids'
 * pastels and mids. A white backing ring [BACKING_W] wide under the outer ring (1.5u each side of it) and a 1.5u white
 * halo round the lettering lift it off a mid-coloured lid.
 */
object BasketHanko {
    val ink: Color = Color(0xFFC8321F)
    const val BACKING_W: Float = HankoGeometry.RING_W + 3f
}

/** Draws the basket-lid stamp (§5.3) into this draw area (square): white backing and halo, then the vermilion stamp. */
fun DrawScope.drawBasketHanko(text: HankoText, measurer: TextMeasurer, style: TextStyle) =
    drawHanko(text, BasketHanko.ink, measurer, style, halo = Color.White)

private fun DrawScope.drawBrokenRing(color: Color, c: Offset, r: Float, w: Float, gap: Float, gapsDeg: List<Float>) {
    val gapDeg = (gap / r) * (180f / PI.toFloat())
    val sorted = gapsDeg.sorted()
    for (i in sorted.indices) {
        val start = sorted[i] + gapDeg / 2f
        val end = (if (i + 1 < sorted.size) sorted[i + 1] else sorted[0] + 360f) - gapDeg / 2f
        drawArc(color, start, end - start, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Butt))
    }
}

/**
 * The "place sticker here" spot (§4.4): a [size] circle with a 2 dp exactly-dashed ring (nominal 4 / 3, 13 dashes at
 * r 14 dp), white fill (Glow: hollow). Decorative.
 */
@Composable
fun StickerSpot(modifier: Modifier = Modifier, size: Dp = 30.dp, dash: Color = PicnicTheme.colors.spotDash, fill: Color = PicnicTheme.colors.spotFill) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) { drawStickerSpot(dash, fill) }
}

/** Draws the spot into this area: ring centred on radius (size / 2 − 1 dp), 2 dp wide. */
fun DrawScope.drawStickerSpot(dash: Color, fill: Color, stroke: Dp = 2.dp, nominalDash: Dp = 4.dp, nominalGap: Dp = 3.dp) {
    val w = stroke.toPx()
    val r = min(size.width, size.height) / 2f - w / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    if (fill.alpha > 0f) drawCircle(fill, r, c)
    drawDashedCircle(dash, c, r, w, nominalDash.toPx(), nominalGap.toPx())
}

/**
 * The basket's landing spot (§5.3): a dotted 7 dp-radius ring, 1.8 dp dashed 3.2 / 2.4, half-sunk in the basket's
 * mouth. Decorative.
 */
@Composable
fun LandingSpot(modifier: Modifier = Modifier, color: Color = PicnicTheme.colors.ghostDash) {
    Canvas(modifier.size(16.dp).clearAndSetSemantics { }) {
        drawDashedCircle(color, Offset(size.width / 2f, size.height / 2f), 7.dp.toPx(), 1.8.dp.toPx(), 3.2.dp.toPx(), 2.4.dp.toPx())
    }
}

/**
 * The checkbox face in its 48 dp target (§4.4): unchecked = the 30 dp dashed spot, centred; checked = the 40 dp hanko
 * at (4, 4), rotated by [hankoAngle] of [seed], the spot hidden underneath. [slam] plays the 1.4 → 1.15 → 1 stamp
 * slam (2 steps, 160 ms) on the frame clock under the fixed motion scale (a fade under reduced motion). Decorative:
 * the ROW is the toggle (Role.Checkbox) and carries the state. The spot and stamp never scale with the font.
 */
@Composable
fun CheckMark(
    checked: Boolean,
    seed: String,
    stampText: HankoText = HankoText.GotIt,
    modifier: Modifier = Modifier,
    slam: Boolean = false,
) {
    val reduced = LocalReducedMotion.current
    val scale = remember(seed) { Animatable(1f) }
    val alpha = remember(seed) { Animatable(1f) }
    LaunchedEffect(checked, slam) {
        if (checked && slam) {
            withContext(FixedMotionScale) {
                if (reduced) {
                    alpha.snapTo(0f)
                    alpha.animateTo(1f, androidx.compose.animation.core.tween(PicnicMotion.rmFade.durationMillis))
                } else {
                    val scales = PicnicMotion.hankoSlamScales
                    val step = PicnicMotion.hankoSlam.durationMillis / (scales.size - 1)
                    for (s in scales.dropLast(1)) {
                        scale.snapTo(s)
                        kotlinx.coroutines.delay(step.toLong())
                    }
                    scale.snapTo(scales.last())
                }
            }
        }
    }
    Box(modifier.size(48.dp).clearAndSetSemantics { }) {
        if (checked) {
            HankoStamp(
                stampText,
                Modifier
                    .offset(4.dp, 4.dp)
                    .rotate(hankoAngle(seed))
                    .scaleAlpha(scale.value, alpha.value),
                size = 40.dp,
            )
        } else {
            StickerSpot(Modifier.align(Alignment.Center))
        }
    }
}

/** v1 form: a checkbox face seeded by nothing (rotation −12° ± 4 from ""). */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    CheckMark(checked = checked, seed = "", modifier = modifier)
}

private fun Modifier.scaleAlpha(scale: Float, alpha: Float): Modifier =
    if (scale == 1f && alpha == 1f) this else this.then(Modifier.graphicsScaleAlpha(scale, alpha))

private fun Modifier.graphicsScaleAlpha(scale: Float, alpha: Float): Modifier =
    graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }

/** A stamp drawn into a canvas, already rotated (for places that draw it themselves, e.g. a basket lid). */
fun DrawScope.drawHankoRotated(text: HankoText, color: Color, measurer: TextMeasurer, style: TextStyle, degrees: Float) {
    rotate(degrees) { drawHanko(text, color, measurer, style) }
}

/** Kept for the v1 call sites that scaled a stamp: scales [block] about the centre. */
internal inline fun DrawScope.scaled(factor: Float, block: DrawScope.() -> Unit) = scale(factor) { block() }

/** The check-off stamp's pose numbers, for tests and the choreography. */
@Immutable
data class HankoPose(val scale: Float, val angle: Float)

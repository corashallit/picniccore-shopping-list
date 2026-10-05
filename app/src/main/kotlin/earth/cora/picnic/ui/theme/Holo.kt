package earth.cora.picnic.ui.theme

import android.graphics.SweepGradient
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/*
 * Holo prism foil (docs/DESIGN_STICKER.md §2.11): REWARD MOMENTS ONLY. Allowed on: the next sticker spot in the book,
 * the Next card's foil (static), the celebration (title glint, stamp card back, bonus card), the full book (release
 * paper and fore-edge) and the PERFECT stamp page, shiny and jackpot stickers, holo album pockets, the perfect-week
 * card, the "shiny!" chip. Nowhere else (HoloUsageTest scans every call site of [holoBrush] and [stickerHoloBrush]).
 */
object Holo {
    /** The foil brush's closed 6-stop loop (§2.11). */
    val sweepColors: List<Color> = listOf(
        Color(0xFFFFD6E8), Color(0xFFCCEBFF), Color(0xFFC9F2E3), Color(0xFFFFF3A6), Color(0xFFE4D6FF), Color(0xFFFFD6E8),
    )
    val sweepStops: List<Float> = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)

    /** The sweep starts at 210° (CSS conic angle: clockwise from 12 o'clock). */
    const val SWEEP_FROM_DEG: Float = 210f

    /** Sticker holo: a linear gradient (0,0) → (1,1), clipped to the sticker's `dominant` at 62 %. */
    val stickerColors: List<Color> = listOf(
        Color(0xFFFFD6E8), Color(0xFFCCEBFF), Color(0xFFC9F2E3), Color(0xFFFFF3A6), Color(0xFFE4D6FF),
    )
    val stickerStops: List<Float> = listOf(0f, 0.25f, 0.5f, 0.72f, 1f)
    const val STICKER_ALPHA: Float = 0.62f

    /** The glint (§2.11): a 30 dp band of white at 35 %, at 60° from the horizontal, 12 steps over 1.2 s. */
    val glint: GlintSpec = GlintSpec(widthDp = 30f, alpha = 0.35f, angleDeg = 60f, steps = 12, durationMillis = 1200)

    /** The shiny sticker's glint: an 80 % white band clipped to the die-cut, 6 steps, every 4 s. */
    val shinyGlint: GlintSpec = GlintSpec(widthDp = 14f, alpha = 0.8f, angleDeg = 60f, steps = 6, durationMillis = 500)
}

/** A glint band: [widthDp] wide, white at [alpha], tilted [angleDeg] from the horizontal, [steps] poses. */
@Immutable
data class GlintSpec(val widthDp: Float, val alpha: Float, val angleDeg: Float, val steps: Int, val durationMillis: Int) {
    /** The stepped progress (0..1) of the sweep [elapsedMillis] after it started. */
    fun progressAt(elapsedMillis: Long): Float = Stepped(steps).transform((elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f))
}

/**
 * The foil brush (§2.11): a sweep gradient around [center] from 210° through the closed 6-stop loop. REWARD SURFACES
 * ONLY (see the file header).
 */
fun holoBrush(center: Offset): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val shader = SweepGradient(
            center.x, center.y,
            Holo.sweepColors.map { it.toArgb() }.toIntArray(),
            Holo.sweepStops.toFloatArray(),
        )
        // Android's sweep starts at 3 o'clock; CSS's conic angle starts at 12 o'clock: 210° CSS = 120° Android.
        val m = android.graphics.Matrix()
        m.setRotate(Holo.SWEEP_FROM_DEG - 90f, center.x, center.y)
        shader.setLocalMatrix(m)
        return shader
    }
}

/** The sticker holo (§2.11): a linear (0,0) → (w,h) gradient over [size]. REWARD SURFACES ONLY. */
fun stickerHoloBrush(size: Size, topLeft: Offset = Offset.Zero): Brush = Brush.linearGradient(
    colorStops = Holo.stickerStops.zip(Holo.stickerColors).toTypedArray(),
    start = topLeft,
    end = Offset(topLeft.x + size.width, topLeft.y + size.height),
)

/**
 * Draws a glint band at [progress] (0 = off the left edge, 1 = off the right edge) across this draw area, clipped to
 * [clip] when given. The band is [spec] wide and tilted from the horizontal.
 */
fun DrawScope.drawGlint(progress: Float, spec: GlintSpec = Holo.glint, clip: Path? = null) {
    if (progress <= 0f || progress >= 1f) return
    val band = spec.widthDp.dp.toPx()
    val diag = hypot(size.width, size.height)
    val x = -band + (size.width + band * 2) * progress
    val draw: DrawScope.() -> Unit = {
        // A vertical band rotated so its long edges sit at angleDeg from the horizontal.
        rotate(degrees = spec.angleDeg - 90f, pivot = Offset(x, size.height / 2f)) {
            drawRect(Color.White.copy(alpha = spec.alpha), Offset(x - band / 2f, size.height / 2f - diag), Size(band, diag * 2))
        }
    }
    if (clip != null) clipPath(clip) { draw() } else draw()
}

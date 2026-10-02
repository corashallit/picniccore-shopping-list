package earth.cora.picnic.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Exact dashes (rule R2, docs/DESIGN_STICKER.md §2.12): the dash count divides the path's length exactly, so there is
 * never a half dash at the seam, and the seam falls in the middle of a gap. Every dashed circle (the checkbox spot
 * 4 / 3 dp, stamp rings 3.4 / 2.6, book spots, the basket's landing spot 3.2 / 2.4) uses [forCircle]; every other
 * dashed closed path (the ghost outline 3 / 2 round the die-cut, the Add-aisle spot, the recipe card's frame) uses
 * [forPath] with its PathMeasure length. Lengths are in px at the drawing density (DashSpecTest checks 1×–4×).
 *
 * @property count dashes along the path.
 * @property interval dash + gap, exactly length / count (closed paths).
 */
@Immutable
data class DashSpec(val count: Int, val interval: Float, val dash: Float, val gap: Float, val phase: Float, val length: Float) {
    /** The path effect for a Stroke; draw circles with [circlePath] so the path starts at −90°. */
    fun pathEffect(): PathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), phase)

    companion object {
        /** A circle of radius [r] (px); its path ([circlePath]) starts at −90° (12 o'clock), clockwise. */
        fun forCircle(r: Float, nominalDash: Float, nominalGap: Float): DashSpec =
            forPath((2 * PI * r).toFloat(), closed = true, nominalDash = nominalDash, nominalGap = nominalGap)

        /**
         * Any path of [length] (px, from PathMeasure). Closed: n = round(length / (dash + gap)), interval = length / n,
         * the dash keeps its nominal share of the interval, and the phase puts the seam (the path's start) in the middle
         * of a gap. Open: dashes at both ends, n chosen the same way.
         */
        fun forPath(length: Float, closed: Boolean, nominalDash: Float, nominalGap: Float): DashSpec {
            require(nominalDash > 0f && nominalGap >= 0f) { "dash must be positive" }
            val unit = nominalDash + nominalGap
            val share = nominalDash / unit
            if (length <= 0f) return DashSpec(0, unit, nominalDash, nominalGap, 0f, length)
            return if (closed) {
                val n = max(1, (length / unit).roundToInt())
                val interval = length / n
                val dash = interval * share
                val gap = interval - dash
                // Pattern position at distance s is (s + phase) mod interval; s = 0 lands mid-gap.
                DashSpec(n, interval, dash, gap, phase = dash + gap / 2f, length = length)
            } else {
                // length = n·dash + (n − 1)·gap = interval·(n − 1 + share)
                val n = max(1, (length / unit + 1f - share).roundToInt())
                val interval = length / (n - 1 + share)
                val dash = interval * share
                DashSpec(n, interval, dash, interval - dash, phase = 0f, length = length)
            }
        }

        /** A closed circle path centred at [center] with radius [r], starting at −90° and running clockwise. */
        fun circlePath(center: Offset, r: Float): Path = Path().apply {
            val rect = Rect(center, r)
            moveTo(center.x, center.y - r)
            arcTo(rect, -90f, 180f, false)
            arcTo(rect, 90f, 180f, false)
            close()
        }

        /** The length of [path]'s first contour, measured as drawn. */
        fun lengthOf(path: Path): Float {
            val m = PathMeasure()
            m.setPath(path, forceClosed = false)
            return m.length
        }

        /**
         * Splits [path] into its contours (each with its own length), so each can be dashed exactly on its own: a
         * dash effect restarts at every contour.
         */
        fun contoursOf(path: Path): List<Pair<Path, Float>> {
            val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
            val out = mutableListOf<Pair<Path, Float>>()
            do {
                val len = measure.length
                if (len > 0f) {
                    val dst = android.graphics.Path()
                    measure.getSegment(0f, len, dst, true)
                    if (measure.isClosed) dst.close()
                    out += dst.asComposePath() to len
                }
            } while (measure.nextContour())
            return out
        }
    }
}

/** Draws an exact dashed ring of radius [r] (px) with [strokeWidth] (px), seam mid-gap at 12 o'clock. */
fun DrawScope.drawDashedCircle(
    color: Color,
    center: Offset,
    r: Float,
    strokeWidth: Float,
    nominalDash: Float,
    nominalGap: Float,
    alpha: Float = 1f,
) {
    val spec = DashSpec.forCircle(r, nominalDash, nominalGap)
    drawPath(
        DashSpec.circlePath(center, r),
        color,
        alpha = alpha,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt, pathEffect = spec.pathEffect()),
    )
}

/** Draws [path] dashed exactly ([DashSpec.forPath], closed), seam mid-gap at the path's start. */
fun DrawScope.drawDashedPath(
    path: Path,
    color: Color,
    strokeWidth: Float,
    nominalDash: Float,
    nominalGap: Float,
    alpha: Float = 1f,
    join: StrokeJoin = StrokeJoin.Round,
) {
    for ((contour, length) in DashSpec.contoursOf(path)) {
        val spec = DashSpec.forPath(length, closed = true, nominalDash = nominalDash, nominalGap = nominalGap)
        drawPath(contour, color, alpha = alpha, style = Stroke(width = strokeWidth, join = join, cap = StrokeCap.Butt, pathEffect = spec.pathEffect()))
    }
}

package earth.cora.picnic.release

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Measures flashing the way WCAG 2.3.1's general flash threshold counts it (docs/DESIGN_STICKER.md §8.8, §15 "No
 * flashing", item 198): the screen is sampled frame by frame as it is drawn (every window), cut into [CELL_DP] cells,
 * and each cell's mean relative luminance followed through time. A change is a swing of at least [SWING] in relative
 * luminance against the last turn, the darker side under [DARK] (a turn on paler paper is not a flash); a flash is a
 * pair of opposing changes. [worst] is the cell and the one-second window holding the most changes: more than six (three
 * flashes) in any second is too fast. Small marks (the REC dot, the LCD star) are under a cell's notice by design, as
 * they are under WCAG's (a flash counts over an area); their own timings are checked on the motion spec.
 */
class FlashMeter(private val compose: ComposeContentTestRule) {
    private var cols = 0
    private var rows = 0
    private val times = ArrayList<Long>()
    private val frames = ArrayList<FloatArray>()

    /** How many samples were taken. */
    val samples: Int get() = frames.size

    /** Samples the screen as it is now, at test-clock [time]. */
    fun sample(time: Long) {
        compose.waitForIdle()
        var cells: FloatArray? = null
        compose.runOnIdle {
            val (w, h) = screenSize()
            val bw = (w * SCALE).toInt()
            val bh = (h * SCALE).toInt()
            val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            canvas.scale(SCALE, SCALE)
            drawWindows(canvas)
            val px = IntArray(bw * bh)
            bitmap.getPixels(px, 0, bw, 0, 0, bw, bh)
            bitmap.recycle()
            val cell = max(1, (CELL_DP * compose.density.density * SCALE).toInt())
            cols = (bw + cell - 1) / cell
            rows = (bh + cell - 1) / cell
            val sum = DoubleArray(cols * rows)
            val count = IntArray(cols * rows)
            for (y in 0 until bh) for (x in 0 until bw) {
                val i = (y / cell) * cols + x / cell
                sum[i] += luminance(px[y * bw + x])
                count[i]++
            }
            cells = FloatArray(cols * rows) { (sum[it] / max(1, count[it])).toFloat() }
        }
        times += time
        frames += requireNotNull(cells)
    }

    /** The worst cell: its most changes in any one second, when, and where (dp from the top left). */
    data class Worst(val changes: Int, val at: List<Long>, val xDp: Int, val yDp: Int) {
        override fun toString(): String = "$changes changes in one second at the cell ($xDp, $yDp) dp, at ${at.take(10)} ms"
    }

    fun worst(): Worst {
        var best = Worst(0, emptyList(), 0, 0)
        if (frames.isEmpty()) return best
        for (c in 0 until cols * rows) {
            val changes = changesOf(c)
            var lo = 0
            for (hi in changes.indices) {
                while (changes[hi] - changes[lo] >= 1000L) lo++
                val n = hi - lo + 1
                if (n > best.changes) best = Worst(n, changes.subList(lo, hi + 1).toList(), (c % cols) * CELL_DP.toInt(), (c / cols) * CELL_DP.toInt())
            }
        }
        return best
    }

    /** The times of cell [c]'s changes (each a swing of [SWING] against its last turn, the darker side under [DARK]). */
    private fun changesOf(c: Int): List<Long> {
        val out = ArrayList<Long>()
        var dir = 0
        var ext = frames[0][c]
        var lo = ext
        var hi = ext
        for (i in 1 until frames.size) {
            val l = frames[i][c]
            when (dir) {
                0 -> {
                    lo = min(lo, l)
                    hi = max(hi, l)
                    if (l - lo >= SWING && lo < DARK) {
                        out += times[i]; dir = 1; ext = l
                    } else if (hi - l >= SWING && l < DARK) {
                        out += times[i]; dir = -1; ext = l
                    }
                }
                1 -> if (l > ext) ext = l else if (ext - l >= SWING && l < DARK) {
                    out += times[i]; dir = -1; ext = l
                }
                else -> if (l < ext) ext = l else if (l - ext >= SWING && ext < DARK) {
                    out += times[i]; dir = 1; ext = l
                }
            }
        }
        return out
    }

    companion object {
        /** The cell (dp): a flash smaller than about a sixth of a 360 dp phone's width is a mark, not a flash. */
        const val CELL_DP = 48f

        /** WCAG's flash: a change of 10 % in relative luminance, the darker image under 0.80. */
        const val SWING = 0.10f
        const val DARK = 0.80f

        /** Three flashes, six changes, in any one second at most (WCAG 2.3.1; §15). */
        const val MAX_CHANGES = 6

        /** The screen is sampled at a quarter of its pixels (a cell is still 36 samples wide at 3 px per dp). */
        private const val SCALE = 0.25f

        private val LINEAR = FloatArray(256) { v ->
            val s = v / 255f
            if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }

        /** Relative luminance (WCAG) of an opaque sRGB pixel. */
        fun luminance(argb: Int): Float =
            0.2126f * LINEAR[(argb shr 16) and 0xFF] + 0.7152f * LINEAR[(argb shr 8) and 0xFF] + 0.0722f * LINEAR[argb and 0xFF]
    }
}

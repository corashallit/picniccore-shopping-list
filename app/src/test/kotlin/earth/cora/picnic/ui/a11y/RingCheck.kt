package earth.cora.picnic.ui.a11y

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Focus shows where it lands (docs/DESIGN_STICKER.md §4.6 "a 2 dp ballpoint focus ring 3 dp outside the target", §15):
 * for the first stop of each kind of control (its role, size and test tag's stem), the screen round it while it holds
 * focus is set against the same place once the next Tab has moved focus on (allowing for a scroll between, and leaving
 * out the next control's own ring). A kind is judged where both pictures hold it whole; a later stop of the kind is
 * tried where they do not. The pictures cost time, so each kind is judged once a walk.
 */
class RingCheck(private val compose: ComposeContentTestRule, private val problems: MutableCollection<String>) {
    private class Pending(
        val where: String,
        val kind: String,
        val key: String,
        val said: String,
        val bounds: Rect,
        val region: android.graphics.Rect,
        val pixels: IntArray,
    )

    private val judged = HashSet<String>()
    private var pending: Pending? = null
    var checked = 0
        private set

    /** Focus has landed on [f] (the control [control]) in [v]: judge the last stop, and keep this one if new. */
    fun at(v: Visit, f: SemanticsNode, control: SemanticsNode) {
        val root = f.root()
        var shot: Bitmap? = null
        fun shot(): Bitmap = shot ?: picture(root.id).also { shot = it }
        val margin = (RING_REACH_DP * v.density).roundToInt()
        pending?.let { p ->
            pending = null
            judge(v, p, shot(), outset(hull(f.boundsInRoot, control.boundsInRoot), margin))
        }
        val kind = kind(v, control)
        // The Trip book's page holds focus itself, with nothing on it to ring (Escape reaches it there, §15).
        if (kind in judged || KeyRules.holdsItself(f)) return
        val bounds = hull(f.boundsInRoot, control.boundsInRoot)
        val region = outset(bounds, margin)
        val bmp = shot()
        if (!inside(region, bmp)) return
        pending = Pending(v.where, kind, KeyRules.key(control), control.spoken(), bounds, region, pixels(bmp, region))
    }

    /** The walk of one screen is over: a stop left unjudged is tried again at the next of its kind. */
    fun end() {
        pending = null
    }

    private fun judge(v: Visit, p: Pending, bmp: Bitmap, next: android.graphics.Rect) {
        // Where the control is now (a Tab can scroll the screen): the same control, the same size.
        val now = v.nodes().firstOrNull { KeyRules.key(it) == p.key && abs(it.boundsInRoot.width - p.bounds.width) < 2f && abs(it.boundsInRoot.height - p.bounds.height) < 2f }
            ?: return
        val dx = (now.boundsInRoot.left - p.bounds.left).roundToInt()
        val dy = (now.boundsInRoot.top - p.bounds.top).roundToInt()
        val moved = android.graphics.Rect(p.region).apply { offset(dx, dy) }
        if (!inside(moved, bmp)) return
        val after = pixels(bmp, moved)
        var changed = 0
        var counted = 0
        for (y in 0 until p.region.height()) for (x in 0 until p.region.width()) {
            // The next control's own ring is not this one's.
            if (next.contains(moved.left + x, moved.top + y)) continue
            counted++
            val i = y * p.region.width() + x
            if (differs(p.pixels[i], after[i])) changed++
        }
        judged += p.kind
        checked++
        val needed = max(MIN_CHANGED_PX, ((p.bounds.width + p.bounds.height) / 2f).roundToInt())
        if (changed < needed) {
            problems += "${p.where}: focus does not show on ${p.said} ($changed of $counted pixels round it change as it takes focus; a ring changes $needed or more)"
        }
    }

    private fun picture(rootId: Int): Bitmap {
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        val index = roots.indexOfFirst { it.id == rootId }.coerceAtLeast(0)
        return compose.onAllNodes(isRoot())[index].captureToImage().asAndroidBitmap()
    }

    companion object {
        /** How far round a control its ring can reach: 3 dp out plus 2 dp of ink, and a little over. */
        const val RING_REACH_DP = 7f

        /** Fewer changed pixels than this is never a ring. */
        const val MIN_CHANGED_PX = 60

        /** A control's kind: its role, size (in 8 dp steps) and test tag's stem ("picnic_row_milk" is a "picnic_row"). */
        fun kind(v: Visit, n: SemanticsNode): String {
            val role = n.config.getOrNull(SemanticsProperties.Role)
            val stem = n.config.getOrNull(SemanticsProperties.TestTag)?.substringBeforeLast('_') ?: "-"
            val step = 8f * v.density
            return "$role|$stem|${(n.boundsInRoot.width / step).roundToInt()}x${(n.boundsInRoot.height / step).roundToInt()}"
        }

        private fun hull(a: Rect, b: Rect) = Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

        private fun outset(r: Rect, by: Int) =
            android.graphics.Rect(r.left.roundToInt() - by, r.top.roundToInt() - by, r.right.roundToInt() + by, r.bottom.roundToInt() + by)

        private fun inside(r: android.graphics.Rect, bmp: Bitmap) = r.left >= 0 && r.top >= 0 && r.right <= bmp.width && r.bottom <= bmp.height && !r.isEmpty

        private fun pixels(bmp: Bitmap, r: android.graphics.Rect): IntArray =
            IntArray(r.width() * r.height()).also { bmp.getPixels(it, 0, r.width(), r.left, r.top, r.width(), r.height()) }

        private fun differs(a: Int, b: Int): Boolean =
            abs(((a shr 16) and 0xff) - ((b shr 16) and 0xff)) > 40 ||
                abs(((a shr 8) and 0xff) - ((b shr 8) and 0xff)) > 40 ||
                abs((a and 0xff) - (b and 0xff)) > 40
    }
}

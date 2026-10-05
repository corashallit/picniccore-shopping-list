package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/**
 * The reading rule's measuring tape (docs/DESIGN_STICKER.md §9, item 167): every text-bearing motion piece reports, every
 * frame on the test clock, its laid-out bounds, its transform and its alpha, and every overlay that could cross a text
 * reports its bounds. A text is STILL while its transform and alpha are constant (and it is fully shown) and no overlay
 * drawn in front of it crosses its bounds; rays and confetti drawn behind every plate do not count. [stillFrom] measures
 * how long a text stays still from a given moment (its settle). The harness also samples where the Next card and the
 * toast are really drawn (their nodes' bounds through every layer, [real]) and reports each as an overlay to the other,
 * so a card sweeping through a held toast, or the toast's lift crossing a held card, fails. Test helper; owner WP6.
 */
class StillnessProbe : MotionProbe {
    data class TextFrame(val time: Long, val bounds: Rect, val dx: Float, val dy: Float, val scale: Float, val rotation: Float, val alpha: Float) {
        /** Where it is drawn: the laid-out bounds moved and scaled about their centre. */
        val drawn: Rect
            get() {
                val c = bounds.center
                val w = bounds.width * scale / 2f
                val h = bounds.height * scale / 2f
                return Rect(c.x - w + dx, c.y - h + dy, c.x + w + dx, c.y + h + dy)
            }

        fun sameAs(o: TextFrame): Boolean =
            close(bounds.left, o.bounds.left) && close(bounds.top, o.bounds.top) && close(bounds.right, o.bounds.right) &&
                close(bounds.bottom, o.bounds.bottom) && close(dx, o.dx) && close(dy, o.dy) && close(scale, o.scale, 1e-3f) &&
                close(rotation, o.rotation, 1e-2f) && close(alpha, o.alpha, 1e-3f)

        private fun close(a: Float, b: Float, eps: Float = 0.5f) = abs(a - b) <= eps
    }

    data class OverlayFrame(val tag: String, val time: Long, val bounds: Rect?, val behind: Boolean)

    /** Where a piece was really drawn at [time] (root px): its node's bounds through every layer, sampled by the harness. */
    data class RealFrame(val time: Long, val bounds: Rect)

    private val texts = LinkedHashMap<String, MutableList<TextFrame>>()
    private val reals = LinkedHashMap<String, MutableList<RealFrame>>()
    private val overlays = ArrayList<OverlayFrame>()

    /**
     * The harness's sample of where [tag] is really drawn at [time] (its semantics node's bounds, which pass through the
     * motion layers): a text is still only while these stay put too, so the check never rests on the piece's own word.
     */
    fun real(tag: String, time: Long, bounds: Rect) {
        val list = reals.getOrPut(tag) { ArrayList() }
        val f = RealFrame(time, bounds)
        if (list.isNotEmpty() && list.last().time == time) list[list.size - 1] = f else list += f
    }

    fun realFrames(tag: String): List<RealFrame> = reals[tag].orEmpty()

    override fun text(tag: String, time: Long, bounds: Rect, dx: Float, dy: Float, scale: Float, rotation: Float, alpha: Float) {
        val list = texts.getOrPut(tag) { ArrayList() }
        val f = TextFrame(time, bounds, dx, dy, scale, rotation, alpha)
        // One record per frame time: the last report wins.
        if (list.isNotEmpty() && list.last().time == time) list[list.size - 1] = f else list += f
    }

    override fun overlay(tag: String, time: Long, bounds: Rect?, behind: Boolean) {
        overlays += OverlayFrame(tag, time, bounds, behind)
    }

    private val recompositions = HashMap<String, Int>()

    override fun recomposed(tag: String) {
        recompositions[tag] = (recompositions[tag] ?: 0) + 1
    }

    /** How many times [tag] recomposed so far. */
    fun recompositions(tag: String): Int = recompositions[tag] ?: 0

    fun frames(tag: String): List<TextFrame> = texts[tag].orEmpty()

    fun overlaysAt(time: Long): List<OverlayFrame> = overlays.filter { it.time == time }

    /** The tags that reported. */
    val tags: Set<String> get() = texts.keys

    /**
     * How long [tag] stays still from [from] (ms of frame time): from the first frame at or after [from] until the first
     * frame where its pose or alpha changes, it is not fully shown, or a front overlay crosses it. Returns that span (the
     * last frame recorded counts as still to its own time). [why] receives the reason it stopped.
     */
    fun stillFrom(tag: String, from: Long, why: ((String) -> Unit)? = null): Long {
        val list = frames(tag)
        val i0 = list.indexOfFirst { it.time >= from }
        if (i0 < 0) {
            why?.invoke("no frame at or after $from")
            return 0
        }
        val ref = list[i0]
        if (ref.alpha < 0.99f) {
            why?.invoke("not fully shown at ${ref.time} (alpha ${ref.alpha})")
            return 0
        }
        val real = reals[tag].orEmpty()
        val realRef = real.firstOrNull { it.time >= ref.time }
        var last = ref.time
        for (k in i0 until list.size) {
            val f = list[k]
            if (!f.sameAs(ref)) {
                why?.invoke("moved at ${f.time}: $f vs $ref")
                return f.time - from
            }
            // Where it is really drawn, when the harness sampled it: the same as at the settle.
            val r = real.firstOrNull { it.time == f.time }
            if (r != null && realRef != null && !sameRect(r.bounds, realRef.bounds)) {
                why?.invoke("really drawn elsewhere at ${f.time}: ${r.bounds} vs ${realRef.bounds}")
                return f.time - from
            }
            val drawn = r?.bounds ?: f.drawn
            val crossing = overlays.firstOrNull { o -> o.tag != tag && !o.behind && o.time == f.time && o.bounds != null && o.bounds.overlaps(drawn) }
            if (crossing != null) {
                why?.invoke("crossed by ${crossing.tag} at ${f.time}")
                return f.time - from
            }
            last = f.time
        }
        why?.invoke("still to the last frame at $last")
        return last - from
    }

    private fun sameRect(a: Rect, b: Rect): Boolean =
        abs(a.left - b.left) <= 0.5f && abs(a.top - b.top) <= 0.5f && abs(a.right - b.right) <= 0.5f && abs(a.bottom - b.bottom) <= 0.5f

    fun clear() {
        texts.clear()
        reals.clear()
        overlays.clear()
    }
}

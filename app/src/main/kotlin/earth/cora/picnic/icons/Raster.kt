package earth.cora.picnic.icons

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A small raster toolkit for the live-sticker post-processor ([IconPostProcessorV4]): plain Kotlin on row-major arrays
 * (index = y * width + x), no Android types, so it runs in JVM unit tests exactly as on the phone. Each function is
 * the counterpart of the numpy / scipy / scikit-image call the mockup's `post4.py` makes.
 */
internal object Raster {
    /** "No feature anywhere" in a squared distance map. */
    const val FAR: Float = Float.MAX_VALUE

    /* ---------------- distance transforms (scipy.ndimage.distance_transform_edt) ---------------- */

    /**
     * Squared Euclidean distance from every pixel to the nearest pixel where [feature] is true (0 on a feature),
     * exact (Felzenszwalb and Huttenlocher's lower envelope). With [nearest], also the index of that feature pixel
     * (scipy's `return_indices`); -1 where there is no feature at all. Pixels outside the grid are not features.
     */
    fun edt(feature: BooleanArray, w: Int, h: Int, nearest: IntArray? = null): FloatArray {
        val n = w * h
        val colD = FloatArray(n)
        val colY = IntArray(n)
        // Column pass: the distance to the nearest feature in the same column (two scans).
        for (x in 0 until w) {
            var last = -1
            var i = x
            for (y in 0 until h) {
                if (feature[i]) last = y
                colY[i] = last
                i += w
            }
            var next = -1
            i = (h - 1) * w + x
            for (y in h - 1 downTo 0) {
                if (feature[i]) next = y
                val up = colY[i]
                val best = when {
                    up < 0 -> next
                    next < 0 -> up
                    y - up <= next - y -> up
                    else -> next
                }
                colY[i] = best
                colD[i] = if (best < 0) FAR else ((y - best) * (y - best)).toFloat()
                i -= w
            }
        }
        // Row pass: the lower envelope of the parabolas (x - q)^2 + colD[q]. Float math is exact enough: every value
        // is an integer under 2^24.
        val out = FloatArray(n)
        val v = IntArray(w)
        val z = FloatArray(w + 1)
        for (y in 0 until h) {
            val row = y * w
            var k = -1
            for (q in 0 until w) {
                val fq = colD[row + q]
                if (fq == FAR) continue
                if (k < 0) {
                    k = 0; v[0] = q; z[0] = Float.NEGATIVE_INFINITY; z[1] = Float.POSITIVE_INFINITY
                    continue
                }
                val qq = fq + (q * q).toFloat()
                var p = v[k]
                var s = (qq - (colD[row + p] + (p * p).toFloat())) / (2 * (q - p)).toFloat()
                while (s <= z[k]) {
                    k--
                    p = v[k]
                    s = (qq - (colD[row + p] + (p * p).toFloat())) / (2 * (q - p)).toFloat()
                }
                k++
                v[k] = q; z[k] = s; z[k + 1] = Float.POSITIVE_INFINITY
            }
            if (k < 0) {
                java.util.Arrays.fill(out, row, row + w, FAR)
                if (nearest != null) java.util.Arrays.fill(nearest, row, row + w, -1)
                continue
            }
            var j = 0
            for (x in 0 until w) {
                while (z[j + 1] < x) j++
                val p = v[j]
                val dx = (x - p).toFloat()
                out[row + x] = dx * dx + colD[row + p]
                if (nearest != null) nearest[row + x] = colY[row + p] * w + p
            }
        }
        return out
    }

    /** Euclidean distance from every pixel of [mask] to the nearest pixel outside it (scipy's `edt(mask)`); 0 outside. */
    fun insideDistance(mask: BooleanArray, w: Int, h: Int): FloatArray {
        val outside = BooleanArray(mask.size) { !mask[it] }
        val d = edt(outside, w, h)
        for (i in d.indices) d[i] = if (d[i] == FAR) FAR else sqrt(d[i])
        return d
    }

    /** Euclidean distance from every pixel to the nearest pixel of [mask] (0 inside it). */
    fun outsideDistance(mask: BooleanArray, w: Int, h: Int): FloatArray {
        val d = edt(mask, w, h)
        for (i in d.indices) d[i] = if (d[i] == FAR) FAR else sqrt(d[i])
        return d
    }

    /** Disk erosion by radius [r] px: the pixels of [mask] more than [r] px from anything outside it. */
    fun erodeDisk(mask: BooleanArray, w: Int, h: Int, r: Double): BooleanArray {
        val outside = BooleanArray(mask.size)
        for (i in mask.indices) outside[i] = !mask[i]
        val d = edt(outside, w, h)
        val r2 = (r * r).toFloat()
        val out = BooleanArray(mask.size)
        // The grid's own border counts as outside (scipy's border_value = 0).
        for (y in 0 until h) {
            val ey = min(y + 1, h - y)
            for (x in 0 until w) {
                val i = y * w + x
                val edge = min(min(x + 1, w - x), ey).toFloat()
                out[i] = mask[i] && d[i] > r2 && edge * edge > r2
            }
        }
        return out
    }

    /** Disk dilation by radius [r] px. */
    fun dilateDisk(mask: BooleanArray, w: Int, h: Int, r: Double): BooleanArray {
        val d = edt(mask, w, h)
        val r2 = (r * r).toFloat()
        val out = BooleanArray(mask.size)
        for (i in out.indices) out[i] = d[i] <= r2
        return out
    }

    /* ---------------- morphology with the 4-neighbour cross (scipy's default structure) ---------------- */

    fun erodeCross(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 1 until h - 1) {
            var i = y * w + 1
            for (x in 1 until w - 1) {
                out[i] = mask[i] && mask[i - 1] && mask[i + 1] && mask[i - w] && mask[i + w]
                i++
            }
        }
        return out
    }

    fun dilateCross(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until h) {
            var i = y * w
            for (x in 0 until w) {
                out[i] = mask[i] || (x > 0 && mask[i - 1]) || (x < w - 1 && mask[i + 1]) || (y > 0 && mask[i - w]) ||
                    (y < h - 1 && mask[i + w])
                i++
            }
        }
        return out
    }

    /** `binary_opening(mask, iterations)`: [iterations] cross erosions, then as many dilations. */
    fun openCross(mask: BooleanArray, w: Int, h: Int, iterations: Int = 1): BooleanArray {
        var m = mask
        repeat(iterations) { m = erodeCross(m, w, h) }
        repeat(iterations) { m = dilateCross(m, w, h) }
        return m
    }

    /** The mockup's `width_u`: a mask's mean stroke width in px, 2 * area / boundary + 1 (boundary = mask minus its erosion). */
    fun strokeWidthPx(mask: BooleanArray, w: Int, h: Int): Double {
        var area = 0
        var edge = 0
        val eroded = erodeCross(mask, w, h)
        for (i in mask.indices) {
            if (mask[i]) {
                area++
                if (!eroded[i]) edge++
            }
        }
        return 2.0 * area / max(edge, 1) + 1.0
    }

    /** Fills every background region of [mask] that does not touch the grid's border (scipy's `binary_fill_holes`). */
    fun fillHoles(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val reached = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var head = 0
        var tail = 0
        fun seed(i: Int) {
            if (!mask[i] && !reached[i]) {
                reached[i] = true; queue[tail++] = i
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
        return BooleanArray(mask.size) { mask[it] || !reached[it] }
    }

    /* ---------------- connected components (scipy.ndimage.label, 4-connected) ---------------- */

    /** Labels the 4-connected components of [mask] 1..n into [labels] (0 = background) and returns n. */
    fun label(mask: BooleanArray, w: Int, h: Int, labels: IntArray): Int {
        labels.fill(0)
        val queue = IntArray(mask.size)
        var n = 0
        for (start in mask.indices) {
            if (!mask[start] || labels[start] != 0) continue
            n++
            var head = 0
            var tail = 0
            labels[start] = n
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                if (x > 0 && mask[i - 1] && labels[i - 1] == 0) { labels[i - 1] = n; queue[tail++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && labels[i + 1] == 0) { labels[i + 1] = n; queue[tail++] = i + 1 }
                if (i >= w && mask[i - w] && labels[i - w] == 0) { labels[i - w] = n; queue[tail++] = i - w }
                if (i + w < mask.size && mask[i + w] && labels[i + w] == 0) { labels[i + w] = n; queue[tail++] = i + w }
            }
        }
        return n
    }

    /**
     * Labels the 4-connected regions of equal value in [values] (pixels with a negative value are not labelled) and
     * returns the region count; [labels] gets 1..n, 0 for the unlabelled.
     */
    fun labelEqual(values: IntArray, w: Int, h: Int, labels: IntArray): Int {
        labels.fill(0)
        val queue = IntArray(values.size)
        var n = 0
        for (start in values.indices) {
            val v = values[start]
            if (v < 0 || labels[start] != 0) continue
            n++
            var head = 0
            var tail = 0
            labels[start] = n
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                if (x > 0 && values[i - 1] == v && labels[i - 1] == 0) { labels[i - 1] = n; queue[tail++] = i - 1 }
                if (x < w - 1 && values[i + 1] == v && labels[i + 1] == 0) { labels[i + 1] = n; queue[tail++] = i + 1 }
                if (i >= w && values[i - w] == v && labels[i - w] == 0) { labels[i - w] = n; queue[tail++] = i - w }
                if (i + w < values.size && values[i + w] == v && labels[i + w] == 0) { labels[i + w] = n; queue[tail++] = i + w }
            }
        }
        return n
    }

    /* ---------------- colour ---------------- */

    private val SRGB_TO_LINEAR = DoubleArray(256) { i ->
        val c = i / 255.0
        if (c > 0.04045) ((c + 0.055) / 1.055).pow(2.4) else c / 12.92
    }

    /** CIE L*a*b* (D65) of an sRGB colour, written into [out] (L, a, b). Same constants as the mockup's `lab()`. */
    fun lab(rgb: Int, out: DoubleArray) {
        val r = SRGB_TO_LINEAR[(rgb shr 16) and 0xFF]
        val g = SRGB_TO_LINEAR[(rgb shr 8) and 0xFF]
        val b = SRGB_TO_LINEAR[rgb and 0xFF]
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        val fx = labF(x)
        val fy = labF(y)
        val fz = labF(z)
        out[0] = 116 * fy - 16
        out[1] = 500 * (fx - fy)
        out[2] = 200 * (fy - fz)
    }

    private fun labF(t: Double): Double = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0

    fun parseHex(hex: String): Int = hex.removePrefix("#").toInt(16) and 0xFFFFFF

    /* ---------------- contours (skimage.measure.find_contours) ---------------- */

    /** A closed contour in pixel coordinates (pixel centres at integers). */
    class Loop(val xs: FloatArray, val ys: FloatArray) {
        val size: Int get() = xs.size

        /** Shoelace area with y down: negative for an outer boundary, positive for a hole (see [contours]). */
        val signedArea: Double by lazy {
            var s = 0.0
            for (i in 0 until size) {
                val j = if (i + 1 == size) 0 else i + 1
                s += xs[i].toDouble() * ys[j] - xs[j].toDouble() * ys[i]
            }
            s / 2
        }
    }

    /**
     * Marching squares on [field] at [level] with linear interpolation. Values outside the grid count as below the
     * level (0 for a positive level, as skimage on a zero-padded mask), so every loop closes. Loops keep the inside
     * (>= level) on their left as drawn on screen (y down), so an outer boundary has a negative [Loop.signedArea] and
     * a hole a positive one. Saddles join through the cell centre's average value.
     */
    fun contours(field: FloatArray, w: Int, h: Int, level: Float): List<Loop> {
        val outside = if (level > 0f) 0f else level - 1f
        val stride = w + 2
        val ids = IntSlots()
        var px = FloatArray(256)
        var py = FloatArray(256)
        var next = IntArray(256)
        fun v(x: Int, y: Int): Float = if (x < 0 || y < 0 || x >= w || y >= h) outside else field[y * w + x]

        /** The crossing on the grid edge from point (x0, y0) to (x1, y1) (one step right or down): its slot. */
        fun cross(x0: Int, y0: Int, x1: Int, y1: Int): Int {
            // Edge id: (base = point (x0, y0) on the padded grid) * 2, + 1 for a vertical edge.
            val id = (((y0 + 1) * stride + (x0 + 1)) shl 1) or (if (x1 == x0) 1 else 0)
            val known = ids.get(id)
            if (known >= 0) return known
            val slot = ids.put(id)
            if (slot >= px.size) {
                px = px.copyOf(px.size * 2); py = py.copyOf(py.size * 2); next = next.copyOf(next.size * 2)
            }
            val a = v(x0, y0)
            val b = v(x1, y1)
            val t = if (b == a) 0.5f else ((level - a) / (b - a)).coerceIn(0f, 1f)
            px[slot] = x0 + (x1 - x0) * t
            py[slot] = y0 + (y1 - y0) * t
            next[slot] = -1
            return slot
        }
        // Inside flags on the padded grid (point (x, y) at (y + 1) * stride + x + 1), so the cell scan has no bounds checks.
        val inside = BooleanArray(stride * (h + 2))
        val insideOutside = outside >= level
        if (insideOutside) inside.fill(true)
        for (y in 0 until h) {
            var i = y * w
            var p = (y + 1) * stride + 1
            for (x in 0 until w) inside[p++] = field[i++] >= level
        }
        for (cy in -1 until h) {
            var p = (cy + 1) * stride
            for (cx in -1 until w) {
                val a = inside[p]
                val b = inside[p + 1]
                val c = inside[p + 1 + stride]
                val d = inside[p + stride]
                p++
                if (a == b && b == c && c == d) continue
                // Entry / exit per crossed edge, walking with the inside on the left (screen coordinates).
                var in1 = -1; var in2 = -1; var out1 = -1; var out2 = -1
                fun entry(s: Int) { if (in1 < 0) in1 = s else in2 = s }
                fun exit(s: Int) { if (out1 < 0) out1 = s else out2 = s }
                var top = -1; var right = -1; var bottom = -1; var left = -1
                if (a != b) { top = cross(cx, cy, cx + 1, cy); if (a) exit(top) else entry(top) }
                if (b != c) { right = cross(cx + 1, cy, cx + 1, cy + 1); if (b) exit(right) else entry(right) }
                if (c != d) { bottom = cross(cx, cy + 1, cx + 1, cy + 1); if (c) exit(bottom) else entry(bottom) }
                if (d != a) { left = cross(cx, cy, cx, cy + 1); if (a) entry(left) else exit(left) }
                if (in2 < 0) {
                    next[in1] = out1
                } else {
                    val centre = (v(cx, cy) + v(cx + 1, cy) + v(cx + 1, cy + 1) + v(cx, cy + 1)) / 4f >= level
                    if (a && c) {
                        // entries: right, left; exits: top, bottom
                        if (centre) { next[right] = top; next[left] = bottom } else { next[left] = top; next[right] = bottom }
                    } else {
                        // b and d inside: entries: top, bottom; exits: right, left
                        if (centre) { next[top] = left; next[bottom] = right } else { next[top] = right; next[bottom] = left }
                    }
                }
            }
        }
        val loops = ArrayList<Loop>()
        val seen = BooleanArray(ids.size)
        val xs = FloatArrayBuilder()
        val ys = FloatArrayBuilder()
        for (start in 0 until ids.size) {
            if (seen[start]) continue
            xs.clear(); ys.clear()
            var s = start
            while (s >= 0 && !seen[s]) {
                seen[s] = true
                xs.add(px[s]); ys.add(py[s])
                s = next[s]
            }
            if (xs.size >= 3) loops += Loop(xs.toArray(), ys.toArray())
        }
        return loops
    }

    /** Edge id -> dense slot (open addressing; ids are non-negative). */
    private class IntSlots {
        private var keys = IntArray(1024) { -1 }
        private var vals = IntArray(1024)
        var size = 0
            private set

        fun get(id: Int): Int {
            var i = mix(id) and (keys.size - 1)
            while (true) {
                val k = keys[i]
                if (k == id) return vals[i]
                if (k == -1) return -1
                i = (i + 1) and (keys.size - 1)
            }
        }

        fun put(id: Int): Int {
            if ((size + 1) * 2 > keys.size) grow()
            var i = mix(id) and (keys.size - 1)
            while (keys[i] != -1) i = (i + 1) and (keys.size - 1)
            keys[i] = id
            vals[i] = size
            return size++
        }

        private fun grow() {
            val oldK = keys
            val oldV = vals
            keys = IntArray(oldK.size * 2) { -1 }
            vals = IntArray(oldK.size * 2)
            for (j in oldK.indices) {
                if (oldK[j] == -1) continue
                var i = mix(oldK[j]) and (keys.size - 1)
                while (keys[i] != -1) i = (i + 1) and (keys.size - 1)
                keys[i] = oldK[j]; vals[i] = oldV[j]
            }
        }

        private fun mix(v: Int): Int {
            val x = v * -0x61c88647
            return x xor (x ushr 15)
        }
    }

    private class FloatArrayBuilder {
        private var data = FloatArray(256)
        var size = 0
            private set

        fun clear() { size = 0 }

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }

    /** Ramer-Douglas-Peucker on a closed loop: at most [epsilon] px from the original. */
    fun simplifyClosed(loop: Loop, epsilon: Double): Loop {
        val n = loop.size
        if (n < 8) return loop
        var far = 0
        var best = -1.0
        for (i in 0 until n) {
            val dx = (loop.xs[i] - loop.xs[0]).toDouble()
            val dy = (loop.ys[i] - loop.ys[0]).toDouble()
            val d = dx * dx + dy * dy
            if (d > best) { best = d; far = i }
        }
        val keep = BooleanArray(n)
        keep[0] = true
        keep[far] = true
        rdp(loop, 0, far, epsilon, keep)
        rdp(loop, far, n, epsilon, keep)
        val idx = (0 until n).filter { keep[it] }
        return Loop(FloatArray(idx.size) { loop.xs[idx[it]] }, FloatArray(idx.size) { loop.ys[idx[it]] })
    }

    /** RDP between points [from] and [to] (index [to] == size wraps to point 0). */
    private fun rdp(loop: Loop, from: Int, to: Int, epsilon: Double, keep: BooleanArray) {
        val n = loop.size
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(from, to))
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            if (e - s < 2) continue
            val ax = loop.xs[s].toDouble()
            val ay = loop.ys[s].toDouble()
            val bx = loop.xs[e % n].toDouble()
            val by = loop.ys[e % n].toDouble()
            var worst = -1.0
            var at = -1
            for (i in s + 1 until e) {
                val d = segmentDistance(loop.xs[i].toDouble(), loop.ys[i].toDouble(), ax, ay, bx, by)
                if (d > worst) { worst = d; at = i }
            }
            if (worst > epsilon) {
                keep[at] = true
                stack.addLast(intArrayOf(s, at))
                stack.addLast(intArrayOf(at, e))
            }
        }
    }

    /** Distance from (px, py) to the segment (ax, ay)-(bx, by). */
    fun segmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val ex = px - (ax + t * dx)
        val ey = py - (ay + t * dy)
        return sqrt(ex * ex + ey * ey)
    }

    /* ---------------- resampling ---------------- */

    /**
     * Area-weighted (box filter) resampling of a premultiplied RGBA float image ([src], 4 floats per pixel, [sw] x
     * [sh]) into [dst] ([dw] x [dh]): destination pixel X covers source x in [(X - ox) / f, (X + 1 - ox) / f), so the
     * source pixel grid is scaled by [f] and its origin lands at ([ox], [oy]). Accumulates into [dst].
     */
    fun resampleBox(src: FloatArray, sw: Int, sh: Int, dst: FloatArray, dw: Int, dh: Int, f: Double, ox: Double, oy: Double) {
        // Separable: horizontal weights per destination column, vertical per destination row.
        val colSpans = spans(dw, sw, f, ox)
        val rowSpans = spans(dh, sh, f, oy)
        val tmp = FloatArray(dw * sh * 4)
        for (sy in 0 until sh) {
            for (X in 0 until dw) {
                val span = colSpans[X] ?: continue
                var r = 0f; var g = 0f; var b = 0f; var a = 0f
                for (k in span.indices) {
                    val sx = span.first + k
                    val wgt = span.weights[k]
                    val s = (sy * sw + sx) * 4
                    r += src[s] * wgt; g += src[s + 1] * wgt; b += src[s + 2] * wgt; a += src[s + 3] * wgt
                }
                val t = (sy * dw + X) * 4
                tmp[t] = r; tmp[t + 1] = g; tmp[t + 2] = b; tmp[t + 3] = a
            }
        }
        for (Y in 0 until dh) {
            val span = rowSpans[Y] ?: continue
            for (X in 0 until dw) {
                var r = 0f; var g = 0f; var b = 0f; var a = 0f
                for (k in span.indices) {
                    val sy = span.first + k
                    val wgt = span.weights[k]
                    val s = (sy * dw + X) * 4
                    r += tmp[s] * wgt; g += tmp[s + 1] * wgt; b += tmp[s + 2] * wgt; a += tmp[s + 3] * wgt
                }
                val t = (Y * dw + X) * 4
                dst[t] += r; dst[t + 1] += g; dst[t + 2] += b; dst[t + 3] += a
            }
        }
    }

    private class Span(val first: Int, val weights: FloatArray) {
        val indices: IntRange get() = weights.indices
    }

    /** For each destination index, the source pixels it covers and their normalised overlap weights. */
    private fun spans(dn: Int, sn: Int, f: Double, o: Double): Array<Span?> = Array(dn) { d ->
        val s0 = (d - o) / f
        val s1 = (d + 1 - o) / f
        val lo = max(0, floor(s0).toInt())
        val hi = min(sn - 1, floor(s1 - 1e-9).toInt())
        if (hi < lo) return@Array null
        val weights = FloatArray(hi - lo + 1) { k ->
            val p = lo + k
            val overlap = min(s1, p + 1.0) - max(s0, p.toDouble())
            (max(0.0, overlap) / (s1 - s0)).toFloat()
        }
        Span(lo, weights)
    }

    /** Coverage of a pixel whose centre is [d] px from a shape's edge at radius [r] (1 inside, 0 outside, ~1 px ramp). */
    fun coverage(d: Double, r: Double): Float = (r - d + 0.5).coerceIn(0.0, 1.0).toFloat()

    fun near(a: Double, b: Double, eps: Double): Boolean = abs(a - b) <= eps
}

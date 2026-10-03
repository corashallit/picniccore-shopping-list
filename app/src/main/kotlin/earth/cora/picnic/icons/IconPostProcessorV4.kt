package earth.cora.picnic.icons

import earth.cora.picnic.core.assistant.StickerPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Live stickers v4, the on-device post-processing (docs/DESIGN_STICKER.md §18.3 step 5, checklist item 45): a
 * plain-Kotlin port of the mockup's `design/mockup/ai4` `post4.py`, CPU only, well under 100 ms per icon.
 *
 * Input: the model's 1024 × 1024 answer to the neighbour sheet ([SheetComposer]); the new artwork is in its
 * bottom-right 512 px cell, on real alpha. Steps, as in post4:
 * 1. **Alpha cut** at 50 % (opened twice with the 4-neighbour cross; the main pile kept), or a flat-grey key when the
 *    answer came back opaque.
 * 2. **Palette snap**: every art pixel to the nearest (CIE Lab) of the item's two families' pastel, mid, trace and
 *    deep, white and lemon pastel. Trace blobs thicker than 3.4u are bands, not outlines: they become the deep tone.
 * 3. **Speck removal**: the model's own gloss and white specks (thinner than 3.2u, under 60u²) and specks of any other
 *    colour under 2u take the colour of their surroundings.
 * 4. **Size and base**: the silhouette is scaled to the neighbours' mean art area ([targetAreaU2]), capped at a 39u
 *    box, centred on x 24 with its base on y 42 (the 52u box −2…50).
 * 5. **Trace to 2u**: a thinner outline grows to 2u in final units.
 * 6. **Procedural gloss**: one 2.6u white stroke with round caps along the upper-left inner contour of the dominant
 *    fill region (bearing 200–290°, ≥ 10u), plus a 1.6u dot just past its lower end — the library's gloss recipe.
 * 7. **Die-cut**: the art's alpha dilated 4u with its holes filled, in white under the art; its contour (marching
 *    squares + Ramer-Douglas-Peucker) is the stored cut path.
 *
 * Output: a 208 px ARGB sticker (4 px per u: 52 dp at xxxhdpi), art on its white die-cut and transparent around it,
 * plus the cut path in the bitmap's pixel space. The hard shadow is not baked in: `ItemIcon` draws it at render time
 * from the alpha, like every library sticker (§3.3), so live and library stickers match in every theme.
 */
object IconPostProcessorV4 {
    /** Output size: the 52u box at 4 px per u. */
    const val OUT_PX: Int = 208

    /** The sticker box in u (viewBox −2 −2 52 52). */
    const val BOX_U: Double = 52.0

    /** Largest art side, base line and centre line, in icon u (§3.1). */
    const val ART_MAX_U: Double = 39.0
    const val BASE_U: Double = 42.0
    const val CENTRE_U: Double = 24.0

    /** The white die-cut border outside the art. */
    const val CUT_U: Double = 4.0

    /** The colour-trace outline width. */
    const val TRACE_U: Double = 2.0

    /** Gloss stroke width and dot radius (gloss.py MAIN_W, DOT_R). */
    const val GLOSS_W_U: Double = 2.6
    const val GLOSS_DOT_R_U: Double = 0.8

    /** Shortest acceptable gloss stroke. */
    const val GLOSS_MIN_U: Double = 10.0

    /** Used when the neighbours' art area is unknown: the library's typical sticker. */
    const val DEFAULT_TARGET_AREA_U2: Double = 850.0

    /** An answer with less art than this in the fourth cell is unusable (in cell u², before scaling). */
    const val MIN_ART_U2: Double = 120.0

    /** Cut path tolerance (post4: simplify(.12)). */
    const val CUT_SIMPLIFY_U: Double = 0.12

    private const val SS = 2
    private const val OU = OUT_PX / BOX_U
    private const val RU = OU * SS
    private const val RENDER_PX = OUT_PX * SS

    /** gloss.py's bearing ranges, widest last (degrees, y down: 180 = left, 270 = up). */
    private val GLOSS_RANGES = listOf(200.0 to 290.0, 190.0 to 296.0, 176.0 to 304.0)

    enum class Tone { PASTEL, MID, TRACE, DEEP, WHITE, LEMON_PASTEL }

    /** The snap palette: each family's pastel, mid, trace and deep (in that order), then white and lemon pastel. */
    class Palette(val families: List<String>, val colors: IntArray, val tones: Array<Tone>) {
        val size: Int get() = colors.size
    }

    fun palette(families: List<String>): Palette {
        val names = StickerPalette.normalize(families)
        val colors = ArrayList<Int>()
        val tones = ArrayList<Tone>()
        for (name in names) {
            val f = StickerPalette.family(name) ?: continue
            colors += Raster.parseHex(f.pastel); tones += Tone.PASTEL
            colors += Raster.parseHex(f.mid); tones += Tone.MID
            colors += Raster.parseHex(f.trace); tones += Tone.TRACE
            colors += Raster.parseHex(f.deep); tones += Tone.DEEP
        }
        colors += Raster.parseHex(StickerPalette.WHITE); tones += Tone.WHITE
        colors += Raster.parseHex(StickerPalette.LEMON_PASTEL); tones += Tone.LEMON_PASTEL
        return Palette(names, colors.toIntArray(), tones.toTypedArray())
    }

    data class Stats(
        /** Mean Lab distance from the model's colours to the palette (how far off-palette it drew). */
        val deltaE: Double,
        /** The model's outline width, in its own cell's u. */
        val outlineRawU: Double,
        /** Outline width after the trace growth, in final u. */
        val outlineU: Double,
        /** Final u per cell u. */
        val scale: Double,
        val targetAreaU2: Double,
        /** The art's area in final u² (alpha over 50 %). */
        val areaU2: Double,
        /** The art's box in icon u: left, top, right, bottom. */
        val artLeftU: Double,
        val artTopU: Double,
        val artRightU: Double,
        val artBottomU: Double,
        /** White blobs removed (the model's own gloss and white specks), and specks of other colours. */
        val whiteSpecksRemoved: Int,
        val specksRemoved: Int,
        /** The procedural gloss stroke's length in u (0 = none fitted) and whether its dot fitted. */
        val glossLengthU: Double,
        val glossDot: Boolean,
        val millis: Double,
        /** Milliseconds per step, for the log ("cut 3.1, snap 4.0, …"). */
        val steps: String = "",
    )

    class Result(
        /** [OUT_PX]² ARGB (not premultiplied): the art on its white die-cut, transparent outside. */
        val argb: IntArray,
        /** The die-cut outline as SVG path data in the bitmap's pixel space (0…[OUT_PX]). */
        val cutPath: String,
        val stats: Stats,
    )

    /** The fourth cell held nothing usable. */
    class UnusableDrawing(message: String) : Exception(message)

    /**
     * Post-processes the model's answer [argb] ([width] × [height], ARGB, not premultiplied; the new art in the
     * bottom-right cell) into a sticker drawn in [families] (body first; unknown names fall back as in
     * [StickerPalette.normalize]). [targetAreaU2] is the neighbours' mean art area ([SheetComposer]).
     * Throws [UnusableDrawing] when the cell holds no art.
     */
    fun process(
        argb: IntArray,
        width: Int,
        height: Int,
        families: List<String>,
        targetAreaU2: Double = DEFAULT_TARGET_AREA_U2,
    ): Result {
        val started = System.nanoTime()
        val steps = StringBuilder()
        var lapAt = started
        fun lap(name: String) {
            val now = System.nanoTime()
            if (steps.isNotEmpty()) steps.append(", ")
            steps.append(name).append(' ').append(((now - lapAt) / 1e5).toInt() / 10.0)
            lapAt = now
        }
        require(width == height && width >= 64 && width % 2 == 0 && argb.size == width * height) { "expected a square sheet" }
        val cs = width / 2
        val pxu = cs / BOX_U

        // 1. The fourth cell, cut by its alpha (or keyed on its flat grey when the answer came back opaque).
        val cellRgb = IntArray(cs * cs)
        val cellAlpha = IntArray(cs * cs)
        var transparent = 0
        var tx0 = cs; var ty0 = cs; var tx1 = -1; var ty1 = -1
        for (y in 0 until cs) {
            var src = (y + cs) * width + cs
            var i = y * cs
            for (x in 0 until cs) {
                val p = argb[src++]
                cellRgb[i] = p and 0xFFFFFF
                val a = p ushr 24
                cellAlpha[i++] = a
                if (a < 128) {
                    transparent++
                } else {
                    if (x < tx0) tx0 = x; if (x > tx1) tx1 = x; if (y < ty0) ty0 = y; if (y > ty1) ty1 = y
                }
            }
        }
        // The frame the cut is made in: the opaque pixels' box (plus room for the opening), or the whole cell.
        val fx0: Int; val fy0: Int; val fw: Int; val fh: Int
        val frameArt: BooleanArray
        if (transparent > 0.2 * cs * cs) {
            if (tx1 < 0) throw UnusableDrawing("no artwork in the fourth cell")
            fx0 = max(0, tx0 - 3); fy0 = max(0, ty0 - 3)
            fw = min(cs, tx1 + 4) - fx0; fh = min(cs, ty1 + 4) - fy0
            val opaque = BooleanArray(fw * fh)
            for (y in 0 until fh) for (x in 0 until fw) opaque[y * fw + x] = cellAlpha[(y + fy0) * cs + x + fx0] > 127
            frameArt = keepMainPile(Raster.openCross(opaque, fw, fh, iterations = 2), fw, fh)
        } else {
            fx0 = 0; fy0 = 0; fw = cs; fh = cs
            frameArt = keepMainPile(greyKey(cellRgb, cs, pxu), cs, cs)
        }
        var ax0 = cs; var ay0 = cs; var ax1 = -1; var ay1 = -1; var cellArea = 0
        for (y in 0 until fh) for (x in 0 until fw) {
            if (!frameArt[y * fw + x]) continue
            cellArea++
            if (x < ax0) ax0 = x; if (x > ax1) ax1 = x; if (y < ay0) ay0 = y; if (y > ay1) ay1 = y
        }
        if (cellArea < MIN_ART_U2 * pxu * pxu) throw UnusableDrawing("no artwork in the fourth cell")
        ax0 += fx0; ax1 += fx0; ay0 += fy0; ay1 += fy0

        // Work on the art's box plus a margin (the trace may grow outwards a little).
        val margin = ceil(2.5 * pxu).toInt()
        val cx0 = max(0, ax0 - margin); val cy0 = max(0, ay0 - margin)
        val w = min(cs, ax1 + 1 + margin) - cx0
        val h = min(cs, ay1 + 1 + margin) - cy0
        val n = w * h
        val art = BooleanArray(n)
        val rgb = IntArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val cy = y + cy0
            val cx = x + cx0
            rgb[y * w + x] = cellRgb[cy * cs + cx]
            val fy = cy - fy0
            val fx = cx - fx0
            art[y * w + x] = fx in 0 until fw && fy in 0 until fh && frameArt[fy * fw + fx]
        }

        lap("cut")
        // 2. Palette snap in Lab.
        val pal = palette(families)
        val tones = pal.tones
        val k = IntArray(n) { -1 }
        val snap = ColorSnap(pal)
        var dSum = 0.0
        var dCount = 0
        for (i in 0 until n) {
            if (!art[i]) continue
            k[i] = snap.nearest(rgb[i])
            dSum += snap.lastDistance
            dCount++
        }
        val deltaE = if (dCount == 0) 0.0 else dSum / dCount

        // Thick trace blobs are bands, not outlines: the deep tone of the same family.
        val trace = BooleanArray(n) { k[it] >= 0 && tones[k[it]] == Tone.TRACE }
        if (trace.any { it }) {
            // post4: dilate(open(trace, disk r), disk r) & trace. Two disk dilations by r are one by 2r.
            val r = 1.7 * pxu
            val eroded = Raster.erodeDisk(trace, w, h, r)
            if (eroded.any { it }) {
                val thick = Raster.dilateDisk(eroded, w, h, 2 * r)
                for (i in 0 until n) if (thick[i] && trace[i]) { k[i] += 1; trace[i] = false }
            }
        }
        val outlineRawU = Raster.strokeWidthPx(Raster.openCross(trace, w, h), w, h) / pxu

        lap("snap")
        // 3a. The model's own gloss and white specks: thin (≤ 3.2u) white blobs under 60u² take their surroundings.
        var whiteRemoved = 0
        run {
            val white = BooleanArray(n) { k[it] >= 0 && tones[k[it]] == Tone.WHITE }
            val labels = IntArray(n)
            val count = Raster.label(white, w, h, labels)
            if (count == 0) return@run
            val dIn = Raster.insideDistance(white, w, h)
            val maxD = FloatArray(count + 1)
            val size = IntArray(count + 1)
            for (i in 0 until n) {
                val l = labels[i]
                if (l == 0) continue
                size[l]++
                if (dIn[i] > maxD[l]) maxD[l] = dIn[i]
            }
            val removed = BooleanArray(count + 1) { l -> l > 0 && maxD[l] <= 1.6 * pxu && size[l] <= 60 * pxu * pxu }
            whiteRemoved = removed.count { it }
            if (whiteRemoved == 0) return@run
            for (i in 0 until n) if (removed[labels[i]]) k[i] = -2
            val fillable = BooleanArray(n) { art[it] && k[it] >= 0 && tones[k[it]] != Tone.TRACE }
            fillFromNearest(k, fillable, w, h, hole = -2)
        }

        lap("white")
        // 3b. Specks of any colour but the trace under 2u (thinner than 1.8u, under 6u²).
        var specks = 0
        run {
            val labels = IntArray(n)
            val count = Raster.labelEqual(k, w, h, labels)
            if (count == 0) return@run
            // Distance to the nearest colour change, one transform for every colour (±1 px of a per-colour transform).
            val boundary = BooleanArray(n)
            for (y in 0 until h) {
                var i = y * w
                for (x in 0 until w) {
                    val v = k[i]
                    boundary[i] = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                        k[i - 1] != v || k[i + 1] != v || k[i - w] != v || k[i + w] != v
                    i++
                }
            }
            val d = Raster.outsideDistance(boundary, w, h)
            val maxD = FloatArray(count + 1)
            val size = IntArray(count + 1)
            val tone = IntArray(count + 1)
            for (i in 0 until n) {
                val l = labels[i]
                if (l == 0) continue
                size[l]++
                tone[l] = k[i]
                val di = d[i] + 1f
                if (di > maxD[l]) maxD[l] = di
            }
            val removed = BooleanArray(count + 1) { l ->
                l > 0 && maxD[l] < 0.9 * pxu && size[l] < 6 * pxu * pxu && tones[tone[l]] != Tone.TRACE
            }
            specks = removed.count { it }
            if (specks == 0) return@run
            for (i in 0 until n) if (removed[labels[i]]) k[i] = -3
            val ok = BooleanArray(n) { art[it] && k[it] >= 0 }
            fillFromNearest(k, ok, w, h, hole = -3)
        }

        lap("specks")
        // 4. Scale: the neighbours' mean area, capped by the 39u box.
        var bx0 = w; var by0 = h; var bx1 = -1; var by1 = -1; var areaPx = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (!art[y * w + x]) continue
            areaPx++
            if (x < bx0) bx0 = x; if (x > bx1) bx1 = x; if (y < by0) by0 = y; if (y > by1) by1 = y
        }
        val target = if (targetAreaU2 > 0) targetAreaU2 else DEFAULT_TARGET_AREA_U2
        val sArea = sqrt(target / (areaPx / (pxu * pxu)))
        val sBox = ART_MAX_U / (max(by1 - by0 + 1, bx1 - bx0 + 1) / pxu)
        val scale = min(sArea, sBox)

        // 5. The trace to 2u in final units: grow it (nearest trace colour) where it is thinner.
        val isTrace = BooleanArray(n) { k[it] >= 0 && tones[k[it]] == Tone.TRACE }
        val wNow = Raster.strokeWidthPx(Raster.openCross(isTrace, w, h), w, h) / pxu * scale
        if (wNow < TRACE_U && isTrace.any { it }) {
            val r = (TRACE_U - wNow) / scale * pxu / 2
            val nearest = IntArray(n)
            val d2 = Raster.edt(isTrace, w, h, nearest)
            val r2 = (r * r).toFloat()
            for (i in 0 until n) {
                if (d2[i] > 0f && d2[i] <= r2 && nearest[i] >= 0) {
                    k[i] = k[nearest[i]]
                    art[i] = true
                    isTrace[i] = true
                }
            }
        }
        val outlineU = Raster.strokeWidthPx(Raster.openCross(isTrace, w, h), w, h) / pxu * scale

        lap("trace")
        // 6. Render the snapped art at 2x (8 px per u), base on y 42, centred on x 24.
        bx0 = w; by0 = h; bx1 = -1; by1 = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (!art[y * w + x]) continue
            if (x < bx0) bx0 = x; if (x > bx1) bx1 = x; if (y < by0) by0 = y; if (y > by1) by1 = y
        }
        val bw = bx1 - bx0 + 1
        val bh = by1 - by0 + 1
        val f = scale * RU / pxu
        val ox = (CENTRE_U + 2) * RU - bw * f / 2
        val oy = (BASE_U + 2) * RU - bh * f
        val src = FloatArray(bw * bh * 4)
        for (y in 0 until bh) for (x in 0 until bw) {
            val i = (y + by0) * w + x + bx0
            if (!art[i] || k[i] < 0) continue
            val c = pal.colors[k[i]]
            val s = (y * bw + x) * 4
            src[s] = ((c shr 16) and 0xFF) / 255f
            src[s + 1] = ((c shr 8) and 0xFF) / 255f
            src[s + 2] = (c and 0xFF) / 255f
            src[s + 3] = 1f
        }
        val rn = RENDER_PX * RENDER_PX
        val layer = FloatArray(rn * 4)
        Raster.resampleBox(src, bw, bh, layer, RENDER_PX, RENDER_PX, f, ox, oy)
        // Cell-crop coordinates (pixel centres at integers) -> render px (continuous).
        val toRx = { x: Double -> ox + (x + 0.5 - bx0) * f }
        val toRy = { y: Double -> oy + (y + 0.5 - by0) * f }

        lap("render")
        // 7. The procedural gloss on the dominant fill region.
        val gloss = drawGloss(k, tones, art, w, h, pxu, scale, outlineU, layer, toRx, toRy)

        lap("gloss")
        // 8. The die-cut: the art (alpha over 50 %) dilated 4u, holes filled, in white under the art.
        val artMask = BooleanArray(rn) { layer[it * 4 + 3] > 0.5f }
        var artAreaPx = 0
        var rx0 = RENDER_PX; var ry0 = RENDER_PX; var rx1 = -1; var ry1 = -1
        for (y in 0 until RENDER_PX) for (x in 0 until RENDER_PX) {
            if (!artMask[y * RENDER_PX + x]) continue
            artAreaPx++
            if (x < rx0) rx0 = x; if (x > rx1) rx1 = x; if (y < ry0) ry0 = y; if (y > ry1) ry1 = y
        }
        val dOut = Raster.outsideDistance(artMask, RENDER_PX, RENDER_PX)
        val cutR = CUT_U * RU + 0.5 // from art pixel centres: the art's edge lies half a pixel beyond them
        val cutCore = BooleanArray(rn) { dOut[it] <= cutR }
        val cutFilled = Raster.fillHoles(cutCore, RENDER_PX, RENDER_PX)
        val total = FloatArray(rn)
        for (i in 0 until rn) {
            val white = if (cutFilled[i] && !cutCore[i]) 1f else Raster.coverage(dOut[i].toDouble(), cutR)
            val s = i * 4
            val a = layer[s + 3]
            val under = white * (1f - a)
            layer[s] += under; layer[s + 1] += under; layer[s + 2] += under
            layer[s + 3] = a + under
            total[i] = layer[s + 3]
        }

        lap("diecut")
        // 9. Down to 208 px (2 x 2 box) and the cut path from the 2x alpha.
        val out = IntArray(OUT_PX * OUT_PX)
        for (y in 0 until OUT_PX) for (x in 0 until OUT_PX) {
            var r = 0f; var g = 0f; var b = 0f; var a = 0f
            for (dy in 0 until SS) for (dx in 0 until SS) {
                val s = ((y * SS + dy) * RENDER_PX + x * SS + dx) * 4
                r += layer[s]; g += layer[s + 1]; b += layer[s + 2]; a += layer[s + 3]
            }
            val q = (SS * SS).toFloat()
            r /= q; g /= q; b /= q; a /= q
            out[y * OUT_PX + x] = if (a <= 0f) 0 else packArgb(r / a, g / a, b / a, a)
        }
        val cutPath = cutPath(total)
        lap("path")

        val stats = Stats(
            deltaE = deltaE,
            outlineRawU = outlineRawU,
            outlineU = outlineU,
            scale = scale,
            targetAreaU2 = target,
            areaU2 = artAreaPx / (RU * RU),
            artLeftU = rx0 / RU - 2,
            artTopU = ry0 / RU - 2,
            artRightU = (rx1 + 1) / RU - 2,
            artBottomU = (ry1 + 1) / RU - 2,
            whiteSpecksRemoved = whiteRemoved,
            specksRemoved = specks,
            glossLengthU = gloss.first,
            glossDot = gloss.second,
            millis = (System.nanoTime() - started) / 1e6,
            steps = steps.toString(),
        )
        return Result(out, cutPath, stats)
    }

    /* ---------------- step 1 helpers ---------------- */

    /**
     * Opaque answers: the flat grey (127, 127, 127) flooded from the border is background, and so are enclosed grey
     * holes bigger than 2.4u with a median distance under 10 (a bagel's hole).
     */
    private fun greyKey(rgb: IntArray, cs: Int, pxu: Double): BooleanArray {
        val n = cs * cs
        val dist = IntArray(n) { i ->
            val c = rgb[i]
            max(kotlin.math.abs(((c shr 16) and 0xFF) - 127), max(kotlin.math.abs(((c shr 8) and 0xFF) - 127), kotlin.math.abs((c and 0xFF) - 127)))
        }
        val cand = BooleanArray(n) { dist[it] < 22 }
        val labels = IntArray(n)
        val count = Raster.label(cand, cs, cs, labels)
        val border = HashSet<Int>()
        for (x in 0 until cs) { border += labels[x]; border += labels[(cs - 1) * cs + x] }
        for (y in 0 until cs) { border += labels[y * cs]; border += labels[y * cs + cs - 1] }
        border -= 0
        val members = Array(count + 1) { ArrayList<Int>() }
        for (i in 0 until n) if (labels[i] > 0) members[labels[i]] += dist[i]
        val bgLabel = BooleanArray(count + 1) { l ->
            if (l == 0) false
            else if (l in border) true
            else members[l].size > (2.4 * pxu) * (2.4 * pxu) && members[l].sorted()[members[l].size / 2] < 10
        }
        val art = BooleanArray(n) { !bgLabel[labels[it]] }
        return Raster.openCross(art, cs, cs, iterations = 2)
    }

    /** Keeps the main pile: components over 2 % of the largest one (drops stray specks around it). */
    internal fun keepMainPile(art: BooleanArray, w: Int, h: Int): BooleanArray {
        val labels = IntArray(art.size)
        val count = Raster.label(art, w, h, labels)
        if (count <= 1) return art
        val size = IntArray(count + 1)
        for (l in labels) if (l > 0) size[l]++
        val biggest = size.max()
        return BooleanArray(art.size) { labels[it] > 0 && size[labels[it]] > 0.02 * biggest }
    }

    /** Pixels of [k] equal to [hole] take the value of the nearest pixel where [source] is true. */
    private fun fillFromNearest(k: IntArray, source: BooleanArray, w: Int, h: Int, hole: Int) {
        val nearest = IntArray(k.size)
        Raster.edt(source, w, h, nearest)
        for (i in k.indices) if (k[i] == hole) k[i] = if (nearest[i] >= 0) k[nearest[i]] else -1
    }

    /* ---------------- step 6: gloss ---------------- */

    /**
     * Draws the gloss into [layer] (premultiplied RGBA at render resolution). Candidates are the non-trace fill regions,
     * coloured ones first, largest first (white regions only when they dominate); the first of four that fits a stroke
     * of at least 10u wins. Returns (stroke length in u, dot drawn).
     */
    private fun drawGloss(
        k: IntArray,
        tones: Array<Tone>,
        art: BooleanArray,
        w: Int,
        h: Int,
        pxu: Double,
        scale: Double,
        outlineU: Double,
        layer: FloatArray,
        toRx: (Double) -> Double,
        toRy: (Double) -> Double,
    ): Pair<Double, Boolean> {
        val n = w * h
        val fill = BooleanArray(n) { art[it] && k[it] >= 0 && tones[k[it]] != Tone.TRACE }
        val labels = IntArray(n)
        val count = Raster.label(fill, w, h, labels)
        if (count == 0) return 0.0 to false
        val size = IntArray(count + 1)
        val votes = Array(count + 1) { IntArray(tones.size) }
        val box = Array(count + 1) { intArrayOf(w, h, -1, -1) }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val l = labels[i]
            if (l == 0) continue
            size[l]++
            votes[l][k[i]]++
            val b = box[l]
            if (x < b[0]) b[0] = x; if (y < b[1]) b[1] = y; if (x > b[2]) b[2] = x; if (y > b[3]) b[3] = y
        }
        val white = BooleanArray(count + 1) { l -> l > 0 && tones[votes[l].indices.maxBy { votes[l][it] }] == Tone.WHITE }
        val maxColoured = (1..count).filter { !white[it] }.maxOfOrNull { size[it] } ?: 0
        val order = (1..count).sortedWith(compareBy({ if (white[it] && size[it] < 2.5 * maxColoured) 1 else 0 }, { -size[it] }))
        val u = pxu / scale // cell px per final u
        for (l in order.take(4)) {
            if (size[l] < 4) continue
            val glossed = glossRegion(labels, l, box[l], w, h, u, outlineU, layer, toRx, toRy)
            if (glossed != null) return glossed
        }
        return 0.0 to false
    }

    private fun glossRegion(
        labels: IntArray,
        label: Int,
        box: IntArray,
        w: Int,
        h: Int,
        u: Double,
        outlineU: Double,
        layer: FloatArray,
        toRx: (Double) -> Double,
        toRy: (Double) -> Double,
    ): Pair<Double, Boolean>? {
        // A sub-grid around the region with room for the half-outline dilation.
        val (x0, y0, x1, y1) = box
        val pad = ceil(outlineU / 2 * u).toInt() + 3
        val gx0 = x0 - pad; val gy0 = y0 - pad
        val gw = x1 - x0 + 1 + 2 * pad
        val gh = y1 - y0 + 1 + 2 * pad
        val region = BooleanArray(gw * gh)
        for (gy in 0 until gh) {
            val y = gy + gy0
            if (y !in 0 until h) continue
            for (gx in 0 until gw) {
                val x = gx + gx0
                if (x in 0 until w && labels[y * w + x] == label) region[gy * gw + gx] = true
            }
        }
        // The region grown to the trace's centre line, as post4's poly.buffer(outline / 2).
        val shape = Raster.dilateDisk(region, gw, gh, outlineU / 2 * u)
        val dIn = Raster.insideDistance(shape, gw, gh)
        for (inset in doubleArrayOf(GLOSS_W_U / 2 + 1.7, 2.6, 2.2)) {
            // gloss.py arc(): the contour inset by `inset`, its corners rounded by an opening of 1.6u.
            val e = ((inset + 1.6) * u).toFloat()
            val inner = BooleanArray(gw * gh) { dIn[it] > e }
            if (!inner.any { it }) continue
            val rounded = largestComponent(Raster.dilateDisk(inner, gw, gh, 1.6 * u), gw, gh)
            val field = FloatArray(gw * gh) { if (rounded[it]) 1f else 0f }
            val ring = Raster.contours(field, gw, gh, 0.5f).minByOrNull { it.signedArea } ?: continue
            if (ring.signedArea >= 0) continue
            for ((a0, a1) in GLOSS_RANGES) {
                val run = arcRun(ring, a0, a1, step = 0.2 * u) ?: continue
                val lengthU = run.length / u
                if (lengthU < GLOSS_MIN_U) continue
                // The stroke (render px), then the dot one step past its lower end along the same ring.
                val pts = run.points.map { (x, y) -> toRx(x + gx0) to toRy(y + gy0) }
                val ru = RU
                strokePolyline(layer, simplifyOpen(pts, 0.1 * ru), GLOSS_W_U / 2 * ru)
                val past = (GLOSS_W_U / 2 + 1.6 + GLOSS_DOT_R_U) * u
                val (qx, qy) = run.ringPoint(run.startPos - run.direction * past)
                val qi = qy.roundToInt() * gw + qx.roundToInt()
                val dot = qx.roundToInt() in 0 until gw && qy.roundToInt() in 0 until gh && dIn[qi] > 1.6 * u
                if (dot) strokePolyline(layer, listOf(toRx(qx + gx0) to toRy(qy + gy0)), GLOSS_DOT_R_U * ru)
                return lengthU to dot
            }
        }
        return null
    }

    private fun largestComponent(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val labels = IntArray(mask.size)
        val count = Raster.label(mask, w, h, labels)
        if (count <= 1) return mask
        val size = IntArray(count + 1)
        for (l in labels) if (l > 0) size[l]++
        val best = (1..count).maxBy { size[it] }
        return BooleanArray(mask.size) { labels[it] == best }
    }

    /** A run of a closed ring: its sampled points, length, where it starts on the ring and which way it runs. */
    private class Run(
        val points: List<Pair<Double, Double>>,
        val length: Double,
        val startPos: Double,
        val direction: Int,
        private val ringX: DoubleArray,
        private val ringY: DoubleArray,
        private val step: Double,
    ) {
        /** The ring point at arc position [pos] (wraps). */
        fun ringPoint(pos: Double): Pair<Double, Double> {
            val total = ringX.size * step
            var p = pos % total
            if (p < 0) p += total
            val i = (p / step).toInt().coerceIn(0, ringX.size - 1)
            val j = (i + 1) % ringX.size
            val t = p / step - i
            return (ringX[i] + (ringX[j] - ringX[i]) * t) to (ringY[i] + (ringY[j] - ringY[i]) * t)
        }
    }

    /**
     * gloss.py `arc()`: samples the ring every [step] px, keeps the longest run whose bearing from the ring's box centre
     * (in box-normalised space, so long and tall shapes get the same share as round ones) lies in [a0, a1], and orients
     * it from the lower bearing to the higher one. Null when nothing qualifies.
     */
    private fun arcRun(ring: Raster.Loop, a0: Double, a1: Double, step: Double): Run? {
        // Resample the closed ring at a constant step.
        val m = ring.size
        val seg = DoubleArray(m)
        var total = 0.0
        for (i in 0 until m) {
            val j = (i + 1) % m
            val dx = (ring.xs[j] - ring.xs[i]).toDouble()
            val dy = (ring.ys[j] - ring.ys[i]).toDouble()
            seg[i] = sqrt(dx * dx + dy * dy)
            total += seg[i]
        }
        val count = max(8, (total / step).toInt())
        val st = total / count
        val rx = DoubleArray(count)
        val ry = DoubleArray(count)
        var i = 0
        var acc = 0.0
        for (s in 0 until count) {
            val target = s * st
            while (i < m - 1 && acc + seg[i] < target) { acc += seg[i]; i++ }
            val j = (i + 1) % m
            val t = if (seg[i] == 0.0) 0.0 else ((target - acc) / seg[i]).coerceIn(0.0, 1.0)
            rx[s] = ring.xs[i] + (ring.xs[j] - ring.xs[i]) * t
            ry[s] = ring.ys[i] + (ring.ys[j] - ring.ys[i]) * t
        }
        val cx = (rx.min() + rx.max()) / 2
        val cy = (ry.min() + ry.max()) / 2
        val hw = max((rx.max() - rx.min()) / 2, 0.1)
        val hh = max((ry.max() - ry.min()) / 2, 0.1)
        fun bearing(s: Int): Double {
            val a = atan2((ry[s] - cy) / hh, (rx[s] - cx) / hw) * 180 / PI
            return if (a < 0) a + 360 else a
        }
        val ok = BooleanArray(count) { bearing(it) in a0..a1 }
        if (!ok.any { it }) return null
        // Longest circular run of ok samples.
        var bestStart = -1
        var bestLen = 0
        if (ok.all { it }) {
            bestStart = 0; bestLen = count
        } else {
            val firstBad = ok.indexOfFirst { !it }
            var s = 0
            while (s < count) {
                val idx = (firstBad + s) % count
                if (!ok[idx]) { s++; continue }
                var len = 0
                while (s + len < count && ok[(firstBad + s + len) % count]) len++
                if (len > bestLen) { bestLen = len; bestStart = idx }
                s += len
            }
        }
        if (bestLen < 3) return null
        var idx = List(bestLen) { (bestStart + it) % count }
        var direction = 1
        if (bearing(idx.first()) > bearing(idx.last())) {
            idx = idx.reversed()
            direction = -1
        }
        val points = idx.map { rx[it] to ry[it] }
        return Run(points, (bestLen - 1) * st, idx.first() * st, direction, rx, ry, st)
    }

    /** Ramer-Douglas-Peucker for an open polyline. */
    private fun simplifyOpen(pts: List<Pair<Double, Double>>, epsilon: Double): List<Pair<Double, Double>> {
        if (pts.size < 3) return pts
        val keep = BooleanArray(pts.size)
        keep[0] = true; keep[pts.size - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, pts.size - 1))
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var worst = -1.0
            var at = -1
            for (i in s + 1 until e) {
                val d = Raster.segmentDistance(pts[i].first, pts[i].second, pts[s].first, pts[s].second, pts[e].first, pts[e].second)
                if (d > worst) { worst = d; at = i }
            }
            if (at >= 0 && worst > epsilon) {
                keep[at] = true
                stack.addLast(intArrayOf(s, at)); stack.addLast(intArrayOf(at, e))
            }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    /** White, anti-aliased: every pixel within [radius] px of the polyline (round caps and joins; one point = a dot). */
    private fun strokePolyline(layer: FloatArray, pts: List<Pair<Double, Double>>, radius: Double) {
        if (pts.isEmpty()) return
        val minX = max(0, (pts.minOf { it.first } - radius - 2).toInt())
        val maxX = min(RENDER_PX - 1, (pts.maxOf { it.first } + radius + 2).toInt())
        val minY = max(0, (pts.minOf { it.second } - radius - 2).toInt())
        val maxY = min(RENDER_PX - 1, (pts.maxOf { it.second } + radius + 2).toInt())
        // Coverage per pixel of the box: the max over the segments, each visiting only the pixels near it.
        val bw = maxX - minX + 1
        val cover = FloatArray(bw * (maxY - minY + 1))
        val reach = radius + 1.0
        val segments = if (pts.size == 1) listOf(pts[0] to pts[0]) else pts.zipWithNext()
        for ((a, b) in segments) {
            val sx0 = max(minX, (min(a.first, b.first) - reach).toInt())
            val sx1 = min(maxX, (max(a.first, b.first) + reach).toInt())
            val sy0 = max(minY, (min(a.second, b.second) - reach).toInt())
            val sy1 = min(maxY, (max(a.second, b.second) + reach).toInt())
            for (y in sy0..sy1) for (x in sx0..sx1) {
                val d = Raster.segmentDistance(x + 0.5, y + 0.5, a.first, a.second, b.first, b.second)
                val c = Raster.coverage(d, radius)
                val j = (y - minY) * bw + x - minX
                if (c > cover[j]) cover[j] = c
            }
        }
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val c = cover[(y - minY) * bw + x - minX]
                if (c <= 0f) continue
                val i = (y * RENDER_PX + x) * 4
                val keep = 1f - c
                layer[i] = layer[i] * keep + c
                layer[i + 1] = layer[i + 1] * keep + c
                layer[i + 2] = layer[i + 2] * keep + c
                layer[i + 3] = layer[i + 3] * keep + c
            }
        }
    }

    /* ---------------- step 7: the cut path ---------------- */

    /** Outer contours of the sticker's alpha at 50 %, simplified, as SVG path data in output pixels. */
    private fun cutPath(alpha: FloatArray): String {
        val loops = Raster.contours(alpha, RENDER_PX, RENDER_PX, 0.5f)
            .filter { it.signedArea < -(RU * RU) } // outer boundaries over 1u²
            .sortedBy { it.signedArea }
        val eps = CUT_SIMPLIFY_U * RU
        val sb = StringBuilder()
        for (loop in loops) {
            val s = Raster.simplifyClosed(loop, eps)
            for (i in 0 until s.size) {
                sb.append(if (i == 0) 'M' else 'L')
                sb.append(fmt((s.xs[i] + 0.5) / SS)).append(' ').append(fmt((s.ys[i] + 0.5) / SS))
            }
            sb.append('Z')
        }
        return sb.toString()
    }

    private fun fmt(v: Double): String {
        val r = (v * 10).roundToInt()
        val whole = r / 10
        val frac = kotlin.math.abs(r % 10)
        return if (frac == 0) whole.toString() else (if (r < 0 && whole == 0) "-0" else whole.toString()) + "." + frac
    }

    private fun packArgb(r: Float, g: Float, b: Float, a: Float): Int {
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (ch(a) shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Nearest palette entry per sRGB colour, memoised (a drawing has tens of thousands of distinct colours at most). */
    private class ColorSnap(private val pal: Palette) {
        private val labs = Array(pal.size) { DoubleArray(3).also { lab -> Raster.lab(pal.colors[it], lab) } }
        private val tmp = DoubleArray(3)
        private var keys = IntArray(1 shl 15) { -1 }
        private var values = IntArray(1 shl 15)
        private var dists = FloatArray(1 shl 15)
        private var used = 0
        var lastDistance: Double = 0.0
            private set

        fun nearest(rgb: Int): Int {
            var slot = hash(rgb) and (keys.size - 1)
            while (true) {
                val key = keys[slot]
                if (key == rgb) {
                    lastDistance = dists[slot].toDouble()
                    return values[slot]
                }
                if (key == -1) break
                slot = (slot + 1) and (keys.size - 1)
            }
            Raster.lab(rgb, tmp)
            var best = 0
            var bestD = Double.MAX_VALUE
            for (i in labs.indices) {
                val dl = tmp[0] - labs[i][0]; val da = tmp[1] - labs[i][1]; val db = tmp[2] - labs[i][2]
                val d = dl * dl + da * da + db * db
                if (d < bestD) { bestD = d; best = i }
            }
            lastDistance = sqrt(bestD)
            keys[slot] = rgb; values[slot] = best; dists[slot] = lastDistance.toFloat()
            if (++used * 10 > keys.size * 7) grow()
            return best
        }

        private fun grow() {
            val oldK = keys; val oldV = values; val oldD = dists
            keys = IntArray(oldK.size * 2) { -1 }; values = IntArray(oldK.size * 2); dists = FloatArray(oldK.size * 2)
            for (i in oldK.indices) {
                if (oldK[i] == -1) continue
                var slot = hash(oldK[i]) and (keys.size - 1)
                while (keys[slot] != -1) slot = (slot + 1) and (keys.size - 1)
                keys[slot] = oldK[i]; values[slot] = oldV[i]; dists[slot] = oldD[i]
            }
        }

        private fun hash(v: Int): Int {
            var x = v * -0x61c88647
            x = x xor (x ushr 16)
            return x
        }
    }
}

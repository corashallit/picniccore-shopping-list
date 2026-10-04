package earth.cora.picnic.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsNode
import kotlin.math.abs
import kotlin.math.hypot

/*
 * Pixel and layout probes for the "nothing clips" tests (§2.7 "Puffy text never clips", §15 font scale 2.0): where ink
 * landed in a captured bitmap, and whether an ancestor's clip cuts into a node's box.
 */

/** The bounding box (px, inclusive-exclusive) of the pixels of [bitmap] that [isInk], or null when there are none. */
internal fun inkBox(bitmap: Bitmap, region: android.graphics.Rect? = null, isInk: (Int) -> Boolean): android.graphics.Rect? {
    val r = region ?: android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
    var left = Int.MAX_VALUE
    var top = Int.MAX_VALUE
    var right = Int.MIN_VALUE
    var bottom = Int.MIN_VALUE
    val row = IntArray(r.width())
    for (y in r.top until r.bottom) {
        bitmap.getPixels(row, 0, r.width(), r.left, y, r.width(), 1)
        for (i in row.indices) {
            if (isInk(row[i])) {
                val x = r.left + i
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    return if (right < left) null else android.graphics.Rect(left, top, right + 1, bottom + 1)
}

/** True when [argb] is within [tolerance] of [rgb] on every channel (alpha ignored). */
internal fun near(argb: Int, rgb: Int, tolerance: Int = 24): Boolean =
    abs(((argb shr 16) and 0xFF) - ((rgb shr 16) and 0xFF)) <= tolerance &&
        abs(((argb shr 8) and 0xFF) - ((rgb shr 8) and 0xFF)) <= tolerance &&
        abs((argb and 0xFF) - (rgb and 0xFF)) <= tolerance

/**
 * The node's box in the root with every ancestor's clip applied, and without: equal (to 0.5 px) when no clip
 * (`Modifier.clip`, `clipToBounds`, a clipping graphics layer, a lazy list's viewport) cuts into it.
 */
internal fun SemanticsNode.clippedAndWhole(): Pair<Rect, Rect> {
    val coords = layoutInfo.coordinates
    var root = coords
    while (root.parentLayoutCoordinates != null) root = root.parentLayoutCoordinates!!
    return coords.boundsInRoot() to root.localBoundingBoxOf(coords, clipBounds = false)
}

/** True when no ancestor's clip cuts into this node's box. */
internal fun SemanticsNode.isWhole(): Boolean {
    val (clipped, whole) = clippedAndWhole()
    return abs(clipped.left - whole.left) <= 0.5f && abs(clipped.top - whole.top) <= 0.5f &&
        abs(clipped.right - whole.right) <= 0.5f && abs(clipped.bottom - whole.bottom) <= 0.5f
}

/**
 * The least distance (px) from a number circle's numeral ink to the inside of its ring, for the circle at [b] in [img]
 * with a ring [ringDp] wide at [density] px per dp: over every pixel inside the ring's inner edge (half a px in, clear of
 * the ring's own anti-aliasing) at least half-way from white to [trace]. Null when there is no ink. Item 337 asks for
 * 0.75 dp (half a px allowed for anti-aliasing).
 */
internal fun numeralRingGap(img: Bitmap, b: Rect, density: Float, trace: Int, ringDp: Float = 2f): Float? {
    val cx = b.center.x
    val cy = b.center.y
    val inner = b.width / 2f - ringDp * density
    val lum = { c: Int -> (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3f }
    val threshold = (255f + lum(trace)) / 2f
    val left = b.left.toInt()
    val top = b.top.toInt()
    val w = b.width.toInt()
    val h = b.height.toInt()
    val px = IntArray(w * h)
    img.getPixels(px, 0, w, left, top, w, h)
    var gap: Float? = null
    for (y in 0 until h) for (x in 0 until w) {
        val dist = hypot(left + x + 0.5f - cx, top + y + 0.5f - cy)
        if (dist >= inner - 0.5f) continue
        if (lum(px[y * w + x]) <= threshold) gap = minOf(gap ?: Float.MAX_VALUE, inner - dist)
    }
    return gap
}

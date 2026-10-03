package earth.cora.picnic.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsNode
import kotlin.math.abs

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

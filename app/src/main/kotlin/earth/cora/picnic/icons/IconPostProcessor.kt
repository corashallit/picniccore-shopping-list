package earth.cora.picnic.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns a raw drawn icon (1024 px webp with alpha) into the stored 192 px WebP. */
fun interface IconPostProcessor {
    /** Throws [IOException] if [raw] can't be decoded or the result can't be encoded. CPU-bound: call off main. */
    fun process(raw: ByteArray): ByteArray
}

/**
 * docs/ARCHITECTURE.md §8.3: decode -> trim transparent bounds -> fit in [contentPx] -> centre on a
 * [canvasPx] square transparent canvas -> WEBP_LOSSY q[quality] (API 30+) / WEBP (API 26-29).
 * Result: ~5-7 KB per icon with alpha intact.
 */
class BitmapIconPostProcessor(
    private val canvasPx: Int = 192,
    private val contentPx: Int = 176,
    private val quality: Int = 82,
) : IconPostProcessor {
    override fun process(raw: ByteArray): ByteArray {
        val src = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IOException("Undecodable icon image")
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val bounds = IconGeometry.opaqueBounds(pixels, src.width, src.height)
            ?: throw IOException("Icon image is fully transparent")
        val cropped = Bitmap.createBitmap(src, bounds.left, bounds.top, bounds.width, bounds.height)
        val (w, h) = IconGeometry.fitInside(bounds.width, bounds.height, contentPx)
        val scaled = downscale(cropped, w, h)
        val out = createBitmap(canvasPx, canvasPx)
        Canvas(out).drawBitmap(
            scaled,
            ((canvasPx - w) / 2).toFloat(),
            ((canvasPx - h) / 2).toFloat(),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return ByteArrayOutputStream().use { stream ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            if (!out.compress(format, quality, stream)) throw IOException("WebP encode failed")
            stream.toByteArray()
        }
    }

    /** Halves with bilinear filtering until within 2x of the target, then one final scale: avoids aliasing. */
    private fun downscale(src: Bitmap, w: Int, h: Int): Bitmap {
        var current = src
        while (current.width / 2 >= w * 2 && current.height / 2 >= h * 2) {
            current = current.scale(current.width / 2, current.height / 2)
        }
        return if (current.width == w && current.height == h) current else current.scale(w, h)
    }
}

/** Pure geometry for icon trimming (unit-tested on the JVM). */
internal object IconGeometry {
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * Bounding box (right/bottom exclusive) of pixels whose alpha is above [alphaThreshold] in ARGB
     * [pixels]; null if every pixel is (nearly) transparent. The threshold ignores faint halos.
     */
    fun opaqueBounds(pixels: IntArray, width: Int, height: Int, alphaThreshold: Int = 8): Bounds? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((pixels[row + x] ushr 24) > alphaThreshold) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else Bounds(left, top, right + 1, bottom + 1)
    }

    /** Scales (w, h) to fit inside a [max] square, keeping the aspect ratio (up or down); each side >= 1. */
    fun fitInside(w: Int, h: Int, max: Int): Pair<Int, Int> {
        require(w > 0 && h > 0 && max > 0)
        val scale = max.toFloat() / max(w, h)
        return max(1, (w * scale).roundToInt()).coerceAtMost(max) to max(1, (h * scale).roundToInt()).coerceAtMost(max)
    }
}

package earth.cora.picnic.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException

/** A live sticker ready to store (docs/DESIGN_STICKER.md §18.3 step 5): the 208 px WebP and its die-cut path. */
class ProcessedSticker(
    /** [IconPostProcessorV4.OUT_PX]² lossless WebP: the art on its white die-cut, transparent around it, no shadow. */
    val webp: ByteArray,
    /** The die-cut outline, SVG path data in the bitmap's pixel space (stored as `<id>.cut`). */
    val cut: String,
    val stats: IconPostProcessorV4.Stats? = null,
)

/** Turns the model's raw answer into a stored sticker. CPU-bound (< 100 ms): call it off the main thread. */
fun interface LiveStickerProcessor {
    /**
     * [raw] is the image the API returned for the neighbour sheet (1024 px, the new art in the bottom-right cell),
     * [families] the item's two sticker families, [targetAreaU2] the neighbours' mean art area ([LiveSheet]).
     * Throws [IOException] when [raw] can't be decoded or holds no usable art.
     */
    fun process(raw: ByteArray, families: List<String>, targetAreaU2: Double): ProcessedSticker
}

/** [LiveStickerProcessor] on the device: decode, [IconPostProcessorV4], encode as lossless WebP. */
class BitmapLiveStickerProcessor : LiveStickerProcessor {
    override fun process(raw: ByteArray, families: List<String>, targetAreaU2: Double): ProcessedSticker {
        val src = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IOException("undecodable sticker image")
        var w = src.width
        var h = src.height
        val square = if (w == h && w % 2 == 0 && w >= 64) {
            src
        } else {
            // The API always answers 1024 x 1024; anything else is scaled onto that grid so the cells line up.
            w = SheetComposer.SHEET_PX; h = SheetComposer.SHEET_PX
            Bitmap.createScaledBitmap(src, w, h, true)
        }
        // getPixels returns non-premultiplied ARGB, which is what the post-processor reads.
        val argb = IntArray(w * h)
        square.getPixels(argb, 0, w, 0, 0, w, h)
        val result = try {
            IconPostProcessorV4.process(argb, w, h, families, targetAreaU2)
        } catch (e: IconPostProcessorV4.UnusableDrawing) {
            throw IOException(e.message, e)
        }
        return ProcessedSticker(encode(result.argb), result.cutPath, result.stats)
    }

    companion object {
        /** Lossless: flat palette art compresses to a few KB and keeps its edges exact. */
        fun encode(argb: IntArray): ByteArray {
            val px = IconPostProcessorV4.OUT_PX
            val bitmap = Bitmap.createBitmap(argb, px, px, Bitmap.Config.ARGB_8888)
            return ByteArrayOutputStream().use { out ->
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out)
                }
                if (!ok) throw IOException("WebP encode failed")
                out.toByteArray()
            }
        }
    }
}

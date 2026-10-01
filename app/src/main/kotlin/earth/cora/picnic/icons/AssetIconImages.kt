package earth.cora.picnic.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.platform.PicnicLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * The bundled icon library: assets/icons/<key>.webp (192 px transparent WebP, one per catalog key,
 * produced by the art pass). Files starting with '_' (the style reference) are not icons.
 * The key set is listed once, lazily (it is I/O).
 */
class BundledIcons(private val context: Context) {
    val keys: Set<String> by lazy {
        try {
            context.assets.list(DIR).orEmpty()
                .filter { it.endsWith(EXT) && !it.startsWith("_") }
                .map { it.removeSuffix(EXT) }
                .toSet()
        } catch (e: IOException) {
            PicnicLog.w("couldn't list bundled icons", e)
            emptySet()
        }
    }

    fun contains(key: String): Boolean = key in keys

    fun open(key: String) = context.assets.open("$DIR/$key$EXT")

    /** The live-drawing style sheet (assets/icons/_style_reference.png), or null if not bundled. */
    fun styleReference(): ByteArray? = try {
        context.assets.open("$DIR/$STYLE_REFERENCE").use { it.readBytes() }
    } catch (e: IOException) {
        null
    }

    companion object {
        const val DIR: String = "icons"
        const val EXT: String = ".webp"
        const val STYLE_REFERENCE: String = "_style_reference.png"
    }
}

/**
 * [IconImages] for the app (provided to the theme through LocalIconImages). Render order for a ref
 * (docs/ARCHITECTURE.md §8.5): bespoke file (filesDir/icons/custom/<id>.webp) -> bundled <key>.webp
 * -> bundled category fallback -> null (the theme draws placeholder art). Decoded bitmaps live in a
 * byte-bounded LRU; misses are remembered so placeholder rows don't hit the disk on every scroll.
 */
class AssetIconImages(
    private val bundled: BundledIcons,
    private val catalog: IconCatalog,
    private val customDir: File,
    maxBytes: Int = 16 * 1024 * 1024,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : IconImages {
    private val memory = object : LruCache<IconRef, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: IconRef, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val misses: MutableSet<IconRef> = ConcurrentHashMap.newKeySet()

    override fun cached(ref: IconRef): ImageBitmap? = memory[ref]

    override suspend fun load(ref: IconRef): ImageBitmap? {
        memory[ref]?.let { return it }
        if (ref in misses) return null
        val bitmap = withContext(io) { resolve(ref) }
        if (bitmap != null) memory.put(ref, bitmap) else misses.add(ref)
        return bitmap
    }

    /** Drop everything cached for a bespoke icon id (after a redraw deleted its file). */
    fun evictCustom(customIconId: String) {
        memory.snapshot().keys.filter { it.customIconId == customIconId }.forEach { memory.remove(it) }
        misses.removeAll { it.customIconId == customIconId }
    }

    private fun resolve(ref: IconRef): ImageBitmap? {
        ref.customIconId?.let { id -> decodeFile(File(customDir, "$id.webp"))?.let { return it } }
        decodeAsset(ref.key)?.let { return it }
        val fallback = catalog.fallbackFor(ref.key)
        return if (fallback != ref.key) decodeAsset(fallback) else null
    }

    private fun decodeFile(file: File): ImageBitmap? {
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath, options())?.asImageBitmap()
    }

    private fun decodeAsset(key: String): ImageBitmap? {
        if (!bundled.contains(key)) return null
        return try {
            bundled.open(key).use { BitmapFactory.decodeStream(it, null, options()) }?.asImageBitmap()
        } catch (e: IOException) {
            null
        }
    }

    private companion object {
        // Options objects are written to during decoding: one per call (loads run concurrently).
        fun options() = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    }
}

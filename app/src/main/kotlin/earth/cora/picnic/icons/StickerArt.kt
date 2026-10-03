package earth.cora.picnic.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.PathParser
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.ui.theme.gen.StickerManifest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Sticker art for `ItemIcon` and the icon repository (docs/DESIGN_STICKER.md §3.2, §3.3, §19.2; replaces
 * [IconImages]). Library stickers are vector drawables with named groups; live-drawn stickers are a bitmap plus their
 * die-cut path. Both go through the one render path: the shadow is drawn at render time from the sticker's alpha
 * (the bitmap already carries its white die-cut, exactly as the vector's `cut` group does), so a live sticker and a
 * library sticker look alike in every theme variant.
 */
interface StickerArt {
    /** A library sticker: its drawable and named groups (§3.2), or null when the key has no art yet. */
    fun vector(key: String): StickerVector?

    /** Memory only, composition-safe. */
    fun cached(customIconId: String): LiveSticker?

    /** Bitmap + cut path (null cut = a legacy icon, §3.3 step 5). Null when the file is gone. Main-safe. */
    suspend fun load(customIconId: String): LiveSticker?

    /** True when [key] has library art (from [StickerManifest]). */
    fun isBundled(key: String): Boolean

    /** No art at all: previews and tests that draw the placeholder. */
    object None : StickerArt {
        override fun vector(key: String): StickerVector? = null
        override fun cached(customIconId: String): LiveSticker? = null
        override suspend fun load(customIconId: String): LiveSticker? = null
        override fun isBundled(key: String): Boolean = false
    }
}

/** A library sticker: `sk_i_<key>.xml` and the named groups it carries (`cut`, `fills`, `lines`, `gloss`, `dominant`). */
data class StickerVector(@DrawableRes val id: Int, val groups: Set<String>)

/**
 * A live-drawn sticker. v4 ([legacy] false): [bitmap] is 208 px (4 px per u of the 52u box, so 52 dp is crisp at
 * xxxhdpi), the art on its 4u white die-cut, transparent around it and without a shadow; [cut] is the die-cut outline
 * in the bitmap's pixel space (scale by `size / bitmap.width`), for the ghost variant. Legacy ([legacy] true, [cut]
 * null): a 192 px icon drawn before v4 with its outline, border and shadow baked in; drawn as it is.
 */
data class LiveSticker(val bitmap: ImageBitmap, val cut: Path?, val legacy: Boolean)

/**
 * [StickerArt] on the device: vectors from [StickerManifest], live stickers from `filesDir/icons/custom/<id>.webp`
 * plus `<id>.cut` (written by [BespokeIconCache]). Decoded stickers live in a byte-bounded LRU; misses are
 * remembered so a row whose file vanished doesn't hit the disk on every scroll.
 */
class AppStickerArt(
    private val customDir: File,
    maxBytes: Int = 8 * 1024 * 1024,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : StickerArt {
    private val memory = object : LruCache<String, LiveSticker>(maxBytes) {
        override fun sizeOf(key: String, value: LiveSticker): Int = value.bitmap.width * value.bitmap.height * 4
    }
    private val misses: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun vector(key: String): StickerVector? =
        StickerManifest.entry(key)?.let { StickerVector(it.drawable, StickerManifest.GROUPS) }

    override fun cached(customIconId: String): LiveSticker? = memory[customIconId]

    override suspend fun load(customIconId: String): LiveSticker? {
        memory[customIconId]?.let { return it }
        if (customIconId in misses) return null
        val sticker = withContext(io) { read(customIconId) }
        if (sticker != null) memory.put(customIconId, sticker) else misses.add(customIconId)
        return sticker
    }

    override fun isBundled(key: String): Boolean = key != StickerManifest.CAPSULE && StickerManifest.isBundled(key)

    /** Drops a live sticker from memory (after a redraw replaced or deleted it). */
    fun evict(customIconId: String) {
        memory.remove(customIconId)
        misses.remove(customIconId)
    }

    private fun read(id: String): LiveSticker? {
        val file = File(customDir, "$id.webp")
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val cutFile = File(customDir, "$id.cut")
        val cut = if (cutFile.isFile) {
            try {
                parseCut(cutFile.readText(Charsets.UTF_8))
            } catch (e: IOException) {
                null
            } catch (e: IllegalArgumentException) {
                PicnicLog.w("unreadable cut path for $id", e)
                null
            }
        } else {
            null
        }
        // A v4 sticker whose cut can't be read still renders (its alpha is the silhouette); only the ghost loses it.
        return LiveSticker(bitmap.asImageBitmap(), cut, legacy = !cutFile.isFile)
    }

    companion object {
        /** SVG path data ("M x y L x y … Z", as [IconPostProcessorV4] writes it) as a Compose [Path]. */
        fun parseCut(d: String): Path = PathParser().parsePathString(d).toPath()
    }
}

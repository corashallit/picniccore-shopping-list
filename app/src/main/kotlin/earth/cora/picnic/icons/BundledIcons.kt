package earth.cora.picnic.icons

import android.content.Context
import java.io.IOException

/**
 * What the app bundles for live drawing under assets/icons/ (live stickers v4, docs/DESIGN_STICKER.md §18.3). Library
 * stickers are vector drawables (`res/drawable/sk_i_<key>.xml`, StickerManifest), so the only asset left is the mask.
 */
class BundledIcons(private val context: Context) {
    /**
     * The live-drawing mask (assets/icons/mask.png): 1024 px, opaque except the empty bottom-right cell of the
     * neighbour sheet, so the model may only draw there. Null if not bundled.
     */
    fun mask(): ByteArray? = try {
        context.assets.open("$DIR/$MASK").use { it.readBytes() }
    } catch (e: IOException) {
        null
    }

    companion object {
        const val DIR: String = "icons"
        const val MASK: String = "mask.png"
    }
}

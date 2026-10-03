package earth.cora.picnic.ui.theme

import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.icons.AppStickerArt
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/**
 * Real live stickers for ItemIcon goldens, read through the app's own [AppStickerArt] (docs/DESIGN_STICKER.md §3.3):
 *
 * - [V4]: the three stickers of the live end-to-end run (`icons/live/<item>/sticker.webp` and its `sticker.cut`, as
 *   the v4 processor wrote them; LiveStickerFixturesTest keeps them current), ids `live-<item>`;
 * - [LEGACY]: a direction-A icon drawn live before v4 (`icons/legacy/gochujang.webp`: the v1 pipeline's 192 px
 *   raster with its ink outline, white border and lavender shadow baked in, and no cut file), id `legacy-<item>`.
 *
 * [art] copies them into a fresh folder named as `filesDir/icons/custom/` names them and decodes every one up front,
 * so `StickerArt.cached` answers on the first frame and no golden waits on a background decode.
 */
internal object LiveStickerFixtures {
    /** v4 items and the catalog key each one's items carry (their approximate icon). */
    val V4: List<Pair<String, String>> = listOf(
        "dragon_fruit" to "fruit_generic",
        "gochujang" to "condiments_generic",
        "mochi" to "snacks_generic",
    )

    const val LEGACY: String = "gochujang"

    fun v4Ref(item: String): IconRef = IconRef(V4.first { it.first == item }.second, customIconId = "live-$item")

    fun legacyRef(): IconRef = IconRef("condiments_generic", customIconId = "legacy-$LEGACY")

    fun art(): AppStickerArt {
        val dir = Files.createTempDirectory("live-stickers").toFile().apply { deleteOnExit() }
        for ((item, _) in V4) {
            copy("icons/live/$item/sticker.webp", File(dir, "live-$item.webp"))
            copy("icons/live/$item/sticker.cut", File(dir, "live-$item.cut"))
        }
        copy("icons/legacy/$LEGACY.webp", File(dir, "legacy-$LEGACY.webp"))
        val art = AppStickerArt(dir)
        runBlocking {
            for ((item, _) in V4) checkNotNull(art.load("live-$item")) { "live-$item did not decode" }
            checkNotNull(art.load("legacy-$LEGACY")) { "legacy-$LEGACY did not decode" }
        }
        return art
    }

    private fun copy(resource: String, to: File) {
        val url = requireNotNull(javaClass.classLoader!!.getResource(resource)) { "missing test resource $resource" }
        url.openStream().use { input -> to.outputStream().use { input.copyTo(it) } }
        to.deleteOnExit()
    }
}

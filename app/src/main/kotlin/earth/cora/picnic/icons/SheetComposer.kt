package earth.cora.picnic.icons

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.catalog.IconEntry
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.ui.theme.gen.StickerManifest
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Which three library stickers sit next to a live drawing (docs/DESIGN_STICKER.md §18.3 step 5, checklist item 44): the
 * approximate key first when it has art, then the same category's stickers closest to the item, then (only when the
 * category has too few with art) the closest anywhere in the library. Pure and deterministic.
 *
 * "Closest" scores what the item is (its name and the decider's look) against each sticker's key, label, aliases and
 * draw hint (a shared packaging word such as jar, carton or bag counts triple: the model copies its neighbours' size
 * and silhouette), plus the colour families the two share (the body family counts extra).
 */
internal object NeighbourPicker {
    const val COUNT: Int = 3

    /**
     * [art] gives the families of every key that has library art (null = no art, e.g. a key still in
     * `StickerManifest.missing`). [text] is the item's name and look.
     */
    fun pick(
        catalog: IconCatalog,
        art: (String) -> List<String>?,
        approximateKey: String?,
        category: String?,
        families: List<String>,
        text: String,
    ): List<String> {
        val chosen = ArrayList<String>(COUNT)
        if (approximateKey != null && approximateKey != StickerManifest.CAPSULE && art(approximateKey) != null) chosen += approximateKey
        val cat = category ?: approximateKey?.let { catalog[it]?.category }
        val words = words(text)
        val candidates = catalog.icons.filter { it.key !in chosen && art(it.key) != null }
        fun best(pool: List<IconEntry>): List<String> = pool
            .mapIndexed { order, entry -> Triple(entry.key, score(entry, art(entry.key).orEmpty(), words, families), order) }
            .sortedWith(compareByDescending<Triple<String, Double, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
        if (cat != null) chosen += best(candidates.filter { it.category == cat }).take(COUNT - chosen.size)
        if (chosen.size < COUNT) chosen += best(candidates.filter { it.key !in chosen }).take(COUNT - chosen.size)
        return chosen
    }

    internal fun score(entry: IconEntry, entryFamilies: List<String>, itemWords: Set<String>, itemFamilies: List<String>): Double {
        val entryWords = words(listOf(entry.key.replace('_', ' '), entry.label, entry.drawHint) + entry.aliases)
        var s = 0.0
        for (w in itemWords) if (w in entryWords) s += if (w in SHAPES) 3.0 else 1.0
        if (itemFamilies.isNotEmpty() && entryFamilies.firstOrNull() == itemFamilies.first()) s += 1.5
        s += entryFamilies.count { it in itemFamilies }
        return s
    }

    private fun words(parts: List<String>): Set<String> = words(parts.joinToString(" "))

    /** Lower-case content words of three letters or more, crudely singular ("jars" -> "jar"). */
    internal fun words(text: String): Set<String> = text.lowercase()
        .split(Regex("[^\\p{L}]+"))
        .asSequence()
        .filter { it.length >= 3 && it !in STOP }
        .map { if (it.length > 3 && it.endsWith('s') && !it.endsWith("ss")) it.dropLast(1) else it }
        .toSet()

    /** Packaging and silhouette words: sharing one means the neighbour has the right shape to copy. */
    private val SHAPES = setOf(
        "jar", "bottle", "carton", "box", "bag", "can", "tin", "tub", "pouch", "packet", "tray", "loaf", "bunch", "roll",
        "jug", "cup", "basket", "pack", "bar", "block", "wedge", "tube", "stick", "bowl", "pot", "crate", "sack", "net",
        "dumpling", "pie", "bun", "leaf", "stalk", "pod", "spray", "pump", "brick",
    )

    private val STOP = setOf(
        "the", "and", "with", "one", "two", "three", "four", "five", "its", "few", "small", "big", "large", "little", "tall",
        "short", "round", "seen", "from", "front", "for", "into", "onto", "that", "this", "show", "showing", "through",
        "each", "other", "behind", "overlapping", "standing", "upright", "plump", "whole", "single", "blank", "simple",
        "sold", "canadian", "grocery", "store", "piece", "pieces", "exactly", "some", "top", "bottom", "side", "edge",
    )
}

/**
 * The neighbour sheet for one live drawing: [png] is the 1024 × 1024 sheet (three library stickers' art on a
 * transparent ground, the bottom-right cell empty), [mask] the bundled mask that opens only that cell, and
 * [targetAreaU2] the neighbours' mean art area in u² (what [IconPostProcessorV4] scales the new art to).
 */
class LiveSheet(
    val png: ByteArray,
    val mask: ByteArray,
    val neighbours: List<String>,
    val neighbourAreasU2: List<Double>,
) {
    val targetAreaU2: Double get() = neighbourAreasU2.average()
}

/**
 * Composes the live-drawing sheet (docs/DESIGN_STICKER.md §18.3 step 5): three library stickers drawn **art only** (no
 * die-cut, no shadow) into the first three 512 px cells of a transparent 1024 px square, exactly as the mockup's
 * `make4.py` did, plus the bundled `assets/icons/mask.png`. CPU-bound (≈ 50 ms): call it off the main thread.
 */
class SheetComposer(
    private val resources: Resources,
    private val catalog: IconCatalog,
    private val mask: () -> ByteArray?,
) : SheetSource {
    /** The sheet for an item, or null when no mask is bundled (live drawing is then off). */
    override fun compose(approximateKey: String?, category: String?, families: List<String>, text: String): LiveSheet? {
        val neighbours = NeighbourPicker.pick(catalog, ::artFamilies, approximateKey, category, families, text)
        return compose(neighbours)
    }

    /** The sheet for these [neighbours] (in cell order), or null when no mask is bundled. */
    fun compose(neighbours: List<String>): LiveSheet? {
        val maskBytes = mask() ?: return null
        val image = ImageBitmap(SHEET_PX, SHEET_PX)
        val canvas = Canvas(image)
        val size = Size(SHEET_PX.toFloat(), SHEET_PX.toFloat())
        val drawn = ArrayList<String>(NeighbourPicker.COUNT)
        for (key in neighbours) {
            if (drawn.size == NeighbourPicker.COUNT) break
            val id = StickerManifest.drawable(key) ?: continue
            val vector = try {
                StickerRenderer.load(resources, id)
            } catch (e: Exception) {
                // A broken drawable must not stop the drawing: the next candidate takes its cell.
                PicnicLog.w("sticker $key unreadable for the sheet", e)
                continue
            }
            val n = drawn.size
            StickerRenderer.draw(vector, StickerRenderer.ART_GROUPS, canvas, (n % 2) * CELL_PX.toFloat(), (n / 2) * CELL_PX.toFloat(), CELL_PX.toFloat(), size)
            drawn += key
        }
        val bitmap = image.asAndroidBitmap()
        val areas = drawn.indices.map { artAreaU2(bitmap, it) }
        val png = ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("sheet PNG encode failed")
            out.toByteArray()
        }
        return LiveSheet(png, maskBytes, drawn, areas.ifEmpty { listOf(IconPostProcessorV4.DEFAULT_TARGET_AREA_U2) })
    }

    private fun artFamilies(key: String): List<String>? =
        if (key == StickerManifest.CAPSULE) null else StickerManifest.entry(key)?.families

    /** Cell [n]'s art area in u², measured as post4 measures the new art (alpha over 50 %, opened twice, main pile). */
    private fun artAreaU2(sheet: Bitmap, n: Int): Double {
        val pixels = IntArray(CELL_PX * CELL_PX)
        sheet.getPixels(pixels, 0, CELL_PX, (n % 2) * CELL_PX, (n / 2) * CELL_PX, CELL_PX, CELL_PX)
        val opaque = BooleanArray(pixels.size) { (pixels[it] ushr 24) > 127 }
        val art = IconPostProcessorV4.keepMainPile(Raster.openCross(opaque, CELL_PX, CELL_PX, iterations = 2), CELL_PX, CELL_PX)
        val pxu = CELL_PX / IconPostProcessorV4.BOX_U
        return art.count { it } / (pxu * pxu)
    }

    companion object {
        const val SHEET_PX: Int = 1024
        const val CELL_PX: Int = 512
    }
}

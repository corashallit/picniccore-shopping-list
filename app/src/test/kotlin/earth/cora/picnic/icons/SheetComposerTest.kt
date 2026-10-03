package earth.cora.picnic.icons

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.ui.theme.gen.StickerManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * The live sheet (docs/DESIGN_STICKER.md §18.3 step 5, checklist item 44): composed from the approximate key plus two
 * same-category keys that have art, drawn art only into the first three 512 px cells exactly as the mockup's
 * `make4.py` placed them, with the bundled mask opening only the fourth cell.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetComposerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val catalog = IconCatalog.default
    private val bundled = BundledIcons(context)
    private val composer = SheetComposer(context.resources, catalog, bundled::mask)
    private val cell = SheetComposer.CELL_PX

    private fun decode(png: ByteArray) = ImageIO.read(ByteArrayInputStream(png))!!

    /** Alpha > 50 % per pixel of cell [n] (0..3, row-major) of a 1024 px image. */
    private fun cellAlpha(img: java.awt.image.BufferedImage, n: Int) = BooleanArray(cell * cell) { i ->
        val x = (n % 2) * cell + i % cell
        val y = (n / 2) * cell + i / cell
        (img.getRGB(x, y) ushr 24) > 127
    }

    @Test
    fun theApproximateKeyAndTwoSameCategoryNeighboursFillThreeCells() {
        val sheet = composer.compose(
            approximateKey = "jam",
            category = "breakfast",
            families = listOf("strawberry", "melon"),
            text = "Kimchi one squat glass jar of kimchi with a round screw lid",
        )!!
        assertEquals("jam", sheet.neighbours.first())
        assertEquals(3, sheet.neighbours.toSet().size)
        sheet.neighbours.forEach { key ->
            assertEquals(key, "breakfast", catalog[key]!!.category)
            assertTrue(key, StickerManifest.isBundled(key))
        }
        val img = decode(sheet.png)
        assertEquals(1024, img.width)
        assertEquals(1024, img.height)
        // Three cells hold art, the fourth is empty and transparent everywhere.
        for (n in 0 until 3) assertTrue("cell $n has art", cellAlpha(img, n).count { it } > 20_000)
        for (y in cell until 1024) for (x in cell until 1024) assertEquals(0, img.getRGB(x, y) ushr 24)
        // The neighbours' mean art area is what the new art is scaled to: a library sticker is 500 to 1300 u².
        assertEquals(3, sheet.neighbourAreasU2.size)
        sheet.neighbourAreasU2.forEach { assertTrue("area $it", it in 400.0..1400.0) }
        assertEquals(sheet.neighbourAreasU2.average(), sheet.targetAreaU2, 1e-9)
        assertTrue(sheet.mask.contentEquals(bundled.mask()))
    }

    @Test
    fun artOnlyNoDieCutAndTheMockupsPlacement() {
        // The mockup's v4 run used exactly these three neighbours for kimchi (sk/ai4/neigh.json); its sheet was
        // rendered by Chrome from the same DSL. Ours must put the same art in the same place at the same size.
        val sheet = composer.compose(listOf("jam", "pasta_sauce", "peanut_butter"))!!
        System.getenv("PICNIC_V4_DUMP")?.let { File(it).apply { mkdirs() }.resolve("sheet_kimchi_app.png").writeBytes(sheet.png) }
        val ours = decode(sheet.png)
        val mockup = ImageIO.read(resource("icons/ai4/sheet_kimchi.png"))
        for (n in 0 until 3) {
            val a = cellAlpha(ours, n)
            val b = cellAlpha(mockup, n)
            val inter = a.indices.count { a[it] && b[it] }
            val union = a.indices.count { a[it] || b[it] }
            assertTrue("cell $n overlap ${inter * 100 / union} %", inter.toDouble() / union > 0.85)
            // No white die-cut: the art's outermost ring is its colour trace, never the 4u white border.
            var whiteEdge = 0
            var edge = 0
            for (i in a.indices) {
                if (!a[i]) continue
                val x = i % cell
                val y = i / cell
                val border = x == 0 || y == 0 || x == cell - 1 || y == cell - 1 || !a[i - 1] || !a[i + 1] || !a[i - cell] || !a[i + cell]
                if (!border) continue
                edge++
                val p = ours.getRGB((n % 2) * cell + x, (n / 2) * cell + y)
                if (((p shr 16) and 0xFF) > 240 && ((p shr 8) and 0xFF) > 240 && (p and 0xFF) > 240) whiteEdge++
            }
            assertTrue("cell $n: ${whiteEdge * 100 / edge} % of the silhouette edge is white", whiteEdge * 20 < edge)
        }
    }

    @Test
    fun theMaskOpensOnlyTheFourthCell() {
        val mask = decode(bundled.mask()!!)
        assertEquals(1024, mask.width)
        for (y in 0 until 1024 step 7) for (x in 0 until 1024 step 7) {
            val open = x >= cell && y >= cell
            assertEquals("($x, $y)", if (open) 0 else 255, mask.getRGB(x, y) ushr 24)
        }
    }

    @Test
    fun aGenericWithoutArtIsSkippedAndTheCategoryFillsTheSheet() {
        assertTrue("pantry_generic" in StickerManifest.missing)
        val sheet = composer.compose("pantry_generic", "pantry", listOf("strawberry", "melon"), "Kimchi one squat glass jar of kimchi")!!
        assertEquals(3, sheet.neighbours.size)
        sheet.neighbours.forEach { assertEquals(it, "pantry", catalog[it]!!.category) }
        // A jar wins over a can or a box: the shared packaging word counts triple.
        assertEquals("pasta_sauce", sheet.neighbours.first())
    }

    @Test
    fun aThinCategoryBorrowsTheClosestStickersElsewhere() {
        // Seafood has one sticker with art: the other two cells come from the rest of the library.
        val sheet = composer.compose(null, "seafood", listOf("peach", "strawberry"), "Smoked salmon one pack of sliced smoked salmon")!!
        assertEquals(3, sheet.neighbours.size)
        assertEquals("seafood", catalog[sheet.neighbours.first()]!!.category)
        assertEquals(3, sheet.neighbours.toSet().size)
    }

    @Test
    fun noMaskNoSheet() {
        assertNull(SheetComposer(context.resources, catalog) { null }.compose("jam", "breakfast", listOf("strawberry"), "x"))
    }

    @Test
    fun pickingIsDeterministicAndPrefersTheItemsColours() {
        val art: (String) -> List<String>? = { k -> StickerManifest.entry(k)?.families?.takeIf { k != StickerManifest.CAPSULE } }
        val a = NeighbourPicker.pick(catalog, art, null, "fruit", listOf("sakura", "melon"), "Dragon fruit one whole dragon fruit with green tipped scales")
        val b = NeighbourPicker.pick(catalog, art, null, "fruit", listOf("sakura", "melon"), "Dragon fruit one whole dragon fruit with green tipped scales")
        assertEquals(a, b)
        assertEquals(3, a.size)
        // Every pick shares a family with the item (fruit has green-leafed red and pink stickers to choose from).
        a.forEach { k -> assertTrue(k, StickerManifest.entry(k)!!.families.any { it in listOf("sakura", "melon") }) }
    }

    private fun resource(path: String): File =
        File(requireNotNull(javaClass.classLoader!!.getResource(path)) { "missing test resource $path" }.toURI())
}

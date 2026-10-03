package earth.cora.picnic.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.theme.gen.StickerManifest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * The device half of live stickers v4 around the pure post-processor: decode the API's answer, process it, store a
 * lossless 208 px WebP plus its cut, and read both back through [StickerArt] (docs/DESIGN_STICKER.md §3.3, §18.3).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveStickerProcessorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun resource(path: String): File =
        File(requireNotNull(javaClass.classLoader!!.getResource(path)) { "missing test resource $path" }.toURI())

    /** The mockup's kimchi answer as the API returns it: 1024 px, the art in the fourth cell, encoded. */
    private fun kimchiAnswer(format: Bitmap.CompressFormat): Pair<ByteArray, IntArray> {
        val cell = ImageIO.read(resource("icons/ai4/cell_kimchi.png"))
        val argb = IntArray(1024 * 1024)
        for (y in 0 until 512) for (x in 0 until 512) argb[(y + 512) * 1024 + x + 512] = cell.getRGB(x, y)
        val bmp = Bitmap.createBitmap(argb, 1024, 1024, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { bmp.compress(format, 100, it); it.toByteArray() }
        return bytes to argb
    }

    @Test
    fun theAnswerBecomesALosslessStickerAndItsCut() {
        val (png, argb) = kimchiAnswer(Bitmap.CompressFormat.PNG)
        val sticker = BitmapLiveStickerProcessor().process(png, listOf("strawberry", "melon"), 1019.0)
        val decoded = BitmapFactory.decodeByteArray(sticker.webp, 0, sticker.webp.size)!!
        assertEquals(IconPostProcessorV4.OUT_PX, decoded.width)
        assertEquals(IconPostProcessorV4.OUT_PX, decoded.height)
        // Lossless: exactly the post-processor's pixels (fully transparent pixels may carry any colour).
        val expected = IconPostProcessorV4.process(argb, 1024, 1024, listOf("strawberry", "melon"), 1019.0)
        val got = IntArray(expected.argb.size)
        decoded.getPixels(got, 0, decoded.width, 0, 0, decoded.width, decoded.height)
        var differ = 0
        for (i in got.indices) {
            val a = expected.argb[i]
            val b = got[i]
            if ((a ushr 24) == 0 && (b ushr 24) == 0) continue
            if (a != b && kotlin.math.abs((a ushr 24) - (b ushr 24)) > 1) differ++
        }
        assertEquals(0, differ)
        assertEquals(expected.cutPath, sticker.cut)
        assertTrue(sticker.cut.startsWith("M") && sticker.cut.endsWith("Z"))
        assertTrue("a few KB (${sticker.webp.size} bytes)", sticker.webp.size < 40_000)
        assertNotNull(sticker.stats)
    }

    @Test
    fun aWebpAnswerWorksToo() {
        // The app asks for webp (ModelDefaults.IMAGE.outputFormat).
        val (webp, _) = kimchiAnswer(Bitmap.CompressFormat.WEBP_LOSSLESS)
        val sticker = BitmapLiveStickerProcessor().process(webp, listOf("strawberry", "melon"), 1019.0)
        assertTrue(sticker.cut.isNotEmpty())
    }

    @Test(expected = IOException::class)
    fun garbageIsAnIOException() {
        BitmapLiveStickerProcessor().process(byteArrayOf(1, 2, 3), listOf("lemon"), 800.0)
    }

    @Test(expected = IOException::class)
    fun anEmptyCellIsAnIOException() {
        val blank = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().use { blank.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        BitmapLiveStickerProcessor().process(png, listOf("lemon"), 800.0)
    }

    @Test
    fun stickerArtReadsV4AndLegacyStickersAndTheLibrary() = runTest {
        val (png, _) = kimchiAnswer(Bitmap.CompressFormat.PNG)
        val sticker = BitmapLiveStickerProcessor().process(png, listOf("strawberry", "melon"), 1019.0)
        val dir = tmp.newFolder("custom")
        File(dir, "c-v4.webp").writeBytes(sticker.webp)
        File(dir, "c-v4.cut").writeText(sticker.cut)
        // A legacy icon: a 192 px bitmap and no cut file.
        val legacy = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFB597F2.toInt()) }
        File(dir, "c-old.webp").writeBytes(ByteArrayOutputStream().use { legacy.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() })

        val art = AppStickerArt(dir)
        assertNull(art.cached("c-v4"))
        val v4 = art.load("c-v4")!!
        assertFalse(v4.legacy)
        assertEquals(208, v4.bitmap.width)
        val bounds = v4.cut!!.getBounds()
        // The cut hugs the art 4u (16 px) out, inside the 208 px square.
        assertTrue("cut $bounds", bounds.left >= 0f && bounds.top >= 0f && bounds.right <= 208f && bounds.bottom <= 208f)
        assertTrue("cut $bounds", bounds.width > 100f && bounds.height > 100f)
        assertTrue(art.cached("c-v4") === v4)

        val old = art.load("c-old")!!
        assertTrue(old.legacy)
        assertNull(old.cut)
        assertNull(art.load("c-missing"))

        // Library stickers: the drawable and its named groups; keys without art and the capsule sentinel have none.
        val apple = art.vector("apple")!!
        assertEquals(StickerManifest.drawable("apple"), apple.id)
        assertEquals(setOf("cut", "fills", "lines", "gloss", "dominant"), apple.groups)
        assertNull(art.vector("pantry_generic"))
        assertTrue(art.isBundled("apple"))
        assertFalse(art.isBundled("pantry_generic"))
        assertFalse(art.isBundled(StickerManifest.CAPSULE))
    }

    @Test
    fun theLibraryRendersArtOnlyOrWithItsCut() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val vector = StickerRenderer.load(context.resources, StickerManifest.drawable("apple")!!)
        val art = StickerRenderer.render(vector, StickerRenderer.ART_GROUPS, 208)
        val full = StickerRenderer.render(vector, StickerRenderer.STICKER_GROUPS, 208)
        fun area(b: Bitmap): Int {
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            return px.count { (it ushr 24) > 127 }
        }
        // The die-cut adds a 4u (16 px) white ring around the art.
        assertTrue(area(full) > area(art) * 1.3)
        assertArrayEquals(intArrayOf(208, 208), intArrayOf(full.width, full.height))
    }
}

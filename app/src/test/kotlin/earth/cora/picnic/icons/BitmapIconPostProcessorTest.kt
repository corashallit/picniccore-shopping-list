package earth.cora.picnic.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapIconPostProcessorTest {
    private fun raw(draw: (Canvas) -> Unit): ByteArray {
        val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        draw(Canvas(bmp))
        return ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
    }

    @Test
    fun trimsFitsAndCentresOnA192Canvas() {
        // A small off-centre sticker in a big transparent canvas, as the image model often returns.
        val input = raw { it.drawCircle(300f, 260f, 120f, Paint().apply { color = Color.RED; isAntiAlias = true }) }
        val out = BitmapIconPostProcessor().process(input)
        val bmp = BitmapFactory.decodeByteArray(out, 0, out.size)
        assertEquals(192, bmp.width)
        assertEquals(192, bmp.height)
        assertTrue("corners stay transparent", Color.alpha(bmp.getPixel(2, 2)) < 16)
        assertTrue("centre is the sticker", Color.alpha(bmp.getPixel(96, 96)) > 200)
        // Trimmed and scaled up to ~176 px: opaque content reaches near the 8 px margins.
        val px = IntArray(192 * 192).also { bmp.getPixels(it, 0, 192, 0, 0, 192, 192) }
        val b = IconGeometry.opaqueBounds(px, 192, 192, alphaThreshold = 128)!!
        assertTrue("width ${b.width}", b.width in 166..178)
        assertTrue(out.size < 20_000)
    }

    @Test(expected = IOException::class)
    fun garbageIsAnIOException() {
        BitmapIconPostProcessor().process(byteArrayOf(1, 2, 3))
    }

    @Test(expected = IOException::class)
    fun fullyTransparentIsAnIOException() {
        BitmapIconPostProcessor().process(raw { })
    }
}

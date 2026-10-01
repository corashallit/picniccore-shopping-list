package earth.cora.picnic.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapturesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun newCaptureUriPointsIntoTheCaptureDirAndCanBeDeleted() {
        val uri = Captures.newCaptureUri(context)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        val file = Captures.captureFile(context, uri)
        assertNotNull(file)
        assertTrue(file!!.exists())
        assertEquals(File(context.cacheDir, "captures"), file.parentFile)
        assertTrue(Captures.delete(context, uri))
        assertFalse(file.exists())
    }

    @Test
    fun foreignUrisAreNeverDeleted() {
        assertNull(Captures.captureFile(context, Uri.parse("content://media/external/images/media/42")))
        assertNull(Captures.captureFile(context, Uri.parse("content://${context.packageName}.fileprovider/captures/../state.json")))
        assertNull(Captures.captureFile(context, Uri.parse("content://${context.packageName}.fileprovider/other/list_1.jpg")))
        assertFalse(Captures.delete(context, Uri.parse("file:///sdcard/DCIM/photo.jpg")))
    }

    @Test
    fun sweepRemovesOnlyStaleFiles() {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val voice = File(context.cacheDir, "voice").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val old = File(dir, "list_old.jpg").apply { writeText("x"); setLastModified(now - 2 * 86_400_000L) }
        val oldVoice = File(voice, "voice_1.m4a").apply { writeText("x"); setLastModified(now - 2 * 86_400_000L) }
        val fresh = File(dir, "list_new.jpg").apply { writeText("x"); setLastModified(now - 1_000L) }
        Captures.sweepStale(context, now)
        assertFalse(old.exists())
        assertFalse(oldVoice.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun toJpegDownscalesAndFlattensTransparency() {
        val png = File(context.cacheDir, "big.png")
        val src = Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        png.outputStream().use { src.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val jpeg = Captures.toJpeg(context, Uri.fromFile(png))
        assertEquals(0xFF.toByte(), jpeg[0])
        assertEquals(0xD8.toByte(), jpeg[1])
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(1600, maxOf(decoded.width, decoded.height))
        // Transparent pixels became white, not black.
        val p = decoded.getPixel(10, 10)
        assertTrue(Color.red(p) > 240 && Color.green(p) > 240 && Color.blue(p) > 240)
    }
}

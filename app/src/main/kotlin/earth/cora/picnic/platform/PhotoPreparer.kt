package earth.cora.picnic.platform

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a camera/picker Uri into the JPEG the ingest call sends (EXIF-aware, long edge <= 1600 px, q85).
 * An interface so ViewModels never hold a Context and tests can fake it.
 */
fun interface PhotoPreparer {
    /** Main-safe. Throws java.io.IOException / IllegalStateException if the image can't be decoded. */
    suspend fun jpegBytes(uri: Uri): ByteArray
}

class CapturePhotoPreparer(private val context: Context) : PhotoPreparer {
    override suspend fun jpegBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) { Captures.toJpeg(context, uri) }
}

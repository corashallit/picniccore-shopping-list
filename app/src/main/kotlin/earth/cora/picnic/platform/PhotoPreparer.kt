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
    /** Main-safe. Throws java.io.IOException if the image can't be read or decoded ("Couldn't open that photo."). */
    suspend fun jpegBytes(uri: Uri): ByteArray

    /**
     * Deletes the file behind [uri] if it is one of our camera captures (cacheDir/captures);
     * photo-picker Uris are left alone. Call once the photo has been sent (or abandoned). Main-safe.
     */
    suspend fun discard(uri: Uri) = Unit
}

class CapturePhotoPreparer(private val context: Context) : PhotoPreparer {
    override suspend fun jpegBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) { Captures.toJpeg(context, uri) }

    override suspend fun discard(uri: Uri) {
        withContext(Dispatchers.IO) { Captures.delete(context, uri) }
    }
}

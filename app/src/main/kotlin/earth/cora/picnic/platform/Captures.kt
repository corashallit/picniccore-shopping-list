package earth.cora.picnic.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Photo capture helpers (docs/ARCHITECTURE.md §10.4, "Snap"). The system camera is used through
 * ActivityResultContracts.TakePicture, so the app declares NO CAMERA permission.
 * Capture files live in cacheDir/captures/ (exposed by res/xml/file_paths.xml) and are deleted after use.
 */
object Captures {
    private const val DIR = "captures"
    private const val PREFIX = "list_"

    /** The FileProvider authority declared in the manifest. */
    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** A new empty file in cacheDir/captures/ and its content:// Uri for TakePicture. */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File.createTempFile(PREFIX, ".jpg", dir)
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /**
     * Deletes a capture created by [newCaptureUri]. Uris from the photo picker (or anything else)
     * are left alone: they are not ours to delete. Returns true if a file was deleted.
     */
    fun delete(context: Context, uri: Uri): Boolean {
        val file = captureFile(context, uri) ?: return false
        return file.delete()
    }

    /** Maps one of our FileProvider capture Uris back to its file; null for any other Uri. */
    internal fun captureFile(context: Context, uri: Uri): File? {
        if (uri.scheme != "content" || uri.authority != authority(context)) return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != DIR) return null
        val name = segments[1]
        if (!name.startsWith(PREFIX) || name.contains('/') || name.contains("..")) return null
        return File(File(context.cacheDir, DIR), name)
    }

    /**
     * Removes leftovers older than [maxAgeMs] from cacheDir/captures and cacheDir/voice (a capture
     * whose ingest was interrupted by process death). Call off the main thread.
     */
    fun sweepStale(context: Context, now: Long = System.currentTimeMillis(), maxAgeMs: Long = 24L * 60 * 60 * 1000) {
        listOf(DIR, VoiceRecorder.DIR).forEach { name ->
            File(context.cacheDir, name).listFiles()?.forEach { f ->
                if (f.isFile && now - f.lastModified() > maxAgeMs) f.delete()
            }
        }
    }

    /**
     * Decode (EXIF-aware), downscale so the long edge is <= [maxEdge], flatten transparency onto
     * white, re-encode as JPEG. Throws [IOException] if the image can't be read or decoded.
     */
    fun toJpeg(context: Context, uri: Uri, maxEdge: Int = 1600, quality: Int = 85): ByteArray {
        val decoded: Bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // ImageDecoder applies the EXIF orientation and also decodes HEIF.
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val scale = maxEdge.toFloat() / max(info.size.width, info.size.height)
                    if (scale < 1f) {
                        decoder.setTargetSize(
                            max(1, (info.size.width * scale).roundToInt()),
                            max(1, (info.size.height * scale).roundToInt()),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                decodeLegacy(context, uri, maxEdge)
            }
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            // SecurityException (revoked grant), IllegalStateException, OOM-adjacent decoder errors.
            throw IOException("Couldn't decode image", e)
        }
        val opaque = flattenOnWhite(decoded)
        return ByteArrayOutputStream().use { out ->
            if (!opaque.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IOException("JPEG encode failed")
            out.toByteArray()
        }
    }

    /** JPEG has no alpha: a transparent PNG/WebP would otherwise turn black. */
    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val out = createBitmap(bitmap.width, bitmap.height)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return out
    }

    private fun decodeLegacy(context: Context, uri: Uri, maxEdge: Int): Bitmap {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val raw = cr.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Could not decode image")
        val rotation = cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val scale = minOf(1f, maxEdge.toFloat() / max(raw.width, raw.height))
        if (rotation == 0 && scale == 1f) return raw
        val m = Matrix().apply {
            postRotate(rotation.toFloat())
            postScale(scale, scale)
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }
}

/**
 * The three ways into the Add sheet that need the platform: camera (no CAMERA permission), photo
 * picker (no permission), microphone (RECORD_AUDIO at runtime). Remember it in the Add sheet.
 * The pending camera Uri survives process death (rememberSaveable): camera apps can kill us.
 */
@Composable
fun rememberAddSources(
    onImage: (Uri) -> Unit,
    onMicGranted: () -> Unit,
    onMicDenied: () -> Unit = {},
    onCameraUnavailable: () -> Unit = {},
): AddSources {
    val context = LocalContext.current
    val currentOnImage by rememberUpdatedState(onImage)
    val currentOnMicGranted by rememberUpdatedState(onMicGranted)
    val currentOnMicDenied by rememberUpdatedState(onMicDenied)
    val currentOnCameraUnavailable by rememberUpdatedState(onCameraUnavailable)
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingUri
        pendingUri = null
        if (uri != null) {
            if (saved) currentOnImage(uri) else Captures.delete(context, uri)
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) currentOnImage(uri)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) currentOnMicGranted() else currentOnMicDenied()
    }
    return remember(context, takePicture, pickImage, micPermission) {
        AddSources(
            takePhoto = {
                val uri = Captures.newCaptureUri(context)
                pendingUri = uri
                try {
                    takePicture.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    pendingUri = null
                    Captures.delete(context, uri)
                    currentOnCameraUnavailable()
                }
            },
            pickPhoto = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            recordVoice = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    currentOnMicGranted()
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
    }
}

@Stable
class AddSources(val takePhoto: () -> Unit, val pickPhoto: () -> Unit, val recordVoice: () -> Unit)

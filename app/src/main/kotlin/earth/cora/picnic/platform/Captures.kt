package earth.cora.picnic.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

object Captures {
    /** cacheDir/captures/ is exposed by res/xml/file_paths.xml (<cache-path name="captures" path="captures/"/>). */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File.createTempFile("list_", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Decode (EXIF-aware), downscale so the long edge is <= [maxEdge], re-encode as JPEG. */
    fun toJpeg(context: Context, uri: Uri, maxEdge: Int = 1600, quality: Int = 85): ByteArray {
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF orientation and decodes HEIF.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = maxEdge.toFloat() / max(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            decodeLegacy(context, uri, maxEdge)
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }

    private fun decodeLegacy(context: Context, uri: Uri, maxEdge: Int): Bitmap {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val raw = cr.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not decode image")
        val rotation = cr.openInputStream(uri).use { ExifInterface(it!!).rotationDegrees }
        val scale = minOf(1f, maxEdge.toFloat() / max(raw.width, raw.height))
        if (rotation == 0 && scale == 1f) return raw
        val m = Matrix().apply { postRotate(rotation.toFloat()); postScale(scale, scale) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }
}

/** Example wiring for the Add sheet: camera (no CAMERA permission), photo picker, mic permission. */
@Composable
fun rememberAddSources(
    onImage: (Uri) -> Unit,
    onMicGranted: () -> Unit,
): AddSources {
    val context = LocalContext.current
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingUri
        pendingUri = null
        if (saved && uri != null) onImage(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImage(uri)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onMicGranted()
    }
    return remember(context) {
        AddSources(
            takePhoto = {
                val uri = Captures.newCaptureUri(context)
                pendingUri = uri
                takePicture.launch(uri)
            },
            pickPhoto = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            recordVoice = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) onMicGranted() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}

class AddSources(val takePhoto: () -> Unit, val pickPhoto: () -> Unit, val recordVoice: () -> Unit)

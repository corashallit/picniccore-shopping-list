package earth.cora.picnic.platform

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException
import kotlin.math.log10

/** Voice capture seam (fake it in AddViewModel tests). CONTRACT: docs/ARCHITECTURE.md §10.4. */
interface AudioRecorder {
    /**
     * Starts recording to a new .m4a file. The caller holds RECORD_AUDIO and is in the foreground.
     * Throws [IOException] if the microphone can't be opened (busy, no mic, permission revoked).
     */
    fun start(): File

    /** Current input level 0..1 for the waveform (poll ~10x/s while recording); 0 when idle. */
    fun level(): Float

    /** Stops and returns the file, or null if nothing usable was captured (file deleted). Safe to call twice. */
    fun stop(): File?

    /** True once the recording hit its maximum length and stopped by itself; call [stop] to collect the file. */
    fun reachedLimit(): Boolean = false

    /** Stops and deletes the recording (sheet dismissed, ON_STOP without a submit). Safe to call any time. */
    fun cancel() {
        stop()?.delete()
    }
}

/**
 * Records mono AAC in an MPEG-4 container (.m4a), which /audio/transcriptions accepts (the ".m4a"
 * file name selects the decoder; ".aac"/".3gp" are rejected). 16 kHz, 32 kbps: ~14 MB per hour,
 * far below the 25 MB upload cap. Foreground only: the owner stops it on Lifecycle ON_STOP.
 */
class VoiceRecorder(private val context: Context) : AudioRecorder {
    private var recorder: MediaRecorder? = null

    @Volatile
    private var limitReached = false

    var output: File? = null
        private set

    override fun start(): File {
        if (recorder != null) cancel()
        val file = File(context.cacheDir, "$DIR/voice_${System.currentTimeMillis()}.m4a").apply { parentFile?.mkdirs() }
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        limitReached = false
        try {
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(SAMPLE_RATE_HZ)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setMaxDuration(MAX_DURATION_MS)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) limitReached = true
            }
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            // IOException from prepare(), IllegalStateException/RuntimeException from start() when the
            // mic is busy or the permission was revoked.
            r.release()
            file.delete()
            throw if (e is IOException) e else IOException("Couldn't start recording", e)
        }
        recorder = r
        output = file
        return file
    }

    /** Perceptual level: maxAmplitude since the last poll, mapped from -50..0 dBFS onto 0..1. */
    override fun level(): Float {
        val amplitude = try {
            recorder?.maxAmplitude ?: 0
        } catch (e: IllegalStateException) {
            0
        }
        return amplitudeToLevel(amplitude)
    }

    override fun reachedLimit(): Boolean = limitReached

    /** Returns the file, or null if the recording was too short (MediaRecorder.stop() throws then). */
    override fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val file = output
        output = null
        return try {
            // After MAX_DURATION_REACHED the recorder has already stopped itself.
            if (!limitReached) r.stop()
            file?.takeIf { it.length() > MIN_BYTES } ?: run {
                file?.delete()
                null
            }
        } catch (e: RuntimeException) {
            file?.delete()
            null
        } finally {
            r.release()
        }
    }

    companion object {
        /** cacheDir/voice/: transient recordings, deleted after transcription (and swept at start). */
        const val DIR: String = "voice"
        const val MAX_DURATION_MS: Int = 5 * 60 * 1000
        private const val SAMPLE_RATE_HZ = 16_000
        private const val BIT_RATE = 32_000

        /** An m4a with only a header is a few hundred bytes. */
        private const val MIN_BYTES = 1_024L

        internal fun amplitudeToLevel(amplitude: Int): Float {
            if (amplitude <= 0) return 0f
            val db = 20f * log10(amplitude.coerceAtMost(32_767) / 32_767f)
            return ((db + 50f) / 50f).coerceIn(0f, 1f)
        }
    }
}

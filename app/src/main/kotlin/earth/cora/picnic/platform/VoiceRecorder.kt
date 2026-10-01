package earth.cora.picnic.platform

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Voice capture seam (fake it in AddViewModel tests). CONTRACT: docs/ARCHITECTURE.md §10.4. */
interface AudioRecorder {
    /** Starts recording to a new .m4a file. Caller holds RECORD_AUDIO and is in the foreground. */
    fun start(): File

    /** Current input level 0..1 for the waveform (poll ~10x/s while recording); 0 when idle. */
    fun level(): Float

    /** Stops and returns the file, or null if nothing usable was captured (file deleted). Safe to call twice. */
    fun stop(): File?
}

/** Records mono AAC in an MPEG-4 container (.m4a), which /audio/transcriptions accepts. */
class VoiceRecorder(private val context: Context) : AudioRecorder {
    private var recorder: MediaRecorder? = null
    var output: File? = null
        private set

    /** Caller must hold RECORD_AUDIO (runtime permission) and be in the foreground. */
    override fun start(): File {
        val file = File(context.cacheDir, "voice/${System.currentTimeMillis()}.m4a").apply { parentFile?.mkdirs() }
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(16_000)
        r.setAudioEncodingBitRate(32_000) // ~14 MB/hour: far below the 25 MB upload cap for any shopping list
        r.setMaxDuration(5 * 60 * 1000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        output = file
        return file
    }

    override fun level(): Float = (recorder?.maxAmplitude ?: 0).coerceIn(0, 32767) / 32767f

    /** Returns the file, or null if the recording was too short (stop() throws RuntimeException then). */
    override fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        return try {
            r.stop(); output
        } catch (e: RuntimeException) {
            output?.delete(); null
        } finally {
            r.release()
        }
    }
}

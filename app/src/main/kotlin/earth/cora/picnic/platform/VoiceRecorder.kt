package earth.cora.picnic.platform

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records mono AAC in an MPEG-4 container (.m4a), which /audio/transcriptions accepts. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var output: File? = null
        private set

    /** Caller must hold RECORD_AUDIO (runtime permission) and be in the foreground. */
    fun start(): File {
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

    /** Returns the file, or null if the recording was too short (stop() throws RuntimeException then). */
    fun stop(): File? {
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

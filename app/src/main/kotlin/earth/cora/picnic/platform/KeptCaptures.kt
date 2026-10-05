package earth.cora.picnic.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/*
 * Photos and voice notes that could not be sent (docs/DESIGN_STICKER.md §0.4, 5 October; PRIVACY.md): the add flows
 * used to hold them only in memory, so closing the sheet or the app ending lost them. Now a photo or recording whose
 * request failed is kept in app-private storage (noBackupFilesDir/kept: never backed up, no other app can read it) until
 * it is sent, she takes or records a new one, or she deletes it; at most 7 days ([CaptureKeeper.MAX_AGE_MS]). One of
 * each kind at a time.
 */

enum class CaptureKind(val prefix: String, val extension: String) {
    PHOTO("photo", "jpg"),
    VOICE("voice", "m4a"),
}

/** A kept capture: its kind, its file's name in the keeper's folder, and when it was kept (epoch ms). */
data class KeptCapture(val kind: CaptureKind, val name: String, val keptAt: Long)

/** The kept capture a saved pointer (its file's name) names; null for a name the keeper never writes. */
fun keptCaptureOf(kind: CaptureKind, name: String): KeptCapture? {
    if (!name.startsWith(kind.prefix + "-") || !name.endsWith("." + kind.extension) || '/' in name) return null
    val at = name.removePrefix(kind.prefix + "-").removeSuffix("." + kind.extension).toLongOrNull() ?: return null
    return KeptCapture(kind, name, at)
}

interface CaptureKeeper {
    /** Keeps [bytes] as the [kind]'s capture, replacing the one kept before. Null when it could not be written. */
    suspend fun keep(kind: CaptureKind, bytes: ByteArray): KeptCapture?

    /** The bytes of [kept]; null once it is gone (sent, deleted, swept). */
    suspend fun read(kept: KeptCapture): ByteArray?

    /** The [kind]'s kept capture, if there is one (a fresh start finds it again). */
    suspend fun latest(kind: CaptureKind): KeptCapture?

    /** Deletes the [kind]'s kept capture: sent, replaced or thrown away. */
    suspend fun discard(kind: CaptureKind)

    /** Deletes kept captures older than [maxAgeMs] (and half-written leftovers). */
    suspend fun sweep(maxAgeMs: Long = MAX_AGE_MS)

    /** Keeps nothing (previews, tests that do not care). */
    object None : CaptureKeeper {
        override suspend fun keep(kind: CaptureKind, bytes: ByteArray): KeptCapture? = null
        override suspend fun read(kept: KeptCapture): ByteArray? = null
        override suspend fun latest(kind: CaptureKind): KeptCapture? = null
        override suspend fun discard(kind: CaptureKind) = Unit
        override suspend fun sweep(maxAgeMs: Long) = Unit
    }

    companion object {
        /** A kept capture goes after 7 days, sent or not. */
        const val MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * [CaptureKeeper] on files in [dir] (`photo-<keptAt>.jpg`, `voice-<keptAt>.m4a`), written atomically (tmp + rename).
 * Main-safe; a failed read or write is logged and answers null (the flows then work as before, in memory only).
 */
class FileCaptureKeeper(
    private val dir: File,
    private val clock: () -> Long,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CaptureKeeper {
    private val mutex = Mutex()

    override suspend fun keep(kind: CaptureKind, bytes: ByteArray): KeptCapture? = locked {
        val keptAt = clock()
        val name = "${kind.prefix}-$keptAt.${kind.extension}"
        try {
            dir.mkdirs()
            val target = File(dir, name)
            val tmp = File(dir, "$name.tmp")
            tmp.outputStream().use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            files(kind).filter { it.name != name }.forEach { it.delete() }
            KeptCapture(kind, name, keptAt)
        } catch (e: IOException) {
            PicnicLog.w("couldn't keep the ${kind.prefix}", e)
            null
        }
    }

    override suspend fun read(kept: KeptCapture): ByteArray? = locked {
        val file = File(dir, kept.name)
        if (!safeName(kept.kind, kept.name) || !file.isFile) return@locked null
        try {
            file.readBytes()
        } catch (e: IOException) {
            PicnicLog.w("couldn't read the kept ${kept.kind.prefix}", e)
            null
        }
    }

    override suspend fun latest(kind: CaptureKind): KeptCapture? = locked {
        files(kind).mapNotNull { f -> keptAt(kind, f.name)?.let { KeptCapture(kind, f.name, it) } }.maxByOrNull { it.keptAt }
    }

    override suspend fun discard(kind: CaptureKind) {
        locked { files(kind).forEach { it.delete() } }
    }

    override suspend fun sweep(maxAgeMs: Long) {
        locked {
            val now = clock()
            dir.listFiles()?.forEach { f ->
                val kind = CaptureKind.entries.firstOrNull { f.name.startsWith(it.prefix + "-") }
                val at = kind?.let { keptAt(it, f.name.removeSuffix(".tmp")) } ?: f.lastModified()
                if (f.isFile && (now - at > maxAgeMs || (f.name.endsWith(".tmp") && now - f.lastModified() > TMP_AGE_MS))) f.delete()
            }
        }
    }

    private fun files(kind: CaptureKind): List<File> =
        dir.listFiles()?.filter { it.isFile && safeName(kind, it.name) }.orEmpty()

    private fun safeName(kind: CaptureKind, name: String): Boolean = keptAt(kind, name) != null

    private fun keptAt(kind: CaptureKind, name: String): Long? = keptCaptureOf(kind, name)?.keptAt

    private suspend fun <T> locked(block: () -> T): T = withContext(io) { mutex.withLock { block() } }

    private companion object {
        /** A half-written file older than this is a leftover of a crash. */
        const val TMP_AGE_MS: Long = 60L * 60 * 1000
    }
}

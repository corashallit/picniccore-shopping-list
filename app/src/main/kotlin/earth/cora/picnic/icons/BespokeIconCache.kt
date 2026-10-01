package earth.cora.picnic.icons

import earth.cora.picnic.platform.PicnicLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** filesDir/icons/index.json (docs/ARCHITECTURE.md §8.4). Device-local; never backed up. */
@Serializable
internal data class IconIndex(
    val version: Int = 1,
    /** normalizeName(item) -> customIconId of its newest bespoke icon. */
    val byName: Map<String, String> = emptyMap(),
    /** Names image moderation refused: never drawn automatically again. */
    val blocked: List<String> = emptyList(),
    /** Local date (ISO yyyy-MM-dd) that [drawnToday] counts for. */
    val day: String? = null,
    val drawnToday: Int = 0,
)

/**
 * Bespoke (live-drawn) icons on disk: `<root>/custom/<customIconId>.webp` plus `<root>/index.json`.
 * Every write is atomic (tmp + rename) and all access is serialized by one Mutex. Main-safe.
 */
class BespokeIconCache(
    private val root: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    val customDir: File = File(root, "custom")
    private val indexFile = File(root, "index.json")
    private val mutex = Mutex()
    private var loaded: IconIndex? = null

    /** The cached icon id for [key] (a normalizeName), only if its file still exists. */
    suspend fun lookup(key: String): String? = locked { index ->
        index.byName[key]?.takeIf { fileFor(it).isFile }
    }

    suspend fun isBlocked(key: String): Boolean = locked { it.blocked.contains(key) }

    suspend fun block(key: String) {
        mutate { if (key in it.blocked) it else it.copy(blocked = (it.blocked + key).takeLast(MAX_BLOCKED)) }
    }

    /** Counts one automatic drawing for [day]; false (and nothing counted) when [cap] is reached. */
    suspend fun tryConsumeDaily(day: String, cap: Int): Boolean {
        var allowed = false
        mutate { index ->
            val used = if (index.day == day) index.drawnToday else 0
            allowed = used < cap
            if (allowed) index.copy(day = day, drawnToday = used + 1) else index
        }
        return allowed
    }

    /** Gives one count back (the drawing never reached the API, e.g. no style reference). */
    suspend fun refundDaily(day: String) {
        mutate { if (it.day == day && it.drawnToday > 0) it.copy(drawnToday = it.drawnToday - 1) else it }
    }

    /**
     * Writes [webp] as the icon [id] and points [key] at it. Returns the id [key] pointed at before
     * (the caller may delete that file after a redraw). Throws [IOException] on a failed write.
     */
    suspend fun put(key: String, id: String, webp: ByteArray): String? {
        var previous: String? = null
        mutate { index ->
            writeAtomically(fileFor(id), webp)
            previous = index.byName[key]
            index.copy(byName = index.byName + (key to id))
        }
        return previous
    }

    /** The user picked a catalog icon for [key]: stop attaching its bespoke icon to new items. */
    suspend fun forget(key: String) {
        mutate { if (key in it.byName) it.copy(byName = it.byName - key) else it }
    }

    /** Deletes one icon file (after a redraw replaced it and nothing references it). */
    suspend fun deleteFile(id: String) {
        locked { fileFor(id).delete() }
    }

    /**
     * Deletes icon files that neither an item ([referencedIds]) nor the index points at, and that
     * are older than [minAgeMs] (so a drawing that is being attached right now is never swept).
     */
    suspend fun sweep(referencedIds: Set<String>, now: Long, minAgeMs: Long = 10 * 60 * 1000L): Int = locked { index ->
        val keep = referencedIds + index.byName.values
        var removed = 0
        customDir.listFiles()?.forEach { f ->
            val id = f.name.removeSuffix(".webp")
            val stale = now - f.lastModified() > minAgeMs
            val orphanTmp = f.name.endsWith(".tmp")
            if (f.isFile && stale && (orphanTmp || id !in keep) && f.delete()) removed++
        }
        removed
    }

    fun fileFor(id: String): File = File(customDir, "$id.webp")

    private suspend fun <T> locked(block: (IconIndex) -> T): T = withContext(io) {
        mutex.withLock { block(loadLocked()) }
    }

    private suspend fun mutate(transform: (IconIndex) -> IconIndex) {
        withContext(io) {
            mutex.withLock {
                val before = loadLocked()
                val after = transform(before)
                if (after != before) {
                    writeAtomically(indexFile, JSON.encodeToString(IconIndex.serializer(), after).toByteArray(Charsets.UTF_8))
                    loaded = after
                }
            }
        }
    }

    private fun loadLocked(): IconIndex {
        loaded?.let { return it }
        val index = if (indexFile.isFile) {
            try {
                JSON.decodeFromString(IconIndex.serializer(), indexFile.readText(Charsets.UTF_8))
            } catch (e: SerializationException) {
                PicnicLog.w("icon index unreadable; starting fresh", e)
                IconIndex()
            } catch (e: IllegalArgumentException) {
                PicnicLog.w("icon index unreadable; starting fresh", e)
                IconIndex()
            } catch (e: IOException) {
                PicnicLog.w("icon index unreadable; starting fresh", e)
                IconIndex()
            }
        } else {
            IconIndex()
        }
        loaded = index
        return index
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private const val MAX_BLOCKED = 500
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * `"c-" + sha1(key).take(10) + "-" + base36(epochSeconds)`. A redraw gets a new id, so a stale
         * bitmap is never served from a memory cache. [taken] guards the same-second case.
         */
        fun newIconId(key: String, nowMs: Long, taken: (String) -> Boolean = { false }): String {
            val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(10)
            val base = "c-$hash-${(nowMs / 1000).toString(36)}"
            if (!taken(base)) return base
            var n = 2
            while (taken("$base$n")) n++
            return "$base$n"
        }
    }
}

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
    /**
     * Names whose automatic drawing waits for a connection (offline, or the drawing failed on the way: unreachable, a
     * network error, a timeout). resumePending and the catch-up submit them again, under the daily cap; a name leaves the
     * set once it is drawn, refused, given up on, or no item wants it any more.
     */
    val drawLater: List<String> = emptyList(),
)

/**
 * Bespoke (live-drawn) icons on disk: `<root>/custom/<customIconId>.webp`, its die-cut path `<customIconId>.cut`
 * (live stickers v4, docs/DESIGN_STICKER.md §18.3 step 5) and `<root>/index.json`. An icon without a `.cut` file is a
 * **legacy** icon drawn before v4 (192 px, baked ink outline, border and shadow): it renders as it is (§3.3 step 5)
 * and is redrawn in v4 in the background ([legacyIcons]). Every write is atomic (tmp + rename) and all access is
 * serialized by one Mutex. Main-safe.
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

    /** Gives one count back (the drawing never reached the API, e.g. no style reference, or it failed on the way). */
    suspend fun refundDaily(day: String) {
        mutate { if (it.day == day && it.drawnToday > 0) it.copy(drawnToday = it.drawnToday - 1) else it }
    }

    /** The names waiting for a connection to be drawn (oldest first). */
    suspend fun drawLater(): List<String> = locked { it.drawLater }

    /** [key]'s automatic drawing waits for a connection (kept across restarts). */
    suspend fun markDrawLater(key: String) {
        mutate { if (key in it.drawLater) it else it.copy(drawLater = (it.drawLater + key).takeLast(MAX_DRAW_LATER)) }
    }

    /** [key] no longer waits: drawn, refused, given up on, or nothing wants it. */
    suspend fun clearDrawLater(key: String) {
        mutate { if (key in it.drawLater) it.copy(drawLater = it.drawLater - key) else it }
    }

    /**
     * Writes [webp] (and its die-cut path [cut], null for a legacy icon) as the icon [id] and points [key] at it.
     * The cut is written first, so a crash in between never leaves a v4 bitmap without its cut. Returns the id [key]
     * pointed at before (after a redraw the caller releases that one with [deleteIfUnreferenced]). Throws
     * [IOException] on a failed write.
     */
    suspend fun put(key: String, id: String, webp: ByteArray, cut: String? = null): String? {
        var previous: String? = null
        mutate { index ->
            if (cut != null) writeAtomically(cutFileFor(id), cut.toByteArray(Charsets.UTF_8))
            writeAtomically(fileFor(id), webp)
            previous = index.byName[key]
            index.copy(byName = index.byName + (key to id))
        }
        return previous
    }

    /** The stored die-cut path of icon [id], or null for a legacy (or missing) icon. */
    suspend fun readCut(id: String): String? = locked {
        val f = cutFileFor(id)
        if (f.isFile) f.readText(Charsets.UTF_8) else null
    }

    /**
     * Legacy icons the index still points at (a bitmap and no `.cut` file), oldest first, as (normalized name, id).
     * The repository redraws them in v4 in the background, under the daily cap (§18.3 step 5).
     */
    suspend fun legacyIcons(): List<Pair<String, String>> = locked { index ->
        index.byName.entries
            .filter { (_, id) -> fileFor(id).isFile && !cutFileFor(id).isFile }
            .sortedWith(compareBy({ fileFor(it.value).lastModified() }, { it.value }))
            .map { it.key to it.value }
    }

    /** The user picked a catalog icon for [key]: stop attaching its bespoke icon to new items. */
    suspend fun forget(key: String) {
        mutate { if (key in it.byName) it.copy(byName = it.byName - key) else it }
    }

    /** Deletes one icon's files (bitmap and cut). After a redraw, use [deleteIfUnreferenced] instead. */
    suspend fun deleteFile(id: String) {
        locked {
            fileFor(id).delete()
            cutFileFor(id).delete()
        }
    }

    /**
     * After a redraw replaced icon [id]: deletes its files only when nothing references it any more, neither an item
     * or pantry entry nor `album.json` (both in [referencedIds]) nor the index. Returns whether it deleted them.
     */
    suspend fun deleteIfUnreferenced(id: String, referencedIds: Set<String>): Boolean = locked { index ->
        if (id in referencedIds || id in index.byName.values) return@locked false
        fileFor(id).delete()
        cutFileFor(id).delete()
        true
    }

    /**
     * Deletes icon files (bitmaps and cuts) that neither an item, a pantry entry or the album ([referencedIds]) nor
     * the index points at, and that are older than [minAgeMs] (so a drawing being attached right now is never swept).
     */
    suspend fun sweep(referencedIds: Set<String>, now: Long, minAgeMs: Long = 10 * 60 * 1000L): Int = locked { index ->
        val keep = referencedIds + index.byName.values
        var removed = 0
        customDir.listFiles()?.forEach { f ->
            val id = f.name.removeSuffix(".webp").removeSuffix(".cut")
            val stale = now - f.lastModified() > minAgeMs
            val orphanTmp = f.name.endsWith(".tmp")
            if (f.isFile && stale && (orphanTmp || id !in keep) && f.delete()) removed++
        }
        removed
    }

    fun fileFor(id: String): File = File(customDir, "$id.webp")

    /** The die-cut path file of icon [id] (absent for legacy icons). */
    fun cutFileFor(id: String): File = File(customDir, "$id.cut")

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

        /** At most this many names wait for a connection (the oldest go first). */
        private const val MAX_DRAW_LATER = 200
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

package earth.cora.picnic.core.store

import earth.cora.picnic.core.CoreJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One versioned JSON file on disk (ARCHITECTURE §6.1, §6.2, §6.4; docs/DESIGN_STICKER.md §19.3): the generic
 * part of JsonFileStateStore (state.json) and JsonFileAlbumStore (album.json). Blocking: callers run it on
 * their IO dispatcher and serialize access. CONTRACT:
 * - [read]: a missing file -> [initial] (nothing is written). An unreadable file (not JSON, wrong shape, a
 *   failing migration) is renamed to "<name>.corrupt-<epochMillis>" and [initial] is used: never crash on a
 *   bad file. A file without "version" is version 1. An older version runs [migrate] up to [version]; a NEWER
 *   one (a downgrade) is decoded leniently. Either way the original bytes are kept as "<name>.v<old>.bak"
 *   before the first write (an existing backup is never overwritten). The decoded value goes through [repair],
 *   which also stamps the current version.
 * - [write]: encodes to "<name>.tmp" in the same directory, fsyncs, then moves it over the file with
 *   ATOMIC_MOVE + REPLACE_EXISTING. Throws IOException when that fails (the old file stays intact).
 */
class JsonFileStore<T>(
    val file: File,
    private val serializer: KSerializer<T>,
    private val version: Int,
    /** Raw JSON of version N (N < [version]) -> raw JSON of [version]. */
    private val migrate: (raw: JsonObject, fromVersion: Int) -> JsonObject,
    /** Restores invariants after decoding and stamps [version]. */
    private val repair: (T) -> T,
    /** The value used when there is no (readable) file. */
    private val initial: () -> T,
    private val json: Json = CoreJson.json,
) {
    /** Version of an older or newer file whose original must be copied aside before we overwrite it. */
    private var pendingBackupVersion: Int? = null

    fun read(): T {
        if (!file.exists()) return initial()
        return try {
            val raw = json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
                ?: throw SerializationException("${file.name} is not a JSON object")
            val fileVersion = (raw["version"] as? JsonPrimitive)?.intOrNull ?: 1
            val upgraded = when {
                fileVersion < version -> migrate(raw, fileVersion)
                else -> raw
            }
            val decoded = json.decodeFromJsonElement(serializer, upgraded)
            repair(decoded).also { pendingBackupVersion = fileVersion.takeIf { it != version } }
        } catch (e: SerializationException) {
            quarantine()
        } catch (e: IllegalArgumentException) {
            quarantine()
        } catch (e: IllegalStateException) {
            quarantine()
        } catch (e: IOException) {
            quarantine()
        }
    }

    fun write(value: T) {
        val dir = file.absoluteFile.parentFile
        if (dir != null && !dir.exists()) dir.mkdirs()
        pendingBackupVersion?.let { old ->
            val backup = File(dir, "${file.name}.v$old.bak")
            if (file.exists() && !backup.exists()) Files.copy(file.toPath(), backup.toPath())
            pendingBackupVersion = null
        }
        val bytes = json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
        val tmp = File(dir, "${file.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Moves an unreadable file aside and starts from [initial]. */
    private fun quarantine(): T {
        pendingBackupVersion = null
        val aside = File(file.absoluteFile.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        if (!file.renameTo(aside)) file.delete()
        return initial()
    }
}

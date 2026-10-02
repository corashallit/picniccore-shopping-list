package earth.cora.picnic.core.store

import earth.cora.picnic.core.CoreJson
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.STATE_SCHEMA_VERSION
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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
 * Single source of truth for AppState. CONTRACT (docs/ARCHITECTURE.md §6):
 * - [states] emits the current state (loading it on first use), then every committed update.
 *   It never emits a state that failed to persist.
 * - [update] runs [transform] under a Mutex (updates are serialized), persists the result, then emits it.
 *   If transform returns the same instance, nothing is written or emitted.
 * - All functions are main-safe (I/O happens on the injected IO dispatcher).
 */
interface StateStore {
    val states: Flow<AppState>

    /** Loads if needed and returns the current state. */
    suspend fun current(): AppState

    /** Returns the committed new state. Throws java.io.IOException if persisting failed (state unchanged). */
    suspend fun update(transform: (AppState) -> AppState): AppState
}

/**
 * state.json on disk (Android: File(context.filesDir, "state.json")).
 * Writes go to "<name>.tmp" in the same directory, are fsynced, then moved over the target with
 * ATOMIC_MOVE + REPLACE_EXISTING. Unreadable files are renamed to "<name>.corrupt-<epochMillis>"
 * and a fresh [initial] state is used. Older schema versions run through [StateMigrations];
 * a NEWER version is loaded leniently and a copy is kept as "<name>.v<version>.bak" before the first write.
 *
 * [schemaVersion] and [migrate] exist for tests; the app uses the defaults.
 */
class JsonFileStateStore(
    private val file: File,
    /** Called once when no file exists (or it was corrupt): e.g. { StateOps.initialState(uuid(), now()) }. */
    private val initial: () -> AppState,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val schemaVersion: Int = STATE_SCHEMA_VERSION,
    private val migrate: (JsonObject, Int) -> JsonObject = StateMigrations::migrate,
) : StateStore {
    private val mutex = Mutex()
    private val loaded = MutableStateFlow<AppState?>(null)

    /** Version of a newer-schema file that must be backed up before we overwrite it (downgrade). */
    private var pendingBackupVersion: Int? = null

    override val states: Flow<AppState> = flow {
        if (loaded.value == null) current()
        emitAll(loaded.filterNotNull())
    }

    override suspend fun current(): AppState = loaded.value ?: mutex.withLock { loadLocked() }

    override suspend fun update(transform: (AppState) -> AppState): AppState = mutex.withLock {
        val current = loadLocked()
        val next = transform(current)
        if (next === current) return@withLock current
        withContext(ioDispatcher) { write(next) }
        loaded.value = next
        next
    }

    private suspend fun loadLocked(): AppState {
        loaded.value?.let { return it }
        val state = withContext(ioDispatcher) { read() }
        loaded.value = state
        return state
    }

    private fun read(): AppState {
        if (!file.exists()) return initial()
        return try {
            val raw = CoreJson.json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
                ?: throw SerializationException("state.json is not a JSON object")
            val version = (raw["version"] as? JsonPrimitive)?.intOrNull ?: 1
            val upgraded = when {
                version < schemaVersion -> migrate(raw, version)
                version > schemaVersion -> raw.also { pendingBackupVersion = version }
                else -> raw
            }
            val decoded = CoreJson.json.decodeFromJsonElement(AppState.serializer(), upgraded)
            repair(decoded).copy(version = schemaVersion)
        } catch (e: SerializationException) {
            quarantine()
        } catch (e: IllegalArgumentException) {
            quarantine()
        } catch (e: IOException) {
            quarantine()
        }
    }

    /** Keeps the invariants the app relies on: at least one list, and a current list that exists. */
    private fun repair(state: AppState): AppState {
        val withList = if (state.lists.isEmpty()) {
            val fresh = initial()
            state.copy(lists = fresh.lists, currentListId = fresh.currentListId)
        } else {
            state
        }
        return if (withList.lists.any { it.id == withList.currentListId }) {
            withList
        } else {
            withList.copy(currentListId = withList.lists.first().id)
        }
    }

    /** Moves an unreadable file aside (never crash on a bad file) and starts fresh. */
    private fun quarantine(): AppState {
        pendingBackupVersion = null
        val aside = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        if (!file.renameTo(aside)) file.delete()
        return initial()
    }

    private fun write(state: AppState) {
        val dir = file.absoluteFile.parentFile
        if (dir != null && !dir.exists()) dir.mkdirs()
        pendingBackupVersion?.let { version ->
            val backup = File(dir, "${file.name}.v$version.bak")
            if (file.exists() && !backup.exists()) Files.copy(file.toPath(), backup.toPath())
            pendingBackupVersion = null
        }
        val bytes = CoreJson.json.encodeToString(AppState.serializer(), state).toByteArray(Charsets.UTF_8)
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
}

/** Non-persistent store for tests, previews and screenshot tests. */
class InMemoryStateStore(initial: AppState) : StateStore {
    private val flow = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val states: Flow<AppState> = flow.asStateFlow()

    override suspend fun current(): AppState = flow.value

    override suspend fun update(transform: (AppState) -> AppState): AppState = mutex.withLock {
        val next = transform(flow.value)
        if (next !== flow.value) flow.value = next
        next
    }
}

package earth.cora.picnic.core.store

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
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Single source of truth for AppState. CONTRACT (docs/ARCHITECTURE.md §6):
 * - [states] emits the current state (loading it on first use), then every update.
 * - [update] runs [transform] under a Mutex (updates are serialized), emits the result at once (so what she did shows
 *   from her tap, not from the fsync: the check-off chain's beats are measured from the tap, §8.3), then persists it.
 *   If persisting fails, the previous state is emitted again and the call throws: a state that failed to persist never
 *   stays. If transform returns the same instance, nothing is written or emitted.
 * - All functions are main-safe (I/O happens on the injected IO dispatcher).
 */
interface StateStore {
    val states: Flow<AppState>

    /** Loads if needed and returns the current state. */
    suspend fun current(): AppState

    /** Returns the committed new state. Throws java.io.IOException if persisting failed (the state is rolled back). */
    suspend fun update(transform: (AppState) -> AppState): AppState
}

/**
 * state.json on disk (Android: File(context.filesDir, "state.json")), on the generic [JsonFileStore]:
 * writes go to "<name>.tmp" in the same directory, are fsynced, then moved over the target with
 * ATOMIC_MOVE + REPLACE_EXISTING. Unreadable files are renamed to "<name>.corrupt-<epochMillis>"
 * and a fresh [initial] state is used. Older schema versions run through [StateMigrations] (v1 -> v2 is a real
 * step, §7.2) and a NEWER version is loaded leniently; either way the original is kept as
 * "<name>.v<version>.bak" before the first write.
 *
 * [schemaVersion], [migrate] and [clock] exist for tests; the app uses the defaults.
 */
class JsonFileStateStore(
    private val file: File,
    /** Called once when no file exists (or it was corrupt): e.g. { StateOps.initialState(uuid(), now()) }. */
    private val initial: () -> AppState,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val schemaVersion: Int = STATE_SCHEMA_VERSION,
    /** Epoch millis for the migration (the v2 step stamps trips with it). */
    private val clock: () -> Long = System::currentTimeMillis,
    private val migrate: (JsonObject, Int) -> JsonObject = { raw, from -> StateMigrations.migrate(raw, from, clock()) },
) : StateStore {
    private val mutex = Mutex()
    private val loaded = MutableStateFlow<AppState?>(null)
    private val disk = JsonFileStore(
        file = file,
        serializer = AppState.serializer(),
        version = schemaVersion,
        migrate = { raw, from -> migrate(raw, from) },
        repair = { repair(it).copy(version = schemaVersion) },
        initial = initial,
    )

    override val states: Flow<AppState> = flow {
        if (loaded.value == null) current()
        emitAll(loaded.filterNotNull())
    }

    override suspend fun current(): AppState = loaded.value ?: mutex.withLock { loadLocked() }

    override suspend fun update(transform: (AppState) -> AppState): AppState = mutex.withLock {
        val current = loadLocked()
        val next = transform(current)
        if (next === current) return@withLock current
        // Published before the write (the tap's chain starts now), rolled back if the write fails.
        loaded.value = next
        try {
            withContext(ioDispatcher) { disk.write(next) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // The blocking write ran to its end before the cancellation surfaced: the file holds [next].
            throw e
        } catch (e: Throwable) {
            if (loaded.value === next) loaded.value = current
            throw e
        }
        next
    }

    private suspend fun loadLocked(): AppState {
        loaded.value?.let { return it }
        val state = withContext(ioDispatcher) { disk.read() }
        loaded.value = state
        return state
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

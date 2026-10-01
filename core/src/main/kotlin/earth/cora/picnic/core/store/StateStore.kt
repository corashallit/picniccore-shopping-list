package earth.cora.picnic.core.store

import earth.cora.picnic.core.model.AppState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

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
 */
class JsonFileStateStore(
    private val file: File,
    /** Called once when no file exists (or it was corrupt): e.g. { StateOps.initialState(uuid(), now()) }. */
    private val initial: () -> AppState,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateStore {
    override val states: Flow<AppState>
        get() = TODO("core: JsonFileStateStore.states")

    override suspend fun current(): AppState = TODO("core: JsonFileStateStore.current")

    override suspend fun update(transform: (AppState) -> AppState): AppState =
        TODO("core: JsonFileStateStore.update")
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

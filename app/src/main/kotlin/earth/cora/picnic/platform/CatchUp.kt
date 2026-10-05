package earth.cora.picnic.platform

import earth.cora.picnic.core.assistant.AisleSyncReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Catch-up when the connection comes back (docs/DESIGN_STICKER.md §0.4, 5 October; docs/ARCHITECTURE.md §10.1). What
 * was added offline went on as typed and sits in Everything else; stickers waited; a forecast may have failed. When a
 * usable connection arrives (NetworkStatus.arrivals, debounced) and at a cold start, this does what waited: the icon
 * cascade resumes (PENDING items, names waiting to be drawn), every item still needing an aisle is sorted
 * (AisleSync.sortPending), the pantry's unknown stickers are decided, and a forecast that is due or failed is asked for.
 */

/** What a catch-up sorted, for the list's note ("Sorted 2 items · 1 left in Everything else"); shown once. */
data class CatchUpNote(val id: Long, val sorted: Int, val left: Int)

/** The list's side of the catch-up: its note, until the list has shown it. */
interface CatchUpNotes {
    val note: StateFlow<CatchUpNote?>

    /** The list showed note [id]: it is not shown again. */
    fun shown(id: Long)

    /** No catch-up (previews, tests that do not care). */
    object None : CatchUpNotes {
        override val note: StateFlow<CatchUpNote?> = MutableStateFlow<CatchUpNote?>(null).asStateFlow()

        override fun shown(id: Long) = Unit
    }
}

/**
 * The catch-up. [start] runs it once at the cold start (the icon cascade always resumes, offline too, for its once-per-
 * process sweep; the rest only online with a key) and then after every arrival of a connection, [debounceMs] after the
 * last one. Never two at once: a run that is going on absorbs the arrivals that come meanwhile (one more run follows),
 * and runs are at least [minGapMs] apart (a connection that flaps waits, never piles up calls). Without a key nothing
 * is sorted, decided or drawn ([hasKey]); the forecast does not need one ([weather] checks its own switch).
 */
class CatchUp(
    private val network: NetworkStatus,
    private val hasKey: suspend () -> Boolean,
    /** IconRepository.resumePending (returns at once; the cascade runs in the app scope). */
    private val resumeStickers: () -> Unit,
    /** AisleSync.sortPending. */
    private val sortPending: suspend () -> AisleSyncReport,
    /** The pantry's unknown stickers (PantryIcons.decidePending). */
    private val pantryStickers: suspend () -> Unit,
    /** The weather eggs' forecast, if it is due or the last ask failed (SurpriseEngine.onBackOnline). */
    private val weather: () -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val minGapMs: Long = MIN_GAP_MS,
) : CatchUpNotes {
    private val _note = MutableStateFlow<CatchUpNote?>(null)
    override val note: StateFlow<CatchUpNote?> = _note.asStateFlow()

    private val running = Mutex()
    private val started = AtomicBoolean(false)
    @Volatile
    private var lastRunAt: Long? = null
    private var ids = 0L

    /** Runs that did their work (tests). */
    @Volatile
    var runs: Int = 0
        private set

    override fun shown(id: Long) {
        _note.update { if (it?.id == id) null else it }
    }

    /** Once per process (AppContainer.startOnce): the cold start's catch-up, then one after each arrival. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            run(coldStart = true)
            network.arrivals.drop(1).debounce(debounceMs).conflate().collect {
                val since = lastRunAt?.let { clock() - it }
                if (since != null && since in 0 until minGapMs) delay(minGapMs - since)
                run(coldStart = false)
            }
        }
    }

    /**
     * One catch-up; false when it did nothing (another is running, the phone is offline, or there is no key). At a cold
     * start the icon cascade resumes whatever the network (it asks nothing offline) and the forecast is left to the list
     * opening (it checks it then).
     */
    suspend fun run(coldStart: Boolean): Boolean {
        if (!running.tryLock()) return false
        try {
            if (coldStart) resumeStickers()
            if (!network.online.value) return false
            lastRunAt = clock()
            if (!coldStart) safely("weather") { weather() }
            if (!hasKeySafely()) return false
            runs++
            if (!coldStart) resumeStickers()
            val report = try {
                sortPending()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PicnicLog.w("catch-up: sorting failed", e)
                null
            }
            if (report != null && report.recategorized > 0) {
                _note.value = CatchUpNote(++ids, report.recategorized, report.leftUnsorted)
            }
            safely("pantry stickers") { pantryStickers() }
            return true
        } finally {
            running.unlock()
        }
    }

    private suspend fun hasKeySafely(): Boolean = try {
        hasKey()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PicnicLog.w("catch-up: $what failed", e)
        }
    }

    companion object {
        /** A connection must hold this long before the catch-up runs (Wi-Fi often hands over in a few seconds). */
        const val DEBOUNCE_MS: Long = 3_000

        /** Catch-ups are at least this far apart. */
        const val MIN_GAP_MS: Long = 60_000
    }
}

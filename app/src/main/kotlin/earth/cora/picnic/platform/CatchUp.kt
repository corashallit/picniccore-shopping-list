package earth.cora.picnic.platform

import earth.cora.picnic.core.assistant.AisleSyncReport
import earth.cora.picnic.core.llm.transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/*
 * Catch-up when the connection comes back (docs/DESIGN_STICKER.md §0.4, 5 October; docs/ARCHITECTURE.md §10.1). What
 * was added offline went on as typed and sits in Everything else; stickers waited; a forecast may have failed. When a
 * usable connection arrives (NetworkStatus.arrivals, debounced) and at a cold start, this does what waited: the icon
 * cascade resumes (PENDING items, names waiting to be drawn), every item still needing an aisle is sorted
 * (AisleSync.sortPending), the pantry's unknown stickers are decided, and a forecast that is due or failed is asked for.
 * A phone can look online and still not reach OpenAI (a captive portal, a dead router): then no arrival comes, so a
 * sort that could not get through asks for a few retries of its own ([CatchUp.request], 1, 5 and 15 minutes later).
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
 * last one. Never two at once: runs take turns on one lock, the arrivals that come during a run fold into one more run
 * after it, and runs after arrivals are at least [minGapMs] apart (a connection that flaps waits, never piles up calls).
 * Without a key nothing is sorted, decided or drawn ([hasKey]); the forecast does not need one ([weather] checks its own
 * switch).
 *
 * Retries: when sorting could not get through while the phone looked online (the add sheet's [request], or a run whose
 * sort failed on the way), the catch-up runs again [RETRY_BACKOFF_MS] later (1 min, then 5, then 15), through the same
 * lock and the same gap, until the items are sorted; after the third it stops until the next arrival, cold start or
 * [request]. The retries leave the forecast alone (it has its own 15-minute retry).
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

    /** Items still need an aisle and the last try could not get through (cleared when a sort gets an answer). */
    @Volatile
    private var sortOwed = false

    /** Counts [request]s: one that comes while a run sorts keeps the sort owed (its items may be newer than the run's). */
    private val requests = AtomicLong()
    private val retryLock = Any()
    private var retries: Job? = null

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
        // Arrivals are followed from now, also while the cold start's run goes on (that one waits its turn after it).
        val seen = network.arrivals.value
        scope.launch { run(coldStart = true) }
        scope.launch {
            network.arrivals.filter { it != seen }.debounce(debounceMs).conflate().collect {
                val since = lastRunAt?.let { clock() - it }
                if (since != null && since in 0 until minGapMs) delay(minGapMs - since)
                run(coldStart = false)
            }
        }
    }

    /**
     * Something was added while the phone looked online but its sorting could not reach OpenAI (AddViewModel): the
     * catch-up tries again 1, 5 and 15 minutes later until it is sorted (see the class comment). A request while the
     * retries are already waiting changes nothing.
     */
    fun request() {
        requests.incrementAndGet()
        sortOwed = true
        retryLater()
    }

    private fun retryLater() {
        synchronized(retryLock) {
            if (retries?.isActive == true) return
            retries = scope.launch {
                for (wait in RETRY_BACKOFF_MS) {
                    delay(wait)
                    val since = lastRunAt?.let { clock() - it }
                    if (since != null && since in 0 until minGapMs) delay(minGapMs - since)
                    if (!sortOwed) return@launch
                    // Offline or no key: nothing to retry now (the next arrival or cold start does it).
                    if (!run(coldStart = false, retry = true)) return@launch
                    if (!sortOwed) return@launch
                }
            }
        }
    }

    /**
     * One catch-up, after any other that is going on; false when it did nothing (the phone is offline, or there is no
     * key). At a cold start the icon cascade resumes whatever the network (it asks nothing offline) and the forecast is
     * left to the list opening (it checks it then). A [retry] leaves the forecast alone too.
     */
    suspend fun run(coldStart: Boolean, retry: Boolean = false): Boolean = running.withLock {
        if (coldStart) resumeStickers()
        if (!network.online.value) return@withLock false
        lastRunAt = clock()
        if (!coldStart && !retry) safely("weather") { weather() }
        if (!hasKeySafely()) {
            sortOwed = false
            return@withLock false
        }
        runs++
        if (!coldStart) resumeStickers()
        val asked = requests.get()
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
        // Sorting could not get through (the phone looked online): try again in a while. Any answer, or an error a
        // retry can't fix (the key, the quota), ends the retries, unless a request came in while it sorted.
        sortOwed = report?.error?.transport == true || requests.get() != asked
        if (sortOwed) retryLater()
        safely("pantry stickers") { pantryStickers() }
        true
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

        /** After a sort that could not get through: the waits before each retry (then none until something new). */
        val RETRY_BACKOFF_MS: List<Long> = listOf(60_000, 5 * 60_000, 15 * 60_000)
    }
}

package earth.cora.picnic.icons

import android.app.Application
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.assistant.IconDecision
import earth.cora.picnic.core.assistant.IconDrawRequest
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.assistant.resolve
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ResolvedIcon
import earth.cora.picnic.core.model.applyIconDecisions
import earth.cora.picnic.core.model.attachCustomIcon
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/** Knobs of the on-device icon cascade (docs/ARCHITECTURE.md §8.3). Tests shorten the delays. */
data class IconQueueTuning(
    /** The decider batcher waits this long after the last new name before calling. */
    val debounceMs: Long = 300,
    /** Names per IconDecider call. */
    val maxBatch: Int = 40,
    /** Concurrent live drawings. */
    val drawWorkers: Int = 2,
    /** Pause before the single retry of a drawing that hit a rate limit, network error or timeout. */
    val retryDelayMs: Long = 3_000,
    /** Automatic drawings per local day (a user "Draw a new icon" ignores it). */
    val dailyCap: Int = 40,
)

/**
 * Production [IconRepository]: the device side of the icon cascade (docs/ARCHITECTURE.md §8).
 *
 * - [submit] attaches a cached bespoke icon when one exists for the item's normalized name; sends
 *   PENDING names to the IconDecider batcher (step 2); queues APPROXIMATE names for a live drawing
 *   (step 3) when "Draw new icons with AI" is on, a token is set, the name isn't blocked and the
 *   daily cap allows it.
 * - The batcher is one coroutine: 300 ms debounce, at most 40 names per call. LlmError leaves the
 *   items PENDING (retried by [resumePending] on the next start).
 * - Drawing runs on [IconQueueTuning.drawWorkers] coroutines reading one channel (so at most 2 at
 *   once), retries once on RateLimited/Network/Timeout, blocks the name on a moderation refusal and
 *   gives up silently otherwise. Results are trimmed to 192 px WebP, cached on disk by name and
 *   attached to every item with that name in every list.
 *
 * Every public method returns immediately and never throws; the work runs in [scope] (the app scope).
 */
class AppIconRepository internal constructor(
    private val store: StateStore,
    private val settings: SettingsRepository,
    private val assistant: GroceryAssistant,
    private val decider: IconDecider,
    private val catalog: IconCatalog,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val cache: BespokeIconCache,
    private val bundledKeys: () -> Set<String>,
    private val styleReference: () -> ByteArray?,
    private val processor: IconPostProcessor,
    private val onIconReplaced: (String) -> Unit,
    private val tuning: IconQueueTuning,
    private val compute: CoroutineDispatcher,
    private val io: CoroutineDispatcher,
) : IconRepository {

    /** Production wiring (AppContainer). [onIconReplaced] receives a bespoke id whose file a redraw deleted. */
    constructor(
        app: Application,
        store: StateStore,
        settings: SettingsRepository,
        assistant: GroceryAssistant,
        iconDecider: IconDecider,
        catalog: IconCatalog,
        appScope: CoroutineScope,
        clock: () -> Long,
        bundled: BundledIcons = BundledIcons(app),
        onIconReplaced: (String) -> Unit = {},
    ) : this(
        store = store,
        settings = settings,
        assistant = assistant,
        decider = iconDecider,
        catalog = catalog,
        scope = appScope,
        clock = clock,
        cache = BespokeIconCache(iconsDir(app)),
        bundledKeys = { bundled.keys },
        styleReference = bundled::styleReference,
        processor = BitmapIconPostProcessor(),
        onIconReplaced = onIconReplaced,
        tuning = IconQueueTuning(),
        compute = Dispatchers.Default,
        io = Dispatchers.IO,
    )

    private val busyFlow = MutableStateFlow<Set<String>>(emptySet())
    override val busy: StateFlow<Set<String>> = busyFlow.asStateFlow()

    /** Reference counts behind [busy]: a name can be decided and redrawn at the same time. */
    private val busyCounts = HashMap<String, Int>()

    private val decideQueue = Channel<NamedItem>(Channel.UNLIMITED)
    private val drawQueue = Channel<DrawJob>(Channel.UNLIMITED)

    /** Serializes "is it busy? -> mark busy -> enqueue" so two submits never queue the same name twice. */
    private val intake = Mutex()
    private val started = AtomicBoolean(false)
    private val swept = AtomicBoolean(false)

    private val styleRefLock = Mutex()
    private var styleRefBytes: ByteArray? = null

    private data class DrawJob(val key: String, val name: String, val forced: Boolean, val day: String)

    override fun isBundled(key: String): Boolean = key in bundledKeys()

    override fun submit(items: List<GroceryItem>) {
        // Neediest item per name: PENDING before APPROXIMATE before EXACT.
        val byKey = items.asSequence()
            .filter { it.customIconId == null }
            .map { normalizeName(it.name) to it }
            .filter { it.first.isNotEmpty() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, group) -> group.minBy { it.iconFit.priority() } }
        if (byKey.isEmpty()) return
        ensureStarted()
        scope.launch {
            intake.withLock {
                byKey.forEach { (key, item) ->
                    try {
                        process(key, item)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        PicnicLog.w("icon intake failed", e)
                    }
                }
            }
        }
    }

    override fun resumePending() {
        ensureStarted()
        scope.launch {
            val state = try {
                store.current()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PicnicLog.w("resumePending: state unavailable", e)
                return@launch
            }
            val items = state.lists.flatMap { it.items }
            submit(items.filter { it.iconFit == IconFit.PENDING && it.customIconId == null })
            if (swept.compareAndSet(false, true)) {
                val referenced = items.mapNotNullTo(HashSet()) { it.customIconId }
                try {
                    val removed = cache.sweep(referenced, clock())
                    if (removed > 0) PicnicLog.i("swept $removed orphaned icon files")
                } catch (e: IOException) {
                    PicnicLog.w("icon sweep failed", e)
                }
            }
        }
    }

    override fun redraw(itemName: String) {
        val key = normalizeName(itemName)
        if (key.isEmpty()) return
        ensureStarted()
        scope.launch {
            intake.withLock {
                acquire(key, exclusive = false)
                drawQueue.send(DrawJob(key, itemName.trim(), forced = true, day = today()))
            }
        }
    }

    override fun forgetBespoke(itemName: String) {
        val key = normalizeName(itemName)
        if (key.isEmpty()) return
        scope.launch {
            try {
                cache.forget(key)
            } catch (e: IOException) {
                PicnicLog.w("forgetBespoke failed", e)
            }
        }
    }

    /* ---------------- intake ---------------- */

    private suspend fun process(key: String, item: GroceryItem) {
        val cached = try {
            cache.lookup(key)
        } catch (e: IOException) {
            null
        }
        if (cached != null) {
            safeUpdate { it.attachCustomIcon(key, cached, overwrite = false) }
            return
        }
        when (item.iconFit) {
            // Step 2 needs a token (no sparkle, no doomed NotConfigured call without one): the item
            // keeps its fallback icon and stays PENDING until resumePending() runs with a token set.
            IconFit.PENDING -> if (hasToken() && acquire(key, exclusive = true)) decideQueue.send(NamedItem(key, item.name.trim()))
            IconFit.APPROXIMATE -> requestAutoDraw(key, item.name.trim(), alreadyBusy = false)
            IconFit.EXACT -> Unit
        }
    }

    /** Queues an automatic drawing if every gate passes. With [alreadyBusy] the caller hands over its busy mark. */
    private suspend fun requestAutoDraw(key: String, name: String, alreadyBusy: Boolean) {
        if (!alreadyBusy && isBusy(key)) return
        val day = today()
        if (!autoDrawAllowed(key, day)) {
            if (alreadyBusy) release(key)
            return
        }
        if (!alreadyBusy && !acquire(key, exclusive = true)) {
            runCatching { cache.refundDaily(day) }
            return
        }
        drawQueue.send(DrawJob(key, name, forced = false, day = day))
    }

    private suspend fun autoDrawAllowed(key: String, day: String): Boolean {
        val s = try {
            settings.current()
        } catch (e: IOException) {
            return false
        }
        if (!s.drawIcons || !s.hasToken) return false
        return try {
            !cache.isBlocked(key) && stillApproximate(key) && cache.tryConsumeDaily(day, tuning.dailyCap)
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun hasToken(): Boolean = try {
        settings.current().hasToken
    } catch (e: IOException) {
        false
    }

    /** No learned icon override for the name, and some item still shows a stand-in icon. */
    private suspend fun stillWanted(key: String): Boolean =
        store.current().iconOverrides.none { it.name == key } && stillApproximate(key)

    /** Some item with this name still shows a stand-in icon (a user choice made meanwhile wins). */
    private suspend fun stillApproximate(key: String): Boolean = store.current().lists.any { list ->
        list.items.any { it.iconFit == IconFit.APPROXIMATE && it.customIconId == null && normalizeName(it.name) == key }
    }

    /* ---------------- step 2: the decider batcher ---------------- */

    private fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { decideLoop() }
        repeat(tuning.drawWorkers) { scope.launch { drawLoop() } }
    }

    private suspend fun decideLoop() {
        while (true) {
            val first = decideQueue.receive()
            val batch = LinkedHashMap<String, NamedItem>().apply { put(first.id, first) }
            // Debounce without losing elements: wait, drain what arrived, stop after a quiet period.
            while (batch.size < tuning.maxBatch) {
                delay(tuning.debounceMs)
                var arrived = false
                while (batch.size < tuning.maxBatch) {
                    val next = decideQueue.tryReceive().getOrNull() ?: break
                    batch.putIfAbsent(next.id, next)
                    arrived = true
                }
                if (!arrived) break
            }
            decideBatch(batch.values.toList())
        }
    }

    private suspend fun decideBatch(batch: List<NamedItem>) {
        val unreleased = batch.mapTo(LinkedHashSet()) { it.id }
        try {
            val decisions = decider.decide(batch)
            val resolved: Map<String, ResolvedIcon> =
                batch.associate { it.id to (decisions[it.id] ?: IconDecision.NONE).resolve(catalog) }
            if (!safeUpdate { it.applyIconDecisions(resolved) }) return
            intake.withLock {
                for (item in batch) {
                    unreleased.remove(item.id)
                    if (resolved.getValue(item.id).fit == IconFit.APPROXIMATE) {
                        requestAutoDraw(item.id, item.name, alreadyBusy = true)
                    } else {
                        release(item.id)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmError) {
            // Offline, no token, quota...: the items stay PENDING with the fallback icon.
            PicnicLog.w("icon decisions failed for ${batch.size} names", e)
        } catch (e: Exception) {
            PicnicLog.w("icon decisions failed for ${batch.size} names", e)
        } finally {
            unreleased.forEach(::release)
        }
    }

    /* ---------------- step 3: live drawing ---------------- */

    private suspend fun drawLoop() {
        for (job in drawQueue) {
            try {
                draw(job)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Decode/encode/disk problems: the item keeps its catalog icon.
                PicnicLog.w("icon drawing failed", e)
            } finally {
                release(job.key)
            }
        }
    }

    private suspend fun draw(job: DrawJob) {
        if (!job.forced) {
            // The setting may have been switched off while this waited in the queue.
            val enabled = try {
                settings.current().drawIcons
            } catch (e: IOException) {
                false
            }
            if (!enabled) {
                cache.refundDaily(job.day)
                return
            }
        }
        val reference = styleReferenceBytes()
        if (reference == null) {
            PicnicLog.w("no style reference bundled; live drawing disabled")
            if (!job.forced) cache.refundDaily(job.day)
            return
        }
        val raw = drawWithOneRetry(job, reference) ?: return
        // The user may have picked a catalog icon for this name while it was drawing (~10 s):
        // their choice wins, so an automatic result is dropped rather than cached and attached.
        if (!job.forced && !stillWanted(job.key)) return
        val webp = withContext(compute) { processor.process(raw) }
        val id = BespokeIconCache.newIconId(job.key, clock()) { cache.fileFor(it).exists() }
        val previous = cache.put(job.key, id, webp)
        if (!safeUpdate { it.attachCustomIcon(job.key, id, overwrite = job.forced) }) return
        if (job.forced && previous != null && previous != id) {
            // Every item with this name now points at the new id; the old file is unreferenced.
            cache.deleteFile(previous)
            onIconReplaced(previous)
        }
    }

    private suspend fun drawWithOneRetry(job: DrawJob, reference: ByteArray): ByteArray? {
        var retried = false
        while (true) {
            try {
                return assistant.drawIcon(IconDrawRequest(itemName = job.name, styleReference = reference)).bytes
            } catch (e: LlmError.Refusal) {
                PicnicLog.w("icon drawing refused by moderation; name blocked")
                cache.block(job.key)
                return null
            } catch (e: LlmError) {
                val transient = e is LlmError.RateLimited || e is LlmError.Network || e is LlmError.Timeout
                if (!transient || retried) {
                    PicnicLog.w("icon drawing gave up", e)
                    return null
                }
                retried = true
                delay(tuning.retryDelayMs)
            }
        }
    }

    private suspend fun styleReferenceBytes(): ByteArray? = styleRefLock.withLock {
        styleRefBytes ?: withContext(io) { styleReference() }?.also { styleRefBytes = it }
    }

    /* ---------------- helpers ---------------- */

    private suspend fun safeUpdate(transform: (AppState) -> AppState): Boolean = try {
        store.update(transform)
        true
    } catch (e: IOException) {
        PicnicLog.w("couldn't save icon change", e)
        false
    }

    private fun isBusy(key: String): Boolean = synchronized(busyCounts) { (busyCounts[key] ?: 0) > 0 }

    private fun acquire(key: String, exclusive: Boolean): Boolean = synchronized(busyCounts) {
        val count = busyCounts[key] ?: 0
        if (exclusive && count > 0) return false
        busyCounts[key] = count + 1
        busyFlow.value = busyCounts.keys.toSet()
        true
    }

    private fun release(key: String) {
        synchronized(busyCounts) {
            val count = busyCounts[key] ?: return
            if (count <= 1) busyCounts.remove(key) else busyCounts[key] = count - 1
            busyFlow.value = busyCounts.keys.toSet()
        }
    }

    private fun today(): String = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun IconFit.priority(): Int = when (this) {
        IconFit.PENDING -> 0
        IconFit.APPROXIMATE -> 1
        IconFit.EXACT -> 2
    }

    companion object {
        /** filesDir/icons: index.json + custom/<customIconId>.webp. */
        fun iconsDir(app: Application): File = File(app.filesDir, "icons")
    }
}

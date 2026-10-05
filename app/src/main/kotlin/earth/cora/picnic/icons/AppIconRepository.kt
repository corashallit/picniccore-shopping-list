package earth.cora.picnic.icons

import android.app.Application
import earth.cora.picnic.core.assistant.DrawOutcome
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.assistant.IconDecision
import earth.cora.picnic.core.assistant.IconDrawRequest
import earth.cora.picnic.core.assistant.LiveIconDrawer
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.assistant.StickerPalette
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
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.setRarity
import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.theme.gen.StickerManifest
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
import kotlin.time.Duration.Companion.milliseconds

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
    /** Automatic drawings per local day, legacy redraws included (a user "Draw a new icon" ignores it). */
    val dailyCap: Int = 40,
)

/** Composes the neighbour sheet for a live drawing ([SheetComposer] on the device; a fake in tests). */
fun interface SheetSource {
    /** Null when live drawing is impossible (no mask bundled). CPU-bound: the repository calls it off main. */
    fun compose(approximateKey: String?, category: String?, families: List<String>, text: String): LiveSheet?
}

/**
 * Egg 13's hooks (docs/DESIGN_STICKER.md §12.2; owner WP9, the app passes its SurpriseEngine): told when an automatic
 * live drawing starts for an APPROXIMATE item (its capsule may draw gold while it waits), and how it ended. [drawn]
 * returns the new sticker's rarity (1 in 100 JACKPOT), which the repository saves on the item. Default: nothing.
 */
interface LiveDrawJackpot {
    fun drawing(item: GroceryItem) = Unit

    fun drawn(item: GroceryItem): Rarity = Rarity.NONE

    fun failed(item: GroceryItem) = Unit

    object None : LiveDrawJackpot
}

/**
 * Production [IconRepository]: the device side of the icon cascade (docs/ARCHITECTURE.md §8) with live stickers v4
 * (docs/DESIGN_STICKER.md §18.3 step 5).
 *
 * - [submit] attaches a cached bespoke icon when one exists for the item's normalized name; sends PENDING names to
 *   the IconDecider batcher (step 2); queues APPROXIMATE names for a live drawing (step 3) when "Draw new icons with
 *   AI" is on, a token is set, the name isn't blocked and the daily cap allows it.
 * - The batcher is one coroutine: 300 ms debounce, at most 40 names per call. LlmError leaves the items PENDING
 *   (retried by [resumePending] on the next start). A "none" answer carries the drawing's brief (the look and the two
 *   sticker families), which travels with the drawing job.
 * - A drawing (on [IconQueueTuning.drawWorkers] coroutines reading one channel, so at most 2 at once): the brief
 *   ([LiveIconDrawer.brief]: the decider is asked once more only for a name that reached drawing without one), the
 *   neighbour sheet ([SheetSource]: the approximate key and the two closest same-category stickers, with the bundled
 *   mask), one `drawIcon` call retried once on RateLimited/Network/Timeout ([LiveIconDrawer.draw]), a moderation
 *   refusal blocks the name, then [LiveStickerProcessor] (palette snap, specks, size, gloss, die-cut) and the 208 px
 *   WebP plus its `.cut` file are cached by name and attached to every item with that name in every list.
 * - Legacy icons (drawn before v4, no `.cut` file) are redrawn in v4 in the background from [resumePending], oldest
 *   first, under the daily cap and only while a token is set and drawing is on. A replaced file is deleted only when
 *   no item, pantry entry or `album.json` ([albumRefs]) references it.
 *
 * Every public method returns immediately and never throws; the work runs in [scope] (the app scope).
 */
class AppIconRepository internal constructor(
    private val store: StateStore,
    private val settings: SettingsRepository,
    assistant: GroceryAssistant,
    private val decider: IconDecider,
    private val catalog: IconCatalog,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val cache: BespokeIconCache,
    private val bundledKeys: () -> Set<String>,
    private val sheets: SheetSource,
    private val processor: LiveStickerProcessor,
    private val albumRefs: suspend () -> Set<String>,
    private val onIconReplaced: (String) -> Unit,
    private val tuning: IconQueueTuning,
    private val compute: CoroutineDispatcher,
    private val io: CoroutineDispatcher,
    private val jackpot: LiveDrawJackpot = LiveDrawJackpot.None,
) : IconRepository {

    /**
     * Production wiring (AppContainer). [onIconReplaced] receives a bespoke id whose file a redraw deleted. [album]
     * keeps the icons filed in the sticker album alive through sweeps and redraws.
     */
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
        album: AlbumStore? = null,
        onIconReplaced: (String) -> Unit = {},
        jackpot: LiveDrawJackpot = LiveDrawJackpot.None,
    ) : this(
        store = store,
        settings = settings,
        assistant = assistant,
        decider = iconDecider,
        catalog = catalog,
        scope = appScope,
        clock = clock,
        cache = BespokeIconCache(iconsDir(app)),
        bundledKeys = { LIBRARY_KEYS },
        sheets = SheetComposer(app.resources, catalog, MaskCache(bundled::mask)::get),
        processor = BitmapLiveStickerProcessor(),
        albumRefs = { album?.referencedCustomIconIds().orEmpty() },
        onIconReplaced = onIconReplaced,
        tuning = IconQueueTuning(),
        compute = Dispatchers.Default,
        io = Dispatchers.IO,
        jackpot = jackpot,
    )

    private val drawer = LiveIconDrawer(
        assistant = assistant,
        decider = decider,
        retryDelay = tuning.retryDelayMs.milliseconds,
        sleep = { delay(it) },
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

    /**
     * One drawing. [brief] is the decider's "none" answer when the batcher just gave it (look, families, category);
     * [replaces] is the legacy icon id a background v4 redraw replaces.
     */
    private data class DrawJob(
        val key: String,
        val name: String,
        val forced: Boolean,
        val day: String,
        val brief: IconDecision? = null,
        val replaces: String? = null,
    )

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
                try {
                    val referenced = referencedIds(state)
                    val removed = if (referenced == null) 0 else cache.sweep(referenced, clock())
                    if (removed > 0) PicnicLog.i("swept $removed orphaned icon files")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PicnicLog.w("icon sweep failed", e)
                }
                redrawLegacy()
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
            IconFit.APPROXIMATE -> requestAutoDraw(key, item.name.trim(), alreadyBusy = false, brief = null)
            IconFit.EXACT -> Unit
        }
    }

    /** Queues an automatic drawing if every gate passes. With [alreadyBusy] the caller hands over its busy mark. */
    private suspend fun requestAutoDraw(key: String, name: String, alreadyBusy: Boolean, brief: IconDecision?, replaces: String? = null) {
        if (!alreadyBusy && isBusy(key)) return
        val day = today()
        if (!autoDrawAllowed(key, day, legacy = replaces != null)) {
            if (alreadyBusy) release(key)
            return
        }
        if (!alreadyBusy && !acquire(key, exclusive = true)) {
            runCatching { cache.refundDaily(day) }
            return
        }
        drawQueue.send(DrawJob(key, name, forced = false, day = day, brief = brief, replaces = replaces))
    }

    private suspend fun autoDrawAllowed(key: String, day: String, legacy: Boolean): Boolean {
        val s = try {
            settings.current()
        } catch (e: IOException) {
            return false
        }
        if (!s.drawIcons || !s.hasToken) return false
        return try {
            !cache.isBlocked(key) && (legacy || stillApproximate(key)) && cache.tryConsumeDaily(day, tuning.dailyCap)
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun hasToken(): Boolean = try {
        settings.current().hasToken
    } catch (e: IOException) {
        false
    }

    /** Whether a finished automatic drawing is still wanted (the user may have chosen meanwhile: their choice wins). */
    private suspend fun stillWanted(job: DrawJob): Boolean {
        // A legacy redraw: the index still points at the icon it replaces (forgetBespoke drops it).
        if (job.replaces != null) return (try { cache.lookup(job.key) } catch (e: IOException) { null }) == job.replaces
        return store.current().iconOverrides.none { it.name == job.key } && stillApproximate(job.key)
    }

    /** Some item with this name still shows its approximate (generic) sticker (a user choice made meanwhile wins). */
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
                        requestAutoDraw(item.id, item.name, alreadyBusy = true, brief = decisions[item.id])
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

    /* ---------------- step 3: live drawing (v4) ---------------- */

    /** Queues a v4 redraw of every legacy icon still in use, oldest first, under the daily cap. */
    private suspend fun redrawLegacy() {
        val legacy = try {
            cache.legacyIcons()
        } catch (e: IOException) {
            return
        }
        if (legacy.isEmpty()) return
        val state = store.current()
        intake.withLock {
            for ((key, id) in legacy) {
                // The name shown on the items that carry it (the index key is only its normalized form).
                val name = state.lists.asSequence().flatMap { it.items }.firstOrNull { it.customIconId == id }?.name?.trim()
                    ?: state.pantry.firstOrNull { it.icon?.customIconId == id }?.name?.trim()
                    ?: continue
                requestAutoDraw(key, name, alreadyBusy = false, brief = null, replaces = id)
            }
        }
    }

    private suspend fun drawLoop() {
        for (job in drawQueue) {
            // Egg 13: an automatic drawing for an APPROXIMATE item may come out a jackpot.
            val lucky = luckyItem(job)
            lucky?.let { jackpot.drawing(it.second) }
            try {
                draw(job)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Decode/encode/disk problems or an unusable answer: the item keeps its catalog icon.
                PicnicLog.w("icon drawing failed", e)
            } finally {
                release(job.key)
            }
            lucky?.let { (listId, item) -> settleJackpot(listId, item) }
        }
    }

    /** The APPROXIMATE item an automatic drawing is for (egg 13), with its list id; null for redraws or without hooks. */
    private suspend fun luckyItem(job: DrawJob): Pair<String, GroceryItem>? {
        if (job.forced || job.replaces != null || jackpot === LiveDrawJackpot.None) return null
        val state = try {
            store.current()
        } catch (e: IOException) {
            return null
        }
        return state.lists.firstNotNullOfOrNull { list ->
            list.items.firstOrNull { it.iconFit == IconFit.APPROXIMATE && it.customIconId == null && normalizeName(it.name) == job.key }
                ?.let { list.id to it }
        }
    }

    /** Egg 13: the drawing was attached, so the sticker gets its rarity (saved); otherwise the gold capsule goes. */
    private suspend fun settleJackpot(listId: String, item: GroceryItem) {
        val now = try {
            store.current().lists.firstOrNull { it.id == listId }?.items?.firstOrNull { it.id == item.id }
        } catch (e: IOException) {
            null
        }
        if (now?.customIconId == null) {
            jackpot.failed(item)
            return
        }
        val rarity = jackpot.drawn(now)
        if (rarity != Rarity.NONE) safeUpdate { it.setRarity(listId, item.id, rarity) }
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
        // The brief: what to draw and in which two families (the decider's look, else the plain name).
        val shown = shownIcon(job)
        // The batcher's answer is used as it is, even without a look (asking the same question again rarely helps).
        val brief = job.brief ?: drawer.brief(job.key, job.name, null)
        val families = StickerPalette.normalize(
            brief?.families.orEmpty().ifEmpty { shown?.let { StickerManifest.entry(it)?.families }.orEmpty() },
        )
        val subject = brief?.look ?: LiveIconDrawer.fallbackSubject(job.name)
        val category = brief?.category ?: shown?.let { catalog[it]?.category }
        val sheet = withContext(compute) { sheets.compose(shown, category, families, "${job.name} $subject") }
        if (sheet == null) {
            PicnicLog.w("no live-drawing mask bundled; live drawing disabled")
            if (!job.forced) cache.refundDaily(job.day)
            return
        }
        val raw = when (val outcome = drawer.draw(IconDrawRequest(job.name, sheet.png, sheet.mask, subject, families))) {
            is DrawOutcome.Drawn -> outcome.icon.bytes
            DrawOutcome.Refused -> {
                PicnicLog.w("icon drawing refused by moderation; name blocked")
                cache.block(job.key)
                return
            }
            is DrawOutcome.Failed -> {
                PicnicLog.w("icon drawing gave up", outcome.error)
                return
            }
        }
        // The user may have picked a catalog icon for this name while it was drawing (~10 s):
        // their choice wins, so an automatic result is dropped rather than cached and attached.
        if (!job.forced && !stillWanted(job)) return
        val sticker = withContext(compute) { processor.process(raw, families, sheet.targetAreaU2) }
        sticker.stats?.let { PicnicLog.i("live sticker v4 in ${"%.0f".format(it.millis)} ms (${sheet.neighbours.joinToString()})") }
        val id = BespokeIconCache.newIconId(job.key, clock()) { cache.fileFor(it).exists() }
        val previous = cache.put(job.key, id, sticker.webp, sticker.cut)
        val attached = safeUpdate { state ->
            if (job.replaces != null) state.replaceCustomIcon(job.replaces, id) else state.attachCustomIcon(job.key, id, overwrite = job.forced)
        }
        if (!attached) return
        // The icon this one replaces (a redraw, a legacy redraw): its file goes only when nothing references it any
        // more; an icon filed in the album keeps its file (§18.3 step 5).
        for (old in setOfNotNull(previous, job.replaces)) {
            if (old == id) continue
            val referenced = referencedIds(store.current()) ?: break
            if (cache.deleteIfUnreferenced(old, referenced)) onIconReplaced(old)
        }
    }

    /** The catalog key the name's items show now (the approximate key the sheet starts from), if any. */
    private suspend fun shownIcon(job: DrawJob): String? {
        val items = store.current().lists.asSequence().flatMap { it.items }.filter { normalizeName(it.name) == job.key }.toList()
        val pick = items.firstOrNull { it.iconFit == IconFit.APPROXIMATE } ?: items.firstOrNull()
        return pick?.icon?.takeIf { it in catalog }
    }

    /**
     * Every custom icon id something still shows: items, pantry entries and the album. Null when the album can't be
     * read: then nothing is deleted (a filed sticker's art must never go).
     */
    private suspend fun referencedIds(state: AppState): Set<String>? {
        val ids = HashSet<String>()
        state.lists.forEach { list -> list.items.forEach { it.customIconId?.let(ids::add) } }
        state.pantry.forEach { entry -> entry.icon?.customIconId?.let(ids::add) }
        ids += try {
            albumRefs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PicnicLog.w("album references unavailable; keeping every icon file", e)
            return null
        }
        return ids
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

    /** Reads the bundled mask once (it is the same 6 KB for every drawing). */
    private class MaskCache(private val read: () -> ByteArray?) {
        @Volatile private var bytes: ByteArray? = null

        fun get(): ByteArray? = bytes ?: read()?.also { bytes = it }
    }

    companion object {
        /** filesDir/icons: index.json + custom/<customIconId>.webp and .cut. */
        fun iconsDir(app: Application): File = File(app.filesDir, "icons")

        /** Keys with library art (§19.3: isBundled reads StickerManifest, no longer assets/icons/<key>.webp). */
        private val LIBRARY_KEYS: Set<String> by lazy {
            StickerManifest.entries.mapNotNullTo(HashSet()) { e -> e.key.takeUnless { it == StickerManifest.CAPSULE } }
        }
    }
}

/** Points every item and pantry entry that shows icon [old] at [new] (a legacy icon's v4 redraw). */
internal fun AppState.replaceCustomIcon(old: String, new: String): AppState = copy(
    lists = lists.map { list ->
        if (list.items.none { it.customIconId == old }) list
        else list.copy(items = list.items.map { if (it.customIconId == old) it.copy(customIconId = new) else it })
    },
    pantry = pantry.map { entry ->
        val icon = entry.icon
        if (icon?.customIconId == old) entry.copy(icon = icon.copy(customIconId = new)) else entry
    },
)

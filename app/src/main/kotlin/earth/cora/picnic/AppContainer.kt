package earth.cora.picnic

import android.app.Application
import android.util.Log
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.assistant.OpenAiGroceryAssistant
import earth.cora.picnic.core.assistant.ResponsesIconDecider
import earth.cora.picnic.core.catalog.BasketCatalog
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.ConfigSource
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.llm.TokenSource
import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.core.calendar.SunClock
import earth.cora.picnic.core.model.StateOps
import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.JsonFileAlbumStore
import earth.cora.picnic.core.store.JsonFileStateStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.core.store.TripFiling
import earth.cora.picnic.icons.AppIconRepository
import earth.cora.picnic.icons.AppStickerArt
import earth.cora.picnic.icons.IconRepository
import earth.cora.picnic.icons.StickerArt
import earth.cora.picnic.platform.AndroidAnnouncer
import earth.cora.picnic.platform.AndroidFeedback
import earth.cora.picnic.platform.AndroidTimeouts
import earth.cora.picnic.platform.Announcer
import earth.cora.picnic.platform.AudioRecorder
import earth.cora.picnic.platform.CapturePhotoPreparer
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.PhotoPreparer
import earth.cora.picnic.platform.Timeouts
import earth.cora.picnic.platform.VoiceRecorder
import earth.cora.picnic.settings.DataStoreSettingsRepository
import earth.cora.picnic.settings.KeystoreTokenStore
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.eggs.NoOpSurpriseEngine
import earth.cora.picnic.ui.eggs.SurpriseEngine
import earth.cora.picnic.ui.eggs.ThemeVariantSource
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.itemsNeedingAisle
import earth.cora.picnic.platform.Captures
import earth.cora.picnic.platform.PicnicLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manual DI graph (one per process, owned by PicnicApp). CONTRACT (docs/ARCHITECTURE.md §10.1):
 * everything is lazy and touching a property never blocks the main thread on I/O.
 * ViewModels take the individual dependencies (not the container) so tests can pass fakes.
 * Models are NOT configured here: core's ModelDefaults is the one place (amendment A1).
 * The art pass (docs/DESIGN_STICKER.md §21): this file belongs to the lead; packages send their hunks
 * (a new service's construction) and the lead merges them. Phase 0 wires the C services with their stubs.
 */
class AppContainer(private val app: Application) {
    /**
     * Process-lifetime scope for work that must outlive a screen (commits, aisle sync, icon cascade).
     * The handler is a last-resort net: background work logs a stray exception instead of crashing
     * the app mid-shop. Callers still handle their expected errors (LlmError, IOException).
     */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.e("Picnic", "Uncaught exception in app scope", e)
        },
    )

    /** Epoch millis. */
    val clock: () -> Long = System::currentTimeMillis

    /** New random id for lists and items. */
    fun newId(): String = UUID.randomUUID().toString()

    val catalog: IconCatalog by lazy { IconCatalog.default }

    val baskets: BasketCatalog get() = catalog.baskets

    val store: StateStore by lazy {
        JsonFileStateStore(
            file = File(app.filesDir, "state.json"),
            initial = { StateOps.initialState(newId(), clock()) },
        )
    }

    /** DataStore (draw-icons, install id, key hint) + the Keystore-encrypted OpenAI key. */
    val settings: SettingsRepository by lazy { DataStoreSettingsRepository(app, KeystoreTokenStore(app)) }

    /** OpenAI's base URL + the safety identifier, read before every call. */
    val configSource: ConfigSource = ConfigSource { settings.llmConfig() }

    /** The token is decrypted per request and never stored anywhere else. */
    val tokenSource: TokenSource = TokenSource { settings.token() }

    private val client: OpenAiClient by lazy { OpenAiClient(configSource, tokenSource) }

    val assistant: GroceryAssistant by lazy { OpenAiGroceryAssistant(client, catalog) }

    /**
     * Icon cascade step 2. Today: gpt-6-luna on the Responses API. When OpenAI's Decisions API is
     * enabled for the project and its schema is public, swap the implementation here
     * (docs/ARCHITECTURE.md §8.6); nothing else changes.
     */
    val iconDecider: IconDecider by lazy { ResponsesIconDecider(client, catalog) }

    val aisleSync: AisleSync by lazy { AisleSync(store, assistant, baskets, clock) }

    /**
     * The sticker album: filesDir/album.json, next to state.json on the backup allow-list (§7.2, amendment A5).
     * Filing writes it BEFORE state.json (TripFiling); [startOnce] keeps it in step with every committed state.
     */
    val album: AlbumStore by lazy { JsonFileAlbumStore(File(app.filesDir, "album.json")) }

    /**
     * Sounds + haptics (§14): SoundPool sonification gated by Surprises → Sounds and the ringer, View haptics
     * gated by Surprises → Haptics. Built in [startOnce], before MainActivity resumes, so its window tracker
     * sees the activity.
     */
    val feedback: Feedback by lazy { AndroidFeedback.create(app, settings.settings, appScope) }

    /** Polite TalkBack announcements (§15), de-duplicated within 1 s. */
    val announcer: Announcer by lazy { AndroidAnnouncer(app) }

    /** "Time to take action" and "Touch & hold delay" (§9, §15), read live from the phone's settings. */
    val timeouts: Timeouts by lazy { AndroidTimeouts(app) }

    /** Where sunset and the forecast are computed (§0.4 Q6). Phase 0: Toronto until "Use my area" lands (WP8 · WP9). */
    val places: PlaceSource get() = PlaceSource.Toronto

    /** Every Easter-egg trigger (§12). Phase 0: no eggs until WP9's engine lands. */
    val surprises: SurpriseEngine get() = NoOpSurpriseEngine

    /** Day / Glow / Dusk / Golden (§2.5); MainActivity feeds it the system dark mode (WP4). */
    val themeVariants: ThemeVariantSource by lazy { ThemeVariantSource(settings, clock, SunClock, places) }

    /**
     * Sticker art for ItemIcon (§3.3, §19.2), provided to the theme as LocalStickerArt: library vectors from
     * StickerManifest, live stickers from filesDir/icons/custom/<id>.webp and their `.cut` paths, decoded off the main
     * thread into a bounded memory cache.
     */
    private val appStickerArt: AppStickerArt by lazy { AppStickerArt(File(AppIconRepository.iconsDir(app), "custom")) }

    val stickerArt: StickerArt get() = appStickerArt

    /**
     * The icon cascade on device: decider batcher, live drawing queue (v4), bespoke cache (§8.3). A redraw that
     * deletes the file it replaced drops that sticker from [stickerArt]'s memory too.
     */
    val icons: IconRepository by lazy {
        AppIconRepository(
            app = app,
            store = store,
            settings = settings,
            assistant = assistant,
            iconDecider = iconDecider,
            catalog = catalog,
            appScope = appScope,
            clock = clock,
            album = album,
            onIconReplaced = appStickerArt::evict,
        )
    }

    val photos: PhotoPreparer by lazy { CapturePhotoPreparer(app) }

    /** A fresh recorder per recording session (the Add sheet owns it; stop it in ON_STOP). */
    fun newVoiceRecorder(): AudioRecorder = VoiceRecorder(app)

    private val _ready = MutableStateFlow(false)

    /**
     * True once [startOnce] has parsed the icon catalog (a ~110 KB JSON resource) off the main
     * thread. MainActivity keeps the splash screen up and composes nothing until then, so no
     * ViewModel factory ever parses it on the main thread.
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val started = AtomicBoolean(false)

    /**
     * Once per process (MainActivity calls it on every onCreate; only the first call does anything),
     * all in [appScope]: warm the catalog, then keep the album in step with the state (TripFiling.launchAlbumSync:
     * collects check-offs, repairs a filing interrupted between album.json and state.json, §7.2), resume the icon
     * cascade (PENDING items from an offline or token-less session), sweep stale camera captures, and watch for a
     * token being added.
     */
    fun startOnce() {
        if (!started.compareAndSet(false, true)) return
        feedback // registers its resumed-window tracker (haptics) before the first onResume
        appScope.launch {
            withContext(Dispatchers.IO) {
                catalog.match("milk") // parses the catalog and builds its match index
            }
            _ready.value = true
            TripFiling.launchAlbumSync(appScope, store, album, catalog, clock)
            icons.resumePending()
            withContext(Dispatchers.IO) { Captures.sweepStale(app) }
        }
        appScope.launch { onTokenAdded(settings) { resumeAfterTokenAdded() } }
    }

    /**
     * A token arrived after a session without one: typed items were left PENDING (no icon decision)
     * and Unsorted (sortItems got NotConfigured). Catch up on both now instead of at the next cold start.
     */
    private suspend fun resumeAfterTokenAdded() {
        icons.resumePending()
        val state = try {
            store.current()
        } catch (e: IOException) {
            return
        }
        val typed = state.lists.asSequence().flatMap { it.items }.filter { it.source == ItemSource.TYPED }.mapTo(HashSet()) { it.id }
        val ids = state.itemsNeedingAisle().map { it.id }.filter { it in typed }
        if (ids.isNotEmpty()) aisleSync.sortItems(ids)
    }
}

/**
 * Suspends forever, calling [action] each time [SettingsRepository.settings] goes from "no token"
 * to "token set" (never for the value at subscription time).
 */
internal suspend fun onTokenAdded(settings: SettingsRepository, action: suspend () -> Unit) {
    settings.settings
        .map { it.hasToken }
        .distinctUntilChanged()
        .drop(1)
        .filter { it }
        .collect {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PicnicLog.w("catching up after a new token failed", e)
            }
        }
}

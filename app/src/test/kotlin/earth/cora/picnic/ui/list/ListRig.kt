package earth.cora.picnic.ui.list

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.InMemoryAlbumStore
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.icons.IconRepository
import earth.cora.picnic.platform.FakeAnnouncer
import earth.cora.picnic.platform.FakeFeedback
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.add.AddViewModel
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.ui.eggs.SurpriseEngine
import earth.cora.picnic.ui.eggs.SurpriseState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.FakePhotos
import earth.cora.picnic.ui.common.FakeRecorder
import earth.cora.picnic.ui.common.RecordingIconRepository
import earth.cora.picnic.ui.list.motion.MotionChoreographer
import earth.cora.picnic.ui.nav.AppNav
import earth.cora.picnic.ui.nav.LocalAppNav
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The icon repository the list talks to, counting [resumePending] (pull to refresh, item 119). [onResume] lets a test
 * play the repository's part (mark names busy, finish a drawing).
 */
class CountingIcons(val inner: RecordingIconRepository = RecordingIconRepository()) : IconRepository by inner {
    var resumed = 0
        private set
    var onResume: () -> Unit = {}

    override fun resumePending() {
        resumed++
        onResume()
    }
}

/** The egg hooks the list calls (§19.2; WP9 implements the engine): every call recorded, nothing else happens. */
class ListSurprises : SurpriseEngine {
    val stampTaps = mutableListOf<Int>()
    val basketHolds = mutableListOf<Pair<String, Long>>()
    val committed = mutableListOf<List<GroceryItem>>()
    var lcdHolds = 0
    var sunTaps = 0
    var moonTaps = 0

    /** What the eggs show; a test sets it (a seasonal sheet, eggs off). */
    val states = MutableStateFlow(SurpriseState())
    override val state: StateFlow<SurpriseState> = states

    override fun interceptText(text: String): Boolean = false

    override fun rollRarity(): Rarity = Rarity.NONE

    override fun onCommitted(items: List<GroceryItem>) {
        committed += items
    }

    override fun onSunTap() {
        sunTaps++
    }

    override fun onMoonTap() {
        moonTaps++
    }

    override fun onStampTap(position: Int) {
        stampTaps += position
    }

    override fun onLcdHold() {
        lcdHolds++
    }

    override fun onBasketHold(sectionId: String, heldMs: Long) {
        basketHolds += sectionId to heldMs
    }

    override fun onShake() = Unit

    override fun onIconDrawn(item: GroceryItem): Rarity = Rarity.NONE
}

/** Where the list's navigation went (the overflow menu, item 121). */
class RecordingNav {
    val opened = mutableListOf<String>()
    val nav = AppNav(
        openMyStore = { opened += "store" },
        openPantry = { opened += "pantry" },
        openAlbum = { opened += "album" },
        openSettings = { opened += "settings" },
    )
}

/**
 * The real List destination for Robolectric tests (docs/DESIGN_STICKER.md §6.1): [ListViewModel] and WP7's
 * [AddViewModel] over one in-memory store, with fakes for the icons, the assistant, TalkBack and the cues. The clock is
 * [now] (moved by the test); work runs on the main looper, so `waitForIdle` waits for commits.
 */
class ListRig(
    initial: AppState = ListFixture.state(),
    album: Album = Album(),
    var now: Long = ListFixture.NOW,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val store = InMemoryStateStore(initial)
    val album = InMemoryAlbumStore(album)
    val icons = CountingIcons()
    val assistant = FakeAssistant()
    val announcer = FakeAnnouncer()
    val feedback = FakeFeedback()
    val surprises = ListSurprises()
    val nav = RecordingNav()
    val aisles = mutableListOf<String>()
    val settingsOpened = mutableListOf<Unit>()
    private var next = 0
    private val newId: () -> String = { "new-${next++}" }
    val aisleSync = AisleSync(store, assistant, ListFixture.catalog.baskets) { now }
    val engine = MotionChoreographer(feedback, announcer)
    val settings = InMemorySettingsRepository(storedToken = "sk-test-0123456789abcdef")
    val vm = ListViewModel(
        store = store,
        album = this.album,
        icons = icons,
        settings = settings,
        catalog = ListFixture.catalog,
        clock = { now },
        newId = newId,
        appScope = scope,
        feedback = feedback,
        announcer = announcer,
        surprises = surprises,
        aisleSync = aisleSync,
        engine = engine,
    )
    val addVm = AddViewModel(
        store = store,
        assistant = assistant,
        icons = icons,
        aisleSync = aisleSync,
        catalog = ListFixture.catalog,
        photos = FakePhotos(),
        recorderFactory = { FakeRecorder() },
        clock = { now },
        newId = newId,
        appScope = scope,
        savedState = SavedStateHandle(),
        hasKey = { true },
        surprises = surprises,
        feedback = feedback,
        ioDispatcher = Dispatchers.Unconfined,
        thumbs = { null },
        announcer = announcer,
        words = ApplicationProvider.getApplicationContext<Context>()::getString,
    )

    /** The eggs as the app shows them on [now]'s day (Appendix A: Thanksgiving's sheet, the pumpkin tape and pictogram). */
    fun inSeason(): ListRig = apply { surprises.states.value = SurpriseState(season = ListFixture.seasonAt(now)) }

    /** The committed state now. */
    val app: AppState get() = (store.states as kotlinx.coroutines.flow.StateFlow<AppState>).value

    /** An item of the fixture list as committed. */
    fun item(id: String) = app.list(ListFixture.LIST_ID)!!.items.first { it.id == id }

    /** The List destination as the app hosts it. */
    @Composable
    fun Content() {
        CompositionLocalProvider(LocalAppNav provides nav.nav) {
            ListRoute(vm, addVm, onOpenAisles = { aisles += "aisles" }, onOpenSettings = { settingsOpened += Unit })
        }
    }

    /** [Content] themed and filling the window. */
    @Composable
    fun Themed(variant: ThemeVariant = ThemeVariant.Day, reducedMotion: Boolean = false) {
        PicnicTheme(variant = variant, reducedMotion = reducedMotion) { Box(Modifier.fillMaxSize()) { Content() } }
    }
}

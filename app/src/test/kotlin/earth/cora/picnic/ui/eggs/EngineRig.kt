package earth.cora.picnic.ui.eggs

import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.InMemoryAlbumStore
import earth.cora.picnic.core.weather.WeatherSource
import earth.cora.picnic.core.weather.WeatherToday
import earth.cora.picnic.platform.FakeFeedback
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.platform.InMemoryWeatherCache
import earth.cora.picnic.platform.WeatherService
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Collections
import kotlin.random.Random

/** A forecast source that counts its requests and answers what the test says (null = offline, or it throws). */
class CountingWeather(var answer: (LocalDate) -> WeatherToday? = { null }) : WeatherSource {
    @Volatile var requests = 0

    /** The day the source thinks it is (the rig's clock). */
    var day: () -> LocalDate = { LocalDate.of(2026, 10, 3) }

    override suspend fun today(): WeatherToday? {
        requests++
        return answer(day())
    }
}

/**
 * The SurpriseEngine alone (no screen), on an unconfined scope so its collectors and saves run at once: the practice
 * fixture (or [state]) in memory, in-memory album and settings, a fake clock, a seeded Random, recorded cues and toasts.
 */
class EngineRig(
    state: AppState = PracticeFixture.state(),
    settings: AppSettings = AppSettings(eggs = true, weather = false, sounds = false),
    val clock: FakeClock = FakeClock(PracticeFixture.NOW),
    random: Random = Random(7),
    val weatherSource: CountingWeather? = null,
    forceEggs: Boolean = false,
    album: Album = Album(),
    val timeouts: FakeTimeouts = FakeTimeouts(),
    places: PlaceSource = PlaceSource.Toronto,
) {
    val zone: ZoneId = ZoneId.of("America/Toronto")
    val catalog: IconCatalog = IconCatalog.default
    val store = PracticeStore(state)
    val albumStore = InMemoryAlbumStore(album)
    val settings = InMemorySettingsRepository(settings)
    val feedback = FakeFeedback()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val weatherCache = InMemoryWeatherCache()
    val weather = weatherSource?.let { src ->
        src.day = { java.time.Instant.ofEpochMilli(clock.now()).atZone(zone).toLocalDate() }
        WeatherService(src, weatherCache, clock::now, zone)
    }
    val engine = AppSurpriseEngine(
        store = store,
        album = albumStore,
        settings = this.settings,
        catalog = catalog,
        scope = scope,
        clock = clock::now,
        random = random,
        feedback = feedback,
        timeouts = timeouts,
        weather = weather,
        places = places,
        zone = zone,
        forceEggs = forceEggs,
    )
    val toasts: MutableList<EggToast> = Collections.synchronizedList(ArrayList())

    init {
        scope.launch { engine.events.collect { toasts += it } }
    }

    val eggs: SurpriseState get() = engine.state.value
    val listId: String get() = store.value.currentListId
    val list: GroceryList get() = store.value.list(listId)!!

    /** Sets the clock to [date] at [time] (Toronto) and re-reads it. */
    fun at(date: LocalDate, time: LocalTime) {
        clock.nowMs = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        engine.refresh()
    }

    /** A typed item as an add flow commits it (its icon from the catalog's match for [name] unless given). */
    fun item(id: String, name: String, aisle: String? = null, icon: String? = null): GroceryItem = GroceryItem(
        id = id,
        name = name,
        aisleId = aisle,
        icon = icon ?: catalog.match(name)?.key ?: "generic",
        iconFit = if (icon != null || catalog.match(name) != null) IconFit.EXACT else IconFit.APPROXIMATE,
        addedAt = clock.now(),
        source = ItemSource.TYPED,
    )

    fun close() = scope.cancel()
}

package earth.cora.picnic.ui.eggs

import android.os.SystemClock
import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.core.calendar.Seasons
import earth.cora.picnic.core.calendar.SunClock
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.core.model.markFinaleShown
import earth.cora.picnic.core.model.removeItem
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.setRarity
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.core.model.tripBook
import earth.cora.picnic.core.store.InMemoryAlbumStore
import earth.cora.picnic.core.store.TripFiling
import earth.cora.picnic.core.store.file
import earth.cora.picnic.core.weather.WeatherToday
import earth.cora.picnic.platform.Announcer
import earth.cora.picnic.platform.DefaultTimeouts
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.InMemoryWeatherCache
import earth.cora.picnic.platform.NoOpAnnouncer
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.Timeouts
import earth.cora.picnic.platform.WeatherMemo
import earth.cora.picnic.platform.WeatherService
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * The sandbox's clock (§6.9), epoch ms: the fixture's Saturday 10:24 when the sandbox opens, then running with real time
 * ([ticker]: elapsed ms, `SystemClock.elapsedRealtime` on the phone, the test clock in tests), so the eggs' time rules
 * (1.5 s between sun taps, the 6 s bonus limit, the 5.2 s petal cooldown) expire on the practice list as they do on her
 * list. A simulated condition (23:30, 20 min before sunset, a season's window) only moves the clock's offset ([nowMs],
 * [advance]); time keeps running from there. A [ticker] that never moves gives a clock that moves only by hand.
 */
class SandboxClock(private val start: Long, private val ticker: () -> Long) {
    private val tick0 = ticker()

    @Volatile private var offset = 0L

    fun now(): Long = start + (ticker() - tick0) + offset

    /** Moves the clock on by [ms] (a simulated condition; real time keeps running from there). */
    fun advance(ms: Long) {
        offset += ms
    }

    /** The time now; setting it moves the clock there (real time keeps running from there). */
    var nowMs: Long
        get() = now()
        set(value) {
            offset += value - now()
        }

    companion object {
        /** The phone's sandbox clock: [start], then real time. */
        fun realTime(start: Long = PracticeFixture.NOW): SandboxClock = SandboxClock(start) { SystemClock.elapsedRealtime() }
    }
}

/**
 * The Surprise lab's sandbox (docs/DESIGN_STICKER.md §6.9, §12.3): the Appendix A fixture in an in-memory store, an
 * in-memory album, in-memory settings (Easter eggs and Weather surprises on), an in-memory weather cache fed by a pretend
 * forecast and a fake clock, with its own SurpriseEngine (eggs forced on, whatever her switch says) and its own theme
 * variants. Nothing it does reaches state.json, album.json or DataStore; [close] discards it. [fire] plays one egg the
 * way §12.3's "Surprise lab action" column says. Owner: WP9 (SurpriseLabTest).
 */
class SurpriseLabSandbox(
    fixture: AppState = PracticeFixture.state(),
    val clock: SandboxClock = SandboxClock.realTime(),
    val catalog: IconCatalog = IconCatalog.default,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    feedback: Feedback = NoOpFeedback,
    announcer: Announcer = NoOpAnnouncer,
    val timeouts: Timeouts = DefaultTimeouts,
    seed: Int = 7,
    private val zone: ZoneId = ZoneId.of("America/Toronto"),
    /** Re-read the clock once a minute (midnight arriving, golden hour's sun sinking), as her list does. */
    minuteTicks: Boolean = true,
) {
    private val job = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(job + dispatcher)

    val store = PracticeStore(fixture)
    val album = InMemoryAlbumStore()
    val settings = InMemorySettingsRepository(AppSettings(eggs = true, weather = true, sounds = false))

    /** The pretend forecast (egg 16: "simulates rain today; no network"). */
    @Volatile var rainToday: Boolean = false
    val weatherCache = InMemoryWeatherCache()
    val weather = WeatherService(
        source = { WeatherToday(Instant.ofEpochMilli(clock.now()).atZone(zone).toLocalDate(), rain = rainToday, snow = false) },
        cache = weatherCache,
        clock = clock::now,
        zone = zone,
    )

    val engine = AppSurpriseEngine(
        store = store,
        album = album,
        settings = settings,
        catalog = catalog,
        scope = scope,
        clock = clock::now,
        random = Random(seed),
        feedback = feedback,
        timeouts = timeouts,
        weather = weather,
        places = PlaceSource.Toronto,
        zone = zone,
        forceEggs = true,
    )

    val model = PracticeModel(store, album, engine, catalog, clock::now, feedback, announcer, timeouts)

    init {
        // The practice album collects what the practice list checks off, as her album does (§7.2): holo pulls included.
        TripFiling.launchAlbumSync(scope, store, album, catalog, clock::now)
    }

    /**
     * New trip on the practice list (§6.5): its book is filed into the practice album (a printed receipt with it, as
     * saved), then the list starts over ([mode]). Runs in the sandbox's scope.
     */
    fun newTrip(mode: NewTripMode = NewTripMode.CLEAR_GOTTEN) {
        if (closed) return
        scope.launch {
            TripFiling.newTrip(store, album, listId, mode, clock.now())
            model.set { it }
        }
    }

    /**
     * The practice list's own variant (§2.5 on the sandbox clock and settings; the phone's dark mode still applies), and
     * the minute tick that re-reads the clock for the engine too.
     */
    val variants = ThemeVariantSource(
        settings, clock::now, SunClock, PlaceSource.Toronto, zone,
        duskShown = engine.state.map { it.dusk },
        onTick = { engine.refresh() },
    ).also {
        it.start(scope, everyMinute = minuteTicks)
    }

    /** A step an egg plays a little later (on the frame clock, by the lab screen): the cake's check-off, the jackpot. */
    data class FollowUp(val id: Int, val afterMs: Long, val run: () -> Unit)

    private val _followUp = MutableStateFlow<FollowUp?>(null)
    val followUp: StateFlow<FollowUp?> = _followUp.asStateFlow()

    /** Which egg played last (the lab scrolls its practice list into view, egg 14 shows the album's card). */
    private val _fired = MutableStateFlow<Int?>(null)
    val fired: StateFlow<Int?> = _fired.asStateFlow()

    private var firings = 0
    private var closed = false

    val listId: String get() = model.listId

    /**
     * Plays egg [egg] (1–18) on the practice copy as it is now (§12.3). The lab gives every Try it a fresh sandbox
     * (SurpriseLabViewModel), so each one starts from the fixture; [attempt] counts her tries of this egg (egg 3 hops
     * strawberries on the first, rolls lemons on the next, and so on).
     */
    fun fire(egg: Int, attempt: Int = 0) {
        if (closed) return
        firings++
        _fired.value = egg
        when (egg) {
            1 -> dusk()
            2 -> ladybug()
            3 -> rolling(attempt)
            4 -> {
                // The lab plays it as its practice list comes into view (a shake only counts on the list).
                engine.onListShown(listId)
                engine.onShake()
            }
            5 -> moveClock(LocalTime.of(23, 30))
            6 -> shiny()
            7 -> engine.interceptText("kirakira")
            8 -> nap()
            9 -> birthday()
            10 -> bonusStage()
            11 -> nextSeason()
            12 -> goldenHour()
            13 -> jackpot()
            14 -> perfectWeek()
            15 -> engine.onLcdHold()
            16 -> rain()
            17 -> picnic()
            18 -> receipt()
        }
    }

    /** The follow-up [id] has been run (or dropped). */
    fun followUpDone(id: Int) {
        if (_followUp.value?.id == id) _followUp.value = null
    }

    /** Discards the sandbox: nothing it held survives the lab (§6.9). */
    fun close() {
        closed = true
        scope.cancel()
    }

    // ---- The actions (§12.3 "Surprise lab action"). ----

    private fun today(): LocalDate = Instant.ofEpochMilli(clock.now()).atZone(zone).toLocalDate()

    private fun setClock(date: LocalDate, time: LocalTime) {
        clock.nowMs = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        engine.refresh()
        scope.launch { variants.tickNow() }
        engine.onListShown(listId)
    }

    private fun moveClock(time: LocalTime) = setClock(today(), time)

    /** Egg 1: forces Day (mid-morning, no Dusk), then taps the sun 7 times, 300 ms apart. */
    private fun dusk() {
        scope.launch { settings.setDuskBinder(false) }
        setClock(today(), LocalTime.of(10, 24))
        repeat(EggRules.SUN_TAPS) {
            clock.advance(300)
            engine.onSunTap()
        }
    }

    /** Egg 2: holds the first folded basket for the hold threshold + 560 ms. */
    private fun ladybug() {
        val g = model.grouped()
        val strip = g.sections.firstOrNull { it.id != g.openSectionId } ?: return
        engine.onBasketHold(strip.id, timeouts.holdThresholdMs + EggRules.LADYBUG_AFTER_THRESHOLD_MS)
    }

    /** Egg 3: adds "Strawberries" (they hop); the next try, "Lemons" (they roll and spin); and so on. */
    private fun rolling(attempt: Int) {
        model.set { it.setOpenAisle(listId, PRODUCE) }
        if (attempt % 2 == 0) {
            replaceAndAdd("Strawberries", "1 L basket", "produce", "strawberry")
        } else {
            replaceAndAdd("Lemons", "3 lemons", "produce", "lemon")
        }
    }

    /** Egg 6: forces a shiny on the next add, adds "Maple syrup". */
    private fun shiny() {
        engine.forceShinyNext = true
        model.set { it.setOpenAisle(listId, BREAKFAST) }
        replaceAndAdd("Maple syrup", "540 mL tin", "breakfast", "maple_syrup")
    }

    /** Egg 8: checks every stop but Frozen, leaving its basket empty; everything folds. */
    private fun nap() {
        val now = clock.now()
        model.set { s ->
            val list = s.list(listId) ?: return@set s
            var t = now
            list.items.filter { !it.checked && it.aisleId != FROZEN }.fold(s) { acc, item ->
                t += 20_000
                acc.toggleChecked(listId, item.id, t)
            }.setOpenAisle(listId, "")
        }
        clock.nowMs = now + 20_000L * 20
    }

    /** Egg 9: adds "Birthday cake", then checks it once the confetti has fallen (2.2 s). */
    private fun birthday() {
        val ids = replaceAndAdd("Birthday cake", "1 cake · for Saturday", "aisle-2-baking", "cake")
        val id = ids.firstOrNull() ?: return
        model.set { it.setOpenAisle(listId, "aisle-2-baking") }
        _followUp.value = FollowUp(firings, 2200) {
            clock.advance(2200)
            model.toggle(id)
        }
    }

    /** Egg 10: taps the first four aisle stamps in the order 1st 1st 2nd 2nd 3rd 4th 3rd 4th. */
    private fun bonusStage() {
        val g = model.grouped()
        val aisleStamps = g.sections.withIndex().filter { it.value.kind == SectionKind.AISLE }.map { it.index }
        if (aisleStamps.size < EggRules.BONUS_MIN_AISLE_STOPS) return
        for (k in EggRules.BONUS_SEQUENCE) {
            clock.advance(250)
            engine.onStampTap(aisleStamps[k])
        }
    }

    /** Egg 11: moves the sandbox date into the next season's window. */
    private fun nextSeason() {
        val (season, day) = Seasons.next(today()) ?: return
        setClock(Seasons.window(season to day).start, LocalTime.of(10, 24))
    }

    /** Egg 12: sets the sandbox clock to 20 min before sunset (Day, no Dusk). */
    private fun goldenHour() {
        scope.launch { settings.setDuskBinder(false) }
        val date = today()
        val sunset = SunClock.sunset(date, PlaceSourceToronto.place, zone)
        setClock(date, sunset.minusMinutes(20))
    }

    /** Egg 13: plays the reveal on Baking powder: a gold capsule while it "draws", then JACK / POT! (no network). */
    private fun jackpot() {
        val item = store.value.list(listId)?.items?.firstOrNull { it.id == JACKPOT_ROW } ?: return
        model.set { it.setOpenAisle(listId, item.aisleId) }
        engine.forceJackpotNext = true
        engine.onIconDrawing(item)
        _followUp.value = FollowUp(firings, 1200) {
            val rarity = engine.onIconDrawn(item)
            if (rarity != Rarity.NONE) model.set { it.setRarity(listId, item.id, rarity) }
        }
    }

    /** Egg 14: files four complete practice trips into the practice album. */
    private fun perfectWeek() {
        val now = clock.now()
        val done = store.value.let { s ->
            val list = s.list(listId) ?: return
            var t = now
            list.items.filter { !it.checked }.fold(s) { acc, item ->
                t += 30_000
                acc.toggleChecked(listId, item.id, t)
            }
        }
        val book = done.tripBook(listId, now) ?: return
        scope.launch {
            album.update { a ->
                (0 until EggRules.PERFECT_WEEK_TRIPS).fold(a) { acc, i ->
                    acc.file(book.copy(id = "t-practice-week-$i", closedAt = now - (EggRules.PERFECT_WEEK_TRIPS - 1 - i) * WEEK))
                }
            }
        }
    }

    /** Egg 16: simulates rain today (no network): the forecast says rain, the cache is fresh, the list re-opens. */
    private fun rain() {
        rainToday = true
        weatherCache.memo = WeatherMemo()
        engine.onListShown(listId)
    }

    /** Egg 17: adds three picnic things. */
    private fun picnic() {
        val now = clock.now()
        model.add(
            listOf(
                practiceItem("Watermelon", "1 small", "produce", "watermelon", now),
                practiceItem("Lemonade", "2 L bottle", null, "lemonade", now + 1),
                practiceItem("Sandwich bread", "675 g loaf", "bakery", "sliced_bread", now + 2),
            ),
            now,
        )
    }

    /**
     * Egg 18: completes the practice trip (its finale already shown), then holds the LCD. The rest of the shop is
     * checked off 30 s apart, finishing 41 minutes after the trip started (or later, if the lab has run that long), so
     * the receipt reads like §13.2's: "TIME ......... 41 MIN".
     */
    private fun receipt() {
        val now = clock.now()
        val left = store.value.list(listId)?.items?.count { !it.checked } ?: 0
        val done = maxOf(now + 30_000L * left, PracticeFixture.TRIP_START + RECEIPT_MINUTES * PracticeFixture.MIN)
        model.set { s ->
            val list = s.list(listId) ?: return@set s
            list.items.filter { !it.checked }.foldIndexed(s) { i, acc, item ->
                acc.toggleChecked(listId, item.id, done - 30_000L * (left - 1 - i))
            }.let { it.markFinaleShown(listId, done + 1000) }.setOpenAisle(listId, "")
        }
        clock.nowMs = done + 60_000L
        engine.onLcdHold()
    }

    /** Removes the practice list's row named [name] (if any) and adds a fresh one, so the add really commits. */
    private fun replaceAndAdd(name: String, quantity: String, aisle: String?, icon: String): List<String> {
        val now = clock.now()
        val existing = store.value.list(listId)?.items?.filter { it.name.equals(name, ignoreCase = true) }.orEmpty()
        if (existing.isNotEmpty()) model.set { s -> existing.fold(s) { acc, it -> acc.removeItem(listId, it.id, now) } }
        return model.add(listOf(practiceItem(name, quantity, aisle, icon, now)), now)
    }

    private fun practiceItem(name: String, quantity: String, aisle: String?, icon: String, now: Long) = GroceryItem(
        id = "practice-${model.nextId()}",
        name = name,
        quantity = quantity,
        aisleId = aisle,
        icon = icon,
        iconFit = IconFit.EXACT,
        addedAt = now,
        source = ItemSource.TYPED,
    )

    private object PlaceSourceToronto {
        val place = earth.cora.picnic.core.calendar.Place.TORONTO
    }

    private companion object {
        const val FROZEN = "frozen"
        const val PRODUCE = "produce"
        const val BREAKFAST = "breakfast"
        const val JACKPOT_ROW = "baking-powder"
        const val WEEK = 7L * 24 * 60 * 60 * 1000
        /** The practice trip's shop time once the lab finishes it (§13.2's example receipt, §11's summary). */
        const val RECEIPT_MINUTES = 41L
    }
}

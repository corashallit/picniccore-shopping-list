package earth.cora.picnic.ui.eggs

import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.core.calendar.SunClock
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.GroupedList
import earth.cora.picnic.core.model.ListGrouping
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionIds
import earth.cora.picnic.core.model.TripRules
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.dismissNap
import earth.cora.picnic.core.model.hatchPet
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.printReceipt
import earth.cora.picnic.core.model.saveReceipt
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.core.store.unlockSheet
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.DefaultTimeouts
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.platform.Timeouts
import earth.cora.picnic.platform.WeatherService
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.list.motion.MotionEvent
import earth.cora.picnic.ui.theme.isGoldenHour
import earth.cora.picnic.ui.theme.isMidnight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * The eggs (docs/DESIGN_STICKER.md §12, §13.2, §2.5). Every trigger is decided here from the injected [clock] (epoch
 * ms), [random], the committed state ([store]), the album ([album]) and the Surprises switches ([settings]); state the
 * eggs persist goes through StateOps ([store]), the album and DataStore. What plays is published in [state]; the
 * overlays (EggOverlays) play each effect on the frame clock and report back through [effectDone]. Owner: WP9.
 *
 * Gating (§2.5, item 237): with Surprises → Easter eggs off nothing fires and no persisted egg state renders (Dusk, the
 * pet, the receipt's LCD action, tapes, the umbrella, HAPPY / B-DAY!, the perfect-week holo), while what she collected
 * stays. [forceEggs] (the Surprise lab's sandbox) keeps them on whatever the switch says.
 *
 * Threads: the hooks are called on the main thread; the collectors run in [scope] and only touch [state] (atomically).
 */
class AppSurpriseEngine(
    private val store: StateStore,
    private val album: AlbumStore,
    private val settings: SettingsRepository,
    private val catalog: IconCatalog,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val random: Random = Random.Default,
    private val feedback: Feedback = NoOpFeedback,
    private val timeouts: Timeouts = DefaultTimeouts,
    private val weather: WeatherService? = null,
    private val places: PlaceSource = PlaceSource.Toronto,
    private val zone: ZoneId = ZoneId.of("America/Toronto"),
    /** The Surprise lab's sandbox: eggs on whatever the switch says (§6.9). */
    private val forceEggs: Boolean = false,
) : SurpriseEngine {
    private val ids = AtomicLong(0)
    private fun nextId(): Long = ids.incrementAndGet()

    private val _state = MutableStateFlow(SurpriseState())
    override val state: StateFlow<SurpriseState> = _state.asStateFlow()

    private val toasts = MutableSharedFlow<EggToast>(extraBufferCapacity = 8)
    override val events: Flow<EggToast> = toasts.asSharedFlow()

    // ---- Inputs (written by the collectors and the hooks; read when deriving). ----
    @Volatile private var appSettings: AppSettings? = null
    @Volatile private var appState: AppState? = null
    @Volatile private var albumNow: Album? = null
    @Volatile private var calendar: Calendar = Calendar()
    @Volatile private var weatherRain: Boolean = false
    @Volatile private var firstSnow: LocalDate? = null
    @Volatile private var duskPending: Boolean = false

    /** Reduced motion (§8.8), from the host. */
    @Volatile var reducedMotion: Boolean = false
        private set

    // ---- Hook state (main thread). ----
    private var sunTaps = 0
    private var lastSunTap = Long.MIN_VALUE
    private var bonusStep = 0
    private var bonusStartedAt = 0L
    private var lastPetals = Long.MIN_VALUE / 2
    private var listShown = false
    private var shownListId: String? = null
    private var imeOpen = false
    private var antsSeen = false
    private var startTucked = false
    private val goldRolls = HashMap<String, Boolean>()

    /** Trips whose cat was sent off this session (before the store has saved it, it must not come back). */
    private val napGone = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** The Surprise lab: the next committed item is shiny / the next drawing is a jackpot (§6.9). */
    @Volatile var forceShinyNext: Boolean = false
    @Volatile var forceJackpotNext: Boolean = false

    init {
        scope.launch { settings.settings.collect { s -> appSettings = s; derive() } }
        scope.launch {
            store.states.collect { s ->
                val before = appState
                appState = s
                if (!startTucked) {
                    startTucked = true
                    tuckAllOnStart(s)
                }
                if (before != null && before.currentListId != s.currentListId) onListSwitched(s.currentListId)
                derive()
            }
        }
        scope.launch {
            album.album.collect { a ->
                albumNow = a
                applyAlbumRules(a)
                derive()
            }
        }
        refresh()
    }

    // =============================================================================================
    // Derived state.
    // =============================================================================================

    private val eggsOn: Boolean get() = forceEggs || appSettings?.eggs == true

    private fun currentList(): GroceryList? = appState?.let { s -> s.list(s.currentListId) ?: s.lists.firstOrNull() }

    /** The current list grouped as the list shows it (§4.2 shape). */
    fun grouped(): GroupedList? {
        val s = appState ?: return null
        val list = currentList() ?: return null
        return ListGrouping.group(
            list = list,
            aisles = s.store.aisles(),
            basketFor = { s.store.aisleBaskets[it.id] ?: catalog.baskets.guess(it.name) },
            styleFor = { s.store.aisleStyles[it.id] },
        )
    }

    private fun derive() {
        val on = eggsOn
        val st = appSettings
        val list = currentList()
        val g = grouped()
        val cal = calendar
        val now = clock()
        _state.update { cur ->
            if (!on) {
                SurpriseState.Off
            } else {
                cur.copy(
                    enabled = true,
                    dusk = (st?.duskBinder == true) && !duskPending,
                    midnight = cal.midnight,
                    golden = cal.goldenMinutesLeft != null,
                    goldenMinutesLeft = cal.goldenMinutesLeft,
                    season = EggRules.season(cal.date, firstSnow.takeIf { st?.weather == true || forceEggs }),
                    rain = weatherRain && (st?.weather == true || forceEggs),
                    napSectionId = g?.let { EggRules.napSection(list, it) }?.takeIf { list?.trip?.id?.let { id -> id in napGone } != true },
                    pet = list?.trip?.pet == true,
                    receiptReady = list != null && TripRules.isComplete(list),
                    snackSectionId = if (cal.midnight && g != null) EggRules.frozenSection(g)?.id else null,
                    perfectWeek = (albumNow?.perfectUntil ?: 0L) > now,
                    kirakira = cur.kirakiraId != null,
                    petals = cur.petalsId != null,
                    ants = cur.antsId != null,
                )
            }
        }
    }

    /** What the clock says (§2.5, eggs 5, 11, 12). */
    private data class Calendar(
        val date: LocalDate = LocalDate.MIN,
        val midnight: Boolean = false,
        val goldenMinutesLeft: Int? = null,
    )

    private var sunsetCache: Pair<String, LocalTime>? = null

    override fun refresh() {
        val now = clock()
        val at = Instant.ofEpochMilli(now).atZone(zone)
        val wasMidnight = calendar.midnight
        val sunset = sunsetCache?.takeIf { it.first == at.toLocalDate().toString() }?.second
        calendar = Calendar(
            date = at.toLocalDate(),
            midnight = isMidnight(true, at.toLocalTime()),
            goldenMinutesLeft = sunset?.let { goldenMinutesLeft(at, it) },
        )
        if (sunset == null) {
            scope.launch {
                val place = try {
                    places.place()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: return@launch
                val time = SunClock.sunset(at.toLocalDate(), place, zone)
                sunsetCache = at.toLocalDate().toString() to time
                calendar = calendar.copy(goldenMinutesLeft = goldenMinutesLeft(Instant.ofEpochMilli(clock()).atZone(zone), time))
                derive()
            }
        }
        derive()
        unlockSeason()
        // Egg 5: crossing 23:00 while the list is open reads "SNACK?" as if she had just come in.
        if (listShown && eggsOn && calendar.midnight && !wasMidnight) snack()
    }

    /** Minutes left before [sunset] (0–30) while golden hour holds at [at], else null. */
    private fun goldenMinutesLeft(at: ZonedDateTime, sunset: LocalTime): Int? {
        if (!isGoldenHour(at.toLocalTime(), sunset)) return null
        val sunsetAt = at.toLocalDate().atTime(sunset).atZone(zone)
        return ChronoUnit.MINUTES.between(at, sunsetAt).toInt().coerceIn(0, 30)
    }

    // =============================================================================================
    // The phase-0 hooks.
    // =============================================================================================

    override fun interceptText(text: String): Boolean {
        if (!eggsOn || !EggRules.isMagicWord(text)) return false
        // Egg 7: nothing is added and nothing is sent; stickers twinkle and the title turns holo for 5 s.
        val id = nextId()
        _state.update { it.copy(kirakiraId = id, kirakira = true) }
        feedback.cue(Cue.MAGIC_APPEAR)
        return true
    }

    override fun rollRarity(): Rarity {
        if (!eggsOn) return Rarity.NONE
        if (forceShinyNext) {
            forceShinyNext = false
            return Rarity.SHINY
        }
        return if (random.nextInt(EggRules.SHINY_ODDS) == 0) Rarity.SHINY else Rarity.NONE
    }

    override fun onCommitted(items: List<GroceryItem>) {
        if (!eggsOn || items.isEmpty()) return
        val reduced = reducedMotion
        // Egg 3: the first fruit that rolls (or berries that hop), one per commit; pure motion, so not under reduced motion.
        val roller = EggRules.roller(items, catalog)
        if (roller != null && !reduced) {
            val travel = EggRules.travelOf(roller, catalog)
            val section = roller.aisleId?.takeIf { id -> appState?.store?.aisles()?.any { it.id == id } == true } ?: SectionIds.UNSORTED
            val position = appState?.store?.aisles()?.firstOrNull { it.id == section }?.position ?: 0
            _state.update {
                it.copy(
                    rolling = RollingFx(
                        nextId(), roller.id, section, roller.iconRef(), hop = travel == EggRules.Travel.Hop,
                        holo = roller.rarity != Rarity.NONE, aisleIndex = if (position > 0) position - 1 else -1,
                    ),
                )
            }
        }
        // Egg 9: confetti for 2 s (the hanko reads HAPPY / B-DAY! whenever the item is checked, reduced motion or not).
        if (items.any { EggRules.isBirthday(it, catalog) } && !reduced) {
            _state.update { it.copy(confettiId = nextId()) }
            feedback.cue(Cue.CELEBRATION)
        }
        // Egg 6: each new shiny row bursts with 8 stickers and a "kira" (static holo under reduced motion).
        val shiny = items.filter { it.rarity == Rarity.SHINY }
        if (shiny.isNotEmpty()) {
            if (!reduced) _state.update { s -> s.copy(shinyBursts = s.shinyBursts + shiny.map { ShinyFx(nextId(), it.id) }) }
            feedback.cue(Cue.MAGIC_APPEAR)
        }
        checkAnts()
    }

    override fun onSunTap() {
        if (!eggsOn) return
        val s = _state.value
        // In Dusk the sticker by the title is the moon: a tap undoes Dusk. Midnight's moon (Glow) is static.
        if (s.dusk) return onMoonTap()
        if (s.midnight || s.duskFx != null) return
        val now = clock()
        sunTaps = if (now - lastSunTap <= EggRules.SUN_TAP_GAP_MS) sunTaps + 1 else 1
        lastSunTap = now
        if (sunTaps < EggRules.SUN_TAPS) return
        sunTaps = 0
        val reduced = reducedMotion
        // Persisted at once (it survives a restart mid-transition); shown when the sun has set (600 ms), or at once
        // under reduced motion (the moon and the Dusk palette fade in, 120 ms).
        duskPending = !reduced
        _state.update { it.copy(duskFx = DuskFx(nextId(), toDusk = true, reduced = reduced)) }
        scope.launch { safely { settings.setDuskBinder(true) } }
        derive()
    }

    override fun onDuskSunset(id: Long) {
        if (_state.value.duskFx?.id != id) return
        duskPending = false
        derive()
    }

    override fun onMoonTap() {
        if (!eggsOn || !_state.value.dusk) return
        duskPending = false
        sunTaps = 0
        _state.update { it.copy(duskFx = DuskFx(nextId(), toDusk = false, reduced = reducedMotion)) }
        scope.launch { safely { settings.setDuskBinder(false) } }
        // The flag in memory goes at once (the DataStore write follows).
        appSettings = appSettings?.copy(duskBinder = false)
        derive()
    }

    override fun onStampTap(position: Int) {
        if (!eggsOn) return
        // Egg 10: tapping any stamp again ends the bonus stage.
        if (_state.value.bonusStage) return endBonusStage()
        val g = grouped() ?: return
        if (EggRules.aisleStops(g) < EggRules.BONUS_MIN_AISLE_STOPS) return
        val index = EggRules.aisleStampIndex(position, g) ?: run {
            bonusStep = 0
            return
        }
        val now = clock()
        if (bonusStep > 0 && now - bonusStartedAt > EggRules.BONUS_LIMIT_MS) bonusStep = 0
        if (index == EggRules.BONUS_SEQUENCE[bonusStep]) {
            if (bonusStep == 0) bonusStartedAt = now
            bonusStep++
        } else {
            // A wrong tap restarts; a tap on the 1st stamp restarts at step 1.
            bonusStep = if (index == EggRules.BONUS_SEQUENCE[0]) 1 else 0
            if (bonusStep == 1) bonusStartedAt = now
        }
        if (bonusStep == EggRules.BONUS_SEQUENCE.size) {
            bonusStep = 0
            _state.update { it.copy(bonusStage = true, lcdWord = LcdWord(nextId(), EggRules.BONUS, 0)) }
            toasts.tryEmit(EggToast.BonusStage)
        }
    }

    private fun endBonusStage() {
        bonusStep = 0
        _state.update { s -> s.copy(bonusStage = false, lcdWord = s.lcdWord?.takeIf { it.text != EggRules.BONUS }) }
    }

    override fun onLcdHold() {
        if (!eggsOn) return
        val s = appState ?: return
        val list = currentList() ?: return
        // D21: a complete trip prints its receipt, however it was completed; the pet branch never runs then.
        if (TripRules.isComplete(list)) return printReceipt(s, list)
        if (list.trip == null) {
            // Egg 15 with no trip: the bee visits for 1.5 s with "FED" and leaves; nothing is persisted.
            _state.update { it.copy(petVisitId = nextId(), lcdWord = LcdWord(nextId(), EggRules.FED, EggRules.FED_MS)) }
        } else {
            scope.launch { safely { store.update { it.hatchPet(list.id) } } }
            _state.update { it.copy(pet = true, lcdWord = LcdWord(nextId(), EggRules.FED, EggRules.FED_MS)) }
        }
        feedback.cue(Cue.PET_FED)
    }

    private fun printReceipt(s: AppState, list: GroceryList) {
        val stops = grouped()?.stops ?: 0
        val receipt = ReceiptMaker.receipt(list, stops, s.store.name, clock(), zone)
        scope.launch { safely { store.update { it.printReceipt(list.id, receipt) } } }
        _state.update {
            it.copy(
                receipt = ReceiptFx(nextId(), list.id, list.title, receipt, reduced = reducedMotion),
                receiptTuckId = null,
                lcdWord = LcdWord(nextId(), EggRules.PRINT, EggRules.PRINT_MS),
            )
        }
        feedback.cue(Cue.RECEIPT_PRINT)
    }

    override fun onBasketHold(sectionId: String, heldMs: Long) {
        if (!eggsOn || reducedMotion) return
        if (heldMs < timeouts.holdThresholdMs + EggRules.LADYBUG_AFTER_THRESHOLD_MS) return
        if (_state.value.ladybug != null) return
        _state.update { it.copy(ladybug = LadybugFx(nextId(), sectionId)) }
    }

    override fun onShake() {
        if (!eggsOn || reducedMotion || !listShown) return
        val now = clock()
        if (now - lastPetals < EggRules.PETAL_COOLDOWN_MS) return
        lastPetals = now
        val id = nextId()
        _state.update { it.copy(petalsId = id, petals = true) }
        feedback.cue(Cue.PETAL_SHAKE)
    }

    override fun onIconDrawn(item: GroceryItem): Rarity {
        if (!eggsOn) return Rarity.NONE
        val gold = goldRolls.remove(item.id) ?: rollGold()
        _state.update { it.copy(goldCapsules = it.goldCapsules - item.id) }
        if (!gold) return Rarity.NONE
        _state.update { it.copy(jackpot = JackpotFx(nextId(), item.id, item.iconRef(), reduced = reducedMotion)) }
        return Rarity.JACKPOT
    }

    private fun rollGold(): Boolean {
        if (forceJackpotNext) {
            forceJackpotNext = false
            return true
        }
        return random.nextInt(EggRules.JACKPOT_ODDS) == 0
    }

    // =============================================================================================
    // WP9 hooks.
    // =============================================================================================

    override fun onIconDrawing(item: GroceryItem) {
        if (!eggsOn || item.id in goldRolls) return
        val gold = rollGold()
        goldRolls[item.id] = gold
        if (gold) _state.update { it.copy(goldCapsules = it.goldCapsules + item.id) }
    }

    override fun onListShown(listId: String) {
        val wasShown = listShown && shownListId == listId
        listShown = true
        shownListId = listId
        refresh()
        if (!eggsOn) return
        if (!wasShown && calendar.midnight) snack()
        checkWeather()
        checkAnts()
    }

    override fun onListHidden() {
        listShown = false
        tuckPrinted(silent = true)
        if (_state.value.bonusStage) endBonusStage()
    }

    override fun onReceiptTap() {
        val fx = _state.value.receipt ?: return
        scope.launch { safely { store.update { it.saveReceipt(fx.listId) } } }
        _state.update { it.copy(receipt = null, receiptTuckId = nextId()) }
        toasts.tryEmit(EggToast.ReceiptSaved)
    }

    override fun onNapTap() {
        val section = _state.value.napSectionId ?: return
        val list = currentList() ?: return
        list.trip?.id?.let { napGone += it }
        scope.launch { safely { store.update { it.dismissNap(list.id) } } }
        _state.update {
            it.copy(
                napLeaving = NapLeaveFx(nextId(), section, reducedMotion),
                napSectionId = null,
                basketHello = if (reducedMotion) it.basketHello else BasketHelloFx(nextId(), section),
            )
        }
    }

    override fun onBasketHello(sectionId: String) {
        if (!eggsOn) return
        _state.update { it.copy(basketHello = BasketHelloFx(nextId(), sectionId)) }
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (!eggsOn) return
        if (event is MotionEvent.Landed && _state.value.pet) _state.update { it.copy(petChompId = nextId()) }
    }

    override fun onImeVisible(visible: Boolean) {
        imeOpen = visible
        if (!visible) checkAnts()
    }

    override fun setReducedMotion(on: Boolean) {
        reducedMotion = on
    }

    override fun effectDone(id: Long) {
        _state.update { s ->
            s.copy(
                duskFx = s.duskFx?.takeIf { it.id != id },
                kirakiraId = s.kirakiraId?.takeIf { it != id },
                kirakira = s.kirakiraId != null && s.kirakiraId != id,
                petalsId = s.petalsId?.takeIf { it != id },
                petals = s.petalsId != null && s.petalsId != id,
                antsId = s.antsId?.takeIf { it != id },
                ants = s.antsId != null && s.antsId != id,
                ladybug = s.ladybug?.takeIf { it.id != id },
                rolling = s.rolling?.takeIf { it.id != id },
                confettiId = s.confettiId?.takeIf { it != id },
                shinyBursts = s.shinyBursts.filterNot { it.id == id },
                jackpot = s.jackpot?.takeIf { it.id != id },
                lcdWord = s.lcdWord?.takeIf { it.id != id },
                petVisitId = s.petVisitId?.takeIf { it != id },
                petChompId = s.petChompId?.takeIf { it != id },
                receiptTuckId = s.receiptTuckId?.takeIf { it != id },
                napLeaving = s.napLeaving?.takeIf { it.id != id },
            )
        }
        if (_state.value.duskFx == null) duskPending = false
        derive()
    }

    // =============================================================================================
    // Helpers.
    // =============================================================================================

    /** Egg 5: "SNACK?" for 2.6 s on entering the list (or at 23:00 while it is open). */
    private fun snack() {
        _state.update { it.copy(lcdWord = LcdWord(nextId(), EggRules.SNACK, EggRules.SNACK_MS)) }
    }

    /** Egg 17: once per app process, ≥ 3 unchecked picnic things, never with the keyboard open or reduced motion. */
    private fun checkAnts() {
        if (antsSeen || imeOpen || reducedMotion || !eggsOn || !listShown) return
        scope.launch {
            val s = try {
                store.current()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            val list = s.list(s.currentListId)
            if (!EggRules.antsWanted(list, catalog) || antsSeen || imeOpen) return@launch
            antsSeen = true
            _state.update { it.copy(antsId = nextId(), ants = true) }
        }
    }

    /** Egg 16 and first snow (§12.4): the forecast, only with Weather surprises on. */
    private fun checkWeather() {
        val service = weather ?: return
        val on = forceEggs || appSettings?.weather == true
        if (!on) {
            weatherRain = false
            return
        }
        scope.launch {
            val w = service.check(weatherOn = true) ?: return@launch
            weatherRain = w.rain
            firstSnow = w.firstSnowDate
            derive()
            unlockSeason()
            if (w.rainToastDue && eggsOn) {
                service.markRainToast()
                toasts.tryEmit(EggToast.RainToday)
            }
        }
    }

    /** Egg 11: the active season's sheet unlocks in the album (its magic appear plays on first view there). */
    private fun unlockSeason() {
        if (!eggsOn) return
        val season = _state.value.season ?: return
        val sheet = EggRules.sheetId(season, calendar.date)
        if (albumNow?.sheets?.contains(sheet) == true) return
        scope.launch { safely { album.update { it.unlockSheet(sheet) } } }
    }

    /** Egg 14: a perfect week and the back pocket (§7.8). */
    private fun applyAlbumRules(a: Album) {
        if (!eggsOn) return
        val next = EggRules.perfectWeek(a)
        if (next == a) return
        val opened = !a.backPocketOpen && next.backPocketOpen
        scope.launch {
            safely {
                album.update { EggRules.perfectWeek(it) }
                if (opened) toasts.tryEmit(EggToast.BackPocket)
            }
        }
    }

    /** §13.2: a printed receipt nobody tapped is tucked into the book, without a poof or a toast. */
    private fun tuckPrinted(silent: Boolean) {
        val fx = _state.value.receipt ?: return
        scope.launch { safely { store.update { it.saveReceipt(fx.listId) } } }
        _state.update { it.copy(receipt = null, receiptTuckId = if (silent) null else nextId()) }
    }

    private fun onListSwitched(listId: String) {
        val fx = _state.value.receipt
        if (fx != null && fx.listId != listId) tuckPrinted(silent = true)
        if (_state.value.bonusStage) endBonusStage()
    }

    /** On the next start after the process died: every printed receipt that was never tapped is tucked in (§13.2). */
    private fun tuckAllOnStart(s: AppState) {
        val untucked = s.lists.filter { it.trip?.receipt != null && it.trip?.receiptSaved == false }.map { it.id }
        if (untucked.isEmpty()) return
        scope.launch { safely { store.update { st -> untucked.fold(st) { acc, id -> acc.saveReceipt(id) } } } }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PicnicLog.w("an egg couldn't save", e)
        }
    }
}

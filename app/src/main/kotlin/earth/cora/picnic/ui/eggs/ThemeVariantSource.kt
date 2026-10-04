package earth.cora.picnic.ui.eggs

import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.core.calendar.SunClock
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.resolveThemeVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Resolves the theme variant (docs/DESIGN_STICKER.md §2.5) at composition and on every minute tick:
 * 1. Easter eggs on and local time in [23:00, 04:00) -> Glow + midnight extras (egg 5);
 * 2. system dark mode -> Glow;
 * 3. Easter eggs on and the Dusk binder saved (egg 1) -> Dusk;
 * 4. Easter eggs on and within 30 min before sunset at [places]' point (egg 12, §0.4 Q6: her approximate area with "Use
 *    my area" on, else Toronto) -> Golden; the place is read again on every minute tick and whenever "Use my area" is
 *    switched, and the sunset is computed again for a new day or a new place;
 * 5. otherwise Day.
 * MainActivity feeds the system dark mode in ([onSystemDark]) and sets the bar styles from [variant] (WP4). [start]
 * collects the settings and ticks once a minute in a scope (AppContainer passes the app scope; tests a TestScope); the
 * minute ticks run only while the app is in front (MainActivity's onStart [resume] / onStop [pause]), and with Easter
 * eggs off nothing reads the place (golden hour is an egg, §2.5).
 * [duskShown] (the SurpriseEngine's `state.dusk`) holds egg 1's Dusk back until the sun has set in its transition;
 * without it the saved flag applies at once. Owner: WP9 (ThemeVariantTest).
 */
class ThemeVariantSource(
    private val settings: SettingsRepository,
    private val clock: () -> Long,
    private val sunClock: SunClock = SunClock,
    private val places: PlaceSource = PlaceSource.Toronto,
    /** Her local time (midnight, golden hour); AppContainer passes the phone's zone. */
    private val zone: ZoneId = ZoneId.of("America/Toronto"),
    private val duskShown: Flow<Boolean>? = null,
    /** Called on every minute tick (AppContainer: the SurpriseEngine re-reads the clock: midnight, golden hour, seasons). */
    private val onTick: (() -> Unit)? = null,
) {
    private val resolved = MutableStateFlow(ThemeVariant.Day)

    val variant: StateFlow<ThemeVariant> = resolved.asStateFlow()

    @Volatile private var systemDark = false
    @Volatile private var current: AppSettings? = null
    @Volatile private var dusk: Boolean? = null
    /** Today's sunset for the place it was computed for. */
    private data class Sunset(val date: LocalDate, val lat: Double, val lon: Double, val time: LocalTime)

    @Volatile private var sunset: Sunset? = null
    private var job: Job? = null
    private var ticker: Job? = null
    private var scope: CoroutineScope? = null
    private var everyMinute = false
    private var paused = false

    /** The system's dark mode (Configuration.uiMode), on start and on every change. */
    fun onSystemDark(dark: Boolean) {
        systemDark = dark
        resolve()
    }

    /**
     * Collects the settings (and [duskShown]) and, with [everyMinute], re-resolves once a minute, on the minute, in
     * [scope]. Idempotent. (The Surprise lab's sandbox moves its fake clock by hand and calls [tick] instead.)
     */
    fun start(scope: CoroutineScope, everyMinute: Boolean = true) {
        if (job != null) return
        this.scope = scope
        this.everyMinute = everyMinute
        job = scope.launch {
            launch {
                settings.settings.collect {
                    val before = current
                    current = it
                    // The first settings, "Use my area" switched (the sunset follows the new place now, not tomorrow) or
                    // Easter eggs turned on: the sunset is worked out again.
                    if (before == null || before.useMyArea != it.useMyArea || (!before.eggs && it.eggs)) refreshSunset()
                    resolve()
                }
            }
            duskShown?.let { flow -> launch { flow.collect { dusk = it; resolve() } } }
            refreshSunset()
            resolve()
        }
        if (everyMinute && !paused) ticker = launchTicker(scope, tickFirst = false)
    }

    /** The app went to the background (MainActivity.onStop): the minute ticks stop, so nothing asks for the place. */
    fun pause() {
        paused = true
        ticker?.cancel()
        ticker = null
    }

    /** The app is in front again (MainActivity.onStart): ticks at once, then once a minute, on the minute. */
    fun resume() {
        paused = false
        val s = scope ?: return
        if (!everyMinute || ticker?.isActive == true) return
        ticker = launchTicker(s, tickFirst = true)
    }

    private fun launchTicker(scope: CoroutineScope, tickFirst: Boolean): Job = scope.launch {
        if (tickFirst) tickOnce()
        while (true) {
            val now = clock()
            delay(MINUTE - Math.floorMod(now, MINUTE) + 1)
            tickOnce()
        }
    }

    private suspend fun tickOnce() {
        refreshSunset()
        resolve()
        onTick?.invoke()
    }

    /** Re-reads the clock now (a resume, a moved test clock): the sunset of a new day too. */
    suspend fun tickNow() {
        refreshSunset()
        resolve()
    }

    /** Re-reads the clock now with the sunset already known (a resume). */
    fun tick() = resolve()

    /** The variant the rules give right now. */
    fun resolveNow(): ThemeVariant {
        val s = current
        val eggs = s?.eggs ?: true
        val at = Instant.ofEpochMilli(clock()).atZone(zone)
        val set = sunset?.takeIf { it.date == at.toLocalDate() }?.time
        val duskSaved = dusk ?: (s?.duskBinder == true)
        return resolveThemeVariant(eggs = eggs, systemDark = systemDark, duskSaved = duskSaved, now = at.toLocalTime(), sunset = set)
    }

    private fun resolve() {
        resolved.value = resolveNow()
    }

    /**
     * Reads the place (AreaPlaceSource answers from its 6 h store) and computes the sunset for a new day or place. With
     * Easter eggs off (or the settings not read yet) nothing can use it, so the place is not asked for.
     */
    private suspend fun refreshSunset() {
        if (current?.eggs != true) return
        val date = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        val place = try {
            places.place()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        val known = sunset
        if (known != null && known.date == date && known.lat == place.lat && known.lon == place.lon) return
        sunset = Sunset(date, place.lat, place.lon, sunClock.sunset(date, place, zone))
    }

    private companion object {
        const val MINUTE: Long = 60_000
    }
}

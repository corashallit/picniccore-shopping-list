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
 *    my area" on, else Toronto) -> Golden;
 * 5. otherwise Day.
 * MainActivity feeds the system dark mode in ([onSystemDark]) and sets the bar styles from [variant] (WP4). [start]
 * collects the settings and ticks once a minute in a scope (AppContainer passes the app scope; tests a TestScope).
 * [duskShown] (the SurpriseEngine's `state.dusk`) holds egg 1's Dusk back until the sun has set in its transition;
 * without it the saved flag applies at once. Owner: WP9 (ThemeVariantTest).
 */
class ThemeVariantSource(
    private val settings: SettingsRepository,
    private val clock: () -> Long,
    private val sunClock: SunClock = SunClock,
    private val places: PlaceSource = PlaceSource.Toronto,
    private val zone: ZoneId = ZoneId.of("America/Toronto"),
    private val duskShown: Flow<Boolean>? = null,
) {
    private val resolved = MutableStateFlow(ThemeVariant.Day)

    val variant: StateFlow<ThemeVariant> = resolved.asStateFlow()

    @Volatile private var systemDark = false
    @Volatile private var current: AppSettings? = null
    @Volatile private var dusk: Boolean? = null
    @Volatile private var sunset: Pair<LocalDate, LocalTime>? = null
    private var job: Job? = null

    /** The system's dark mode (Configuration.uiMode), on start and on every change. */
    fun onSystemDark(dark: Boolean) {
        systemDark = dark
        resolve()
    }

    /** Collects the settings (and [duskShown]) and re-resolves once a minute, on the minute, in [scope]. Idempotent. */
    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            launch { settings.settings.collect { current = it; resolve() } }
            duskShown?.let { flow -> launch { flow.collect { dusk = it; resolve() } } }
            while (true) {
                refreshSunset()
                resolve()
                val now = clock()
                delay(MINUTE - Math.floorMod(now, MINUTE) + 1)
            }
        }
    }

    /** Re-reads the clock now (a resume, a test). */
    fun tick() = resolve()

    /** The variant the rules give right now. */
    fun resolveNow(): ThemeVariant {
        val s = current
        val eggs = s?.eggs ?: true
        val at = Instant.ofEpochMilli(clock()).atZone(zone)
        val set = sunset?.takeIf { it.first == at.toLocalDate() }?.second
        val duskSaved = dusk ?: (s?.duskBinder == true)
        return resolveThemeVariant(eggs = eggs, systemDark = systemDark, duskSaved = duskSaved, now = at.toLocalTime(), sunset = set)
    }

    private fun resolve() {
        resolved.value = resolveNow()
    }

    private suspend fun refreshSunset() {
        val date = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        if (sunset?.first == date) return
        val place = try {
            places.place()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        sunset = date to sunClock.sunset(date, place, zone)
    }

    private companion object {
        const val MINUTE: Long = 60_000
    }
}

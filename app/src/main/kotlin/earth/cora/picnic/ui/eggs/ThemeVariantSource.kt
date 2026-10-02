package earth.cora.picnic.ui.eggs

import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.core.calendar.SunClock
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Resolves the theme variant (docs/DESIGN_STICKER.md §2.5) at composition and on every minute tick:
 * 1. Easter eggs on and local time in [23:00, 04:00) -> Glow + midnight extras (egg 5);
 * 2. system dark mode -> Glow;
 * 3. Easter eggs on and the Dusk binder saved (egg 1) -> Dusk;
 * 4. Easter eggs on and within 30 min before sunset at [places]' point (egg 12, §0.4 Q6) -> Golden;
 * 5. otherwise Day.
 * MainActivity feeds the system dark mode in ([onSystemDark]) and sets the bar styles from [variant] (WP4).
 * Owner: WP9 (ThemeVariantTest). PHASE-0 STUB: system dark -> Glow, else Day (rules 2 and 5 only).
 */
class ThemeVariantSource(
    @Suppress("unused") private val settings: SettingsRepository,
    @Suppress("unused") private val clock: () -> Long,
    @Suppress("unused") private val sunClock: SunClock = SunClock,
    @Suppress("unused") private val places: PlaceSource = PlaceSource.Toronto,
) {
    private val resolved = MutableStateFlow(ThemeVariant.Day)

    val variant: StateFlow<ThemeVariant> = resolved.asStateFlow()

    /** The system's dark mode (Configuration.uiMode), on start and on every change. */
    fun onSystemDark(dark: Boolean) {
        resolved.value = if (dark) ThemeVariant.Glow else ThemeVariant.Day
    }
}

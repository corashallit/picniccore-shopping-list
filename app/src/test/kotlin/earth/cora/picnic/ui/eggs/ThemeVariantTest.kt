package earth.cora.picnic.ui.eggs

import earth.cora.picnic.core.calendar.SunClock
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Items 16 (the WP9 half) and 237: ThemeVariantSource resolves midnight → system dark → Dusk → Golden → Day on the
 * fake clock and settings, and re-evaluates every minute on the minute with no other trigger; with Easter eggs off the
 * saved Dusk binder never shows (it is kept for when they return).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeVariantTest {
    private val zone = ZoneId.of("America/Toronto")
    private val day = LocalDate.of(2026, 10, 3)
    private val sunset = SunClock.sunset(day, 43.65, -79.38, zone) // 18:57

    private fun ms(time: LocalTime): Long = day.atTime(time).atZone(zone).toInstant().toEpochMilli()

    /** A source on [settings] whose clock starts at [start] and moves with the test's virtual time. */
    private fun TestScope.source(settings: AppSettings, start: LocalTime, dusk: MutableStateFlow<Boolean>? = null): Pair<ThemeVariantSource, InMemorySettingsRepository> {
        val repo = InMemorySettingsRepository(settings)
        val base = ms(start)
        val t0 = testScheduler.currentTime
        val s = ThemeVariantSource(repo, { base + (testScheduler.currentTime - t0) }, SunClock, zone = zone, duskShown = dusk)
        s.start(backgroundScope)
        runCurrent()
        return s to repo
    }

    @Test
    fun resolutionOrder() = runTest(UnconfinedTestDispatcher()) {
        // Day at 10:24; nothing else ticks it, yet each minute boundary re-reads the clock.
        val (s, repo) = source(AppSettings(eggs = true), LocalTime.of(10, 24))
        assertEquals(ThemeVariant.Day, s.variant.value)

        // Golden: within 30 min before sunset (18:57) — reached by the minute ticks alone.
        val (g, _) = source(AppSettings(eggs = true), sunset.minusMinutes(31).plusSeconds(30))
        assertEquals(ThemeVariant.Day, g.variant.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("golden hour starts on the minute tick", ThemeVariant.Golden, g.variant.value)
        // The saved binder turns Day to Dusk at once.
        repo.setDuskBinder(true)
        assertEquals(ThemeVariant.Dusk, s.variant.value)
        // Dusk beats Golden.
        val (gd, gdRepo) = source(AppSettings(eggs = true, duskBinder = true), sunset.minusMinutes(10))
        assertEquals("Dusk beats Golden", ThemeVariant.Dusk, gd.variant.value)
        // System dark beats Dusk.
        gd.onSystemDark(true)
        assertEquals("system dark beats Dusk", ThemeVariant.Glow, gd.variant.value)
        gd.onSystemDark(false)
        assertEquals(ThemeVariant.Dusk, gd.variant.value)
        gdRepo.setDuskBinder(false)
        assertEquals(ThemeVariant.Golden, gd.variant.value)
        // Past sunset: Day again on the next tick.
        advanceTimeBy(11 * 60_000L)
        runCurrent()
        assertEquals(ThemeVariant.Day, gd.variant.value)

        // Midnight beats Dusk and the light system: Glow from the 23:00 tick to 04:00.
        val (m, mRepo) = source(AppSettings(eggs = true, duskBinder = true), LocalTime.of(22, 59, 10))
        assertEquals(ThemeVariant.Dusk, m.variant.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("midnight beats Dusk", ThemeVariant.Glow, m.variant.value)
        mRepo.setDuskBinder(false)
        assertEquals(ThemeVariant.Glow, m.variant.value)
        val (late, _) = source(AppSettings(eggs = true), LocalTime.of(3, 59, 30))
        assertEquals(ThemeVariant.Glow, late.variant.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("04:00 ends midnight", ThemeVariant.Day, late.variant.value)
    }

    @Test
    fun duskNeedsEggs() = runTest(UnconfinedTestDispatcher()) {
        // The saved binder with Easter eggs off: Day (and no midnight or golden either), the flag kept.
        val (s, repo) = source(AppSettings(eggs = false, duskBinder = true), LocalTime.of(10, 24))
        assertEquals(ThemeVariant.Day, s.variant.value)
        assertEquals(true, repo.current().duskBinder)
        val (m, _) = source(AppSettings(eggs = false), LocalTime.of(23, 30))
        assertEquals("no midnight Glow with eggs off", ThemeVariant.Day, m.variant.value)
        val (g, _) = source(AppSettings(eggs = false), sunset.minusMinutes(10))
        assertEquals("no golden hour with eggs off", ThemeVariant.Day, g.variant.value)
        // Eggs back on: the kept binder shows again.
        repo.setSurprise(earth.cora.picnic.settings.Surprise.EasterEggs, true)
        assertEquals(ThemeVariant.Dusk, s.variant.value)
        // Egg 1's transition holds Dusk back until the sun has set in it (duskShown).
        val shown = MutableStateFlow(false)
        val (held, _) = source(AppSettings(eggs = true, duskBinder = true), LocalTime.of(10, 24), dusk = shown)
        assertEquals(ThemeVariant.Day, held.variant.value)
        shown.value = true
        assertEquals(ThemeVariant.Dusk, held.variant.value)
    }
}

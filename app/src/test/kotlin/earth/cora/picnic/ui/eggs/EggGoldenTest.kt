package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.calendar.Place
import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.core.calendar.SunClock
import earth.cora.picnic.ui.theme.GoldenShadows
import earth.cora.picnic.ui.theme.PicnicShadows
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Item 250 (egg 12) on the fake clock: in the 30 minutes before sunset (Toronto, or her approximate area with "Use my
 * area" on, §0.4) the list turns Golden, every hard shadow lengthens to (+3, +4) dp in #E59A7A, and the sun sinks 0.5 dp
 * for each minute that passes, re-read once a minute (read from the sticker as drawn on the list: 7.5 px at xxhdpi from
 * 20 to 15 minutes left); back to Day at sunset; Day only (system dark and Dusk win).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggGoldenTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("America/Toronto")
    private val day = LocalDate.of(2026, 10, 3)
    private val sunset = SunClock.sunset(day, Place.TORONTO, zone)

    @Test
    fun theThirtyMinutesBeforeSunset() {
        // 18:57 at Toronto on 3 October 2026 (§12.2).
        assertEquals(LocalTime.of(18, 57).toSecondOfDay().toDouble(), sunset.toSecondOfDay().toDouble(), 180.0)
        val e = EngineRig()
        try {
            e.at(day, sunset.minusMinutes(31))
            assertFalse(e.eggs.golden)
            assertNull(e.eggs.goldenMinutesLeft)
            e.at(day, sunset.minusMinutes(30))
            assertTrue(e.eggs.golden)
            assertEquals(30, e.eggs.goldenMinutesLeft)
            e.at(day, sunset.minusMinutes(20))
            assertEquals(20, e.eggs.goldenMinutesLeft)
            e.at(day, sunset.minusMinutes(1))
            assertEquals(1, e.eggs.goldenMinutesLeft)
            e.at(day, sunset.plusMinutes(1))
            assertFalse("back to Day after sunset", e.eggs.golden)
            assertNull(e.eggs.goldenMinutesLeft)
        } finally {
            e.close()
        }
    }

    @Test
    fun theSunSinksHalfADpPerMinute() {
        val e = EngineRig()
        try {
            e.at(day, sunset.minusMinutes(29).minusSeconds(30))
            assertEquals(29, e.eggs.goldenMinutesLeft)
            var last = e.eggs.goldenMinutesLeft!!
            // The minute tick re-reads the clock: one step per minute, never two, never back.
            repeat(25) {
                e.clock.advance(60_000)
                e.engine.refresh()
                val left = e.eggs.goldenMinutesLeft!!
                assertEquals(last - 1, left)
                last = left
            }
            // (The sun's drawn drop on the list, 0.5 dp a minute: onTheListGoldenWithLongShadows.)
            assertEquals(4, last)
        } finally {
            e.close()
        }
    }

    @Test
    fun followsHerAreaWithUseMyAreaOn() {
        // Ottawa's sunset is earlier than Toronto's: with her area there, golden hour starts and ends earlier.
        val ottawa = Place(lat = 45.4, lon = -75.7, cityCode = "s0000430")
        val there = SunClock.sunset(day, ottawa, zone)
        assertTrue(there.isBefore(sunset))
        val e = EngineRig(places = PlaceSource { ottawa })
        try {
            e.at(day, there.minusMinutes(10))
            assertEquals(10, e.eggs.goldenMinutesLeft)
            e.at(day, there.plusMinutes(2))
            assertFalse("past her sunset, though not Toronto's", e.eggs.golden)
        } finally {
            e.close()
        }
        // The variant source follows the same place.
        val repo = earth.cora.picnic.settings.InMemorySettingsRepository(earth.cora.picnic.settings.AppSettings(eggs = true))
        val clock = FakeClock(day.atTime(there.minusMinutes(10)).atZone(zone).toInstant().toEpochMilli())
        val s = ThemeVariantSource(repo, clock::now, SunClock, PlaceSource { ottawa }, zone)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        try {
            s.start(scope, everyMinute = false)
            assertEquals(ThemeVariant.Golden, s.variant.value)
            clock.nowMs = day.atTime(there.plusMinutes(2)).atZone(zone).toInstant().toEpochMilli()
            runBlocking { s.tickNow() }
            assertEquals(ThemeVariant.Day, s.variant.value)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    /**
     * Item 316 (WP9 half, §0.4 Q6): golden hour follows her place the moment it changes, not the next day: "Use my area"
     * switched on (her area far west of Toronto: its sunset is an hour later), switched off again, and her stored area
     * refreshed mid-day (the minute tick reads the place again). The variant source follows the same switch at once.
     */
    @Test
    fun aPlaceChangeMidDayMovesGoldenHourAtOnce() {
        val winnipeg = Place(lat = 49.9, lon = -97.1, cityCode = "s0000193")
        val ottawa = Place(lat = 45.4, lon = -75.7, cityCode = "s0000430")
        val far = SunClock.sunset(day, winnipeg, zone)
        assertTrue("its sunset is long after Toronto's", far.isAfter(sunset.plusMinutes(40)))
        var stored = winnipeg
        val repo = earth.cora.picnic.settings.InMemorySettingsRepository(earth.cora.picnic.settings.AppSettings(eggs = true))
        val area = PlaceSource { if (repo.current().useMyArea) stored else Place.TORONTO }
        val e = EngineRig(places = area, repo = repo)
        try {
            e.at(day, sunset.minusMinutes(10))
            assertEquals("Toronto's golden hour", 10, e.eggs.goldenMinutesLeft)
            runBlocking { repo.setSurprise(earth.cora.picnic.settings.Surprise.UseMyArea, true) }
            assertFalse("her area's sun is still high: no golden hour, at once", e.eggs.golden)
            runBlocking { repo.setSurprise(earth.cora.picnic.settings.Surprise.UseMyArea, false) }
            assertEquals("off again: Toronto's", 10, e.eggs.goldenMinutesLeft)
            // On again, and her stored area refreshes to Ottawa (the 6 h refresh) mid-day: the next minute tick follows it.
            runBlocking { repo.setSurprise(earth.cora.picnic.settings.Surprise.UseMyArea, true) }
            assertFalse(e.eggs.golden)
            stored = ottawa
            val there = SunClock.sunset(day, ottawa, zone)
            e.at(day, there.minusMinutes(12))
            assertEquals("Ottawa's sunset now", 12, e.eggs.goldenMinutesLeft)
        } finally {
            e.close()
        }
        // The theme variant follows the switch the same way, with no tick.
        stored = winnipeg
        val off = earth.cora.picnic.settings.InMemorySettingsRepository(earth.cora.picnic.settings.AppSettings(eggs = true))
        val clock = FakeClock(day.atTime(sunset.minusMinutes(10)).atZone(zone).toInstant().toEpochMilli())
        val s = ThemeVariantSource(off, clock::now, SunClock, PlaceSource { if (off.current().useMyArea) stored else Place.TORONTO }, zone)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        try {
            s.start(scope, everyMinute = false)
            assertEquals(ThemeVariant.Golden, s.variant.value)
            runBlocking { off.setSurprise(earth.cora.picnic.settings.Surprise.UseMyArea, true) }
            assertEquals(ThemeVariant.Day, s.variant.value)
            runBlocking { off.setSurprise(earth.cora.picnic.settings.Surprise.UseMyArea, false) }
            assertEquals(ThemeVariant.Golden, s.variant.value)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun onTheListGoldenWithLongShadows() {
        var shadows: PicnicShadows? = null
        val r = EggRig(compose)
        r.start {
            PracticeList(r.model, feedback = r.feedback)
            ReadShadows { shadows = it }
        }
        r.fire(12)
        r.advance(300)
        assertEquals(ThemeVariant.Golden, r.resolved)
        assertEquals("every hard shadow (+3, +4) dp in #E59A7A", GoldenShadows, shadows)
        assertEquals(20, r.eggs.goldenMinutesLeft)
        // The sun sticker as drawn (the sun's 48 dp target holds it; the sticker inside sinks), and its target.
        fun drawnSun(): Rect = compose.onNodeWithContentDescription("Sun sticker", useUnmergedTree = true).fetchSemanticsNode()
            .children.single().boundsInRoot
        val target = r.anchor(AnchorRegistry.SUN)
        val at20 = drawnSun()
        assertEquals("(30 − 20) × 0.5 dp below the target's centre", target.center.y + 5 * 3f, at20.center.y, 1f)
        // Five minutes on, at 15 minutes left (the minute tick re-reads the clock): the sun is 2.5 dp (7.5 px) lower.
        val sunsetAt = day.atTime(sunset).atZone(zone).toInstant().toEpochMilli()
        r.act {
            r.clock.nowMs = sunsetAt - 15 * 60_000L - 30_000L
            r.engine.refresh()
            runBlocking { r.sandbox.variants.tickNow() }
        }
        r.frame()
        assertEquals(15, r.eggs.goldenMinutesLeft)
        val at15 = drawnSun()
        assertEquals("2.5 dp lower at xxhdpi", 7.5f, at15.top - at20.top, 1f)
        assertEquals("the same size, straight down", at20.left, at15.left, 0.5f)
        assertEquals("its tap target stays put", target, r.anchor(AnchorRegistry.SUN))
        // Past sunset: Day again.
        r.act {
            r.clock.advance(21 * 60_000L)
            r.engine.refresh()
            runBlocking { r.sandbox.variants.tickNow() }
        }
        assertEquals(ThemeVariant.Day, r.resolved)
        assertFalse(r.eggs.golden)
    }

    @Test
    fun dayOnly() {
        // The phone in dark mode: Glow wins; golden hour does not show on her list.
        val r = EggRig(compose, variant = ThemeVariant.Glow).start()
        r.act {
            r.clock.nowMs = day.atTime(sunset.minusMinutes(20)).atZone(zone).toInstant().toEpochMilli()
            r.engine.refresh()
            runBlocking { r.sandbox.variants.tickNow() }
        }
        r.advance(300)
        assertTrue("20 minutes before sunset", r.eggs.golden)
        assertEquals(ThemeVariant.Glow, r.resolved)
        // The Surprise lab's Try it forces Day (§12.3), so golden hour shows on a dark phone's practice list too.
        r.fire(12)
        r.advance(300)
        assertEquals(ThemeVariant.Golden, r.resolved)
    }

    @Composable
    private fun ReadShadows(read: (PicnicShadows) -> Unit) {
        read(PicnicTheme.shadows)
    }
}

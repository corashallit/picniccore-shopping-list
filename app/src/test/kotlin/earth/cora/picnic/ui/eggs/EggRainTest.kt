package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.weather.WeatherToday
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.ui.add.AddRules
import earth.cora.picnic.ui.theme.WashiPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * Item 255 (egg 16) with a fake WeatherSource: with Weather surprises on and rain in today's forecast, the umbrella
 * shelters the add bar's left end, the raindrop washi replaces the tape and "Rain on the way · bring the tote!" shows
 * once a day; offline, switched off or with Easter eggs off nothing shows and (switched off) nothing is requested.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggRainTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 3)

    private fun rig(weatherOn: Boolean = true, eggs: Boolean = true, answer: (LocalDate) -> WeatherToday? = { WeatherToday(it, rain = true, snow = false) }) =
        CountingWeather(answer).let { w -> EngineRig(settings = AppSettings(eggs = eggs, weather = weatherOn), weatherSource = w) to w }

    @Test
    fun rainTodayShowsTheUmbrellaTapeAndOneToastADay() {
        val (e, w) = rig()
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals(1, w.requests)
            assertTrue(e.eggs.rain)
            assertTrue(e.eggs.umbrella)
            assertEquals(WashiPattern.Raindrop, AddRules.addBarTape(e.eggs))
            assertEquals(listOf(EggToast.RainToday), e.toasts)
            // The list again an hour later: no new request (3 h cache), no second toast today.
            e.at(today, LocalTime.of(10, 0))
            e.engine.onListShown(e.listId)
            assertEquals(1, w.requests)
            assertEquals(1, e.toasts.size)
            assertTrue(e.eggs.umbrella)
            // Three hours on, it asks again; still no second toast today.
            e.at(today, LocalTime.of(12, 1))
            e.engine.onListShown(e.listId)
            assertEquals(2, w.requests)
            assertEquals(1, e.toasts.size)
            // Tomorrow, rain again: one more toast.
            e.at(today.plusDays(1), LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals(2, e.toasts.size)
        } finally {
            e.close()
        }
    }

    /**
     * The rain toast waits for the toast host (she left the list during a slow fetch): it is shown when the host takes it,
     * and only then does today count as toasted, so it is never lost and never shown twice.
     */
    @Test
    fun theRainToastWaitsForTheToastHost() {
        val w = CountingWeather { WeatherToday(it, rain = true, snow = false) }
        val e = EngineRig(settings = AppSettings(eggs = true, weather = true), weatherSource = w, collectToasts = false)
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            e.engine.onListHidden()
            e.engine.onListShown(e.listId)
            assertTrue(e.eggs.umbrella)
            assertEquals("not counted before it shows", null, kotlinx.coroutines.runBlocking { e.weatherCache.read() }.rainToastDay)
            // The host comes back and takes what waited: one toast, now counted as today's.
            e.collectToasts()
            assertEquals(listOf(EggToast.RainToday), e.toasts)
            assertEquals(today, kotlinx.coroutines.runBlocking { e.weatherCache.read() }.rainToastDay)
            e.engine.onListShown(e.listId)
            assertEquals(1, e.toasts.size)
        } finally {
            e.close()
        }
    }

    @Test
    fun noRainNoUmbrella() {
        val (e, _) = rig(answer = { WeatherToday(it, rain = false, snow = false) })
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertFalse(e.eggs.umbrella)
            assertEquals(WashiPattern.Thanksgiving, AddRules.addBarTape(e.eggs))
            assertTrue(e.toasts.isEmpty())
        } finally {
            e.close()
        }
    }

    @Test
    fun offlineIsSilent() {
        val (e, w) = rig(answer = { null })
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals(1, w.requests)
            assertFalse(e.eggs.umbrella)
            assertTrue(e.toasts.isEmpty())
        } finally {
            e.close()
        }
        // A source that throws is just as silent.
        val (t, _) = rig(answer = { throw java.io.IOException("no network") })
        try {
            t.at(today, LocalTime.of(9, 0))
            t.engine.onListShown(t.listId)
            assertFalse(t.eggs.umbrella)
            assertTrue(t.toasts.isEmpty())
        } finally {
            t.close()
        }
    }

    @Test
    fun switchedOffNothingIsRequested() {
        val (e, w) = rig(weatherOn = false)
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals("nothing is requested with Weather surprises off", 0, w.requests)
            assertFalse(e.eggs.umbrella)
            assertTrue(e.toasts.isEmpty())
        } finally {
            e.close()
        }
        val (off, w2) = rig(eggs = false)
        try {
            off.at(today, LocalTime.of(9, 0))
            off.engine.onListShown(off.listId)
            assertEquals("nor with Easter eggs off", 0, w2.requests)
            assertFalse(off.eggs.umbrella)
        } finally {
            off.close()
        }
    }

    @Test
    fun onTheListTheUmbrellaAppears() {
        val r = EggRig(compose).start()
        val t0 = r.fire(16)
        assertTrue(r.eggs.umbrella)
        assertEquals("Rain on the way · bring the tote!", r.model.toasts.current?.text)
        r.advanceTo(t0 + 600)
        val umbrella = r.probe.frames.last { it.tag == "umbrella" }
        val bar = r.anchor(earth.cora.picnic.ui.list.motion.AnchorRegistry.ADD_BAR)
        assertTrue("at the add bar's left end", umbrella.center.x < bar.left + bar.width * 0.25f)
        // Inside the bar, in the band over the field: never over the list under the bar's edge.
        assertTrue("inside the bar", umbrella.bounds.top >= bar.top && umbrella.bounds.bottom <= bar.top + 20 * 3f)
        assertEquals(WashiPattern.Raindrop, AddRules.addBarTape(r.eggs))
    }
}

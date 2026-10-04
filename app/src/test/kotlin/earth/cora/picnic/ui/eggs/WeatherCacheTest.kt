package earth.cora.picnic.ui.eggs

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.weather.WeatherToday
import earth.cora.picnic.platform.DataStoreWeatherCache
import earth.cora.picnic.platform.WeatherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Item 256, the app half (the feed and its parser are core's WeatherSourceTest): the forecast is asked only with
 * Weather surprises on, at most every 3 h, with the cache in DataStore (`weather_fetched_at`, `weather_cache`,
 * `rain_toast_day`, `first_snow_date`) surviving a restart; the rainy-day toast is due at most once a day; the first snow
 * of the season is kept; offline it stays silent and keeps what it knew.
 */
@RunWith(AndroidJUnit4::class)
class WeatherCacheTest {
    private val zone = ZoneId.of("America/Toronto")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "weather-test-${System.nanoTime()}"
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        File(context.filesDir, "datastore").listFiles()?.filter { it.name.startsWith(name) }?.forEach { it.delete() }
    }

    /** A fresh DataStore over the same file (a process restart): the previous one is closed first. */
    private suspend fun cache(): DataStoreWeatherCache {
        scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return DataStoreWeatherCache(PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile(name) })
    }

    private fun ms(date: LocalDate, time: LocalTime): Long = date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private val day = LocalDate.of(2026, 10, 3)

    @Test
    fun askedAtMostEveryThreeHoursAcrossRestarts() = runBlocking {
        var now = ms(day, LocalTime.of(9, 0))
        val source = CountingWeather { WeatherToday(it, rain = true, snow = false) }
        source.day = { java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
        val first = WeatherService(source, cache(), { now }, zone)
        assertTrue(first.check(weatherOn = true)!!.rain)
        assertEquals(1, source.requests)
        now += 60 * 60_000L
        first.check(weatherOn = true)
        assertEquals("an hour later: from the cache", 1, source.requests)
        // A restart two hours after the ask: the cache read back from DataStore, still no request.
        now += 60 * 60_000L
        val again = WeatherService(source, cache(), { now }, zone)
        assertTrue("rain today, from DataStore", again.check(weatherOn = true)!!.rain)
        assertEquals(1, source.requests)
        // Three hours after the ask: asked again.
        now += 60 * 60_000L + 1
        again.check(weatherOn = true)
        assertEquals(2, source.requests)
    }

    /**
     * Item 316, the forecast half (§0.4 Q6): the forecast belongs to a city. Asked for Toronto, then "Use my area" names
     * Ottawa within the 3 h: asked again at once (one request), and the rain follows Ottawa's forecast; the cache keeps
     * the city across a restart, so Ottawa's answer is not asked for again.
     */
    @Test
    fun anotherCityIsAskedAgainAtOnce() = runBlocking {
        val now = ms(day, LocalTime.of(9, 0))
        var city = earth.cora.picnic.core.calendar.Place.TORONTO.cityCode
        val ottawa = "s0000430"
        // Toronto: dry today. Ottawa: rain.
        val source = CountingWeather { WeatherToday(it, rain = city == ottawa, snow = false) }
        val service = WeatherService(source, cache(), { now }, zone, city = { city })
        assertFalse("Toronto is dry", service.check(weatherOn = true)!!.rain)
        assertEquals(1, source.requests)
        city = ottawa
        assertTrue("rain follows Ottawa at once", service.check(weatherOn = true)!!.rain)
        assertEquals("one new request for the new city", 2, source.requests)
        assertTrue(service.check(weatherOn = true)!!.rain)
        assertEquals("then from the cache", 2, source.requests)
        val restarted = WeatherService(source, cache(), { now + 60_000L }, zone, city = { city })
        assertTrue("Ottawa's rain, from DataStore", restarted.check(weatherOn = true)!!.rain)
        assertEquals(2, source.requests)
        // Back to Toronto (the switch turned off): Toronto's forecast again, asked at once.
        city = earth.cora.picnic.core.calendar.Place.TORONTO.cityCode
        assertFalse(restarted.check(weatherOn = true)!!.rain)
        assertEquals(3, source.requests)
    }

    @Test
    fun switchedOffNothingIsAsked() = runBlocking {
        val source = CountingWeather { WeatherToday(it, rain = true, snow = false) }
        val service = WeatherService(source, cache(), { ms(day, LocalTime.of(9, 0)) }, zone)
        assertNull(service.check(weatherOn = false))
        assertEquals(0, source.requests)
    }

    @Test
    fun theRainToastAtMostOnceADay() = runBlocking {
        var now = ms(day, LocalTime.of(9, 0))
        val source = CountingWeather { WeatherToday(it, rain = true, snow = false) }
        source.day = { java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
        val c = cache()
        val service = WeatherService(source, c, { now }, zone)
        assertTrue(service.check(weatherOn = true)!!.rainToastDue)
        service.markRainToast()
        assertFalse(service.check(weatherOn = true)!!.rainToastDue)
        // After a restart the same day: still not due (rain_toast_day is in DataStore).
        val restarted = WeatherService(source, cache(), { now }, zone)
        assertFalse(restarted.check(weatherOn = true)!!.rainToastDue)
        // Tomorrow it may show again.
        now = ms(day.plusDays(1), LocalTime.of(9, 0))
        assertTrue(restarted.check(weatherOn = true)!!.rainToastDue)
    }

    @Test
    fun theFirstSnowOfTheSeasonIsKept() = runBlocking {
        var now = ms(LocalDate.of(2026, 11, 20), LocalTime.of(9, 0))
        var snowing = true
        val source = CountingWeather { WeatherToday(it, rain = false, snow = snowing) }
        source.day = { java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
        val service = WeatherService(source, cache(), { now }, zone)
        assertEquals(LocalDate.of(2026, 11, 20), service.check(weatherOn = true)!!.firstSnowDate)
        // Later snow does not move it; dry days keep it.
        now = ms(LocalDate.of(2026, 11, 25), LocalTime.of(9, 0))
        assertEquals(LocalDate.of(2026, 11, 20), service.check(weatherOn = true)!!.firstSnowDate)
        snowing = false
        now = ms(LocalDate.of(2026, 12, 2), LocalTime.of(9, 0))
        assertEquals(LocalDate.of(2026, 11, 20), WeatherService(source, cache(), { now }, zone).check(weatherOn = true)!!.firstSnowDate)
        // Next season (from 1 October 2027), the first snow is a new one.
        snowing = true
        now = ms(LocalDate.of(2027, 11, 5), LocalTime.of(9, 0))
        assertEquals(LocalDate.of(2027, 11, 5), WeatherService(source, cache(), { now }, zone).check(weatherOn = true)!!.firstSnowDate)
    }

    @Test
    fun offlineIsSilentAndKeepsWhatItKnew() = runBlocking {
        var now = ms(day, LocalTime.of(9, 0))
        var online = true
        val source = CountingWeather { if (online) WeatherToday(it, rain = true, snow = false) else null }
        source.day = { java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
        val service = WeatherService(source, cache(), { now }, zone)
        assertTrue(service.check(weatherOn = true)!!.rain)
        online = false
        now += 3 * 60 * 60_000L + 1
        val offline = service.check(weatherOn = true)
        assertEquals(2, source.requests)
        assertTrue("an unanswered ask keeps today's rain", offline!!.rain)
        // And it waits its 3 h before asking again.
        now += 60_000L
        service.check(weatherOn = true)
        assertEquals(2, source.requests)
        // A source that throws is no weather, never a crash.
        val throwing = WeatherService({ throw IllegalStateException("boom") }, cache(), { now + 4 * 60 * 60_000L }, zone)
        assertNull(throwing.check(weatherOn = true))
    }
}

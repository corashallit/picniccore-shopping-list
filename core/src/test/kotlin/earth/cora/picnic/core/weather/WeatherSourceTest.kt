package earth.cora.picnic.core.weather

import earth.cora.picnic.core.calendar.Place
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The weather eggs' forecast (docs/DESIGN_STICKER.md §12.4, item 256) on recorded Environment Canada feeds: Toronto as
 * fetched on Saturday 3 October 2026 (clear today, showers only tomorrow), Iqaluit the same morning ("Flurries or rain
 * showers"), and the Toronto feed edited to rain tonight, a 40 % chance of showers today and a 40 % chance of flurries
 * today, plus a feed cut off mid-entry. Served by MockWebServer: one GET for the forecast city's own coordinates.
 */
class WeatherSourceTest {
    private val server = MockWebServer().apply { start() }
    private val saturday = LocalDate.of(2026, 10, 3)
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

    @AfterTest
    fun close() = server.close()

    private fun feed(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/weather/$name")) { "missing feed $name" }.bufferedReader().use { it.readText() }

    private fun source(place: Place = Place.TORONTO, day: LocalDate = saturday) = EnvironmentCanadaWeather(
        http = { client },
        place = { place },
        date = { day },
        baseUrl = server.url("/").toString(),
    )

    private fun serve(name: String) = server.enqueue(MockResponse.Builder().code(200).body(feed(name)).addHeader("Content-Type", "application/atom+xml").build())

    @Test
    fun clearTodayShowersTomorrowIsNoRain() = runTest {
        serve("toronto_clear.xml")
        val w = assertNotNull(source().today())
        assertEquals(saturday, w.date)
        assertFalse(w.rain, "Sunday's 40 % showers are tomorrow's, not today's")
        assertFalse(w.snow)
    }

    @Test
    fun rainTonightIsRainToday() = runTest {
        serve("toronto_rain_tonight.xml")
        assertTrue(assertNotNull(source().today()).rain)
    }

    @Test
    fun fortyPercentShowersIsRain() = runTest {
        serve("toronto_showers40.xml")
        assertTrue(assertNotNull(source().today()).rain)
    }

    @Test
    fun flurriesAreSnowAndTheirChanceIsNotRain() = runTest {
        serve("toronto_flurries40.xml")
        val w = assertNotNull(source().today())
        assertTrue(w.snow)
        assertFalse(w.rain, "a 40 % chance of flurries is snow, not rain")
    }

    @Test
    fun flurriesOrRainShowersIsBoth() = runTest {
        serve("iqaluit_flurries_rain.xml")
        val w = assertNotNull(source().today())
        assertTrue(w.rain)
        assertTrue(w.snow)
    }

    @Test
    fun malformedIsSilent() = runTest {
        serve("malformed.xml")
        assertNull(source().today())
    }

    @Test
    fun serverErrorAndOfflineAreSilent() = runTest {
        server.enqueue(MockResponse.Builder().code(503).body("busy").build())
        assertNull(source().today())
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertNull(source().today())
    }

    /** §0.4 Q6: the request names only the forecast CITY's coordinates, never the phone's point, and nothing else. */
    @Test
    fun requestNamesOnlyTheForecastCity() = runTest {
        serve("toronto_clear.xml")
        // Her rounded area near Oakville; its forecast city is a different point.
        val area = Place(lat = 43.5, lon = -79.7, cityCode = "s0000000", siteLat = 43.37, siteLon = -79.81)
        assertNotNull(source(area).today())
        val request = assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/rss/weather/43.37_-79.81_e.xml", request.url.encodedPath)
        assertNull(request.url.query, "no query string")
        assertFalse(request.url.toString().contains("43.5") || request.url.toString().contains("79.7"), "her point never leaves the phone")
        assertNull(request.headers["Cookie"])
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun theDefaultFeedIsTorontosOnWeatherGcCa() {
        val url = EnvironmentCanadaWeather(place = { Place.TORONTO }).feedUrl(Place.TORONTO)
        assertEquals("https://weather.gc.ca/rss/weather/43.65_-79.38_e.xml", url)
    }

    /** "Tonight" / "Today" titles, and a feed whose periods are not named after today (just after midnight). */
    @Test
    fun periodNames() {
        val feed = feed("toronto_rain_tonight.xml")
        val periods = EcForecastParser.periods(feed)
        assertEquals(listOf("Saturday", "Saturday night"), periods.take(2).map { it.name })
        assertEquals(13, periods.size)
        // Read on Sunday the 4th, the Sunday entries are today's (a 40 % chance of showers).
        assertTrue(assertNotNull(EcForecastParser.parse(feed, saturday.plusDays(1))).rain)
        assertTrue(assertNotNull(EcForecastParser.parse(synthetic("Tonight: Showers. Low 9."), saturday)).rain)
        assertFalse(assertNotNull(EcForecastParser.parse(synthetic("Today: Sunny. High 20.", "Tonight: Clear. Low 9."), saturday)).rain)
        // No period named after today: the feed's first period and its night stand for today.
        assertTrue(assertNotNull(EcForecastParser.parse(synthetic("Holiday: Cloudy. High 9.", "Holiday night: Drizzle. Low 2.", "Monday: Rain."), saturday)).rain)
        assertFalse(assertNotNull(EcForecastParser.parse(synthetic("Holiday: Cloudy. High 9.", "Monday: Rain."), saturday)).rain)
        // A feed with no forecast entry at all is no weather.
        assertNull(EcForecastParser.parse("<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>x</title></feed>", saturday))
    }

    private fun synthetic(vararg titles: String): String = buildString {
        append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xml:lang=\"en-ca\">\n")
        for (t in titles) {
            append("<entry><title>$t</title><category term=\"Weather Forecasts\"/><summary type=\"html\">${t.substringAfter(": ")}</summary></entry>\n")
        }
        append("</feed>\n")
    }
}

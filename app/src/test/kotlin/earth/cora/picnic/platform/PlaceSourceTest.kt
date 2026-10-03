package earth.cora.picnic.platform

import earth.cora.picnic.core.calendar.Place
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist 316 (docs/DESIGN_STICKER.md §0.4 Q6): "Use my area" rounds the coarse fix to 0.1° before any use or storage,
 * refreshes it at most every 6 h, finds the nearest bundled Environment Canada forecast city, and falls back to Toronto
 * (43.65 N, 79.38 W) when the switch is off, when permission is denied and when anything fails.
 */
class PlaceSourceTest {
    private class FakeLocator(var granted: Boolean = true, var fix: LatLon? = LatLon(43.6789, -79.4123)) : CoarseLocator {
        var calls = 0
        var fail = false

        override fun hasPermission(): Boolean = granted

        override suspend fun locate(): LatLon? {
            calls++
            if (fail) throw IllegalStateException("no provider")
            return fix
        }
    }

    private var now = 1_759_500_000_000L
    private var on = true
    private val locator = FakeLocator()
    private val cache = InMemoryPlaceCache()
    private val source = AreaPlaceSource({ on }, locator, cache, { now })

    @Test
    fun roundsToATenthOfADegreeBeforeUseAndStorage() = runBlocking {
        val place = source.place()
        assertEquals(43.7, place.lat, 0.0)
        assertEquals(-79.4, place.lon, 0.0)
        val stored = assertNotNullAnd(cache.stored)
        // Only the rounded point, the city code and the time are stored: never the raw fix.
        assertEquals(StoredPlace(43.7, -79.4, place.cityCode, now), stored)
    }

    @Test
    fun roundingReadsTheDecimalValue() {
        assertEquals(43.7, EcSites.round(43.65), 0.0)
        assertEquals(43.6, EcSites.round(43.6499), 0.0)
        assertEquals(-79.5, EcSites.round(-79.45), 0.0)
        assertEquals(-79.4, EcSites.round(-79.3501), 0.0)
        assertEquals(0.0, EcSites.round(0.04), 0.0)
        assertEquals(LatLon(45.4, -75.7), EcSites.round(LatLon(45.4215, -75.6972)))
    }

    @Test
    fun offIsTorontoAndDeletesTheStoredPoint() = runBlocking {
        source.place()
        assertNotNull(cache.stored)
        on = false
        assertEquals(Place.TORONTO, source.place())
        assertNull(cache.stored)
        assertEquals(1, locator.calls)
    }

    @Test
    fun deniedIsTorontoWithoutAskingForAFix() = runBlocking {
        locator.granted = false
        assertEquals(Place.TORONTO, source.place())
        assertEquals(0, locator.calls)
        assertNull(cache.stored)
    }

    @Test
    fun refreshesAtMostEverySixHours() = runBlocking {
        val first = source.place()
        assertEquals(1, locator.calls)
        locator.fix = LatLon(45.42, -75.69) // she drove to Ottawa
        now += AreaPlaceSource.REFRESH_MILLIS - 60_000
        assertEquals(first, source.place())
        assertEquals("a fix younger than 6 h is reused", 1, locator.calls)
        now += 60_000
        val second = source.place()
        assertEquals(2, locator.calls)
        assertEquals(45.4, second.lat, 0.0)
        assertEquals(-75.7, second.lon, 0.0)
        assertEquals("s0000430", second.cityCode)
    }

    @Test
    fun aMissingOrFailedFixKeepsTheLastPlaceElseToronto() = runBlocking {
        locator.fix = null
        assertEquals("no fix and nothing stored: Toronto", Place.TORONTO, source.place())
        locator.fix = LatLon(43.26, -79.87)
        val hamilton = source.place()
        assertEquals("s0000549", hamilton.cityCode)
        now += AreaPlaceSource.REFRESH_MILLIS
        locator.fail = true
        assertEquals("a failure keeps the last place", hamilton, source.place())
        cache.stored = null
        assertEquals("never throws", Place.TORONTO, source.place())
    }

    @Test
    fun theNearestForecastCity() {
        fun city(lat: Double, lon: Double) = EcSites.nearest(EcSites.round(LatLon(lat, lon))).name
        assertEquals("Toronto", city(43.6532, -79.3832))
        assertEquals("Hamilton", city(43.2557, -79.8711))
        assertEquals("Ottawa (Kanata - Orléans)", city(45.4215, -75.6972))
        assertEquals("Kingston", city(44.2312, -76.4860))
        assertEquals("Thunder Bay", city(48.3809, -89.2477))
        assertEquals("Montréal", city(45.5019, -73.5674))
        assertEquals("Vancouver", city(49.2827, -123.1207))
        assertEquals("Halifax", city(44.6488, -63.5752))
    }

    @Test
    fun theForecastRequestNamesOnlyTheCity() {
        // The feed is asked for the city's own coordinates, the same for everyone near it.
        assertEquals("https://weather.gc.ca/rss/weather/43.65_-79.38_e.xml", EcSites.TORONTO.feedUrl)
        assertEquals(EcSites.TORONTO, EcSites.byCode(Place.TORONTO.cityCode))
        assertEquals("Hamilton", EcSites.byCode("s0000549")?.name)
        assertNull(EcSites.byCode("nowhere"))
        assertEquals(Place.TORONTO.lat, EcSites.TORONTO.lat, 0.0)
        assertEquals(Place.TORONTO.lon, EcSites.TORONTO.lon, 0.0)
    }

    @Test
    fun theBundledListIsOntarioAndTheMainCities() {
        val sites = EcSites.all
        assertTrue(sites.size in 150..250)
        assertEquals("site codes are unique", sites.size, sites.map { it.code }.toSet().size)
        assertTrue(sites.count { it.province == "ON" } > 100)
        for (p in listOf("QC", "BC", "AB", "MB", "SK", "NS", "NB", "PE", "NL", "YT", "NT", "NU")) {
            assertTrue("a city in $p", sites.any { it.province == p })
        }
        assertTrue("no parks or open water", sites.none { "Park" in it.name || "Island" in it.name })
    }

    private fun <T : Any> assertNotNullAnd(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}

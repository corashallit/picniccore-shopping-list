package earth.cora.picnic.core.calendar

import java.util.Locale

/**
 * Where sunset (egg 12) and the forecast (eggs 11 and 16) are computed for (Cora's answer to Q6, §0.4):
 * the device's approximate area ([lat], [lon]), rounded to 0.1° before any use, when "Use my area" is on and
 * coarse location is granted; otherwise Toronto.
 *
 * The forecast is the nearest Environment Canada city's: [cityCode] is its EC site code (one scheme for every
 * place, Toronto's included: "s0000458"), and [siteLat], [siteLon] are that CITY's own coordinates, which name
 * its feed ([forecastFeedUrl]). The request never carries the phone's point (§0.4: "only the forecast request
 * names the nearest city"). The site coordinates default to the point only for test places built by hand.
 */
data class Place(
    val lat: Double,
    val lon: Double,
    val cityCode: String,
    val siteLat: Double = lat,
    val siteLon: Double = lon,
) {
    /** The city's forecast feed: EC's coordinate feed for the city's own coordinates (§12.4). */
    val forecastFeedUrl: String get() = forecastFeedUrl(siteLat, siteLon)

    companion object {
        /** The default (and the fallback whenever the switch is off or permission is denied): EC site s0000458. */
        val TORONTO: Place = Place(lat = 43.65, lon = -79.38, cityCode = "s0000458", siteLat = 43.65, siteLon = -79.38)

        /**
         * Environment Canada's city forecast feed for a city's coordinates, two decimals:
         * `https://weather.gc.ca/rss/weather/43.65_-79.38_e.xml` (the older `rss/city/<code>_e.xml` feeds answer 404).
         */
        fun forecastFeedUrl(siteLat: Double, siteLon: Double): String =
            "https://weather.gc.ca/rss/weather/${String.format(Locale.ROOT, "%.2f", siteLat)}_${String.format(Locale.ROOT, "%.2f", siteLon)}_e.xml"
    }
}

/**
 * The current [Place] (§0.4 Q6). The app implementation (owners WP8 · WP9, PlaceSourceTest) reads the
 * "Use my area" switch and the coarse location, refreshing at most every 6 h; it never throws and falls
 * back to [Place.TORONTO]. PHASE-0 CONTRACT: [Toronto] is the stub every consumer can use today.
 */
fun interface PlaceSource {
    suspend fun place(): Place

    /** Always Toronto: the switch-off behaviour, and the phase-0 stub. */
    object Toronto : PlaceSource {
        override suspend fun place(): Place = Place.TORONTO
    }
}

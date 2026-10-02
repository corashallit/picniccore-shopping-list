package earth.cora.picnic.core.calendar

/**
 * Where sunset (egg 12) and the forecast (eggs 11 and 16) are computed for (Cora's answer to Q6, §0.4):
 * the device's approximate area, rounded to 0.1° before any use, when "Use my area" is on and coarse
 * location is granted; otherwise Toronto. [cityCode] is the nearest Environment Canada city feed.
 */
data class Place(val lat: Double, val lon: Double, val cityCode: String) {
    companion object {
        /** The default (and the fallback whenever the switch is off or permission is denied). */
        val TORONTO: Place = Place(lat = 43.65, lon = -79.38, cityCode = "on-143")
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

package earth.cora.picnic.core.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Sunset for golden hour (egg 12, docs/DESIGN_STICKER.md §12.2): the NOAA algorithm for a point; no
 * network (D4). The point comes from a [PlaceSource] (§0.4 Q6: the approximate area, or Toronto, the
 * default). Pure. Owner: WP3 (SunClockTest, ±3 min). PHASE-0 STUB: the signatures are the contract.
 */
object SunClock {
    /** Sunset at [place] (from a [PlaceSource]). */
    fun sunset(date: LocalDate, place: Place, zone: ZoneId = ZoneId.of("America/Toronto")): LocalTime =
        sunset(date, place.lat, place.lon, zone)

    fun sunset(
        date: LocalDate,
        lat: Double = 43.65,
        lon: Double = -79.38,
        zone: ZoneId = ZoneId.of("America/Toronto"),
    ): LocalTime = TODO("WP3: SunClock.sunset (§12.2 egg 12, SunClockTest)")
}

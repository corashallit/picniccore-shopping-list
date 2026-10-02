package earth.cora.picnic.core.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunset for golden hour (egg 12, docs/DESIGN_STICKER.md §12.2): the NOAA solar calculator's algorithm for a point
 * (standard refraction, the sun's upper limb at 90.833° from the zenith); no network (D4). The point comes from a
 * [PlaceSource] (§0.4 Q6: the approximate area, or Toronto, the default). Pure. Owner: WP3 (SunClockTest, ±3 min).
 */
object SunClock {
    /** Sunset at [place] (from a [PlaceSource]). */
    fun sunset(date: LocalDate, place: Place, zone: ZoneId = ZoneId.of("America/Toronto")): LocalTime =
        sunset(date, place.lat, place.lon, zone)

    /**
     * Local sunset on [date] at [lat], [lon] (degrees, east positive) in [zone]. Where the sun does not set (polar
     * day) this is solar midnight; where it does not rise (polar night), solar noon.
     */
    fun sunset(
        date: LocalDate,
        lat: Double = 43.65,
        lon: Double = -79.38,
        zone: ZoneId = ZoneId.of("America/Toronto"),
    ): LocalTime {
        val jd = date.toEpochDay() + 2_440_587.5
        val first = sunsetUtcMinutes(jd, lat, lon)
        val minutes = sunsetUtcMinutes(jd + first / 1440.0, lat, lon)
        val utcMidnight = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        val instant: Instant = utcMidnight.plusMillis((minutes * 60_000).roundToLong())
        return instant.atZone(zone).toLocalTime().withNano(0)
    }

    /** NOAA calcSunriseSetUTC for sunset: minutes after 00:00 UTC of the Julian day [jd]. */
    private fun sunsetUtcMinutes(jd: Double, lat: Double, lon: Double): Double {
        val t = (jd - 2_451_545.0) / 36_525.0
        val eqTime = equationOfTime(t)
        val declination = sunDeclination(t)
        val hourAngle = -hourAngleSunrise(lat, declination)
        val delta = lon + degrees(hourAngle)
        return 720 - 4.0 * delta - eqTime
    }

    private fun geomMeanLongSun(t: Double): Double {
        var l0 = 280.46646 + t * (36_000.76983 + t * 0.0003032)
        while (l0 > 360) l0 -= 360
        while (l0 < 0) l0 += 360
        return l0
    }

    private fun geomMeanAnomalySun(t: Double): Double = 357.52911 + t * (35_999.05029 - 0.0001537 * t)

    private fun eccentricityEarthOrbit(t: Double): Double = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun sunEqOfCenter(t: Double): Double {
        val m = radians(geomMeanAnomalySun(t))
        return sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(2 * m) * (0.019993 - 0.000101 * t) + sin(3 * m) * 0.000289
    }

    private fun sunApparentLong(t: Double): Double {
        val trueLong = geomMeanLongSun(t) + sunEqOfCenter(t)
        val omega = 125.04 - 1934.136 * t
        return trueLong - 0.00569 - 0.00478 * sin(radians(omega))
    }

    private fun meanObliquityOfEcliptic(t: Double): Double {
        val seconds = 21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))
        return 23.0 + (26.0 + seconds / 60.0) / 60.0
    }

    private fun obliquityCorrection(t: Double): Double {
        val omega = 125.04 - 1934.136 * t
        return meanObliquityOfEcliptic(t) + 0.00256 * cos(radians(omega))
    }

    private fun sunDeclination(t: Double): Double = degrees(asin(sin(radians(obliquityCorrection(t))) * sin(radians(sunApparentLong(t)))))

    /** Minutes. */
    private fun equationOfTime(t: Double): Double {
        val epsilon = obliquityCorrection(t)
        val l0 = geomMeanLongSun(t)
        val e = eccentricityEarthOrbit(t)
        val m = geomMeanAnomalySun(t)
        var y = tan(radians(epsilon) / 2.0)
        y *= y
        val sin2l0 = sin(2.0 * radians(l0))
        val sinm = sin(radians(m))
        val cos2l0 = cos(2.0 * radians(l0))
        val sin4l0 = sin(4.0 * radians(l0))
        val sin2m = sin(2.0 * radians(m))
        val eTime = y * sin2l0 - 2.0 * e * sinm + 4.0 * e * y * sinm * cos2l0 - 0.5 * y * y * sin4l0 - 1.25 * e * e * sin2m
        return degrees(eTime) * 4.0
    }

    /** Radians; clamped so polar day and night still give a time. */
    private fun hourAngleSunrise(lat: Double, declination: Double): Double {
        val latRad = radians(lat)
        val sdRad = radians(declination)
        val arg = cos(radians(90.833)) / (cos(latRad) * cos(sdRad)) - tan(latRad) * tan(sdRad)
        return acos(arg.coerceIn(-1.0, 1.0))
    }

    private fun radians(degrees: Double): Double = degrees * PI / 180.0

    private fun degrees(radians: Double): Double = radians * 180.0 / PI
}

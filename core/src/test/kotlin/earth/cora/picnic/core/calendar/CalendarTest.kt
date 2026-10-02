package earth.cora.picnic.core.calendar

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Egg 11's seasons (§12.2; checklist 249). */
class SeasonsTest {
    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun thanksgivingIsTheSecondMondayOfOctober() {
        val expected = listOf(
            "2026-10-12", "2027-10-11", "2028-10-09", "2029-10-08", "2030-10-14",
            "2031-10-13", "2032-10-11", "2033-10-10", "2034-10-09", "2035-10-08",
        ).map(::d)
        assertEquals(expected, (2026..2035).map { Seasons.dayOf(Season.THANKSGIVING, it) })
    }

    @Test
    fun fixedDates() {
        (2026..2035).forEach { y ->
            assertEquals(LocalDate.of(y, 7, 1), Seasons.dayOf(Season.CANADA_DAY, y))
            assertEquals(LocalDate.of(y, 10, 31), Seasons.dayOf(Season.HALLOWEEN, y))
            assertEquals(LocalDate.of(y, 2, 14), Seasons.dayOf(Season.VALENTINES, y))
        }
        assertNull(Seasons.dayOf(Season.FIRST_SNOW, 2026), "first snow comes from the forecast")
    }

    @Test
    fun windowsAreTheFourteenDaysUpToAndIncludingTheDay() {
        assertNull(Seasons.active(d("2026-09-28")))
        assertEquals(Season.THANKSGIVING, Seasons.active(d("2026-09-29")), "Thanksgiving 2026: Sep 29–Oct 12")
        assertEquals(Season.THANKSGIVING, Seasons.active(d("2026-10-03")), "the fixture date wears the pumpkin tape")
        assertEquals(Season.THANKSGIVING, Seasons.active(d("2026-10-12")))
        assertNull(Seasons.active(d("2026-10-13")))
        assertNull(Seasons.active(d("2026-10-17")))
        assertEquals(Season.HALLOWEEN, Seasons.active(d("2026-10-18")))
        assertEquals(Season.HALLOWEEN, Seasons.active(d("2026-10-31")))
        assertNull(Seasons.active(d("2026-11-01")))
        assertNull(Seasons.active(d("2027-06-17")))
        assertEquals(Season.CANADA_DAY, Seasons.active(d("2027-06-18")))
        assertEquals(Season.CANADA_DAY, Seasons.active(d("2027-07-01")))
        assertEquals(Season.VALENTINES, Seasons.active(d("2028-02-01")))
        assertEquals(Season.VALENTINES, Seasons.active(d("2028-02-14")))
        assertNull(Seasons.active(d("2028-02-15")))
        // Every window across 2026–2035 is exactly 14 days long.
        (2026..2035).forEach { y ->
            listOf(Season.CANADA_DAY, Season.THANKSGIVING, Season.HALLOWEEN, Season.VALENTINES).forEach { season ->
                val day = Seasons.dayOf(season, y)!!
                val window = Seasons.window(season to day)
                assertEquals(13, Duration.between(window.start.atStartOfDay(), window.endInclusive.atStartOfDay()).toDays())
            }
        }
    }

    @Test
    fun lunarNewYearComesFromTheTable() {
        assertEquals(
            listOf("2027-02-06", "2028-01-26", "2029-02-13", "2030-02-03", "2031-01-23", "2032-02-11", "2033-01-31", "2034-02-19", "2035-02-08").map(::d),
            (2027..2035).map { Seasons.dayOf(Season.LUNAR_NEW_YEAR, it) },
        )
        // 2028-01-26: window Jan 23–30 (−3 to +4), no overlap.
        assertNull(Seasons.active(d("2028-01-22")))
        assertEquals(Season.LUNAR_NEW_YEAR, Seasons.active(d("2028-01-23")))
        assertEquals(Season.LUNAR_NEW_YEAR, Seasons.active(d("2028-01-30")))
        assertNull(Seasons.active(d("2028-01-31")))
        // After 2035 it is skipped, never guessed.
        assertNull(Seasons.dayOf(Season.LUNAR_NEW_YEAR, 2036))
        assertNull(Seasons.dayOf(Season.LUNAR_NEW_YEAR, 2040))
        (LocalDate.of(2036, 1, 1)..LocalDate.of(2036, 2, 28)).forEach { day ->
            assertTrue(Seasons.active(day) != Season.LUNAR_NEW_YEAR, "$day")
        }
    }

    @Test
    fun overlapsGoToTheEarliestDayStillToCome() {
        // 2027: Lunar New Year Feb 6 (window Feb 3–10) inside Valentine's (Feb 1–14).
        assertEquals(Season.VALENTINES, Seasons.active(d("2027-02-02")), "only Valentine's yet")
        assertEquals(Season.LUNAR_NEW_YEAR, Seasons.active(d("2027-02-03")), "Feb 6 comes before Feb 14")
        assertEquals(Season.LUNAR_NEW_YEAR, Seasons.active(d("2027-02-06")))
        assertEquals(Season.VALENTINES, Seasons.active(d("2027-02-07")), "Lunar New Year's day has passed; Valentine's is still to come")
        assertEquals(Season.VALENTINES, Seasons.active(d("2027-02-14")))
    }

    @Test
    fun next() {
        assertEquals(Season.THANKSGIVING to d("2026-10-12"), Seasons.next(d("2026-09-20")), "next! · Thanksgiving Oct 12")
        assertEquals(Season.HALLOWEEN to d("2026-10-31"), Seasons.next(d("2026-10-03")), "an active season is not next")
        assertEquals(Season.VALENTINES to d("2027-02-14"), Seasons.next(d("2026-12-01")), "Valentine's tape shows first")
        assertEquals(Season.LUNAR_NEW_YEAR to d("2027-02-06"), Seasons.next(d("2027-02-01")))
        assertEquals(Season.CANADA_DAY to d("2036-07-01"), Seasons.next(d("2036-03-01")), "the yearly seasons go on after the table")
    }

    @Test
    fun sheetIds() {
        assertEquals("thanksgiving-2026", Seasons.sheetId(Season.THANKSGIVING, d("2026-10-12")))
        assertEquals("lunar-new-year-2027", Seasons.sheetId(Season.LUNAR_NEW_YEAR, d("2027-02-06")))
        assertEquals("first-snow-2026", Seasons.sheetId(Season.FIRST_SNOW, d("2026-11-20")))
    }

    private operator fun LocalDate.rangeTo(other: LocalDate): List<LocalDate> =
        generateSequence(this) { if (it < other) it.plusDays(1) else null }.toList()
}

/** Egg 12's sunset (§12.2; checklist 251). */
class SunClockTest {
    private val toronto = ZoneId.of("America/Toronto")

    /** Reference: the NOAA algorithm as implemented independently by the Python astral library, plus the spec's 18:57. */
    private val reference = mapOf(
        "2026-01-01" to "16:51:10", "2026-02-14" to "17:46:18", "2026-03-07" to "18:13:42", "2026-03-08" to "19:14:58",
        "2026-03-20" to "19:29:43", "2026-05-01" to "20:19:52", "2026-06-21" to "21:02:25", "2026-07-01" to "21:02:30",
        "2026-08-15" to "20:20:42", "2026-10-03" to "18:54:31", "2026-10-12" to "18:38:46", "2026-10-31" to "18:09:15",
        "2026-11-01" to "17:07:53", "2026-12-21" to "16:43:16", "2027-02-06" to "17:35:04", "2030-06-21" to "21:02:25",
        "2035-12-31" to "16:49:56",
    )

    private fun minutesApart(a: LocalTime, b: LocalTime) = Math.abs(Duration.between(a, b).toSeconds()) / 60.0

    @Test
    fun torontoSunsetsWithinThreeMinutes() {
        reference.forEach { (date, time) ->
            val got = SunClock.sunset(LocalDate.parse(date))
            assertTrue(minutesApart(got, LocalTime.parse(time)) <= 3.0, "$date: $got vs $time")
        }
        val fixture = SunClock.sunset(LocalDate.parse("2026-10-03"))
        assertTrue(minutesApart(fixture, LocalTime.of(18, 57)) <= 3.0, "the spec's 18:57 on 2026-10-03: $fixture")
    }

    @Test
    fun daylightSavingTimeIsTheZones() {
        val before = SunClock.sunset(LocalDate.parse("2026-03-07"))
        val after = SunClock.sunset(LocalDate.parse("2026-03-08"))
        assertTrue(Duration.between(before, after).toMinutes() in 55..65, "the clocks go forward an hour")
    }

    @Test
    fun aPlaceFromThePlaceSource() {
        val ottawa = Place(45.4, -75.7, "on-118")
        val got = SunClock.sunset(LocalDate.parse("2026-06-21"), ottawa, toronto)
        assertTrue(minutesApart(got, LocalTime.of(20, 54, 32)) <= 3.0, "Ottawa: $got")
        assertEquals(SunClock.sunset(LocalDate.parse("2026-06-21")), SunClock.sunset(LocalDate.parse("2026-06-21"), Place.TORONTO))
    }

    @Test
    fun polarDaysStillGiveATime() {
        SunClock.sunset(LocalDate.parse("2026-06-21"), 82.5, -62.3, ZoneId.of("America/Toronto"))
        SunClock.sunset(LocalDate.parse("2026-12-21"), 82.5, -62.3, ZoneId.of("America/Toronto"))
    }
}

package earth.cora.picnic.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/*
 * Seasonal sheets (egg 11, docs/DESIGN_STICKER.md §12.2), on the America/Toronto calendar. Pure.
 * Owner: WP3 (SeasonsTest).
 */

/** Persisted in album sheet ids ("thanksgiving-2026"); never rename an entry. */
enum class Season { CANADA_DAY, THANKSGIVING, HALLOWEEN, FIRST_SNOW, VALENTINES, LUNAR_NEW_YEAR }

object Seasons {
    /** Lunar New Year from a table (§12.2): after the last year the season is skipped, never guessed. */
    val LUNAR_NEW_YEAR_DATES: List<LocalDate> = listOf(
        "2026-02-17", "2027-02-06", "2028-01-26", "2029-02-13", "2030-02-03",
        "2031-01-23", "2032-02-11", "2033-01-31", "2034-02-19", "2035-02-08",
    ).map(LocalDate::parse)

    /** Every window but Lunar New Year's is the 14 days up to and including the day. */
    const val WINDOW_DAYS: Long = 14

    /**
     * The season whose window contains [date], or null. Windows: Canada Day Jul 1 (Jun 18–Jul 1), Thanksgiving the
     * 2nd Monday of October, Halloween Oct 31, Valentine's Feb 14, each the 14 days up to and including the day;
     * Lunar New Year −3 to +4 days around its table date, nothing after the table ends. Where windows overlap, the
     * season whose day is the earliest still to come wins (one whose day has passed only wins alone).
     * FIRST_SNOW comes from the weather cache, never from here.
     */
    fun active(date: LocalDate): Season? {
        val containing = occurrencesAround(date).filter { date in window(it) }
        if (containing.isEmpty()) return null
        val toCome = containing.filter { (_, day) -> !day.isBefore(date) }
        return (toCome.minByOrNull { it.second } ?: containing.maxByOrNull { it.second })?.first
    }

    /**
     * The next season to show after [date] (the first window that has not started yet) and its day: the album's
     * Seasonal tab flags it "next! · Halloween Oct 31". The yearly seasons always have a next one; Lunar New Year
     * only within its table.
     */
    fun next(date: LocalDate): Pair<Season, LocalDate>? =
        (0..2).flatMap { occurrences(date.year + it) }
            .filter { window(it).start.isAfter(date) }
            .minWithOrNull(compareBy({ window(it).start }, { it.second }))

    /** [season]'s day in [year] (FIRST_SNOW and a Lunar New Year outside the table: null). */
    fun dayOf(season: Season, year: Int): LocalDate? = when (season) {
        Season.CANADA_DAY -> LocalDate.of(year, Month.JULY, 1)
        Season.THANKSGIVING -> LocalDate.of(year, Month.OCTOBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY))
        Season.HALLOWEEN -> LocalDate.of(year, Month.OCTOBER, 31)
        Season.VALENTINES -> LocalDate.of(year, Month.FEBRUARY, 14)
        Season.LUNAR_NEW_YEAR -> LUNAR_NEW_YEAR_DATES.firstOrNull { it.year == year }
        Season.FIRST_SNOW -> null
    }

    /** The first and last day of the window around [occurrence]'s day, inclusive. */
    fun window(occurrence: Pair<Season, LocalDate>): ClosedRange<LocalDate> {
        val (season, day) = occurrence
        return if (season == Season.LUNAR_NEW_YEAR) day.minusDays(3)..day.plusDays(4) else day.minusDays(WINDOW_DAYS - 1)..day
    }

    /** The album sheet id of [season] in the window that contains its [day]: "thanksgiving-2026". */
    fun sheetId(season: Season, day: LocalDate): String = season.name.lowercase().replace('_', '-') + "-" + day.year

    private fun occurrences(year: Int): List<Pair<Season, LocalDate>> =
        Season.entries.mapNotNull { season -> dayOf(season, year)?.let { season to it } }

    private fun occurrencesAround(date: LocalDate): List<Pair<Season, LocalDate>> = (date.year - 1..date.year + 1).flatMap(::occurrences)
}

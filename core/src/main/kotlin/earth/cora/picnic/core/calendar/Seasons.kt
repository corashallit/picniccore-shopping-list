package earth.cora.picnic.core.calendar

import java.time.LocalDate

/*
 * Seasonal sheets (egg 11, docs/DESIGN_STICKER.md §12.2), on the America/Toronto calendar. Pure.
 * Owner: WP3 (SeasonsTest). PHASE-0 STUB: the signatures are the contract.
 */

/** Persisted in album sheet ids ("thanksgiving-2026"); never rename an entry. */
enum class Season { CANADA_DAY, THANKSGIVING, HALLOWEEN, FIRST_SNOW, VALENTINES, LUNAR_NEW_YEAR }

object Seasons {
    /**
     * The season whose window contains [date] (each window is the 14 days up to and including the day,
     * Lunar New Year −3 to +4 days from its table, nothing after the table ends; overlaps go to the season
     * whose date is the earliest still to come), or null. FIRST_SNOW comes from the weather cache, not here.
     */
    fun active(date: LocalDate): Season? = TODO("WP3: Seasons.active (§12.2 egg 11, SeasonsTest)")

    /** The next season after [date] and its day ("next! · Thanksgiving Oct 12"), or null past the table. */
    fun next(date: LocalDate): Pair<Season, LocalDate>? = TODO("WP3: Seasons.next (§12.2 egg 11, SeasonsTest)")
}

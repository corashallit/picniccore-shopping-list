package earth.cora.picnic.core.model

/*
 * Trip statistics for the LCD, the receipt and the celebration (docs/DESIGN_STICKER.md §7.1).
 * Pure. Owner: WP3 (TripStatsTest). PHASE-0 STUB: the signatures are the contract.
 */

/** stickers = checked items; baskets = stops ("Everything else" included); minutes = frozen shop time; shiny = shiny + jackpot. */
data class TripStatsValue(val stickers: Int, val baskets: Int, val minutes: Int?, val shiny: Int)

object TripStats {
    /** Stats of [list]'s trip; [stops] = GroupedList.stops. Minutes come from Trip.shopMinutes (frozen). */
    fun of(list: GroceryList, stops: Int): TripStatsValue =
        TODO("WP3: TripStats.of (§7.1, TripStatsTest)")

    /** "41 min", "1 h 5 min"; null (a migrated trip) -> "--". */
    fun formatMinutes(m: Int?): String =
        TODO("WP3: TripStats.formatMinutes (§7.1, TripStatsTest)")

    /**
     * The first check-off of the trip's last session (sessions break at any gap over 45 min between
     * check-offs): the book's date label and the receipt's date. Null before the trip opens (show today).
     */
    fun lastSessionStart(list: GroceryList): Long? =
        TODO("WP3: TripStats.lastSessionStart (§7.1, TripStatsTest)")
}

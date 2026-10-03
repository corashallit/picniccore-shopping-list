package earth.cora.picnic.core.model

/*
 * Trip statistics for the LCD, the receipt, the celebration and the date label (docs/DESIGN_STICKER.md §7.1).
 * Pure. Owner: WP3 (TripStatsTest).
 */

/** stickers = checked items; baskets = stops ("Everything else" included); minutes = frozen shop time; shiny = shiny + jackpot. */
data class TripStatsValue(val stickers: Int, val baskets: Int, val minutes: Int?, val shiny: Int)

object TripStats {
    /** Sessions break at any gap over this between check-offs (§7.1): a check at home on Tuesday does not start Saturday's shop. */
    const val SESSION_BREAK_MILLIS: Long = 45L * 60 * 1000

    private const val MINUTE: Long = 60_000

    /**
     * Stats of [list]'s trip; [stops] = GroupedList.stops. Minutes come from Trip.shopMinutes (frozen at the
     * first completion; null before it and for a migrated trip). Without a trip everything but [stops] is 0.
     */
    fun of(list: GroceryList, stops: Int): TripStatsValue {
        val checked = list.items.filter { it.checked }
        return TripStatsValue(
            stickers = checked.size,
            baskets = stops,
            minutes = list.trip?.shopMinutes,
            shiny = checked.count { it.rarity != Rarity.NONE },
        )
    }

    /** "41 min", "1 h 5 min", "2 h"; null (a migrated trip, or not complete yet) -> "--". */
    fun formatMinutes(m: Int?): String {
        if (m == null) return "--"
        val minutes = m.coerceAtLeast(0)
        if (minutes < 60) return "$minutes min"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "$hours h" else "$hours h $rest min"
    }

    /**
     * The first check-off of the trip's last session (sessions break at any gap over 45 min between
     * check-offs): the book's date label and the receipt's date. Null before the trip opens (show today).
     * Only items that are still checked count (an un-check takes its check-off with it).
     */
    fun lastSessionStart(list: GroceryList): Long? = lastSessionStart(list.items)

    /** [lastSessionStart] over [items]. */
    fun lastSessionStart(items: List<GroceryItem>): Long? {
        val times = items.mapNotNull { if (it.checked) it.checkedAt else null }.sorted()
        if (times.isEmpty()) return null
        var start = times.last()
        for (i in times.size - 2 downTo 0) {
            if (start - times[i] > SESSION_BREAK_MILLIS) break
            start = times[i]
        }
        return start
    }

    /**
     * Shop time at a completion at [completedAt] (§7.1): from the first check-off of the last session to the
     * completion, rounded to the nearest minute, at least 1. A trip completed long after its last check-off
     * (the last unchecked item deleted at home, hours later) ends at that last check-off instead: the gap
     * breaks the session just as it would between two check-offs. Null when nothing is checked.
     */
    fun shopMinutes(items: List<GroceryItem>, completedAt: Long): Int? {
        val start = lastSessionStart(items) ?: return null
        val lastCheck = items.mapNotNull { if (it.checked) it.checkedAt else null }.max()
        val end = if (completedAt - lastCheck > SESSION_BREAK_MILLIS) lastCheck else maxOf(completedAt, lastCheck)
        val minutes = (end - start + MINUTE / 2) / MINUTE
        return minutes.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }
}

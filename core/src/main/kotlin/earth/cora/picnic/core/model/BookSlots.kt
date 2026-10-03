package earth.cora.picnic.core.model

/*
 * Book spots of the trip sticker book (docs/DESIGN_STICKER.md §7.2). Pure. Owner: WP3 (BookSlotsTest).
 *
 * The book keeps still, like a real album: there is one spot per item on the list; a check takes the
 * LOWEST free spot, an un-check frees its spot (a hole the next check fills, so later stickers never shift),
 * adding items adds spots at the end, and deleting one removes a spot. Only when the spot count drops below
 * a used spot do the highest stickers move into the lowest holes, in their order.
 */
object BookSlots {
    /**
     * The spot for the item [checkingId] that is being checked: the LOWEST free spot among
     * 0 until items.size (spots = items on the list; a spot is used by another checked item's bookSlot).
     * With [checkingId] on the list there is always one; the result is never negative.
     */
    fun assign(items: List<GroceryItem>, checkingId: String): Int {
        val used = HashSet<Int>()
        for (item in items) if (item.checked && item.id != checkingId) item.bookSlot?.let(used::add)
        var slot = 0
        while (slot in used) slot++
        return slot
    }

    /**
     * Makes every spot valid after the item set changed (a delete, a restore, a revive, a migration).
     * CONTRACT:
     * - Unchecked items have bookSlot = null.
     * - A checked item keeps its spot when the spot is in 0 until items.size and no earlier item (in list
     *   order) holds it.
     * - Every other checked item (spot too high after a delete, a clash, or none at all) moves into the lowest
     *   holes, in order of its old spot (none last), then check-off time, then list order (stable).
     * - Returns [items] itself when nothing changed.
     */
    fun compact(items: List<GroceryItem>): List<GroceryItem> {
        val spots = items.size
        val taken = HashSet<Int>()
        val displaced = ArrayList<Int>() // indices into items
        for ((index, item) in items.withIndex()) {
            if (!item.checked) continue
            val slot = item.bookSlot
            if (slot != null && slot in 0 until spots && taken.add(slot)) continue
            displaced += index
        }
        val needsClearing = items.any { !it.checked && it.bookSlot != null }
        if (displaced.isEmpty() && !needsClearing) return items

        val order = displaced.sortedWith(
            compareBy<Int>({ items[it].bookSlot?.takeIf { slot -> slot >= 0 } ?: Int.MAX_VALUE }, { items[it].checkedAt ?: Long.MAX_VALUE }, { it }),
        )
        val newSlot = HashMap<Int, Int>()
        var hole = 0
        for (index in order) {
            while (hole in taken) hole++
            newSlot[index] = hole
            taken += hole
        }
        return items.mapIndexed { index, item ->
            when {
                !item.checked -> if (item.bookSlot == null) item else item.copy(bookSlot = null)
                index in newSlot -> item.copy(bookSlot = newSlot.getValue(index))
                else -> item
            }
        }
    }

    /** The spot a restored or revived checked item may keep: its own when it is free and inside [spots], else null (lowest free). */
    internal fun reclaimable(items: List<GroceryItem>, slot: Int?, spots: Int): Int? {
        if (slot == null || slot !in 0 until spots) return null
        return slot.takeIf { s -> items.none { it.checked && it.bookSlot == s } }
    }
}

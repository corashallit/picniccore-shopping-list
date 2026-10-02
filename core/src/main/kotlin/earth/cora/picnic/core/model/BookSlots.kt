package earth.cora.picnic.core.model

/*
 * Book spots of the trip sticker book (docs/DESIGN_STICKER.md §7.2). Pure. Owner: WP3 (BookSlotsTest).
 * PHASE-0 STUB: the signatures are the contract; WP3 fills in the bodies.
 */
object BookSlots {
    /**
     * The spot for the item [checkingId] that is being checked: the LOWEST free spot among
     * 0 until items.size (spots = items on the list; a spot is used by a checked item's bookSlot).
     */
    fun assign(items: List<GroceryItem>, checkingId: String): Int =
        TODO("WP3: BookSlots.assign (§7.2, BookSlotsTest)")

    /**
     * After the spot count dropped (a delete): if it is below the highest used spot, the highest
     * stickers move into the lowest holes, in stable order. Unchecked items keep bookSlot = null.
     */
    fun compact(items: List<GroceryItem>): List<GroceryItem> =
        TODO("WP3: BookSlots.compact (§7.2, BookSlotsTest)")
}

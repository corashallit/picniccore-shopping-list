package earth.cora.picnic.core.model

/** Kind of a displayed section. */
enum class SectionKind { UNSORTED, AISLE, CHECKED }

/** One displayed section of a list, in walking order. Pure data: the UI maps it to rows/headers. */
data class AisleSection(
    /** Aisle id, or SectionIds.UNSORTED / SectionIds.CHECKED. Also the key in GroceryList.collapsedAisles. */
    val id: String,
    val kind: SectionKind,
    /** Aisle name; "Unsorted" / "In the basket" for the special sections (the UI may restyle these). */
    val title: String,
    /** The aisle for AISLE sections, else null. */
    val aisle: Aisle?,
    /** Aisle.position for AISLE sections (medallion number, colour rotation); 0 for UNSORTED/CHECKED. */
    val position: Int,
    val basket: BasketKind,
    /**
     * UNSORTED/AISLE: the section's UNCHECKED items, oldest first (addedAt, then list order).
     * CHECKED: every checked item of the list, in aisle walking order, then oldest first.
     */
    val items: List<GroceryItem>,
    /** AISLE/UNSORTED: how many of this aisle's items are checked (they are listed under CHECKED). 0 for CHECKED. */
    val checkedCount: Int,
    val collapsed: Boolean,
) {
    /** True for an aisle whose items are all checked (UI shows a "cleared" header). */
    val cleared: Boolean get() = kind != SectionKind.CHECKED && items.isEmpty() && checkedCount > 0
}

data class GroupedList(
    val sections: List<AisleSection>,
    /** All items on the list. */
    val total: Int,
    /** Unchecked items on the list. */
    val remaining: Int,
)

object ListGrouping {
    /**
     * Groups [list] for display. CONTRACT:
     * - Item -> section: item.aisleId if it is one of [aisles]' ids, otherwise UNSORTED
     *   (null and vanished ids alike).
     * - Section order: UNSORTED first (only if it has unchecked items), then aisles in walking order,
     *   then CHECKED last (only if any item is checked).
     * - An aisle section is present iff it has at least one item (checked or not); an aisle with only
     *   checked items is present with empty [AisleSection.items] and [AisleSection.cleared] = true.
     * - basket: [basketFor] for aisles; BasketKind.DEFAULT for UNSORTED and CHECKED.
     * - collapsed: section id in list.collapsedAisles.
     */
    fun group(list: GroceryList, aisles: List<Aisle>, basketFor: (Aisle) -> BasketKind): GroupedList {
        TODO("core: ListGrouping.group")
    }
}

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
     * UNSORTED/AISLE: the section's UNCHECKED items (still to get), oldest first (addedAt, then list order).
     * CHECKED: every checked item of the list, in aisle walking order, then oldest first.
     */
    val items: List<GroceryItem>,
    /**
     * UNSORTED/AISLE: the section's CHECKED items (already gotten), in the order CHECKED lists them
     * (oldest first: check time is not tracked). This is what the section's basket holds; items still
     * to get are never in it. They are listed under CHECKED too. Empty for CHECKED itself.
     */
    val checkedItems: List<GroceryItem>,
    val collapsed: Boolean,
) {
    /** UNSORTED/AISLE: how many of the section's items are checked ([checkedItems]). 0 for CHECKED. */
    val checkedCount: Int get() = checkedItems.size

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
     * - UNSORTED/AISLE: [AisleSection.items] = unchecked (to get), [AisleSection.checkedItems] = checked
     *   (gotten, the basket's contents); both oldest first. CHECKED: items = every checked item.
     * - basket: [basketFor] for aisles; BasketKind.DEFAULT for UNSORTED and CHECKED.
     * - collapsed: section id in list.collapsedAisles.
     */
    fun group(list: GroceryList, aisles: List<Aisle>, basketFor: (Aisle) -> BasketKind): GroupedList {
        val aisleIds = aisles.mapTo(HashSet()) { it.id }
        // Oldest first; sortedBy is stable, so equal timestamps keep list order.
        val ordered = list.items.sortedBy { it.addedAt }
        val bySection = ordered.groupBy { item -> item.aisleId?.takeIf { it in aisleIds } ?: SectionIds.UNSORTED }

        val sections = mutableListOf<AisleSection>()
        val checkedInWalkingOrder = mutableListOf<GroceryItem>()

        val unsorted = bySection[SectionIds.UNSORTED].orEmpty()
        val (unsortedGotten, unsortedOpen) = unsorted.partition { it.checked }
        checkedInWalkingOrder += unsortedGotten
        if (unsortedOpen.isNotEmpty()) {
            sections += AisleSection(
                id = SectionIds.UNSORTED,
                kind = SectionKind.UNSORTED,
                title = UNSORTED_TITLE,
                aisle = null,
                position = 0,
                basket = BasketKind.DEFAULT,
                items = unsortedOpen,
                checkedItems = unsortedGotten,
                collapsed = SectionIds.UNSORTED in list.collapsedAisles,
            )
        }

        for (aisle in aisles) {
            val items = bySection[aisle.id] ?: continue
            val (gotten, open) = items.partition { it.checked }
            checkedInWalkingOrder += gotten
            sections += AisleSection(
                id = aisle.id,
                kind = SectionKind.AISLE,
                title = aisle.name,
                aisle = aisle,
                position = aisle.position,
                basket = basketFor(aisle),
                items = open,
                checkedItems = gotten,
                collapsed = aisle.id in list.collapsedAisles,
            )
        }

        if (checkedInWalkingOrder.isNotEmpty()) {
            sections += AisleSection(
                id = SectionIds.CHECKED,
                kind = SectionKind.CHECKED,
                title = CHECKED_TITLE,
                aisle = null,
                position = 0,
                basket = BasketKind.DEFAULT,
                items = checkedInWalkingOrder,
                checkedItems = emptyList(),
                collapsed = SectionIds.CHECKED in list.collapsedAisles,
            )
        }

        val remaining = list.items.count { !it.checked }
        return GroupedList(sections = sections, total = list.items.size, remaining = remaining)
    }

    /** Default titles of the special sections (the UI may show its own strings instead). */
    const val UNSORTED_TITLE: String = "Unsorted"
    const val CHECKED_TITLE: String = "In the basket"
}

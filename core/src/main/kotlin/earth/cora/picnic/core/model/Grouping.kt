package earth.cora.picnic.core.model

/*
 * Grouping in place (docs/DESIGN_STICKER.md §19.1, D1, D2). Replaces the v1 shape with its CHECKED
 * section: checked rows stay in their aisle, and each aisle lists what is gotten for its basket.
 * Consumer: the List screen (ListUiState). Owner: WP3 (GroupingInPlaceTest).
 */

/** Kind of a displayed section. There is no CHECKED section any more (D1). */
enum class SectionKind { AISLE, UNSORTED }

/** One stop of a list ("Everything else" included), in walking order. Pure data. */
data class AisleSection(
    /** Aisle id, or SectionIds.UNSORTED. Also the value GroceryList.openAisle names. */
    val id: String,
    val kind: SectionKind,
    /** The aisle for AISLE sections, else null. */
    val aisle: Aisle?,
    /** Walking position (Aisle.position, 1-based) for AISLE sections; 0 for UNSORTED. */
    val position: Int,
    /** The tab's name: [Aisle.displayName] (store prefix stripped); [ListGrouping.UNSORTED_TITLE] for UNSORTED. */
    val displayName: String,
    /** The "A2" chip: AisleStyle.storeAisle, else [storeAisleNumber] of the name; null for UNSORTED. */
    val storeAisle: Int?,
    /** Every item of the section, checked or not, in addedAt order (then list order). Rows stay in place (D1). */
    val rows: List<GroceryItem>,
    /** The checked items in check-off order (checkedAt, falling back to addedAt): what the basket holds (R4). */
    val gotten: List<GroceryItem>,
    /** Unchecked items: the basket badge. */
    val toGet: Int,
    /** All items of the section. */
    val total: Int,
    /** total > 0 and toGet == 0: the aisle is done (lid shut, stamp). */
    val complete: Boolean,
    val basket: BasketKind,
)

data class GroupedList(
    /** UNSORTED first whenever it has any item, then the aisles with items in walking order. */
    val sections: List<AisleSection>,
    /** All items on the list. */
    val total: Int,
    /** Checked items on the list. */
    val checked: Int,
    /** Sections with items, UNSORTED included (= sections.size). */
    val stops: Int,
    /** D2's automatic current aisle: the first aisle in walking order with something left; UNSORTED only when it is all that is left. */
    val currentSectionId: String?,
    /** The open section: GroceryList.openAisle resolved (null -> current, "" -> none, a stale id -> current). */
    val openSectionId: String?,
    /** The "now" ring (§4.13): the open section if it has items left, else the current one. */
    val nowSectionId: String?,
)

object ListGrouping {
    /**
     * Groups [list] for display. CONTRACT:
     * - Item -> section: item.aisleId if it is one of [aisles]' ids, otherwise UNSORTED (null and vanished ids alike).
     * - A section is present iff it has at least one item (checked or not). Order: UNSORTED first, then
     *   aisles in walking order.
     * - rows: every item, oldest first (addedAt, then list order). gotten: the checked ones by checkedAt
     *   (null checkedAt counts as addedAt; ties keep row order).
     * - basket: [basketFor] for aisles; BasketKind.DEFAULT for UNSORTED.
     * - storeAisle: [styleFor]'s storeAisle, else storeAisleNumber(aisle.name).
     */
    fun group(
        list: GroceryList,
        aisles: List<Aisle>,
        basketFor: (Aisle) -> BasketKind,
        styleFor: (Aisle) -> AisleStyle? = { null },
    ): GroupedList {
        val aisleIds = aisles.mapTo(HashSet()) { it.id }
        // Oldest first; sortedBy is stable, so equal timestamps keep list order.
        val ordered = list.items.sortedBy { it.addedAt }
        val bySection = ordered.groupBy { item -> item.aisleId?.takeIf { it in aisleIds } ?: SectionIds.UNSORTED }

        fun section(id: String, kind: SectionKind, aisle: Aisle?, rows: List<GroceryItem>): AisleSection {
            val gotten = rows.filter { it.checked }.sortedBy { it.checkedAt ?: it.addedAt }
            val toGet = rows.size - gotten.size
            return AisleSection(
                id = id,
                kind = kind,
                aisle = aisle,
                position = aisle?.position ?: 0,
                displayName = aisle?.displayName() ?: UNSORTED_TITLE,
                storeAisle = aisle?.let { styleFor(it)?.storeAisle ?: storeAisleNumber(it.name) },
                rows = rows,
                gotten = gotten,
                toGet = toGet,
                total = rows.size,
                complete = rows.isNotEmpty() && toGet == 0,
                // TODO(WP3): §4.10 / §5.2 give "Everything else" the wire shopping basket; BasketKind.DEFAULT
                //  keeps the v1 look until WP3 switches it (with the basket keyword tuning).
                basket = aisle?.let(basketFor) ?: BasketKind.DEFAULT,
            )
        }

        val sections = buildList {
            bySection[SectionIds.UNSORTED]?.let { add(section(SectionIds.UNSORTED, SectionKind.UNSORTED, null, it)) }
            for (aisle in aisles) {
                val rows = bySection[aisle.id] ?: continue
                add(section(aisle.id, SectionKind.AISLE, aisle, rows))
            }
        }

        val current = sections.firstOrNull { it.kind == SectionKind.AISLE && it.toGet > 0 }?.id
            ?: sections.firstOrNull { it.kind == SectionKind.UNSORTED && it.toGet > 0 }?.id
        val open = when (val stored = list.openAisle) {
            null -> current
            "" -> null
            else -> stored.takeIf { id -> sections.any { it.id == id } } ?: current
        }
        val now = open?.takeIf { id -> sections.first { it.id == id }.toGet > 0 } ?: current

        return GroupedList(
            sections = sections,
            total = list.items.size,
            checked = list.items.count { it.checked },
            stops = sections.size,
            currentSectionId = current,
            openSectionId = open,
            nowSectionId = now,
        )
    }

    /** Core's name for the UNSORTED section; the UI shows its own string ("Everything else", §4.10). */
    const val UNSORTED_TITLE: String = "Unsorted"
}

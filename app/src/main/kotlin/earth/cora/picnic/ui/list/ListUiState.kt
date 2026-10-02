package earth.cora.picnic.ui.list

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ListGrouping
import earth.cora.picnic.core.model.SectionIds
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.ui.common.storeAisleNumber

/*
 * UI state of the List screen. Names are binding (docs/ARCHITECTURE.md §10.3); fields may be added.
 */

data class ListUiState(
    val loading: Boolean = true,
    val listId: String = "",
    val title: String = "",
    val lists: List<ListSummary> = emptyList(),
    val sections: List<SectionUi> = emptyList(),
    val remaining: Int = 0,
    val total: Int = 0,
    val editing: EditItemUi? = null,
    /** StoreProfile.name, shown under the title of an empty list. */
    val storeName: String = "",
) {
    val checked: Int get() = total - remaining
}

data class ListSummary(val id: String, val title: String, val remaining: Int, val current: Boolean)

/**
 * PHASE-0 BRIDGE (docs/DESIGN_STICKER.md §19.1, §21.3): core's grouping no longer has a CHECKED section
 * (D1, rows stay in place), so this v1 screen derives its "In the basket" section here, from each aisle's
 * gotten items, to look exactly as before. WP5 replaces SectionUi with the C shape (§19.2) and drops this.
 */
enum class SectionUiKind { UNSORTED, AISLE, CHECKED }

data class SectionUi(
    /** Aisle id or SectionIds.UNSORTED / SectionIds.CHECKED (also the collapse key). */
    val id: String,
    val kind: SectionUiKind,
    /** Aisle name; the screen shows its own strings for the special sections. */
    val title: String,
    /**
     * The store's own aisle number from the name ("Aisle 2 - Baking" -> 2; see storeAisleNumber), or
     * null (unnumbered aisles and the special sections: the medallion shows a star).
     */
    val number: Int?,
    /** position - 1, or -1 for the special sections. */
    val aisleIndex: Int,
    val basket: BasketKind,
    val collapsed: Boolean,
    /** Every item of this aisle is checked. */
    val cleared: Boolean,
    /** Items listed in the section: still to get (unchecked); every checked item for CHECKED. The basket's badge. */
    val count: Int,
    /** Items of this aisle already gotten (checked, listed under CHECKED); 0 for CHECKED itself. */
    val gotten: Int,
    /**
     * What the collapsed basket holds: displayRefs of the first five GOTTEN items (AisleSection.checkedItems;
     * CHECKED: its own items, all gotten). Never an item still to get; empty = an empty basket.
     */
    val inBasket: List<IconRef>,
    val items: List<ItemRowUi>,
)

data class ItemRowUi(
    val id: String,
    val name: String,
    /** "qty · note", or null. */
    val detail: String?,
    /** IconCatalog.displayRef(item, busy). */
    val icon: IconRef,
    /** The icon is being decided or drawn (normalized name in IconRepository.busy). */
    val drawing: Boolean,
    val checked: Boolean,
)

data class EditItemUi(
    val item: GroceryItem,
    /** Current aisles in walking order (for "Move to aisle"). */
    val aisles: List<Aisle>,
    /** Catalog keys for "Change icon": the item's category first, then catalog order. */
    val iconChoices: List<String>,
    /** "Draw a new icon" needs a token (it calls the image model). */
    val canDraw: Boolean,
    /** What the row shows right now (IconCatalog.displayRef). */
    val icon: IconRef = item.iconRef(),
    /** The icon is being decided or drawn. */
    val drawing: Boolean = false,
) {
    /** The aisle the item is listed under (null = Unsorted; vanished ids count as Unsorted). */
    val currentAisleId: String? get() = item.aisleId?.takeIf { id -> aisles.any { it.id == id } }
}

sealed interface ListEvent {
    /** Swipe-to-delete: show "Removed <name>" with Undo -> undoDelete(item, index, listId). */
    data class Deleted(val item: GroceryItem, val index: Int, val listId: String = "") : ListEvent

    /** "Clear checked": show "Cleared N items" with Undo -> undoClear(removed, listId). */
    data class Cleared(val removed: List<IndexedValue<GroceryItem>>, val listId: String) : ListEvent

    data class Message(val text: String) : ListEvent
}

/** "2 · ripe ones": quantity and note joined, or null when both are empty. */
internal fun GroceryItem.detailLine(): String? =
    listOfNotNull(quantity?.trim()?.ifEmpty { null }, note?.trim()?.ifEmpty { null }).joinToString(" · ").ifEmpty { null }

/** Catalog keys with [item]'s category first (catalog order otherwise; the paper bag gets plain catalog order). */
internal fun iconChoicesFor(item: GroceryItem, catalog: IconCatalog): List<String> {
    val category = if (item.icon == IconCatalog.DEFAULT_ICON) null else catalog.categoryOf(item.icon)?.key
    if (category == null) return catalog.keys
    val (first, rest) = catalog.keys.partition { catalog[it]?.category == category }
    return first + rest
}

/**
 * Pure mapping AppState -> ListUiState for the current list (also used by screenshot tests to build
 * realistic states). [busy] holds normalized names whose icon is being decided or drawn.
 */
internal fun buildListUiState(
    state: AppState,
    busy: Set<String>,
    hasToken: Boolean,
    editingId: String?,
    catalog: IconCatalog,
): ListUiState {
    val list = state.currentList()
    val aisles = state.store.aisles()
    val grouped = ListGrouping.group(
        list = list,
        aisles = aisles,
        basketFor = { aisle -> state.store.aisleBaskets[aisle.id] ?: catalog.baskets.guess(aisle) },
        styleFor = { aisle -> state.store.aisleStyles[aisle.id] },
    )

    fun row(item: GroceryItem): ItemRowUi {
        val isBusy = normalizeName(item.name) in busy
        return ItemRowUi(
            id = item.id,
            name = item.name,
            detail = item.detailLine(),
            icon = catalog.displayRef(item, isBusy),
            drawing = isBusy,
            checked = item.checked,
        )
    }

    fun basketRefs(items: List<GroceryItem>) = items.take(BASKET_ICONS).map { catalog.displayRef(it, normalizeName(it.name) in busy) }

    // v1 look (phase-0 bridge): sections list what is still to get, Unsorted only while it has some, and every
    // checked item moves to a trailing "In the basket" section, in walking order, then oldest first.
    val sections = grouped.sections.mapNotNull { section ->
        val toGet = section.rows.filterNot { it.checked }
        if (section.kind == SectionKind.UNSORTED && toGet.isEmpty()) return@mapNotNull null
        SectionUi(
            id = section.id,
            kind = if (section.kind == SectionKind.UNSORTED) SectionUiKind.UNSORTED else SectionUiKind.AISLE,
            title = section.aisle?.name ?: ListGrouping.UNSORTED_TITLE,
            number = section.aisle?.let { storeAisleNumber(it.name) },
            aisleIndex = section.aisle?.let { it.position - 1 } ?: -1,
            basket = section.basket,
            collapsed = section.id in list.collapsedAisles,
            cleared = section.complete,
            count = toGet.size,
            gotten = section.gotten.size,
            // The basket holds what has been gotten (R4): the aisle's checked items, in check-off order.
            inBasket = basketRefs(section.gotten),
            items = toGet.map(::row),
        )
    }
    val gottenInWalkingOrder = grouped.sections.flatMap { section -> section.rows.filter { it.checked } }
    val checkedSection = gottenInWalkingOrder.takeIf { it.isNotEmpty() }?.let { gotten ->
        SectionUi(
            id = SectionIds.CHECKED,
            kind = SectionUiKind.CHECKED,
            title = CHECKED_TITLE,
            number = null,
            aisleIndex = -1,
            basket = BasketKind.DEFAULT,
            collapsed = SectionIds.CHECKED in list.collapsedAisles,
            cleared = false,
            count = gotten.size,
            gotten = 0,
            inBasket = basketRefs(gotten),
            items = gotten.map(::row),
        )
    }

    val editing = editingId
        ?.let { id -> list.items.firstOrNull { it.id == id } }
        ?.let { item ->
            val isBusy = normalizeName(item.name) in busy
            EditItemUi(
                item = item,
                aisles = aisles,
                iconChoices = iconChoicesFor(item, catalog),
                canDraw = hasToken,
                icon = catalog.displayRef(item, isBusy),
                drawing = isBusy,
            )
        }

    return ListUiState(
        loading = false,
        listId = list.id,
        title = list.title,
        lists = state.lists.map { l ->
            ListSummary(id = l.id, title = l.title, remaining = l.items.count { !it.checked }, current = l.id == list.id)
        },
        sections = sections + listOfNotNull(checkedSection),
        remaining = grouped.total - grouped.checked,
        total = grouped.total,
        editing = editing,
        storeName = state.store.name,
    )
}

/** Gotten items peeking out of a collapsed aisle's basket (AisleBasket shows up to 5). */
private const val BASKET_ICONS = 5

/** Title of the bridge's "In the basket" section (the screen shows its own string, list_section_checked). */
private const val CHECKED_TITLE = "In the basket"

package earth.cora.picnic.ui.list

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ListGrouping
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

data class SectionUi(
    /** Aisle id or SectionIds.UNSORTED / SectionIds.CHECKED (also the collapse key). */
    val id: String,
    val kind: SectionKind,
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
    /** Items listed in the section (unchecked ones; every checked item for CHECKED). */
    val count: Int,
    /** displayRefs of the first five items, for the collapsed basket. */
    val miniIcons: List<IconRef>,
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
    val grouped = ListGrouping.group(list, aisles) { aisle ->
        state.store.aisleBaskets[aisle.id] ?: catalog.baskets.guess(aisle)
    }

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

    val sections = grouped.sections.map { section ->
        val rows = section.items.map(::row)
        SectionUi(
            id = section.id,
            kind = section.kind,
            title = section.title,
            number = section.aisle?.let { storeAisleNumber(it.name) },
            aisleIndex = section.aisle?.let { it.position - 1 } ?: -1,
            basket = section.basket,
            collapsed = section.collapsed,
            cleared = section.cleared,
            count = rows.size,
            miniIcons = rows.take(MINI_ICONS).map { it.icon },
            items = rows,
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
        sections = sections,
        remaining = grouped.remaining,
        total = grouped.total,
        editing = editing,
        storeName = state.store.name,
    )
}

/** Mini icons peeking out of a collapsed aisle's basket (AisleBasket shows 2..5). */
private const val MINI_ICONS = 5

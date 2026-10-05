package earth.cora.picnic.core.model

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.store.BookStamp
import earth.cora.picnic.core.store.BookSticker
import earth.cora.picnic.core.store.TripBook

/*
 * The trip book snapshot that New trip and deleting a list file into the album (docs/DESIGN_STICKER.md §7.8).
 * Pure. Owner: WP3 (TripOpsTest.newTripModes).
 */

/** The name a filed stamp of "Everything else" carries (position 0; the UI may show its own string). */
const val EVERYTHING_ELSE_NAME: String = "Everything else"

/**
 * The sticker an item's twin shows in a filed book: its live-drawn sticker, its catalog key, or the capsule
 * while it is still unresolved or matched nothing (D5, [IconCatalog.showsCapsule]: the same rule as the live book's
 * IconCatalog.displayRef, so a twin never turns from capsule into the kraft "Shopping bag" at filing). Frozen at filing.
 */
fun GroceryItem.bookIcon(): IconRef = when {
    customIconId != null -> IconRef(icon, customIconId)
    IconCatalog.showsCapsule(icon, iconFit) -> IconRef(IconCatalog.CAPSULE)
    else -> IconRef(icon)
}

/** The item's twin as it sits in the book right now (also what the Trip book sheet draws). */
fun GroceryItem.bookSticker(now: Long): BookSticker =
    BookSticker(slot = bookSlot ?: -1, name = name, icon = bookIcon(), rarity = rarity, at = checkedAt ?: now)

/**
 * The stamp page of [list] for the current store (§4.13, §7.3): one stamp per stop in walking order,
 * "Everything else" first when it is a stop; colour, pattern and pictogram from the aisle rotation and the
 * aisle editor's styles; done = the stop has nothing left to get.
 */
fun AppState.stamps(list: GroceryList): List<BookStamp> {
    val grouped = ListGrouping.group(
        list = list,
        aisles = store.aisles(),
        basketFor = { store.aisleBaskets[it.id] ?: BasketKind.DEFAULT },
        styleFor = { store.aisleStyles[it.id] },
    )
    return grouped.sections.map { section ->
        val aisle = section.aisle
        val look = if (aisle == null) AisleRotation.EVERYTHING_ELSE else AisleRotation.lookOf(aisle, store.aisleStyles[aisle.id])
        BookStamp(
            position = section.position,
            aisleName = if (aisle == null) EVERYTHING_ELSE_NAME else section.displayName,
            family = look.family,
            pattern = look.pattern,
            pictogram = look.pictogram,
            done = section.complete,
        )
    }
}

/**
 * The book New trip, an automatic new book or deleting the list would file for [listId] right now (§7.8), or null
 * without a trip. It counts the trip's own items (TripRules.bookItems: items added after the finale, for the next
 * shop, stay out). stickers: every checked item in spot order with its sticker, rarity and check-off time; stamps:
 * [stamps] of those items; total: those items (the book's spots); minutes: the frozen shop time; perfect: every one
 * of them checked; completedAt: when the shop was completed (kept when only next week's items re-opened the list);
 * receipt: the printed receipt (filed as saved); bonus: the celebration's bonus sticker; napAisleId (egg 8): the
 * first aisle in walking order whose basket is still empty, unless the cat already trotted off this trip.
 */
fun AppState.tripBook(listId: String, now: Long): TripBook? {
    val list = list(listId) ?: return null
    val trip = list.trip ?: return null
    val items = TripRules.bookItems(list)
    val stickers = items
        .filter { it.checked }
        .sortedWith(compareBy({ it.bookSlot ?: Int.MAX_VALUE }, { it.checkedAt ?: Long.MAX_VALUE }))
        .map { it.bookSticker(now) }
    if (stickers.isEmpty()) return null
    val napAisle = if (trip.napDismissed) {
        null
    } else {
        store.aisles().firstOrNull { aisle ->
            val rows = items.filter { it.aisleId == aisle.id }
            rows.isNotEmpty() && rows.none { it.checked }
        }?.id
    }
    val perfect = items.isNotEmpty() && items.all { it.checked }
    // A shop completed before next week's items were added keeps its completion; the stickers' latest check-off
    // is when it was (the trip's own completedAt was cleared by the add, TripRules.reconcile).
    val completedAt = trip.completedAt ?: if (perfect) stickers.maxOf { it.at } else null
    return TripBook(
        id = trip.id,
        listTitle = list.title,
        storeName = trip.storeName,
        startedAt = trip.startedAt,
        completedAt = completedAt,
        closedAt = now,
        total = items.size,
        minutes = trip.shopMinutes,
        stickers = stickers,
        stamps = stamps(list.copy(items = items)),
        perfect = perfect,
        receipt = trip.receipt,
        bonus = trip.bonus,
        napAisleId = napAisle,
    )
}

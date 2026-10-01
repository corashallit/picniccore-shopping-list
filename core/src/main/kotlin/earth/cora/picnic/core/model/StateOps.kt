package earth.cora.picnic.core.model

import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.NamedItem

/*
 * Pure state transitions. CONTRACT: the app changes AppState ONLY through these functions,
 * inside StateStore.update { ... }. They never do I/O, never throw for unknown ids (they return
 * the state unchanged), and return the SAME instance when nothing changed (so the store can skip
 * the write).
 */

object StateOps {
    /** Cap for AppState.corrections (oldest dropped first). */
    const val MAX_CORRECTIONS: Int = 200

    /** Corrections sent to the model per call (newest first, only those whose aisle still exists). */
    const val MAX_PROMPT_CORRECTIONS: Int = 60

    /** A fresh install: one empty list titled "Groceries", default store profile. */
    fun initialState(listId: String, now: Long): AppState = AppState(
        lists = listOf(GroceryList(id = listId, title = "Groceries", createdAt = now)),
        currentListId = listId,
    )
}

/** The current list (falls back to the first list if currentListId is stale). */
fun AppState.currentList(): GroceryList = lists.firstOrNull { it.id == currentListId } ?: lists.first()

fun AppState.list(listId: String): GroceryList? = lists.firstOrNull { it.id == listId }

/** Applies [transform] to one list. */
fun AppState.updateList(listId: String, transform: (GroceryList) -> GroceryList): AppState {
    TODO("core: updateList")
}

/**
 * Appends [items] to the list. Items whose normalizeName matches an UNCHECKED item already on the
 * list, or an earlier item in [items], are dropped. Aisle ids are kept as given (callers already
 * applied corrections; unknown ids render as Unsorted).
 */
fun AppState.addItems(listId: String, items: List<GroceryItem>): AppState {
    TODO("core: addItems")
}

fun AppState.toggleChecked(listId: String, itemId: String): AppState {
    TODO("core: toggleChecked")
}

/** Removes one item. For undo, the caller remembers the item and its index first, then calls [restoreItem]. */
fun AppState.removeItem(listId: String, itemId: String): AppState {
    TODO("core: removeItem")
}

/** Re-inserts a removed item at [index] (clamped). No-op if an item with that id exists. */
fun AppState.restoreItem(listId: String, item: GroceryItem, index: Int): AppState {
    TODO("core: restoreItem")
}

/** Generic edit (rename, quantity, note, icon). Use [moveItemToAisle] for aisle changes. */
fun AppState.editItem(listId: String, itemId: String, transform: (GroceryItem) -> GroceryItem): AppState {
    TODO("core: editItem")
}

/**
 * The user moved an item. Sets aisleId (null = Unsorted) and manuallyPlaced = true, and when
 * [aisleId] != null records a Correction(normalizeName(item.name), aisleId, now) (replacing any
 * correction for that name; capped at StateOps.MAX_CORRECTIONS).
 */
fun AppState.moveItemToAisle(listId: String, itemId: String, aisleId: String?, now: Long): AppState {
    TODO("core: moveItemToAisle")
}

/** User chose a catalog icon in the edit sheet: icon = key, iconFit = EXACT, customIconId = null. */
fun AppState.setItemIcon(listId: String, itemId: String, iconKey: String): AppState {
    TODO("core: setItemIcon")
}

/** Removes all checked items from the list. */
fun AppState.clearChecked(listId: String): AppState {
    TODO("core: clearChecked")
}

fun AppState.setSectionCollapsed(listId: String, sectionId: String, collapsed: Boolean): AppState {
    TODO("core: setSectionCollapsed")
}

/** Adds an empty list and makes it current. */
fun AppState.newList(listId: String, title: String, now: Long): AppState {
    TODO("core: newList")
}

fun AppState.switchList(listId: String): AppState {
    TODO("core: switchList")
}

fun AppState.renameList(listId: String, title: String): AppState {
    TODO("core: renameList")
}

/** Deletes a list. Never deletes the last one. If it was current, the neighbouring list becomes current. */
fun AppState.deleteList(listId: String): AppState {
    TODO("core: deleteList")
}

/**
 * Replaces the aisle text. Drops aisleBaskets entries for vanished aisle ids and fills missing
 * ones with [guessBasket] (so the UI shows baskets immediately). Item aisle ids are NOT touched
 * here: vanished ids render as Unsorted until AisleSync recategorizes them.
 */
fun AppState.setAislesText(text: String, guessBasket: (Aisle) -> BasketKind): AppState {
    TODO("core: setAislesText")
}

/** Merges model-assigned baskets (ignores ids that are not current aisles). */
fun AppState.setAisleBaskets(baskets: Map<String, BasketKind>): AppState {
    TODO("core: setAisleBaskets")
}

/**
 * Applies recategorization results (item id -> aisle id or null) across ALL lists.
 * Skips items that were manually placed into an aisle that still exists, and ids that are not
 * current aisles (treated as null).
 */
fun AppState.applyAisleAssignments(assignments: Map<String, String?>): AppState {
    TODO("core: applyAisleAssignments")
}

/**
 * A bespoke icon finished drawing. For every item in every list with normalizeName(name) ==
 * [normalizedName]: set customIconId when it is null, or always when [overwrite] (a user "redraw").
 */
fun AppState.attachCustomIcon(normalizedName: String, customIconId: String, overwrite: Boolean): AppState {
    TODO("core: attachCustomIcon")
}

/**
 * Input for recategorize: items in ALL lists (checked or not) whose aisleId is null or not a current
 * aisle id, except items the user deliberately moved to Unsorted (manuallyPlaced && aisleId == null).
 * Deduplicated by item id.
 */
fun AppState.itemsNeedingAisle(): List<NamedItem> {
    TODO("core: itemsNeedingAisle")
}

/** Prompt context for [listId]: aisles, newest valid corrections (max StateOps.MAX_PROMPT_CORRECTIONS), unchecked names. */
fun AppState.ingestContext(listId: String): IngestContext {
    TODO("core: ingestContext")
}

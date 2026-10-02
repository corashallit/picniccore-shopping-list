package earth.cora.picnic.core.model

import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.catalog.IconCatalog

/*
 * Pure state transitions. CONTRACT (docs/ARCHITECTURE.md §4.3): the app changes AppState ONLY through
 * these functions, inside StateStore.update { ... }. They never do I/O, never throw for unknown ids
 * (they return the state unchanged), and return the SAME instance when nothing changed (so the store
 * can skip the write).
 */

object StateOps {
    /** Cap for AppState.corrections (oldest dropped first). */
    const val MAX_CORRECTIONS: Int = 200

    /** Cap for AppState.iconOverrides (oldest dropped first). */
    const val MAX_ICON_OVERRIDES: Int = 200

    /** Corrections sent to the model per call (newest first, only those whose aisle still exists). */
    const val MAX_PROMPT_CORRECTIONS: Int = 60

    /** A fresh install: one empty list titled "Groceries", default store profile. */
    fun initialState(listId: String, now: Long): AppState = AppState(
        lists = listOf(GroceryList(id = listId, title = DEFAULT_LIST_TITLE, createdAt = now)),
        currentListId = listId,
    )
}

/** The current list (falls back to the first list if currentListId is stale). */
fun AppState.currentList(): GroceryList = lists.firstOrNull { it.id == currentListId } ?: lists.first()

fun AppState.list(listId: String): GroceryList? = lists.firstOrNull { it.id == listId }

/** Finds an item in any list. */
fun AppState.findItem(itemId: String): GroceryItem? = lists.firstNotNullOfOrNull { l -> l.items.firstOrNull { it.id == itemId } }

/** Applies [transform] to one list. Unknown id, or a transform that returns the same instance -> this. */
fun AppState.updateList(listId: String, transform: (GroceryList) -> GroceryList): AppState {
    val index = lists.indexOfFirst { it.id == listId }
    if (index < 0) return this
    val before = lists[index]
    val after = transform(before)
    if (after === before) return this
    return copy(lists = lists.toMutableList().also { it[index] = after })
}

/** Applies [transform] to every item of every list; keeps instances when nothing changed. */
private fun AppState.mapAllItems(transform: (GroceryItem) -> GroceryItem): AppState {
    var changed = false
    val newLists = lists.map { list ->
        var listChanged = false
        val items = list.items.map { item ->
            val next = transform(item)
            if (next !== item) listChanged = true
            next
        }
        if (listChanged) {
            changed = true
            list.copy(items = items)
        } else {
            list
        }
    }
    return if (changed) copy(lists = newLists) else this
}

private fun GroceryList.updateItem(itemId: String, transform: (GroceryItem) -> GroceryItem): GroceryList {
    val index = items.indexOfFirst { it.id == itemId }
    if (index < 0) return this
    val before = items[index]
    val after = transform(before)
    if (after === before || after == before) return this
    return copy(items = items.toMutableList().also { it[index] = after })
}

/**
 * Appends [items] to the list. Items whose normalizeName matches an UNCHECKED item already on the
 * list, or an earlier item in [items], are dropped. Aisle ids are kept as given (callers already
 * applied corrections; unknown ids render as Unsorted). Callers use [findItem] / the returned
 * state to learn which ids were actually added.
 */
fun AppState.addItems(listId: String, items: List<GroceryItem>): AppState {
    if (items.isEmpty()) return this
    val allIds = lists.flatMapTo(HashSet()) { l -> l.items.map { it.id } }
    return updateList(listId) { list ->
        val seen = list.items.filterNot { it.checked }.mapTo(HashSet()) { normalizeName(it.name) }
        val toAdd = items.filter { item ->
            val key = normalizeName(item.name)
            key.isNotEmpty() && item.id !in allIds && seen.add(key) && allIds.add(item.id)
        }
        if (toAdd.isEmpty()) list else list.copy(items = list.items + toAdd)
    }
}

fun AppState.toggleChecked(listId: String, itemId: String): AppState =
    updateList(listId) { list -> list.updateItem(itemId) { it.copy(checked = !it.checked) } }

/** Removes one item. For undo, the caller remembers the item and its index first, then calls [restoreItem]. */
fun AppState.removeItem(listId: String, itemId: String): AppState = updateList(listId) { list ->
    if (list.items.none { it.id == itemId }) list else list.copy(items = list.items.filterNot { it.id == itemId })
}

/** Re-inserts a removed item at [index] (clamped). No-op if an item with that id exists. */
fun AppState.restoreItem(listId: String, item: GroceryItem, index: Int): AppState {
    if (findItem(item.id) != null) return this
    return updateList(listId) { list ->
        val at = index.coerceIn(0, list.items.size)
        list.copy(items = list.items.toMutableList().also { it.add(at, item) })
    }
}

/**
 * Edit sheet "save": name (trimmed, blank = keep), quantity and note (blank = null).
 * When normalizeName(name) changes, the icon is re-resolved (icon cascade step 1):
 * customIconId = null; icon/iconFit = [localIcon] for the new name, else DEFAULT_ICON + PENDING.
 * The caller then passes the updated item to IconRepository.submit (steps 2 and 3).
 * Aisle changes use [moveItemToAisle]; icon choices use [setItemIcon].
 */
fun AppState.editItemDetails(
    listId: String,
    itemId: String,
    name: String,
    quantity: String?,
    note: String?,
    catalog: IconCatalog,
): AppState {
    val newName = name.trim().replace(WHITESPACE, " ")
    val newQuantity = quantity?.trim()?.ifEmpty { null }
    val newNote = note?.trim()?.ifEmpty { null }
    return updateList(listId) { list ->
        list.updateItem(itemId) { item ->
            val finalName = newName.ifEmpty { item.name }
            val renamed = normalizeName(finalName) != normalizeName(item.name)
            if (!renamed) {
                item.copy(name = finalName, quantity = newQuantity, note = newNote)
            } else {
                val local = localIcon(finalName, catalog)
                item.copy(
                    name = finalName,
                    quantity = newQuantity,
                    note = newNote,
                    icon = local ?: IconCatalog.DEFAULT_ICON,
                    iconFit = if (local != null) IconFit.EXACT else IconFit.PENDING,
                    customIconId = null,
                )
            }
        }
    }
}

/**
 * The user moved an item. Sets aisleId (null = Unsorted) and manuallyPlaced = true, and when
 * [aisleId] != null records a Correction(normalizeName(item.name), aisleId, now) (replacing any
 * correction for that name; capped at StateOps.MAX_CORRECTIONS). Moving to Unsorted (null) removes
 * a stale correction for that name, so new items with that name are no longer auto-placed there.
 * An [aisleId] that is not a current aisle is a no-op.
 */
fun AppState.moveItemToAisle(listId: String, itemId: String, aisleId: String?, now: Long): AppState {
    if (aisleId != null && store.aisles().none { it.id == aisleId }) return this
    val item = list(listId)?.items?.firstOrNull { it.id == itemId } ?: return this
    val moved = updateList(listId) { list ->
        list.updateItem(itemId) { it.copy(aisleId = aisleId, manuallyPlaced = true) }
    }
    val key = normalizeName(item.name)
    if (key.isEmpty()) return moved
    val others = corrections.filterNot { it.name == key }
    val newCorrections = if (aisleId == null) {
        others
    } else {
        (others + Correction(key, aisleId, now)).takeLast(StateOps.MAX_CORRECTIONS)
    }
    return if (newCorrections == corrections) moved else moved.copy(corrections = newCorrections)
}

/**
 * The user picked a catalog icon in the edit sheet: icon = key, iconFit = EXACT, customIconId = null,
 * and an IconOverride(normalizeName(item.name), key, now) is recorded (replacing any override for that
 * name; capped at StateOps.MAX_ICON_OVERRIDES). Keys that are not in the catalog are a no-op. The
 * caller also calls IconRepository.forgetBespoke(name) so a cached bespoke icon is not re-attached
 * to future items. Pass the catalog-validated key; [catalog] defaults to the bundled catalog.
 */
fun AppState.setItemIcon(
    listId: String,
    itemId: String,
    iconKey: String,
    now: Long,
    catalog: IconCatalog = IconCatalog.default,
): AppState {
    if (iconKey !in catalog) return this
    val item = list(listId)?.items?.firstOrNull { it.id == itemId } ?: return this
    val updated = updateList(listId) { list ->
        list.updateItem(itemId) { it.copy(icon = iconKey, iconFit = IconFit.EXACT, customIconId = null) }
    }
    val key = normalizeName(item.name)
    if (key.isEmpty()) return updated
    val overrides = (iconOverrides.filterNot { it.name == key } + IconOverride(key, iconKey, now))
        .takeLast(StateOps.MAX_ICON_OVERRIDES)
    return updated.copy(iconOverrides = overrides)
}

/** Removes all checked items from the list. */
fun AppState.clearChecked(listId: String): AppState = updateList(listId) { list ->
    if (list.items.none { it.checked }) list else list.copy(items = list.items.filterNot { it.checked })
}

fun AppState.setSectionCollapsed(listId: String, sectionId: String, collapsed: Boolean): AppState =
    updateList(listId) { list ->
        when {
            collapsed && sectionId !in list.collapsedAisles -> list.copy(collapsedAisles = list.collapsedAisles + sectionId)
            !collapsed && sectionId in list.collapsedAisles -> list.copy(collapsedAisles = list.collapsedAisles - sectionId)
            else -> list
        }
    }

/** Adds an empty list and makes it current. Blank title -> "Groceries". An existing [listId] is a no-op. */
fun AppState.newList(listId: String, title: String, now: Long): AppState {
    if (lists.any { it.id == listId }) return this
    val list = GroceryList(id = listId, title = title.trim().ifEmpty { DEFAULT_LIST_TITLE }, createdAt = now)
    return copy(lists = lists + list, currentListId = listId)
}

fun AppState.switchList(listId: String): AppState =
    if (listId == currentListId || lists.none { it.id == listId }) this else copy(currentListId = listId)

/** Renames a list (trimmed; blank = no-op). */
fun AppState.renameList(listId: String, title: String): AppState {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return this
    return updateList(listId) { if (it.title == trimmed) it else it.copy(title = trimmed) }
}

/** Deletes a list. Never deletes the last one. If it was current, the neighbouring list becomes current. */
fun AppState.deleteList(listId: String): AppState {
    val index = lists.indexOfFirst { it.id == listId }
    if (index < 0 || lists.size <= 1) return this
    val remaining = lists.filterIndexed { i, _ -> i != index }
    val current = if (currentListId == listId || remaining.none { it.id == currentListId }) {
        remaining[index.coerceAtMost(remaining.size - 1)].id
    } else {
        currentListId
    }
    return copy(lists = remaining, currentListId = current)
}

/** Store name shown on the Aisles screen (trimmed; blank = keep). */
fun AppState.setStoreName(name: String): AppState {
    val trimmed = name.trim()
    return if (trimmed.isEmpty() || trimmed == store.name) this else copy(store = store.copy(name = trimmed))
}

/**
 * Replaces the aisle text. Drops aisleBaskets entries for vanished aisle ids and fills missing
 * ones with [guessBasket] (so the UI shows baskets immediately). Item aisle ids are NOT touched
 * here: vanished ids render as Unsorted until AisleSync recategorizes them.
 */
fun AppState.setAislesText(text: String, guessBasket: (Aisle) -> BasketKind): AppState {
    val aisles = Aisles.parse(text)
    val baskets = aisles.associate { aisle -> aisle.id to (store.aisleBaskets[aisle.id] ?: guessBasket(aisle)) }
    if (text == store.aislesText && baskets == store.aisleBaskets) return this
    return copy(store = store.copy(aislesText = text, aisleBaskets = baskets))
}

/** Merges model-assigned baskets (ignores ids that are not current aisles). */
fun AppState.setAisleBaskets(baskets: Map<String, BasketKind>): AppState {
    val ids = store.aisles().mapTo(HashSet()) { it.id }
    val merged = store.aisleBaskets + baskets.filterKeys { it in ids }
    return if (merged == store.aisleBaskets) this else copy(store = store.copy(aisleBaskets = merged))
}

/**
 * Applies recategorization results (item id -> aisle id or null) across ALL lists.
 * Skips items the user placed by hand into an aisle that still exists (or deliberately into Unsorted),
 * and treats ids that are not current aisles as null. Applied items are no longer "manually placed".
 */
fun AppState.applyAisleAssignments(assignments: Map<String, String?>): AppState {
    if (assignments.isEmpty()) return this
    val ids = store.aisles().mapTo(HashSet()) { it.id }
    return mapAllItems { item ->
        if (item.id !in assignments) return@mapAllItems item
        val keptByUser = item.manuallyPlaced && (item.aisleId == null || item.aisleId in ids)
        if (keptByUser) return@mapAllItems item
        val target = assignments[item.id]?.takeIf { it in ids }
        if (target == item.aisleId && !item.manuallyPlaced) item else item.copy(aisleId = target, manuallyPlaced = false)
    }
}

/**
 * Icon cascade step 2 results, keyed by normalizeName(item name). Applies across ALL lists, only to
 * items whose iconFit is still PENDING (a user choice made meanwhile wins): icon/iconFit = the value.
 */
fun AppState.applyIconDecisions(decisions: Map<String, ResolvedIcon>): AppState {
    if (decisions.isEmpty()) return this
    return mapAllItems { item ->
        if (item.iconFit != IconFit.PENDING) return@mapAllItems item
        val decision = decisions[normalizeName(item.name)] ?: return@mapAllItems item
        item.copy(icon = decision.icon, iconFit = decision.fit)
    }
}

/**
 * A bespoke icon finished drawing. For every item in every list with normalizeName(name) ==
 * [normalizedName]: set customIconId when it is null, or always when [overwrite] (a user "redraw").
 */
fun AppState.attachCustomIcon(normalizedName: String, customIconId: String, overwrite: Boolean): AppState =
    mapAllItems { item ->
        val applies = (overwrite || item.customIconId == null) &&
            item.customIconId != customIconId &&
            normalizeName(item.name) == normalizedName
        if (applies) item.copy(customIconId = customIconId) else item
    }

/**
 * Icon cascade step 1 for a free-text name: the newest IconOverride for normalizeName(name) (if its key
 * is still in the catalog), else IconCatalog.match(name)?.key, else null. Pure; no network.
 */
fun AppState.localIcon(name: String, catalog: IconCatalog): String? {
    val key = normalizeName(name)
    val override = iconOverrides.lastOrNull { it.name == key }?.icon?.takeIf { it in catalog }
    return override ?: catalog.match(name)?.key
}

/** Newest override per normalized name, as a map (for IngestContext and OfflineParser). */
fun AppState.iconOverrideMap(): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    // Oldest first: later entries overwrite earlier ones; ties on `at` resolve to the later entry.
    iconOverrides.sortedBy { it.at }.forEach { map[it.name] = it.icon }
    return map
}

/**
 * Input for IconDecider: one entry per distinct normalizeName among items (ALL lists) whose
 * iconFit == PENDING. NamedItem.id = the normalized name, NamedItem.name = a display name.
 */
fun AppState.itemsNeedingIconDecision(): List<NamedItem> {
    val byKey = LinkedHashMap<String, String>()
    for (list in lists) for (item in list.items) {
        if (item.iconFit != IconFit.PENDING) continue
        val key = normalizeName(item.name)
        if (key.isNotEmpty() && key !in byKey) byKey[key] = item.name
    }
    return byKey.map { (key, name) -> NamedItem(id = key, name = name) }
}

/**
 * Input for recategorize: items in ALL lists (checked or not) whose aisleId is null or not a current
 * aisle id, except items the user deliberately moved to Unsorted (manuallyPlaced && aisleId == null).
 * Deduplicated by item id.
 */
fun AppState.itemsNeedingAisle(): List<NamedItem> {
    val ids = store.aisles().mapTo(HashSet()) { it.id }
    val seen = HashSet<String>()
    val out = mutableListOf<NamedItem>()
    for (list in lists) for (item in list.items) {
        val needs = item.aisleId == null || item.aisleId !in ids
        val deliberate = item.manuallyPlaced && item.aisleId == null
        if (needs && !deliberate && seen.add(item.id)) out += NamedItem(id = item.id, name = item.name)
    }
    return out
}

/**
 * Prompt context for [listId]: aisles, newest valid corrections (max StateOps.MAX_PROMPT_CORRECTIONS),
 * unchecked names, and [iconOverrideMap].
 */
fun AppState.ingestContext(listId: String): IngestContext {
    val aisles = store.aisles()
    val ids = aisles.mapTo(HashSet()) { it.id }
    val seenNames = HashSet<String>()
    val corrections = corrections
        .sortedByDescending { it.at }
        .filter { it.aisleId in ids && seenNames.add(it.name) }
        .take(StateOps.MAX_PROMPT_CORRECTIONS)
    val existing = list(listId)?.items.orEmpty().filterNot { it.checked }.map { it.name }
    return IngestContext(
        aisles = aisles,
        corrections = corrections,
        existingNames = existing,
        iconOverrides = iconOverrideMap(),
    )
}

/** Title of a new list when none is given (also the first list's title). */
const val DEFAULT_LIST_TITLE: String = "Groceries"

private val WHITESPACE = Regex("\\s+")

package earth.cora.picnic.ui.pantry

import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.model.PantryMatch
import earth.cora.picnic.core.model.PantryNames
import earth.cora.picnic.core.model.normalizeName

/*
 * The screen-side pantry steps that core's text-level ops (setPantryText, addToPantry, removeFromPantry; WP3) do not
 * cover: one whole name onto the shelf (the Edit sheet's toggle must not split "Mac and cheese"), finding the entry that
 * covers an item, and Undo putting a sticker back exactly where it was. Pure. Owner: WP8 (PantryScreenTest, EditSheetTest).
 */

/** The shelf entry that covers [itemName] (normalizeName equality with singular/plural folding, [PantryMatch.covers]). */
fun AppState.pantryEntryFor(itemName: String): PantryEntry? = pantry.firstOrNull { PantryMatch.covers(it.name, itemName) }

/**
 * Puts [itemName] on the shelf as ONE entry (the Edit sheet's "In my pantry", §6.3): cleaned like a pantry entry
 * (bullets and quantities dropped, capitalised) but never split on commas or "and". Already covered -> this.
 */
fun AppState.putOnShelf(itemName: String, now: Long): AppState {
    if (pantryEntryFor(itemName) != null) return this
    val name = PantryNames.clean(itemName.replace(Regex("[\\r\\n]+"), " ")) ?: return this
    if (pantry.any { normalizeName(it.name) == normalizeName(name) }) return this
    return copy(pantry = pantry + PantryEntry(name = name, icon = null, addedAt = now))
}

/** "Undo" after "Take off the shelf" (§6.13): [entry] goes back at [index] with its sticker and date. Already back -> this. */
fun AppState.restoreToShelf(entry: PantryEntry, index: Int): AppState {
    if (pantry.any { normalizeName(it.name) == normalizeName(entry.name) }) return this
    return copy(pantry = pantry.toMutableList().apply { add(index.coerceIn(0, size), entry) })
}

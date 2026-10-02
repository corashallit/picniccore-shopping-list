package earth.cora.picnic.core.model

import earth.cora.picnic.core.assistant.ProposedItem

/*
 * The pantry (Cora's answer to Q5, docs/DESIGN_STICKER.md §0.4, §6.13): what she keeps at home. One pantry for
 * the app; it never adds or removes list items, it only tells the AI what to leave out of the things it works
 * out. Owner: WP3 (PantryOpsTest, PantryMatchTest). PHASE-0 CONTRACT: the one-line ops have bodies; the rest
 * are TODO("WP3: ...") stubs.
 */

/** Pantry entries sent with an AI request: the first 200 in shelf order (§6.13). */
const val PANTRY_IN_CONTEXT: Int = 200

/** "Pantry as text": one entry per line, shelf order. */
fun AppState.pantryText(): String = pantry.joinToString("\n") { it.name }

/**
 * Commits the "Pantry as text" field: splits on new lines, commas and " and ", drops quantities ("2 kg flour"
 * -> "Flour"), capitalises like an item name, dedupes by normalizeName, keeps the text's order and the icon
 * and addedAt of unchanged names (new ones get [now]).
 */
fun AppState.setPantryText(text: String, now: Long): AppState = TODO("WP3: setPantryText (§6.13, PantryOpsTest)")

/** Adds entries (the add field, the Edit sheet toggle, a suggestion chip); names already there are skipped (same state if all were). */
fun AppState.addToPantry(names: List<String>, now: Long): AppState = TODO("WP3: addToPantry (§6.13, PantryOpsTest)")

/** "Take off the shelf" / the Edit sheet toggle off: removes the entry whose normalizeName is [normalizedName]. */
fun AppState.removeFromPantry(normalizedName: String): AppState {
    val kept = pantry.filterNot { normalizeName(it.name) == normalizedName }
    return if (kept.size == pantry.size) this else copy(pantry = kept)
}

/** "Change sticker" on a shelf sticker, or the resolved icon; null = back to unresolved. */
fun AppState.setPantryIcon(normalizedName: String, icon: IconRef?): AppState {
    var changed = false
    val updated = pantry.map { entry ->
        if (normalizeName(entry.name) == normalizedName && entry.icon != icon) {
            changed = true
            entry.copy(icon = icon)
        } else {
            entry
        }
    }
    return if (changed) copy(pantry = updated) else this
}

/** The Edit sheet's "In my pantry" toggle state: an entry with the same normalizeName is on the shelf. */
fun AppState.inPantry(name: String): Boolean {
    val key = normalizeName(name)
    return key.isNotEmpty() && pantry.any { normalizeName(it.name) == key }
}

/** The client-side backstop for the model's pantry rule (§6.2 rule 8). Pure. */
object PantryMatch {
    /** normalizeName equality with singular/plural folding only: "eggs"/"egg", "berries"/"berry"; "flour" does not cover "almond flour". */
    fun covers(entry: String, itemName: String): Boolean = TODO("WP3: PantryMatch.covers (§6.2 rule 8, PantryMatchTest)")

    /** A named item is never skipped for the pantry (switched back on); a worked-out item an entry covers is skipped as IN_PANTRY. */
    fun apply(items: List<ProposedItem>, pantry: List<String>): List<ProposedItem> =
        TODO("WP3: PantryMatch.apply (§6.2 rule 8, PantryMatchTest)")
}

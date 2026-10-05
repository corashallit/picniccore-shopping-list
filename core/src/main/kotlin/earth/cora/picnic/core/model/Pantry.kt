package earth.cora.picnic.core.model

import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.SkipReason

/*
 * The pantry (Cora's answer to Q5, docs/DESIGN_STICKER.md §0.4, §6.13): what she keeps at home. One pantry for
 * the app; it never adds or removes list items, it only tells the AI what to leave out of the things it works
 * out. Owner: WP3 (PantryOpsTest, PantryMatchTest).
 */

/** Pantry entries sent with an AI request: the first 200 in shelf order (§6.13). */
const val PANTRY_IN_CONTEXT: Int = 200

/** "Pantry as text": one entry per line, shelf order. */
fun AppState.pantryText(): String = pantry.joinToString("\n") { it.name }

/**
 * Commits the "Pantry as text" field (§6.13, PantryOpsTest): one entry per line, split and cleaned by
 * [PantryNames.split] (commas and semicolons; " and " only inside a list; quantities and bullets dropped, "2 kg flour"
 * -> "Flour"; capitalised like an item name). A line, or a piece of one, that is the whole name of an entry already on
 * the shelves stays that one entry with its sticker and date: the Edit sheet keeps names such as "Mac and cheese" or
 * "Milk, 2 %" whole on purpose, and editing another line must never split them. Dedupes by normalizeName (the first
 * one stays), keeps the text's order, and keeps the icon and addedAt of names that were already there (new ones get
 * [now], no icon yet). Same state when nothing changed. The screen's 400 ms preview runs this too.
 */
fun AppState.setPantryText(text: String, now: Long): AppState {
    val existing = pantry.associateBy { normalizeName(it.name) }
    val seen = HashSet<String>()
    val entries = PantryNames.split(text) { normalizeName(it) in existing }.mapNotNull { name ->
        val key = normalizeName(name)
        if (!seen.add(key)) return@mapNotNull null
        existing[key]?.copy(name = name) ?: PantryEntry(name = name, icon = null, addedAt = now)
    }
    return if (entries == pantry) this else copy(pantry = entries)
}

/**
 * Adds entries at the end of the shelves (the add field, the Edit sheet toggle, a suggestion chip, §6.13). Each
 * name is split and cleaned like [setPantryText]; names already there (by normalizeName) are skipped, so the
 * same state comes back when all of them were.
 */
fun AppState.addToPantry(names: List<String>, now: Long): AppState {
    val seen = pantry.mapTo(HashSet()) { normalizeName(it.name) }
    val added = names.flatMap(PantryNames::split).mapNotNull { name ->
        if (seen.add(normalizeName(name))) PantryEntry(name = name, icon = null, addedAt = now) else null
    }
    return if (added.isEmpty()) this else copy(pantry = pantry + added)
}

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

/** How pantry text becomes entry names (§6.13). */
object PantryNames {
    private val LINE_BREAK = Regex("""\r?\n""")
    private val SEPARATORS = Regex(""";|(?<!\d),|,(?!\d)""")
    private val AND = Regex("""\s+(?:and|et|&)\s+""", RegexOption.IGNORE_CASE)
    private val BULLET = Regex("""^(?:[-*•·+–—]|\[[ xX✓]?]|[☐☑✓✔□■]|\d{1,2}[.)](?=\s))\s*""")
    private val SPACES = Regex("""\s+""")

    /**
     * "2 kg flour, salt and pepper\n- spices" -> ["Flour", "Salt", "Pepper", "Spices"]. One entry per line; a line
     * splits on commas and semicolons, and on " and ", "&" and "et" only when it is such a list ("flour, salt and
     * pepper"): a name written alone stays whole ("Half and half", "Pork & beans", "Mac and cheese"). A line or a piece
     * that [whole] accepts (an entry already on the shelves) is kept as one name. Blank pieces go.
     */
    fun split(text: String, whole: (String) -> Boolean = { false }): List<String> = text.split(LINE_BREAK).flatMap { line ->
        val oneLine = line.replace(SPACES, " ").trim()
        if (oneLine.isNotEmpty() && whole(oneLine)) return@flatMap listOf(capitalised(oneLine))
        val pieces = line.split(SEPARATORS)
        val listed = pieces.size > 1
        pieces.flatMap { piece ->
            val one = piece.replace(SPACES, " ").trim()
            when {
                one.isNotEmpty() && whole(one) -> listOf(capitalised(one))
                listed -> piece.split(AND).mapNotNull(::clean)
                else -> listOfNotNull(clean(piece))
            }
        }
    }

    private fun capitalised(name: String): String = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    /** One piece: bullets and quantities dropped, spaces collapsed, capitalised; null when nothing is left. */
    fun clean(piece: String): String? {
        var s = piece.trim().replace(Regex("\\s+"), " ")
        repeat(2) { s = s.replaceFirst(BULLET, "").trim() }
        s = s.trimEnd('.', '!', '?', ':').trim()
        val name = Quantities.split(s).name.trim().removePrefix("of ").trim()
        if (name.none { it.isLetter() }) return null
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

/** The client-side backstop for the model's pantry rule (§6.2 rule 8, PantryMatchTest). Pure. */
object PantryMatch {
    /**
     * The pantry [entry] covers [itemName]: equal after normalizeName and singular/plural folding of the last
     * word ("eggs"/"egg", "tomatoes"/"tomato", "berries"/"berry", "peaches"/"peach"), and nothing more: "flour" does
     * not cover "almond flour", and "spices" does not cover "cinnamon" here (the model judges broad entries).
     */
    fun covers(entry: String, itemName: String): Boolean {
        val a = key(entry)
        return a.isNotEmpty() && (a == key(itemName) || normalizeName(entry) == normalizeName(itemName))
    }

    /** The first pantry entry that [covers] [itemName], or null. */
    fun coveringEntry(pantry: List<String>, itemName: String): String? = pantry.firstOrNull { covers(it, itemName) }

    /**
     * Applies the two client rules to an ingest answer: a NAMED item (typed, photographed or spoken) is never
     * skipped for the pantry (an in_pantry row is switched back on); a worked-out item that a pantry entry covers is
     * skipped as IN_PANTRY with that entry even when the model kept it. Other skip reasons stay as the model gave them.
     */
    fun apply(items: List<ProposedItem>, pantry: List<String>): List<ProposedItem> = items.map { item ->
        when {
            item.named && item.skipReason == SkipReason.IN_PANTRY -> item.copy(skipReason = SkipReason.NONE, pantryEntry = null)
            item.named -> item
            item.skipReason == SkipReason.NONE -> coveringEntry(pantry, item.name)
                ?.let { item.copy(skipReason = SkipReason.IN_PANTRY, pantryEntry = it) } ?: item
            item.skipReason == SkipReason.IN_PANTRY && item.pantryEntry.isNullOrBlank() ->
                item.copy(pantryEntry = coveringEntry(pantry, item.name))
            else -> item
        }
    }

    private fun key(name: String): String {
        val words = foldText(name).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        return (words.dropLast(1) + singular(words.last())).joinToString(" ")
    }

    private fun singular(word: String): String = when {
        word.length <= 3 -> word
        word.endsWith("ies") -> word.dropLast(3) + "y"
        word.endsWith("oes") || word.endsWith("ches") || word.endsWith("shes") || word.endsWith("sses") ||
            word.endsWith("xes") || word.endsWith("zes") -> word.dropLast(2)
        word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
        word.endsWith("s") -> word.dropLast(1)
        else -> word
    }
}

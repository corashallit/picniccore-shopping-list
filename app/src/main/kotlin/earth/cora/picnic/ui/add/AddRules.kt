package earth.cora.picnic.ui.add

import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.PhotoLine
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.model.normalizeMetric
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.ui.eggs.SurpriseState
import earth.cora.picnic.ui.theme.WashiPattern

/*
 * The add flows' rules as pure functions (docs/DESIGN_STICKER.md §6.2 send rules, §4.17–§4.20, D16). Owner: WP7.
 * Unit-tested by AddRulesTest and used by AddViewModel and the sheet.
 */
object AddRules {
    /** Rule 4: at most this many items commit at once. */
    const val QUICK_ADD_MAX_ITEMS: Int = 3

    /** The thinking box's lines and the photo's lines land this far apart (§4.18, §4.19, §9). */
    const val STEP_MILLIS: Long = 250

    /** The stickers fan out this far apart after the capsule pops (§4.18). */
    const val FAN_OUT_MILLIS: Long = 80

    /** The web-search step quotes at most this many characters of her note. */
    private const val QUERY_MAX = 44

    /**
     * Rule 4: a `message` is worth showing when it is non-blank and is not an echo of the input (not equal to the sent
     * text after normalizeName).
     */
    fun messageWorthShowing(message: String?, sent: String?): Boolean {
        val m = message?.trim().orEmpty()
        if (m.isEmpty()) return false
        return normalizeName(m) != normalizeName(sent.orEmpty())
    }

    /**
     * Rule 4, quick add: the answer has ≤ 3 items, no `recipe` block, no skipped rows and no message worth showing.
     * A skipped row is one she could switch on (optional, in her pantry); a name already on her list is a duplicate
     * (rule 7: its sticker pops and the toast says so), never a reason to review. So an answer with a pantry row is never
     * a quick add (item 324).
     */
    fun isQuickAdd(result: IngestResult, sent: String?): Boolean =
        result.items.size <= QUICK_ADD_MAX_ITEMS &&
            result.recipe == null &&
            result.items.none { it.skipReason == SkipReason.OPTIONAL || it.skipReason == SkipReason.IN_PANTRY } &&
            !messageWorthShowing(result.message, sent)

    /**
     * The thinking box's steps (§4.18, D16), only from real fields of the answer, in this order: "Searched the web" only
     * if the answer used web search ([query] is her note: the answer does not carry the model's own search terms);
     * "Compared 3 recipes · makes 12" only with `recipe.recipes_compared` or `servings`; "Checked your pantry · left out
     * n" only with `in_pantry` rows (n is their count); "Checked your list, sorted into n aisles" always.
     */
    fun thinkingSteps(result: IngestResult, query: String?): List<ThinkingStep> = buildList {
        if (result.meta.webSearches > 0 && !query.isNullOrBlank()) add(ThinkingStep.SearchedWeb(searchQuery(query)))
        val recipe = result.recipe
        if (recipe != null && (recipe.recipesCompared != null || recipe.servings != null)) {
            add(ThinkingStep.ComparedRecipes(recipe.recipesCompared, recipe.servings))
        }
        val inPantry = result.items.count { it.skipReason == SkipReason.IN_PANTRY }
        if (inPantry > 0) add(ThinkingStep.CheckedPantry(inPantry))
        add(ThinkingStep.SortedAisles(result.items.mapNotNullTo(HashSet()) { it.aisleId }.size))
    }

    /** Her note as the search step quotes it: one line, at most [QUERY_MAX] characters ("…" when cut). */
    fun searchQuery(note: String): String {
        val one = note.trim().replace(WHITESPACE, " ")
        return if (one.length <= QUERY_MAX) one else one.take(QUERY_MAX - 1).trimEnd() + "…"
    }

    /**
     * A kept row's detail line (§4.17, item 296): the pack size, then the recipe amount, then the note, joined by " · ".
     * Metric after normalisation; a recipe amount after the "·" may keep cup, tbsp, tsp or pinch.
     */
    fun detailLine(item: ProposedItem): String? = detailLine(item.quantity, item.note)

    fun detailLine(quantity: String?, note: String?): String? {
        val q = quantity?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeMetric)?.let(::tidyDots)
        val n = note?.trim()?.takeIf { it.isNotEmpty() }
        return listOfNotNull(q, n).joinToString(" · ").ifEmpty { null }
    }

    /** The recipe amount a pantry row keeps after its reason ("in your pantry · ½ tsp"): the part after the "·". */
    fun recipeAmount(quantity: String?): String? {
        val q = quantity ?: return null
        val dot = q.indexOf('·')
        if (dot < 0) return null
        return normalizeMetric(q).substringAfter('·').trim().takeIf { it.isNotEmpty() }
    }

    /** "2.5 kg bag·2 cups" → "2.5 kg bag · 2 cups". */
    private fun tidyDots(text: String): String = text.split('·').joinToString(" · ") { it.trim() }

    /**
     * The add bar's tape (§4.14, egg 11, egg 16): the raindrop washi on a rainy day, else the season's washi, else the
     * default sakura stripes. With Easter eggs off ([SurpriseState.enabled] false) always the sakura stripes (§2.5).
     */
    fun addBarTape(surprises: SurpriseState): WashiPattern = when {
        !surprises.enabled -> WashiPattern.SakuraStripe
        surprises.rain -> WashiPattern.Raindrop
        else -> when (surprises.season) {
            Season.CANADA_DAY -> WashiPattern.CanadaDay
            Season.THANKSGIVING -> WashiPattern.Thanksgiving
            Season.HALLOWEEN -> WashiPattern.Halloween
            Season.FIRST_SNOW -> WashiPattern.FirstSnow
            Season.VALENTINES -> WashiPattern.Valentines
            Season.LUNAR_NEW_YEAR -> WashiPattern.LunarNewYear
            null -> WashiPattern.SakuraStripe
        }
    }

    /**
     * The transcript's keywords (§4.20): where each heard item's name (or its last word, "peas" for "Frozen peas") appears
     * in [transcript], case- and accent-insensitive; non-overlapping ranges in order.
     */
    fun keywordRanges(transcript: String, names: List<String>): List<IntRange> {
        val lower = transcript.lowercase()
        val ranges = mutableListOf<IntRange>()
        for (name in names) {
            val wanted = name.trim().lowercase()
            if (wanted.isEmpty()) continue
            val candidates = listOf(wanted, wanted.substringAfterLast(' ')).filter { it.length >= 3 }.distinct()
            for (c in candidates) {
                val at = indexOfWord(lower, c)
                if (at >= 0) {
                    val range = at until at + c.length
                    if (ranges.none { it.first <= range.last && range.first <= it.last }) ranges += range
                    break
                }
            }
        }
        return ranges.sortedBy { it.first }
    }

    private fun indexOfWord(text: String, word: String): Int {
        var from = 0
        while (true) {
            val i = text.indexOf(word, from)
            if (i < 0) return -1
            val before = i == 0 || !text[i - 1].isLetter()
            val after = i + word.length >= text.length || !text[i + word.length].isLetter()
            if (before && after) return i
            from = i + 1
        }
    }

    /**
     * The photo batch's rows in the order they pop (§4.19): the order of the lines that became them (top to bottom), then
     * any row no line points at. [rowIndexOfItem] maps an item index of the answer to a row key.
     */
    fun photoPopOrder(lines: List<PhotoLine>, rowKeys: List<Int>): List<Int> {
        val order = LinkedHashSet<Int>()
        for (line in lines) {
            if (line.crossedOut) continue
            val index = line.itemIndex ?: continue
            if (index in rowKeys.indices) order += rowKeys[index]
        }
        order += rowKeys
        return order.toList()
    }

    /** D19: voice items that are not already in the review (by normalizeName), in order, without repeats. */
    fun newHeard(existing: List<String>, heard: List<ProposedItem>): List<ProposedItem> {
        val seen = existing.mapTo(HashSet()) { normalizeName(it) }
        return heard.filter { seen.add(normalizeName(it.name)) }
    }

    private val WHITESPACE = Regex("\\s+")
}

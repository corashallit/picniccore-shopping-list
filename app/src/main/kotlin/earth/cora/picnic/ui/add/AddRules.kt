package earth.cora.picnic.ui.add

import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.PhotoLine
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.AisleStyle
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.displayName
import earth.cora.picnic.core.model.storeAisleNumber
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

    /** The web-search step quotes at most this many characters of the model's query. */
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
     * Rule 4, quick add (item 201): the answer has 1 to 3 items, no `recipe` block, no skipped rows (optional, in her
     * pantry or already on her list: §4.17 calls all three "skipped") and no message worth showing. So an answer with a
     * pantry row is never a quick add (item 324), and a name already on her list shows in the review as "already on
     * your list". An answer with nothing to add is never a quick add either: it would commit nothing and wipe her note,
     * so it opens the review ("OK" keeps the draft), with or without a message.
     */
    fun isQuickAdd(result: IngestResult, sent: String?): Boolean =
        result.items.isNotEmpty() &&
            result.items.size <= QUICK_ADD_MAX_ITEMS &&
            result.recipe == null &&
            result.items.none { it.skipReason != SkipReason.NONE } &&
            !messageWorthShowing(result.message, sent)

    /**
     * The thinking box's steps (§4.18, D16), only from real fields of the answer, in this order: "Searched the web" only
     * if the answer used web search, quoting the model's own first query (`CallMeta.searchQueries`; never her note, which
     * may not be what was searched) or, when the answer names none, unquoted; "Compared 3 recipes · makes 12" only with
     * `recipe.recipes_compared` or `servings`; "Checked your pantry · left out n" only with `in_pantry` rows (n is their
     * count); "Checked your list, sorted into n aisles" always.
     */
    fun thinkingSteps(result: IngestResult): List<ThinkingStep> = buildList {
        val queries = result.meta.searchQueries.filter { it.isNotBlank() }
        if (result.meta.webSearches > 0 || queries.isNotEmpty()) add(ThinkingStep.SearchedWeb(queries.firstOrNull()?.let(::searchQuery)))
        val recipe = result.recipe
        if (recipe != null && (recipe.recipesCompared != null || recipe.servings != null)) {
            add(ThinkingStep.ComparedRecipes(recipe.recipesCompared, recipe.servings))
        }
        val inPantry = result.items.count { it.skipReason == SkipReason.IN_PANTRY }
        if (inPantry > 0) add(ThinkingStep.CheckedPantry(inPantry))
        add(ThinkingStep.SortedAisles(result.items.mapNotNullTo(HashSet()) { it.aisleId }.size))
    }

    /** A search query as the step quotes it: one line, at most [QUERY_MAX] characters ("…" when cut). */
    fun searchQuery(query: String): String {
        val one = query.trim().replace(WHITESPACE, " ")
        return if (one.length <= QUERY_MAX) one else one.take(QUERY_MAX - 1).trimEnd() + "…"
    }

    /**
     * A kept row's detail line (§4.17, item 296): the pack size, then the recipe amount, then the note, joined by " · ".
     * Metric after normalisation; only a recipe amount after the quantity's "·" may keep cup, tbsp, tsp or pinch. The
     * note is not a recipe amount, so every unit in it becomes metric ("about 1 lb" → "about 454 g").
     */
    fun detailLine(item: ProposedItem): String? = detailLine(item.quantity, item.note)

    fun detailLine(quantity: String?, note: String?): String? {
        val q = quantity?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeMetric)?.let(::tidyDots)
        // A "·" inside the note would let normalizeMetric keep cooking units after it: read it as a comma instead.
        val n = note?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizeMetric(it.replace(" · ", ", ").replace('·', ',')) }
        return listOfNotNull(q, n).joinToString(" · ").ifEmpty { null }
    }

    /** The recipe amount a pantry row keeps after its reason ("in your pantry · ½ tsp"): the part after the "·". */
    fun recipeAmount(quantity: String?): String? {
        val q = quantity ?: return null
        val dot = q.indexOf('·')
        if (dot < 0) return null
        return normalizeMetric(q).substringAfter('·').trim().takeIf { it.isNotEmpty() }
    }

    /**
     * For display: a number stays with its unit ("2\u00A0cups") and the " · " never starts a line, so a wrapped detail
     * line reads "in your pantry ·" / "2 cups", not "in your pantry · 2" / "cups". TalkBack hears the same words.
     */
    fun keepTogether(detail: String): String =
        detail.replace(NUMBER_THEN_FRACTION, "$1\u00A0").replace(NUMBER_THEN_WORD, "$1\u00A0").replace(" · ", "\u00A0· ")

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
     * The transcript's keywords (§4.20): where each heard item's name (or its longest tail found, "cat food" for "Dry cat food") appears
     * in [transcript] as whole words, ignoring case; non-overlapping ranges in order.
     */
    fun keywordRanges(transcript: String, names: List<String>): List<IntRange> {
        val lower = transcript.lowercase()
        val ranges = mutableListOf<IntRange>()
        for (name in names) {
            val wanted = name.trim().lowercase()
            if (wanted.isEmpty()) continue
            // The whole name, then shorter tails ("dry cat food" → "cat food" → "food").
            val words = wanted.split(' ').filter { it.isNotEmpty() }
            val candidates = words.indices.map { words.drop(it).joinToString(" ") }.filter { it.length >= 3 }.distinct()
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

    /** The answer's items as review rows (§4.17): optional and pantry rows start switched off; pending icons show the capsule (D5). */
    fun rows(items: List<ProposedItem>, aisles: List<Aisle>, styles: Map<String, AisleStyle>, batch: RowBatch, firstKey: Int): List<ReviewRow> {
        val byId = aisles.associateBy { it.id }
        return items.mapIndexed { i, item ->
            val aisle = item.aisleId?.let(byId::get)
            val style = aisle?.let { styles[it.id] }
            ReviewRow(
                key = firstKey + i,
                item = item,
                keep = item.skipReason == SkipReason.NONE,
                batch = batch,
                aisleTitle = aisle?.displayName().orEmpty(),
                aisleIndex = aisle?.let { it.position - 1 } ?: -1,
                storeAisle = aisle?.let { style?.storeAisle ?: storeAisleNumber(it.name) },
                style = style,
                icon = if (IconCatalog.showsCapsule(item.icon, item.iconFit)) IconRef(IconCatalog.CAPSULE) else IconRef(item.icon),
            )
        }
    }

    /** The review of an answer (rule 5): its rows, its real thinking steps (D16, none for a photo) and the card's facts. */
    fun review(id: Long, result: IngestResult, source: ItemSource, query: String?, aisles: List<Aisle>, styles: Map<String, AisleStyle>): AddReview =
        AddReview(
            id = id,
            source = source,
            rows = rows(result.items, aisles, styles, RowBatch.MAIN, firstKey = 0),
            steps = if (source == ItemSource.PHOTO) emptyList() else thinkingSteps(result),
            recipe = result.recipe,
            webSearch = result.meta.webSearches > 0,
            sources = result.sources,
            message = result.message.takeIf { messageWorthShowing(it, query) }?.trim(),
            lines = result.lines,
        )

    private val WHITESPACE = Regex("\\s+")
    private val NUMBER_THEN_WORD = Regex("([0-9½¼¾⅓⅔⅛][0-9.,/½¼¾⅓⅔⅛]*) (?=\\p{L})")

    /** A whole number and the fraction after it ("1 ½ cups"): one amount, never split over two lines. */
    private val NUMBER_THEN_FRACTION = Regex("([0-9]+) (?=[½¼¾⅓⅔⅛])")
}

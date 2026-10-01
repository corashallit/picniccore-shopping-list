package earth.cora.picnic.core.eval

import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.foldText
import earth.cora.picnic.core.model.normalizeName
import kotlinx.serialization.Serializable
import java.text.Normalizer

/**
 * Tolerant name matching for eval expectations.
 * - A plain phrase matches when its words appear as consecutive whole words of the folded name
 *   (foldText: lowercase, accents folded, punctuation dropped), each word optionally pluralised
 *   ("egg" matches "Large eggs", "oeuf" matches "Œufs", "tomato" matches "Tomatoes").
 * - "re:<regex>" is matched (case-insensitively) against the folded name instead.
 */
object Phrase {
    private val cache = HashMap<String, Regex>()

    fun regex(phrase: String): Regex = synchronized(cache) {
        cache.getOrPut(phrase) {
            if (phrase.startsWith(RE)) {
                Regex(phrase.removePrefix(RE), RegexOption.IGNORE_CASE)
            } else {
                val words = foldText(phrase).split(' ').filter { it.isNotEmpty() }
                require(words.isNotEmpty()) { "empty phrase '$phrase'" }
                val body = words.joinToString(" ") { word ->
                    // "berry" also matches "berries"; other words take an optional "s"/"es".
                    if (word.length > 2 && word.endsWith("y")) Regex.escape(word.dropLast(1)) + "(?:y|ies)" else Regex.escape(word) + "(?:s|es)?"
                }
                Regex("(?:^| )$body(?: |$)")
            }
        }
    }

    fun matches(phrase: String, name: String): Boolean = regex(phrase).containsMatchIn(foldText(name))

    /** 2 = the whole name is the phrase (singular/plural aside), 1 = contains it, 0 = no match or excluded. */
    fun score(expect: ItemExpect, name: String): Int {
        if (expect.exclude.any { matches(it, name) }) return 0
        val key = normalizeName(name)
        var best = 0
        for (phrase in expect.match) {
            if (!matches(phrase, name)) continue
            best = maxOf(best, if (!phrase.startsWith(RE) && normalizeName(phrase) == key) 2 else 1)
        }
        return best
    }

    /** Lowercase + accents stripped, punctuation kept (for qty/detail regexes: "2%", "4 L"). */
    fun loose(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace("œ", "oe").replace("æ", "ae")

    private const val RE = "re:"
}

/** One item the pipeline produced (an ingest ProposedItem, flattened). */
@Serializable
data class Produced(
    val name: String,
    val quantity: String? = null,
    val note: String? = null,
    val aisleId: String? = null,
    val icon: String,
    val fit: IconFit,
)

@Serializable
data class Check(val what: String, val ok: Boolean, val detail: String = "")

/** Scores of one case run. Counters are summed across cases for the micro-averaged summary. */
@Serializable
data class CaseScore(
    val requiredTotal: Int = 0,
    val requiredMatched: Int = 0,
    val produced: Int = 0,
    /** Produced items matched by any expectation (required or optional). */
    val producedMatched: Int = 0,
    val aisleTotal: Int = 0,
    val aisleOk: Int = 0,
    val iconTotal: Int = 0,
    val iconOk: Int = 0,
    val exact: Int = 0,
    val approximate: Int = 0,
    val basketTotal: Int = 0,
    val basketOk: Int = 0,
    val checks: List<Check> = emptyList(),
    /** Human-readable findings for the report. */
    val misses: List<String> = emptyList(),
    val extras: List<String> = emptyList(),
    val wrong: List<String> = emptyList(),
) {
    val failedChecks: List<Check> get() = checks.filter { !it.ok }

    /** All required items, nothing unexpected, every scored aisle/icon right and every check passed. */
    val clean: Boolean
        get() = requiredMatched == requiredTotal && producedMatched == produced && aisleOk == aisleTotal &&
            iconOk == iconTotal && basketOk == basketTotal && failedChecks.isEmpty()
}

object EvalScoring {
    private val IMPERIAL = Regex(
        "\\b(lbs?|pounds?|oz|ounces?|cups?|tbsp|tsp|teaspoons?|tablespoons?|gallons?|quarts?|qt|pints?|fl\\.? ?oz)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Rough French detector for the message: enough for "did it answer in French". */
    private val FRENCH = Regex("\\b(j'ai|ajout|ajouté|et|les|des|du|la|le|à|pour|liste|épicerie)\\b", RegexOption.IGNORE_CASE)

    const val MESSAGE_MAX_CHARS = 140

    /**
     * Scores an ingest answer: greedy best-first matching of produced items to expectations (whole-name
     * matches before partial ones, required before optional), then per-item aisle/icon/qty/detail checks
     * and case-level checks (forbidden names, search, message, metric units).
     */
    fun scoreIngest(
        case: EvalCase,
        store: EvalStore,
        items: List<Produced>,
        message: String,
        /** web_search_call items in the answer (searches and opened pages). */
        webSearches: Int,
        transcript: String? = null,
    ): CaseScore {
        val expects = case.expect
        val pairs = buildList {
            expects.forEachIndexed { e, expect ->
                items.forEachIndexed { i, item ->
                    val s = Phrase.score(expect, item.name)
                    if (s > 0) add(Triple(e, i, s))
                }
            }
        }.sortedWith(
            compareByDescending<Triple<Int, Int, Int>> { it.third }
                .thenBy { if (expects[it.first].optional) 1 else 0 }
                .thenBy { it.first }
                .thenBy { it.second },
        )
        val itemFor = HashMap<Int, MutableList<Int>>()
        val expectFor = HashMap<Int, Int>()
        for ((e, i, _) in pairs) {
            if (i in expectFor) continue
            val taken = itemFor[e]
            if (taken != null && !expects[e].many) continue
            itemFor.getOrPut(e) { mutableListOf() } += i
            expectFor[i] = e
        }

        val checks = mutableListOf<Check>()
        val misses = mutableListOf<String>()
        val wrong = mutableListOf<String>()
        var aisleTotal = 0
        var aisleOk = 0
        var iconTotal = 0
        var iconOk = 0
        expects.forEachIndexed { e, expect ->
            val matched = itemFor[e].orEmpty()
            if (matched.isEmpty()) {
                if (!expect.optional) misses += expect.match.first()
                return@forEachIndexed
            }
            for (i in matched) {
                val item = items[i]
                if (expect.aisle.isNotEmpty()) {
                    aisleTotal++
                    val ok = expect.aisle.any { store.resolve(it) == item.aisleId }
                    if (ok) aisleOk++ else wrong += "${item.name}: aisle ${store.aliasOf(item.aisleId)} (want ${expect.aisle.joinToString("|")})"
                }
                if (expect.scoresIcon) {
                    iconTotal++
                    if (iconCorrect(expect, item.icon, item.fit)) {
                        iconOk++
                    } else {
                        wrong += "${item.name}: icon ${item.icon} ${item.fit.name.lowercase()} (want ${iconWant(expect)})"
                    }
                }
                expect.qty?.let { re ->
                    val pattern = Regex(re.removePrefix("re:"), RegexOption.IGNORE_CASE)
                    val ok = item.quantity?.let { pattern.containsMatchIn(it.lowercase()) } ?: false
                    checks += Check("qty ${item.name}", ok, "got ${item.quantity ?: "null"}, want /${re.removePrefix("re:")}/")
                }
                expect.detail?.let { re ->
                    val text = Phrase.loose(listOfNotNull(item.name, item.note, item.quantity).joinToString(" | "))
                    val ok = Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(text)
                    checks += Check("detail ${item.name}", ok, "got \"$text\", want /$re/")
                }
            }
        }

        val extras = items.indices.filter { it !in expectFor }.map { i ->
            val item = items[i]
            val forbidden = case.forbidden.firstOrNull { Phrase.matches(it, item.name) }
            if (forbidden != null) "${item.name} (FORBIDDEN: $forbidden)" else item.name
        }
        // Forbidden names are also checked on matched items (an optional "oil" must not hide "butter").
        val forbiddenHits = items.mapNotNull { item -> case.forbidden.firstOrNull { Phrase.matches(it, item.name) }?.let { "${item.name} ~ $it" } }
        checks += Check("no forbidden items", forbiddenHits.isEmpty(), forbiddenHits.joinToString())

        when (case.search) {
            SearchExpectation.REQUIRED -> checks += Check("web search used", webSearches > 0, "$webSearches search(es)")
            SearchExpectation.AVOID -> checks += Check("no web search", webSearches == 0, "$webSearches search(es)")
            SearchExpectation.ANY -> Unit
        }

        val imperial = items.filter { it.quantity?.let(IMPERIAL::containsMatchIn) == true }
        checks += Check("metric quantities", imperial.isEmpty(), imperial.joinToString { "${it.name}: ${it.quantity}" })

        checks += Check("message present, <= $MESSAGE_MAX_CHARS chars", message.isNotBlank() && message.length <= MESSAGE_MAX_CHARS, "${message.length} chars")
        case.message.mustNot.forEach { phrase ->
            val hit = if (phrase.startsWith("re:")) {
                Regex(phrase.removePrefix("re:"), RegexOption.IGNORE_CASE).containsMatchIn(message)
            } else {
                Phrase.matches(phrase, message)
            }
            checks += Check("message avoids '$phrase'", !hit, message)
        }
        when (case.message.lang) {
            "fr" -> checks += Check("message in French", FRENCH.findAll(message).count() >= 2, message)
            "en" -> checks += Check("message in English", FRENCH.findAll(message).count() < 2, message)
        }

        if (transcript != null) {
            case.transcript.forEach { phrase -> checks += Check("transcript has '$phrase'", Phrase.matches(phrase, transcript), "") }
        }

        val required = expects.count { !it.optional }
        return CaseScore(
            requiredTotal = required,
            requiredMatched = expects.indices.count { !expects[it].optional && !itemFor[it].isNullOrEmpty() },
            produced = items.size,
            producedMatched = expectFor.size,
            aisleTotal = aisleTotal,
            aisleOk = aisleOk,
            iconTotal = iconTotal,
            iconOk = iconOk,
            exact = items.count { it.fit == IconFit.EXACT },
            approximate = items.count { it.fit == IconFit.APPROXIMATE },
            checks = checks,
            misses = misses,
            extras = extras,
            wrong = wrong,
        )
    }

    /**
     * An icon decision is right when it shows the item (EXACT with a depicting key) or, for items no key
     * depicts, when it asks for a drawing (APPROXIMATE). APPROXIMATE on an item the catalog depicts is wrong
     * (it pays for an unneeded drawing); EXACT with a key that doesn't depict it is wrong (no drawing, wrong picture).
     */
    fun iconCorrect(expect: ItemExpect, icon: String, fit: IconFit): Boolean = when (fit) {
        IconFit.EXACT -> icon in expect.icons
        IconFit.APPROXIMATE -> expect.approx
        IconFit.PENDING -> false
    }

    private fun iconWant(expect: ItemExpect): String =
        (expect.icons + if (expect.approx) listOf("(approximate)") else emptyList()).joinToString("|")

    /** AISLE_EDIT: final aisle ids per item name, and the baskets per aisle id after AisleSync. */
    fun scoreAisleEdit(
        case: EvalCase,
        oldStore: EvalStore,
        newStore: EvalStore,
        finalAisles: Map<String, String?>,
        baskets: Map<String, String>,
    ): CaseScore {
        val newIds = newStore.aisles.mapTo(HashSet()) { it.id }
        var total = 0
        var ok = 0
        val wrong = mutableListOf<String>()
        for (item in case.items) {
            // Only items whose aisle vanished are re-placed; the rest keep their (still valid) aisle.
            if (oldStore.resolve(item.from) in newIds) continue
            total++
            val got = finalAisles[item.name]
            if (item.aisle.any { newStore.resolve(it) == got }) ok++ else wrong += "${item.name}: ${newStore.aliasOf(got)} (want ${item.aisle.joinToString("|")})"
        }
        var basketTotal = 0
        var basketOk = 0
        for ((alias, accepted) in case.baskets) {
            basketTotal++
            val got = baskets[newStore.resolve(alias)]
            if (got in accepted) basketOk++ else wrong += "basket $alias: $got (want ${accepted.joinToString("|")})"
        }
        return CaseScore(aisleTotal = total, aisleOk = ok, basketTotal = basketTotal, basketOk = basketOk, wrong = wrong)
    }

    /**
     * ICONS: the cascade's final (icon, fit) per typed name. Only names present in [resolved] are scored
     * (a live run resolves every name; a rescored older report may predate a name).
     */
    fun scoreIcons(case: EvalCase, resolved: Map<String, Pair<String, IconFit>>): CaseScore {
        var ok = 0
        var total = 0
        val wrong = mutableListOf<String>()
        for (expect in case.names) {
            val name = expect.name ?: continue
            val (icon, fit) = resolved[name] ?: continue
            total++
            if (iconCorrect(expect, icon, fit)) ok++ else wrong += "$name: $icon ${fit.name.lowercase()} (want ${iconWant(expect)})"
        }
        val fits = resolved.values.map { it.second }
        return CaseScore(
            iconTotal = total,
            iconOk = ok,
            exact = fits.count { it == IconFit.EXACT },
            approximate = fits.count { it == IconFit.APPROXIMATE },
            wrong = wrong,
        )
    }
}

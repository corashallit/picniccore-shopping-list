package earth.cora.picnic.core.catalog

import earth.cora.picnic.core.CoreJson
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.Quantities
import earth.cora.picnic.core.model.normalizeName
import kotlinx.serialization.Serializable
import java.text.Normalizer

/*
 * The icon + basket catalog: core/src/main/resources/icon_catalog.json (source:
 * research/icon_catalog.json, validated by research/validate_icon_catalog.py).
 * Icon keys are the enum the LLM chooses from; the bundled art lives in the app at
 * assets/icons/<key>.webp (192 px, transparent). Keys are persisted: never rename one.
 */

@Serializable
data class IconCategory(val key: String, val label: String, val fallbackIcon: String)

@Serializable
data class IconEntry(
    val key: String,
    val label: String,
    val category: String,
    val aliases: List<String>,
    /** One sentence for image generation (library sheets and live icons). */
    val drawHint: String,
    /**
     * Easter-egg tags (§12.2, CatalogTagsTest): "roll" / "hop" (egg 3, rolling produce and hopping berries),
     * "picnic" (egg 17, the ant parade), "birthday" (egg 9), "frozen" (the frozen goods).
     */
    val tags: List<String> = emptyList(),
    /** How the sticker hops into its basket (§8.3); StickerManifest repeats it per drawable. */
    val personality: Personality = Personality.NONE,
)

@Serializable
data class BasketEntry(val key: String, val label: String, val drawHint: String, val keywords: List<String>)

@Serializable
data class CatalogFile(
    val version: Int,
    val categories: List<IconCategory>,
    val icons: List<IconEntry>,
    val baskets: List<BasketEntry>,
)

class IconCatalog(val file: CatalogFile) {
    val version: Int get() = file.version
    val categories: List<IconCategory> get() = file.categories
    val icons: List<IconEntry> get() = file.icons

    /** All icon keys in catalog order: the strict-schema enum. */
    val keys: List<String> = file.icons.map { it.key }

    private val byKey: Map<String, IconEntry> = file.icons.associateBy { it.key }
    private val categoryByKey: Map<String, IconCategory> = file.categories.associateBy { it.key }

    operator fun get(key: String): IconEntry? = byKey[key]

    operator fun contains(key: String): Boolean = key in byKey

    fun categoryOf(iconKey: String): IconCategory? = byKey[iconKey]?.let { categoryByKey[it.category] }

    /** Keys that are category fallbacks ("fruit_generic", ...). Never a "match" for the IconDecider. */
    val genericKeys: Set<String> = file.categories.map { it.fallbackIcon }.toSet()

    fun isGeneric(key: String): Boolean = key in genericKeys

    /** Strict-schema enum for the IconDecider: every key except the category fallbacks (plus "none" on the wire). */
    val decidableKeys: List<String> = keys.filterNot { it in genericKeys }

    /**
     * The category fallback icon for [iconKey] ("lemon" -> "fruit_generic"; "fruit_generic" -> itself).
     * [DEFAULT_ICON] and unknown keys -> [DEFAULT_ICON].
     */
    fun fallbackFor(iconKey: String): String =
        if (iconKey == DEFAULT_ICON) DEFAULT_ICON else categoryOf(iconKey)?.fallbackIcon ?: DEFAULT_ICON

    /** The fallback icon of a category key ("condiments" -> "condiments_generic"); unknown/null -> [DEFAULT_ICON]. */
    fun fallbackForCategory(categoryKey: String?): String =
        file.categories.firstOrNull { it.key == categoryKey }?.fallbackIcon ?: DEFAULT_ICON

    /** [key] if it is a catalog key, otherwise [DEFAULT_ICON]. */
    fun sanitize(key: String?): String = if (key != null && key in byKey) key else DEFAULT_ICON

    /**
     * Icon cascade step 1 (catalog part; learned overrides are applied by AppState.localIcon).
     * Offline lookup for a free-text item name. CONTRACT (precision over recall: when unsure return
     * null and let the IconDecider handle it):
     * 1. Exact: normalizeName(name) equals normalizeName of an icon's key (underscores as spaces),
     *    label or any alias. "Lemons" -> lemon, "Lait" -> milk_carton, "Oat milk" -> plant_milk.
     * 2. Head noun: the longest key/label/alias (normalized, >= 3 chars) that is a whole-word SUFFIX of
     *    the normalized name. "Meyer lemons" -> lemon, "organic baby spinach" -> spinach,
     *    "Lindt dark chocolate" -> chocolate_bar, "vanilla oat milk" -> plant_milk. Ties: catalog order.
     * 3. Otherwise null. A leading quantity ("2 lemons", "500 g flour") is ignored before matching
     *    (the full text is tried first, because some aliases carry a size: "4L milk" -> milk_bag).
     * Precision refinements: a term shared by two icons ("rose": wine or bouquet) never matches; generic
     * (*_generic) keys are never head-noun matches; a single-word head noun is rejected when the word
     * before it is a catalog term for a different icon ("garlic bread", "coconut milk" -> null).
     */
    fun match(name: String): IconEntry? {
        matchExact(name)?.let { return it }
        val stripped = normalizeName(Quantities.stripLeading(name))
        if (stripped.isEmpty()) return null
        return headNounMatch(stripped)
    }

    /**
     * [match] without the head-noun guess: the name (or the name without a leading quantity) IS an
     * icon's key, label or alias ("Cilantro" -> parsley, "Birthday candles" -> candle, but not
     * "Nutritional yeast" -> yeast). For places where a model has already judged the fit (ingest
     * post-processing): a curated term may overrule the model's "approximate", a head noun may not.
     * Leading/trailing freshness and organic descriptors are ignored ("Coriandre fraîche" -> parsley).
     */
    fun matchExact(name: String): IconEntry? {
        val full = normalizeName(name)
        if (full.isEmpty()) return null
        // The full text first: some aliases include a quantity ("4l milk" -> milk_bag).
        exactMatch(full)?.let { return it }
        val stripped = normalizeName(Quantities.stripLeading(name))
        if (stripped.isEmpty()) return null
        if (stripped != full) exactMatch(stripped)?.let { return it }
        // Words that never change what the product looks like ("Coriandre fraîche", "organic kale").
        val words = stripped.split(' ')
        val core = words.dropWhile { it in DESCRIPTORS }.dropLastWhile { it in DESCRIPTORS }
        return if (core.isNotEmpty() && core.size < words.size) exactMatch(normalizeName(core.joinToString(" "))) else null
    }

    /**
     * normalizeName(term) -> icons using that term, in catalog order. Terms are the key (underscores as
     * spaces), the label and every alias.
     */
    private val termIndex: Map<String, List<IconEntry>> by lazy {
        val index = LinkedHashMap<String, MutableList<IconEntry>>()
        for (icon in file.icons) {
            val terms = listOf(icon.key.replace('_', ' '), icon.label) + icon.aliases
            for (term in terms) {
                val key = normalizeName(term)
                if (key.isEmpty()) continue
                val owners = index.getOrPut(key) { mutableListOf() }
                if (owners.none { it.key == icon.key }) owners += icon
            }
        }
        index
    }

    /** Head-noun candidates: terms of >= 3 chars on non-generic icons, longest first (catalog order on ties). */
    private val suffixTerms: List<Pair<String, IconEntry>> by lazy {
        termIndex.entries
            .filter { it.key.length >= 3 }
            .mapNotNull { (term, owners) ->
                val specific = owners.filterNot { isGeneric(it.key) }
                // Ambiguous terms ("rose": wine or bouquet) never decide on their own.
                if (specific.size == 1 && owners.size == 1) term to specific.single() else null
            }
            .sortedByDescending { it.first.length } // stable: catalog order kept on ties
    }

    /** Exact match: unambiguous term only (precision over recall). */
    private fun exactMatch(normalized: String): IconEntry? = termIndex[normalized]?.singleOrNull()

    /**
     * The longest term that is a whole-word suffix of [normalized] ("organic baby spinach" -> spinach).
     * A single-word head noun is rejected when the word before it is itself a catalog term for a
     * different icon: "garlic bread", "coconut milk" or "apple juice" name a different product than
     * "bread", "milk" or "juice", so the IconDecider decides those instead.
     */
    private fun headNounMatch(normalized: String): IconEntry? {
        for ((term, icon) in suffixTerms) {
            if (!normalized.endsWith(" $term")) continue
            if (' ' !in term) {
                val before = normalized.removeSuffix(" $term").substringAfterLast(' ')
                val modifierOwners = termIndex[before]
                if (modifierOwners != null && modifierOwners.none { it.key == icon.key }) return null
            }
            return icon
        }
        return null
    }

    /**
     * What a row shows for [item]. CONTRACT (D5, DisplayRefTest):
     * - bespoke icon attached -> IconRef(item.icon, customIconId) (a redraw in progress keeps showing it);
     * - [busy] (IconRepository is deciding or drawing this name), iconFit == PENDING, or the stored [DEFAULT_ICON] that
     *   was not chosen EXACT (nothing matched: [showsCapsule]) -> the gashapon capsule sentinel IconRef([CAPSULE]),
     *   tinted by its aisle (gold for a jackpot) by ItemIcon, with the "drawing..." sparkle when busy. Display only:
     *   nothing persisted changes;
     * - otherwise IconRef(item.icon): an APPROXIMATE item that won't be drawn keeps its closest icon, and an item matched
     *   EXACT to `paper_bag` ("grocery bags") shows the kraft shopping bag.
     */
    fun displayRef(item: GroceryItem, busy: Boolean): IconRef = when {
        item.customIconId != null -> IconRef(item.icon, item.customIconId)
        busy || showsCapsule(item.icon, item.iconFit) -> IconRef(CAPSULE)
        else -> IconRef(item.icon)
    }

    /** Keys with egg tag [tag] ("roll", "hop", "picnic", "birthday", "frozen"), in catalog order. */
    fun keysTagged(tag: String): List<String> = file.icons.filter { tag in it.tags }.map { it.key }

    /** [key]'s egg tags; empty for unknown keys. */
    fun tagsOf(key: String): List<String> = byKey[key]?.tags.orEmpty()

    /** [key]'s hop personality (§8.3); NONE for unknown keys and the capsule. */
    fun personalityOf(key: String): Personality = byKey[key]?.personality ?: Personality.NONE

    /**
     * The album key an item is collected under when it is checked (§7.2 "collected = really bought"): its
     * live-drawn sticker ("c:<customIconId>") or its catalog key when the fit is EXACT and the key is not a
     * category generic (generics are not collectible, §6.10). Null for PENDING and APPROXIMATE items: they are
     * collected later, when they resolve or get their live sticker.
     */
    fun collectKey(item: GroceryItem): String? = when {
        item.customIconId != null -> COLLECTED_CUSTOM_PREFIX + item.customIconId
        item.iconFit == IconFit.EXACT && item.icon in byKey && !isGeneric(item.icon) -> item.icon
        else -> null
    }

    /** What the album can collect from the catalog: every key but the category generics (§6.10). */
    val collectibleKeys: List<String> = keys.filterNot { it in genericKeys }

    val baskets: BasketCatalog by lazy { BasketCatalog(file.baskets) }

    companion object {
        /**
         * The catalog key stored for unknown and PENDING items (a paper grocery bag; always a valid key). Rows show
         * the [CAPSULE] for PENDING items instead (D5).
         */
        const val DEFAULT_ICON: String = "paper_bag"

        /** D5: the gashapon capsule shown for PENDING and busy items. A display-only sentinel, never a catalog key. */
        const val CAPSULE: String = "_capsule"

        /**
         * Whether an item's stored [icon] and [fit] still show the [CAPSULE] (D5): PENDING, or the [DEFAULT_ICON] kept
         * because nothing matched (not EXACT). `paper_bag` chosen EXACT is the real "Shopping bag" sticker.
         */
        fun showsCapsule(icon: String, fit: IconFit): Boolean =
            fit == IconFit.PENDING || (icon == DEFAULT_ICON && fit != IconFit.EXACT)

        /** Album collected-key prefix for live-drawn stickers: "c:<customIconId>" (§7.2). */
        const val COLLECTED_CUSTOM_PREFIX: String = "c:"

        const val RESOURCE: String = "/icon_catalog.json"

        /** Folded words that describe freshness or farming, never a different product (see [matchExact]). */
        private val DESCRIPTORS: Set<String> = setOf("fresh", "frais", "fraiche", "fraiches", "organic", "bio", "biologique", "biologiques")

        fun parse(json: String): IconCatalog = IconCatalog(CoreJson.json.decodeFromString(CatalogFile.serializer(), json))

        /** Loads the bundled catalog from core's classpath resources (works on the JVM and on Android). */
        fun load(): IconCatalog {
            val stream = IconCatalog::class.java.getResourceAsStream(RESOURCE)
                ?: error("icon_catalog.json missing from core resources")
            return stream.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        }

        /** Process-wide lazily loaded catalog (immutable), for previews and places without DI. */
        val default: IconCatalog by lazy { load() }
    }
}

class BasketCatalog(val entries: List<BasketEntry>) {
    private val byKind: Map<BasketKind, BasketEntry> =
        entries.mapNotNull { e -> BasketKind.fromKey(e.key)?.let { it to e } }.toMap()

    fun entry(kind: BasketKind): BasketEntry? = byKind[kind]

    /**
     * Offline keyword heuristic (reference: research/validate_icon_catalog.py basket_for, 43/43 samples).
     * CONTRACT: fold the aisle name (lowercase, accents removed), pad with spaces; for every keyword of
     * every basket find its earliest whole-word position; the earliest position wins, ties go to the
     * longest keyword. No match -> BasketKind.DEFAULT.
     */
    fun guess(aisleName: String): BasketKind {
        val text = " " + heuristicText(aisleName) + " "
        var best: BasketKind? = null
        var bestPos = Int.MAX_VALUE
        var bestLen = -1
        for ((keyword, kind) in keywordIndex) {
            val pos = text.indexOf(keyword)
            if (pos < 0) continue
            if (pos < bestPos || (pos == bestPos && keyword.length > bestLen)) {
                best = kind
                bestPos = pos
                bestLen = keyword.length
            }
        }
        return best ?: BasketKind.DEFAULT
    }

    /** " keyword " (padded, heuristic-normalized) -> basket kind, in catalog order; first owner wins. */
    private val keywordIndex: List<Pair<String, BasketKind>> by lazy {
        val seen = HashSet<String>()
        entries.flatMap { entry ->
            val kind = BasketKind.fromKey(entry.key) ?: return@flatMap emptyList()
            entry.keywords.mapNotNull { kw ->
                val padded = " " + heuristicText(kw) + " "
                if (padded.isBlank() || !seen.add(padded)) null else padded to kind
            }
        }
    }

    /**
     * Port of `basket_for` in research/validate_icon_catalog.py: lowercase, fold accents ("œ" -> "oe"),
     * then every run of characters other than [a-z0-9'&+-] becomes one space. Unlike foldText it keeps
     * '&', '+', '-' and apostrophes, so keywords like "fruits & legumes" behave exactly as validated.
     */
    private fun heuristicText(text: String): String {
        val lowered = text.lowercase().replace("œ", "oe").replace("æ", "ae").replace('’', '\'')
        val folded = Normalizer.normalize(lowered, Normalizer.Form.NFKD).replace(COMBINING_MARKS, "")
        return folded.replace(HEURISTIC_SEPARATORS, " ").trim()
    }

    private companion object {
        val COMBINING_MARKS = Regex("\\p{Mn}+")
        val HEURISTIC_SEPARATORS = Regex("[^a-z0-9'&+-]+")
    }

    fun guess(aisle: Aisle): BasketKind = guess(aisle.name)
}

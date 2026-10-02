package earth.cora.picnic.core.model

/** Parses the user's free-text aisle list (one aisle per line, in walking order). */
object Aisles {
    /** Default for a fresh install: a typical Ontario supermarket walk (also the benchmark store). */
    val DEFAULT_TEXT: String = """
        Produce
        Bakery
        Deli & cheese
        Meat & seafood
        Aisle 1 - Breakfast, coffee & tea
        Aisle 2 - Baking & spices
        Aisle 3 - Pasta, rice & international
        Aisle 4 - Canned goods & soups
        Aisle 5 - Snacks & drinks
        Aisle 6 - Household & cleaning
        Aisle 7 - Health, beauty & baby
        Dairy & eggs
        Frozen
        Bulk & natural foods
    """.trimIndent()

    private val BULLET = Regex("^(?:[-*•·]|\\d+[.)])\\s+")

    /**
     * CONTRACT (ids are persisted):
     * - Each non-blank line is one aisle; lines starting with '#' are comments.
     * - A leading list bullet ("- ", "* ", "• ", "1. ", "2) ") is removed; then the line is trimmed.
     *   (Note "Aisle 2 - Baking" keeps its text: only a bullet at the very start is removed.)
     * - id = slugify(name); "" -> "aisle-<position>"; the reserved "unsorted" -> "unsorted-aisle".
     * - Duplicate ids get "-2", "-3", ... in order of appearance.
     * - position is 1-based over the kept lines.
     */
    fun parse(text: String): List<Aisle> {
        val used = mutableSetOf<String>()
        val out = mutableListOf<Aisle>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val name = line.replaceFirst(BULLET, "").trim()
            if (name.isEmpty()) continue
            val position = out.size + 1
            var base = slugify(name).ifEmpty { "aisle-$position" }
            if (base == UNSORTED_WIRE_ID) base = "unsorted-aisle"
            var id = base
            var n = 2
            while (id in used) id = "$base-${n++}"
            used += id
            out += Aisle(id = id, name = name, position = position)
        }
        return out
    }
}

fun StoreProfile.aisles(): List<Aisle> = Aisles.parse(aislesText)

/**
 * The store's own aisle number at the start of an aisle name (D9, the "A2" chip):
 * "Aisle 2 - Baking & spices" -> 2, "12 Frozen" -> 12, "Allée 4 : conserves" -> 4, "Aisle 7" -> 7.
 * Names without one ("Produce", "Dairy & eggs", "1% milk fridge") -> null.
 * Moved here from the app's ui/common/AisleNumbers.kt (same regex); the app copy delegates here until
 * WP8 deletes it. An [AisleStyle.storeAisle] set in the aisle editor wins over this.
 */
fun storeAisleNumber(name: String): Int? =
    STORE_NUMBER.find(name.trim())?.groupValues?.get(1)?.toIntOrNull()

/**
 * The name a tab shows (D9, §4.10): the aisle name without its store prefix, so a tab never reads
 * "5 Aisle 1 - Breakfast". "Aisle 3 - Pasta, rice & international" -> "Pasta, rice & international",
 * "12 Frozen" -> "Frozen", "#3 Snacks" -> "Snacks". A name that is only a prefix ("Aisle 7") and names
 * without one ("Produce") are returned trimmed, unchanged. TalkBack still reads the full [Aisle.name].
 */
fun Aisle.displayName(): String {
    val trimmed = name.trim()
    val match = STORE_NUMBER.find(trimmed) ?: return trimmed
    val rest = trimmed.substring(match.range.last + 1).trimStart { it.isWhitespace() || it in PREFIX_SEPARATORS }
    return rest.ifEmpty { trimmed }
}

private val STORE_NUMBER = Regex(
    """^(?:(?:aisle|isle|allée|allee|rayon|row)\s*)?#?\s*(\d{1,3})(?=\s*[-–—:.)]|\s|$)""",
    RegexOption.IGNORE_CASE,
)

private const val PREFIX_SEPARATORS = "-–—:.)"

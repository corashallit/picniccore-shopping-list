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

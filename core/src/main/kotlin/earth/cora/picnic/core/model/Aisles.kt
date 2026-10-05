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
     * An "Also here" note at the end of an aisle line: "(also: toothpaste, floss)", "also" in any case, "also here:"
     * accepted too, the closing ")" optional (easy to forget on a phone): everything from the "(" to the end of the line.
     */
    private val NOTE = Regex("""\s*\(\s*also(?:\s+here)?\s*:\s*(.*?)\s*\)?\s*$""", RegexOption.IGNORE_CASE)

    /** The longest "Also here" note kept (§6.7): a few things, not a paragraph. A longer note is cut after a whole word. */
    const val NOTE_MAX: Int = 80

    /**
     * CONTRACT (ids are persisted):
     * - Each non-blank line is one aisle; lines starting with '#' are comments.
     * - A leading list bullet ("- ", "* ", "• ", "1. ", "2) ") is removed; then the line is trimmed.
     *   (Note "Aisle 2 - Baking" keeps its text: only a bullet at the very start is removed.)
     * - "Also here" note (Cora, 5 October; §6.7): a trailing "(also: toothpaste, floss)" is the aisle's [Aisle.note] and
     *   never part of its name or id, so writing, changing or removing a note never changes an id or moves an item.
     *   "also" in any case, "(also here: …)" too, spaces optional, the closing ")" optional (the note then runs to the
     *   end of the line). The note is put on one line and kept to [NOTE_MAX] characters, cut after a whole word;
     *   "(also:)" with nothing in it is no note. A line with no name before its "(also" keeps its whole text as the name.
     *   A line without "(also …:" parses exactly as it did before notes existed.
     * - id = slugify(name); "" -> "aisle-<position>"; the reserved "unsorted" -> "unsorted-aisle".
     * - Duplicate ids get "-2", "-3", ... in order of appearance.
     * - position is 1-based over the kept lines.
     */
    fun parse(text: String): List<Aisle> = parseLines(text).map { it.aisle }

    /**
     * One aisle line of the text: [lineIndex] is its index in text.lines() (comments and blank lines count); its name
     * runs from [nameStart] to [nameEnd] (exclusive) in that raw line, after any indent and bullet, before any note.
     */
    internal data class AisleLine(val lineIndex: Int, val aisle: Aisle, val nameStart: Int, val nameEnd: Int)

    /** [parse], keeping where each aisle sits in the text (renameAisle, moveAisle, setAisleNote and commitAislesText rewrite lines). */
    internal fun parseLines(text: String): List<AisleLine> {
        val used = mutableSetOf<String>()
        val out = mutableListOf<AisleLine>()
        for ((index, raw) in text.lines().withIndex()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val body = line.replaceFirst(BULLET, "").trim()
            if (body.isEmpty()) continue
            val (name, note) = splitNote(body)
            val position = out.size + 1
            var base = slugify(name).ifEmpty { "aisle-$position" }
            if (base == UNSORTED_WIRE_ID) base = "unsorted-aisle"
            var id = base
            var n = 2
            while (id in used) id = "$base-${n++}"
            used += id
            // The name starts after the indent and the bullet (whose pattern takes the spaces after it too).
            val start = (raw.length - raw.trimStart().length) + (line.length - line.replaceFirst(BULLET, "").length)
            out += AisleLine(index, Aisle(id = id, name = name, position = position, note = note), start, start + name.length)
        }
        return out
    }

    /**
     * A trimmed aisle line (no bullet) as its name and its "Also here" note (null = none). A note needs a name before
     * it: "(also: x)" on its own stays the name it always was.
     */
    internal fun splitNote(body: String): Pair<String, String?> {
        val match = NOTE.find(body) ?: return body to null
        val name = body.substring(0, match.range.first).trimEnd()
        if (name.isEmpty()) return body to null
        return name to cleanNote(match.groupValues[1]).ifEmpty { null }
    }

    /** One aisle name as a line of aisle text: trimmed, on one line, without a leading bullet. Blank -> "". */
    internal fun cleanName(name: String): String =
        name.replace(LINE_BREAKS, " ").trim().replaceFirst(BULLET, "").trim().removePrefix("#").trim()

    /**
     * An "Also here" note as the parser keeps it: on one line with single spaces, no stray brackets at its ends, at most
     * [NOTE_MAX] characters (a longer one is cut after its last whole word, a dangling comma dropped). Blank -> "".
     */
    fun cleanNote(note: String): String {
        var one = note.replace(WHITESPACE, " ").trim()
        // A bracket left over at an end ("toothpaste)") goes; "floss (mint)" keeps its pair.
        while (one.endsWith(")") && one.count { it == ')' } > one.count { it == '(' }) one = one.dropLast(1).trimEnd()
        while (one.startsWith("(") && one.count { it == '(' } > one.count { it == ')' }) one = one.drop(1).trimStart()
        if (one.length <= NOTE_MAX) return one
        val cut = one.take(NOTE_MAX + 1)
        val atWord = cut.lastIndexOf(' ').takeIf { it >= NOTE_MAX / 2 } ?: NOTE_MAX
        return cut.take(atWord).trimEnd(' ', ',', ';', '·', '-', '(')
    }

    /** The raw aisle [line] with its note set to [note]: " (also: …)" after the name ([nameEnd]), or none when blank. */
    internal fun withNote(line: String, nameEnd: Int, note: String?): String {
        val clean = note?.let(::cleanNote).orEmpty()
        val head = line.substring(0, nameEnd).trimEnd()
        return if (clean.isEmpty()) head else "$head (also: $clean)"
    }

    private val LINE_BREAKS = Regex("[\\r\\n]+")
    private val WHITESPACE = Regex("\\s+")
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

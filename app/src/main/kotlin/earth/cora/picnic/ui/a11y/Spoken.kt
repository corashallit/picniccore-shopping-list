package earth.cora.picnic.ui.a11y

/*
 * What TalkBack says for text that reads badly aloud (docs/DESIGN_STICKER.md §15 "Units in words"). Pure Kotlin,
 * so the screens, the announcements and SpokenUnitsTest share one implementation. Owner: WP10.
 *
 * Compose can only hand TalkBack a verbatim TtsSpan (VerbatimTtsAnnotation), not a measure span, so units are
 * spelled out in words instead: that reads correctly on every speech engine.
 */

/**
 * A detail line as it should be spoken: every unit after a number becomes a word, and the "·" between the pack
 * size and a recipe amount becomes a comma pause.
 *
 * - "1 L" → "1 litre", "540 mL" → "540 millilitres", "2.5 kg" → "2.5 kilograms", "454 g" → "454 grams".
 * - A unit used as an adjective stays singular, as it is said: "4 L bag" → "4 litre bag",
 *   "1 L basket" → "1 litre basket", "540 mL tin" → "540 millilitre tin".
 * - The cooking measures a recipe amount may keep (§4.17): "½ tsp" → "½ teaspoon", "2 tbsp" → "2 tablespoons".
 * - "2.5 kg bag · 2 cups" → "2.5 kilogram bag, 2 cups"; "in your pantry · ½ tsp" → "in your pantry, ½ teaspoon".
 * - Anything else (counts, words, "1 dozen", "6 cans") is kept as written.
 */
fun spokenDetail(detail: String): String {
    val units = UNIT_PATTERN.replace(detail) { match ->
        val unit = UNITS[match.groups["unit"]!!.value.lowercase()] ?: return@replace match.value
        val number = match.groups["num"]!!.value
        val rest = detail.substring(match.range.last + 1)
        val singular = isOne(number) || isAttributive(rest)
        "$number ${if (singular) unit.singular else unit.plural}"
    }
    return SEPARATOR.replace(units, ", ").trim()
}

/**
 * A row's TalkBack label (§15): the name, then its detail line in words. "Strawberries", "1 L basket" →
 * "Strawberries, 1 litre basket"; a blank detail gives the name alone.
 */
fun spokenItemLabel(name: String, detail: String?): String {
    val spoken = detail?.let(::spokenDetail).orEmpty()
    return if (spoken.isEmpty()) name else "$name, $spoken"
}

/**
 * A duration in words, for announcements and the receipt (§11.3, §13.2): 41 → "41 minutes", 1 → "1 minute",
 * 60 → "1 hour", 65 → "1 hour 5 minutes", 125 → "2 hours 5 minutes". Negative counts as 0.
 */
fun spokenDuration(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val hours = m / 60
    val rest = m % 60
    val h = if (hours == 0) null else if (hours == 1) "1 hour" else "$hours hours"
    val r = if (rest == 1) "1 minute" else "$rest minutes"
    return when {
        h == null -> r
        rest == 0 -> h
        else -> "$h $r"
    }
}

private class UnitWords(val singular: String, val plural: String)

private val UNITS: Map<String, UnitWords> = run {
    val gram = UnitWords("gram", "grams")
    val kilogram = UnitWords("kilogram", "kilograms")
    val milligram = UnitWords("milligram", "milligrams")
    val litre = UnitWords("litre", "litres")
    val millilitre = UnitWords("millilitre", "millilitres")
    val centilitre = UnitWords("centilitre", "centilitres")
    val teaspoon = UnitWords("teaspoon", "teaspoons")
    val tablespoon = UnitWords("tablespoon", "tablespoons")
    mapOf(
        "g" to gram, "kg" to kilogram, "mg" to milligram,
        "l" to litre, "ml" to millilitre, "cl" to centilitre,
        "tsp" to teaspoon, "tsps" to teaspoon,
        "tbsp" to tablespoon, "tbsps" to tablespoon, "tbs" to tablespoon,
    )
}

private const val FRACTION_CHARS = "½¼¾⅓⅔⅛"

/** "1 1/2", "1/2", "2.5", "2,5", "1½", "½", "540". */
private const val NUMBER = """(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?(?:\s*[$FRACTION_CHARS])?|[$FRACTION_CHARS])"""

/** A number, an optional space or hyphen, a unit that is not the start of a longer word ("2 large" has no unit). */
private val UNIT_PATTERN = Regex(
    """(?<![\p{L}\d.,/])(?<num>$NUMBER)\s*-?\s*(?<unit>${UNITS.keys.sortedByDescending { it.length }.joinToString("|")})(?![\p{L}\d])""",
    RegexOption.IGNORE_CASE,
)

private val SEPARATOR = Regex("""\s*·\s*""")

/** The next word after a unit; the unit is an adjective when a noun follows ("4 L bag"). */
private val NEXT_WORD = Regex("""^\s+(\p{L}+)""")

/** Words after which a unit is still a noun: "500 g each", "1 kg of flour", "2 L or more". */
private val NOUN_FOLLOWERS = setOf(
    "each", "per", "of", "and", "or", "to", "total", "max", "min", "approx", "about", "plus", "at", "for", "in",
)

private fun isAttributive(rest: String): Boolean {
    val word = NEXT_WORD.find(rest)?.groupValues?.get(1)?.lowercase() ?: return false
    return word !in NOUN_FOLLOWERS
}

/** True for exactly one ("1", "1.0", "1,00") and for a fraction under one ("½", "1/2"), which are said singular. */
private fun isOne(number: String): Boolean {
    val n = number.trim()
    if (n.length == 1 && n[0] in FRACTION_CHARS) return true
    Regex("""^(\d+)/(\d+)$""").matchEntire(n)?.let { m ->
        val (a, b) = m.destructured
        return b.toInt() != 0 && a.toInt() in 1 until b.toInt()
    }
    if (n.any { it in FRACTION_CHARS } || n.contains('/')) return false
    return n.replace(',', '.').toDoubleOrNull() == 1.0
}

package earth.cora.picnic.core.model

/**
 * Pulls quantities out of typed item text ("2 lemons", "500g flour", "lemons x2", "milk 2L").
 * Used by OfflineParser (no network) and by IconCatalog.match (to ignore a leading quantity).
 * Deliberately conservative: a bare trailing number is only taken from "x2"/"2x" forms or
 * parentheses, so names like "Diapers size 4" stay intact.
 */
internal object Quantities {
    data class Split(val name: String, val quantity: String?)

    /** Metric/imperial units: what the user may type -> how it is displayed. */
    private val MEASURE_UNITS: Map<String, String> = buildMap {
        listOf("g", "gr", "gram", "grams", "gramme", "grammes").forEach { put(it, "g") }
        listOf("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms", "kilogramme", "kilogrammes").forEach { put(it, "kg") }
        put("mg", "mg")
        listOf("ml", "mls", "millilitre", "millilitres", "milliliter", "milliliters").forEach { put(it, "mL") }
        listOf("l", "ltr", "litre", "litres", "liter", "liters").forEach { put(it, "L") }
        listOf("lb", "lbs", "pound", "pounds").forEach { put(it, "lb") }
        put("oz", "oz")
    }

    /** Count words kept as typed (lowercased): "2 bags of chips" -> quantity "2 bags". */
    private val COUNT_UNITS: Set<String> = setOf(
        "dozen", "doz", "pack", "packs", "packet", "packets", "package", "packages", "pkg", "pkgs",
        "bag", "bags", "box", "boxes", "can", "cans", "tin", "tins", "bottle", "bottles", "jar", "jars",
        "bunch", "bunches", "loaf", "loaves", "head", "heads", "carton", "cartons", "tub", "tubs",
        "block", "blocks", "case", "cases", "roll", "rolls", "bar", "bars", "container", "containers",
        "bundle", "bundles", "punnet", "punnets", "pint", "pints",
        "douzaine", "douzaines", "paquet", "paquets", "sac", "sacs", "boite", "boites", "boîte", "boîtes",
        "pot", "pots", "bouteille", "bouteilles",
    )

    private val NUMBER_WORDS: Map<String, String> = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5", "six" to "6",
        "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10", "eleven" to "11", "twelve" to "12",
    )

    private const val NUM = """(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?|[½¼¾⅓⅔])"""
    private val UNIT_ALT: String = (MEASURE_UNITS.keys + COUNT_UNITS)
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    // "2 lemons", "2x lemons", "500g flour", "1 L milk", "2 bags of chips", "a dozen eggs"
    private val LEADING = Regex(
        """^(?<num>$NUM|an?|${NUMBER_WORDS.keys.joinToString("|")})\s*(?:[x×]\s*)?(?:(?<unit>$UNIT_ALT)\b\.?)?(?:\s+of)?\s+(?<rest>.+)$""",
        RegexOption.IGNORE_CASE,
    )

    // "lemons x2", "lemons 2x", "milk 2L", "flour 500 g", "avocados (3)", "eggs (1 dozen)"
    private val TRAILING = Regex(
        """^(?<rest>.+?)(?:\s*[,:]\s*|\s+|(?=\())(?:(?:[x×]\s*(?<xnum>$NUM))|(?:(?<numx>$NUM)\s*[x×])|(?:(?<num>$NUM)\s*(?<unit>$UNIT_ALT)\.?)|(?:\(\s*(?<pnum>$NUM)\s*(?<punit>$UNIT_ALT)?\.?\s*\)))$""",
        RegexOption.IGNORE_CASE,
    )

    /** Splits [text] into a name and an optional quantity. The name is trimmed but not capitalized. */
    fun split(text: String): Split {
        val trimmed = text.trim()
        LEADING.matchEntire(trimmed)?.let { m ->
            val rawNum = m.groups["num"]!!.value
            val unit = m.groups["unit"]?.value
            val rest = m.groups["rest"]!!.value.trim()
            val isArticle = rawNum.equals("a", true) || rawNum.equals("an", true)
            // "a lemon" is not a quantity worth extracting; "a dozen eggs" / "a bag of chips" is.
            if (rest.any { it.isLetter() } && !(isArticle && unit == null)) {
                val num = if (isArticle) "1" else NUMBER_WORDS[rawNum.lowercase()] ?: rawNum
                return Split(rest, format(num, unit))
            }
        }
        // "dozen eggs" -> "1 dozen"
        Regex("""^(dozen|douzaine)\s+(?:of\s+)?(.+)$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)?.let { m ->
            return Split(m.groupValues[2].trim(), "1 ${m.groupValues[1].lowercase()}")
        }
        TRAILING.matchEntire(trimmed)?.let { m ->
            val rest = m.groups["rest"]!!.value.trim().trimEnd('-', '–', ',', ':').trim()
            if (rest.any { it.isLetter() }) {
                val q = m.groups["xnum"]?.value?.let { format(it, null) }
                    ?: m.groups["numx"]?.value?.let { format(it, null) }
                    ?: m.groups["num"]?.value?.let { format(it, m.groups["unit"]?.value) }
                    ?: m.groups["pnum"]?.value?.let { format(it, m.groups["punit"]?.value) }
                if (q != null) return Split(rest, q)
            }
        }
        return Split(trimmed, null)
    }

    /** [text] without a leading quantity ("2 lemons" -> "lemons", "500 g flour" -> "flour"). */
    fun stripLeading(text: String): String {
        val m = LEADING.matchEntire(text.trim()) ?: return text.trim()
        val isArticle = m.groups["num"]!!.value.let { it.equals("a", true) || it.equals("an", true) }
        val rest = m.groups["rest"]!!.value.trim()
        return if ((isArticle && m.groups["unit"] == null) || rest.none { it.isLetter() }) text.trim() else rest
    }

    private fun format(num: String, unit: String?): String {
        val n = num.replace(Regex("\\s+"), " ").trim()
        if (unit == null) return n
        val measure = MEASURE_UNITS[unit.lowercase()]
        return if (measure != null) "$n $measure" else "$n ${unit.lowercase()}"
    }
}

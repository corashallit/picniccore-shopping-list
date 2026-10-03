package earth.cora.picnic.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

/**
 * Detail lines are metric (§4.17, §16; checklist 214, 296): lb, oz, fl oz, qt and gal become g, kg, mL and L,
 * wherever they appear ("3 lb bag" -> "1.36 kg bag", "12 oz" -> "340 g", "2-lb bag" -> "907 g bag",
 * "1 gal" -> "3.79 L"). Cup, tbsp and tsp survive only as a recipe amount after the "·" ("2.5 kg bag · 2 cups");
 * before it they become mL (1 cup = 250 mL, 1 tbsp = 15 mL, 1 tsp = 5 mL). A pinch stays a pinch. Everything
 * that is already metric, a count or a word is kept as written. Owner: WP3 (MetricNormaliserTest).
 * Applied to quantities from the model (OpenAiGroceryAssistant) and typed text (OfflineParser).
 */
fun normalizeMetric(quantity: String): String {
    val dot = quantity.indexOf('·')
    if (dot < 0) return Metric.convert(quantity, keepCooking = false)
    return Metric.convert(quantity.substring(0, dot), keepCooking = false) + "·" +
        Metric.convert(quantity.substring(dot + 1), keepCooking = true)
}

internal object Metric {
    private enum class Unit(val factor: Double, val metric: String, val cooking: Boolean = false) {
        POUND(453.59237, "g"),
        OUNCE(28.349523125, "g"),
        FLUID_OUNCE(29.5735295625, "mL"),
        QUART(946.352946, "mL"),
        GALLON(3785.411784, "mL"),
        CUP(250.0, "mL", cooking = true),
        TABLESPOON(15.0, "mL", cooking = true),
        TEASPOON(5.0, "mL", cooking = true),
    }

    private val UNIT_WORDS: List<Pair<String, Unit>> = listOf(
        "fluid ounces" to Unit.FLUID_OUNCE, "fluid ounce" to Unit.FLUID_OUNCE, "fl. oz." to Unit.FLUID_OUNCE,
        "fl. oz" to Unit.FLUID_OUNCE, "fl oz" to Unit.FLUID_OUNCE, "fl.oz" to Unit.FLUID_OUNCE, "floz" to Unit.FLUID_OUNCE,
        "pounds" to Unit.POUND, "pound" to Unit.POUND, "lbs" to Unit.POUND, "lb" to Unit.POUND,
        "ounces" to Unit.OUNCE, "ounce" to Unit.OUNCE, "oz" to Unit.OUNCE,
        "quarts" to Unit.QUART, "quart" to Unit.QUART, "qts" to Unit.QUART, "qt" to Unit.QUART,
        "gallons" to Unit.GALLON, "gallon" to Unit.GALLON, "gal" to Unit.GALLON,
        "cups" to Unit.CUP, "cup" to Unit.CUP, "c." to Unit.CUP,
        "tablespoons" to Unit.TABLESPOON, "tablespoon" to Unit.TABLESPOON, "tbsps" to Unit.TABLESPOON, "tbsp" to Unit.TABLESPOON,
        "tbs" to Unit.TABLESPOON, "teaspoons" to Unit.TEASPOON, "teaspoon" to Unit.TEASPOON, "tsps" to Unit.TEASPOON, "tsp" to Unit.TEASPOON,
    )

    private val FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125)

    private const val NUMBER = """(?:\d+\s+\d+/\d+|\d+\s*[½¼¾⅓⅔⅛]|\d+/\d+|\d+(?:[.,]\d+)?|[½¼¾⅓⅔⅛])"""
    private val UNIT_ALT = UNIT_WORDS.map { it.first }.sortedByDescending { it.length }
        .joinToString("|") { it.replace(".", "\\.").replace(" ", "\\s*") }

    // "3 lb", "3lb", "3-lb", "1 1/2 lb", "½ lb", "12 fl oz", "2 x 1 lb" (the multiplier stays).
    private val PATTERN = Regex("""(?<![\w.,/])(?<num>$NUMBER)\s*-?\s*(?<unit>$UNIT_ALT)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    fun convert(text: String, keepCooking: Boolean): String = PATTERN.replace(text) { m ->
        val unit = unitOf(m.groups["unit"]!!.value) ?: return@replace m.value
        if (unit.cooking && keepCooking) return@replace m.value
        val amount = parse(m.groups["num"]!!.value) ?: return@replace m.value
        format(amount * unit.factor, unit.metric)
    }

    private fun unitOf(raw: String): Unit? {
        val key = raw.lowercase().replace(Regex("\\s+"), "")
        return UNIT_WORDS.firstOrNull { it.first.replace(" ", "") == key }?.second
    }

    private fun parse(raw: String): Double? {
        val s = raw.trim()
        Regex("""^(\d+)\s+(\d+)/(\d+)$""").matchEntire(s)?.let { m ->
            val (w, n, d) = m.destructured
            return if (d.toInt() == 0) null else w.toDouble() + n.toDouble() / d.toDouble()
        }
        Regex("""^(\d+)\s*([½¼¾⅓⅔⅛])$""").matchEntire(s)?.let { m -> return m.groupValues[1].toDouble() + FRACTIONS.getValue(m.groupValues[2][0]) }
        Regex("""^(\d+)/(\d+)$""").matchEntire(s)?.let { m ->
            val (n, d) = m.destructured
            return if (d.toInt() == 0) null else n.toDouble() / d.toDouble()
        }
        if (s.length == 1 && s[0] in FRACTIONS) return FRACTIONS.getValue(s[0])
        return s.replace(',', '.').toDoubleOrNull()
    }

    /** Grams or millilitres as a shelf label would print them: "454 g", "1.36 kg", "355 mL", "3.79 L". */
    private fun format(value: Double, base: String): String {
        val big = base == "g" && value >= 1000 || base == "mL" && value >= 1000
        if (!big) {
            val rounded = if (value < 10) round(value, 1) else BigDecimal(value.roundToLong())
            return "${rounded.toPlainString()} $base"
        }
        val unit = if (base == "g") "kg" else "L"
        return "${round(value / 1000, 2).toPlainString()} $unit"
    }

    private fun round(value: Double, places: Int): BigDecimal =
        BigDecimal(value).setScale(places, RoundingMode.HALF_UP).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
}

package earth.cora.picnic.release

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Items 294 and 295 (docs/DESIGN_STICKER.md §16, §17): no user-visible string mentions a proxy, a model name or prices,
 * and none uses an emoji as an icon (294); strings use Canadian spelling and metric units only, with cooking measures
 * only in a recipe amount after the "·" (§4.17) (295, [canadianAndMetric]).
 *
 * "User-visible strings": the string resources, the copy in the app's Kotlin (its literals with words in them), the
 * catalog's labels, the core's output side (its messages, the default aisles, the trip's stats) and the fixtures and
 * samples that render into the goldens. Developers' messages (exceptions, logs, require/check) are not copy. What the
 * screens actually show is walked by ScreenCopyTest. Owner: WP10.
 */
class CopyRulesTest {
    /**
     * The core's two files on the input side (§17), whose words are never shown: Requests.kt writes what is sent to
     * OpenAI (the prompts' context, "Icon catalog by category"), and Metric.kt is the normaliser that reads "12 fl oz" as
     * she writes it and turns it into millilitres (§16's metric rule is what it enforces).
     */
    private val coreInputSide = setOf("Requests.kt", "Metric.kt")

    private val resources: List<Found> by lazy { StringResources.all.map { Found("${it.file} ${it.name}", it.text) } }

    private val appCopy: List<Found> by lazy {
        Repo.files(File(Repo.appMain, "kotlin"), "kt").flatMap { f ->
            KotlinText.copyLiterals(f).map { Found("${Repo.rel(f)}:${it.line}", it.text) }
        }
    }

    private val coreCopy: List<Found> by lazy {
        Repo.files(File(Repo.coreMain, "kotlin"), "kt").filter { it.name !in coreInputSide }.flatMap { f ->
            KotlinText.copyLiterals(f).map { Found("${Repo.rel(f)}:${it.line}", it.text) }
        }
    }

    private val fixtures: List<Found> by lazy { Fixtures.literals.filter { KotlinText.isProse(it.text) } }

    /** Every source of copy, each with the least it must hold (so a broken scan never passes by finding nothing). */
    private val sources: List<Triple<String, List<Found>, Int>> by lazy {
        listOf(
            Triple("string resources", resources, 300),
            Triple("the app's copy in Kotlin", appCopy, 200),
            Triple("the core's output side", coreCopy, 20),
            Triple("catalog labels", Catalog.labels, 140),
            Triple("fixtures and samples", fixtures, 200),
        )
    }

    @Test
    fun noProxyModelNamesPricesOrEmoji() {
        val offences = sources.flatMap { (what, found, minimum) ->
            assertTrue("$what: ${found.size} texts, expected at least $minimum", found.size >= minimum)
            CopyRules.offences(found, CopyRules.copy).map { "$what — $it" }
        }
        assertTrue(offences.joinToString("\n"), offences.isEmpty())
    }

    @Test
    fun canadianAndMetric() {
        val offences = sources.flatMap { (what, found, minimum) ->
            assertTrue("$what: ${found.size} texts, expected at least $minimum", found.size >= minimum)
            (CopyRules.offences(found, CopyRules.canadianMetric) + CopyRules.cookingOffences(found)).map { "$what — $it" }
        }
        assertTrue(offences.joinToString("\n"), offences.isEmpty())
    }

    /** The rules find what they are for (and leave the kit's own symbols and Canadian spelling alone). */
    @Test
    fun theRulesBite() {
        fun hits(rules: List<CopyRules.Rule>, text: String) = rules.flatMap { it.matches(text) }
        assertTrue(hits(CopyRules.copy, "Sent through our proxy").isNotEmpty())
        assertTrue(hits(CopyRules.copy, "Drawn by gpt-image-2").isNotEmpty())
        assertTrue(hits(CopyRules.copy, "Milk \$4.99 on sale").size >= 2)
        assertTrue(hits(CopyRules.copy, "🛒 Start my list").isNotEmpty())
        assertTrue(hits(CopyRules.copy, "Done ✔️").isNotEmpty())
        assertTrue(hits(CopyRules.copy, "♡ let's go shopping ♡ ★ ☆ ✦ ♪ · …").isEmpty())
        assertTrue(hits(CopyRules.canadianMetric, "Pick a color for your favorite aisle").size == 2)
        assertTrue(hits(CopyRules.canadianMetric, "Pick a colour for your favourite aisle · 4 L bag · 454 g").isEmpty())
        assertTrue(hits(CopyRules.canadianMetric, "Ground beef 1 lb · 12 oz").size == 2)
        assertTrue(CopyRules.cookingAllowed("in your pantry · ½ tsp"))
        assertTrue(CopyRules.cookingAllowed("Salt, in your pantry, ½ teaspoon"))
        assertTrue(CopyRules.cookingAllowed("Paper & plastic cups"))
        assertTrue(!CopyRules.cookingAllowed("Add 2 cups of flour"))
        assertTrue(!CopyRules.cookingAllowed("Flour (2 cups)"))
        assertTrue(hits(CopyRules.ip, "Kirakira Basket · Hillcrest Market").isEmpty())
        assertTrue(hits(CopyRules.ip, "Sailor Moon stickers from Sanrio").size >= 2)
        assertTrue(hits(CopyRules.ip, "Kraft Dinner and Timbits from Foodland").size >= 3)
    }
}

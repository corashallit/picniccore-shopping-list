package earth.cora.picnic.core.eval

import earth.cora.picnic.core.assistant.TokenUsage
import earth.cora.picnic.core.model.IconFit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvalScoringTest {
    private val store = EvalStore(aliases = mapOf("dairy" to "dairy-eggs", "produce" to "produce", "baking" to "aisle-2-baking-spices"))

    private fun case(expect: List<ItemExpect>, forbidden: List<String> = emptyList(), search: SearchExpectation = SearchExpectation.ANY) =
        EvalCase(id = "t", kind = CaseKind.TEXT, input = "x", expect = expect, forbidden = forbidden, search = search)

    private fun item(name: String, aisle: String? = null, icon: String = "paper_bag", fit: IconFit = IconFit.EXACT, qty: String? = null, note: String? = null) =
        Produced(name, qty, note, aisle, icon, fit)

    @Test
    fun phrasesAreAccentCaseAndPluralTolerant() {
        assertTrue(Phrase.matches("egg", "Large eggs"))
        assertTrue(Phrase.matches("oeuf", "Œufs"))
        assertTrue(Phrase.matches("tomato", "Roma tomatoes"))
        assertTrue(Phrase.matches("beurre d'arachide", "Beurre d'arachide croquant"))
        assertFalse(Phrase.matches("pea", "Peanuts"), "whole words only")
        assertTrue(Phrase.matches("battery", "AA batteries"))
        assertTrue(Phrase.matches("blueberry", "Blueberries"))
        assertTrue(Phrase.matches("re:^salt$", "Salt"))
        assertFalse(Phrase.matches("re:^salt$", "Sea salt flakes"))
    }

    @Test
    fun wholeNameMatchesWinOverPartialOnes() {
        val c = case(listOf(ItemExpect(match = listOf("milk"), exclude = listOf("oat")), ItemExpect(match = listOf("oat milk"))))
        val s = EvalScoring.scoreIngest(c, store, listOf(item("Oat milk"), item("Milk")), "ok", 0)
        assertEquals(2, s.requiredMatched)
        assertEquals(2, s.producedMatched)
    }

    @Test
    fun missesExtrasAndForbiddenAreReported() {
        val c = case(
            listOf(ItemExpect(match = listOf("flour")), ItemExpect(match = listOf("sugar")), ItemExpect(match = listOf("vanilla"), optional = true)),
            forbidden = listOf("butter"),
        )
        val s = EvalScoring.scoreIngest(c, store, listOf(item("Flour"), item("Vanilla extract"), item("Butter"), item("Sprinkles")), "ok", 0)
        assertEquals(1, s.requiredMatched)
        assertEquals(2, s.requiredTotal)
        assertEquals(2, s.producedMatched)
        assertEquals(4, s.produced)
        assertEquals(listOf("sugar"), s.misses)
        assertTrue(s.extras.any { "FORBIDDEN" in it })
        assertFalse(s.checks.first { it.what == "no forbidden items" }.ok)
        assertFalse(s.clean)
    }

    @Test
    fun aislesIconsQuantitiesAndUnitsAreChecked() {
        val c = case(
            listOf(
                ItemExpect(match = listOf("milk"), aisle = listOf("dairy"), icons = listOf("milk_carton"), qty = "re:1\\s?l"),
                ItemExpect(match = listOf("lemon"), aisle = listOf("produce"), icons = listOf("lemon"), qty = "re:\\b2\\b"),
                ItemExpect(match = listOf("gochujang"), aisle = listOf("produce"), approx = true),
                ItemExpect(match = listOf("cream cheese"), aisle = listOf("dairy"), icons = listOf("cream_cheese")),
            ),
        )
        val s = EvalScoring.scoreIngest(
            c,
            store,
            listOf(
                item("Milk", "dairy-eggs", "milk_carton", qty = "1 L"),
                item("Lemon", "aisle-2-baking-spices", "lemon", qty = "2"),
                item("Gochujang", "produce", "condiments_generic", IconFit.APPROXIMATE),
                item("Cream cheese", "dairy-eggs", "cream_cheese", IconFit.APPROXIMATE, qty = "8 oz"),
            ),
            "Added 4 items.",
            0,
        )
        assertEquals(3, s.aisleOk)
        assertEquals(4, s.aisleTotal)
        assertEquals(3, s.iconOk, "an approximate fit on a depicted item is wrong")
        assertFalse(s.checks.first { it.what == "metric quantities" }.ok)
        assertTrue(s.checks.filter { it.what.startsWith("qty") }.all { it.ok })
    }

    @Test
    fun searchAndManyExpectations() {
        val c = case(listOf(ItemExpect(match = listOf("spice", "salt"), optional = true, many = true)), search = SearchExpectation.AVOID)
        val s = EvalScoring.scoreIngest(c, store, listOf(item("Salt"), item("Mixed spice")), "ok", 1)
        assertEquals(2, s.producedMatched)
        assertFalse(s.checks.first { it.what == "no web search" }.ok)
    }

    /**
     * The message rule (§8 item 23): a correction she made mid-note (the cases tagged "self-correction") needs a message
     * saying what was corrected, even when every row is one she named; a plain list she named needs none, so no check
     * is added and an empty message passes.
     */
    @Test
    fun selfCorrectionsMustExplainThemselves() {
        val file = EvalFile.load()
        val voice = file.cases.single { it.id == "voice-cat-food" }
        assertTrue("self-correction" in voice.tags)
        val named = listOf(item("Potatoes", qty = "1 kg"), item("Milk"))
        fun messageCheck(case: EvalCase, message: String) =
            EvalScoring.scoreIngest(case, file.store(case.store), named, message, 0).checks.firstOrNull { it.what == "message present (something to explain)" }
        assertFalse(assertNotNull(messageCheck(voice, ""), "a correction needs its message checked").ok, "an empty message hides the correction")
        assertTrue(assertNotNull(messageCheck(voice, "Updated the potatoes to 1 kg.")).ok)
        // The same named rows on a plain list: nothing to explain, so no message check at all.
        val plain = case(listOf(ItemExpect(match = listOf("potato")), ItemExpect(match = listOf("milk"))))
        assertNull(messageCheck(plain, ""), "a plain named list adds no message check")
        // A worked-out row (not named) needs its message again.
        val worked = EvalScoring.scoreIngest(plain, store, listOf(item("Potatoes").copy(named = false)), "", 0)
        assertFalse(worked.checks.first { it.what == "message present (something to explain)" }.ok)
    }

    @Test
    fun pricingUsesSnapshotPrefixesAndCacheRates() {
        val usage = TokenUsage(inputTokens = 10_000, cachedTokens = 8_000, cacheWriteTokens = 0, outputTokens = 500, reasoningTokens = 100)
        // 2000 fresh * $2 + 8000 cached * $0.20 + 500 out * $12 per 1M
        val expected = (2_000 * 2.0 + 8_000 * 0.2 + 500 * 12.0) / 1_000_000
        assertEquals(expected, EvalPricing.responses("gpt-5.6-terra-2026-09-12", usage, 0), 1e-9)
        assertEquals(expected + 0.01, EvalPricing.responses("gpt-5.6-terra", usage, 1), 1e-9)
        assertEquals(0.0045 * 0.5, EvalPricing.transcription("gpt-transcribe", 30.0), 1e-9)
    }
}

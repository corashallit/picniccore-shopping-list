package earth.cora.picnic.ui.add

import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.PhotoLine
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.ui.eggs.SurpriseState
import earth.cora.picnic.ui.theme.WashiPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The add flows' pure rules (docs/DESIGN_STICKER.md §6.2 rules 4 and 7, §4.17–§4.20, D16, D19; item 296's add side). */
class AddRulesTest {
    private val f = AddFixtures

    private fun answer(vararg items: earth.cora.picnic.core.assistant.ProposedItem, message: String = "") =
        IngestResult(items.toList(), message, emptyList(), emptyList(), f.meta())

    @Test
    fun aMessageIsWorthShowingOnlyWhenItSaysSomethingNew() {
        assertFalse(AddRules.messageWorthShowing(null, "milk"))
        assertFalse(AddRules.messageWorthShowing("  ", "milk"))
        assertFalse("an echo of her note", AddRules.messageWorthShowing("Milk.", "milk"))
        assertTrue(AddRules.messageWorthShowing("Milk comes in 4 L bags here.", "milk"))
    }

    @Test
    fun quickAddRule() {
        val three = answer(f.proposed("Lemons", "lemon", "produce"), f.proposed("Limes", "lime", "produce"), f.proposed("Oat milk", "plant_milk", "dairy-eggs"))
        assertTrue(AddRules.isQuickAdd(three, "lemons limes oat milk"))
        val four = answer(*three.items.toTypedArray(), f.proposed("Eggs", "eggs", "dairy-eggs"))
        assertFalse("more than three items", AddRules.isQuickAdd(four, "x"))
        assertFalse("a recipe", AddRules.isQuickAdd(f.pancakes(), "pancakes"))
        assertFalse("an optional row", AddRules.isQuickAdd(answer(f.proposed("Vanilla", "vanilla", null, skip = SkipReason.OPTIONAL)), "x"))
        assertFalse("a pantry row (item 324)", AddRules.isQuickAdd(answer(f.proposed("Salt", "salt", null, skip = SkipReason.IN_PANTRY)), "x"))
        assertTrue(
            "a duplicate only pops (rule 7)",
            AddRules.isQuickAdd(answer(f.proposed("Milk", "milk_bag", "dairy-eggs", skip = SkipReason.ALREADY_ON_LIST)), "milk"),
        )
        assertFalse("a message worth showing", AddRules.isQuickAdd(answer(f.proposed("Milk", "milk_bag", null), message = "Bags are 4 L."), "milk"))
    }

    /** Item 296, the add flows' half: every detail line is metric after normalisation; a recipe amount after "·" may be cooking. */
    @Test
    fun detailMetric() {
        assertEquals("1.36 kg bag", AddRules.detailLine("3 lb bag", null))
        assertEquals("340 g", AddRules.detailLine("12 oz", null))
        assertEquals("3.79 L", AddRules.detailLine("1 gal", null))
        assertEquals("2.5 kg bag · 2 cups", AddRules.detailLine("2.5 kg bag·2 cups", null))
        assertEquals("907 g bag · 1 tbsp", AddRules.detailLine("2-lb bag · 1 tbsp", null))
        assertEquals("1 kg box · pinch", AddRules.detailLine("1 kg box · pinch", null))
        assertEquals("dozen · from your recipe", AddRules.detailLine("dozen", "from your recipe"))
        assertNull(AddRules.detailLine(" ", null))
        // Every detail line of the review fixtures reads metric before the dot.
        val imperial = Regex("\\b(lb|lbs|oz|fl oz|qt|gal|pounds?|ounces?|cups?|tbsp|tsp)\\b")
        val all = (f.pancakes().items + f.pancakes(withPantry = true).items + f.photo().items + f.voice().items)
        for (item in all) {
            val line = AddRules.detailLine(item) ?: continue
            assertFalse("$line is metric before the dot", imperial.containsMatchIn(line.substringBefore('·')))
        }
        // The pantry row's reason keeps only the recipe amount.
        assertEquals("½ tsp", AddRules.recipeAmount("1 kg box · ½ tsp"))
        assertNull(AddRules.recipeAmount("1 kg box"))
    }

    @Test
    fun thinkingStepsComeOnlyFromRealFields() {
        // A plain list: no web search, no recipe, no pantry, so only the sorting step.
        val plain = answer(f.proposed("Lemons", "lemon", "produce"), f.proposed("Eggs", "eggs", "dairy-eggs"))
        assertEquals(listOf(ThinkingStep.SortedAisles(2)), AddRules.thinkingSteps(plain, "lemons and eggs"))

        val steps = AddRules.thinkingSteps(f.pancakes(withPantry = true), PANCAKES)
        assertEquals(
            listOf(
                ThinkingStep.SearchedWeb(AddRules.searchQuery(PANCAKES)),
                ThinkingStep.ComparedRecipes(3, 12),
                ThinkingStep.CheckedPantry(4),
                ThinkingStep.SortedAisles(4),
            ),
            steps,
        )
    }

    @Test
    fun theSearchStepQuotesHerNoteOnOneLine() {
        assertEquals("pancakes from scratch", AddRules.searchQuery("  pancakes\nfrom   scratch "))
        val long = AddRules.searchQuery("ingredients for a big birthday cake with cream cheese icing and sprinkles")
        assertTrue(long.length <= 44)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun theBarTapeFollowsTheSurprises() {
        assertEquals(WashiPattern.SakuraStripe, AddRules.addBarTape(SurpriseState()))
        assertEquals(WashiPattern.Raindrop, AddRules.addBarTape(SurpriseState(rain = true, season = Season.HALLOWEEN)))
        assertEquals(WashiPattern.Halloween, AddRules.addBarTape(SurpriseState(season = Season.HALLOWEEN)))
        assertEquals("eggs off: always the sakura stripes", WashiPattern.SakuraStripe, AddRules.addBarTape(SurpriseState(enabled = false, rain = true)))
    }

    @Test
    fun keywordsFindTheNameOrItsTail() {
        val text = f.TRANSCRIPT
        val ranges = AddRules.keywordRanges(text, listOf("Frozen corn", "Cat litter", "Eggs"))
        assertEquals(listOf("frozen corn", "litter"), ranges.map { text.substring(it.first, it.last + 1).lowercase() })
        // Whole words only: "corn" is not found inside "cornflakes".
        assertTrue(AddRules.keywordRanges("cornflakes please", listOf("Corn")).isEmpty())
    }

    @Test
    fun photoRowsPopInTheOrderOfTheirLines() {
        val lines = listOf(
            PhotoLine("eggs", crossedOut = false, correctionOf = null, itemIndex = 1),
            PhotoLine("chips", crossedOut = true, correctionOf = null, itemIndex = null),
            PhotoLine("milk", crossedOut = false, correctionOf = null, itemIndex = 0),
        )
        assertEquals(listOf(11, 10, 12), AddRules.photoPopOrder(lines, listOf(10, 11, 12)))
    }

    @Test
    fun heardItemsAlreadyInTheReviewAreDropped() {
        val heard = f.voice().items
        assertEquals(listOf("Frozen corn", "Cat litter"), AddRules.newHeard(listOf("Milk", "eggs"), heard).map { it.name })
    }

    @Test
    fun rowsStartSwitchedOnOnlyWhenNothingSkippedThem() {
        val rows = AddRules.rows(
            listOf(
                f.proposed("Eggs", "eggs", "dairy-eggs"),
                f.proposed("Vanilla", "vanilla", "aisle-2-baking", skip = SkipReason.OPTIONAL),
                f.proposed("Salt", "salt", "aisle-2-baking", skip = SkipReason.IN_PANTRY),
                f.proposed("Milk, 2 %", "milk_bag", "dairy-eggs", skip = SkipReason.ALREADY_ON_LIST),
                f.proposed("Yuzu kosho", "condiment", null, fit = IconFit.PENDING),
            ),
            f.state.store.aisles(), f.state.store.aisleStyles, RowBatch.MAIN, firstKey = 0,
        )
        assertEquals(listOf(true, false, false, false, true), rows.map { it.keep })
        assertEquals(listOf(true, true, true, false, true), rows.map { it.canKeep })
        assertEquals("a pending icon shows the capsule", IconCatalog.CAPSULE, rows.last().icon.key)
        assertEquals("Everything else has no store aisle", null, rows.last().storeAisle)
        assertEquals(2, rows[1].storeAisle)
    }

    @Test
    fun aPhotoReviewHasNoThinkingSteps() {
        val review = f.review(1, f.photo(), ItemSource.PHOTO, query = null)
        assertTrue(review.steps.isEmpty())
        assertEquals(f.photo().lines, review.lines)
    }
}

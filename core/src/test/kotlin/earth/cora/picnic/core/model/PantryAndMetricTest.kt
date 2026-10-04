package earth.cora.picnic.core.model

import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.SkipReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The metric normaliser (§4.17, §16; checklist 214). */
class MetricNormaliserTest {
    @Test
    fun imperialBecomesMetric() {
        assertEquals("454 g", normalizeMetric("1 lb"))
        assertEquals("907 g bag", normalizeMetric("2 lb bag"))
        assertEquals("1.36 kg bag", normalizeMetric("3 lb bag"), "the fixture's McIntosh apples")
        assertEquals("907 g bag", normalizeMetric("2-lb bag"))
        assertEquals("680 g", normalizeMetric("1.5 lbs"))
        assertEquals("680 g", normalizeMetric("1 1/2 pounds"))
        assertEquals("227 g", normalizeMetric("½ lb"))
        assertEquals("340 g bag", normalizeMetric("12 oz bag"), "the fixture's coffee beans")
        assertEquals("227 g", normalizeMetric("8oz"))
        assertEquals("355 mL", normalizeMetric("12 fl oz"))
        assertEquals("473 mL", normalizeMetric("16 fl. oz."))
        assertEquals("946 mL", normalizeMetric("1 qt"))
        assertEquals("1.89 L", normalizeMetric("2 quarts"))
        assertEquals("3.79 L", normalizeMetric("1 gal"))
        assertEquals("4.54 kg", normalizeMetric("10 lb"))
        assertEquals("2 x 454 g", normalizeMetric("2 x 1 lb"))
        assertEquals("1,5 kg", normalizeMetric("1,5 kg"), "already metric")
    }

    @Test
    fun metricCountsAndWordsAreKept() {
        listOf("4 L bag", "1 L basket", "540 mL tin", "675 g loaf", "454 g brick", "796 mL can", "6 bananas", "dozen", "1 crown", "2", "12 double rolls")
            .forEach { assertEquals(it, normalizeMetric(it), it) }
        assertEquals("Golden delicious", normalizeMetric("Golden delicious"), "\"lb\" inside a word is not a unit")
        assertEquals("1 bulb", normalizeMetric("1 bulb"))
    }

    @Test
    fun cookingMeasuresOnlyAsARecipeAmount() {
        assertEquals("2.5 kg bag · 2 cups", normalizeMetric("2.5 kg bag · 2 cups"), "the recipe amount keeps cups")
        assertEquals("225 g tin · 1 tbsp", normalizeMetric("225 g tin · 1 tbsp"))
        assertEquals("1 jar · ½ tsp", normalizeMetric("1 jar · ½ tsp"))
        assertEquals("1 jar · a pinch", normalizeMetric("1 jar · a pinch"))
        assertEquals("500 mL", normalizeMetric("2 cups"), "before the dot a cup is 250 mL")
        assertEquals("15 mL", normalizeMetric("1 tbsp"))
        assertEquals("2.5 mL", normalizeMetric("½ tsp"))
        assertEquals("454 g · 227 g", normalizeMetric("1 lb · 8 oz"), "every other unit is converted after the dot too")
    }
}

/** The pantry ops (§6.13; checklist 319). */
class PantryOpsTest {
    private val base = AppState(lists = listOf(GroceryList("l1", "Groceries", 0)), currentListId = "l1")

    @Test
    fun setPantryTextSplitsDropsQuantitiesAndDedupes() {
        val s = base.setPantryText("2 kg flour, salt and pepper\n- spices\n\nFlour\nolive oil; eggs", 10)
        assertEquals(listOf("Flour", "Salt", "Pepper", "Spices", "Olive oil", "Eggs"), s.pantry.map { it.name })
        assertTrue(s.pantry.all { it.addedAt == 10L && it.icon == null })
        assertEquals("Flour\nSalt\nPepper\nSpices\nOlive oil\nEggs", s.pantryText())
    }

    @Test
    fun setPantryTextKeepsUnchangedEntriesAndTheTextsOrder() {
        val s = base.setPantryText("Flour\nSalt", 10).setPantryIcon("flour", IconRef("flour"))
        val edited = s.setPantryText("salt\nSugar\nflours", 20)
        assertEquals(listOf("Salt", "Sugar", "Flours"), edited.pantry.map { it.name }, "the text's order")
        assertEquals(10L, edited.pantry[0].addedAt, "an unchanged name keeps its addedAt")
        assertEquals(IconRef("flour"), edited.pantry[2].icon, "and its sticker (flour = flours)")
        assertEquals(20L, edited.pantry[1].addedAt)
        assertSame(s, s.setPantryText("Flour\nSalt", 30), "nothing changed")
        assertTrue(s.setPantryText("  \n", 30).pantry.isEmpty())
    }

    /** Names kept whole on the shelf (the Edit sheet's toggle) survive an edit of another line in "Pantry as text". */
    @Test
    fun setPantryTextKeepsWholeNamesWhole() {
        val s = base.copy(
            pantry = listOf(
                PantryEntry("Mac and cheese", IconRef("mac_cheese"), addedAt = 1),
                PantryEntry("Milk, 2 %", addedAt = 2),
                PantryEntry("Salt", addedAt = 3),
            ),
        )
        val edited = s.setPantryText(s.pantryText() + "\nRice", 10)
        assertEquals(listOf("Mac and cheese", "Milk, 2 %", "Salt", "Rice"), edited.pantry.map { it.name })
        assertEquals(IconRef("mac_cheese"), edited.pantry[0].icon, "it keeps its sticker")
        assertEquals(listOf(1L, 2L, 3L, 10L), edited.pantry.map { it.addedAt })
    }

    @Test
    fun addToPantryIgnoresNamesAlreadyThere() {
        val s = base.addToPantry(listOf("Salt"), 1)
        assertEquals(listOf("Salt"), s.pantry.map { it.name })
        assertSame(s, s.addToPantry(listOf("salt", "SALT"), 2))
        val more = s.addToPantry(listOf("pepper, oil", "Pepper"), 3)
        assertEquals(listOf("Salt", "Pepper", "Oil"), more.pantry.map { it.name }, "added at the end of the shelves")
        assertEquals(3L, more.pantry.last().addedAt)
    }

    /** " and " splits a list ("flour, salt and pepper"); a name written alone stays whole. */
    @Test
    fun andSplitsOnlyInsideAList() {
        assertEquals(listOf("Half and half"), PantryNames.split("half and half"))
        assertEquals(listOf("Pork & beans"), PantryNames.split("pork & beans"))
        assertEquals(listOf("Flour", "Salt", "Pepper"), PantryNames.split("flour, salt and pepper"))
        assertEquals(listOf("Half and half", "Rice"), PantryNames.split("half and half\nrice"), "one thing per line")
    }

    @Test
    fun removeIconAndInPantry() {
        val s = base.copy(pantry = listOf(PantryEntry("Flour", addedAt = 1), PantryEntry("Eggs", addedAt = 2)))
        assertEquals("Flour\nEggs", s.pantryText())
        assertTrue(s.inPantry("egg"))
        assertTrue(s.inPantry("FLOUR"))
        assertFalse(s.inPantry("Almond flour"))
        assertFalse(s.inPantry(""))
        assertEquals(listOf("Eggs"), s.removeFromPantry("flour").pantry.map { it.name })
        assertSame(s, s.removeFromPantry("salt"))
        assertEquals(IconRef("eggs"), s.setPantryIcon("egg", IconRef("eggs")).pantry[1].icon)
        assertSame(s, s.setPantryIcon("salt", IconRef("salt")))
        assertNull(s.setPantryIcon("egg", IconRef("eggs")).setPantryIcon("egg", null).pantry[1].icon)
    }

    @Test
    fun theFirstTwoHundredGoWithAnAiRequest() {
        val s = base.setPantryText((1..250).joinToString("\n") { "Thing $it" }, 1)
        assertEquals(250, s.pantry.size)
        val context = s.ingestContext("l1")
        assertEquals(PANTRY_IN_CONTEXT, context.pantry.size)
        assertEquals("Thing 1", context.pantry.first())
        assertEquals("Thing 200", context.pantry.last())
    }
}

/** The client backstop for the pantry rule (§6.2 rule 8; checklist 321). */
class PantryMatchTest {
    private fun proposed(name: String, named: Boolean, skip: SkipReason = SkipReason.NONE, entry: String? = null) =
        ProposedItem(name, null, null, null, "flour", earth.cora.picnic.core.model.IconFit.EXACT, skipReason = skip, named = named, pantryEntry = entry)

    @Test
    fun coversFoldsPluralsAndNothingMore() {
        assertTrue(PantryMatch.covers("eggs", "Egg"))
        assertTrue(PantryMatch.covers("Egg", "eggs"))
        assertTrue(PantryMatch.covers("tomatoes", "Tomato"))
        assertTrue(PantryMatch.covers("berries", "Berry"))
        assertTrue(PantryMatch.covers("Peaches", "peach"))
        assertTrue(PantryMatch.covers("Crème fraîche", "creme fraiche"))
        assertFalse(PantryMatch.covers("flour", "Almond flour"), "flour does not cover almond flour")
        assertFalse(PantryMatch.covers("spices", "Ground cinnamon"), "broad entries are the model's call")
        assertFalse(PantryMatch.covers("", ""))
    }

    @Test
    fun namedItemsAreNeverSkippedForThePantry() {
        val out = PantryMatch.apply(
            listOf(proposed("Flour", named = true), proposed("Salt", named = true, skip = SkipReason.IN_PANTRY, entry = "salt")),
            listOf("flour", "salt"),
        )
        assertEquals(listOf(SkipReason.NONE, SkipReason.NONE), out.map { it.skipReason }, "writing \"flour\" means she ran out")
        assertEquals(listOf(null, null), out.map { it.pantryEntry })
    }

    @Test
    fun workedOutItemsThePantryCoversAreSkipped() {
        val out = PantryMatch.apply(
            listOf(
                proposed("All-purpose flour", named = false),
                proposed("Eggs", named = false),
                proposed("Baking powder", named = false),
                proposed("Almond flour", named = false),
                proposed("Ground cinnamon", named = false, skip = SkipReason.IN_PANTRY, entry = "spices"),
                proposed("Sugar", named = false, skip = SkipReason.OPTIONAL),
                proposed("Salt", named = false, skip = SkipReason.IN_PANTRY),
            ),
            listOf("Egg", "Baking powder", "spices", "flour", "sugar", "salt"),
        )
        assertEquals(
            listOf(SkipReason.NONE, SkipReason.IN_PANTRY, SkipReason.IN_PANTRY, SkipReason.NONE, SkipReason.IN_PANTRY, SkipReason.OPTIONAL, SkipReason.IN_PANTRY),
            out.map { it.skipReason },
        )
        assertEquals(listOf(null, "Egg", "Baking powder", null, "spices", null, "salt"), out.map { it.pantryEntry })
    }
}

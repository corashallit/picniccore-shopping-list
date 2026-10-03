package earth.cora.picnic.ui.a11y

import org.junit.Assert.assertEquals
import org.junit.Test

/** Checklist 283 (docs/DESIGN_STICKER.md §15 "Units in words"): detail lines are spoken with units in words. */
class SpokenUnitsTest {
    private fun says(detail: String, spoken: String) = assertEquals(detail, spoken, spokenDetail(detail))

    @Test
    fun theSpecExamples() {
        says("1 L", "1 litre")
        says("540 mL", "540 millilitres")
        says("2.5 kg", "2.5 kilograms")
        says("454 g", "454 grams")
        says("4 L bag", "4 litre bag")
    }

    @Test
    fun theCopyDeckDetailLines() {
        // §16: "4 L bag", "1 L basket", "540 mL tin", "675 g loaf", "454 g brick", "796 mL can".
        says("1 L basket", "1 litre basket")
        says("540 mL tin", "540 millilitre tin")
        says("675 g loaf", "675 gram loaf")
        says("454 g brick", "454 gram brick")
        says("796 mL can", "796 millilitre can")
        says("1.36 kg bag", "1.36 kilogram bag")
    }

    @Test
    fun singularForOneAndForFractionsUnderOne() {
        says("1 kg", "1 kilogram")
        says("1 g", "1 gram")
        says("1.0 L", "1.0 litre")
        says("½ L", "½ litre")
        says("1/2 kg", "1/2 kilogram")
        says("1½ L", "1½ litres")
        says("0.5 L", "0.5 litres")
        says("3/2 L", "3/2 litres")
        says("2 L", "2 litres")
    }

    @Test
    fun theUnitIsANounBeforeEachOfOrAndMore() {
        says("500 g each", "500 grams each")
        says("1 kg of flour", "1 kilogram of flour")
        says("2 L or more", "2 litres or more")
        says("2 L or 4 L bag", "2 litres or 4 litre bag")
        says("2 L, 1%", "2 litres, 1%")
        says("355 mL × 12", "355 millilitres × 12")
    }

    @Test
    fun spellingsAndSpacing() {
        says("500g", "500 grams")
        says("2L", "2 litres")
        says("2-L bottle", "2 litre bottle")
        says("750 ml", "750 millilitres")
        says("1 KG", "1 kilogram")
        says("33 cL", "33 centilitres")
        says("500 mg", "500 milligrams")
        says("2,5 kg", "2,5 kilograms")
    }

    @Test
    fun recipeAmountsAfterTheDot() {
        says("2.5 kg bag · 2 cups", "2.5 kilogram bag, 2 cups")
        says("in your pantry · ½ tsp", "in your pantry, ½ teaspoon")
        says("1 tbsp", "1 tablespoon")
        says("2 tbsp", "2 tablespoons")
        says("3 tsp", "3 teaspoons")
        says("optional · skipped", "optional, skipped")
    }

    @Test
    fun wordsCountsAndLookAlikesAreKept() {
        says("1 dozen", "1 dozen")
        says("6 cans", "6 cans")
        says("2 large", "2 large")
        says("12 limes", "12 limes")
        says("3 lemons", "3 lemons")
        says("Size 4", "Size 4")
        says("", "")
        says("already on your list", "already on your list")
    }

    @Test
    fun rowLabels() {
        assertEquals("Strawberries, 1 litre basket", spokenItemLabel("Strawberries", "1 L basket"))
        assertEquals("Milk, 4 litre bag", spokenItemLabel("Milk", "4 L bag"))
        assertEquals("Bananas", spokenItemLabel("Bananas", null))
        assertEquals("Bananas", spokenItemLabel("Bananas", "  "))
    }

    @Test
    fun durations() {
        assertEquals("0 minutes", spokenDuration(0))
        assertEquals("1 minute", spokenDuration(1))
        assertEquals("41 minutes", spokenDuration(41))
        assertEquals("1 hour", spokenDuration(60))
        assertEquals("1 hour 1 minute", spokenDuration(61))
        assertEquals("1 hour 5 minutes", spokenDuration(65))
        assertEquals("2 hours", spokenDuration(120))
        assertEquals("2 hours 5 minutes", spokenDuration(125))
        assertEquals("0 minutes", spokenDuration(-3))
    }
}

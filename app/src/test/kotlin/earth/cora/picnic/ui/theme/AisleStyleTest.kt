package earth.cora.picnic.ui.theme

import earth.cora.picnic.core.model.AisleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The aisle rotation (§2.3) and the editor's overrides (§4.26); checklist 7 and 8. */
class AisleStyleTest {
    private val families = listOf("strawberry", "ramune", "lemon", "melon", "grape", "peach", "mint", "sakura", "cocoa", "periwinkle")
    private val patterns = listOf("gingham", "polka", "stripes", "hearts", "stars", "clouds", "check", "waves", "flowers", "plain")
    private val pictograms = listOf("strawberry", "bee", "star", "heart", "cloud", "flower", "ladybug", "teacup", "sandwich", "sun")

    @Test
    fun rotationWrapsAfterTen() {
        for (position in 1..21) {
            val look = AisleLooks.of(position)
            val i = (position - 1) % 10
            assertEquals("family at $position", families[i], look.family.name)
            assertEquals("pattern at $position", patterns[i], look.pattern.key)
            assertEquals("pictogram at $position", pictograms[i], look.pictogram.key)
            assertEquals(position, look.position)
        }
        val unsorted = AisleLooks.of(0)
        assertTrue(unsorted.isEverythingElse)
        assertEquals("neutral", unsorted.family.name)
        assertEquals(TabPattern.Plain, unsorted.pattern)
        assertEquals(Pictogram.Capsule, unsorted.pictogram)
    }

    @Test
    fun overrideWins() {
        val look = AisleLooks.of(4, AisleStyle(family = "sakura", pattern = "polka"))
        assertEquals("sakura", look.family.name)
        assertEquals(TabPattern.Polka, look.pattern)
        // The pictogram follows the position, never the colour.
        assertEquals(Pictogram.Heart, look.pictogram)
        // A partial override keeps the rest of the rotation; unknown names fall back.
        val colourOnly = AisleLooks.of(2, AisleStyle(family = "cocoa"))
        assertEquals("cocoa", colourOnly.family.name)
        assertEquals(TabPattern.Polka, colourOnly.pattern)
        val unknown = AisleLooks.of(3, AisleStyle(family = "chartreuse", pattern = "plaid"))
        assertEquals("lemon", unknown.family.name)
        assertEquals(TabPattern.Stripes, unknown.pattern)
        // The neutral family is "Everything else"'s alone.
        assertEquals("strawberry", AisleLooks.of(1, AisleStyle(family = "neutral")).family.name)
    }

    @Test
    fun v1AisleColoursFollowTheRotation() {
        for (i in 0 until 10) {
            assertEquals(Families.rotation[i].pastel, DayColors.aisle(i).pastel)
            assertEquals(Families.rotation[i].trace, DayColors.aisle(i).deep)
        }
        assertEquals(Families.neutral.pastel, DayColors.aisle(-1).pastel)
    }
}

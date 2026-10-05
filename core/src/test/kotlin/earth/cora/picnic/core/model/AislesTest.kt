package earth.cora.picnic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Store numbers and tab names (D9, §4.10; checklist 77). */
class AislesTest {
    @Test
    fun storeAisleNumber() {
        assertEquals(2, storeAisleNumber("Aisle 2 - Baking & spices"))
        assertEquals(12, storeAisleNumber("12 Frozen"))
        assertEquals(7, storeAisleNumber("aisle 7"))
        assertEquals(4, storeAisleNumber("Allée 4 : conserves"))
        assertEquals(3, storeAisleNumber("#3 Snacks"))
        assertEquals(5, storeAisleNumber("Aisle5 - Snacks & drinks"))
        assertEquals(9, storeAisleNumber("  Rayon 9 - Boissons"))
        assertNull(storeAisleNumber("Produce"))
        assertNull(storeAisleNumber("Dairy & eggs"))
        assertNull(storeAisleNumber("1% milk fridge"))
        assertNull(storeAisleNumber("Aisle of wonders"))
    }

    @Test
    fun displayName() {
        val names = Aisles.parse(
            "Aisle 3 - Pasta, rice & international\n12 Frozen\nAisle 7\nProduce\nAisle5 - Snacks & drinks\nAllée 4 : conserves\nAisle 2 - Baking",
        ).map { it.displayName() }
        assertEquals(listOf("Pasta, rice & international", "Frozen", "Aisle 7", "Produce", "Snacks & drinks", "conserves", "Baking"), names)
        assertEquals("Snacks", Aisle("snacks", "#3 Snacks", 1).displayName(), "(a '#' line is a comment in aisle text)")
        assertEquals("1% milk fridge", Aisle("x", "1% milk fridge", 1).displayName())
    }

    @Test
    fun theDefaultStoreTabsNeverReadAnumberTwice() {
        val tabs = Aisles.parse(Aisles.DEFAULT_TEXT).map { "${it.position} ${it.displayName()}" }
        assertEquals("5 Breakfast, coffee & tea", tabs[4], "never \"5 Aisle 1 - Breakfast\"")
        assertEquals(14, tabs.size)
    }

    @Test
    fun parseLinesKnowsWhereEachAisleIs() {
        val lines = Aisles.parseLines("# store\n- Produce\n\nBakery\n")
        assertEquals(listOf(1 to "produce", 3 to "bakery"), lines.map { it.lineIndex to it.aisle.id })
    }

    @Test
    fun cleanName() {
        assertEquals("Produce", Aisles.cleanName(" - Produce "))
        assertEquals("Fresh fruit", Aisles.cleanName("Fresh\r\nfruit"))
        assertEquals("Snacks", Aisles.cleanName("# Snacks"))
        assertEquals("", Aisles.cleanName("   "))
    }

    @Test
    fun aisleRotation() {
        assertEquals(AisleLook("strawberry", "gingham", "strawberry"), AisleRotation.forPosition(1))
        assertEquals(AisleLook("periwinkle", "plain", "sun"), AisleRotation.forPosition(10))
        assertEquals(AisleLook("strawberry", "gingham", "strawberry"), AisleRotation.forPosition(11), "the rotation wraps every 10")
        assertEquals(AisleLook("neutral", "plain", "capsule"), AisleRotation.forPosition(0))
        val baking = Aisle("baking", "Baking", 4)
        assertEquals(AisleLook("melon", "hearts", "heart"), AisleRotation.lookOf(baking, null))
        assertEquals(AisleLook("cocoa", "hearts", "heart"), AisleRotation.lookOf(baking, AisleStyle(family = "cocoa", pattern = " ")))
    }
}

/** Favourites (D17; checklist 218). */
class FavouritesTest {
    private val s = AppState(lists = listOf(GroceryList("l", "List", 0)), currentListId = "l")

    @Test
    fun storedByNormalizedNameAndToggled() {
        val on = s.setFavourite(normalizeName("Coffee beans"), true)
        assertEquals(setOf("coffee bean"), on.favourites)
        assertEquals(on, on.setFavourite("coffee bean", true))
        kotlin.test.assertSame(on, on.setFavourite("coffee bean", true), "already on: same state")
        assertEquals(emptySet(), on.setFavourite("coffee bean", false).favourites)
        kotlin.test.assertSame(s, s.setFavourite("coffee bean", false))
        kotlin.test.assertSame(s, s.setFavourite("", true), "a blank name is never a favourite")
        assertEquals(setOf("coffee bean", "milk"), on.setFavourite("milk", true).favourites)
    }
}

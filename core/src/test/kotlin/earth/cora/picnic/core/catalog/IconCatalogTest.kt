package earth.cora.picnic.core.catalog

import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconCatalogTest {
    private val catalog = TestSupport.catalog

    private fun m(name: String) = catalog.match(name)?.key

    @Test
    fun exactMatchesOnKeyLabelAndAliasesIncludingFrench() {
        assertEquals("lemon", m("Lemons"))
        assertEquals("lemon", m("lemon"))
        assertEquals("milk_carton", m("Lait"))
        assertEquals("plant_milk", m("Oat milk"))
        assertEquals("eggs", m("Œufs"))
        assertEquals("donut", m("Timbits"))
        assertEquals("mac_and_cheese", m("Kraft Dinner"))
        assertEquals("green_onion", m("green onions"))
        assertEquals("sweet_potato", m("Sweet potatoes"))
        assertEquals("candle", m("Birthday candles"))
    }

    @Test
    fun matchExactSkipsTheHeadNounGuess() {
        assertEquals("parsley", catalog.matchExact("Cilantro")?.key)
        assertEquals("lemon", catalog.matchExact("2 lemons")?.key)
        assertEquals("milk_bag", catalog.matchExact("4L milk")?.key)
        assertEquals("chocolate_bar", m("Lindt dark chocolate"), "match() still guesses the head noun")
        assertNull(catalog.matchExact("Lindt dark chocolate"))
        assertNull(catalog.matchExact("Nutritional yeast"))
        assertEquals("parsley", catalog.matchExact("Coriandre fraîche")?.key, "freshness descriptors are ignored")
        assertEquals("spinach", catalog.matchExact("Organic spinach")?.key)
        assertNull(catalog.matchExact("Fresh"), "a descriptor alone is not a product")
    }

    @Test
    fun aliasesWithSizesWinBeforeQuantitiesAreStripped() {
        assertEquals("milk_bag", m("4L milk"))
        assertEquals("milk_bag", m("bag of milk"))
        assertEquals("milk_carton", m("2 L milk"))
    }

    @Test
    fun leadingQuantitiesAreIgnored() {
        assertEquals("lemon", m("2 lemons"))
        assertEquals("flour", m("500 g flour"))
        assertEquals("lemon", m("2x lemons"))
        assertEquals("potato_chips", m("2 bags of chips"))
    }

    @Test
    fun headNounSuffixMatches() {
        assertEquals("lemon", m("Meyer lemons"))
        assertEquals("spinach", m("organic baby spinach"))
        assertEquals("chocolate_bar", m("Lindt dark chocolate"))
        assertEquals("plant_milk", m("vanilla oat milk"))
        assertEquals("apple", m("Honeycrisp apples"), "a modifier that names the same icon is fine")
    }

    @Test
    fun precisionOverRecall() {
        assertNull(m("Gochujang"))
        assertNull(m("Spatula"))
        assertNull(m("garlic bread"), "garlic is a different product: let the IconDecider decide")
        assertNull(m("cheese pizza"))
        assertNull(m("Rosé"), "ambiguous term: wine or a bouquet")
        assertNull(m(""))
        assertNull(m("2"))
        assertNull(m("  ★ "))
    }

    @Test
    fun genericIconsAreNeverHeadNounMatches() {
        val result = catalog.match("artisan sourdough something")
        assertTrue(result == null || !catalog.isGeneric(result.key))
    }

    @Test
    fun displayRefRules() {
        val base = GroceryItem(id = "a", name = "Lemons", icon = "lemon", iconFit = IconFit.EXACT, addedAt = 0)
        assertEquals(IconRef("lemon"), catalog.displayRef(base, busy = false))
        assertEquals(IconRef(IconCatalog.CAPSULE), catalog.displayRef(base, busy = true))
        assertEquals(IconRef("lemon", "c-1"), catalog.displayRef(base.copy(customIconId = "c-1"), busy = true))
        assertEquals(IconRef(IconCatalog.CAPSULE), catalog.displayRef(base.copy(icon = IconCatalog.DEFAULT_ICON, iconFit = IconFit.PENDING), busy = false))
        assertEquals(IconRef("condiments_generic"), catalog.displayRef(base.copy(icon = "condiments_generic", iconFit = IconFit.APPROXIMATE), busy = false))
    }

    @Test
    fun enumBudgetsStayWithinStructuredOutputLimits() {
        // research/openai_api.md §2.3: <= 1000 enum values per schema; one enum of > 250 values must stay <= 15k chars.
        assertTrue(catalog.keys.size <= 600, "keep the catalog under ~600 keys")
        assertTrue(catalog.keys.sumOf { it.length } < 15_000)
        assertEquals(catalog.keys.size, catalog.keys.toSet().size)
    }

    /**
     * research/validate_icon_catalog.py SAMPLES, re-tuned for the C design's 17 kinds (docs/DESIGN_STICKER.md §5.2:
     * Produce wicker picnic, Deli & meat bento box, Baking mixing bowl, Breakfast fruit crate, Dairy & eggs egg
     * basket, Frozen cooler, Household canvas tote, Pantry jar shelf).
     */
    @Test
    fun basketHeuristicMatchesTheSamples() {
        val samples = mapOf(
            "Produce" to "wicker_picnic", "Fruits et légumes" to "wicker_picnic", "Fruits & Légumes" to "wicker_picnic",
            "Fruit" to "berry_punnet", "Bakery" to "bread_basket", "Boulangerie" to "bread_basket",
            "Aisle 2 - Baking & spices" to "mixing_bowl", "Dairy" to "milk_crate", "Produits laitiers" to "milk_crate",
            "Eggs & dairy" to "egg_basket", "Meat & seafood" to "bento_box", "Poissons et fruits de mer" to "cooler",
            "Deli & cheese" to "bento_box", "Frozen" to "cooler", "Surgelés" to "cooler", "Ice cream" to "ice_bucket",
            "Crème glacée" to "ice_bucket", "Aisle 7 - Snacks & pop" to "candy_jar", "Chips & candy" to "candy_jar",
            "Health & beauty" to "vanity_case", "Pharmacy" to "vanity_case", "Pain relief" to "vanity_case",
            "Baby" to "vanity_case", "Pet food" to "wire_basket", "Cleaning & paper" to "canvas_tote",
            "International" to "bento_box", "World foods" to "bento_box", "Flowers" to "flower_bucket",
            "Fleurs" to "flower_bucket", "Cereal & breakfast" to "fruit_crate", "Canned goods & soups" to "jar_shelf",
            "Condiments" to "jar_shelf", "Pasta & rice" to "jar_shelf", "Bulk" to "jar_shelf",
            "Beer & wine" to "bottle_carrier", "Boissons" to "bottle_carrier", "Coffee & tea" to "bottle_carrier",
            "Aisle 12" to "wicker_picnic", "Checkout" to "wicker_picnic", "Seasonal" to "wicker_picnic",
            "Plant-based" to "milk_crate", "Prepared meals" to "bento_box", "Around the store" to "wicker_picnic",
        )
        assertEquals(43, samples.size)
        val wrong = samples.mapNotNull { (name, expected) ->
            val got = catalog.baskets.guess(name).key
            if (got != expected) "$name: expected $expected, got $got" else null
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun basketGuessDefaultsAndEntries() {
        assertEquals(BasketKind.DEFAULT, catalog.baskets.guess(""))
        assertEquals(BasketKind.DEFAULT, catalog.baskets.guess("Aisle 99"))
        BasketKind.entries.forEach { kind -> assertTrue(catalog.baskets.entry(kind) != null, kind.key) }
    }
}

package earth.cora.picnic.core.catalog

import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.Rarity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The catalog's egg tags, hop personalities and the new peameal_bacon key (§12.2, §8.3, §19.1; checklist 301). */
class CatalogTagsTest {
    private val catalog = TestSupport.catalog

    @Test
    fun tags() {
        assertEquals(
            listOf("apple", "orange", "clementine", "lemon", "lime", "watermelon", "mango", "peach", "pear", "kiwi", "tomato").sorted(),
            catalog.keysTagged("roll").sorted(),
            "egg 3: rolling produce",
        )
        assertEquals(listOf("strawberry", "blueberries", "raspberries", "cherries").sorted(), catalog.keysTagged("hop").sorted(), "berries hop")
        assertEquals(
            listOf(
                "watermelon", "lemonade", "strawberry", "sliced_bread", "baguette", "deli_meat", "potato_chips", "cherries", "grapes",
                "picnic_blanket", "paper_plates", "paper_cups", "plastic_cutlery", "sparkling_water", "cookies", "butter_tart",
            ).sorted(),
            catalog.keysTagged("picnic").sorted(),
            "egg 17: the ant parade",
        )
        assertEquals(listOf("cake", "candle"), catalog.keysTagged("birthday").sorted(), "egg 9")
        assertTrue(catalog.keysTagged("frozen").containsAll(listOf("ice_cream", "popsicle", "frozen_pizza", "frozen_vegetables")))
        assertTrue(catalog.keysTagged("frozen").all { catalog.categoryOf(it)?.key == "frozen" })
        assertEquals(listOf("hop", "picnic"), catalog.tagsOf("strawberry"))
        assertTrue(catalog.tagsOf("no_such_key").isEmpty())
        // "fraises" and "pommes" find their key, so the egg fires in any language.
        assertEquals("strawberry", catalog.match("Fraises")?.key)
        assertEquals("apple", catalog.match("Pommes")?.key)
    }

    @Test
    fun personalities() {
        listOf("strawberry", "blueberries", "raspberries", "cherries").forEach { assertEquals(Personality.BERRY, catalog.personalityOf(it), it) }
        assertEquals(Personality.EGG, catalog.personalityOf("eggs"))
        assertEquals(Personality.BREAD, catalog.personalityOf("croissant"))
        assertEquals(Personality.BREAD, catalog.personalityOf("sliced_bread"))
        assertEquals(Personality.CAN, catalog.personalityOf("maple_syrup"), "the maple tin rolls like a can")
        assertEquals(Personality.CAN, catalog.personalityOf("canned_tomatoes"))
        assertEquals(Personality.FROZEN, catalog.personalityOf("ice_cream"))
        assertEquals(Personality.NONE, catalog.personalityOf("lemon"))
        assertEquals(Personality.NONE, catalog.personalityOf(IconCatalog.CAPSULE))
        assertTrue(catalog.icons.filter { it.category == "bakery" }.all { it.personality == Personality.BREAD })
    }

    @Test
    fun peamealBacon() {
        val entry = catalog["peameal_bacon"]!!
        assertEquals("Peameal bacon", entry.label)
        assertEquals("meat", entry.category)
        listOf("Peameal", "peameal bacon", "Back bacon", "Bacon de dos").forEach { assertEquals("peameal_bacon", catalog.match(it)?.key, it) }
        assertEquals("bacon", catalog.match("Bacon")?.key, "plain bacon keeps its strips")
        assertEquals(236, catalog.keys.size)
        assertEquals(216, catalog.collectibleKeys.size, "the album counts the 236 keys minus the 20 generics")
    }

    @Test
    fun groundSpicesAreExactSpiceJars() {
        // The pantry eval's spiced banana bread: terra once called "Ground cinnamon" approximate (a paid drawing).
        listOf("Ground cinnamon", "Ground nutmeg", "Nutmeg", "Allspice", "Ground ginger").forEach {
            assertEquals("spice_jar", catalog.matchExact(it)?.key, it)
        }
        assertEquals("ginger", catalog.matchExact("Ginger")?.key, "fresh ginger root keeps its own sticker")
    }
}

/** The basket kinds and the keyword heuristic (§5.2; checklist 102, 103). */
class BasketCatalogTest {
    private val catalog = TestSupport.catalog

    @Test
    fun mixingBowlKeywords() {
        assertEquals(BasketKind.MIXING_BOWL, BasketKind.fromKey("mixing_bowl"))
        assertTrue(catalog.baskets.entry(BasketKind.MIXING_BOWL)!!.keywords.containsAll(listOf("baking", "bake", "flour", "cuisson", "farine")))
        listOf("Baking", "Aisle 2 - Baking", "Baking supplies", "Flour & sugar", "Cuisson", "Farine et sucre").forEach {
            assertEquals(BasketKind.MIXING_BOWL, catalog.baskets.guess(it), it)
        }
        assertEquals(BasketKind.BREAD_BASKET, catalog.baskets.guess("Bakery"), "a bakery is not baking")
    }

    @Test
    fun fixtureStoreKinds() {
        // Appendix A's store and the §5.2 "Default aisle" column.
        val expected = mapOf(
            "Produce" to BasketKind.WICKER_PICNIC,
            "Bakery" to BasketKind.BREAD_BASKET,
            "Deli & meat" to BasketKind.BENTO_BOX,
            "Aisle 2 - Baking" to BasketKind.MIXING_BOWL,
            "Breakfast" to BasketKind.FRUIT_CRATE,
            "Dairy & eggs" to BasketKind.EGG_BASKET,
            "Frozen" to BasketKind.COOLER,
            "Household" to BasketKind.CANVAS_TOTE,
            "Farmers' market" to BasketKind.BERRY_PUNNET,
            "Dairy" to BasketKind.MILK_CRATE,
            "Drinks" to BasketKind.BOTTLE_CARRIER,
            "Florist" to BasketKind.FLOWER_BUCKET,
            "Snacks" to BasketKind.CANDY_JAR,
            "Pantry" to BasketKind.JAR_SHELF,
            "Pharmacy" to BasketKind.VANITY_CASE,
            "Everything else" to BasketKind.WIRE_BASKET,
        )
        val wrong = expected.mapNotNull { (name, kind) -> catalog.baskets.guess(name).takeIf { it != kind }?.let { "$name: $it" } }
        assertTrue(wrong.isEmpty(), wrong.joinToString())
        assertEquals(BasketKind.WIRE_BASKET, BasketKind.EVERYTHING_ELSE)
    }

    @Test
    fun theAppsDefaultStore() {
        val kinds = Aisles.parse(Aisles.DEFAULT_TEXT).associate { it.name to catalog.baskets.guess(it) }
        assertEquals(
            mapOf(
                "Produce" to BasketKind.WICKER_PICNIC,
                "Bakery" to BasketKind.BREAD_BASKET,
                "Deli & cheese" to BasketKind.BENTO_BOX,
                "Meat & seafood" to BasketKind.BENTO_BOX,
                "Aisle 1 - Breakfast, coffee & tea" to BasketKind.FRUIT_CRATE,
                "Aisle 2 - Baking & spices" to BasketKind.MIXING_BOWL,
                "Aisle 3 - Pasta, rice & international" to BasketKind.JAR_SHELF,
                "Aisle 4 - Canned goods & soups" to BasketKind.JAR_SHELF,
                "Aisle 5 - Snacks & drinks" to BasketKind.CANDY_JAR,
                "Aisle 6 - Household & cleaning" to BasketKind.CANVAS_TOTE,
                "Aisle 7 - Health, beauty & baby" to BasketKind.VANITY_CASE,
                "Dairy & eggs" to BasketKind.EGG_BASKET,
                "Frozen" to BasketKind.COOLER,
                "Bulk & natural foods" to BasketKind.JAR_SHELF,
            ),
            kinds,
        )
    }

    @Test
    fun everyKeywordHasOneOwner() {
        val owners = catalog.file.baskets.flatMap { b -> b.keywords.map { it to b.key } }.groupBy({ it.first }, { it.second })
        assertTrue(owners.filterValues { it.size > 1 }.isEmpty(), owners.filterValues { it.size > 1 }.toString())
    }
}

/** D5: the capsule sentinel (checklist 39). */
class DisplayRefTest {
    private val catalog = TestSupport.catalog
    private val base = GroceryItem(id = "a", name = "Gochujang", icon = IconCatalog.DEFAULT_ICON, iconFit = IconFit.PENDING, addedAt = 0)

    @Test
    fun pendingShowsCapsule() {
        assertEquals(IconRef("_capsule"), catalog.displayRef(base, busy = false))
        assertEquals(IconRef("_capsule"), catalog.displayRef(base.copy(icon = "lemon", iconFit = IconFit.EXACT), busy = true), "drawing: the capsule")
        assertEquals(IconRef("_capsule"), catalog.displayRef(base.copy(rarity = Rarity.JACKPOT), busy = true), "a jackpot's capsule is gold (ItemIcon), same ref")
        assertTrue(IconCatalog.CAPSULE !in catalog, "a display-only sentinel, never a catalog key")
        assertEquals(IconRef("paper_bag"), catalog.displayRef(base.copy(name = "Reusable bags", icon = "paper_bag", iconFit = IconFit.EXACT), busy = false), "paper_bag is a real sticker now")
    }

    @Test
    fun collectKey() {
        assertNull(catalog.collectKey(base), "PENDING is not collected")
        assertNull(catalog.collectKey(base.copy(icon = "condiments_generic", iconFit = IconFit.APPROXIMATE)))
        assertNull(catalog.collectKey(base.copy(icon = "bakery_generic", iconFit = IconFit.EXACT)), "generics are not collectible")
        assertEquals("lemon", catalog.collectKey(base.copy(icon = "lemon", iconFit = IconFit.EXACT)))
        assertEquals("c:c-1", catalog.collectKey(base.copy(icon = "condiments_generic", iconFit = IconFit.APPROXIMATE, customIconId = "c-1")))
    }
}

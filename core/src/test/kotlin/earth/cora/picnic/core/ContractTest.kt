package earth.cora.picnic.core

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.slugify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Skeleton contract checks (docs/ARCHITECTURE.md). Core owns and extends these. */
class ContractTest {
    private val catalog = IconCatalog.load()

    @Test
    fun basketEnumMatchesCatalog() {
        assertEquals(catalog.file.baskets.map { it.key }.toSet(), BasketKind.entries.map { it.key }.toSet())
    }

    @Test
    fun catalogFallbacksAndDefaultExist() {
        assertTrue(IconCatalog.DEFAULT_ICON in catalog)
        catalog.categories.forEach { assertTrue(it.fallbackIcon in catalog, it.fallbackIcon) }
        assertEquals("fruit_generic", catalog.fallbackFor("lemon"))
        assertEquals(IconCatalog.DEFAULT_ICON, catalog.sanitize("no_such_icon"))
    }

    @Test
    fun slugsAndNames() {
        assertEquals("aisle-2-baking-spices", slugify("Aisle 2 - Baking & spices"))
        assertEquals("fruits-et-legumes", slugify("Fruits et légumes"))
        assertEquals("lemon", normalizeName("Lemons"))
        assertEquals("berry", normalizeName("Berries"))
        assertEquals("tomato", normalizeName("Tomatoes"))
        assertEquals("hummus", normalizeName("Hummus"))
        assertEquals("creme fraiche", normalizeName("Crème fraîche"))
    }

    @Test
    fun aisleParsing() {
        val aisles = Aisles.parse("- Produce\n\n# comment\nBakery\nBakery\nbakery 2\nUnsorted\n✿\n")
        assertEquals(
            listOf("produce", "bakery", "bakery-2", "bakery-2-2", "unsorted-aisle", "aisle-6"),
            aisles.map { it.id },
        )
        assertEquals((1..6).toList(), aisles.map { it.position })
        assertEquals(14, Aisles.parse(Aisles.DEFAULT_TEXT).size)
    }

    @Test
    fun stateRoundTrip() {
        val item = GroceryItem(id = "a", name = "Milk", quantity = "4 L bag", aisleId = "dairy-eggs", icon = "milk_bag", iconFit = IconFit.EXACT, addedAt = 1L)
        val state = AppState(lists = listOf(GroceryList("l", "Groceries", 0L, listOf(item), setOf("bakery"))), currentListId = "l")
        val text = CoreJson.json.encodeToString(AppState.serializer(), state)
        assertTrue("\"iconFit\":\"exact\"" in text, text)
        assertEquals(state, CoreJson.json.decodeFromString(AppState.serializer(), text))
    }
}

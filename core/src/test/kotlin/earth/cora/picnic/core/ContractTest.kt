package earth.cora.picnic.core

import earth.cora.picnic.core.assistant.IconDecision
import earth.cora.picnic.core.assistant.resolve
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.core.llm.ModelDefaults
import earth.cora.picnic.core.llm.ReasoningEffort
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconOverride
import earth.cora.picnic.core.model.ResolvedIcon
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.slugify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertEquals("fruit_generic", catalog.fallbackFor("fruit_generic"))
        assertEquals(IconCatalog.DEFAULT_ICON, catalog.fallbackFor(IconCatalog.DEFAULT_ICON))
        assertEquals(IconCatalog.DEFAULT_ICON, catalog.sanitize("no_such_icon"))
        assertEquals("condiments_generic", catalog.fallbackForCategory("condiments"))
        assertTrue(catalog.decidableKeys.none { catalog.isGeneric(it) })
        assertEquals(catalog.keys.size - catalog.categories.size, catalog.decidableKeys.size)
    }

    @Test
    fun iconDecisionResolution() {
        assertEquals(ResolvedIcon("lemon", IconFit.EXACT), IconDecision("lemon", "fruit").resolve(catalog))
        assertEquals(ResolvedIcon("condiments_generic", IconFit.APPROXIMATE), IconDecision(null, "condiments").resolve(catalog))
        assertEquals(ResolvedIcon("condiments_generic", IconFit.APPROXIMATE), IconDecision("condiments_generic", "condiments").resolve(catalog))
        assertEquals(ResolvedIcon(IconCatalog.DEFAULT_ICON, IconFit.APPROXIMATE), IconDecision.NONE.resolve(catalog))
    }

    @Test
    fun modelDefaultsAreTheOnePlace() {
        assertEquals("gpt-5.6-terra", ModelDefaults.SMART.id)
        assertEquals(ReasoningEffort.LOW, ModelDefaults.SMART.effort)
        assertEquals("gpt-6-luna", ModelDefaults.FAST.id)
        assertEquals(ReasoningEffort.NONE, ModelDefaults.FAST.effort)
        assertEquals("gpt-transcribe", ModelDefaults.TRANSCRIBE.id)
        assertEquals("gpt-image-2.5-flare", ModelDefaults.IMAGE.id)
        assertEquals("transparent", ModelDefaults.IMAGE.background)
        assertEquals(ModelDefaults.SMART, ModelDefaults.models.smart)
    }

    @Test
    fun baseUrlNormalization() {
        assertEquals("https://picnic.cora.earth/v1", LlmConfig.normalizeBaseUrl("  https://picnic.cora.earth/v1/ "))
        assertEquals("http://127.0.0.1:8787/v1", LlmConfig.normalizeBaseUrl("http://127.0.0.1:8787/v1"))
        assertNull(LlmConfig.normalizeBaseUrl("http://picnic.cora.earth/v1"))
        assertNull(LlmConfig.normalizeBaseUrl("picnic.cora.earth"))
        assertNull(LlmConfig.normalizeBaseUrl(""))
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
        val pending = item.copy(id = "b", name = "Gochujang", icon = IconCatalog.DEFAULT_ICON, iconFit = IconFit.PENDING)
        val state = AppState(
            lists = listOf(GroceryList("l", "Groceries", 0L, listOf(item, pending), setOf("bakery"))),
            currentListId = "l",
            iconOverrides = listOf(IconOverride("oat milk", "plant_milk", 2L)),
        )
        val text = CoreJson.json.encodeToString(AppState.serializer(), state)
        assertTrue("\"iconFit\":\"exact\"" in text, text)
        assertTrue("\"iconFit\":\"pending\"" in text, text)
        assertEquals(state, CoreJson.json.decodeFromString(AppState.serializer(), text))
    }

    @Test
    fun minimalStateFileDecodes() {
        // Fields added later (iconOverrides, collapsedAisles, ...) must default when absent.
        val text = """{"lists":[{"id":"l","title":"Groceries","createdAt":0,"items":[{"id":"a","name":"Milk","icon":"milk_bag","addedAt":1}]}],"currentListId":"l"}"""
        val state = CoreJson.json.decodeFromString(AppState.serializer(), text)
        assertEquals(1, state.version)
        assertTrue(state.iconOverrides.isEmpty())
        assertEquals(IconFit.EXACT, state.lists.single().items.single().iconFit)
    }
}

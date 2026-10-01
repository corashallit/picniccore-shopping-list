package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.model.setAislesText
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.core.store.StateStore
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AisleSyncTest {
    private val catalog = TestSupport.catalog

    /** Scripted assistant: places items by a name -> aisle table; baskets from a table. */
    private class FakeAssistant(
        val placements: Map<String, String?> = emptyMap(),
        val baskets: Map<String, BasketKind> = emptyMap(),
        val recategorizeError: LlmError? = null,
        val basketsError: LlmError? = null,
    ) : GroceryAssistant {
        val recategorized = mutableListOf<List<NamedItem>>()

        override suspend fun recategorize(items: List<NamedItem>, aisles: List<Aisle>, corrections: List<Correction>): Map<String, String?> {
            recategorizeError?.let { throw it }
            recategorized += items
            return items.associate { it.id to placements[it.name] }
        }

        override suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind> {
            basketsError?.let { throw it }
            return baskets.filterKeys { id -> aisles.any { it.id == id } }
        }

        override suspend fun ingest(input: IngestInput, context: IngestContext) = error("unused")
        override suspend fun transcribe(clip: AudioClip, keywords: List<String>) = error("unused")
        override suspend fun drawIcon(request: IconDrawRequest) = error("unused")
        override suspend fun testConnection() = error("unused")
    }

    private fun item(id: String, name: String, aisle: String? = null, manual: Boolean = false) =
        GroceryItem(id = id, name = name, aisleId = aisle, icon = "paper_bag", addedAt = 0, manuallyPlaced = manual)

    private fun state(vararg items: GroceryItem) = AppState(
        lists = listOf(GroceryList("l", "Groceries", 0, items.toList())),
        currentListId = "l",
        store = StoreProfile(aislesText = "Produce\nBakery\nFrozen"),
    )

    @Test
    fun syncAssignsBasketsThenPlacesItemsNeedingAnAisle() = runTest {
        val store = InMemoryStateStore(state(item("a", "Lemons"), item("b", "Croissant", "gone"), item("c", "Mystery"), item("d", "Bread", "bakery")))
        val assistant = FakeAssistant(
            placements = mapOf("Lemons" to "produce", "Croissant" to "bakery"),
            baskets = mapOf("produce" to BasketKind.BERRY_PUNNET),
        )
        val report = AisleSync(store, assistant, catalog.baskets).sync()
        assertTrue(report.basketsFromModel)
        assertEquals(2, report.recategorized)
        assertEquals(1, report.leftUnsorted)
        assertNull(report.error)
        val s = store.current()
        assertEquals("produce", s.findItem("a")!!.aisleId)
        assertEquals("bakery", s.findItem("b")!!.aisleId)
        assertNull(s.findItem("c")!!.aisleId)
        assertEquals(
            mapOf("produce" to BasketKind.BERRY_PUNNET, "bakery" to BasketKind.BREAD_BASKET, "frozen" to BasketKind.ICE_BUCKET),
            s.store.aisleBaskets,
            "missing model baskets fall back to the heuristic",
        )
        assertEquals(listOf("a", "b", "c"), assistant.recategorized.single().map { it.id }, "bread already has an aisle")
    }

    @Test
    fun offlineSyncKeepsHeuristicBasketsAndReportsTheError() = runTest {
        val store = InMemoryStateStore(state(item("a", "Lemons")).setAislesText("Produce\nFleurs") { BasketKind.WICKER_PICNIC })
        val offline = LlmError.Network(IOException("offline"))
        val report = AisleSync(store, FakeAssistant(recategorizeError = offline, basketsError = offline), catalog.baskets).sync()
        assertFalse(report.basketsFromModel)
        assertIs<LlmError.Network>(report.error)
        assertEquals(1, report.leftUnsorted)
        assertEquals(BasketKind.FRUIT_CRATE, store.current().store.aisleBaskets["produce"])
        assertEquals(BasketKind.FLOWER_BUCKET, store.current().store.aisleBaskets["fleurs"])
        assertNull(store.current().findItem("a")!!.aisleId)
    }

    @Test
    fun sortItemsOnlyTouchesTheGivenIdsAndSkipsPlacedOnes() = runTest {
        val store = InMemoryStateStore(state(item("a", "Lemons"), item("b", "Croissant"), item("c", "Bread", "bakery"), item("d", "Glitter", manual = true)))
        val assistant = FakeAssistant(placements = mapOf("Lemons" to "produce", "Croissant" to "bakery"))
        val report = AisleSync(store, assistant, catalog.baskets).sortItems(listOf("a", "c", "d", "zzz"))
        assertEquals(1, report.recategorized)
        assertEquals(0, report.leftUnsorted)
        assertEquals(listOf("a"), assistant.recategorized.single().map { it.id })
        assertNull(store.current().findItem("b")!!.aisleId, "not asked about")
        assertEquals(AisleSyncReport(false, 0, 0, null), AisleSync(store, assistant, catalog.baskets).sortItems(emptyList()))
    }

    @Test
    fun saveFailuresAreReportedNotThrown() = runTest {
        val inner = InMemoryStateStore(state(item("a", "Lemons")))
        val failing = object : StateStore by inner {
            override suspend fun update(transform: (AppState) -> AppState): AppState = throw IOException("disk full")
        }
        val report = AisleSync(failing, FakeAssistant(placements = mapOf("Lemons" to "produce")), catalog.baskets).sortItems(listOf("a"))
        assertIs<IOException>(report.saveError)
        assertEquals(1, report.leftUnsorted)
    }
}

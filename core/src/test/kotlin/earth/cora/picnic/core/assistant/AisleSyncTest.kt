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
        var recategorizeError: LlmError? = null,
        val basketsError: LlmError? = null,
    ) : GroceryAssistant {
        val recategorized = mutableListOf<List<NamedItem>>()
        val correctionsSent = mutableListOf<List<Correction>>()

        override suspend fun recategorize(items: List<NamedItem>, aisles: List<Aisle>, corrections: List<Correction>): Map<String, String?> {
            recategorizeError?.let { throw it }
            recategorized += items
            correctionsSent += corrections
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
            mapOf("produce" to BasketKind.BERRY_PUNNET, "bakery" to BasketKind.BREAD_BASKET, "frozen" to BasketKind.COOLER),
            s.store.aisleBaskets,
            "missing model baskets fall back to the heuristic",
        )
        assertEquals(listOf("a", "b", "c"), assistant.recategorized.single().map { it.id }, "bread already has an aisle")
    }

    @Test
    fun offlineSyncKeepsHeuristicBasketsAndReportsTheError() = runTest {
        val store = InMemoryStateStore(state(item("a", "Lemons")).setAislesText("Produce\nFleurs", { BasketKind.WICKER_PICNIC }, 0L))
        val offline = LlmError.Network(IOException("offline"))
        val report = AisleSync(store, FakeAssistant(recategorizeError = offline, basketsError = offline), catalog.baskets).sync()
        assertFalse(report.basketsFromModel)
        assertIs<LlmError.Network>(report.error)
        assertEquals(1, report.leftUnsorted)
        assertEquals(BasketKind.WICKER_PICNIC, store.current().store.aisleBaskets["produce"])
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

    /**
     * Catch-up (§0.4, 5 October): every list's items still needing an aisle are sorted in one call, with the learned
     * corrections sent along exactly as sortItems sends them; items she placed by hand (in an aisle, or deliberately in
     * Everything else) and items with an aisle are never asked about. No baskets.
     */
    @Test
    fun sortPendingSortsEveryListAndKeepsHerPlacements() = runTest {
        val correction = Correction(name = "lemons", aisleId = "produce", at = 5)
        val start = state(item("a", "Lemons"), item("c", "Glitter", manual = true), item("d", "Bread", "bakery", manual = true)).let { s ->
            s.copy(
                lists = s.lists + GroceryList("l2", "Big shop", 0, listOf(item("b", "Croissant", "gone"), item("e", "Peas", "frozen"))),
                corrections = listOf(correction),
            )
        }
        val store = InMemoryStateStore(start)
        val assistant = FakeAssistant(placements = mapOf("Lemons" to "produce", "Croissant" to "bakery"))
        val report = AisleSync(store, assistant, catalog.baskets).sortPending()
        assertEquals(2, report.recategorized)
        assertEquals(0, report.leftUnsorted)
        assertNull(report.error)
        assertEquals(listOf("a", "b"), assistant.recategorized.single().map { it.id }, "one call, both lists")
        assertEquals(listOf(correction), assistant.correctionsSent.single(), "learned corrections go along")
        val s = store.current()
        assertEquals("produce", s.findItem("a")!!.aisleId)
        assertEquals("bakery", s.findItem("b")!!.aisleId)
        assertNull(s.findItem("c")!!.aisleId, "her Everything else stays")
        assertEquals("bakery", s.findItem("d")!!.aisleId)
        assertEquals(start.store.aisleBaskets, s.store.aisleBaskets, "no baskets")
    }

    /** Offline nothing moves and the error is reported; the next catch-up asks again and places them. */
    @Test
    fun sortPendingOfflineMovesNothingAndTheNextOneSorts() = runTest {
        val store = InMemoryStateStore(state(item("a", "Lemons"), item("b", "Croissant")))
        val assistant = FakeAssistant(placements = mapOf("Lemons" to "produce", "Croissant" to "bakery"), recategorizeError = LlmError.Offline())
        val sync = AisleSync(store, assistant, catalog.baskets)
        val offline = sync.sortPending()
        assertIs<LlmError.Offline>(offline.error)
        assertEquals(0, offline.recategorized)
        assertEquals(2, offline.leftUnsorted)
        assertNull(store.current().findItem("a")!!.aisleId)

        assistant.recategorizeError = null
        val online = sync.sortPending()
        assertEquals(2, online.recategorized)
        assertEquals("produce", store.current().findItem("a")!!.aisleId)
    }

    /**
     * What the model already answered (an item it left in Everything else) is not sent again at the next catch-up of the
     * same process, so a connection that comes and goes never repeats the call; a change of aisles asks again.
     */
    @Test
    fun sortPendingDoesNotRepeatWhatTheModelAnswered() = runTest {
        val store = InMemoryStateStore(state(item("f", "Batteries")))
        val assistant = FakeAssistant(placements = emptyMap())
        val sync = AisleSync(store, assistant, catalog.baskets)
        assertEquals(1, sync.sortPending().leftUnsorted)
        assertEquals(AisleSyncReport(false, 0, 0, null), sync.sortPending())
        assertEquals(1, assistant.recategorized.size, "asked once")
        sync.sync()
        assertEquals(2, assistant.recategorized.size, "the aisles changed: asked again")
        sync.sortItems(listOf("f"))
        assertEquals(3, assistant.recategorized.size, "a refresh she asks for always asks")
    }

    @Test
    fun resortAll() = runTest {
        val start = state(
            item("a", "Lemons", "bakery"), // misfiled: re-sorted
            item("b", "Croissant"), // Everything else: sorted
            item("c", "Bread", "bakery", manual = true), // placed by hand: stays, not asked
            item("d", "Glitter", manual = true), // deliberately Everything else: stays, not asked
            item("e", "Mystery", "frozen"), // the model cannot place it: keeps its aisle
            item("f", "Thing"), // the model cannot place it: stays in Everything else
            item("g", "Peas", "gone", manual = true), // placed by hand into a vanished aisle: re-sorted
        ).let { s -> s.copy(lists = s.lists + GroceryList("l2", "Party", 0, listOf(item("h", "Ice", "produce").copy(checked = true)))) }
        val store = InMemoryStateStore(start)
        // "Mystery" comes back filed under an aisle that is gone (renamed while the model answered): it keeps its aisle.
        val assistant = FakeAssistant(placements = mapOf("Lemons" to "produce", "Croissant" to "bakery", "Peas" to "frozen", "Ice" to "frozen", "Mystery" to "deli"))
        val report = AisleSync(store, assistant, catalog.baskets).resortAll()
        assertEquals(listOf("a", "b", "e", "f", "g", "h"), assistant.recategorized.single().map { it.id }, "every item not placed by hand, every list, checked or not")
        val s = store.current()
        assertEquals("produce", s.findItem("a")!!.aisleId)
        assertEquals("bakery", s.findItem("b")!!.aisleId)
        assertEquals("bakery", s.findItem("c")!!.aisleId)
        assertNull(s.findItem("d")!!.aisleId)
        assertEquals("frozen", s.findItem("e")!!.aisleId, "a re-sort never empties a good aisle into Everything else")
        assertNull(s.findItem("f")!!.aisleId)
        assertEquals("frozen", s.findItem("g")!!.aisleId)
        assertEquals("frozen", s.findItem("h")!!.aisleId)
        assertEquals(AisleSyncReport(false, recategorized = 5, leftUnsorted = 1, error = null), report, "Sorted 5 items · 1 left in Everything else")

        val offline = AisleSync(InMemoryStateStore(start), FakeAssistant(recategorizeError = LlmError.Network(IOException("x"))), catalog.baskets).resortAll()
        assertIs<LlmError.Network>(offline.error)
        assertEquals(0, offline.recategorized, "nothing moved, so nothing counts as sorted")
        assertEquals(3, offline.leftUnsorted, "Croissant, Thing and Peas (vanished aisle) are in Everything else")
        assertEquals(AisleSyncReport(false, 0, 0, null), AisleSync(InMemoryStateStore(state(item("c", "Bread", "bakery", manual = true))), assistant, catalog.baskets).resortAll())
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

    /** An answer that could not be saved does not count as answered: the next catch-up asks again and places the item. */
    @Test
    fun sortPendingWhoseSaveFailedAsksAgain() = runTest {
        val inner = InMemoryStateStore(state(item("a", "Lemons")))
        var full = true
        val flaky = object : StateStore by inner {
            override suspend fun update(transform: (AppState) -> AppState): AppState =
                if (full) throw IOException("disk full") else inner.update(transform)
        }
        val assistant = FakeAssistant(placements = mapOf("Lemons" to "produce"))
        val sync = AisleSync(flaky, assistant, catalog.baskets)
        assertIs<IOException>(sync.sortPending().saveError)
        assertNull(inner.current().findItem("a")!!.aisleId)

        full = false
        val again = sync.sortPending()
        assertEquals(2, assistant.recategorized.size, "asked again")
        assertEquals(1, again.recategorized)
        assertEquals("produce", inner.current().findItem("a")!!.aisleId)
    }
}

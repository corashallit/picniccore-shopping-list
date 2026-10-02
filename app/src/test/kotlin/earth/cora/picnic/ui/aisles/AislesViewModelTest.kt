package earth.cora.picnic.ui.aisles

import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.common.testAppScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AislesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val baskets = IconCatalog.default.baskets

    private class Fixture(val vm: AislesViewModel, val store: InMemoryStateStore, val assistant: FakeAssistant, val events: List<AislesEvent>)

    private fun TestScope.fixture(assistant: FakeAssistant = FakeAssistant()): Fixture {
        val store = InMemoryStateStore(Samples.shoppingState)
        val vm = AislesViewModel(store, AisleSync(store, assistant, baskets), baskets, testAppScope())
        val events = mutableListOf<AislesEvent>()
        testAppScope().launch { vm.events.collect { events += it } }
        return Fixture(vm, store, assistant, events)
    }

    @Test
    fun previewParsesTheDraftLiveWithBasketsAndCounts() = runTest {
        val f = fixture()
        advanceUntilIdle()
        val initial = f.vm.state.first { !it.loading }
        assertEquals("No Frills on Bank St", initial.storeName)
        assertEquals(14, initial.parsed.size)
        assertFalse(initial.dirty)
        assertEquals(3, initial.parsed.first { it.aisle.id == "produce" }.itemCount)
        assertEquals("Dish rack and candles have no aisle", 2, initial.needingAisle)

        f.vm.setText("Produce\nBakery\n# a note\nFlowers")
        advanceUntilIdle()
        val s = f.vm.state.value
        assertTrue(s.dirty)
        assertEquals(listOf("produce", "bakery", "flowers"), s.parsed.map { it.aisle.id })
        assertEquals(BasketKind.FLOWER_BUCKET, s.parsed.last().basket)
        assertEquals("nothing saved yet", Samples.shoppingState.store.aislesText, f.store.current().store.aislesText)

        f.vm.discard()
        advanceUntilIdle()
        assertFalse(f.vm.state.value.dirty)
        assertEquals(14, f.vm.state.value.parsed.size)
    }

    @Test
    fun saveStoresTheTextThenSyncsBasketsAndVanishedAisles() = runTest {
        val assistant = FakeAssistant(
            assignBaskets = { aisles -> aisles.associate { it.id to BasketKind.CANVAS_TOTE } },
            recategorize = { items, _ -> items.associate { it.id to "pantry" } },
        )
        val f = fixture(assistant)
        advanceUntilIdle()
        f.vm.setStoreName("  Loblaws  ")
        f.vm.setText("Produce\nPantry")
        f.vm.save()
        advanceUntilIdle()

        val saved = f.store.current()
        assertEquals("Loblaws", saved.store.name)
        assertEquals("Produce\nPantry", saved.store.aislesText)
        assertEquals(mapOf("produce" to BasketKind.CANVAS_TOTE, "pantry" to BasketKind.CANVAS_TOTE), saved.store.aisleBaskets)
        // Items whose aisle vanished were placed by the fast model; produce items kept theirs.
        assertEquals("pantry", saved.findItem("i-flour")!!.aisleId)
        assertEquals("produce", saved.findItem("i-lemons")!!.aisleId)

        val s = f.vm.state.value
        assertFalse(s.dirty)
        assertFalse(s.syncing)
        val report = (f.events.single() as AislesEvent.Synced).report
        assertTrue(report.basketsFromModel)
        assertEquals(report, s.lastReport)
        assertEquals(0, report.leftUnsorted)
    }

    @Test
    fun resortWithoutEditsJustSyncsAndReportsErrors() = runTest {
        val f = fixture(FakeAssistant(
            assignBaskets = { throw LlmError.NotConfigured("no token") },
            recategorize = { _, _ -> throw LlmError.NotConfigured("no token") },
        ))
        advanceUntilIdle()
        f.vm.resort()
        advanceUntilIdle()
        val report = (f.events.single() as AislesEvent.Synced).report
        assertTrue(report.error is LlmError.NotConfigured)
        assertEquals(2, report.leftUnsorted)
        assertNull(f.store.current().findItem("i-rack")!!.aisleId)
        assertFalse(f.vm.state.value.syncing)
    }
}

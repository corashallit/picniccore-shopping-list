package earth.cora.picnic.ui.list

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.SectionIds
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.RecordingIconRepository
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.common.testAppScope
import earth.cora.picnic.ui.common.ScreenText
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalog = IconCatalog.default
    private var nextId = 0

    private class Fixture(val vm: ListViewModel, val store: StateStore, val icons: RecordingIconRepository)

    private fun TestScope.fixture(
        state: AppState = Samples.shoppingState,
        store: StateStore = InMemoryStateStore(state),
        busy: Set<String> = emptySet(),
        token: String? = "sk-test-0123456789abcdef",
    ): Fixture {
        val icons = RecordingIconRepository(busy)
        val vm = ListViewModel(
            store = store,
            icons = icons,
            settings = InMemorySettingsRepository(storedToken = token),
            catalog = catalog,
            clock = { Samples.NOW + 1_000 },
            newId = { "new-${nextId++}" },
            appScope = testAppScope(),
        )
        backgroundScope.launch { vm.state.collect {} }
        return Fixture(vm, store, icons)
    }

    private suspend fun ListViewModel.loaded() = state.first { !it.loading }

    @Test
    fun sectionsFollowTheWalkingOrderWithUnsortedFirstAndCheckedLast() = runTest {
        val f = fixture(busy = setOf("gochujang"))
        advanceUntilIdle()
        val s = f.vm.loaded()

        assertEquals("Groceries", s.title)
        assertEquals(15, s.total)
        assertEquals(12, s.remaining)
        assertEquals(
            listOf(SectionIds.UNSORTED, "produce", "bakery", "aisle-1-breakfast-coffee-tea", "aisle-2-baking-spices",
                "aisle-3-pasta-rice-international", "dairy-eggs", SectionIds.CHECKED),
            s.sections.map { it.id },
        )
        val unsorted = s.sections.first()
        assertEquals(SectionUiKind.UNSORTED, unsorted.kind)
        assertNull(unsorted.number)
        assertEquals(-1, unsorted.aisleIndex)

        val bakery = s.sections.first { it.id == "bakery" }
        assertTrue("all bakery items are checked", bakery.cleared)
        assertEquals(0, bakery.count)
        assertEquals(2, bakery.gotten)
        assertEquals("a cleared aisle's basket is full", listOf(IconRef("baguette"), IconRef("bagel")), bakery.inBasket)
        assertNull("no store number in \"Bakery\": the medallion shows a star", bakery.number)
        assertEquals(1, bakery.aisleIndex)
        assertEquals("the store's own number, not the walking position", 2, s.sections.first { it.id == "aisle-2-baking-spices" }.number)

        val dairy = s.sections.first { it.id == "dairy-eggs" }
        assertTrue(dairy.collapsed)
        assertEquals(4, dairy.count)
        assertEquals(0, dairy.gotten)
        assertTrue("nothing gotten yet: the basket is empty, never the items still to get", dairy.inBasket.isEmpty())

        val checked = s.sections.last()
        assertEquals(listOf("Baguette", "Bagels", "Coffee beans"), checked.items.map { it.name })
        assertEquals(0, checked.gotten)
        assertEquals(listOf(IconRef("baguette"), IconRef("bagel"), IconRef("coffee")), checked.inBasket)

        val avocado = s.sections.first { it.id == "produce" }.items.first { it.name == "Avocados" }
        assertEquals("3 · ripe ones", avocado.detail)

        // Busy names and PENDING items show the gashapon capsule (D5); busy ones add the sparkle.
        val gochujang = s.sections.first { it.id == "aisle-3-pasta-rice-international" }.items.single()
        assertTrue(gochujang.drawing)
        assertEquals(IconRef(IconCatalog.CAPSULE), gochujang.icon)
        val rack = unsorted.items.first { it.name == "Dish rack" }
        assertFalse(rack.drawing)
        assertEquals(IconRef(IconCatalog.CAPSULE), rack.icon)

        assertEquals(listOf("Groceries" to true, "Saturday party" to false), s.lists.map { it.title to it.current })
        assertEquals(12, s.lists.first().remaining)
    }

    @Test
    fun aCollapsedBasketFillsWithWhatIsGottenAndEmptiesWhenUnticked() = runTest {
        val f = fixture(busy = setOf("milk"))
        f.vm.toggle("i-eggs")
        f.vm.toggle("i-butter")
        advanceUntilIdle()
        var dairy = f.vm.loaded().sections.first { it.id == "dairy-eggs" }
        assertTrue(dairy.collapsed)
        assertEquals("the badge still counts what is left to get", 2, dairy.count)
        assertEquals(2, dairy.gotten)
        assertEquals("gotten items only, oldest first", listOf(IconRef("eggs"), IconRef("butter")), dairy.inBasket)
        assertEquals(listOf("Milk", "Greek yogurt"), dairy.items.map { it.name })

        // A busy (drawing) item in the basket shows its display ref, like its row.
        f.vm.toggle("i-milk")
        advanceUntilIdle()
        dairy = f.vm.loaded().sections.first { it.id == "dairy-eggs" }
        assertEquals(catalog.displayRef(f.store.current().findItem("i-milk")!!, busy = true), dairy.inBasket.first())

        f.vm.toggle("i-eggs")
        f.vm.toggle("i-butter")
        f.vm.toggle("i-milk")
        advanceUntilIdle()
        dairy = f.vm.loaded().sections.first { it.id == "dairy-eggs" }
        assertTrue(dairy.inBasket.isEmpty())
        assertEquals(4, dairy.count)
    }

    @Test
    fun toggleChecksAnItemAndMovesItToTheBasket() = runTest {
        val f = fixture()
        f.vm.toggle("i-lemons")
        advanceUntilIdle()
        assertTrue(f.store.current().findItem("i-lemons")!!.checked)
        val s = f.vm.loaded()
        assertTrue(s.sections.last().items.any { it.id == "i-lemons" })
        assertEquals(11, s.remaining)
    }

    @Test
    fun deleteEmitsAnUndoableEventAndUndoRestoresThePosition() = runTest {
        val f = fixture()
        val events = mutableListOf<ListEvent>()
        testAppScope().launch { f.vm.events.collect { events += it } }
        val before = f.store.current().currentList().items.map { it.id }

        f.vm.delete("i-flour")
        advanceUntilIdle()
        assertNull(f.store.current().findItem("i-flour"))
        val deleted = events.single() as ListEvent.Deleted
        assertEquals("All-purpose flour", deleted.item.name)
        assertEquals(before.indexOf("i-flour"), deleted.index)
        assertEquals(Samples.LIST_ID, deleted.listId)

        f.vm.undoDelete(deleted.item, deleted.index, deleted.listId)
        advanceUntilIdle()
        assertEquals(before, f.store.current().currentList().items.map { it.id })
    }

    @Test
    fun clearCheckedCanBeUndone() = runTest {
        val f = fixture()
        val events = mutableListOf<ListEvent>()
        testAppScope().launch { f.vm.events.collect { events += it } }
        val before = f.store.current().currentList().items.map { it.id }

        f.vm.clearChecked()
        advanceUntilIdle()
        assertTrue(f.store.current().currentList().items.none { it.checked })
        val cleared = events.single() as ListEvent.Cleared
        assertEquals(3, cleared.removed.size)

        f.vm.undoClear(cleared.removed, cleared.listId)
        advanceUntilIdle()
        assertEquals(before, f.store.current().currentList().items.map { it.id })
    }

    @Test
    fun toggleSectionPersistsCollapseStatePerList() = runTest {
        val f = fixture()
        f.vm.toggleSection("produce")
        f.vm.toggleSection("dairy-eggs")
        advanceUntilIdle()
        assertEquals(setOf("produce"), f.store.current().currentList().collapsedAisles)
        assertTrue(f.vm.loaded().sections.first { it.id == "produce" }.collapsed)
        // The other list is untouched.
        assertTrue(f.store.current().list(Samples.PARTY_ID)!!.collapsedAisles.isEmpty())
    }

    @Test
    fun editSheetStateShowsAislesIconChoicesAndDrawAccess() = runTest {
        val f = fixture(token = null)
        f.vm.openEdit("i-lemons")
        advanceUntilIdle()
        val editing = f.vm.loaded().editing!!
        assertEquals("Lemons", editing.item.name)
        assertEquals("produce", editing.currentAisleId)
        assertEquals(14, editing.aisles.size)
        assertFalse("no token, no drawing", editing.canDraw)
        // The item's category (fruit) comes first, and every catalog key is offered.
        assertEquals("fruit", catalog[editing.iconChoices.first()]!!.category)
        assertEquals(catalog.keys.toSet(), editing.iconChoices.toSet())

        f.vm.closeEdit()
        advanceUntilIdle()
        assertNull(f.vm.loaded().editing)
    }

    @Test
    fun renameReResolvesTheIconAndSubmitsItToTheCascade() = runTest {
        val f = fixture()
        f.vm.openEdit("i-gochujang")
        f.vm.saveEdit("Doenjang", "1 tub", "  ")
        advanceUntilIdle()

        val item = f.store.current().findItem("i-gochujang")!!
        assertEquals("Doenjang", item.name)
        assertEquals("1 tub", item.quantity)
        assertNull("blank note clears it", item.note)
        assertEquals(IconFit.PENDING, item.iconFit)
        assertEquals(listOf("Doenjang"), f.icons.submitted.single().map { it.name })
        assertNull("saving closes the sheet", f.vm.loaded().editing)
    }

    @Test
    fun editWithoutRenameDoesNotTouchTheIconCascade() = runTest {
        val f = fixture()
        f.vm.openEdit("i-milk")
        f.vm.saveEdit("milk", "2 bags", "")
        advanceUntilIdle()
        assertEquals("2 bags", f.store.current().findItem("i-milk")!!.quantity)
        assertTrue(f.icons.submitted.isEmpty())
    }

    @Test
    fun moveToAisleRecordsALearnedCorrection() = runTest {
        val f = fixture()
        f.vm.moveToAisle("i-candles", "aisle-2-baking-spices")
        advanceUntilIdle()
        val state = f.store.current()
        val item = state.findItem("i-candles")!!
        assertEquals("aisle-2-baking-spices", item.aisleId)
        assertTrue(item.manuallyPlaced)
        assertEquals("birthday candle", state.corrections.single().name)
    }

    @Test
    fun setIconRecordsAnOverrideAndForgetsTheBespokeIcon() = runTest {
        val f = fixture()
        f.vm.setIcon("i-rack", "household_generic")
        advanceUntilIdle()
        val state = f.store.current()
        assertEquals("household_generic", state.findItem("i-rack")!!.icon)
        assertEquals(IconFit.EXACT, state.findItem("i-rack")!!.iconFit)
        assertEquals("dish rack", state.iconOverrides.single().name)
        assertEquals(listOf("Dish rack"), f.icons.forgotten)
    }

    @Test
    fun redrawAsksTheIconRepository() = runTest {
        val f = fixture()
        f.vm.redrawIcon("i-gochujang")
        advanceUntilIdle()
        assertEquals(listOf("Gochujang"), f.icons.redrawn)
    }

    @Test
    fun listsCanBeCreatedSwitchedRenamedAndDeleted() = runTest {
        val f = fixture()
        f.vm.newList("Costco run")
        advanceUntilIdle()
        assertEquals("Costco run", f.vm.loaded().title)
        assertEquals("new-0", f.store.current().currentListId)

        f.vm.renameList("new-0", "Costco")
        f.vm.switchList(Samples.PARTY_ID)
        advanceUntilIdle()
        assertEquals("Saturday party", f.vm.loaded().title)
        assertEquals("Costco", f.store.current().list("new-0")!!.title)

        f.vm.deleteList("new-0")
        advanceUntilIdle()
        assertEquals(2, f.store.current().lists.size)
    }

    @Test
    fun aFailedSaveTellsTheUser() = runTest {
        val failing = object : StateStore by InMemoryStateStore(Samples.shoppingState) {
            override suspend fun update(transform: (AppState) -> AppState): AppState = throw IOException("disk full")
        }
        val f = fixture(store = failing)
        val events = mutableListOf<ListEvent>()
        testAppScope().launch { f.vm.events.collect { events += it } }
        f.vm.delete("i-lemons")
        advanceUntilIdle()
        assertEquals(listOf<ListEvent>(ListEvent.Message(ScreenText.SAVE_FAILED)), events)
    }
}

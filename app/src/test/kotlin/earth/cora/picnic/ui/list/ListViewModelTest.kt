package earth.cora.picnic.ui.list

import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.InMemoryAlbumStore
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.RecordingIconRepository
import earth.cora.picnic.ui.common.ScreenText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The list's view model without a screen (docs/DESIGN_STICKER.md §6.3, §6.4, §7.2; ARCHITECTURE §10.3): the Edit
 * sheet's state and saves, Move to aisle and Change sticker as learned corrections, lists created, switched, renamed
 * and deleted (a book filed first, and a list kept when its book cannot be filed), and a failed save told.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private var nextId = 0

    private class Fixture(val vm: ListViewModel, val store: StateStore, val icons: RecordingIconRepository, val album: AlbumStore)

    private fun TestScope.fixture(
        state: AppState = ListFixture.state(),
        store: StateStore = InMemoryStateStore(state),
        album: AlbumStore = InMemoryAlbumStore(),
        token: String? = "sk-test-0123456789abcdef",
    ): Fixture {
        val icons = RecordingIconRepository()
        val vm = ListViewModel(
            store = store,
            album = album,
            icons = icons,
            settings = InMemorySettingsRepository(storedToken = token),
            catalog = ListFixture.catalog,
            clock = { ListFixture.NOW + 1_000 },
            newId = { "new-${nextId++}" },
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
        backgroundScope.launch { vm.state.collect {} }
        return Fixture(vm, store, icons, album)
    }

    private suspend fun ListViewModel.loaded() = state.first { !it.loading }

    @Test
    fun editSheetStateShowsAislesIconChoicesAndDrawAccess() = runTest {
        val f = fixture(token = null)
        f.vm.openEdit("baking-powder")
        advanceUntilIdle()
        val editing = f.vm.loaded().editing!!
        assertEquals("Baking powder", editing.item.name)
        assertEquals("aisle-2-baking", editing.currentAisleId)
        assertEquals("the store's eight aisles", 8, editing.aisles.size)
        assertFalse("no key, no drawing", editing.canDraw)
        // The item's own category comes first, and every catalog key is offered.
        val category = ListFixture.catalog.categoryOf(editing.item.icon)?.key
        assertEquals(category, ListFixture.catalog[editing.iconChoices.first()]!!.category)
        assertEquals(ListFixture.catalog.keys.toSet(), editing.iconChoices.toSet())
        f.vm.closeEdit()
        advanceUntilIdle()
        assertNull(f.vm.loaded().editing)
    }

    @Test
    fun renameReResolvesTheIconAndSubmitsItToTheCascade() = runTest {
        val f = fixture()
        f.vm.openEdit("baking-powder")
        f.vm.saveEdit("Baking soda", "500 g box", "  ")
        advanceUntilIdle()
        val item = f.store.current().findItem("baking-powder")!!
        assertEquals("Baking soda", item.name)
        assertEquals("500 g box", item.quantity)
        assertNull("a blank note clears it", item.note)
        assertEquals(listOf("Baking soda"), f.icons.submitted.single().map { it.name })
        assertNull("saving closes the sheet", f.vm.loaded().editing)
    }

    @Test
    fun editWithoutRenameDoesNotTouchTheIconCascade() = runTest {
        val f = fixture()
        f.vm.openEdit("milk")
        f.vm.saveEdit("milk, 2 %", "2 × 4 L bags", "")
        advanceUntilIdle()
        assertEquals("2 × 4 L bags", f.store.current().findItem("milk")!!.quantity)
        assertTrue(f.icons.submitted.isEmpty())
    }

    @Test
    fun moveToAisleRecordsALearnedCorrection() = runTest {
        val f = fixture()
        f.vm.moveToAisle("toothpaste", "dairy-eggs")
        advanceUntilIdle()
        val state = f.store.current()
        val item = state.findItem("toothpaste")!!
        assertEquals("dairy-eggs", item.aisleId)
        assertTrue(item.manuallyPlaced)
        assertEquals(normalizeName("Toothpaste"), state.corrections.single().name)
    }

    @Test
    fun setIconRecordsAnOverrideAndForgetsTheBespokeIcon() = runTest {
        val f = fixture()
        f.vm.setIcon("toothpaste", "household_generic")
        advanceUntilIdle()
        val state = f.store.current()
        assertEquals("household_generic", state.findItem("toothpaste")!!.icon)
        assertEquals(IconFit.EXACT, state.findItem("toothpaste")!!.iconFit)
        assertEquals(normalizeName("Toothpaste"), state.iconOverrides.single().name)
        assertEquals(listOf("Toothpaste"), f.icons.forgotten)
    }

    @Test
    fun redrawAsksTheIconRepository() = runTest {
        val f = fixture()
        f.vm.redrawIcon("toothpaste")
        advanceUntilIdle()
        assertEquals(listOf("Toothpaste"), f.icons.redrawn)
    }

    @Test
    fun listsCanBeCreatedSwitchedRenamedAndDeleted() = runTest {
        val f = fixture()
        f.vm.newList("Long weekend")
        advanceUntilIdle()
        assertEquals("Long weekend", f.vm.loaded().title)
        assertEquals("new-0", f.store.current().currentListId)
        f.vm.renameList("new-0", "Cottage weekend")
        f.vm.switchList(ListFixture.LIST_ID)
        advanceUntilIdle()
        assertEquals("Saturday shop", f.vm.loaded().title)
        assertEquals("Cottage weekend", f.store.current().list("new-0")!!.title)
        f.vm.deleteList("new-0")
        advanceUntilIdle()
        assertEquals(1, f.store.current().lists.size)
        assertTrue("an empty list files no book", f.album.current().books.isEmpty())
    }

    /** Deleting a list with a trip files its book in the album first (§6.4, §7.2, checklist 144). */
    @Test
    fun deletingAListFilesItsBookFirst() = runTest {
        val f = fixture()
        f.vm.newList("Long weekend")
        advanceUntilIdle()
        val trip = f.store.current().list(ListFixture.LIST_ID)!!.trip!!
        f.vm.deleteList(ListFixture.LIST_ID)
        advanceUntilIdle()
        assertNull(f.store.current().list(ListFixture.LIST_ID))
        val book = f.album.current().books.single()
        assertEquals(trip.id, book.id)
        assertEquals("Saturday shop", book.listTitle)
        assertEquals("Hillcrest Market", book.storeName)
        assertEquals(10, book.stickers.size)
    }

    /** A list whose book can't be filed stays: album.json is written before state.json (§7.2). */
    @Test
    fun aListWhoseBookCannotBeFiledStays() = runTest {
        val failing = object : AlbumStore by InMemoryAlbumStore() {
            override suspend fun update(transform: (Album) -> Album): Album = throw IOException("disk full")
        }
        val f = fixture(album = failing)
        val events = mutableListOf<ListEvent>()
        // A foreground collector: advanceUntilIdle may leave backgroundScope's work undone.
        val collector = launch { f.vm.events.collect { events += it } }
        f.vm.newList("Long weekend")
        advanceUntilIdle()
        f.vm.deleteList(ListFixture.LIST_ID)
        advanceUntilIdle()
        assertNotNull(f.store.current().list(ListFixture.LIST_ID))
        assertEquals(listOf<ListEvent>(ListEvent.Message(ScreenText.SAVE_FAILED)), events)
        collector.cancel()
    }

    @Test
    fun aFailedSaveTellsTheUser() = runTest {
        val failing = object : StateStore by InMemoryStateStore(ListFixture.state()) {
            override suspend fun update(transform: (AppState) -> AppState): AppState = throw IOException("disk full")
        }
        val f = fixture(store = failing)
        val events = mutableListOf<ListEvent>()
        val collector = launch { f.vm.events.collect { events += it } }
        f.vm.delete("toothpaste")
        advanceUntilIdle()
        assertEquals(listOf<ListEvent>(ListEvent.Message(ScreenText.SAVE_FAILED)), events)
        collector.cancel()
    }
}

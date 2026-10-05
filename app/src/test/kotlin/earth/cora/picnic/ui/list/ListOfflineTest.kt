package earth.cora.picnic.ui.list

import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.store.InMemoryAlbumStore
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.icons.DrawFailure
import earth.cora.picnic.icons.IconRepository
import earth.cora.picnic.platform.CatchUpNote
import earth.cora.picnic.platform.CatchUpNotes
import earth.cora.picnic.platform.FakeNetworkStatus
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.RecordingIconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The list offline (docs/DESIGN_STICKER.md §0.4, 5 October; items 352, 353, 357): a refresh with a key while the phone is
 * offline says that sorting and new stickers wait (and asks nothing); the catch-up's sorting shows once as the refresh's
 * note; the Edit sheet rests "Draw a new sticker" offline and says so when a drawing did not work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListOfflineTest {
    @get:Rule
    val main = MainDispatcherRule()

    /** Icons that report failed drawings when the test says so. */
    private class Icons(val inner: RecordingIconRepository = RecordingIconRepository()) : IconRepository by inner {
        val failures = MutableSharedFlow<DrawFailure>(extraBufferCapacity = 4)
        override val drawFailures: Flow<DrawFailure> = failures
        var resumed = 0
        override fun resumePending() {
            resumed++
        }
    }

    private class Notes : CatchUpNotes {
        override val note = MutableStateFlow<CatchUpNote?>(null)
        val shownIds = mutableListOf<Long>()
        override fun shown(id: Long) {
            shownIds += id
            note.value = null
        }
    }

    private class Rig(val vm: ListViewModel, val icons: Icons, val network: FakeNetworkStatus, val assistant: FakeAssistant, val notes: Notes, val events: List<ListEvent>)

    private fun TestScope.rig(online: Boolean = true, token: String? = "test-token-0123456789", state: AppState = ListFixture.state()): Rig {
        val store = InMemoryStateStore(state)
        val icons = Icons()
        val network = FakeNetworkStatus(online)
        val assistant = FakeAssistant()
        val notes = Notes()
        val vm = ListViewModel(
            store = store,
            album = InMemoryAlbumStore(),
            icons = icons,
            settings = InMemorySettingsRepository(storedToken = token),
            catalog = ListFixture.catalog,
            clock = { ListFixture.NOW + 1_000 },
            newId = { "new" },
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            aisleSync = AisleSync(store, assistant, ListFixture.catalog.baskets) { ListFixture.NOW },
            network = network,
            catchUp = notes,
        )
        backgroundScope.launch { vm.state.collect {} }
        val events = mutableListOf<ListEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        return Rig(vm, icons, network, assistant, notes, events)
    }

    private suspend fun ListViewModel.loaded(): ListUiState = state.first { !it.loading }

    @Test
    fun anOfflineRefreshSaysWhatWaitsAndAsksNothing() = runTest {
        val r = rig(online = false, state = ListFixture.state().let { s ->
            // One item in Everything else, so an online refresh would sort it.
            s.copy(lists = s.lists.map { l -> l.copy(items = l.items.map { if (it.id == "milk") it.copy(aisleId = null) else it }) })
        })
        r.vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf<ListEvent>(ListEvent.RefreshWaits), r.events)
        assertEquals("no sticker work offline", 0, r.icons.resumed)
        assertTrue("no sorting offline", r.assistant.recategorized.isEmpty())

        // Online again: the refresh sorts as before.
        r.network.goOnline()
        r.vm.refresh()
        advanceUntilIdle()
        assertEquals(1, r.icons.resumed)
        assertEquals(1, r.assistant.recategorized.size)
    }

    @Test
    fun withoutAKeyAnOfflineRefreshStaysQuiet() = runTest {
        val r = rig(online = false, token = null)
        r.vm.refresh()
        advanceUntilIdle()
        assertTrue("no promise of sorting without a key", r.events.isEmpty())
    }

    @Test
    fun theCatchUpsSortingShowsOnceAsTheRefreshNote() = runTest {
        val r = rig()
        r.vm.loaded()
        r.notes.note.value = CatchUpNote(id = 7, sorted = 2, left = 1)
        advanceUntilIdle()
        assertEquals(listOf<ListEvent>(ListEvent.Refreshed(2, 1, 0)), r.events)
        assertEquals(listOf(7L), r.notes.shownIds)
    }

    @Test
    fun offlineTheEditSheetRestsDrawingAndSaysWhy() = runTest {
        val r = rig(online = false)
        r.vm.openEdit("milk")
        advanceUntilIdle()
        val editing = r.vm.loaded().editing!!
        assertTrue(editing.canDraw)
        assertTrue(editing.offline)
        r.network.goOnline()
        advanceUntilIdle()
        assertFalse(r.vm.loaded().editing!!.offline)
    }

    @Test
    fun aDrawingThatDidNotWorkShowsInTheSheetUntilItIsTriedAgain() = runTest {
        val r = rig()
        r.vm.openEdit("milk")
        advanceUntilIdle()
        val key = normalizeName(r.vm.loaded().editing!!.item.name)
        r.icons.failures.emit(DrawFailure(key))
        advanceUntilIdle()
        assertEquals(DrawFailure(key), r.vm.loaded().editing!!.drawFailed)

        // Another item's failure is not this sheet's.
        r.icons.failures.emit(DrawFailure("oat milk"))
        advanceUntilIdle()
        assertNull(r.vm.loaded().editing!!.drawFailed)

        r.icons.failures.emit(DrawFailure(key, refused = true))
        advanceUntilIdle()
        assertNotNull(r.vm.loaded().editing!!.drawFailed)
        r.vm.redrawIcon("milk")
        advanceUntilIdle()
        assertNull("a new try clears it", r.vm.loaded().editing!!.drawFailed)

        r.icons.failures.emit(DrawFailure(key))
        advanceUntilIdle()
        r.vm.closeEdit()
        r.vm.openEdit("milk")
        advanceUntilIdle()
        assertNull("the sheet opened again starts afresh", r.vm.loaded().editing!!.drawFailed)
    }
}

package earth.cora.picnic.ui.add

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.model.list
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.testAppScope
import earth.cora.picnic.ui.theme.ToastHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The add flows' sticky notes (docs/DESIGN_STICKER.md §4.15 "Queue and merge", items 201, 212): two commits during one
 * note's hold merge into one note that says everything its single Undo takes back, and that Undo removes all of it.
 * The host's hold is not running here, so every second note arrives "during the hold".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AddToastsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val f = AddFixtures
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun answer(vararg items: earth.cora.picnic.core.assistant.ProposedItem) =
        IngestResult(items.toList(), "", emptyList(), emptyList(), f.meta())

    /** The commits [vm] reports, as they are sent. */
    private fun TestScope.commits(vm: AddViewModel): List<AddCommit> {
        val out = mutableListOf<AddEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(out) }
        return object : AbstractList<AddCommit>() {
            private val committed get() = out.filterIsInstance<AddEvent.Committed>()
            override val size: Int get() = committed.size
            override fun get(index: Int): AddCommit = committed[index].commit
        }
    }

    @Test
    fun twoQuickAddsDuringOneHoldMergeAndOneUndoTakesBothBack() = runTest {
        val assistant = FakeAssistant(ingest = { input, _ ->
            if ((input as IngestInput.Text).text.startsWith("lemons")) {
                answer(f.proposed("Lemons", "lemon", "produce", "2"), f.proposed("Oat milk", "plant_milk", "dairy-eggs", "1.89 L carton"))
            } else {
                answer(f.proposed("Limes", "lime", "produce", "4"))
            }
        })
        val r = addRig(testAppScope(), f.state, assistant)
        val commits = commits(r.vm)
        val notes = AddToasts(resources, r.vm::undo)
        val host = ToastHostState()
        val before = r.store.current().list(f.LIST_ID)!!.items.map { it.id to it.checked }

        r.vm.setText("lemons, oat milk")
        r.vm.send()
        advanceUntilIdle()
        host.show(notes.toast(commits[0]))
        assertEquals("Added 2 items to Saturday\u00A0shop\u00A0♡", host.current!!.text)

        r.vm.setText("limes")
        r.vm.send()
        advanceUntilIdle()
        host.show(notes.toast(commits[1]))

        // One note: it counts both commits, so it says what Undo restores; nothing waits behind it.
        val note = host.current!!
        assertEquals("Added 3 items to Saturday\u00A0shop\u00A0♡", note.text)
        assertEquals("Undo", note.actionLabel)
        assertTrue(host.queue.isEmpty())

        note.onAction!!()
        advanceUntilIdle()
        assertEquals("one Undo removes everything the note named", before, r.store.current().list(f.LIST_ID)!!.items.map { it.id to it.checked })
    }

    @Test
    fun aRevivedRowJoinsTheMergedNote() = runTest {
        val assistant = FakeAssistant(ingest = { input, _ ->
            if ((input as IngestInput.Text).text == "limes") answer(f.proposed("Limes", "lime", "produce", "4")) else answer(f.proposed("Bananas", "banana", "produce", "6 bananas"))
        })
        val r = addRig(testAppScope(), f.state, assistant)
        val commits = commits(r.vm)
        val notes = AddToasts(resources, r.vm::undo)
        val host = ToastHostState()
        r.vm.setText("limes")
        r.vm.send()
        advanceUntilIdle()
        host.show(notes.toast(commits[0]))
        assertEquals("Added Limes to Produce\u00A0♡", host.current!!.text)
        r.vm.setText("bananas")
        r.vm.send()
        advanceUntilIdle()
        host.show(notes.toast(commits[1]))
        assertEquals("Added Limes to Produce\u00A0♡ · Bananas is back on your list", host.current!!.text)

        host.current!!.onAction!!()
        advanceUntilIdle()
        val items = r.store.current().list(f.LIST_ID)!!.items
        assertTrue("the limes are gone", items.none { it.name == "Limes" })
        assertTrue("the bananas are checked again", items.single { it.id == "i-bananas" }.checked)
    }

    @Test
    fun mergedCommitsCountEverything() {
        val one = AddCommit(1, f.LIST_ID, "Saturday shop", listOf(f.items[0]), emptyList(), emptyList(), aisleTitle = "Produce")
        val two = AddCommit(2, f.LIST_ID, "Saturday shop", emptyList(), listOf(f.items[5]), listOf(f.items[1]))
        val both = one + two
        assertEquals(listOf(f.items[0]), both.added)
        assertEquals("one added row keeps its aisle", "Produce", both.aisleTitle)
        assertEquals(listOf(f.items[1]), both.revived)
        assertEquals(listOf(f.items[5]), both.alreadyThere)
        assertEquals(null, (both + one.copy(id = 3)).aisleTitle)
    }

    /**
     * "Move" on the add note (Cora, 5 October; §6.2, §4.15): a note that put one item in an aisle offers it as a second
     * chip before Undo, and it hands that item over; no other note does (several items, Everything else, another list,
     * as typed), and a merge that makes it several items takes it away.
     */
    @Test
    fun oneSortedItemOffersMove() {
        val toothpaste = f.items.single { it.id == "i-toothpaste" }
        val moved = mutableListOf<earth.cora.picnic.core.model.GroceryItem>()
        val notes = AddToasts(resources, undo = {}, move = { moved += it })
        val one = AddCommit(1, f.LIST_ID, "Saturday shop", listOf(toothpaste), emptyList(), emptyList(), aisleTitle = "Household")
        val note = notes.toast(one)
        assertEquals("Added Toothpaste to Household ♡", note.text)
        assertEquals("Move", note.secondaryLabel)
        assertEquals("Undo", note.actionLabel)
        note.onSecondary!!()
        assertEquals(listOf(toothpaste), moved)

        fun offers(c: AddCommit) = notes.toast(c).secondaryLabel != null
        assertEquals(false, offers(one.copy(added = listOf(toothpaste, f.items[0]), aisleTitle = null)))
        assertEquals(false, offers(one.copy(added = listOf(toothpaste.copy(aisleId = null)), aisleTitle = "")))
        assertEquals(false, offers(one.copy(otherList = true)))
        assertEquals(false, offers(one.copy(asTypedNoKey = true)))
        assertEquals("no Move without a host that can open the sheet", null, AddToasts(resources, undo = {}).toast(one).secondaryLabel)

        val host = ToastHostState()
        host.show(notes.toast(one))
        host.show(notes.toast(one.copy(id = 2, added = listOf(f.items[0]), aisleTitle = "Produce")))
        assertEquals("Added 2 items to Saturday shop ♡", host.current!!.text)
        assertEquals("two items: no single one to move", null, host.current!!.secondaryLabel)
    }
}

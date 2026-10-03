package earth.cora.picnic.ui.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.assistant.Transcript
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.list
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.FakeRecorder
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.testAppScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The add flows' ViewModel (docs/DESIGN_STICKER.md §6.2 send rules 1–8, D16, D19; items 201, 211, 212, 213, 215, 216,
 * 324) with a fake assistant, recorder, photo preparer and SurpriseEngine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AddViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val f = AddFixtures

    private fun answer(vararg items: earth.cora.picnic.core.assistant.ProposedItem, message: String = "") =
        IngestResult(items.toList(), message, emptyList(), emptyList(), f.meta())

    private fun TestScope.rig(
        result: IngestResult? = null,
        hasKey: Boolean = true,
        state: earth.cora.picnic.core.model.AppState = f.state,
        surprises: RecordingSurprises = RecordingSurprises(),
        recorder: FakeRecorder = FakeRecorder(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ): AddRig {
        val assistant = FakeAssistant(ingest = { _, _ -> result ?: error("no answer scripted") })
        return addRig(testAppScope(), state, assistant, hasKey, surprises, recorder, savedState)
    }

    private fun TestScope.events(vm: AddViewModel): MutableList<AddEvent> {
        val out = mutableListOf<AddEvent>()
        // Unconfined: the collector takes each event as it is sent (advanceUntilIdle does not wait for background work).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(out) }
        return out
    }

    // ---- rule 4: quick add ----

    @Test
    fun quickAddCommitsAtOnceWithUndo() = runTest {
        val r = rig(answer(f.proposed("Lemons", "lemon", "produce", "2"), f.proposed("Oat milk", "plant_milk", "dairy-eggs", "1.89 L carton")))
        val events = events(r.vm)
        val before = r.store.current().list(f.LIST_ID)!!.items
        r.vm.setText("2 lemons, oat milk")
        r.vm.send(AddOrigin.BAR)
        advanceUntilIdle()

        val s = r.vm.state.value
        assertFalse("a quick add never opens the sheet", s.sheetOpen)
        assertEquals("", s.text)
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        assertEquals(listOf("Lemons", "Oat milk"), commit.added.map { it.name })
        assertTrue(commit.canUndo)
        assertEquals(listOf(0L, 80L), commit.added.indices.map(commit::popDelayMillis))
        assertTrue(Cue.CAPSULE_POP in r.feedback.cues)
        val names = r.store.current().list(f.LIST_ID)!!.items.map { it.name }
        assertTrue(names.containsAll(listOf("Lemons", "Oat milk")))

        // Undo removes exactly the added items.
        r.vm.undo(commit.id)
        advanceUntilIdle()
        // (The store hands out book slots on its first write, so compare what she sees: the rows and their ticks.)
        assertEquals(before.map { it.id to it.checked }, r.store.current().list(f.LIST_ID)!!.items.map { it.id to it.checked })
        assertTrue(events.any { it is AddEvent.Undone && it.commitId == commit.id })
    }

    @Test
    fun aMessageWorthShowingForcesReview() = runTest {
        val r = rig(answer(f.proposed("Milk", "milk_bag", "dairy-eggs"), message = "Milk is a 4 L bag in Ontario."))
        r.vm.setText("milk")
        r.vm.send()
        advanceUntilIdle()
        val s = r.vm.state.value
        assertTrue(s.sheetOpen)
        assertEquals(AddPhase.Preview, s.phase)
        assertEquals("Milk is a 4 L bag in Ontario.", s.review!!.message)
    }

    @Test
    fun anEchoedMessageIsNotWorthShowing() = runTest {
        val r = rig(answer(f.proposed("Lemons", "lemon", "produce"), message = "Lemons."))
        r.vm.setText("lemons")
        r.vm.send()
        advanceUntilIdle()
        assertFalse(r.vm.state.value.sheetOpen)
        assertTrue(r.store.current().list(f.LIST_ID)!!.items.any { it.name == "Lemons" })
    }

    @Test
    fun aRecipeOrMoreThanThreeItemsReview() = runTest {
        val r = rig(f.pancakes())
        r.vm.setText(PANCAKES)
        r.vm.send()
        advanceUntilIdle()
        val s = r.vm.state.value
        assertTrue(s.sheetOpen)
        assertEquals(AddMode.WRITE, s.mode)
        assertEquals(PANCAKES, s.sentText)
        assertEquals(ItemSource.AI_TEXT, s.review!!.source)
        // Optional rows and rows already on the list start switched off.
        assertEquals(6, s.keptCount)
        assertFalse(s.review!!.rows.single { it.item.name == "Milk, 2 %" }.canKeep)
    }

    @Test
    fun pantryRowForcesReview() = runTest {
        // Two items, no recipe, no message: only the pantry row keeps it from a quick add (item 324).
        val r = rig(
            answer(
                f.proposed("Eggs", "eggs", "dairy-eggs", "dozen"),
                f.proposed("All-purpose flour", "flour", "aisle-2-baking", "2.5 kg bag · 2 cups", skip = SkipReason.IN_PANTRY, pantryEntry = "Flour"),
            ),
            state = f.stateWithPantry,
        )
        r.vm.setText("eggs and whatever pancakes need")
        r.vm.send()
        advanceUntilIdle()
        val s = r.vm.state.value
        assertTrue(s.sheetOpen)
        assertEquals(AddPhase.Preview, s.phase)
        assertTrue(s.review!!.steps.contains(ThinkingStep.CheckedPantry(1)))
        val flour = s.review!!.rows.single { it.item.name == "All-purpose flour" }
        assertFalse("a pantry row starts off", flour.keep)
        assertEquals("nothing was committed", f.stateWithPantry, r.store.current())
        // The request carried the pantry.
        assertEquals(listOf("Flour", "Sugar", "Salt", "Baking powder"), r.assistant.ingested.single().second.pantry)
    }

    // ---- rules 1 and 2 ----

    @Test
    fun theMagicWordNeverReachesTheNetwork() = runTest {
        val r = rig(answer(f.proposed("Milk", "milk_bag", null)))
        r.vm.setText("Kira Kira!")
        r.vm.send()
        advanceUntilIdle()
        assertEquals(0, r.assistant.ingested.size)
        assertEquals("", r.vm.state.value.text)
        assertEquals(f.state, r.store.current())
        assertEquals(listOf("Kira Kira!"), r.surprises.intercepted)
    }

    @Test
    fun noKeyAddsAsTyped() = runTest {
        val r = rig(hasKey = false)
        val events = events(r.vm)
        r.vm.setText("2 lemons, oat milk")
        r.vm.send()
        advanceUntilIdle()
        assertEquals("no request without a key", 0, r.assistant.ingested.size)
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        assertTrue(commit.asTypedNoKey)
        assertTrue(commit.canUndo)
        assertEquals(listOf("Lemons", "Oat milk"), commit.added.map { it.name })
        assertTrue(commit.added.all { it.source == ItemSource.TYPED })
    }

    // ---- rule 7: duplicates ----

    @Test
    fun aDuplicateOfAnUncheckedItemIsNeverAddedTwice() = runTest {
        val r = rig(hasKey = false)
        val events = events(r.vm)
        r.vm.setText("strawberries")
        r.vm.send()
        advanceUntilIdle()
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        assertTrue(commit.added.isEmpty())
        assertEquals(listOf("i-strawberries"), commit.alreadyThere.map { it.id })
        assertFalse(commit.canUndo)
        assertEquals(f.state, r.store.current())
    }

    @Test
    fun aDuplicateOfACheckedItemRevivesItAndUndoChecksItAgain() = runTest {
        val r = rig(hasKey = false)
        val events = events(r.vm)
        r.vm.setText("bananas")
        r.vm.send()
        advanceUntilIdle()
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        assertEquals(listOf("i-bananas"), commit.revived.map { it.id })
        val revived = r.store.current().list(f.LIST_ID)!!.items.single { it.id == "i-bananas" }
        assertFalse(revived.checked)
        assertEquals(f.state.list(f.LIST_ID)!!.items.size, r.store.current().list(f.LIST_ID)!!.items.size)

        r.vm.undo(commit.id)
        advanceUntilIdle()
        val back = r.store.current().list(f.LIST_ID)!!.items
        val bananas = back.single { it.id == "i-bananas" }
        assertTrue(bananas.checked)
        assertEquals(f.items.single { it.id == "i-bananas" }.checkedAt, bananas.checkedAt)
        assertEquals("it is back in its place", f.items.indexOfFirst { it.id == "i-bananas" }, back.indexOfFirst { it.id == "i-bananas" })
    }

    // ---- item 212: commit ----

    @Test
    fun commitEmitsEvents() = runTest {
        // Rolls go in row order: blueberries, flour, sugar, baking powder, then salt is shiny.
        val surprises = RecordingSurprises(rarities = listOf(Rarity.NONE, Rarity.NONE, Rarity.NONE, Rarity.NONE, Rarity.SHINY))
        val r = rig(f.pancakes(), surprises = surprises)
        val events = events(r.vm)
        r.vm.setText(PANCAKES)
        r.vm.send(AddOrigin.BAR)
        advanceUntilIdle()
        val review = r.vm.state.value.review!!
        // Switch the optional blueberries on: kept rows are committed in the answer's order.
        r.vm.toggleKeep(review.rows.single { it.item.name == "Blueberries" }.key)
        r.vm.commit()
        assertTrue(r.vm.state.value.committing)
        advanceUntilIdle()

        val s = r.vm.state.value
        assertFalse("the sheet closes", s.sheetOpen)
        assertNull(s.review)
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        // Kept rows go on in the answer's order; flour and sugar were gotten (checked), so they come back (rule 7),
        // and the baking powder is already there unchecked, so it only pops.
        assertEquals(listOf("Blueberries", "Salt", "Eggs", "Unsalted butter"), commit.added.map { it.name })
        assertEquals(listOf("i-flour", "i-sugar"), commit.revived.map { it.id })
        assertEquals(listOf("i-bp"), commit.alreadyThere.map { it.id })
        assertEquals(listOf(0L, 80L, 160L), (0..2).map(commit::popDelayMillis))
        assertEquals("one roll per committed row", 7, surprises.rolls)
        assertEquals(Rarity.SHINY, commit.added[1].rarity)
        assertTrue(commit.shiny)
        assertEquals("the SurpriseEngine hears of the new rows", listOf(commit.added), surprises.committed)
        assertEquals(listOf(commit.added), r.icons.submitted)
        assertEquals("Saturday shop", commit.listTitle)
    }

    @Test
    fun aSingleAddedItemNamesItsAisle() = runTest {
        val r = rig(answer(f.proposed("Oat milk", "plant_milk", "dairy-eggs", "1.89 L carton")))
        val events = events(r.vm)
        r.vm.setText("oat milk")
        r.vm.send()
        advanceUntilIdle()
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        assertEquals("Dairy & eggs", commit.aisleTitle)
    }

    // ---- item 211: Snap then Speak ----

    @Test
    fun snapThenSpeakMerges() = runTest {
        val assistant = FakeAssistant(
            ingest = { input, _ -> if (input is IngestInput.Image) f.photo() else f.voice() },
            transcribe = { Transcript(f.TRANSCRIPT) },
        )
        val clip = tmp.newFile("note.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val recorder = FakeRecorder(stopResult = { clip })
        val r = addRig(testAppScope(), f.state, assistant, recorder = recorder)
        r.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        advanceUntilIdle()
        val photo = r.vm.state.value.review!!
        assertEquals(ItemSource.PHOTO, photo.source)
        assertEquals(AddMode.SNAP, r.vm.state.value.mode)
        assertEquals(f.photo().lines, photo.lines)

        r.vm.startRecording()
        assertEquals("the photo's review stays while Speak records", photo.id, r.vm.state.value.review!!.id)
        advanceTimeBy(350)
        r.vm.stopRecording()
        advanceUntilIdle()

        val merged = r.vm.state.value.review!!
        assertEquals("the same preview", photo.id, merged.id)
        assertNotNull(merged.heardId)
        // "Eggs" was already in the photo's batch: de-duplicated.
        assertEquals(listOf("Frozen corn", "Cat litter"), merged.heardRows.map { it.item.name })
        assertEquals(photo.rows, merged.mainRows)
        assertEquals(listOf(Cue.CASSETTE, Cue.CASSETTE), r.feedback.cues.filter { it == Cue.CASSETTE })

        r.vm.commit()
        advanceUntilIdle()
        val items = r.store.current().list(f.LIST_ID)!!.items
        assertEquals(ItemSource.PHOTO, items.single { it.name == "Old cheddar" }.source)
        assertEquals(ItemSource.VOICE, items.single { it.name == "Cat litter" }.source)
    }

    // ---- item 215: failures ----

    @Test
    fun failures() = runTest {
        var calls = 0
        val assistant = FakeAssistant(ingest = { _, _ ->
            calls++
            throw LlmError.Network(null)
        })
        val r = addRig(testAppScope(), f.state, assistant)
        r.vm.setText("2 lemons, oat milk")
        r.vm.send(AddOrigin.BAR)
        advanceUntilIdle()
        val failed = r.vm.state.value.phase as AddPhase.Failed
        assertTrue("the error shows in the sheet", r.vm.state.value.sheetOpen)
        assertEquals(AddMode.WRITE, r.vm.state.value.mode)
        assertEquals(LlmError.Network(null).friendlyMessage, failed.message)
        assertTrue(failed.retryable)
        assertTrue(failed.canUsePlainLines)
        assertFalse(failed.needsSettings)

        r.vm.retry()
        advanceUntilIdle()
        assertEquals("Try again repeats the request", 2, calls)

        // "Add as plain lines" commits the note line by line.
        r.vm.usePlainLines()
        advanceUntilIdle()
        val names = r.store.current().list(f.LIST_ID)!!.items.map { it.name }
        assertTrue(names.containsAll(listOf("Lemons", "Oat milk")))
        assertFalse(r.vm.state.value.sheetOpen)

        // NotConfigured offers Open Settings.
        assistant.ingest = { _, _ -> throw LlmError.NotConfigured("no key") }
        r.vm.setText("pears")
        r.vm.send()
        advanceUntilIdle()
        assertTrue((r.vm.state.value.phase as AddPhase.Failed).needsSettings)
    }

    @Test
    fun aPhotoFailureOffersTryAgainButNoPlainLines() = runTest {
        val r = addRig(testAppScope(), f.state, FakeAssistant(ingest = { _, _ -> throw LlmError.Timeout() }))
        r.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        advanceUntilIdle()
        val failed = r.vm.state.value.phase as AddPhase.Failed
        assertTrue(failed.retryable)
        assertFalse(failed.canUsePlainLines)
        assertEquals(listOf(Uri.parse("content://test/photo.jpg")), r.photos.discarded)
    }

    // ---- item 216: As typed ----

    @Test
    fun asTyped() = runTest {
        val r = rig()
        val events = events(r.vm)
        r.vm.openSheet(AddMode.WRITE)
        r.vm.setText("2 lb potatoes\n- butter tarts")
        r.vm.addAsTyped()
        advanceUntilIdle()
        assertEquals("As typed never calls the assistant", 0, r.assistant.ingested.size)
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        assertFalse("she chose As typed: the plain toast", commit.asTypedNoKey)
        assertEquals(listOf("Potatoes", "Butter tarts"), commit.added.map { it.name })
        assertEquals("907 g", commit.added.first().quantity)
        assertFalse(r.vm.state.value.sheetOpen)
    }

    // ---- the review ----

    @Test
    fun aRowAlreadyOnTheListCannotBeSwitchedOn() = runTest {
        val r = rig(f.pancakes())
        r.vm.setText(PANCAKES)
        r.vm.send()
        advanceUntilIdle()
        val milk = r.vm.state.value.review!!.rows.single { it.item.name == "Milk, 2 %" }
        r.vm.toggleKeep(milk.key)
        assertFalse(r.vm.state.value.review!!.rows.single { it.key == milk.key }.keep)
    }

    @Test
    fun aPlayedRevealIsRememberedAndChangingTheNoteDropsTheReview() = runTest {
        val r = rig(f.pancakes())
        r.vm.setText(PANCAKES)
        r.vm.send()
        advanceUntilIdle()
        val id = r.vm.state.value.review!!.id
        r.vm.revealFinished(id)
        assertEquals(setOf(id), r.vm.state.value.review!!.revealed)
        r.vm.editNote()
        assertNull(r.vm.state.value.review)
        assertEquals(PANCAKES, r.vm.state.value.text)
        assertEquals(AddMode.WRITE, r.vm.state.value.mode)
    }

    @Test
    fun closingTheSheetKeepsTheDraftOnly() = runTest {
        val r = rig(f.pancakes())
        r.vm.setText(PANCAKES)
        r.vm.send()
        advanceUntilIdle()
        r.vm.closeSheet()
        val s = r.vm.state.value
        assertFalse(s.sheetOpen)
        assertNull(s.review)
        assertEquals(PANCAKES, s.text)
    }

    @Test
    fun theDraftAndTheOpenSheetSurviveProcessDeath() = runTest {
        val saved = SavedStateHandle()
        val r = rig(savedState = saved)
        r.vm.openSheet(AddMode.SNAP)
        r.vm.setText("eggs")
        runCurrent()
        val restored = addRig(testAppScope(), f.state, savedState = saved)
        val s = restored.vm.state.value
        assertEquals("eggs", s.text)
        assertEquals(AddMode.SNAP, s.mode)
        assertTrue(s.sheetOpen)
    }

    @Test
    fun theBarFieldIsReadOnlyWhileWaiting() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<IngestResult>()
        val r = addRig(testAppScope(), f.state, FakeAssistant(ingest = { _, _ -> gate.await() }))
        r.vm.setText("pears")
        r.vm.send(AddOrigin.BAR)
        runCurrent()
        assertTrue(r.vm.state.value.waiting)
        assertTrue(r.vm.state.value.barUi().waiting)
        r.vm.setText("pears and plums")
        assertEquals("pears", r.vm.state.value.text)
        r.vm.cancel()
        assertFalse(r.vm.state.value.waiting)
    }
}

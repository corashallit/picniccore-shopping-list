package earth.cora.picnic.ui.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.assistant.Transcript
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.list
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.FakeRecorder
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.testAppScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
        // Only new stickers roll (egg 6), in row order: blueberries, then salt is shiny.
        val surprises = RecordingSurprises(rarities = listOf(Rarity.NONE, Rarity.SHINY))
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
        // the baking powder is already there unchecked, so it only pops, and so does the milk row the answer marked
        // "already on your list" (rule 7: its sticker pops; it is never added twice).
        assertEquals(listOf("Blueberries", "Salt", "Eggs", "Unsalted butter"), commit.added.map { it.name })
        assertEquals(listOf("i-flour", "i-sugar"), commit.revived.map { it.id })
        assertEquals(listOf("i-bp", "i-milk"), commit.alreadyThere.map { it.id })
        assertEquals(listOf(0L, 80L, 160L), (0..2).map(commit::popDelayMillis))
        assertEquals("one roll per new sticker (revived and already-there rows keep theirs)", commit.added.size, surprises.rolls)
        assertEquals("the stored rows carry the rolls", Rarity.SHINY, r.store.current().list(f.LIST_ID)!!.items.single { it.name == "Salt" }.rarity)
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

        // "Add as plain lines" (ARCHITECTURE §10.4): the note line by line, then a review she can switch rows off in.
        r.vm.usePlainLines()
        advanceUntilIdle()
        val plain = r.vm.state.value.review!!
        assertTrue(plain.plain)
        assertEquals(ItemSource.TYPED, plain.source)
        assertEquals(AddMode.WRITE, r.vm.state.value.mode)
        assertEquals(AddPhase.Preview, r.vm.state.value.phase)
        assertEquals(listOf("Lemons", "Oat milk"), plain.rows.map { it.item.name })
        assertTrue("every row can be switched off", plain.rows.all { it.canKeep && it.keep && it.typed })
        assertEquals("nothing lands before Stick all", f.state, r.store.current())
        r.vm.toggleKeep(plain.rows.single { it.item.name == "Lemons" }.key)
        r.vm.commit()
        advanceUntilIdle()
        val items = r.store.current().list(f.LIST_ID)!!.items
        assertFalse("switched off: never added", items.any { it.name == "Lemons" })
        assertEquals(ItemSource.TYPED, items.single { it.name == "Oat milk" }.source)
        assertFalse(r.vm.state.value.sheetOpen)
        assertEquals("the note went on: the draft clears", "", r.vm.state.value.text)
        assertEquals("the typed row is placed in the background", listOf("Oat milk"), r.assistant.recategorized.single().map { it.name })

        // NotConfigured offers Open Settings.
        assistant.ingest = { _, _ -> throw LlmError.NotConfigured("no key") }
        r.vm.setText("pears")
        r.vm.send()
        advanceUntilIdle()
        assertTrue((r.vm.state.value.phase as AddPhase.Failed).needsSettings)
    }

    /**
     * Item 215 / §10.4: a voice note whose reading failed goes through a review as plain lines, so spoken fragments
     * ("Oh and frozen corn") can be switched off before they land; nothing reaches the list before "Stick all".
     */
    @Test
    fun aVoiceNotesPlainLinesOpenAReview() = runTest {
        val assistant = FakeAssistant(
            ingest = { _, _ -> throw LlmError.Network(null) },
            transcribe = { Transcript("oh and frozen corn, and the big jug of litter for Mochi") },
        )
        val clip = tmp.newFile("plain.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val r = addRig(testAppScope(), f.state, assistant, recorder = FakeRecorder(stopResult = { clip }))
        r.vm.openSheet(AddMode.SPEAK)
        r.vm.startRecording()
        advanceTimeBy(350)
        r.vm.stopRecording()
        advanceUntilIdle()
        assertTrue((r.vm.state.value.phase as AddPhase.Failed).canUsePlainLines)

        r.vm.usePlainLines()
        advanceUntilIdle()
        val s = r.vm.state.value
        val review = s.review!!
        assertEquals(AddMode.SPEAK, s.mode)
        assertEquals(ItemSource.VOICE, review.source)
        assertTrue("no AI ran: no thinking box", review.plain && review.steps.isEmpty())
        assertEquals(listOf("Oh and frozen corn", "And the big jug of litter for Mochi"), review.rows.map { it.item.name })
        assertTrue("each fragment has its keep switch", review.rows.all { it.canKeep && it.keep })
        assertEquals("nothing lands before Stick all", f.state, r.store.current())

        r.vm.toggleKeep(review.rows.first().key)
        r.vm.commit()
        advanceUntilIdle()
        val items = r.store.current().list(f.LIST_ID)!!.items
        assertFalse(items.any { it.name == "Oh and frozen corn" })
        assertEquals("plain lines commit as typed (the aisle sort places them)", ItemSource.TYPED, items.single { it.name == "And the big jug of litter for Mochi" }.source)
    }

    /** Plain lines mark a name already unchecked on her list "already on your list" (rule 7): it pops, never twice. */
    @Test
    fun plainLinesKnowWhatIsAlreadyOnTheList() = runTest {
        val r = addRig(testAppScope(), f.state, FakeAssistant(ingest = { _, _ -> throw LlmError.Timeout() }))
        r.vm.setText("strawberries, bananas, star fruit")
        r.vm.send(AddOrigin.SHEET)
        advanceUntilIdle()
        r.vm.usePlainLines()
        advanceUntilIdle()
        val rows = r.vm.state.value.review!!.rows.associateBy { it.item.name }
        assertFalse("unchecked on the list: no switch", rows.getValue("Strawberries").canKeep)
        assertTrue("checked on the list: a row that revives", rows.getValue("Bananas").keep)
        assertTrue(rows.getValue("Star fruit").keep)
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

    // ---- item 207: a photo that cannot be opened ----

    @Test
    fun anUnreadablePhotoLeavesNoPolaroid() = runTest {
        val r = addRig(testAppScope(), f.state, FakeAssistant())
        r.photos.bytes = { throw java.io.IOException("gone") }
        r.vm.onPhoto(Uri.parse("content://test/gone.jpg"))
        advanceUntilIdle()
        val s = r.vm.state.value
        assertEquals(AddProblem.PHOTO_UNREADABLE, (s.phase as AddPhase.Failed).problem)
        assertNull("the Snap tab is back to choosing a photo", s.photo)
        assertEquals(AddMode.SNAP, s.mode)
        assertEquals(listOf(Uri.parse("content://test/gone.jpg")), r.photos.discarded)
        // Another photo reads normally.
        r.photos.bytes = { byteArrayOf(1, 2, 3) }
        r.assistant.ingest = { _, _ -> f.photo() }
        r.vm.onPhoto(Uri.parse("content://test/next.jpg"))
        advanceUntilIdle()
        assertEquals(ItemSource.PHOTO, r.vm.state.value.review!!.source)
    }

    // ---- items 199, 215: Snap and Speak need the key before anything is captured ----

    @Test
    fun snapAndSpeakNeedTheKeyFirst() = runTest {
        val r = rig(hasKey = false)
        var captured = 0
        r.vm.requireKey(AddMode.SPEAK) { captured++ }
        advanceUntilIdle()
        assertEquals("no microphone, no permission prompt", 0, captured)
        val s = r.vm.state.value
        assertTrue(s.sheetOpen)
        assertEquals(AddMode.SPEAK, s.mode)
        val failed = s.phase as AddPhase.Failed
        assertEquals(LlmError.NotConfigured("").friendlyMessage, failed.message)
        assertTrue(failed.needsSettings)
        r.vm.requireKey(AddMode.SNAP) { captured++ }
        advanceUntilIdle()
        assertEquals("no camera either", 0, captured)
        assertEquals(AddMode.SNAP, r.vm.state.value.mode)

        val keyed = rig(hasKey = true)
        keyed.vm.requireKey(AddMode.SNAP) { captured++ }
        advanceUntilIdle()
        assertEquals("with a key the camera opens at once", 1, captured)
        assertFalse(keyed.vm.state.value.sheetOpen)
    }

    /** A key error after a capture keeps the recording: once the key is fixed, Try again picks it up. */
    @Test
    fun aKeyErrorKeepsTheRecordingForTryAgain() = runTest {
        var keyFixed = false
        val assistant = FakeAssistant(
            transcribe = { if (keyFixed) Transcript(f.TRANSCRIPT) else throw LlmError.Unauthorized(401) },
            ingest = { _, _ -> f.voice() },
        )
        val clip = tmp.newFile("kept.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val r = addRig(testAppScope(), f.state, assistant, recorder = FakeRecorder(stopResult = { clip }))
        r.vm.startRecording()
        advanceTimeBy(350)
        r.vm.stopRecording()
        advanceUntilIdle()
        val failed = r.vm.state.value.phase as AddPhase.Failed
        assertTrue(failed.needsSettings)
        assertTrue("the recording waits: Try again", failed.retryable)
        keyFixed = true
        r.vm.retry()
        advanceUntilIdle()
        assertEquals(ItemSource.VOICE, r.vm.state.value.review!!.source)
        assertEquals("the same bytes, no second recording", 2, r.assistant.transcribed.size)
    }

    // ---- item 206: tabs while something runs ----

    @Test
    fun tabsWaitWhileSomethingRuns() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<IngestResult>()
        val r = addRig(testAppScope(), f.state, FakeAssistant(ingest = { _, _ -> gate.await() }))
        r.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        runCurrent()
        val s = r.vm.state.value
        assertTrue(s.phase.busy)
        for (mode in AddMode.entries) assertFalse("$mode while the photo is read", s.tabEnabled(mode))
        r.vm.setMode(AddMode.WRITE)
        assertEquals(AddMode.SNAP, r.vm.state.value.mode)
        gate.complete(f.photo())
        advanceUntilIdle()
        assertTrue(r.vm.state.value.tabEnabled(AddMode.SPEAK))
    }

    // ---- §15: the bar's wait is announced once ----

    @Test
    fun theBarsWaitIsAnnounced() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<IngestResult>()
        val r = addRig(testAppScope(), f.state, FakeAssistant(ingest = { _, _ -> gate.await() }))
        r.vm.setText("pears")
        r.vm.send(AddOrigin.BAR)
        runCurrent()
        assertEquals(listOf("Reading your note…"), r.announcer.messages)
        r.vm.cancel()
        // From the sheet the thinking box's own polite headline says it: no second announcement.
        r.vm.openSheet(AddMode.WRITE)
        r.vm.send(AddOrigin.SHEET)
        runCurrent()
        assertEquals(1, r.announcer.messages.size)
        r.vm.cancel()
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

    /**
     * Item 216 / ARCHITECTURE §10.3: the aisle sort after As typed runs in the app scope. While it waits on the network,
     * Send works; closing the sheet (which stops the next request) never stops the sort, so the typed row is placed.
     */
    @Test
    fun theSortAfterAsTypedNeverHoldsOrLosesAnything() = runTest {
        val sorted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val next = kotlinx.coroutines.CompletableDeferred<IngestResult>()
        val assistant = FakeAssistant(
            ingest = { _, _ -> next.await() },
            recategorize = { items, _ ->
                sorted.await()
                items.associate { it.id to "produce" }
            },
        )
        val r = addRig(testAppScope(), f.state, assistant)
        val events = events(r.vm)
        r.vm.openSheet(AddMode.WRITE)
        r.vm.setText("star fruit")
        r.vm.addAsTyped()
        advanceUntilIdle()
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        val star = commit.added.single()
        assertNull("typed: no aisle yet", star.aisleId)
        assertEquals("the sort is out", 1, r.assistant.recategorized.size)
        assertFalse(r.vm.state.value.committing)

        // Send works while the sort waits.
        r.vm.setText("eggs")
        r.vm.send(AddOrigin.SHEET)
        runCurrent()
        assertEquals(AddPhase.Thinking(ItemSource.AI_TEXT), r.vm.state.value.phase)

        // Closing the sheet stops the request, not the sort.
        r.vm.closeSheet()
        sorted.complete(Unit)
        advanceUntilIdle()
        assertEquals("produce", r.store.current().list(f.LIST_ID)!!.items.single { it.id == star.id }.aisleId)
        assertTrue(events.none { it is AddEvent.SortFailed })
    }

    /** Rule 4: an answer with nothing to add is never a quick add, with or without a message: the draft stays. */
    @Test
    fun anEmptyAnswerWithNoMessageKeepsTheDraft() = runTest {
        for (message in listOf("", "Hello!")) {
            val r = rig(answer(message = message))
            val events = events(r.vm)
            r.vm.setText("hello")
            r.vm.send(AddOrigin.BAR)
            advanceUntilIdle()
            val s = r.vm.state.value
            assertEquals("\"$message\": the draft stays", "hello", s.text)
            assertTrue(events.none { it is AddEvent.Committed })
            assertTrue("the review opens with OK", s.sheetOpen && s.review!!.nothingToKeep)
            assertNull("an echo is not worth showing (the card says there was nothing to add)", s.review!!.message)
            r.vm.acknowledge()
            advanceUntilIdle()
            assertFalse(r.vm.state.value.sheetOpen)
            assertEquals("hello", r.vm.state.value.text)
            assertTrue(events.none { it is AddEvent.Committed })
        }
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

    /**
     * A list switched while a bar Send is being read: that add lands on the list it was sent to (its toast names that
     * list), and the next one goes to the list she is looking at now, not the old one for good.
     */
    @Test
    fun aListSwitchedWhileThinkingTakesTheNextAdd() = runTest {
        val costco = GroceryList("list-costco", "Costco", f.NOW, emptyList())
        val assistant = FakeAssistant(ingest = { input, _ ->
            delay(2_000)
            val name = (input as IngestInput.Text).text.replaceFirstChar { it.uppercase() }
            answer(f.proposed(name, "dish_soap", "household", "1 pack", named = true))
        })
        val r = addRig(testAppScope(), f.state.copy(lists = f.state.lists + costco), assistant)
        val events = events(r.vm)
        r.vm.setText("paper towels")
        r.vm.send(AddOrigin.BAR)
        advanceTimeBy(500)
        runCurrent()
        assertTrue(r.vm.state.value.waiting)
        r.vm.showList(costco.id)
        advanceUntilIdle()
        val first = (events.filterIsInstance<AddEvent.Committed>().single()).commit
        assertEquals("the add in flight keeps its list", f.LIST_ID, first.listId)
        assertTrue("its toast names that list", first.otherList)

        r.vm.setText("sponges")
        r.vm.send(AddOrigin.BAR)
        advanceUntilIdle()
        val second = events.filterIsInstance<AddEvent.Committed>().last().commit
        assertEquals("the next add goes to the list on screen", costco.id, second.listId)
        assertFalse(second.otherList)
        assertTrue(r.store.current().list(costco.id)!!.items.any { it.name == "Sponges" })
        assertTrue(r.store.current().list(f.LIST_ID)!!.items.none { it.name == "Sponges" })
    }

    // ---- rule 4: a row already on the list is a skipped row ----

    /** §6.2 rule 7: a name she wrote that is already unchecked on the list pops there, as it does without a key. */
    @Test
    fun aNamedDuplicatePopsWithoutTheSheet() = runTest {
        val r = rig(answer(f.proposed("Milk, 2 %", "milk_bag", "dairy-eggs", skip = SkipReason.ALREADY_ON_LIST, named = true), message = "Milk is already on your list."))
        val events = events(r.vm)
        r.vm.setText("milk")
        r.vm.send(AddOrigin.BAR)
        advanceUntilIdle()
        assertFalse("no review for one name already there", r.vm.state.value.sheetOpen)
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        assertTrue(commit.added.isEmpty())
        assertEquals(listOf("i-milk"), commit.alreadyThere.map { it.id })
        assertEquals(f.items.size, r.store.current().list(f.LIST_ID)!!.items.size)
    }

    @Test
    fun aNameAlreadyOnTheListShowsInReview() = runTest {
        val r = rig(answer(f.proposed("Milk, 2 %", "milk_bag", "dairy-eggs", skip = SkipReason.ALREADY_ON_LIST), message = "Milk is already on your list."))
        val events = events(r.vm)
        r.vm.setText("milk")
        r.vm.send(AddOrigin.BAR)
        advanceUntilIdle()
        val s = r.vm.state.value
        assertTrue("item 201: a worked-out row already there is not a quick add", s.sheetOpen)
        assertTrue("nothing can be switched on: the footer offers OK", s.review!!.nothingToKeep)
        assertTrue(events.none { it is AddEvent.Committed })

        // OK: the row already there pops ("Milk is already on your list"); nothing is added twice.
        r.vm.acknowledge()
        advanceUntilIdle()
        val commit = (events.single { it is AddEvent.Committed } as AddEvent.Committed).commit
        assertTrue(commit.added.isEmpty())
        assertEquals(listOf("i-milk"), commit.alreadyThere.map { it.id })
        assertFalse(r.vm.state.value.sheetOpen)
        assertEquals(f.items.size, r.store.current().list(f.LIST_ID)!!.items.size)
    }

    @Test
    fun anAnswerWithNothingToBuyOffersOkAndKeepsTheDraft() = runTest {
        val r = rig(answer(message = "Tell me what to buy, or a dish to cook, and I'll sort it into your aisles."))
        val events = events(r.vm)
        r.vm.setText("hello")
        r.vm.send()
        advanceUntilIdle()
        val review = r.vm.state.value.review!!
        assertTrue(review.rows.isEmpty())
        assertTrue(review.nothingToKeep)
        assertNotNull(review.message)
        r.vm.acknowledge()
        advanceUntilIdle()
        assertFalse(r.vm.state.value.sheetOpen)
        assertEquals("the draft stays to change", "hello", r.vm.state.value.text)
        assertTrue(events.none { it is AddEvent.Committed })
    }

    // ---- D19: a review is kept until it is committed ----

    @Test
    fun aSecondVoiceNoteKeepsTheFirstHeardRows() = runTest {
        var note = 0
        val assistant = FakeAssistant(
            ingest = { input, _ ->
                when {
                    input is IngestInput.Image -> f.photo()
                    note == 1 -> answer(f.proposed("Frozen corn", "frozen_vegetables", "frozen", "750 g bag", named = true))
                    else -> answer(f.proposed("Cat litter", "cat_litter", "household", "9 kg jug", named = true), f.proposed("Frozen corn", "frozen_vegetables", "frozen", named = true))
                }
            },
            transcribe = { Transcript("note ${++note}") },
        )
        val clip = tmp.newFile("note.m4a")
        val recorder = FakeRecorder(stopResult = { clip.apply { writeBytes(byteArrayOf(1, 2, 3)) } })
        val r = addRig(testAppScope(), f.state, assistant, recorder = recorder)
        r.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        advanceUntilIdle()
        val photo = r.vm.state.value.review!!
        for (i in 1..2) {
            r.vm.startRecording()
            advanceTimeBy(350)
            r.vm.stopRecording()
            advanceUntilIdle()
        }
        val merged = r.vm.state.value.review!!
        assertEquals("the same preview", photo.id, merged.id)
        assertEquals(photo.rows, merged.mainRows)
        assertEquals("the first note's corn stays; the second adds only the litter", listOf("Frozen corn", "Cat litter"), merged.heardRows.map { it.item.name })
        assertEquals("only the latest batch fans out", setOf(merged.heardRows.last().key), merged.heardFresh)
    }

    @Test
    fun aVoiceNoteJoinsANotesReviewAndAnotherVoiceNotesReview() = runTest {
        var spoken = 0
        val assistant = FakeAssistant(
            ingest = { input, _ ->
                when (input) {
                    is IngestInput.Transcript -> if (input.text == "first") f.voice() else answer(f.proposed("Maple syrup", "maple_syrup", "breakfast", named = true), f.proposed("Frozen corn", "frozen_vegetables", "frozen", named = true))
                    else -> f.pancakes()
                }
            },
            transcribe = { Transcript(if (++spoken == 1) "first" else "second") },
        )
        val clip = tmp.newFile("note2.m4a")
        val recorder = FakeRecorder(stopResult = { clip.apply { writeBytes(byteArrayOf(1, 2, 3)) } })

        // A note's recipe card, then Speak: the voice batch joins the card's review (it is never dropped).
        val r = addRig(testAppScope(), f.state, assistant, recorder = recorder)
        r.vm.setText(PANCAKES)
        r.vm.send(AddOrigin.SHEET)
        advanceUntilIdle()
        val recipe = r.vm.state.value.review!!
        assertTrue("Speak adds to a note's review", r.vm.state.value.tabEnabled(AddMode.SPEAK))
        r.vm.setMode(AddMode.SPEAK)
        r.vm.startRecording()
        advanceTimeBy(350)
        r.vm.stopRecording()
        advanceUntilIdle()
        val joined = r.vm.state.value.review!!
        assertEquals(recipe.id, joined.id)
        assertEquals(recipe.rows, joined.mainRows)
        assertEquals("eggs are already in the card: de-duplicated", listOf("Frozen corn", "Cat litter"), joined.heardRows.map { it.item.name })

        // A voice note's own review, then a second voice note: the first note's rows stay.
        spoken = 0
        val v = addRig(testAppScope(), f.state, assistant, recorder = recorder)
        v.vm.openSheet(AddMode.SPEAK)
        v.vm.startRecording()
        advanceTimeBy(350)
        v.vm.stopRecording()
        advanceUntilIdle()
        val first = v.vm.state.value.review!!
        assertEquals(ItemSource.VOICE, first.source)
        v.vm.startRecording()
        advanceTimeBy(350)
        v.vm.stopRecording()
        advanceUntilIdle()
        val second = v.vm.state.value.review!!
        assertEquals(first.id, second.id)
        assertEquals(first.rows, second.mainRows)
        assertEquals(listOf("Maple syrup"), second.heardRows.map { it.item.name })
        v.vm.commit()
        advanceUntilIdle()
        val names = v.store.current().list(f.LIST_ID)!!.items.map { it.name }
        assertTrue(names.containsAll(listOf("Frozen corn", "Cat litter", "Maple syrup")))
    }

    @Test
    fun aNewNoteOrPhotoNeverReplacesAnOpenReview() = runTest {
        val r = rig(f.pancakes())
        r.vm.setText(PANCAKES)
        r.vm.send(AddOrigin.SHEET)
        advanceUntilIdle()
        val review = r.vm.state.value.review!!
        // Snap would start another review: its tab is dimmed and a stray photo is discarded, not read.
        assertFalse(r.vm.state.value.tabEnabled(AddMode.SNAP))
        r.vm.setMode(AddMode.SNAP)
        assertEquals(AddMode.WRITE, r.vm.state.value.mode)
        val uri = Uri.parse("content://test/late.jpg")
        r.vm.onPhoto(uri)
        advanceUntilIdle()
        assertEquals(review, r.vm.state.value.review)
        assertEquals(listOf(uri), r.photos.discarded)
        assertEquals("no second request", 1, r.assistant.ingested.size)
        // Send and As typed wait too (the Write tab shows the card, not the field, meanwhile).
        r.vm.send(AddOrigin.SHEET)
        r.vm.addAsTyped()
        advanceUntilIdle()
        assertEquals(review, r.vm.state.value.review)
        assertEquals(1, r.assistant.ingested.size)
    }

    // ---- §14.3: the capsule's pop ----

    @Test
    fun theCapsulePopCuesOncePerReveal() = runTest {
        val r = rig(f.pancakes())
        r.vm.setText(PANCAKES)
        r.vm.send(AddOrigin.SHEET)
        advanceUntilIdle()
        val id = r.vm.state.value.review!!.id
        r.vm.capsulePopped(id)
        r.vm.capsulePopped(id)
        assertEquals("pon! once, through the ViewModel's own Feedback", 1, r.feedback.cues.count { it == Cue.CAPSULE_POP })
    }
}

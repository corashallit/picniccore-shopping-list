package earth.cora.picnic.ui.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.Transcript
import earth.cora.picnic.core.assistant.WebSource
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.FakePhotos
import earth.cora.picnic.ui.common.FakeRecorder
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.RecordingIconRepository
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.common.testAppScope
import earth.cora.picnic.ui.common.ScreenText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.IOException

/** The Add sheet's flows (docs/ARCHITECTURE.md §10.4) with a fake assistant, recorder and photo preparer. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AddViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val catalog = IconCatalog.default
    private var nextId = 0

    private class Fixture(
        val vm: AddViewModel,
        val store: InMemoryStateStore,
        val assistant: FakeAssistant,
        val icons: RecordingIconRepository,
        val photos: FakePhotos,
        val recorder: FakeRecorder,
        val events: MutableList<AddEvent>,
    )

    private fun TestScope.fixture(
        state: AppState = Samples.shoppingState,
        assistant: FakeAssistant = FakeAssistant(),
        recorder: FakeRecorder = FakeRecorder(),
        savedState: SavedStateHandle = SavedStateHandle(),
        open: Boolean = true,
    ): Fixture {
        val store = InMemoryStateStore(state)
        val icons = RecordingIconRepository()
        val photos = FakePhotos()
        val vm = AddViewModel(
            store = store,
            assistant = assistant,
            icons = icons,
            aisleSync = AisleSync(store, assistant, catalog.baskets),
            catalog = catalog,
            photos = photos,
            recorderFactory = { recorder },
            clock = { Samples.NOW + testScheduler.currentTime },
            newId = { "new-${nextId++}" },
            appScope = testAppScope(),
            savedState = savedState,
            ioDispatcher = main.dispatcher,
        )
        val events = mutableListOf<AddEvent>()
        testAppScope().launch { vm.events.collect { events += it } }
        if (open) vm.open(Samples.LIST_ID)
        return Fixture(vm, store, assistant, icons, photos, recorder, events)
    }

    private fun proposed(name: String, aisleId: String?, icon: String = "lemon", fit: IconFit = IconFit.EXACT, quantity: String? = null) =
        ProposedItem(name = name, quantity = quantity, note = null, aisleId = aisleId, icon = icon, iconFit = fit)

    private val pancakes = listOf(
        proposed("All-purpose flour", "aisle-2-baking-spices", icon = "flour", quantity = "2 cups"),
        proposed("Buttermilk", "dairy-eggs", icon = "dairy_generic", fit = IconFit.APPROXIMATE),
        proposed("Maple syrup", null, icon = "maple_syrup"),
        proposed("Baking soda", "aisle-2-baking-spices", icon = "baking_soda"),
    )

    @Test
    fun writeWithAiShowsThinkingThenAPreviewInWalkingOrder() = runTest {
        val gate = CompletableDeferred<Unit>()
        val assistant = FakeAssistant(ingest = { _, _ ->
            gate.await()
            FakeAssistant.result(
                pancakes,
                message = "Here's what you need for fluffy pancakes.",
                sources = listOf(WebSource("https://www.allrecipes.com/recipe/pancakes", "Fluffy pancakes")),
                skipped = listOf("Eggs"),
            )
        })
        val f = fixture(assistant = assistant)
        f.vm.setText("ingredients for pancakes from scratch")
        f.vm.submitText()
        runCurrent()
        assertEquals(AddPhase.Thinking(ItemSource.AI_TEXT, recipe = true), f.vm.state.value.phase)

        gate.complete(Unit)
        advanceUntilIdle()
        val s = f.vm.state.value
        assertEquals(AddPhase.Preview, s.phase)
        assertEquals(ItemSource.AI_TEXT, s.previewSource)
        // Unsorted first, then walking order (baking aisle before dairy); model order inside an aisle.
        assertEquals(listOf("Maple syrup", "All-purpose flour", "Baking soda", "Buttermilk"), s.preview.map { it.item.name })
        assertEquals(listOf(-1, 5, 5, 11), s.preview.map { it.aisleIndex })
        assertEquals("Aisle 2 - Baking & spices", s.preview[1].aisleTitle)
        assertEquals(listOf("Eggs"), s.skipped)
        assertEquals("Here's what you need for fluffy pancakes.", s.message)
        assertEquals(1, s.sources.size)

        val (input, ctx) = f.assistant.ingested.single()
        assertEquals(IngestInput.Text("ingredients for pancakes from scratch"), input)
        assertTrue("dedupe context has the list's unchecked names", "Lemons" in ctx.existingNames)
    }

    @Test
    fun commitAddsTheKeptItemsHandsThemToTheIconCascadeAndClosesTheSheet() = runTest {
        val f = fixture(state = Samples.emptyState, assistant = FakeAssistant(ingest = { _, _ -> FakeAssistant.result(pancakes) }))
        f.vm.setText("pancakes")
        f.vm.submitText()
        advanceUntilIdle()
        val buttermilk = f.vm.state.value.preview.first { it.item.name == "Buttermilk" }
        f.vm.toggleKeep(buttermilk.key)
        assertEquals(3, f.vm.state.value.keptCount)

        f.vm.commit()
        advanceUntilIdle()
        val items = f.store.current().currentList().items
        assertEquals(listOf("All-purpose flour", "Maple syrup", "Baking soda"), items.map { it.name })
        assertTrue(items.all { it.source == ItemSource.AI_TEXT })
        assertEquals(listOf("All-purpose flour", "Maple syrup", "Baking soda"), f.icons.submitted.single().map { it.name })
        assertEquals(listOf<AddEvent>(AddEvent.Added(3, Samples.LIST_ID)), f.events)
        assertEquals("the draft is cleared after a commit", "", f.vm.state.value.text)
        assertTrue("AI items are not re-sorted", f.assistant.recategorized.isEmpty())
    }

    @Test
    fun aFailedAiCallOffersPlainLinesWhichCommitAsTypedAndGetSortedInTheBackground() = runTest {
        val assistant = FakeAssistant(
            ingest = { _, _ -> throw LlmError.Network(IOException("offline")) },
            recategorize = { items, _ -> items.associate { it.id to "dairy-eggs" } },
        )
        val f = fixture(assistant = assistant)
        f.vm.setText("oat milk, 2 lemons\nlemons")
        f.vm.submitText()
        advanceUntilIdle()
        val failed = f.vm.state.value.phase as AddPhase.Failed
        assertTrue(failed.retryable)
        assertTrue(failed.canUsePlainLines)
        assertFalse(failed.needsSettings)

        f.vm.usePlainLines()
        advanceUntilIdle()
        val s = f.vm.state.value
        assertEquals(AddPhase.Preview, s.phase)
        assertEquals(ItemSource.TYPED, s.previewSource)
        // "Lemons" is already on the list, so only oat milk survives OfflineParser's dedupe.
        assertEquals(listOf("Oat milk"), s.preview.map { it.item.name })

        f.vm.commit()
        advanceUntilIdle()
        val added = f.store.current().currentList().items.last()
        assertEquals("Oat milk", added.name)
        assertEquals(ItemSource.TYPED, added.source)
        assertEquals("plant_milk", added.icon)
        assertEquals(listOf(added.id), f.assistant.recategorized.single().map { it.id })
        assertEquals("placed in the background", "dairy-eggs", f.store.current().currentList().items.last().aisleId)
    }

    @Test
    fun retryRepeatsTheSameRequest() = runTest {
        var calls = 0
        val f = fixture(assistant = FakeAssistant(ingest = { _, _ ->
            if (calls++ == 0) throw LlmError.Timeout() else FakeAssistant.result(pancakes)
        }))
        f.vm.setText("pancakes")
        f.vm.submitText()
        advanceUntilIdle()
        assertTrue(f.vm.state.value.phase is AddPhase.Failed)
        f.vm.retry()
        advanceUntilIdle()
        assertEquals(AddPhase.Preview, f.vm.state.value.phase)
        assertEquals(2, f.assistant.ingested.size)
    }

    @Test
    fun noTokenAsksForSettings() = runTest {
        val f = fixture(assistant = FakeAssistant(ingest = { _, _ -> throw LlmError.NotConfigured("no token") }))
        f.vm.setText("milk")
        f.vm.submitText()
        advanceUntilIdle()
        val failed = f.vm.state.value.phase as AddPhase.Failed
        assertTrue(failed.needsSettings)
        assertEquals(LlmError.NotConfigured("x").friendlyMessage, failed.message)
        // Typing again brings the normal buttons back.
        f.vm.setText("milk, eggs")
        assertEquals(AddPhase.Idle, f.vm.state.value.phase)
    }

    @Test
    fun addAsTypedCommitsAtOnceAndSortsTheUnsortedItems() = runTest {
        val assistant = FakeAssistant(recategorize = { items, _ -> items.associate { it.id to "aisle-6-household-cleaning" } })
        val f = fixture(assistant = assistant)
        f.vm.setText("- dish soap\n- 2 sponges")
        f.vm.addTyped()
        advanceUntilIdle()
        val added = f.store.current().currentList().items.takeLast(2)
        assertEquals(listOf("Dish soap", "Sponges"), added.map { it.name })
        assertEquals("2", added[1].quantity)
        assertEquals(listOf("dish_soap", "sponge"), added.map { it.icon })
        assertTrue(added.all { it.aisleId == "aisle-6-household-cleaning" })
        assertEquals(listOf<AddEvent>(AddEvent.Added(2, Samples.LIST_ID)), f.events)
        assertTrue(f.assistant.ingested.isEmpty())
    }

    @Test
    fun addAsTypedWithOnlyDuplicatesSaysSo() = runTest {
        val f = fixture()
        f.vm.setText("lemons, milk")
        f.vm.addTyped()
        advanceUntilIdle()
        assertEquals(listOf<AddEvent>(AddEvent.Message(ScreenText.NOTHING_NEW)), f.events)
    }

    @Test
    fun aPhotoIsReadDiscardedAndIngestedAndCanBeRetried() = runTest {
        var calls = 0
        val f = fixture(assistant = FakeAssistant(ingest = { _, _ ->
            if (calls++ == 0) throw LlmError.RateLimited(null) else FakeAssistant.result(pancakes.take(1))
        }))
        val uri = Uri.parse("content://earth.cora.picnic.fileprovider/captures/list_1.jpg")
        f.vm.onPhoto(uri)
        runCurrent()
        assertEquals(AddMode.SNAP, f.vm.state.value.mode)
        advanceUntilIdle()
        assertEquals(listOf(uri), f.photos.discarded)
        val failed = f.vm.state.value.phase as AddPhase.Failed
        assertTrue(failed.retryable)
        assertFalse("nothing to parse offline in a photo", failed.canUsePlainLines)

        f.vm.retry()
        advanceUntilIdle()
        assertEquals(AddPhase.Preview, f.vm.state.value.phase)
        assertEquals(ItemSource.PHOTO, f.vm.state.value.previewSource)
        val inputs = f.assistant.ingested.map { it.first }
        assertTrue(inputs.all { it is IngestInput.Image })
        assertEquals(2, inputs.size)
    }

    @Test
    fun aCameraResultAfterProcessDeathIsReadForTheCurrentListAndSurvivesAttach() = runTest {
        // The restored VM: the draft came back, no list attached yet, and the camera result arrives
        // during the first composition, before ListRoute's attachList.
        val restored = SavedStateHandle(mapOf("add.text" to "a draft", "add.mode" to "SNAP", "add.listId" to ""))
        val gate = CompletableDeferred<Unit>()
        val f = fixture(
            assistant = FakeAssistant(ingest = { _, _ ->
                gate.await()
                FakeAssistant.result(pancakes.take(2))
            }),
            savedState = restored,
            open = false,
        )
        assertEquals("a draft", f.vm.state.value.text)
        assertEquals(AddMode.SNAP, f.vm.state.value.mode)
        val uri = Uri.parse("content://earth.cora.picnic.fileprovider/captures/list_9.jpg")
        f.vm.onPhoto(uri)
        runCurrent()
        assertEquals(AddPhase.Thinking(ItemSource.PHOTO), f.vm.state.value.phase)

        f.vm.attachList(Samples.LIST_ID)
        runCurrent()
        assertEquals("attaching never resets the work", AddPhase.Thinking(ItemSource.PHOTO), f.vm.state.value.phase)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AddPhase.Preview, f.vm.state.value.phase)
        assertEquals(Samples.LIST_ID, f.vm.state.value.listId)
        assertTrue("the current list's names reach the dedupe context", "Lemons" in f.assistant.ingested.single().second.existingNames)

        f.vm.commit()
        advanceUntilIdle()
        // Committed to the current list (which already had one of the two, so it was deduped).
        assertEquals(listOf<AddEvent>(AddEvent.Added(1, Samples.LIST_ID)), f.events)
        assertEquals(listOf(uri), f.photos.discarded)
    }

    @Test
    fun theDraftModeAndListAreKeptForProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val f = fixture(savedState = handle)
        f.vm.setText("2 lemons, oat milk")
        f.vm.setMode(AddMode.SPEAK)
        runCurrent()
        assertEquals("2 lemons, oat milk", handle.get<String>("add.text"))
        assertEquals("SPEAK", handle.get<String>("add.mode"))
        assertEquals(Samples.LIST_ID, handle.get<String>("add.listId"))

        val again = fixture(savedState = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }), open = false)
        assertEquals("2 lemons, oat milk", again.vm.state.value.text)
        assertEquals(AddMode.SPEAK, again.vm.state.value.mode)
        assertEquals(Samples.LIST_ID, again.vm.state.value.listId)
        again.vm.attachList("another-list")
        assertEquals("a known list is kept", Samples.LIST_ID, again.vm.state.value.listId)
    }

    @Test
    fun anUnreadablePhotoSaysSo() = runTest {
        val f = fixture()
        f.photos.bytes = { throw IOException("bad jpeg") }
        f.vm.onPhoto(Uri.parse("content://media/picker/0/1"))
        advanceUntilIdle()
        assertEquals(ScreenText.PHOTO_UNREADABLE, (f.vm.state.value.phase as AddPhase.Failed).message)
        assertTrue(f.assistant.ingested.isEmpty())
    }

    @Test
    fun speakRecordsTranscribesShowsTheTranscriptAndIngestsIt() = runTest {
        val audio = tmp.newFile("voice.m4a").apply { writeBytes(ByteArray(2048) { 7 }) }
        val recorder = FakeRecorder(stopResult = { audio })
        val assistant = FakeAssistant(
            transcribe = { Transcript(" Milk, two lemons and stuff for tacos ") },
            ingest = { _, _ -> FakeAssistant.result(pancakes.take(2)) },
        )
        val f = fixture(assistant = assistant, recorder = recorder)
        f.vm.startRecording()
        advanceTimeBy(350)
        runCurrent()
        val recording = f.vm.state.value.phase as AddPhase.Recording
        assertEquals(AddMode.SPEAK, f.vm.state.value.mode)
        assertEquals(3, recording.levels.size)
        assertEquals(300, recording.elapsedMs)

        f.vm.stopRecording()
        advanceUntilIdle()
        val s = f.vm.state.value
        assertEquals("Milk, two lemons and stuff for tacos", s.transcript)
        assertEquals(AddPhase.Preview, s.phase)
        assertEquals(ItemSource.VOICE, s.previewSource)
        assertFalse("the recording is deleted after reading", audio.exists())
        val (clip, keywords) = f.assistant.transcribed.single()
        assertEquals(2048, clip.bytes.size)
        assertEquals("recording.m4a", clip.fileName)
        assertTrue("list names help the transcriber", "Gochujang" in keywords)
        assertEquals(IngestInput.Transcript("Milk, two lemons and stuff for tacos"), f.assistant.ingested.single().first)
    }

    @Test
    fun silenceSaysDidntCatchThat() = runTest {
        val audio = tmp.newFile("quiet.m4a").apply { writeBytes(ByteArray(2048)) }
        val f = fixture(
            assistant = FakeAssistant(transcribe = { Transcript("") }),
            recorder = FakeRecorder(stopResult = { audio }),
        )
        f.vm.startRecording()
        f.vm.stopRecording()
        advanceUntilIdle()
        assertEquals(ScreenText.NOTHING_HEARD, (f.vm.state.value.phase as AddPhase.Failed).message)
        assertNull(f.vm.state.value.transcript)
    }

    @Test
    fun aTooShortRecordingSaysSo() = runTest {
        val f = fixture(recorder = FakeRecorder(stopResult = { null }))
        f.vm.startRecording()
        f.vm.stopRecording()
        advanceUntilIdle()
        assertEquals(ScreenText.TOO_SHORT, (f.vm.state.value.phase as AddPhase.Failed).message)
        assertTrue(f.assistant.transcribed.isEmpty())
    }

    @Test
    fun aBusyMicSaysSo() = runTest {
        val f = fixture(recorder = FakeRecorder(failOnStart = true))
        f.vm.startRecording()
        assertEquals(ScreenText.MIC_UNAVAILABLE, (f.vm.state.value.phase as AddPhase.Failed).message)
    }

    @Test
    fun cancelStopsTheCallAndKeepsTheText() = runTest {
        val never = CompletableDeferred<Unit>()
        val f = fixture(assistant = FakeAssistant(ingest = { _, _ ->
            never.await()
            error("unreachable")
        }))
        f.vm.setText("stuff for tacos")
        f.vm.submitText()
        runCurrent()
        f.vm.cancel()
        advanceUntilIdle()
        assertEquals(AddPhase.Idle, f.vm.state.value.phase)
        assertEquals("stuff for tacos", f.vm.state.value.text)
    }

    @Test
    fun closingKeepsOnlyTheDraftAndCancelsRecording() = runTest {
        val recorder = FakeRecorder()
        val f = fixture(recorder = recorder)
        f.vm.setText("half-typed")
        f.vm.setMode(AddMode.SPEAK)
        f.vm.startRecording()
        f.vm.close()
        assertEquals(1, recorder.cancelled)
        assertEquals(AddUiState(listId = "", text = "half-typed"), f.vm.state.value)

        f.vm.open("other-list", AddMode.SNAP)
        assertEquals(AddUiState(listId = "other-list", mode = AddMode.SNAP, text = "half-typed"), f.vm.state.value)
    }

    @Test
    fun editTranscriptMovesItToTheWriteTab() = runTest {
        val audio = tmp.newFile("v.m4a").apply { writeBytes(ByteArray(2048)) }
        val f = fixture(
            assistant = FakeAssistant(transcribe = { Transcript("milk and bred") }, ingest = { _, _ -> throw LlmError.Timeout() }),
            recorder = FakeRecorder(stopResult = { audio }),
        )
        f.vm.startRecording()
        f.vm.stopRecording()
        advanceUntilIdle()
        assertTrue((f.vm.state.value.phase as AddPhase.Failed).canUsePlainLines)
        f.vm.editTranscript()
        val s = f.vm.state.value
        assertEquals(AddMode.WRITE, s.mode)
        assertEquals("milk and bred", s.text)
        assertEquals(AddPhase.Idle, s.phase)
    }

    @Test
    fun recipeGuessForTheThinkingLabel() {
        assertTrue(looksLikeRecipe("ingredients for pancakes from scratch"))
        assertTrue(looksLikeRecipe("stuff for tacos"))
        assertTrue(looksLikeRecipe("https://www.allrecipes.com/recipe/21014/good-old-fashioned-pancakes/"))
        assertFalse(looksLikeRecipe("milk, 2 lemons, eggs"))
        assertFalse(looksLikeRecipe("bananas"))
    }
}

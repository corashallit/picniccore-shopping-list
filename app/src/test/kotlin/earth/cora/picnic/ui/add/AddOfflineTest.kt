package earth.cora.picnic.ui.add

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.Transcript
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.list
import earth.cora.picnic.platform.CaptureKind
import earth.cora.picnic.platform.FileCaptureKeeper
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.FakeRecorder
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.testAppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * The add flows offline (docs/DESIGN_STICKER.md §0.4, 5 October; §6.2 rule 2b; items 350, 351, 358): the bar's Send adds the
 * note as typed at once, with one toast and Undo, and so does a request that never reached OpenAI; the sheet's Write
 * Send, Snap and Speak fail at once with the offline words and no request; "As typed" offline gives one note, never a
 * second "couldn't sort" one; a photo or voice note that could not be sent is kept (closing the sheet, a new
 * ViewModel after process death, a fresh start) until it is sent, replaced or deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AddOfflineTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val f = AddFixtures
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val offlineWords = "You’re offline right now. Try again once you’re back online."

    private fun TestScope.events(vm: AddViewModel): MutableList<AddEvent> {
        val out = mutableListOf<AddEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(out) }
        return out
    }

    /** A keeper on a temporary folder, its file work on the test's thread. */
    private fun keeper(dir: File = tmp.newFolder(), now: () -> Long = { f.NOW }) = FileCaptureKeeper(dir, now, io = Dispatchers.Unconfined)

    private fun answer(vararg items: earth.cora.picnic.core.assistant.ProposedItem) = IngestResult(items.toList(), "", emptyList(), emptyList(), f.meta())

    private fun commits(events: List<AddEvent>) = events.filterIsInstance<AddEvent.Committed>().map { it.commit }

    @Test
    fun offlineBarSendAddsAsTypedWithOneToastAndUndo() = runTest {
        val assistant = FakeAssistant(ingest = { _, _ -> error("no request offline") })
        val r = addRig(testAppScope(), f.state, assistant, online = { false })
        val events = events(r.vm)
        val before = r.store.current().list(f.LIST_ID)!!.items.map { it.id }

        r.vm.setText("paper towels, lip balm")
        r.vm.send()
        advanceUntilIdle()

        assertTrue("no ingest offline", assistant.ingested.isEmpty())
        assertTrue("no sort offline: the catch-up does it", assistant.recategorized.isEmpty())
        val commit = commits(events).single()
        assertTrue(commit.asTypedOffline)
        assertFalse(commit.asTypedNoKey)
        assertEquals(listOf("Paper towels", "Lip balm"), commit.added.map { it.name })
        assertTrue(commit.added.all { it.source == ItemSource.TYPED && it.aisleId == null })
        assertTrue("the bar keeps its field for the next thing", commit.fromBar)
        assertEquals("", r.vm.state.value.text)
        val note = commit.toast(resources) { r.vm.undo(commit.id) }
        assertEquals("Added as typed · sorting waits until you’re back online", note.text)
        assertEquals("Undo", note.actionLabel)
        assertTrue("one note only", events.none { it is AddEvent.SortFailed || it is AddEvent.SortWaits })
        assertFalse("no Move on an add as typed", commit.movable)

        note.onAction!!()
        advanceUntilIdle()
        assertEquals("Undo takes them back", before, r.store.current().list(f.LIST_ID)!!.items.map { it.id })
    }

    @Test
    fun withoutAKeyTheNoKeyRuleStillComesFirst() = runTest {
        val r = addRig(testAppScope(), f.state, FakeAssistant(), hasKey = false, online = { false })
        val events = events(r.vm)
        r.vm.setText("paper towels")
        r.vm.send()
        advanceUntilIdle()
        val commit = commits(events).single()
        assertTrue(commit.asTypedNoKey)
        assertFalse("no promise of sorting without a key", commit.asTypedOffline)
        assertEquals("Added as typed · add your OpenAI key to sort it", commit.toastText(resources))
    }

    @Test
    fun aRequestThatNeverReachedOpenAiAddsAsTypedToo() = runTest {
        val assistant = FakeAssistant(ingest = { _, _ -> throw LlmError.Network(IOException("connect timed out"), beforeConnect = true) })
        val r = addRig(testAppScope(), f.state, assistant)
        val events = events(r.vm)
        r.vm.setText("paper towels")
        r.vm.send()
        advanceUntilIdle()
        assertEquals("it was tried once", 1, assistant.ingested.size)
        val commit = commits(events).single()
        assertTrue(commit.asTypedOffline)
        assertEquals("Paper towels", commit.added.single().name)
        assertTrue("no error state", r.vm.state.value.phase !is AddPhase.Failed)
        assertFalse(r.vm.state.value.sheetOpen)
    }

    @Test
    fun aConnectedFailureKeepsTheErrorWithTryAgain() = runTest {
        val assistant = FakeAssistant(ingest = { _, _ -> throw LlmError.Timeout() })
        val r = addRig(testAppScope(), f.state, assistant)
        val events = events(r.vm)
        r.vm.setText("paper towels")
        r.vm.send()
        advanceUntilIdle()
        assertTrue(commits(events).isEmpty())
        val failed = r.vm.state.value.phase as AddPhase.Failed
        assertTrue(failed.retryable && failed.canUsePlainLines)
        assertFalse(failed.offline)
    }

    @Test
    fun writeSendOfflineFailsAtOnceAndKeepsAsTyped() = runTest {
        val assistant = FakeAssistant(ingest = { _, _ -> error("no request offline") })
        val r = addRig(testAppScope(), f.state, assistant, online = { false })
        r.vm.openSheet(AddMode.WRITE)
        r.vm.setText("pancakes from scratch")
        r.vm.send(AddOrigin.SHEET)
        advanceUntilIdle()
        assertTrue(assistant.ingested.isEmpty())
        val s = r.vm.state.value
        val failed = s.phase as AddPhase.Failed
        assertEquals(offlineWords, failed.message)
        assertTrue(failed.offline && failed.retryable && failed.canUsePlainLines)
        assertEquals(AddMode.WRITE, s.mode)
        assertEquals("the note stays", "pancakes from scratch", s.text)
    }

    @Test
    fun asTypedOfflineGivesOneNoteThatSaysTheSortingWaits() = runTest {
        var online = false
        val assistant = FakeAssistant()
        val r = addRig(testAppScope(), f.state, assistant, online = { online })
        val events = events(r.vm)
        r.vm.openSheet(AddMode.WRITE)
        r.vm.setText("lip balm")
        r.vm.addAsTyped()
        advanceUntilIdle()
        val commit = commits(events).single()
        assertTrue(commit.asTypedOffline)
        assertEquals("Added as typed · sorting waits until you’re back online", commit.toastText(resources))
        assertTrue("no second note", events.none { it is AddEvent.SortFailed || it is AddEvent.SortWaits })
        assertTrue(assistant.recategorized.isEmpty())

        // Online, "As typed" sorts in the background as before (the usual note, no promise).
        online = true
        r.vm.setText("clothespins")
        r.vm.addAsTyped()
        advanceUntilIdle()
        val second = commits(events).last()
        assertFalse(second.asTypedOffline)
        assertEquals(1, assistant.recategorized.size)
    }

    @Test
    fun aSortThatNeverReachesOpenAiSaysItWaits() = runTest {
        val assistant = FakeAssistant(recategorize = { _, _ -> throw LlmError.Offline() })
        val r = addRig(testAppScope(), f.state, assistant)
        val events = events(r.vm)
        r.vm.setText("lip balm")
        r.vm.addAsTyped()
        advanceUntilIdle()
        assertFalse(commits(events).single().asTypedOffline)
        assertEquals(1, events.filterIsInstance<AddEvent.SortWaits>().size)
        assertTrue(events.none { it is AddEvent.SortFailed })
    }

    @Test
    fun snapOfflineFailsAtOnceWithNoRequestAndKeepsThePhoto() = runTest {
        val dir = tmp.newFolder()
        val assistant = FakeAssistant(ingest = { _, _ -> error("no request offline") })
        val r = addRig(testAppScope(), f.state, assistant, online = { false }, keeper = keeper(dir))
        r.photos.bytes = { byteArrayOf(7, 7, 7) }
        r.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        advanceUntilIdle()
        assertTrue(assistant.ingested.isEmpty())
        val s = r.vm.state.value
        assertEquals(AddMode.SNAP, s.mode)
        assertEquals(offlineWords, (s.phase as AddPhase.Failed).message)
        assertTrue((s.phase as AddPhase.Failed).retryable)
        assertNotNull("the polaroid stays", s.photo)
        assertEquals(CaptureKind.PHOTO, s.kept)
        assertArrayEquals(byteArrayOf(7, 7, 7), dir.listFiles()!!.single { it.name.startsWith("photo-") }.readBytes())
        assertTrue(r.vm.hasKept(AddMode.SNAP))
    }

    @Test
    fun speakOfflineFailsAtOnceWithNoRequestAndKeepsTheRecording() = runTest {
        val dir = tmp.newFolder()
        val clip = tmp.newFile("clip.m4a").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val assistant = FakeAssistant(transcribe = { error("no request offline") })
        val r = addRig(testAppScope(), f.state, assistant, recorder = FakeRecorder(stopResult = { clip }), online = { false }, keeper = keeper(dir))
        r.vm.startRecording()
        r.vm.stopRecording()
        advanceUntilIdle()
        assertTrue(assistant.transcribed.isEmpty())
        val s = r.vm.state.value
        assertEquals(offlineWords, (s.phase as AddPhase.Failed).message)
        assertEquals(CaptureKind.VOICE, s.kept)
        assertArrayEquals(byteArrayOf(4, 5, 6), dir.listFiles()!!.single { it.name.startsWith("voice-") }.readBytes())
    }

    @Test
    fun aKeptPhotoComesBackAfterClosingTheSheetAndGoesOnceSent() = runTest {
        val dir = tmp.newFolder()
        var online = false
        val assistant = FakeAssistant(ingest = { _, _ -> answer(f.proposed("Eggs", "eggs", "dairy-eggs", "12")) })
        val r = addRig(testAppScope(), f.state, assistant, online = { online }, keeper = keeper(dir))
        r.photos.bytes = { byteArrayOf(9, 8, 7) }
        r.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        advanceUntilIdle()
        r.vm.closeSheet()
        assertNull(r.vm.state.value.photo)

        // The bar's camera (or the Snap tab) shows it again, with Try again; the footer still takes a new one.
        assertTrue(r.vm.showKept(AddMode.SNAP))
        advanceUntilIdle()
        val s = r.vm.state.value
        assertTrue(s.sheetOpen)
        assertEquals(AddMode.SNAP, s.mode)
        assertNotNull(s.photo)
        assertEquals(CaptureKind.PHOTO, s.kept)
        assertEquals(offlineWords, (s.phase as AddPhase.Failed).message)

        online = true
        r.vm.retry()
        advanceUntilIdle()
        assertArrayEquals("the same photo is sent", byteArrayOf(9, 8, 7), (assistant.ingested.single().first as IngestInput.Image).bytes)
        assertEquals(AddPhase.Preview, r.vm.state.value.phase)
        assertFalse("sent: no longer kept", r.vm.hasKept(AddMode.SNAP))
        assertTrue("its file is gone", dir.listFiles()!!.none { it.name.startsWith("photo-") })
    }

    @Test
    fun aKeptPhotoSurvivesProcessDeathAndAFreshStart() = runTest {
        val dir = tmp.newFolder()
        val saved = SavedStateHandle()
        val first = addRig(testAppScope(), f.state, FakeAssistant(), online = { false }, keeper = keeper(dir), savedState = saved)
        first.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        advanceUntilIdle()
        assertNotNull("the pointer is saved with the draft", saved.get<String>("add.kept.photo"))

        // Process death: a new ViewModel on the saved state finds it, and shows it with its own words when Snap opens.
        val again = addRig(testAppScope(), f.state, FakeAssistant(), online = { false }, keeper = keeper(dir), savedState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertTrue(again.vm.hasKept(AddMode.SNAP))
        again.vm.openSheet(AddMode.SNAP)
        advanceUntilIdle()
        val s = again.vm.state.value
        assertEquals(CaptureKind.PHOTO, s.kept)
        assertEquals(AddProblem.KEPT_PHOTO, (s.phase as AddPhase.Failed).problem)
        assertTrue((s.phase as AddPhase.Failed).retryable)

        // A fresh start (no saved state at all) still finds it on the phone.
        val fresh = addRig(testAppScope(), f.state, FakeAssistant(), keeper = keeper(dir))
        advanceUntilIdle()
        assertTrue(fresh.vm.hasKept(AddMode.SNAP))
    }

    @Test
    fun deletingTheKeptPhotoRemovesItsFile() = runTest {
        val dir = tmp.newFolder()
        val r = addRig(testAppScope(), f.state, FakeAssistant(), online = { false }, keeper = keeper(dir))
        r.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        advanceUntilIdle()
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("photo-") })
        r.vm.discardKept()
        advanceUntilIdle()
        assertNull(r.vm.state.value.kept)
        assertNull(r.vm.state.value.photo)
        assertFalse(r.vm.hasKept(AddMode.SNAP))
        assertTrue(dir.listFiles()!!.none { it.name.startsWith("photo-") })
    }

    @Test
    fun aNewPhotoReplacesTheKeptOne() = runTest {
        val dir = tmp.newFolder()
        var online = false
        val r = addRig(testAppScope(), f.state, FakeAssistant(ingest = { _, _ -> answer() }), online = { online }, keeper = keeper(dir))
        r.photos.bytes = { byteArrayOf(1) }
        r.vm.onPhoto(Uri.parse("content://test/one.jpg"))
        advanceUntilIdle()
        r.vm.closeSheet()
        online = true
        r.photos.bytes = { byteArrayOf(2) }
        r.vm.onPhoto(Uri.parse("content://test/two.jpg"))
        advanceUntilIdle()
        assertFalse(r.vm.hasKept(AddMode.SNAP))
        assertTrue(dir.listFiles()!!.none { it.name.startsWith("photo-") })
    }

    @Test
    fun aKeptVoiceNoteIsTriedAgainFromItsWords() = runTest {
        val dir = tmp.newFolder()
        val clip = tmp.newFile("clip.m4a").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        var ingestOnline = false
        val assistant = FakeAssistant(
            transcribe = { Transcript("eggs and milk") },
            ingest = { _, _ -> if (ingestOnline) answer(f.proposed("Eggs", "eggs", "dairy-eggs", "12")) else throw LlmError.Timeout() },
        )
        val r = addRig(testAppScope(), f.state, assistant, recorder = FakeRecorder(stopResult = { clip }), keeper = keeper(dir))
        r.vm.startRecording()
        r.vm.stopRecording()
        advanceUntilIdle()
        assertEquals(CaptureKind.VOICE, r.vm.state.value.kept)
        r.vm.closeSheet()
        assertTrue(r.vm.showKept(AddMode.SPEAK))
        advanceUntilIdle()
        assertEquals("eggs and milk", r.vm.state.value.transcript)
        ingestOnline = true
        r.vm.retry()
        advanceUntilIdle()
        assertEquals("written down once: Try again reads the words", 1, assistant.transcribed.size)
        assertEquals(AddPhase.Preview, r.vm.state.value.phase)
        assertFalse(r.vm.hasKept(AddMode.SPEAK))
        assertTrue(dir.listFiles()!!.none { it.name.startsWith("voice-") })
    }

    @Test
    fun keptCapturesAreSweptAfterSevenDays() = runTest {
        val dir = tmp.newFolder()
        var now = f.NOW
        val keeper = keeper(dir) { now }
        keeper.keep(CaptureKind.PHOTO, byteArrayOf(1))
        now += 6 * 24 * 60 * 60 * 1000L
        keeper.keep(CaptureKind.VOICE, byteArrayOf(2))
        now += 24 * 60 * 60 * 1000L + 1
        keeper.sweep()
        assertNull("the photo is over 7 days old", keeper.latest(CaptureKind.PHOTO))
        assertNotNull("the voice note is a day old", keeper.latest(CaptureKind.VOICE))
    }
}

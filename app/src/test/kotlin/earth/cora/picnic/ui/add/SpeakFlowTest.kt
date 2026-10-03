package earth.cora.picnic.ui.add

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.Transcript
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.Haptic
import earth.cora.picnic.platform.Sound
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.FakeRecorder
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Speak (docs/DESIGN_STICKER.md §4.20, §6.2 "Speak"; item 210): the microphone permission, the reels at 8 fps, the dot
 * equaliser in whole dots, the LCD timer and REC dot, the cassette cues (clunk + KEYBOARD_TAP), and the Heard tray.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class SpeakFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val f = AddFixtures

    private val speak get() = compose.onNode(hasContentDescription("Speak a voice note") and hasClickAction())

    @Test
    fun asksForTheMicrophone() {
        val recorder = FakeRecorder()
        val h = compose.setHarness(recorder = recorder, fakeSources = false)
        speak.performClick()
        compose.waitForIdle()
        val asked = shadowOf(compose.activity).lastRequestedPermission
        assertNotNull("Speak asks for RECORD_AUDIO", asked)
        assertTrue(Manifest.permission.RECORD_AUDIO in asked!!.requestedPermissions)
        assertEquals("nothing records before she allows it", 0, recorder.started)

        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        speak.performClick()
        compose.waitForIdle()
        assertEquals(1, recorder.started)
        assertTrue(h.vm.state.value.recording)
        assertEquals(AddMode.SPEAK, h.vm.state.value.mode)
        compose.onNodeWithTag(STOP_STICKER_TAG).assert(hasContentDescription("Stop recording")).assert(hasRole(Role.Button))
        h.vm.cancel()
    }

    @Test
    fun reelsTurnAtEightFps() {
        // 8 steps a second, 45° each: one full turn per second, held between steps.
        assertEquals(0f, reelAngle(0))
        assertEquals(0f, reelAngle(124))
        assertEquals(45f, reelAngle(125))
        assertEquals(90f, reelAngle(250))
        assertEquals(315f, reelAngle(999))
        assertEquals(0f, reelAngle(1_000))
        assertEquals(8, (0 until 1_000 step 5).map { reelAngle(it.toLong()) }.distinct().size)
    }

    @Test
    fun equaliserLightsWholeDots() {
        assertEquals(0, litDots(0f))
        assertEquals(1, litDots(0.01f))
        assertEquals(3, litDots(0.5f))
        assertEquals(4, litDots(0.51f))
        assertEquals(6, litDots(1f))
        assertEquals(6, litDots(1.4f))
        val columns = equaliserColumns(LEVELS, 1_000, reduced = false)
        assertEquals(EQ_COLUMNS, columns.size)
        assertTrue(columns.all { it in 0..EQ_ROWS })
        // The newest level is on the right.
        assertEquals(litDots(LEVELS.last()), columns.last())
        // Reduced motion: every column shows the current level, still.
        assertEquals(List(EQ_COLUMNS) { litDots(LEVELS.last()) }, equaliserColumns(LEVELS, 1_000, reduced = true))
        // Nothing heard yet: all dots off.
        assertEquals(List(EQ_COLUMNS) { 0 }, equaliserColumns(emptyList(), 0, reduced = false))
    }

    @Test
    fun recordingShowsTheTimerAndTheRecDot() {
        val state = AddUiState(
            listId = f.LIST_ID, sheetOpen = true, mode = AddMode.SPEAK,
            phase = AddPhase.Recording(elapsedMs = 12_400, level = 0.72f, levels = LEVELS),
        )
        compose.setContent { PicnicTheme(ThemeVariant.Day) { SheetStage { AddSheet(state, AddActions()) } } }
        compose.waitForIdle()
        compose.onNodeWithTag(REC_DOT_TAG, useUnmergedTree = true).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Recording, 0:12"))).assertExists()
        compose.onNode(hasText("0:12"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(EQUALISER_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(STOP_STICKER_TAG).assertExists()
        compose.onNodeWithTag(RECORD_STICKER_TAG).assertDoesNotExist()
        compose.onNodeWithText("Recordings stop by themselves after 5 minutes.").assertExists()
        // The Speak tab says it is recording.
        compose.onNode(hasRole(Role.Tab) and hasText("Speak")).assert(hasStateDescription("recording"))
    }

    /**
     * §6.2 Speak: the microphone turned off for good. The words say where to allow it, and "Open phone settings" goes
     * there (Android's page for this app), so tapping the record sticker is not the only, failing, way.
     */
    @Test
    fun aDeniedMicrophoneOffersThePhonesSettings() {
        val h = compose.setHarness()
        h.vm.micDenied()
        compose.waitForIdle()
        compose.onNodeWithText("Speak needs the microphone. You can allow it in the phone's settings.").assertExists()
        compose.onNode(hasText("Open phone settings") and hasClickAction()).performClick()
        compose.waitForIdle()
        val started = shadowOf(compose.activity).nextStartedActivity
        assertNotNull("Android's app settings page opens", started)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:${compose.activity.packageName}", started.data.toString())
    }

    @Test
    fun cassetteCues() {
        assertEquals(Sound.CLUNK, Cue.CASSETTE.sound)
        assertEquals(Haptic.KEYBOARD_TAP, Cue.CASSETTE.haptic)
        val h = compose.setHarness()
        h.vm.startRecording()
        compose.waitForIdle()
        h.vm.stopRecording()
        compose.waitForIdle()
        assertEquals("clunk on start and on stop", listOf(Cue.CASSETTE, Cue.CASSETTE), h.rig.feedback.cues.filter { it == Cue.CASSETTE })
    }

    @Test
    fun theHeardTrayFills() {
        val clip = tmp.newFile("note.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val h = compose.setHarness(
            FakeAssistant(ingest = { _, _ -> f.voice() }, transcribe = { Transcript(f.TRANSCRIPT) }),
            recorder = FakeRecorder(stopResult = { clip }),
        )
        h.vm.startRecording()
        compose.waitForIdle()
        compose.onNodeWithText("Say your list out loud.").assertExists()
        compose.onNodeWithTag(STOP_STICKER_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(HEARD_TRAY_TAG).assertExists()
        compose.onNode(hasText("Heard · 3 stickers"), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("“${f.TRANSCRIPT}”").assertExists()
        val stick = compose.onNode(hasText("Stick all 3 on my list") and hasClickAction())
        stick.performClick()
        compose.waitForIdle()
        val commit = h.commits.single()
        assertEquals(listOf("Frozen corn", "Cat litter", "Eggs"), commit.added.map { it.name })
        assertTrue(commit.added.all { it.source == ItemSource.VOICE })
        assertTrue("the recording is not kept", !clip.exists())
    }
}

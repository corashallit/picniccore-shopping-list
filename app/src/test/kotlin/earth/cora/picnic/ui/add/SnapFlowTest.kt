package earth.cora.picnic.ui.add

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasClickAction
import earth.cora.picnic.core.llm.LlmError
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Snap (docs/DESIGN_STICKER.md §4.19; item 207): the polaroid while reading with "Reading…" in the footer, then the lines
 * 250 ms apart (crossed-out struck and dropped, corrections with an arrow), each kept line popping into a sticker, and
 * the footer going from "Reading… n of m" to "Stick all".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class SnapFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val f = AddFixtures

    @Test
    fun theCameraButtonAndTheSheetTakeAPhoto() {
        val h = compose.setHarness()
        compose.onNode(hasContentDescription("Snap a photo of a list")).performClick()
        compose.waitForIdle()
        assertEquals(1, h.sources.photos)
        h.vm.openSheet(AddMode.SNAP, f.LIST_ID)
        compose.waitForIdle()
        compose.onNode(hasText("Take a photo")).performClick()
        compose.waitForIdle()
        assertEquals(2, h.sources.photos)
    }

    @Test
    fun readingShowsThePolaroid() {
        val gate = CompletableDeferred<IngestResult>()
        val h = compose.setHarness(FakeAssistant(ingest = { _, _ -> gate.await() }))
        h.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        compose.waitForIdle()
        val s = h.vm.state.value
        assertTrue(s.sheetOpen)
        assertEquals(AddMode.SNAP, s.mode)
        compose.onNodeWithTag(POLAROID_TAG).assertExists()
        compose.onNodeWithText("Reading your list…").assertExists()
        compose.onNode(hasText("Reading…"), useUnmergedTree = true).assertExists()
        gate.complete(f.photo())
        compose.waitForIdle()
        compose.onNodeWithText("Reading your list…").assertDoesNotExist()
        compose.onNodeWithTag(POPPED_TRAY_TAG).assertExists()
        compose.onNode(hasText("Stick all 5 on my list"), useUnmergedTree = true).assertExists()
        assertEquals("the photo was discarded once read", 1, h.rig.photos.discarded.size)
    }

    @Test
    fun linesRevealThenPop() {
        var state by mutableStateOf(
            AddUiState(
                listId = f.LIST_ID, sheetOpen = true, mode = AddMode.SNAP, phase = AddPhase.Preview,
                photo = SnapPhoto(null, f.NOW),
                review = f.review(4, f.photo(), ItemSource.PHOTO, null, revealed = false),
            ),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(ThemeVariant.Day, reducedMotion = false) {
                SheetStage {
                    AddSheet(state, AddActions(onRevealed = { id -> state = state.copy(review = state.review!!.copy(revealed = state.review!!.revealed + id)) }))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        fun line(spoken: String) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(spoken)))
        fun says(text: String) = compose.onNode(hasText(text), useUnmergedTree = true).assertExists()

        // The first line lands at once; the next 250 ms later.
        compose.mainClock.advanceTimeBy(40)
        line("milk (bag)").assertExists()
        line("eggs ×12").assertDoesNotExist()
        says("Reading your list…")
        says("Reading… 0 of 7")
        compose.mainClock.advanceTimeBy(240)
        line("eggs ×12").assertExists()
        says("Reading… 1 of 7")
        // A correction reads "bread → sourdough"; the crossed-out line is struck and pops nothing.
        compose.mainClock.advanceTimeBy(1_000)
        line("bread → sourdough").assertExists()
        line("chips, crossed out").assertExists()
        says("Reading… 5 of 7")
        compose.mainClock.advanceTimeBy(1_000)
        says("Stick all 5 on my list")
        says("7 of 7")
        compose.onNodeWithText("Reading your list…").assertDoesNotExist()
        // After the photo, Speak can add more (D19).
        compose.onNodeWithText("Anything else? Say it out loud.").assertExists()
        assertEquals(setOf(4L), state.review!!.revealed)
    }

    /** Item 207: a photo that cannot be opened leaves no dead end: the empty polaroid, the chip and "Take a photo". */
    @Test
    fun anUnreadablePhotoOffersAnotherOne() {
        val h = compose.setHarness()
        h.rig.photos.bytes = { throw java.io.IOException("gone") }
        h.vm.onPhoto(Uri.parse("content://test/gone.jpg"))
        compose.waitForIdle()
        compose.onNodeWithText("Couldn't open that photo. Try another one?").assertExists()
        compose.onNodeWithTag(CHOOSE_PHOTO_CHIP_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, h.sources.picks)
        compose.onNode(hasText("Take a photo") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(1, h.sources.photos)
    }

    /**
     * Item 215: an answer that cannot be retried (a refusal, an account out of credit) keeps the photo and its error,
     * and offers another photo: the chip under the error and "Take a photo" in the footer.
     */
    @Test
    fun aRefusedPhotoOffersAnotherOne() {
        val h = compose.setHarness()
        for ((i, error) in listOf(LlmError.Refusal("no"), LlmError.QuotaExceeded("insufficient_quota")).withIndex()) {
            h.rig.assistant.ingest = { _, _ -> throw error }
            h.vm.onPhoto(Uri.parse("content://test/photo$i.jpg"))
            compose.waitForIdle()
            compose.onNodeWithText(error.friendlyMessage).assertExists()
            compose.onNode(hasText("Try again")).assertDoesNotExist()
            compose.onNodeWithTag(POLAROID_TAG).assertExists()
            compose.onNodeWithTag(CHOOSE_PHOTO_CHIP_TAG).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(i + 1, h.sources.picks)
            compose.onNode(hasText("Take a photo") and hasClickAction()).performClick()
            compose.waitForIdle()
            assertEquals(i + 1, h.sources.photos)
        }
    }

    /** §6.2 Snap: with no camera app, "Take a photo" could only fail again; "Choose a photo" is the way. */
    @Test
    fun noCameraAppOffersOnlyThePicker() {
        val h = compose.setHarness()
        h.vm.cameraUnavailable()
        compose.waitForIdle()
        compose.onNodeWithText("There's no camera app on this phone. Choose a photo instead.").assertExists()
        compose.onNode(hasText("Take a photo")).assertDoesNotExist()
        compose.onNodeWithTag(CHOOSE_PHOTO_CHIP_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, h.sources.picks)
    }

    @Test
    fun anUnreadablePhotoOffersTryAgain() {
        val h = compose.setHarness(FakeAssistant(ingest = { _, _ -> throw earth.cora.picnic.core.llm.LlmError.Network(null) }))
        h.vm.onPhoto(Uri.parse("content://test/photo.jpg"))
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_ERROR_TAG).assertExists()
        compose.onNode(hasText("Try again")).assertExists()
        compose.onNode(hasText("Add as plain lines")).assertDoesNotExist()
        compose.onNodeWithTag(POLAROID_TAG).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
    }
}

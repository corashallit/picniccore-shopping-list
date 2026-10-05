package earth.cora.picnic.ui.add

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.MainActivity
import earth.cora.picnic.PicnicApp
import earth.cora.picnic.core.model.currentList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The add flow in the real app on Robolectric (MainActivity, the real container, JSON store and DataStore settings, no
 * key, so no network): a note sent from the list's add bar goes on as typed, on screen and in the store. Moved here
 * from the old ui/nav/PicnicAppRootTest when WP8's NavTest replaced it (integration of part 2); WP5's C list starts it
 * from its empty state's "Start my list".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AddInTheAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun addAsTypedPutsItemsOnTheList() {
        // The list's empty state (§4.21): "Start my list" focuses the add bar's field.
        waitForText("A brand-new sticker sheet!")
        compose.onNodeWithText("Start my list").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("milk, 2 lemons")
        // No key: the bar's Send puts the lines on as typed (§6.2 rule 2), with the toast that says how to sort them.
        // The toast holds for seconds of frame time, which an auto-advancing clock skips in one idle: hold the clock and
        // step it a frame per poll until the note is up.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(TIMEOUT_MS) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Added as typed", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.autoAdvance = true
        // A list row is one node whose label is its spoken line ("Lemons, 2"); the open first stop shows it.
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasContentDescription("Lemons", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val app = compose.activity.application as PicnicApp
        val names = runBlocking { app.container.store.current().currentList().items.map { it.name } }
        assertEquals(listOf("Milk", "Lemons"), names)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

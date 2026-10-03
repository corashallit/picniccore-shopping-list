package earth.cora.picnic.ui.add

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * key, so no network): "As typed" from the Add sheet puts the items on the list on screen and in the store. Moved here
 * from the old ui/nav/PicnicAppRootTest when WP8's NavTest replaced it (integration of part 2).
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
        waitForText("The basket is empty")
        compose.onNodeWithText("Add items").performClick()
        // The Add sheet opens on its Write tab.
        waitForText("Write")
        compose.onNode(hasSetTextAction()).performTextInput("milk, 2 lemons")
        compose.onNodeWithText("As typed").performClick()

        waitForText("Lemons")
        waitForText("Added 2 items", substring = true)
        val app = compose.activity.application as PicnicApp
        val names = runBlocking { app.container.store.current().currentList().items.map { it.name } }
        assertEquals(listOf("Milk", "Lemons"), names)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

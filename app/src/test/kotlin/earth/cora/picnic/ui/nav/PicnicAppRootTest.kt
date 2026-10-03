package earth.cora.picnic.ui.nav

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
 * The real app on Robolectric (real container, JSON store, DataStore settings, no token, so no
 * network): navigation List -> Settings / Aisles -> back, and "Add as typed" from the Add sheet.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PicnicAppRootTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun navigatesToSettingsAndAislesAndBack() {
        waitForText("The basket is empty")

        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Your OpenAI key")
        compose.onNodeWithText("Draw new icons with AI").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("The basket is empty")

        compose.onNodeWithContentDescription("Store aisles").performClick()
        waitForText("Your store")
        // A field's label is announced by the field itself (its content description).
        compose.onNodeWithContentDescription("Aisles, in walking order").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("The basket is empty")
    }

    @Test
    fun addAsTypedPutsItemsOnTheList() {
        waitForText("The basket is empty")
        compose.onNodeWithText("Add items").performClick()
        // The Add sheet opens on its Write tab (WP7).
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

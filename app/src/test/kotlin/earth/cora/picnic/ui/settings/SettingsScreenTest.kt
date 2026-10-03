package earth.cora.picnic.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.settings.Surprise
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.PicnicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings (§6.8, checklist 226): the key card, the Stickers card, the Surprises card and the About row. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = SettingsActions(
        onTokenChange = { calls += "token:$it" },
        onSave = { calls += "save" },
        onClearToken = { calls += "clear" },
        onDrawIconsChange = { calls += "draw:$it" },
        onTest = { calls += "test" },
        onSurpriseChange = { s, on -> calls += "${s.key}:$on" },
        onOpenAbout = { calls += "about" },
        onOpenAlbum = { calls += "album" },
    )

    private fun launch(state: SettingsUiState) {
        compose.setContent { PicnicTheme { SettingsScreen(state, actions, location = LocationPermission.Denied) } }
    }

    @Test
    fun keyCardNoKey() {
        launch(SettingsSamples.noKey)
        compose.onNodeWithText("Your OpenAI key").assertExists()
        compose.onNodeWithContentDescription("OpenAI API key").performTextInput("sk-proj-abc")
        assertTrue(calls.any { it.startsWith("token:") })
        compose.onNodeWithText("Stored encrypted on this phone (Android Keystore) and sent only to OpenAI.").assertExists()
        compose.onNodeWithText("Remove key").assertDoesNotExist()
    }

    @Test
    fun keyCardSaveTestAndRemove() {
        var state by mutableStateOf(SettingsSamples.typed)
        compose.setContent { PicnicTheme { SettingsScreen(state, actions, location = LocationPermission.Denied) } }
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        state = SettingsSamples.ok
        compose.onNodeWithText("Test connection").performClick()
        compose.onNodeWithText("Remove key").performClick()
        assertEquals(listOf("save", "test", "clear"), calls)
    }

    @Test
    fun failedTestShowsTheFriendlyError() {
        launch(SettingsSamples.failed)
        compose.onNodeWithText("missing a permission", substring = true).assertExists()
    }

    @Test
    fun stickersCard() {
        launch(SettingsSamples.ok)
        compose.onNodeWithText("Draw new stickers with AI", substring = true).performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("costs about 1.5¢", substring = true).assertExists()
        assertEquals(listOf("draw:false"), calls)
    }

    @Test
    fun surprisesCardDefaultsAndSwitches() {
        launch(SettingsSamples.ok)
        val expected = mapOf(
            "Easter eggs" to true, "Sounds" to false, "Haptics" to true, "“Next stop” card" to true,
            "Celebration" to true, "Weather surprises" to false, "Use my area" to false,
        )
        expected.forEach { (label, on) ->
            val node = compose.onNodeWithText(label, substring = true).performScrollTo()
            if (on) node.assertIsOn() else node.assertIsOff()
        }
        compose.onNodeWithText("Sounds", substring = true).performClick()
        compose.onNodeWithText("Celebration", substring = true).performClick()
        assertEquals(listOf("${Surprise.Sounds.key}:true", "${Surprise.Celebration.key}:false"), calls)
        compose.onNodeWithText("Reduced motion follows your phone’s setting.").performScrollTo().assertExists()
    }

    @Test
    fun aboutRowAndMenu() {
        launch(SettingsSamples.ok)
        compose.onNodeWithText("About & licences", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Sticker album").performClick()
        assertEquals(listOf("about", "album"), calls)
    }
}

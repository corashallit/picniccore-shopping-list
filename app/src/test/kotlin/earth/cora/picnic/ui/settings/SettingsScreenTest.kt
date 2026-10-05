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
import org.junit.Assert.assertFalse
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.text.AnnotatedString
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.common.FakeAssistant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A clipboard holding [text] (null: empty). */
private class FakeClipboard(var text: String?) : ClipboardManager {
    override fun getText(): AnnotatedString? = text?.let(::AnnotatedString)

    override fun setText(annotatedString: AnnotatedString) {
        text = annotatedString.text
    }
}

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
        compose.onNodeWithText("Drawing a new sticker takes about 10\u00a0s and uses your OpenAI key.").assertExists()
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

    /** The eye reveals the key she is typing and hides it again (§4.8); the field is masked by default. */
    @Test
    fun eyeRevealsTheTypedKey() {
        var state by mutableStateOf(SettingsSamples.typed)
        val reveal = SettingsActions(onRevealChange = { state = state.copy(tokenVisible = it) })
        compose.setContent { PicnicTheme { SettingsScreen(state, reveal, location = LocationPermission.Denied) } }
        val key = SettingsSamples.typed.tokenInput
        val field = compose.onNodeWithContentDescription("OpenAI API key")
        fun shown(): String = field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("•".repeat(key.length), shown())
        compose.onNodeWithContentDescription("Show key").performClick()
        assertEquals(key, shown())
        compose.onNodeWithContentDescription("Hide key").performClick()
        assertEquals("•".repeat(key.length), shown())
        // Shown, then cleared: the eye is disabled while empty, so the field masks again for the next paste.
        compose.onNodeWithContentDescription("Show key").performClick()
        state = state.copy(tokenInput = "")
        compose.waitForIdle()
        assertFalse("a cleared field masks again", state.tokenVisible)
    }

    /** Paste reads the clipboard (trimmed) into the key field, through the route's real wiring. */
    @Test
    fun pasteFillsTheKeyFromTheClipboard() {
        val settings = InMemorySettingsRepository()
        val vm = SettingsViewModel(settings, FakeAssistant())
        val clipboard = FakeClipboard("  sk-proj-pasted0123456789abcd \n")
        compose.setContent {
            PicnicTheme {
                CompositionLocalProvider(LocalClipboardManager provides clipboard) { SettingsRoute(vm, onBack = {}) }
            }
        }
        compose.onNodeWithContentDescription("Paste key").performClick()
        compose.waitUntil(5_000) { vm.state.value.tokenInput == "sk-proj-pasted0123456789abcd" }
        assertTrue(vm.state.value.canSave)
        // An empty clipboard changes nothing.
        clipboard.text = null
        compose.onNodeWithContentDescription("Paste key").performClick()
        compose.waitForIdle()
        assertEquals("sk-proj-pasted0123456789abcd", vm.state.value.tokenInput)
    }

    /** A passing test ticks on its LCD: "OK" → "OK." → "OK.." every 0.6 s (§6.8), then round again. */
    @Test
    fun okLcdTicks() {
        compose.mainClock.autoAdvance = false
        launch(SettingsSamples.ok)
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        val lcd = compose.onNode(SemanticsMatcher.keyIsDefined(OkLcdShown))
        fun shown() = lcd.fetchSemanticsNode().config[OkLcdShown]
        lcd.assertContentDescriptionEquals("Connected")
        assertEquals("OK", shown())
        compose.mainClock.advanceTimeBy(600)
        assertEquals("OK.", shown())
        compose.mainClock.advanceTimeBy(600)
        assertEquals("OK..", shown())
        compose.mainClock.advanceTimeBy(600)
        assertEquals("OK", shown())
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

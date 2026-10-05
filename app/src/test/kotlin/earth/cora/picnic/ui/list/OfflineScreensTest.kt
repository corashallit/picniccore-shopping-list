package earth.cora.picnic.ui.list

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import earth.cora.picnic.icons.DrawFailure
import earth.cora.picnic.platform.CaptureKind
import earth.cora.picnic.platform.FakeNetworkStatus
import earth.cora.picnic.ui.add.ADD_DISCARD_KEPT_TAG
import earth.cora.picnic.ui.add.ADD_FOOTER_TAG
import earth.cora.picnic.ui.add.AddActions
import earth.cora.picnic.ui.add.AddFixtures
import earth.cora.picnic.ui.add.AddMode
import earth.cora.picnic.ui.add.AddOrigin
import earth.cora.picnic.ui.add.AddPhase
import earth.cora.picnic.ui.add.AddProblem
import earth.cora.picnic.ui.add.AddSheet
import earth.cora.picnic.ui.add.AddUiState
import earth.cora.picnic.ui.add.SnapPhoto
import earth.cora.picnic.ui.common.isCut
import earth.cora.picnic.ui.edit.EDIT_DRAW_LINE_TAG
import earth.cora.picnic.ui.edit.EditItemSheetContent
import earth.cora.picnic.ui.edit.EditSamples
import earth.cora.picnic.ui.settings.SETTINGS_UNCHECKED_TAG
import earth.cora.picnic.ui.settings.SettingsActions
import earth.cora.picnic.ui.settings.SettingsSamples
import earth.cora.picnic.ui.settings.SettingsScreen
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.TOAST_TAG
import earth.cora.picnic.ui.theme.ToastHost
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * The offline experience on screen (docs/DESIGN_STICKER.md §0.4, 5 October; items 350, 351, 353, 356–358): the menu's Refresh offline
 * says what waits and asks nothing; the Write tab offline fails at once and keeps "As typed", which adds the note with
 * the one offline note; a kept photo offers "Delete photo"; the Edit sheet rests its drawing offline and says why or that
 * a drawing did not work; Settings shows "Saved." and the neutral line, never the hanko error. (The new notes' fit at
 * font scale 1.0, 1.3 and 2.0 is the walker's: the fixtures list_add_offline_toast and list_refresh_offline_toast in
 * NoClippedTextTest.)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class OfflineScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theMenusRefreshOfflineSaysWhatWaits() {
        val rig = ListRig(network = FakeNetworkStatus(online = false))
        compose.show(rig)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        // The clock held, so the note can be read during its hold.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Refresh").performClick()
        assertTrue("the note dropped in", compose.framesUntil(3000) { compose.exists(TOAST_TAG) })
        compose.advanceMs(600)
        compose.onNodeWithText("Sorting and new stickers wait until you’re back online").assertIsDisplayed()
        assertEquals("no sticker work offline", 0, rig.icons.resumed)
        assertTrue("no sorting offline", rig.assistant.recategorized.isEmpty())
    }

    @Test
    fun theWriteTabOfflineKeepsAsTypedWhichAddsWithOneNote() {
        val rig = ListRig(network = FakeNetworkStatus(online = false))
        compose.show(rig)
        compose.runOnIdle {
            rig.addVm.openSheet(AddMode.WRITE)
            rig.addVm.setText("lip balm")
            rig.addVm.send(AddOrigin.SHEET)
        }
        compose.waitForIdle()
        assertTrue("no request offline", rig.assistant.ingested.isEmpty())
        compose.onNodeWithText("You’re offline right now. Try again once you’re back online.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("Add as plain lines").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("As typed") and hasAnyAncestor(hasTestTag(ADD_FOOTER_TAG)) and hasClickAction()).assertIsEnabled().performClick()
        assertTrue("the note dropped in", compose.framesUntil(3000) { compose.exists(TOAST_TAG) })
        compose.advanceMs(600)
        // The note draws "·" glued to the word before it; its words are matched without it.
        compose.onNodeWithText("sorting waits until you’re back online", substring = true).assertIsDisplayed()
        assertTrue("the sort waits for the catch-up", rig.assistant.recategorized.isEmpty())
        assertTrue(rig.app.items().any { it.name == "Lip balm" && it.aisleId == null })
    }

    @Test
    fun aKeptPhotoOffersDelete() {
        var deleted = 0
        var retried = 0
        val state = AddUiState(
            listId = AddFixtures.LIST_ID, sheetOpen = true, mode = AddMode.SNAP, kept = CaptureKind.PHOTO,
            photo = SnapPhoto(null, AddFixtures.NOW),
            phase = AddPhase.Failed(AddProblem.KEPT_PHOTO, retryable = true),
        )
        compose.setContent { PicnicTheme { Box(Modifier.fillMaxSize()) { AddSheet(state, AddActions(onDiscardKept = { deleted++ }, onRetry = { retried++ })) } } }
        compose.onNodeWithText("This photo hasn’t been sent yet. Try again, or take a new one.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithTag(ADD_DISCARD_KEPT_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delete photo").performClick()
        assertEquals(1, retried)
        assertEquals(1, deleted)
        compose.onNodeWithText("Take a photo").assertIsDisplayed()
    }

    @Test
    fun theEditSheetRestsDrawingOfflineAndSaysWhenItDidNotWork() {
        var state by mutableStateOf(EditSamples.milk().copy(offline = true))
        compose.setContent { PicnicTheme { Box(Modifier.fillMaxSize()) { EditItemSheetContent(state, { _, _, _ -> }, {}, {}, {}, {}, {}) } } }
        compose.onNodeWithText("Draw a new sticker").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(EDIT_DRAW_LINE_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Drawing a new sticker needs a connection. You’re offline right now.").assertExists()

        state = EditSamples.milk().copy(drawFailed = DrawFailure("milk"))
        compose.waitForIdle()
        compose.onNodeWithText("Draw a new sticker").assertIsEnabled()
        compose.onNodeWithText("Couldn’t draw a new sticker this time. The old one stays.").assertExists()
        state = EditSamples.milk().copy(drawFailed = DrawFailure("milk", refused = true))
        compose.waitForIdle()
        compose.onNodeWithText("This one couldn’t be drawn. Pick a sticker above instead.").assertExists()
        state = EditSamples.milk().copy(drawFailed = DrawFailure("milk", offline = true))
        compose.waitForIdle()
        compose.onNodeWithText("Couldn’t reach OpenAI to draw it. The old sticker stays.").assertExists()
    }

    @Test
    fun settingsShowsSavedAndTheNeutralLine() {
        compose.setContent { PicnicTheme { SettingsScreen(SettingsSamples.uncheckedOffline, SettingsActions()) } }
        compose.onNodeWithText("Saved. Paste a new key here to replace it.").assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_UNCHECKED_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not checked yet: you’re offline. It’s checked as soon as you’re back online.").assertIsDisplayed()
        assertTrue("no hanko error", compose.onAllNodes(hasText(LlmOffline)).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val LlmOffline = "You’re offline right now. Try again once you’re back online."
    }
}

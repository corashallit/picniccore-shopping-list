package earth.cora.picnic.ui.about

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** About & licences, the Surprise lab's unlock (§6.9, checklist 21, 229). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings = InMemorySettingsRepository()
    private var labOpened = 0

    private fun launch(debugBuild: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vm = AboutViewModel(settings, "0.4.0", debugBuild) { Licences.load(context) }
        compose.setContent { PicnicTheme { AboutRoute(vm, onBack = {}, onOpenLab = { labOpened++ }) } }
        compose.waitForIdle()
    }

    @Test
    fun licencesListed() {
        launch()
        val families = listOf("DynaPuff", "Zen Maru Gothic", "Klee One", "Hachi Maru Pop", "DotGothic16")
        families.forEach { family ->
            compose.onNodeWithText(family, substring = true).performScrollTo().assertExists()
        }
        // Each licence opens to its full OFL text.
        compose.onNodeWithText("DotGothic16", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("This Font Software is licensed under the SIL Open Font License", substring = true).assertExists()
        compose.onNodeWithText("Every sticker, basket and doodle is original, made for this app.").performScrollTo().assertExists()
    }

    @Test
    fun sevenTapsUnlockLab() {
        launch()
        assertTrue(compose.onAllNodesWithText("Surprise lab").fetchSemanticsNodes().isEmpty())
        val version = compose.onNodeWithText("Version 0.4.0")
        repeat(6) { version.performClick() }
        compose.waitForIdle()
        assertFalse("six taps are not enough", runBlocking { settings.current() }.surpriseLab)
        // The seventh tap: a toast says so (the clock held so the toast is caught mid-stay, not played out).
        compose.mainClock.autoAdvance = false
        version.performClick()
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Surprise lab unlocked", substring = true).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { runBlocking { settings.current() }.surpriseLab }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Surprise lab").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Surprise lab").performClick()
        assertEquals(1, labOpened)
    }

    @Test
    fun debugBuildShowsTheLabWithoutTaps() {
        launch(debugBuild = true)
        compose.onNodeWithText("Surprise lab").assertExists()
    }
}

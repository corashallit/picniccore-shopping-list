package earth.cora.picnic.ui.about

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
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

    /** The version taps are timed on the compose test clock (never the wall clock), so a slow machine can't break a run. */
    private fun launch(debugBuild: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vm = AboutViewModel(settings, "0.4.0", debugBuild, clock = { compose.mainClock.currentTime }) { Licences.load(context) }
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

    /** Lets the tap's work (the ViewModel's write, the toast's drop) run on the held clock. */
    private fun settle(frames: Int = 10) = repeat(frames) {
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun unlocked(): Boolean = runBlocking { settings.current() }.surpriseLab

    @Test
    fun sevenTapsUnlockLab() {
        launch()
        assertTrue(compose.onAllNodesWithText("Surprise lab").fetchSemanticsNodes().isEmpty())
        // The clock stands still between taps; each tap comes 1.5 s after the last (inside the 2 s gap).
        compose.mainClock.autoAdvance = false
        val version = compose.onNodeWithText("Version 0.4.0")
        repeat(6) {
            version.performClick()
            settle(1)
            compose.mainClock.advanceTimeBy(1_500)
        }
        settle()
        assertFalse("six taps are not enough", unlocked())
        // The seventh tap: a toast says so (caught mid-stay, not played out).
        version.performClick()
        settle()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Surprise lab unlocked", substring = true).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { unlocked() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Surprise lab").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Surprise lab").performClick()
        assertEquals(1, labOpened)
    }

    /** A pause longer than 2 s between two taps starts the count again (on the test clock, as on the phone). */
    @Test
    fun aPauseStartsTheCountAgain() {
        launch()
        compose.mainClock.autoAdvance = false
        val version = compose.onNodeWithText("Version 0.4.0")
        fun tap(gapMillis: Long) {
            version.performClick()
            settle(1)
            compose.mainClock.advanceTimeBy(gapMillis)
        }
        repeat(5) { tap(300) }
        compose.mainClock.advanceTimeBy(TAP_GAP_MILLIS + 500)
        // Six more: eleven in all, but only six since the pause.
        repeat(6) { tap(300) }
        settle()
        assertFalse(unlocked())
        tap(300)
        settle()
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { unlocked() }
    }

    /** The counter itself (pure): seven taps within 2 s of each other unlock; the count restarts after a pause. */
    @Test
    fun unlockTapsCount() {
        var now = 0L
        val taps = UnlockTaps { now }
        repeat(6) { assertFalse(taps.tap()); now += TAP_GAP_MILLIS }
        assertTrue("the seventh tap, each exactly 2 s apart", taps.tap())
        now += TAP_GAP_MILLIS + 1
        repeat(6) { assertFalse(taps.tap()); now += 100 }
        now += TAP_GAP_MILLIS + 1
        assertFalse("a pause restarted the count", taps.tap())
    }

    /**
     * The sandbox goes when she leaves the lab, however she leaves: here the screen simply leaves the composition (as a
     * pop from "Back to <list>" or the system back does), and close() runs exactly once. The Back button only navigates.
     */
    @Test
    fun leavingTheLabClosesTheSandboxOnce() {
        var closes = 0
        var backs = 0
        val lab = object : SurpriseLab by ShellSurpriseLab {
            override fun close() {
                closes++
            }
        }
        var shown by mutableStateOf(true)
        compose.setContent {
            PicnicTheme {
                if (shown) SurpriseLabScreen(onBack = { backs++; shown = false }, lab = lab)
            }
        }
        compose.onNodeWithText("Surprise lab").assertExists()
        assertEquals(0, closes)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals("closed once, by leaving", 1, closes)

        // And with no Back tap at all: a pop from elsewhere.
        closes = 0
        shown = true
        compose.waitForIdle()
        shown = false
        compose.waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun debugBuildShowsTheLabWithoutTaps() {
        launch(debugBuild = true)
        compose.onNodeWithText("Surprise lab").assertExists()
    }
}

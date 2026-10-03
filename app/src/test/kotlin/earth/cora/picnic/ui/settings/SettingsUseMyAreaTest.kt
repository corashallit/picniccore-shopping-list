package earth.cora.picnic.ui.settings

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.calendar.Place
import earth.cora.picnic.platform.AreaPlaceSource
import earth.cora.picnic.platform.CoarseLocator
import earth.cora.picnic.platform.InMemoryPlaceCache
import earth.cora.picnic.platform.LatLon
import earth.cora.picnic.settings.Surprise
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Use my area" (§0.4, checklist 316): off by default; the rationale comes before the system prompt, which asks for
 * approximate location only; denied, it stays off and the surprises use Toronto.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class SettingsUseMyAreaTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    /** A permission that answers [answer] when asked, and records the asking. */
    private class FakePermission(var isGranted: Boolean = false, val answer: Boolean = false) : LocationPermission {
        var requests = 0
        override fun granted() = isGranted
        override fun request(onResult: (Boolean) -> Unit) {
            requests++
            isGranted = answer
            onResult(answer)
        }
    }

    private fun launch(permission: LocationPermission) {
        val actions = SettingsActions(
            onSurpriseChange = { s, on -> calls += "${s.key}:$on" },
            onAreaDenied = { calls += "denied" },
            onAreaRefresh = { calls += "refresh" },
        )
        compose.setContent { PicnicTheme { SettingsScreen(SettingsSamples.ok, actions, location = permission) } }
    }

    private val rationale =
        "Kirakira Basket can use your approximate area to find tonight’s sunset and the local forecast for its weather " +
            "surprises. Your area stays on this phone; only the forecast request names the nearest city."

    @Test
    fun rationaleComesBeforeThePrompt() {
        val permission = FakePermission(answer = true)
        launch(permission)
        compose.onNodeWithText("Use my area", substring = true).performScrollTo().performClick()
        // The §0.4 rationale, word for word, and no system prompt yet.
        compose.onNodeWithText("Use my area?").assertExists()
        compose.onNodeWithText(rationale).assertExists()
        assertEquals(0, permission.requests)
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, permission.requests)
        assertTrue(calls.contains("${Surprise.UseMyArea.key}:true"))
    }

    @Test
    fun notNowAsksNothing() {
        val permission = FakePermission(answer = true)
        launch(permission)
        compose.onNodeWithText("Use my area", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(0, permission.requests)
        assertTrue(calls.none { it.startsWith(Surprise.UseMyArea.key) })
    }

    @Test
    fun deniedStaysOffWithToronto() {
        val permission = FakePermission(answer = false)
        launch(permission)
        compose.onNodeWithText("Use my area", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Continue").performClick()
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(listOf("denied"), calls)
        compose.onNodeWithText("Location wasn’t allowed, so the surprises use Toronto.").assertExists()

        // And the place the surprises get is Toronto: the setting is off, no fix is asked for.
        val located = mutableListOf<Unit>()
        val source = AreaPlaceSource(
            useMyArea = { false },
            locator = object : CoarseLocator {
                override fun hasPermission() = false
                override suspend fun locate(): LatLon? = null.also { located += Unit }
            },
            cache = InMemoryPlaceCache(),
            clock = { 0L },
        )
        assertEquals(Place.TORONTO, runBlocking { source.place() })
        assertTrue(located.isEmpty())
    }

    @Test
    fun alreadyGrantedSwitchesStraightOn() {
        val permission = FakePermission(isGranted = true)
        launch(permission)
        compose.onNodeWithText("Use my area", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Use my area?").assertDoesNotExist()
        assertEquals(0, permission.requests)
        assertTrue(calls.contains("${Surprise.UseMyArea.key}:true"))
    }
}

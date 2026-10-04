package earth.cora.picnic.ui.settings

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.calendar.Place
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import earth.cora.picnic.platform.AreaLookup
import earth.cora.picnic.platform.AreaPlaceSource
import earth.cora.picnic.platform.EcSites
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.platform.CoarseLocator
import earth.cora.picnic.platform.InMemoryPlaceCache
import earth.cora.picnic.platform.LatLon
import earth.cora.picnic.platform.StoredPlace
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.settings.Surprise
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    }

    /** Android stopped asking (denied for good): the rationale's button opens Android settings, never a prompt that fails at once. */
    @Test
    fun deniedForGoodOffersAndroidSettings() {
        var opened = 0
        var requests = 0
        val permission = object : LocationPermission {
            override fun granted() = false
            override fun blocked() = true
            override fun request(onResult: (Boolean) -> Unit) {
                requests++
                onResult(false)
            }
            override fun openSettings() {
                opened++
            }
        }
        launch(permission)
        compose.onNodeWithText("Use my area", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithText("Open Android settings").performClick()
        assertEquals(1, opened)
        assertEquals(0, requests)
    }

    /**
     * The switch stays on but the permission was taken back in Android's settings: the place the surprises get is Toronto,
     * no fix is asked for, and the stored area is deleted (§0.4: nothing about location is kept without it).
     */
    @Test
    fun revokedPermissionFallsBackToToronto() = runBlocking {
        val settings = InMemorySettingsRepository(AppSettings(useMyArea = true))
        val cache = InMemoryPlaceCache(StoredPlace(43.3, -79.9, "s0000549", at = 0L))
        val located = mutableListOf<Unit>()
        val source = AreaPlaceSource(
            useMyArea = { settings.current().allows(Surprise.UseMyArea) },
            locator = object : CoarseLocator {
                override fun hasPermission() = false
                override suspend fun locate(): LatLon? = null.also { located += Unit }
            },
            cache = cache,
            clock = { 1L },
        )
        assertEquals(Place.TORONTO, source.place())
        assertTrue("no fix asked for", located.isEmpty())
        assertNull("the stored area is gone", cache.stored)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun locator(granted: Boolean, fix: LatLon?) = object : CoarseLocator {
        override fun hasPermission() = granted
        override suspend fun locate(): LatLon? = fix
    }

    /**
     * Turning "Use my area" off deletes the stored rounded point and city at once (PRIVACY.md), not at the next sunset or
     * forecast check: with Easter eggs and Weather off nothing else would ever ask.
     */
    @Test
    fun switchOffDeletesStoredArea() {
        val settings = InMemorySettingsRepository(AppSettings(useMyArea = true))
        val ottawa = StoredPlace(45.4, -75.7, "s0000430", at = 1_000L)
        val cache = InMemoryPlaceCache(ottawa)
        val source = AreaPlaceSource({ settings.current().useMyArea }, locator(granted = true, fix = LatLon(45.42, -75.69)), cache, clock = { 2_000L })
        val vm = SettingsViewModel(settings, FakeAssistant(), places = source)
        idle()
        assertEquals(EcSites.byCode("s0000430")!!.name, vm.state.value.areaCity)
        assertEquals(ottawa, cache.stored)

        vm.setSurprise(Surprise.UseMyArea, false)
        idle()
        assertFalse(vm.state.value.settings.useMyArea)
        assertNull("the stored area is gone at once", cache.stored)
        assertEquals(AreaLookup.Off, vm.state.value.area)
    }

    /** Location refused after the rationale: the switch stays off and nothing about the area stays on the phone. */
    @Test
    fun refusedLocationDeletesStoredArea() {
        val settings = InMemorySettingsRepository(AppSettings(useMyArea = false))
        val cache = InMemoryPlaceCache(StoredPlace(45.4, -75.7, "s0000430", at = 1_000L))
        val source = AreaPlaceSource({ settings.current().useMyArea }, locator(granted = false, fix = null), cache, clock = { 2_000L })
        val vm = SettingsViewModel(settings, FakeAssistant(), places = source)
        idle()
        cache.stored = StoredPlace(45.4, -75.7, "s0000430", at = 1_000L)
        vm.onAreaDenied()
        idle()
        assertNull(cache.stored)
    }

    /** On and allowed but the phone has no fix: the hint says Toronto for now, not "near you" (§0.4: plain true things). */
    @Test
    fun noFixHintSaysTorontoForNow() {
        val on = SettingsSamples.ok.copy(settings = SettingsSamples.ok.settings.copy(useMyArea = true))
        var state by mutableStateOf(on.copy(area = AreaLookup.Finding))
        compose.setContent { PicnicTheme { SettingsScreen(state, SettingsActions(), location = FakePermission(isGranted = true)) } }
        compose.onNodeWithText("sunset and forecast near you; approximate area only").performScrollTo().assertExists()
        state = on.copy(area = AreaLookup.NoFix)
        compose.onNodeWithText("couldn’t find your area yet, so Toronto for now").performScrollTo().assertExists()
        compose.onNodeWithText("sunset and forecast near you", substring = true).assertDoesNotExist()
        compose.onNodeWithText("asks Environment Canada for Toronto’s forecast", substring = true).assertExists()
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

package earth.cora.picnic.ui.settings

import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.platform.AreaLookup
import earth.cora.picnic.platform.AreaPlaceSource
import earth.cora.picnic.platform.CoarseLocator
import earth.cora.picnic.platform.EcSites
import earth.cora.picnic.platform.InMemoryPlaceCache
import earth.cora.picnic.platform.LatLon
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.ScreenText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Fixture(val vm: SettingsViewModel, val settings: InMemorySettingsRepository, val assistant: FakeAssistant)

    private fun TestScope.fixture(
        initial: AppSettings = AppSettings(),
        token: String? = null,
        assistant: FakeAssistant = FakeAssistant(),
    ): Fixture {
        val settings = InMemorySettingsRepository(initial, token)
        val vm = SettingsViewModel(settings, assistant)
        advanceUntilIdle()
        return Fixture(vm, settings, assistant)
    }

    /**
     * "Use my area" with no fix (location services off, airplane mode, no last-known fix): Finding while it looks, then
     * NoFix, never a city; with a fix the forecast city's name.
     */
    @Test
    fun areaLookupWithoutAFix() = runTest {
        val settings = InMemorySettingsRepository(AppSettings(useMyArea = true))
        val answer = CompletableDeferred<LatLon?>()
        val source = AreaPlaceSource(
            useMyArea = { settings.current().useMyArea },
            locator = object : CoarseLocator {
                override fun hasPermission() = true
                override suspend fun locate(): LatLon? = answer.await()
            },
            cache = InMemoryPlaceCache(),
            clock = { 0L },
        )
        val vm = SettingsViewModel(settings, FakeAssistant(), places = source)
        runCurrent()
        assertEquals(AreaLookup.Finding, vm.state.value.area)
        answer.complete(null)
        advanceUntilIdle()
        assertEquals(AreaLookup.NoFix, vm.state.value.area)
        assertNull(vm.state.value.areaCity)

        // Asked again with a fix (Settings resumed): the city.
        val located = AreaPlaceSource({ true }, object : CoarseLocator {
            override fun hasPermission() = true
            override suspend fun locate(): LatLon? = LatLon(45.42, -75.69)
        }, InMemoryPlaceCache(), { 0L })
        val vm2 = SettingsViewModel(settings, FakeAssistant(), places = located)
        advanceUntilIdle()
        val found = vm2.state.value.area as AreaLookup.Found
        assertEquals(EcSites.nearest(LatLon(45.4, -75.7)).name, found.city)
        assertEquals(found.city, vm2.state.value.areaCity)
    }

    @Test
    fun showsTheSavedKeyOnlyAsAHint() = runTest {
        val f = fixture(token = "sk-proj-abcdefghijklmnop1234")
        val s = f.vm.state.value
        assertFalse(s.loading)
        assertTrue(s.hasToken)
        assertEquals("sk-…1234", s.tokenHint)
        assertEquals("", s.tokenInput)
        assertFalse(s.canSave)
        assertNull(s.notice)
    }

    @Test
    fun savingAKeyTrimsItClearsTheFieldAndTestsTheConnection() = runTest {
        val f = fixture()
        f.vm.setTokenInput("  sk-proj-abcdefghijklmnop1234  ")
        f.vm.toggleTokenVisible()
        runCurrent()
        assertTrue(f.vm.state.value.canSave)

        f.vm.saveConnection()
        advanceUntilIdle()
        assertEquals("sk-proj-abcdefghijklmnop1234", f.settings.token())
        val s = f.vm.state.value
        assertEquals("", s.tokenInput)
        assertFalse("the field hides again", s.tokenVisible)
        assertTrue(s.hasToken)
        assertEquals("sk-…1234", s.tokenHint)
        assertFalse(s.canSave)
        assertEquals(ConnectionTestUi.Ok(412), s.test)
        assertEquals(1, f.assistant.tests)
    }

    @Test
    fun aBlankKeyIsNotSaved() = runTest {
        val f = fixture(token = "sk-proj-abcdefghijklmnop1234")
        f.vm.setTokenInput("   ")
        runCurrent()
        assertFalse(f.vm.state.value.canSave)
        f.vm.saveConnection()
        advanceUntilIdle()
        assertEquals("the saved key stays", "sk-proj-abcdefghijklmnop1234", f.settings.token())
        assertEquals(0, f.assistant.tests)
    }

    @Test
    fun aNewKeyReplacesTheSavedOne() = runTest {
        val f = fixture(token = "sk-proj-abcdefghijklmnop1234")
        f.vm.setTokenInput("sk-proj-zyxwvutsrqponmlk9876")
        f.vm.saveConnection()
        advanceUntilIdle()
        assertEquals("sk-proj-zyxwvutsrqponmlk9876", f.settings.token())
        assertEquals("sk-…9876", f.vm.state.value.tokenHint)
    }

    @Test
    fun testConnectionShowsRunningThenTheFriendlyError() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = fixture(token = "sk-proj-abcdefghijklmnop1234", assistant = FakeAssistant(testConnection = {
            gate.await()
            throw LlmError.Unauthorized(401, missingScopes = listOf("api.model.read"))
        }))
        f.vm.testConnection()
        runCurrent()
        assertEquals(ConnectionTestUi.Running, f.vm.state.value.test)
        gate.complete(Unit)
        advanceUntilIdle()
        val failed = f.vm.state.value.test as ConnectionTestUi.Failed
        assertTrue(failed.message.contains("Ask the person who made it to allow List models (Read)"))
    }

    @Test
    fun testConnectionSavesATypedKeyFirst() = runTest {
        val f = fixture()
        f.vm.setTokenInput("sk-proj-abcdefghijklmnop1234")
        f.vm.testConnection()
        advanceUntilIdle()
        assertEquals("sk-proj-abcdefghijklmnop1234", f.settings.token())
        assertEquals(ConnectionTestUi.Ok(412), f.vm.state.value.test)
        assertEquals(1, f.assistant.tests)
    }

    @Test
    fun testConnectionWithoutAKeySendsNothing() = runTest {
        val f = fixture()
        f.vm.testConnection()
        advanceUntilIdle()
        assertEquals(ConnectionTestUi.Idle, f.vm.state.value.test)
        assertEquals(0, f.assistant.tests)
    }

    @Test
    fun removeKey() = runTest {
        val f = fixture(token = "sk-proj-abcdefghijklmnop1234")
        f.vm.clearToken()
        advanceUntilIdle()
        val s = f.vm.state.value
        assertFalse(s.hasToken)
        assertNull(s.tokenHint)
        assertNull(f.settings.token())
    }

    @Test
    fun aKeyTheKeystoreRefusedStaysInTheFieldWithANotice() = runTest {
        // DataStoreSettingsRepository.setToken: a Keystore failure stores nothing and doesn't throw.
        val refusing = object : SettingsRepository by InMemorySettingsRepository() {
            override suspend fun setToken(token: String?) = Unit
        }
        val assistant = FakeAssistant()
        val vm = SettingsViewModel(refusing, assistant)
        advanceUntilIdle()
        vm.setTokenInput("sk-proj-abcdefghijklmnop1234")
        vm.saveConnection()
        advanceUntilIdle()
        assertEquals(ScreenText.SAVE_FAILED, vm.state.value.notice)
        assertEquals("sk-proj-abcdefghijklmnop1234", vm.state.value.tokenInput)
        assertEquals(0, assistant.tests)

        vm.setTokenInput("sk-proj-abcdefghijklmnop12345")
        runCurrent()
        assertNull("typing clears the notice", vm.state.value.notice)
    }

    @Test
    fun aFailedSettingsWriteIsReportedNotThrown() = runTest {
        val failing = object : SettingsRepository by InMemorySettingsRepository() {
            override suspend fun setDrawIcons(enabled: Boolean) = throw IOException("disk full")
            override suspend fun setToken(token: String?) = throw IOException("disk full")
        }
        val vm = SettingsViewModel(failing, FakeAssistant())
        advanceUntilIdle()
        vm.setDrawIcons(false)
        advanceUntilIdle()
        assertEquals(ScreenText.SAVE_FAILED, vm.state.value.notice)
        vm.setTokenInput("sk-proj-abcdefghijklmnop1234")
        vm.saveConnection()
        advanceUntilIdle()
        assertEquals(ScreenText.SAVE_FAILED, vm.state.value.notice)
    }

    @Test
    fun drawIconsToggleAndKeyVisibility() = runTest {
        val f = fixture()
        f.vm.setDrawIcons(false)
        f.vm.toggleTokenVisible()
        advanceUntilIdle()
        assertFalse(f.settings.current().drawIcons)
        assertFalse(f.vm.state.value.drawIcons)
        assertTrue(f.vm.state.value.tokenVisible)
    }
}

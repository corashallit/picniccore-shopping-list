package earth.cora.picnic.ui.settings

import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.ScreenText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
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
        val vm = SettingsViewModel(settings, assistant, "0.1.0-test")
        advanceUntilIdle()
        return Fixture(vm, settings, assistant)
    }

    @Test
    fun showsTheSavedConnectionWithoutEverShowingTheToken() = runTest {
        val f = fixture(token = "sk-proj-abcdefghijklmnop1234")
        val s = f.vm.state.value
        assertFalse(s.loading)
        assertEquals(LlmConfig.DEFAULT_BASE_URL, s.baseUrlInput)
        assertTrue(s.usesDefaultEndpoint)
        assertTrue(s.hasToken)
        assertEquals("sk-…1234", s.tokenHint)
        assertEquals("", s.tokenInput)
        assertFalse(s.canSave)
        assertEquals("0.1.0-test", s.appVersion)
    }

    @Test
    fun anInvalidAddressIsRejectedAndNothingIsSaved() = runTest {
        val f = fixture()
        f.vm.setBaseUrlInput("http://picnic.cora.earth/v1")
        f.vm.setTokenInput("pk_live_0123456789abcdef")
        runCurrent()
        assertTrue(f.vm.state.value.canSave)
        f.vm.saveConnection()
        advanceUntilIdle()
        assertEquals(ScreenText.BASE_URL_INVALID, f.vm.state.value.baseUrlError)
        assertEquals(LlmConfig.DEFAULT_BASE_URL, f.settings.current().baseUrl)
        assertNull("the token waits for a valid address", f.settings.token())

        f.vm.setBaseUrlInput("https://picnic.cora.earth/v1/")
        runCurrent()
        assertNull("typing clears the error", f.vm.state.value.baseUrlError)
    }

    @Test
    fun savingAProxyAndTokenClearsTheFieldsAndTestsTheConnection() = runTest {
        val f = fixture()
        f.vm.setBaseUrlInput(" https://picnic.cora.earth/v1/ ")
        f.vm.setTokenInput("  pk_live_0123456789abcdef  ")
        f.vm.saveConnection()
        advanceUntilIdle()

        assertEquals("https://picnic.cora.earth/v1", f.settings.current().baseUrl)
        assertEquals("pk_live_0123456789abcdef", f.settings.token())
        val s = f.vm.state.value
        assertEquals("https://picnic.cora.earth/v1", s.baseUrlInput)
        assertFalse(s.usesDefaultEndpoint)
        assertEquals("", s.tokenInput)
        assertTrue(s.hasToken)
        assertFalse(s.canSave)
        assertEquals(ConnectionTestUi.Ok(412), s.test)
        assertEquals(1, f.assistant.tests)
    }

    @Test
    fun testConnectionShowsRunningThenTheFriendlyError() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = fixture(token = "sk-proj-abcdefghijklmnop1234", assistant = FakeAssistant(testConnection = {
            gate.await()
            throw LlmError.Unauthorized(401)
        }))
        f.vm.testConnection()
        runCurrent()
        assertEquals(ConnectionTestUi.Running, f.vm.state.value.test)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ConnectionTestUi.Failed(LlmError.Unauthorized(401).friendlyMessage), f.vm.state.value.test)
    }

    @Test
    fun clearTokenAndUseOpenAiDirectly() = runTest {
        val f = fixture(initial = AppSettings(baseUrl = "https://picnic.cora.earth/v1"), token = "pk_live_0123456789abcdef")
        f.vm.clearToken()
        f.vm.useOpenAiDirectly()
        advanceUntilIdle()
        val s = f.vm.state.value
        assertFalse(s.hasToken)
        assertTrue(s.usesDefaultEndpoint)
        assertEquals(LlmConfig.DEFAULT_BASE_URL, s.baseUrlInput)
        assertNull(f.settings.token())
    }

    @Test
    fun aNewHostNeverGetsTheOldToken() = runTest {
        val f = fixture(token = "sk-proj-abcdefghijklmnop1234")
        f.vm.setBaseUrlInput("https://picnic.cora.earth/v1")
        f.vm.saveConnection()
        advanceUntilIdle()
        assertEquals("https://picnic.cora.earth/v1", f.settings.current().baseUrl)
        assertNull("the OpenAI key is not kept for the proxy", f.settings.token())
        assertEquals(ScreenText.TOKEN_FOR_NEW_SERVER, f.vm.state.value.notice)
        assertEquals("no request went to the new host", 0, f.assistant.tests)
    }

    @Test
    fun theSameHostKeepsTheTokenAndANewTokenIsSavedForANewHost() = runTest {
        val f = fixture(initial = AppSettings(baseUrl = "https://picnic.cora.earth/v1"), token = "pk_live_0123456789abcdef")
        f.vm.setBaseUrlInput("https://PICNIC.cora.earth:443/v2")
        f.vm.saveConnection()
        advanceUntilIdle()
        assertEquals("pk_live_0123456789abcdef", f.settings.token())
        assertNull(f.vm.state.value.notice)

        f.vm.setBaseUrlInput("https://other.example/v1")
        f.vm.setTokenInput("pk_live_fedcba9876543210")
        f.vm.saveConnection()
        advanceUntilIdle()
        assertEquals("pk_live_fedcba9876543210", f.settings.token())
        assertNull(f.vm.state.value.notice)

        f.vm.useOpenAiDirectly()
        advanceUntilIdle()
        assertNull("the proxy token is not sent to OpenAI either", f.settings.token())
        assertEquals(ScreenText.TOKEN_FOR_NEW_SERVER, f.vm.state.value.notice)
    }

    @Test
    fun aFailedSettingsWriteIsReportedNotThrown() = runTest {
        val failing = object : SettingsRepository by InMemorySettingsRepository() {
            override suspend fun setDrawIcons(enabled: Boolean) = throw IOException("disk full")
            override suspend fun setBaseUrl(url: String): Boolean = throw IOException("disk full")
        }
        val vm = SettingsViewModel(failing, FakeAssistant(), "0.1.0-test")
        advanceUntilIdle()
        vm.setDrawIcons(false)
        advanceUntilIdle()
        assertEquals(ScreenText.SAVE_FAILED, vm.state.value.notice)
        vm.setBaseUrlInput("https://picnic.cora.earth/v1")
        vm.saveConnection()
        advanceUntilIdle()
        assertEquals(ScreenText.SAVE_FAILED, vm.state.value.notice)
    }

    @Test
    fun sameHostComparesHostAndPort() {
        assertTrue(SettingsViewModel.sameHost("https://a.example/v1", "https://A.example:443/other"))
        assertFalse(SettingsViewModel.sameHost("https://a.example/v1", "https://a.example:8443/v1"))
        assertFalse(SettingsViewModel.sameHost("https://a.example/v1", "https://b.example/v1"))
        assertFalse(SettingsViewModel.sameHost("not a url", "not a url"))
    }

    @Test
    fun drawIconsToggleAndTokenVisibility() = runTest {
        val f = fixture()
        f.vm.setDrawIcons(false)
        f.vm.toggleTokenVisible()
        advanceUntilIdle()
        assertFalse(f.settings.current().drawIcons)
        assertFalse(f.vm.state.value.drawIcons)
        assertTrue(f.vm.state.value.tokenVisible)
    }
}

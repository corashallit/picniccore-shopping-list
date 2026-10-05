package earth.cora.picnic.ui.settings

import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.platform.FakeNetworkStatus
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Settings offline (docs/DESIGN_STICKER.md §0.4, 5 October; item 356): saving a key with no connection keeps "Saved." and
 * a neutral "not checked" state, never the error on a key that may be fine; it is checked by itself once the connection
 * returns; a connection problem while online is neutral too, and a wrong key is still the error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsOfflineTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Fixture(val vm: SettingsViewModel, val assistant: FakeAssistant, val network: FakeNetworkStatus)

    private fun TestScope.fixture(online: Boolean, assistant: FakeAssistant = FakeAssistant()): Fixture {
        val network = FakeNetworkStatus(online)
        val vm = SettingsViewModel(InMemorySettingsRepository(), assistant, network = network)
        advanceUntilIdle()
        return Fixture(vm, assistant, network)
    }

    @Test
    fun savingOfflineKeepsSavedAndChecksOnceBackOnline() = runTest {
        val f = fixture(online = false)
        f.vm.setTokenInput("test-key-0123456789abcdef")
        f.vm.saveConnection()
        advanceUntilIdle()
        assertTrue("the key is stored", f.vm.state.value.hasToken)
        assertEquals(ConnectionTestUi.Unchecked(offline = true), f.vm.state.value.test)
        assertEquals("no request offline", 0, f.assistant.tests)

        f.network.goOnline()
        advanceUntilIdle()
        assertEquals("checked by itself once back online", 1, f.assistant.tests)
        assertTrue(f.vm.state.value.test is ConnectionTestUi.Ok)
    }

    @Test
    fun aConnectionProblemIsNeutralAWrongKeyIsNot() = runTest {
        var error: LlmError = LlmError.Network(IOException("unreachable"), beforeConnect = true)
        val f = fixture(online = true, assistant = FakeAssistant(testConnection = { throw error }))
        f.vm.setTokenInput("test-key-0123456789abcdef")
        f.vm.saveConnection()
        advanceUntilIdle()
        assertEquals(ConnectionTestUi.Unchecked(offline = false), f.vm.state.value.test)

        error = LlmError.Timeout()
        f.vm.testConnection()
        advanceUntilIdle()
        assertEquals(ConnectionTestUi.Unchecked(offline = false), f.vm.state.value.test)

        error = LlmError.Offline()
        f.vm.testConnection()
        advanceUntilIdle()
        assertEquals("the phone went offline mid-call", ConnectionTestUi.Unchecked(offline = true), f.vm.state.value.test)

        error = LlmError.Unauthorized(401)
        f.vm.testConnection()
        advanceUntilIdle()
        assertTrue(f.vm.state.value.test is ConnectionTestUi.Failed)
    }

    @Test
    fun aKeyThatCouldNotBeCheckedOnlineWaitsForANewConnection() = runTest {
        var reachable = false
        val f = fixture(
            online = true,
            assistant = FakeAssistant(testConnection = {
                if (!reachable) throw LlmError.Network(IOException("no route"), beforeConnect = true)
                earth.cora.picnic.core.assistant.ConnectionCheck("gpt-5.6-terra", FakeAssistant.meta(300))
            }),
        )
        f.vm.setTokenInput("test-key-0123456789abcdef")
        f.vm.saveConnection()
        advanceUntilIdle()
        assertEquals(ConnectionTestUi.Unchecked(offline = false), f.vm.state.value.test)
        // The phone moves to another network (Wi-Fi swapped for mobile data): checked again by itself.
        reachable = true
        f.network.switchNetwork()
        advanceUntilIdle()
        assertTrue(f.vm.state.value.test is ConnectionTestUi.Ok)
        assertEquals(2, f.assistant.tests)
    }
}

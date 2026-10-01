package earth.cora.picnic

import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saving a token catches up on work skipped without one (icon decisions, aisle placement). */
@OptIn(ExperimentalCoroutinesApi::class)
class OnTokenAddedTest {
    @Test
    fun firesOnEachNoTokenToTokenChangeOnly() = runTest {
        val settings = InMemorySettingsRepository(AppSettings())
        var calls = 0
        val job = launch { onTokenAdded(settings) { calls++ } }
        runCurrent()
        assertEquals("not for the initial value", 0, calls)

        settings.setToken("sk-proj-0123456789abcdef")
        runCurrent()
        assertEquals(1, calls)

        settings.setToken("sk-proj-fedcba9876543210") // replaced: still set
        settings.setDrawIcons(false)
        runCurrent()
        assertEquals(1, calls)

        settings.setToken(null)
        runCurrent()
        settings.setToken("sk-proj-abcdefghijklmnop1234")
        runCurrent()
        assertEquals(2, calls)
        job.cancel()
    }

    @Test
    fun aStartWithATokenDoesNotFireAndFailuresDoNotStopIt() = runTest {
        val settings = InMemorySettingsRepository(AppSettings(), storedToken = "sk-proj-0123456789abcdef")
        var calls = 0
        val job = launch {
            onTokenAdded(settings) {
                calls++
                error("boom")
            }
        }
        runCurrent()
        assertEquals(0, calls)
        settings.setToken(null)
        runCurrent()
        settings.setToken("sk-proj-0123456789abcdef")
        runCurrent()
        settings.setToken(null)
        runCurrent()
        settings.setToken("sk-proj-0123456789abcdef")
        runCurrent()
        assertEquals(2, calls)
        job.cancel()
    }
}

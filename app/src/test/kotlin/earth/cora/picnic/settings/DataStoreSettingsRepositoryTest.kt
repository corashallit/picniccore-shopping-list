package earth.cora.picnic.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import earth.cora.picnic.core.llm.LlmConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var file: File

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        file = File(tmp.root, "settings.preferences_pb")
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun repo(tokens: TokenStore = InMemoryTokenStore()) =
        DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, tokens)

    @Test
    fun defaults() = runBlocking {
        val s = repo().current()
        assertEquals(LlmConfig.DEFAULT_BASE_URL, s.baseUrl)
        assertTrue(s.drawIcons)
        assertFalse(s.hasToken)
        assertNull(s.tokenHint)
        assertTrue(s.usesDefaultEndpoint)
    }

    @Test
    fun baseUrlIsNormalizedValidatedAndResettable() = runBlocking {
        val r = repo()
        assertTrue(r.setBaseUrl("  https://picnic.cora.earth/v1/ "))
        assertEquals("https://picnic.cora.earth/v1", r.current().baseUrl)
        assertEquals("https://picnic.cora.earth/v1", r.llmConfig().baseUrl)
        assertFalse(r.settings.first().usesDefaultEndpoint)

        assertFalse(r.setBaseUrl("http://picnic.cora.earth/v1"))
        assertFalse(r.setBaseUrl("not a url"))
        assertEquals("https://picnic.cora.earth/v1", r.current().baseUrl)

        assertTrue(r.setBaseUrl(""))
        assertEquals(LlmConfig.DEFAULT_BASE_URL, r.current().baseUrl)
    }

    @Test
    fun drawIconsToggle() = runBlocking {
        val r = repo()
        r.setDrawIcons(false)
        assertFalse(r.current().drawIcons)
        r.setDrawIcons(true)
        assertTrue(r.current().drawIcons)
    }

    @Test
    fun tokenIsStoredOnlyAsAMaskedHint() = runBlocking {
        val tokens = InMemoryTokenStore()
        val r = repo(tokens)
        r.setToken("  sk-proj-abcdefghijklmnop1234  ")
        val s = r.current()
        assertTrue(s.hasToken)
        assertEquals("sk-…1234", s.tokenHint)
        assertFalse(s.toString().contains("abcdefghijklmnop"))
        assertEquals("sk-proj-abcdefghijklmnop1234", r.token())
        assertEquals("sk-proj-abcdefghijklmnop1234", tokens.read())
        // The DataStore file never holds the token.
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("abcdefghijklmnop"))

        r.setToken("   ")
        assertFalse(r.current().hasToken)
        assertNull(r.token())
    }

    @Test
    fun undecryptableTokenFlipsHasTokenOff() = runBlocking {
        val tokens = InMemoryTokenStore()
        val r = repo(tokens)
        r.setToken("pk_live_0123456789abcdef")
        tokens.write(null) // e.g. Keystore key invalidated: the store now reads null
        assertNull(r.token())
        assertFalse(r.current().hasToken)
    }

    @Test
    fun aTransientlyUnreadableTokenKeepsItsHint() = runBlocking {
        var busy = false
        val tokens = object : TokenStore {
            var stored: String? = null
            override suspend fun read(): String? {
                if (busy) throw IOException("keystore busy")
                return stored
            }
            override suspend fun write(token: String?) {
                stored = token
            }
        }
        val r = repo(tokens)
        r.setToken("pk_live_0123456789abcdef")
        busy = true
        assertNull("this call goes without a token", r.token())
        assertTrue("but the token is not forgotten", r.current().hasToken)
        busy = false
        assertEquals("pk_live_0123456789abcdef", r.token())
    }

    @Test
    fun failingTokenStoreLeavesNoTokenAndDoesNotThrow() = runBlocking {
        val failing = object : TokenStore {
            override suspend fun read(): String? = null
            override suspend fun write(token: String?) {
                if (token != null) throw IOException("keystore unavailable")
            }
        }
        val r = repo(failing)
        r.setToken("sk-proj-abcdefghijklmnop1234")
        assertFalse(r.current().hasToken)
    }

    @Test
    fun safetyIdentifierIsStableAndHashed() = runBlocking {
        // One DataStore per file per process: reuse the instance (it persists the install id).
        val r = repo()
        val first = r.llmConfig().safetyIdentifier
        assertTrue(first!!.matches(Regex("[0-9a-f]{32}")))
        assertEquals(first, r.llmConfig().safetyIdentifier)
        assertTrue(file.length() > 0)
        assertEquals(
            "a665a45920422f9d417e4867efdc4fb8",
            DataStoreSettingsRepository.safetyIdentifierFor("123"),
        )
    }
}

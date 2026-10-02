package earth.cora.picnic.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    // One DataStore per file per process: a test that seeds the file reuses that instance.
    private fun dataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }

    private fun repo(tokens: TokenStore = InMemoryTokenStore(), store: DataStore<Preferences> = dataStore()) =
        DataStoreSettingsRepository(store, tokens)

    /** What a build with the server-address setting left behind. */
    private suspend fun seedLegacy(store: DataStore<Preferences>, baseUrl: String, hint: String) {
        store.edit {
            it[DataStoreSettingsRepository.LEGACY_BASE_URL] = baseUrl
            it[DataStoreSettingsRepository.TOKEN_HINT] = hint
        }
    }

    @Test
    fun defaults() = runBlocking {
        val r = repo()
        val s = r.current()
        assertTrue(s.drawIcons)
        assertFalse(s.hasToken)
        assertNull(s.tokenHint)
        assertEquals(LlmConfig.DEFAULT_BASE_URL, r.llmConfig().baseUrl)
    }

    @Test
    fun upgradeDeletesAKeySavedForAnotherServerBeforeItCanBeSent() = runBlocking {
        val store = dataStore()
        seedLegacy(store, "https://lists.example/v1", "ab_…cdef")
        val tokens = InMemoryTokenStore("ab_live_0123456789abcdef")
        val r = repo(tokens, store)

        assertNull("the very first read already goes without it", r.token())
        assertNull(tokens.read())
        val s = r.settings.first()
        assertFalse(s.hasToken)
        assertNull(s.tokenHint)
        assertNull(store.data.first()[DataStoreSettingsRepository.LEGACY_BASE_URL])
        assertEquals(LlmConfig.DEFAULT_BASE_URL, r.llmConfig().baseUrl)

        // Runs once: a key pasted afterwards stays.
        r.setToken("sk-proj-abcdefghijklmnop1234")
        assertEquals("sk-proj-abcdefghijklmnop1234", r.token())
        assertTrue(r.current().hasToken)
    }

    @Test
    fun upgradeRunsBeforeTheFirstSettingsEmission() = runBlocking {
        val store = dataStore()
        seedLegacy(store, "https://lists.example/v1", "ab_…cdef")
        val tokens = InMemoryTokenStore("ab_live_0123456789abcdef")
        assertFalse(repo(tokens, store).settings.first().hasToken)
        assertNull(tokens.read())
    }

    @Test
    fun upgradeKeepsAKeySavedForOpenAi() = runBlocking {
        val store = dataStore()
        seedLegacy(store, "https://API.openai.com:443/v1beta", "sk-…1234")
        val r = repo(InMemoryTokenStore("sk-proj-abcdefghijklmnop1234"), store)
        assertEquals("sk-proj-abcdefghijklmnop1234", r.token())
        assertEquals("sk-…1234", r.current().tokenHint)
        assertNull(store.data.first()[DataStoreSettingsRepository.LEGACY_BASE_URL])
        assertEquals(LlmConfig.DEFAULT_BASE_URL, r.llmConfig().baseUrl)
    }

    @Test
    fun anUnfinishedUpgradeSendsNoKeyAndTriesAgain() = runBlocking {
        val store = dataStore()
        seedLegacy(store, "https://lists.example/v1", "ab_…cdef")
        var busy = true
        val tokens = object : TokenStore {
            var stored: String? = "ab_live_0123456789abcdef"
            override suspend fun read(): String? = stored
            override suspend fun write(token: String?) {
                if (busy) throw IOException("can't delete right now")
                stored = token
            }
        }
        val r = repo(tokens, store)
        assertNull("never sent while the old key is still there", r.token())
        assertEquals("ab_live_0123456789abcdef", tokens.stored)
        busy = false
        assertNull(r.token())
        assertNull(tokens.stored)
        assertFalse(r.current().hasToken)
    }

    @Test
    fun sameHostComparesHostAndPort() {
        assertTrue(DataStoreSettingsRepository.sameHost("https://a.example/v1", "https://A.example:443/other"))
        assertFalse(DataStoreSettingsRepository.sameHost("https://a.example/v1", "https://a.example:8443/v1"))
        assertFalse(DataStoreSettingsRepository.sameHost("https://a.example/v1", "https://b.example/v1"))
        assertFalse(DataStoreSettingsRepository.sameHost("not a url", "not a url"))
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
        r.setToken("sk-proj-0123456789abcdef")
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
        r.setToken("sk-proj-0123456789abcdef")
        busy = true
        assertNull("this call goes without a token", r.token())
        assertTrue("but the token is not forgotten", r.current().hasToken)
        busy = false
        assertEquals("sk-proj-0123456789abcdef", r.token())
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

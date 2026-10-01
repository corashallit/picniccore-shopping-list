package earth.cora.picnic.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.platform.PicnicLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production settings (docs/ARCHITECTURE.md §9): Preferences DataStore at
 * filesDir/datastore/settings.preferences_pb (not backed up) plus the encrypted token in [tokenStore].
 *
 * Keys: `base_url` (absent = LlmConfig.DEFAULT_BASE_URL), `draw_icons` (absent = true),
 * `token_hint` (masked token; present = a token is stored), `install_id` (random UUID, created on
 * first use; hashed into LlmConfig.safetyIdentifier). The token itself never enters DataStore,
 * a Flow, a data class or a log line: it is decrypted only inside [token].
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val tokenStore: TokenStore,
) : SettingsRepository {
    /** One instance per process (AppContainer): DataStore forbids two instances on one file. */
    constructor(context: Context, tokenStore: TokenStore) : this(
        PreferenceDataStoreFactory.create(
            produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
        ),
        tokenStore,
    )

    private val tokenChecked = AtomicBoolean(false)

    private val prefs: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) {
            PicnicLog.w("settings unreadable; using defaults", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    override val settings: Flow<AppSettings> = prefs
        .map { it.toSettings() }
        .distinctUntilChanged()
        // Once per process: a token that can no longer be decrypted (key invalidated) reads as unset.
        .onStart { if (tokenChecked.compareAndSet(false, true)) token() }

    override suspend fun current(): AppSettings = prefs.first().toSettings()

    override suspend fun setBaseUrl(url: String): Boolean {
        if (url.isBlank()) {
            dataStore.edit { it.remove(BASE_URL) }
            return true
        }
        val normalized = LlmConfig.normalizeBaseUrl(url) ?: return false
        dataStore.edit {
            if (normalized == LlmConfig.DEFAULT_BASE_URL) it.remove(BASE_URL) else it[BASE_URL] = normalized
        }
        return true
    }

    override suspend fun setDrawIcons(enabled: Boolean) {
        dataStore.edit { if (enabled) it.remove(DRAW_ICONS) else it[DRAW_ICONS] = false }
    }

    /**
     * Stores the token encrypted (blank/null clears it). If the Keystore refuses (rare OEM bugs),
     * nothing is stored and [AppSettings.hasToken] stays false, so the UI asks again; never throws
     * for that, and never logs the token.
     */
    override suspend fun setToken(token: String?) {
        val clean = token?.trim()?.takeIf { it.isNotEmpty() }
        val stored = try {
            tokenStore.write(clean)
            clean
        } catch (e: IOException) {
            PicnicLog.w("couldn't store the token", e)
            runCatching { tokenStore.write(null) }
            null
        }
        dataStore.edit { if (stored == null) it.remove(TOKEN_HINT) else it[TOKEN_HINT] = maskToken(stored) }
    }

    override suspend fun token(): String? {
        val token = try {
            tokenStore.read()?.takeIf { it.isNotBlank() }
        } catch (e: IOException) {
            // Temporarily unreadable (a busy Keystore, a file error): the token and its hint stay, and
            // this call goes without one (core reports NotConfigured; the next call tries again).
            PicnicLog.w("token temporarily unreadable", e)
            return null
        }
        if (token == null && prefs.first()[TOKEN_HINT] != null) {
            // Gone for good (key invalidated, blob deleted): show "no token" so the UI asks again.
            try {
                dataStore.edit { it.remove(TOKEN_HINT) }
            } catch (e: IOException) {
                PicnicLog.w("couldn't clear the token hint", e)
            }
        }
        return token
    }

    override suspend fun llmConfig(): LlmConfig {
        val p = prefs.first()
        val installId = p[INSTALL_ID] ?: run {
            // edit{} is serialized by DataStore: concurrent first calls agree on one id.
            dataStore.edit { if (it[INSTALL_ID] == null) it[INSTALL_ID] = UUID.randomUUID().toString() }
            prefs.first()[INSTALL_ID]
        }
        return LlmConfig(
            baseUrl = p[BASE_URL] ?: LlmConfig.DEFAULT_BASE_URL,
            safetyIdentifier = installId?.let(::safetyIdentifierFor),
        )
    }

    private fun Preferences.toSettings(): AppSettings {
        val hint = this[TOKEN_HINT]
        return AppSettings(
            baseUrl = this[BASE_URL] ?: LlmConfig.DEFAULT_BASE_URL,
            drawIcons = this[DRAW_ICONS] ?: true,
            hasToken = hint != null,
            tokenHint = hint,
        )
    }

    companion object {
        const val FILE_NAME: String = "settings"

        internal val BASE_URL = stringPreferencesKey("base_url")
        internal val DRAW_ICONS = booleanPreferencesKey("draw_icons")
        internal val TOKEN_HINT = stringPreferencesKey("token_hint")
        internal val INSTALL_ID = stringPreferencesKey("install_id")

        /** First 32 hex characters of sha256(installId): opaque, stable per install, not the user's identity. */
        fun safetyIdentifierFor(installId: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(installId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(32)
    }
}

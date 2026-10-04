package earth.cora.picnic.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.platform.PicnicLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production settings (docs/ARCHITECTURE.md §9): Preferences DataStore at
 * filesDir/datastore/settings.preferences_pb (not backed up) plus the encrypted OpenAI key in [tokenStore].
 *
 * Keys: `draw_icons` (absent = true), `token_hint` (masked key; present = a key is stored),
 * `install_id` (random UUID, created on first use; hashed into LlmConfig.safetyIdentifier), the Surprises switches
 * (`eggs`, `sounds`, `haptics`, `next_stop_card`, `celebration`, `weather`, `use_my_area`; absent = the switch's
 * default, [Surprise.default]), the egg state `dusk_binder` and `surprise_lab` (absent = false) and `ants_day` (egg 17's
 * last parade, her local date as yyyy-mm-dd; absent = never), and `seen_books` (the trip books the album has shown, §7.8;
 * absent = none). Builds
 * with a server-address setting also stored `base_url`; [migrateOnce] removes it. The key itself
 * never enters DataStore, a Flow, a data class or a log line: it is decrypted only inside [token].
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
    private val migration = Mutex()

    @Volatile
    private var migrated = false

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
        .onStart {
            migrateQuietly()
            // Once per process: a key that can no longer be decrypted (Keystore key invalidated) reads as unset.
            if (tokenChecked.compareAndSet(false, true)) token()
        }

    override suspend fun current(): AppSettings {
        migrateQuietly()
        return prefs.first().toSettings()
    }

    override suspend fun setDrawIcons(enabled: Boolean) {
        dataStore.edit { if (enabled) it.remove(DRAW_ICONS) else it[DRAW_ICONS] = false }
    }

    override suspend fun setSurprise(surprise: Surprise, on: Boolean) {
        val key = SURPRISE_KEYS.getValue(surprise)
        dataStore.edit { if (on == surprise.default) it.remove(key) else it[key] = on }
    }

    override suspend fun setDuskBinder(on: Boolean) {
        dataStore.edit { if (on) it[DUSK_BINDER] = true else it.remove(DUSK_BINDER) }
    }

    override suspend fun setSurpriseLab(unlocked: Boolean) {
        dataStore.edit { if (unlocked) it[SURPRISE_LAB] = true else it.remove(SURPRISE_LAB) }
    }

    override suspend fun antsDay(): java.time.LocalDate? =
        prefs.first()[ANTS_DAY]?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }

    override suspend fun markAntsDay(day: java.time.LocalDate) {
        dataStore.edit { it[ANTS_DAY] = day.toString() }
    }

    override val seenBooks: Flow<Set<String>> = prefs.map { it[SEEN_BOOKS].orEmpty() }.distinctUntilChanged()

    override suspend fun markBooksSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { it[SEEN_BOOKS] = it[SEEN_BOOKS].orEmpty() + ids }
    }

    /**
     * Stores the key encrypted (blank/null clears it). If the Keystore refuses (rare OEM bugs),
     * nothing is stored and [AppSettings.hasToken] stays false, so the UI asks again; never throws
     * for that, and never logs the key.
     */
    override suspend fun setToken(token: String?) {
        // A pending upgrade must not delete the key saved here afterwards.
        migrateOnce()
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
        try {
            migrateOnce()
        } catch (e: IOException) {
            // Can't tell yet whether the stored key was saved for another server: send none this time.
            PicnicLog.w("settings upgrade pending; no key for this call", e)
            return null
        }
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
        // Always OpenAI: there is no address setting (LlmConfig's default base URL).
        return LlmConfig(safetyIdentifier = installId?.let(::safetyIdentifierFor))
    }

    /**
     * One-time upgrade from builds that had a server-address setting (since removed): a stored
     * `base_url` is dropped, so requests go to OpenAI again. A key saved for another host is deleted
     * first, so it is never sent to OpenAI; a key saved for OpenAI's own host is kept. Throws
     * [IOException] when the settings can't be read or the old key can't be deleted: nothing has
     * changed then, and the next call tries again.
     */
    private suspend fun migrateOnce() {
        if (migrated) return
        migration.withLock {
            if (migrated) return
            val legacyUrl = dataStore.data.first()[LEGACY_BASE_URL]
            if (legacyUrl != null) {
                val keepKey = sameHost(legacyUrl, LlmConfig.DEFAULT_BASE_URL)
                if (!keepKey) {
                    tokenStore.write(null)
                    PicnicLog.w("removed the key saved for a custom server address; the app now talks to OpenAI only")
                }
                dataStore.edit {
                    it.remove(LEGACY_BASE_URL)
                    if (!keepKey) it.remove(TOKEN_HINT)
                }
            }
            migrated = true
        }
    }

    /** For reads that fall back to what DataStore holds: [token] stays the gate for the key itself. */
    private suspend fun migrateQuietly() {
        try {
            migrateOnce()
        } catch (e: IOException) {
            PicnicLog.w("settings upgrade failed; will retry", e)
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val hint = this[TOKEN_HINT]
        val base = AppSettings(
            drawIcons = this[DRAW_ICONS] ?: true,
            hasToken = hint != null,
            tokenHint = hint,
            duskBinder = this[DUSK_BINDER] ?: false,
            surpriseLab = this[SURPRISE_LAB] ?: false,
        )
        return Surprise.entries.fold(base) { s, surprise -> s.with(surprise, this[SURPRISE_KEYS.getValue(surprise)] ?: surprise.default) }
    }

    companion object {
        const val FILE_NAME: String = "settings"

        /** Written only by builds with a server-address setting; read once by [migrateOnce], then removed. */
        internal val LEGACY_BASE_URL = stringPreferencesKey("base_url")
        internal val DRAW_ICONS = booleanPreferencesKey("draw_icons")
        internal val TOKEN_HINT = stringPreferencesKey("token_hint")
        internal val INSTALL_ID = stringPreferencesKey("install_id")
        internal val DUSK_BINDER = booleanPreferencesKey("dusk_binder")
        internal val SURPRISE_LAB = booleanPreferencesKey("surprise_lab")
        internal val SEEN_BOOKS = stringSetPreferencesKey("seen_books")
        internal val ANTS_DAY = stringPreferencesKey("ants_day")

        /** One boolean key per Surprises switch, named [Surprise.key]; absent = [Surprise.default]. */
        internal val SURPRISE_KEYS: Map<Surprise, Preferences.Key<Boolean>> = Surprise.entries.associateWith { booleanPreferencesKey(it.key) }

        /** Same scheme-less authority (host and port, default ports filled in); unparsable URLs never match. */
        internal fun sameHost(a: String, b: String): Boolean {
            fun authority(url: String) = runCatching {
                val uri = URI(url.trim())
                val host = uri.host?.lowercase() ?: return@runCatching null
                val port = if (uri.port != -1) uri.port else if (uri.scheme.equals("http", true)) 80 else 443
                "$host:$port"
            }.getOrNull()
            val x = authority(a)
            return x != null && x == authority(b)
        }

        /** First 32 hex characters of sha256(installId): opaque, stable per install, not the user's identity. */
        fun safetyIdentifierFor(installId: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(installId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(32)
    }
}

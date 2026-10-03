package earth.cora.picnic.settings

import earth.cora.picnic.core.llm.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-local settings (NOT in state.json, not backed up). CONTRACT (docs/ARCHITECTURE.md §9).
 * Amendment A1: the user's OpenAI key (with "Test connection") and "Draw new icons with AI".
 * Amendment A4 (docs/DESIGN_STICKER.md §6.8, §19.2, §0.4): the Surprises switches below. Requests always go
 * to LlmConfig.DEFAULT_BASE_URL (api.openai.com; the opt-in weather feed is the one exception); models are
 * fixed in core's ModelDefaults; reduced motion follows the system. The key itself is never part of this
 * class: only whether one is set and a masked hint.
 * DataStore keys (DataStoreSettingsRepository): eggs, sounds, haptics, next_stop_card, celebration, weather,
 * use_my_area, dusk_binder, surprise_lab; and seen_books (the album's shown trip books, §7.8). Consumers ask [allows] for a Surprises switch, so SurprisesGatingTest
 * covers every gate in one table.
 */
data class AppSettings(
    /** "Draw new icons with AI" (icon cascade step 3; about 10 s per icon, on her OpenAI key). */
    val drawIcons: Boolean = true,
    /** Read-only for UI: a key is stored and decryptable. */
    val hasToken: Boolean = false,
    /** Read-only for UI: masked key, e.g. "sk-…a1b2" (see [maskToken]). */
    val tokenHint: String? = null,
    /** Surprises → Easter eggs ("all 18 secrets"). */
    val eggs: Boolean = true,
    /** Surprises → Sounds (off by default; never in silent or vibrate, §14.1). */
    val sounds: Boolean = false,
    /** Surprises → Haptics. */
    val haptics: Boolean = true,
    /** Surprises → "Next stop" card (§10). */
    val nextStopCard: Boolean = true,
    /** Surprises → Celebration (§11). */
    val celebration: Boolean = true,
    /** Surprises → Weather surprises (off by default; the Environment Canada feed, §12.4, Q3). */
    val weather: Boolean = false,
    /** Surprises → Use my area (off by default; coarse location only, rounded to 0.1°, §0.4 Q6). */
    val useMyArea: Boolean = false,
    /** Egg 1: the Dusk binder is saved (kept while eggs are off). */
    val duskBinder: Boolean = false,
    /** The Surprise lab is unlocked (7 taps on the version line, §6.9). */
    val surpriseLab: Boolean = false,
) {
    /** Whether the Surprises switch [surprise] is on (§6.8). Each switch gates exactly its own feature (item 228). */
    fun allows(surprise: Surprise): Boolean = when (surprise) {
        Surprise.EasterEggs -> eggs
        Surprise.Sounds -> sounds
        Surprise.Haptics -> haptics
        Surprise.NextStopCard -> nextStopCard
        Surprise.Celebration -> celebration
        Surprise.Weather -> weather
        Surprise.UseMyArea -> useMyArea
    }

    /** These settings with the Surprises switch [surprise] set to [on]. */
    fun with(surprise: Surprise, on: Boolean): AppSettings = when (surprise) {
        Surprise.EasterEggs -> copy(eggs = on)
        Surprise.Sounds -> copy(sounds = on)
        Surprise.Haptics -> copy(haptics = on)
        Surprise.NextStopCard -> copy(nextStopCard = on)
        Surprise.Celebration -> copy(celebration = on)
        Surprise.Weather -> copy(weather = on)
        Surprise.UseMyArea -> copy(useMyArea = on)
    }
}

/**
 * The switches of the Settings "Surprises" card (§6.8, §0.4), in the card's order, with their DataStore [key] and
 * default. Easter eggs gate every egg (§2.5); Sounds and Haptics gate Feedback (§14.1); the Next stop card and the
 * Celebration gate their scenes (§10, §11); Weather surprises gate the Environment Canada feed (eggs 11 and 16,
 * §12.4); Use my area gates the approximate location behind sunset and the forecast (PlaceSource, §0.4 Q6).
 */
enum class Surprise(val key: String, val default: Boolean) {
    EasterEggs("eggs", true),
    Sounds("sounds", false),
    Haptics("haptics", true),
    NextStopCard("next_stop_card", true),
    Celebration("celebration", true),
    Weather("weather", false),
    UseMyArea("use_my_area", false),
}

interface SettingsRepository {
    /** Emits the persisted settings (defaults for anything never changed), then every change. */
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun setDrawIcons(enabled: Boolean)

    /** Turns one Surprises switch on or off (§6.8). */
    suspend fun setSurprise(surprise: Surprise, on: Boolean)

    /** Egg 1: the Dusk binder is saved (true) or the moon was tapped (false). Kept while eggs are off (§2.5). */
    suspend fun setDuskBinder(on: Boolean)

    /** The Surprise lab is unlocked (7 taps on the version line, §6.9); persisted. */
    suspend fun setSurpriseLab(unlocked: Boolean)

    /**
     * Filed trip books (TripBook.id) whose album row has played its magic appear (§7.8: a newly filed book's row blooms
     * in once, the next time the album opens). Device-local, like the rest of these settings.
     */
    val seenBooks: Flow<Set<String>>

    /** Marks the filed trip books [ids] as shown in the album. */
    suspend fun markBooksSeen(ids: Collection<String>)

    /** Stores the OpenAI key encrypted (Android Keystore AES-GCM); null or blank clears it. Never logs it. */
    suspend fun setToken(token: String?)

    /** Decrypts on demand for the HTTP layer only (core TokenSource). Null if unset or undecryptable. */
    suspend fun token(): String?

    /** For core's ConfigSource: the OpenAI base URL + safetyIdentifier (first 32 hex chars of sha256(install id)). */
    suspend fun llmConfig(): LlmConfig
}

/** For previews, screenshot tests and ViewModel tests. */
class InMemorySettingsRepository(
    initial: AppSettings = AppSettings(),
    private var storedToken: String? = null,
) : SettingsRepository {
    private val state = MutableStateFlow(initial.withToken(storedToken))
    override val settings: Flow<AppSettings> = state.asStateFlow()

    override suspend fun current(): AppSettings = state.value

    override suspend fun setDrawIcons(enabled: Boolean) {
        state.value = state.value.copy(drawIcons = enabled)
    }

    override suspend fun setSurprise(surprise: Surprise, on: Boolean) {
        state.value = state.value.with(surprise, on)
    }

    override suspend fun setDuskBinder(on: Boolean) {
        state.value = state.value.copy(duskBinder = on)
    }

    override suspend fun setSurpriseLab(unlocked: Boolean) {
        state.value = state.value.copy(surpriseLab = unlocked)
    }

    private val seen = MutableStateFlow<Set<String>>(emptySet())
    override val seenBooks: Flow<Set<String>> = seen.asStateFlow()

    override suspend fun markBooksSeen(ids: Collection<String>) {
        seen.value = seen.value + ids
    }

    override suspend fun setToken(token: String?) {
        storedToken = token?.trim()?.takeIf { it.isNotEmpty() }
        state.value = state.value.withToken(storedToken)
    }

    override suspend fun token(): String? = storedToken

    override suspend fun llmConfig(): LlmConfig = LlmConfig(safetyIdentifier = "test-install")

    private fun AppSettings.withToken(token: String?) =
        copy(hasToken = token != null, tokenHint = token?.let(::maskToken))
}

/**
 * Write-only hint for the Settings key field. Shows a short known prefix and at most the last 4
 * characters: "sk-proj-abc…wxyz" -> "sk-…wxyz" (an underscore prefix such as "ab_…wxyz" works the same).
 * Short keys reveal less (2 characters under 16, none under 8), so the hint never gives away a
 * meaningful part.
 */
fun maskToken(token: String): String {
    val t = token.trim()
    val prefixWord = t.takeWhile { it != '-' && it != '_' }
    val prefix = if (prefixWord.length in 1..4 && t.length > prefixWord.length) prefixWord + t[prefixWord.length] else ""
    val tail = when {
        t.length >= 16 -> t.takeLast(4)
        t.length >= 8 -> t.takeLast(2)
        else -> ""
    }
    return prefix + "…" + tail
}

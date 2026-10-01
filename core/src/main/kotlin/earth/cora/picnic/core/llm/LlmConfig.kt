package earth.cora.picnic.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Values accepted by `reasoning.effort` (support varies by model; see ModelCapabilities). */
@Serializable
enum class ReasoningEffort(val wire: String) {
    @SerialName("none") NONE("none"),
    @SerialName("minimal") MINIMAL("minimal"),
    @SerialName("low") LOW("low"),
    @SerialName("medium") MEDIUM("medium"),
    @SerialName("high") HIGH("high"),
    @SerialName("xhigh") XHIGH("xhigh"),
    @SerialName("max") MAX("max"),
    ;

    companion object {
        fun fromWire(value: String): ReasoningEffort? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * THE ONE SOURCE OF TRUTH for model defaults (chosen by the live benchmark, research/openai_api.md §0).
 * The app persists only values the user changed, so new defaults reach existing installs.
 */
object LlmDefaults {
    const val BASE_URL: String = "https://api.openai.com/v1"

    /** Ingest (text, photo, transcript): reasoning + web search + strict JSON. */
    const val SMART_MODEL: String = "gpt-5.6-terra"
    val SMART_EFFORT: ReasoningEffort = ReasoningEffort.LOW

    /** recategorize + assignBaskets: no tools, strict JSON. */
    const val FAST_MODEL: String = "gpt-6-luna"
    val FAST_EFFORT: ReasoningEffort = ReasoningEffort.NONE

    const val TRANSCRIBE_MODEL: String = "gpt-transcribe"

    /** Live icons via /images/edits (real alpha). gpt-image-2 does NOT support transparent backgrounds. */
    const val IMAGE_MODEL: String = "gpt-image-2.5-flare"
    const val IMAGE_QUALITY: String = "low"
    const val IMAGE_SIZE: String = "1024x1024"

    const val WEB_SEARCH: Boolean = true
    const val SEARCH_CONTEXT_SIZE: String = "low"

    /** web_search user_location (country must be ISO-3166). */
    const val LOCATION_COUNTRY: String = "CA"
    const val LOCATION_REGION: String = "Ontario"
    const val LOCATION_TIMEZONE: String = "America/Toronto"

    /** Suggestions shown under the model fields in Settings (label -> model, effort). */
    val SMART_PRESETS: List<Triple<String, String, ReasoningEffort>> = listOf(
        Triple("Balanced (default)", SMART_MODEL, SMART_EFFORT),
        Triple("Best", "gpt-6.1-sol", ReasoningEffort.LOW),
        Triple("Budget", "gpt-6-luna", ReasoningEffort.LOW),
    )

    val CONNECT_TIMEOUT: Duration = 15.seconds
    val INGEST_TIMEOUT: Duration = 120.seconds
    val FAST_TIMEOUT: Duration = 45.seconds
    val TRANSCRIBE_TIMEOUT: Duration = 60.seconds
    val IMAGE_TIMEOUT: Duration = 120.seconds
    val TEST_CONNECTION_TIMEOUT: Duration = 20.seconds

    const val INGEST_MAX_OUTPUT_TOKENS: Int = 12_000
    const val FAST_MAX_OUTPUT_TOKENS: Int = 4_000
}

/**
 * Non-secret LLM settings. The token is NOT part of this class (it must never be in a data class
 * whose toString could be logged); it comes from [TokenSource] at request time.
 */
@Serializable
data class LlmConfig(
    /** https URL ending without a slash, e.g. https://api.openai.com/v1 or https://picnic.cora.earth/v1. */
    val baseUrl: String = LlmDefaults.BASE_URL,
    val smartModel: String = LlmDefaults.SMART_MODEL,
    val smartEffort: ReasoningEffort = LlmDefaults.SMART_EFFORT,
    val fastModel: String = LlmDefaults.FAST_MODEL,
    val fastEffort: ReasoningEffort = LlmDefaults.FAST_EFFORT,
    val transcribeModel: String = LlmDefaults.TRANSCRIBE_MODEL,
    val imageModel: String = LlmDefaults.IMAGE_MODEL,
    val imageQuality: String = LlmDefaults.IMAGE_QUALITY,
    val webSearch: Boolean = LlmDefaults.WEB_SEARCH,
    val searchContextSize: String = LlmDefaults.SEARCH_CONTEXT_SIZE,
    /** Opaque hashed per-install id sent as `safety_identifier` (never the user's identity). */
    val safetyIdentifier: String? = null,
)

/** Current config, read before every call (settings can change at runtime). */
fun interface ConfigSource {
    suspend fun config(): LlmConfig
}

/** Bearer token (user's OpenAI key or a proxy device token), decrypted on demand; null/blank = not configured. */
fun interface TokenSource {
    suspend fun token(): String?
}

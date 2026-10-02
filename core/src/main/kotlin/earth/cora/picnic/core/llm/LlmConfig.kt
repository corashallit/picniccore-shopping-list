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
 * WHERE to send requests (amendment A1: no model settings). Non-secret. The app always uses
 * [DEFAULT_BASE_URL] (there is no address setting); [baseUrl] stays configurable for tests and the
 * developer CLI. The key is NOT part of this class (it must never sit in a data class whose toString
 * could be logged); it comes from [TokenSource] at request time.
 */
data class LlmConfig(
    /** Normalized base URL (see [normalizeBaseUrl]), e.g. https://api.openai.com/v1. */
    val baseUrl: String = DEFAULT_BASE_URL,
    /** Opaque hashed per-install id sent as `safety_identifier` on /responses (never the user's identity). */
    val safetyIdentifier: String? = null,
) {
    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.openai.com/v1"

        /**
         * CONTRACT (used by OpenAiClient before every call): trims, drops trailing '/', requires a
         * parseable absolute URL with a host and scheme https (http is accepted only for localhost,
         * 127.0.0.1 and [::1]: tests and local development). Returns null when invalid, including
         * for blank input.
         */
        fun normalizeBaseUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val ok = when (uri.scheme?.lowercase()) {
                "https" -> true
                "http" -> host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1"
                else -> false
            }
            return if (ok && uri.rawQuery == null && uri.rawFragment == null) trimmed else null
        }
    }
}

/** Per-call timeouts (research/openai_api.md §2.8). Calls are cancellable well before these. */
object LlmTimeouts {
    val CONNECT: Duration = 15.seconds
    /** Reasoning + web search + open_page; observed max ~23 s. */
    val INGEST: Duration = 120.seconds
    /** recategorize, assignBaskets, IconDecider. */
    val FAST: Duration = 45.seconds
    val TRANSCRIBE: Duration = 60.seconds
    /** Observed 8-39 s; docs say up to 2 min. */
    val IMAGE: Duration = 120.seconds
    val TEST_CONNECTION: Duration = 20.seconds
}

/** Current connection config, read before every call (settings can change at runtime). */
fun interface ConfigSource {
    suspend fun config(): LlmConfig
}

/** Bearer token (the user's OpenAI API key), decrypted on demand; null/blank = not configured. */
fun interface TokenSource {
    suspend fun token(): String?
}

package earth.cora.picnic.core.llm

/**
 * Typed failures of every GroceryAssistant / OpenAiClient call (research/openai_api.md §2.8).
 * CONTRACT: core never throws anything else for API problems (CancellationException still propagates).
 * [friendlyMessage] is safe to show in the UI; [message] is for logs and never contains the token
 * or the server's raw 401 text (which echoes a masked key).
 */
sealed class LlmError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** x-request-id of the failed call, when there was a response. */
    abstract val requestId: String?

    /** One short sentence for the user. */
    abstract val friendlyMessage: String

    /** True when retrying later can help (offer "Try again"). */
    open val retryable: Boolean get() = false

    /** No token configured, or the base URL is not a valid https URL. */
    class NotConfigured(val reason: String, override val requestId: String? = null) : LlmError("Not configured: $reason") {
        override val friendlyMessage: String get() = "Add your API key or proxy token in Settings first."
    }

    /** 401/403: bad or revoked key/token, region or project not allowed. */
    class Unauthorized(val status: Int, override val requestId: String? = null) : LlmError("HTTP $status unauthorized") {
        override val friendlyMessage: String get() = "The API key or token was not accepted. Check it in Settings."
    }

    /** 429 rate limit / 503 overloaded, after the automatic retries were used up. */
    class RateLimited(val retryAfterSeconds: Long?, override val requestId: String? = null) : LlmError("Rate limited") {
        override val friendlyMessage: String get() = "The service is busy right now. Try again in a moment."
        override val retryable: Boolean get() = true
    }

    /** 429 insufficient_quota / spend or usage limits. Not retryable. */
    class QuotaExceeded(val code: String?, override val requestId: String? = null) : LlmError("Quota exceeded: $code") {
        override val friendlyMessage: String get() = "The API account is out of credit or over its spending limit."
    }

    /** DNS, TLS, connection reset, cleartext blocked, 5xx after retries. */
    class Network(cause: Throwable?, override val requestId: String? = null) : LlmError("Network error: ${cause?.javaClass?.simpleName}", cause) {
        override val friendlyMessage: String get() = "Couldn't reach the server. Check your connection and try again."
        override val retryable: Boolean get() = true
    }

    /** Call exceeded its timeout (ingest 120 s, transcribe 60 s, image 120 s). */
    class Timeout(override val requestId: String? = null) : LlmError("Timed out") {
        override val friendlyMessage: String get() = "That took too long. Try again, or try a shorter request."
        override val retryable: Boolean get() = true
    }

    /**
     * 400/404/other 4xx, status "incomplete", unparsable or schema-violating output.
     * [detail] is for logs; model_not_found gets its own friendly text.
     */
    class BadResponse(
        val detail: String,
        val httpStatus: Int? = null,
        val code: String? = null,
        override val requestId: String? = null,
    ) : LlmError("Bad response: $detail (status=$httpStatus code=$code)") {
        override val friendlyMessage: String
            get() = if (code == "model_not_found") {
                "That model isn't available to this key. Check the model names in Settings."
            } else {
                "The assistant's answer couldn't be used. Try again or rephrase."
            }
    }

    /** The model refused, or image moderation blocked the request (moderation_blocked). */
    class Refusal(val refusal: String, override val requestId: String? = null) : LlmError("Refusal: $refusal") {
        override val friendlyMessage: String get() = "The assistant couldn't help with that one."
    }
}

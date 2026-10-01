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

    /** No token configured, or the base URL fails LlmConfig.normalizeBaseUrl (no request was sent). */
    class NotConfigured(val reason: String, override val requestId: String? = null) : LlmError("Not configured: $reason") {
        override val friendlyMessage: String get() = "Add your API key or proxy token in Settings first."
    }

    /** 401/403 (except the proxy's 403 model_not_allowed, which is BadResponse): bad or revoked key/token, region or project not allowed. */
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

    /** Call exceeded its timeout (LlmTimeouts: ingest 120 s, transcribe 60 s, image 120 s). */
    class Timeout(override val requestId: String? = null) : LlmError("Timed out") {
        override val friendlyMessage: String get() = "That took too long. Try again, or try a shorter request."
        override val retryable: Boolean get() = true
    }

    /**
     * 400/404/other 4xx, status "incomplete", unparsable or schema-violating output, and the proxy's
     * own fatal 5xx codes. [detail] is for logs; model_not_found, a wrong address and a broken proxy
     * get their own friendly text.
     */
    class BadResponse(
        val detail: String,
        val httpStatus: Int? = null,
        val code: String? = null,
        override val requestId: String? = null,
    ) : LlmError("Bad response: $detail (status=$httpStatus code=$code)") {
        override val friendlyMessage: String
            get() = when (code) {
                // Models are fixed in ModelDefaults (no model settings): the key/proxy lacks access.
                "model_not_found", "model_not_allowed" ->
                    "This key or proxy can't use the AI model the app needs. Ask whoever set it up."
                ENDPOINT_NOT_FOUND -> "That server address doesn't look like an OpenAI-compatible API. Check the base URL in Settings."
                PROXY_UPSTREAM_AUTH_FAILED, PROXY_MISCONFIGURED ->
                    "The proxy can't reach OpenAI right now. Ask whoever runs it."
                else -> "The assistant's answer couldn't be used. Try again or rephrase."
            }

        /** Answer-level problems (incomplete, unparsable, no message) often succeed on a second try; 4xx do not. */
        override val retryable: Boolean get() = httpStatus == null
    }

    companion object {
        /** BadResponse.code for an HTTP 404 on a path the server doesn't serve (usually a wrong base URL). */
        const val ENDPOINT_NOT_FOUND: String = "endpoint_not_found"

        /** BadResponse.code: the proxy's own OpenAI key was rejected (HTTP 502). Not retryable. */
        const val PROXY_UPSTREAM_AUTH_FAILED: String = "upstream_auth_failed"

        /** BadResponse.code: the proxy's configuration is invalid (HTTP 500). Not retryable. */
        const val PROXY_MISCONFIGURED: String = "proxy_misconfigured"
    }

    /** The model refused, or image moderation blocked the request (moderation_blocked). */
    class Refusal(val refusal: String, override val requestId: String? = null) : LlmError("Refusal: $refusal") {
        override val friendlyMessage: String get() = "The assistant couldn't help with that one."
    }
}

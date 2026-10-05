package earth.cora.picnic.core.llm

/**
 * Typed failures of every GroceryAssistant / OpenAiClient call (research/openai_api.md §2.8).
 * CONTRACT: core never throws anything else for API problems (CancellationException still propagates).
 * [friendlyMessage] is safe to show in the UI (plain words, typographic apostrophes, no scope ids or codes); [message]
 * is for logs and never contains the key or the server's raw 401 text (which echoes a masked key).
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

    /** No key configured, or the base URL fails LlmConfig.normalizeBaseUrl (no request was sent). */
    class NotConfigured(val reason: String, override val requestId: String? = null) : LlmError("Not configured: $reason") {
        override val friendlyMessage: String get() = "Add your OpenAI key in Settings first."
    }

    /**
     * 401/403 (except a 403 model_not_found, which is BadResponse): a wrong, revoked or expired key, or
     * an unsupported region. [missingScopes] is non-null when the key works but lacks a permission
     * (a restricted key): the scope names OpenAI listed, possibly none.
     */
    class Unauthorized(
        val status: Int,
        override val requestId: String? = null,
        val missingScopes: List<String>? = null,
    ) : LlmError("HTTP $status unauthorized" + (missingScopes?.let { " (missing scopes: ${it.joinToString()})" } ?: "")) {
        override val friendlyMessage: String
            get() {
                if (missingScopes == null) return "OpenAI didn’t accept this key. Check it in Settings, or paste a new one."
                // She can't change a key's permissions herself: say whom to ask and what to turn on, in the words of
                // OpenAI's key dialog (docs/OPENAI_KEY.md), never the scope ids (those stay in [message], for logs).
                val permissions = missingScopes.mapNotNull(::permissionFor).distinct()
                val ask = if (permissions.isEmpty()) "check its permissions" else "allow " + permissions.joinToString(" and ")
                return "This key can’t do everything the app needs. Ask the person who made it to $ask."
            }

        private companion object {
            /** A missing scope as the permission row in OpenAI's restricted-key dialog that grants it. */
            fun permissionFor(scope: String): String? = when (scope.removePrefix("api.")) {
                "model.read" -> "List models (Read)"
                "model.request" -> "Model capabilities (Request)"
                "responses.write", "responses.read" -> "the Responses API (Write)"
                else -> null
            }
        }
    }

    /** 429 rate limit / 503 overloaded, after the automatic retries were used up. */
    class RateLimited(val retryAfterSeconds: Long?, override val requestId: String? = null) : LlmError("Rate limited") {
        override val friendlyMessage: String get() = "The service is busy right now. Try again in a moment."
        override val retryable: Boolean get() = true
    }

    /** 429 insufficient_quota / credit exhausted / spend or usage limits (e.g. the project's hard budget). Not retryable. */
    class QuotaExceeded(val code: String?, override val requestId: String? = null) : LlmError("Quota exceeded: $code") {
        override val friendlyMessage: String get() = "The OpenAI account behind this key is out of credit or over its monthly budget."
    }

    /**
     * The phone has no network at all (the client's connectivity hook said so, OpenAiClient): no request was sent and
     * nothing waited. The app tells a connection that is merely slow or broken apart from this one by [Network].
     */
    class Offline(override val requestId: String? = null) : LlmError("Offline: no request sent") {
        override val friendlyMessage: String get() = "You’re offline right now. Try again once you’re back online."
        override val retryable: Boolean get() = true
    }

    /**
     * DNS, TLS, connection refused or reset, a connect timeout, cleartext blocked, 5xx after retries. [beforeConnect] is
     * true when the call never got a connection to the server (DNS, refused, a connect timeout, a failed TLS handshake):
     * the request was not sent, so nothing reached OpenAI.
     */
    class Network(
        cause: Throwable?,
        override val requestId: String? = null,
        val beforeConnect: Boolean = false,
    ) : LlmError("Network error: ${cause?.javaClass?.simpleName}" + if (beforeConnect) " (no connection)" else "", cause) {
        override val friendlyMessage: String get() = "Couldn’t reach OpenAI. Check your connection?"
        override val retryable: Boolean get() = true
    }

    /**
     * A connected call exceeded its timeout (LlmTimeouts: ingest 120 s, transcribe 60 s, image 120 s). A call that never
     * got a connection is [Network], whatever ran out first.
     */
    class Timeout(override val requestId: String? = null) : LlmError("Timed out") {
        override val friendlyMessage: String get() = "That took too long. Try again, or try a shorter request."
        override val retryable: Boolean get() = true
    }

    /**
     * 400/404/other 4xx, status "incomplete", unparsable or schema-violating output. [detail] is for
     * logs; model_not_found (the model doesn't exist for this key, or its project doesn't allow it)
     * gets its own friendly text.
     */
    class BadResponse(
        val detail: String,
        val httpStatus: Int? = null,
        val code: String? = null,
        override val requestId: String? = null,
    ) : LlmError("Bad response: $detail (status=$httpStatus code=$code)") {
        override val friendlyMessage: String
            get() = when (code) {
                // Models are fixed in ModelDefaults (no model settings): the key's project lacks access.
                "model_not_found" ->
                    "This OpenAI key can’t use one of the AI models the app needs. Check which models its project allows."
                else -> "The assistant’s answer couldn’t be used. Try again or rephrase."
            }

        /** Answer-level problems (incomplete, unparsable, no message) often succeed on a second try; 4xx do not. */
        override val retryable: Boolean get() = httpStatus == null
    }

    /** The model refused, or image moderation blocked the request (moderation_blocked). */
    class Refusal(val refusal: String, override val requestId: String? = null) : LlmError("Refusal: $refusal") {
        override val friendlyMessage: String get() = "The assistant couldn’t help with that one."
    }
}

/**
 * The request never reached OpenAI: the phone is offline, or the call got no connection (DNS, refused, a connect
 * timeout). Safe to redo later without asking twice: nothing was sent.
 */
val LlmError.unreached: Boolean get() = this is LlmError.Offline || (this is LlmError.Network && beforeConnect)

/** A failure of the connection itself (offline, unreachable, or a connected call that ran out of time): worth redoing once online. */
val LlmError.transport: Boolean get() = this is LlmError.Offline || this is LlmError.Network || this is LlmError.Timeout

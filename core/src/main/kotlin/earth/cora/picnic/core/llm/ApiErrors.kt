package earth.cora.picnic.core.llm

import earth.cora.picnic.core.CoreJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.time.Duration as JavaDuration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The `error` object of an OpenAI-shaped error body: {"error":{"message","type","param","code"}}. */
internal data class ApiErrorBody(val message: String?, val type: String?, val param: String?, val code: String?) {
    companion object {
        fun parse(body: ByteArray): ApiErrorBody? {
            if (body.isEmpty()) return null
            val root = runCatching { CoreJson.json.parseToJsonElement(body.decodeToString()) }.getOrNull() as? JsonObject
                ?: return null
            val error = root["error"] as? JsonObject ?: return null
            fun str(key: String) = (error[key] as? JsonPrimitive)?.contentOrNull
            return ApiErrorBody(str("message"), str("type"), str("param"), str("code"))
        }
    }
}

/** A non-2xx answer after the automatic retries; mapped to LlmError by [ApiErrors.toLlmError]. */
internal class ApiFailure(
    val status: Int,
    val error: ApiErrorBody?,
    val requestId: String?,
    val retryAfterSeconds: Long?,
) : Exception("HTTP $status ${error?.code ?: error?.type ?: ""}".trim())

internal object ApiErrors {
    private val QUOTA_CODES = setOf(
        "insufficient_quota",
        "credit_balance_exhausted",
        "organization_spend_limit_exceeded",
        "project_spend_limit_exceeded",
        "organization_usage_limit_exceeded",
    )

    /**
     * A key without a permission the endpoint needs (a restricted key) gets 401 "You have insufficient
     * permissions for this operation. Missing scopes: api.model.read. Check that you have the correct
     * role…". Unlike the invalid-key 401, this text names no key, only scopes.
     */
    private const val INSUFFICIENT_PERMISSIONS = "insufficient permissions"
    private val MISSING_SCOPES = Regex("""Missing scopes?:\s*([A-Za-z0-9_.,\s]+?)(?:\.\s|\.?$)""")
    private val SCOPE = Regex("""^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$""")

    fun isQuota(error: ApiErrorBody?): Boolean =
        error != null && (error.type == "insufficient_quota" || error.code in QUOTA_CODES)

    /** 429 rate limits (not quota) and transient 5xx are retried automatically. */
    fun isRetryable(status: Int, error: ApiErrorBody?): Boolean =
        (status == 429 && !isQuota(error)) || status == 500 || status == 502 || status == 503 || status == 504

    fun toLlmError(f: ApiFailure): LlmError {
        val e = f.error
        val rid = f.requestId
        val scopes = if (f.status == 401 || f.status == 403) missingScopes(e) else null
        return when {
            scopes != null -> LlmError.Unauthorized(f.status, rid, scopes)
            // The server's 401 text echoes a masked key: never keep it.
            f.status == 401 -> LlmError.Unauthorized(401, rid)
            // "Project `proj_…` does not have access to model `…`": the project's allowed models exclude it.
            f.status == 403 && e?.code == "model_not_found" ->
                LlmError.BadResponse("model not available to this key's project", 403, e.code, rid)
            f.status == 403 -> LlmError.Unauthorized(403, rid)
            f.status == 429 && isQuota(e) -> LlmError.QuotaExceeded(e?.code ?: e?.type, rid)
            f.status == 429 || f.status == 503 -> LlmError.RateLimited(f.retryAfterSeconds, rid)
            f.status >= 500 -> LlmError.Network(IOException("HTTP ${f.status}"), rid)
            e?.code == "moderation_blocked" -> LlmError.Refusal("moderation_blocked", rid)
            else -> LlmError.BadResponse(
                detail = (e?.message ?: "HTTP ${f.status}").take(300) + (e?.param?.let { " (param $it)" } ?: ""),
                httpStatus = f.status,
                code = e?.code,
                requestId = rid,
            )
        }
    }

    /**
     * For the "insufficient permissions" error: the scope names it lists (only well-formed names such as
     * `api.model.read`, nothing else from the server's text), or an empty list when it names none.
     * Null for any other error.
     */
    fun missingScopes(error: ApiErrorBody?): List<String>? {
        val message = error?.message ?: return null
        if (!message.contains(INSUFFICIENT_PERMISSIONS, ignoreCase = true)) return null
        val listed = MISSING_SCOPES.find(message)?.groupValues?.get(1).orEmpty()
        return listed.split(',', ' ').map { it.trim() }.filter { SCOPE.matches(it) }.distinct().take(5)
    }

    /**
     * Seconds to wait from `retry-after-ms`, `retry-after` (seconds or an HTTP date), rounded up.
     * Null when absent or unparsable.
     */
    fun retryAfterSeconds(retryAfterMs: String?, retryAfter: String?, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        retryAfterMs?.trim()?.toDoubleOrNull()?.let { ms -> return kotlin.math.ceil(ms / 1000.0).toLong().coerceAtLeast(0) }
        val value = retryAfter?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toDoubleOrNull()?.let { return kotlin.math.ceil(it).toLong().coerceAtLeast(0) }
        return runCatching {
            val at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
            JavaDuration.between(now, at).seconds.coerceAtLeast(0)
        }.getOrNull()
    }
}

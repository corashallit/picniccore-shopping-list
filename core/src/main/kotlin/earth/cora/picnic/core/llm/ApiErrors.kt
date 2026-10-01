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
     * Cora's proxy's own failures (docs/PROXY.md): its OpenAI key was rejected (502) or its config is
     * broken (500). Retrying or blaming the phone's connection can't help; whoever runs it must act.
     */
    private val PROXY_FATAL_CODES = setOf(LlmError.PROXY_UPSTREAM_AUTH_FAILED, LlmError.PROXY_MISCONFIGURED)

    fun isQuota(error: ApiErrorBody?): Boolean =
        error != null && (error.type == "insufficient_quota" || error.code in QUOTA_CODES)

    /** 429 rate limits (not quota) and transient 5xx are retried automatically (not the proxy's fatal codes). */
    fun isRetryable(status: Int, error: ApiErrorBody?): Boolean =
        ((status == 429 && !isQuota(error)) || status == 500 || status == 502 || status == 503 || status == 504) &&
            error?.code !in PROXY_FATAL_CODES

    fun toLlmError(f: ApiFailure): LlmError {
        val e = f.error
        val rid = f.requestId
        return when {
            // The server's 401 text echoes a masked key: never keep it.
            f.status == 401 -> LlmError.Unauthorized(401, rid)
            f.status == 403 && e?.code == "model_not_allowed" ->
                LlmError.BadResponse("model not allowed by the proxy", 403, e.code, rid)
            f.status == 403 -> LlmError.Unauthorized(403, rid)
            f.status == 429 && isQuota(e) -> LlmError.QuotaExceeded(e?.code ?: e?.type, rid)
            e?.code in PROXY_FATAL_CODES ->
                LlmError.BadResponse(e?.message.orEmpty().take(300).ifEmpty { "HTTP ${f.status}" }, f.status, e?.code, rid)
            f.status == 429 || f.status == 503 -> LlmError.RateLimited(f.retryAfterSeconds, rid)
            f.status >= 500 -> LlmError.Network(IOException("HTTP ${f.status}"), rid)
            e?.code == "moderation_blocked" -> LlmError.Refusal("moderation_blocked", rid)
            // A path the server doesn't serve: OpenAI answers an empty 404, Cora's proxy an
            // "Invalid URL (...)" error. Both usually mean a base URL without /v1 (or a typo).
            // model_not_found is a real model problem and keeps its own message.
            f.status == 404 && e?.code != "model_not_found" ->
                LlmError.BadResponse((e?.message ?: "HTTP 404 without an API error body").take(300), 404, LlmError.ENDPOINT_NOT_FOUND, rid)
            else -> LlmError.BadResponse(
                detail = (e?.message ?: "HTTP ${f.status}").take(300) + (e?.param?.let { " (param $it)" } ?: ""),
                httpStatus = f.status,
                code = e?.code,
                requestId = rid,
            )
        }
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

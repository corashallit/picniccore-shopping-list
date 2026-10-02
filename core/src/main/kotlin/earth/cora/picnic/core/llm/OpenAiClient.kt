package earth.cora.picnic.core.llm

import earth.cora.picnic.core.CoreJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A successful (HTTP 2xx) JSON API call. */
data class ApiResult(
    val json: JsonObject,
    /** x-request-id response header (log this, never the token). */
    val requestId: String?,
    val latencyMs: Long,
)

/** A successful image call: decoded data[0].b64_json bytes. */
class ImageResult(
    val bytes: ByteArray,
    val requestId: String?,
    val latencyMs: Long,
)

/**
 * Automatic retries for 429 rate limits (not quota) and 500/502/503/504: up to [maxRetries] extra
 * attempts, waiting [baseDelay] x 2^attempt plus up to [maxJitter], or the server's Retry-After when it
 * is at most [maxRetryAfter] (a longer Retry-After ends the retries). [sleep] exists for tests.
 */
data class RetryPolicy(
    val maxRetries: Int = 2,
    val baseDelay: Duration = 1.seconds,
    val maxJitter: Duration = 500.milliseconds,
    val maxRetryAfter: Duration = 20.seconds,
    val sleep: suspend (Duration) -> Unit = { delay(it) },
) {
    companion object {
        val DEFAULT: RetryPolicy = RetryPolicy()
    }
}

/**
 * One line per HTTP attempt: method, path, status, x-request-id and latency. Never headers, bodies or
 * the token. The default goes to java.util.logging (logcat on Android).
 */
fun interface LlmLog {
    fun log(line: String)

    companion object {
        val None: LlmLog = LlmLog { }
        val Default: LlmLog = object : LlmLog {
            private val logger = Logger.getLogger("earth.cora.picnic.core.llm")
            override fun log(line: String) = logger.log(Level.INFO, line)
        }
    }
}

/**
 * Thin OpenAI HTTP client on OkHttp (OkHttp stays an implementation detail of core:
 * `implementation`, not `api`). All paths are relative to LlmConfig.baseUrl, which the app always
 * leaves at api.openai.com (tests point it at a mock server).
 *
 * CONTRACT (details in docs/ARCHITECTURE.md §7.2):
 * - Reads [config] and [token] before every call. Missing/blank token or a base URL that fails
 *   LlmConfig.normalizeBaseUrl -> LlmError.NotConfigured (no request is sent).
 * - Sends `Authorization: Bearer <token>`; never logs headers, bodies or the token.
 * - Suspends with cancellation: cancelling the coroutine cancels the OkHttp call.
 * - Per-call timeout (OkHttp call timeout) = the [timeout] argument; connect timeout LlmTimeouts.CONNECT.
 * - Retries (max 2 extra attempts, backoff 1 s / 2 s + jitter, honours Retry-After up to 20 s):
 *   429 rate limits (not insufficient_quota / spend or usage limits), 500, 502, 503, 504.
 *   [imageEdit] is never retried.
 * - [responses] also performs the one-shot capability downgrade on HTTP 400 (param reasoning.effort,
 *   prompt_cache_options or prompt_cache_breakpoint), so a model whose parameter support
 *   ModelCapabilities doesn't know yet (a new ModelDefaults value, the developer CLI's --smart/--fast)
 *   still works.
 * - Maps every failure to LlmError (research/openai_api.md §2.8).
 */
class OpenAiClient(
    private val config: ConfigSource,
    private val token: TokenSource,
    private val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    private val log: LlmLog = LlmLog.Default,
) {
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(LlmTimeouts.CONNECT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            // Reasoning calls send nothing until they finish: only the per-call timeout applies.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * Core-internal hook for developer tooling (the live eval's token and cost accounting): sees every
     * successful JSON result with its API path. The app never sets it.
     */
    internal var resultObserver: (suspend (path: String, result: ApiResult) -> Unit)? = null

    /** POST {base}/responses with a JSON body (stream=false). */
    suspend fun responses(body: JsonObject, timeout: Duration = LlmTimeouts.INGEST): ApiResult {
        val conn = connection()
        var payload = ModelCapabilities.adapt(body)
        conn.safetyIdentifier?.let { payload = JsonObject(payload + ("safety_identifier" to JsonPrimitive(it))) }
        var effortDowngraded = false
        var cacheDowngraded = false
        while (true) {
            try {
                return sendJson(conn, "POST", "responses", payload, timeout)
            } catch (f: ApiFailure) {
                if (f.status != 400) throw ApiErrors.toLlmError(f)
                val param = f.error?.param.orEmpty()
                when {
                    param == "reasoning.effort" && !effortDowngraded -> {
                        effortDowngraded = true
                        val current = effortOf(payload) ?: throw ApiErrors.toLlmError(f)
                        val next = ModelCapabilities.nearestSupportedEffort(f.error?.message.orEmpty(), current)
                            ?: throw ApiErrors.toLlmError(f)
                        log.log("responses: effort ${current.wire} rejected, retrying with ${next.wire}")
                        payload = ModelCapabilities.withEffort(payload, next)
                    }
                    "prompt_cache" in param && !cacheDowngraded -> {
                        cacheDowngraded = true
                        log.log("responses: prompt cache fields rejected, retrying without them")
                        payload = ModelCapabilities.withoutCacheFields(payload)
                    }
                    else -> throw ApiErrors.toLlmError(f)
                }
            }
        }
    }

    /**
     * POST {base}/audio/transcriptions (multipart). [fileName] MUST carry the extension (".m4a"):
     * the API picks the decoder from it. response_format=json; repeated languages[] and keywords[]
     * (each one line, '<' and '>' stripped, at most model.maxKeywords).
     */
    suspend fun transcription(
        model: TranscriptionModel,
        audio: ByteArray,
        fileName: String,
        keywords: List<String>,
        timeout: Duration = LlmTimeouts.TRANSCRIBE,
    ): ApiResult {
        val conn = connection()
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model.id)
            .addFormDataPart("response_format", "json")
        model.languages.forEach { form.addFormDataPart("languages[]", it) }
        model.prompt?.let { form.addFormDataPart("prompt", it) }
        sanitizeKeywords(keywords, model.maxKeywords).forEach { form.addFormDataPart("keywords[]", it) }
        form.addFormDataPart("file", fileName, audio.toRequestBody(audioMediaType(fileName).toMediaType()))
        return mapFailures { parseJson(execute(conn, "POST", "audio/transcriptions", form.build(), timeout)) }
            .also { resultObserver?.invoke("audio/transcriptions", it) }
    }

    /**
     * POST {base}/images/edits (multipart: model, prompt, image[] = reference PNG, background,
     * output_format, output_compression, quality, size, n=1, all from [model]).
     * Returns the decoded data[0].b64_json bytes. Never retried automatically (not even on 429/5xx):
     * the caller decides, so one drawing is never billed more than twice.
     */
    suspend fun imageEdit(
        model: ImageModel,
        prompt: String,
        reference: ByteArray,
        referenceFileName: String,
        timeout: Duration = LlmTimeouts.IMAGE,
    ): ImageResult {
        val conn = connection()
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model.id)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("image[]", referenceFileName, reference.toRequestBody(imageMediaType(referenceFileName).toMediaType()))
            .addFormDataPart("background", model.background)
            .addFormDataPart("output_format", model.outputFormat)
            .addFormDataPart("output_compression", model.outputCompression.toString())
            .addFormDataPart("quality", model.quality)
            .addFormDataPart("size", model.size)
            .addFormDataPart("n", "1")
            .build()
        return mapFailures {
            // No automatic retries: a drawing is slow (~10 s) and billed, and the caller retries it
            // at most once itself (the app's icon queue, research/openai_api.md §6.3).
            val result = parseJson(execute(conn, "POST", "images/edits", form, timeout, maxRetries = 0))
            val b64 = ((result.json["data"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("b64_json")?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: throw LlmError.BadResponse("image response without data[0].b64_json", requestId = result.requestId)
            val bytes = withContext(Dispatchers.Default) {
                runCatching { Base64.getDecoder().decode(b64) }.getOrNull()
            } ?: throw LlmError.BadResponse("image data is not valid base64", requestId = result.requestId)
            ImageResult(bytes, result.requestId, result.latencyMs)
        }
    }

    /** GET {base}/models/{id} (free; used by "Test connection"). */
    suspend fun model(id: String, timeout: Duration = LlmTimeouts.TEST_CONNECTION): ApiResult {
        val conn = connection()
        return mapFailures { parseJson(execute(conn, "GET", "models/$id", null, timeout, encodeLastSegment = true)) }
    }

    // ---------------------------------------------------------------------------------------------

    private class Connection(val baseUrl: HttpUrl, val token: String, val safetyIdentifier: String?)

    private class RawResponse(val code: Int, val headers: Headers, val body: ByteArray, val latencyMs: Long) {
        val requestId: String? get() = headers["x-request-id"]
    }

    private suspend fun connection(): Connection {
        val cfg = try {
            config.config()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LlmError.NotConfigured("connection settings unavailable (${e.javaClass.simpleName})")
        }
        val base = LlmConfig.normalizeBaseUrl(cfg.baseUrl)?.toHttpUrlOrNull()
            ?: throw LlmError.NotConfigured("base URL is not a valid https URL")
        val raw = try {
            token.token()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LlmError.NotConfigured("token unavailable (${e.javaClass.simpleName})")
        }
        val bearer = raw?.trim()?.removePrefix("Bearer ")?.trim()
        if (bearer.isNullOrEmpty()) throw LlmError.NotConfigured("no API key")
        if (bearer.any { it.code <= 0x20 || it.code >= 0x7f }) throw LlmError.NotConfigured("API key contains invalid characters")
        return Connection(base, bearer, cfg.safetyIdentifier?.takeIf { it.isNotBlank() })
    }

    private suspend fun sendJson(conn: Connection, method: String, path: String, body: JsonObject, timeout: Duration): ApiResult {
        val bytes = withContext(Dispatchers.Default) {
            CoreJson.json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)
        }
        return parseJson(execute(conn, method, path, bytes.toRequestBody(JSON), timeout))
            .also { resultObserver?.invoke(path, it) }
    }

    /** Runs [block], turning an ApiFailure (non-2xx after retries) into its LlmError. */
    private inline fun <T> mapFailures(block: () -> T): T = try {
        block()
    } catch (f: ApiFailure) {
        throw ApiErrors.toLlmError(f)
    }

    private suspend fun parseJson(raw: RawResponse): ApiResult {
        val json = withContext(Dispatchers.Default) {
            try {
                CoreJson.json.parseToJsonElement(raw.body.decodeToString()) as? JsonObject
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: throw LlmError.BadResponse("response is not a JSON object", requestId = raw.requestId)
        return ApiResult(json, raw.requestId, raw.latencyMs)
    }

    /**
     * Sends one request with the automatic retries. Returns a 2xx response; throws [ApiFailure] for any
     * other status (after retries) and LlmError.Network / LlmError.Timeout for transport failures.
     */
    private suspend fun execute(
        conn: Connection,
        method: String,
        path: String,
        body: RequestBody?,
        timeout: Duration,
        encodeLastSegment: Boolean = false,
        maxRetries: Int = retryPolicy.maxRetries,
    ): RawResponse {
        val url = conn.baseUrl.newBuilder().apply {
            if (encodeLastSegment) {
                addPathSegments(path.substringBeforeLast('/'))
                addPathSegment(path.substringAfterLast('/'))
            } else {
                addPathSegments(path)
            }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${conn.token}")
            .header("Accept", "application/json")
            .method(method, body)
            .build()
        val logPath = url.encodedPath
        var attempt = 0
        while (true) {
            val raw = try {
                send(request, timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InterruptedIOException) {
                // OkHttp's call timeout and socket timeouts (SocketTimeoutException is a subclass).
                log.log("$method $logPath -> timeout")
                throw LlmError.Timeout()
            } catch (e: IOException) {
                log.log("$method $logPath -> ${e.javaClass.simpleName}")
                throw LlmError.Network(e)
            }
            log.log("$method $logPath -> ${raw.code} in ${raw.latencyMs} ms (${raw.requestId ?: "no request id"})")
            if (raw.code in 200..299) return raw

            val error = ApiErrorBody.parse(raw.body)
            val retryAfter = ApiErrors.retryAfterSeconds(raw.headers["retry-after-ms"], raw.headers["retry-after"])
            if (ApiErrors.isRetryable(raw.code, error) && attempt < maxRetries) {
                val wait = backoff(attempt, retryAfter)
                if (wait != null) {
                    retryPolicy.sleep(wait)
                    attempt++
                    continue
                }
            }
            throw ApiFailure(raw.code, error, raw.requestId, retryAfter)
        }
    }

    /** Null when the server asks for a longer pause than we are willing to wait. */
    private fun backoff(attempt: Int, retryAfterSeconds: Long?): Duration? {
        if (retryAfterSeconds != null) {
            val wait = retryAfterSeconds.seconds
            return if (wait <= retryPolicy.maxRetryAfter) wait else null
        }
        val jitterMs = retryPolicy.maxJitter.inWholeMilliseconds
        val jitter = if (jitterMs > 0) Random.nextLong(jitterMs + 1).milliseconds else Duration.ZERO
        return retryPolicy.baseDelay * (1 shl attempt) + jitter
    }

    private suspend fun send(request: Request, timeout: Duration): RawResponse {
        // The first call builds the OkHttpClient (platform TLS, system CA store): never on the
        // caller's thread, which is the main thread for the calls a screen waits for.
        val call = withContext(Dispatchers.IO) { http.newCall(request) }
        return enqueue(call, timeout)
    }

    private suspend fun enqueue(call: Call, timeout: Duration): RawResponse = suspendCancellableCoroutine { cont ->
        call.timeout().timeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        cont.invokeOnCancellation { call.cancel() }
        val started = System.nanoTime()
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val bytes = it.body.bytes()
                        val latency = (System.nanoTime() - started) / 1_000_000
                        cont.resume(RawResponse(it.code, it.headers, bytes, latency))
                    }
                } catch (e: IOException) {
                    cont.resumeWithException(e)
                }
            }
        })
    }

    private fun effortOf(body: JsonObject): ReasoningEffort? =
        ((body["reasoning"] as? JsonObject)?.get("effort") as? JsonPrimitive)?.contentOrNull?.let(ReasoningEffort::fromWire)

    internal companion object {
        private val JSON = "application/json".toMediaType()

        /** keywords[]: one line each, '<' and '>' stripped (they make the API answer 400), deduplicated, capped. */
        fun sanitizeKeywords(keywords: List<String>, max: Int): List<String> {
            val seen = HashSet<String>()
            return keywords.asSequence()
                .map { it.replace(Regex("[\\r\\n\\t]+"), " ").replace("<", "").replace(">", "").trim().take(100) }
                .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
                .take(max.coerceAtLeast(0))
                .toList()
        }

        fun audioMediaType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
            "m4a", "mp4" -> "audio/mp4"
            "mp3", "mpeg", "mpga" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "webm" -> "audio/webm"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }

        fun imageMediaType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}

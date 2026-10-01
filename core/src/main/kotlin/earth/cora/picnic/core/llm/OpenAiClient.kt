package earth.cora.picnic.core.llm

import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

/** A successful (HTTP 2xx) API call. */
data class ApiResult(
    val json: JsonObject,
    /** x-request-id response header (log this, never the token). */
    val requestId: String?,
    val latencyMs: Long,
)

/**
 * Thin OpenAI-compatible HTTP client on OkHttp (OkHttp stays an implementation detail of core).
 * All paths are relative to LlmConfig.baseUrl, so api.openai.com and Cora's proxy behave the same.
 *
 * CONTRACT (implementation notes in docs/ARCHITECTURE.md §7):
 * - Reads [config] and [token] before every call. Missing/blank token or non-https base URL ->
 *   LlmError.NotConfigured (no request is sent).
 * - Sends `Authorization: Bearer <token>`; never logs headers, bodies or the token.
 * - Suspends with cancellation: cancelling the coroutine cancels the OkHttp call.
 * - Per-call timeout (OkHttp call timeout) = the [timeout] argument; connect timeout LlmDefaults.CONNECT_TIMEOUT.
 * - Retries (max 2 extra attempts, exponential backoff with jitter, honours Retry-After):
 *   429 rate_limit (not insufficient_quota), 500, 502, 503, 504, and IOExceptions on idempotent GETs.
 * - Maps every failure to LlmError (see LlmError and research/openai_api.md §2.8).
 */
class OpenAiClient(
    private val config: ConfigSource,
    private val token: TokenSource,
) {
    /** POST {base}/responses with a JSON body (stream=false). */
    suspend fun responses(body: JsonObject, timeout: Duration = LlmDefaults.INGEST_TIMEOUT): ApiResult =
        TODO("core: OpenAiClient.responses")

    /**
     * POST {base}/audio/transcriptions (multipart). [fileName] MUST carry the extension (".m4a"):
     * the API picks the decoder from it. response_format=json; repeated languages[] and keywords[].
     */
    suspend fun transcription(
        model: String,
        audio: ByteArray,
        fileName: String,
        languages: List<String>,
        keywords: List<String>,
        prompt: String?,
        timeout: Duration = LlmDefaults.TRANSCRIBE_TIMEOUT,
    ): ApiResult = TODO("core: OpenAiClient.transcription")

    /**
     * POST {base}/images/edits (multipart: model, prompt, image[] = reference PNG, background=transparent,
     * output_format=webp, output_compression=80, quality, size, n=1). Returns the decoded data[0].b64_json bytes.
     */
    suspend fun imageEdit(
        model: String,
        prompt: String,
        reference: ByteArray,
        referenceFileName: String,
        quality: String,
        size: String = LlmDefaults.IMAGE_SIZE,
        timeout: Duration = LlmDefaults.IMAGE_TIMEOUT,
    ): ByteArray = TODO("core: OpenAiClient.imageEdit")

    /** GET {base}/models/{id} (free; used by "Test connection"). */
    suspend fun model(id: String, timeout: Duration = LlmDefaults.TEST_CONNECTION_TIMEOUT): ApiResult =
        TODO("core: OpenAiClient.model")
}

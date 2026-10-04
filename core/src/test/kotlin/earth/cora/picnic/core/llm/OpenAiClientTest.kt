package earth.cora.picnic.core.llm

import earth.cora.picnic.core.MockApi
import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.formFields
import earth.cora.picnic.core.path
import earth.cora.picnic.core.str
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.MockResponse
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OpenAiClientTest {
    private val api = MockApi()

    @AfterTest
    fun close() = api.close()

    private val ok = """{"id":"resp_1","status":"completed","output":[]}"""

    private fun body(model: String = "gpt-6-luna", effort: String = "none"): JsonObject = buildJsonObject {
        put("model", model)
        putJsonObject("reasoning") { put("effort", effort) }
        put("input", kotlinx.serialization.json.JsonArray(emptyList()))
        putJsonObject("prompt_cache_options") { put("mode", "explicit") }
    }

    @Test
    fun sendsBearerTokenToTheBaseUrlPathAndInjectsSafetyIdentifier() = runTest {
        api.safetyIdentifier = "abc123"
        api.enqueue(200, ok, "x-request-id" to "req_42")
        val result = api.client.responses(body())
        assertEquals("req_42", result.requestId)
        val req = api.take()
        assertEquals("POST", req.method)
        assertEquals("/v1/responses", req.url.encodedPath)
        assertEquals("Bearer sk-test-123", req.headers["Authorization"])
        val sent = TestSupport.parse(req.body!!.utf8())
        assertEquals("abc123", sent["safety_identifier"]!!.str())
        assertEquals("false", sent["store"].toString())
        assertTrue(api.logLines.single().contains("/v1/responses -> 200"))
        assertFalse(api.logLines.single().contains("sk-test"), "the token is never logged")
    }

    @Test
    fun bearerPrefixAndWhitespaceArePeeledOffThePastedToken() = runTest {
        api.token = "  Bearer sk-pasted \n"
        api.enqueue(200, ok)
        api.client.responses(body())
        assertEquals("Bearer sk-pasted", api.take().headers["Authorization"])
    }

    @Test
    fun notConfiguredSendsNothing() = runTest {
        api.token = "  "
        assertIs<LlmError.NotConfigured>(assertFailsWith<LlmError> { api.client.responses(body()) })
        api.token = null
        assertFailsWith<LlmError.NotConfigured> { api.client.model("gpt-5.6-terra") }
        api.token = "sk-with space"
        assertFailsWith<LlmError.NotConfigured> { api.client.model("gpt-5.6-terra") }
        assertEquals(0, api.server.requestCount)

        val badUrl = OpenAiClient(ConfigSource { LlmConfig(baseUrl = "http://example.com/v1") }, TokenSource { "sk" })
        assertFailsWith<LlmError.NotConfigured> { badUrl.model("x") }
        val throwingToken = OpenAiClient(ConfigSource { LlmConfig() }, TokenSource { error("keystore broke") })
        assertFailsWith<LlmError.NotConfigured> { throwingToken.model("x") }
    }

    @Test
    fun unauthorizedDropsTheServerText() = runTest {
        api.enqueueFixture(401, "errors/401_invalid_api_key.json", "x-request-id" to "req_401")
        val e = assertFailsWith<LlmError.Unauthorized> { api.client.responses(body()) }
        assertEquals(401, e.status)
        assertEquals("req_401", e.requestId)
        assertFalse(e.message!!.contains("sk-inval"))
        assertEquals(1, api.server.requestCount, "never retried")
    }

    @Test
    fun aMissingPermissionNamesTheScopeButNeverTheServerText() = runTest {
        api.enqueueFixture(401, "errors/401_missing_scopes.json")
        val e = assertFailsWith<LlmError.Unauthorized> { api.client.model("gpt-5.6-terra") }
        assertEquals(listOf("api.model.read"), e.missingScopes)
        assertEquals("This key can’t do everything the app needs. Ask the person who made it to allow List models (Read).", e.friendlyMessage)
        assertTrue(e.message!!.contains("api.model.read"), "the scope id stays in the log line")
        assertFalse(e.friendlyMessage.contains("Reader"), "nothing else from the server's text")

        // Several scopes, and the variant without a "Missing scopes" list.
        api.enqueue(401, """{"error":{"message":"You have insufficient permissions for this operation. Missing scopes: model.request, api.responses.write. Check that you have the correct role.","type":"invalid_request_error","param":null,"code":null}}""")
        assertEquals(
            listOf("model.request", "api.responses.write"),
            assertFailsWith<LlmError.Unauthorized> { api.client.responses(body()) }.missingScopes,
        )
        api.enqueue(401, """{"error":{"message":"You have insufficient permissions for this operation.","type":"invalid_request_error","param":null,"code":null}}""")
        val unnamed = assertFailsWith<LlmError.Unauthorized> { api.client.responses(body()) }
        assertEquals(emptyList(), unnamed.missingScopes)
        assertTrue(unnamed.friendlyMessage.contains("Ask the person who made it to check its permissions"))
        assertEquals(3, api.server.requestCount, "never retried")
    }

    @Test
    fun aWrongKeyIsNotAPermissionProblem() = runTest {
        api.enqueueFixture(401, "errors/401_invalid_api_key.json")
        val e = assertFailsWith<LlmError.Unauthorized> { api.client.model("gpt-5.6-terra") }
        assertNull(e.missingScopes)
        assertTrue(e.friendlyMessage.contains("Settings"))
    }

    @Test
    fun forbiddenIsUnauthorizedExceptAModelTheProjectDoesNotAllow() = runTest {
        api.enqueue(403, """{"error":{"message":"Country, region, or territory not supported","type":"request_forbidden","param":null,"code":"unsupported_country_region_territory"}}""")
        assertEquals(403, assertFailsWith<LlmError.Unauthorized> { api.client.responses(body()) }.status)

        // OpenAI's answer when the key's project has an allowed-models list without this model.
        api.enqueueFixture(403, "errors/403_project_model_not_found.json")
        val e = assertFailsWith<LlmError.BadResponse> { api.client.model("gpt-5.6-terra") }
        assertEquals("model_not_found", e.code)
        assertEquals(403, e.httpStatus)
        assertFalse(e.retryable)
        assertTrue(e.friendlyMessage.contains("can’t use one of the AI models"))
        assertFalse(e.message!!.contains("proj_"), "the project id stays out of the log line")
    }

    @Test
    fun modelNotFoundAndOtherNotFound() = runTest {
        api.enqueueFixture(404, "errors/404_model_not_found.json")
        val e = assertFailsWith<LlmError.BadResponse> { api.client.model("gpt-nope-1") }
        assertEquals("model_not_found", e.code)
        assertEquals(404, e.httpStatus)
        assertEquals("/v1/models/gpt-nope-1", api.take().url.encodedPath)

        api.server.enqueue(MockResponse.Builder().code(404).body("<html>Not Found</html>").build())
        val other = assertFailsWith<LlmError.BadResponse> { api.client.model("gpt-5.6-terra") }
        assertEquals(404, other.httpStatus)
        assertNull(other.code)
        assertFalse(other.retryable)
    }

    @Test
    fun serverErrorsAreRetriedThenReportedAsNetwork() = runTest {
        repeat(3) { api.enqueue(502, """{"error":{"message":"Bad gateway","type":"server_error","param":null,"code":null}}""") }
        assertFailsWith<LlmError.Network> { api.client.responses(body()) }
        assertEquals(3, api.server.requestCount)
        assertEquals(2, api.sleeps.size)
    }

    @Test
    fun quotaIsNotRetriedAndIsNotARateLimit() = runTest {
        api.enqueueFixture(429, "errors/429_insufficient_quota.json")
        assertEquals("insufficient_quota", assertFailsWith<LlmError.QuotaExceeded> { api.client.responses(body()) }.code)
        api.enqueueFixture(429, "errors/429_spend_limit.json")
        assertEquals("project_spend_limit_exceeded", assertFailsWith<LlmError.QuotaExceeded> { api.client.responses(body()) }.code)
        assertEquals(2, api.server.requestCount)
        assertTrue(api.sleeps.isEmpty())
    }

    @Test
    fun rateLimitsAreRetriedTwiceWithBackoffThenSurface() = runTest {
        repeat(3) { api.enqueueFixture(429, "errors/429_rate_limit.json") }
        val e = assertFailsWith<LlmError.RateLimited> { api.client.responses(body()) }
        assertTrue(e.retryable)
        assertEquals(3, api.server.requestCount)
        assertEquals(2, api.sleeps.size)
    }

    @Test
    fun retryAfterIsHonouredAndARetryCanSucceed() = runTest {
        api.enqueueFixture(429, "errors/429_rate_limit.json", "retry-after" to "3")
        api.enqueue(503, """{"error":{"message":"overloaded","type":"service_unavailable_error","code":"server_is_overloaded"}}""", "retry-after-ms" to "1500")
        api.enqueue(200, ok)
        api.client.responses(body())
        assertEquals(listOf(3.seconds, 2.seconds), api.sleeps)
    }

    @Test
    fun aRetryAfterLongerThanTheCapStopsRetrying() = runTest {
        api.enqueueFixture(429, "errors/429_rate_limit.json", "retry-after" to "120")
        val e = assertFailsWith<LlmError.RateLimited> { api.client.responses(body()) }
        assertEquals(120L, e.retryAfterSeconds)
        assertEquals(1, api.server.requestCount)
    }

    @Test
    fun serverErrorsAreRetriedThenBecomeNetworkErrors() = runTest {
        repeat(3) { api.enqueue(502, "bad gateway") }
        val e = assertFailsWith<LlmError.Network> { api.client.responses(body()) }
        assertTrue(e.retryable)
        assertEquals(3, api.server.requestCount)
        repeat(3) { api.enqueue(503, "{}") }
        assertFailsWith<LlmError.RateLimited> { api.client.responses(body()) }
    }

    @Test
    fun otherClientErrorsAreBadResponsesWithoutRetry() = runTest {
        api.enqueueFixture(400, "errors/400_invalid_json_schema.json")
        val e = assertFailsWith<LlmError.BadResponse> { api.client.responses(body()) }
        assertEquals("invalid_json_schema", e.code)
        assertEquals(400, e.httpStatus)
        assertFalse(e.retryable)
        assertEquals(1, api.server.requestCount)
    }

    @Test
    fun effortDowngradeRetriesOnceWithTheNearestSupportedValue() = runTest {
        api.enqueueFixture(400, "errors/400_effort_unsupported.json")
        api.enqueue(200, ok)
        // A model id the capability table doesn't know, so the effort reaches the server unchanged.
        api.client.responses(body(model = "gpt-7-mystery", effort = "none"))
        assertEquals("none", api.takeJson().path("reasoning", "effort")!!.str())
        assertEquals("low", api.takeJson().path("reasoning", "effort")!!.str())

        // Only once per call.
        api.enqueueFixture(400, "errors/400_effort_unsupported.json")
        api.enqueueFixture(400, "errors/400_effort_unsupported.json")
        assertFailsWith<LlmError.BadResponse> { api.client.responses(body(model = "gpt-7-mystery")) }
        assertEquals(4, api.server.requestCount)
    }

    @Test
    fun cacheFieldDowngradeRetriesOnceWithoutTheCacheFields() = runTest {
        api.enqueueFixture(400, "errors/400_cache_options_unsupported.json")
        api.enqueue(200, ok)
        api.client.responses(body(model = "gpt-6-luna"))
        assertTrue("prompt_cache_options" in api.takeJson())
        assertFalse("prompt_cache_options" in api.takeJson())
    }

    @Test
    fun timeoutsAndCancellation() = runBlocking {
        api.server.enqueue(MockResponse.Builder().code(200).body(ok).headersDelay(2, TimeUnit.SECONDS).build())
        assertIs<LlmError.Timeout>(assertFailsWith<LlmError> { api.client.responses(body(), timeout = 300.milliseconds) })

        // Cancelling the coroutine cancels the OkHttp call (no waiting for the slow response).
        api.server.enqueue(MockResponse.Builder().code(200).body(ok).headersDelay(5, TimeUnit.SECONDS).build())
        val started = System.nanoTime()
        val call = async { api.client.responses(body(), timeout = 30.seconds) }
        delay(200)
        call.cancel()
        runCatching { call.await() }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000, "cancelled promptly")
    }

    @Test
    fun networkFailureIsNetwork() = runTest {
        val port = api.server.port
        api.server.close()
        val client = OpenAiClient(ConfigSource { LlmConfig("http://127.0.0.1:$port/v1") }, TokenSource { "sk" })
        assertIs<LlmError.Network>(assertFailsWith<LlmError> { client.model("x") })
    }

    @Test
    fun nonJsonSuccessIsABadResponse() = runTest {
        api.server.enqueue(MockResponse.Builder().code(200).body("<html>captive portal</html>").build())
        val e = assertFailsWith<LlmError.BadResponse> { api.client.responses(body()) }
        assertTrue(e.retryable)
    }

    @Test
    fun transcriptionIsAMultipartFormWithSanitizedKeywords() = runTest {
        api.enqueueFixture(200, "other/transcription.json")
        val keywords = listOf("Oatly", "za'atar", "<b>PC</b> Blue\nMenu", "oatly", "  ") + List(200) { "kw$it" }
        val result = api.client.transcription(ModelDefaults.TRANSCRIBE, byteArrayOf(1, 2, 3), "recording.m4a", keywords)
        assertTrue(result.json["text"]!!.str().startsWith("Okay so"))
        val req = api.take()
        assertEquals("/v1/audio/transcriptions", req.url.encodedPath)
        assertTrue(req.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        val fields = req.formFields()
        assertEquals(listOf("gpt-transcribe"), fields["model"])
        assertEquals(listOf("json"), fields["response_format"])
        assertEquals(listOf("en"), fields["languages[]"])
        assertEquals(listOf(ModelDefaults.TRANSCRIBE.prompt!!), fields["prompt"])
        assertEquals(listOf("filename:recording.m4a"), fields["file"])
        val sentKeywords = fields["keywords[]"]!!
        assertEquals(ModelDefaults.TRANSCRIBE.maxKeywords, sentKeywords.size)
        assertEquals(listOf("Oatly", "za'atar", "bPC/b Blue Menu"), sentKeywords.take(3))
        assertTrue(sentKeywords.none { '<' in it || '>' in it || '\n' in it })
        assertNull(fields["language"], "never language together with languages[]")
    }

    @Test
    fun modelGetEncodesTheId() = runTest {
        api.enqueueFixture(200, "other/model.json")
        val result = api.client.model("gpt-5.6-terra")
        assertEquals(JsonPrimitive("gpt-5.6-terra"), result.json["id"])
        val req = api.take()
        assertEquals("GET", req.method)
        assertEquals("/v1/models/gpt-5.6-terra", req.url.encodedPath)
    }

    @Test
    fun retryAfterParsing() {
        val now = ZonedDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(2L, ApiErrors.retryAfterSeconds("1500", null, now))
        assertEquals(7L, ApiErrors.retryAfterSeconds(null, "7", now))
        assertEquals(30L, ApiErrors.retryAfterSeconds(null, "Thu, 01 Oct 2026 12:00:30 GMT", now))
        assertNull(ApiErrors.retryAfterSeconds(null, "soon", now))
        assertNull(ApiErrors.retryAfterSeconds(null, null, now))
    }
}

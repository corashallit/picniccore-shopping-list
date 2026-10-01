package earth.cora.picnic.core

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.ConfigSource
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.core.llm.LlmLog
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.llm.RetryPolicy
import earth.cora.picnic.core.llm.TokenSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Shared helpers for core unit tests. */
object TestSupport {
    val catalog: IconCatalog by lazy { IconCatalog.load() }

    fun fixture(path: String): String =
        requireNotNull(TestSupport::class.java.getResourceAsStream("/fixtures/$path")) { "missing fixture $path" }
            .bufferedReader().use { it.readText() }

    fun fixtureBytes(path: String): ByteArray =
        requireNotNull(TestSupport::class.java.getResourceAsStream("/fixtures/$path")) { "missing fixture $path" }
            .use { it.readBytes() }

    /** Any test resource by its path ("live/handwritten_list.jpg"). */
    fun resourceBytes(path: String): ByteArray =
        requireNotNull(TestSupport::class.java.getResourceAsStream("/$path")) { "missing resource $path" }.use { it.readBytes() }

    fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    /** A final_answer message output item carrying [text]. */
    fun finalMessage(text: String, phase: String = "final_answer"): JsonObject = buildJsonObject {
        put("type", "message")
        put("role", "assistant")
        put("phase", phase)
        put("status", "completed")
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "output_text")
                        put("text", text)
                        put("annotations", JsonArray(emptyList()))
                    },
                )
            },
        )
    }

    /** A minimal completed /responses envelope whose last output item is a final message with [structuredJson]. */
    fun responseEnvelope(structuredJson: String, model: String = "gpt-6-luna", extraOutput: List<JsonObject> = emptyList()): String =
        buildJsonObject {
            put("id", "resp_test")
            put("object", "response")
            put("status", "completed")
            put("model", model)
            put("output", JsonArray(extraOutput + finalMessage(structuredJson)))
        }.toString()
}

/** MockWebServer wrapper: an OpenAiClient pointed at it with instant retries. */
class MockApi : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val sleeps = mutableListOf<Duration>()
    var token: String? = "sk-test-123"
    var safetyIdentifier: String? = null
    val logLines = mutableListOf<String>()

    val baseUrl: String get() = server.url("/v1").toString().trimEnd('/')

    val client: OpenAiClient by lazy {
        OpenAiClient(
            config = ConfigSource { LlmConfig(baseUrl = baseUrl, safetyIdentifier = safetyIdentifier) },
            token = TokenSource { token },
            retryPolicy = RetryPolicy(baseDelay = Duration.ZERO, maxJitter = Duration.ZERO, sleep = { sleeps += it }),
            log = LlmLog { synchronized(logLines) { logLines += it } },
        )
    }

    fun enqueue(code: Int, body: String, vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json")
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        server.enqueue(builder.build())
    }

    fun enqueueFixture(code: Int, path: String, vararg headers: Pair<String, String>) =
        enqueue(code, TestSupport.fixture(path), *headers)

    fun take(): RecordedRequest = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request was sent" }

    fun takeJson(): JsonObject = TestSupport.parse(take().body!!.utf8())

    override fun close() = server.close()
}

/** Multipart form fields of a recorded request: name -> values (file parts as "filename:<name>"). */
fun RecordedRequest.formFields(): Map<String, List<String>> {
    val contentType = headers["Content-Type"] ?: error("no content type")
    val boundary = contentType.substringAfter("boundary=").trim('"')
    val text = body!!.string(Charsets.ISO_8859_1)
    val out = LinkedHashMap<String, MutableList<String>>()
    text.split("--$boundary").drop(1).dropLast(1).forEach { part ->
        val headerEnd = part.indexOf("\r\n\r\n")
        val head = part.substring(0, headerEnd)
        val value = part.substring(headerEnd + 4).removeSuffix("\r\n")
        val name = Regex("name=\"([^\"]+)\"").find(head)!!.groupValues[1]
        val fileName = Regex("filename=\"([^\"]+)\"").find(head)?.groupValues?.get(1)
        out.getOrPut(name) { mutableListOf() } += if (fileName != null) "filename:$fileName" else value
    }
    return out
}

/** Walks a JSON tree. */
fun JsonElement.walk(visit: (JsonElement) -> Unit) {
    visit(this)
    when (this) {
        is JsonObject -> values.forEach { it.walk(visit) }
        is JsonArray -> forEach { it.walk(visit) }
        else -> Unit
    }
}

fun JsonObject.path(vararg keys: String): JsonElement? {
    var cur: JsonElement? = this
    for (k in keys) cur = (cur as? JsonObject)?.get(k)
    return cur
}

fun JsonElement.str(): String = jsonPrimitive.content

fun JsonElement.strings(): List<String> = jsonArray.map { it.jsonPrimitive.content }

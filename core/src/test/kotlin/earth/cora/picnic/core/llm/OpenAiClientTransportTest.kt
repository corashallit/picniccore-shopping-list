package earth.cora.picnic.core.llm

import earth.cora.picnic.core.MockApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Offline and honest timeouts (docs/DESIGN_STICKER.md §0.4, 5 October; ARCHITECTURE §7.2): with no network the client
 * says [LlmError.Offline] at once and sends nothing; a call that never got a connection is [LlmError.Network] with
 * `beforeConnect` (never "That took too long"), and only a connected call that runs out of time is [LlmError.Timeout].
 */
class OpenAiClientTransportTest {
    private val api = MockApi()

    @AfterTest
    fun close() = api.close()

    private val ok = """{"id":"resp_1","status":"completed","output":[]}"""

    private fun body() = buildJsonObject {
        put("model", "gpt-6-luna")
        put("input", JsonArray(emptyList()))
    }

    /** A client on the mock server whose connectivity hook answers [online]. */
    private fun client(online: () -> Boolean): OpenAiClient = OpenAiClient(
        config = ConfigSource { LlmConfig(baseUrl = api.baseUrl) },
        token = TokenSource { "test-token-123" },
        retryPolicy = RetryPolicy(baseDelay = Duration.ZERO, maxJitter = Duration.ZERO, sleep = {}),
        log = LlmLog { synchronized(api.logLines) { api.logLines += it } },
        isOnline = online,
    )

    @Test
    fun offlineHookShortCircuitsEveryCallWithNoRequest() = runTest {
        val asked = AtomicInteger()
        val client = client { asked.incrementAndGet(); false }
        assertFailsWith<LlmError.Offline> { client.responses(body()) }
        assertFailsWith<LlmError.Offline> { client.model("gpt-5.6-terra") }
        assertFailsWith<LlmError.Offline> { client.transcription(ModelDefaults.TRANSCRIBE, byteArrayOf(1), "a.m4a", emptyList()) }
        assertFailsWith<LlmError.Offline> {
            client.imageEdit(ModelDefaults.IMAGE, "one lemon", listOf(ImagePart(byteArrayOf(1), "sheet.png")))
        }
        assertEquals(4, asked.get(), "asked before every call")
        assertEquals(0, api.server.requestCount, "nothing was sent")
        val e = LlmError.Offline()
        assertTrue(e.retryable)
        assertEquals("You’re offline right now. Try again once you’re back online.", e.friendlyMessage)
        assertTrue(e.unreached && e.transport)
    }

    @Test
    fun aKeyProblemStillComesFirstAndTheHookIsReadPerCall() = runTest {
        var online = false
        val noKey = OpenAiClient(ConfigSource { LlmConfig(baseUrl = api.baseUrl) }, TokenSource { null }, isOnline = { online })
        assertFailsWith<LlmError.NotConfigured> { noKey.model("x") }

        val client = client { online }
        assertFailsWith<LlmError.Offline> { client.responses(body()) }
        online = true
        api.enqueue(200, ok)
        client.responses(body())
        assertEquals(1, api.server.requestCount, "back online: the next call goes out")
    }

    @Test
    fun aConnectTimeoutIsNetworkNeverTimeout() = runTest {
        val client = client { true }
        // Every socket times out while connecting (Wi-Fi with no internet): the call never gets a connection.
        client.httpCustomizer = { it.socketFactory(NeverConnects()) }
        val e = assertFailsWith<LlmError> { client.responses(body(), timeout = 10.seconds) }
        assertTrue(e is LlmError.Network, "was ${e::class.simpleName}")
        assertTrue(e.beforeConnect)
        assertEquals("Couldn’t reach OpenAI. Check your connection?", e.friendlyMessage)
        assertTrue(e.unreached)
        assertEquals(0, api.server.requestCount)
        assertTrue(api.logLines.any { "no connection" in it }, api.logLines.toString())
    }

    @Test
    fun theCallTimeoutRunningOutWhileConnectingIsNetworkToo() = runTest {
        val client = client { true }
        // The connect hangs past the call's own timeout (300 ms): still no connection, so still unreachable.
        client.httpCustomizer = { it.socketFactory(NeverConnects(hangMs = 1_500)) }
        val e = assertFailsWith<LlmError> { client.model("x", timeout = 300.milliseconds) }
        assertTrue(e is LlmError.Network && e.beforeConnect, "was ${e::class.simpleName}")
    }

    @Test
    fun aRefusedConnectionIsNetworkBeforeConnect() = runTest {
        val port = api.server.port
        api.server.close()
        val client = OpenAiClient(ConfigSource { LlmConfig("http://127.0.0.1:$port/v1") }, TokenSource { "test-token" })
        val e = assertFailsWith<LlmError.Network> { client.model("x") }
        assertTrue(e.beforeConnect)
    }

    @Test
    fun aStalledConnectedResponseIsTimeout() = runBlocking {
        api.server.enqueue(MockResponse.Builder().code(200).body(ok).headersDelay(3, TimeUnit.SECONDS).build())
        val e = assertFailsWith<LlmError> { client { true }.responses(body(), timeout = 400.milliseconds) }
        assertTrue(e is LlmError.Timeout, "was ${e::class.simpleName}")
        assertEquals(1, api.server.requestCount, "the request reached the server: it was connected")
        assertFalse(e.unreached)
        assertTrue(e.transport)
    }

    @Test
    fun serverErrorsAfterRetriesAreNetworkThatReachedTheServer() = runTest {
        repeat(3) { api.enqueue(503, """{"error":{"message":"overloaded","type":"server_error"}}""") }
        repeat(3) { api.enqueue(500, """{"error":{"message":"oops","type":"server_error"}}""") }
        val e = assertFailsWith<LlmError> { client { true }.responses(body()) }
        if (e is LlmError.Network) assertFalse(e.beforeConnect, "a 5xx came from the server")
        assertFalse(e.unreached)
    }

    /** Sockets whose connect never succeeds: a timeout at once, or after [hangMs] (the call timeout fires first). */
    private class NeverConnects(private val hangMs: Long = 0) : SocketFactory() {
        override fun createSocket(): Socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                if (hangMs > 0) {
                    try {
                        Thread.sleep(hangMs)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                throw SocketTimeoutException("connect timed out")
            }
        }

        override fun createSocket(host: String?, port: Int): Socket = createSocket()
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = createSocket()
        override fun createSocket(host: InetAddress?, port: Int): Socket = createSocket()
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = createSocket()
    }
}

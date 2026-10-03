package earth.cora.picnic.core.llm

import earth.cora.picnic.core.MockApi
import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.formFields
import kotlinx.coroutines.test.runTest
import mockwebserver3.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Live stickers v4 (docs/DESIGN_STICKER.md §18.3, checklist item 42): imageEdit sends the neighbour sheet and the
 * mask as multipart parts, asks for a transparent background at quality low, and is never retried automatically.
 */
class OpenAiClientImageEditTest {
    private val api = MockApi()

    @AfterTest
    fun close() = api.close()

    private val sheet = ImagePart(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3), "sheet.png")
    private val mask = ImagePart(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 9, 9), "mask.png")

    @Test
    fun sheetAndMaskAreMultipartPartsWithATransparentBackgroundAtQualityLow() = runTest {
        api.enqueueFixture(200, "other/image_edit.json", "x-request-id" to "req_img")
        val result = api.client.imageEdit(ModelDefaults.IMAGE, "Draw ONE new artwork", listOf(sheet), mask)
        assertContentEquals(TestSupport.fixtureBytes("other/tiny.webp"), result.bytes)
        assertEquals("req_img", result.requestId)

        val req = api.take()
        assertEquals("POST", req.method)
        assertEquals("/v1/images/edits", req.url.encodedPath)
        assertEquals("Bearer sk-test-123", req.headers["Authorization"])
        val fields = req.formFields()
        assertEquals(listOf("gpt-image-2.5-flare"), fields["model"])
        assertEquals(listOf("Draw ONE new artwork"), fields["prompt"])
        assertEquals(listOf("filename:sheet.png"), fields["image[]"])
        assertEquals(listOf("filename:mask.png"), fields["mask"])
        assertEquals(listOf("transparent"), fields["background"])
        assertEquals(listOf("low"), fields["quality"])
        assertEquals(listOf("webp"), fields["output_format"])
        assertEquals(listOf("80"), fields["output_compression"])
        assertEquals(listOf("1024x1024"), fields["size"])
        assertEquals(listOf("1"), fields["n"])

        // The parts carry the bytes untouched, typed as PNG.
        val parts = req.fileParts()
        assertEquals(listOf("image[]", "mask"), parts.map { it.name })
        assertEquals(listOf("image/png", "image/png"), parts.map { it.contentType })
        assertContentEquals(sheet.bytes, parts[0].bytes)
        assertContentEquals(mask.bytes, parts[1].bytes)
    }

    @Test
    fun theModelSettingsAreTheShippedOnes() {
        // gpt-image-2 rejects background=transparent; flare returns real alpha (§18.3). Quality low ≈ US$0.015.
        assertEquals("gpt-image-2.5-flare", ModelDefaults.IMAGE.id)
        assertEquals("transparent", ModelDefaults.IMAGE.background)
        assertEquals("low", ModelDefaults.IMAGE.quality)
        assertEquals("1024x1024", ModelDefaults.IMAGE.size)
    }

    @Test
    fun imagesKeepTheirOrderAndTheMaskIsOptional() = runTest {
        api.enqueueFixture(200, "other/image_edit.json")
        api.client.imageEdit(ModelDefaults.IMAGE, "p", listOf(sheet, ImagePart(byteArrayOf(7), "second.webp")))
        val req = api.take()
        val fields = req.formFields()
        assertEquals(listOf("filename:sheet.png", "filename:second.webp"), fields["image[]"])
        assertNull(fields["mask"])
        assertEquals(listOf("image/png", "image/webp"), req.fileParts().map { it.contentType })
    }

    @Test
    fun anImageIsRequiredAndNothingIsSentWithoutOne() = runTest {
        assertFailsWith<IllegalArgumentException> { api.client.imageEdit(ModelDefaults.IMAGE, "p", emptyList(), mask) }
        assertEquals(0, api.server.requestCount)
    }

    @Test
    fun imageEditsAreNeverRetriedAutomatically() = runTest {
        api.enqueueFixture(429, "errors/429_rate_limit.json")
        assertFailsWith<LlmError.RateLimited> { api.client.imageEdit(ModelDefaults.IMAGE, "p", listOf(sheet), mask) }
        api.enqueue(503, """{"error":{"message":"overloaded","type":"server_error","param":null,"code":null}}""")
        assertFailsWith<LlmError.RateLimited> { api.client.imageEdit(ModelDefaults.IMAGE, "p", listOf(sheet), mask) }
        api.enqueue(502, "")
        assertFailsWith<LlmError.Network> { api.client.imageEdit(ModelDefaults.IMAGE, "p", listOf(sheet), mask) }
        assertEquals(3, api.server.requestCount)
        assertTrue(api.sleeps.isEmpty())
    }

    @Test
    fun imageModerationIsARefusalAndOtherImageErrorsAreBadResponses() = runTest {
        api.enqueueFixture(400, "errors/400_moderation_blocked.json")
        assertFailsWith<LlmError.Refusal> { api.client.imageEdit(ModelDefaults.IMAGE, "p", listOf(sheet), mask) }
        api.enqueueFixture(400, "errors/400_transparent_unsupported.json")
        assertEquals("invalid_value", assertFailsWith<LlmError.BadResponse> { api.client.imageEdit(ModelDefaults.IMAGE, "p", listOf(sheet), mask) }.code)
        api.enqueue(200, """{"data":[]}""")
        assertFailsWith<LlmError.BadResponse> { api.client.imageEdit(ModelDefaults.IMAGE, "p", listOf(sheet), mask) }
        api.enqueue(200, """{"data":[{"b64_json":"%%% not base64"}]}""")
        assertFailsWith<LlmError.BadResponse> { api.client.imageEdit(ModelDefaults.IMAGE, "p", listOf(sheet), mask) }
        assertEquals(4, api.server.requestCount)
    }

    @Test
    fun noKeyIsNotConfiguredAndSendsNothing() = runTest {
        api.token = null
        assertFailsWith<LlmError.NotConfigured> { api.client.imageEdit(ModelDefaults.IMAGE, "p", listOf(sheet), mask) }
        assertEquals(0, api.server.requestCount)
    }

    private class FilePart(val name: String, val contentType: String?, val bytes: ByteArray)

    /** The file parts of a multipart request, in order, with their raw bytes (ISO-8859-1 maps bytes 1:1). */
    private fun RecordedRequest.fileParts(): List<FilePart> {
        val boundary = headers["Content-Type"]!!.substringAfter("boundary=").trim('"')
        val text = body!!.string(Charsets.ISO_8859_1)
        return text.split("--$boundary").drop(1).dropLast(1).mapNotNull { part ->
            val headerEnd = part.indexOf("\r\n\r\n")
            val head = part.substring(0, headerEnd)
            if ("filename=" !in head) return@mapNotNull null
            val name = Regex("name=\"([^\"]+)\"").find(head)!!.groupValues[1]
            val type = Regex("Content-Type: ([^\r\n;]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
            FilePart(name, type, part.substring(headerEnd + 4).removeSuffix("\r\n").toByteArray(Charsets.ISO_8859_1))
        }
    }
}

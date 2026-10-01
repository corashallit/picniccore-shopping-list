package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.llm.ApiResult
import earth.cora.picnic.core.llm.LlmError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResponsesOutputTest {
    private fun result(fixture: String) = ApiResult(TestSupport.parse(TestSupport.fixture("responses/$fixture")), "req_x", 10)

    @Test
    fun realTerraAnswerDecodes() {
        val wire = ResponsesOutput.decode(result("ingest_terra_pancakes.json"), WireIngest.serializer())
        assertEquals(listOf("All-purpose flour", "Granulated sugar", "Baking powder", "Salt", "Milk", "Eggs"), wire.items.map { it.name })
        assertEquals("aisle-2-baking-spices", wire.items.first().aisleId)
        assertEquals("exact", wire.items.first().iconFit)
        assertTrue(wire.message.contains("butter"))
    }

    @Test
    fun duplicateFinalMessagesUseTheLastOneAndIgnoreCommentary() {
        val texts = ResponsesOutput.finalTexts(result("duplicate_final_messages.json"))
        assertEquals(2, texts.size, "two final_answer messages, commentary skipped")
        val wire = ResponsesOutput.decode(result("duplicate_final_messages.json"), WireIngest.serializer())
        assertTrue(wire.items.isNotEmpty())
    }

    @Test
    fun anUnparsableLastMessageFallsBackToAnEarlierFinalOne() {
        val good = """{"items":[{"name":"Milk","quantity":null,"note":null,"aisle_id":"dairy-eggs","icon":"milk_carton","icon_fit":"exact"}],"message":"ok"}"""
        val truncatedOnly = TestSupport.parse(TestSupport.responseEnvelope("{\"items\": [trunc"))
        val withEarlier = TestSupport.parse(
            TestSupport.responseEnvelope("{\"items\": [trunc", extraOutput = listOf(TestSupport.finalMessage(good))),
        )
        assertEquals("unparsable", assertFailsWith<LlmError.BadResponse> { ResponsesOutput.decode(ApiResult(truncatedOnly, null, 0), WireIngest.serializer()) }.code)
        assertEquals("Milk", ResponsesOutput.decode(ApiResult(withEarlier, null, 0), WireIngest.serializer()).items.single().name)
    }

    @Test
    fun incompleteIsAFailureWithOrWithoutAMessage() {
        val noMessage = assertFailsWith<LlmError.BadResponse> { ResponsesOutput.finalTexts(result("incomplete_no_message.json")) }
        assertEquals("incomplete", noMessage.code)
        assertTrue(noMessage.retryable)
        assertEquals("req_x", noMessage.requestId)
        assertFailsWith<LlmError.BadResponse> { ResponsesOutput.finalTexts(result("incomplete_with_message.json")) }
    }

    @Test
    fun noFinalMessageIsAFailure() {
        assertEquals("no_message", assertFailsWith<LlmError.BadResponse> { ResponsesOutput.finalTexts(result("commentary_only.json")) }.code)
    }

    @Test
    fun refusalPart() {
        val e = assertFailsWith<LlmError.Refusal> { ResponsesOutput.finalTexts(result("refusal.json")) }
        assertTrue(e.refusal.contains("can't help"))
        assertEquals("The assistant couldn't help with that one.", e.friendlyMessage)
    }

    @Test
    fun failedStatus() {
        val json = TestSupport.parse("""{"status":"failed","error":{"code":"server_error","message":"boom"},"output":[]}""")
        assertEquals("server_error", assertFailsWith<LlmError.BadResponse> { ResponsesOutput.finalTexts(ApiResult(json, null, 0)) }.code)
    }

    @Test
    fun sourcesFromSearchOpenPageAndCitations() {
        val kuku = ResponsesOutput.sources(result("ingest_terra_kuku_search.json").json)
        assertTrue(kuku.isEmpty(), "this search was recorded without include=sources, so it has no URLs")
        assertEquals(1, ResponsesOutput.webSearches(result("ingest_terra_kuku_search.json").json))
        assertEquals(0, ResponsesOutput.webSearches(result("ingest_terra_url_open_page.json").json), "open_page is not billed as a search")

        val openPage = ResponsesOutput.sources(result("ingest_terra_url_open_page.json").json)
        assertEquals(listOf("https://www.ricardocuisine.com/en/recipes/4530-sugar-pie-2"), openPage.map { it.url })

        val cited = ResponsesOutput.sources(result("ingest_astra_url_citation.json").json)
        assertEquals("https://www.ricardocuisine.com/en/recipes/4530-sugar-pie-2", cited.single().url, "open_page + citation dedupe")
        assertEquals("Sugar Pie (2) | RICARDO", cited.single().title)

        val withSources = TestSupport.parse(
            """{"output":[{"type":"web_search_call","action":{"type":"search","query":"q","sources":[
               {"type":"url","url":"https://a.example/x"},{"type":"api","name":"oai-calculator"},{"type":"url","url":"https://a.example/x"}]}}]}""",
        )
        assertEquals(listOf("https://a.example/x"), ResponsesOutput.sources(withSources).map { it.url })
        assertEquals(1, ResponsesOutput.webSearches(withSources))
    }

    @Test
    fun messageMarkdownLinksAreReducedToText() {
        assertEquals(
            "Added ingredients for Ricardo’s sugar pie; skipped butter already on your list. (ricardocuisine.com)",
            ResponsesOutput.cleanMessage("Added ingredients for Ricardo’s sugar pie; skipped butter already on your list. ([ricardocuisine.com](https://www.ricardocuisine.com/en/recipes/4530-sugar-pie-2))"),
        )
        assertEquals("See this.", ResponsesOutput.cleanMessage("See   this (https://x.example/a)."))
    }
}

package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.MockApi
import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.formFields
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.path
import earth.cora.picnic.core.str
import earth.cora.picnic.core.strings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenAiGroceryAssistantTest {
    private val api = MockApi()
    private val catalog = TestSupport.catalog
    private val assistant by lazy { OpenAiGroceryAssistant(api.client, catalog) }
    private val aisles = Aisles.parse(Aisles.DEFAULT_TEXT)

    /** The benchmark context (research/openai/examples/context_example.txt). */
    private val context = IngestContext(
        aisles = aisles,
        corrections = listOf(
            Correction("nutritional yeast", "bulk-natural-foods", 4),
            Correction("oat milk", "dairy-eggs", 3),
            Correction("maple syrup", "aisle-1-breakfast-coffee-tea", 2),
            Correction("tortilla", "aisle-3-pasta-rice-international", 1),
        ),
        existingNames = listOf("Butter", "Bananas", "Paper towels"),
    )

    @AfterTest
    fun close() = api.close()

    private fun items(vararg rows: String, message: String = "Added things.") =
        TestSupport.responseEnvelope("""{"items":[${rows.joinToString(",")}],"message":${kotlinx.serialization.json.JsonPrimitive(message)}}""", model = "gpt-5.6-terra")

    private fun row(name: String, aisle: String = "produce", icon: String = "lemon", fit: String = "exact", quantity: String? = null, note: String? = null) =
        buildJsonObject {
            put("name", name)
            put("quantity", quantity)
            put("note", note)
            put("aisle_id", aisle)
            put("icon", icon)
            put("icon_fit", fit)
        }.toString()

    @Test
    fun textIngestRequestShape() = runTest {
        api.enqueueFixture(200, "responses/ingest_terra_pancakes.json")
        assistant.ingest(IngestInput.Text("ingredients for pancakes from scratch"), context)
        val body = api.takeJson()
        assertEquals("gpt-5.6-terra", body["model"]!!.str())
        assertEquals("false", body["store"].toString())
        assertEquals("12000", body["max_output_tokens"].toString())
        assertEquals("low", body.path("reasoning", "effort")!!.str())
        assertEquals("grocery_items", body.path("text", "format", "name")!!.str())
        assertEquals("true", body.path("text", "format", "strict").toString())
        assertFalse("temperature" in body)

        val input = body["input"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("developer", "developer", "user"), input.map { it["role"]!!.str() })
        val stable = input[0]["content"]!!.jsonArray.single().jsonObject
        assertTrue(stable["text"]!!.str().startsWith("You are the list helper"))
        assertEquals("explicit", stable.path("prompt_cache_breakpoint", "mode")!!.str())
        assertEquals("explicit", body.path("prompt_cache_options", "mode")!!.str())
        val ctx = input[1]["content"]!!.jsonArray.single().jsonObject["text"]!!.str()
        assertTrue(ctx.startsWith("Store aisles in walking order (id: name):\n- produce: Produce\n"))
        assertTrue("- nutritional yeast -> bulk-natural-foods" in ctx)
        assertTrue("Already on the list: Butter, Bananas, Paper towels\n\nIn the pantry (she keeps these at home): nothing yet\n\nApp language: English." in ctx)
        assertEquals("ingredients for pancakes from scratch", input[2]["content"]!!.jsonArray.single().jsonObject["text"]!!.str())

        val tool = body["tools"]!!.jsonArray.single().jsonObject
        assertEquals("web_search", tool["type"]!!.str())
        assertEquals("low", tool["search_context_size"]!!.str())
        assertEquals("CA", tool.path("user_location", "country")!!.str())
        assertEquals("Ontario", tool.path("user_location", "region")!!.str())
        assertEquals("America/Toronto", tool.path("user_location", "timezone")!!.str())
        assertEquals("auto", body["tool_choice"]!!.str())
        assertEquals(listOf("web_search_call.action.sources"), body["include"]!!.strings())
    }

    @Test
    fun contextBlockMatchesTheBenchmarkedFormatExactly() {
        // research/openai/examples/context_example.txt, the block every benchmark call used.
        val benchmark = IngestContext(
            aisles = aisles,
            corrections = listOf(
                Correction("tortillas", "aisle-3-pasta-rice-international", 4),
                Correction("maple syrup", "aisle-1-breakfast-coffee-tea", 3),
                Correction("oat milk", "dairy-eggs", 2),
                Correction("nutritional yeast", "bulk-natural-foods", 1),
            ),
            existingNames = listOf("Butter", "Bananas", "Paper towels"),
        )
        assertEquals(
            TestSupport.fixture("context_example.txt").trim(),
            Prompts.contextBlock(benchmark.aisles, benchmark.corrections, benchmark.existingNames, pantry = benchmark.pantry),
        )
        assertTrue(Prompts.contextBlock(emptyList(), emptyList(), emptyList()).endsWith("- none\n\nAlready on the list: nothing yet"))
        assertFalse("Already on the list" in Prompts.contextBlock(aisles, emptyList(), null))
        assertTrue(Prompts.contextBlock(aisles, emptyList(), emptyList(), "English").contains("nothing yet\n\nApp language: English. Write item names"))
    }

    @Test
    fun realAnswerIsPostProcessed() = runTest {
        api.enqueueFixture(200, "responses/ingest_terra_pancakes.json", "x-request-id" to "req_p")
        val result = assistant.ingest(IngestInput.Text("ingredients for pancakes from scratch"), context)
        assertEquals(6, result.items.size)
        val flour = result.items.first()
        assertEquals("All-purpose flour", flour.name)
        assertEquals("aisle-2-baking-spices", flour.aisleId)
        assertEquals("flour", flour.icon)
        assertEquals(IconFit.EXACT, flour.iconFit)
        assertEquals("gpt-5.6-terra", result.meta.model)
        assertEquals("req_p", result.meta.requestId)
        assertEquals(0, result.meta.webSearches)
        assertTrue(result.message.contains("butter"))
    }

    @Test
    fun photoIngestSendsAnImageAndNoTools() = runTest {
        api.enqueue(200, items(row("Dill", icon = "parsley", fit = "approximate")))
        val jpeg = byteArrayOf(-1, -40, -1, -32, 1, 2, 3)
        val result = assistant.ingest(IngestInput.Image(jpeg), context)
        assertEquals(IconFit.APPROXIMATE, result.items.single().iconFit)
        val body = api.takeJson()
        assertNull(body["tools"])
        assertNull(body["include"])
        val user = body["input"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(Prompts.PHOTO_INSTRUCTION, user[0]["text"]!!.str())
        assertEquals("input_image", user[1]["type"]!!.str())
        assertEquals("high", user[1]["detail"]!!.str())
        assertEquals("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg), user[1]["image_url"]!!.str())
    }

    @Test
    fun transcriptIngestIsPrefixedAndKeepsWebSearch() = runTest {
        api.enqueue(200, items(row("Lemon", quantity = "2")))
        assistant.ingest(IngestInput.Transcript("um two lemons"), context)
        val body = api.takeJson()
        assertEquals("Voice note transcript: um two lemons", body["input"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.single().jsonObject["text"]!!.str())
        assertTrue("tools" in body)
    }

    @Test
    fun blankInputSendsNothing() = runTest {
        val result = assistant.ingest(IngestInput.Text("   "), context)
        assertTrue(result.items.isEmpty())
        assertEquals(0, api.server.requestCount)
    }

    @Test
    fun correctionsOverrideTheModelAndUnknownAislesBecomeUnsorted() = runTest {
        api.enqueue(
            200,
            items(
                row("Oat milk", aisle = "aisle-1-breakfast-coffee-tea", icon = "plant_milk"),
                row("Nutritional yeast", aisle = "unsorted", icon = "spice_jar", fit = "approximate"),
                row("Hummus", aisle = "deli-cheese", icon = "hummus"),
                row("Glitter", aisle = "unsorted", icon = "paper_bag", fit = "approximate"),
                row("Mystery", aisle = "aisle-99", icon = "not_a_key", fit = "exact"),
            ),
        )
        val result = assistant.ingest(IngestInput.Text("oat milk, nooch, hummus, glitter, mystery"), context)
        val byName = result.items.associateBy { it.name }
        assertEquals("dairy-eggs", byName.getValue("Oat milk").aisleId)
        assertTrue(byName.getValue("Oat milk").aisleFromCorrection)
        assertEquals("bulk-natural-foods", byName.getValue("Nutritional yeast").aisleId)
        assertEquals("deli-cheese", byName.getValue("Hummus").aisleId)
        assertFalse(byName.getValue("Hummus").aisleFromCorrection)
        assertNull(byName.getValue("Glitter").aisleId, "\"unsorted\" -> null")
        assertNull(byName.getValue("Mystery").aisleId, "unknown aisle -> null")
        assertEquals(IconCatalog.DEFAULT_ICON, byName.getValue("Mystery").icon)
        assertEquals(IconFit.APPROXIMATE, byName.getValue("Mystery").iconFit, "invalid icon -> DEFAULT + APPROXIMATE")
    }

    @Test
    fun aCuratedCatalogAliasUpgradesAnApproximateFit() = runTest {
        api.enqueue(
            200,
            items(
                row("Cilantro", icon = "parsley", fit = "approximate"),
                row("Birthday candles", aisle = "aisle-6-household-cleaning", icon = "candle", fit = "approximate"),
                row("Dill", icon = "parsley", fit = "approximate"),
                row("Lime", icon = "lemon", fit = "exact"),
            ),
        )
        val byName = assistant.ingest(IngestInput.Text("x"), context).items.associateBy { it.name }
        assertEquals("parsley" to IconFit.EXACT, byName.getValue("Cilantro").let { it.icon to it.iconFit })
        assertEquals("candle" to IconFit.EXACT, byName.getValue("Birthday candles").let { it.icon to it.iconFit })
        assertEquals("parsley" to IconFit.APPROXIMATE, byName.getValue("Dill").let { it.icon to it.iconFit }, "no alias: stays a drawing candidate")
        assertEquals("lemon" to IconFit.EXACT, byName.getValue("Lime").let { it.icon to it.iconFit }, "an exact model answer is kept as is")
    }

    @Test
    fun headNounsAndCategoryStandInsAreNotExact() = runTest {
        api.enqueue(
            200,
            items(
                // The local matcher would say "yeast" by head noun; the model judged it a different product.
                row("Nutritional yeast", icon = "spice_jar", fit = "approximate"),
                // A category icon is only a stand-in, even when the model calls it exact...
                row("Gochujang", icon = "condiments_generic", fit = "exact"),
                // ...unless the catalog lists the name for it (bakery_generic is a loaf of bread).
                row("Bread", aisle = "bakery", icon = "bakery_generic", fit = "exact"),
            ),
        )
        val byName = assistant.ingest(IngestInput.Text("x"), context).items.associateBy { it.name }
        assertEquals("spice_jar" to IconFit.APPROXIMATE, byName.getValue("Nutritional yeast").let { it.icon to it.iconFit })
        assertEquals("condiments_generic" to IconFit.APPROXIMATE, byName.getValue("Gochujang").let { it.icon to it.iconFit })
        assertEquals("bakery_generic" to IconFit.EXACT, byName.getValue("Bread").let { it.icon to it.iconFit })
    }

    @Test
    fun sourcesAreCappedWithCitationsFirst() = runTest {
        val search = buildJsonObject {
            put("type", "web_search_call")
            put(
                "action",
                buildJsonObject {
                    put("type", "search")
                    put("sources", buildJsonArray { repeat(12) { i -> add(buildJsonObject { put("type", "url"); put("url", "https://s$i.example/") }) } })
                },
            )
        }
        val message = TestSupport.finalMessage("""{"items":[],"message":"ok"}""").let { msg ->
            val text = msg["content"]!!.jsonArray.single().jsonObject
            val cited = buildJsonObject {
                text.forEach { (k, v) -> if (k != "annotations") put(k, v) }
                put("annotations", buildJsonArray { add(buildJsonObject { put("type", "url_citation"); put("url", "https://cited.example/"); put("title", "Cited") }) })
            }
            buildJsonObject {
                msg.forEach { (k, v) -> if (k != "content") put(k, v) }
                put("content", buildJsonArray { add(cited) })
            }
        }
        api.enqueue(200, buildJsonObject { put("status", "completed"); put("output", buildJsonArray { add(search); add(message) }) }.toString())
        val sources = assistant.ingest(IngestInput.Text("x"), context).sources
        assertEquals(OpenAiGroceryAssistant.MAX_SOURCES, sources.size)
        assertEquals(WebSource("https://cited.example/", "Cited"), sources.first())
    }

    @Test
    fun iconOverridesWinWithExactFit() = runTest {
        api.enqueue(200, items(row("Gochujang", aisle = "aisle-3-pasta-rice-international", icon = "hot_sauce", fit = "approximate")))
        val ctx = context.copy(iconOverrides = mapOf("gochujang" to "condiments_generic", "kimchi" to "retired"))
        val item = assistant.ingest(IngestInput.Text("gochujang"), ctx).items.single()
        assertEquals("condiments_generic", item.icon)
        assertEquals(IconFit.EXACT, item.iconFit)
    }

    @Test
    fun duplicatesAgainstTheListAndWithinTheAnswerAreSkipped() = runTest {
        api.enqueue(
            200,
            items(
                row("Butter", aisle = "dairy-eggs", icon = "butter"),
                row("Banana", icon = "banana"),
                row("Eggs", aisle = "dairy-eggs", icon = "eggs"),
                row("eggs", aisle = "dairy-eggs", icon = "eggs", quantity = "12"),
                row("  ", icon = "eggs"),
                row("Lemon", quantity = "  ", note = "null"),
                message = "Added stuff ([example.com](https://example.com/x)).",
            ),
        )
        val result = assistant.ingest(IngestInput.Text("x"), context)
        assertEquals(listOf("Butter", "Banana", "Eggs", "Lemon"), result.items.map { it.name })
        assertEquals(
            listOf(SkipReason.ALREADY_ON_LIST, SkipReason.ALREADY_ON_LIST, SkipReason.NONE, SkipReason.NONE),
            result.items.map { it.skipReason },
            "names already on the list stay as skipped rows (\"already on your list\")",
        )
        val eggs = result.items[2]
        assertEquals("12", eggs.quantity, "the duplicate's quantity fills the gap")
        assertNull(result.items[3].quantity)
        assertNull(result.items[3].note)
        assertEquals(listOf("Butter", "Banana", "eggs"), result.skippedDuplicates)
        assertEquals("Added stuff (example.com).", result.message)
    }

    @Test
    fun ingestErrorsAreTyped() = runTest {
        api.enqueueFixture(200, "responses/refusal.json")
        assertFailsWith<LlmError.Refusal> { assistant.ingest(IngestInput.Text("x"), context) }
        api.enqueueFixture(200, "responses/incomplete_no_message.json")
        assertFailsWith<LlmError.BadResponse> { assistant.ingest(IngestInput.Text("x"), context) }
        api.enqueueFixture(401, "errors/401_invalid_api_key.json")
        assertFailsWith<LlmError.Unauthorized> { assistant.ingest(IngestInput.Text("x"), context) }
        api.enqueueFixture(429, "errors/429_insufficient_quota.json")
        assertFailsWith<LlmError.QuotaExceeded> { assistant.ingest(IngestInput.Text("x"), context) }
    }

    @Test
    fun sourcesAreReturned() = runTest {
        api.enqueueFixture(200, "responses/ingest_astra_url_citation.json")
        val result = assistant.ingest(IngestInput.Text("https://www.ricardocuisine.com/en/recipes/4530-sugar-pie-2"), context)
        assertEquals("https://www.ricardocuisine.com/en/recipes/4530-sugar-pie-2", result.sources.single().url)
        assertFalse(result.message.contains("https://"))
    }

    @Test
    fun recategorizeResolvesCorrectionsLocallyAndBatches() = runTest {
        val items = List(45) { NamedItem("id$it", "Thing $it") } + NamedItem("oat", "Oat milks")
        // Batch 1: 40 items; the answer skips i2 and uses an unknown aisle for i3.
        val batch1 = buildJsonArray {
            for (i in 1..40) {
                if (i == 2) continue
                add(buildJsonObject { put("id", "i$i"); put("aisle_id", if (i == 3) "nope" else "frozen") })
            }
        }
        api.enqueue(200, TestSupport.responseEnvelope(buildJsonObject { put("assignments", batch1) }.toString()))
        api.enqueue(200, TestSupport.responseEnvelope("""{"assignments":[{"id":"i1","aisle_id":"unsorted"},{"id":"i2","aisle_id":"produce"}]}"""))
        val result = assistant.recategorize(items, aisles, context.corrections)

        assertEquals(46, result.size)
        assertEquals("dairy-eggs", result["oat"], "correction applied locally")
        assertEquals("frozen", result["id0"])
        assertNull(result["id1"], "skipped by the model -> null")
        assertTrue("id1" in result)
        assertNull(result["id2"], "unknown aisle -> null")
        assertNull(result["id40"], "\"unsorted\" -> null")
        assertEquals("produce", result["id41"])
        assertNull(result["id42"])

        val first = api.takeJson()
        assertEquals("gpt-6-luna", first["model"]!!.str())
        assertEquals("none", first.path("reasoning", "effort")!!.str())
        assertNull(first["tools"])
        assertEquals("aisle_assignments", first.path("text", "format", "name")!!.str())
        val userText = first["input"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.single().jsonObject["text"]!!.str()
        assertTrue(userText.startsWith("Items:\ni1: Thing 0\ni2: Thing 1"))
        assertFalse("Oat milks" in userText, "items with a correction are not sent")
        assertEquals(40, first.path("text", "format", "schema", "properties", "assignments", "items", "properties", "id", "enum")!!.jsonArray.size)
        val second = api.takeJson()
        assertEquals(5, second.path("text", "format", "schema", "properties", "assignments", "items", "properties", "id", "enum")!!.jsonArray.size)
    }

    @Test
    fun recategorizeWithoutAislesOrItemsSendsNothing() = runTest {
        assertTrue(assistant.recategorize(emptyList(), aisles, emptyList()).isEmpty())
        assertEquals(mapOf<String, String?>("a" to null), assistant.recategorize(listOf(NamedItem("a", "Milk")), emptyList(), emptyList()))
        assertEquals(0, api.server.requestCount)
    }

    @Test
    fun assignBaskets() = runTest {
        api.enqueue(
            200,
            TestSupport.responseEnvelope(
                """{"baskets":[{"aisle_id":"produce","basket":"fruit_crate"},{"aisle_id":"bakery","basket":"bread_basket"},
                   {"aisle_id":"ghost","basket":"cooler"},{"aisle_id":"frozen","basket":"not_a_basket"},{"aisle_id":"produce","basket":"cooler"}]}""",
            ),
        )
        val result = assistant.assignBaskets(aisles)
        assertEquals(mapOf("produce" to BasketKind.FRUIT_CRATE, "bakery" to BasketKind.BREAD_BASKET), result)
        val body = api.takeJson()
        assertEquals("aisle_baskets", body.path("text", "format", "name")!!.str())
        assertEquals(aisles.map { it.id }, body.path("text", "format", "schema", "properties", "baskets", "items", "properties", "aisle_id", "enum")!!.strings())
        assertTrue(api.server.requestCount == 1)
        assertTrue(assistant.assignBaskets(emptyList()).isEmpty())
        assertEquals(1, api.server.requestCount)
    }

    @Test
    fun assignBasketsOrGuessNeverThrows() = runTest {
        api.enqueue(200, TestSupport.responseEnvelope("""{"baskets":[{"aisle_id":"produce","basket":"berry_punnet"}]}"""))
        val partial = assistant.assignBasketsOrGuess(aisles, catalog.baskets::guess)
        assertEquals(BasketKind.BERRY_PUNNET, partial["produce"])
        assertEquals(catalog.baskets.guess("Bakery"), partial["bakery"])
        assertEquals(aisles.size, partial.size)
        api.token = null
        val offline = assistant.assignBasketsOrGuess(aisles, catalog.baskets::guess)
        assertEquals(BasketKind.WICKER_PICNIC, offline["produce"])
    }

    @Test
    fun transcribe() = runTest {
        api.enqueueFixture(200, "other/transcription.json")
        val transcript = assistant.transcribe(AudioClip(byteArrayOf(1, 2)), listOf("Oatly", "PC Blue Menu"))
        assertTrue(transcript.text.startsWith("Okay so"))
        val fields = api.take().formFields()
        assertEquals(listOf("Oatly", "PC Blue Menu"), fields["keywords[]"])
        assertEquals(listOf("filename:recording.m4a"), fields["file"])

        api.enqueueFixture(200, "other/transcription_silence.json")
        assertTrue(assistant.transcribe(AudioClip(byteArrayOf(1))).isEmpty)

        assertEquals("unsupported_audio", assertFailsWith<LlmError.BadResponse> { assistant.transcribe(AudioClip(byteArrayOf(1), "rec.aac")) }.code)
        assertFailsWith<LlmError.BadResponse> { assistant.transcribe(AudioClip(byteArrayOf(1), "recording")) }
        assertEquals(2, api.server.requestCount)
    }

    @Test
    fun drawIcon() = runTest {
        api.enqueueFixture(200, "other/image_edit.json", "x-request-id" to "req_draw")
        val icon = assistant.drawIcon(IconDrawRequest("Gochujang", hint = "red tub", styleReference = byteArrayOf(1, 2, 3)))
        assertContentEquals(TestSupport.fixtureBytes("other/tiny.webp"), icon.bytes)
        assertEquals("image/webp", icon.mimeType)
        assertEquals("req_draw", icon.meta.requestId)
        val fields = api.take().formFields()
        assertEquals(listOf("gpt-image-2.5-flare"), fields["model"])
        assertTrue(fields["prompt"]!!.single().startsWith("Draw ONE new sticker icon of Gochujang"))
        assertTrue(fields["prompt"]!!.single().endsWith("Details about the item: red tub."))
        assertEquals(listOf("filename:_style_reference.png"), fields["image[]"])
    }

    @Test
    fun testConnection() = runTest {
        api.enqueueFixture(200, "other/model.json", "x-request-id" to "req_m")
        val check = assistant.testConnection()
        assertEquals("gpt-5.6-terra", check.model)
        assertEquals("req_m", check.meta.requestId)
        assertEquals("/v1/models/gpt-5.6-terra", api.take().url.encodedPath)
        api.enqueueFixture(404, "errors/404_model_not_found.json")
        val e = assertFailsWith<LlmError.BadResponse> { assistant.testConnection() }
        assertTrue(e.friendlyMessage.contains("can't use one of the AI models"))
    }
}

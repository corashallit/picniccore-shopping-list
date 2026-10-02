package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.MockApi
import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ResolvedIcon
import earth.cora.picnic.core.path
import earth.cora.picnic.core.str
import earth.cora.picnic.core.strings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IconDeciderTest {
    private val api = MockApi()
    private val catalog = TestSupport.catalog
    private val decider by lazy { ResponsesIconDecider(api.client, catalog) }

    @AfterTest
    fun close() = api.close()

    @Test
    fun requestShapeMatchesTheVerifiedProbe() = runTest {
        api.enqueue(
            200,
            TestSupport.responseEnvelope(
                """{"decisions":[{"id":"i1","icon":"none","category":"condiments"},{"id":"i2","icon":"plant_milk","category":"plant_based"}]}""",
            ),
        )
        val result = decider.decide(listOf(NamedItem("gochujang", "Gochujang"), NamedItem("oat milk", "Oat milk")))
        assertEquals(IconDecision(null, "condiments"), result["gochujang"])
        assertEquals(IconDecision("plant_milk", "plant_based"), result["oat milk"])
        assertEquals(ResolvedIcon("condiments_generic", IconFit.APPROXIMATE), result.getValue("gochujang").resolve(catalog))
        assertEquals(ResolvedIcon("plant_milk", IconFit.EXACT), result.getValue("oat milk").resolve(catalog))

        val body = api.takeJson()
        assertEquals("gpt-6-luna", body["model"]!!.str())
        assertEquals("none", body.path("reasoning", "effort")!!.str())
        assertEquals("4000", body["max_output_tokens"].toString())
        assertEquals("icon_decisions", body.path("text", "format", "name")!!.str())
        val props = body.path("text", "format", "schema", "properties", "decisions", "items", "properties")!!.jsonObject
        assertEquals(listOf("i1", "i2"), props.path("id", "enum")!!.strings())
        assertEquals(catalog.decidableKeys + "none", props.path("icon", "enum")!!.strings())
        assertEquals(catalog.categories.map { it.key }, props.path("category", "enum")!!.strings())
        val input = body["input"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("developer", "user"), input.map { it["role"]!!.str() })
        val rules = input[0]["content"]!!.jsonArray.single().jsonObject
        assertTrue(rules["text"]!!.str().startsWith("You pick icons for a grocery list app."))
        assertEquals("explicit", rules.path("prompt_cache_breakpoint", "mode")!!.str())
        assertEquals("Items:\ni1: Gochujang\ni2: Oat milk", input[1]["content"]!!.jsonArray.single().jsonObject["text"]!!.str())
        assertTrue("tools" !in body)
    }

    @Test
    fun skippedIdsGenericAndUnknownKeysBecomeNone() = runTest {
        api.enqueue(
            200,
            TestSupport.responseEnvelope(
                """{"decisions":[{"id":"i2","icon":"fruit_generic","category":"fruit"},{"id":"i3","icon":"retired_key","category":"nonsense"}]}""",
            ),
        )
        val result = decider.decide(listOf(NamedItem("a", "Spatula"), NamedItem("b", "Mixed fruit cup"), NamedItem("c", "Thing")))
        assertEquals(IconDecision.NONE, result["a"])
        assertEquals(IconDecision(null, "fruit"), result["b"])
        assertEquals(IconDecision.NONE, result["c"])
        assertEquals(ResolvedIcon("paper_bag", IconFit.APPROXIMATE), result.getValue("a").resolve(catalog))
    }

    @Test
    fun emptyInputSendsNothingAndDuplicatesAreSentOnce() = runTest {
        assertTrue(decider.decide(emptyList()).isEmpty())
        assertEquals(0, api.server.requestCount)
        api.enqueue(200, TestSupport.responseEnvelope("""{"decisions":[{"id":"i1","icon":"lemon","category":"fruit"}]}"""))
        val result = decider.decide(listOf(NamedItem("lemon", "Lemons"), NamedItem("lemon", "lemon")))
        assertEquals(mapOf("lemon" to IconDecision("lemon", "fruit")), result)
        assertEquals("Items:\ni1: Lemons", api.takeJson()["input"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.single().jsonObject["text"]!!.str())
    }

    @Test
    fun batchesOfFortyNames() = runTest {
        fun answer(n: Int) = TestSupport.responseEnvelope(
            buildJsonObject {
                put("decisions", buildJsonArray { for (i in 1..n) add(buildJsonObject { put("id", "i$i"); put("icon", "none"); put("category", "household") }) })
            }.toString(),
        )
        api.enqueue(200, answer(40))
        api.enqueue(200, answer(5))
        val result = decider.decide(List(45) { NamedItem("k$it", "Thing $it") })
        assertEquals(45, result.size)
        assertEquals(2, api.server.requestCount)
        assertTrue(result.values.all { it == IconDecision(null, "household") })
    }

    @Test
    fun errorsAreLlmErrors() = runTest {
        api.enqueueFixture(200, "responses/incomplete_no_message.json")
        assertFailsWith<LlmError.BadResponse> { decider.decide(listOf(NamedItem("a", "A"))) }
        api.token = null
        assertFailsWith<LlmError.NotConfigured> { decider.decide(listOf(NamedItem("a", "A"))) }
    }
}

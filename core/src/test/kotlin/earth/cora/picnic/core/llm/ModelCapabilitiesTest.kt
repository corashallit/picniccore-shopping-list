package earth.cora.picnic.core.llm

import earth.cora.picnic.core.path
import earth.cora.picnic.core.str
import earth.cora.picnic.core.walk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelCapabilitiesTest {
    private fun body(model: String, effort: String = "none") = Json.parseToJsonElement(
        """
        {"model":"$model","max_output_tokens":100,"temperature":0.2,"reasoning":{"effort":"$effort"},
         "input":[{"role":"developer","content":[{"type":"input_text","text":"rules","prompt_cache_breakpoint":{"mode":"explicit"}}]},
                  {"role":"user","content":[{"type":"input_text","text":"hi"}]}],
         "tools":[{"type":"web_search","search_context_size":"low"}],"tool_choice":"auto",
         "include":["web_search_call.action.sources"],
         "prompt_cache_options":{"mode":"explicit"}}
        """.trimIndent(),
    ).jsonObject

    private fun JsonObject.hasCacheFields(): Boolean {
        var found = "prompt_cache_options" in this
        walk { if (it is JsonObject && "prompt_cache_breakpoint" in it) found = true }
        return found
    }

    @Test
    fun explicitCacheOnlyForGpt56AndGpt6() {
        assertTrue(ModelCapabilities.adapt(body("gpt-5.6-terra")).hasCacheFields())
        assertTrue(ModelCapabilities.adapt(body("gpt-6-luna")).hasCacheFields())
        assertTrue(ModelCapabilities.adapt(body("gpt-6.1-sol-2026-09-01")).hasCacheFields())
        assertFalse(ModelCapabilities.adapt(body("gpt-5.5")).hasCacheFields())
        assertFalse(ModelCapabilities.adapt(body("gpt-5.4-mini")).hasCacheFields())
        assertFalse(ModelCapabilities.adapt(body("gpt-5-mini")).hasCacheFields())
    }

    @Test
    fun neverTemperatureAlwaysStoreFalse() {
        val adapted = ModelCapabilities.adapt(body("gpt-5.6-terra"))
        assertFalse("temperature" in adapted)
        assertEquals("false", adapted["store"].toString())
    }

    @Test
    fun effortRules() {
        assertEquals("low", ModelCapabilities.adapt(body("gpt-6.1-sol")).path("reasoning", "effort")!!.str())
        assertEquals("low", ModelCapabilities.adapt(body("gpt-6-astra")).path("reasoning", "effort")!!.str())
        assertEquals("none", ModelCapabilities.adapt(body("gpt-6-luna")).path("reasoning", "effort")!!.str())
        assertEquals("none", ModelCapabilities.adapt(body("gpt-5.6-terra", "minimal")).path("reasoning", "effort")!!.str())
        assertEquals("medium", ModelCapabilities.adapt(body("gpt-6.1-sol", "medium")).path("reasoning", "effort")!!.str())
    }

    @Test
    fun gpt5SmallUsesMinimalAndDropsWebSearch() {
        val adapted = ModelCapabilities.adapt(body("gpt-5-nano"))
        assertEquals("minimal", adapted.path("reasoning", "effort")!!.str())
        assertNull(adapted["tools"])
        assertNull(adapted["tool_choice"])
        assertNull(adapted["include"])
        // gpt-5-mini at low keeps its tools.
        assertTrue("tools" in ModelCapabilities.adapt(body("gpt-5-mini", "low")))
        // gpt-5.5 is not "gpt-5-mini".
        assertEquals("none", ModelCapabilities.adapt(body("gpt-5.5")).path("reasoning", "effort")!!.str())
    }

    @Test
    fun nearestSupportedEffortPrefersGoingUp() {
        val msg = "Unsupported value: 'none' is not supported with the 'gpt-6.1-sol' model. Supported values are: 'low', 'medium', 'high', 'xhigh', and 'max'."
        assertEquals(ReasoningEffort.LOW, ModelCapabilities.nearestSupportedEffort(msg, ReasoningEffort.NONE))
        val capped = "Supported values are: 'none', 'low', 'medium', 'high', and 'xhigh'."
        assertEquals(ReasoningEffort.XHIGH, ModelCapabilities.nearestSupportedEffort(capped, ReasoningEffort.MAX))
        val small = "Supported values are: 'minimal', 'low', 'medium', and 'high'."
        assertEquals(ReasoningEffort.MINIMAL, ModelCapabilities.nearestSupportedEffort(small, ReasoningEffort.NONE))
        assertNull(ModelCapabilities.nearestSupportedEffort("something else entirely", ReasoningEffort.NONE))
        assertNull(ModelCapabilities.nearestSupportedEffort(capped, ReasoningEffort.LOW), "current value already supported")
    }

    @Test
    fun withoutCacheFieldsKeepsEverythingElse() {
        val stripped = ModelCapabilities.withoutCacheFields(body("gpt-5.6-terra"))
        assertFalse(stripped.hasCacheFields())
        assertTrue("\"rules\"" in stripped["input"].toString())
        assertTrue("tools" in stripped)
    }
}

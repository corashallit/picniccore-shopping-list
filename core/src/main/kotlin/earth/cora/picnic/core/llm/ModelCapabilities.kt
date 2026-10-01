package earth.cora.picnic.core.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Per-model request rules for /responses, keyed by the model id being SENT (research/openai_api.md
 * §2.2 and §2.6). With fixed models this mostly matters when a proxy MODEL_MAP or the developer CLI
 * changes the model; the one-shot downgrade in OpenAiClient covers anything this table misses.
 */
internal object ModelCapabilities {
    private val GPT5_SMALL = Regex("^gpt-5-(mini|nano)(\\b|-).*")

    /** gpt-5.6* and gpt-6* take explicit prompt caching; gpt-5.5/5.4 answer 400 invalid_parameter. */
    fun explicitCache(model: String): Boolean = model.startsWith("gpt-5.6") || model.startsWith("gpt-6")

    /** The effort to send for [requested] on [model]. */
    fun effortFor(model: String, requested: ReasoningEffort): ReasoningEffort {
        val noNone = model.startsWith("gpt-6.1-sol") || model.startsWith("gpt-6-astra")
        val small = GPT5_SMALL.matches(model)
        return when {
            small && requested == ReasoningEffort.NONE -> ReasoningEffort.MINIMAL
            small -> requested
            noNone && requested <= ReasoningEffort.MINIMAL -> ReasoningEffort.LOW
            // Only gpt-5-mini/nano take "minimal"; everything newer takes "none".
            requested == ReasoningEffort.MINIMAL -> ReasoningEffort.NONE
            else -> requested
        }
    }

    /**
     * Rewrites a /responses body for its model: drops cache fields where unsupported, adapts the
     * effort, drops web_search when the effort is "minimal" (400 otherwise), never sends temperature,
     * and makes sure store=false is present.
     */
    fun adapt(body: JsonObject): JsonObject {
        val model = (body["model"] as? JsonPrimitive)?.contentOrNull ?: return body
        var out = body
        if ("temperature" in out) out = JsonObject(out - "temperature")
        if ("store" !in out) out = JsonObject(out + ("store" to JsonPrimitive(false)))
        if (!explicitCache(model)) out = withoutCacheFields(out)

        val reasoning = out["reasoning"] as? JsonObject
        val requested = (reasoning?.get("effort") as? JsonPrimitive)?.contentOrNull?.let(ReasoningEffort::fromWire)
        if (reasoning != null && requested != null) {
            val effort = effortFor(model, requested)
            if (effort != requested) out = withEffort(out, effort)
            if (effort == ReasoningEffort.MINIMAL) out = withoutWebSearch(out)
        }
        return out
    }

    fun withEffort(body: JsonObject, effort: ReasoningEffort): JsonObject {
        val reasoning = (body["reasoning"] as? JsonObject).orEmpty()
        return JsonObject(body + ("reasoning" to JsonObject(reasoning + ("effort" to JsonPrimitive(effort.wire)))))
    }

    /** Removes `prompt_cache_options` and every `prompt_cache_breakpoint` inside input content parts. */
    fun withoutCacheFields(body: JsonObject): JsonObject {
        val input = body["input"] as? JsonArray
        val cleanedInput = input?.let { items ->
            JsonArray(items.map { item -> stripBreakpoints(item) })
        }
        val withoutOptions = body - "prompt_cache_options"
        return JsonObject(if (cleanedInput != null) withoutOptions + ("input" to cleanedInput) else withoutOptions)
    }

    private fun stripBreakpoints(item: JsonElement): JsonElement {
        val message = item as? JsonObject ?: return item
        val content = message["content"] as? JsonArray ?: return message
        val parts = content.map { part ->
            if (part is JsonObject && "prompt_cache_breakpoint" in part) JsonObject(part - "prompt_cache_breakpoint") else part
        }
        return JsonObject(message + ("content" to JsonArray(parts)))
    }

    private fun withoutWebSearch(body: JsonObject): JsonObject {
        val tools = body["tools"] as? JsonArray ?: return body
        val kept = tools.filterNot { tool ->
            (tool as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull?.startsWith("web_search") == true
        }
        if (kept.size == tools.size) return body
        var out: Map<String, JsonElement> = body
        if (kept.isEmpty()) {
            out = out - "tools" - "tool_choice"
            val include = (body["include"] as? JsonArray)
                ?.filterNot { (it as? JsonPrimitive)?.contentOrNull?.startsWith("web_search_call") == true }
            out = if (include.isNullOrEmpty()) out - "include" else out + ("include" to JsonArray(include))
        } else {
            out = out + ("tools" to JsonArray(kept))
        }
        return JsonObject(out)
    }

    private val SUPPORTED_VALUES = Regex("Supported values are:(.*)", RegexOption.IGNORE_CASE)
    private val QUOTED = Regex("'([a-z]+)'")

    /**
     * For a 400 on `reasoning.effort`: parses "Supported values are: 'low', 'medium', ..." and returns the
     * nearest supported effort, preferring the next higher one. Null when nothing usable is listed.
     */
    fun nearestSupportedEffort(errorMessage: String, current: ReasoningEffort): ReasoningEffort? {
        val list = SUPPORTED_VALUES.find(errorMessage)?.groupValues?.get(1) ?: return null
        val supported = QUOTED.findAll(list).mapNotNull { ReasoningEffort.fromWire(it.groupValues[1]) }.toSortedSet()
        if (supported.isEmpty() || current in supported) return null
        return supported.firstOrNull { it > current } ?: supported.lastOrNull { it < current }
    }

    private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
}

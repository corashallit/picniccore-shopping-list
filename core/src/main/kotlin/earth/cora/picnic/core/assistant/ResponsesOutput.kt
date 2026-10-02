package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.llm.ApiResult
import earth.cora.picnic.core.llm.LlmError
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reading a /responses answer (research/openai_api.md §2.5). Rules:
 * - status "incomplete" (any reason) or "failed" -> BadResponse, even with a partial message.
 * - Use the LAST message item whose phase is not "commentary" (missing phase = final). A refusal
 *   part there -> Refusal. If its JSON does not parse, earlier final messages are tried.
 * - Never concatenate output_text across messages (duplicate final messages exist in the wild).
 */
internal object ResponsesOutput {
    /** Lenient decoding of the model's JSON (strict schema already constrains the shape). */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = false
    }

    /** Final message texts, newest first. Throws LlmError for incomplete/failed/no-message/refusal. */
    fun finalTexts(result: ApiResult): List<String> {
        val resp = result.json
        val rid = result.requestId
        when (val status = resp.string("status")) {
            null, "completed" -> Unit
            "incomplete" -> {
                val reason = (resp["incomplete_details"] as? JsonObject)?.string("reason") ?: "unknown"
                throw LlmError.BadResponse("response incomplete ($reason)", code = "incomplete", requestId = rid)
            }
            "failed" -> {
                val error = resp["error"] as? JsonObject
                throw LlmError.BadResponse(
                    "response failed: ${error?.string("message")?.take(200) ?: "no details"}",
                    code = error?.string("code") ?: "failed",
                    requestId = rid,
                )
            }
            else -> throw LlmError.BadResponse("unexpected response status '$status'", code = status, requestId = rid)
        }
        val finals = output(resp).filter { it.string("type") == "message" && it.string("phase") != "commentary" }
        if (finals.isEmpty()) throw LlmError.BadResponse("response has no message", code = "no_message", requestId = rid)

        val last = finals.last()
        parts(last).firstOrNull { it.string("type") == "refusal" }?.let { part ->
            throw LlmError.Refusal(part.string("refusal").orEmpty().take(300), rid)
        }
        return finals.asReversed().mapNotNull { message ->
            val texts = parts(message).filter { it.string("type") == "output_text" }.mapNotNull { it.string("text") }
            texts.joinToString("").takeIf { it.isNotBlank() }
        }
    }

    /** Decodes the structured output with [deserializer], trying final messages newest first. */
    fun <T> decode(result: ApiResult, deserializer: DeserializationStrategy<T>): T {
        val candidates = finalTexts(result)
        for (text in candidates) {
            try {
                return json.decodeFromString(deserializer, text)
            } catch (e: SerializationException) {
                // try an earlier final message
            } catch (e: IllegalArgumentException) {
                // try an earlier final message
            }
        }
        throw LlmError.BadResponse("structured output did not match the schema", code = "unparsable", requestId = result.requestId)
    }

    /**
     * Web pages the model searched or opened, for the "Sources" chips. Most relevant first:
     * url_citation annotations, then pages it opened (action.url of open_page / find_in_page), then the
     * search results it was shown (action.sources[].url, only present with include=web_search_call.action.sources).
     * Deduplicated by URL; a title from any of them is kept.
     */
    fun sources(resp: JsonObject): List<WebSource> {
        val cited = mutableListOf<Pair<String?, String?>>()
        val opened = mutableListOf<Pair<String?, String?>>()
        val searched = mutableListOf<Pair<String?, String?>>()
        for (item in output(resp)) {
            when (item.string("type")) {
                "web_search_call" -> {
                    val action = item["action"] as? JsonObject ?: continue
                    opened += action.string("url") to null
                    (action["sources"] as? JsonArray)?.forEach { s ->
                        (s as? JsonObject)?.let { searched += it.string("url") to it.string("title") }
                    }
                }
                "message" -> parts(item).forEach { part ->
                    (part["annotations"] as? JsonArray)?.forEach { a ->
                        val ann = a as? JsonObject ?: return@forEach
                        if (ann.string("type") == "url_citation") cited += ann.string("url") to ann.string("title")
                    }
                }
            }
        }
        val byUrl = LinkedHashMap<String, String?>()
        for ((url, title) in cited + opened + searched) {
            val u = url?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: continue
            val t = title?.trim()?.takeIf { it.isNotEmpty() }
            if (u !in byUrl || (byUrl[u] == null && t != null)) byUrl[u] = t
        }
        return byUrl.map { (url, title) -> WebSource(url, title) }
    }

    /** Billable web searches: tool_usage.web_search.num_requests, else the number of search actions. */
    fun webSearches(resp: JsonObject): Int {
        val counted = ((resp["tool_usage"] as? JsonObject)?.get("web_search") as? JsonObject)
            ?.get("num_requests")?.let { (it as? JsonPrimitive)?.intOrNull }
        if (counted != null) return counted
        return output(resp).count { item ->
            item.string("type") == "web_search_call" && (item["action"] as? JsonObject)?.string("type") == "search"
        }
    }

    private val MARKDOWN_LINK = Regex("""\[([^\]]*)]\((?:https?://|www\.)[^)\s]*\)""")
    private val BARE_URL = Regex("""\(\s*https?://[^)\s]*\s*\)""")

    /** The user-facing message: markdown links reduced to their text, bare "(url)" removed, whitespace tidied. */
    fun cleanMessage(message: String): String =
        message.replace(MARKDOWN_LINK, "$1")
            .replace(BARE_URL, "")
            .replace(Regex("\\(\\s*\\)"), "")
            .replace(Regex("\\s+"), " ")
            .replace(Regex(" ([.,;:!?])"), "$1")
            .trim()

    fun model(resp: JsonObject): String? = resp.string("model")

    fun usage(resp: JsonObject): TokenUsage? {
        val usage = resp["usage"] as? JsonObject ?: return null
        fun int(obj: JsonObject?, key: String): Int = (obj?.get(key) as? JsonPrimitive)?.intOrNull ?: 0
        val inDetails = usage["input_tokens_details"] as? JsonObject
        val outDetails = usage["output_tokens_details"] as? JsonObject
        return TokenUsage(
            inputTokens = int(usage, "input_tokens"),
            cachedTokens = int(inDetails, "cached_tokens"),
            cacheWriteTokens = int(inDetails, "cache_write_tokens"),
            outputTokens = int(usage, "output_tokens"),
            reasoningTokens = int(outDetails, "reasoning_tokens"),
        )
    }

    private fun output(resp: JsonObject): List<JsonObject> =
        (resp["output"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun parts(message: JsonObject): List<JsonObject> =
        (message["content"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}

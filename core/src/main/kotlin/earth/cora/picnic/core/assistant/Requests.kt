package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.catalog.BasketCatalog
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.ResponsesModel
import earth.cora.picnic.core.llm.WebSearchSpec
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.UNSORTED_WIRE_ID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Strict JSON-schema builders for `text.format` (research/openai_api.md §2.3). Every object is built
 * through [strictObject], so the strict-mode rules (every property required, additionalProperties
 * false, optional values as ["string","null"]) hold by construction.
 */
internal object Schemas {
    fun string(): JsonObject = buildJsonObject { put("type", "string") }

    fun nullableString(): JsonObject = buildJsonObject {
        putJsonArray("type") { add(JsonPrimitive("string")); add(JsonPrimitive("null")) }
    }

    fun enumString(values: List<String>): JsonObject {
        require(values.isNotEmpty()) { "enum must not be empty" }
        return buildJsonObject {
            put("type", "string")
            putJsonArray("enum") { values.distinct().forEach { add(JsonPrimitive(it)) } }
        }
    }

    fun array(items: JsonObject): JsonObject = buildJsonObject {
        put("type", "array")
        put("items", items)
    }

    fun strictObject(vararg properties: Pair<String, JsonElement>): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
        putJsonArray("required") { properties.forEach { add(JsonPrimitive(it.first)) } }
        put("additionalProperties", false)
    }

    /** `text` block: {"format":{"type":"json_schema","name":…,"strict":true,"schema":…}}. */
    fun textFormat(name: String, schema: JsonObject): JsonObject = buildJsonObject {
        putJsonObject("format") {
            put("type", "json_schema")
            put("name", name)
            put("strict", true)
            put("schema", schema)
        }
    }

    /** grocery_items: {items:[{name, quantity|null, note|null, aisle_id, icon, icon_fit}], message}. */
    fun ingest(aisleIds: List<String>, iconKeys: List<String>): JsonObject = strictObject(
        "items" to array(
            strictObject(
                "name" to string(),
                "quantity" to nullableString(),
                "note" to nullableString(),
                "aisle_id" to enumString(aisleIds + UNSORTED_WIRE_ID),
                "icon" to enumString(iconKeys),
                "icon_fit" to enumString(listOf(ICON_FIT_EXACT, ICON_FIT_APPROXIMATE)),
            ),
        ),
        "message" to string(),
    )

    /** aisle_assignments: {assignments:[{id: enum(i1..iN), aisle_id: enum(aisles + "unsorted")}]}. */
    fun assignments(itemIds: List<String>, aisleIds: List<String>): JsonObject = strictObject(
        "assignments" to array(
            strictObject(
                "id" to enumString(itemIds),
                "aisle_id" to enumString(aisleIds + UNSORTED_WIRE_ID),
            ),
        ),
    )

    /** aisle_baskets: {baskets:[{aisle_id: enum(ids), basket: enum(kinds)}]}. */
    fun baskets(aisleIds: List<String>, basketKeys: List<String>): JsonObject = strictObject(
        "baskets" to array(
            strictObject(
                "aisle_id" to enumString(aisleIds),
                "basket" to enumString(basketKeys),
            ),
        ),
    )

    /** icon_decisions: {decisions:[{id: enum(i1..iN), icon: enum(decidable keys + "none"), category: enum(categories)}]}. */
    fun iconDecisions(itemIds: List<String>, iconKeys: List<String>, categoryKeys: List<String>): JsonObject = strictObject(
        "decisions" to array(
            strictObject(
                "id" to enumString(itemIds),
                "icon" to enumString(iconKeys + ICON_NONE),
                "category" to enumString(categoryKeys),
            ),
        ),
    )

    const val ICON_FIT_EXACT = "exact"
    const val ICON_FIT_APPROXIMATE = "approximate"
    const val ICON_NONE = "none"
}

/** Prompt texts (core/src/main/resources/prompts, documented in docs/PROMPTS.md) and context blocks. */
internal object Prompts {
    fun resource(name: String): String {
        val stream = Prompts::class.java.getResourceAsStream("/prompts/$name")
            ?: error("prompt resource $name missing from core")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
    }

    val ingestRules: String by lazy { resource("ingest_rules.txt") }
    val recategorizeRules: String by lazy { resource("recategorize_rules.txt") }
    val basketsRules: String by lazy { resource("baskets_rules.txt") }
    val iconDeciderRules: String by lazy { resource("icon_decider_rules.txt") }
    val iconPromptTemplate: String by lazy { resource("icon_prompt.txt") }

    /** The stable ingest developer message: rules + the icon catalog (cached prefix). */
    fun ingestStable(catalog: IconCatalog): String =
        ingestRules + "\n\n" + catalogListing(catalog, includeGeneric = true)

    /** The stable IconDecider developer message: rules + the decidable catalog (cached prefix). */
    fun iconDeciderStable(catalog: IconCatalog): String =
        iconDeciderRules + "\n\n" + catalogListing(catalog, includeGeneric = false)

    /** The stable basket developer message: rules + the container list with what each suits. */
    fun basketsStable(baskets: BasketCatalog): String = buildString {
        append(basketsRules).append("\n\nContainers (key: what it is; suits):\n")
        BasketKind.entries.forEach { kind ->
            val entry = baskets.entry(kind)
            val label = entry?.label ?: kind.key
            // English keywords only (the French ones repeat them); they are what each container is meant for.
            val suits = entry?.keywords?.filter { it.all { c -> c.code < 0x80 } }?.take(20)?.joinToString(", ").orEmpty()
            append("- ").append(kind.key).append(": ").append(label)
            if (suits.isNotEmpty()) append("; ").append(suits)
            append('\n')
        }
    }.trimEnd()

    /** "Icon catalog …:\n- fruit: apple (Apples), …" grouped by category, catalog order. */
    fun catalogListing(catalog: IconCatalog, includeGeneric: Boolean): String = buildString {
        append("Icon catalog by category (key (what it shows)):\n")
        catalog.categories.forEach { category ->
            val members = catalog.icons.filter { it.category == category.key && (includeGeneric || !catalog.isGeneric(it.key)) }
            if (members.isEmpty()) return@forEach
            append("- ").append(category.key).append(": ")
            append(members.joinToString(", ") { "${it.key} (${it.label})" })
            append('\n')
        }
    }.trimEnd()

    /**
     * The per-request developer message (research/openai/examples/context_example.txt): aisles in
     * walking order, learned corrections, and (for ingest) the names already on the list.
     */
    fun contextBlock(
        aisles: List<Aisle>,
        corrections: List<Correction>,
        existingNames: List<String>?,
        appLanguage: String? = null,
    ): String = buildString {
        append("Store aisles in walking order (id: name):\n")
        aisles.forEach { append("- ").append(it.id).append(": ").append(oneLine(it.name, 120)).append('\n') }
        append("- ").append(UNSORTED_WIRE_ID).append(": (no matching aisle)\n")
        append("\nLearned corrections (item -> aisle id):\n")
        if (corrections.isEmpty()) append("- none\n")
        corrections.forEach { append("- ").append(oneLine(it.name, 80)).append(" -> ").append(it.aisleId).append('\n') }
        if (existingNames != null) {
            val names = existingNames.map { oneLine(it, 80) }.filter { it.isNotEmpty() }.take(MAX_EXISTING_NAMES)
            append("\nAlready on the list: ")
            append(if (names.isEmpty()) "nothing yet" else names.joinToString(", "))
        }
        appLanguage?.let { oneLine(it, 40) }?.takeIf { it.isNotEmpty() }?.let { language ->
            append("\n\nApp language: ").append(language)
            append(". Write item names and the message in ").append(language)
            append(" unless the user's own words are in another language; web pages and dish names do not count.")
        }
    }.trimEnd()

    /** "Items:\ni1: Oat milk\ni2: …" */
    fun itemsBlock(names: List<String>): String =
        "Items:\n" + names.mapIndexed { i, name -> "${wireId(i)}: ${oneLine(name, 120)}" }.joinToString("\n")

    /** "Aisles:\n- produce: Produce\n…" */
    fun aislesBlock(aisles: List<Aisle>): String =
        "Aisles:\n" + aisles.joinToString("\n") { "- ${it.id}: ${oneLine(it.name, 120)}" }

    fun iconPrompt(itemName: String, hint: String?): String {
        val prompt = iconPromptTemplate.replace("{item}", oneLine(itemName, 80).ifEmpty { "a grocery item" })
        val extra = hint?.let { oneLine(it, 160) }?.takeIf { it.isNotEmpty() }
        return if (extra == null) prompt else "$prompt Details about the item: $extra."
    }

    fun wireId(index: Int): String = "i${index + 1}"

    fun oneLine(text: String, max: Int): String = text.replace(Regex("\\s+"), " ").trim().take(max)

    const val PHOTO_INSTRUCTION = "Photo of a handwritten shopping list. Add its items."
    const val TRANSCRIPT_PREFIX = "Voice note transcript: "
    const val MAX_EXISTING_NAMES = 150
}

/** Builds /responses bodies in the cache-friendly order (research/openai_api.md §2.1). */
internal object Requests {
    private val EXPLICIT = buildJsonObject { put("mode", "explicit") }

    fun inputText(text: String, breakpoint: Boolean = false): JsonObject = buildJsonObject {
        put("type", "input_text")
        put("text", text)
        if (breakpoint) put("prompt_cache_breakpoint", EXPLICIT)
    }

    fun inputImage(dataUrl: String): JsonObject = buildJsonObject {
        put("type", "input_image")
        put("image_url", dataUrl)
        put("detail", "high")
    }

    private fun message(role: String, parts: List<JsonObject>): JsonObject = buildJsonObject {
        put("role", role)
        put("content", JsonArray(parts))
    }

    fun webSearchTool(spec: WebSearchSpec): JsonObject = buildJsonObject {
        put("type", "web_search")
        put("search_context_size", spec.contextSize)
        putJsonObject("user_location") {
            put("type", "approximate")
            put("country", spec.country)
            put("region", spec.region)
            put("timezone", spec.timezone)
        }
    }

    /**
     * {model, store:false, max_output_tokens, reasoning:{effort}, text:{format}, input:[developer stable
     * (+ cache breakpoint), developer context?, user], tools?, prompt_cache_options}. ModelCapabilities
     * removes the cache fields for models that reject them.
     */
    fun responses(
        model: ResponsesModel,
        schemaName: String,
        schema: JsonObject,
        stable: String,
        context: String?,
        user: List<JsonObject>,
        webSearch: WebSearchSpec? = null,
    ): JsonObject = buildJsonObject {
        put("model", model.id)
        put("store", false)
        put("max_output_tokens", model.maxOutputTokens)
        putJsonObject("reasoning") { put("effort", model.effort.wire) }
        put("text", Schemas.textFormat(schemaName, schema))
        put(
            "input",
            buildJsonArray {
                add(message("developer", listOf(inputText(stable, breakpoint = true))))
                if (context != null) add(message("developer", listOf(inputText(context))))
                add(message("user", user))
            },
        )
        if (webSearch != null && webSearch.enabled) {
            putJsonArray("tools") { add(webSearchTool(webSearch)) }
            put("tool_choice", "auto")
            putJsonArray("include") { add(JsonPrimitive("web_search_call.action.sources")) }
        }
        put("prompt_cache_options", EXPLICIT)
    }
}

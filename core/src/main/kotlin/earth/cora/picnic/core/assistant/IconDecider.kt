package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmTimeouts
import earth.cora.picnic.core.llm.ModelDefaults
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.llm.ResponsesModel
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ResolvedIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Step 2 of the icon cascade (docs/ARCHITECTURE.md §8.2): for item names the local matcher could
 * not place, pick a catalog icon that depicts the item, or "none" (then step 3 may draw one).
 * CROSS-MODULE CONTRACT. Main-safe, cancellable, throws only LlmError.
 *
 * Implementations:
 * - [ResponsesIconDecider]: ModelDefaults.FAST (gpt-6-luna) via POST /responses, strict enum schema.
 * - FUTURE: an implementation on OpenAI's Decisions API (POST /v1/decisions). It is a limited
 *   preview whose schema is not public, and it answers 403 "Decision API is not enabled for this
 *   user" for the project key (checked 2026-10-01). Do NOT write a client against a guessed schema;
 *   see docs/ARCHITECTURE.md §8.6 for the seam and the switch-over checklist.
 */
interface IconDecider {
    /**
     * Returns a decision for EVERY input id (an id the model skipped maps to IconDecision.NONE).
     * Batches internally (<= 40 names per call). Empty input -> empty map, no request.
     */
    suspend fun decide(items: List<NamedItem>): Map<String, IconDecision>
}

data class IconDecision(
    /** A non-generic catalog key that depicts the item, or null for "none". */
    val icon: String?,
    /** The item's catalog category key (picks the fallback icon), or null if unknown. */
    val category: String?,
) {
    companion object {
        val NONE: IconDecision = IconDecision(icon = null, category = null)
    }
}

/**
 * What to store for a decision: a match -> (icon, EXACT); "none" -> (category fallback icon or
 * DEFAULT_ICON, APPROXIMATE), which makes the item a candidate for a live-drawn icon.
 * Keys that are not decidable catalog keys count as "none".
 */
fun IconDecision.resolve(catalog: IconCatalog): ResolvedIcon {
    val key = icon
    return if (key != null && key in catalog && !catalog.isGeneric(key)) {
        ResolvedIcon(key, IconFit.EXACT)
    } else {
        ResolvedIcon(catalog.fallbackForCategory(category), IconFit.APPROXIMATE)
    }
}

/**
 * IconDecider on the Responses API (verified live 2026-10-01: 12 names, 2.6 s, ~US$0.0004 cold).
 * Request: store=false, reasoning.effort from [model] ("none"), max_output_tokens from [model],
 * strict json_schema "icon_decisions" =
 * {decisions:[{id: enum(i1..iN), icon: enum(catalog.decidableKeys + "none"), category: enum(category keys)}]},
 * input = developer RULES + the catalog grouped by category (cache breakpoint after it, on models that
 * take explicit cache fields) and a user message "Items:\ni1: <name>\n...". Rules text lives in
 * core/src/main/resources/prompts/icon_decider_rules.txt (docs/PROMPTS.md).
 */
class ResponsesIconDecider(
    private val client: OpenAiClient,
    private val catalog: IconCatalog,
    private val model: ResponsesModel = ModelDefaults.FAST,
) : IconDecider {
    private val stable: String by lazy { Prompts.iconDeciderStable(catalog) }
    private val categoryKeys: List<String> by lazy { catalog.categories.map { it.key } }

    override suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> {
        val unique = items.distinctBy { it.id }
        if (unique.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, IconDecision>()
        for (batch in unique.chunked(BATCH_SIZE)) {
            val wireIds = batch.indices.map(Prompts::wireId)
            val body = Requests.responses(
                model = model,
                schemaName = "icon_decisions",
                schema = Schemas.iconDecisions(wireIds, catalog.decidableKeys, categoryKeys),
                stable = stable,
                context = null,
                user = listOf(Requests.inputText(Prompts.itemsBlock(batch.map { it.name }))),
            )
            val result = client.responses(body, LlmTimeouts.FAST)
            val wire = withContext(Dispatchers.Default) { ResponsesOutput.decode(result, WireDecisions.serializer()) }
            val byWire = wire.decisions.associateBy { it.id }
            batch.forEachIndexed { i, item ->
                val row = byWire[wireIds[i]]
                out[item.id] = if (row == null) IconDecision.NONE else toDecision(row)
            }
        }
        return out
    }

    private fun toDecision(row: WireDecision): IconDecision {
        val icon = row.icon?.takeIf { it != Schemas.ICON_NONE && it in catalog && !catalog.isGeneric(it) }
        val category = row.category?.takeIf { it in categoryKeys } ?: icon?.let { catalog[it]?.category }
        return IconDecision(icon = icon, category = category)
    }

    internal companion object {
        const val BATCH_SIZE = 40
    }
}

@Serializable
internal data class WireDecisions(val decisions: List<WireDecision>)

@Serializable
internal data class WireDecision(val id: String, val icon: String? = null, val category: String? = null)

package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.llm.LlmTimeouts
import earth.cora.picnic.core.llm.ModelDefaults
import earth.cora.picnic.core.llm.ModelSet
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.PantryMatch
import earth.cora.picnic.core.model.UNSORTED_WIRE_ID
import earth.cora.picnic.core.model.normalizeMetric
import earth.cora.picnic.core.model.normalizeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

/**
 * The production GroceryAssistant. Builds requests with the prompt + schema builders (core-internal),
 * sends them with [client], parses with the Responses parsing rules (last non-commentary message,
 * refusal part, status incomplete / no message) and post-processes (corrections, icon overrides,
 * unknown aisle, dedupe, icon sanitize). See docs/ARCHITECTURE.md §7 and docs/PROMPTS.md.
 *
 * [models] is always ModelDefaults.models in the app; only tests and the developer CLI pass another set.
 */
class OpenAiGroceryAssistant(
    private val client: OpenAiClient,
    private val catalog: IconCatalog,
    private val models: ModelSet = ModelDefaults.models,
) : GroceryAssistant {
    private val ingestStable: String by lazy { Prompts.ingestStable(catalog) }
    private val basketsStable: String by lazy { Prompts.basketsStable(catalog.baskets) }

    override suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult {
        val user = when (input) {
            is IngestInput.Text -> input.text.trim().takeIf { it.isNotEmpty() }?.let {
                listOf(Requests.inputText(it.take(MAX_INPUT_CHARS)))
            }
            is IngestInput.Transcript -> input.text.trim().takeIf { it.isNotEmpty() }?.let {
                listOf(Requests.inputText(Prompts.TRANSCRIPT_PREFIX + it.take(MAX_INPUT_CHARS)))
            }
            is IngestInput.Image -> {
                if (input.bytes.isEmpty()) {
                    null
                } else {
                    val dataUrl = withContext(Dispatchers.Default) {
                        "data:${input.mimeType};base64," + Base64.getEncoder().encodeToString(input.bytes)
                    }
                    listOf(Requests.inputText(Prompts.PHOTO_INSTRUCTION), Requests.inputImage(dataUrl))
                }
            }
        } ?: return IngestResult(emptyList(), "Nothing to add.", emptyList(), emptyList(), CallMeta(null, null, 0))

        val body = Requests.responses(
            model = models.smart,
            schemaName = "grocery_items",
            schema = Schemas.ingest(context.aisles.map { it.id }, catalog.keys),
            stable = ingestStable,
            context = Prompts.contextBlock(context.aisles, context.corrections, context.existingNames, context.appLanguage, context.pantry),
            user = user,
            // Web search is for text (and transcripts); photos never get tools.
            webSearch = if (input is IngestInput.Image) null else models.webSearch,
        )
        val result = client.responses(body, LlmTimeouts.INGEST)
        val wire = withContext(Dispatchers.Default) { ResponsesOutput.decode(result, WireIngest.serializer()) }
        val processed = postProcess(wire.items, context)
        return IngestResult(
            items = processed.items,
            message = ResponsesOutput.cleanMessage(wire.message).take(MAX_MESSAGE_CHARS),
            sources = ResponsesOutput.sources(result.json).take(MAX_SOURCES),
            skippedDuplicates = processed.skipped,
            meta = CallMeta(
                model = ResponsesOutput.model(result.json),
                requestId = result.requestId,
                latencyMs = result.latencyMs,
                webSearches = ResponsesOutput.webSearches(result.json),
                usage = ResponsesOutput.usage(result.json),
            ),
            lines = if (input is IngestInput.Image) photoLines(wire.lines, processed.indexOf) else emptyList(),
            recipe = wire.recipe?.let(::recipeInfo),
        )
    }

    /**
     * [items] after post-processing; [skipped] the names already on the list or repeated in the answer;
     * [indexOf] maps the model's item index to the index in [items] (a repeat maps to the row it was merged into).
     */
    internal class Processed(val items: List<ProposedItem>, val skipped: List<String>, val indexOf: Map<Int, Int> = emptyMap())

    /** Photos only (§4.19): the lines read off the paper, top to bottom; item_index re-pointed into the processed items. */
    private fun photoLines(lines: List<WireLine>, indexOf: Map<Int, Int>): List<PhotoLine> = lines.take(MAX_ITEMS * 2).mapNotNull { line ->
        val text = Prompts.oneLine(line.text, 200).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        PhotoLine(
            text = text,
            crossedOut = line.crossedOut,
            correctionOf = line.correctionOf?.let { Prompts.oneLine(it, 120) }?.takeIf { it.isNotEmpty() },
            itemIndex = line.itemIndex?.let(indexOf::get),
        )
    }

    /** The recipe card's header facts (§4.17, D16): only real, positive numbers; a blank title means no recipe. */
    private fun recipeInfo(recipe: WireRecipe): RecipeInfo? {
        val title = Prompts.oneLine(recipe.title, 120).takeIf { it.isNotEmpty() } ?: return null
        return RecipeInfo(title, recipe.recipesCompared?.takeIf { it > 0 }, recipe.servings?.takeIf { it > 0 })
    }

    /**
     * Client-side rules on top of the model's answer: trim; quantities in metric (normalizeMetric); a learned
     * correction overrides aisle_id; "unsorted"/unknown aisle -> null; a learned icon override replaces icon (EXACT);
     * "exact" on a *_generic key holds only when the catalog lists the name for it; a model "approximate" becomes
     * EXACT when IconCatalog.matchExact finds the name (curated term, no head-noun guess); an invalid icon ->
     * DEFAULT_ICON + APPROXIMATE. Rows: a name already on the list (unchecked) stays as a skipped row
     * (SkipReason.ALREADY_ON_LIST, also named in skipped); a repeat within the answer is merged into its first
     * copy; the model's skip_reason, named and pantry_entry are kept, then the pantry backstop runs (PantryMatch.apply:
     * a named item is never skipped for the pantry, a worked-out item a pantry entry covers is).
     */
    internal fun postProcess(items: List<WireItem>, context: IngestContext): Processed {
        val aisleIds = context.aisles.mapTo(HashSet()) { it.id }
        val corrections = correctionMap(context.corrections, aisleIds)
        val existing = context.existingNames.mapTo(HashSet()) { normalizeName(it) }
        val kept = LinkedHashMap<String, ProposedItem>()
        val position = HashMap<String, Int>()
        val indexOf = HashMap<Int, Int>()
        val skipped = mutableListOf<String>()
        for ((wireIndex, raw) in items.take(MAX_ITEMS).withIndex()) {
            val name = Prompts.oneLine(raw.name, 120)
            val key = normalizeName(name)
            if (key.isEmpty()) continue
            val quantity = raw.quantity.cleanOptional()?.let(::normalizeMetric)
            val note = raw.note.cleanOptional()
            kept[key]?.let { first ->
                // Duplicate within the answer: keep the first, borrow a quantity/note it lacks.
                kept[key] = first.copy(quantity = first.quantity ?: quantity, note = first.note ?: note)
                indexOf[wireIndex] = position.getValue(key)
                skipped += name
                continue
            }
            val onList = key in existing
            if (onList) skipped += name
            val corrected = corrections[key]
            val aisleId = corrected ?: raw.aisleId?.takeIf { it != UNSORTED_WIRE_ID && it in aisleIds }
            val override = context.iconOverrides[key]?.takeIf { it in catalog }
            val modelIcon = raw.icon?.takeIf { it in catalog }
            // A *_generic key depicts only what the catalog lists for it ("Bread" is bakery_generic); for
            // anything else it is a category stand-in, so the model's "exact" is checked against the catalog below.
            val modelExact = modelIcon != null && raw.iconFit == Schemas.ICON_FIT_EXACT && !catalog.isGeneric(modelIcon)
            val (icon, fit) = when {
                override != null -> override to IconFit.EXACT
                modelExact -> modelIcon!! to IconFit.EXACT
                // Step 1 still wins over a modest model when the curated catalog lists this very name
                // ("Cilantro" -> parsley), so no bespoke drawing is paid for. Only exact terms count: the
                // model saw the catalog and judged the head noun ("Nutritional yeast" -> yeast) wrong.
                else -> catalog.matchExact(name)?.key?.let { it to IconFit.EXACT }
                    ?: ((modelIcon ?: IconCatalog.DEFAULT_ICON) to IconFit.APPROXIMATE)
            }
            val modelReason = skipReasonOf(raw.skipReason)
            val reason = when {
                onList -> SkipReason.ALREADY_ON_LIST
                // The model cannot know better than the list: a name that is not on it is not "already on your list".
                modelReason == SkipReason.ALREADY_ON_LIST -> SkipReason.NONE
                else -> modelReason
            }
            kept[key] = ProposedItem(
                name = name,
                quantity = quantity,
                note = note,
                aisleId = aisleId,
                icon = icon,
                iconFit = fit,
                aisleFromCorrection = corrected != null,
                skipReason = reason,
                named = raw.named ?: true,
                pantryEntry = raw.pantryEntry?.let { Prompts.oneLine(it, 80) }?.takeIf { it.isNotEmpty() && reason == SkipReason.IN_PANTRY },
            )
            position[key] = kept.size - 1
            indexOf[wireIndex] = kept.size - 1
        }
        return Processed(PantryMatch.apply(kept.values.toList(), context.pantry), skipped, indexOf)
    }

    private fun skipReasonOf(wire: String?): SkipReason = when (wire) {
        "already_on_list" -> SkipReason.ALREADY_ON_LIST
        "optional" -> SkipReason.OPTIONAL
        "in_pantry" -> SkipReason.IN_PANTRY
        else -> SkipReason.NONE
    }

    override suspend fun transcribe(clip: AudioClip, keywords: List<String>): Transcript {
        val extension = clip.fileName.substringAfterLast('.', "").lowercase()
        if (extension !in SUPPORTED_AUDIO) {
            // The API picks the decoder from the extension; .aac/.3gp and extension-less names are rejected.
            throw LlmError.BadResponse("unsupported audio file name '${clip.fileName}'", code = "unsupported_audio")
        }
        if (clip.bytes.isEmpty()) return Transcript("")
        val result = client.transcription(models.transcribe, clip.bytes, clip.fileName, keywords, LlmTimeouts.TRANSCRIBE)
        val text = (result.json["text"] as? JsonPrimitive)?.contentOrNull
            ?: throw LlmError.BadResponse("transcription without text", requestId = result.requestId)
        return Transcript(text.trim())
    }

    override suspend fun recategorize(
        items: List<NamedItem>,
        aisles: List<Aisle>,
        corrections: List<Correction>,
    ): Map<String, String?> {
        if (items.isEmpty()) return emptyMap()
        val aisleIds = aisles.mapTo(HashSet()) { it.id }
        val correctionMap = correctionMap(corrections, aisleIds)
        val out = LinkedHashMap<String, String?>()
        val toSend = mutableListOf<NamedItem>()
        for (item in items.distinctBy { it.id }) {
            val corrected = correctionMap[normalizeName(item.name)]
            if (corrected != null) out[item.id] = corrected else toSend += item
        }
        if (aisles.isEmpty()) {
            toSend.forEach { out[it.id] = null }
            return out
        }
        val stable = Prompts.recategorizeRules
        val context = Prompts.contextBlock(aisles, corrections.filter { it.aisleId in aisleIds }.take(60), existingNames = null)
        for (batch in toSend.chunked(BATCH_SIZE)) {
            val wireIds = batch.indices.map(Prompts::wireId)
            val body = Requests.responses(
                model = models.fast,
                schemaName = "aisle_assignments",
                schema = Schemas.assignments(wireIds, aisles.map { it.id }),
                stable = stable,
                context = context,
                user = listOf(Requests.inputText(Prompts.itemsBlock(batch.map { it.name }))),
            )
            val result = client.responses(body, LlmTimeouts.FAST)
            val wire = withContext(Dispatchers.Default) { ResponsesOutput.decode(result, WireAssignments.serializer()) }
            val byWire = wire.assignments.associate { it.id to it.aisleId }
            batch.forEachIndexed { i, item ->
                out[item.id] = byWire[wireIds[i]]?.takeIf { it != UNSORTED_WIRE_ID && it in aisleIds }
            }
        }
        return out
    }

    override suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind> {
        if (aisles.isEmpty()) return emptyMap()
        val body = Requests.responses(
            model = models.fast,
            schemaName = "aisle_baskets",
            schema = Schemas.baskets(aisles.map { it.id }, BasketKind.entries.map { it.key }),
            stable = basketsStable,
            context = null,
            user = listOf(Requests.inputText(Prompts.aislesBlock(aisles))),
        )
        val result = client.responses(body, LlmTimeouts.FAST)
        val wire = withContext(Dispatchers.Default) { ResponsesOutput.decode(result, WireBaskets.serializer()) }
        val ids = aisles.mapTo(HashSet()) { it.id }
        val out = LinkedHashMap<String, BasketKind>()
        wire.baskets.forEach { row ->
            val kind = BasketKind.fromKey(row.basket)
            if (row.aisleId in ids && kind != null && row.aisleId !in out) out[row.aisleId] = kind
        }
        return out
    }

    override suspend fun drawIcon(request: IconDrawRequest): DrawnIcon {
        val prompt = Prompts.iconPrompt(request.itemName, request.hint)
        val image = client.imageEdit(models.image, prompt, request.styleReference, request.styleReferenceFileName, LlmTimeouts.IMAGE)
        return DrawnIcon(
            bytes = image.bytes,
            mimeType = "image/${models.image.outputFormat}",
            meta = CallMeta(model = models.image.id, requestId = image.requestId, latencyMs = image.latencyMs),
        )
    }

    override suspend fun testConnection(): ConnectionCheck {
        val result = client.model(models.smart.id, LlmTimeouts.TEST_CONNECTION)
        val id = (result.json["id"] as? JsonPrimitive)?.contentOrNull ?: models.smart.id
        return ConnectionCheck(model = id, meta = CallMeta(model = id, requestId = result.requestId, latencyMs = result.latencyMs))
    }

    private fun correctionMap(corrections: List<Correction>, aisleIds: Set<String>): Map<String, String> {
        val map = HashMap<String, String>()
        // Newest wins regardless of the order the caller passes.
        corrections.sortedBy { it.at }.forEach { if (it.aisleId in aisleIds) map[it.name] = it.aisleId }
        return map
    }

    private fun String?.cleanOptional(): String? =
        this?.let { Prompts.oneLine(it, 200) }?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    internal companion object {
        const val BATCH_SIZE = 40
        const val MAX_INPUT_CHARS = 8_000
        const val MAX_ITEMS = 120
        const val MAX_MESSAGE_CHARS = 280

        /** Sources chips shown under the preview (cited and opened pages come first). */
        const val MAX_SOURCES = 8
        val SUPPORTED_AUDIO = setOf("m4a", "mp4", "mp3", "mpeg", "mpga", "wav", "ogg", "oga", "webm", "flac")
    }
}

@Serializable
internal data class WireIngest(
    val items: List<WireItem>,
    val message: String = "",
    val lines: List<WireLine> = emptyList(),
    val recipe: WireRecipe? = null,
)

@Serializable
internal data class WireItem(
    val name: String,
    val quantity: String? = null,
    val note: String? = null,
    @SerialName("aisle_id") val aisleId: String? = null,
    val icon: String? = null,
    @SerialName("icon_fit") val iconFit: String? = null,
    @SerialName("skip_reason") val skipReason: String? = null,
    /** She named it (typed, photographed, spoken); missing (an older answer) counts as named. */
    val named: Boolean? = null,
    @SerialName("pantry_entry") val pantryEntry: String? = null,
)

@Serializable
internal data class WireLine(
    val text: String = "",
    @SerialName("crossed_out") val crossedOut: Boolean = false,
    @SerialName("correction_of") val correctionOf: String? = null,
    @SerialName("item_index") val itemIndex: Int? = null,
)

@Serializable
internal data class WireRecipe(
    val title: String = "",
    @SerialName("recipes_compared") val recipesCompared: Int? = null,
    val servings: Int? = null,
)

@Serializable
internal data class WireAssignments(val assignments: List<WireAssignment>)

@Serializable
internal data class WireAssignment(val id: String, @SerialName("aisle_id") val aisleId: String? = null)

@Serializable
internal data class WireBaskets(val baskets: List<WireBasket>)

@Serializable
internal data class WireBasket(@SerialName("aisle_id") val aisleId: String, val basket: String)

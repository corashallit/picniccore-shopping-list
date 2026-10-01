package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource

/**
 * Everything the app asks the LLM. CROSS-MODULE CONTRACT (docs/ARCHITECTURE.md §7).
 * Every function is main-safe and cancellable, and throws only LlmError (plus CancellationException).
 */
interface GroceryAssistant {
    /**
     * One Responses API call to the smart model with a strict JSON schema. Text input may use
     * web_search (model decides; only if LlmConfig.webSearch). Post-processing is already applied
     * to the result: learned corrections override aisle_id, unknown/"unsorted" aisle -> null,
     * invalid icon -> IconCatalog.DEFAULT_ICON, duplicates (vs context.existingNames and within
     * the answer) removed and reported in [IngestResult.skippedDuplicates].
     */
    suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult

    /** /audio/transcriptions with the transcription model. Empty text means "nothing heard" (not an error). */
    suspend fun transcribe(clip: AudioClip, keywords: List<String> = emptyList()): Transcript

    /**
     * Fast model, no tools. Returns item id -> aisle id (null = unsorted) for EVERY input id.
     * Corrections are applied client-side first (those items are not sent). Batches of <= 40 items.
     */
    suspend fun recategorize(
        items: List<NamedItem>,
        aisles: List<Aisle>,
        corrections: List<Correction>,
    ): Map<String, String?>

    /** Fast model, no tools. Returns a basket kind for every aisle id in [aisles]. */
    suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind>

    /** /images/edits with the image model and the style reference sheet. Returns the raw image (webp, 1024 px, alpha). */
    suspend fun drawIcon(request: IconDrawRequest): DrawnIcon

    /** GET /models/{smartModel}: verifies base URL + token + model access. */
    suspend fun testConnection(): ConnectionCheck
}

/** What the user gave us. */
sealed interface IngestInput {
    /** Typed or pasted text, including plain lists and recipe URLs. */
    data class Text(val text: String) : IngestInput

    /** A photo of a handwritten list: JPEG (long edge <= 1600 px, q~85) is what the app sends. */
    class Image(val bytes: ByteArray, val mimeType: String = "image/jpeg") : IngestInput

    /** A voice-note transcript (from [GroceryAssistant.transcribe]). */
    data class Transcript(val text: String) : IngestInput
}

/** Prompt context. Build it with AppState.ingestContext(listId). */
data class IngestContext(
    /** Aisles in walking order. */
    val aisles: List<Aisle>,
    /** Newest first, only corrections whose aisle exists. */
    val corrections: List<Correction>,
    /** Display names of the list's UNCHECKED items (for dedupe and the prompt). */
    val existingNames: List<String>,
)

/** One item the model proposed, after client-side post-processing. */
data class ProposedItem(
    val name: String,
    val quantity: String?,
    val note: String?,
    /** A current aisle id, or null for Unsorted. */
    val aisleId: String?,
    /** Always a valid catalog key. */
    val icon: String,
    val iconFit: IconFit,
    /** True when a learned correction decided the aisle. */
    val aisleFromCorrection: Boolean = false,
)

fun ProposedItem.toGroceryItem(id: String, now: Long, source: ItemSource): GroceryItem = GroceryItem(
    id = id,
    name = name,
    quantity = quantity,
    note = note,
    aisleId = aisleId,
    icon = icon,
    iconFit = iconFit,
    addedAt = now,
    source = source,
)

/** A web page the model searched or opened (show as a clickable "Sources" chip: required by OpenAI policy). */
data class WebSource(val url: String, val title: String? = null)

data class CallMeta(
    val model: String?,
    val requestId: String?,
    val latencyMs: Long,
    /** Billable web searches (tool_usage.web_search.num_requests). */
    val webSearches: Int = 0,
)

data class IngestResult(
    val items: List<ProposedItem>,
    /** Short user-facing sentence from the model, markdown links stripped. */
    val message: String,
    val sources: List<WebSource>,
    /** Display names the model returned that were already on the list (or repeated). */
    val skippedDuplicates: List<String>,
    val meta: CallMeta,
)

/** Audio as recorded on device: MPEG-4/AAC with an ".m4a" file name (the extension matters). */
class AudioClip(val bytes: ByteArray, val fileName: String = "recording.m4a")

data class Transcript(val text: String) {
    val isEmpty: Boolean get() = text.isBlank()
}

data class NamedItem(val id: String, val name: String)

class IconDrawRequest(
    val itemName: String,
    /** Optional extra description (e.g. the closest catalog icon's drawHint is NOT used: it would bias the drawing). */
    val hint: String? = null,
    /** The bundled style sheet (app: assets/icons/_style_reference.png). */
    val styleReference: ByteArray,
    val styleReferenceFileName: String = "_style_reference.png",
)

/** Raw image as returned by the API (webp, 1024x1024, transparent). The app downsizes to 192 px. */
class DrawnIcon(val bytes: ByteArray, val mimeType: String = "image/webp", val meta: CallMeta)

data class ConnectionCheck(val model: String, val meta: CallMeta)

/** assignBaskets with the offline heuristic as fallback: never throws LlmError. */
suspend fun GroceryAssistant.assignBasketsOrGuess(
    aisles: List<Aisle>,
    guess: (Aisle) -> BasketKind,
): Map<String, BasketKind> = try {
    val assigned = assignBaskets(aisles)
    aisles.associate { it.id to (assigned[it.id] ?: guess(it)) }
} catch (e: LlmError) {
    aisles.associate { it.id to guess(it) }
}

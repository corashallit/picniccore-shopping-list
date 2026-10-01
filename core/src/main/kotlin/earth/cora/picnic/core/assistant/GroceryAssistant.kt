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
     * One Responses API call to ModelDefaults.SMART with a strict JSON schema. Text and transcript
     * input may use web_search (the model decides; ModelDefaults.WEB_SEARCH). Post-processing is
     * already applied to the result: learned corrections override aisle_id, unknown/"unsorted"
     * aisle -> null, learned icon overrides replace icon (fit EXACT), "exact" on a *_generic key holds
     * only when the catalog lists the name for it, a model "approximate" becomes EXACT when IconCatalog.matchExact knows the name
     * (curated key, label or alias; no head-noun guess), an invalid icon ->
     * IconCatalog.DEFAULT_ICON + APPROXIMATE, duplicates (vs context.existingNames and within the
     * answer) removed and reported in [IngestResult.skippedDuplicates]. The model's icon/icon_fit
     * counts as step 2 of the icon cascade for these items. Blank input returns an empty result
     * without a request. Sources are capped at 8 (cited and opened pages first).
     */
    suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult

    /**
     * /audio/transcriptions with ModelDefaults.TRANSCRIBE. Empty text means "nothing heard" (not an error).
     * [AudioClip.fileName] must end in a format the API decodes by extension (m4a, mp4, mp3, wav, ogg,
     * webm, flac); anything else (.aac, .3gp, no extension) -> LlmError.BadResponse(code "unsupported_audio")
     * without a request.
     */
    suspend fun transcribe(clip: AudioClip, keywords: List<String> = emptyList()): Transcript

    /**
     * ModelDefaults.FAST, no tools. Returns item id -> aisle id (null = unsorted) for EVERY input id.
     * Corrections are applied client-side first (those items are not sent). Batches of <= 40 items.
     */
    suspend fun recategorize(
        items: List<NamedItem>,
        aisles: List<Aisle>,
        corrections: List<Correction>,
    ): Map<String, String?>

    /** ModelDefaults.FAST, no tools. Returns a basket kind for every aisle id in [aisles] (may omit some on a sloppy answer). */
    suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind>

    /**
     * Icon cascade step 3: /images/edits with ModelDefaults.IMAGE and the bundled style reference sheet.
     * Returns the raw image (webp, 1024 px, alpha); the app trims and downsizes it to 192 px.
     * Image moderation (moderation_blocked) -> LlmError.Refusal.
     */
    suspend fun drawIcon(request: IconDrawRequest): DrawnIcon

    /** GET /models/{ModelDefaults.SMART.id}: verifies base URL + token + model access in one free call. */
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
    /** Learned icon choices: normalizeName(item) -> catalog key (applied client-side, not sent). */
    val iconOverrides: Map<String, String> = emptyMap(),
    /**
     * The app's UI language, in English ("English", "French"). The model answers in the language the
     * user wrote in and falls back to this one when a request is too short to tell ("Ricardo's pouding
     * chômeur" alone). The app is English-only today; pass the UI language once it is localized.
     */
    val appLanguage: String = DEFAULT_APP_LANGUAGE,
) {
    companion object {
        const val DEFAULT_APP_LANGUAGE: String = "English"
    }
}

/** One item the model proposed, after client-side post-processing. */
data class ProposedItem(
    val name: String,
    val quantity: String?,
    val note: String?,
    /** A current aisle id, or null for Unsorted. */
    val aisleId: String?,
    /** Always a valid catalog key. */
    val icon: String,
    /** EXACT / APPROXIMATE from the model; OfflineParser may also produce PENDING. */
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
    /** Token usage of /responses calls (for logs and the developer CLI), when reported. */
    val usage: TokenUsage? = null,
)

/** `usage` of a /responses call. cachedTokens > 0 means the prompt-cache prefix was reused. */
data class TokenUsage(
    val inputTokens: Int,
    val cachedTokens: Int,
    val cacheWriteTokens: Int,
    val outputTokens: Int,
    val reasoningTokens: Int,
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
    /** Display name of the item ("Gochujang"); the prompt names it. */
    val itemName: String,
    /**
     * Optional extra description, e.g. the item's note. The closest catalog icon's drawHint is
     * deliberately NOT used: it would bias the drawing towards the wrong object.
     */
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

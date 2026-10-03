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
     * already applied to the result: quantities in metric (normalizeMetric), learned corrections override
     * aisle_id, unknown/"unsorted" aisle -> null, learned icon overrides replace icon (fit EXACT), "exact" on a
     * *_generic key holds only when the catalog lists the name for it, a model "approximate" becomes EXACT when
     * IconCatalog.matchExact knows the name (curated key, label or alias; no head-noun guess), an invalid icon ->
     * IconCatalog.DEFAULT_ICON + APPROXIMATE. Skipped rows (§4.17) stay in [IngestResult.items] with their
     * [ProposedItem.skipReason]: names already on the list (vs context.existingNames) come back as
     * ALREADY_ON_LIST and are also named in [IngestResult.skippedDuplicates], repeats within the answer are merged
     * (also named there), and the pantry backstop (PantryMatch) runs last. Commit only the rows she keeps
     * (skipReason NONE by default). Photos carry [IngestResult.lines]; recipe answers [IngestResult.recipe].
     * The model's icon/icon_fit counts as step 2 of the icon cascade for these items. Blank input returns an
     * empty result without a request. Sources are capped at 8 (cited and opened pages first).
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
     * Icon cascade step 3, live stickers v4: one /images/edits call with ModelDefaults.IMAGE, the neighbour
     * sheet as `image[]`, the mask and the v4 prompt (prompts/icon_prompt.txt with the subject and the
     * families' colours). Returns the raw 1024 px image with alpha; the app cuts the fourth cell out and
     * post-processes it into a 208 px sticker plus its cut path. Never retried here.
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
    /**
     * The pantry entry names as she wrote them, the first 200 in shelf order (§0.4 Q5, §6.2 rule 8): the context
     * block prints them ("In the pantry (she keeps these at home): …", "nothing yet" when empty) and the model
     * leaves out the worked-out items they cover (PantryMatch backs it up).
     */
    val pantry: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_APP_LANGUAGE: String = "English"
    }
}

/** Why a proposed row starts switched off (§4.17, §6.2). Wire values: none / already_on_list / optional / in_pantry. */
enum class SkipReason { NONE, ALREADY_ON_LIST, OPTIONAL, IN_PANTRY }

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
    /** Why the row starts switched off (the recipe card shows it as the reason); NONE = kept. */
    val skipReason: SkipReason = SkipReason.NONE,
    /** She named it (typed, photographed or spoken) versus the model worked it out; a named item is never skipped for the pantry. */
    val named: Boolean = true,
    /** The pantry entry that covers it ("spices" for cinnamon), when [skipReason] is IN_PANTRY. */
    val pantryEntry: String? = null,
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
    /** Photos only: the transcribed lines, revealed one by one after the answer (§4.19, D16); else empty. */
    val lines: List<PhotoLine> = emptyList(),
    /** The recipe the answer worked from, or null (§4.17). */
    val recipe: RecipeInfo? = null,
)

/** One line read off a photographed list (§4.19). [itemIndex] points into [IngestResult.items], or null. */
data class PhotoLine(val text: String, val crossedOut: Boolean, val correctionOf: String?, val itemIndex: Int?)

/** The recipe card's header facts (§4.17, §4.18): shown only from real fields in the answer (D16). */
data class RecipeInfo(val title: String, val recipesCompared: Int?, val servings: Int?)

/** Audio as recorded on device: MPEG-4/AAC with an ".m4a" file name (the extension matters). */
class AudioClip(val bytes: ByteArray, val fileName: String = "recording.m4a")

data class Transcript(val text: String) {
    val isEmpty: Boolean get() = text.isBlank()
}

data class NamedItem(val id: String, val name: String)

/**
 * One live sticker drawing, v4 (docs/DESIGN_STICKER.md §18.3): the model fills the empty fourth cell of a 2 x 2
 * sheet that already holds three finished library stickers, so it copies their line weight, size and gloss.
 */
class IconDrawRequest(
    /** Display name of the item ("Kimchi"); logged and used when [subject] is blank. */
    val itemName: String,
    /** The 1024 x 1024 PNG neighbour sheet on a transparent background (the app's SheetComposer). */
    val sheet: ByteArray,
    /** The bundled mask (app: assets/icons/mask.png): opaque everywhere except the empty bottom-right cell. */
    val mask: ByteArray,
    /**
     * What to draw, one line: the IconDecider's `look` ("one squat glass jar of kimchi with a round screw lid; …").
     * Blank: "one <itemName>, as it is sold in a Canadian grocery store".
     */
    val subject: String,
    /** The sticker families to draw in, body first ([StickerPalette.FAMILIES]); unknown names are dropped. */
    val families: List<String>,
)

/** Raw image as returned by the API (webp, 1024x1024, transparent). The app post-processes it (v4). */
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

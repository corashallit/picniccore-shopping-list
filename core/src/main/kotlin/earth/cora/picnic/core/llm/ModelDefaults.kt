package earth.cora.picnic.core.llm

/**
 * THE ONE PLACE where the app's models and their request details are chosen (client amendment A1).
 *
 * Not user-editable: there is no Settings field and no persisted override. To change a model,
 * maintainers edit this file and ship an APK. Values come from the live benchmark of 2026-10-01
 * (research/openai_api.md §0). Model-specific request rules (allowed efforts, prompt-cache fields)
 * live in ModelCapabilities (core-internal), which keys off the model id sent.
 */
object ModelDefaults {
    /** Ingest (text, photo, transcript): reasoning + web_search (text and transcript only) + strict JSON. */
    val SMART: ResponsesModel = ResponsesModel(id = "gpt-5.6-terra", effort = ReasoningEffort.LOW, maxOutputTokens = 12_000)

    /** recategorize, assignBaskets and the IconDecider: no tools, strict JSON, no reasoning. */
    val FAST: ResponsesModel = ResponsesModel(id = "gpt-6-luna", effort = ReasoningEffort.NONE, maxOutputTokens = 4_000)

    /** /audio/transcriptions (the gpt-4o-*-transcribe models shut down 2027-02-26). */
    val TRANSCRIBE: TranscriptionModel = TranscriptionModel(
        id = "gpt-transcribe",
        languages = listOf("en"),
        prompt = "A person dictating a grocery list in Ontario, Canada.",
        maxKeywords = 100,
    )

    /** Live icons via /images/edits. Real alpha; gpt-image-2 rejects background=transparent. ~9 s, ~US$0.015 each. */
    val IMAGE: ImageModel = ImageModel(
        id = "gpt-image-2.5-flare",
        quality = "low",
        size = "1024x1024",
        background = "transparent",
        outputFormat = "webp",
        outputCompression = 80,
    )

    /** web_search tool for text and transcript ingest (the model decides whether to search). */
    val WEB_SEARCH: WebSearchSpec = WebSearchSpec(
        enabled = true,
        contextSize = "low",
        country = "CA",
        region = "Ontario",
        timezone = "America/Toronto",
    )

    /** Everything above, as passed to OpenAiGroceryAssistant / ResponsesIconDecider. */
    val models: ModelSet = ModelSet(SMART, FAST, TRANSCRIBE, IMAGE, WEB_SEARCH)
}

/** A Responses API model and its fixed request settings. */
data class ResponsesModel(
    val id: String,
    val effort: ReasoningEffort,
    val maxOutputTokens: Int,
)

data class TranscriptionModel(
    val id: String,
    /** Sent as repeated languages[] (never together with `language`). */
    val languages: List<String>,
    val prompt: String?,
    /** keywords[] cap (1000 broke the multipart parser; 150 was fine). */
    val maxKeywords: Int,
)

data class ImageModel(
    val id: String,
    val quality: String,
    val size: String,
    val background: String,
    val outputFormat: String,
    val outputCompression: Int,
)

/** `{"type":"web_search","search_context_size":…,"user_location":{"type":"approximate",…}}`; country is ISO-3166. */
data class WebSearchSpec(
    val enabled: Boolean,
    val contextSize: String,
    val country: String,
    val region: String,
    val timezone: String,
)

/**
 * The full model set. Production code always uses [ModelDefaults.models]; only tests and the
 * developer CLI construct a different one (e.g. to compare prompts across models).
 */
data class ModelSet(
    val smart: ResponsesModel,
    val fast: ResponsesModel,
    val transcribe: TranscriptionModel,
    val image: ImageModel,
    val webSearch: WebSearchSpec,
)

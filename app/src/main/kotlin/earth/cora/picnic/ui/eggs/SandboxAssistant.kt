package earth.cora.picnic.ui.eggs

import earth.cora.picnic.core.assistant.AudioClip
import earth.cora.picnic.core.assistant.CallMeta
import earth.cora.picnic.core.assistant.ConnectionCheck
import earth.cora.picnic.core.assistant.DrawnIcon
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.assistant.IconDrawRequest
import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.assistant.OfflineParser
import earth.cora.picnic.core.assistant.Transcript
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction

/**
 * The Surprise lab's assistant (docs/DESIGN_STICKER.md §6.9: "no network call and no cost"): what the practice list's add
 * bar sends is read by the offline parser on the phone, as typed, and nothing ever leaves it. Photos, voice notes and
 * drawings are never asked of it (the practice add bar has no camera or microphone, and its stickers come from the
 * library). Owner: WP9.
 */
internal class SandboxAssistant(private val catalog: IconCatalog) : GroceryAssistant {
    override suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult {
        val text = (input as? IngestInput.Text)?.text ?: throw LlmError.NotConfigured("the practice list reads text only")
        return IngestResult(
            items = OfflineParser.parse(text, catalog, context),
            message = "",
            sources = emptyList(),
            skippedDuplicates = emptyList(),
            meta = CallMeta(model = null, requestId = null, latencyMs = 0),
        )
    }

    override suspend fun transcribe(clip: AudioClip, keywords: List<String>): Transcript =
        throw LlmError.NotConfigured("the practice list has no microphone")

    /** Nothing is sorted over the network: every item stays where the offline parse put it. */
    override suspend fun recategorize(items: List<NamedItem>, aisles: List<Aisle>, corrections: List<Correction>): Map<String, String?> =
        items.associate { it.id to null }

    override suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind> = emptyMap()

    override suspend fun drawIcon(request: IconDrawRequest): DrawnIcon = throw LlmError.NotConfigured("the practice list draws no stickers")

    override suspend fun testConnection(): ConnectionCheck = throw LlmError.NotConfigured("the practice list never connects")
}

package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.ConfigSource
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction

/**
 * The production GroceryAssistant. Builds requests with Prompts + Schemas (core.llm), sends them
 * with [client], parses with the Responses parsing rules (last non-commentary message, refusal,
 * status incomplete) and post-processes (corrections, unknown aisle, dedupe, icon sanitize).
 * See docs/ARCHITECTURE.md §7 and docs/PROMPTS.md.
 */
class OpenAiGroceryAssistant(
    private val client: OpenAiClient,
    private val config: ConfigSource,
    private val catalog: IconCatalog,
) : GroceryAssistant {
    override suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult =
        TODO("core: ingest")

    override suspend fun transcribe(clip: AudioClip, keywords: List<String>): Transcript =
        TODO("core: transcribe")

    override suspend fun recategorize(
        items: List<NamedItem>,
        aisles: List<Aisle>,
        corrections: List<Correction>,
    ): Map<String, String?> = TODO("core: recategorize")

    override suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind> =
        TODO("core: assignBaskets")

    override suspend fun drawIcon(request: IconDrawRequest): DrawnIcon = TODO("core: drawIcon")

    override suspend fun testConnection(): ConnectionCheck = TODO("core: testConnection")
}

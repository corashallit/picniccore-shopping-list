package earth.cora.picnic.core.cli

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmDefaults
import earth.cora.picnic.core.model.Aisles

/**
 * Manual prompt-work CLI (owned by core). Planned commands (docs/ARCHITECTURE.md §12):
 *   ingest "<text>" | photo <file.jpg> | transcribe <file.m4a> | recat "<item>"... |
 *   baskets [aisles.txt] | icon "<item name>" <out.webp> | test
 * Reads OPENAI_API_KEY (and optional PICNIC_BASE_URL, PICNIC_SMART_MODEL, ...) from the environment.
 * Usage: ./gradlew -p core run --args="ingest 'ingredients for pancakes from scratch'"
 */
fun main(args: Array<String>) {
    val catalog = IconCatalog.load()
    println("Picnic core CLI (skeleton). Catalog v${catalog.version}: ${catalog.keys.size} icons, ${catalog.file.baskets.size} baskets.")
    println("Default smart model: ${LlmDefaults.SMART_MODEL}; aisles: ${Aisles.parse(Aisles.DEFAULT_TEXT).joinToString { it.id }}")
    if (args.isNotEmpty()) println("Command '${args.first()}' is not implemented yet.")
}

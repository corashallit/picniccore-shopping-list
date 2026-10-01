package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Correction

/**
 * The "raw lines" fallback when the AI call fails (and for quick manual adds). No network.
 * CONTRACT:
 * - Split on newlines, commas and semicolons; trim; drop empty pieces and leading bullets.
 * - Leading or trailing quantity is pulled out: "2 lemons", "2x lemons", "lemons x2", "500 g flour",
 *   "1 L milk", "1.5 kg potatoes" -> quantity "2" / "500 g" / "1 L" / "1.5 kg", name capitalized.
 * - icon = catalog.match(name) (EXACT) or IconCatalog.DEFAULT_ICON (APPROXIMATE).
 * - aisleId = the matching correction's aisle, else null (Unsorted). note = null.
 * - Dedupe by normalizeName against [existingNames] and within the input.
 */
object OfflineParser {
    fun parse(
        text: String,
        catalog: IconCatalog,
        corrections: List<Correction>,
        existingNames: List<String>,
    ): List<ProposedItem> = TODO("core: OfflineParser.parse")
}

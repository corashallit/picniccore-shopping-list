package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.Quantities
import earth.cora.picnic.core.model.normalizeName

/**
 * Turns typed text into items with no network: the Add sheet's "Add as typed" action and the
 * raw-lines fallback when the AI call fails. CONTRACT:
 * - Split on newlines, commas and semicolons; trim; drop empty pieces and leading bullets.
 *   A comma between two digits ("1,5 kg") is a decimal comma, not a separator.
 * - A leading or trailing quantity is pulled out: "2 lemons", "2x lemons", "lemons x2", "500 g flour",
 *   "1 L milk", "1.5 kg potatoes" -> quantity "2" / "500 g" / "1 L" / "1.5 kg"; name capitalized.
 * - Icon, cascade step 1: context.iconOverrides[normalizeName] else catalog.match(name) -> (key, EXACT);
 *   no local match -> (IconCatalog.DEFAULT_ICON, PENDING) for IconRepository to decide (step 2).
 * - aisleId = the matching correction's aisle (aisleFromCorrection = true), else null (Unsorted;
 *   AisleSync.sortItems places it in the background). note = null.
 * - Dedupe by normalizeName against context.existingNames and within the input.
 */
object OfflineParser {
    // Newlines, semicolons, and commas that are not decimal commas ("1,5 kg").
    private val SEPARATORS = Regex("""\r?\n|;|(?<!\d),|,(?!\d)""")

    // "- ", "* ", "• ", "+ ", "[ ] ", "[x] ", "☐ ", and numbering "1. " / "2) " (which needs the space,
    // so "1.5 kg potatoes" keeps its quantity).
    private val BULLET = Regex("""^(?:(?:[-*•·+–—]|\[[ xX✓]?]|[☐☑✓✔□■])\s*|\d{1,2}[.)]\s+)""")
    private val TRAILING_PUNCTUATION = Regex("""[\s.!?:]+$""")

    fun parse(text: String, catalog: IconCatalog, context: IngestContext): List<ProposedItem> {
        val existing = context.existingNames.mapTo(HashSet()) { normalizeName(it) }
        val aisleIds = context.aisles.mapTo(HashSet()) { it.id }
        val corrections = HashMap<String, String>()
        // IngestContext.corrections is newest first: the first correction for a name wins.
        context.corrections.forEach { if (it.aisleId in aisleIds) corrections.putIfAbsent(it.name, it.aisleId) }

        val seen = HashSet<String>()
        val out = mutableListOf<ProposedItem>()
        for (piece in text.split(SEPARATORS)) {
            val cleaned = cleanPiece(piece) ?: continue
            val split = Quantities.split(cleaned)
            val name = capitalize(split.name.trim().removePrefix("of ").trim())
            val key = normalizeName(name)
            if (key.isEmpty() || key in existing || !seen.add(key)) continue

            val override = context.iconOverrides[key]?.takeIf { it in catalog }
            // The whole piece first: some aliases carry a size ("4l milk" -> milk_bag).
            val matched = override ?: (catalog.match(cleaned) ?: catalog.match(name))?.key
            val corrected = corrections[key]
            out += ProposedItem(
                name = name,
                quantity = split.quantity,
                note = null,
                aisleId = corrected,
                icon = matched ?: IconCatalog.DEFAULT_ICON,
                iconFit = if (matched != null) IconFit.EXACT else IconFit.PENDING,
                aisleFromCorrection = corrected != null,
            )
        }
        return out
    }

    private fun cleanPiece(piece: String): String? {
        var s = piece.trim()
        // Strip stacked bullets ("- [ ] milk").
        repeat(2) { s = s.replaceFirst(BULLET, "").trim() }
        s = s.replace(TRAILING_PUNCTUATION, "").replace(Regex("\\s+"), " ").trim()
        return s.takeIf { it.any { c -> c.isLetter() } }
    }

    private fun capitalize(name: String): String =
        name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

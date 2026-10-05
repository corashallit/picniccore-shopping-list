package earth.cora.picnic.core.model

/*
 * The aisle rotation by name (docs/DESIGN_STICKER.md §2.3): which sticker family, tab pattern and stamp
 * pictogram an aisle wears. Pure; the theme (WP4) maps the names to colours and drawables
 * (PatternTiles, DecorManifest.pictograms). Filed books (TripBook stamps) store these names, so they are
 * persisted: never rename one.
 */

/** One aisle's look: [family] (§2.2), tab [pattern] (§2.12) and stamp [pictogram] (§4.13). */
data class AisleLook(val family: String, val pattern: String, val pictogram: String)

object AisleRotation {
    /** Index i = (position - 1) mod 10 (§2.3). */
    val LOOKS: List<AisleLook> = listOf(
        AisleLook("strawberry", "gingham", "strawberry"),
        AisleLook("ramune", "polka", "bee"),
        AisleLook("lemon", "stripes", "star"),
        AisleLook("melon", "hearts", "heart"),
        AisleLook("grape", "stars", "cloud"),
        AisleLook("peach", "clouds", "flower"),
        AisleLook("mint", "check", "ladybug"),
        AisleLook("sakura", "waves", "teacup"),
        AisleLook("cocoa", "flowers", "sandwich"),
        AisleLook("periwinkle", "plain", "sun"),
    )

    /** "Everything else": the neutral family, no pattern, the capsule pictogram and no numeral (§2.3, §4.10). */
    val EVERYTHING_ELSE: AisleLook = AisleLook("neutral", "plain", "capsule")

    /** The rotation's look for a 1-based walking [position]; position 0 (or below) is Everything else. */
    fun forPosition(position: Int): AisleLook = if (position <= 0) EVERYTHING_ELSE else LOOKS[(position - 1) % LOOKS.size]

    /**
     * The look of [aisle] with the aisle editor's [style] applied: a family or pattern override replaces the
     * rotation's; the pictogram always follows the walking position ("a colour override keeps the pictogram of
     * its position", §2.3). Overrides naming nothing (blank) are ignored.
     */
    fun lookOf(aisle: Aisle, style: AisleStyle?): AisleLook {
        val base = forPosition(aisle.position)
        return base.copy(
            family = style?.family?.takeIf { it.isNotBlank() } ?: base.family,
            pattern = style?.pattern?.takeIf { it.isNotBlank() } ?: base.pattern,
        )
    }
}

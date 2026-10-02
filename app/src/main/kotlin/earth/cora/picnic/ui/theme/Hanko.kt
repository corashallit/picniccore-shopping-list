package earth.cora.picnic.ui.theme

/**
 * The two lines a hanko stamp can read (§4.5). Stamp lettering is art (Zen Maru 900, auto-fitted), not copy,
 * so it lives here rather than in strings. Owner: WP4 (HankoTest); phase-0 contract.
 */
enum class HankoText(val top: String, val bottom: String) {
    /** Rows (40 dp) and basket lids (34 dp). */
    GotIt("GOT", "IT!"),

    /** The celebration stamp card, the book's spine, the album's perfect-weeks card. */
    PerFect("PER-", "FECT!"),

    /** Egg 9: a birthday item's check-off stamp. */
    HappyBday("HAPPY", "B-DAY!"),

    /** Egg 13: the gashapon jackpot. */
    JackPot("JACK", "POT!"),
}

/** The stamp's seeded rotation (§4.5): −12° ± 4°, from the item id, stable across recompositions and restarts. */
fun hankoAngle(id: String): Float = -12f + (Math.floorMod(id.hashCode(), 9) - 4)

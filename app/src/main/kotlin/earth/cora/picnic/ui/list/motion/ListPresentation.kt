package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.Immutable
import earth.cora.picnic.ui.theme.LcdMode

/*
 * What the list shows NOW while chains play (docs/DESIGN_STICKER.md §8.7). Every map holds overrides only:
 * an absent key means "show the committed state as it is", so [ListPresentation.Identity] is a settled list.
 * Times (t0) are frame-clock millis (withFrameMillis) under FixedMotionScale (§8.8). Owner: WP6.
 * PHASE-0 CONTRACT: WP6 may add fields (with defaults) and phases; renames need a note to WP5 and WP9.
 */
@Immutable
data class ListPresentation(
    /** Item id -> its row's phase in the chain. */
    val rows: Map<String, RowPhase> = emptyMap(),
    /** Section id -> its basket. */
    val baskets: Map<String, BasketPresentation> = emptyMap(),
    /** Book slot -> its spot. */
    val spots: Map<Int, SpotPhase> = emptyMap(),
    /** Section id -> its stamp on the book's stamp page. */
    val stamps: Map<String, StampPhase> = emptyMap(),
    /** The LCD's shown count and mode while a chain lags the state; null = the state's. */
    val lcd: LcdPresentation? = null,
    /** The Next card, while it shows. */
    val nextCard: NextCardPresentation? = null,
    /** The celebration, while it shows. */
    val celebration: CelebrationPresentation? = null,
) {
    companion object {
        /** Nothing playing: the list shows the committed state. */
        val Identity: ListPresentation = ListPresentation()
    }
}

/** A row during the check-off chain (§8.3) and its reverse (§8.4). */
enum class RowPhase {
    Idle,

    /** The hanko slams (0–160 ms). */
    Slam,

    /** The sticker's corner lifts (200–320 ms). */
    Peeling,

    /** The sticker is hopping; the row shows its ghost. */
    Away,

    /** Un-check: the ghost waits for the sticker to hop back (360 ms). */
    Landing,
}

/** A basket's overrides while a chain plays (§5.3). */
@Immutable
data class BasketPresentation(
    /** The minis visible, as item ids in check-off order; null = the state's gotten items. */
    val minis: List<String>? = null,
    /** Bumped on each landing; the basket wobbles once per change. */
    val wobbleTick: Int = 0,
    /** Lid shut (true) or open (false); null = the state's (shut when the aisle is complete). */
    val lidClosed: Boolean? = null,
    /** Held for a peek (§5.3). */
    val peek: Boolean = false,
)

/** A book spot (§7.6). */
sealed interface SpotPhase {
    data object Empty : SpotPhase

    /** The next free spot (holo). */
    data object Next : SpotPhase

    /** The magic appear, started at [t0]. */
    data class Appearing(val t0: Long) : SpotPhase

    data object Shown : SpotPhase

    /** The peel off (un-check), started at [t0]. */
    data class Peeling(val t0: Long) : SpotPhase
}

/** A stamp circle (§4.13, §8.4). */
sealed interface StampPhase {
    data object Waiting : StampPhase

    /** The "now" ring. */
    data object Now : StampPhase

    /** The pictogram thunking in, started at [t0]. */
    data class Thunking(val t0: Long) : StampPhase

    data object Stamped : StampPhase
}

/** The LCD while a chain lags the state (it ticks at the landing, not at the tap). */
@Immutable
data class LcdPresentation(val shown: Int, val mode: LcdMode)

/** The Next card (§10). [holdMs] is the computed still time (readHold, doubled with TalkBack, recommended timeout). */
@Immutable
data class NextCardPresentation(val sectionId: String, val holdMs: Long, val t0: Long)

/** The celebration (§11), started at [t0] (frame clock). */
@Immutable
data class CelebrationPresentation(val listId: String, val t0: Long)

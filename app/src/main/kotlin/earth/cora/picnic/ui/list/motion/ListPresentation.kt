package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import earth.cora.picnic.core.catalog.Personality
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.LcdMode

/*
 * What the list shows NOW while chains play (docs/DESIGN_STICKER.md §8.7). Every map holds overrides only:
 * an absent key means "show the committed state as it is", so [ListPresentation.Identity] is a settled list.
 * Times (t0) are frame-clock millis (withFrameMillis) under FixedMotionScale (§8.8), on the engine's [MotionClock].
 * The presentation changes only at beats; the pieces that move read the clock in their draw or layer lambdas and
 * take their pose from the pure functions in Poses.kt, so a chain never recomposes the list every frame.
 * Owner: WP6. Fields may be added (with defaults); renames need a note to WP5 and WP9.
 *
 * How WP5 reads it (each list piece, all optional overrides on the committed state):
 * - row: [rows] phase + [rowStarts] t0 → hanko slam / lift ([HankoMotion]), peel ([PeelMotion]); Away = the ghost shows.
 * - basket: [baskets] → hide [BasketPresentation.hiddenMinis] (not landed yet), badge and strip meta = state.toGet +
 *   [BasketPresentation.toGetLag], the landing squash, wobble, rim heart, lid, lid hanko and hop ([BasketMotion]).
 * - book: [spots] and [stamps] by slot / section, [bookFx] (the held-back holo spot, page turn, lift, full glint, PERFECT).
 * - LCD: state.checked − [lcdLag]; [lcdWiggleT0] the "ready" wiggle.
 * - aisles: [open] (which section is open while a chain holds the state back), [aisles] (curl, fold, ribbon), [scroll].
 * - overlay: [flights], [bursts], [nextCard] (drawn by FlightLayer / NextStopCard), [celebration] (CelebrationHost).
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
    /** The LCD's shown count and mode while a chain lags the state; null = the state's (see [lcdLag]). */
    val lcd: LcdPresentation? = null,
    /** The Next card, while it shows. */
    val nextCard: NextCardPresentation? = null,
    /** The celebration, while it shows. */
    val celebration: CelebrationPresentation? = null,
    /** Item id -> when its row's current chain started (the tap), for the row's poses. */
    val rowStarts: Map<String, Long> = emptyMap(),
    /** Sticker copies travelling on the overlay: only ever a row's sticker and its own basket (R5). */
    val flights: List<Flight> = emptyList(),
    /** Transient bursts drawn on the overlay (the hanko's 8 stickers at the checkbox). */
    val bursts: List<Burst> = emptyList(),
    /** Section id -> aisle-level overrides: the curl, the fold, the ribbon. */
    val aisles: Map<String, AislePresentation> = emptyMap(),
    /** Which section is open while a chain holds the committed state back; null = the state's open section. */
    val open: OpenOverride? = null,
    /** A programmatic scroll for the list to run once (the next stop opening behind the Next card, §10). */
    val scroll: ScrollRequest? = null,
    /** Book-level effects (§7.6). */
    val bookFx: BookFx = BookFx(),
    /** Check-offs whose stickers have not landed yet: the LCD shows the state's checked count minus this (§8.3). */
    val lcdLag: Int = 0,
    /** The LCD's one "ready" wiggle after the finale (a hint for egg 18, eggs on), started at this time. */
    val lcdWiggleT0: Long? = null,
) {
    /** What WP5's StickerBook takes (§19.2). */
    val book: BookPresentation get() = BookPresentation(spots, stamps, bookFx)

    /** The LCD count to show for a state with [checked] items checked. */
    fun lcdCount(checked: Int): Int = (lcd?.shown ?: (checked - lcdLag)).coerceAtLeast(0)

    /** The section that shows open (its tray) now, given the state's open section [stateOpen]. */
    fun openSection(stateOpen: String?): String? = if (open != null) open.sectionId else stateOpen

    companion object {
        /** Nothing playing: the list shows the committed state. */
        val Identity: ListPresentation = ListPresentation()
    }
}

/** A row during the check-off chain (§8.3) and its reverse (§8.4). */
enum class RowPhase {
    Idle,

    /** The hanko slams (0–160 ms) and its ink spreads (160–330 ms). Reduced motion: the hanko fades in (120 ms). */
    Slam,

    /** The sticker's corner lifts (200–320 ms). */
    Peeling,

    /** The sticker is hopping; the row shows its ghost. */
    Away,

    /** Un-check: the hanko lifts (160 ms) and the ghost waits for the sticker to hop back (360 ms). */
    Landing,

    /**
     * Un-check before the sticker hopped (an accidental double tap, §8.3 "rapid taps"): the sticker never left the row,
     * so it stays; only the hanko lifts (160 ms).
     */
    Lifting,
}

/** A basket's overrides while a chain plays (§5.3, §8.3, §8.4). */
@Immutable
data class BasketPresentation(
    /** The minis visible, as item ids in check-off order; null = the state's gotten items minus [hiddenMinis]. */
    val minis: List<String>? = null,
    /** Bumped on each landing; the basket wobbles once per change. */
    val wobbleTick: Int = 0,
    /** Lid shut (true) or open (false); null = the state's (shut when the aisle is complete). */
    val lidClosed: Boolean? = null,
    /** Held for a peek (§5.3). */
    val peek: Boolean = false,
    /** Gotten items still in flight (or waiting to land): not in the basket yet. */
    val hiddenMinis: Set<String> = emptySet(),
    /** Add to the state's to-get count for the badge and the strip meta (they tick at the landing, not the tap). */
    val toGetLag: Int = 0,
    /** The mini that just landed, squashing (2 frames, 160 ms). */
    val squashItem: String? = null,
    val squashT0: Long? = null,
    /** The landing wobble (−6 / +5 / −2 / 0°, 4 steps, 360 ms); also on an un-check and the ambient wobble. */
    val wobbleT0: Long? = null,
    /** The 14 dp sakura heart rising off the rim (4 steps, 600 ms). */
    val heartT0: Long? = null,
    /** When the lid started to shut ([lidClosed] true) or re-open ([lidClosed] false): 2 frames, 160 ms. */
    val lidT0: Long? = null,
    /** The 34 dp GOT IT slamming onto the lid (2 steps, 200 ms), while it slams. */
    val lidHankoT0: Long? = null,
    /** The lid is shut but its GOT IT is not pressed yet (140 ms after the lid). */
    val lidHankoHidden: Boolean = false,
    /** The GOT IT lifting off the lid (un-check in a done aisle). */
    val lidHankoLiftT0: Long? = null,
    /** The basket's hop as its lid shuts (−5 dp squash, 3 steps, 360 ms). */
    val hopT0: Long? = null,
    /** The minis are hidden under the shut lid (after it landed, 240 ms); null = the state's. */
    val minisHidden: Boolean? = null,
    /** The badge pops (0 → 1.4 → 1, 3 steps, 300 ms) when its count changes at a landing or an un-check. */
    val badgePopT0: Long? = null,
    /** Fades in place instead of movement (a ListChange, or reduced motion; §8.4, §8.8). */
    val inPlace: Boolean = false,
)

/** A book spot (§7.6). */
sealed interface SpotPhase {
    data object Empty : SpotPhase

    /** The next free spot (holo). */
    data object Next : SpotPhase

    /** The magic appear, started at [t0]. */
    data class Appearing(val t0: Long) : SpotPhase

    data object Shown : SpotPhase

    /** The peel off (un-check), started at [t0]; [icon] is the twin peeling (the state no longer has it in this spot). */
    data class Peeling(val t0: Long, val icon: IconRef? = null, val holo: Boolean = false) : SpotPhase
}

/** A stamp circle (§4.13, §8.4). */
sealed interface StampPhase {
    data object Waiting : StampPhase

    /** The "now" ring. */
    data object Now : StampPhase

    /**
     * The pictogram thunking in, started at [t0]; [inPlace] (a ListChange or reduced motion, §8.4, §8.8): a 120 ms fade
     * in place instead, with no burst.
     */
    data class Thunking(val t0: Long, val inPlace: Boolean = false) : StampPhase

    data object Stamped : StampPhase
}

/** The LCD while a chain lags the state (it ticks at the landing, not at the tap). */
@Immutable
data class LcdPresentation(val shown: Int, val mode: LcdMode)

/**
 * The Next card (§10). [holdMs] is the computed still time (readHold, doubled with TalkBack, recommended timeout). It
 * rises at [t0] (400 ms, or a 120 ms fade under reduced motion), holds still from [settledAt] for [holdMs], then exits
 * at [exitT0] (300 ms, or a 120 ms fade).
 */
@Immutable
data class NextCardPresentation(
    val sectionId: String,
    val holdMs: Long,
    val t0: Long,
    /** The stop the card names: number circle, display name, store chip, colours and pattern. */
    val stop: MotionSection? = null,
    val settledAt: Long = t0,
    /** When it starts to leave (scheduled at settle + hold; earlier on a tap). */
    val exitT0: Long? = null,
    val reduced: Boolean = false,
)

/** The celebration (§11), started at [t0] (frame clock). */
@Immutable
data class CelebrationPresentation(
    val listId: String,
    val t0: Long,
    /** What the scene shows: the trip's real name, stats, stops, riders and bonus. */
    val content: CelebrationContent? = null,
    /** The beat times computed from the real words (§11.2). */
    val plan: CelebrationPlan? = null,
    /** Where the heart iris opens, in window px: the last tapped checkbox's centre; null = the screen centre. */
    val origin: Offset? = null,
    /** Reduced motion: only the summary frame, faded in over 120 ms, held max(6000, readHold) (§11.4). */
    val reduced: Boolean = false,
    /** TalkBack: no countdown and no auto-close; one node "Celebration. Double-tap to close" (§11.3, §0.4 Q7). */
    val talkBack: Boolean = false,
    /** The summary was tapped: the progress bar is gone and a Done button closes it (§0.4 Q7). */
    val keptAt: Long? = null,
    /**
     * How long the summary waits before it closes by itself: its reading hold passed through Android's "Time to take
     * action" setting (it carries an action, "tap to keep it", §9). The progress bar fills over exactly this. 0 = the
     * plan's hold (no engine, previews).
     */
    val summaryHoldMs: Long = 0,
    /** The overlay fades (200 ms) from here, then it is gone. */
    val closingT0: Long? = null,
)

/** One section the engine knows by id: what the Next card shows and what TalkBack hears (§10, §15). */
@Immutable
data class MotionSection(
    val id: String,
    /** Walking position (the number circle); 0 = "Everything else" (capsule, no numeral). */
    val position: Int,
    /** The tab's name (store prefix stripped). */
    val displayName: String,
    /** The full aisle name, for TalkBack. */
    val fullName: String = displayName,
    /** The store's own aisle number (the "A3" chip), or null. */
    val storeAisle: Int? = null,
    val look: AisleLook,
)

/**
 * A sticker copy on the overlay (§8.3, §8.7): from a row's sticker ([fromKey] `row:<id>`) to its basket's mouth
 * ([toKey] `basket:<section>`), or back. Window bounds are read live from the AnchorRegistry each frame, falling back to
 * [from] / [to] as captured when the hop started.
 */
@Immutable
data class Flight(
    val itemId: String,
    val sectionId: String,
    val fromKey: String,
    val toKey: String,
    val from: Rect,
    val to: Rect,
    /** The basket mouth (window px) the hop aims at (the start, for a hop back). */
    val mouth: Offset,
    val t0: Long,
    val durationMs: Long,
    val personality: Personality,
    /** An un-check's hop from the basket back to the row (340 ms, reverse arc). */
    val back: Boolean,
    val icon: IconRef?,
    val holo: Boolean = false,
    val aisleIndex: Int = -1,
    /** A reversed flight starts where the cancelled one was (window px centre and scale); null = from its anchor. */
    val startAt: Offset? = null,
    val startScale: Float? = null,
)

/** A transient burst on the overlay (§8.3): the hanko's 8 stickers round the checkbox. */
@Immutable
data class Burst(val id: String, val center: Offset, val t0: Long, val seed: Int)

/** Aisle-level overrides (§8.4). */
@Immutable
data class AislePresentation(
    /** The tray curls away like a page (4 frames, 300 ms). */
    val curlT0: Long? = null,
    /** The tray folds into its strip (240 ms `layout`), or a strip unfolds into its tray. */
    val foldT0: Long? = null,
    /** The finished strip's ribbon: tied (true) or untied (false); null = the state's (tied when complete). */
    val ribbon: Boolean? = null,
    /** The ribbon ties down (scaleY 0 → 1, 3 steps, 300 ms). */
    val ribbonT0: Long? = null,
    /** The bow pops (300 ms). */
    val bowT0: Long? = null,
    /** The ribbon unties (un-check in a done aisle). */
    val untieT0: Long? = null,
    /** Fades in place instead of movement. */
    val inPlace: Boolean = false,
)

/** Which section is open while a chain holds the state back: [sectionId], or none at all (null). */
@Immutable
data class OpenOverride(val sectionId: String?)

/** A programmatic scroll to [sectionId] (smooth), requested at [t0]; the list runs each request once. */
@Immutable
data class ScrollRequest(val sectionId: String, val t0: Long)

/** The book turning to [toPage] before a twin appears there (3 frames, 240 ms, §7.6). */
@Immutable
data class PageTurn(val toPage: Int, val t0: Long)

/** Book-level effects (§7.6). */
@Immutable
data class BookFx(
    /** The spot that stays the holo "next" spot while its twin waits to appear; null = the state's lowest free spot. */
    val holdNext: Int? = null,
    /** A page turn in progress. */
    val pageTurn: PageTurn? = null,
    /** The page the book shows while a twin appears or peels there (it returns to the shown page by itself later). */
    val showPage: Int? = null,
    /** The right page lifts 2 dp (2 steps, 160 ms) with each magic appear. */
    val liftT0: Long? = null,
    /** The book is full in the state, but its holo fore-edge and paper wait for the last twin. */
    val fullHeld: Boolean = false,
    /** One holo glint across the release paper (12 steps, 1.2 s), 360 ms after the last twin. */
    val fullGlintT0: Long? = null,
    /** PERFECT is due in the state, but waits for the celebration to close (+450 ms). */
    val perfectHeld: Boolean = false,
    /** The stamp page turns holo and PER-FECT! slams across the spine (3 steps, 260 ms) with an 8-sticker burst. */
    val perfectT0: Long? = null,
)

/** What WP5's `StickerBook(state, presentation)` takes (§19.2). */
@Immutable
data class BookPresentation(
    val spots: Map<Int, SpotPhase> = emptyMap(),
    val stamps: Map<String, StampPhase> = emptyMap(),
    val fx: BookFx = BookFx(),
) {
    companion object {
        val Identity: BookPresentation = BookPresentation()
    }
}

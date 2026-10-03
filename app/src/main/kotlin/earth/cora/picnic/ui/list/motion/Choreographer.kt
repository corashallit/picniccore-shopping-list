package earth.cora.picnic.ui.list.motion

import earth.cora.picnic.core.catalog.Personality
import earth.cora.picnic.core.model.IconRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The choreography engine's interface (docs/DESIGN_STICKER.md §8.3, §8.4, §19.2). The store is updated
 * first; everything the Choreographer does is presentation ([ListPresentation]), so state is never late and
 * never lost if the screen leaves mid-chain. Owner: WP6. Consumers: WP5 (sends events, reads the presentation),
 * WP9 (overlays, events). [MotionChoreographer] is the engine; [IdentityChoreographer] shows the state as it is.
 *
 * WP6 additions to the phase-0 contract (all with defaults, so phase-0 call sites still compile):
 * - [ToggleEvent]: `icon`, `holo`, `aisleIndex` (what the hop's overlay copy draws), `aisleWasOpen` (the folded-aisle
 *   form of §8.4), `finaleShown` (a re-completion only re-presses the book's PERFECT, §7.1).
 * - [ListChange]: `openSectionId`, `finaleShown`, `finaleStale` (§7.1: owed but older than 30 min).
 * - [Choreographer.keepCelebration] (§0.4 Q7: tapping the summary keeps it open).
 * - [Choreographer.onUserOpen] (§6.1: her own tap on a strip or chevron always wins over an aisle-done chain).
 * - [ToggleEvent.newBook] and [ChangeCause.NEW_BOOK_UNDO] with [ListChange.slot] (§0.4 Q9, §7.6: the automatic new
 *   sticker book and its Undo).
 */
interface Choreographer {
    /** What the list shows right now, per row, basket, book spot and stamp, plus the LCD, Next card and celebration. */
    val presentation: StateFlow<ListPresentation>

    /** A check-off tap (or its TalkBack, keyboard or swipe equivalent), AFTER the store update (§8.3). */
    fun onToggle(event: ToggleEvent)

    /** Any other change that completes or re-opens an aisle or the trip: plays the in-place form (§8.4). */
    fun onChange(change: ListChange)

    /** A tap on the Next card: it exits at once (§10). */
    fun skipNextCard()

    /** The celebration closes (a tap, its summary hold ending, or Back; §11.3, §0.4 Q7). */
    fun closeCelebration()

    /** A tap on the celebration's summary: the auto-close stops, the progress bar goes and a Done button shows (§0.4 Q7). */
    fun keepCelebration() = Unit

    /**
     * She opened [sectionId] herself (a tap on its strip), or folded everything (null: a tap on the open tray's chevron),
     * AFTER the store update. Her choice wins at once over any aisle-done chain (§6.1 "Tapping a folded strip opens it
     * and folds the others"): the chain stops holding its finished tray open (and stops holding everything folded until
     * the Next card), its pending scroll is dropped, and a Next card (shown or waiting) naming another aisle leaves. WP5
     * calls it from the strip and chevron click handlers; programmatic opens (the next stop, D2) never call it.
     */
    fun onUserOpen(sectionId: String?) = Unit
}

/** One check-off or un-check, as the ViewModel saw it in the committed state (WP5 builds it). */
data class ToggleEvent(
    val itemId: String,
    val sectionId: String,
    /** The new state: true = checked (the chain), false = un-checked (the reverse). */
    val checked: Boolean,
    /** The book spot the twin appears in (or peels out of); null when it has none. */
    val slot: Int?,
    /** This toggle finished its aisle (or, un-checking, re-opened it). */
    val aisleCompleted: Boolean,
    /** This toggle finished the trip (or, un-checking, re-opened it). */
    val tripCompleted: Boolean,
    /** The stop that opens next (behind the Next card), or null. */
    val nextSectionId: String?,
    /** The hop's personality (§8.3). */
    val personality: Personality,
    /** The item's sticker, drawn by the hop's overlay copy (null draws the capsule). */
    val icon: IconRef? = null,
    /** A shiny or jackpot sticker stays holo in flight too. */
    val holo: Boolean = false,
    /** The capsule's aisle tint (0-based rotation index, −1 = neutral), as ItemIcon takes it. */
    val aisleIndex: Int = -1,
    /** The item's aisle was the open tray at the tap; false plays the folded-aisle form of §8.4 (no curl or fold). */
    val aisleWasOpen: Boolean = true,
    /** The trip's finale was already shown (Trip.finaleShownAt != null): a re-completion re-presses PERFECT only (§7.1). */
    val finaleShown: Boolean = false,
    /**
     * This check-off started a new sticker book on its own (§0.4 Q9: the first check-off more than 12 h after the trip's
     * last activity): the old book was filed and the item lands in the fresh book. The book turns its page (3 frames,
     * 240 ms) to the fresh page at the tap, so the old book's twins leave with the turn; the twin then magic-appears in
     * the fresh book at its usual beat (§7.6 "New trip"). Under reduced motion the book simply shows the fresh page.
     */
    val newBook: Boolean = false,
)

/** What changed between two successive GroupedLists without a check-off tap (§8.4; WP5 compares them). */
data class ListChange(
    val aisleCompleted: Set<String>,
    val aisleReopened: Set<String>,
    val tripCompleted: Boolean,
    val tripReopened: Boolean,
    val nextSectionId: String?,
    val cause: ChangeCause,
    /** The section that was open before the change: a completed aisle that was open folds at L + 1100. */
    val openSectionId: String? = null,
    /** The trip's finale was already shown: a completion re-presses the book's PERFECT without the scene (§7.1). */
    val finaleShown: Boolean = false,
    /**
     * RESUME only: the finale is owed but the trip completed more than 30 min ago (TripRules.finaleIsStale): it is
     * marked shown and PERFECT is pressed in place, no scene (§7.1, item 154).
     */
    val finaleStale: Boolean = false,
    /**
     * [ChangeCause.NEW_BOOK_UNDO] only: the book spot the tapped item takes again in the restored old book. The book
     * turns back to the old book's page (240 ms) and the twin fades into this spot (120 ms) once the turn has shown it.
     */
    val slot: Int? = null,
)

/**
 * Why a [ListChange] happened. [NEW_BOOK_UNDO]: Undo on the "New sticker book for today" toast (§0.4 Q9) restored the
 * old book and checked the tapped item into it: the old book shows again with a page turn back. Its aisles come back as
 * they were (done ones stamped, lids shut), so WP5 sends it with empty aisle sets and no trip flags, and [ListChange.slot].
 */
enum class ChangeCause { DELETE, MOVE, SYNC, AISLES_EDIT, UNDO, ADD, RESUME, NEW_BOOK_UNDO }

/**
 * No choreography: the list shows the state as it is ([ListPresentation.Identity]). What WP5 uses until
 * WP6's engine lands, and a fine stand-in for screenshot tests of settled states.
 */
class IdentityChoreographer : Choreographer {
    private val state = MutableStateFlow(ListPresentation.Identity)

    override val presentation: StateFlow<ListPresentation> = state.asStateFlow()

    override fun onToggle(event: ToggleEvent) = Unit

    override fun onChange(change: ListChange) = Unit

    override fun skipNextCard() = Unit

    override fun closeCelebration() = Unit
}

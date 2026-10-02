package earth.cora.picnic.ui.list.motion

import earth.cora.picnic.core.catalog.Personality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The choreography engine's interface (docs/DESIGN_STICKER.md §8.3, §8.4, §19.2). The store is updated
 * first; everything the Choreographer does is presentation ([ListPresentation]), so state is never late and
 * never lost if the screen leaves mid-chain. Owner: WP6. PHASE-0 CONTRACT; WP5 builds against
 * [IdentityChoreographer] until WP6's engine lands. Consumers: WP5 (sends events, reads the presentation),
 * WP9 (overlays, events).
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
)

/** What changed between two successive GroupedLists without a check-off tap (§8.4; WP5 compares them). */
data class ListChange(
    val aisleCompleted: Set<String>,
    val aisleReopened: Set<String>,
    val tripCompleted: Boolean,
    val tripReopened: Boolean,
    val nextSectionId: String?,
    val cause: ChangeCause,
)

enum class ChangeCause { DELETE, MOVE, SYNC, AISLES_EDIT, UNDO, ADD, RESUME }

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

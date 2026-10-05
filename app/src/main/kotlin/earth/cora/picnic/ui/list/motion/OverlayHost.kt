package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.ui.theme.NextStopCard
import earth.cora.picnic.ui.theme.ToastHost
import earth.cora.picnic.ui.theme.ToastHostState

/*
 * The overlay host (docs/DESIGN_STICKER.md §6.1 item 4, §8.7, §4.15, §10): above the list and the add bar, below sheets.
 * It draws the flights and bursts ([FlightLayer]), mounts the egg overlays (WP9's slot), and stacks the sticky-note
 * toast and the Next card so they never overlap: whichever arrives second sits 8 dp above the first, and keeps that
 * place while it holds (text never moves during its hold, §9). Owner: WP6.
 */

/** The Next card's test tag: its button node, inside its motion layer, so its bounds are where it is drawn. */
const val NEXT_CARD_TAG: String = "picnic_next_card"

/** The Next card's slot: where it is laid out (its resting place), before its motion. */
const val NEXT_CARD_SLOT_TAG: String = "picnic_next_card_slot"

/** The toast's slot: the whole overlay, or, under a stacked card, the part below it that the toast is clipped to. */
const val TOAST_SLOT_TAG: String = "picnic_toast_slot"

/** The overlay's test tag. */
const val OVERLAY_TAG: String = "picnic_motion_overlay"

/**
 * Hosts [engine]'s overlay pieces over the list: flights and bursts, [eggs] (WP9), the toasts of [toasts] and the Next
 * card, both 20 dp above [bottomInset] (the add bar, the FAB or the IME).
 */
@Composable
fun MotionOverlay(
    engine: MotionChoreographer,
    presentation: ListPresentation,
    toasts: ToastHostState,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 84.dp,
    eggs: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    // A toast's hold waits while the celebration covers it, then restarts in full: an Undo is never cut short, not even
    // by a scene she never saw it under (§4.15, §9).
    val covered = presentation.celebration != null
    SideEffect { toasts.paused = covered }
    // The toasts may outlive this screen (a host higher up): never leave them paused.
    DisposableEffect(toasts) { onDispose { toasts.paused = false } }
    val card = presentation.nextCard
    val toastId = toasts.current?.id
    val stack = remember { StackState() }
    // Arrival order: the second one sits 8 dp above the first, frozen while it shows.
    if (toastId != stack.toastId) {
        stack.toastId = toastId
        // Over the card wherever it sits (it may itself be lifted over an earlier toast).
        stack.toastLiftPx = if (toastId != null && stack.cardT0 != null) stack.cardLiftPx + stack.cardHeightPx + with(density) { 8.dp.roundToPx() } else 0
    }
    val cardT0 = card?.t0
    if (cardT0 != stack.cardT0) {
        stack.cardT0 = cardT0
        stack.cardLiftPx = if (cardT0 != null && stack.toastId != null) stack.toastNoteHeightPx + with(density) { 8.dp.roundToPx() } else 0
    }
    val toastLift = with(density) { stack.toastLiftPx.toDp() }
    val cardLift = with(density) { stack.cardLiftPx.toDp() }
    // The card came second and sits over a toast: it enters and leaves in place (a 120 ms fade), so it never
    // sweeps through the toast below it, and the toast's lift-off is clipped 3 dp under the card's face (§4.15, §9).
    val stacked = card != null && stack.cardLiftPx > 0
    // Only a toast below the card is clipped; a later toast that arrives over the card is not.
    val clipToast = stacked && stack.toastLiftPx == 0
    val cardFaceBottom = bottomInset + 20.dp + cardLift
    Box(modifier.fillMaxSize().testTag(OVERLAY_TAG)) {
        FlightLayer(engine, presentation)
        eggs()
        // The toast host, measured at its own height so the note's height is known for stacking. Under a stacked card
        // its slot ends 3 dp under the card's face and clips: the toast's lift-off never draws over the card.
        Layout(
            content = { ToastHost(toasts, bottomInset = bottomInset + toastLift, bottomGap = 20.dp) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .testTag(TOAST_SLOT_TAG)
                .then(if (clipToast) Modifier.fillMaxWidth().height(cardFaceBottom - STACK_CLIP).clipToBounds() else Modifier.fillMaxSize()),
        ) { m, c ->
            val placeable = m.first().measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val pads = (bottomInset + toastLift + 20.dp).roundToPx()
            if (toasts.current != null) stack.toastNoteHeightPx = (placeable.height - pads).coerceAtLeast(0)
            layout(c.maxWidth, c.maxHeight) { placeable.place(0, c.maxHeight - placeable.height) }
        }
        if (card != null) {
            NextCardHost(
                card,
                engine.clock,
                onSkip = { engine.skipNextCard() },
                stacked = stacked,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // The card face sits 20 dp above the inset; its box carries 8 dp of room for the sparkles.
                    .padding(bottom = bottomInset + 20.dp - CARD_ROOM + cardLift)
                    .onSizeChanged { stack.cardHeightPx = it.height - with(density) { (CARD_ROOM * 2).roundToPx() } },
            )
        }
    }
}

/**
 * How far under the stacked card's face the toast below it is clipped: past the card's tilted corner (about 2 dp at
 * −1°), short of the toast's own tilted top (it rests 8 dp below, its corners up to 4 dp higher at −2°).
 */
private val STACK_CLIP: Dp = 3.dp

/** The Next card box's room round the face (corner sparkles, shadow). */
private val CARD_ROOM: Dp = 8.dp

private class StackState {
    var toastId: Long? = null
    var cardT0: Long? = null
    var toastLiftPx: Int = 0
    var cardLiftPx: Int = 0
    var toastNoteHeightPx: Int = 0
    var cardHeightPx: Int = 0
}

/**
 * The Next card in motion (§10): it rises from below (translateY(100 % + 130 dp) at 4° → −8 dp at −1.5° → rest at −1°,
 * 400 ms), holds still for its computed hold, exits downward (300 ms); under reduced motion it fades in and out (120 ms)
 * and holds the same time. [stacked] over a toast, it fades in and out in place too, so its path never crosses the
 * toast (§4.15). A tap anywhere on it skips it. TalkBack: one button "Next: aisle 5, Breakfast",
 * "Double-tap to dismiss"; the card never steals focus.
 */
@Composable
fun NextCardHost(card: NextCardPresentation, clock: MotionClock, onSkip: () -> Unit, modifier: Modifier = Modifier, stacked: Boolean = false) {
    val stop = card.stop ?: return
    val spoken = nextStopSpoken(stop)
    val dismiss = stringResource(R.string.motion_next_dismiss)
    val probe = LocalMotionProbe.current
    val px = LocalDensity.current.density
    var bounds by remember { mutableStateOf<Rect?>(null) }
    if (probe != null) {
        // Tests only: read the clock in composition so every frame reports the card's pose (the harness also samples
        // where the card is really drawn, from its button node's bounds).
        val now = clock.now
        val b = bounds
        SideEffect {
            if (b != null) {
                val pose = nextCardPose(card, now, stacked)
                probe.text(NEXT_CARD_TAG, now, b, dy = pose.dyFraction * b.height + pose.dyDp * px, rotation = pose.rotation, alpha = pose.alpha)
            }
        }
    }
    // The slot is where the card rests; the card moves inside it. Its button node, its tap target and its test tag
    // sit inside the motion layer, so they follow the card as it rises and leaves: a tap where it is not is the list's.
    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .testTag(NEXT_CARD_SLOT_TAG),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val pose = nextCardPose(card, clock.now, stacked)
                    translationY = pose.dyFraction * size.height + pose.dyDp * density
                    rotationZ = pose.rotation
                    alpha = pose.alpha
                }
                .testTag(NEXT_CARD_TAG)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.Button
                    onClick(label = dismiss) {
                        onSkip()
                        true
                    }
                }
                .pointerInput(card.t0) { detectTapGestures { onSkip() } },
        ) {
            NextStopCard(
                number = stop.position.takeIf { it > 0 },
                displayName = stop.displayName,
                storeAisle = stop.storeAisle,
                look = stop.look,
                nextLabel = stringResource(R.string.motion_next),
            )
        }
    }
}

/** The Next card's pose at [now]: translateY as (a fraction of its height, dp), rotation and alpha. */
internal data class CardPose(val dyFraction: Float, val dyDp: Float, val rotation: Float, val alpha: Float)

internal fun nextCardPose(card: NextCardPresentation, now: Long, stacked: Boolean = false): CardPose {
    val exit = card.exitT0
    if (card.reduced || stacked) {
        val leaving = exit != null && now >= exit
        val a = if (leaving) fadeOut(now - exit!!) else fadeIn(now - card.t0)
        // Stacked over a toast: a fade in place (NextCardMotion.STACKED_SHIFT_DP), never reaching the toast.
        val shift = if (stacked && !card.reduced) NextCardMotion.STACKED_SHIFT_DP * (1f - a) else 0f
        return CardPose(0f, shift, NextCardMotion.REST_ROTATION, a)
    }
    return if (exit != null && now >= exit) {
        val (f, d, r) = NextCardMotion.exit(now - exit)
        CardPose(f, d, r, 1f)
    } else {
        val (f, d, r) = NextCardMotion.rise(now - card.t0)
        CardPose(f, d, r, if (now < card.t0) 0f else 1f)
    }
}

/** What TalkBack hears for [stop] (§10, §15): "Next: aisle 5, Breakfast" (+ ", store aisle 3"). */
@Composable
fun nextStopSpoken(stop: MotionSection): String {
    val name = MotionStrings.spoken(stop.displayName)
    return when {
        stop.position <= 0 -> stringResource(R.string.motion_next_spoken_unsorted, MotionStrings.spoken(stop.fullName))
        stop.storeAisle != null -> stringResource(R.string.motion_next_spoken_store, stop.position, name, stop.storeAisle)
        else -> stringResource(R.string.motion_next_spoken, stop.position, name)
    }
}

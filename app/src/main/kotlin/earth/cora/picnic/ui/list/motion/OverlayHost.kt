package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** The Next card's test tag. */
const val NEXT_CARD_TAG: String = "picnic_next_card"

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
    val card = presentation.nextCard
    val toastId = toasts.current?.id
    val stack = remember { StackState() }
    // Arrival order: the second one sits 8 dp above the first, frozen while it shows.
    if (toastId != stack.toastId) {
        stack.toastId = toastId
        stack.toastLiftPx = if (toastId != null && stack.cardT0 != null) stack.cardHeightPx + with(density) { 8.dp.roundToPx() } else 0
    }
    val cardT0 = card?.t0
    if (cardT0 != stack.cardT0) {
        stack.cardT0 = cardT0
        stack.cardLiftPx = if (cardT0 != null && stack.toastId != null) stack.toastNoteHeightPx + with(density) { 8.dp.roundToPx() } else 0
    }
    val toastLift = with(density) { stack.toastLiftPx.toDp() }
    val cardLift = with(density) { stack.cardLiftPx.toDp() }
    Box(modifier.fillMaxSize().testTag(OVERLAY_TAG)) {
        FlightLayer(engine, presentation)
        eggs()
        // The toast host, measured at its own height so the note's height is known for stacking.
        Layout(content = { ToastHost(toasts, bottomInset = bottomInset + toastLift, bottomGap = 20.dp) }, modifier = Modifier.fillMaxSize()) { m, c ->
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // The card face sits 20 dp above the inset; its box carries 8 dp of room for the sparkles.
                    .padding(bottom = bottomInset + 20.dp - CARD_ROOM + cardLift)
                    .onSizeChanged { stack.cardHeightPx = it.height - with(density) { CARD_ROOM.roundToPx() } },
            )
        }
    }
}

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
 * and holds the same time. A tap anywhere on it skips it. TalkBack: one button "Next: aisle 5, Breakfast", "Double-tap to
 * dismiss"; the card never steals focus.
 */
@Composable
fun NextCardHost(card: NextCardPresentation, clock: MotionClock, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val stop = card.stop ?: return
    val spoken = nextStopSpoken(stop)
    val dismiss = stringResource(R.string.motion_next_dismiss)
    val probe = LocalMotionProbe.current
    val px = LocalDensity.current.density
    var bounds by remember { mutableStateOf<Rect?>(null) }
    if (probe != null) {
        // Tests only: read the clock in composition so every frame reports the card's pose.
        val now = clock.now
        val b = bounds
        SideEffect {
            if (b != null) {
                val pose = nextCardPose(card, now)
                probe.text(NEXT_CARD_TAG, now, b, dy = pose.dyFraction * b.height + pose.dyDp * px, rotation = pose.rotation, alpha = pose.alpha)
            }
        }
    }
    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .testTag(NEXT_CARD_TAG)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = dismiss) {
                    onSkip()
                    true
                }
            }
            .pointerInput(card.t0) { detectTapGestures { onSkip() } }
            .graphicsLayer {
                val pose = nextCardPose(card, clock.now)
                translationY = pose.dyFraction * size.height + pose.dyDp * density
                rotationZ = pose.rotation
                alpha = pose.alpha
            },
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

/** The Next card's pose at [now]: translateY as (a fraction of its height, dp), rotation and alpha. */
internal data class CardPose(val dyFraction: Float, val dyDp: Float, val rotation: Float, val alpha: Float)

internal fun nextCardPose(card: NextCardPresentation, now: Long): CardPose {
    val exit = card.exitT0
    if (card.reduced) {
        val a = if (exit != null && now >= exit) fadeOut(now - exit) else fadeIn(now - card.t0)
        return CardPose(0f, 0f, NextCardMotion.REST_ROTATION, a)
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

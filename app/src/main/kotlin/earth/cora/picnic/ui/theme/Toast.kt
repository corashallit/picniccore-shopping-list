package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import earth.cora.picnic.platform.Timeouts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/*
 * The sticky-note toast (docs/DESIGN_STICKER.md §4.15, §9, item 68). Owner: WP4 (ToastTest).
 */

/**
 * One toast. [kind] groups toasts for merging and queueing ("tear", "add", "info", …). An Undo toast ([actionLabel] +
 * [onAction], [undo] true) is never cut short; another Undo of the same kind during its hold merges into it through
 * [merge] (default: the newer text, and Undo runs both actions), restarting the hold.
 */
@Immutable
data class ToastMessage(
    val text: String,
    val kind: String = "info",
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val undo: Boolean = actionLabel != null && onAction != null,
    /**
     * How this toast folds with another of its kind on screen: an Undo toast's merges the next Undo into it (§4.15); an
     * info note's, on the incoming note, folds it into the one still up ([ToastHostState.show]).
     */
    val merge: ((current: ToastMessage, incoming: ToastMessage) -> ToastMessage)? = null,
    val id: Long = ToastIds.next(),
    /** A higher reading floor than the toast's 4000 ms (the nudge holds max(6000, readHold), §6.1, §9). */
    val minHoldMs: Long = 0,
)

internal object ToastIds {
    private val ids = AtomicLong(0)
    fun next(): Long = ids.incrementAndGet()
}

/** The toast's still hold (§4.15, §9): max(4000, readHold(words)), then Android's "Time to take action" setting. */
fun toastHold(message: ToastMessage, timeouts: Timeouts): Long {
    val hold = readHold(words(message.text + " " + (message.actionLabel ?: "")), maxOf(PicnicMotion.floorToast, message.minHoldMs))
    return timeouts.recommended(hold, controls = message.actionLabel != null)
}

/** The toast queue (§4.15): one showing, at most 2 waiting. */
@Stable
class ToastHostState {
    /** The toast on screen (dropping, holding or lifting), or null. */
    var current: ToastMessage? by mutableStateOf(null)
        private set

    /** Bumped when the current toast's hold restarts (a merge). */
    var generation by mutableIntStateOf(0)
        private set

    /** True once the current toast has started to leave (a merge then queues instead). */
    internal var leaving by mutableStateOf(false)

    /**
     * True while something covers the toasts (the celebration, §11): the current toast's hold waits, and once it is
     * false again the toast holds its full hold from then (§4.15: an Undo toast is never cut short, not even by a
     * scene it sat under unseen). A toast already lifting off finishes. The host that draws the cover sets it.
     */
    var paused: Boolean by mutableStateOf(false)

    private val waiting = mutableStateListOf<ToastMessage>()

    /** What waits, oldest first. */
    val queue: List<ToastMessage> get() = waiting.toList()

    /** Shows [message] now, merges it into the current Undo, or queues it. */
    fun show(message: ToastMessage) {
        val cur = current
        if (cur == null) {
            current = message
            leaving = false
            generation++
            return
        }
        if (!leaving && cur.undo && message.undo && cur.kind == message.kind) {
            current = cur.merge?.invoke(cur, message) ?: defaultMerge(cur, message)
            generation++
            return
        }
        // An info note that knows how to fold itself into the one of its kind still up does (a refresh's stickers drawn
        // after its sorting, §6.1): one note, its hold starting again.
        val fold = message.merge
        if (!leaving && fold != null && !cur.undo && !message.undo && cur.kind == message.kind) {
            current = fold(cur, message)
            generation++
            return
        }
        if (!message.undo) waiting.removeAll { !it.undo && it.kind == message.kind }
        waiting += message
        while (waiting.size > MAX_QUEUE) waiting.removeAt(0)
    }

    /** The current toast left (hold done, swiped or acted on): the next queued one shows. */
    internal fun finished(id: Long) {
        if (current?.id != id) return
        current = waiting.removeFirstOrNull()
        leaving = false
        if (current != null) generation++
    }

    private fun defaultMerge(cur: ToastMessage, incoming: ToastMessage): ToastMessage {
        val a = cur.onAction
        val b = incoming.onAction
        return incoming.copy(id = cur.id, onAction = { b?.invoke(); a?.invoke() })
    }

    companion object {
        const val MAX_QUEUE = 2
    }
}

/**
 * Hosts the toasts at the bottom-left: 14 dp from the left, [bottomGap] (20 dp) above [bottomInset] (the add bar, the FAB
 * or the IME). Drops in 220 ms from −30 dp at −9°, overshoots +4 dp at −1°, settles at −2°, stays still for its hold (on
 * the frame clock under the fixed motion scale), lifts off upward (260 ms). A swipe dismisses it. Reduced motion: 120 ms
 * fades, the same hold. A polite live region.
 */
@Composable
fun ToastHost(state: ToastHostState, modifier: Modifier = Modifier, bottomInset: Dp = 0.dp, bottomGap: Dp = 20.dp) {
    val message = state.current
    val timeouts = LocalTimeouts.current
    val reduced = LocalReducedMotion.current
    val drop = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    var dropped by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(message?.id, state.generation) {
        val m = message ?: return@LaunchedEffect
        withContext(FixedMotionScale) {
            if (dropped != m.id) {
                lift.snapTo(0f)
                drop.snapTo(0f)
                drop.animateTo(1f, if (reduced) tween(PicnicMotion.rmFade.durationMillis) else tween(PicnicMotion.toastIn.durationMillis, easing = PicnicMotion.toastIn.easing))
                dropped = m.id
            }
            // The hold, on the frame clock; a pause (a cover over the toast) waits, then the full hold starts again.
            val hold = toastHold(m, timeouts)
            while (!holdUnlessPaused(hold) { state.paused }) {
                snapshotFlow { state.paused }.first { !it }
            }
            state.leaving = true
            lift.animateTo(1f, if (reduced) tween(PicnicMotion.rmFade.durationMillis) else tween(PicnicMotion.toastOut.durationMillis, easing = PicnicMotion.toastOut.easing))
            dropped = null
            state.finished(m.id)
        }
    }
    Box(modifier.fillMaxSize()) {
        if (message != null) {
            val e = drop.value
            val l = lift.value
            val from = PicnicMotion.toastDropFrom
            val rest = PicnicMotion.toastRest
            StickyNote(
                message.text,
                message.actionLabel,
                onAction = {
                    message.onAction?.invoke()
                    state.leaving = true
                    state.finished(message.id)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = bottomInset + bottomGap)
                    .testTag(TOAST_TAG)
                    .pointerInput(message.id) {
                        var dx = 0f
                        detectHorizontalDragGestures(onDragEnd = {
                            if (abs(dx) > 56.dp.toPx()) state.finished(message.id)
                            dx = 0f
                        }) { _, d -> dx += d }
                    }
                    .graphicsLayer {
                        if (reduced) {
                            alpha = e * (1f - l)
                            rotationZ = rest.rotation
                        } else {
                            translationY = (lerp(from.dy, 0f, e) - 40f * l).dp.toPx()
                            rotationZ = lerp(from.rotation, rest.rotation, e)
                            alpha = 1f - l
                        }
                    },
                rotate = false,
            )
        }
    }
}

/** The host's test tag on the note. */
const val TOAST_TAG: String = "picnic_toast"

/** Holds for [millis] measured on the frame clock (§8.8): never a tween delay, never scaled. */
suspend fun holdOnFrameClock(millis: Long) {
    val start = withFrameMillis { it }
    while (withFrameMillis { it } - start < millis) Unit
}

/** Holds [millis] on the frame clock like [holdOnFrameClock]; returns false as soon as [paused] is true (start over). */
private suspend fun holdUnlessPaused(millis: Long, paused: () -> Boolean): Boolean {
    if (paused()) return false
    val start = withFrameMillis { it }
    while (true) {
        val now = withFrameMillis { it }
        if (paused()) return false
        if (now - start >= millis) return true
    }
}

/**
 * The sticky note itself (§4.15): a 220 dp lemon-pastel square note rotated −2° ([rotate]), a pink-gingham washi strip
 * (90 dp, +3°) on top, 3 dp white border, hs2; text Zen Maru 700 14 sp ballpoint (hearts drawn as vectors); the action
 * is a tiny white chip sticker with an undo glyph in a 48 dp target. `role=status` (polite).
 */
@Composable
fun StickyNote(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    rotate: Boolean = true,
) {
    val c = PicnicTheme.colors
    val shape = PicnicTheme.shapes.toast
    Box(modifier.width(PicnicTheme.dimens.toast + 8.dp).graphicsLayer { if (rotate) rotationZ = PicnicMotion.toastRest.rotation }) {
        Column(
            Modifier
                .padding(top = 8.dp)
                .width(PicnicTheme.dimens.toast)
                .hardShadow(shape, PicnicTheme.shadows, large = true)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(3.dp)
                .background(c.toastNote)
                .padding(start = 14.dp, end = 10.dp, top = 18.dp, bottom = if (actionLabel != null) 8.dp else 16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PicnicText(message, style = PicnicTheme.type.toast)
            if (actionLabel != null) {
                // Pressed, the chip sticker drops onto its shadow in 2 steps like the kit's chips (never a ripple, §0.2,
                // §4.7); focused, it wears their 2 dp ring.
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val focused by interaction.collectIsFocusedAsState()
                Box(
                    Modifier
                        .align(Alignment.End)
                        .minimumInteractiveComponentSize()
                        .clickable(interaction, indication = null, role = Role.Button, onClick = onAction),
                    contentAlignment = Alignment.Center,
                ) {
                    ChipFace(fill = c.stickerWhite, height = 32.dp, border = 0.dp, horizontal = 12.dp, press = rememberPressFraction(pressed), focused = focused) {
                        Glyph(PicnicGlyph.Undo, null, tint = c.ballpoint, size = 16.dp)
                        Text(actionLabel, style = PicnicTheme.type.label.copy(color = c.ballpoint), maxLines = 1)
                    }
                }
            }
        }
        WashiTape(
            WashiPattern.PinkGingham,
            Modifier.align(Alignment.TopCenter).offset(x = (-10).dp, y = 0.dp),
            length = 90.dp,
            rotation = 3f,
            alpha = 0.85f,
        )
    }
}

/** The note's horizontal footprint on a 360 dp phone (x 14–234 dp), for stacking with the Next card (§4.15). */
val ToastFootprint: Pair<Dp, Dp> = 14.dp to 234.dp

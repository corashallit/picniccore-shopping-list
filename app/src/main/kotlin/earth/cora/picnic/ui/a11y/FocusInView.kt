package earth.cora.picnic.ui.a11y

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Keyboard focus kept in sight and in reach (docs/DESIGN_STICKER.md §15 "Focus", "Keyboard / switch access").
 * Owner: WP10.
 */

/**
 * Where a scrolling container brings what takes focus (a row reached with Tab or the D-pad): wholly into the part of
 * its viewport that nothing covers, [top] px under its start (a pinned plate) and [bottom] px above its end (the docked
 * add bar), scrolling no further than it must, and leaving what already shows (or what is taller than that part)
 * where it is. Both are read when focus moves, so they follow the plate and the dock as they change.
 */
@OptIn(ExperimentalFoundationApi::class)
class ClearOfCovers(private val top: () -> Float, private val bottom: () -> Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = top().coerceIn(0f, containerSize)
        val end = (containerSize - bottom()).coerceIn(start, containerSize)
        val leading = offset - start
        val trailing = offset + size - end
        return when {
            leading >= 0f && trailing <= 0f -> 0f
            leading < 0f && trailing > 0f -> 0f
            abs(leading) < abs(trailing) -> leading
            else -> trailing
        }
    }
}

/**
 * Tab and the D-pad go on through a lazy list to its next control however far down it is (§15: everything reachable by
 * keyboard). Compose looks only a few screens past the list's end for the next thing to focus, so a long run of rows with
 * nothing to press (the album's empty pockets) would send Tab back to the top of the screen and leave the D-pad stuck,
 * the pockets and books below out of reach. Here, a move that finds nothing while the list has more to show scrolls it
 * on most of a screen at a time and looks again, until it finds the next control; at the list's end the list goes back
 * to where it was and focus goes on as the key would have taken it (Tab round to the top of the screen). Put it on the
 * list's modifier.
 */
@Composable
fun Modifier.keyboardScrollsOn(state: LazyListState): Modifier {
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    // Read and written within one focus move (not snapshot state).
    val flags = remember { KeyboardScrollFlags() }
    fun canScroll(forward: Boolean) = if (forward) state.canScrollForward else state.canScrollBackward
    fun stepFrom(direction: FocusDirection, forward: Boolean) {
        flags.stepping = true
        scope.launch {
            try {
                stepOn(state, forward, direction, focus, flags)
            } finally {
                flags.stepping = false
            }
        }
    }
    return onFocusChanged { flags.inside = it.hasFocus }
        .onKeyEvent { e ->
            // The D-pad (and arrows) up and down the list: a move that finds nothing leaves focus where it was.
            val direction = when (e.key) {
                Key.DirectionDown -> FocusDirection.Down
                Key.DirectionUp -> FocusDirection.Up
                else -> return@onKeyEvent false
            }
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            if (flags.stepping) return@onKeyEvent true
            if (focus.moveFocus(direction)) return@onKeyEvent true
            val forward = direction == FocusDirection.Down
            if (!canScroll(forward)) return@onKeyEvent false
            stepFrom(direction, forward)
            true
        }
        .focusProperties {
            onExit = {
                // Tab and Shift+Tab: a move that finds nothing goes round the screen, out of the list.
                val direction = requestedFocusDirection
                val forward = direction == FocusDirection.Next
                if (forward || direction == FocusDirection.Previous) {
                    if (flags.probing) {
                        // The list's own look for the next control found none: focus stays, and the list scrolls on.
                        cancelFocusChange()
                        flags.blocked = true
                    } else if (canScroll(forward) && !flags.stepping) {
                        cancelFocusChange()
                        stepFrom(direction, forward)
                    }
                }
            }
        }
        .focusGroup()
}

private class KeyboardScrollFlags {
    /** Focus is in the list. */
    var inside = false

    /** The list is scrolling to find the next control (keys and entries wait for it). */
    var stepping = false

    /** The list is moving focus on itself, after a scroll. */
    var probing = false

    /** That move found nothing in the list and was kept from going round the screen. */
    var blocked = false
}

/**
 * Scrolls [state] on until a move of focus in [direction] lands on a control, or the list ends and focus goes on as the
 * key would have taken it.
 */
private suspend fun stepOn(state: LazyListState, forward: Boolean, direction: FocusDirection, focus: FocusManager, flags: KeyboardScrollFlags) {
    // After the key's own focus change is over.
    withFrameMillis { }
    val fromIndex = state.firstVisibleItemIndex
    val fromOffset = state.firstVisibleItemScrollOffset
    for (i in 0 until MAX_KEYBOARD_STEPS) {
        val viewport = state.layoutInfo.run { viewportEndOffset - viewportStartOffset }
        // Less than a screen, so nothing between here and there goes unseen.
        val step = viewport * KEYBOARD_SCROLL_STEP
        if (abs(state.scrollBy(if (forward) step else -step)) < 1f) break
        withFrameMillis { }
        flags.blocked = false
        flags.probing = true
        val moved = try {
            focus.moveFocus(direction)
        } finally {
            flags.probing = false
        }
        if (flags.blocked || !moved) continue
        return
    }
    // The list's end and nothing more to reach: back to where she was (what is in view is what Tab comes back into), and
    // on as the key would have gone (still stepping, so the list lets this move out).
    state.scrollToItem(fromIndex, fromOffset)
    withFrameMillis { }
    focus.moveFocus(direction)
}

/** How far [keyboardScrollsOn] scrolls a list at a time, as a share of its viewport. */
private const val KEYBOARD_SCROLL_STEP = 0.8f

/** The most steps [keyboardScrollsOn] takes for one key (a list some 40 screens long). */
private const val MAX_KEYBOARD_STEPS = 50

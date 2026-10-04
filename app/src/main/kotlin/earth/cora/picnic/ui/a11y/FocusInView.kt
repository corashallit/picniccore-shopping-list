package earth.cora.picnic.ui.a11y

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import kotlin.math.abs

/*
 * Keyboard focus kept in sight (docs/DESIGN_STICKER.md §15 "Focus", "Keyboard / switch access"). Owner: WP10.
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

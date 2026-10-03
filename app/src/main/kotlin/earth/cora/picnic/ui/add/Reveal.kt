package earth.cora.picnic.ui.add

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import earth.cora.picnic.core.assistant.PhotoLine
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.PicnicMotion
import kotlinx.coroutines.withContext

/*
 * The reveal after an answer (docs/DESIGN_STICKER.md §4.18, §4.19, D16): nothing streams; once the answer is here its
 * real steps (or the photo's lines) land 250 ms apart, the capsule pops ("pon!") and the stickers fan out 80 ms apart
 * into the card's rows. Times are measured on the frame clock under the fixed motion scale (§8.8). Reduced motion keeps
 * every beat time (steps still land 250 ms apart, as fades) and drops the fan-out: the rows appear at once. Owner: WP7.
 */

/**
 * When each piece shows, in ms from the start of the reveal. [stepAt] for thinking steps (or [lineAt] for photo lines),
 * [popAt] the capsule's pop (the card appears), [rowAt] each row's sticker by row key, [total] the end.
 */
@Immutable
data class RevealPlan(
    val stepAt: List<Long> = emptyList(),
    val lineAt: List<Long> = emptyList(),
    val popAt: Long = 0,
    val rowAt: Map<Int, Long> = emptyMap(),
    val total: Long = 0,
) {
    fun stepsShown(elapsed: Long): Int = stepAt.count { it <= elapsed }
    fun linesShown(elapsed: Long): Int = lineAt.count { it <= elapsed }
    fun popped(elapsed: Long): Boolean = elapsed >= popAt
    fun rowShown(key: Int, elapsed: Long): Boolean = (rowAt[key] ?: popAt) <= elapsed
    fun rowsShown(elapsed: Long): Int = rowAt.values.count { it <= elapsed }

    companion object {
        /** The step's tick lands at 1.3 in 2 frames (one pose each), then the line darkens (§4.18). */
        const val TICK_MILLIS: Long = 2L * 83
        const val ROW_POP_MILLIS: Long = 2L * 83

        /**
         * The thinking box (§4.18): [steps] lines 250 ms apart from 0, the pop one beat after the last, then the rows in
         * [rowKeys] order 80 ms apart (at once under [reduced] motion).
         */
        fun thinking(steps: Int, rowKeys: List<Int>, reduced: Boolean): RevealPlan {
            val stepAt = List(steps) { it * AddRules.STEP_MILLIS }
            val pop = steps * AddRules.STEP_MILLIS
            val first = pop + PicnicMotion.pose
            val rowAt = rowKeys.withIndex().associate { (i, key) -> key to if (reduced) pop else first + i * AddRules.FAN_OUT_MILLIS }
            val lastRow = rowAt.values.maxOrNull() ?: pop
            return RevealPlan(stepAt = stepAt, popAt = pop, rowAt = rowAt, total = maxOf(lastRow + ROW_POP_MILLIS, pop + TICK_MILLIS))
        }

        /**
         * The polaroid (§4.19): the lines appear 250 ms apart; each kept line's row pops into the stickers tray right after
         * its line (crossed-out lines pop nothing); rows no line points at pop after the last line, 80 ms apart.
         * [rowKeyOfItem] maps the answer's item index to its row key.
         */
        fun photo(lines: List<PhotoLine>, rowKeyOfItem: List<Int>, reduced: Boolean): RevealPlan {
            val lineAt = List(lines.size) { it * AddRules.STEP_MILLIS }
            val rowAt = LinkedHashMap<Int, Long>()
            lines.forEachIndexed { i, line ->
                val index = line.itemIndex
                if (!line.crossedOut && index != null && index in rowKeyOfItem.indices) {
                    val key = rowKeyOfItem[index]
                    if (key !in rowAt) rowAt[key] = lineAt[i] + if (reduced) 0 else TICK_MILLIS
                }
            }
            val afterLines = lines.size * AddRules.STEP_MILLIS
            var n = 0
            for (key in rowKeyOfItem) if (key !in rowAt) rowAt[key] = afterLines + if (reduced) 0 else (n++) * AddRules.FAN_OUT_MILLIS
            val last = rowAt.values.maxOrNull() ?: 0
            return RevealPlan(lineAt = lineAt, popAt = 0, rowAt = rowAt, total = maxOf(last + ROW_POP_MILLIS, afterLines))
        }

        /** Everything shown at once (a reveal that already played). */
        val Done: RevealPlan = RevealPlan()
    }
}

/** A running reveal: [elapsed] ms on the frame clock since it started (huge once it already played). */
@Stable
class RevealClock(val id: Long, val plan: RevealPlan, alreadyPlayed: Boolean) {
    var elapsed by mutableLongStateOf(if (alreadyPlayed) DONE else 0L)
        internal set

    val done: Boolean get() = elapsed >= plan.total

    fun stepsShown(): Int = plan.stepsShown(elapsed)
    fun linesShown(): Int = plan.linesShown(elapsed)
    fun popped(): Boolean = plan.popped(elapsed)
    fun rowShown(key: Int): Boolean = plan.rowShown(key, elapsed)

    /** How long ago [at] ms was (negative before it). */
    fun since(at: Long): Long = elapsed - at

    companion object {
        const val DONE: Long = Long.MAX_VALUE / 4
    }
}

/**
 * Runs the reveal [id] with [plan] on the frame clock (under [FixedMotionScale]); [onDone] once it has played. A reveal
 * in [played] shows everything at once (returning from My pantry never replays it).
 */
@Composable
fun rememberRevealClock(id: Long, plan: RevealPlan, played: Boolean, onDone: (Long) -> Unit): RevealClock {
    val clock = remember(id) { RevealClock(id, plan, played) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(id) {
        if (!clock.done) {
            withContext(FixedMotionScale) {
                val start = withFrameMillis { it }
                while (!clock.done) {
                    clock.elapsed = withFrameMillis { it } - start
                }
            }
        }
        done(id)
    }
    return clock
}

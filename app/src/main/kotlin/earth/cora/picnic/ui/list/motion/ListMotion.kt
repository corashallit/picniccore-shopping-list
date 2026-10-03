package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import earth.cora.picnic.R
import earth.cora.picnic.ui.a11y.isScreenReaderOn
import earth.cora.picnic.ui.theme.LocalReducedMotion
import kotlinx.coroutines.withContext
import earth.cora.picnic.ui.theme.FixedMotionScale

/*
 * Running the engine in a screen, and the row's poses (docs/DESIGN_STICKER.md §8.3, §8.8). WP5's list screen hosts
 * [MotionEngineHost] once, mounts MotionOverlay above the list and CelebrationHost above everything, and draws its rows
 * with [RowMotion]. Owner: WP6.
 */

/**
 * Runs [engine]'s frame loop for as long as this is composed (it ticks only while something is in flight), keeps its
 * settings current (the Surprises switches, reduced motion, TalkBack, the density) and its spoken strings in the app's
 * language, and drops every chain when the screen leaves (state is never late: the list then shows the committed state).
 */
@Composable
fun MotionEngineHost(
    engine: MotionChoreographer,
    nextStopCard: Boolean = true,
    celebration: Boolean = true,
    eggs: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val talkBack = isScreenReaderOn()
    val density = LocalDensity.current.density
    val strings = rememberMotionStrings()
    SideEffect {
        engine.settings = MotionSettings(
            nextStopCard = nextStopCard,
            celebration = celebration,
            eggs = eggs,
            reducedMotion = reduced,
            talkBack = talkBack,
            density = density,
        )
        engine.strings = strings
    }
    LaunchedEffect(engine) { withContext(FixedMotionScale) { engine.run() } }
    DisposableEffect(engine) { onDispose { engine.finishAll() } }
}

/** The engine's spoken strings and the Next card's word, from strings_motion.xml. */
@Composable
fun rememberMotionStrings(): MotionStrings {
    val res = LocalResources.current
    val next = stringResource(R.string.motion_next)
    val copy = rememberCelebrationCopy()
    return remember(res, next, copy) {
        MotionStrings(
            next = next,
            aisleDone = { name -> res.getString(R.string.motion_aisle_done, name) },
            nextStop = { s ->
                val name = MotionStrings.spoken(s.displayName)
                when {
                    s.position <= 0 -> res.getString(R.string.motion_next_spoken_unsorted, MotionStrings.spoken(s.fullName))
                    s.storeAisle != null -> res.getString(R.string.motion_next_spoken_store, s.position, name, s.storeAisle)
                    else -> res.getString(R.string.motion_next_spoken, s.position, name)
                }
            },
            listComplete = { n, m ->
                if (m == null) {
                    res.getQuantityString(R.plurals.motion_list_complete_spoken_no_time, n, n)
                } else {
                    val h = m / 60
                    val r = m % 60
                    val time = buildList {
                        if (h > 0) add(res.getQuantityString(R.plurals.motion_hours, h, h))
                        if (r > 0 || h == 0) add(res.getQuantityString(R.plurals.motion_minutes, r, r))
                    }.joinToString(" ")
                    res.getQuantityString(R.plurals.motion_list_complete_spoken, n, n, time)
                }
            },
            celebration = copy,
        )
    }
}

/**
 * A row's look during the chains (§4.11, §8.3, §8.4): the item text never moves; only the sticker, the hanko and the
 * ruled line do. WP5's row reads these from the presentation and the clock.
 */
object RowMotion {
    /**
     * True while the row shows its full sticker rather than its ghost: an unchecked row; a just-checked row until its
     * sticker hops (Slam, Peeling); never while it is away; an un-checked row only once its sticker is back (360 ms).
     * Reduced motion: the sticker until the landing, then a 120 ms cross-fade to the ghost ([ghostAlpha]).
     */
    fun showsSticker(checked: Boolean, phase: RowPhase?): Boolean = when (phase) {
        RowPhase.Slam, RowPhase.Peeling -> true
        RowPhase.Away -> false
        RowPhase.Landing -> false
        RowPhase.Idle, null -> !checked
    }

    /** The ghost's alpha (1 = fully the ghost): the reduced-motion cross-fades at the landing and the un-check. */
    fun ghostAlpha(checked: Boolean, phase: RowPhase?, rowStart: Long?, now: Long, reduced: Boolean): Float {
        if (!reduced || rowStart == null) return if (showsSticker(checked, phase)) 0f else 1f
        return when (phase) {
            RowPhase.Away -> fadeIn(now - rowStart)
            RowPhase.Landing -> fadeOut(now - rowStart)
            else -> if (showsSticker(checked, phase)) 0f else 1f
        }
    }

    /** The hanko's scale (0 = not drawn): the slam on a check, the lift on an un-check; 1 at rest when checked. */
    fun hankoScale(checked: Boolean, phase: RowPhase?, rowStart: Long?, now: Long, reduced: Boolean): Float {
        if (rowStart == null || phase == null) return if (checked) 1f else 0f
        val e = now - rowStart
        return when {
            reduced -> 1f
            phase == RowPhase.Landing -> HankoMotion.lift(e)
            checked -> HankoMotion.slam(e)
            else -> 0f
        }
    }

    /** The hanko's alpha: a 120 ms fade in on a check (out on an un-check) under reduced motion. */
    fun hankoAlpha(checked: Boolean, phase: RowPhase?, rowStart: Long?, now: Long, reduced: Boolean): Float {
        if (!reduced || rowStart == null || phase == null) return 1f
        val e = now - rowStart
        return if (phase == RowPhase.Landing) fadeOut(e) else if (checked) fadeIn(e) else 1f
    }
}

/** The row sticker's peel (corner lift, pivot 20 % / 90 %) while [phase] is Peeling; at rest otherwise. */
fun Modifier.rowStickerPeel(phase: RowPhase?, rowStart: Long?, clock: MotionClock): Modifier =
    if (phase != RowPhase.Peeling || rowStart == null) this else this
        .graphicsLayer {
            val pose = PeelMotion.at(clock.now - rowStart)
            translationY = pose.dy * density
            rotationZ = pose.rotation
            transformOrigin = TransformOrigin(PeelMotion.pivotX, PeelMotion.pivotY)
        }
        .skewed(PeelMotion.pivotX, PeelMotion.pivotY) { PeelMotion.at(clock.now - rowStart).skewX }

/** The row's hanko: the slam (1.4 → 1.15 → 1) or the lift (1 → 1.1 → 0), or the reduced-motion fades. */
fun Modifier.rowHanko(checked: Boolean, phase: RowPhase?, rowStart: Long?, clock: MotionClock, reduced: Boolean): Modifier =
    if (rowStart == null || phase == null) this else graphicsLayer {
        val s = RowMotion.hankoScale(checked, phase, rowStart, clock.now, reduced)
        scaleX = s
        scaleY = s
        alpha = RowMotion.hankoAlpha(checked, phase, rowStart, clock.now, reduced)
    }

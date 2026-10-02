package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import kotlin.math.floor

/*
 * Stepped poses (docs/DESIGN_STICKER.md §8.1, §8.2): stickers move in 2–3 held poses like a cel, sparkles twinkle
 * at 8 fps. Owner: WP4 (MotionSpecTest.stepped).
 */

/** CSS `steps(n, end)`: floor(t × n) / n, reaching 1 only at t = 1. */
@Immutable
data class Stepped(val steps: Int) : Easing {
    init {
        require(steps > 0) { "steps must be positive" }
    }

    override fun transform(fraction: Float): Float =
        if (fraction >= 1f) 1f else floor(fraction * steps) / steps
}

/**
 * The pose index (0 until [poses]) of a looping [periodMillis] animation at [timeMillis], shifted by [delayMillis]
 * (loops are offset by delay only, §4.27). Before the delay the first pose holds.
 */
fun loopPose(timeMillis: Long, periodMillis: Long, poses: Int, delayMillis: Long = 0): Int {
    val t = timeMillis - delayMillis
    if (t < 0) return 0
    val inCycle = t % periodMillis
    return ((inCycle * poses) / periodMillis).toInt().coerceIn(0, poses - 1)
}

/**
 * The pose index (0 until [poses]) of a one-shot stepped animation of [durationMillis] that started [elapsedMillis]
 * ago; [poses] − 1 once it has ended.
 */
fun shotPose(elapsedMillis: Long, durationMillis: Long, poses: Int): Int =
    if (elapsedMillis >= durationMillis) poses - 1
    else ((elapsedMillis.coerceAtLeast(0) * poses) / durationMillis).toInt().coerceIn(0, poses - 1)

/** One held pose of a sticker or sparkle: a shape change only (scale and rotation), never a flash. */
@Immutable
data class Pose(val scale: Float = 1f, val rotation: Float = 0f, val dx: Float = 0f, val dy: Float = 0f)

/**
 * The one twinkle rule (§4.27): three hand-drawn scale poses (.82, −8° → 1.12, +6° → .95, 0°) at 8 fps (375 ms per
 * cycle, `steps(3)`), offset by delay only.
 */
object Twinkle {
    val poses: List<Pose> = listOf(Pose(0.82f, -8f), Pose(1.12f, 6f), Pose(0.95f, 0f))
    val periodMillis: Long = PicnicMotion.sparkle.durationMillis.toLong()

    /** The pose at [timeMillis] for a sparkle started [delayMillis] in. */
    fun at(timeMillis: Long, delayMillis: Long = 0): Pose = poses[loopPose(timeMillis, periodMillis, poses.size, delayMillis)]
}

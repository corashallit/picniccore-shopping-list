package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/*
 * A hook for the reading-rule audit (docs/DESIGN_STICKER.md §9: "each moment's interaction test measures its still
 * time from settle with StillnessProbe"). Text-bearing motion pieces report, every frame they change, their laid-out
 * bounds (root px), their transform and alpha; overlays that could cross a text (glints, the curl, the shake,
 * sparkles, cards) report their bounds. Null in production: nothing is recorded. Owner: WP6; the test helper
 * StillnessProbe implements it.
 */
interface MotionProbe {
    /**
     * A text-bearing piece [tag] at frame time [time]: its laid-out [bounds] (root px, before its own motion), its
     * motion as ([dx], [dy]) px, [scale], [rotation] degrees and [alpha].
     */
    fun text(tag: String, time: Long, bounds: Rect, dx: Float = 0f, dy: Float = 0f, scale: Float = 1f, rotation: Float = 0f, alpha: Float = 1f)

    /** An overlay [tag] drawn at [time] over [bounds] (root px); [behind] = drawn behind every text plate (rays, confetti). */
    fun overlay(tag: String, time: Long, bounds: Rect?, behind: Boolean = false)
}

/** The probe the motion pieces report to; null outside tests. */
val LocalMotionProbe = staticCompositionLocalOf<MotionProbe?> { null }

package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.MotionDurationScale
import kotlin.math.floor

/*
 * Motion tokens and the reading rule (docs/DESIGN_STICKER.md §8.2, §8.8, §9). Every timing in the app lives
 * here. Owner: WP4 (MotionSpecTest, ReadHoldTest). Phase-0 contract: WP4's toasts, sheets, buttons, LCD and
 * switches and all of WP6 build on these names; values may be tuned, names change only with a consumer note.
 */

/** One animation: a duration and its easing. */
@Immutable
data class MotionToken(val durationMillis: Int, val easing: Easing)

/** CSS `steps(n, end)`: floor(t × n) / n. Stickers move in stepped poses (§8.1). */
@Immutable
data class Stepped(val steps: Int) : Easing {
    init {
        require(steps > 0) { "steps must be positive" }
    }

    override fun transform(fraction: Float): Float = floor(fraction * steps) / steps
}

/** Every §8.2 token. Millis are on the frame clock under [FixedMotionScale] (§8.8), never under Android's scales. */
object PicnicMotion {
    /** One sticker pose at 12 fps (ms). */
    val pose: Int = 83

    /** Sparkle twinkle: a 3-pose cycle at 8 fps. */
    val sparkle: MotionToken = MotionToken(375, Stepped(3))

    /** Fold / unfold an aisle. */
    val layout: MotionToken = MotionToken(240, CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.2f))

    /** App bar collapse, list placement, book height. */
    val standard: MotionToken = MotionToken(200, CubicBezierEasing(0.4f, 0f, 0.2f, 1f))

    val sheetIn: MotionToken = MotionToken(280, CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.1f))

    /** §8.2 gives only 260 ms; the easing is the mockup's (CSS `ease-in`). */
    val sheetOut: MotionToken = MotionToken(260, EaseInCss)

    val toastIn: MotionToken = MotionToken(220, CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f))

    /** §8.2 gives only 260 ms; the easing is the mockup's (CSS `ease-in`). */
    val toastOut: MotionToken = MotionToken(260, EaseInCss)

    /** The check-off hop to the basket. */
    val hop: MotionToken = MotionToken(300, CubicBezierEasing(0.3f, 0.9f, 0.5f, 1f))

    /** A personality hop (berry, egg, bread, can, frozen; §8.3). */
    val hopPersonality: MotionToken = MotionToken(360, CubicBezierEasing(0.3f, 0.9f, 0.5f, 1f))

    /** The Next card rising. */
    val cardIn: MotionToken = MotionToken(400, CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f))

    val cardOut: MotionToken = MotionToken(300, CubicBezierEasing(0.5f, 0f, 0.7f, 0.4f))

    /** Holo glint across a reward. */
    val glint: MotionToken = MotionToken(1200, Stepped(12))

    /** Every movement's reduced-motion replacement: a fade in place (§8.8). */
    val rmFade: MotionToken = MotionToken(120, LinearEasing)

    /** Reading-rule floors (§9), ms: the interstitial (Next) card, a toast, an in-scene beat, an LCD word. */
    val floorCard: Long = 2600
    val floorToast: Long = 4000
    val floorBeat: Long = 1500
    val floorLcdWord: Long = 1500
}

/** CSS `ease-in`. */
private val EaseInCss: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

/**
 * The reading rule (§9): fully still time for a text-bearing moment, from the moment its text is still.
 * `max(floor, 1200 + 300 × words)` ms. Never shortened by reduced motion or Android's animation scales.
 */
fun readHold(words: Int, floor: Long): Long = maxOf(floor, 1200L + 300L * words)

/**
 * The words of a text for [readHold] (§9): whitespace-separated tokens after removing · ♡ ★ ✦ … ! ? , . and
 * dropping every token made only of the punctuation - – — & / + :. Count the REAL text at run time
 * ("NEXT 7 Pasta, rice & international A3" -> 6).
 */
fun words(text: String): Int =
    text.filterNot { it in REMOVED }
        .split(WHITESPACE)
        .count { token -> token.isNotEmpty() && !token.all { it in PUNCTUATION_ONLY } }

private const val REMOVED = "·♡★✦…!?,."
private const val PUNCTUATION_ONLY = "-–—&/+:"
private val WHITESPACE = Regex("\\s+")

/**
 * A fixed animation scale of 1 (§8.8): the Choreographer, the overlay layer, toasts, the Next card, the
 * receipt, sheets and the celebration run their `Animatable`s with this in their coroutine context, so
 * Android's animator scale (0, 0.5×, 2×) never changes a beat or a hold. MotionScaleTest injects 0, 0.5, 2.
 */
object FixedMotionScale : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

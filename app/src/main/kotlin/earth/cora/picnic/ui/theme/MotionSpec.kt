package earth.cora.picnic.ui.theme

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.MotionDurationScale

/*
 * Motion tokens and the reading rule (docs/DESIGN_STICKER.md §8.2, §8.8, §9). Every timing in the app lives
 * here. Owner: WP4 (MotionSpecTest, ReadHoldTest). Phase-0 contract: WP4's toasts, sheets, buttons, LCD and
 * switches and all of WP6 build on these names; values may be tuned, names change only with a consumer note.
 */

/** One animation: a duration and its easing. */
@Immutable
data class MotionToken(val durationMillis: Int, val easing: Easing)

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

    // ---- Component timings (§4) ----

    /** A pressed button drops onto its shadow in 2 steps of 80 ms (§4.6). */
    val buttonPress: MotionToken = MotionToken(160, Stepped(2))

    /** The switch knob moves in 2 steps (120 ms, §4.9). */
    val switchKnob: MotionToken = MotionToken(120, Stepped(2))

    /** LCD digits tick on change: −2 → +1 → 0 dp, 2 steps, 160 ms (§4.3). */
    val lcdTick: MotionToken = MotionToken(160, Stepped(2))
    val lcdTickOffsetsDp: List<Float> = listOf(-2f, 1f, 0f)

    /** The LCD star blinks to 12 % for 3 % of every 4 s (0.25 Hz, §4.3). */
    val lcdBlinkPeriod: Long = 4000
    val lcdBlinkOn: Long = 120
    val lcdBlinkAlpha: Float = 0.12f

    /** The hanko slams 1.4 → 1.15 → 1.0 in 2 steps (160 ms, §8.3). */
    val hankoSlam: MotionToken = MotionToken(160, Stepped(2))
    val hankoSlamScales: List<Float> = listOf(1.4f, 1.15f, 1f)

    /** The sun bounces 1.25, −20° → 1.1, +10° → 1 (2 steps, 240 ms, §4.2). */
    val sunBounce: MotionToken = MotionToken(240, Stepped(2))
    val sunBouncePoses: List<Pose> = listOf(Pose(1.25f, -20f), Pose(1.1f, 10f), Pose(1f, 0f))

    /** The washi handle slaps on a beat after the sheet arrives: 1.6 → 1.2 → 1, 2 poses (§4.16). */
    val handleSlapScales: List<Float> = listOf(1.6f, 1.2f, 1f)

    /** Toast drop poses (§4.15): from −30 dp at −9°, overshoot +4 dp at −1°, settle at −2°. */
    val toastDropFrom: Pose = Pose(rotation = -9f, dy = -30f)
    val toastDropOvershoot: Pose = Pose(rotation = -1f, dy = 4f)
    val toastRest: Pose = Pose(rotation = -2f)

    /** The overflow menu pops from 90 % (220 ms, cubic-bezier(.3, 1.5, .5, 1), §4.24). */
    val menuPop: MotionToken = MotionToken(220, CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f))

    /** The FAB bobs −4 dp on a 2.4 s ease-in-out loop while visible (§4.6). */
    val fabBob: MotionToken = MotionToken(2400, CubicBezierEasing(0.42f, 0f, 0.58f, 1f))

    /** Washi flutter: skewX 10° → −6° for 10 % of every 6 s (§4.14). */
    val washiFlutterPeriod: Long = 6000

    /** Glow stars charge 85 % → 100 % at 1.15× for 2 frames every 5 s (§2.12). */
    val glowChargePeriod: Long = 5000
    val glowChargeFrames: Int = 2

    /** Loaders (§4.22): the bee loop (2.4 s), the ladybug ring (3 s), the busy sparkle (8 fps). */
    val beeLoop: Long = 2400
    val ladybugRing: Long = 3000
    val busySparkleFps: Int = 8

    /** The gashapon wobbles ±8° in 3 poses "on threes" (§4.18). */
    val gashaponPoses: List<Pose> = listOf(Pose(rotation = -8f), Pose(rotation = 0f), Pose(rotation = 8f))
    val gashaponPoseMillis: Long = 125

    /** The shiny glint: an 80 % white band clipped to the die-cut, 6 steps, every 4 s (§2.11). */
    val shinyGlintPeriod: Long = 4000
    val shinyGlint: MotionToken = MotionToken(500, Stepped(6))

    /** The "now" stamp ring turns once per 6 s (§4.13). */
    val nowRingTurn: Long = 6000
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

/** Reduced motion (§8.8): on when the animator duration scale OR the transition animation scale is 0. */
fun reducedMotionFrom(animatorScale: Float, transitionScale: Float): Boolean = animatorScale == 0f || transitionScale == 0f

/**
 * Reads both Android animation scales explicitly from Settings.Global (the system scale is used only to make this
 * decision; animations never follow it, §8.8). MainActivity calls it at start and on every resume.
 */
fun readReducedMotion(resolver: ContentResolver): Boolean = reducedMotionFrom(
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
    Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f),
)

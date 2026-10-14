package earth.cora.picnic.ui.list.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import earth.cora.picnic.core.catalog.Personality
import earth.cora.picnic.ui.theme.PicnicMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/*
 * The chains' beat times and every pose as a pure function of elapsed time (docs/DESIGN_STICKER.md §7.6, §8.3, §8.4,
 * §10). Stickers move in held poses (2–4 frames, like a cel); the hop and the cards move on their easings. Nothing here
 * reads a clock: a piece computes its pose from (now − t0) on the engine's frame clock, so a frame strip is exactly
 * reproducible and Android's animation scales never touch it (§8.8). Distances are dp, angles degrees. Owner: WP6.
 * The §8.2 tokens (pose, hop, cardIn, …) stay in MotionSpec.kt; these are the chains' own numbers.
 */

/** Beat times, ms. The check-off chain from the tap (§8.3), the aisle-done chain from the last landing L (§8.4). */
object Beats {
    // ---- The check-off chain (§8.3) ----
    const val HANKO: Long = 0
    const val INK: Long = 160
    const val PEEL: Long = 200
    const val PEEL_2: Long = 260
    const val HOP: Long = 320

    /** Land = hop start + the hop (300 ms; 360 ms for a personality). */
    fun land(personality: Personality): Long = HOP + hopMillis(personality)

    /** The twin appears 320 ms after the landing (940 ms; 1000 ms after a personality hop, §7.6). */
    const val MAGIC_AFTER_LAND: Long = 320
    fun magic(personality: Personality): Long = land(personality) + MAGIC_AFTER_LAND

    /** The twin's 4 poses take 320 ms: settled at 1260 (1320). */
    const val TWIN: Long = 320
    fun settled(personality: Personality): Long = magic(personality) + TWIN

    fun hopMillis(personality: Personality): Long =
        if (personality == Personality.NONE) PicnicMotion.hop.durationMillis.toLong() else PicnicMotion.hopPersonality.durationMillis.toLong()

    // ---- The un-check (§8.4) ----
    /** The overlay copy hops from the basket back to the row: 340 ms reverse arc. */
    const val HOP_BACK: Long = 340

    /** The row keeps its ghost until the sticker is back: 360 ms. */
    const val GHOST_UNTIL: Long = 360

    /** The hanko lifts off an un-checked row: 1 → 1.1 → gone, 2 steps (160 ms). */
    const val HANKO_LIFT: Long = 160

    /** The twin peels off the book: gone at +320. */
    const val PEEL_OFF: Long = 320

    // ---- The book (§7.6) ----
    /** A page turn before a twin appears on another page: 3 frames, 240 ms. */
    const val PAGE_TURN: Long = 240

    /** A full book glints once, 360 ms after its last twin starts to appear. */
    const val FULL_GLINT_AFTER_TWIN: Long = 360

    /** PERFECT is pressed 450 ms after the celebration closes (L + 1800 when it is skipped or off). */
    const val PERFECT_AFTER_CLOSE: Long = 450

    // ---- The aisle-done chain, from L (§8.4) ----
    const val LID: Long = 300
    const val LID_HANKO: Long = 440
    const val MINIS_HIDE: Long = 540
    const val STAMP: Long = 500
    const val CURL: Long = 800
    const val FOLD: Long = 1100
    const val RIBBON: Long = 1200
    const val BOW: Long = 1400
    const val NEXT_CARD: Long = 1300
    const val CELEBRATION: Long = 1350

    /** A folded aisle completing (rare): the Next card or the celebration at L + 700, no curl or fold. */
    const val FOLDED_NEXT: Long = 700

    /** The celebration overlay fades out in 200 ms. */
    const val CELEBRATION_FADE: Long = 200

    /** Reduced motion swaps every movement for a fade in place of this length (§8.8). */
    val RM_FADE: Long = PicnicMotion.rmFade.durationMillis.toLong()
}

/** One held pose of a sticker or piece: offsets in dp, angles in degrees. */
@Immutable
data class MotionPose(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val skewX: Float = 0f,
    val alpha: Float = 1f,
) {
    constructor(scale: Float, rotation: Float = 0f, dx: Float = 0f, dy: Float = 0f, alpha: Float = 1f) :
        this(dx, dy, scale, scale, rotation, 0f, alpha)

    companion object {
        val Rest: MotionPose = MotionPose()
        val Gone: MotionPose = MotionPose(alpha = 0f, scaleX = 0f, scaleY = 0f)
    }
}

/** The index of a held pose: −1 before [start], [count] − 1 once [count] steps of [stepMs] have run (or [count] when [after]). */
internal fun heldStep(elapsed: Long, stepMs: Long, count: Int): Int =
    if (elapsed < 0) -1 else min((elapsed / stepMs).toInt(), count - 1)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun clamp01(x: Float): Float = x.coerceIn(0f, 1f)

/** A 120 ms reduced-motion fade in (0 → 1) from [t0]. */
fun fadeIn(elapsed: Long): Float = clamp01(elapsed.toFloat() / Beats.RM_FADE)

/** A 120 ms reduced-motion fade out (1 → 0) from [t0]. */
fun fadeOut(elapsed: Long): Float = 1f - clamp01(elapsed.toFloat() / Beats.RM_FADE)

/* ------------------------------------------------------------------------------------------------
 * The row: hanko, ink, burst, peel (§8.3) and the hanko's lift (§8.4).
 * ------------------------------------------------------------------------------------------------ */

object HankoMotion {
    /** The slam: 1.4 → 1.15 → 1.0 in 2 steps of 80 ms (WP4's PicnicMotion.hankoSlamScales). */
    fun slam(elapsed: Long): Float {
        val scales = PicnicMotion.hankoSlamScales
        if (elapsed < 0) return scales.first()
        val step = (PicnicMotion.hankoSlam.durationMillis / (scales.size - 1)).toLong()
        return scales[min((elapsed / step).toInt(), scales.size - 1)]
    }

    /** The un-check lift: 1 → 1.1 → 0 in 2 steps (160 ms); 0 = gone. */
    fun lift(elapsed: Long): Float = when {
        elapsed < 0 -> 1f
        elapsed < 80 -> 1f
        elapsed < 160 -> 1.1f
        else -> 0f
    }

    /** The ink spread at 160: a 2.5 dp ring, 1 → 1.08, .4 → 0 alpha (2 steps, 170 ms). Null = not showing. */
    fun ink(elapsedFromTap: Long): Pair<Float, Float>? {
        val e = elapsedFromTap - Beats.INK
        return when {
            e < 0 -> null
            e < 85 -> 1f to 0.4f
            e < 170 -> 1.08f to 0.2f
            else -> null
        }
    }

    /** The ruled line under the row thunks (2 frames, 170 ms) from 160: its offset in dp. */
    fun ruleThunk(elapsedFromTap: Long): Float {
        val e = elapsedFromTap - Beats.INK
        return when {
            e < 0 -> 0f
            e < 85 -> 1.2f
            e < 170 -> 0.4f
            else -> 0f
        }
    }
}

/** The sticker's corner lift before the hop (§8.3): 2 poses at 200 and 260, pivot 20 % / 90 %. */
object PeelMotion {
    val pivotX: Float = 0.2f
    val pivotY: Float = 0.9f

    fun at(elapsedFromTap: Long): MotionPose = when {
        elapsedFromTap < Beats.PEEL -> MotionPose.Rest
        elapsedFromTap < Beats.PEEL_2 -> MotionPose(dy = -2f, rotation = -6f, skewX = -5f)
        else -> MotionPose(dy = -4f, rotation = -12f, skewX = -8f)
    }
}

/** The hanko's 8 stickers bursting 30–50 dp from the checkbox (5 steps, 500 ms), never towards the item text. */
object HankoBurst {
    const val DURATION: Long = 500
    const val COUNT: Int = 8

    /** The four burst shapes, as DecorManifest names them. */
    val kinds: List<String> = listOf("heart", "star", "star4", "flower")

    @Immutable
    data class Particle(val kind: String, val angleDeg: Float, val distanceDp: Float, val sizeDp: Float)

    /**
     * The particles for [seed]: angles spread over −110° … +110° around "right" (0° = towards the row's end, away from
     * its text, which lies to the left of the checkbox), distances 30–50 dp, sizes 12–16 dp.
     */
    fun particles(seed: Int): List<Particle> {
        var s = seed.toLong() and 0x7fffffff
        fun next(): Float {
            s = (s * 1103515245L + 12345L) and 0x7fffffff
            return (s % 10_000).toFloat() / 10_000f
        }
        return List(COUNT) { i ->
            val base = -110f + 220f * i / (COUNT - 1)
            Particle(kinds[i % kinds.size], base + (next() - 0.5f) * 14f, 30f + 20f * next(), 12f + 4f * next())
        }
    }

    /** Eight particles all round a centre (the PER-FECT! slams, where no text sits beside it), 26–40 dp out. */
    fun ring(seed: Int): List<Particle> {
        val base = particles(seed)
        return base.mapIndexed { i, p -> p.copy(angleDeg = -90f + 45f * i + (p.angleDeg % 7f), distanceDp = p.distanceDp * 0.8f) }
    }

    /** A particle's pose at [elapsed]: its offset from the centre (dp), scale and alpha; null once gone. */
    fun at(p: Particle, elapsed: Long): MotionPose? {
        val i = heldStep(elapsed, DURATION / 5, 5)
        if (i < 0 || elapsed >= DURATION) return null
        val reach = floatArrayOf(0.35f, 0.65f, 0.85f, 0.95f, 1f)[i]
        val scale = floatArrayOf(0.6f, 1f, 1f, 0.85f, 0.6f)[i]
        val alpha = floatArrayOf(1f, 1f, 1f, 0.8f, 0.5f)[i]
        val a = Math.toRadians(p.angleDeg.toDouble())
        return MotionPose(
            dx = (cos(a) * p.distanceDp * reach).toFloat(),
            dy = (sin(a) * p.distanceDp * reach).toFloat(),
            scaleX = scale,
            scaleY = scale,
            rotation = p.angleDeg * 0.3f * i,
            alpha = alpha,
        )
    }
}

/* ------------------------------------------------------------------------------------------------
 * The hop (§8.3) and its reverse (§8.4).
 * ------------------------------------------------------------------------------------------------ */

/** Where a hopping sticker is at a given moment: its centre (window px), scale and rotation. */
@Immutable
data class HopFrame(val center: Offset, val scaleX: Float, val scaleY: Float, val rotation: Float)

object HopMotion {
    /** The apex: half way along the chord, 46 dp above it, scale .8, −4°, at 45 % of the time (55 % on the way back). */
    const val APEX_LIFT_DP: Float = 46f
    const val APEX_TIME: Float = 0.45f
    const val APEX_SCALE: Float = 0.8f
    const val APEX_ROTATION: Float = -4f

    /** The sticker ends at .48 of its row size: a basket mini. */
    const val END_SCALE: Float = 0.48f

    /** The peel's last pose, where the hop starts from. */
    const val START_ROTATION: Float = -12f

    private val easing: Easing = PicnicMotion.hop.easing

    /** [personality]'s hop length, ms. */
    fun duration(personality: Personality): Long = Beats.hopMillis(personality)

    /**
     * The hop from [from] (the row sticker's centre) to [mouth] (the basket's mouth), both window px, [elapsed] ms after
     * it started, with [personality]'s poses (§8.3): berry lands at 66 % and bounces +10 dp at 78 % and +4 dp at 94 %;
     * egg wobbles ±10° before landing (70 %, 84 %); bread puffs to 1.08 in the first 10 %; can rolls the last 10 dp
     * turning 120°; frozen shivers ±1° in the first 16 %.
     */
    fun forward(from: Offset, mouth: Offset, elapsed: Long, personality: Personality, density: Float): HopFrame {
        val total = duration(personality).toFloat()
        val u = clamp01(elapsed / total)
        val dir = if (mouth.x >= from.x) 1f else -1f
        val rollPx = 10f * density
        // Each personality reshapes time or the target.
        val arcEnd = when (personality) {
            Personality.BERRY -> 0.66f
            Personality.CAN -> 0.9f
            else -> 1f
        }
        val target = if (personality == Personality.CAN) Offset(mouth.x - dir * rollPx, mouth.y) else mouth
        val a = clamp01(u / arcEnd)
        val arc = arc(from, target, a, APEX_TIME, 1f, END_SCALE, START_ROTATION, 0f, density)
        var cx = arc.center.x
        var cy = arc.center.y
        var sx = arc.scaleX
        var sy = arc.scaleY
        var rot = arc.rotation
        when (personality) {
            Personality.BERRY -> if (u > arcEnd) cy += berryBounce(u) * density
            Personality.EGG -> rot += eggWobble(u)
            Personality.BREAD -> if (u < 0.1f) {
                val puff = 1f + 0.08f * sin(PI.toFloat() * (u / 0.1f))
                sx *= puff
                sy *= puff
            }
            Personality.CAN -> if (u > arcEnd) {
                val r = clamp01((u - arcEnd) / (1f - arcEnd))
                cx += dir * rollPx * r
                rot += dir * 120f * r
            }
            Personality.FROZEN -> if (u < 0.16f) rot += if ((u / 0.04f).toInt() % 2 == 0) 1f else -1f
            Personality.NONE -> Unit
        }
        return HopFrame(Offset(cx, cy), sx, sy, rot)
    }

    /**
     * The un-check's hop back (§8.4): from [start] (the basket mouth, or wherever a cancelled hop was) at [startScale] to
     * [row] (the row sticker's centre) at full size, 340 ms on the reverse arc (apex at 55 %).
     */
    fun back(start: Offset, row: Offset, elapsed: Long, startScale: Float = END_SCALE, density: Float): HopFrame {
        val a = clamp01(elapsed / Beats.HOP_BACK.toFloat())
        return arc(start, row, a, 1f - APEX_TIME, startScale, 1f, 0f, 0f, density)
    }

    /**
     * A two-segment arc like the mockup's keyframes (0 %, apex, 100 %, the hop easing in each segment): x and the chord
     * follow the eased progress, the lift is a sine over it (apex half way, 46 dp, less on a short chord).
     */
    private fun arc(
        from: Offset,
        to: Offset,
        a: Float,
        apexTime: Float,
        startScale: Float,
        endScale: Float,
        startRotation: Float,
        endRotation: Float,
        density: Float,
    ): HopFrame {
        val p: Float
        val scale: Float
        val rot: Float
        if (a <= apexTime) {
            val l = easing.transform(clamp01(a / apexTime))
            p = 0.5f * l
            scale = lerp(startScale, APEX_SCALE, l)
            rot = lerp(startRotation, APEX_ROTATION, l)
        } else {
            val l = easing.transform(clamp01((a - apexTime) / (1f - apexTime)))
            p = 0.5f + 0.5f * l
            scale = lerp(APEX_SCALE, endScale, l)
            rot = lerp(APEX_ROTATION, endRotation, l)
        }
        val chord = hypot(to.x - from.x, to.y - from.y)
        val lift = min(APEX_LIFT_DP * density, chord * 0.75f + 18f * density) * sin(PI.toFloat() * p)
        val x = lerp(from.x, to.x, p)
        val y = lerp(from.y, to.y, p) - lift
        return HopFrame(Offset(x, y), scale, scale, rot)
    }

    /** Berry: +10 dp at 78 %, back at 87 %, +4 dp at 94 %, back at 100 % (negative = up, dp). */
    private fun berryBounce(u: Float): Float {
        val out = CubicBezierEasing(0f, 0f, 0.58f, 1f)
        val inn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
        return when {
            u < 0.78f -> -10f * out.transform((u - 0.66f) / 0.12f)
            u < 0.87f -> -10f * (1f - inn.transform((u - 0.78f) / 0.09f))
            u < 0.94f -> -4f * out.transform((u - 0.87f) / 0.07f)
            else -> -4f * (1f - inn.transform(clamp01((u - 0.94f) / 0.06f)))
        }
    }

    /** Egg: +10° at 70 %, −10° at 84 %, settled at 100 %. */
    private fun eggWobble(u: Float): Float = when {
        u < 0.55f -> 0f
        u < 0.70f -> lerp(0f, 10f, (u - 0.55f) / 0.15f)
        u < 0.84f -> lerp(10f, -10f, (u - 0.70f) / 0.14f)
        else -> lerp(-10f, 0f, clamp01((u - 0.84f) / 0.16f))
    }
}

/* ------------------------------------------------------------------------------------------------
 * The basket: landing, wobble, heart, badge, lid, lid hanko, hop (§5.3, §8.3, §8.4).
 * ------------------------------------------------------------------------------------------------ */

object BasketMotion {
    /** The landing squash of the newest mini (pivot: its bottom centre): 1.12 / .86 → .96 / 1.05 → 1, 2 frames, 160 ms. */
    fun squash(elapsed: Long): Pair<Float, Float> = when {
        elapsed < 0 -> 1f to 1f
        elapsed < 80 -> 1.12f to 0.86f
        elapsed < 160 -> 0.96f to 1.05f
        else -> 1f to 1f
    }

    /** The wobble about 50 % / 90 %: −6, +5, −2, 0°, 4 steps, 360 ms. */
    fun wobble(elapsed: Long): Float = when {
        elapsed < 0 -> 0f
        elapsed < 90 -> -6f
        elapsed < 180 -> 5f
        elapsed < 270 -> -2f
        else -> 0f
    }

    const val WOBBLE: Long = 360
    val wobblePivotX: Float = 0.5f
    val wobblePivotY: Float = 0.9f

    /** The 14 dp sakura heart rising off the rim: 14 → 24 dp, .4 → 1 → .9, fading, 4 steps, 600 ms. Null = gone. */
    fun heart(elapsed: Long): MotionPose? {
        val i = heldStep(elapsed, 150, 4)
        if (i < 0 || elapsed >= 600) return null
        val rise = floatArrayOf(14f, 17.3f, 20.7f, 24f)[i]
        val scale = floatArrayOf(0.4f, 1f, 0.95f, 0.9f)[i]
        val alpha = floatArrayOf(1f, 1f, 0.65f, 0.3f)[i]
        return MotionPose(dy = -rise, scaleX = scale, scaleY = scale, alpha = alpha)
    }

    /** The badge pop on a count change: 0 → 1.4 → 1, 3 steps, 300 ms. */
    fun badgePop(elapsed: Long): Float = when {
        elapsed < 0 -> 1f
        elapsed < 100 -> 0.5f
        elapsed < 200 -> 1.4f
        else -> 1f
    }

    /** The lid shutting from the bottom: scaleY .2 → .7 → 1, 2 frames, 160 ms. */
    fun lidShut(elapsed: Long): Float = when {
        elapsed < 0 -> 0f
        elapsed < 80 -> 0.2f
        elapsed < 160 -> 0.7f
        else -> 1f
    }

    /** The lid re-opening (un-check): .7 → .2 → gone (the open lid and the minis show again), 2 frames, 160 ms. */
    fun lidOpen(elapsed: Long): Float = when {
        elapsed < 0 -> 1f
        elapsed < 80 -> 0.7f
        elapsed < 160 -> 0.2f
        else -> 0f
    }

    /** The GOT IT slamming onto the lid: 1.6 → 1.2 → 1, 2 steps, 200 ms. 0 before it starts. */
    fun lidHanko(elapsed: Long): Float = when {
        elapsed < 0 -> 0f
        elapsed < 100 -> 1.6f
        elapsed < 200 -> 1.2f
        else -> 1f
    }

    /** The GOT IT lifting off the lid (un-check): 1.1 then gone, 2 frames, 160 ms. */
    fun lidHankoLift(elapsed: Long): Float = when {
        elapsed < 0 -> 1f
        elapsed < 80 -> 1.1f
        elapsed < 160 -> 0.5f
        else -> 0f
    }

    /** The basket's hop as its lid shuts: −5 dp, a squash, then rest (3 steps, 360 ms). */
    fun hop(elapsed: Long): MotionPose = when {
        elapsed < 0 -> MotionPose.Rest
        elapsed < 120 -> MotionPose(dy = -5f, scaleX = 0.97f, scaleY = 1.04f)
        elapsed < 240 -> MotionPose(dy = 0f, scaleX = 1.06f, scaleY = 0.92f)
        else -> MotionPose.Rest
    }

    /** The ambient peek: one mini rises 3 dp (2 steps, 500 ms). */
    fun ambientPeek(elapsed: Long): Float = when {
        elapsed < 0 -> 0f
        elapsed < 250 -> -1.5f
        elapsed < 500 -> -3f
        else -> 0f
    }
}

/* ------------------------------------------------------------------------------------------------
 * The stamp, the curl, the fold and the ribbon (§8.4, §8.5).
 * ------------------------------------------------------------------------------------------------ */

object StampMotion {
    /** The pictogram thunking in: 1.6 → 1.25 → 1, −30° → −16° → −10°, 3 steps, 300 ms. Rests at 1, −10°. */
    fun thunk(elapsed: Long): MotionPose = when {
        elapsed < 0 -> MotionPose.Gone
        elapsed < 100 -> MotionPose(scale = 1.6f, rotation = -30f)
        elapsed < 200 -> MotionPose(scale = 1.25f, rotation = -16f)
        else -> MotionPose(scale = 1f, rotation = -10f)
    }

    const val THUNK: Long = 300

    /** The 4-pictogram burst in the aisle trace round the stamp (3 steps, 300 ms): offset (dp), scale, alpha; null = gone. */
    fun burst(index: Int, elapsed: Long): MotionPose? {
        val i = heldStep(elapsed, 100, 3)
        if (i < 0 || elapsed >= 300) return null
        val angle = Math.toRadians((-45.0 + 90.0 * index))
        val reach = floatArrayOf(10f, 15f, 18f)[i]
        val scale = floatArrayOf(0.7f, 1f, 0.7f)[i]
        val alpha = floatArrayOf(1f, 1f, 0.5f)[i]
        return MotionPose(dx = (cos(angle) * reach).toFloat(), dy = (sin(angle) * reach).toFloat(), scaleX = scale, scaleY = scale, alpha = alpha)
    }
}

object CurlMotion {
    /** The tray's bottom-right corner curling back like a page: 4 frames, 300 ms. The fold line's reach (0..1). */
    fun at(elapsed: Long): Float = when {
        elapsed < 0 -> 0f
        elapsed < 75 -> 0.18f
        elapsed < 150 -> 0.4f
        elapsed < 225 -> 0.66f
        else -> 0.9f
    }

    const val DURATION: Long = 300
}

object FoldMotion {
    /** strip ↔ tray (§8.5): opacity .2 → 1, scaleY .6 → 1 from the top, 240 ms `layout`. */
    fun unfold(elapsed: Long): Pair<Float, Float> {
        val t = clamp01(elapsed / PicnicMotion.layout.durationMillis.toFloat())
        val e = PicnicMotion.layout.easing.transform(t)
        return lerp(0.2f, 1f, e) to lerp(0.6f, 1f, e)
    }

    val DURATION: Long = PicnicMotion.layout.durationMillis.toLong()

    /** The chevron turns 180° with the fold. */
    fun chevron(elapsed: Long, opening: Boolean): Float {
        val e = PicnicMotion.layout.easing.transform(clamp01(elapsed / PicnicMotion.layout.durationMillis.toFloat()))
        return if (opening) lerp(0f, 180f, e) else lerp(180f, 0f, e)
    }
}

object RibbonMotion {
    /** The ribbon ties down: scaleY 0 → 1, 3 steps, 300 ms. */
    fun tie(elapsed: Long): Float = when {
        elapsed < 0 -> 0f
        elapsed < 100 -> 0.33f
        elapsed < 200 -> 0.67f
        else -> 1f
    }

    /** The bow pops: .5 → 1.25 → 1 (300 ms). 0 before it. */
    fun bow(elapsed: Long): Float = when {
        elapsed < 0 -> 0f
        elapsed < 100 -> 0.5f
        elapsed < 200 -> 1.25f
        else -> 1f
    }

    /** The ribbon unties (un-check): 1 → .5 → gone, 2 frames, 160 ms. */
    fun untie(elapsed: Long): Float = when {
        elapsed < 0 -> 1f
        elapsed < 80 -> 0.6f
        elapsed < 160 -> 0.25f
        else -> 0f
    }
}

/* ------------------------------------------------------------------------------------------------
 * The book (§7.6): the magic appear, the kira bloom, the sparks, the peel off, the page turn and lift.
 * ------------------------------------------------------------------------------------------------ */

object BookMotion {
    /** The book's hand-stuck tilts, by slot % 12 (§7.3). */
    val ROTS: IntArray = intArrayOf(-6, 4, -2, 7, -5, 3, 6, -4, 2, -7, 5, -3)

    fun tilt(slot: Int): Float = ROTS[Math.floorMod(slot, ROTS.size)].toFloat()

    /**
     * The twin in 4 stepped poses: .2 at −16° (0) → 1.3 at −8° (+64) → .86 at +5° (+144) → pressed .95 offset
     * (+0.8, +1.2) dp (+224) → its tilt at 1.0 (+320).
     */
    fun twin(elapsed: Long, slot: Int): MotionPose = when {
        elapsed < 0 -> MotionPose.Gone
        elapsed < 64 -> MotionPose(scale = 0.2f, rotation = -16f)
        elapsed < 144 -> MotionPose(scale = 1.3f, rotation = -8f)
        elapsed < 224 -> MotionPose(scale = 0.86f, rotation = 5f)
        elapsed < 320 -> MotionPose(scale = 0.95f, rotation = tilt(slot), dx = 0.8f, dy = 1.2f)
        else -> MotionPose(scale = 1f, rotation = tilt(slot))
    }

    /** The 30 dp kira star blooming: 1.4 → .9 → .5 → .2 while turning −30° → +30°, 4 steps, 400 ms. Null = gone. */
    fun bloom(elapsed: Long): MotionPose? {
        val i = heldStep(elapsed, 100, 4)
        if (i < 0 || elapsed >= 400) return null
        return MotionPose(scale = floatArrayOf(1.4f, 0.9f, 0.5f, 0.2f)[i], rotation = floatArrayOf(-30f, -10f, 10f, 30f)[i])
    }

    /** The six sparks (star4, heart, star, star4, flower, star) flying 13 dp out from the spot's edge. */
    val sparkKinds: List<String> = listOf("star4", "heart", "star", "star4", "flower", "star")

    /** Spark [index]'s offset from the spot centre (dp), scale and alpha at [elapsed] (4 steps, 400 ms); null = gone. */
    fun spark(index: Int, elapsed: Long, inward: Boolean = false): MotionPose? {
        val i = heldStep(elapsed, if (inward) 24 else 100, 4)
        val end = if (inward) 96L else 400L
        if (i < 0 || elapsed >= end) return null
        val reach = if (inward) floatArrayOf(19.5f, 15f, 11f, 7f)[i] else floatArrayOf(9f, 13f, 16.5f, 19.5f)[i]
        val scale = floatArrayOf(0.6f, 1f, 0.8f, 0.5f)[i]
        val alpha = floatArrayOf(1f, 1f, 0.8f, 0.4f)[i]
        val a = Math.toRadians(-90.0 + 60.0 * index)
        return MotionPose(dx = (cos(a) * reach).toFloat(), dy = (sin(a) * reach).toFloat(), scaleX = scale, scaleY = scale, alpha = alpha)
    }

    /**
     * The peel off (un-check): the corner lifts (−1, −2 dp, −12°, skewX −8°) at +64 → rises to 1.12 (−16°) at +144 →
     * shrinks to .55 with 6 sparks running inward at +224 → gone at +320.
     */
    fun peel(elapsed: Long, slot: Int): MotionPose = when {
        elapsed < 0 -> MotionPose(scale = 1f, rotation = tilt(slot))
        elapsed < 64 -> MotionPose(scale = 1f, rotation = tilt(slot))
        elapsed < 144 -> MotionPose(dx = -1f, dy = -2f, rotation = -12f, skewX = -8f)
        elapsed < 224 -> MotionPose(scale = 1.12f, rotation = -16f, dy = -2f)
        elapsed < 320 -> MotionPose(scale = 0.55f, rotation = -16f, dy = -1f)
        else -> MotionPose.Gone
    }

    /** When the peel's inward sparks start (+224). */
    const val PEEL_SPARKS_AT: Long = 224

    /** The right page lifting 2 dp with a magic appear (2 steps, 160 ms): its offset in dp. */
    fun lift(elapsed: Long): Float = when {
        elapsed < 0 -> 0f
        elapsed < 80 -> -2f
        elapsed < 160 -> -1f
        else -> 0f
    }

    /**
     * The page turn about the spine (3 frames, 240 ms): rotateY −70° on the old page, then +70° and 0° on the new one.
     * Returns (rotationY, showsNewPage).
     */
    fun pageTurn(elapsed: Long): Pair<Float, Boolean> = when {
        elapsed < 0 -> 0f to false
        elapsed < 80 -> -70f to false
        elapsed < 160 -> 70f to true
        else -> 0f to true
    }

    /** PER-FECT! slamming across the spine: 2.2 → 1.25 → 1, −30° → −12°, 3 steps, 260 ms. */
    fun perfect(elapsed: Long): MotionPose = when {
        elapsed < 0 -> MotionPose.Gone
        elapsed < 87 -> MotionPose(scale = 2.2f, rotation = -30f)
        elapsed < 174 -> MotionPose(scale = 1.25f, rotation = -21f)
        else -> MotionPose(scale = 1f, rotation = -12f)
    }
}

/* ------------------------------------------------------------------------------------------------
 * The celebration's entrance (§11.2): the heart iris from the last tapped checkbox, with a kira bloom on it.
 * ------------------------------------------------------------------------------------------------ */

object HeartIris {
    /** One held pose per 12 fps frame (§8.1, `pose`). */
    val POSE: Long = PicnicMotion.pose.toLong()

    /**
     * The heart's width (dp) in its held poses from the scene's start: the first already reads as the scene arriving,
     * and none but the whole screen reaches the top bar from a checkbox in the lower part of the screen.
     */
    val WIDTHS: List<Float> = listOf(180f, 400f, 900f)

    /**
     * The heart's width (dp) at [elapsed] ms from the scene's start; 0 before the start; null from
     * CelebrationTimeline.IRIS (the three poses, 249 ms), when the scene has the whole screen and is no longer clipped.
     */
    fun width(elapsed: Long): Float? = when {
        elapsed < 0 -> 0f
        elapsed >= CelebrationTimeline.IRIS -> null
        else -> WIDTHS[heldStep(elapsed, POSE, WIDTHS.size)]
    }
}

/* ------------------------------------------------------------------------------------------------
 * The Next card (§10).
 * ------------------------------------------------------------------------------------------------ */

object NextCardMotion {
    /** The card rises 400 ms; it is still from then. */
    val RISE: Long = PicnicMotion.cardIn.durationMillis.toLong()

    /** It exits downward in 300 ms. */
    val EXIT: Long = PicnicMotion.cardOut.durationMillis.toLong()

    /** Settled at rest at −1°. */
    const val REST_ROTATION: Float = -1f

    /**
     * Stacked over a toast (§4.15), the card enters and leaves in its reduced form, a 120 ms fade in place: the 8 dp gap
     * above the toast, less both pieces' tilted corners, leaves no room for a rise that could not touch it.
     */
    const val STACKED_SHIFT_DP: Float = 0f

    /**
     * The rise: translateY(100 % + 130 dp) at 4° → −8 dp at −1.5° (75 %) → rest at −1°, the cardIn easing in each
     * segment. Returns (translateY as (fraction of the card's own height, dp), rotation).
     */
    fun rise(elapsed: Long): Triple<Float, Float, Float> {
        val u = clamp01(elapsed / RISE.toFloat())
        val ease = PicnicMotion.cardIn.easing
        return if (u <= 0.75f) {
            val l = ease.transform(u / 0.75f)
            Triple(lerp(1f, 0f, l), lerp(130f, -8f, l), lerp(4f, -1.5f, l))
        } else {
            val l = ease.transform((u - 0.75f) / 0.25f)
            Triple(0f, lerp(-8f, 0f, l), lerp(-1.5f, REST_ROTATION, l))
        }
    }

    /** The exit downward (300 ms, cardOut): (fraction of height, dp, rotation). */
    fun exit(elapsed: Long): Triple<Float, Float, Float> {
        val l = PicnicMotion.cardOut.easing.transform(clamp01(elapsed / EXIT.toFloat()))
        return Triple(lerp(0f, 1f, l), lerp(0f, 130f, l), lerp(REST_ROTATION, 3f, l))
    }

    /** True once the card no longer moves (its hold has begun). */
    fun settled(elapsed: Long, reduced: Boolean): Boolean = elapsed >= if (reduced) Beats.RM_FADE else RISE
}

/** A hop path never doubles back sideways faster than this (tests). */
internal fun HopFrame.distanceTo(o: Offset): Float = hypot(center.x - o.x, center.y - o.y)

internal fun sameSign(a: Float, b: Float): Boolean = sign(a) == sign(b) || abs(a) < 1e-3f || abs(b) < 1e-3f

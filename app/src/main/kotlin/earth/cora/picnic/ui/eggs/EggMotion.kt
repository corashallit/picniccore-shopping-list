package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The eggs' poses (docs/DESIGN_STICKER.md §12.2, §13.2), pure functions of elapsed frame-clock time so the overlays,
 * the strips and the tests read the same numbers. Stickers move in stepped poses (§8.1: 12 fps, sparkles 8 fps).
 * Geometry is in px (the overlay's own coordinates) with the density passed in. Owner: WP9.
 */

/** One overlay sticker's pose: its centre, rotation (degrees, clockwise-positive like Compose's rotationZ), scale, alpha. */
@Immutable
data class EggPose(
    val center: Offset,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = scaleX,
    val alpha: Float = 1f,
)

/** The time of the stepped pose [elapsed] falls in, at [stepMs] per pose. */
internal fun stepped(elapsed: Long, stepMs: Long): Long = if (elapsed <= 0) 0 else (elapsed / stepMs) * stepMs

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

/** 12 fps (§8.2 `pose`). */
internal const val POSE_MS: Long = 83

/** 8 fps (sparkles, petals). */
internal const val SPARKLE_MS: Long = 125

/* ------------------------------------------------------------------------------------------------
 * Egg 3: rolling produce. θ = Δx / r: one full turn per 2πr of sideways travel, clockwise while it moves right,
 * anticlockwise while it moves left, and no turn at all while it only rises or drops (Cora, 2 October; item 335).
 * ------------------------------------------------------------------------------------------------ */

/** Egg 3's travel, laid out once from the anchors when it starts (px). */
@Immutable
data class RollingPath(
    /** Where it pops out of the add bar (the sticker's centre). */
    val launch: Offset,
    /** The lane it rolls along to the basket (centre height) and the basket mouth's x. */
    val laneY: Float,
    val basketX: Float,
    /** Its own row's sticker centre, where it settles. */
    val row: Offset,
    /** The rolling radius (22 dp). */
    val radius: Float,
    /** Berries hop in arcs instead of rolling. */
    val hop: Boolean,
    val density: Float,
)

object RollingMotion {
    /** The sticker is 44 dp; it rolls on a 22 dp radius: one full turn per 2π × 22 ≈ 138 dp. */
    const val SIZE_DP: Float = 44f
    const val RADIUS_DP: Float = 22f

    // Beats (ms from the commit): the toss out of the add bar, the roll to the basket, the bump, the drop onto its line,
    // the roll home, the settle. The basket is reached well inside the 3 s, and it is settled on its line by 3.2 s even
    // when its row takes a few frames to be placed (ROW_WAIT_FRAMES).
    const val RISE_END: Long = 300
    const val ROLL_END: Long = 1500
    const val BUMP_END: Long = 1950
    const val DROP_END: Long = 2150
    const val HOME_END: Long = 2920
    const val SETTLE_END: Long = 3040

    /** The bump happens here (the basket wobbles hello, a rim heart, "pff"). */
    const val BUMP_AT: Long = ROLL_END

    /** The whole travel (the overlay is done; the row's own sticker shows again). */
    const val DURATION: Long = SETTLE_END

    /** Frames to wait for the new row to be placed before rolling to the folded strip it joins instead (~200 ms). */
    const val ROW_WAIT_FRAMES: Int = 12

    /** Frames after which, with no row, strip, basket or add bar placed, it gives up (the item is simply on its line). */
    const val GIVE_UP_FRAMES: Int = 90

    /**
     * [p] with its row and basket where they are now ([row], [mouth]; null = where they were): the list may scroll its
     * aisle into view while the sticker travels. The launch point stays put, so the turn still follows the sideways
     * travel exactly (θ = Δx ⁄ r).
     */
    fun follow(p: RollingPath, row: Offset?, mouth: Offset?): RollingPath = p.copy(
        row = row ?: p.row,
        laneY = mouth?.let { it.y - p.radius * 0.6f } ?: p.laneY,
        basketX = mouth?.x ?: p.basketX,
    )

    /**
     * The path for a sticker whose row sticker sits at [row] and whose basket mouth is at [mouth], launched from the add
     * bar whose top edge is at [addBarTop], on a screen [width] wide. The launch x is chosen one full turn (2πr) to the
     * right of the row, so that rolling right to the basket and then left home leaves it upright exactly where its row
     * sticker is: Δx home − launch = −2πr, θ = −360°. Clamped inside the screen, it uses two turns or none instead.
     */
    fun path(row: Offset, mouth: Offset, addBarTop: Float, width: Float, density: Float, hop: Boolean): RollingPath {
        val r = RADIUS_DP * density
        val turn = (2 * PI * r).toFloat()
        val margin = r + 6 * density
        var launchX = row.x + turn
        if (launchX > width - margin) launchX = row.x
        if (launchX < margin) launchX = row.x + 2 * turn
        val laneY = mouth.y - r * 0.6f
        val launch = Offset(launchX, addBarTop - r - 2 * density)
        return RollingPath(launch, laneY, mouth.x, row, r, hop, density)
    }

    /** The pose at [elapsed] ms (stepped at 12 fps); null once settled. */
    fun at(p: RollingPath, elapsed: Long): EggPose? {
        if (elapsed >= DURATION) return null
        val t = stepped(elapsed, POSE_MS)
        return if (p.hop) hopAt(p, t) else rollAt(p, t)
    }

    /** θ (degrees) for a centre at x: the turn its sideways travel from the launch point makes, without slipping. */
    fun theta(p: RollingPath, x: Float): Float = ((x - p.launch.x) / p.radius * 180.0 / PI).toFloat()

    private fun rollAt(p: RollingPath, t: Long): EggPose {
        val bounceDy = 6 * p.density
        return when {
            t < RISE_END -> {
                // Tossed straight up out of the add bar: no sideways travel, so no turn (it keeps its angle).
                val f = easeOut(t.toFloat() / RISE_END)
                val c = Offset(p.launch.x, lerp(p.launch.y, p.laneY, f))
                EggPose(c, theta(p, c.x), scaleX = lerp(0.6f, 1f, clamp01(t / 160f)))
            }
            t < ROLL_END -> {
                val f = (t - RISE_END).toFloat() / (ROLL_END - RISE_END)
                val c = Offset(lerp(p.launch.x, p.basketX, f), p.laneY)
                EggPose(c, theta(p, c.x))
            }
            t < BUMP_END -> {
                // The bump: up 6 dp and squashed (1.1 / .9), then .95 / 1.05, then rest; it only moves up and down.
                val i = ((t - BUMP_AT) * 3 / (BUMP_END - BUMP_AT)).toInt().coerceIn(0, 2)
                val c = Offset(p.basketX, p.laneY - if (i == 0) bounceDy else 0f)
                val (sx, sy) = when (i) {
                    0 -> 1.1f to 0.9f
                    1 -> 0.95f to 1.05f
                    else -> 1f to 1f
                }
                EggPose(c, theta(p, c.x), sx, sy)
            }
            t < DROP_END -> {
                // Drops onto its own line: straight down, no turn.
                val f = easeIn((t - BUMP_END).toFloat() / (DROP_END - BUMP_END))
                EggPose(Offset(p.basketX, lerp(p.laneY, p.row.y, f)), theta(p, p.basketX))
            }
            t < HOME_END -> {
                val f = easeOut((t - DROP_END).toFloat() / (HOME_END - DROP_END))
                val c = Offset(lerp(p.basketX, p.row.x, f), p.row.y)
                EggPose(c, theta(p, c.x))
            }
            else -> {
                // Settles on its line with a little squash (1.12 / .88 → 1).
                val i = ((t - HOME_END) / POSE_MS).toInt()
                EggPose(p.row, theta(p, p.row.x), if (i == 0) 1.12f else 1f, if (i == 0) 0.88f else 1f)
            }
        }
    }

    /** Berries hop in three arcs to the basket (apexes 40, 34, 26 dp; tilts −8°, +8°, −6°), bump, then hop home. */
    private fun hopAt(p: RollingPath, t: Long): EggPose {
        val d = p.density
        val start = Offset(min(p.launch.x, p.basketX - 150 * d).coerceAtLeast(p.radius + 6 * d), p.laneY)
        return when {
            t < RISE_END -> {
                val f = easeOut(t.toFloat() / RISE_END)
                EggPose(Offset(start.x, lerp(p.launch.y, p.laneY, f)), scaleX = lerp(0.6f, 1f, clamp01(t / 160f)))
            }
            t < ROLL_END -> {
                val f = (t - RISE_END).toFloat() / (ROLL_END - RISE_END)
                val hop = min(2, floor(f * 3).toInt())
                val local = f * 3 - hop
                val apex = floatArrayOf(40f, 34f, 26f)[hop] * d
                val tilt = floatArrayOf(-8f, 8f, -6f)[hop]
                val x = lerp(start.x, p.basketX, f)
                val y = p.laneY - apex * 4 * local * (1 - local)
                EggPose(Offset(x, y), tilt * (1 - kotlin.math.abs(local * 2 - 1)))
            }
            t < BUMP_END -> {
                val i = ((t - BUMP_AT) * 3 / (BUMP_END - BUMP_AT)).toInt().coerceIn(0, 2)
                val (sx, sy) = when (i) {
                    0 -> 1.1f to 0.9f
                    1 -> 0.95f to 1.05f
                    else -> 1f to 1f
                }
                EggPose(Offset(p.basketX, p.laneY - if (i == 0) 6 * d else 0f), 0f, sx, sy)
            }
            t < HOME_END -> {
                val f = (t - BUMP_END).toFloat() / (HOME_END - BUMP_END)
                val x = lerp(p.basketX, p.row.x, f)
                val base = lerp(p.laneY, p.row.y, f)
                EggPose(Offset(x, base - 30 * d * 4 * f * (1 - f)), -6f * (1 - kotlin.math.abs(f * 2 - 1)))
            }
            else -> EggPose(p.row, 0f, 1.12f, 0.88f)
        }
    }

    private fun easeOut(t: Float) = 1 - (1 - clamp01(t)) * (1 - clamp01(t))

    private fun easeIn(t: Float) = clamp01(t) * clamp01(t)
}

/* ------------------------------------------------------------------------------------------------
 * Egg 2: the ladybug's stroll along a folded strip's bottom edge.
 * ------------------------------------------------------------------------------------------------ */

object LadybugMotion {
    /** 5 s on the strip, 10 % per hop (18 dp each), a pause at 40–60 %, then 600 ms flying off. */
    const val WALK_MS: Long = 5000
    const val FLY_MS: Long = 600
    const val DURATION: Long = WALK_MS + FLY_MS
    const val HOP_DP: Float = 18f
    const val SIZE_DP: Float = 15f
    const val WADDLE_MS: Long = 250

    /** Hops made by [elapsed]: one per 10 % of the walk, none during the 40–60 % pause (8 hops in all). */
    fun hops(elapsed: Long): Int {
        val tenth = (elapsed.coerceIn(0, WALK_MS) * 10 / WALK_MS).toInt()
        return when {
            tenth <= 4 -> tenth
            tenth <= 6 -> 4
            else -> tenth - 2
        }.coerceAtMost(8)
    }

    /**
     * The pose at [elapsed] for a walk starting at [start] (the bug's centre on the strip's bottom edge), heading right:
     * stepped hops with a 2-frame waddle (±5° every 250 ms; still while paused), then the wings open and it flies up and
     * off to the right, fading. Rotation 90° = facing right (the sticker's head points up). Null once gone.
     */
    fun at(start: Offset, elapsed: Long, density: Float): Pair<EggPose, Boolean>? {
        if (elapsed >= DURATION) return null
        if (elapsed < WALK_MS) {
            val x = start.x + hops(elapsed) * HOP_DP * density
            val tenth = elapsed * 10 / WALK_MS
            val paused = tenth in 4..5
            val waddle = if (paused) 0f else if ((elapsed / WADDLE_MS) % 2 == 0L) -5f else 5f
            return EggPose(Offset(x, start.y), 90f + waddle) to false
        }
        val f = stepped(elapsed - WALK_MS, POSE_MS).toFloat() / FLY_MS
        val x = start.x + 8 * HOP_DP * density + f * 60 * density
        val y = start.y - f * 70 * density
        return EggPose(Offset(x, y), 60f, alpha = 1f - clamp01((f - 0.5f) * 2)) to true
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 4: petals in the gutters.
 * ------------------------------------------------------------------------------------------------ */

object PetalMotion {
    const val COUNT: Int = 14
    const val SCATTER_MS: Long = 625
    const val REST_MS: Long = 3000
    const val BLOW_MS: Long = 750
    const val DURATION: Long = SCATTER_MS + REST_MS + BLOW_MS

    /** The whole scatter, the last petal's delay included. */
    const val TOTAL: Long = DURATION + (COUNT / 2 - 1) * 40L

    @Immutable
    data class Petal(val spot: Offset, val fromLeft: Boolean, val rotation: Float, val delay: Long)

    /**
     * Fourteen spots, seven per gutter, spread down the screen between [top] and [bottom] (px), centred [inset] px in
     * from each edge of a [width]-wide screen. Seeded, so a test and a golden see the same petals.
     */
    fun petals(width: Float, top: Float, bottom: Float, inset: Float, seed: Int = 4): List<Petal> {
        var s = seed.toLong() and 0x7fffffff
        fun next(): Float {
            s = (s * 1103515245L + 12345L) and 0x7fffffff
            return (s % 10_000).toFloat() / 10_000f
        }
        val perSide = COUNT / 2
        val span = max(1f, bottom - top)
        return List(COUNT) { i ->
            val left = i % 2 == 0
            val k = i / 2
            val y = top + span * (k + 0.2f + 0.6f * next()) / perSide
            val x = if (left) inset else width - inset
            Petal(Offset(x, y), left, (next() * 140f - 70f), delay = (k * 40L))
        }
    }

    /**
     * A petal's pose at [elapsed]: it flutters in from its own screen edge to its spot (5 poses at 8 fps), rests there as a
     * tiny sticker for 3 s, then blows away back out over its edge (6 poses), turning. Null once gone or not yet in.
     */
    fun at(p: Petal, elapsed: Long, density: Float): EggPose? {
        val t = elapsed - p.delay
        if (t < 0 || t >= DURATION) return null
        val out = if (p.fromLeft) -24f * density else 24f * density
        val edgeX = p.spot.x + out
        return when {
            t < SCATTER_MS -> {
                val f = stepped(t, SPARKLE_MS).toFloat() / SCATTER_MS
                EggPose(Offset(lerp(edgeX, p.spot.x, f), p.spot.y - (1 - f) * 18 * density), p.rotation - (1 - f) * 90f, alpha = clamp01(f * 3))
            }
            t < SCATTER_MS + REST_MS -> EggPose(p.spot, p.rotation)
            t < DURATION -> {
                val f = stepped(t - SCATTER_MS - REST_MS, SPARKLE_MS).toFloat() / BLOW_MS
                EggPose(Offset(lerp(p.spot.x, edgeX + out, f), p.spot.y - f * 40 * density), p.rotation + f * 160f, alpha = 1f - clamp01(f - 0.4f))
            }
            else -> null
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 17: the ant parade along the add bar's perforation.
 * ------------------------------------------------------------------------------------------------ */

object AntMotion {
    const val COUNT: Int = 5
    const val STEPS: Int = 21
    const val MARCH_MS: Long = 4200
    const val APART_MS: Long = 420
    const val DURATION: Long = MARCH_MS + APART_MS * (COUNT - 1)

    /** The middle ant (index 2) carries the strawberry and is 30 dp; the others carry crumbs at 22 dp. */
    fun sizeDp(index: Int): Float = if (index == COUNT / 2) 30f else 22f

    fun carriesStrawberry(index: Int): Boolean = index == COUNT / 2

    /**
     * Ant [index]'s pose at [elapsed]: from just past the right edge ([width] + its size) to just past the left, right to
     * left in 21 steps of 200 ms (2-frame steps: a little lift on odd steps), its feet on the perforation at [edgeY]. Null
     * before it sets off and once it has left.
     */
    fun at(index: Int, elapsed: Long, width: Float, edgeY: Float, density: Float): EggPose? {
        val t = elapsed - index * APART_MS
        if (t < 0 || t >= MARCH_MS) return null
        val size = sizeDp(index) * density
        val step = (t * STEPS / MARCH_MS).toInt()
        val from = width + size
        val to = -size
        val x = lerp(from, to, (step + 1).toFloat() / STEPS)
        val lift = if (step % 2 == 1) 1.5f * density else 0f
        return EggPose(Offset(x, edgeY - size * 0.42f - lift), if (step % 2 == 1) -4f else 2f)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 9: the confetti rain (lanes avoid the text column; text keep-outs clip the rest).
 * ------------------------------------------------------------------------------------------------ */

object ConfettiMotion {
    const val COUNT: Int = 26
    const val RAIN_MS: Long = 2000
    val kinds: List<String> = listOf("heart", "star4", "petal", "star", "cake", "flower")

    @Immutable
    data class Piece(val kind: String, val x: Float, val sizeDp: Float, val delay: Long, val fallMs: Long, val spin: Float, val sway: Float)

    /**
     * 26 die-cut confetti stickers (18–26 dp), each falling 1.3–1.8 s, all started inside the 2 s, in the side lanes of a
     * [width]-wide screen: the gutter and sticker column on the left, the checkbox column on the right (§4.11), so they
     * fall beside the text, never over it.
     */
    fun pieces(width: Float, density: Float, seed: Int = 9): List<Piece> {
        var s = seed.toLong() and 0x7fffffff
        fun next(): Float {
            s = (s * 1103515245L + 12345L) and 0x7fffffff
            return (s % 10_000).toFloat() / 10_000f
        }
        val lane = 52 * density
        return List(COUNT) { i ->
            val left = i % 2 == 0
            val x = if (left) 4 * density + next() * lane else width - 4 * density - next() * lane
            val fall = 1300L + (next() * 500).toLong()
            Piece(kinds[i % kinds.size], x, 18f + next() * 8f, ((RAIN_MS - fall) * next()).toLong(), fall, (if (next() < 0.5f) -1 else 1) * (140f + next() * 180f), (next() - 0.5f) * 16f)
        }
    }

    /** A piece's pose at [elapsed]: falling from above [top] to below [bottom] in 10 steps, turning. */
    fun at(p: Piece, elapsed: Long, top: Float, bottom: Float, density: Float): EggPose? {
        val t = elapsed - p.delay
        if (t < 0 || t >= p.fallMs) return null
        val f = stepped(t, p.fallMs / 10).toFloat() / p.fallMs
        val size = p.sizeDp * density
        val y = lerp(top - size, bottom + size, f)
        return EggPose(Offset(p.x + p.sway * density * kotlin.math.sin(f * 6f), y), p.spin * f)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 13: JACK / POT!
 * ------------------------------------------------------------------------------------------------ */

object JackpotMotion {
    const val WOBBLE_MS: Long = 600
    const val POP_MS: Long = 300
    const val SLAM_AT: Long = 650
    const val SLAM_MS: Long = 200
    const val HOLD_MS: Long = 1800
    const val FADE_MS: Long = 200
    const val DURATION: Long = SLAM_AT + SLAM_MS + HOLD_MS + FADE_MS

    /** The gold capsule over the row's sticker: two wobbles (3 steps each, ±10°), then the pop (1.2 / .85), then gone. */
    fun capsule(elapsed: Long): EggPose? {
        if (elapsed < WOBBLE_MS) {
            val i = (elapsed / 100).toInt() % 3
            return EggPose(Offset.Zero, floatArrayOf(-10f, 10f, 0f)[i])
        }
        val p = elapsed - WOBBLE_MS
        if (p >= POP_MS) return null
        return if (p < POP_MS / 2) EggPose(Offset.Zero, 0f, 1.2f) else EggPose(Offset.Zero, 0f, 0.85f, alpha = 0.6f)
    }

    /** The 60 dp JACK / POT! hanko: slams (1.6 → 1.2 → 1, 2 steps, 200 ms), holds 1.8 s, fades (200 ms). */
    fun hanko(elapsed: Long): EggPose? {
        val t = elapsed - SLAM_AT
        if (t < 0 || elapsed >= DURATION) return null
        return when {
            t < SLAM_MS / 2 -> EggPose(Offset.Zero, -14f, 1.6f)
            t < SLAM_MS -> EggPose(Offset.Zero, -14f, 1.2f)
            t < SLAM_MS + HOLD_MS -> EggPose(Offset.Zero, -14f, 1f)
            else -> EggPose(Offset.Zero, -14f, 1f, alpha = 1f - (t - SLAM_MS - HOLD_MS).toFloat() / FADE_MS)
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 18: the receipt printing from the LCD's slot (§13.2).
 * ------------------------------------------------------------------------------------------------ */

object ReceiptMotion {
    const val FEED_MS: Long = 1700
    const val FRAMES: Int = 14
    const val JITTER_MS: Long = 120
    const val TEAR_MS: Long = 260
    const val SETTLE_AT: Long = 1960
    const val FADE_MS: Long = 120

    /** The tucked receipt poofs (1.08 then 0, turning 10°, 4 steps, 400 ms); the slip appears 420 ms after the tap. */
    const val POOF_MS: Long = 400
    const val SLIP_AT: Long = 420

    /** How much of the paper has come out of the slot at [elapsed] (0–1): 14 stepped frames over 1.7 s. */
    fun fed(elapsed: Long): Float {
        if (elapsed >= FEED_MS) return 1f
        val frame = (elapsed * FRAMES / FEED_MS).toInt() + 1
        return frame.toFloat() / FRAMES
    }

    /** The dot-matrix jitter while it feeds: ±0.6 dp sideways, 2 frames, every 120 ms. */
    fun jitterDp(elapsed: Long): Float = if (elapsed >= FEED_MS) 0f else if ((elapsed / JITTER_MS) % 2 == 0L) -0.6f else 0.6f

    /**
     * The paper's offset (dp) and rotation after it has fed out: it tears off (2 frames over 260 ms: a little tug down
     * and a turn), then settles at (−4, +10) dp rotated 3° (§13.2).
     */
    fun settle(elapsed: Long): Triple<Float, Float, Float> = when {
        elapsed < FEED_MS -> Triple(0f, 0f, 0f)
        elapsed < FEED_MS + TEAR_MS / 2 -> Triple(-1f, 3f, 1f)
        elapsed < SETTLE_AT -> Triple(-2.5f, 6f, 2f)
        else -> Triple(-4f, 10f, 3f)
    }

    /** True while the slot line under the LCD shows (until it settles). */
    fun slotVisible(elapsed: Long): Boolean = elapsed < SETTLE_AT

    /** The poof's scale and rotation at [elapsed] after the tap (4 steps). */
    fun poof(elapsed: Long): Pair<Float, Float>? {
        if (elapsed >= POOF_MS) return null
        val i = (elapsed * 4 / POOF_MS).toInt()
        return floatArrayOf(1.08f, 0.8f, 0.45f, 0.1f)[i] to (3f + 10f * (i + 1) / 4)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 1: the sun sets, the moon rises (§12.2).
 * ------------------------------------------------------------------------------------------------ */

object DuskMotion {
    const val SINK_MS: Long = 600
    const val RISE_MS: Long = 600

    /** The six-star burst plays while the moon rises (from its second pose), so the whole change takes 1.2 s. */
    const val BURST_AT: Long = SINK_MS + RISE_MS / 3
    const val BURST_MS: Long = RISE_MS - RISE_MS / 3
    const val DURATION: Long = SINK_MS + RISE_MS
    const val UNDO_MS: Long = 720
    const val FADE_MS: Long = 120

    /** The sun's drop (dp) while it sinks behind its horizon: 3 poses over 600 ms, gone from 600. */
    fun sunDrop(elapsed: Long): Float? = if (elapsed >= SINK_MS) null else floatArrayOf(9f, 20f, 31f)[(elapsed.coerceAtLeast(0) * 3 / SINK_MS).toInt()]

    /** The moon's drop (dp) while it rises: 3 poses from 600 ms, at rest from 1.2 s; null before. */
    fun moonDrop(elapsed: Long): Float? {
        val t = elapsed - SINK_MS
        if (t < 0) return null
        if (t >= RISE_MS) return 0f
        return floatArrayOf(24f, 12f, 4f)[(t * 3 / RISE_MS).toInt()]
    }

    /** The 6-star sparkle burst around the rising moon, 800–1200 ms in 3 poses: (reach 0–1, scale), or null. */
    fun burst(elapsed: Long): Pair<Float, Float>? {
        val t = elapsed - BURST_AT
        if (t < 0 || t >= BURST_MS) return null
        val i = (t * 3 / BURST_MS).toInt()
        return floatArrayOf(0.45f, 0.8f, 1f)[i] to floatArrayOf(0.8f, 1.1f, 0.7f)[i]
    }

    /** The sticker's drop at [elapsed] in the moon-to-sun undo: the moon sinks (3 poses, 360 ms), the sun rises (360 ms). */
    fun undo(elapsed: Long): Pair<Boolean, Float> {
        val half = UNDO_MS / 2
        return if (elapsed < half) {
            true to floatArrayOf(9f, 20f, 31f)[(elapsed.coerceAtLeast(0) * 3 / half).toInt()]
        } else {
            val t = elapsed - half
            false to if (t >= half) 0f else floatArrayOf(24f, 12f, 4f)[(t * 3 / half).toInt()]
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 8: the nap cat.
 * ------------------------------------------------------------------------------------------------ */

object NapMotion {
    /** The cat curls up this long after the chain that finished the last other stop has played out (§12.3). */
    const val SETTLE_DELAY_MS: Long = 120
    const val BREATH_MS: Long = 2600
    const val Z_MS: Long = 2400
    const val STRETCH_MS: Long = 250
    const val TROT_MS: Long = 1200
    const val LEAVE_MS: Long = STRETCH_MS + TROT_MS

    /** Breathing: 1.04 and 1.07 in two poses across 2.6 s. */
    fun breath(elapsed: Long): Float = if ((elapsed % BREATH_MS) < BREATH_MS / 2) 1.04f else 1.07f

    /** The floating "z" / "Z": progress 0–1 through its 2.4 s rise (stepped at 8 fps), and which one. */
    fun z(elapsed: Long): Pair<Float, Boolean> {
        val cycle = elapsed % Z_MS
        return (stepped(cycle, SPARKLE_MS).toFloat() / Z_MS) to ((elapsed / Z_MS) % 2 == 0L)
    }

    /** Leaving: the stretch (3 frames, scaleX, scaleY), then 8 trot steps off to the right (dx dp per step). */
    fun leave(elapsed: Long, distanceDp: Float): EggPose? {
        if (elapsed >= LEAVE_MS) return null
        if (elapsed < STRETCH_MS) {
            val i = (elapsed * 3 / STRETCH_MS).toInt()
            return EggPose(Offset.Zero, 0f, floatArrayOf(1.1f, 1.18f, 1.05f)[i], floatArrayOf(0.94f, 0.88f, 1f)[i])
        }
        val step = ((elapsed - STRETCH_MS) * 8 / TROT_MS).toInt() + 1
        val hop = if (step % 2 == 0) -3f else 0f
        return EggPose(Offset(distanceDp * step / 8f, hop), if (step % 2 == 0) 4f else -2f)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Small shared beats.
 * ------------------------------------------------------------------------------------------------ */

object EggBeats {
    /** The book's magic appear (§7.6): .2 at −16° → 1.3 at −8° → .86 at +5° → 1 at 0°, 320 ms (umbrella, slip). */
    fun magicAppear(elapsed: Long): EggPose = when {
        elapsed < 64 -> EggPose(Offset.Zero, -16f, 0.2f)
        elapsed < 144 -> EggPose(Offset.Zero, -8f, 1.3f)
        elapsed < 224 -> EggPose(Offset.Zero, 5f, 0.86f)
        elapsed < 320 -> EggPose(Offset.Zero, 0f, 0.95f)
        else -> EggPose(Offset.Zero, 0f, 1f)
    }

    /** The one twinkle rule (§4.27): .82, −8° → 1.12, +6° → .95, 0°, at 8 fps; then rest. */
    fun twinkle(elapsed: Long): EggPose = when {
        elapsed < 0 -> EggPose(Offset.Zero)
        elapsed < SPARKLE_MS -> EggPose(Offset.Zero, -8f, 0.82f)
        elapsed < 2 * SPARKLE_MS -> EggPose(Offset.Zero, 6f, 1.12f)
        elapsed < 3 * SPARKLE_MS -> EggPose(Offset.Zero, 0f, 0.95f)
        else -> EggPose(Offset.Zero)
    }

    /** The rim heart (§8.3): rises 14 → 24 dp off the rim, .4 → 1 → .9, fading, 4 steps over 600 ms. */
    fun rimHeart(elapsed: Long): Triple<Float, Float, Float>? {
        if (elapsed < 0 || elapsed >= 600) return null
        val i = (elapsed / 150).toInt()
        return Triple(floatArrayOf(14f, 18f, 21f, 24f)[i], floatArrayOf(0.4f, 1f, 0.95f, 0.9f)[i], floatArrayOf(1f, 1f, 0.7f, 0.35f)[i])
    }

    /** The basket's hello wobble (§8.3 landing wobble): −6 / +5 / −2 / 0°, 4 steps, 360 ms. */
    fun wobble(elapsed: Long): Float {
        if (elapsed < 0 || elapsed >= 360) return 0f
        return floatArrayOf(-6f, 5f, -2f, 0f)[(elapsed / 90).toInt()]
    }

    /**
     * The standing bee's loop over the current tray (§4.27): a figure of eight 26 × 10 dp flown once per 2.4 s in 12 fps
     * poses, tilted toward where it flies. The offset is in dp from the top left of its 48 × 30 dp box.
     */
    fun beeLoop(elapsed: Long): EggPose {
        val t = stepped(elapsed % BEE_LAP_MS, POSE_MS).toFloat() / BEE_LAP_MS
        val a = (t * 2 * PI).toFloat()
        val x = 13f + 13f * kotlin.math.sin(a)
        val y = 4f + 4f * kotlin.math.sin(2 * a)
        return EggPose(Offset(x, y), if (kotlin.math.cos(a) >= 0f) 8f else -8f)
    }

    const val BEE_LAP_MS: Long = 2400

    /** The pet's bob (2 steps per second) and its 2-frame chomp (520 ms). */
    fun petBob(elapsed: Long): Boolean = (elapsed / 500) % 2 == 1L

    /** Rounds a px value. */
    internal fun px(v: Float): Int = v.roundToInt()
}

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
 *
 * Its way never crosses a word (§4.1, item 259): it pops up out of the add bar in its row's checkbox column (no words
 * there), rolls left along the lane at its basket's mouth (past the chevron) and bumps the basket, rolls back to the
 * column, drops down it onto its own line, and rolls home along that line to its sticker spot, drawn by its row beneath
 * the row's words, so the words stay whole over it (RowRoller). It starts out of sight inside the add bar a whole
 * number of turns to the right of its spot, so it lands upright exactly where its row sticker is.
 * ------------------------------------------------------------------------------------------------ */

/** Egg 3's travel, laid out once from the anchors when it starts (px). */
@Immutable
data class RollingPath(
    /** Where it starts, out of sight inside the add bar: its spot's x + a whole number of turns, so it lands upright. */
    val launch: Offset,
    /** The column it rises in and drops down (its row's checkbox column: no words). */
    val columnX: Float,
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
    /** Its line is a folded strip (no row shows): after the bump it settles into the strip at its basket. */
    val intoStrip: Boolean = false,
)

object RollingMotion {
    /** The sticker is 44 dp; it rolls on a 22 dp radius: one full turn per 2π × 22 ≈ 138 dp. */
    const val SIZE_DP: Float = 44f
    const val RADIUS_DP: Float = 22f

    // Beats (ms from the commit): inside the add bar to its column (out of sight), tossed up out of the bar to the lane,
    // the roll to the basket, the bump, back to the column, the drop onto its line, the roll home, the settle. The
    // basket is reached well inside the 3 s, and it is settled on its line by 3.2 s even when its row takes a few frames
    // to be placed (ROW_WAIT_FRAMES).
    const val HIDDEN_END: Long = 150
    const val RISE_END: Long = 450
    const val ROLL_END: Long = 1500
    const val BUMP_END: Long = 1950
    const val BACK_END: Long = 2150
    const val DROP_END: Long = 2350
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
     * The path for a sticker whose row sticker sits at [row], whose checkbox column is at [columnX] and whose basket mouth
     * is at [mouth], out of the add bar [addBar] (px). The start inside the bar is the whole number of turns (2πr) right
     * of the row's sticker nearest the column, so the turn it has made when it reaches its spot is a whole number of
     * turns: it lands upright.
     */
    fun path(row: Offset, columnX: Float, mouth: Offset, addBar: androidx.compose.ui.geometry.Rect, density: Float, hop: Boolean, intoStrip: Boolean = false): RollingPath {
        val r = RADIUS_DP * density
        val turn = (2 * PI * r).toFloat()
        var k = ((columnX - row.x) / turn).roundToInt().coerceAtLeast(1)
        while (k > 1 && row.x + k * turn > addBar.right - r) k--
        val launch = Offset(row.x + k * turn, addBar.top + r + 6 * density)
        return RollingPath(launch, columnX, mouth.y - r * 0.6f, mouth.x, row, r, hop, density, intoStrip)
    }

    /** The pose at [elapsed] ms (stepped at 12 fps); null once settled (or, into a strip, once it has gone in). */
    fun at(p: RollingPath, elapsed: Long): EggPose? {
        if (elapsed >= DURATION) return null
        if (p.intoStrip && elapsed >= BACK_END) return null
        val t = stepped(elapsed, POSE_MS)
        return if (p.hop) hopAt(p, t) else rollAt(p, t)
    }

    /** Out of sight inside the add bar (it pops up out of the bar's top edge after this). */
    fun hidden(elapsed: Long): Boolean = elapsed < HIDDEN_END

    /** From here its row draws it, beneath the row's words (the roll home along its own line). */
    fun home(p: RollingPath, elapsed: Long): Boolean = !p.intoStrip && stepped(elapsed, POSE_MS) >= DROP_END

    /** θ (degrees) for a centre at x: the turn its sideways travel from the launch point makes, without slipping. */
    fun theta(p: RollingPath, x: Float): Float = ((x - p.launch.x) / p.radius * 180.0 / PI).toFloat()

    private fun rollAt(p: RollingPath, t: Long): EggPose = when {
        t < HIDDEN_END -> {
            // Inside the bar, out of sight: along to its column.
            val c = Offset(lerp(p.launch.x, p.columnX, t.toFloat() / HIDDEN_END), p.launch.y)
            EggPose(c, theta(p, c.x))
        }
        t < RISE_END -> {
            // Tossed straight up out of the add bar: no sideways travel, so no turn (it keeps its angle).
            val f = easeOut((t - HIDDEN_END).toFloat() / (RISE_END - HIDDEN_END))
            EggPose(Offset(p.columnX, lerp(p.launch.y, p.laneY, f)), theta(p, p.columnX))
        }
        t < ROLL_END -> {
            val f = (t - RISE_END).toFloat() / (ROLL_END - RISE_END)
            val c = Offset(lerp(p.columnX, p.basketX, f), p.laneY)
            EggPose(c, theta(p, c.x))
        }
        t < BUMP_END -> bump(p, t, theta(p, p.basketX))
        t < BACK_END -> {
            if (p.intoStrip) {
                // Into the folded strip at its basket: it shrinks in, turning nowhere.
                val f = (t - BUMP_END).toFloat() / (BACK_END - BUMP_END)
                EggPose(Offset(p.basketX, p.laneY), theta(p, p.basketX), lerp(1f, 0.2f, f), alpha = 1f - f)
            } else {
                val f = easeOut((t - BUMP_END).toFloat() / (BACK_END - BUMP_END))
                val c = Offset(lerp(p.basketX, p.columnX, f), p.laneY)
                EggPose(c, theta(p, c.x))
            }
        }
        t < DROP_END -> {
            // Drops down its column onto its own line: straight down, no turn.
            val f = easeIn((t - BACK_END).toFloat() / (DROP_END - BACK_END))
            EggPose(Offset(p.columnX, lerp(p.laneY, p.row.y, f)), theta(p, p.columnX))
        }
        t < HOME_END -> {
            val f = easeOut((t - DROP_END).toFloat() / (HOME_END - DROP_END))
            val c = Offset(lerp(p.columnX, p.row.x, f), p.row.y)
            EggPose(c, theta(p, c.x))
        }
        else -> {
            // Settles on its line with a little squash (1.12 / .88 → 1).
            val i = ((t - HOME_END) / POSE_MS).toInt()
            EggPose(p.row, theta(p, p.row.x), if (i == 0) 1.12f else 1f, if (i == 0) 0.88f else 1f)
        }
    }

    /** The bump: up 6 dp and squashed (1.1 / .9), then .95 / 1.05, then rest; it only moves up and down. */
    private fun bump(p: RollingPath, t: Long, rotation: Float): EggPose {
        val i = ((t - BUMP_AT) * 3 / (BUMP_END - BUMP_AT)).toInt().coerceIn(0, 2)
        val (sx, sy) = when (i) {
            0 -> 1.1f to 0.9f
            1 -> 0.95f to 1.05f
            else -> 1f to 1f
        }
        return EggPose(Offset(p.basketX, p.laneY - if (i == 0) 6 * p.density else 0f), rotation, sx, sy)
    }

    /**
     * Berries hop instead: up out of the bar, three arcs to the basket (apexes 24, 20, 14 dp, low enough to stay by their
     * tray; tilts −8°, +8°, −6°), the
     * bump, one arc back to the column, the drop, and three small skips home along their line (5 dp: they stay in their
     * row, under its words).
     */
    private fun hopAt(p: RollingPath, t: Long): EggPose {
        val d = p.density
        return when {
            t < HIDDEN_END -> EggPose(Offset(lerp(p.launch.x, p.columnX, t.toFloat() / HIDDEN_END), p.launch.y))
            t < RISE_END -> {
                val f = easeOut((t - HIDDEN_END).toFloat() / (RISE_END - HIDDEN_END))
                EggPose(Offset(p.columnX, lerp(p.launch.y, p.laneY, f)))
            }
            t < ROLL_END -> arcs(p.columnX, p.basketX, p.laneY, (t - RISE_END).toFloat() / (ROLL_END - RISE_END), floatArrayOf(24f, 20f, 14f), floatArrayOf(-8f, 8f, -6f), d)
            t < BUMP_END -> bump(p, t, 0f)
            t < BACK_END -> {
                if (p.intoStrip) {
                    val f = (t - BUMP_END).toFloat() / (BACK_END - BUMP_END)
                    EggPose(Offset(p.basketX, p.laneY), 0f, lerp(1f, 0.2f, f), alpha = 1f - f)
                } else {
                    arcs(p.basketX, p.columnX, p.laneY, (t - BUMP_END).toFloat() / (BACK_END - BUMP_END), floatArrayOf(20f), floatArrayOf(6f), d)
                }
            }
            t < DROP_END -> {
                val f = easeIn((t - BACK_END).toFloat() / (DROP_END - BACK_END))
                EggPose(Offset(p.columnX, lerp(p.laneY, p.row.y, f)))
            }
            t < HOME_END -> arcs(p.columnX, p.row.x, p.row.y, (t - DROP_END).toFloat() / (HOME_END - DROP_END), floatArrayOf(5f, 5f, 5f), floatArrayOf(-6f, 6f, -4f), d)
            else -> EggPose(p.row, 0f, 1.12f, 0.88f)
        }
    }

    /** [f] (0–1) of the way from [fromX] to [toX] on [y] in as many arcs as [apex] has (dp), tilting [tilt] at each top. */
    private fun arcs(fromX: Float, toX: Float, y: Float, f: Float, apex: FloatArray, tilt: FloatArray, d: Float): EggPose {
        val n = apex.size
        val hop = min(n - 1, floor(f * n).toInt())
        val local = f * n - hop
        val x = lerp(fromX, toX, f)
        return EggPose(Offset(x, y - apex[hop] * d * 4 * local * (1 - local)), tilt[hop] * (1 - kotlin.math.abs(local * 2 - 1)))
    }

    private fun easeOut(t: Float) = 1 - (1 - clamp01(t)) * (1 - clamp01(t))

    private fun easeIn(t: Float) = clamp01(t) * clamp01(t)
}

/* ------------------------------------------------------------------------------------------------
 * Egg 2: the ladybug's stroll along a folded strip's bottom edge.
 * ------------------------------------------------------------------------------------------------ */

object LadybugMotion {
    /** 5 s on the strip, 10 % per hop (18 dp each), a pause at 40–60 %, then 600 ms flying off the nearer edge. */
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
     * The pose at [elapsed] for a walk starting at [start] (the bug's centre on the strip's bottom edge), heading right,
     * on a screen [width] px wide: stepped hops with a 2-frame waddle (±5° every 250 ms; still while paused), then the
     * wings open and it flies off the nearer screen edge along the strip's bottom edge (in the gap under the strip, past
     * no words) in 12 fps poses, fading only in the last one. Rotation 90° = facing right (the sticker's head points up),
     * −90° = facing left. Null once gone.
     */
    fun at(start: Offset, elapsed: Long, density: Float, width: Float): Pair<EggPose, Boolean>? {
        if (elapsed >= DURATION) return null
        if (elapsed < WALK_MS) {
            val x = start.x + hops(elapsed) * HOP_DP * density
            val tenth = elapsed * 10 / WALK_MS
            val paused = tenth in 4..5
            val waddle = if (paused) 0f else if ((elapsed / WADDLE_MS) % 2 == 0L) -5f else 5f
            return EggPose(Offset(x, start.y), 90f + waddle) to false
        }
        val endX = start.x + 8 * HOP_DP * density
        val size = SIZE_DP * density
        val leftward = endX < width - endX
        val edgeX = if (leftward) -size else width + size
        val flown = elapsed - WALK_MS
        val step = stepped(flown, POSE_MS)
        // The first pose has already lifted off; the last one is past the edge's line, half faded.
        val f = clamp01((step + POSE_MS).toFloat() / FLY_MS)
        val x = lerp(endX, edgeX, f)
        val y = start.y + 2f * density * kotlin.math.sin((f * PI).toFloat())
        val last = flown >= FLY_MS - POSE_MS
        return EggPose(Offset(x, y), if (leftward) -80f else 80f, alpha = if (last) 0.45f else 1f) to true
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
        return EggPose(Offset(x, edgeY - size * 0.5f - lift), if (step % 2 == 1) -4f else 2f)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 9: the confetti rain, in the edge lanes beside every word.
 * ------------------------------------------------------------------------------------------------ */

object ConfettiMotion {
    const val COUNT: Int = 26
    const val RAIN_MS: Long = 2000
    val kinds: List<String> = listOf("heart", "star4", "petal", "star", "cake", "flower")

    @Immutable
    data class Piece(val kind: String, val x: Float, val sizeDp: Float, val delay: Long, val fallMs: Long, val spin: Float, val sway: Float)

    /**
     * 26 die-cut confetti stickers (12–16 dp), each falling 1.3–1.8 s, all started inside the 2 s, in the two edge lanes
     * of a [width]-wide screen: the binder gutter on the left (no list words start before 18 dp) and the strip past the
     * checkbox and chevron column on the right, so they rain beside every word, never over one (§4.1), with nothing cut.
     */
    fun pieces(width: Float, density: Float, seed: Int = 9): List<Piece> {
        var s = seed.toLong() and 0x7fffffff
        fun next(): Float {
            s = (s * 1103515245L + 12345L) and 0x7fffffff
            return (s % 10_000).toFloat() / 10_000f
        }
        return List(COUNT) { i ->
            val left = i % 2 == 0
            val x = if (left) (5f + next() * 2f) * density else width - (10f + next() * 2f) * density
            val fall = 1300L + (next() * 500).toLong()
            Piece(kinds[i % kinds.size], x, 12f + next() * 4f, ((RAIN_MS - fall) * next()).toLong(), fall, (if (next() < 0.5f) -1 else 1) * (140f + next() * 180f), (next() - 0.5f) * 2f)
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
     * The standing bee's loop over the current tray (§4.27, the mockup's figure of eight): its centre flies a 48 × 18 dp
     * figure of eight in a 64 × 30 dp box once per 3.2 s in 12 fps poses, tilted toward where it flies. Centre in dp.
     */
    fun beeLoop(elapsed: Long): EggPose {
        val t = stepped(elapsed % BEE_LAP_MS, POSE_MS).toFloat() / BEE_LAP_MS
        val a = (t * 2 * PI).toFloat()
        val x = 32f + 24f * kotlin.math.sin(a)
        val y = 15f - 9f * kotlin.math.sin(2 * a)
        return EggPose(Offset(x, y), if (kotlin.math.cos(a) >= 0f) 8f else -8f)
    }

    const val BEE_LAP_MS: Long = 3200

    /** The pet's bob (2 steps per second) and its 2-frame chomp (520 ms). */
    fun petBob(elapsed: Long): Boolean = (elapsed / 500) % 2 == 1L

    /** Rounds a px value. */
    internal fun px(v: Float): Int = v.roundToInt()
}

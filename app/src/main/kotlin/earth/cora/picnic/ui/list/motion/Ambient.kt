package earth.cora.picnic.ui.list.motion

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.theme.GlintSpec
import kotlin.math.hypot
import kotlin.random.Random

/*
 * The ambient loops (docs/DESIGN_STICKER.md §8.5, item 194): every 5 s, while the list is at least 10 % visible, one
 * random folded unfinished basket wobbles and one of its minis peeks up 3 dp (2 steps, 500 ms); every 9 s one visible
 * unchecked sticker gets a WHITE gloss sweep (an 80 % band clipped to the sticker, 6 steps, 900 ms; holo is for rewards
 * only, §2.11). They run only while visible and never under reduced motion; they are decorative loops, so they ride the
 * infinite-animation frame clock (tests' policy stops them unless a test drives them). Owner: WP6.
 *
 * The beats' times are on the director's own frame clock, so the pieces that play them read [AmbientDirector.clock]:
 * the engine's MotionClock only moves while a chain is in flight, and an idle list is exactly when the loops run.
 *
 * WP5 wiring: `val ambient = rememberAmbient(ambientVisible(resumed, fraction), reduced, baskets, stickers)`, with the
 * fraction from [Modifier.visibleFraction] on the list; then `MotionBasket(…, ambientPeekItem = beat?.peekItem,
 * ambientT0 = beat?.wobbleT0 (for beat?.wobbleSection's basket), ambientClock = ambient.clock)` and the row sticker's
 * `Modifier.glossSweep(beat?.sweepT0 (for beat?.sweepItem), ambient.clock)`, where `beat = ambient.state.value`.
 */

/** The ambient loops' current beat: a folded basket's wobble with a peeking mini, a sticker's gloss sweep. */
@Immutable
data class AmbientPresentation(
    val wobbleSection: String? = null,
    val wobbleT0: Long? = null,
    val peekItem: String? = null,
    val sweepItem: String? = null,
    val sweepT0: Long? = null,
)

/** The ambient sweep: a white band at 80 %, 6 steps over 900 ms (never holo). */
object AmbientSweep {
    val color: Color = Color.White
    val spec: GlintSpec = GlintSpec(widthDp = 14f, alpha = 0.8f, angleDeg = 60f, steps = 6, durationMillis = 900)
    const val EVERY: Long = 9000
}

/** One folded, unfinished basket the wobble may pick, and its minis (the peek lifts one). */
@Immutable
data class AmbientBasket(val sectionId: String, val minis: List<String>)

/**
 * Picks the ambient beats on its own frame source: every [WOBBLE_EVERY] a basket, every [AmbientSweep.EVERY] a sticker,
 * from what [baskets] and [stickers] say is on screen now. [random] is injectable (tests seed it).
 */
class AmbientDirector(private val random: Random = Random(7)) {
    private val beat: MutableState<AmbientPresentation?> = mutableStateOf(null)

    /** The beat showing now, or null. */
    val state: State<AmbientPresentation?> get() = beat

    /**
     * The frame time the beats' t0s are on, advanced every frame while a beat plays (and once as it ends). The pieces
     * read it in their draw and layer lambdas, so a beat animates without recomposing anything.
     */
    val clock: MotionClock = MotionClock()

    /** Beats so far (tests). */
    var beats: Int = 0
        private set

    /**
     * Runs until cancelled, reading the frame time from [frame] (in the app the infinite-animation frame clock).
     * [baskets] and [stickers] are read at each beat.
     */
    suspend fun run(frame: suspend () -> Long, baskets: () -> List<AmbientBasket>, stickers: () -> List<String>) {
        val start = frame()
        var nextWobble = start + WOBBLE_EVERY
        var nextSweep = start + AmbientSweep.EVERY
        try {
            while (true) {
                val now = frame()
                var cur = beat.value
                if (cur?.wobbleT0 != null && now >= cur.wobbleT0!! + 500) cur = cur.copy(wobbleSection = null, wobbleT0 = null, peekItem = null)
                if (cur?.sweepT0 != null && now >= cur.sweepT0!! + AmbientSweep.spec.durationMillis) cur = cur.copy(sweepItem = null, sweepT0 = null)
                if (now >= nextWobble) {
                    nextWobble += WOBBLE_EVERY
                    val pick = baskets().takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
                    if (pick != null) {
                        cur = (cur ?: AmbientPresentation()).copy(
                            wobbleSection = pick.sectionId,
                            wobbleT0 = now,
                            peekItem = pick.minis.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] },
                        )
                        beats++
                    }
                }
                if (now >= nextSweep) {
                    nextSweep += AmbientSweep.EVERY
                    val pick = stickers().takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
                    if (pick != null) {
                        cur = (cur ?: AmbientPresentation()).copy(sweepItem = pick, sweepT0 = now)
                        beats++
                    }
                }
                if (cur != null && cur.wobbleT0 == null && cur.sweepT0 == null) cur = null
                // The clock moves only while something plays, so an idle list invalidates nothing.
                if (cur != null || beat.value != null) clock.now = now
                if (cur != beat.value) beat.value = cur
            }
        } finally {
            beat.value = null
        }
    }

    companion object {
        const val WOBBLE_EVERY: Long = 5000
    }
}

/**
 * The ambient loops for a list: running only while [visible] (the screen resumed and the list at least 10 % visible,
 * [ambientVisible]) and never under [reduced] motion. Returns the director: its [AmbientDirector.state] is the beat
 * showing now, its [AmbientDirector.clock] the time the pieces play it on.
 */
@Composable
fun rememberAmbient(
    visible: Boolean,
    reduced: Boolean,
    baskets: () -> List<AmbientBasket>,
    stickers: () -> List<String>,
    director: AmbientDirector = remember { AmbientDirector() },
    frame: suspend () -> Long = { withInfiniteAnimationFrameMillis { it } },
): AmbientDirector {
    val b by rememberUpdatedState(baskets)
    val s by rememberUpdatedState(stickers)
    LaunchedEffect(visible, reduced, director) {
        if (visible && !reduced) director.run(frame, { b() }, { s() })
    }
    return director
}

/** The §8.5 gate: the loops run only while the screen is resumed and at least 10 % of the list is on screen. */
fun ambientVisible(resumed: Boolean, visibleFraction: Float): Boolean = resumed && visibleFraction >= AMBIENT_MIN_VISIBLE

/** At least this much of the list must be on screen for the ambient loops to run (§8.5). */
const val AMBIENT_MIN_VISIBLE: Float = 0.1f

/**
 * Reports how much of this element is inside the window (0..1, by area) whenever it moves: the list's visibility for
 * [ambientVisible].
 */
fun Modifier.visibleFraction(onChange: (Float) -> Unit): Modifier = onGloballyPositioned { c ->
    val w = c.size.width.toFloat()
    val h = c.size.height.toFloat()
    if (w <= 0f || h <= 0f || !c.isAttached) {
        onChange(0f)
        return@onGloballyPositioned
    }
    val shown = c.boundsInWindow()
    onChange(((shown.width * shown.height) / (w * h)).coerceIn(0f, 1f))
}

/**
 * The white gloss sweep over a sticker (clipped to what the sticker drew), from [t0] on [clock]: the ambient director's
 * [AmbientDirector.clock] (never the engine's, which sleeps while the list is idle).
 */
fun Modifier.glossSweep(t0: Long?, clock: MotionClock): Modifier =
    if (t0 == null) this else this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawAmbientSweep(AmbientSweep.spec.progressAt(clock.now - t0))
        }

/** Draws the ambient sweep's white band at [progress] (0..1), only where something is already drawn (SrcAtop). */
fun DrawScope.drawAmbientSweep(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val spec = AmbientSweep.spec
    val band = spec.widthDp.dp.toPx()
    val diag = hypot(size.width, size.height)
    val x = -band + (size.width + band * 2) * progress
    rotate(spec.angleDeg - 90f, Offset(x, size.height / 2f)) {
        drawRect(AmbientSweep.color.copy(alpha = spec.alpha), Offset(x - band / 2f, size.height / 2f - diag), Size(band, diag * 2), blendMode = BlendMode.SrcAtop)
    }
}

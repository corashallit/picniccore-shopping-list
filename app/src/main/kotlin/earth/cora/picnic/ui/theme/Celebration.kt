package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.list.motion.BasketMini
import earth.cora.picnic.ui.list.motion.BasketPresentation
import earth.cora.picnic.ui.list.motion.BeatName
import earth.cora.picnic.ui.list.motion.BonusSticker
import earth.cora.picnic.ui.list.motion.BookMotion
import earth.cora.picnic.ui.list.motion.CelebrationContent
import earth.cora.picnic.ui.list.motion.CelebrationCopy
import earth.cora.picnic.ui.list.motion.CelebrationPlan
import earth.cora.picnic.ui.list.motion.CelebrationPresentation
import earth.cora.picnic.ui.list.motion.CelebrationTimeline
import earth.cora.picnic.ui.list.motion.LocalMotionProbe
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.list.motion.MotionProbe
import earth.cora.picnic.ui.list.motion.MotionBasket
import earth.cora.picnic.ui.list.motion.MotionPose
import earth.cora.picnic.ui.list.motion.StampCircle
import earth.cora.picnic.ui.list.motion.drawCurl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The celebration scene, "List complete!" (docs/DESIGN_STICKER.md §11, D25, §0.4 Q7; items 180–191, 314). Owner: WP6.
 *
 * Always in Day tokens (the host wraps it in PicnicTheme(Day)). One text beat at a time, at the times
 * CelebrationTimeline computed from the real words: the title with its chip, LCD and "tap to skip" (settled 0.70 s),
 * PER-FECT! on the stamp card (still from 4.73 s), the ribbon banner with the trip's real stats (6.23 s), the bonus card
 * (8.93 s), the page curl into the album (11.23 s), then the still summary with its washi-tape progress bar
 * ("Closing… tap to keep it"). Around them: the white flash, the heart iris from the last tapped checkbox onto pastel
 * rays, the burst star, the kira stars round the title, the ribbon ride of up to 24 riders into the hamper, the picnic
 * spread, the stamp card's rise and flip, the glints (never inside a text's hold), the screen shake, the confetti
 * (behind every text plate), the gold capsule, the pet dance. Reduced motion: the summary frame only, faded in over
 * 120 ms. Nothing in it is a TalkBack node (the host gives the scene its one node). Every pose is a pure function of
 * the scene time on the engine's clock, read in draw and layer lambdas.
 */

/** Test tags of the scene's pieces (CelebrationTest, ReadingRuleAuditTest). */
object CelebrationTags {
    const val SCENE = "cele_scene"
    const val CONFETTI = "cele_confetti"
    const val CONTENT = "cele_content"
    const val TITLE = "cele_title"
    const val CHIP = "cele_chip"
    const val LCD = "cele_lcd"
    const val SKIP = "cele_skip"
    const val PERFECT = "cele_perfect"
    const val BANNER = "cele_banner"
    const val BONUS = "cele_bonus"
    const val SUMMARY_CARD = "cele_summary_card"
    const val CLOSING = "cele_closing"
    const val CLOSING_LABEL = "cele_closing_label"
    const val DONE = "cele_done"
    const val STAMP_CARD = "cele_stamp_card"
    const val SHELF = "cele_shelf"
    const val HAMPER = "cele_hamper"
}

/** The scene's clock: ms since it started; under reduced motion every movement is at its end. */
private class SceneClock(initial: CelebrationPresentation, val plan: CelebrationPlan, val clock: MotionClock) {
    /** The presentation now (kept, closing): snapshot state, so the layers that read it follow it. */
    var cele: CelebrationPresentation by mutableStateOf(initial)

    val reduced: Boolean = initial.reduced

    /** The full storyboard's beat times (also under reduced motion, where everything is at its end). */
    val story: CelebrationPlan = if (plan.reduced) {
        val c = cele.content!!
        CelebrationTimeline.plan(c.listName, c.stats, c.petHatched, reduced = false, total = c.total)
    } else {
        plan
    }

    fun t(): Long = if (reduced) FAR else clock.now - cele.t0

    fun at(name: BeatName): Long = story.beatOrNull(name)?.at ?: 0L

    fun since(name: BeatName): Long = t() - at(name)

    /** The whole scene's alpha: the reduced-motion fade in, the close fade out. */
    fun sceneAlpha(): Float {
        val now = clock.now
        val closing = cele.closingT0
        val fadeOut = if (closing != null) {
            val out = if (reduced) 120f else 200f
            (1f - (now - closing) / out).coerceIn(0f, 1f)
        } else {
            1f
        }
        val fadeIn = if (reduced) ((now - cele.t0) / 120f).coerceIn(0f, 1f) else 1f
        return fadeIn * fadeOut
    }

    companion object {
        const val FAR: Long = 1_000_000
    }
}

/**
 * The scene for [cele] on [clock] in the copy [copy]. [onSummaryCard] reports the summary card's bounds (root px) so the
 * host can route taps (in it: keep; elsewhere: close). [onDone] closes from the Done button once kept.
 */
@Composable
fun CelebrationScene(
    cele: CelebrationPresentation,
    clock: MotionClock,
    copy: CelebrationCopy,
    modifier: Modifier = Modifier,
    onSummaryCard: (Rect) -> Unit = {},
    onDone: () -> Unit = {},
) {
    val content = cele.content ?: return
    val plan = cele.plan ?: return
    val sc = remember(cele.t0, cele.reduced, plan) { SceneClock(cele, plan, clock) }
    // Written before anything in this composition reads it.
    sc.cele = cele
    val density = LocalDensity.current
    val probe = LocalMotionProbe.current
    val anchors = remember { SceneAnchors() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var sceneSize by remember { mutableStateOf(Size.Zero) }
    // The flash whitens the whole finished list (it is not inside the iris); the scene opens inside the iris.
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .testTag(CelebrationTags.SCENE)
                .decorative()
                .onGloballyPositioned {
                    origin = it.boundsInRoot().topLeft
                    sceneSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
                }
                .graphicsLayer { alpha = sc.sceneAlpha() }
                .irisClip(sc, cele, { origin }, { sceneSize }),
        ) {
            val w = maxWidth
            val h = maxHeight
            // The screen shake: two 3 dp frames after PERFECT's slam.
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { anchors.stageOrigin = it.boundsInRoot().topLeft }
                    .graphicsLayer { translationX = shakeDx(sc) * density.density }
                    .onPlaced { anchors.stage = it },
            ) {
                Rays(sc)
                BurstStar(sc)
                Confetti(sc, w, h)
                SceneContent(sc, content, copy, anchors, onSummaryCard)
                AlbumCorner(sc, content.bonus, w, h)
                BottomBand(sc, copy, anchors, onDone)
            }
            if (probe != null) ProbeReports(sc, probe, anchors, origin, sceneSize)
        }
        Flash(sc)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Background: the iris, the rays, the burst star, the flash, the shake.
 * ------------------------------------------------------------------------------------------------ */

/** The heart iris: from 1 dp at [cele].origin to 80 → 260 → 820 → 3000 dp in 4 steps over 420 ms (from 0.04 s). */
private fun Modifier.irisClip(sc: SceneClock, cele: CelebrationPresentation, origin: () -> Offset, size: () -> Size): Modifier = drawWithContent {
    val e = sc.since(BeatName.Iris)
    val d = when {
        sc.reduced -> -1f
        e < 0 -> 0f
        e < 105 -> 80f
        e < 210 -> 260f
        e < 315 -> 820f
        e < 420 -> 3000f
        else -> -1f
    }
    if (d < 0f) {
        drawContent()
        return@drawWithContent
    }
    if (d == 0f) return@drawWithContent
    val o = cele.origin?.let { it - origin() } ?: Offset(size().width / 2f, size().height / 2f)
    val heart = heartPath(o, d.dp.toPx())
    clipPath(heart) { this@drawWithContent.drawContent() }
}

private fun shakeDx(sc: SceneClock): Float {
    if (sc.reduced) return 0f
    val e = sc.since(BeatName.Shake)
    return when {
        e < 0 -> 0f
        e < 85 -> 3f
        e < 170 -> -3f
        else -> 0f
    }
}

/** Pastel rays: 7.5° conic stripes, fading in over 600 ms from 0.04 s, then turning once per 24 s. Behind everything. */
@Composable
private fun Rays(sc: SceneClock) {
    val loop by rememberLoopTime(enabled = !sc.reduced)
    // The scene is always Day: sakura, lemon and ramune pastels between stripes of the mint binder.
    val ground = PicnicTheme.colors.binder
    val rayColors = listOf(Families.sakura.pastel, Families.lemon.pastel, Families.ramune.pastel, ground)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(ground)
        val e = sc.since(BeatName.Iris)
        val a = if (sc.reduced) 1f else (e / 600f).coerceIn(0f, 1f)
        val c = Offset(size.width / 2f, size.height * 0.2f)
        val r = max(size.width, size.height) * 1.5f
        rotate((loop % 24_000L) * 360f / 24_000f, c) {
            for (i in 0 until 48) {
                val start = i * 7.5f
                drawArc(rayColors[i % 4], start, 7.5f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), alpha = a)
            }
        }
    }
}

/** The 240 dp double 12-point burst star behind the title: pops at 0.10 s (500 ms), then 18 s per turn. */
@Composable
private fun BurstStar(sc: SceneClock) {
    val loop by rememberLoopTime(enabled = !sc.reduced)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        val e = sc.since(BeatName.BurstStar)
        if (e < 0) return@Canvas
        val pop = if (sc.reduced) 1f else popScale(e, 500)
        val c = Offset(size.width / 2f, 64.dp.toPx())
        val r = 120.dp.toPx() * pop
        rotate((loop % 18_000L) * 360f / 18_000f, c) {
            drawPath(starPath(c, r, r * 0.72f, points = 12), Color.White, alpha = 0.55f)
            rotate(15f, c) { drawPath(starPath(c, r * 0.78f, r * 0.56f, points = 12), Color.White, alpha = 0.75f) }
        }
    }
}

/** A pop: 0 → 1.15 → 1 over [ms] (cubic-bezier(.3, 1.5, .5, 1)). */
private fun popScale(e: Long, ms: Long): Float {
    if (e <= 0) return 0f
    val u = (e.toFloat() / ms).coerceIn(0f, 1f)
    return PopEasing.transform(u)
}

private val PopEasing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)
private val TitleEasing = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f)
private val FlipEasing = CubicBezierEasing(0.4f, 0f, 0.3f, 1f)

/** One white flash over the finished list: 1 → .92 → 0, 2 steps, 170 ms. */
@Composable
private fun Flash(sc: SceneClock) {
    Canvas(Modifier.fillMaxSize()) {
        if (sc.reduced) return@Canvas
        val e = sc.since(BeatName.Flash)
        val a = when {
            e < 0 -> 0f
            e < 85 -> 1f
            e < 170 -> 0.92f
            else -> 0f
        }
        if (a > 0f) drawRect(Color.White, alpha = a)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Confetti: 36 die-cut stickers in 3 waves of 12, behind every text plate.
 * ------------------------------------------------------------------------------------------------ */

private class ConfettoSpec(val kind: String, val grocery: String?, val x: Float, val size: Float, val turn: Float, val fall: Long, val wave: Int, val i: Int)

private val ConfettiKinds = listOf("heart", "star4", "petal", "star")
private val ConfettiGroceries = listOf("strawberry", "cherries", "lemonade", "croissant")

private fun confettoSpecs(): List<ConfettoSpec> {
    var s = 0x2468aceL
    fun next(): Float {
        s = (s * 1103515245L + 12345L) and 0x7fffffff
        return (s % 10_000).toFloat() / 10_000f
    }
    return List(36) { k ->
        val wave = k / 12
        val i = k % 12
        val grocery = if (i % 6 == 5) ConfettiGroceries[(k / 6) % ConfettiGroceries.size] else null
        val dir = if (next() < 0.5f) -1f else 1f
        ConfettoSpec(
            kind = ConfettiKinds[k % ConfettiKinds.size],
            grocery = grocery,
            x = (i + 0.5f + (next() - 0.5f) * 0.8f) / 12f,
            size = 18f + 8f * next(),
            turn = dir * (140f + 180f * next()),
            fall = 1300L + (500 * next()).toLong(),
            wave = wave,
            i = i,
        )
    }
}

@Composable
private fun Confetti(sc: SceneClock, w: Dp, h: Dp) {
    if (sc.reduced) return
    val specs = remember { confettoSpecs() }
    Box(Modifier.fillMaxSize().testTag(CelebrationTags.CONFETTI)) {
        for (c in specs) {
            val sizeDp = c.size.dp
            val mod = Modifier
                .offset {
                    val e = confettoElapsed(sc, c)
                    val u = steppedFall(e, c.fall)
                    val x = (w * c.x - sizeDp / 2).roundToPx()
                    val y = ((-sizeDp) + (h + sizeDp * 2) * u).roundToPx()
                    IntOffset(x, y)
                }
                .graphicsLayer {
                    val e = confettoElapsed(sc, c)
                    if (e < 0 || e > c.fall) {
                        alpha = 0f
                    } else {
                        rotationZ = c.turn * steppedFall(e, c.fall)
                    }
                }
            if (c.grocery != null) {
                ItemIcon(IconRef(c.grocery), null, size = sizeDp, modifier = mod, shadow = false)
            } else {
                DecorSticker(c.kind, mod, size = sizeDp)
            }
        }
    }
}

private fun confettoElapsed(sc: SceneClock, c: ConfettoSpec): Long =
    sc.since(BeatName.Confetti) - c.wave * 300L - c.i * 35L

/** 10 steps over the fall. */
private fun steppedFall(e: Long, fall: Long): Float {
    if (e <= 0) return 0f
    val u = (e.toFloat() / fall).coerceIn(0f, 1f)
    return Stepped(10).transform(u)
}

/* ------------------------------------------------------------------------------------------------
 * The content: title, chip, LCD, shelf, stamp card, banner, picnic spread, bonus card.
 * ------------------------------------------------------------------------------------------------ */

/** Where the content's pieces landed (root px), for the riders' paths and the kira stars. */
private class SceneAnchors {
    // Snapshot state: the kira stars and the riders read these in their offset and layer blocks, which must run again
    // once the first layout has measured the title, the shelf and the hamper.
    var title: Rect by mutableStateOf(Rect.Zero)
    var shelf: List<Rect> by mutableStateOf(emptyList())
    var hamper: Rect by mutableStateOf(Rect.Zero)

    /** Where the riders drop into the hamper: its rim's centre (root px). */
    var hamperMouth: Offset by mutableStateOf(Offset.Zero)
    var origin: Offset by mutableStateOf(Offset.Zero)
    var sceneRight: Float by mutableFloatStateOf(0f)

    /** Inside the shake layer, and where that layer sits at rest (root px): plates are measured from it, unshaken. */
    var stage: androidx.compose.ui.layout.LayoutCoordinates? = null
    var stageOrigin: Offset = Offset.Zero

    /** [coords]'s bounds in root px as if the screen never shook. */
    fun unshaken(coords: androidx.compose.ui.layout.LayoutCoordinates): Rect {
        val st = stage
        return if (st != null && st.isAttached) st.localBoundingBoxOf(coords, clipBounds = false).translate(stageOrigin) else coords.boundsInRoot()
    }

    /** Every text plate's laid-out bounds (root px, before its own motion), by tag. */
    val plates: MutableMap<String, Rect> = LinkedHashMap()

    /** The summary card and its closing bar (root px): together they are "the summary" a tap keeps open. */
    var content: Rect? = null
    var closing: Rect? = null

    /** The summary card (§0.4 Q7) with, while it shows, the washi bar along its bottom edge. */
    fun card(): Rect? {
        val a = content ?: return closing
        val b = closing ?: return a
        return Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
    }
}

/** Records this text plate's laid-out bounds (put it before the plate's motion layer). */
private fun Modifier.plate(anchors: SceneAnchors, tag: String): Modifier =
    testTag(tag).onGloballyPositioned { anchors.plates[tag] = anchors.unshaken(it) }

@Composable
private fun SceneContent(sc: SceneClock, content: CelebrationContent, copy: CelebrationCopy, anchors: SceneAnchors, onSummaryCard: (Rect) -> Unit) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                anchors.origin = b.topLeft
                anchors.sceneRight = b.right
            },
    ) {
        Ribbon(sc, anchors)
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 10.dp, start = 12.dp, end = 12.dp, bottom = BAND)
                .testTag(CelebrationTags.CONTENT),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- Text beat 1: the title with its chip, the LCD and (bottom-left) "tap to skip" ----
            Box(
                Modifier
                    .plate(anchors, CelebrationTags.TITLE)
                    .onGloballyPositioned { anchors.title = it.boundsInRoot() }
                    .graphicsLayer {
                        val e = sc.since(BeatName.Title)
                        val pose = titlePose(e, sc.reduced)
                        translationY = pose.dy * density
                        scaleX = pose.scaleX
                        scaleY = pose.scaleY
                        alpha = pose.alpha
                    }
                    .titleGlint(sc),
            ) {
                PuffyText(
                    copy.title,
                    style = t.celebrationTitle,
                    fill = Families.strawberry.mid,
                    stroke = 9.dp,
                    strokeColor = Color.White,
                    shadow = PicnicTheme.shadows.hs2,
                    minFontSize = 26.sp,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.plate(anchors, CelebrationTags.CHIP).graphicsLayer { alpha = textBeatAlpha(sc, BeatName.Title) }) {
                SceneChip("♡ ${content.listName}", t.label.copy(color = c.ballpoint), maxLines = 2)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.plate(anchors, CelebrationTags.LCD).graphicsLayer { alpha = textBeatAlpha(sc, BeatName.Title) }) {
                SceneLcd(sc, content)
            }
            Spacer(Modifier.height(10.dp))
            Shelf(sc, content, anchors)
            Spacer(Modifier.height(6.dp))
            SummaryCard(sc, copy, anchors, onSummaryCard) {
                StampCard(sc, content, anchors)
                Spacer(Modifier.height(8.dp))
                Banner(sc, content, copy, anchors)
            }
            PicnicSpread(sc, content, copy, anchors)
        }
        Riders(sc, content, anchors)
        KiraStars(sc, anchors)
    }
}

/**
 * The summary card (§0.4 Q7): the trip's results, the stamp card with PER-FECT! and the ribbon banner, on a ruled pad
 * card (white kiss-cut, hs2) that fades in behind them as the summary starts, with the washi progress bar along its
 * bottom edge, labelled "Closing… tap to keep it". A tap on this card keeps the scene open (the bar goes, and a Done
 * button shows in the bottom band, where "tap to skip" was); a tap on the rays, the shelf, the picnic or the album
 * corner closes it, as "tap to skip" taught. Its last row is laid out from the start and keeps its place once kept, so
 * nothing moves when the bar comes or goes; under TalkBack (no countdown) it has none.
 */
@Composable
private fun SummaryCard(
    sc: SceneClock,
    copy: CelebrationCopy,
    anchors: SceneAnchors,
    onSummaryCard: (Rect) -> Unit,
    results: @Composable () -> Unit,
) {
    val c = PicnicTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val bar = !sc.cele.talkBack
    val rule = c.rule.copy(alpha = 0.55f)
    Box(
        Modifier
            .fillMaxWidth()
            .testTag(CelebrationTags.SUMMARY_CARD)
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                anchors.plates[CelebrationTags.SUMMARY_CARD] = b
                anchors.content = b
                anchors.card()?.let(onSummaryCard)
            },
    ) {
        // The pad card, behind every plate on it; the washi strip straddles its bottom edge.
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = if (bar) STRIP / 2 else 0.dp)
                .graphicsLayer { alpha = summaryCardAlpha(sc) }
                .hardShadow(shape, PicnicTheme.shadows, large = true)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(3.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.pad)
                .ruledLines(24.dp, rule, firstLine = 20.dp),
        )
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The stamp card's own box leaves room above it for PER-FECT!'s overhang: no padding needed.
            results()
            if (bar) {
                Spacer(Modifier.height(2.dp))
                SummaryBarRow(sc, copy, anchors, onSummaryCard)
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** The washi strip's height; it straddles the summary card's bottom edge. */
private val STRIP: Dp = 16.dp

/** The summary card fades in behind its plates over 200 ms as the summary starts (at once under reduced motion). */
private fun summaryCardAlpha(sc: SceneClock): Float {
    if (sc.reduced) return 1f
    return (sc.since(BeatName.Summary) / 200f).coerceIn(0f, 1f)
}

/**
 * The card's last row: the washi progress bar and its label while the summary waits to close. Once kept, the bar goes
 * and the row keeps its place empty (the Done button is in the bottom band), so nothing on the card or below it moves.
 */
@Composable
private fun SummaryBarRow(sc: SceneClock, copy: CelebrationCopy, anchors: SceneAnchors, onSummaryCard: (Rect) -> Unit) {
    val cele = sc.cele
    val summaryAt = sc.at(BeatName.Summary).let { if (sc.reduced) 0L else it }
    if (cele.keptAt != null) {
        // The bar's footprint, drawn as nothing.
        ClosingBar(sc, copy, summaryAt, anchors, Modifier.fillMaxWidth().padding(horizontal = 14.dp), placeholder = true)
    } else {
        DisposableEffect(Unit) {
            onDispose {
                anchors.closing = null
                anchors.card()?.let(onSummaryCard)
            }
        }
        ClosingBar(
            sc, copy, summaryAt, anchors,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .onGloballyPositioned {
                    anchors.closing = it.boundsInRoot()
                    anchors.card()?.let(onSummaryCard)
                },
        )
    }
}

/**
 * A white chip sticker on the scene (the list name, "tap to skip"): 32 dp tall at least, and taller when the font scale
 * or a long name needs a second line, so its text never clips (ChipFace keeps a fixed height).
 */
@Composable
private fun SceneChip(text: String, style: androidx.compose.ui.text.TextStyle, maxLines: Int) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .widthIn(max = 312.dp)
            .heightIn(min = 32.dp)
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(PicnicTheme.colors.stickerWhite)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        val center = androidx.compose.ui.text.style.TextAlign.Center
        // PicnicText draws the inline glyphs (♡); plain words keep the plain Text.
        if (text.any { it in "♡♥☆★✦♪" }) {
            PicnicText(text, style = style, textAlign = center, maxLines = maxLines)
        } else {
            androidx.compose.material3.Text(text, style = style, textAlign = center, maxLines = maxLines)
        }
    }
}

/** The bottom band's height (the "tap to skip" chip, the progress bar or Done; the album corner beside it). */
private val BAND: Dp = 74.dp

/** The title drops in from −80 dp, .6 → 1.06 → 1, 600 ms (cubic-bezier(.3, 1.4, .5, 1)). */
private fun titlePose(e: Long, reduced: Boolean): earth.cora.picnic.ui.list.motion.MotionPose {
    if (reduced) return earth.cora.picnic.ui.list.motion.MotionPose.Rest
    if (e < 0) return earth.cora.picnic.ui.list.motion.MotionPose(alpha = 0f)
    val u = (e / 600f).coerceIn(0f, 1f)
    val k = TitleEasing.transform(u)
    val s = 0.6f + 0.4f * k
    return earth.cora.picnic.ui.list.motion.MotionPose(dy = -80f * (1f - k), scaleX = s, scaleY = s, alpha = (e / 120f).coerceIn(0f, 1f))
}

/** A plate newly on screen with a text beat fades in with it (by 0.30 s for the title beat's chips). */
private fun textBeatAlpha(sc: SceneClock, beat: BeatName): Float {
    if (sc.reduced) return 1f
    val e = sc.since(beat)
    return (e / 200f).coerceIn(0f, 1f)
}

/** The title glint: a holo glint sweeps the title only, after its hold (12 steps, 1.2 s). */
private fun Modifier.titleGlint(sc: SceneClock): Modifier = drawWithContent {
    drawContent()
    if (sc.reduced) return@drawWithContent
    val e = sc.since(BeatName.TitleGlint)
    if (e in 0 until Holo.glint.durationMillis) clipRect { drawGlint(Holo.glint.progressAt(e)) }
}

@Composable
private fun SceneLcd(sc: SceneClock, content: CelebrationContent) {
    if (content.petHatched) {
        // The pixel bee dances 4 × 500 ms with the banner (egg 15). The pose is derived from the clock, so the chip
        // recomposes only when the pose changes (five times), never every frame of the scene.
        val pose by remember(sc) {
            derivedStateOf(structuralEqualityPolicy()) {
                val e = sc.clock.now - sc.cele.t0 - sc.at(BeatName.PetDance)
                when {
                    sc.reduced || e < 0 || e >= 2000 -> PetPose.Rest
                    else -> listOf(PetPose.Dance1, PetPose.Dance2, PetPose.Dance3, PetPose.Dance4)[(e / 500).toInt().coerceIn(0, 3)]
                }
            }
        }
        val probe = LocalMotionProbe.current
        if (probe != null) SideEffect { probe.recomposed(CelebrationTags.LCD) }
        LcdChip(LcdState(LcdMode.Pet, content.stats.stickers, content.total, petPose = pose))
    } else {
        // A text plate holds still (§9): the scene's LCD does not blink its star.
        CompositionLocalProvider(LocalReducedMotion provides true) {
            LcdChip(LcdState(LcdMode.Count, content.stats.stickers, content.total))
        }
    }
}

/* ---- The shelf: one basket per stop (44 dp in a row for 1–8, 32 dp in two rows for 9–16, 26 dp in three for 17–24) ---- */

/** The shelf's rows and basket width for [stops] stops (§11.2). */
internal fun shelfGrid(stops: Int): Pair<Int, Dp> = when {
    stops <= 8 -> 1 to 44.dp
    stops <= 16 -> 2 to 32.dp
    else -> 3 to 26.dp
}

@Composable
private fun Shelf(sc: SceneClock, content: CelebrationContent, anchors: SceneAnchors) {
    val stops = content.stops.take(24)
    val (rows, basketW) = shelfGrid(stops.size)
    val perRow = ceil(stops.size / rows.toFloat()).toInt().coerceAtLeast(1)
    val rects = remember(stops.size) { arrayOfNulls<Rect>(stops.size) }
    val riders = content.riders
    // How many of each basket's minis have left on the ribbon, and whether the shelf has emptied (2.2 s). Derived from
    // the clock, so the shelf recomposes only when a rider departs (about 24 times), never every frame of the scene.
    val departures by remember(sc, riders, stops.size) {
        derivedStateOf(structuralEqualityPolicy()) {
            val e = sc.t()
            if (e >= 2200) null else List(stops.size) { i -> riders.withIndex().count { (k, rider) -> rider.stopIndex == i && e >= riderStart(k) } }
        }
    }
    val gap = if (rows == 1) 0.dp else 2.dp
    val probe = LocalMotionProbe.current
    BoxWithConstraints(Modifier.testTag(CelebrationTags.SHELF), contentAlignment = Alignment.Center) {
        // Tests only: the performance gate counts the shelf's recompositions.
        if (probe != null) SideEffect { probe.recomposed(CelebrationTags.SHELF) }
        // A basket's art box is 1.18 × the shelf size (the art has margins round it), shrunk to fit the row: a full
        // row of 8 on a 360 dp phone gets 42 dp boxes rather than running off the screen.
        val box = min(basketW.value * 1.18f, (maxWidth.value - gap.value * (perRow - 1)) / perRow).dp
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (i in r * perRow until min(stops.size, (r + 1) * perRow)) {
                        val stop = stops[i]
                        // A basket's minis leave as their riders depart; the rest go at 2.2 s ("shelf baskets empty").
                        val departed = departures?.getOrNull(i)
                        val minis = if (departed == null) emptyList() else stop.gotten.drop(departed).take(earth.cora.picnic.ui.theme.gen.BasketManifest.MAX_MINIS).mapIndexed { k, icon -> BasketMini("$i-$k", icon) }
                        MotionBasket(
                            kind = stop.basket,
                            look = stop.look,
                            gotten = minis,
                            toGet = 0,
                            complete = false,
                            presentation = BasketPresentation(),
                            clock = sc.clock,
                            size = DpSize(box, box * 0.8f),
                            modifier = Modifier
                                .onGloballyPositioned { rects[i] = it.boundsInRoot(); anchors.shelf = rects.filterNotNull() },
                        )
                    }
                }
            }
        }
    }
}

/** Rider [k] departs its basket at 0.85 s + 45 ms × k (550 ms each, 10 steps). */
private fun riderStart(k: Int): Long = CelebrationTimeline.RIDE_AT + k * CelebrationTimeline.RIDE_GAP

/* ---- The stamp card: rises at 2.30 (+320 dp, 400 ms), flips at 2.70 to its holo back, PER-FECT! at 4.30 ---- */

@Composable
private fun StampCard(sc: SceneClock, content: CelebrationContent, anchors: SceneAnchors) {
    val stops = content.stops.take(24)
    // The book's stamp grid scaled to the card: one row up to 4 stops, two up to 16, three beyond; as many columns
    // as that needs, so a short trip gets a small card rather than empty circles.
    val rows = when {
        stops.size <= 4 -> 1
        stops.size <= 16 -> 2
        else -> 3
    }
    val cols = max(1, ceil(stops.size / rows.toFloat()).toInt())
    val stamp = if (stops.size <= 8) 26.dp else 22.dp
    // Never smaller than a card the PER-FECT! hanko can sit on: the stamps centre on it.
    val cardW = maxOf(stamp * cols + 6.dp * (cols - 1) + 28.dp, 132.dp)
    val cardH = maxOf(stamp * rows + 6.dp * (rows - 1) + 24.dp, 74.dp)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val hankoStyle = PicnicTheme.type.hanko
    val hanko = PicnicTheme.colors.hanko
    Box(
        Modifier
            .testTag(CelebrationTags.STAMP_CARD)
            .size(cardW + 34.dp, cardH + 22.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(
            Modifier
                .size(cardW, cardH)
                .onGloballyPositioned { anchors.plates[CelebrationTags.STAMP_CARD] = anchors.unshaken(it) }
                .graphicsLayer {
                    val e = sc.since(BeatName.StampCard)
                    if (!sc.reduced) {
                        val u = (e / 400f).coerceIn(0f, 1f)
                        translationY = (320f * (1f - CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f).transform(u))) * density
                        alpha = if (e < 0) 0f else 1f
                    }
                    val f = flipDegrees(sc)
                    rotationY = f
                    cameraDistance = 14f * density
                },
        ) {
            val back = abs(flipDegrees(sc)) > 90f
            StampCardFace(stops, cols, rows, stamp, back = back, sc)
        }
        // PER-FECT! slams onto its corner at 4.30 (2.2 → 1.25 → 1, −30° → −12°, 3 steps, 260 ms), then its ink ring.
        Canvas(
            Modifier
                .align(Alignment.TopEnd)
                .size(58.dp)
                .plate(anchors, CelebrationTags.PERFECT)
                .graphicsLayer {
                    val e = sc.since(BeatName.Perfect)
                    val pose = BookMotion.perfect(e)
                    alpha = if (sc.reduced) 1f else pose.alpha
                    scaleX = if (sc.reduced) 1f else pose.scaleX
                    scaleY = if (sc.reduced) 1f else pose.scaleY
                    rotationZ = if (sc.reduced) -12f else pose.rotation
                },
        ) {
            drawHanko(HankoText.PerFect, hanko, measurer, hankoStyle)
            val ink = sc.since(BeatName.Shake)
            if (!sc.reduced && ink in 0 until 170) {
                val s = if (ink < 85) 1f else 1.08f
                val a = if (ink < 85) 0.4f else 0.2f
                drawCircle(hanko, size.minDimension / 2f * s, style = Stroke(2.5.dp.toPx()), alpha = a)
            }
        }
    }
}

private fun flipDegrees(sc: SceneClock): Float {
    if (sc.reduced) return 180f
    val e = sc.since(BeatName.Flip)
    if (e <= 0) return 0f
    return 180f * FlipEasing.transform((e / 500f).coerceIn(0f, 1f))
}

@Composable
private fun StampCardFace(stops: List<earth.cora.picnic.ui.list.motion.CelebrationStop>, cols: Int, rows: Int, stamp: Dp, back: Boolean, sc: SceneClock) {
    val c = PicnicTheme.colors
    // The card's front is the sticker book's release paper (the scene is always Day).
    val releasePaper = c.book.releasePaper
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { if (back) rotationY = 180f }
            .hardShadow(shape, PicnicTheme.shadows, large = true)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(3.dp)
            .clip(RoundedCornerShape(8.dp))
            .drawBehind {
                if (back) drawRect(holoBrush(Offset(size.width / 2f, size.height / 2f))) else drawRect(releasePaper)
            }
            .drawWithContent {
                drawContent()
                // The card glint sweeps the back only, ending before PERFECT settles.
                val e = sc.since(BeatName.CardGlint)
                if (back && !sc.reduced && e in 0 until Holo.glint.durationMillis) drawGlint(Holo.glint.progressAt(e))
            }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in 0 until cols) {
                        val i = r * cols + k
                        val stop = stops.getOrNull(i)
                        if (stop == null) Spacer(Modifier.size(stamp)) else StampCircle(
                            look = stop.look,
                            number = stop.look.position.takeIf { it > 0 },
                            stamped = true,
                            now = false,
                            phase = null,
                            clock = sc.clock,
                            size = stamp,
                        )
                    }
                }
            }
        }
    }
}

/* ---- The ribbon banner: a sakura banner with swallow-tail ends and white stitching; the trip's real stats ---- */

@Composable
private fun Banner(sc: SceneClock, content: CelebrationContent, copy: CelebrationCopy, anchors: SceneAnchors) {
    val text = copy.banner(content.stats.stickers, earth.cora.picnic.core.model.TripStats.formatMinutes(content.stats.minutes))
    val shadow = PicnicTheme.shadows.color
    Box(
        Modifier
            .plate(anchors, CelebrationTags.BANNER)
            .heightIn(min = 46.dp)
            .width(324.dp)
            .graphicsLayer {
                val e = sc.since(BeatName.Banner)
                if (sc.reduced) return@graphicsLayer
                scaleX = when {
                    e < 0 -> 0f
                    e < 100 -> 0.1f
                    e < 200 -> 1.08f
                    else -> 1f
                }
                alpha = if (e < 0) 0f else 1f
            }
            .drawBehind { drawBanner(shadow) },
        contentAlignment = Alignment.Center,
    ) {
        PuffyText(
            text,
            style = PicnicTheme.type.cardTitle.copy(fontSize = 16.sp, lineHeight = 20.sp),
            fill = Color.White,
            stroke = 4.dp,
            strokeColor = Families.sakura.trace,
            shadow = null,
            minFontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 34.dp, vertical = 10.dp),
        )
    }
}

/** The banner: a sakura band 46 dp tall with swallow-tail ends, white stitching, a hard shadow. */
private fun DrawScope.drawBanner(shadow: Color) {
    val tail = 18.dp.toPx()
    val notch = 9.dp.toPx()
    val h = size.height
    val w = size.width
    val body = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(w - notch, h / 2f)
        lineTo(w, h)
        lineTo(0f, h)
        lineTo(notch, h / 2f)
        close()
    }
    val inner = Rect(tail, 3.dp.toPx(), w - tail, h - 3.dp.toPx())
    translate(2.4.dp.toPx(), 3.4.dp.toPx()) { drawPath(body, shadow) }
    drawPath(body, Families.sakura.trace)
    drawRoundRect(Color.White, inner.topLeft - Offset(3.dp.toPx(), 3.dp.toPx()), Size(inner.width + 6.dp.toPx(), inner.height + 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
    drawRoundRect(Families.sakura.mid, inner.topLeft, inner.size, androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
    // White stitching inset.
    val stitch = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(inner.deflate(4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))) }
    drawDashedPath(stitch, Color.White, 1.5.dp.toPx(), 4.dp.toPx(), 3.dp.toPx())
}

/* ---- The picnic spread: the gingham blanket, the hamper, the spread, the gold capsule and the bonus card ---- */

@Composable
private fun PicnicSpread(sc: SceneClock, content: CelebrationContent, copy: CelebrationCopy, anchors: SceneAnchors) {
    val shadow = PicnicTheme.shadows.color
    val cloth = PicnicTheme.colors.stickerWhite
    Box(Modifier.fillMaxWidth().height(196.dp)) {
        // The gingham blanket.
        Canvas(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = 4.dp, y = (-6).dp)
                .size(214.dp, 124.dp)
                .graphicsLayer { rotationZ = -2f },
        ) {
            val shape = RoundedCornerShape(12.dp).createOutline(size, layoutDirection, this)
            translate(2.4.dp.toPx(), 3.4.dp.toPx()) { drawOutline(shape, shadow) }
            drawOutline(shape, cloth)
            val inner = Rect(3.dp.toPx(), 3.dp.toPx(), size.width - 3.dp.toPx(), size.height - 3.dp.toPx())
            clipRect(inner.left, inner.top, inner.right, inner.bottom) {
                drawRect(Families.strawberry.pastel, alpha = 0.18f)
                drawGingham(Families.strawberry.mid.copy(alpha = 0.32f), 9.dp.toPx(), inner)
            }
        }
        // The spread pops onto the blanket, 50 ms apart from 1.95 s.
        val spread = listOf("picnic_blanket" to Offset(24f, 140f), "strawberry" to Offset(66f, 160f), "croissant" to Offset(112f, 156f), "milk_bag" to Offset(156f, 150f), "eggs" to Offset(30f, 104f))
        spread.forEachIndexed { k, (key, at) ->
            ItemIcon(
                IconRef(key), null, size = 40.dp,
                modifier = Modifier
                    .offset(at.x.dp, at.y.dp - 20.dp)
                    .graphicsLayer {
                        val e = sc.since(BeatName.Spread) - k * 50L
                        val s = if (sc.reduced) 1f else popScale(e, 400)
                        scaleX = s
                        scaleY = s
                        rotationZ = listOf(-8f, 6f, -4f, 7f, -6f)[k]
                    },
            )
        }
        Hamper(sc, content, anchors, Modifier.align(Alignment.TopStart).offset(x = 52.dp, y = 18.dp))
        BonusCard(sc, content.bonus, copy, anchors, Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 4.dp))
    }
}

/**
 * The open picnic hamper the ribbon ride ends in: the wicker picnic basket at 116 dp, drawn as baskets are (back, the
 * minis, front), so the trip's own stickers sit in it, the newest five showing from 2.1 s as the riders land; until
 * then it shows its dotted landing spot. It pops in at 0.15 s and wobbles at 2.3 s.
 */
@Composable
private fun Hamper(sc: SceneClock, content: CelebrationContent, anchors: SceneAnchors, modifier: Modifier) {
    val minis = remember(content) {
        content.riders.takeLast(earth.cora.picnic.ui.theme.gen.BasketManifest.MAX_MINIS).mapIndexed { k, r -> BasketMini("hamper-$k", r.icon, holo = r.holo) }
    }
    val filled by remember(sc) { derivedStateOf { sc.reduced || sc.t() >= HAMPER_FILLS } }
    val art = earth.cora.picnic.ui.theme.gen.BasketManifest.art(earth.cora.picnic.core.model.BasketKind.WICKER_PICNIC.key)
    val width = 116.dp
    val height = width * (earth.cora.picnic.ui.theme.gen.BasketManifest.VIEWPORT_HEIGHT / earth.cora.picnic.ui.theme.gen.BasketManifest.VIEWPORT_WIDTH)
    Box(
        modifier
            .size(width, height)
            .testTag(CelebrationTags.HAMPER)
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                anchors.hamper = b
                val rim = (art?.rimY ?: 24f) / earth.cora.picnic.ui.theme.gen.BasketManifest.VIEWPORT_WIDTH * b.width
                anchors.hamperMouth = Offset(b.center.x, b.top + rim)
            }
            .graphicsLayer {
                val e = sc.since(BeatName.Hamper)
                val s = if (sc.reduced) 1f else popScale(e, 400)
                scaleX = s
                scaleY = s
                // The hamper wobbles at 2.3 s.
                val wob = sc.t() - 2300
                rotationZ = if (sc.reduced) 0f else when {
                    wob < 0 -> 0f
                    wob < 90 -> -6f
                    wob < 180 -> 5f
                    wob < 270 -> -2f
                    else -> 0f
                }
                transformOrigin = TransformOrigin(0.5f, 0.9f)
            },
    ) {
        MotionBasket(
            kind = earth.cora.picnic.core.model.BasketKind.WICKER_PICNIC,
            look = AisleLooks.of(1),
            gotten = minis,
            toGet = 0,
            complete = false,
            presentation = BasketPresentation(minisHidden = !filled),
            clock = sc.clock,
            size = DpSize(width, height),
        )
    }
}

/** The hamper's minis show as the riders land (§11.2: hamper minis at 2.1 s). */
private const val HAMPER_FILLS: Long = 2100

@Composable
private fun BonusCard(sc: SceneClock, bonus: BonusSticker?, copy: CelebrationCopy, anchors: SceneAnchors, modifier: Modifier) {
    val c = PicnicTheme.colors
    Box(modifier.size(132.dp, 150.dp)) {
        // The gold capsule wobbles twice, then pops behind the card.
        ItemIcon(
            IconRef("_capsule"), null, size = 52.dp, jackpot = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .graphicsLayer {
                    if (sc.reduced) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val wob = sc.since(BeatName.CapsuleWobble)
                    val pop = sc.since(BeatName.CapsulePop)
                    alpha = if (wob < -400) 0f else 1f
                    rotationZ = when {
                        wob < 0 -> 0f
                        wob < 600 -> listOf(-8f, 0f, 8f)[((wob % 300) / 100).toInt().coerceIn(0, 2)]
                        else -> 0f
                    }
                    if (pop >= 0) {
                        val s = when {
                            pop < 150 -> 1.2f
                            pop < 300 -> 0.85f
                            else -> 0f
                        }
                        scaleX = s
                        scaleY = s
                    }
                },
        )
        // The holo card rises out of the capsule (500 ms, cubic-bezier(.3, 1.5, .5, 1)); no glint on it.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .plate(anchors, CelebrationTags.BONUS)
                .graphicsLayer {
                    val e = sc.since(BeatName.Bonus)
                    if (sc.reduced) return@graphicsLayer
                    val k = PopEasing.transform((e / 500f).coerceIn(0f, 1f))
                    alpha = if (e < 0) 0f else 1f
                    translationY = (60f * (1f - k)) * density
                    val s = 0.4f + 0.6f * k
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .hardShadow(RoundedCornerShape(12.dp), PicnicTheme.shadows, large = true)
                .clip(RoundedCornerShape(12.dp))
                .background(c.stickerWhite)
                .padding(4.dp)
                .clip(RoundedCornerShape(9.dp))
                .drawBehind { drawRect(holoBrush(Offset(size.width * 0.3f, size.height * 0.3f))) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                // After it is put into the album pocket, the card keeps its peeled ghost with a small sakura heart.
                val inPocket by remember(sc) { derivedStateOf { sc.since(BeatName.PocketMagic) >= 0 } }
                BonusArt(bonus, 52.dp, ghost = inPocket)
                if (inPocket) DecorSticker("heart", Modifier.align(Alignment.TopEnd), size = 16.dp)
            }
            PicnicText(
                copy.bonus,
                style = PicnicTheme.type.smallTitle.copy(color = c.ballpoint, fontSize = 15.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** The bonus sticker: a library sticker (holo) or a picnic-sheet decor sticker. */
@Composable
private fun BonusArt(bonus: BonusSticker?, size: Dp, ghost: Boolean, modifier: Modifier = Modifier) {
    when {
        bonus?.icon != null -> ItemIcon(bonus.icon, null, modifier, size = size, holo = !ghost, ghost = ghost)
        bonus?.decor != null -> DecorSticker(bonus.decor, modifier.graphicsLayer { alpha = if (ghost) 0.28f else 1f }, size = size)
        else -> DecorSticker("star", modifier.graphicsLayer { alpha = if (ghost) 0.28f else 1f }, size = size)
    }
}

/* ------------------------------------------------------------------------------------------------
 * The ribbon ride: a sakura ribbon from the shelf into the hamper; up to 24 riders, 45 ms apart from 0.85 s.
 * ------------------------------------------------------------------------------------------------ */

/** The ribbon path from the shelf to the hamper (scene px): a loop to the right, then down into the hamper's mouth. */
private fun ribbonPath(from: Offset, to: Offset, w: Float): Path = Path().apply {
    moveTo(from.x, from.y)
    cubicTo(w * 0.95f, from.y + (to.y - from.y) * 0.15f, w * 0.92f, from.y + (to.y - from.y) * 0.62f, w * 0.5f, from.y + (to.y - from.y) * 0.55f)
    cubicTo(w * 0.12f, from.y + (to.y - from.y) * 0.5f, w * 0.1f, from.y + (to.y - from.y) * 0.85f, to.x, to.y)
}

/** A point at fraction [u] along the rider's path from [start] to [end] (the ribbon's two arcs). */
private fun riderPoint(start: Offset, end: Offset, w: Float, u: Float): Offset {
    val dy = end.y - start.y
    val p0 = start
    val p1 = Offset(w * 0.95f, start.y + dy * 0.15f)
    val p2 = Offset(w * 0.92f, start.y + dy * 0.62f)
    val p3 = Offset(w * 0.5f, start.y + dy * 0.55f)
    val q1 = Offset(w * 0.12f, start.y + dy * 0.5f)
    val q2 = Offset(w * 0.1f, start.y + dy * 0.85f)
    return if (u < 0.5f) cubic(p0, p1, p2, p3, u * 2f) else cubic(p3, q1, q2, end, (u - 0.5f) * 2f)
}

private fun cubic(a: Offset, b: Offset, c: Offset, d: Offset, t: Float): Offset {
    val m = 1f - t
    return a * (m * m * m) + b * (3 * m * m * t) + c * (3 * m * t * t) + d * (t * t * t)
}

@Composable
private fun Ribbon(sc: SceneClock, anchors: SceneAnchors) {
    if (sc.reduced) return
    // The ribbon: white 13 dp under sakura 8 dp with a dotted stitch, drawn in 12 steps over 1.2 s, gone by 2.75 s;
    // behind every plate, so the rising stamp card covers it rather than wearing it.
    Canvas(Modifier.fillMaxSize()) {
        val e = sc.since(BeatName.RibbonDraw)
        if (e < 0 || sc.t() > 2750) return@Canvas
        val shelf = anchors.shelf
        if (shelf.isEmpty() || anchors.hamper == Rect.Zero) return@Canvas
        val o = anchors.origin
        val from = Offset(shelf.first().center.x - o.x, shelf.maxOf { it.bottom } - o.y)
        val to = anchors.hamperMouth - o
        val path = ribbonPath(from, to, size.width)
        val drawn = Stepped(12).transform((e / 1200f).coerceIn(0f, 1f))
        val fade = if (sc.t() > 2450) (1f - (sc.t() - 2450) / 300f).coerceIn(0f, 1f) else 1f
        val m = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val part = Path()
        m.getSegment(0f, m.length * drawn, part, true)
        drawPath(part, Color.White, alpha = fade, style = Stroke(13.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawPath(part, Families.sakura.mid, alpha = fade, style = Stroke(8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawPath(
            part, Color.White, alpha = fade,
            style = Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
        )
    }
}

/** The riders: the newest gotten stickers ride the ribbon from their baskets into the hamper (in front of it all). */
@Composable
private fun Riders(sc: SceneClock, content: CelebrationContent, anchors: SceneAnchors) {
    if (sc.reduced) return
    val riders = content.riders
    Box(Modifier.fillMaxSize()) {
        riders.forEachIndexed { k, rider ->
            ItemIcon(
                rider.icon, null, size = 30.dp, holo = rider.holo,
                modifier = Modifier
                    .offset {
                        val shelf = anchors.shelf
                        val o = anchors.origin
                        val startRect = shelf.getOrNull(rider.stopIndex) ?: shelf.firstOrNull() ?: Rect.Zero
                        val start = startRect.center - o
                        val end = anchors.hamperMouth - o
                        val u = riderU(sc, k)
                        val p = riderPoint(start, end, (anchors.hamper.right - o.x).coerceAtLeast(1f) * 1.6f, u)
                        IntOffset((p.x - 15.dp.toPx()).roundToInt(), (p.y - 15.dp.toPx()).roundToInt())
                    }
                    .graphicsLayer {
                        val e = sc.t() - riderStart(k)
                        if (e < 0 || e > CelebrationTimeline.RIDE_EACH + 120) {
                            alpha = 0f
                        } else if (e > CelebrationTimeline.RIDE_EACH) {
                            // Landing squash 1.15 / .8, then into the hamper.
                            scaleX = 1.15f
                            scaleY = 0.8f
                        }
                    },
            )
        }
    }
}

/** Rider [k]'s progress along its path: 10 steps over 550 ms. */
private fun riderU(sc: SceneClock, k: Int): Float {
    val e = sc.t() - riderStart(k)
    if (e <= 0) return 0f
    return Stepped(10).transform((e.toFloat() / CelebrationTimeline.RIDE_EACH).coerceIn(0f, 1f)).coerceAtMost(1f)
}

/* ---- Four 48 dp holo kira stars round the title, outside its bounds; they pop (3 steps, 250 ms), then twinkle ---- */

@Composable
private fun KiraStars(sc: SceneClock, anchors: SceneAnchors) {
    val loop by rememberLoopTime(enabled = !sc.reduced)
    val density = LocalDensity.current
    for (k in 0 until 4) {
        DecorSticker(
            "kira",
            size = KIRA,
            shadow = false,
            modifier = Modifier
                .offset {
                    val r = kiraRect(anchors, k, density.density)
                    IntOffset((r.left - anchors.origin.x).roundToInt(), (r.top - anchors.origin.y).roundToInt())
                }
                .graphicsLayer {
                    // Read the clock first, so this block runs again every frame whatever the anchors say.
                    val e = sc.since(BeatName.Kira) - k * 50L
                    val r = kiraRect(anchors, k, density.density)
                    if (r == Rect.Zero) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    alpha = 1f
                    val pop = when {
                        sc.reduced -> 1f
                        e < 0 -> 0f
                        e < 83 -> 0.5f
                        e < 166 -> 1.4f
                        else -> 1f
                    }
                    val tw = if (sc.reduced || e < 250) Pose() else Twinkle.at(loop, k * 90L)
                    val s = pop * tw.scale * (r.width / (KIRA.value * density.density))
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = s
                    scaleY = s
                    rotationZ = tw.rotation
                },
        )
    }
}

/** The kira stars' size (§11.2: 48 dp), where the room beside the title allows it. */
private val KIRA: Dp = 48.dp

/**
 * Kira star [k]'s bounds (root px), clear of every text plate: the upper pair beside the title (48 dp where the room
 * there allows, smaller where it does not), the lower pair smaller, tucked under the title's ends beside the chip. Zero
 * when there is no room (a long title at font scale 2).
 */
private fun kiraRect(anchors: SceneAnchors, k: Int, density: Float): Rect {
    val title = anchors.title
    if (title == Rect.Zero) return Rect.Zero
    val left = k % 2 == 0
    val gap = 4f * density
    val room = if (left) title.left - anchors.origin.x - 2 * gap else anchors.sceneRight - title.right - 2 * gap
    val upper = min(KIRA.value * density, room)
    if (upper < 18f * density) return Rect.Zero
    val r = if (k < 2) {
        val x = if (left) title.left - gap - upper else title.right + gap
        Rect(Offset(x, title.top + upper * 0.1f), Size(upper, upper))
    } else {
        val size = upper * 0.72f
        val tuck = 8f * density
        val x = if (left) title.left + tuck - size else title.right - tuck
        Rect(Offset(x, title.bottom + 2f * density), Size(size, size))
    }
    // Never over another plate (the chip grows with the font scale).
    val crosses = anchors.plates.any { (tag, b) -> tag != CelebrationTags.SUMMARY_CARD && b.overlaps(r) }
    return if (crosses) Rect.Zero else r
}

/* ------------------------------------------------------------------------------------------------
 * The album corner: the scene's bottom-right corner peels back (a 150 dp triangle, 4 steps, 400 ms) beside the bonus
 * card, revealing the album page (pink pinstripe vinyl, two pockets); a kira bloom, then the bonus magic-appears.
 * ------------------------------------------------------------------------------------------------ */

private val CORNER: Dp = 150.dp

@Composable
private fun AlbumCorner(sc: SceneClock, bonus: BonusSticker?, w: Dp, h: Dp) {
    val c = PicnicTheme.colors
    val vinyl = c.albumVinyl
    val stripe = c.albumStripe
    val shadow = PicnicTheme.shadows.color
    // The scene page's back: white with a breath of periwinkle.
    val flapBack = androidx.compose.ui.graphics.lerp(c.stickerWhite, Families.periwinkle.pastel, 0.22f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val e = sc.since(BeatName.Curl)
            val f = if (sc.reduced) 1f else when {
                e < 0 -> 0f
                e < 100 -> 0.25f
                e < 200 -> 0.5f
                e < 300 -> 0.75f
                else -> 1f
            }
            if (f <= 0f) return@Canvas
            val leg = CORNER.toPx() * f
            val W = size.width
            val H = size.height
            val corner = Path().apply {
                moveTo(W - leg, H)
                lineTo(W, H)
                lineTo(W, H - leg)
                close()
            }
            // The album page under the curl: pink pinstripe vinyl.
            clipPath(corner) {
                drawRect(vinyl)
                var x = 0f
                while (x < W) {
                    drawRect(stripe, Offset(x, 0f), Size(2.dp.toPx(), H))
                    x += 10.dp.toPx()
                }
                for (p in pocketRects(W, H, this)) {
                    drawRoundRect(Color.White.copy(alpha = 0.55f), p.topLeft, p.size, androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                    drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(p.left, p.top), Size(p.width, 9.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                    drawRoundRect(Families.periwinkle.mid.copy(alpha = 0.5f), p.topLeft, p.size, androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
            // The flap: the scene's corner folded back over the fold line.
            val flap = Path().apply {
                moveTo(W - leg, H)
                lineTo(W, H - leg)
                lineTo(W - leg, H - leg)
                close()
            }
            translate(2.4.dp.toPx(), 3.4.dp.toPx()) { drawPath(flap, shadow) }
            drawPath(flap, flapBack)
            drawPath(flap, Families.periwinkle.mid, style = Stroke(1.dp.toPx()))
            drawLine(Color.White, Offset(W - leg, H), Offset(W, H - leg), 1.5.dp.toPx())
        }
        // The kira bloom at the pocket, then the bonus sticker magic-appears in it (4 poses, 320 ms).
        val density = LocalDensity.current
        val pocket = with(density) { pocketRects(w.toPx(), h.toPx(), this).first() }
        val pocketDp = with(density) { DpSize(pocket.width.toDp(), pocket.height.toDp()) }
        Box(
            Modifier
                .offset(with(density) { pocket.left.toDp() }, with(density) { pocket.top.toDp() })
                .size(pocketDp),
            contentAlignment = Alignment.Center,
        ) {
            DecorSticker(
                "kira",
                size = 40.dp,
                shadow = false,
                modifier = Modifier.graphicsLayer {
                    val pose = BookMotion.bloom(sc.since(BeatName.PocketKira))
                    if (sc.reduced || pose == null) alpha = 0f else {
                        scaleX = pose.scaleX
                        scaleY = pose.scaleY
                        rotationZ = pose.rotation
                    }
                },
            )
            BonusArt(
                bonus, 40.dp, ghost = false,
                modifier = Modifier.graphicsLayer {
                    val e = sc.since(BeatName.PocketMagic)
                    val pose = BookMotion.twin(e, 3)
                    if (sc.reduced) {
                        rotationZ = BookMotion.tilt(3)
                    } else {
                        alpha = pose.alpha
                        scaleX = pose.scaleX
                        scaleY = pose.scaleY
                        rotationZ = pose.rotation
                    }
                },
            )
        }
    }
}

/** The two album pockets inside the 150 dp corner (scene px): the bonus goes into the first. */
private fun pocketRects(w: Float, h: Float, density: androidx.compose.ui.unit.Density): List<Rect> = with(density) {
    listOf(
        Rect(Offset(w - 66.dp.toPx(), h - 78.dp.toPx()), Size(50.dp.toPx(), 52.dp.toPx())),
        Rect(Offset(w - 112.dp.toPx(), h - 40.dp.toPx()), Size(40.dp.toPx(), 30.dp.toPx())),
    )
}

/* ------------------------------------------------------------------------------------------------
 * The bottom band: "tap to skip" (until the curl). The summary's washi progress bar "Closing… tap to keep it" runs
 * along the summary card's bottom edge (SummaryCard), or its Done button once kept. No countdown under TalkBack.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun BottomBand(sc: SceneClock, copy: CelebrationCopy, anchors: SceneAnchors, onDone: () -> Unit) {
    val c = PicnicTheme.colors
    Box(Modifier.fillMaxSize().padding(start = 12.dp, bottom = 14.dp), contentAlignment = Alignment.BottomStart) {
        // Kept open (§0.4 Q7): Done closes it.
        if (sc.cele.keptAt != null) PicnicButton(copy.done, onDone, Modifier.testTag(CelebrationTags.DONE))
        if (!sc.reduced) {
            Box(
                Modifier
                    .plate(anchors, CelebrationTags.SKIP)
                    .graphicsLayer {
                        val e = sc.since(BeatName.SkipChip)
                        val out = sc.since(BeatName.SkipChipOut)
                        alpha = when {
                            e < 0 -> 0f
                            out >= 0 -> (1f - out / 120f).coerceIn(0f, 1f)
                            else -> (e / 200f).coerceIn(0f, 1f)
                        }
                    },
            ) {
                SceneChip(copy.skip, PicnicTheme.type.label.copy(color = c.ballpoint, fontSize = 13.sp, lineHeight = 17.sp), maxLines = 1)
            }
        }
    }
}

/**
 * The washi-tape progress bar (§0.4 Q7): its label "Closing… tap to keep it" on a lemon slip, and under it a strip of
 * lemon-dot washi that fills with sakura stripe left to right over the summary hold (8 steps under reduced motion). The
 * label sits above the strip so no part of the fill is ever hidden.
 */
@Composable
private fun ClosingBar(sc: SceneClock, copy: CelebrationCopy, summaryAt: Long, anchors: SceneAnchors, modifier: Modifier, placeholder: Boolean = false) {
    val c = PicnicTheme.colors
    // The bar fills over the hold the engine waits ("Time to take action" included), so it is full as it closes.
    val hold = sc.cele.summaryHoldMs.takeIf { it > 0 } ?: sc.plan.summaryHold
    val start = if (sc.reduced) 120L else summaryAt
    Column(
        modifier
            .then(if (placeholder) Modifier.clearAndSetSemantics { } else Modifier.testTag(CelebrationTags.CLOSING))
            .graphicsLayer {
                val e = sc.clock.now - sc.cele.t0 - start
                alpha = if (e < 0 || placeholder) 0f else 1f
            },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier
                .padding(start = 4.dp)
                .then(if (placeholder) Modifier else Modifier.plate(anchors, CelebrationTags.CLOSING_LABEL))
                .clip(RoundedCornerShape(5.dp))
                .background(Families.lemon.pastel)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            PicnicText(copy.closing, style = PicnicTheme.type.label.copy(color = c.ballpoint, fontSize = 13.sp, lineHeight = 17.sp), maxLines = 2)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(STRIP)
                .graphicsLayer { rotationZ = -1.2f },
        ) {
            drawWashi(WashiPattern.LemonDots)
            val p = closingProgress(sc, start, hold)
            if (p > 0f) clipRect(right = size.width * p) { drawWashi(WashiPattern.SakuraStripe) }
        }
    }
}

/** How far the closing bar has filled (0..1), stepped in 8 under reduced motion. */
private fun closingProgress(sc: SceneClock, start: Long, hold: Long): Float =
    closingFill(sc.clock.now - sc.cele.t0 - start, hold, sc.reduced)

/** The closing bar's fill [elapsed] ms into a summary hold of [hold] ms (0..1; 8 steps under [reduced] motion). */
internal fun closingFill(elapsed: Long, hold: Long, reduced: Boolean): Float {
    val raw = (elapsed.coerceAtLeast(0).toFloat() / hold).coerceIn(0f, 1f)
    return if (reduced) Stepped(8).transform(raw) else raw
}

/* ------------------------------------------------------------------------------------------------
 * Tests only: report every text plate's pose and every crossing overlay to the probe, every frame.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun ProbeReports(sc: SceneClock, probe: MotionProbe, anchors: SceneAnchors, origin: Offset, size: Size) {
    // Read the clock in composition (tests only) so every frame reports.
    val now = sc.clock.now
    val density = LocalDensity.current.density
    SideEffect {
        val dx = shakeDx(sc) * density
        val a = sc.sceneAlpha()
        fun report(tag: String, pose: MotionPose, alpha: Float = pose.alpha) {
            val b = anchors.plates[tag] ?: return
            probe.text(tag, now, b, dx = dx + pose.dx * density, dy = pose.dy * density, scale = pose.scaleX, rotation = pose.rotation, alpha = alpha * a)
        }
        report(CelebrationTags.TITLE, titlePose(sc.since(BeatName.Title), sc.reduced))
        report(CelebrationTags.CHIP, MotionPose.Rest, textBeatAlpha(sc, BeatName.Title))
        report(CelebrationTags.LCD, MotionPose.Rest, textBeatAlpha(sc, BeatName.Title))
        if (!sc.reduced) {
            val skipOut = sc.since(BeatName.SkipChipOut)
            val skipAlpha = if (skipOut >= 0) (1f - skipOut / 120f).coerceIn(0f, 1f) else (sc.since(BeatName.SkipChip) / 200f).coerceIn(0f, 1f)
            report(CelebrationTags.SKIP, MotionPose.Rest, skipAlpha)
        }
        val perfect = if (sc.reduced) MotionPose(scale = 1f, rotation = -12f) else BookMotion.perfect(sc.since(BeatName.Perfect))
        report(CelebrationTags.PERFECT, perfect)
        val be = sc.since(BeatName.Banner)
        val bannerScale = when {
            sc.reduced -> 1f
            be < 0 -> 0f
            be < 100 -> 0.1f
            be < 200 -> 1.08f
            else -> 1f
        }
        report(CelebrationTags.BANNER, MotionPose(scale = bannerScale, alpha = if (be < 0 && !sc.reduced) 0f else 1f))
        val bo = sc.since(BeatName.Bonus)
        val k = if (sc.reduced) 1f else PopEasing.transform((bo / 500f).coerceIn(0f, 1f))
        report(CelebrationTags.BONUS, MotionPose(dy = 60f * (1f - k), scaleX = 0.4f + 0.6f * k, scaleY = 0.4f + 0.6f * k, alpha = if (bo < 0 && !sc.reduced) 0f else 1f))
        if (!sc.cele.talkBack && sc.cele.keptAt == null) {
            // The progress bar's label arrives with the summary and stays put while the strip under it fills.
            val start = if (sc.reduced) 120L else sc.at(BeatName.Summary)
            report(CelebrationTags.CLOSING_LABEL, MotionPose.Rest, if (sc.clock.now - sc.cele.t0 - start < 0) 0f else 1f)
        }
        if (sc.reduced) return@SideEffect
        // Overlays that could cross a text: the shake, the curl, the glints, the ink ring, the kira stars.
        if (dx != 0f) probe.overlay("cele_shake", now, Rect(origin, size))
        if (sc.since(BeatName.Curl) >= 0) {
            val leg = CORNER.value * density
            probe.overlay("cele_curl", now, Rect(origin.x + size.width - leg, origin.y + size.height - leg, origin.x + size.width, origin.y + size.height))
        }
        if (sc.since(BeatName.TitleGlint) in 0 until Holo.glint.durationMillis) probe.overlay("cele_title_glint", now, anchors.plates[CelebrationTags.TITLE])
        if (sc.since(BeatName.CardGlint) in 0 until Holo.glint.durationMillis) probe.overlay("cele_card_glint", now, anchors.plates[CelebrationTags.STAMP_CARD])
        if (sc.since(BeatName.Shake) in 0 until 170) probe.overlay("cele_ink", now, anchors.plates[CelebrationTags.PERFECT])
        for (s in 0 until 4) {
            val r = kiraRect(anchors, s, density)
            if (r != Rect.Zero && sc.since(BeatName.Kira) - s * 50L >= 0) probe.overlay("cele_kira_$s", now, r)
        }
        if (sc.since(BeatName.Confetti) >= 0) probe.overlay("cele_confetti", now, Rect(origin, size), behind = true)
        probe.overlay("cele_rays", now, Rect(origin, size), behind = true)
    }
}

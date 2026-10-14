package earth.cora.picnic.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.traversalIndex
import earth.cora.picnic.R
import earth.cora.picnic.ui.a11y.modalLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The letter-set sheet (docs/DESIGN_STICKER.md §4.16, item 69): Add, Edit, the list switcher, the Trip book and New trip.
 * Owner: WP4 (SheetTest).
 */

/** The washi handle's current scale (SheetTest reads the slap). */
val HandleScale: SemanticsPropertyKey<Float> = SemanticsPropertyKey("HandleScale")
internal var SemanticsPropertyReceiver.handleScale: Float by HandleScale

/** The sheet's test tag. */
const val LETTERSET_TAG: String = "picnic_letterset"

/** The sheet's perforated top: 4 dp semicircle bites every 10 dp, 20 dp top corners. */
val LetterSetShape: BittenEdgeShape = BittenEdgeShape(bite = 4.dp, every = 10.dp, topCorners = 20.dp)

/**
 * A letter-set sheet over the screen (§4.16): the list, app bar and LCD stay visible behind a 38 % ballpoint scrim. A
 * pad sheet with rules and a margin line at 22 dp, 20 dp top corners, a perforated top edge and a hard shadow (0, −3);
 * a pink-gingham washi handle (60 × 16 dp, −3°) straddles the top edge and slaps on a beat after the sheet arrives
 * (1.6 → 1.2 → 1, 2 poses). Slides up 280 ms `cubic-bezier(.2, .9, .3, 1.1)` (a small overshoot); exits 260 ms; 120 ms
 * fades under reduced motion. [footer] is sticky; while more lies below it, the content fades out above it. Back and the scrim close it.
 *
 * [topClearance]: the height the screen's app bar takes under the status bar (WP7's Add sheet passes the 72 dp app bar).
 * A tall sheet then stops below it, so the title, the LCD and More stay visible behind the scrim (§4.16); without it
 * the sheet is capped at 92 % of the window.
 */
@Composable
fun LetterSetSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    topClearance: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val enter = remember { Animatable(0f) }
    // The scrim fades on its own over the sheet's 280 ms (out over its 260 ms), evenly: the sheet's overshooting curve
    // would put it nearly half on in the first frame.
    val scrim = remember { Animatable(0f) }
    var present by remember { mutableStateOf(visible) }
    var handle by remember { mutableStateOf(if (visible) 1f else PicnicMotion.handleSlapScales.first()) }
    // The handle "slaps on" a beat after the sheet arrives: hidden until then.
    var handleShown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        withContext(FixedMotionScale) {
            if (visible) {
                present = true
                handle = PicnicMotion.handleSlapScales.first()
                if (reduced) {
                    enter.animateTo(1f, tween(PicnicMotion.rmFade.durationMillis))
                    handle = 1f
                    handleShown = true
                } else {
                    handleShown = false
                    enter.animateTo(1f, tween(PicnicMotion.sheetIn.durationMillis, easing = PicnicMotion.sheetIn.easing))
                    handleShown = true
                    // The handle slaps on: 1.6 → 1.2 → 1, one pose each.
                    for (s in PicnicMotion.handleSlapScales) {
                        handle = s
                        if (s != 1f) delay(PicnicMotion.pose.toLong())
                    }
                }
            } else if (present) {
                enter.animateTo(0f, tween(if (reduced) PicnicMotion.rmFade.durationMillis else PicnicMotion.sheetOut.durationMillis, easing = PicnicMotion.sheetOut.easing))
                present = false
            }
        }
    }
    LaunchedEffect(visible) {
        val ms = when {
            reduced -> PicnicMotion.rmFade.durationMillis
            visible -> PicnicMotion.sheetIn.durationMillis
            else -> PicnicMotion.sheetOut.durationMillis
        }
        withContext(FixedMotionScale) { scrim.animateTo(if (visible) 1f else 0f, tween(ms, easing = LinearEasing)) }
    }
    if (!present && !visible) return
    BackHandler(enabled = visible, onBack = onDismissRequest)
    val c = PicnicTheme.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxH = maxHeight
        // Below the app bar when asked (never under 60 % of a small window), else 92 % of the window.
        val cap = if (topClearance == null) maxH * 0.92f else minOf(maxH * 0.92f, maxOf(maxH * 0.6f, maxH - statusTop - topClearance))
        // The scrim: a tap closes the sheet. Never a keyboard stop (Escape and Back close it, §15); for TalkBack it is the
        // "Close sheet" button read after the sheet's own controls, and it covers the screen behind, so TalkBack stays in
        // the sheet (a scrim that said nothing was a nameless button over the whole screen). Fading out with a leaving
        // sheet it is only a shade: a tap goes through to the screen behind, and TalkBack reads that screen again.
        val closeLabel = stringResource(R.string.a11y_close_sheet)
        val dismiss by rememberUpdatedState(onDismissRequest)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrim.value }
                .background(c.scrim)
                .then(
                    if (visible) {
                        Modifier
                            .pointerInput(Unit) { detectTapGestures { dismiss() } }
                            .clearAndSetSemantics {
                                contentDescription = closeLabel
                                traversalIndex = 1f
                                onClick(label = closeLabel) {
                                    dismiss()
                                    true
                                }
                            }
                    } else {
                        Modifier.clearAndSetSemantics { }
                    },
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = cap)
                // Opened from the keyboard, the sheet takes focus and keeps it (Tab never wanders behind the scrim);
                // Escape closes it as Back does (§15).
                .then(if (visible) Modifier.modalLayer(onDismissRequest) else Modifier)
                .graphicsLayer {
                    if (reduced) {
                        alpha = enter.value
                    } else {
                        translationY = (1f - enter.value) * size.height
                    }
                }
                .testTag(LETTERSET_TAG)
                // Sliding away it is only a picture of what it showed: nothing in it takes a touch or reads to TalkBack.
                .then(if (visible) Modifier else Modifier.leaving()),
        ) {
            LetterSetSurface(Modifier.padding(top = 8.dp), footer = footer, handleScale = if (handleShown) handle else 0f, glide = !reduced, content = content)
        }
    }
}

/**
 * The sheet's paper (§4.16) without the overlay mechanics: for sheets hosted elsewhere (a ModalBottomSheet with a
 * transparent container). Pad, rules every 24 dp, the margin line at 22 dp, the perforated top, the (0, −3) shadow and
 * the washi handle.
 */
@Composable
fun LetterSetSurface(
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    handleScale: Float = 1f,
    /** The sheet glides to a new height (§4.16); off under reduced motion, where it changes at once. */
    glide: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A floating layer: it re-roots on the binder's colours, then lays its own paper (§2.4.1).
    OnPaper(null) { LetterSetPaper(modifier, footer, handleScale, glide, content) }
}

@Composable
private fun LetterSetPaper(
    modifier: Modifier,
    footer: (@Composable () -> Unit)?,
    handleScale: Float,
    glide: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    // The sticky footer's height as last laid out (none: 0), which the glide counts in (glideHeight).
    val below = remember { FooterRoom() }
    if (footer == null) below.px = 0
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val outline = LetterSetShape.createOutline(size, layoutDirection, this)
                    translate(0f, -3.dp.toPx()) { drawOutline(outline, shadows.color) }
                }
                .clip(LetterSetShape)
                .background(c.pad)
                .ruledLines(24.dp, c.rule.copy(alpha = 0.7f), firstLine = 64.dp, margin = c.margin.copy(alpha = 0.7f), marginX = 22.dp)
                .drawBehind { drawPerforationRims(corner = 20.dp) },
        ) {
            // The sheet's paper: its content takes the pad's inks; round buttons on it draw their rim (§17).
            OnPaper(c.pad) {
                val scroll = rememberScrollState()
                val fadeFooter = footer != null
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .then(if (glide) Modifier.glideHeight(below) else Modifier)
                        // The scroll's viewport starts under the perforated rim and the washi handle, so what scrolls up passes
                        // under the paper's edge instead of over its bites and the tape (§4.16). At rest nothing moves.
                        .padding(top = SCROLL_TOP)
                        .scrollEdgeFade(scroll, top = TOP_FADE, bottom = if (fadeFooter) FOOTER_FADE else 0.dp)
                        .verticalScroll(scroll)
                        .padding(start = 34.dp, end = 18.dp, top = 26.dp - SCROLL_TOP, bottom = if (footer == null) 24.dp else 8.dp),
                    content = content,
                )
                if (footer != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // Measured before the content above it (a column measures its weighted child last).
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                below.px = placeable.height
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            }
                            .drawBehind { drawRect(c.pad) }
                            .padding(start = 22.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) { footer() }
                }
            }
        }
        SheetHandle(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .semantics { this.handleScale = handleScale }
                .graphicsLayer {
                    scaleX = handleScale
                    scaleY = handleScale
                },
        )
    }
}

/** The sticky footer's height in px as last laid out (0 without one). */
private class FooterRoom {
    var px: Int = 0
}

/**
 * The sheet's content glides to a new height (§4.16, §8.1: layout moves smoothly): when what it holds grows or shrinks
 * (a thinking step, the review card arriving), the sheet's height (this area and the sticky footer [below] it, which
 * may come or go in the same frame) moves over the 240 ms `layout` on the fixed motion scale, so its top edge never
 * jumps. The first height is taken as it is (the sheet slides in at it). While it glides, what does not fit yet is cut
 * at this area's top and bottom edges, never its sides.
 */
private fun Modifier.glideHeight(below: FooterRoom): Modifier = this then GlideHeightElement(below)

private class GlideHeightElement(private val below: FooterRoom) : ModifierNodeElement<GlideHeightNode>() {
    override fun create() = GlideHeightNode(below)

    override fun update(node: GlideHeightNode) {
        node.below = below
    }

    override fun hashCode(): Int = below.hashCode()

    override fun equals(other: Any?): Boolean = other is GlideHeightElement && other.below === below
}

private class GlideHeightNode(var below: FooterRoom) : Modifier.Node(), LayoutModifierNode, DrawModifierNode {
    /** The sheet's height from here down (this area and the footer), as shown; null until first laid out. */
    private var shown: Animatable<Float, AnimationVector1D>? = null
    private var target = 0

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val footer = below.px
        val whole = placeable.height + footer
        val anim = shown ?: Animatable(whole.toFloat()).also {
            shown = it
            target = whole
        }
        if (whole != target) {
            target = whole
            coroutineScope.launch(FixedMotionScale) {
                anim.animateTo(whole.toFloat(), tween(PicnicMotion.layout.durationMillis, easing = PicnicMotion.layout.easing))
            }
        }
        val h = constraints.constrainHeight((anim.value.roundToInt() - footer).coerceAtLeast(0))
        return layout(placeable.width, h) { placeable.place(0, 0) }
    }

    // Asked for its natural size, it answers as its content does (a glide is never started by a question).
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = measurable.minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = measurable.maxIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = measurable.minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = measurable.maxIntrinsicWidth(height)

    override fun ContentDrawScope.draw() {
        if (shown?.isRunning == true) {
            clipRect(-size.width, 0f, size.width * 2f, size.height) { this@draw.drawContent() }
        } else {
            drawContent()
        }
    }
}

/**
 * What a sheet shows of [value]: the value itself while the sheet is [open], and the last one it showed open while it
 * slides away (§4.16), so it leaves as she saw it, at its height, never emptied or changed by what its host has
 * already moved on to (the book closed to null, the confirmed list gone, the item's new sticker).
 */
@Composable
fun <T> keptWhileLeaving(open: Boolean, value: T): T {
    val kept = remember { Kept(value) }
    if (open) kept.value = value
    return if (open) value else kept.value
}

/** The value a leaving sheet keeps showing ([keptWhileLeaving]). */
private class Kept<T>(var value: T)

/**
 * Hosts a letter-set sheet that is up while [value] is not null (§4.16): [sheet] gets what to show and whether it is
 * open. Closed, the sheet stays for its exit (`sheetOut`, or the reduced-motion fade) still showing the value it had
 * open, so it slides away as she saw it instead of vanishing in one frame; then it leaves the composition, its own state
 * with it.
 */
@Composable
fun <T : Any> SheetFor(value: T?, sheet: @Composable (shown: T, open: Boolean) -> Unit) {
    val reduced = LocalReducedMotion.current
    val shown = keptWhileLeaving(value != null, value) ?: return
    // Whether the sheet's exit has played out since it last closed (opened again, it is back at once).
    var left by remember { mutableStateOf(false) }
    LaunchedEffect(value == null) {
        left = false
        if (value != null) return@LaunchedEffect
        withContext(FixedMotionScale) {
            holdOnFrameClock((if (reduced) PicnicMotion.rmFade else PicnicMotion.sheetOut).durationMillis.toLong())
            // The sheet's own exit ends on this frame: one more, so its last step is drawn.
            withFrameMillis { }
        }
        left = true
    }
    if (value == null && left) return
    sheet(shown, value != null)
}

/** A sheet sliding away: no touch reaches what it shows, and TalkBack no longer reads it. */
private fun Modifier.leaving(): Modifier =
    clearAndSetSemantics { }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }

/** Where the sheet's scrolling content is cut off at the top: below the 4 dp bites and the handle's tape (to ~10 dp). */
private val SCROLL_TOP = 16.dp

/** The fade above the sticky footer while more lies below it (§4.16), and the softer one under the perforated top. */
private val FOOTER_FADE = 24.dp
private val TOP_FADE = 12.dp

/**
 * Fades the scrolling content itself out at the viewport's edges (an alpha mask, not a pad-coloured wash), so cards
 * thin out onto the paper and its rules instead of being tinted: over [top] while something lies above, over [bottom]
 * while something lies below. Each fade is only as deep as what is left to scroll that way (up to its height), so it
 * eases off as she reaches the end instead of vanishing at the last pixel; at rest at either end that edge is drawn as
 * it is (and with neither, without a layer).
 */
private fun Modifier.scrollEdgeFade(scroll: ScrollState, top: Dp, bottom: Dp): Modifier =
    graphicsLayer {
        val fading = (top > 0.dp && scroll.value > 0) || (bottom > 0.dp && scroll.value < scroll.maxValue)
        compositingStrategy = if (fading) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        if (top > 0.dp && scroll.value > 0) {
            val h = top.toPx()
            val depth = (scroll.value / h).coerceIn(0f, 1f)
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - depth), Color.Black), startY = 0f, endY = h), Offset.Zero, Size(size.width, h), blendMode = BlendMode.DstIn)
        }
        if (bottom > 0.dp && scroll.value < scroll.maxValue) {
            val h = bottom.toPx()
            val y = size.height - h
            val depth = ((scroll.maxValue - scroll.value) / h).coerceIn(0f, 1f)
            drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - depth)), startY = y, endY = size.height), Offset(0f, y), Size(size.width, h), blendMode = BlendMode.DstIn)
        }
    }

/** The sheet's handle (§4.16): a pink-gingham washi strip 60 × 16 dp rotated −3°, straddling the top edge. Decorative. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    WashiTape(WashiPattern.PinkGingham, modifier, length = 60.dp, height = 16.dp, rotation = -3f, alpha = 0.9f)
}

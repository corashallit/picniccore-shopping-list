package earth.cora.picnic.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
        // the sheet (a scrim that said nothing was a nameless button over the whole screen).
        val closeLabel = stringResource(R.string.a11y_close_sheet)
        val dismiss by rememberUpdatedState(onDismissRequest)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
                .background(c.scrim)
                .pointerInput(Unit) { detectTapGestures { dismiss() } }
                .clearAndSetSemantics {
                    contentDescription = closeLabel
                    traversalIndex = 1f
                    onClick(label = closeLabel) {
                        dismiss()
                        true
                    }
                },
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
                .testTag(LETTERSET_TAG),
        ) {
            LetterSetSurface(Modifier.padding(top = 8.dp), footer = footer, handleScale = if (handleShown) handle else 0f, content = content)
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
    content: @Composable ColumnScope.() -> Unit,
) {
    // A floating layer: it re-roots on the binder's colours, then lays its own paper (§2.4.1).
    OnPaper(null) { LetterSetPaper(modifier, footer, handleScale, content) }
}

@Composable
private fun LetterSetPaper(
    modifier: Modifier,
    footer: (@Composable () -> Unit)?,
    handleScale: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
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

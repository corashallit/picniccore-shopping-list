package earth.cora.picnic.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
 * fades under reduced motion. [footer] is sticky with a white fade above it. Back and the scrim close it.
 */
@Composable
fun LetterSetSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
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
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxH = maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
                .background(c.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest)
                .clearAndSetSemantics { },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxH * 0.92f)
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
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 34.dp, end = 18.dp, top = 26.dp, bottom = if (footer == null) 24.dp else 8.dp),
                content = content,
            )
            if (footer != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // The white fade above the sticky footer.
                            drawRect(Brush.verticalGradient(listOf(c.pad.copy(alpha = 0f), c.pad), startY = -24.dp.toPx(), endY = 0f), Offset(0f, -24.dp.toPx()), Size(size.width, 24.dp.toPx()))
                            drawRect(c.pad)
                        }
                        .padding(start = 22.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { footer() }
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

/** The sheet's handle (§4.16): a pink-gingham washi strip 60 × 16 dp rotated −3°, straddling the top edge. Decorative. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    WashiTape(WashiPattern.PinkGingham, modifier, length = 60.dp, height = 16.dp, rotation = -3f, alpha = 0.9f)
}

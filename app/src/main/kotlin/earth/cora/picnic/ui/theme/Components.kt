package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.toSize
import earth.cora.picnic.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * THEME COMPONENT KIT (docs/DESIGN_STICKER.md §4; ARCHITECTURE §11.2): kiss-cut cards, the candy-pill and secondary
 * buttons with exactly one gloss crescent and one dot (rule R3), round sticker buttons, the FAB, chips, ruled text
 * fields, the switch and the keep switch. Every shadow is hard (§2.10); every white on a button face is accounted for
 * (ButtonGlossTest). Screens never hand-roll one.
 */

/** The coloured face of every button (inside its white border): ButtonGlossTest measures the gloss against it. */
const val FACE_TAG: String = "picnic_button_face"

/** The FAB plus's half-length as a fraction of the face (its crimson-stroked bars stay ≥4 dp clear of the gloss). */
const val FAB_PLUS_ARM: Float = 0.2f

/** The FAB plus's bar width (px) for a face [d] px across, crimson stroke included. */
fun fabPlusWidth(d: Float, density: Float): Float = d * 0.1f + 8f * density

/** A pressed face's offset onto its shadow (ButtonTest reads it). */
val FaceOffset: SemanticsPropertyKey<DpOffset> = SemanticsPropertyKey("FaceOffset")
internal var SemanticsPropertyReceiver.faceOffset: DpOffset by FaceOffset

/* ------------------------------------------------------------------------------------------------
 * Surfaces.
 * ------------------------------------------------------------------------------------------------ */

/**
 * A kiss-cut card (§2.8: 10 dp): [color] fill (the pad by default), a 3 dp white kiss-cut border when [outlined] (left
 * off on a navy Glow page, where the shadow carries the edge), and a hard shadow (hs; [large] = hs2). The shadow is
 * drawn outside the bounds (below / right): leave room for it.
 */
@Composable
fun PicnicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PicnicTheme.shapes.card,
    color: Color = PicnicTheme.colors.pad,
    outlined: Boolean = true,
    shadow: Boolean = true,
    large: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = PicnicTheme.colors
    val border = outlined && !(c.isDark && color == c.pad)
    Box(
        modifier
            .then(if (shadow) Modifier.hardShadow(shape, PicnicTheme.shadows, large) else Modifier)
            .clip(shape)
            .background(color)
            .then(if (border) Modifier.border(PicnicTheme.dimens.border, c.stickerWhite, shape) else Modifier),
        content = content,
    )
}

/**
 * The pad patch (§4.1): information text on the binder never sits straight on the pinstripes. A white kiss-cut label
 * (Glow: the page navy), radius 9, 3 × 8 dp padding, 2 dp white border, hs.
 */
@Composable
fun PadPatch(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = PicnicTheme.colors
    val shape = PicnicTheme.shapes.patch
    Box(
        modifier
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(2.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
            .background(c.patch)
            .padding(horizontal = 6.dp),
        content = content,
    )
}

/* ------------------------------------------------------------------------------------------------
 * Gloss (rule R3): exactly one crescent and one dot per face.
 * ------------------------------------------------------------------------------------------------ */

/** What a face is, for the gloss geometry. */
enum class FaceKind { Pill, Circle }

/**
 * The gloss on one face (px, in the face's own coordinates): a tapered crescent [crescent] (polygon [outline] for the
 * clearance checks) and one dot. [mirrored] = moved to the right cap because the label starts at the left edge.
 */
@Immutable
class GlossGeometry(
    val crescent: Path,
    val outline: List<Offset>,
    val dotCenter: Offset,
    val dotRadius: Float,
    val mirrored: Boolean,
) {
    /** Bounds of the crescent and the dot together. */
    val bounds: Rect
        get() {
            val xs = outline.map { it.x } + listOf(dotCenter.x - dotRadius, dotCenter.x + dotRadius)
            val ys = outline.map { it.y } + listOf(dotCenter.y - dotRadius, dotCenter.y + dotRadius)
            return Rect(xs.min(), ys.min(), xs.max(), ys.max())
        }

    /** The smallest distance (px) from any gloss ink to [rect] (0 when they touch or overlap). */
    fun distanceTo(rect: Rect): Float {
        fun d(p: Offset): Float {
            val dx = max(max(rect.left - p.x, 0f), p.x - rect.right)
            val dy = max(max(rect.top - p.y, 0f), p.y - rect.bottom)
            return hypot(dx, dy)
        }
        val crescent = outline.minOf { d(it) }
        val dot = max(0f, d(dotCenter) - dotRadius)
        return min(crescent, dot)
    }
}

/** The pill dot's bottom and right edges on a 52 dp face ((28.8, 6.8) dp, radius 1.8 dp; scaled with the face). */
private val GLOSS_DOT_BOTTOM = 8.6.dp
private val GLOSS_DOT_RIGHT = 30.6.dp

/** The R3 gloss geometry (ButtonGlossTest). */
object ButtonGloss {
    /** Gloss never comes within this of the label's (or glyph's) bounds. */
    val CLEARANCE: Dp = 4.dp

    /**
     * The gloss for a [face] (px) of [kind]. Pills: a crescent hugging the inside of the LEFT cap (on a 52 dp pill, the
     * top edge of a 36 dp ring at (5, 5), 3.5 dp thick, rotated −45°: an arc from the cap's left to its top) and a 3.6
     * dp dot at (27, 5); scaled with the face height. If [label] starts at the left edge (closer than 4 dp to the
     * crescent or the dot), it all moves to the right cap. Circles: a concentric crescent at the upper left and a dot
     * just past its end.
     */
    fun of(kind: FaceKind, face: Size, label: Rect?, density: Density): GlossGeometry {
        val clearance = with(density) { CLEARANCE.toPx() }
        return when (kind) {
            FaceKind.Pill -> {
                val k = face.height / with(density) { 52.dp.toPx() }
                val dp = with(density) { 1.dp.toPx() } * k
                val center = Offset(23f * dp, 23f * dp)
                val left = arcCrescent(center, outer = 18f * dp, thickness = 3.5f * dp, from = 180f, to = 270f)
                val dot = Offset(28.8f * dp, 6.8f * dp)
                val g = GlossGeometry(left.first, left.second, dot, 1.8f * dp, mirrored = false)
                if (label != null && g.distanceTo(label) < clearance) mirror(g, face.width) else g
            }
            FaceKind.Circle -> {
                val d = face.minDimension
                val c = Offset(face.width / 2f, face.height / 2f)
                val crescent = arcCrescent(c, outer = 0.46f * d, thickness = 0.06f * d, from = 195f, to = 255f)
                // The dot sits just past the crescent's top end, clear of a glyph's top ink.
                val a = Math.toRadians(262.0)
                val r = 0.43f * d
                GlossGeometry(crescent.first, crescent.second, Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()), 0.035f * d, mirrored = false)
            }
        }
    }

    private fun mirror(g: GlossGeometry, width: Float): GlossGeometry {
        val pts = g.outline.map { Offset(width - it.x, it.y) }
        return GlossGeometry(polygon(pts), pts, Offset(width - g.dotCenter.x, g.dotCenter.y), g.dotRadius, mirrored = true)
    }

    /** A crescent on a circle of radius [outer] about [center], from [from] to [to] degrees, tapering to points. */
    private fun arcCrescent(center: Offset, outer: Float, thickness: Float, from: Float, to: Float): Pair<Path, List<Offset>> {
        val n = 24
        val outerPts = (0..n).map { i ->
            val a = Math.toRadians((from + (to - from) * i / n).toDouble())
            Offset(center.x + (outer * cos(a)).toFloat(), center.y + (outer * sin(a)).toFloat())
        }
        val innerPts = (n downTo 0).map { i ->
            val s = i.toFloat() / n
            val r = outer - thickness * sin(PI * s).toFloat()
            val a = Math.toRadians((from + (to - from) * s).toDouble())
            Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
        }
        val pts = outerPts + innerPts
        return polygon(pts) to pts
    }

    private fun polygon(pts: List<Offset>): Path = Path().apply {
        pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }
}

/**
 * The label's inked bounds (R3's "label bounds"): the label row's full width, and vertically the band its glyphs ink —
 * the font size around the row's centre (DynaPuff's caps and descenders sit inside 1 em), not the padded line box.
 */
fun labelInkBand(row: Rect, fontPx: Float): Rect {
    val half = minOf(row.height, fontPx) / 2f
    val cy = row.center.y
    return Rect(row.left, cy - half, row.right, cy + half)
}

/** One thing drawn on a button face. [blur] is always 0: nothing is ever blurred (R3, item 63). */
@Immutable
sealed class FaceOp(val color: Color, val blur: Float = 0f) {
    class Fill(color: Color) : FaceOp(color)
    class Ring(color: Color) : FaceOp(color)
    class Crescent(color: Color) : FaceOp(color)
    class Dot(color: Color) : FaceOp(color)
    class Label(color: Color) : FaceOp(color)
    class GlyphInk(color: Color) : FaceOp(color)
    class Star(color: Color) : FaceOp(color)
}

/** Every button kind the R3 audit covers. */
enum class ButtonKind { Primary, Secondary, Round, AddBarSnap, AddBarSpeak, AddBarSend, Fab }

/**
 * What a face draws, in order (ButtonGlossTest.allowedWhiteOnly / opaqueWhite run on these; the buttons draw exactly
 * these). The only white on a face: one crescent, one dot, and the face's own label or glyph (the primary's white
 * label, the FAB's white plus).
 */
fun faceOps(kind: ButtonKind, enabled: Boolean, colors: PicnicColors, leadingStar: Boolean = false): List<FaceOp> {
    val gloss = enabled
    val ops = mutableListOf<FaceOp>()
    when (kind) {
        ButtonKind.Primary -> {
            ops += FaceOp.Fill(if (enabled) Families.strawberry.mid else colors.backing)
            if (leadingStar && enabled) ops += FaceOp.Star(Families.lemon.pastel)
            ops += FaceOp.Label(if (enabled) Color.White else colors.ballpointSoft)
        }
        ButtonKind.Secondary -> {
            ops += FaceOp.Fill(if (enabled) Color.White else colors.backing)
            if (enabled) ops += FaceOp.Ring(Families.periwinkle.trace)
            ops += FaceOp.Label(if (enabled) colors.ballpoint else colors.ballpointSoft)
        }
        // Disabled round faces take the pills' disabled tokens (§2.1 `backing`, §4.6): a flat backing disc with a
        // ballpointSoft glyph, no gloss and no shadow, never a 50 % ghost of the live face (muddy on the Glow navy).
        ButtonKind.Round -> {
            ops += FaceOp.Fill(if (enabled) colors.pad else colors.backing)
            ops += FaceOp.GlyphInk(if (enabled) colors.ink else colors.ballpointSoft)
        }
        ButtonKind.AddBarSnap, ButtonKind.AddBarSpeak, ButtonKind.AddBarSend -> {
            val family = when (kind) {
                ButtonKind.AddBarSnap -> Families.ramune
                ButtonKind.AddBarSpeak -> Families.sakura
                else -> Families.strawberry
            }
            ops += FaceOp.Fill(if (enabled) family.pastel else colors.backing)
            ops += FaceOp.GlyphInk(if (enabled) colors.ballpoint else colors.ballpointSoft)
        }
        ButtonKind.Fab -> {
            ops += FaceOp.Fill(Families.strawberry.mid)
            ops += FaceOp.GlyphInk(Color.White)
        }
    }
    if (gloss) {
        // The gloss sits under the label (drawn after the fill, before the label).
        ops.add(1, FaceOp.Crescent(Color.White))
        ops.add(2, FaceOp.Dot(Color.White))
    }
    return ops
}

/** Draws the R3 gloss: one opaque white crescent and one opaque white dot, never blurred. */
internal fun DrawScope.drawGloss(g: GlossGeometry) {
    drawPath(g.crescent, Color.White)
    drawCircle(Color.White, g.dotRadius, g.dotCenter)
}

/* ------------------------------------------------------------------------------------------------
 * Press steps and focus rings.
 * ------------------------------------------------------------------------------------------------ */

/**
 * The press fraction 0 → 1 in 2 steps of 80 ms (§4.6), on the frame clock under the fixed motion scale; back in 2 steps
 * on release. Instant under reduced motion.
 */
@Composable
internal fun rememberPressFraction(pressed: Boolean): Float {
    val reduced = LocalReducedMotion.current
    var fraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pressed) {
        val target = if (pressed) 1f else 0f
        if (reduced) {
            fraction = target
            return@LaunchedEffect
        }
        withContext(FixedMotionScale) {
            val step = PicnicMotion.buttonPress.durationMillis / 2L
            if (fraction != target) {
                fraction = 0.5f
                delay(step)
                fraction = target
            }
        }
    }
    return fraction
}

/** A 2 dp ballpoint focus ring 3 dp outside [shape] (keyboard / switch access): flat, never a glow. */
internal fun Modifier.focusRing(shape: Shape, color: Color, visible: Boolean): Modifier = if (!visible) this else drawBehind {
    val gap = 3.dp.toPx()
    val w = 2.dp.toPx()
    val grow = gap + w / 2f
    val outline = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
    translate(-grow, -grow) { drawOutline(outline, color, style = Stroke(w)) }
}

/* ------------------------------------------------------------------------------------------------
 * Buttons (§4.6).
 * ------------------------------------------------------------------------------------------------ */

enum class PicnicButtonStyle { Primary, Secondary, Text }

/**
 * A button (§4.6, rule R3), ≥48 dp tall. The pill heights are minimums: a face grows with a label too tall for it
 * (font scales past 2.0) instead of clipping it.
 * - Primary "candy pill": 52 dp, strawberry mid, 3 dp white border, hs2; DynaPuff 18 white label on a 4 dp #C3264F
 *   stroke; optional flat lemon star ([leadingStar]). Pressed: the shadow goes and the face drops (+2.4, +3.4) in 2
 *   steps of 80 ms. Disabled: backing fill, ballpointSoft label, no stroke, shadow or gloss; [busy] leads with a
 *   4-point star spinner.
 * - Secondary: 48 dp white pill, 3 dp white border, an inset 2 dp periwinkle-trace outline, hs; DynaPuff 16 ballpoint,
 *   optional 18 dp [glyph]. Pressed (+1.6, +2.2), flat.
 * - Text: no face; Zen Maru 700 15 sp with a wavy 2 dp sakura underline; pressed = a highlighter band behind the label.
 * Focus: a 2 dp ballpoint ring 3 dp outside.
 */
@Composable
fun PicnicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PicnicButtonStyle = PicnicButtonStyle.Primary,
    enabled: Boolean = true,
    glyph: PicnicGlyph? = null,
    leadingStar: Boolean = false,
    busy: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
) {
    when (style) {
        PicnicButtonStyle.Primary, PicnicButtonStyle.Secondary ->
            PillButton(text, onClick, modifier, style == PicnicButtonStyle.Primary, enabled, glyph, leadingStar, busy, interactionSource)
        PicnicButtonStyle.Text -> TextButton(text, onClick, modifier, enabled, glyph, interactionSource)
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    primary: Boolean,
    enabled: Boolean,
    glyph: PicnicGlyph?,
    leadingStar: Boolean,
    busy: Boolean,
    interactionSource: MutableInteractionSource?,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shadows = PicnicTheme.shadows
    val density = LocalDensity.current
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val press = rememberPressFraction(pressed && enabled)
    val shadowOffset = if (primary) shadows.hs2 else shadows.hs
    val faceOffset = DpOffset(shadowOffset.x * press, shadowOffset.y * press)
    val height = if (primary) 52.dp else 48.dp
    val shape = PicnicTheme.shapes.pill
    val kind = if (primary) ButtonKind.Primary else ButtonKind.Secondary
    val ops = faceOps(kind, enabled, c, leadingStar)
    // The label's bounds in the face's own coordinates (the gloss keeps 4 dp clear of them, R3).
    var faceCoords by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var labelCoords by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val labelFontPx = with(density) { (if (primary) t.primaryButton else t.secondaryButton).fontSize.toPx() }
    fun labelBox(): Rect? {
        val f = faceCoords
        val l = labelCoords
        if (f == null || l == null || !f.isAttached || !l.isAttached) return null
        return labelInkBand(f.localBoundingBoxOf(l, clipBounds = false), labelFontPx)
    }
    val border = PicnicTheme.dimens.border
    // R3 at large text: once the label's ink band reaches the dot's height (font scale ≳ 1.3), a label starting at the
    // usual 27 / 21 dp would sit within 4 dp of the dot, and the mirrored gloss would meet its end. The label then starts
    // past the dot instead, so the gloss keeps its place in the left cap and its 4 dp clearance (ButtonGlossTest).
    // The face is [height] tall at least and grows with a label too tall for it (font scales past 2.0): the inner face
    // is the larger of the nominal one and the label's box (its line, plus the primary's 4 dp stroke).
    val labelStyle = if (primary) t.primaryButton else t.secondaryButton
    val faceInner = maxOf(
        height - border * 2,
        with(density) { labelStyle.lineHeight.toDp() } + if (primary && enabled) 4.dp else 0.dp,
    )
    val labelStart = run {
        val face = faceInner
        val k = face / 52.dp
        val font = with(density) { labelStyle.fontSize.toDp() }
        val inkTop = face / 2 - font / 2
        val base = if (primary) 27.dp else 21.dp
        if (inkTop < GLOSS_DOT_BOTTOM * k + ButtonGloss.CLEARANCE) maxOf(base, GLOSS_DOT_RIGHT * k + ButtonGloss.CLEARANCE + 1.dp) else base
    }
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics { this.faceOffset = faceOffset }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = height)
                .focusRing(shape, c.ballpoint, focused)
                .then(if (enabled) Modifier.hardShadow(shape, shadows.color, shadowOffset) else Modifier)
                .offset(faceOffset.x, faceOffset.y)
                .clip(shape)
                .background(if (enabled) c.stickerWhite else c.backing)
                .padding(border)
                .clip(shape)
                .testTag(FACE_TAG)
                .onPlaced { faceCoords = it }
                .drawBehind {
                    for (op in ops) when (op) {
                        is FaceOp.Fill -> drawRect(op.color)
                        is FaceOp.Ring -> {
                            val inset = 2.dp.toPx()
                            val w = 2.dp.toPx()
                            drawRoundRect(
                                op.color, Offset(inset + w / 2, inset + w / 2), Size(size.width - inset * 2 - w, size.height - inset * 2 - w),
                                CornerRadius((size.height - inset * 2 - w) / 2f), style = Stroke(w),
                            )
                        }
                        is FaceOp.Crescent -> drawPath(ButtonGloss.of(FaceKind.Pill, size, labelBox(), this).crescent, op.color)
                        is FaceOp.Dot -> ButtonGloss.of(FaceKind.Pill, size, labelBox(), this).let { drawCircle(op.color, it.dotRadius, it.dotCenter) }
                        else -> Unit
                    }
                }
                .padding(start = labelStart, end = if (primary) 21.dp else 19.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.onPlaced { labelCoords = it },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    busy && !enabled -> StarSpinner(Modifier.size(18.dp), c.ballpointSoft)
                    leadingStar && primary -> FlatStar(Modifier.size(18.dp))
                    glyph != null -> Glyph(glyph, null, tint = if (enabled) (if (primary) Color.White else c.ballpoint) else c.ballpointSoft, size = 18.dp)
                }
                val labelColor = ops.filterIsInstance<FaceOp.Label>().first().color
                if (primary && enabled) {
                    PuffyText(
                        text, t.primaryButton, fill = labelColor, stroke = 4.dp,
                        strokeColor = Families.strawberry.trace, shadow = null, minFontSize = 15.sp,
                    )
                } else {
                    Text(
                        text,
                        style = if (primary) t.primaryButton else t.secondaryButton,
                        color = labelColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}


@Composable
private fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    glyph: PicnicGlyph?,
    interactionSource: MutableInteractionSource?,
) {
    val c = PicnicTheme.colors
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .focusRing(shape, c.ink, focused)
                .drawBehind {
                    if (pressed) {
                        drawRoundRect(c.pressedBand, Offset(-4.dp.toPx(), -2.dp.toPx()), Size(size.width + 6.dp.toPx(), size.height + 4.dp.toPx()), CornerRadius(4.dp.toPx()))
                    }
                }
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (glyph != null) Glyph(glyph, null, tint = c.ink, size = 18.dp)
            Text(
                text,
                style = PicnicTheme.type.textButton.copy(color = c.ink),
                modifier = Modifier.wavyUnderline(Families.sakura.mid),
            )
        }
    }
}

/**
 * The hand-drawn wavy underline (§4.6): 2 dp in [color], a 12 × 5 dp wave tile, about 13 dp under the baseline for a
 * 15 sp label (drawn just below the text box).
 */
fun Modifier.wavyUnderline(color: Color): Modifier = drawWithContent {
    drawContent()
    val tile = 12.dp.toPx()
    val amp = 2.5.dp.toPx() / 2f
    val y = size.height + 2.5.dp.toPx()
    val p = Path().apply {
        moveTo(0f, y)
        var x = 0f
        var up = true
        while (x < size.width) {
            val nx = min(x + tile / 2f, size.width)
            quadraticTo((x + nx) / 2f, if (up) y - amp * 2 else y + amp * 2, nx, y)
            x = nx
            up = !up
        }
    }
    drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The primary pill's flat lemon star (part of the label: no die-cut border, no gloss). */
@Composable
fun FlatStar(modifier: Modifier = Modifier, fill: Color = Families.lemon.pastel, trace: Color = Families.lemon.trace) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val c = Offset(size.width / 2f, size.height / 2f + size.height * 0.04f)
        val star = starPath(c, size.minDimension * 0.48f, size.minDimension * 0.24f)
        drawPath(star, fill)
        drawPath(star, trace, style = Stroke(size.minDimension * 0.07f, join = StrokeJoin.Round))
    }
}

/** The disabled pill's 4-point star spinner (8 steps per 2 s; still under reduced motion). */
@Composable
fun StarSpinner(modifier: Modifier = Modifier, color: Color = PicnicTheme.colors.ballpointSoft) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val step = ((t % 2000L) / 250L).toInt()
    Canvas(modifier.clearAndSetSemantics { }.rotate(step * 45f)) {
        drawPath(sparklePath(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, 0.3f), color)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Round sticker buttons, add-bar buttons and the FAB (§4.6).
 * ------------------------------------------------------------------------------------------------ */

/**
 * A 40 dp round sticker button in a 48 dp target (More, Back): a pad circle, 3 dp white border, hs, a 24 dp line
 * [glyph] (never sticker art, which would bring a second gloss), one gloss crescent and dot. Pressed: flat onto its
 * shadow. Disabled: the pills' disabled tokens (a flat `backing` disc, ballpointSoft glyph, no gloss or shadow).
 * [emphasized] is the v1 "primary-filled" flag and maps to the send button's look.
 */
@Composable
fun PicnicIconButton(
    glyph: PicnicGlyph,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
) {
    RoundStickerButton(
        glyph = glyph,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        kind = if (emphasized) ButtonKind.AddBarSend else ButtonKind.Round,
        faceSize = 40.dp,
        interactionSource = interactionSource,
    )
}

/** The three add-bar buttons (§4.6): 52 dp faces in their family pastel with a line glyph. */
enum class AddBarButtonKind(val kind: ButtonKind, val glyph: PicnicGlyph) {
    Snap(ButtonKind.AddBarSnap, PicnicGlyph.Polaroid),
    Speak(ButtonKind.AddBarSpeak, PicnicGlyph.Cassette),
    Send(ButtonKind.AddBarSend, PicnicGlyph.Envelope),
}

/** An add-bar button (§4.6): camera ramune, cassette sakura, send strawberry; line glyphs only. */
@Composable
fun AddBarButton(
    kind: AddBarButtonKind,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    RoundStickerButton(kind.glyph, contentDescription, onClick, modifier, enabled, kind.kind, faceSize = 52.dp, interactionSource = interactionSource)
}

/**
 * A round sticker face of [kind] and [faceSize] (≥48 dp target), with the R3 gloss. Disabled: a flat `backing` disc
 * (its border too), the glyph in ballpointSoft, no gloss and no shadow, as the disabled pills (§4.6).
 */
@Composable
fun RoundStickerButton(
    glyph: PicnicGlyph,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: ButtonKind = ButtonKind.Round,
    faceSize: Dp = 40.dp,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val press = rememberPressFraction(pressed && enabled)
    val faceOffset = DpOffset(shadows.hs.x * press, shadows.hs.y * press)
    val ops = faceOps(kind, enabled = enabled, colors = c)
    val target = max(48f, faceSize.value + 8f).dp
    Box(
        modifier
            .size(target)
            .semantics { this.faceOffset = faceOffset }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(faceSize)
                .focusRing(CircleShape, c.ink, focused)
                .then(if (enabled) Modifier.hardShadow(CircleShape, shadows.color, shadows.hs) else Modifier)
                .offset(faceOffset.x, faceOffset.y)
                .clip(CircleShape)
                .background(if (enabled) c.stickerWhite else c.backing)
                .padding(PicnicTheme.dimens.border)
                .clip(CircleShape)
                .testTag(FACE_TAG)
                .drawBehind {
                    for (op in ops) when (op) {
                        is FaceOp.Fill -> drawRect(op.color)
                        is FaceOp.Crescent -> drawPath(ButtonGloss.of(FaceKind.Circle, size, null, this).crescent, op.color)
                        is FaceOp.Dot -> ButtonGloss.of(FaceKind.Circle, size, null, this).let { drawCircle(op.color, it.dotRadius, it.dotCenter) }
                        else -> Unit
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(glyph, contentDescription = null, tint = ops.filterIsInstance<FaceOp.GlyphInk>().first().color, size = 24.dp)
        }
    }
}

/**
 * The FAB (§4.6): a 64 dp strawberry-mid circle sticker, white border, hs2, a white plus over a 4 dp #C3264F stroke, one
 * gloss crescent and dot; a 22 dp lemon star BADGE sticker centred on the 45° point of the white border's outer edge,
 * outside the face. Bobs −4 dp on a 2.4 s ease-in-out loop while visible (still under reduced motion). Pressed: flat.
 */
@Composable
fun PicnicFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bob: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    val reduced = LocalReducedMotion.current
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPressFraction(pressed)
    val faceOffset = DpOffset(shadows.hs2.x * press, shadows.hs2.y * press)
    val t by rememberLoopTime(enabled = bob && !reduced)
    val phase = (t % PicnicMotion.fabBob.durationMillis).toFloat() / PicnicMotion.fabBob.durationMillis
    val bobDy = if (bob && !reduced) (-4f * PicnicMotion.fabBob.easing.transform(if (phase < 0.5f) phase * 2 else (1 - phase) * 2)) else 0f
    val ops = faceOps(ButtonKind.Fab, enabled = true, colors = c)
    val face = 64.dp
    val border = PicnicTheme.dimens.border
    Box(
        modifier
            .size(face + border * 2 + 16.dp)
            .offset(y = bobDy.dp)
            .semantics { this.faceOffset = faceOffset }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(face + border * 2)
                .hardShadow(CircleShape, shadows.color, shadows.hs2)
                .offset(faceOffset.x, faceOffset.y)
                .clip(CircleShape)
                .background(c.stickerWhite)
                .padding(border)
                .clip(CircleShape)
                .testTag(FACE_TAG)
                .drawBehind {
                    for (op in ops) when (op) {
                        is FaceOp.Fill -> drawRect(op.color)
                        is FaceOp.Crescent -> drawPath(ButtonGloss.of(FaceKind.Circle, size, null, this).crescent, op.color)
                        is FaceOp.Dot -> ButtonGloss.of(FaceKind.Circle, size, null, this).let { drawCircle(op.color, it.dotRadius, it.dotCenter) }
                        is FaceOp.GlyphInk -> {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val arm = size.minDimension * FAB_PLUS_ARM
                            val plus = Path().apply {
                                moveTo(cx - arm, cy); lineTo(cx + arm, cy)
                                moveTo(cx, cy - arm); lineTo(cx, cy + arm)
                            }
                            drawPath(plus, Families.strawberry.trace, style = Stroke(fabPlusWidth(size.minDimension, density), cap = StrokeCap.Round))
                            drawPath(plus, op.color, style = Stroke(size.minDimension * 0.1f, cap = StrokeCap.Round))
                        }
                        else -> Unit
                    }
                },
        )
        // The badge: centred on the 45° point of the white border's outer edge, outside the face outline.
        val r = face / 2 + border
        val d = r * 0.7071f
        DecorSticker(
            "star",
            Modifier
                .offset(x = d + faceOffset.x, y = -d + faceOffset.y),
            size = 22.dp,
        )
    }
}

/* ------------------------------------------------------------------------------------------------
 * Chips (§4.7).
 * ------------------------------------------------------------------------------------------------ */

/**
 * A kiss-cut label chip (§4.7): 32 dp face in a 48 dp target, [family] pastel fill, 3 dp white border, hs, Zen Maru 700
 * 13.5 sp ballpoint. Selected: the family's mid fill and a leading 13 dp puffy star (unless [leading] is given).
 */
@Composable
fun PicnicChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    family: StickerFamily = Families.ramune,
) {
    val c = PicnicTheme.colors
    val on = stringResource(R.string.theme_switch_on)
    val off = stringResource(R.string.theme_switch_off)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .semantics { stateDescription = if (selected) on else off }
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChipFace(fill = if (selected) family.mid else family.pastel) {
            when {
                leading != null -> leading()
                selected -> DecorSticker("star", size = 15.dp, shadow = false)
            }
            Text(text, style = PicnicTheme.type.label.copy(color = c.ballpoint), maxLines = 1)
        }
    }
}

/**
 * A chip face: a 32 dp kiss-cut pill (3 dp white border, hs) with [fill]. Decorative container. [height] is a MINIMUM:
 * at large font scales the face grows with its label (1 dp above and below it, inside the border) instead of clipping
 * it (§15, font scale 2.0). A caller that sizes the face to its label's line itself ([StoreAisleChip]) passes
 * [grows] false: the face is then exactly [height] tall.
 */
@Composable
fun ChipFace(
    fill: Color,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    border: Dp = PicnicTheme.dimens.border,
    horizontal: Dp = 11.dp,
    grows: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val shape = PicnicTheme.shapes.pill
    Row(
        modifier
            .then(if (grows) Modifier.heightIn(min = height) else Modifier.height(height))
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(PicnicTheme.colors.stickerWhite)
            .padding(border)
            .clip(shape)
            .background(fill)
            .padding(horizontal = horizontal, vertical = if (grows) CHIP_LABEL_ROOM else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        content = content,
    )
}

/** The room a grown chip keeps above and below its label, inside the border. */
private val CHIP_LABEL_ROOM = 1.dp

/** The aisle number circle's diameter (px) for a numeral laid out [textW] × [textH] px: [min] or what holds it. */
internal fun numberCircleDiameter(min: Int, textW: Int, textH: Int, ringPx: Int): Int = max(min, max(textW, textH) + ringPx * 2)

/**
 * The aisle number circle (§2.7, §4.10): white, a 2 dp ring in the aisle trace, DynaPuff numeral in the trace; the
 * capsule glyph for "Everything else" ([number] null). Decorative (the label repeats the number). [size] is a MINIMUM:
 * the numeral scales with the font (sp), and at large font scales the circle grows until its ring clears the numeral's
 * line box, so a numeral is never cut by the circle (§15, font scale 2.0). The numeral is drawn over the circle, never
 * clipped by it.
 */
@Composable
fun NumberCircle(number: Int?, look: AisleLook, modifier: Modifier = Modifier, size: Dp = 24.dp, ring: Dp = 2.dp) {
    val style = if (size <= 20.dp) PicnicTheme.type.chipNumber else PicnicTheme.type.tabNumber
    androidx.compose.ui.layout.Layout(
        content = {
            Box(Modifier.clip(CircleShape).background(Color.White).border(ring, look.family.trace, CircleShape))
            if (number != null) {
                Text(number.toString(), style = style.copy(color = look.family.trace), maxLines = 1, softWrap = false)
            } else {
                CapsuleMark(look.family, Modifier.size(size * 0.6f))
            }
        },
        modifier = modifier.clearAndSetSemantics { },
    ) { measurables, constraints ->
        val mark = measurables[1].measure(androidx.compose.ui.unit.Constraints())
        val d = numberCircleDiameter(size.roundToPx(), mark.width, mark.height, ring.roundToPx())
        val w = constraints.constrainWidth(d)
        val h = constraints.constrainHeight(d)
        val circle = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
        layout(w, h) {
            circle.place(0, 0)
            mark.place(Alignment.Center.align(IntSize(mark.width, mark.height), IntSize(w, h), layoutDirection))
        }
    }
}

/** A tiny gashapon capsule mark (the "Everything else" number circle). */
@Composable
fun CapsuleMark(family: StickerFamily, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawArc(family.mid, 180f, 180f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
        drawArc(Color.White, 0f, 180f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
        drawCircle(family.trace, r - 0.6.dp.toPx(), c, style = Stroke(1.2.dp.toPx()))
        drawLine(family.trace, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.2.dp.toPx())
    }
}

/** An aisle chip (§4.7): a kiss-cut label in the aisle pastel led by a 20 dp number circle (DynaPuff 11 sp). */
@Composable
fun AisleChip(
    number: Int?,
    name: String,
    look: AisleLook,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PicnicChip(
        text = name,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        family = look.family,
        leading = { NumberCircle(number, look, size = 20.dp) },
    )
}

/**
 * The store's own aisle number chip "A2" (§4.7): a 22 dp lemon-pastel pill (taller when a large font needs it), 2 dp
 * white border, hs, a basket glyph; TalkBack "store aisle 2".
 */
@Composable
fun StoreAisleChip(number: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.theme_store_aisle, number)
    val style = PicnicTheme.type.label.copy(color = PicnicTheme.colors.ballpoint, fontSize = 13.sp, lineHeight = 15.sp)
    ChipFace(
        fill = Families.lemon.pastel,
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        height = labelChipHeight(22.dp, "A$number", style, border = 2.dp),
        border = 2.dp,
        horizontal = 7.dp,
        grows = false,
    ) {
        Glyph(PicnicGlyph.Basket, null, tint = PicnicTheme.colors.ballpoint, size = 13.dp)
        Text("A$number", style = style, maxLines = 1, softWrap = false, modifier = Modifier.shrinkToFitWidth())
    }
}

/**
 * A small chip's height for its one-line [label] in [style]: [min] at font scale 1.0, and under a larger font [min]
 * scaled as the label's text box grew from 1.0, so the face keeps its drawn proportions and the label its place in it
 * (never sinking onto the border). It is always at least that text box (Zen Maru's and DynaPuff's ascent and descent,
 * taller than these chips' 15 sp line) inside [border], so the box is never cut; at 1.0 the box may overhang the
 * face's inside by up to [CHIP_LEADING] of blank leading, as it always has.
 */
@Composable
internal fun labelChipHeight(min: Dp, label: String, style: TextStyle, border: Dp): Dp {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val density = LocalDensity.current
    return remember(label, style, density, measurer, min, border) {
        fun box(at: Density) = measurer.measure(label, style, maxLines = 1, softWrap = false, density = at).size.height.toFloat()
        val now = box(density)
        val atOne = box(Density(density.density, 1f))
        with(density) {
            val needed = maxOf(min.toPx() * now / atOne, now + border.toPx() * 2)
            if (needed <= (min + CHIP_LEADING).toPx()) min else needed.toDp()
        }
    }
}

/** The blank leading a small chip's label box may overhang its face by before the chip grows. */
private val CHIP_LEADING = 1.dp

/**
 * The "shiny!" / "jackpot!" chip (§4.7): DynaPuff 13 sp ballpoint on a holo pill with a 1.5 dp white border, 22 dp tall
 * at least (it grows with a large font instead of clipping). Decorative.
 */
@Composable
fun RarityChip(jackpot: Boolean, modifier: Modifier = Modifier) {
    val text = stringResource(if (jackpot) R.string.theme_chip_jackpot else R.string.theme_chip_shiny)
    val shape = PicnicTheme.shapes.pill
    val style = PicnicTheme.type.counter.copy(color = PicnicTheme.colors.ballpoint, fontSize = 13.sp, lineHeight = 15.sp)
    Box(
        modifier
            .height(labelChipHeight(22.dp, text, style, border = 1.5.dp))
            .clearAndSetSemantics { }
            .clip(shape)
            .background(Color.White)
            .padding(1.5.dp)
            .clip(shape)
            .drawBehind { drawRect(holoBrush(Offset(size.width / 2f, size.height / 2f))) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.shrinkToFitWidth())
    }
}

/**
 * Lays its content out at its natural width and, when the slot is narrower (a large font in a crowded row), draws it
 * scaled down, centred, to the slot's width: a badge's word stays whole instead of being cut ("shin" for "shiny!").
 */
internal fun Modifier.shrinkToFitWidth(): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    if (!constraints.hasBoundedWidth || p.width <= constraints.maxWidth) {
        layout(constraints.constrainWidth(p.width), constraints.constrainHeight(p.height)) {
            p.place(0, (constraints.constrainHeight(p.height) - p.height) / 2)
        }
    } else {
        val w = constraints.maxWidth
        val h = constraints.constrainHeight(p.height)
        val scale = w.toFloat() / p.width
        layout(w, h) {
            p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

/** A source chip (§4.7, web results): a kiss-cut label with a link glyph; opens the URL. */
@Composable
fun SourceChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChipFace(fill = Families.ramune.pastel) {
            Glyph(PicnicGlyph.Link, null, tint = PicnicTheme.colors.ballpoint, size = 15.dp)
            Text(text, style = PicnicTheme.type.label.copy(color = PicnicTheme.colors.ballpoint), maxLines = 1)
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Text fields (§4.8).
 * ------------------------------------------------------------------------------------------------ */

/**
 * The ruled text field (§4.8): no box. A 2 dp ballpointSoft underline, the label above (Zen Maru 700 13 sp
 * ballpointSoft), the value Zen Maru 700 16 sp ballpoint, 56 dp tall. Focus: a 3 dp strawberry-trace underline with a
 * 13 dp puffy heart at its left end. Error: a hanko underline and helper text in hanko 13 sp with a 16 dp torn-paper
 * glyph (key / network wording, never proxy wording). Disabled: a dotted underline, the value ballpointSoft.
 * Multi-line: rule lines continue under every line, one per text line (24 sp pitch, [RULED_LINE_PITCH]: the rules
 * scale with the text). [example] is the add bar's example line under the
 * placeholder. The visible label is announced by the editable node itself ("Note, pancakes, edit box").
 */
@Composable
fun PicnicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    textStyle: TextStyle? = null,
    example: String? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val valueStyle = (textStyle ?: t.fieldValue).let { if (enabled) it.copy(color = c.ink) else it.copy(color = c.inkSoft) }
    val multi = !singleLine && (minLines > 1 || maxLines > 1)
    // The rules sit one per line: their pitch IS the text's line height (24 sp), so both scale with the font together
    // and a rule never cuts through a line at a large font scale.
    val pitchSp = RULED_LINE_PITCH
    val pitch = with(LocalDensity.current) { lineHeightPx(pitchSp, valueStyle.fontSize).toDp() }
    Column(modifier) {
        if (label != null) {
            Text(label, style = t.fieldLabel.copy(color = c.inkSoft), modifier = Modifier.clearAndSetSemantics { })
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = if (multi) valueStyle.copy(lineHeight = pitchSp) else valueStyle,
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
            decorationBox = { inner ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (multi) pitch * minLines else 36.dp)
                            .then(if (multi) Modifier.ruledLines(pitch, c.rule) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f).padding(vertical = if (multi) 0.dp else 4.dp)) {
                            if (value.isEmpty() && placeholder != null) {
                                Column {
                                    Text(placeholder, style = (textStyle ?: t.body).copy(color = c.inkSoft), maxLines = if (multi) Int.MAX_VALUE else 1)
                                    if (example != null) Text(example, style = t.itemDetail.copy(color = c.inkSoft))
                                }
                            }
                            inner()
                        }
                        trailing?.invoke()
                    }
                    FieldUnderline(focused = focused && enabled, error = isError, enabled = enabled)
                }
            },
        )
        if (supportingText != null) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isError) Glyph(PicnicGlyph.TornPaper, contentDescription = null, tint = c.hanko, size = 16.dp)
                Text(
                    supportingText,
                    style = t.fieldLabel.copy(color = if (isError) c.hanko else c.inkSoft),
                )
            }
        }
    }
}

/** A multi-line ruled field's line height and rule pitch (§4.8: 24 at font scale 1.0, scaling with the font). */
val RULED_LINE_PITCH: androidx.compose.ui.unit.TextUnit = 24.sp

/**
 * The px Compose gives each line of text whose line height is [lineHeight] at [fontSize] (a ruled field's rule pitch, a
 * small chip's line). Under Android's non-linear font scaling (font scale 1.03 and up) Compose keeps the line height's
 * ratio to the font size rather than scaling the line height on its own, and each line is a whole number of px (rounded
 * up); this follows both rules, so a field's rules never drift down a long note.
 */
internal fun Density.lineHeightPx(lineHeight: androidx.compose.ui.unit.TextUnit, fontSize: androidx.compose.ui.unit.TextUnit): Float {
    val px = if (fontSize.isSp && lineHeight.isSp && fontScale >= 1.03f) fontSize.toPx() * (lineHeight.value / fontSize.value) else lineHeight.toPx()
    return kotlin.math.ceil(px)
}

/** The field's underline: rest 2 dp ballpointSoft; focus 3 dp strawberry trace + a 13 dp puffy heart; error hanko; disabled dotted. */
@Composable
private fun FieldUnderline(focused: Boolean, error: Boolean, enabled: Boolean) {
    val c = PicnicTheme.colors
    val color = when {
        error -> c.hanko
        focused -> Families.strawberry.trace
        else -> c.inkSoft
    }
    val thickness = if (focused || error) 3.dp else 2.dp
    Box(Modifier.fillMaxWidth().height(13.dp)) {
        Canvas(Modifier.fillMaxWidth().height(13.dp)) {
            val y = 3.dp.toPx() + thickness.toPx() / 2f
            drawLine(
                color, Offset(0f, y), Offset(size.width, y), thickness.toPx(),
                cap = if (enabled) StrokeCap.Round else StrokeCap.Round,
                pathEffect = if (!enabled) PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx())) else null,
            )
        }
        if (focused && !error) {
            DecorSticker("heart", Modifier.offset(x = (-4).dp, y = (-3).dp), size = 13.dp, shadow = false)
        }
    }
}

/**
 * The OpenAI key field (§4.8): masked, NEVER pre-filled (the typed [value] starts empty; a saved key shows only as the
 * [savedMask] placeholder, "sk-•••••••••• 3f9a"), an eye round button to reveal while typing and a paste round button.
 */
@Composable
fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    savedMask: String? = null,
    revealed: Boolean = false,
    onRevealChange: ((Boolean) -> Unit)? = null,
    onPaste: (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    PicnicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = savedMask,
        singleLine = true,
        isError = isError,
        supportingText = supportingText,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
        visualTransformation = if (revealed) VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation('•'),
        trailing = {
            Row {
                if (onRevealChange != null) {
                    PicnicIconButton(
                        PicnicGlyph.Eye,
                        stringResource(if (revealed) R.string.theme_key_hide else R.string.theme_key_show),
                        onClick = { onRevealChange(!revealed) },
                        enabled = enabled,
                    )
                }
                if (onPaste != null) {
                    PicnicIconButton(PicnicGlyph.Paste, stringResource(R.string.theme_key_paste), onClick = onPaste, enabled = enabled)
                }
            }
        },
    )
}

/* ------------------------------------------------------------------------------------------------
 * Switches (§4.9, §4.17).
 * ------------------------------------------------------------------------------------------------ */

/**
 * The toggle switch (§4.9): a 40 × 24 dp pill in a 48 dp target. On = melon mid with a 16 dp white knob at the right;
 * off = backing with the knob at the left and a 1.5 dp periwinkle ring; 2 dp white border, hs; the knob moves in 2
 * steps (120 ms). Disabled: 50 %. Role.Switch (the caller merges its label).
 */
@Composable
fun PicnicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    var knob by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (reduced || knob == target) {
            knob = target
        } else {
            withContext(FixedMotionScale) {
                knob = 0.5f
                delay(PicnicMotion.switchKnob.durationMillis / 2L)
                knob = target
            }
        }
    }
    val shape = PicnicTheme.shapes.pill
    val shadows = PicnicTheme.shadows
    val c = PicnicTheme.colors
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 24.dp)
                .hardShadow(shape, shadows)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(2.dp)
                .clip(shape)
                .background(if (checked) Families.melon.mid else c.backing)
                .drawBehind {
                    if (!checked) {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            Families.periwinkle.mid, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
                            CornerRadius((size.height - w) / 2), style = Stroke(w),
                        )
                    }
                    val r = 8.dp.toPx()
                    val pad = (size.height - r * 2) / 2f
                    val x = pad + r + (size.width - pad * 2 - r * 2) * knob
                    drawCircle(Color.White, r, Offset(x, size.height / 2f))
                    if (!checked) drawCircle(Families.periwinkle.mid, r, Offset(x, size.height / 2f), style = Stroke(1.dp.toPx()))
                },
        )
    }
}

/**
 * The recipe card's keep switch (§4.17): a 48 dp target on a dashed spot; kept = a 30 dp lemon star sticker pressed onto
 * the spot (the book's magic-appear poses, 320 ms; a fade under reduced motion). Role.Switch.
 */
@Composable
fun KeepSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked) {
            withContext(FixedMotionScale) {
                if (reduced) {
                    pop.snapTo(0f)
                    pop.animateTo(1f, tween(PicnicMotion.rmFade.durationMillis))
                } else {
                    // 0 → 1.3 → 0.92 → 1 in stepped poses (320 ms).
                    for (s in listOf(0.3f, 1.3f, 0.92f)) {
                        pop.snapTo(s)
                        delay(320L / 4)
                    }
                    pop.snapTo(1f)
                }
            }
        }
    }
    Box(
        modifier
            .size(48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        StickerSpot(size = 30.dp)
        if (checked) {
            DecorSticker(
                "star",
                Modifier.graphicsLayer {
                    if (reduced) alpha = pop.value else { scaleX = pop.value; scaleY = pop.value }
                },
                size = 30.dp,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * The postage-edged card (§2.12, §4.17).
 * ------------------------------------------------------------------------------------------------ */

/** The postage edge: 6 dp semicircle bites every 10 dp on all four sides. */
val PostageShape: BittenEdgeShape = BittenEdgeShape(bite = 6.dp, every = 10.dp, allSides = true)

/** The postage edge's rim: the perforation rims' 55 % periwinkle (§2.12). */
internal val PostageRim: Color = Families.periwinkle.mid.copy(alpha = 0.55f)

/**
 * A postage-edged card (the recipe card, §4.17): a white card with 6 dp bites every 10 dp on all four sides, a 2 dp dashed
 * `rule` frame inset 8 dp (exact dashes, DashSpec.forPath), the hard shadow on the cut shape (hs2), and a 1 dp periwinkle
 * rim on the whole cut shape. The card sits on the white letter-set sheet, where the shadow only outlines the right and
 * bottom bites; the rim makes the top and left bites read too.
 */
@Composable
fun PostageCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = PicnicTheme.colors
    Column(
        modifier
            .hardShadow(PostageShape, PicnicTheme.shadows, large = true)
            .clip(PostageShape)
            .background(Color.White)
            .drawBehind {
                val inset = 8.dp.toPx()
                val frame = Path().apply { addRect(Rect(inset, inset, size.width - inset, size.height - inset)) }
                drawDashedPath(frame, c.rule, 2.dp.toPx(), 5.dp.toPx(), 3.dp.toPx(), join = StrokeJoin.Miter)
            }
            .drawWithContent {
                drawContent()
                // Drawn inside the clip, so half of the 2 dp stroke shows: a 1 dp rim along every bite.
                drawOutline(PostageShape.createOutline(size, layoutDirection, this), PostageRim, style = Stroke(2.dp.toPx()))
            }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/* ------------------------------------------------------------------------------------------------
 * v1 snackbar.
 * ------------------------------------------------------------------------------------------------ */

/** Use as SnackbarHost(hostState) { PicnicSnackbar(it) }: the sticky-note look (§4.15) with its Undo chip. */
@Composable
fun PicnicSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    StickyNote(
        message = data.visuals.message,
        actionLabel = data.visuals.actionLabel,
        onAction = { data.performAction() },
        modifier = modifier.padding(start = 14.dp, bottom = 20.dp),
    )
}

/** A spacer as wide as a hard shadow, for rows of shadowed pieces. */
@Composable
fun ShadowRoom(modifier: Modifier = Modifier) = Spacer(modifier.width(4.dp))

/** The size of an [IntSize] in dp. */
internal fun IntSize.toDpSize(density: Density) = with(density) { androidx.compose.ui.unit.DpSize(width.toDp(), height.toDp()) }

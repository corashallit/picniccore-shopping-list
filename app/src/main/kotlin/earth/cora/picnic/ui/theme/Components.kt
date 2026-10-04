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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.toSize
import earth.cora.picnic.R
import earth.cora.picnic.ui.a11y.LocalFocusRingInk
import earth.cora.picnic.ui.a11y.focusRingInk
import earth.cora.picnic.ui.a11y.spaceToggles
import earth.cora.picnic.ui.a11y.tabLeavesField
import earth.cora.picnic.ui.a11y.textOverflowed
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
    // A focus ring on this card shows against its paper (§4.6): the ballpoint on a light card (the Glow page's own ink,
    // white, would vanish on it), white on a dark one.
    val ring = if (color.alpha < 0.5f) LocalFocusRingInk.current else if (color.luminance() > 0.5f) c.ballpoint else Color.White
    Box(
        modifier
            .then(if (shadow) Modifier.hardShadow(shape, PicnicTheme.shadows, large) else Modifier)
            .clip(shape)
            .background(color)
            .then(if (border) Modifier.border(PicnicTheme.dimens.border, c.stickerWhite, shape) else Modifier),
    ) {
        CompositionLocalProvider(LocalFocusRingInk provides ring) { content() }
    }
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
            ops += FaceOp.Fill(if (enabled) Families.strawberry.mid else colors.disabledFace)
            if (leadingStar && enabled) ops += FaceOp.Star(Families.lemon.pastel)
            ops += FaceOp.Label(if (enabled) Color.White else colors.onDisabledFace)
        }
        ButtonKind.Secondary -> {
            ops += FaceOp.Fill(if (enabled) Color.White else colors.disabledFace)
            if (enabled) ops += FaceOp.Ring(Families.periwinkle.trace)
            ops += FaceOp.Label(if (enabled) colors.ballpoint else colors.onDisabledFace)
        }
        // Disabled round faces take the pills' disabled tokens (§4.6): a flat disabled-face disc (`backing` in Day, the
        // rule navy in Glow) with its soft glyph, no gloss and no shadow, never a 50 % ghost of the live face.
        ButtonKind.Round -> {
            ops += FaceOp.Fill(if (enabled) colors.pad else colors.disabledFace)
            ops += FaceOp.GlyphInk(if (enabled) colors.ink else colors.onDisabledFace)
        }
        ButtonKind.AddBarSnap, ButtonKind.AddBarSpeak, ButtonKind.AddBarSend -> {
            val family = when (kind) {
                ButtonKind.AddBarSnap -> Families.ramune
                ButtonKind.AddBarSpeak -> Families.sakura
                else -> Families.strawberry
            }
            ops += FaceOp.Fill(if (enabled) family.pastel else colors.disabledFace)
            ops += FaceOp.GlyphInk(if (enabled) colors.ballpoint else colors.onDisabledFace)
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

/**
 * A 2 dp focus ring 3 dp outside [shape] (keyboard / switch access): flat, never a glow. Every kit control draws it in
 * the page's ink (ballpoint in Day, white in Glow), so it shows on the navy page too. The list's own pieces (rows,
 * strips, the tray's chevron, the sticker book) wear the same ring through [showsFocusRing] (§4.4).
 */
fun Modifier.focusRing(shape: Shape, color: Color, visible: Boolean): Modifier = if (!visible) this else drawBehind {
    val gap = 3.dp.toPx()
    val w = 2.dp.toPx()
    val grow = gap + w / 2f
    val outline = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
    translate(-grow, -grow) { drawOutline(outline, color, style = Stroke(w)) }
}

/**
 * Whether a piece focused through [interaction] wears its focus ring (§4.4, §15): while it has focus and the keyboard
 * (or a switch acting as one) is driving focus. A piece that also takes focus in touch mode, so a fold can hand focus
 * to it (§8.5), shows no ring after a tap: a ring is a keyboard user's cursor, not a touch mark.
 */
@Composable
fun showsFocusRing(interaction: androidx.compose.foundation.interaction.InteractionSource): Boolean {
    val focused by interaction.collectIsFocusedAsState()
    return focused && androidx.compose.ui.platform.LocalInputModeManager.current.inputMode == androidx.compose.ui.input.InputMode.Keyboard
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
 *   steps of 80 ms. Disabled: the disabled face (`backing` in Day, the rule navy in Glow), its soft label, no stroke,
 *   shadow or gloss; [busy] leads with a 4-point star spinner.
 * - Secondary: 48 dp white pill, 3 dp white border, an inset 2 dp periwinkle-trace outline, hs; DynaPuff 16 ballpoint,
 *   optional 18 dp [glyph]. Pressed (+1.6, +2.2), flat.
 * - Text: no face; Zen Maru 700 15 sp with a wavy 2 dp sakura underline; pressed = a highlighter band behind the label.
 * Focus: a 2 dp ballpoint ring 3 dp outside.
 *
 * A label too long for one line (a large font, a narrow slot) never loses words: the candy pill's shrinks to 15 sp,
 * then wraps; the others wrap; both onto 2 centred lines, the face growing round them with its one-line caps (a rounded
 * label, never a fat capsule cutting the corners off its words). Past 2 lines it ends in "…" and reports
 * TextOverflowed; TalkBack always hears the whole label.
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

/** The lines a pill's label may take before it ends in "…" (§4.6). */
internal const val PILL_LABEL_LINES = 2

/** The size the candy pill's puffy label shrinks to before it wraps. */
private val PILL_MIN_LABEL = 15.sp

/** The candy pill's label stroke (4 dp #C3264F). */
private val PILL_LABEL_STROKE = 4.dp

/** The secondary's inset periwinkle ring: [PILL_RING_WIDTH] wide, [PILL_RING_INSET] inside the face. */
internal val PILL_RING_INSET = 2.dp
internal val PILL_RING_WIDTH = 2.dp

/** How far a wrapped label's ink keeps from the secondary's inset ring. */
internal val PILL_RING_CLEARANCE = 3.dp

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
    val kind = if (primary) ButtonKind.Primary else ButtonKind.Secondary
    val ops = faceOps(kind, enabled, c, leadingStar)
    val puffy = primary && enabled
    // The label's bounds in the face's own coordinates (the gloss keeps 4 dp clear of them, R3).
    var faceCoords by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var labelCoords by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val labelStyle = if (primary) t.primaryButton else t.secondaryButton
    val labelFontPx = with(density) { labelStyle.fontSize.toPx() }
    val border = PicnicTheme.dimens.border
    // The label on one line: its width (px, what the label row needs) and its box's height.
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val oneLine: IntSize = remember(text, labelStyle, puffy, density) {
        if (puffy) {
            val stroke = with(density) { PILL_LABEL_STROKE.toPx() }
            val box = measurer.measure(text, labelStyle, maxLines = 1, softWrap = false).size.height
            IntSize(puffyLineWidth(measurer, AnnotatedString(text), labelStyle, PILL_MIN_LABEL.value, stroke, 0f), ceil(box + stroke).toInt())
        } else {
            measurer.measure(text, labelStyle, maxLines = 1, softWrap = false).size
        }
    }
    val ringed = ops.any { it is FaceOp.Ring }
    // The secondary's label ink inside its line box (px): the blank above its highest ink and below its lowest, read off
    // the label drawn once on one line. A wrapped label's first and last lines keep at least that, so the inset ring can
    // be kept clear of their letters. Worked out only when the label wraps.
    val inkBlank: Lazy<Pair<Float, Float>> = remember(text, labelStyle, ringed, density, measurer) {
        lazy {
            if (!ringed || text.isEmpty()) 0f to 0f
            else inkBlank(measurer.measure(text, labelStyle, maxLines = 1, softWrap = false))
        }
    }
    // R3 at large text: once the label's ink band reaches the dot's height (font scale ≳ 1.3), a label starting at the
    // usual 27 / 21 dp would sit within 4 dp of the dot, and the mirrored gloss would meet its end. The label then starts
    // past the dot instead, so the gloss keeps its place in the left cap and its 4 dp clearance (ButtonGlossTest).
    // The face is [height] tall at least and grows with a label too tall for it (font scales past 2.0): the inner face
    // is the larger of the nominal one and the label's box (its line, plus the primary's 4 dp stroke).
    val faceInner = maxOf(
        height - border * 2,
        with(density) { labelStyle.lineHeight.toDp() } + if (puffy) PILL_LABEL_STROKE else 0.dp,
    )
    val k = faceInner / 52.dp
    val pastDot = GLOSS_DOT_RIGHT * k + ButtonGloss.CLEARANCE + 1.dp
    val labelStart = run {
        val font = with(density) { labelStyle.fontSize.toDp() }
        val inkTop = faceInner / 2 - font / 2
        val base = if (primary) 27.dp else 21.dp
        if (inkTop < GLOSS_DOT_BOTTOM * k + ButtonGloss.CLEARANCE) maxOf(base, pastDot) else base
    }
    val labelEnd = if (primary) 21.dp else 19.dp
    val leads = (busy && !enabled) || (leadingStar && primary) || glyph != null
    // The one-line face (its caps): a wrapped label's taller face keeps them, on a rounded label.
    val capInner = maxOf(faceInner, with(density) { oneLine.height.toDp() })
    val shape = CappedStadium(capInner / 2 + border)
    val innerShape = CappedStadium(capInner / 2)
    val capInnerPx = with(density) { capInner.toPx() }
    val lineHeightPx = with(density) { labelStyle.lineHeight.toPx() }
    fun labelBox(): Rect? {
        val f = faceCoords
        val l = labelCoords
        if (f == null || l == null || !f.isAttached || !l.isAttached) return null
        val row = f.localBoundingBoxOf(l, clipBounds = false)
        // A wrapped label inks its whole block; one line, the band its glyphs ink.
        return if (row.height > lineHeightPx * 1.5f) row else labelInkBand(row, labelFontPx)
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
                .focusRing(CappedStadium(capInner / 2 + border + FOCUS_GROW), focusRingInk(), focused)
                .then(if (enabled) Modifier.hardShadow(shape, shadows.color, shadowOffset) else Modifier)
                .offset(faceOffset.x, faceOffset.y)
                .clip(shape)
                .background(if (enabled) c.stickerWhite else c.disabledFace)
                .padding(border)
                .clip(innerShape)
                .testTag(FACE_TAG)
                .onPlaced { faceCoords = it }
                .drawBehind {
                    // The gloss and the inset ring belong to the one-line face's caps.
                    val capped = Size(size.width, min(size.height, capInnerPx))
                    for (op in ops) when (op) {
                        is FaceOp.Fill -> drawRect(op.color)
                        is FaceOp.Ring -> {
                            val inset = PILL_RING_INSET.toPx()
                            val w = PILL_RING_WIDTH.toPx()
                            drawRoundRect(
                                op.color, Offset(inset + w / 2, inset + w / 2), Size(size.width - inset * 2 - w, size.height - inset * 2 - w),
                                CornerRadius((capped.height - inset * 2 - w) / 2f), style = Stroke(w),
                            )
                        }
                        is FaceOp.Crescent -> drawPath(ButtonGloss.of(FaceKind.Pill, capped, labelBox(), this).crescent, op.color)
                        is FaceOp.Dot -> ButtonGloss.of(FaceKind.Pill, capped, labelBox(), this).let { drawCircle(op.color, it.dotRadius, it.dotCenter) }
                        else -> Unit
                    }
                }
                .layout { measurable, constraints ->
                    // The label's insets: past the dot when it wraps (its first line then reaches the gloss's height).
                    val endPx = labelEnd.roundToPx()
                    val basePx = labelStart.roundToPx()
                    // The leading star, spinner or glyph and the row's gap, rounded as the row places them.
                    val room = constraints.maxWidth - basePx - endPx - if (leads) 18.dp.roundToPx() + 6.dp.roundToPx() else 0
                    val wraps = constraints.hasBoundedWidth && oneLine.width > room
                    val startPx = if (wraps) max(basePx, pastDot.roundToPx()) else basePx
                    // Measured at its own height: the face's minimum is applied below, round the label.
                    val placeable = measurable.measure(constraints.offset(horizontal = -(startPx + endPx)).copy(minHeight = 0))
                    val w = constraints.constrainWidth(placeable.width + startPx + endPx)
                    // A wrapped label keeps the one-line label's room above and below it (never flush with the face's
                    // edge), and on the secondary its first and last lines' ink keeps 3 dp from the inset ring.
                    var top = 0
                    var bottom = 0
                    if (placeable.height > oneLine.height + 1) {
                        val room = max(0f, (capInnerPx - oneLine.height) / 2f)
                        val floor = if (ringed) (PILL_RING_INSET + PILL_RING_WIDTH + PILL_RING_CLEARANCE).toPx() else 0f
                        val (above, below) = inkBlank.value
                        top = ceil(max(room, floor - above)).toInt()
                        bottom = ceil(max(room, floor - below)).toInt()
                    }
                    val h = constraints.constrainHeight(placeable.height + top + bottom)
                    val y = if (top + bottom == 0) ((h - placeable.height) / 2f).roundToInt() else top
                    layout(w, h) { placeable.place(startPx, y) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.onPlaced { labelCoords = it },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    busy && !enabled -> StarSpinner(Modifier.size(18.dp), c.onDisabledFace)
                    leadingStar && primary -> FlatStar(Modifier.size(18.dp))
                    glyph != null -> Glyph(glyph, null, tint = if (enabled) (if (primary) Color.White else c.ballpoint) else c.onDisabledFace, size = 18.dp)
                }
                val labelColor = ops.filterIsInstance<FaceOp.Label>().first().color
                if (puffy) {
                    PuffyText(
                        text, t.primaryButton, fill = labelColor, stroke = PILL_LABEL_STROKE,
                        strokeColor = Families.strawberry.trace, shadow = null, minFontSize = PILL_MIN_LABEL,
                        maxLines = PILL_LABEL_LINES, textAlign = TextAlign.Center, wrapLast = true,
                    )
                } else {
                    WholeLabel(text, labelStyle.copy(color = labelColor), PILL_LABEL_LINES, TextAlign.Center)
                }
            }
        }
    }
}

/**
 * The blank (px) above [layout]'s highest ink and below its lowest, inside its box: the text drawn once into a small
 * bitmap and its rows read (a glyph's own outline, not its line box, on a device and under tests alike).
 */
internal fun inkBlank(layout: TextLayoutResult): Pair<Float, Float> {
    val w = layout.size.width
    val h = layout.size.height
    if (w <= 0 || h <= 0) return 0f to 0f
    val pad = ceil(h / 4f).toInt()
    val image = androidx.compose.ui.graphics.ImageBitmap(w + pad * 2, h + pad * 2)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    canvas.translate(pad.toFloat(), pad.toFloat())
    layout.multiParagraph.paint(canvas, Color.Black)
    val bw = image.width
    val row = IntArray(bw)
    var first = -1
    var last = -1
    val bitmap = image.asAndroidBitmap()
    for (y in 0 until image.height) {
        bitmap.getPixels(row, 0, bw, 0, y, bw, 1)
        if (row.any { (it ushr 24) >= 128 }) {
            if (first < 0) first = y
            last = y
        }
    }
    if (first < 0) return 0f to 0f
    return (first - pad).toFloat().coerceAtLeast(0f) to (h - (last + 1 - pad)).toFloat().coerceAtLeast(0f)
}

/** How far the focus ring's outline sits outside the face (3 dp gap + half its 2 dp line, [focusRing]). */
private val FOCUS_GROW = 4.dp

/**
 * A kit label that never loses words silently: it wraps onto [maxLines], then ends in "…" and reports
 * [earth.cora.picnic.ui.a11y.TextOverflowed] (the clipped-text checks see it; TalkBack still reads the whole text).
 * A wrapped label is as wide as its longest line, so its chip or pill hugs it instead of running to the slot's edge.
 */
@Composable
internal fun WholeLabel(text: String, style: TextStyle, maxLines: Int, align: TextAlign = TextAlign.Start, modifier: Modifier = Modifier) {
    var lost by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Balanced breaks: a wrapped label is two even lines ("Keep everything, / uncheck it all"), never a long line and
    // an orphan word, so the face that hugs its longest line stays compact.
    val labelStyle = style.copy(textAlign = align, lineBreak = LineBreak.Heading)
    val measurer = rememberTextMeasurer(cacheSize = 2)
    Text(
        text,
        style = labelStyle,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { r -> lost = r.didOverflowHeight || r.multiParagraph.didExceedMaxLines || (0 until r.lineCount).any { r.isLineEllipsized(it) } },
        modifier = modifier
            .layout { measurable, constraints ->
                val width = if (!constraints.hasBoundedWidth) constraints.maxWidth else {
                    val r = measurer.measure(text, labelStyle, TextOverflow.Ellipsis, maxLines = maxLines, constraints = Constraints(maxWidth = constraints.maxWidth))
                    if (r.lineCount < 2) constraints.maxWidth else {
                        // Its longest line, and a px of slack so re-laying it out at that width keeps every break.
                        val longest = (0 until r.lineCount).maxOf { r.getLineRight(it) - r.getLineLeft(it) }
                        (ceil(longest).toInt() + 1).coerceIn(constraints.minWidth, constraints.maxWidth)
                    }
                }
                val placeable = measurable.measure(constraints.copy(maxWidth = width))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .semantics { if (lost) textOverflowed = true },
    )
}

/**
 * A stadium whose caps stop growing at [maxRadius]: a pill or chip face that grows taller than one line (a wrapped label)
 * keeps its one-line caps on a rounded label, so the curve never cuts into the corners of its words. Up to twice
 * [maxRadius] tall it is the plain stadium.
 */
@Immutable
internal class CappedStadium(private val maxRadius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.height / 2f, with(density) { maxRadius.toPx() }).coerceAtLeast(0f)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
    }

    override fun equals(other: Any?): Boolean = other is CappedStadium && other.maxRadius == maxRadius

    override fun hashCode(): Int = maxRadius.hashCode()
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
                .focusRing(shape, focusRingInk(), focused)
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
 * shadow. Disabled: the pills' disabled tokens (a flat disabled-face disc, its soft glyph, no gloss or shadow).
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
 * A round sticker face of [kind] and [faceSize] (≥48 dp target), with the R3 gloss. Disabled: a flat disabled-face disc
 * (`disabledFace`, its border too), the glyph in `onDisabledFace`, no gloss and no shadow, as the disabled pills (§4.6).
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
                .focusRing(CircleShape, focusRingInk(), focused)
                .then(if (enabled) Modifier.hardShadow(CircleShape, shadows.color, shadows.hs) else Modifier)
                .offset(faceOffset.x, faceOffset.y)
                .clip(CircleShape)
                .background(if (enabled) c.stickerWhite else c.disabledFace)
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
    val focused by interaction.collectIsFocusedAsState()
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
                .focusRing(CircleShape, focusRingInk(), focused)
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
 * Pressed: the face drops onto its shadow in 2 steps, like the pills (never a ripple). A label too long for one line
 * wraps onto 2 and the face grows round it; past that it ends in "…" ([WholeLabel]). [leadingHeight] is how tall
 * [leading] stands at most (the chip's caps hold it). Focused (keyboard / switch access): the §4.6 focus ring.
 */
@Composable
fun PicnicChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    family: StickerFamily = Families.ramune,
    leadingHeight: Dp = 15.dp,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = PicnicTheme.colors
    val on = stringResource(R.string.theme_switch_on)
    val off = stringResource(R.string.theme_switch_off)
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .semantics { stateDescription = if (selected) on else off }
            .clickable(interaction, indication = null, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChipFace(
            fill = if (selected) family.mid else family.pastel,
            press = rememberPressFraction(pressed),
            leadingHeight = leadingHeight,
            focused = focused,
        ) {
            when {
                leading != null -> leading()
                selected -> DecorSticker("star", size = 15.dp, shadow = false)
            }
            WholeLabel(text, PicnicTheme.type.label.copy(color = c.ballpoint), CHIP_LABEL_LINES)
        }
    }
}

/** The lines a chip's label may take before it ends in "…" (§4.7). */
internal const val CHIP_LABEL_LINES = 2

/**
 * A chip face: a 32 dp kiss-cut pill (3 dp white border, hs) with [fill]. Decorative container. [height] is a MINIMUM:
 * at large font scales the face grows with its label (1 dp above and below it, inside the border) instead of clipping
 * it (§15, font scale 2.0); a wrapped label grows it further, on a rounded label with its one-line caps
 * ([CappedStadium]: one line of the chip label, or [leadingHeight], whichever is taller). A caller that sizes the face
 * to its label's line itself ([StoreAisleChip]) passes [grows] false: the face is then exactly [height] tall. [press]
 * (0 → 1) drops the face onto its shadow. [focused] (keyboard / switch access) draws the §4.6 focus ring: 2 dp in
 * the page's ink, 3 dp outside the face, flat, staying put while the face drops.
 */
@Composable
fun ChipFace(
    fill: Color,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    border: Dp = PicnicTheme.dimens.border,
    horizontal: Dp = 11.dp,
    grows: Boolean = true,
    press: Float = 0f,
    leadingHeight: Dp = 0.dp,
    focused: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val shadows = PicnicTheme.shadows
    val line = rememberLineHeight(PicnicTheme.type.label)
    val inner = maxOf(height - border * 2, maxOf(line, leadingHeight) + CHIP_LABEL_ROOM * 2)
    val shape = CappedStadium(inner / 2 + border)
    Row(
        modifier
            .then(if (grows) Modifier.heightIn(min = height) else Modifier.height(height))
            .focusRing(CappedStadium(inner / 2 + border + FOCUS_GROW), focusRingInk(), focused)
            .hardShadow(shape, shadows)
            .offset(shadows.hs.x * press, shadows.hs.y * press)
            .clip(shape)
            .background(PicnicTheme.colors.stickerWhite)
            .padding(border)
            .clip(CappedStadium(inner / 2))
            .background(fill)
            .padding(horizontal = horizontal, vertical = if (grows) CHIP_LABEL_ROOM else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        content = content,
    )
}

/** The height of one line of [style] here (its text box, which may stand taller than its line height). */
@Composable
internal fun rememberLineHeight(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val density = LocalDensity.current
    return remember(style, density) { with(density) { measurer.measure("Ag", style, maxLines = 1, softWrap = false).size.height.toDp() } }
}

/** The room a grown chip keeps above and below its label, inside the border. */
private val CHIP_LABEL_ROOM = 1.dp

/** How far a number circle's numeral ink keeps from the inside of its ring (§4.10). */
internal val NUMBER_RING_CLEARANCE = 0.75.dp

/**
 * A numeral's ink: the centres of the px that read as ink, at least half-way from [paper] to [ink] (the circle's
 * white and its trace, as the numeral is drawn), as x, y pairs in its text box (px). Drawn once from [layout] into a
 * small bitmap, so a circle can be sized on the glyphs themselves, not their line box ([numberCircleDiameter]). Only
 * the outermost px of each row are kept: the ink's reach from any centre is theirs.
 */
internal fun numeralInk(layout: TextLayoutResult, ink: Color, paper: Color): FloatArray {
    val w = layout.size.width
    val h = layout.size.height
    if (layout.layoutInput.text.isEmpty() || w <= 0 || h <= 0) return FloatArray(0)
    // A margin for glyphs that overhang their box.
    val pad = ceil(h / 4f).toInt()
    val image = androidx.compose.ui.graphics.ImageBitmap(w + pad * 2, h + pad * 2)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    canvas.drawRect(0f, 0f, image.width.toFloat(), image.height.toFloat(), androidx.compose.ui.graphics.Paint().apply { color = paper })
    canvas.translate(pad.toFloat(), pad.toFloat())
    layout.multiParagraph.paint(canvas, ink)
    val bw = image.width
    val px = IntArray(bw * image.height)
    image.asAndroidBitmap().getPixels(px, 0, bw, 0, 0, bw, image.height)
    fun lum(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
    val paperLum = lum(paper.toArgb())
    val halfway = (paperLum + lum(ink.toArgb())) / 2f
    val darker = lum(ink.toArgb()) < paperLum
    val out = ArrayList<Float>()
    for (y in 0 until image.height) {
        var first = -1
        var last = -1
        for (x in 0 until bw) if (lum(px[y * bw + x]).let { if (darker) it <= halfway else it >= halfway }) {
            if (first < 0) first = x
            last = x
        }
        if (first < 0) continue
        val cy = y + 0.5f - pad
        out += first + 0.5f - pad; out += cy
        if (last != first) { out += last + 0.5f - pad; out += cy }
    }
    return out.toFloatArray()
}

/** Where a [len] px numeral box sits in a [d] px circle (centred, rounded as Compose's Alignment.Center rounds). */
internal fun numeralOffset(d: Int, len: Int): Int = ((d - len) / 2f).roundToInt()

/**
 * The aisle number circle's diameter (px): [min]; at a large font at least its numeral's [textW] × [textH] px text box
 * and ring across (so a one-digit numeral keeps the air it has at 1.0 as it grows); and at least the smallest circle
 * whose ring ([ringPx] wide) keeps [clearPx] from every point of the numeral's [ink] (x, y pairs in its text box,
 * centred in the circle as [NumberCircle] places it). The ink rule is what holds a two-digit numeral, whose corners
 * reach past its box's circle. Without ink (the capsule mark) the box rule alone.
 */
internal fun numberCircleDiameter(min: Int, textW: Int, textH: Int, ink: FloatArray?, ringPx: Float, clearPx: Float): Int {
    val boxed = max(min, max(textW, textH) + ringPx.roundToInt() * 2)
    if (ink == null || ink.isEmpty()) return boxed
    fun clears(d: Int): Boolean {
        val x0 = numeralOffset(d, textW)
        val y0 = numeralOffset(d, textH)
        val c = d / 2f
        val limit = c - ringPx - clearPx
        var i = 0
        while (i < ink.size) {
            if (hypot(ink[i] + x0 - c, ink[i + 1] + y0 - c) > limit) return false
            i += 2
        }
        return true
    }
    // The ink's reach from the box's centre gives the size to within the px the centring rounds by.
    var reach = 0f
    var i = 0
    while (i < ink.size) {
        reach = max(reach, hypot(ink[i] - textW / 2f, ink[i + 1] - textH / 2f))
        i += 2
    }
    val estimate = ceil(2 * (reach + ringPx + clearPx)).toInt()
    var d = max(boxed, estimate - 2)
    while (!clears(d) && d < max(boxed, estimate) + 4) d++
    return d
}

/**
 * The aisle number circle (§2.7, §4.10): white, a 2 dp ring in the aisle trace, DynaPuff numeral in the trace; the
 * capsule glyph for "Everything else" ([number] null). Decorative (the label repeats the number). [size] is a MINIMUM:
 * the numeral scales with the font (sp), and the circle grows with its numeral's text box and further, until its ring
 * keeps [NUMBER_RING_CLEARANCE] from the numeral's ink (its glyphs: a two-digit numeral's corners reach past its box's
 * circle), so a numeral never touches or crosses the ring (§15, font scale 2.0). At 1.0 every numeral 1 to 24 already
 * clears it, so the circle keeps its drawn size. The numeral is drawn over the circle, never clipped by it.
 *
 * [maxSize] caps the growth (never under [size]) for a caller whose words need the room (the index tab, whose name the
 * circle gives way to, §4.10): held smaller than it would grow, the circle keeps the numeral clear of its ring by setting
 * it smaller instead ([fitNumeral]), never by letting it touch.
 */
@Composable
fun NumberCircle(number: Int?, look: AisleLook, modifier: Modifier = Modifier, size: Dp = 24.dp, ring: Dp = 2.dp, maxSize: Dp = Dp.Infinity) {
    val style = numberCircleStyle(size)
    val ink = rememberNumeralInk(number, style, look.family.trace)
    val fitted = rememberNumeralFit(number.takeIf { maxSize != Dp.Infinity }, look, style, size, maxSize, ring)
    androidx.compose.ui.layout.Layout(
        content = {
            Box(Modifier.clip(CircleShape).background(PicnicTheme.colors.stickerWhite).ringStroke(ring, look.family.trace))
            if (number != null) {
                Text(number.toString(), style = (fitted?.style ?: style).copy(color = look.family.trace), maxLines = 1, softWrap = false)
            } else {
                CapsuleMark(look.family, Modifier.size(size * 0.6f))
            }
        },
        modifier = modifier.clearAndSetSemantics { },
    ) { measurables, constraints ->
        val mark = measurables[1].measure(androidx.compose.ui.unit.Constraints())
        val d = fitted?.diameter ?: numberCircleDiameter(size.roundToPx(), mark.width, mark.height, ink, ring.toPx(), NUMBER_RING_CLEARANCE.toPx())
        val w = constraints.constrainWidth(d)
        val h = constraints.constrainHeight(d)
        val circle = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
        layout(w, h) {
            circle.place(0, 0)
            mark.place(numeralOffset(w, mark.width), numeralOffset(h, mark.height))
        }
    }
}

/** The numeral style of a [size] number circle: the chip's 11 sp at 20 dp and under, the tab's 14 sp above. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun numberCircleStyle(size: Dp): TextStyle = if (size <= 20.dp) PicnicTheme.type.chipNumber else PicnicTheme.type.tabNumber

/** A numeral fitted to a capped number circle: its [style] and the circle's [diameter] (px). */
@Immutable
internal class NumeralFit(val style: TextStyle, val diameter: Int)

/** The least a capped circle sets its numeral, as drawn (dp, at any font scale): a 24 dp circle holds 1 to 24 well above it. */
private val NUMERAL_FLOOR: Dp = 6.dp

/**
 * [number] in [style] in a circle of [min] to [max] px with a [ringPx] ring (§2.7, item 337): the diameter
 * [numberCircleDiameter] asks for, capped at [max]. When the cap holds the circle smaller than that, the numeral is set
 * 0.5 sp smaller each step (its line height in proportion) until the ring keeps [NUMBER_RING_CLEARANCE] from its ink in
 * the capped circle. Measured on [ink] over [paper], as the circle draws it.
 */
internal fun Density.fitNumeral(measurer: TextMeasurer, number: Int, style: TextStyle, ink: Color, paper: Color, min: Int, max: Int, ringPx: Float): NumeralFit {
    val clear = NUMBER_RING_CLEARANCE.toPx()
    fun needs(s: TextStyle): Int {
        val layout = measurer.measure(number.toString(), s, maxLines = 1, softWrap = false)
        return numberCircleDiameter(min, layout.size.width, layout.size.height, numeralInk(layout, ink, paper), ringPx, clear)
    }
    val cap = max.coerceAtLeast(min)
    val natural = needs(style)
    if (natural <= cap) return NumeralFit(style, natural)
    val from = style.fontSize.value
    val lineRatio = if (style.lineHeight.isSp && style.fontSize.isSp) style.lineHeight.value / from else null
    var sp = from
    var s = style
    while (sp.sp.toPx() > NUMERAL_FLOOR.toPx()) {
        sp -= 0.5f
        s = style.copy(fontSize = sp.sp, lineHeight = lineRatio?.let { (sp * it).sp } ?: style.lineHeight)
        if (needs(s) <= cap) break
    }
    return NumeralFit(s, cap)
}

/** [fitNumeral] for [number] (null: no cap, or the capsule) in a [size] circle capped at [maxSize] here. */
@Composable
private fun rememberNumeralFit(number: Int?, look: AisleLook, style: TextStyle, size: Dp, maxSize: Dp, ring: Dp): NumeralFit? {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val density = LocalDensity.current
    val paper = PicnicTheme.colors.stickerWhite
    return remember(number, look, style, size, maxSize, ring, density, measurer, paper) {
        if (number == null || maxSize == Dp.Infinity) return@remember null
        with(density) { fitNumeral(measurer, number, style, look.family.trace, paper, size.roundToPx(), maxSize.roundToPx(), ring.toPx()) }
    }
}

/**
 * A circle's ring exactly [width] wide, drawn as Modifier.border draws a circle's (a stroke half its width inside the
 * edge). Modifier.border rounds its stroke up to whole px: at 2.625 px per dp a 2 dp ring would draw 2.29 dp and crowd
 * the numeral the circle was sized for. At whole-px widths (3 px per dp) the two draw the same pixels.
 */
internal fun Modifier.ringStroke(width: Dp, color: Color): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val half = w / 2f
    drawRoundRect(
        color, topLeft = Offset(half, half), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(max(0f, size.minDimension / 2f - half)), style = Stroke(w),
    )
}

/** [number]'s ink in [style] here ([numeralInk]); null for the capsule mark. */
@Composable
private fun rememberNumeralInk(number: Int?, style: TextStyle, ink: Color): FloatArray? {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val paper = PicnicTheme.colors.stickerWhite
    return remember(number, style, measurer, ink, paper) {
        number?.let { numeralInk(measurer.measure(it.toString(), style, maxLines = 1, softWrap = false), ink, paper) }
    }
}

/** How big a [NumberCircle] of [number] and [size] draws here (it grows with a large font, [numberCircleDiameter]). */
@Composable
internal fun rememberNumberCircleSize(number: Int?, look: AisleLook, size: Dp, ring: Dp = 2.dp): Dp {
    val style = numberCircleStyle(size)
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val density = LocalDensity.current
    val paper = PicnicTheme.colors.stickerWhite
    return remember(number, look, size, ring, style, density, measurer, paper) {
        if (number == null) return@remember size
        val layout = measurer.measure(number.toString(), style, maxLines = 1, softWrap = false)
        val ink = numeralInk(layout, look.family.trace, paper)
        with(density) { numberCircleDiameter(size.roundToPx(), layout.size.width, layout.size.height, ink, ring.toPx(), NUMBER_RING_CLEARANCE.toPx()).toDp() }
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
        leadingHeight = rememberNumberCircleSize(number, look, 20.dp),
    )
}

/**
 * The store's own aisle number chip "A2" (§4.7): a 22 dp lemon-pastel pill, 2 dp white border, hs, a 13 dp basket glyph
 * and "A2" (Zen Maru 700 13 sp); TalkBack "store aisle 2". Under a large font the face, the glyph and the label all
 * scale with the label's text box, so the chip keeps its 1.0 proportions. It fits its slot as [fitChipToSlot] says: never
 * drawn under its 1.0 size, so its label never under 13 sp.
 */
@Composable
fun StoreAisleChip(number: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.theme_store_aisle, number)
    val style = storeChipStyle()
    val text = "A$number"
    ChipFace(
        fill = Families.lemon.pastel,
        modifier = modifier.fitChipToSlot(style.fontSize).clearAndSetSemantics { contentDescription = label },
        height = labelChipHeight(22.dp, text, style, border = 2.dp),
        border = STORE_CHIP_BORDER,
        horizontal = STORE_CHIP_PADDING,
        grows = false,
    ) {
        Glyph(PicnicGlyph.Basket, null, tint = PicnicTheme.colors.ballpoint, size = STORE_CHIP_GLYPH * labelBoxGrowth(text, style))
        Text(text, style = style, maxLines = 1, softWrap = false)
    }
}

/** The store-aisle chip's label style: Zen Maru 700 13 sp on 15 sp lines, ballpoint. */
@Composable
private fun storeChipStyle(): TextStyle = PicnicTheme.type.label.copy(color = PicnicTheme.colors.ballpoint, fontSize = 13.sp, lineHeight = 15.sp)

private val STORE_CHIP_BORDER = 2.dp
private val STORE_CHIP_PADDING = 7.dp
private val STORE_CHIP_GLYPH = 13.dp

/** The gap between a chip's leading glyph and its label ([ChipFace]'s row). */
private val CHIP_GAP = 5.dp

/**
 * The store-aisle chip's natural width for [number] here, as [StoreAisleChip] lays it out (its border, padding, the
 * scaled glyph and the gap round "A[number]"): for a caller that plans its row round the chip (the Next card, §10).
 */
@Composable
internal fun storeAisleChipWidth(number: Int): Dp {
    val style = storeChipStyle()
    val text = "A$number"
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val density = LocalDensity.current
    val growth = labelBoxGrowth(text, style)
    return remember(text, style, density, growth) {
        with(density) {
            val label = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp()
            STORE_CHIP_BORDER * 2 + STORE_CHIP_PADDING * 2 + STORE_CHIP_GLYPH * growth + CHIP_GAP + label
        }
    }
}

/**
 * A small chip's height for its one-line [label] in [style]: [min] at font scale 1.0, and under a larger font [min]
 * scaled as the label's text box grew from 1.0 ([labelBoxGrowth]), so the face keeps its drawn proportions and the
 * label its place in it (never sinking onto the border). It is always at least that text box (Zen Maru's and DynaPuff's
 * ascent and descent, taller than these chips' 15 sp line) inside [border], so the box is never cut; at 1.0 the box may
 * overhang the face's inside by up to [CHIP_LEADING] of blank leading, as it always has.
 */
@Composable
internal fun labelChipHeight(min: Dp, label: String, style: TextStyle, border: Dp): Dp {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val density = LocalDensity.current
    val growth = labelBoxGrowth(label, style)
    return remember(label, style, density, measurer, min, border, growth) {
        val now = measurer.measure(label, style, maxLines = 1, softWrap = false).size.height.toFloat()
        with(density) {
            val needed = maxOf(min.toPx() * growth, now + border.toPx() * 2)
            if (needed <= (min + CHIP_LEADING).toPx()) min else needed.toDp()
        }
    }
}

/**
 * How much [label]'s one-line text box in [style] has grown from font scale 1.0 here (1 at 1.0; under Android's
 * non-linear scaling, what the platform's converter gave this size): a small chip's face and glyph scale by it.
 */
@Composable
internal fun labelBoxGrowth(label: String, style: TextStyle): Float {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val density = LocalDensity.current
    return remember(label, style, density, measurer) {
        fun box(at: Density) = measurer.measure(label, style, maxLines = 1, softWrap = false, density = at).size.height.toFloat()
        (box(density) / box(Density(density.density, 1f))).coerceAtLeast(1f)
    }
}

/** The blank leading a small chip's label box may overhang its face by before the chip grows. */
private val CHIP_LEADING = 1.dp

/**
 * The "shiny!" / "jackpot!" chip (§4.7): DynaPuff 13 sp ballpoint on a holo pill with a 1.5 dp white border, 22 dp tall
 * at least (it scales with its label's text box under a large font instead of clipping). It fits its slot as
 * [fitChipToSlot] says: never drawn under its 1.0 size, so its word never under 13 sp. Decorative.
 */
@Composable
fun RarityChip(jackpot: Boolean, modifier: Modifier = Modifier) {
    val text = stringResource(if (jackpot) R.string.theme_chip_jackpot else R.string.theme_chip_shiny)
    val shape = PicnicTheme.shapes.pill
    val style = PicnicTheme.type.counter.copy(color = PicnicTheme.colors.ballpoint, fontSize = 13.sp, lineHeight = 15.sp)
    Box(
        modifier
            .fitChipToSlot(style.fontSize)
            .height(labelChipHeight(22.dp, text, style, border = 1.5.dp))
            .clearAndSetSemantics { }
            .clip(shape)
            .background(PicnicTheme.colors.stickerWhite)
            .padding(1.5.dp)
            .clip(shape)
            .drawBehind { drawRect(holoBrush(Offset(size.width / 2f, size.height / 2f))) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, maxLines = 1, softWrap = false)
    }
}

/**
 * How a small decorative chip (store aisle, shiny / jackpot) whose word is set at [wordSize] fits its slot (§2.7, §4.7):
 * at its natural width when it fits. In a narrower slot under a large font (a crowded row) the whole chip is drawn
 * smaller, face and word together, but never smaller than its font scale 1.0 size, so its word is never under the
 * 13 sp floor; at 1.0 it never shrinks. The floor is the word's own growth from 1.0 (under Android's non-linear font
 * scaling a 13 sp word grows less than the font scale). In a slot narrower even than that it keeps that size and runs on
 * past the slot's end, uncut: a caller whose slot can be that narrow gives the chip a line of its own (a FlowRow, as the
 * recipe card does) or the row's whole width.
 */
/**
 * The least scale [fitChipToSlot] draws a chip at, for a word set at [wordSize]: the word's size at 1.0 over its size
 * here (1 / fontScale when text scales linearly, a little more under Android's non-linear scaling; 1 at 1.0). A caller
 * that plans a row round a store-aisle chip (the tray header) never offers it less than its natural width times this.
 */
internal fun Density.chipFloorScale(wordSize: TextUnit = 13.sp): Float =
    if (wordSize.isSp) min(1f, wordSize.value * density / wordSize.toPx()) else 1f

internal fun Modifier.fitChipToSlot(wordSize: TextUnit): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val floor = chipFloorScale(wordSize)
    val scale = if (!constraints.hasBoundedWidth || p.width <= constraints.maxWidth) 1f
    else max(floor, constraints.maxWidth.toFloat() / p.width)
    val w = (p.width * scale).roundToInt()
    val h = (p.height * scale).roundToInt()
    val lw = constraints.constrainWidth(w)
    val lh = constraints.constrainHeight(h)
    val y = (lh - h) / 2
    val baseline = p[FirstBaseline]
    val lines: Map<AlignmentLine, Int> = if (baseline == AlignmentLine.Unspecified) emptyMap() else mapOf(FirstBaseline to y + (baseline * scale).roundToInt())
    layout(lw, lh, lines) {
        if (scale == 1f) {
            p.placeRelative(0, y)
        } else {
            // Shrunk about its start edge, so it keeps its place in the row (its end edge in a right-to-left row).
            val origin = TransformOrigin(if (layoutDirection == LayoutDirection.Ltr) 0f else 1f, 0f)
            p.placeRelativeWithLayer(0, y) {
                scaleX = scale
                scaleY = scale
                transformOrigin = origin
            }
        }
    }
}

/**
 * A source chip (§4.7, web results): a kiss-cut label with a link glyph; opens the URL. Pressed, focused and long
 * labels as [PicnicChip].
 */
@Composable
fun SourceChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource? = null) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChipFace(fill = Families.ramune.pastel, press = rememberPressFraction(pressed), focused = focused) {
            Glyph(PicnicGlyph.Link, null, tint = PicnicTheme.colors.ballpoint, size = 15.dp)
            WholeLabel(text, PicnicTheme.type.label.copy(color = PicnicTheme.colors.ballpoint), CHIP_LABEL_LINES)
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
 * Multi-line: rule lines continue under every line, one per text line: the pitch is the text's own line height, the
 * caller's [textStyle] line height when it sets one (the aisles text's 24 sp, a transcript's 22 sp) and
 * [RULED_LINE_PITCH] otherwise, so the rules scale with the text. [example] is the add bar's example line under the
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
    // The rules sit one per line: their pitch IS the text's line height (the caller's, else 24 sp), so both scale with
    // the font together and a rule never cuts through a line at a large font scale.
    val pitchSp = textStyle?.lineHeight?.takeIf { it.isSp } ?: RULED_LINE_PITCH
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
                // Tab leaves the field, a ruled note of many lines too (§15 Keyboard / switch access).
                .tabLeavesField()
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

/** A multi-line ruled field's line height and rule pitch when its text style sets none (§4.8: 24 sp, scaling with the font). */
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
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
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
    // No ripple (§0.2): the knob's move is the switch's feedback; focus wears the kit's ring round the pill.
    // A row that is the switch itself ([onCheckedChange] null: SwitchRow) passes its own [interactionSource]: the pill
    // then draws the row's focus, and is no second keyboard stop of its own (§15).
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .then(
                if (onCheckedChange == null) {
                    Modifier
                } else {
                    Modifier
                        .spaceToggles(checked, enabled, onCheckedChange)
                        .toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                },
            )
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 24.dp)
                .focusRing(shape, focusRingInk(), focused)
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
    // No ripple (§0.2): the star pressed onto the spot is the switch's feedback; focus wears the kit's ring round the spot.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .size(48.dp)
            .spaceToggles(checked, onValueChange = onCheckedChange)
            .toggleable(value = checked, interactionSource = interaction, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        StickerSpot(Modifier.focusRing(CircleShape, focusRingInk(), focused), size = 30.dp)
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

package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R

/*
 * THEME COMPONENT KIT: CONTRACT (signatures) between app-shell and app-screens (docs/ARCHITECTURE.md §11.2).
 * Placeholder picnic styling: ink outlines, flat fills, HARD offset shadows (never blur elevation),
 * stamped-toy buttons. The art pass changes the drawing, not the signatures.
 */

/**
 * Card/row container: fill + ink outline + hard offset shadow (no blur elevation). The shadow is
 * drawn outside the bounds (below/right), so leave [PicnicDimens.hardShadow] of room under it.
 * Gesture modifiers passed in [modifier] sit outside the clip; for a ripple that follows the
 * shape, clip first: `Modifier.clip(PicnicTheme.shapes.row).clickable { … }`.
 */
@Composable
fun PicnicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PicnicTheme.shapes.card,
    color: Color = PicnicTheme.colors.surface,
    outlined: Boolean = true,
    shadow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    Box(
        modifier
            .then(if (shadow) Modifier.hardShadow(shape, c.hardShadow, d.hardShadow) else Modifier)
            .clip(shape)
            .background(color)
            .then(if (outlined) Modifier.border(d.outline, c.outline, shape) else Modifier),
        content = content,
    )
}

enum class PicnicButtonStyle { Primary, Secondary, Text }

/**
 * A stamped toy button, >= 48 dp tall. Primary: strawberry pill on a darker base, with the palette's
 * cel highlight ([PicnicColors.buttonGloss]: a tapered lens centred under the top outline, clear of
 * the outline and the label; none when null); at dusk a ground-coloured halo keeps its pale face
 * off pastel bands. Secondary: surface pill on the shadow base; Text: ink label on a highlighter bar.
 * Pressing pushes the face down onto its base (the press feedback; no ripple).
 */
@Composable
fun PicnicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PicnicButtonStyle = PicnicButtonStyle.Primary,
    enabled: Boolean = true,
    glyph: PicnicGlyph? = null,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val shape = PicnicTheme.shapes.button
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val raised = enabled && style != PicnicButtonStyle.Text
    val depth = d.hardShadow.y + 1.dp
    val shift = if (pressed && raised) depth - 1.dp else 0.dp

    val fill = when {
        !enabled && style != PicnicButtonStyle.Text -> c.surfaceSunken
        style == PicnicButtonStyle.Primary -> c.primary
        style == PicnicButtonStyle.Secondary -> c.surface
        else -> Color.Transparent
    }
    val ink = when {
        !enabled -> c.inkFaint
        style == PicnicButtonStyle.Primary -> c.onPrimary
        else -> c.ink
    }
    val base = if (style == PicnicButtonStyle.Primary) c.primaryShadow else c.hardShadow
    val gloss = c.buttonGloss.takeIf { style == PicnicButtonStyle.Primary && enabled }
    val halo = raised && style == PicnicButtonStyle.Primary && c.isDark

    Box(
        modifier
            .defaultMinSize(minHeight = d.touchTarget)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(bottom = if (raised) depth else 0.dp),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        Row(
            Modifier
                .offset(y = shift)
                .then(if (halo) Modifier.halo(shape, c.background, HALO, below = depth - shift) else Modifier)
                .then(if (raised) Modifier.hardShadow(shape, base, DpOffset(0.dp, depth - shift)) else Modifier)
                .then(if (focused) Modifier.focusRing(shape, c.highlight) else Modifier)
                .clip(shape)
                .background(fill)
                .then(if (gloss != null) Modifier.celGloss(gloss, d.outline) else Modifier)
                .then(if (style != PicnicButtonStyle.Text) Modifier.border(d.outline, if (enabled) c.outline else c.inkFaint, shape) else Modifier)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (glyph != null) Glyph(glyph, contentDescription = null, tint = ink, size = 20.dp)
            Text(
                text,
                style = PicnicTheme.type.label,
                color = ink,
                modifier = if (style == PicnicButtonStyle.Text && enabled) Modifier.highlighter(c.highlight, underOnly = c.isDark) else Modifier,
            )
        }
    }
}

/** 48 dp round glyph button: a 40 dp outlined sticker disc. [emphasized] = primary-filled (send, mic). */
@Composable
fun PicnicIconButton(
    glyph: PicnicGlyph,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shift = if (pressed && enabled) 2.dp else 0.dp
    Box(
        modifier
            .size(d.touchTarget)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset(y = shift)
                .size(40.dp)
                .then(if (enabled) Modifier.hardShadow(CircleShape, if (emphasized) c.primaryShadow else c.hardShadow, DpOffset(0.dp, 3.dp - shift)) else Modifier)
                .then(if (focused) Modifier.focusRing(CircleShape, c.highlight) else Modifier)
                .clip(CircleShape)
                .background(if (emphasized && enabled) c.primary else c.surface)
                .border(d.outline, if (enabled) c.outline else c.inkFaint, CircleShape)
                .alpha(if (enabled) 1f else 0.6f),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                glyph,
                contentDescription,
                tint = if (!enabled) c.inkFaint else if (emphasized) c.onPrimary else c.ink,
                size = 22.dp,
            )
        }
    }
}

/** 32 dp pill with a 48 dp touch target. Selected: highlight fill and a leading tick (unless [leading] is given). */
@Composable
fun PicnicChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val shape = PicnicTheme.shapes.chip
    val on = stringResource(R.string.theme_switch_on)
    val off = stringResource(R.string.theme_switch_off)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .semantics { stateDescription = if (selected) on else off }
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 32.dp)
                .hardShadow(shape, c.hardShadow, DpOffset(0.dp, 2.dp))
                .clip(shape)
                .background(if (selected) c.highlight else c.surface)
                .border(d.outline, c.outline, shape)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                leading != null -> leading()
                selected -> Glyph(PicnicGlyph.Check, contentDescription = null, tint = c.onHighlight, size = 16.dp)
            }
            Text(text, style = PicnicTheme.type.label, color = if (selected) c.onHighlight else c.ink)
        }
    }
}

/**
 * All text input. Sunken fill, ink outline, a highlight focus ring outside the outline (no glow).
 * For the token field pass PasswordVisualTransformation and never pre-fill it.
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
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val d = PicnicTheme.dimens
    val shape = PicnicTheme.shapes.field
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(d.xs)) {
        // The label is drawn above the box but announced by the field itself ("Amount, 2, edit box"),
        // so it is cleared here and set as the field's content description below.
        if (label != null) Text(label, style = t.label, color = c.ink, modifier = Modifier.clearAndSetSemantics { })
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = t.body.copy(color = if (enabled) c.ink else c.inkFaint),
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .then(if (focused) Modifier.focusRing(shape, c.highlight) else Modifier)
                        .clip(shape)
                        .background(c.surfaceSunken)
                        .border(d.outline, if (isError) c.error else c.outline, shape)
                        .padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = t.body, color = c.inkFaint)
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        if (supportingText != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.xs)) {
                if (isError) Glyph(PicnicGlyph.Sparkle, contentDescription = null, tint = c.error, size = 12.dp)
                Text(supportingText, style = t.caption, color = if (isError) c.error else c.inkSoft)
            }
        }
    }
}

/**
 * Screen title card: a floating card (gutter margins) with a faint sunburst and two sparkles.
 * [bottom] is a slot for TripProgress or tabs. Applies no window insets itself.
 */
@Composable
fun PicnicTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    PicnicSurface(
        modifier
            .fillMaxWidth()
            .padding(start = d.gutter, end = d.gutter, top = d.s, bottom = d.s + d.hardShadow.y),
        color = c.surfaceRaised,
    ) {
        Canvas(Modifier.matchParentSize()) {
            // Faint sunburst from the top-right corner, plus two decorative sparkles.
            val origin = Offset(size.width * 0.86f, -size.height * 0.2f)
            val rays = 14
            for (i in 0 until rays step 2) {
                val a0 = Math.toRadians(90.0 + i * (180.0 / rays))
                val a1 = Math.toRadians(90.0 + (i + 1) * (180.0 / rays))
                val r = size.width * 1.2f
                drawPath(
                    path {
                        moveTo(origin.x, origin.y)
                        lineTo(origin.x + (r * kotlin.math.cos(a0)).toFloat(), origin.y + (r * kotlin.math.sin(a0)).toFloat())
                        lineTo(origin.x + (r * kotlin.math.cos(a1)).toFloat(), origin.y + (r * kotlin.math.sin(a1)).toFloat())
                        close()
                    },
                    c.backgroundPattern,
                )
            }
            drawPath(sparklePath(Offset(size.width - 18.dp.toPx(), 14.dp.toPx()), 6.dp.toPx()), c.highlight)
            drawPath(sparklePath(Offset(size.width - 40.dp.toPx(), 26.dp.toPx()), 3.5.dp.toPx()), c.primary)
        }
        Column(Modifier.padding(horizontal = d.m, vertical = d.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.xs)) {
                navigation?.invoke()
                Column(Modifier.weight(1f).padding(start = if (navigation == null) d.xs else 0.dp, top = d.xs, bottom = d.xs)) {
                    Text(title, style = PicnicTheme.type.screenTitle, color = c.ink)
                    if (subtitle != null) Text(subtitle, style = PicnicTheme.type.caption, color = c.inkSoft)
                }
                actions()
            }
            if (bottom != null) {
                Box(Modifier.padding(top = d.xs, bottom = d.xs)) { bottom() }
            }
        }
    }
}

/** Settings toggle: a drawn pill switch (thumb carries a star when on), 48 dp touch target. */
@Composable
fun PicnicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val motion = PicnicTheme.motion
    val thumbX by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = if (motion.reduced) snap() else tween(motion.shortMillis),
        label = "switchThumb",
    )
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .hardShadow(PicnicTheme.shapes.chip, c.hardShadow, DpOffset(0.dp, 2.dp))
                .clip(PicnicTheme.shapes.chip)
                .background(if (checked) c.primary else c.surfaceSunken)
                .border(d.outline, c.outline, PicnicTheme.shapes.chip),
        ) {
            Canvas(
                Modifier
                    .offset { IntOffset(thumbX.roundToPx(), 4.dp.roundToPx()) }
                    .size(24.dp),
            ) {
                drawCircle(if (checked) c.onPrimary else c.surface)
                drawCircle(c.outline, style = Stroke(d.outline.toPx() * 0.8f))
                if (checked) drawPath(starPath(center, size.minDimension * 0.3f), c.highlight)
            }
        }
    }
}

/** Use as SnackbarHost(hostState) { PicnicSnackbar(it) }: a speech bubble with a tail. */
@Composable
fun PicnicSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    PicnicSurface(
        modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.s),
        shape = SpeechBubbleShape,
        color = c.surfaceRaised,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp + SPEECH_TAIL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(data.visuals.message, style = PicnicTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
            val action = data.visuals.actionLabel
            if (action != null) {
                PicnicButton(action, onClick = { data.performAction() }, style = PicnicButtonStyle.Text)
            }
        }
    }
}

/** Drag handle for ModalBottomSheet(dragHandle = { SheetHandle() }): a little ribbon bow. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    Canvas(
        modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 16.dp),
    ) {
        val w = size.width
        val h = size.height
        val ink = c.outline
        val line = 1.6.dp.toPx()
        val left = path {
            moveTo(w / 2, h / 2); quadraticTo(w * 0.18f, -h * 0.05f, w * 0.06f, h * 0.3f)
            quadraticTo(w * 0.02f, h * 0.75f, w / 2, h / 2); close()
        }
        val right = path {
            moveTo(w / 2, h / 2); quadraticTo(w * 0.82f, -h * 0.05f, w * 0.94f, h * 0.3f)
            quadraticTo(w * 0.98f, h * 0.75f, w / 2, h / 2); close()
        }
        val tails = path {
            moveTo(w * 0.47f, h * 0.55f); lineTo(w * 0.36f, h); lineTo(w * 0.44f, h * 0.92f); close()
            moveTo(w * 0.53f, h * 0.55f); lineTo(w * 0.64f, h); lineTo(w * 0.56f, h * 0.92f); close()
        }
        fillAndInk(tails, c.primary, ink, line)
        fillAndInk(left, c.primary, ink, line)
        fillAndInk(right, c.primary, ink, line)
        drawCircle(c.primaryShadow, h * 0.17f, Offset(w / 2, h / 2))
        drawCircle(ink, h * 0.17f, Offset(w / 2, h / 2), style = Stroke(line))
    }
}

/* ---------------- internal helpers ---------------- */

/** Height of the snackbar bubble's tail. */
private val SPEECH_TAIL: Dp = 8.dp

/** Rounded bubble with a small tail at the bottom-left (body and tail unioned: no seam). */
private object SpeechBubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(density) { SPEECH_TAIL.toPx() }
        val bodyBottom = (size.height - tail).coerceAtLeast(1f)
        val r = with(density) { 18.dp.toPx() }.coerceAtMost(bodyBottom / 2f)
        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, bodyBottom, CornerRadius(r, r))) }
        val tip = Path().apply {
            moveTo(r + tail * 0.4f, bodyBottom - 2f)
            lineTo(r + tail * 0.2f, size.height)
            lineTo(r + tail * 2.2f, bodyBottom - 2f)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, tip))
    }
}

/**
 * Cel highlight on a button face: one tapered lens under the top outline (thickest at the centre,
 * running out to points), a clear [GLOSS_GAP] below the [outline] ink line and well above the label,
 * about half the face wide (capped, so a long button gets a highlight, not a rule). A shape of its
 * own on the face: it never touches the outline, so it can't read as a second edge or a stray stroke.
 * Drawn under the label and the outline.
 */
private fun Modifier.celGloss(color: Color, outline: Dp): Modifier = drawWithCache {
    val top = outline.toPx() + GLOSS_GAP.toPx()
    val t = GLOSS_THICKNESS.toPx()
    val w = (size.width * 0.5f).coerceIn(GLOSS_MIN.toPx(), GLOSS_MAX.toPx()).coerceAtMost(size.width - size.height)
    val left = (size.width - w) / 2f
    val right = left + w
    val cx = size.width / 2f
    // Ends dip slightly below the crown, like light lying along the curve of a tube.
    val end = top + t * 0.35f
    val lens = Path().apply {
        moveTo(left, end)
        quadraticTo(cx, top - t * 0.35f, right, end)
        quadraticTo(cx, top + t * 1.65f, left, end)
        close()
    }
    onDrawWithContent {
        if (w > t * 4) drawPath(lens, color)
        drawContent()
    }
}

private val GLOSS_GAP: Dp = 2.dp
/** Thickness of the lens at its centre. */
private val GLOSS_THICKNESS: Dp = 4.5.dp
private val GLOSS_MIN: Dp = 24.dp
private val GLOSS_MAX: Dp = 96.dp

/**
 * A [width] ring of [color] round the face and its base ([below] = how far the base shows under the
 * face): a die-cut edge that separates a pale face from whatever pastel it floats over.
 */
private fun Modifier.halo(shape: Shape, color: Color, width: Dp, below: Dp): Modifier = drawBehind {
    val g = width.toPx()
    val outline = shape.createOutline(Size(size.width + g * 2, size.height + below.toPx() + g * 2), layoutDirection, this)
    translate(-g, -g) { drawOutline(outline, color) }
}

/** Width of the dusk halo round Primary buttons. */
private val HALO: Dp = 2.5.dp

/** A 3 dp ring outside the shape (keyboard focus): flat, never a glow. */
private fun Modifier.focusRing(shape: Shape, color: Color): Modifier = drawBehind {
    val grow = 3.dp.toPx()
    val outline = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
    translate(-grow, -grow) { drawOutline(outline, color) }
}

/**
 * Highlighter bar behind the lower half of a text button's label (ink on lemon). [underOnly] draws
 * it as an underline below the glyphs instead (dusk: light text on lemon would be unreadable).
 */
private fun Modifier.highlighter(color: Color, underOnly: Boolean): Modifier = drawBehind {
    val h = if (underOnly) 3.dp.toPx() else size.height * 0.42f
    val top = if (underOnly) size.height else size.height - h
    drawRoundRect(color, Offset(-2.dp.toPx(), top), Size(size.width + 4.dp.toPx(), h), CornerRadius(2.dp.toPx()))
}

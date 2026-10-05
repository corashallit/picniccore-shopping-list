package earth.cora.picnic.ui.a11y

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.launch

/*
 * The kit's focus ring as an indication (docs/DESIGN_STICKER.md §4.6 "Focused: a 2 dp ballpoint focus ring 3 dp outside
 * the 48 dp target", §15). Owner: WP10.
 */

/**
 * The ink a focus ring is drawn in where it is (§4.6: the page's ink): provided by a surface whose paper is not the
 * page's (a light card on the navy Glow page wants the ballpoint ring, a navy pad the white one); null on the page.
 */
val LocalFocusRingInk = compositionLocalOf<Color?> { null }

/** The focus ring's ink here: the surface's ([LocalFocusRingInk]), else the page's. */
@Composable
@ReadOnlyComposable
fun focusRingInk(): Color = LocalFocusRingInk.current ?: PicnicTheme.colors.ink

/** The ring's shape where a target has no shape of its own to follow: a rounded card. */
val FocusRingShape: Shape = RoundedCornerShape(10.dp)

/**
 * The focus ring as a clickable's `indication`, for a target that draws its own pressed state (or none) and so would
 * pass null: a 2 dp ring 3 dp outside [shape], in the page's ink (ballpoint in Day, white in Glow), while the target has
 * focus from the keyboard (or a switch acting as one); never after a tap.
 */
@Composable
fun focusRingIndication(shape: Shape = FocusRingShape): IndicationNodeFactory {
    val ink = focusRingInk()
    return remember(shape, ink) { FocusRingIndication(shape, ink) }
}

private class FocusRingIndication(private val shape: Shape, private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): androidx.compose.ui.node.DelegatableNode = FocusRingNode(interactionSource, shape, color)

    override fun equals(other: Any?): Boolean = other is FocusRingIndication && other.shape == shape && other.color == color

    override fun hashCode(): Int = 31 * shape.hashCode() + color.hashCode()
}

/** Draws the focus ring round its node while [source] has focus from the keyboard. */
private class FocusRingNode(private val source: InteractionSource, private val shape: Shape, private val color: Color) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            val focuses = mutableListOf<FocusInteraction.Focus>()
            source.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focuses += interaction
                    is FocusInteraction.Unfocus -> focuses -= interaction.focus
                    else -> return@collect
                }
                focused = focuses.isNotEmpty()
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused && currentValueOf(LocalInputModeManager).inputMode == InputMode.Keyboard) drawFocusRing(shape, color)
    }
}

/** The kit's focus ring round this scope's bounds: 2 dp of [color], 3 dp outside [shape] (other indications draw it too). */
fun DrawScope.drawFocusRing(shape: Shape, color: Color) {
    val gap = 3.dp.toPx()
    val w = 2.dp.toPx()
    val grow = gap + w / 2f
    val outline = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
    translate(-grow, -grow) { drawOutline(outline, color, style = Stroke(w)) }
}

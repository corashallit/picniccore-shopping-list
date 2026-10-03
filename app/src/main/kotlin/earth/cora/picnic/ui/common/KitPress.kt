package earth.cora.picnic.ui.common

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.launch

/*
 * Press feedback for WP8's own tap targets (docs/DESIGN_STICKER.md §0.2 "no Material default survives", checklist 235).
 * PicnicTheme wraps a MaterialTheme whose LocalIndication is Material's ripple, so:
 * - every clickable, selectable, toggleable or combinedClickable in WP8's files names its indication: [highlighterPress]
 *   for a text row (the §4.11 highlighter band behind the row while it is pressed), or null where the target draws its
 *   own state (a pocket's lift, a swatch's ring, a tab's raise, a sticker cell's lemon ring). NoMaterialDefaultsTest scans
 *   for calls that don't;
 * - each WP8 screen and sheet root runs its content under [NoMaterialPress], so a kit piece that still falls back on
 *   LocalIndication (the label chip, §4.7) shows no ripple there either.
 */

/** An indication that draws nothing: what a press looks like where the target shows no pressed state of its own. */
object NoPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Runs [content] with [NoPressIndication] as the default indication, never Material's ripple. */
@Composable
fun NoMaterialPress(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIndication provides NoPressIndication, content = content)
}

/**
 * The kit's pressed state for a whole text row (§4.6 text button, §4.11 row): while pressed, a highlighter band
 * (`pressedBand`: `#FFF3A6` in Day, `#3A438A` in Glow) lies behind the row's content, 3 dp in from its top and bottom,
 * rounded like a marker stroke (3, 10, 4, 12 dp) and turned −0.6°. Pass it as the `indication` of the row's clickable,
 * placed after the row's own background so the band sits on the paper and under the text.
 */
@Composable
fun highlighterPress(): IndicationNodeFactory {
    val color = PicnicTheme.colors.pressedBand
    return remember(color) { HighlighterIndication(color) }
}

private class HighlighterIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = HighlighterNode(interactionSource, color)

    override fun equals(other: Any?): Boolean = other is HighlighterIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

private class HighlighterNode(private val source: InteractionSource, private val color: Color) : Modifier.Node(), DrawModifierNode {
    private val presses = mutableListOf<PressInteraction.Press>()

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    else -> return@collect
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (presses.isNotEmpty()) {
            val inset = 3.dp.toPx()
            val band = RoundRect(
                left = 0f,
                top = inset,
                right = size.width,
                bottom = size.height - inset,
                topLeftCornerRadius = CornerRadius(3.dp.toPx()),
                topRightCornerRadius = CornerRadius(10.dp.toPx()),
                bottomRightCornerRadius = CornerRadius(4.dp.toPx()),
                bottomLeftCornerRadius = CornerRadius(12.dp.toPx()),
            )
            rotate(-0.6f) { drawPath(Path().apply { addRoundRect(band) }, color) }
        }
        drawContent()
    }
}

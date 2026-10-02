package earth.cora.picnic.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * Puffy lettering (§2.7, checklist 23): the [stroke]-wide stroke pass first (white; Glow `puffyStroke`), then
 * the [fill], on a hard shadow. Screen titles, the wordmark, the Next card. Sets the TextOverflowed semantics
 * key (ui/a11y) when the text does not fit (NoClippedTextTest).
 * PHASE-0 STUB (WP4): the v1 outlined label in white, without the shadow or the overflow key.
 */
@Composable
fun PuffyText(text: String, style: TextStyle, fill: Color, stroke: Dp, modifier: Modifier = Modifier) {
    OutlinedLabel(text = text, style = style, fill = fill, stroke = Color.White, modifier = modifier, strokeWidth = stroke)
}

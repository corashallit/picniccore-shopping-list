package earth.cora.picnic.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.StickerFamily
import earth.cora.picnic.ui.theme.drawDashedPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.theme.FlatStar
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.LocalPicnicColors
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicTheme

/*
 * Small pieces shared by WP8's screens, composed only from the theme kit (§4).
 */

/**
 * A text button in hanko colour (§6.3 "Delete", §6.8 "Remove key"): the kit's text button with its label inked in the
 * variant's hanko (5.34 : 1 on the Day pad, 5.94 : 1 on the Glow page), wavy sakura underline unchanged.
 */
@Composable
fun HankoTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: PicnicGlyph? = null, enabled: Boolean = true) {
    val c = PicnicTheme.colors
    CompositionLocalProvider(LocalPicnicColors provides c.copy(ink = c.hanko)) {
        PicnicButton(text, onClick = onClick, modifier = modifier, style = PicnicButtonStyle.Text, glyph = glyph, enabled = enabled)
    }
}

/** A line of explanatory text led by a small flat lemon star (decorative), as in "About your key". */
@Composable
fun StarLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlatStar(Modifier.padding(top = 4.dp).size(13.dp))
        Text(text, style = PicnicTheme.type.body.copy(color = PicnicTheme.colors.ink), modifier = Modifier.weight(1f))
    }
}

/** A tappable row that opens another screen: [title] over its [hint], a chevron at the right. One Role.Button node. */
@Composable
fun LinkRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, hint: String? = null, glyph: PicnicGlyph? = null) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(interactionSource = null, indication = highlighterPress(), role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (glyph != null) Glyph(glyph, contentDescription = null, tint = c.ink, size = 22.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = t.fieldValue.copy(color = c.ink))
            if (hint != null) Text(hint, style = t.caption.copy(color = c.inkSoft))
        }
        Chevron()
    }
}

/** A right-pointing chevron (the Back glyph turned round), decorative. */
@Composable
fun Chevron(modifier: Modifier = Modifier) {
    Glyph(PicnicGlyph.Back, contentDescription = null, tint = PicnicTheme.colors.inkSoft, size = 20.dp, modifier = modifier.graphicsLayer { rotationZ = 180f })
}

/**
 * The dashed "add" sticker spot (§4.26 "Add aisle", §6.4 "New list"): an empty kiss-cut spot, white at 50 % on its
 * page, a 2 dp dashed periwinkle-trace outline (radius 10, exact dashes, DashSpec), a plus and the [text]. One button.
 */
@Composable
fun DashedAddSpot(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, onBacking: Boolean = false) {
    val c = PicnicTheme.colors
    // On the sticker-sheet backing (light in every variant) the spot keeps its Day inks.
    val dark = c.isDark && !onBacking
    val dash = if (dark) Families.periwinkle.mid else Families.periwinkle.trace
    val ink = if (onBacking) c.ballpoint else c.ink
    val white = c.stickerWhite
    Row(
        modifier
            .heightIn(min = 48.dp)
            .drawBehind {
                val inset = 1.dp.toPx()
                val r = 10.dp.toPx()
                val rect = RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(r))
                drawRoundRect(white.copy(alpha = if (dark) 0.08f else 0.5f), Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(r))
                drawDashedPath(Path().apply { addRoundRect(rect) }, dash, 2.dp.toPx(), 5.dp.toPx(), 3.5.dp.toPx())
            }
            // After the spot's paper, so the pressed highlighter lies on it, under the plus and the words.
            .clickable(interactionSource = null, indication = highlighterPress(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Glyph(PicnicGlyph.Add, contentDescription = null, tint = ink, size = 20.dp)
        Text(text, style = PicnicTheme.type.label.copy(color = ink, fontSize = 15.sp))
    }
}

/**
 * A family's ink for a decorative line glyph on a page: the trace on the Day pad, the mid on the Glow page (where the
 * traces fall to 1.7-2.8 : 1 and the mids reach 5-10 : 1, as the stamp pictograms do, §4.13).
 */
@Composable
fun accentInk(family: StickerFamily): Color = if (PicnicTheme.colors.isDark) family.mid else family.trace

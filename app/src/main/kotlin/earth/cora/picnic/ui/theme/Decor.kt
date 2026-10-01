package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * DECORATIVE SLOTS: CONTRACT (signatures) between app-shell and app-screens.
 * Screens place these; the art pass redraws them (gingham, bunting, medallions, star check,
 * episode ribbon, sleepy-bee empty state, sparkle loader...). All decoration is
 * semantics-free unless a parameter says otherwise. Bodies here are skeleton placeholders.
 */

/** Full-bleed screen ground (pattern). Draws under the system bars; content handles insets. */
@Composable
fun PicnicBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(PicnicTheme.colors.background), content = content)
}

/** Divider between list regions. [major] = between top-level sections (bunting), else a dashed line. */
@Composable
fun SectionDivider(modifier: Modifier = Modifier, major: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = if (major) 8.dp else 4.dp)
            .height(if (major) 3.dp else 1.dp)
            .background(PicnicTheme.colors.inkFaint),
    )
}

/**
 * Aisle header ornament: numbered medallion in the aisle's rotation colour (+ pattern later).
 * [number] null = special section (Unsorted / In the basket). [aisleIndex] = position - 1, or -1.
 */
@Composable
fun AisleMedallion(number: Int?, aisleIndex: Int, modifier: Modifier = Modifier, size: Dp = PicnicTheme.dimens.medallion) {
    val colors = PicnicTheme.colors.aisle(aisleIndex)
    Box(
        modifier
            .size(size)
            .clip(PicnicTheme.shapes.medallion)
            .background(colors.base)
            .border(PicnicTheme.dimens.outline, PicnicTheme.colors.outline, PicnicTheme.shapes.medallion),
        contentAlignment = Alignment.Center,
    ) {
        Text(number?.toString() ?: "?", style = PicnicTheme.type.aisleNumber, color = PicnicTheme.colors.onPrimary)
    }
}

/** The check control's visual (unchecked circle / checked star). 32-40 dp; the row provides the touch target. */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    Box(
        modifier
            .size(32.dp)
            .clip(PicnicTheme.shapes.badge)
            .background(if (checked) c.highlight else c.surface)
            .border(PicnicTheme.dimens.outline, c.outline, PicnicTheme.shapes.badge),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Glyph(PicnicGlyph.Check, contentDescription = null, tint = c.onHighlight, size = 20.dp)
    }
}

/** Trip progress metaphor (episode ribbon / charm bracelet / stamp card). Announces "done of total". */
@Composable
fun TripProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val fraction = if (total == 0) 0f else done.toFloat() / total
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(PicnicTheme.shapes.chip)
            .background(c.surfaceSunken),
    ) {
        Box(Modifier.fillMaxWidth(fraction).height(10.dp).background(c.primary))
    }
}

/** Empty list / empty screen illustration with title, body and optional action. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.padding(PicnicTheme.dimens.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PicnicTheme.dimens.m),
    ) {
        AisleBasket(kind = earth.cora.picnic.core.model.BasketKind.DEFAULT, miniIcons = emptyList(), count = 0, size = 96.dp)
        Text(title, style = PicnicTheme.type.screenTitle, color = PicnicTheme.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = PicnicTheme.type.body, color = PicnicTheme.colors.inkSoft, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** "The assistant is thinking" indicator (bee loader / firefly / sparkle sweep). [label] is announced. */
@Composable
fun LoadingSparkle(modifier: Modifier = Modifier, label: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Glyph(PicnicGlyph.Sparkle, contentDescription = null, tint = PicnicTheme.colors.highlight)
        if (label != null) Text(label, style = PicnicTheme.type.body, color = PicnicTheme.colors.inkSoft)
    }
}

/** Small sparkle badge overlaid on an ItemIcon while its bespoke icon is being drawn. */
@Composable
fun DrawingSparkle(modifier: Modifier = Modifier) {
    Glyph(PicnicGlyph.Sparkle, contentDescription = null, modifier = modifier, tint = PicnicTheme.colors.highlight, size = 14.dp)
}

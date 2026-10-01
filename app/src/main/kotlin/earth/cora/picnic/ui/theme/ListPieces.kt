package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef

/*
 * The two list building blocks the art pass cares most about. CONTRACT (signatures) between
 * app-shell (visuals) and app-screens (wiring: click, swipe, long-press, lazy list keys).
 */

/**
 * Aisle section header (one toggle target). Expanded: patterned medallion + title. Collapsed: the
 * AisleBasket with [miniIcons] peeking out and its count badge. Cleared: a "CLEAR!" star stamp.
 * Exposes "<title>, <count> items" with state "collapsed"/"expanded" to accessibility.
 */
@Composable
fun AisleHeader(
    title: String,
    /** Walking position for aisles; null for Unsorted / In the basket. */
    number: Int?,
    /** position - 1 for aisles; -1 for special sections. */
    aisleIndex: Int,
    basket: BasketKind,
    miniIcons: List<IconRef>,
    /** Unchecked items in the section (checked count for the checked section). */
    count: Int,
    collapsed: Boolean,
    /** Every item in this aisle is checked. */
    cleared: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val family = c.aisle(aisleIndex)
    val description = pluralStringResource(R.plurals.theme_aisle_header, count, title, count)
    val state = stringResource(if (collapsed) R.string.theme_state_collapsed else R.string.theme_state_expanded)
    val clearedText = stringResource(R.string.theme_aisle_cleared)
    Box(modifier.fillMaxWidth()) {
        PicnicSurface(Modifier.fillMaxWidth(), color = family.pastel) {
            // Halftone accent on the right: decoration only.
            Canvas(Modifier.matchParentSize()) {
                val step = 9.dp.toPx()
                var y = step / 2
                var row = 0
                while (y < size.height) {
                    var x = size.width * 0.55f + if (row % 2 == 0) 0f else step / 2
                    while (x < size.width) {
                        val t = (x - size.width * 0.55f) / (size.width * 0.45f)
                        drawCircle(family.base.copy(alpha = 0.22f), 1.dp.toPx() + 1.6.dp.toPx() * t, Offset(x, y))
                        x += step
                    }
                    y += step
                    row++
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = d.touchTarget + d.s)
                    .clickable(onClick = onToggle)
                    .clearAndSetSemantics {
                        contentDescription = if (cleared) "$description, $clearedText" else description
                        stateDescription = state
                        role = Role.Button
                        onClick { onToggle(); true }
                    }
                    .padding(horizontal = d.m, vertical = d.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.m),
            ) {
                if (collapsed) {
                    AisleBasket(basket, miniIcons, count)
                } else {
                    AisleMedallion(number, aisleIndex)
                }
                // Text and chevron sit on the pastel fill, which stays light at dusk too: always the
                // family's deep ink (TokensContrastTest checks deep/pastel for every family).
                Column(Modifier.weight(1f)) {
                    Text(title, style = PicnicTheme.type.aisleTitle, color = family.deep)
                    if (cleared) Text(clearedText, style = PicnicTheme.type.caption, color = family.deep)
                }
                if (!collapsed && count > 0) CountPill(count)
                Glyph(if (collapsed) PicnicGlyph.Expand else PicnicGlyph.Collapse, contentDescription = null, tint = family.deep)
            }
        }
        if (cleared) ClearedStamp(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-10).dp))
    }
}

/** "CLEAR!" lemon star stamp, rotated: decorative (the header's description says it). */
@Composable
private fun ClearedStamp(modifier: Modifier) {
    val c = PicnicTheme.colors
    val lemon = c.aisle(2)
    Box(modifier.size(48.dp).rotate(-10f).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val star = starPath(center, size.minDimension / 2f, size.minDimension * 0.34f, points = 8, rotationDeg = -90f)
            drawPath(star, lemon.base)
            drawPath(star, c.outline, style = Stroke(1.6.dp.toPx()))
        }
        Text(stringResource(R.string.theme_aisle_cleared_stamp), style = PicnicTheme.type.counter.copy(fontSize = PicnicTheme.type.counter.fontSize * 0.75f), color = Art.ink)
    }
}

/**
 * Visual content of one item row (no gestures: the screen adds click/swipe/long-press around it;
 * clip with PicnicTheme.shapes.row before `clickable` so the ripple follows the card).
 * Checked state = star mark + strike-through + softer ink (never colour alone).
 */
@Composable
fun ItemRowCard(
    name: String,
    /** "2 · ripe ones": quantity and note, already joined by the screen; null hides the line. */
    detail: String?,
    icon: IconRef,
    checked: Boolean,
    modifier: Modifier = Modifier,
    drawing: Boolean = false,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val d = PicnicTheme.dimens
    PicnicSurface(modifier.fillMaxWidth(), shape = PicnicTheme.shapes.row, color = if (checked) c.surfaceSunken else c.surface) {
        Row(
            Modifier
                .defaultMinSize(minHeight = d.rowMinHeight)
                .padding(start = d.s, end = d.s, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.m),
        ) {
            ItemIcon(icon, contentDescription = null, drawing = drawing)
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = t.itemName.copy(textDecoration = if (checked) TextDecoration.LineThrough else null),
                    color = if (checked) c.inkSoft else c.ink,
                )
                if (detail != null) Text(detail, style = t.detail, color = c.inkSoft)
            }
            CheckMark(checked)
        }
    }
}

/** Count pill on an expanded header (a collapsed one shows the basket badge instead). */
@Composable
private fun CountPill(count: Int) {
    val c = PicnicTheme.colors
    Box(
        Modifier
            .clip(PicnicTheme.shapes.badge)
            .background(c.surface)
            .border(1.5.dp, c.outline, PicnicTheme.shapes.badge)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(count.toString(), style = PicnicTheme.type.counter, color = c.ink)
    }
}

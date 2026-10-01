package earth.cora.picnic.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef

/*
 * The two list building blocks the art pass cares most about. CONTRACT (signatures) between
 * app-shell (visuals) and app-screens (wiring: click, swipe, long-press, lazy list keys).
 */

/**
 * Aisle section header. Expanded: medallion + title (+ count). Collapsed: the AisleBasket with
 * [miniIcons] peeking out and the count badge. The whole header is one toggle target; it exposes
 * "<title>, <count> items, collapsed|expanded" to accessibility.
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
    PicnicSurface(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $count items"
                stateDescription = if (collapsed) "collapsed" else "expanded"
            }
            .clickable(role = Role.Button, onClick = onToggle),
        color = c.aisle(aisleIndex).pastel,
    ) {
        Row(
            Modifier.padding(PicnicTheme.dimens.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PicnicTheme.dimens.m),
        ) {
            if (collapsed) {
                AisleBasket(basket, miniIcons, count)
            } else {
                AisleMedallion(number, aisleIndex)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = PicnicTheme.type.aisleTitle, color = c.aisle(aisleIndex).deep)
                if (cleared) Text("All picked up!", style = PicnicTheme.type.caption, color = c.inkSoft)
            }
            Glyph(if (collapsed) PicnicGlyph.Expand else PicnicGlyph.Collapse, contentDescription = null)
        }
    }
}

/**
 * Visual content of one item row (no gestures: the screen adds click/swipe/long-press around it).
 * Checked state = mark + strike-through + softer ink (never colour alone).
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
    PicnicSurface(modifier.fillMaxWidth(), shape = PicnicTheme.shapes.row) {
        Row(
            Modifier
                .defaultMinSize(minHeight = PicnicTheme.dimens.rowMinHeight)
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PicnicTheme.dimens.m),
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

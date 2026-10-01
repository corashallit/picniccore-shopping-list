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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef

/*
 * The list building blocks the art pass cares most about. CONTRACT (signatures) between
 * app-shell (visuals) and app-screens (wiring: click, swipe, long-press, lazy list keys).
 *
 * AISLE SECTION: an aisle and its rows read as ONE container: the header is the container's top
 * band (tinted with the aisle's pastel), the rows sit on a lighter inner surface inside a tray of
 * that same tint, divided by thin rules, and the last row closes the container (rounded cap, tray
 * margin, hard shadow). It is drawn slice by slice, one slice per lazy-list item, so a section may
 * cross the viewport edge and every row keeps its own key:
 *
 *   AisleHeader(…, rowsBelow = true)               top band (rounded top, open bottom)
 *   AisleSectionRow(aisleIndex, first, last) { … }  one row (open top/bottom; the last one is the cap)
 *
 * Collapsed or empty sections are just the band: AisleHeader(…, rowsBelow = false) is a closed card.
 * Slices must touch (no list spacing between them); put PicnicDimens.sectionGap between sections.
 */

/**
 * Aisle section header (one toggle target). Expanded: patterned medallion + title. Collapsed: the
 * AisleBasket with [miniIcons] peeking out and its count badge. Cleared: a "CLEAR!" star stamp.
 * Exposes "<title>, <count> items" with state "collapsed"/"expanded" to accessibility.
 * [rowsBelow] = this section's AisleSectionRows follow (expanded, not empty): the header is drawn as
 * the open top band of the section container; otherwise it is a closed card on its own.
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
    rowsBelow: Boolean = false,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val family = c.aisle(aisleIndex)
    val description = pluralStringResource(R.plurals.theme_aisle_header, count, title, count)
    val state = stringResource(if (collapsed) R.string.theme_state_collapsed else R.string.theme_state_expanded)
    val clearedText = stringResource(R.string.theme_aisle_cleared)
    Box(modifier.fillMaxWidth()) {
        SectionBand(aisleIndex, open = rowsBelow, halftone = true) { bandShape ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = d.touchTarget + d.s)
                    .clip(bandShape)
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

/**
 * A plain (non-interactive) section top band: small medallion + title, announced as a heading.
 * For read-only groupings such as the Add sheet's preview; follow it with AisleSectionRows.
 */
@Composable
fun AisleSectionTitle(title: String, number: Int?, aisleIndex: Int, modifier: Modifier = Modifier) {
    val d = PicnicTheme.dimens
    val family = PicnicTheme.colors.aisle(aisleIndex)
    SectionBand(aisleIndex, open = true, halftone = false, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = d.m, vertical = d.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.s),
        ) {
            AisleMedallion(number = number, aisleIndex = aisleIndex, size = 28.dp)
            Text(
                title,
                style = PicnicTheme.type.label,
                color = family.deep,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

/**
 * One row's slice of its aisle section container (see the file comment). Draws the tinted tray
 * walls, the lighter inner surface, a thin rule above every row but the [first], and on the [last]
 * row the section's bottom cap (rounded corners, tray margin, hard shadow below it). [content] is
 * clipped to the inner surface, so a swiped row slides out under the walls and a swipe background
 * fills exactly the row's slot. Gestures and keys stay with the screen.
 */
@Composable
fun AisleSectionRow(
    /** position - 1 for aisles; -1 for special sections (same as the section's AisleHeader). */
    aisleIndex: Int,
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val shapes = PicnicTheme.shapes
    val family = c.aisle(aisleIndex)
    val outer = shapes.section
    val inner = shapes.sectionInner
    Box(
        modifier
            .fillMaxWidth()
            .drawWithCache {
                val stroke = d.outline.toPx()
                val rule = d.rule.toPx()
                val inset = d.sectionInset.toPx()
                val shadowDy = d.hardShadow.y.toPx()
                val indent = d.m.toPx()
                val outerPath = slicePath(outer, top = false, bottom = last, Rect(Offset.Zero, size))
                val innerPath = slicePath(inner, top = first, bottom = last, Rect(inset, 0f, size.width - inset, if (last) size.height - inset else size.height))
                onDrawWithContent {
                    if (last) drawHardShadow(outerPath, c.hardShadow, shadowDy)
                    clipRect(0f, 0f, size.width, size.height) {
                        drawPath(outerPath, family.pastel)
                        drawPath(innerPath, c.surface)
                        inkInside(innerPath, family.base, rule)
                        inkInside(outerPath, c.outline, stroke)
                    }
                    drawContent()
                    // Rules stay put over the rows (a swiped row slides under them).
                    if (!first) drawRect(family.base.copy(alpha = RULE_ALPHA), Offset(inset + indent, 0f), Size(size.width - 2 * (inset + indent), rule))
                }
            }
            .padding(
                start = d.sectionInset + d.rule,
                end = d.sectionInset + d.rule,
                top = if (first) d.rule else 0.dp,
                bottom = if (last) d.sectionInset + d.rule else 0.dp,
            )
            .clip(inner.slice(top = first, bottom = last)),
    ) {
        content()
    }
}

/**
 * Visual content of one item row, laid on its section's inner surface (wrap it in AisleSectionRow;
 * no gestures: the screen adds click/swipe/long-press around it). Opaque, so a swipe background
 * stays hidden until the row moves. Checked state = filled CheckMark + strike-through + softer ink
 * (never colour alone).
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
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surface)
            .defaultMinSize(minHeight = d.rowMinHeight)
            .padding(start = d.s, end = d.xs, top = 6.dp, bottom = 6.dp),
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

/* ---------------- section drawing (internal) ---------------- */

/**
 * The section's top band: pastel fill (+ halftone accent), ink outline; [open] = rows follow, so the
 * bottom edge is left open for the tray; closed = a whole card with its hard shadow. [content] gets
 * the band's shape (for clipping a ripple).
 */
@Composable
private fun SectionBand(
    aisleIndex: Int,
    open: Boolean,
    halftone: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (bandShape: CornerBasedShape) -> Unit,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val family = c.aisle(aisleIndex)
    val shape = PicnicTheme.shapes.section
    Box(
        modifier
            .fillMaxWidth()
            .drawWithCache {
                val stroke = d.outline.toPx()
                val shadowDy = d.hardShadow.y.toPx()
                val path = slicePath(shape, top = true, bottom = !open, Rect(Offset.Zero, size))
                onDrawBehind {
                    if (!open) drawHardShadow(path, c.hardShadow, shadowDy)
                    clipRect(0f, 0f, size.width, size.height) {
                        drawPath(path, family.pastel)
                        if (halftone) clipPath(path) { drawHalftone(family.base.copy(alpha = 0.22f)) }
                        inkInside(path, c.outline, stroke)
                    }
                }
            },
    ) {
        content(shape.slice(top = true, bottom = !open))
    }
}

/** [this] shape with the corners on its open (shared) edges squared off. */
internal fun CornerBasedShape.slice(top: Boolean, bottom: Boolean): CornerBasedShape = copy(
    topStart = if (top) topStart else ZeroCornerSize,
    topEnd = if (top) topEnd else ZeroCornerSize,
    bottomEnd = if (bottom) bottomEnd else ZeroCornerSize,
    bottomStart = if (bottom) bottomStart else ZeroCornerSize,
)

/**
 * Outline path of one slice of [shape] over [rect]. Open edges run [OVERHANG_DP] past the rect, so
 * once the caller clips to its bounds the outline continues seamlessly into the neighbouring slice.
 */
private fun CacheDrawScope.slicePath(shape: CornerBasedShape, top: Boolean, bottom: Boolean, rect: Rect): Path {
    val over = OVERHANG_DP.dp.toPx()
    val t = if (top) rect.top else rect.top - over
    val b = if (bottom) rect.bottom else rect.bottom + over
    val outline = shape.slice(top, bottom).createOutline(Size(rect.width, b - t), layoutDirection, this)
    return Path().apply {
        addOutline(outline)
        translate(Offset(rect.left, t))
    }
}

/** Ink line of [width] just inside [path] (border-style: never spills outside the fill). */
private fun DrawScope.inkInside(path: Path, color: Color, width: Float) {
    clipPath(path) { drawPath(path, color, style = Stroke(width * 2)) }
}

/** Hard offset shadow under a closing slice, never above its own top (that belongs to the slice before). */
private fun DrawScope.drawHardShadow(path: Path, color: Color, dy: Float) {
    clipRect(0f, 0f, size.width, size.height + dy) {
        translate(0f, dy) { drawPath(path, color) }
    }
}

/** Halftone accent on the right of a band: decoration only. */
private fun DrawScope.drawHalftone(color: Color) {
    val step = 9.dp.toPx()
    var y = step / 2
    var row = 0
    while (y < size.height) {
        var x = size.width * 0.55f + if (row % 2 == 0) 0f else step / 2
        while (x < size.width) {
            val t = (x - size.width * 0.55f) / (size.width * 0.45f)
            drawCircle(color, 1.dp.toPx() + 1.6.dp.toPx() * t, Offset(x, y))
            x += step
        }
        y += step
        row++
    }
}

private const val OVERHANG_DP = 8

/** Rules between rows: the aisle's base colour, softened so item names stay the strongest line. */
private const val RULE_ALPHA = 0.5f

/* ---------------- small pieces ---------------- */

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

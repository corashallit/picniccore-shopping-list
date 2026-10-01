package earth.cora.picnic.ui.theme

// TEMPORARY prototype (ui-feedback): three aisle-section groupings, checkbox and button-gloss
// variants, rendered by ProtoSectionsTest into the scratchpad. Delete before finishing.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef

internal enum class ProtoGrouping { BandRules, Tray, Spine }
internal enum class ProtoCheck { Circle, Square, Star }
internal enum class ProtoGloss { None, Rim, Band }

private data class ProtoRow(val name: String, val detail: String?, val icon: String, val checked: Boolean = false)
private data class ProtoSection(
    val title: String, val number: Int?, val index: Int, val basket: BasketKind, val rows: List<ProtoRow>,
    val collapsed: Boolean = false, val cleared: Boolean = false,
)

private val protoSections = listOf(
    ProtoSection("Unsorted", null, -1, BasketKind.DEFAULT, listOf(ProtoRow("Dish rack", null, "paper_bag"), ProtoRow("Birthday candles", null, "flower"))),
    ProtoSection("Produce", null, 0, BasketKind.FRUIT_CRATE, listOf(ProtoRow("Lemons", "2", "lemon"), ProtoRow("Baby spinach", "1 clamshell", "carrot"), ProtoRow("Apples", null, "apple")), collapsed = true),
    ProtoSection("Bakery", null, 1, BasketKind.BREAD_BASKET, emptyList(), cleared = true),
    ProtoSection("Aisle 2 - Baking & spices", 2, 5, BasketKind.JAR_SHELF, listOf(ProtoRow("All-purpose flour", "2 kg", "flour"), ProtoRow("Baking powder", null, "baking_generic"), ProtoRow("Gochujang", "500 g tub · for the bibimbap", "condiments_generic"))),
    ProtoSection("In the basket", null, -1, BasketKind.DEFAULT, listOf(ProtoRow("Milk", "4 L bag", "milk_bag", true), ProtoRow("Eggs", "dozen", "eggs", true))),
)

/** One prototype list: every section drawn slice by slice (as lazy items would be). */
@Composable
internal fun ProtoList(grouping: ProtoGrouping, check: ProtoCheck, modifier: Modifier = Modifier) {
    PicnicBackground(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Grouping: $grouping · check: $check", style = PicnicTheme.type.caption, color = PicnicTheme.colors.inkSoft)
            Spacer(Modifier.height(6.dp))
            protoSections.forEachIndexed { i, s ->
                if (i > 0) Spacer(Modifier.height(16.dp))
                val open = !s.collapsed && s.rows.isNotEmpty()
                ProtoHeader(s, grouping, open)
                if (open) s.rows.forEachIndexed { r, row ->
                    ProtoRowSlice(grouping, s.index, first = r == 0, last = r == s.rows.lastIndex) {
                        ProtoRowContent(row, check)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtoHeader(s: ProtoSection, grouping: ProtoGrouping, open: Boolean) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val family = c.aisle(s.index)
    val stroke = d.outline
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawSlice(top = true, bottom = !open, fill = family.pastel, ink = c.outline, stroke = stroke.toPx(), radius = 18.dp.toPx(), shadow = c.hardShadow, shadowDy = d.hardShadow.y.toPx())
                if (open && grouping == ProtoGrouping.BandRules || open && grouping == ProtoGrouping.Spine) {
                    // Band ends in a full ink rule: the rows below are inside the same card.
                    drawRect(c.outline, Offset(0f, size.height - stroke.toPx()), Size(size.width, stroke.toPx()))
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = d.touchTarget + d.s)
                .padding(horizontal = d.m, vertical = d.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.m),
        ) {
            if (s.collapsed) AisleBasket(s.basket, s.rows.map { IconRef(it.icon) }, s.rows.size) else AisleMedallion(s.number, s.index)
            Column(Modifier.weight(1f)) {
                Text(s.title, style = PicnicTheme.type.aisleTitle, color = family.deep)
                if (s.cleared) Text("All picked up!", style = PicnicTheme.type.caption, color = family.deep)
            }
            if (!s.collapsed && s.rows.isNotEmpty()) {
                Box(Modifier.clip(PicnicTheme.shapes.badge).background(c.surface).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(s.rows.size.toString(), style = PicnicTheme.type.counter, color = c.ink)
                }
            }
            Glyph(if (s.collapsed) PicnicGlyph.Expand else PicnicGlyph.Collapse, contentDescription = null, tint = family.deep)
        }
    }
}

@Composable
private fun ProtoRowSlice(grouping: ProtoGrouping, index: Int, first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val family = c.aisle(index)
    val stroke = d.outline
    val radius = 18.dp
    when (grouping) {
        ProtoGrouping.BandRules -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawSlice(top = false, bottom = last, fill = c.surface, ink = c.outline, stroke = stroke.toPx(), radius = radius.toPx(), shadow = c.hardShadow, shadowDy = d.hardShadow.y.toPx())
                    }
                    .padding(start = stroke, end = stroke, bottom = if (last) stroke else 0.dp)
                    .clip(RoundedCornerShape(bottomStart = if (last) radius - stroke else 0.dp, bottomEnd = if (last) radius - stroke else 0.dp)),
            ) {
                content()
                if (!first) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.5.dp).background(family.base.copy(alpha = 0.55f)))
            }
        }
        ProtoGrouping.Tray -> {
            val margin = 7.dp
            val inner = 12.dp
            val innerStroke = 1.5.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawSlice(top = false, bottom = last, fill = family.pastel, ink = c.outline, stroke = stroke.toPx(), radius = radius.toPx(), shadow = c.hardShadow, shadowDy = d.hardShadow.y.toPx())
                        val m = margin.toPx()
                        clipRect(0f, 0f, size.width, size.height) {
                            val ext = inner.toPx() * 2
                            val t = if (first) 0f else -ext
                            val b = if (last) size.height - m - stroke.toPx() else size.height + ext
                            val rr = RoundRect(
                                m, t, size.width - m, b,
                                topLeftCornerRadius = if (first) CornerRadius(inner.toPx()) else CornerRadius.Zero,
                                topRightCornerRadius = if (first) CornerRadius(inner.toPx()) else CornerRadius.Zero,
                                bottomLeftCornerRadius = if (last) CornerRadius(inner.toPx()) else CornerRadius.Zero,
                                bottomRightCornerRadius = if (last) CornerRadius(inner.toPx()) else CornerRadius.Zero,
                            )
                            val p = Path().apply { addRoundRect(rr) }
                            drawPath(p, c.surface)
                            drawPath(p, family.shadow.takeIf { !c.isDark } ?: c.outline, style = Stroke(innerStroke.toPx()))
                        }
                    }
                    .padding(start = margin + innerStroke, end = margin + innerStroke, bottom = if (last) margin + stroke + innerStroke else 0.dp, top = if (first) innerStroke else 0.dp)
                    .clip(RoundedCornerShape(
                        topStart = if (first) inner else 0.dp, topEnd = if (first) inner else 0.dp,
                        bottomStart = if (last) inner else 0.dp, bottomEnd = if (last) inner else 0.dp,
                    )),
            ) {
                content()
                if (!first) Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.5.dp).background(family.base.copy(alpha = 0.55f)))
            }
        }
        ProtoGrouping.Spine -> {
            val spine = 12.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawSlice(top = false, bottom = last, fill = c.surface, ink = c.outline, stroke = stroke.toPx(), radius = radius.toPx(), shadow = c.hardShadow, shadowDy = d.hardShadow.y.toPx())
                        // Spine: the aisle colour running down the left from the band to the cap.
                        clipPath(Path().apply { addRoundRect(sliceRect(size, false, last, radius.toPx(), 0f)) }) {
                            drawRect(family.base, Offset(0f, 0f), Size(spine.toPx(), size.height))
                            drawRect(c.outline, Offset(spine.toPx(), 0f), Size(stroke.toPx(), size.height))
                        }
                        drawSliceStroke(false, last, c.outline, stroke.toPx(), radius.toPx())
                    }
                    .padding(start = spine + stroke, end = stroke, bottom = if (last) stroke else 0.dp)
                    .clip(RoundedCornerShape(bottomEnd = if (last) radius - stroke else 0.dp)),
            ) {
                content()
                if (!first) Box(Modifier.fillMaxWidth().padding(end = 12.dp).height(1.5.dp).background(family.base.copy(alpha = 0.55f)))
            }
        }
    }
}

@Composable
private fun ProtoRowContent(row: ProtoRow, check: ProtoCheck) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val d = PicnicTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (row.checked) c.surfaceSunken else c.surface)
            .defaultMinSize(minHeight = d.rowMinHeight)
            .padding(start = d.s, end = d.xs, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.m),
    ) {
        ItemIcon(IconRef(row.icon), contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(row.name, style = t.itemName.copy(textDecoration = if (row.checked) TextDecoration.LineThrough else null), color = if (row.checked) c.inkSoft else c.ink)
            if (row.detail != null) Text(row.detail, style = t.detail, color = c.inkSoft)
        }
        ProtoCheckMark(row.checked, check)
    }
}

@Composable
internal fun ProtoCheckMark(checked: Boolean, variant: ProtoCheck, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val lemon = c.aisle(2)
    Canvas(modifier.size(48.dp)) {
        val ink = c.outline
        val line = 2.5.dp.toPx()
        val r = 13.dp.toPx()
        when (variant) {
            ProtoCheck.Circle, ProtoCheck.Star -> {
                if (!checked || variant == ProtoCheck.Circle) {
                    drawCircle(if (checked) c.highlight else c.surface, r)
                    drawCircle(ink, r, style = Stroke(line))
                }
            }
            ProtoCheck.Square -> {
                val s = r * 2
                val tl = Offset(center.x - r, center.y - r)
                drawRoundRect(if (checked) c.highlight else c.surface, tl, Size(s, s), CornerRadius(8.dp.toPx()))
                drawRoundRect(ink, tl, Size(s, s), CornerRadius(8.dp.toPx()), style = Stroke(line))
            }
        }
        if (checked) {
            if (variant == ProtoCheck.Star) {
                val star = starPath(center + Offset(0f, 1.dp.toPx()), 19.dp.toPx(), 10.dp.toPx())
                drawPath(star, lemon.base)
                drawPath(star, ink, style = Stroke(line, join = StrokeJoin.Round))
            } else {
                // Flourish: a little sparkle popping off the top-right of the box.
                val sp = sparklePath(center + Offset(r * 0.95f, -r * 0.95f), 6.dp.toPx())
                drawPath(sp, c.primary)
            }
            inkLine(c.onHighlight.takeIf { variant != ProtoCheck.Star || true } ?: ink, 3.2.dp.toPx(), center + Offset(-6.dp.toPx(), 0.5.dp.toPx()), center + Offset(-1.5.dp.toPx(), 5.dp.toPx()), center + Offset(6.5.dp.toPx(), -4.5.dp.toPx()))
        }
    }
}

/* ---- slice helpers ---- */

private fun sliceRect(size: Size, top: Boolean, bottom: Boolean, radius: Float, inset: Float): RoundRect {
    val ext = radius * 2 + inset * 2
    return RoundRect(
        inset, if (top) inset else -ext, size.width - inset, if (bottom) size.height - inset else size.height + ext,
        topLeftCornerRadius = if (top) CornerRadius(radius - inset) else CornerRadius.Zero,
        topRightCornerRadius = if (top) CornerRadius(radius - inset) else CornerRadius.Zero,
        bottomLeftCornerRadius = if (bottom) CornerRadius(radius - inset) else CornerRadius.Zero,
        bottomRightCornerRadius = if (bottom) CornerRadius(radius - inset) else CornerRadius.Zero,
    )
}

private fun DrawScope.drawSliceStroke(top: Boolean, bottom: Boolean, ink: Color, stroke: Float, radius: Float) {
    clipRect(0f, 0f, size.width, size.height) {
        drawPath(Path().apply { addRoundRect(sliceRect(size, top, bottom, radius, stroke / 2)) }, ink, style = Stroke(stroke))
    }
}

private fun DrawScope.drawSlice(top: Boolean, bottom: Boolean, fill: Color, ink: Color, stroke: Float, radius: Float, shadow: Color, shadowDy: Float) {
    val outer = Path().apply { addRoundRect(sliceRect(size, top, bottom, radius, 0f)) }
    if (bottom) {
        clipRect(0f, 0f, size.width, size.height + shadowDy + 1f) {
            val shifted = Path().apply { addPath(outer, Offset(0f, shadowDy)) }
            drawPath(shifted, shadow)
        }
    }
    clipRect(0f, 0f, size.width, size.height) { drawPath(outer, fill) }
    drawSliceStroke(top, bottom, ink, stroke, radius)
}

/* ---- buttons ---- */

@Composable
internal fun ProtoButtons(modifier: Modifier = Modifier) {
    PicnicBackground(modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (g in ProtoGloss.entries) {
                Text("Gloss: $g", style = PicnicTheme.type.caption, color = PicnicTheme.colors.inkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProtoButton("Add", g, PicnicGlyph.Add)
                    ProtoButton("Sort it out", g, PicnicGlyph.Sparkle)
                }
                ProtoButton("Add 6 items", g, PicnicGlyph.Add, Modifier.fillMaxWidth())
            }
            Text("Checkboxes: circle / square / star (unchecked, checked)", style = PicnicTheme.type.caption, color = PicnicTheme.colors.inkSoft)
            Row {
                for (v in ProtoCheck.entries) {
                    ProtoCheckMark(false, v)
                    ProtoCheckMark(true, v)
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ProtoButton(text: String, gloss: ProtoGloss, glyph: PicnicGlyph, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val shape = PicnicTheme.shapes.button
    val depth = d.hardShadow.y + 1.dp
    Box(modifier.defaultMinSize(minHeight = d.touchTarget).padding(bottom = depth), propagateMinConstraints = true) {
        Row(
            Modifier
                .hardShadow(shape, c.primaryShadow, androidx.compose.ui.unit.DpOffset(0.dp, depth))
                .clip(shape)
                .background(c.primary)
                .drawWithContent {
                    val o = d.outline.toPx()
                    when (gloss) {
                        ProtoGloss.None -> Unit
                        ProtoGloss.Rim -> {
                            // Cel rim light: the face minus itself shifted down, i.e. a crescent hugging the top edge.
                            val r = size.height / 2
                            val face = Path().apply { addRoundRect(RoundRect(o, o, size.width - o, size.height - o, CornerRadius(r - o))) }
                            val lower = Path().apply { addRoundRect(RoundRect(o, o + 4.dp.toPx(), size.width - o, size.height - o + 4.dp.toPx(), CornerRadius(r - o))) }
                            val crescent = Path.combine(PathOperation.Difference, face, lower)
                            clipRect(0f, 0f, size.width, size.height * 0.5f) { drawPath(crescent, Color.White.copy(alpha = 0.35f)) }
                        }
                        ProtoGloss.Band -> {
                            val inset = o + 3.dp.toPx()
                            val h = (size.height - inset * 2) * 0.32f
                            clipRect(0f, 0f, size.width, inset + h) {
                                val r = size.height / 2
                                drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(inset + 6.dp.toPx(), inset), Size(size.width - (inset + 6.dp.toPx()) * 2, size.height - inset * 2), CornerRadius(r - inset))
                            }
                        }
                    }
                    drawContent()
                }
                .drawWithContent {
                    drawContent()
                    drawOutlineBorder(d.outline.toPx(), c.outline)
                }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Glyph(glyph, contentDescription = null, tint = c.onPrimary, size = 20.dp)
            Text(text, style = PicnicTheme.type.label, color = c.onPrimary)
        }
    }
}

private fun DrawScope.drawOutlineBorder(o: Float, ink: Color) {
    drawRoundRect(ink, Offset(o / 2, o / 2), Size(size.width - o, size.height - o), CornerRadius(size.height / 2 - o / 2), style = Stroke(o, cap = StrokeCap.Round))
}

@Suppress("unused")
private val keepDp: Dp = 0.dp

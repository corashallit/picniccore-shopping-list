package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef
import kotlin.math.abs

/**
 * A collapsed aisle drawn as a little basket. CONTRACT (docs/ARCHITECTURE.md §11.4):
 * - [kind] picks the container art (placeholder vector art now; final art later).
 * - The basket holds what has ALREADY BEEN GOTTEN: the first (up to 5) of [inBasket], the icons of
 *   the aisle's checked items, peek out of it, overlapping. They are drawn between the basket's back
 *   layer (handle, open lid) and its front layer (body, rim). Never pass items still to get.
 * - Empty [inBasket] (nothing gotten yet, or the list's empty state) = the bare basket of that kind:
 *   the same back and front art with nothing in between.
 * - [count] > 0 shows a count badge: how many are still to get. 0 shows no badge.
 * - Decorative: the enclosing header carries the accessible text
 *   ("Produce, 2 to get, 3 in the basket", state "collapsed").
 */
@Composable
fun AisleBasket(
    kind: BasketKind,
    /** Icons of the items already gotten (in the basket), first shown first. */
    inBasket: List<IconRef>,
    /** Items still to get: the badge. */
    count: Int,
    modifier: Modifier = Modifier,
    size: Dp = PicnicTheme.dimens.basket,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val shown = inBasket.take(MAX_MINI)
    val unit = size / 64f
    val mini = d.miniIcon * (size / d.basket)
    val measurer = rememberTextMeasurer()
    val counter = PicnicTheme.type.counter
    Box(modifier.size(size).clearAndSetSemantics { }) {
        Canvas(Modifier.matchParentSize()) { inGrid(64f) { drawBasketBack(kind, c.outline) } }
        // Edge icons first so the middle ones overlap them.
        val order = shown.indices.sortedByDescending { abs(it - (shown.size - 1) / 2f) }
        for (i in order) {
            val spread = if (shown.size <= 1) 0f else (i - (shown.size - 1) / 2f) * minOf(9f, 26f / (shown.size - 1))
            val y = basketIconLine(kind) + if (i % 2 == 1) 2f else 0f
            ItemIcon(
                icon = shown[i],
                contentDescription = null,
                size = mini,
                modifier = Modifier
                    .offset(x = unit * (32f + spread) - mini / 2, y = unit * y - mini / 2)
                    .rotate(TILTS[i % TILTS.size]),
            )
        }
        Canvas(Modifier.matchParentSize()) {
            inGrid(64f) { drawBasketFront(kind, c.outline) }
            if (count > 0) drawCountBadge(count, measurer, counter, c)
        }
    }
}

private const val MAX_MINI = 5
private val TILTS = floatArrayOf(-10f, 8f, -4f, 10f, -8f)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCountBadge(
    count: Int,
    measurer: TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
    c: PicnicColors,
) {
    val label = if (count > 99) "99+" else count.toString()
    val layout = measurer.measure(label, style.copy(color = c.onPrimary))
    val h = maxOf(layout.size.height.toFloat() + 2.dp.toPx(), 18.dp.toPx())
    val w = maxOf(layout.size.width.toFloat() + 10.dp.toPx(), h)
    val left = size.width - w
    val top = 0f
    val r = androidx.compose.ui.geometry.CornerRadius(h / 2)
    drawRoundRect(c.primaryShadow, Offset(left, top + 2.dp.toPx()), androidx.compose.ui.geometry.Size(w, h), r)
    drawRoundRect(c.primary, Offset(left, top), androidx.compose.ui.geometry.Size(w, h), r)
    drawRoundRect(c.outline, Offset(left, top), androidx.compose.ui.geometry.Size(w, h), r, style = Stroke(1.5.dp.toPx()))
    drawText(layout, topLeft = Offset(left + (w - layout.size.width) / 2, top + (h - layout.size.height) / 2))
}

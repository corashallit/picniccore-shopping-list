package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef

/**
 * A collapsed aisle drawn as a little basket. CONTRACT (docs/ARCHITECTURE.md §11):
 * - [kind] picks the container art (placeholder vector art now; final art later).
 * - The first 2..5 of [miniIcons] peek out of the basket, overlapping (fewer if fewer given).
 * - [count] > 0 shows a count badge; 0 shows an empty basket (empty state, cleared aisle).
 * - Decorative: the enclosing header carries the accessible text ("Bakery, 4 items, collapsed").
 */
@Composable
fun AisleBasket(
    kind: BasketKind,
    miniIcons: List<IconRef>,
    count: Int,
    modifier: Modifier = Modifier,
    size: Dp = PicnicTheme.dimens.basket,
) {
    val c = PicnicTheme.colors
    val shown = miniIcons.take(5)
    val mini = PicnicTheme.dimens.miniIcon
    Box(modifier.size(size), contentAlignment = Alignment.BottomCenter) {
        shown.forEachIndexed { i, ref ->
            val spread = if (shown.size <= 1) 0f else (i - (shown.size - 1) / 2f) / (shown.size - 1)
            ItemIcon(
                icon = ref,
                contentDescription = null,
                size = mini,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = size * 0.5f * spread, y = size * 0.12f + if (i % 2 == 0) 0.dp else 4.dp),
            )
        }
        Box(
            Modifier
                .size(width = size, height = size * 0.55f)
                .clip(RoundedCornerShape(bottomStart = size * 0.2f, bottomEnd = size * 0.2f, topStart = 4.dp, topEnd = 4.dp))
                .background(c.aisle(kind.ordinal).pastel)
                .border(PicnicTheme.dimens.outline, c.outline, RoundedCornerShape(bottomStart = size * 0.2f, bottomEnd = size * 0.2f, topStart = 4.dp, topEnd = 4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(kind.key.take(2), style = PicnicTheme.type.caption, color = c.aisle(kind.ordinal).deep)
        }
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(PicnicTheme.shapes.badge)
                    .background(c.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(count.toString(), style = PicnicTheme.type.counter, color = c.onPrimary)
            }
        }
    }
}

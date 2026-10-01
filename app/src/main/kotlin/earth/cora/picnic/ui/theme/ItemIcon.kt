package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.core.model.IconRef

/**
 * An item's tiny icon. CONTRACT (docs/ARCHITECTURE.md §11):
 * - Image source via LocalIconImages: custom file -> bundled key -> bundled category fallback;
 *   if none, placeholder art (category-coloured sticker with the label initial).
 * - [drawing] = a bespoke icon is on its way: overlay DrawingSparkle (callers pass the category
 *   fallback ref while drawing, see IconCatalog.fallbackFor).
 * - [contentDescription] null = decorative (rows already read the item name).
 */
@Composable
fun ItemIcon(
    icon: IconRef,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = PicnicTheme.dimens.itemIcon,
    drawing: Boolean = false,
) {
    val images = LocalIconImages.current
    val bitmap: ImageBitmap? by produceState(images.cached(icon), icon, images) {
        if (value == null) value = images.load(icon)
    }
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Box(modifier.then(semantics).size(size), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.size(size))
        } else {
            PlaceholderIconArt(icon.key, size)
        }
        if (drawing) DrawingSparkle(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

@Composable
private fun PlaceholderIconArt(key: String, size: Dp) {
    val catalog = LocalIconCatalog.current
    val categoryIndex = catalog.categoryOf(key)?.let { catalog.categories.indexOf(it) } ?: -1
    val colors = PicnicTheme.colors.aisle(categoryIndex)
    val label = catalog[key]?.label ?: key
    Box(
        Modifier
            .size(size * 0.9f)
            .clip(PicnicTheme.shapes.badge)
            .background(colors.pastel)
            .border(PicnicTheme.dimens.outline * 0.75f, colors.deep, PicnicTheme.shapes.badge),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.take(1).uppercase(), color = colors.deep, fontSize = (size.value * 0.42f).sp)
    }
}

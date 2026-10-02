package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef

/**
 * An item's tiny icon. CONTRACT (docs/ARCHITECTURE.md §11.4):
 * - Image source via LocalIconImages: bespoke file -> bundled key -> bundled category fallback;
 *   if none, placeholder art (a die-cut sticker of the key's category object).
 * - [drawing] = the icon is being decided or drawn: overlay DrawingSparkle (callers pass the
 *   category fallback ref meanwhile, via IconCatalog.displayRef(item, busy)).
 * - [contentDescription] null = decorative (rows already read the item name).
 * - [lifted] = the sticker floats on the ground (edit sheet hero): placeholder art adds its drop
 *   shadow. Default flat: rows, chips and pickers never put an offset shadow under a circle.
 *
 * C additions (§3.3, §19.2; phase-0 signature, WP4 draws them): [aisleIndex] tints the capsule sentinel
 * (`IconRef("_capsule")`, D5; -1 = neutral); [ghost] = a checked row's peeled outline; [holo] = shiny /
 * jackpot foil; [pixel] = the bonus-stage 8-bit sprite (egg 10); [flags] = the corner flags. The phase-0
 * body ignores them. The default [size] is the PicnicDimens.itemIcon token, which WP4 sets to 52 dp.
 */
@Composable
fun ItemIcon(
    icon: IconRef,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = PicnicTheme.dimens.itemIcon,
    aisleIndex: Int = -1,
    ghost: Boolean = false,
    holo: Boolean = false,
    pixel: Boolean = false,
    flags: Set<StickerFlag> = emptySet(),
    drawing: Boolean = false,
    lifted: Boolean = false,
) {
    val images = LocalIconImages.current
    // Restarts whenever the ref changes (e.g. a bespoke icon got attached): the previous bitmap stays
    // on screen until the new one is decoded, so rows never flash the placeholder.
    val bitmap: ImageBitmap? by produceState(images.cached(icon), icon, images) {
        value = images.cached(icon) ?: images.load(icon)
    }
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Box(modifier.then(semantics).size(size), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.size(size), filterQuality = FilterQuality.Medium)
        } else {
            PlaceholderIconArt(icon.key, size, lifted)
        }
        if (drawing) {
            DrawingSparkle(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size((size * 0.42f).coerceIn(10.dp, 18.dp)),
            )
        }
    }
}

/** The flags at a sticker's top-right (§3.3 step 4), each a 20 dp sticker with its own white border. */
enum class StickerFlag {
    /** Lemon 5-point star: added after the list's new-anchor (§7.1). */
    New,

    /** Sakura heart: a favourite (D17). */
    Favourite,

    /** Star for a shiny or jackpot sticker (the row adds the "shiny!" / "jackpot!" chip). */
    Shiny,
}

/** Placeholder sticker for an icon key: its category's object; the paper bag for DEFAULT_ICON and unknown keys. */
@Composable
private fun PlaceholderIconArt(key: String, size: Dp, lifted: Boolean) {
    val catalog = LocalIconCatalog.current
    val category = catalog.categoryOf(key)?.key
    val rim = PicnicTheme.colors.outline
    Canvas(Modifier.size(size)) {
        inGrid(24f) {
            drawItemSticker(
                categoryKey = category,
                isDefault = key == IconCatalog.DEFAULT_ICON || category == null,
                rim = rim,
                disc = size >= STICKER_DISC_MIN,
                lifted = lifted,
            )
        }
    }
}

/** Below this size the placeholder drops its white disc so the object stays legible. */
private val STICKER_DISC_MIN: Dp = 28.dp

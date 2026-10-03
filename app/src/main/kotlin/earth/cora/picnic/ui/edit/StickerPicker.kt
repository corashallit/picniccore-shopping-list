package earth.cora.picnic.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalIconCatalog
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.hardShadow

/*
 * The sticker grid shared by the Edit sheet's "Change sticker" (§6.3) and the pantry's "Change sticker" (§6.13).
 */

/**
 * A grid of 40 dp library stickers in 48 dp kiss-cut cells (backing paper, 2 dp white edge, hs) in 52 dp targets; the
 * [selected] key wears a 2.5 dp lemon ring. Each cell is a Role.RadioButton named by its catalog label.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerPicker(keys: List<String>, selected: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val catalog = LocalIconCatalog.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            StickerCell(key, catalog[key]?.label ?: key, selected = key == selected, onClick = { onPick(key) })
        }
    }
}

/** Catalog keys with [current]'s category first (its own key leading), then the rest in catalog order. */
fun catalogChoices(current: String?, catalog: IconCatalog): List<String> {
    val category = current?.let { catalog[it]?.category }
    if (category == null) return catalog.keys
    val (own, rest) = catalog.keys.partition { catalog[it]?.category == category }
    return (listOf(current) + own + rest).distinct()
}

@Composable
private fun StickerCell(key: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = PicnicTheme.colors
    val shape = PicnicTheme.shapes.card
    Box(
        Modifier
            .size(52.dp)
            // No ripple: the chosen cell answers with its lemon ring.
            .clickable(interactionSource = null, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .hardShadow(shape, PicnicTheme.shadows)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(2.dp)
                .clip(shape)
                .background(c.backing)
                .then(if (selected) Modifier.border(2.5.dp, Families.lemon.mid, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            ItemIcon(IconRef(key), contentDescription = null, size = 40.dp)
        }
    }
}

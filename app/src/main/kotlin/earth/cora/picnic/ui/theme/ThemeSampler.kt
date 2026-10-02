package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef

/**
 * One preview of the theme kit, captured automatically by Roborazzi's @Preview scanner
 * (app/src/test/screenshots). app-shell adds one @Preview per component in light and dark.
 */
@Composable
internal fun ThemeSampler() {
    PicnicBackground {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PicnicTopBar(title = "Picnic List", subtitle = "3 of 7 in the basket", bottom = { TripProgress(3, 7) })
            AisleHeader(
                title = "Produce", number = 1, aisleIndex = 0, basket = BasketKind.FRUIT_CRATE,
                inBasket = listOf(IconRef("lemon"), IconRef("apple")), count = 3, gotten = 2,
                collapsed = true, cleared = false, onToggle = {},
            )
            Column {
                AisleHeader(
                    title = "Bakery", number = 2, aisleIndex = 1, basket = BasketKind.BREAD_BASKET,
                    inBasket = listOf(IconRef("milk_bag")), count = 2, gotten = 1, collapsed = false, cleared = false, onToggle = {}, rowsBelow = true,
                )
                AisleSectionRow(aisleIndex = 1, first = true, last = false) {
                    ItemRowCard(name = "Baguette", detail = "1 · crusty", icon = IconRef("baguette"), checked = false)
                }
                AisleSectionRow(aisleIndex = 1, first = false, last = false) {
                    ItemRowCard(name = "Gochujang", detail = "500 g tub", icon = IconRef("condiments_generic"), checked = false, drawing = true)
                }
                AisleSectionRow(aisleIndex = 1, first = false, last = true) {
                    ItemRowCard(name = "Milk", detail = "4 L bag", icon = IconRef("milk_bag"), checked = true)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PicnicButton("Add all", onClick = {})
                PicnicButton("Cancel", onClick = {}, style = PicnicButtonStyle.Secondary)
                PicnicChip("Produce", selected = true, onClick = {})
            }
        }
    }
}

@Preview(name = "Theme sampler - day", widthDp = 411, heightDp = 760)
@Composable
private fun ThemeSamplerLightPreview() {
    PicnicTheme(darkTheme = false) { ThemeSampler() }
}

@Preview(name = "Theme sampler - dusk", widthDp = 411, heightDp = 760)
@Composable
private fun ThemeSamplerDarkPreview() {
    PicnicTheme(darkTheme = true) { ThemeSampler() }
}

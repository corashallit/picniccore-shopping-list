package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * One screen of the theme kit, captured automatically by Roborazzi's @Preview scanner (app/src/test/screenshots): the
 * binder top with its LCD, a pad card of rows (sticker, name and detail, checkbox and hanko), chips and the buttons, on
 * the binder ground, the way the list composes them (the list's own pieces are WP5's).
 */
@Composable
internal fun ThemeSampler() {
    PicnicBackground(Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BinderTopBar(
                title = "Saturday shop",
                subtitle = "Hillcrest Market · 8 stops",
                lcd = LcdState(LcdMode.Count, count = 10, total = 24),
                onMore = {},
                onSunTap = {},
            )
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .hardShadow(PicnicTheme.shapes.section, PicnicTheme.shadows)
                        .clip(PicnicTheme.shapes.section)
                        .background(Families.melon.pastel)
                        .padding(start = 5.dp, end = 5.dp, top = 8.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabSample(4, "Baking", onTray = true)
                        StoreAisleChip(2)
                    }
                    Column(Modifier.fillMaxWidth().clip(PicnicTheme.shapes.sectionInner).background(PicnicTheme.colors.pad)) {
                        RowHarness("All-purpose flour", "2.5 kg bag", "flour", checked = true, aisleIndex = 3)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(PicnicTheme.colors.rule))
                        RowHarness("White sugar", "2 kg bag", "sugar", checked = true, aisleIndex = 3)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(PicnicTheme.colors.rule))
                        RowHarness("Baking powder", "225 g tin · from your recipe", "baking_soda", flags = setOf(StickerFlag.New), aisleIndex = 3)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PicnicChip("Favourites", selected = true, onClick = {}, family = Families.sakura)
                    PicnicChip("This week", selected = false, onClick = {})
                }
                PicnicButton("Stick all 5 on my list", onClick = {}, leadingStar = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PicnicButton("Re-sort", onClick = {}, style = PicnicButtonStyle.Secondary, glyph = PicnicGlyph.Refresh)
                    PicnicButton("Skip all", onClick = {}, style = PicnicButtonStyle.Text)
                }
            }
        }
    }
}

@Preview(name = "Theme sampler - day", widthDp = 360, heightDp = 760)
@Composable
private fun ThemeSamplerDayPreview() {
    PicnicTheme(variant = ThemeVariant.Day) { ThemeSampler() }
}

@Preview(name = "Theme sampler - glow", widthDp = 360, heightDp = 760)
@Composable
private fun ThemeSamplerGlowPreview() {
    PicnicTheme(variant = ThemeVariant.Glow) { ThemeSampler() }
}

@Preview(name = "Theme sampler - dusk", widthDp = 360, heightDp = 760)
@Composable
private fun ThemeSamplerDuskPreview() {
    PicnicTheme(variant = ThemeVariant.Dusk) { ThemeSampler() }
}

@Preview(name = "Theme sampler - golden", widthDp = 360, heightDp = 760)
@Composable
private fun ThemeSamplerGoldenPreview() {
    PicnicTheme(variant = ThemeVariant.Golden) { ThemeSampler() }
}

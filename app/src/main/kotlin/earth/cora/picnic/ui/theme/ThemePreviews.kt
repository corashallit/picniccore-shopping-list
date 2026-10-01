package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef

/*
 * One @Preview per theme component family, in day ("picnic at noon") and dusk ("picnic at dusk").
 * Roborazzi's preview scanner renders every one of them to app/src/test/screenshots/ (record with
 * ./gradlew :app:recordRoborazziDebug), so the art pass can review the whole kit without a device.
 * Previews use IconImages.None (placeholder art) and IconCatalog.default: no app container.
 */

@Composable
private fun PreviewFrame(dark: Boolean, gutter: Boolean = true, content: @Composable () -> Unit) {
    PicnicTheme(darkTheme = dark) {
        PicnicBackground(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(horizontal = if (gutter) 16.dp else 0.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

@Composable
private fun Caption(text: String) = Text(text, style = PicnicTheme.type.caption, color = PicnicTheme.colors.inkSoft)

/* ---------------- controls ---------------- */

@Composable
private fun ControlsSample() {
    Caption("PicnicButton")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PicnicButton("Add all", onClick = {}, glyph = PicnicGlyph.Add)
        PicnicButton("Cancel", onClick = {}, style = PicnicButtonStyle.Secondary)
        PicnicButton("Undo", onClick = {}, style = PicnicButtonStyle.Text)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PicnicButton("Saving…", onClick = {}, enabled = false)
        PicnicButton("Plain lines", onClick = {}, style = PicnicButtonStyle.Secondary, enabled = false)
    }
    PicnicButton("Test connection", onClick = {}, modifier = Modifier.fillMaxWidth(), glyph = PicnicGlyph.Sparkle)
    Caption("PicnicIconButton · PicnicSwitch")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PicnicIconButton(PicnicGlyph.Camera, "Take a photo", onClick = {})
        PicnicIconButton(PicnicGlyph.Mic, "Speak", onClick = {}, emphasized = true)
        PicnicIconButton(PicnicGlyph.Send, "Send", onClick = {}, emphasized = true)
        PicnicIconButton(PicnicGlyph.Settings, "Settings", onClick = {}, enabled = false)
        PicnicSwitch(checked = true, onCheckedChange = {})
        PicnicSwitch(checked = false, onCheckedChange = {})
    }
    Caption("PicnicChip")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PicnicChip("Produce", selected = true, onClick = {})
        PicnicChip("Bakery", selected = false, onClick = {}, leading = { AisleMedallion(2, 1, size = 20.dp) })
        PicnicChip("allrecipes.com", selected = false, onClick = {}, leading = { Glyph(PicnicGlyph.Link, null, size = 16.dp) })
    }
    Caption("PicnicTextField")
    PicnicTextField(value = "", onValueChange = {}, placeholder = "Add something… or \"pancakes from scratch\"", minLines = 2)
    PicnicTextField(
        value = "https://picnic.example/v1",
        onValueChange = {},
        label = "Server address",
        singleLine = true,
        isError = true,
        supportingText = "Use an https:// address",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    PicnicTextField(
        value = "sk-proj-secret",
        onValueChange = {},
        label = "Access token",
        singleLine = true,
        supportingText = "Saved: sk-…wxyz",
        visualTransformation = PasswordVisualTransformation(),
        trailing = { PicnicIconButton(PicnicGlyph.Key, "Paste", onClick = {}) },
    )
}

@Preview(name = "Controls - day", widthDp = 411, heightDp = 720)
@Composable
private fun ControlsDayPreview() = PreviewFrame(dark = false) { ControlsSample() }

@Preview(name = "Controls - dusk", widthDp = 411, heightDp = 720)
@Composable
private fun ControlsDuskPreview() = PreviewFrame(dark = true) { ControlsSample() }

/* ---------------- decor ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecorSample() {
    Caption("AisleMedallion (8 aisles + special)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 8) AisleMedallion(number = i + 1, aisleIndex = i)
        AisleMedallion(number = null, aisleIndex = -1)
    }
    Caption("SectionDivider (major · minor)")
    SectionDivider(major = true)
    SectionDivider()
    Caption("CheckMark · DrawingSparkle · LoadingSparkle")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CheckMark(checked = false)
        CheckMark(checked = true)
        DrawingSparkle()
        LoadingSparkle(label = "Reading your list…")
    }
    Caption("TripProgress (0 / 3 / 7 of 7)")
    TripProgress(done = 0, total = 7)
    TripProgress(done = 3, total = 7)
    TripProgress(done = 7, total = 7)
    Caption("Glyph")
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PicnicGlyph.entries.forEach { Glyph(it, contentDescription = it.name) }
    }
}

@Preview(name = "Decor - day", widthDp = 411, heightDp = 620)
@Composable
private fun DecorDayPreview() = PreviewFrame(dark = false) { DecorSample() }

@Preview(name = "Decor - dusk", widthDp = 411, heightDp = 620)
@Composable
private fun DecorDuskPreview() = PreviewFrame(dark = true) { DecorSample() }

/* ---------------- item icons (placeholder stickers) ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemIconsSample() {
    val catalog = IconCatalog.default
    Caption("ItemIcon placeholder per category (+ DEFAULT_ICON, + drawing)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        catalog.categories.forEach { ItemIcon(IconRef(it.fallbackIcon), contentDescription = it.label, size = 48.dp) }
        ItemIcon(IconRef(IconCatalog.DEFAULT_ICON), contentDescription = "Default", size = 48.dp)
        ItemIcon(IconRef("condiments_generic"), contentDescription = "Drawing", size = 48.dp, drawing = true)
    }
    Caption("Row size (40 dp) and mini size (20 dp)")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("lemon", "baguette", "milk_bag", "salmon_fillet", "dish_soap").forEach { ItemIcon(IconRef(it), contentDescription = null) }
        listOf("lemon", "baguette", "milk_bag", "salmon_fillet", "dish_soap").forEach { ItemIcon(IconRef(it), contentDescription = null, size = 20.dp) }
    }
}

@Preview(name = "Item icons - day", widthDp = 411, heightDp = 420)
@Composable
private fun ItemIconsDayPreview() = PreviewFrame(dark = false) { ItemIconsSample() }

@Preview(name = "Item icons - dusk", widthDp = 411, heightDp = 420)
@Composable
private fun ItemIconsDuskPreview() = PreviewFrame(dark = true) { ItemIconsSample() }

/* ---------------- baskets ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasketsSample() {
    val icons = listOf("lemon", "sliced_bread", "cheese_curds", "chicken_drumstick", "salmon_fillet").map(::IconRef)
    Caption("AisleBasket, every kind (0-5 mini icons, count badge)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasketKind.entries.forEachIndexed { i, kind ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val n = i % 6
                AisleBasket(kind, icons.take(n), count = if (n == 0) 0 else n + i)
                Text(kind.key, style = PicnicTheme.type.caption, color = PicnicTheme.colors.inkSoft)
            }
        }
    }
    Caption("Empty-state size (120 dp)")
    AisleBasket(BasketKind.DEFAULT, icons, count = 12, size = 120.dp)
}

@Preview(name = "Baskets - day", widthDp = 411, heightDp = 640)
@Composable
private fun BasketsDayPreview() = PreviewFrame(dark = false) { BasketsSample() }

@Preview(name = "Baskets - dusk", widthDp = 411, heightDp = 640)
@Composable
private fun BasketsDuskPreview() = PreviewFrame(dark = true) { BasketsSample() }

/* ---------------- list pieces ---------------- */

@Composable
private fun ListPiecesSample() {
    AisleHeader(
        title = "Unsorted", number = null, aisleIndex = -1, basket = BasketKind.DEFAULT,
        miniIcons = emptyList(), count = 1, collapsed = false, cleared = false, onToggle = {},
    )
    ItemRowCard(name = "Dish rack", detail = null, icon = IconRef(IconCatalog.DEFAULT_ICON), checked = false, drawing = true)
    AisleHeader(
        title = "Produce", number = 1, aisleIndex = 0, basket = BasketKind.FRUIT_CRATE,
        miniIcons = listOf("lemon", "apple", "carrot", "spinach").map(::IconRef), count = 4,
        collapsed = true, cleared = false, onToggle = {},
    )
    AisleHeader(
        title = "Aisle 2 - Baking & spices", number = 3, aisleIndex = 2, basket = BasketKind.JAR_SHELF,
        miniIcons = emptyList(), count = 2, collapsed = false, cleared = false, onToggle = {},
    )
    ItemRowCard(name = "All-purpose flour", detail = "2 kg", icon = IconRef("flour"), checked = false)
    ItemRowCard(name = "Gochujang", detail = "500 g tub · for the bibimbap", icon = IconRef("condiments_generic"), checked = false, drawing = true)
    ItemRowCard(name = "Milk", detail = "4 L bag", icon = IconRef("milk_bag"), checked = true)
    AisleHeader(
        title = "Bakery", number = 2, aisleIndex = 1, basket = BasketKind.BREAD_BASKET,
        miniIcons = emptyList(), count = 0, collapsed = true, cleared = true, onToggle = {},
    )
}

@Preview(name = "List pieces - day", widthDp = 411, heightDp = 720)
@Composable
private fun ListPiecesDayPreview() = PreviewFrame(dark = false) { ListPiecesSample() }

@Preview(name = "List pieces - dusk", widthDp = 411, heightDp = 720)
@Composable
private fun ListPiecesDuskPreview() = PreviewFrame(dark = true) { ListPiecesSample() }

/* ---------------- chrome: top bar, snackbar, sheet handle, empty state ---------------- */

private class PreviewSnackbarData(message: String, actionLabel: String?) : SnackbarData {
    override val visuals: SnackbarVisuals = object : SnackbarVisuals {
        override val message: String = message
        override val actionLabel: String? = actionLabel
        override val withDismissAction: Boolean = false
        override val duration: SnackbarDuration = SnackbarDuration.Short
    }
    override fun performAction() = Unit
    override fun dismiss() = Unit
}

@Composable
private fun ChromeSample() {
    PicnicTopBar(
        title = "Saturday shop",
        subtitle = "3 of 7 in the basket",
        actions = {
            PicnicIconButton(PicnicGlyph.Aisles, "Aisles", onClick = {})
            PicnicIconButton(PicnicGlyph.Settings, "Settings", onClick = {})
        },
        bottom = { TripProgress(done = 3, total = 7) },
    )
    PicnicTopBar(
        title = "Settings",
        navigation = { PicnicIconButton(PicnicGlyph.Back, "Back", onClick = {}) },
    )
    PicnicSnackbar(PreviewSnackbarData("Removed Lemons", "Undo"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { SheetHandle() }
    EmptyState(
        title = "Nothing on the list yet",
        body = "Type, snap or say what you need. \"Pancakes from scratch\" works too.",
        modifier = Modifier.fillMaxWidth(),
        action = { PicnicButton("Add something", onClick = {}, glyph = PicnicGlyph.Add) },
    )
}

@Preview(name = "Chrome - day", widthDp = 411, heightDp = 800)
@Composable
private fun ChromeDayPreview() = PreviewFrame(dark = false, gutter = false) { ChromeSample() }

@Preview(name = "Chrome - dusk", widthDp = 411, heightDp = 800)
@Composable
private fun ChromeDuskPreview() = PreviewFrame(dark = true, gutter = false) { ChromeSample() }

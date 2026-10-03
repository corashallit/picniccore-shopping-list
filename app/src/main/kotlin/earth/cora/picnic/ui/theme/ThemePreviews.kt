package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.theme.gen.StickerManifest

/*
 * The theme kit (docs/DESIGN_STICKER.md §4): every WP4 component in every listed state, in one place. The @Previews below
 * render each group in Day and Glow (Roborazzi's preview scanner records them), and ThemeGoldensTest renders the same
 * groups at w360dp-h760dp-xxhdpi under the spec's golden names (Appendix C). No app container: previews use the library
 * stickers and the default icon catalog.
 */

/** A kit frame: the variant's theme over the binder ground, with a 12 dp gutter. */
@Composable
internal fun KitFrame(variant: ThemeVariant, reducedMotion: Boolean = false, scroll: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    PicnicTheme(variant = variant, reducedMotion = reducedMotion) {
        PicnicBackground(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** A caption on a pad patch (information text never sits straight on the pinstripes). */
@Composable
internal fun KitCaption(text: String) {
    PadPatch { Text(text, style = PicnicTheme.type.caption.copy(color = PicnicTheme.colors.onPadSoft)) }
}

/** An interaction source that reports a held press and/or keyboard focus (static states for goldens). */
@Composable
internal fun rememberHeldInteraction(pressed: Boolean = false, focused: Boolean = false): MutableInteractionSource {
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(pressed, focused) {
        // Interactions are a hot flow without replay: wait for the button's collectors to subscribe first.
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        if (pressed) source.emit(PressInteraction.Press(Offset.Zero))
        if (focused) source.emit(FocusInteraction.Focus())
    }
    return source
}

/* ------------------------------------------------------------------------------------------------
 * Buttons (§4.6, R3).
 * ------------------------------------------------------------------------------------------------ */

/** One primary pill state. */
@Composable
internal fun PrimaryButtonState(state: String) {
    when (state) {
        "rest" -> PicnicButton("Stick all 5 on my list", {}, leadingStar = true)
        "pressed" -> PicnicButton("Stick all 5 on my list", {}, leadingStar = true, interactionSource = rememberHeldInteraction(pressed = true))
        "disabled" -> PicnicButton("Reading… 6 of 9", {}, enabled = false, busy = true)
        "focused" -> PicnicButton("Start my list", {}, interactionSource = rememberHeldInteraction(focused = true))
    }
}

/** One secondary pill state. */
@Composable
internal fun SecondaryButtonState(state: String) {
    when (state) {
        "rest" -> PicnicButton("Re-sort", {}, style = PicnicButtonStyle.Secondary, glyph = PicnicGlyph.Refresh)
        "pressed" -> PicnicButton("Re-sort", {}, style = PicnicButtonStyle.Secondary, glyph = PicnicGlyph.Refresh, interactionSource = rememberHeldInteraction(pressed = true))
        "disabled" -> PicnicButton("Re-sort", {}, style = PicnicButtonStyle.Secondary, glyph = PicnicGlyph.Refresh, enabled = false)
        "focused" -> PicnicButton("Keep everything", {}, style = PicnicButtonStyle.Secondary, interactionSource = rememberHeldInteraction(focused = true))
    }
}

/** One text button state. */
@Composable
internal fun TextButtonState(state: String) {
    when (state) {
        "rest" -> PicnicButton("Skip all", {}, style = PicnicButtonStyle.Text)
        "pressed" -> PicnicButton("Skip all", {}, style = PicnicButtonStyle.Text, interactionSource = rememberHeldInteraction(pressed = true))
        "disabled" -> PicnicButton("Skip all", {}, style = PicnicButtonStyle.Text, enabled = false)
    }
}

/** The round sticker buttons in one state. */
@Composable
internal fun RoundButtonsState(state: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val src = rememberHeldInteraction(pressed = state == "pressed")
        val enabled = state != "disabled"
        PicnicIconButton(PicnicGlyph.More, "More options", {}, enabled = enabled, interactionSource = src)
        PicnicIconButton(PicnicGlyph.Back, "Back", {}, enabled = enabled, interactionSource = rememberHeldInteraction(pressed = state == "pressed"))
        AddBarButton(AddBarButtonKind.Snap, "Snap a photo of a list", {}, enabled = enabled, interactionSource = rememberHeldInteraction(pressed = state == "pressed"))
        AddBarButton(AddBarButtonKind.Speak, "Speak a voice note", {}, enabled = enabled, interactionSource = rememberHeldInteraction(pressed = state == "pressed"))
        AddBarButton(AddBarButtonKind.Send, "Send", {}, enabled = enabled, interactionSource = rememberHeldInteraction(pressed = state == "pressed"))
    }
}

/** The FAB in one state. */
@Composable
internal fun FabState(state: String) {
    PicnicFab("Add to the list", {}, bob = false, interactionSource = rememberHeldInteraction(pressed = state == "pressed"))
}

/** Every button kind and state on one sheet (buttons_matrix_<variant>). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ButtonsKit() {
    KitCaption("primary · pressed · disabled · focused")
    for (s in listOf("rest", "pressed", "disabled", "focused")) PrimaryButtonState(s)
    KitCaption("secondary · text")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in listOf("rest", "pressed", "disabled")) SecondaryButtonState(s)
        for (s in listOf("rest", "pressed", "disabled")) TextButtonState(s)
    }
    KitCaption("round · add bar · FAB")
    RoundButtonsState("rest")
    RoundButtonsState("pressed")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FabState("rest")
        FabState("pressed")
    }
}

/* ------------------------------------------------------------------------------------------------
 * Chips, switches (§4.7, §4.9).
 * ------------------------------------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipsKit() {
    KitCaption("aisle chips")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Produce", "Bakery", "Deli & meat", "Baking", "Breakfast", "Dairy & eggs").forEachIndexed { i, name ->
            AisleChip(i + 1, name, AisleLooks.of(i + 1), selected = i == 3, onClick = {})
        }
        AisleChip(null, "Everything else", AisleLooks.everythingElse, selected = false, onClick = {})
    }
    KitCaption("labels · selected · store aisle · shiny · source")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PicnicChip("Favourites", selected = true, onClick = {}, family = Families.sakura)
        PicnicChip("This week", selected = true, onClick = {}, family = Families.ramune)
        PicnicChip("Groceries", selected = false, onClick = {}, family = Families.lemon)
        PicnicChip("Picnic", selected = false, onClick = {}, family = Families.mint)
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { StoreAisleChip(2) }
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { RarityChip(jackpot = false) }
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { RarityChip(jackpot = true) }
        SourceChip("pancake recipe", onClick = {})
    }
}

/**
 * The kit under a large font (item 337, goldens kit_large_text_fs130 / fs200): the chips (a label, the longest default
 * aisle, a source, store A12, shiny and jackpot, a rarity chip squeezed into 56 dp), the candy, secondary and disabled
 * pills with the longest real labels, number circles of 24 and 20 dp, and a four-line ruled field, in a 332 dp column.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LargeTextKit() {
    Column(Modifier.width(332.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            PicnicChip("Favourites", selected = true, onClick = {}, family = Families.sakura)
            AisleChip(3, "Pasta, rice & international", AisleLooks.of(3), selected = false, onClick = {})
            SourceChip("pancake recipe", onClick = {})
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { StoreAisleChip(12) }
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { RarityChip(jackpot = false) }
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { RarityChip(jackpot = true) }
            Box(Modifier.heightIn(min = 48.dp).width(56.dp), contentAlignment = Alignment.Center) { RarityChip(jackpot = true) }
        }
        PicnicButton("Clear what I got", onClick = {})
        PicnicButton("Keep everything, uncheck it all", onClick = {}, style = PicnicButtonStyle.Secondary)
        PicnicButton("Stick all 12 on my list", onClick = {}, leadingStar = true)
        PicnicButton("Reading… 6 of 9", onClick = {}, enabled = false, busy = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberCircle(4, AisleLooks.of(4), size = 24.dp)
            NumberCircle(12, AisleLooks.of(12), size = 24.dp)
            NumberCircle(8, AisleLooks.of(8), size = 20.dp)
            NumberCircle(24, AisleLooks.of(24), size = 20.dp)
            NumberCircle(null, AisleLooks.everythingElse, size = 24.dp)
        }
        PicnicTextField("pancakes\nmaple syrup\nberries\nflour", {}, label = "Note", minLines = 4, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun SwitchKit() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        PicnicSwitch(checked = true, onCheckedChange = {})
        PicnicSwitch(checked = false, onCheckedChange = {})
        PicnicSwitch(checked = true, onCheckedChange = {}, enabled = false)
        PicnicSwitch(checked = false, onCheckedChange = {}, enabled = false)
        KeepSwitch(checked = false, onCheckedChange = {})
        KeepSwitch(checked = true, onCheckedChange = {})
    }
}

/* ------------------------------------------------------------------------------------------------
 * Checkbox, hanko, row states (§4.4, §4.5, §4.11 harness).
 * ------------------------------------------------------------------------------------------------ */

/** The hanko at −16°, −12° and −8° in its 48 dp target (checkbox_hanko_angles). */
@Composable
internal fun HankoAnglesKit() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckMark(checked = false, seed = "spot")
        for (angle in listOf(-16f, -12f, -8f)) {
            Box(Modifier.size(48.dp).background(PicnicTheme.colors.pad)) {
                HankoStamp(HankoText.GotIt, Modifier.offset(4.dp, 4.dp).rotate(angle))
            }
        }
    }
}

/** The four hanko texts auto-fitted (hanko_variants). */
@Composable
internal fun HankoVariantsKit() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for (t in HankoText.entries) {
            Box(Modifier.size(64.dp).background(PicnicTheme.colors.pad), contentAlignment = Alignment.Center) {
                HankoStamp(t, Modifier.rotate(-12f), size = 58.dp)
            }
        }
        Box(Modifier.size(48.dp).background(PicnicTheme.colors.pad), contentAlignment = Alignment.Center) {
            HankoStamp(HankoText.GotIt, Modifier.rotate(-10f), size = 34.dp)
        }
    }
}

/**
 * A row harness (the list's row is WP5's; this shows the theme pieces it is built from): sticker, name over detail, the
 * 48 dp checkbox target. States: unchecked with flags, pressed (the highlighter band behind the text), checked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RowHarness(
    name: String,
    detail: String,
    icon: String,
    checked: Boolean = false,
    pressed: Boolean = false,
    flags: Set<StickerFlag> = emptySet(),
    holo: Boolean = false,
    seed: String = name,
    aisleIndex: Int = 4,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemIcon(IconRef(icon), null, size = 52.dp, ghost = checked, flags = flags, holo = holo, aisleIndex = aisleIndex)
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .drawBehind {
                    if (pressed) {
                        drawRoundRect(c.pressedBand, Offset(-4.dp.toPx(), -3.dp.toPx()), Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                    }
                },
        ) {
            Text(
                name,
                style = t.itemName.copy(color = if (checked) c.inkSoft else c.ink),
                modifier = Modifier.drawBehind {
                    if (checked) {
                        val y = size.height * 0.55f
                        drawLine(c.hanko, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                    }
                },
            )
            // The chip wraps under the detail when a large font leaves it no room beside it.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(detail, style = t.itemDetail.copy(color = c.inkSoft))
                if (holo) RarityChip(jackpot = false)
            }
        }
        CheckMark(checked = checked, seed = seed)
    }
}

@Composable
internal fun CheckboxKit() {
    val c = PicnicTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .hardShadow(PicnicTheme.shapes.section, PicnicTheme.shadows)
            .clip(PicnicTheme.shapes.section)
            .background(Families.grape.pastel)
            .padding(start = 5.dp, end = 5.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().clip(PicnicTheme.shapes.sectionInner).background(c.pad)) {
            RowHarness("Strawberry jam", "500 mL jar", "jam", flags = setOf(StickerFlag.New))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
            RowHarness("Orange pekoe tea", "72 bags", "tea", pressed = true, flags = setOf(StickerFlag.Favourite))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
            RowHarness("Coffee beans", "340 g bag", "coffee", checked = true)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
            RowHarness("Maple syrup", "540 mL tin", "maple_syrup", flags = setOf(StickerFlag.Shiny), holo = true)
        }
    }
    HankoAnglesKit()
    HankoVariantsKit()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LandingSpot()
        StickerSpot()
        KeepSwitch(checked = true, onCheckedChange = {})
    }
}

/* ------------------------------------------------------------------------------------------------
 * Fields (§4.8).
 * ------------------------------------------------------------------------------------------------ */

/** One field state: rest, focus, error, disabled, multiline, key. */
@Composable
internal fun FieldState(state: String) {
    val c = PicnicTheme.colors
    Box(Modifier.fillMaxWidth().background(c.pad, PicnicTheme.shapes.card).padding(horizontal = 16.dp, vertical = 10.dp)) {
        when (state) {
            "rest" -> PicnicTextField("", {}, label = "Note:", placeholder = "Write a note…", example = "“pancakes from scratch”", singleLine = true)
            "focus" -> {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                PicnicTextField("pancakes from scratch", {}, label = "Note:", singleLine = true, modifier = Modifier.focusRequester(focus))
            }
            "error" -> KeyField(
                "sk-proj-3f9", {}, label = "Your OpenAI key", revealed = true, onRevealChange = {}, onPaste = {},
                isError = true, supportingText = "That key didn't work. Check it?",
            )
            "disabled" -> PicnicTextField("2.5 kg bag", {}, label = "Amount:", enabled = false, singleLine = true)
            "multiline" -> PicnicTextField(
                "Produce\nBakery\nDeli & meat\nAisle 2 - Baking", {}, label = "Aisles as text:", minLines = 4,
                textStyle = PicnicTheme.type.aislesText,
            )
            "key" -> KeyField("", {}, label = "Your OpenAI key", savedMask = "sk-•••••••••• 3f9a", onRevealChange = {}, onPaste = {})
        }
    }
}

@Composable
internal fun FieldsKit() {
    for (s in listOf("rest", "focus", "error", "disabled", "multiline", "key")) FieldState(s)
}

/* ------------------------------------------------------------------------------------------------
 * Toasts, dividers, washi, edges (§4.14, §4.15, §2.12).
 * ------------------------------------------------------------------------------------------------ */

@Composable
internal fun ToastState(state: String) {
    when (state) {
        "undo" -> StickyNote("Added 5 items to Saturday shop ♡", "Undo", {})
        "long" -> StickyNote("Added as typed · add your OpenAI key for smart sorting", null, {})
        "merged" -> StickyNote("2 items torn out", "Undo", {})
    }
}

@Composable
internal fun DividersKit() {
    Column(Modifier.fillMaxWidth().background(PicnicTheme.colors.pad, PicnicTheme.shapes.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionDivider(major = true)
        Text("Major: a perforation with scissors.", style = PicnicTheme.type.caption)
        SectionDivider()
        Text("Minor: the 1 dp rule line.", style = PicnicTheme.type.caption)
    }
}

/** Every washi tape (washi_all): the four everyday patterns, the six seasonal sheets and the raindrop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WashiKit() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WashiPattern.entries.forEachIndexed { i, p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WashiTape(p, length = 96.dp, rotation = if (i % 2 == 0) -3f else 4f)
                KitCaption(p.name)
            }
        }
    }
}

/** The add bar's perforated top edge (perforation_addbar): a pad strip with 4 dp bites every 10 dp and their rims. */
@Composable
internal fun PerforationKit() {
    val c = PicnicTheme.colors
    val shape = BittenEdgeShape(bite = 4.dp, every = 10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .hardShadow(shape, c.stickerShadow, DpOffset(0.dp, (-3).dp))
            .clip(shape)
            .background(c.pad)
            .drawBehind { drawPerforationRims() },
    ) {
        WashiTape(WashiPattern.Thanksgiving, Modifier.offset(x = (-6).dp, y = 2.dp), length = 84.dp, rotation = -4f)
        PicnicText("♡ picnic day ☆", PicnicTheme.type.doodle, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
    }
}

/** The postage-edged recipe card (postage_recipe_card): 6 dp bites every 10 dp on all four sides, a dashed inner frame. */
@Composable
internal fun PostageKit() {
    PostageCard(Modifier.fillMaxWidth()) {
        Text("Pancakes from scratch", style = PicnicTheme.type.cardTitle.copy(color = PicnicTheme.colors.ballpoint))
        PicnicText("from the kitchen of ♡ the web", PicnicTheme.type.transcript.copy(color = PicnicTheme.colors.ballpointSoft))
    }
}

/** A pad card with punch holes (padcard_punchholes). */
@Composable
internal fun PunchHolesKit() {
    PicnicSurface(Modifier.fillMaxWidth().height(150.dp)) {
        Column(Modifier.fillMaxSize().punchHoles(PicnicTheme.colors.binder).padding(start = 34.dp, top = 16.dp, end = 16.dp)) {
            Text("Surprises", style = PicnicTheme.type.cardTitle)
            Text("Easter eggs, sounds, haptics", style = PicnicTheme.type.caption)
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * LCD, loaders, empty state (§4.3, §4.21, §4.22, §13.1).
 * ------------------------------------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LcdKit() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LcdChip(LcdState(LcdMode.Count, count = 10, total = 24))
        LcdChip(LcdState(LcdMode.Pet, count = 12, total = 18))
        LcdChip(LcdState(LcdMode.Text, text = "SNACK?"))
        LcdChip(LcdState(LcdMode.Text, text = "PRINT★"))
        LcdChip(LcdState(LcdMode.Ok, text = "OK.."))
        LcdChip(LcdState(LcdMode.Timer, text = "0:12"))
        LcdChip(LcdState(LcdMode.Page, text = "2/3"))
        LcdChip(LcdState(LcdMode.Text, text = "PERFECT!"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for (p in listOf(PetPose.Rest, PetPose.Chomp1, PetPose.Dance2, PetPose.Dance4)) {
            Box(Modifier.background(PicnicTheme.colors.lcd, PicnicTheme.shapes.lcd).padding(6.dp)) { LcdPetSprite(p) }
        }
    }
}

@Composable
internal fun LoadersKit(reduced: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        PicnicLoader(LoaderKind.Gashapon, "Reading your note…", Modifier.weight(1f), family = Families.lemon)
        PicnicLoader(LoaderKind.BeeLoop, "Reading your list…", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        PicnicLoader(LoaderKind.LadybugRing, "Sorting", Modifier.weight(1f), count = "3/8")
        PicnicLoader(LoaderKind.BusySparkle, if (reduced) null else "Drawing", Modifier.weight(1f))
    }
}

@Composable
internal fun EmptyKit() {
    EmptyState(
        title = "A brand-new sticker sheet!",
        body = "Nothing on Saturday shop yet. Type it, snap your paper list, or just say it out loud.",
        action = { PicnicButton("Start my list", {}) },
    )
}

/* ------------------------------------------------------------------------------------------------
 * App bar, menu, sheet, puffy text (§2.7, §4.2, §4.16, §4.24).
 * ------------------------------------------------------------------------------------------------ */

@Composable
internal fun AppBarKit(collapsed: Boolean) {
    BinderTopBar(
        title = "Saturday shop",
        subtitle = "Hillcrest Market · 8 stops",
        lcd = LcdState(LcdMode.Count, count = 10, total = 24),
        onMore = {},
        onSunTap = {},
        state = remember { AppBarState(collapsed) },
    )
}

@Composable
internal fun SecondaryBarKit() {
    PicnicTopBar(
        title = "My store",
        subtitle = "walking order · colours · baskets",
        navigation = { PicnicIconButton(PicnicGlyph.Back, "Back to Saturday shop", {}) },
        actions = { PicnicButton("Done", {}, style = PicnicButtonStyle.Text) },
    )
}

@Composable
internal fun MenuKit() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        OverflowMenu(
            items = listOf(
                MenuItem("New trip", PicnicGlyph.Trip, {}, enabled = false, disabledState = "No trip yet"),
                MenuItem("Switch list…", PicnicGlyph.Lists, {}),
                MenuItem("Refresh", PicnicGlyph.Refresh, {}),
                MenuItem("My store & aisles", PicnicGlyph.Aisles, {}),
                MenuItem("My pantry", PicnicGlyph.Jar, {}),
                MenuItem("Sticker album", PicnicGlyph.Album, {}),
                MenuItem("Settings", PicnicGlyph.Settings, {}),
            ),
            onDismissRequest = {},
        )
    }
}

@Composable
internal fun SheetKit() {
    LetterSetSurface(
        footer = { PicnicButton("Stick all 5 on my list", {}, leadingStar = true) },
        handleScale = 1f,
    ) {
        Text("Start a new trip?", style = PicnicTheme.type.cardTitle)
        Spacer(Modifier.height(8.dp))
        Text("Today's sticker book goes into your album.", style = PicnicTheme.type.body)
        Spacer(Modifier.height(12.dp))
        PicnicButton("Keep everything, uncheck it all", {}, style = PicnicButtonStyle.Secondary)
        PicnicButton("Not yet", {}, style = PicnicButtonStyle.Text)
    }
}

@Composable
internal fun PuffyKit(long: Boolean = false) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    if (!long) {
        PuffyText("Saturday shop", t.screenTitle, fill = c.ink, stroke = 6.dp, heading = true)
        PuffyText("Everything else", t.aisleTab.copy(color = c.ink), fill = c.ink, stroke = 6.dp)
        PuffyText("NEXT", t.nextLabel, fill = c.ballpoint, stroke = 6.dp, strokeColor = Color.White, shadow = PicnicTheme.shadows.hs)
        PuffyText("List complete!", t.celebrationTitle, fill = Families.strawberry.mid, stroke = 9.dp, strokeColor = Color.White, shadow = PicnicTheme.shadows.hs2)
    } else {
        // Checklist 334: every stroked title at its longest real text, at font scale 1.0 and then 2.0, in the width its
        // caller gives it: a long list name in the binder top (shrunk to 20 sp, then cut), the wordmark, a tab label,
        // NEXT and the celebration title (two lines). Every stroke and shadow whole, every "…" after a whole word part.
        val base = androidx.compose.ui.platform.LocalDensity.current
        for (scale in listOf(1f, 2f)) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, scale),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(290.dp)) {
                        PuffyText("Thanksgiving dinner at the cottage", t.screenTitle, fill = c.ink, stroke = 6.dp, minFontSize = 20.sp)
                    }
                    Box(Modifier.width(290.dp)) { Wordmark("Kirakira Basket") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.width(150.dp)) {
                            PuffyText("Everything else", t.aisleTab.copy(color = c.ink), fill = c.ink, stroke = 6.dp)
                        }
                        PuffyText("NEXT", t.nextLabel, fill = c.ballpoint, stroke = 6.dp, strokeColor = Color.White, shadow = PicnicTheme.shadows.hs)
                    }
                    Box(Modifier.width(290.dp)) {
                        PuffyText(
                            "List complete!", t.celebrationTitle, fill = Families.strawberry.mid, stroke = 9.dp, strokeColor = Color.White,
                            shadow = PicnicTheme.shadows.hs2, minFontSize = 26.sp, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Item icons (§3.3) and tab patterns (§2.12).
 * ------------------------------------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemIconsKit() {
    KitCaption("52 · 40 · 30 · 26 · 22 dp")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for (s in listOf(52, 40, 30, 26, 22)) ItemIcon(IconRef("strawberry"), null, size = s.dp)
        ItemIcon(IconRef("apple"), null, size = 56.dp, face = true)
    }
    KitCaption("ghost · holo · pixel · mono")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemIcon(IconRef("flour"), null, ghost = true)
        ItemIcon(IconRef("coffee"), null, ghost = true)
        ItemIcon(IconRef("maple_syrup"), null, holo = true, flags = setOf(StickerFlag.Shiny))
        ItemIcon(IconRef("croissant"), null, holo = true)
        ItemIcon(IconRef("apple"), null, pixel = true)
        ItemIcon(IconRef("milk_bag"), null, mono = true)
    }
    KitCaption("capsule by aisle · gold jackpot")
    CapsuleRow()
    KitCaption("flags: new · favourite · shiny")
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ItemIcon(IconRef("baguette"), null, flags = setOf(StickerFlag.New))
        ItemIcon(IconRef("coffee"), null, flags = setOf(StickerFlag.Favourite))
        ItemIcon(IconRef("maple_syrup"), null, holo = true, flags = setOf(StickerFlag.Shiny))
        ItemIcon(IconRef("eggs"), null, flags = setOf(StickerFlag.New, StickerFlag.Favourite))
        ItemIcon(IconRef("butter"), null, drawing = true)
    }
}

/** Library stickers with their render-time hard shadow (itemicon_shadow_<variant>). */
@Composable
internal fun ShadowRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (k in listOf("strawberry", "milk_bag", "maple_syrup", "croissant", "carrot")) ItemIcon(IconRef(k), null, size = 52.dp)
    }
}

/** Peeled ghosts (itemicon_ghost_<variant>). */
@Composable
internal fun GhostRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (k in listOf("flour", "sugar", "coffee", "frozen_vegetables", "apple")) ItemIcon(IconRef(k), null, size = 52.dp, ghost = true)
    }
}

/** The corner flags (row_flags): new star, favourite heart, shiny star (+ chip), both. */
@Composable
internal fun FlagsRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 8.dp, end = 8.dp)) {
        ItemIcon(IconRef("baguette"), null, flags = setOf(StickerFlag.New))
        ItemIcon(IconRef("coffee"), null, flags = setOf(StickerFlag.Favourite))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ItemIcon(IconRef("maple_syrup"), null, holo = true, flags = setOf(StickerFlag.Shiny))
            RarityChip(jackpot = false)
        }
        ItemIcon(IconRef("eggs"), null, flags = setOf(StickerFlag.New, StickerFlag.Favourite))
    }
}

/** The capsule sentinel tinted by every aisle, the neutral one and gold (itemicon_capsule_aisles). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CapsuleRow() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in -1 until 10) ItemIcon(IconRef(StickerManifest.CAPSULE), null, size = 46.dp, aisleIndex = i)
        ItemIcon(IconRef(StickerManifest.CAPSULE), null, size = 46.dp, jackpot = true)
        ItemIcon(IconRef("paper_bag"), null, size = 46.dp, aisleIndex = 2)
    }
}

/**
 * A tab sample (the index tab itself is WP5's list piece): the aisle pastel, a 3 dp white border, radius 12 / 4, hs, the
 * number circle, the name, and the pattern on the right 26 dp only.
 */
@Composable
internal fun TabSample(position: Int, name: String, modifier: Modifier = Modifier, onTray: Boolean = false) {
    val look = AisleLooks.of(position)
    val shape = PicnicTheme.shapes.tab
    Row(
        modifier
            .height(if (onTray) 38.dp else 32.dp)
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(Color.White)
            .padding(3.dp)
            .clip(shape)
            .background(look.family.pastel)
            .tabPattern(look)
            .padding(start = 3.dp, end = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NumberCircle(position, look, size = if (onTray) 24.dp else 22.dp)
        Text(
            name,
            style = PicnicTheme.type.aisleTab.copy(color = if (onTray) PicnicTheme.colors.ballpoint else look.family.trace),
            maxLines = 1,
        )
    }
}

@Composable
internal fun TabPatternsKit() {
    val names = listOf("Produce", "Bakery", "Deli & meat", "Baking", "Breakfast", "Dairy & eggs", "Frozen", "Household", "Snacks", "Drinks")
    names.forEachIndexed { i, n -> TabSample(i + 1, n) }
}

/* ------------------------------------------------------------------------------------------------
 * Ground (§4.1, §2.12).
 * ------------------------------------------------------------------------------------------------ */

/**
 * A reward card in holo foil (§2.11): the sweep brush with its glint at [glint] (0..1, stepped). The frames of
 * holo_card_frames sample the glint's 12 steps.
 */
@Composable
internal fun HoloCardSample(glint: Float) {
    val shape = PicnicTheme.shapes.nextCard
    Box(
        Modifier
            .size(width = 236.dp, height = 120.dp)
            .hardShadow(shape, PicnicTheme.shadows, large = true)
            .clip(shape)
            .background(Color.White)
            .padding(3.dp)
            .clip(shape)
            .drawBehind {
                drawRect(holoBrush(Offset(size.width / 2f, size.height / 2f)))
                drawGlint(glint)
            },
        contentAlignment = Alignment.Center,
    ) {
        PuffyText("bonus sticker!", PicnicTheme.type.cardTitle, PicnicTheme.colors.ballpoint, 6.dp, strokeColor = Color.White)
    }
}

/** A bonus-stage pixel row and holo vector stickers (itemicon_pixel, itemicon_holo_vector). */
@Composable
internal fun StickerLooksRow(look: StickerLook) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (k in listOf("strawberry", "maple_syrup", "croissant", "apple", "milk_bag")) {
            ItemIcon(IconRef(k), null, size = 52.dp, holo = look == StickerLook.Holo, pixel = look == StickerLook.Pixel, mono = look == StickerLook.Mono)
        }
    }
}

/** The binder ground with a pinned plate repainting it in line (ground_pinstripe_<variant>). */
@Composable
internal fun GroundKit() {
    Box(Modifier.fillMaxWidth().height(160.dp)) {
        Box(
            Modifier
                .offset(x = 23.dp, y = 37.dp)
                .size(width = 200.dp, height = 53.dp)
                .groundBacking(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(PicnicTheme.shapes.pinnedPlate)
                    .background(Families.melon.pastel),
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Previews: one per group, Day and Glow.
 * ------------------------------------------------------------------------------------------------ */

@Preview(name = "Buttons - day", widthDp = 360, heightDp = 760)
@Composable
private fun ButtonsDayPreview() = KitFrame(ThemeVariant.Day) { ButtonsKit() }

@Preview(name = "Buttons - glow", widthDp = 360, heightDp = 760)
@Composable
private fun ButtonsGlowPreview() = KitFrame(ThemeVariant.Glow) { ButtonsKit() }

@Preview(name = "Chips and switches - day", widthDp = 360, heightDp = 420)
@Composable
private fun ChipsDayPreview() = KitFrame(ThemeVariant.Day) { ChipsKit(); SwitchKit() }

@Preview(name = "Chips and switches - glow", widthDp = 360, heightDp = 420)
@Composable
private fun ChipsGlowPreview() = KitFrame(ThemeVariant.Glow) { ChipsKit(); SwitchKit() }

@Preview(name = "Checkbox and hanko - day", widthDp = 360, heightDp = 560)
@Composable
private fun CheckboxDayPreview() = KitFrame(ThemeVariant.Day) { CheckboxKit() }

@Preview(name = "Checkbox and hanko - glow", widthDp = 360, heightDp = 560)
@Composable
private fun CheckboxGlowPreview() = KitFrame(ThemeVariant.Glow) { CheckboxKit() }

@Preview(name = "Fields - day", widthDp = 360, heightDp = 760)
@Composable
private fun FieldsDayPreview() = KitFrame(ThemeVariant.Day) { FieldsKit() }

@Preview(name = "Fields - glow", widthDp = 360, heightDp = 760)
@Composable
private fun FieldsGlowPreview() = KitFrame(ThemeVariant.Glow) { FieldsKit() }

@Preview(name = "Paper - day", widthDp = 360, heightDp = 760)
@Composable
private fun PaperDayPreview() = KitFrame(ThemeVariant.Day) { ToastState("undo"); DividersKit(); PerforationKit(); PostageKit() }

@Preview(name = "Paper - glow", widthDp = 360, heightDp = 760)
@Composable
private fun PaperGlowPreview() = KitFrame(ThemeVariant.Glow) { ToastState("undo"); DividersKit(); PerforationKit(); PostageKit() }

@Preview(name = "LCD and loaders - day", widthDp = 360, heightDp = 560)
@Composable
private fun LcdDayPreview() = KitFrame(ThemeVariant.Day) { LcdKit(); LoadersKit() }

@Preview(name = "LCD and loaders - glow", widthDp = 360, heightDp = 560)
@Composable
private fun LcdGlowPreview() = KitFrame(ThemeVariant.Glow) { LcdKit(); LoadersKit() }

@Preview(name = "Empty state - day", widthDp = 360, heightDp = 560)
@Composable
private fun EmptyDayPreview() = KitFrame(ThemeVariant.Day) { EmptyKit() }

@Preview(name = "Empty state - glow", widthDp = 360, heightDp = 560)
@Composable
private fun EmptyGlowPreview() = KitFrame(ThemeVariant.Glow) { EmptyKit() }

@Preview(name = "Bars and menu - day", widthDp = 360, heightDp = 640)
@Composable
private fun BarsDayPreview() = KitFrame(ThemeVariant.Day) { AppBarKit(false); AppBarKit(true); SecondaryBarKit(); MenuKit() }

@Preview(name = "Bars and menu - glow", widthDp = 360, heightDp = 640)
@Composable
private fun BarsGlowPreview() = KitFrame(ThemeVariant.Glow) { AppBarKit(false); AppBarKit(true); SecondaryBarKit(); MenuKit() }

@Preview(name = "Item icons - day", widthDp = 360, heightDp = 560)
@Composable
private fun IconsDayPreview() = KitFrame(ThemeVariant.Day) { ItemIconsKit() }

@Preview(name = "Item icons - glow", widthDp = 360, heightDp = 560)
@Composable
private fun IconsGlowPreview() = KitFrame(ThemeVariant.Glow) { ItemIconsKit() }

@Preview(name = "Tabs and lettering - day", widthDp = 360, heightDp = 760)
@Composable
private fun TabsDayPreview() = KitFrame(ThemeVariant.Day) { Wordmark("Kirakira Basket"); PuffyKit(); TabPatternsKit() }

@Preview(name = "Tabs and lettering - glow", widthDp = 360, heightDp = 760)
@Composable
private fun TabsGlowPreview() = KitFrame(ThemeVariant.Glow) { Wordmark("Kirakira Basket"); PuffyKit(); TabPatternsKit() }

package earth.cora.picnic.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import earth.cora.picnic.R
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.INLINE_GLYPHS
import earth.cora.picnic.ui.theme.MenuItem
import earth.cora.picnic.ui.theme.OverflowMenu
import earth.cora.picnic.ui.theme.PadPatch
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicSwitch
import earth.cora.picnic.ui.theme.PicnicText
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.PicnicTopBar
import earth.cora.picnic.ui.theme.PuffyText
import earth.cora.picnic.ui.theme.SectionDivider
import earth.cora.picnic.ui.theme.ToastHost
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.punchHoles

/*
 * The chrome of WP8's secondary screens (docs/DESIGN_STICKER.md §4.2, §4.24, §2.12): the binder ground, the binder top
 * (Back, puffy title, subtitle on a pad patch, "Done" or More), pad cards with punch holes, switch rows and the toast
 * host. Built only from the theme kit (WP4); screens never hand-roll a card or a button (NoMaterialDefaultsTest).
 */

/** Side gutter of the secondary screens' content. */
val ScreenGutter: Dp = 12.dp

/**
 * A secondary screen (§4.2): the binder (full bleed, under the system bars), the binder top kept clear of the status bar
 * (a 40 dp Back round button, the puffy [title] that shrinks 24 → 20 sp and then ellipsizes, the [subtitle] on a pad
 * patch, [actions] such as the "Done" text button or the More button), then [content] in the rest of the window, clipped
 * there so nothing ever scrolls above or beside the title. [toasts] drop in at the bottom-left above the navigation bar;
 * [overlay] draws above everything (sheets, the rationale).
 */
@Composable
fun SecondaryScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    toasts: ToastHostState? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    subtitleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    NoMaterialPress { PicnicBackground(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val barInsets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            if (subtitle != null && subtitle.any { it in INLINE_GLYPHS }) {
                GlyphSubtitleTopBar(title, subtitle, onBack, barInsets, actions, subtitleTrailing)
            } else {
                PicnicTopBar(
                    title = title,
                    subtitle = subtitle,
                    modifier = barInsets,
                    navigation = { BackButton(onBack) },
                    actions = actions,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                content = content,
            )
        }
        if (toasts != null) ToastHost(toasts, bottomInset = bottom)
        overlay()
    } }
}

/**
 * [PicnicTopBar]'s pieces for a subtitle that carries inline glyphs ("★ 64 of 216 collected · 3 holo"): the same Back
 * button and puffy title (one line, 24 → 20 sp, then an ellipsis, §4.2), then the subtitle on its pad patch in a
 * row of its own under the title, so it never shares its width with the actions (the album's LCD and More) and is never
 * cut; [trailing] (the album's LCD, with large text) follows it there, or wraps under it. [PicnicText] draws its star as
 * an inline vector (§2.7).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlyphSubtitleTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier,
    actions: @Composable RowScope.() -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = PicnicTheme.dimens.appBar)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Box(Modifier.weight(1f).padding(start = 4.dp)) {
                // §4.2: one line, shrinking 24 → 20 sp to fit, then an ellipsis (PuffyText keeps the stroke whole and
                // reports the cut through TextOverflowed). At 2.0 the bar grows and the subtitle wraps under it.
                PuffyText(title, style = t.screenTitle, fill = c.ink, stroke = 6.dp, minFontSize = 20.sp, maxLines = 1, heading = true)
            }
            actions()
        }
        FlowRow(
            Modifier.padding(start = 52.dp, end = 8.dp, top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            PadPatch { PicnicText(subtitle, style = t.appBarSubtitle.copy(color = c.onPadSoft)) }
            trailing?.invoke()
        }
    }
}

/**
 * The scrolling column of a secondary screen:[ScreenGutter] sides, room for the last card's hard shadow, the navigation
 * bar and the keyboard below it.
 */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(state)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
            .padding(start = ScreenGutter, end = ScreenGutter, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * A pad card (§2.12, §6.8): a kiss-cut white card (Glow: the navy page) with a hard shadow and punch holes down its left
 * edge (8 dp, binder coloured, at x 13 dp every 30 dp), so its content starts at 32 dp, clear of the holes. An optional
 * DynaPuff [title] (a heading) is led by a line [glyph] in [glyphTint] (line glyphs, never sticker art: the page-sticker
 * budget stays with the screen's own decor, §4.1).
 */
@Composable
fun PadCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    glyph: PicnicGlyph? = null,
    glyphTint: Color = PicnicTheme.colors.ink,
    trailing: @Composable RowScope.() -> Unit = {},
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PicnicTheme.colors
    PicnicSurface(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .punchHoles(c.binder)
                .padding(start = 32.dp, end = 14.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (glyph != null) Glyph(glyph, contentDescription = null, tint = glyphTint, size = 22.dp)
                    Text(
                        title,
                        style = PicnicTheme.type.cardTitle.copy(color = c.ink),
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    trailing()
                }
            }
            content()
        }
    }
}

/**
 * One switch row of a pad card (§4.9, §6.8): the [label] (Zen Maru 700 16 sp) over its one-line [hint] (13 sp), the
 * switch at the right. The whole row is one Role.Switch node (TalkBack reads label, hint and state), and a tap anywhere
 * on it toggles, so the target is the full row.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    // The row is the one switch: one keyboard stop, its focus drawn round the pill (§15).
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                // The pill wears the focus ring (one ring a row).
                indication = highlighterPress(ring = false),
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = t.fieldValue.copy(color = c.ink))
            if (hint != null) Text(hint, style = t.caption.copy(color = c.inkSoft))
        }
        PicnicSwitch(checked = checked, onCheckedChange = null, enabled = enabled, interactionSource = interaction, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** The 1 dp rule between rows of a card (§4.14 minor divider). */
@Composable
fun RowRule(modifier: Modifier = Modifier) = SectionDivider(modifier)

/** The binder top's text button ("Done", §4.2): at least 48 × 48 dp (§15), its short label centred in that target. */
@Composable
fun DoneButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PicnicButton(
        stringResource(R.string.screens_done),
        onClick = onClick,
        // A few dp clear of the subtitle's patch when a large font brings them side by side.
        modifier = modifier.padding(start = 4.dp, end = 6.dp).widthIn(min = 48.dp),
        style = PicnicButtonStyle.Text,
        enabled = enabled,
        // On the binder beside the title: on its pad patch, never straight on the pinstripes (§4.1).
        padPatch = true,
    )
}

/**
 * The More round button with its overflow menu (§4.24): a kiss-cut pad card that pops from the button's corner. Used by
 * the Album and Settings screens ("Back to <list>", "My store & aisles", "Settings" / "Sticker album").
 */
@Composable
fun MoreMenuButton(items: List<MenuItem>, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    Box {
        PicnicIconButton(PicnicGlyph.More, stringResource(R.string.screens_more_options), onClick = { onExpandedChange(true) })
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 0),
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true),
            ) {
                Box(Modifier.padding(top = 52.dp, end = 4.dp, bottom = 8.dp, start = 8.dp)) {
                    OverflowMenu(items, onDismissRequest = { onExpandedChange(false) })
                }
            }
        }
    }
}

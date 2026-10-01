package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/*
 * THEME COMPONENT KIT: CONTRACT (signatures) between app-shell and app-screens.
 * Bodies here are skeleton placeholders; app-shell replaces them with the picnic styling
 * (hard offset shadows, ink outlines, patterns). Screens build UI from these + Decor + ItemIcon/AisleBasket.
 */

/** Card/row container: fill + ink outline + hard offset shadow (no blur elevation). */
@Composable
fun PicnicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PicnicTheme.shapes.card,
    color: Color = PicnicTheme.colors.surface,
    outlined: Boolean = true,
    shadow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    Box(
        modifier
            .clip(shape)
            .background(color, shape)
            .then(if (outlined) Modifier.border(d.outline, c.outline, shape) else Modifier),
        content = content,
    )
}

enum class PicnicButtonStyle { Primary, Secondary, Text }

@Composable
fun PicnicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PicnicButtonStyle = PicnicButtonStyle.Primary,
    enabled: Boolean = true,
    glyph: PicnicGlyph? = null,
) {
    val c = PicnicTheme.colors
    val fill = when (style) {
        PicnicButtonStyle.Primary -> if (enabled) c.primary else c.surfaceSunken
        PicnicButtonStyle.Secondary -> c.surface
        PicnicButtonStyle.Text -> Color.Transparent
    }
    val ink = if (style == PicnicButtonStyle.Primary && enabled) c.onPrimary else c.ink
    Row(
        modifier
            .defaultMinSize(minHeight = PicnicTheme.dimens.touchTarget)
            .clip(PicnicTheme.shapes.button)
            .background(fill)
            .then(if (style != PicnicButtonStyle.Text) Modifier.border(PicnicTheme.dimens.outline, c.outline, PicnicTheme.shapes.button) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (glyph != null) Glyph(glyph, contentDescription = null, tint = ink, size = 20.dp)
        Text(text, style = PicnicTheme.type.label, color = ink)
    }
}

/** 48 dp round glyph button. [emphasized] = primary-filled (e.g. send, mic). */
@Composable
fun PicnicIconButton(
    glyph: PicnicGlyph,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val c = PicnicTheme.colors
    Box(
        modifier
            .size(PicnicTheme.dimens.touchTarget)
            .clip(PicnicTheme.shapes.badge)
            .background(if (emphasized) c.primary else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, contentDescription, tint = if (emphasized) c.onPrimary else c.ink)
    }
}

@Composable
fun PicnicChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = PicnicTheme.colors
    Row(
        modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(PicnicTheme.shapes.chip)
            .background(if (selected) c.highlight else c.surface)
            .border(PicnicTheme.dimens.outline, c.outline, PicnicTheme.shapes.chip)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(text, style = PicnicTheme.type.label, color = if (selected) c.onHighlight else c.ink)
    }
}

@Composable
fun PicnicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Text(label, style = t.label, color = c.ink)
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(PicnicTheme.shapes.field)
                .background(c.surfaceSunken)
                .border(PicnicTheme.dimens.outline, if (isError) c.error else c.outline, PicnicTheme.shapes.field)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, style = t.body, color = c.inkFaint)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    textStyle = t.body.copy(color = c.ink),
                    cursorBrush = SolidColor(c.ink),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
        if (supportingText != null) Text(supportingText, style = t.caption, color = if (isError) c.error else c.inkSoft)
    }
}

/** Screen title card. [bottom] is a slot for TripProgress or tabs. Applies no insets itself. */
@Composable
fun PicnicTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
) {
    val c = PicnicTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = PicnicTheme.dimens.gutter, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            navigation?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = PicnicTheme.type.screenTitle, color = c.ink)
                if (subtitle != null) Text(subtitle, style = PicnicTheme.type.caption, color = c.inkSoft)
            }
            actions()
        }
        bottom?.invoke()
    }
}

@Composable
fun PicnicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = PicnicTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            uncheckedThumbColor = c.inkSoft,
            uncheckedTrackColor = c.surfaceSunken,
            uncheckedBorderColor = c.outline,
        ),
    )
}

/** Use as SnackbarHost(hostState) { PicnicSnackbar(it) }. */
@Composable
fun PicnicSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    PicnicSurface(modifier.padding(PicnicTheme.dimens.gutter), color = c.surfaceRaised) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(data.visuals.message, style = PicnicTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
            val action = data.visuals.actionLabel
            if (action != null) {
                PicnicButton(action, onClick = { data.performAction() }, style = PicnicButtonStyle.Text)
            }
        }
    }
}

/** Drag handle for ModalBottomSheet(dragHandle = { SheetHandle() }). */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 5.dp)
            .clip(PicnicTheme.shapes.chip)
            .background(PicnicTheme.colors.inkFaint),
    )
}

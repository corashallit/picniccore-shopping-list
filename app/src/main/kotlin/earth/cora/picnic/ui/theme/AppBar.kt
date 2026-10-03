package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The binder top (docs/DESIGN_STICKER.md §4.2, items 24, 51, 52) and the overflow menu (§4.24). Owner: WP4.
 * No bar shape: a puffy screen title on the binder, the sun sticker, the subtitle on a pad patch, the LCD chip and a
 * 40 dp More round button.
 */

/**
 * The list app bar's collapse state (§4.2): 72 dp expanded, 56 dp collapsed on scroll. Collapse follows the list's
 * scroll through [nestedScrollConnection] (put it on the list's parent); it snaps open at the top ([atTop]).
 */
@Stable
class AppBarState(initiallyCollapsed: Boolean = false) {
    var collapsed by mutableStateOf(initiallyCollapsed)

    /** Tell the bar the list is at its top (it snaps open). */
    fun atTop(top: Boolean) {
        if (top) collapsed = false
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y < -0.5f) collapsed = true
            if (available.y > 0.5f) collapsed = false
            return Offset.Zero
        }
    }
}

@Composable
fun rememberAppBarState(collapsed: Boolean = false): AppBarState = remember { AppBarState(collapsed) }

/**
 * The list's binder top (§4.2). [title] is a puffy DynaPuff title (24 sp; shrinks to 20 sp to stay on one line, then
 * ellipsizes; 20 sp collapsed); the sun (Day, Golden; the moon in Dusk and Glow, static in Glow) bounces when tapped
 * ([onSunTap], egg 1); the [subtitle] ("★ Hillcrest Market · 8 stops") sits on a pad patch led by a 13 dp lemon star and
 * hides when collapsed; [lcd] and the More button stay. At font scale 2.0 the bar grows and the subtitle wraps.
 */
@Composable
fun BinderTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    lcd: LcdState? = null,
    onLcdHold: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    state: AppBarState = rememberAppBarState(),
    onSunTap: (() -> Unit)? = null,
    /** Golden hour: minutes left before sunset (the sun sinks 0.5 dp per minute left). */
    goldenMinutesLeft: Int? = null,
) {
    val reduced = LocalReducedMotion.current
    val collapsed = state.collapsed
    val minHeight by animateDpAsState(
        if (collapsed) PicnicTheme.dimens.appBarCollapsed else PicnicTheme.dimens.appBar,
        animationSpec = if (reduced) snap() else tween(PicnicMotion.standard.durationMillis, easing = PicnicMotion.standard.easing),
        label = "appBar",
    )
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(start = 8.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PuffyText(
                    title,
                    style = if (collapsed) t.screenTitleCollapsed else t.screenTitle,
                    fill = c.ink,
                    stroke = 6.dp,
                    minFontSize = 20.sp,
                    heading = true,
                    modifier = Modifier.weight(1f, fill = false),
                )
                SunSticker(onTap = onSunTap, goldenMinutesLeft = goldenMinutesLeft)
            }
            if (subtitle != null && !collapsed) {
                PadPatch {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FlatStar(Modifier.size(13.dp), fill = Families.lemon.mid)
                        Text(subtitle, style = t.appBarSubtitle.copy(color = c.onPadSoft))
                    }
                }
            }
        }
        if (lcd != null) LcdChip(lcd, Modifier.padding(horizontal = 2.dp), onHold = onLcdHold)
        if (onMore != null) PicnicIconButton(PicnicGlyph.More, stringResource(R.string.theme_more_options), onMore)
    }
}

/**
 * The sun sticker by the title (§4.2): 22 dp in a 48 dp target. Day and Golden show the sun (Golden's sinks 0.5 dp per
 * minute left), Dusk and Glow a full moon (never a crescent; static in Glow). Each tap bounces it (1.25, −20° → 1.1,
 * +10° → 1; 2 steps, 240 ms). TalkBack: "Sun sticker", no hint (it is an egg, not a feature).
 */
@Composable
fun SunSticker(modifier: Modifier = Modifier, onTap: (() -> Unit)? = null, goldenMinutesLeft: Int? = null) {
    val variant = PicnicTheme.variant
    val reduced = LocalReducedMotion.current
    val moon = variant == ThemeVariant.Dusk || variant == ThemeVariant.Glow
    val label = stringResource(if (moon) R.string.theme_moon_sticker else R.string.theme_sun_sticker)
    var taps by remember { mutableIntStateOf(0) }
    var pose by remember { mutableStateOf(Pose()) }
    LaunchedEffect(taps) {
        if (taps == 0 || reduced) return@LaunchedEffect
        withContext(FixedMotionScale) {
            val poses = PicnicMotion.sunBouncePoses
            for (p in poses.dropLast(1)) {
                pose = p
                delay(PicnicMotion.sunBounce.durationMillis / 2L)
            }
            pose = poses.last()
        }
    }
    val sink = if (variant == ThemeVariant.Golden && goldenMinutesLeft != null) ((30 - goldenMinutesLeft).coerceIn(0, 30) * 0.5f).dp else 0.dp
    Box(
        modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (onTap != null) role = Role.Button
            }
            .then(
                if (onTap != null) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                        taps++
                        onTap()
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        DecorSticker(
            if (moon) "moon" else "sun",
            Modifier
                .offset(y = sink)
                .graphicsLayer { scaleX = pose.scale; scaleY = pose.scale; rotationZ = pose.rotation },
            size = 22.dp,
        )
    }
}

/**
 * A secondary screen's top (§4.2) and the v1 title card: no bar shape. [navigation] (the 40 dp Back round button), the
 * puffy title (24 sp → 20 sp, then ellipsis), an optional [subtitle] on a pad patch, [actions] (a text button "Done")
 * and an optional [bottom] slot. Applies no window insets itself.
 */
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
    val t = PicnicTheme.type
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = PicnicTheme.dimens.appBar)
            .padding(start = if (navigation == null) 12.dp else 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            navigation?.invoke()
            Column(Modifier.weight(1f).padding(start = if (navigation == null) 0.dp else 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PuffyText(title, style = t.screenTitle, fill = c.ink, stroke = 6.dp, minFontSize = 20.sp, heading = true)
                if (subtitle != null) {
                    PadPatch { Text(subtitle, style = t.appBarSubtitle.copy(color = c.onPadSoft)) }
                }
            }
            actions()
        }
        if (bottom != null) Box(Modifier.padding(top = 4.dp)) { bottom() }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Overflow menu (§4.24).
 * ------------------------------------------------------------------------------------------------ */

/** One overflow menu item: 48 dp with a glyph; disabled at 50 % with its [disabledState] ("No trip yet"). */
@Immutable
data class MenuItem(
    val label: String,
    val glyph: PicnicGlyph,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val disabledState: String? = null,
)

/**
 * The overflow menu's card (§4.24): a kiss-cut pad card (radius 10, hs2) that pops from 90 % (220 ms
 * `cubic-bezier(.3, 1.5, .5, 1)`) out of the More button's corner; a fade under reduced motion. Place it in the screen's
 * overlay at the More button's anchor; [onDismissRequest] closes it after an item.
 */
@Composable
fun OverflowMenu(items: List<MenuItem>, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, width: Dp = 224.dp) {
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        withContext(FixedMotionScale) {
            pop.animateTo(1f, if (reduced) tween(PicnicMotion.rmFade.durationMillis) else tween(PicnicMotion.menuPop.durationMillis, easing = PicnicMotion.menuPop.easing))
        }
    }
    PicnicSurface(
        modifier
            .width(width)
            .graphicsLayer {
                transformOrigin = TransformOrigin(1f, 0f)
                if (reduced) {
                    alpha = pop.value
                } else {
                    val s = 0.9f + 0.1f * pop.value
                    scaleX = s
                    scaleY = s
                    alpha = pop.value.coerceIn(0f, 1f)
                }
            },
        large = true,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            for (item in items) MenuRow(item, onDismissRequest)
        }
    }
}

@Composable
private fun ColumnScope.MenuRow(item: MenuItem, onDismissRequest: () -> Unit) {
    val c = PicnicTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (item.enabled) {
                    Modifier.clickable(role = Role.Button) {
                        item.onClick()
                        onDismissRequest()
                    }
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        disabled()
                        item.disabledState?.let { stateDescription = it }
                    }
                },
            )
            .alpha(if (item.enabled) 1f else 0.5f)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Glyph(item.glyph, null, tint = c.ink, size = 22.dp)
        Column {
            Text(item.label, style = PicnicTheme.type.label.copy(color = c.ink, fontSize = 15.sp))
            if (!item.enabled && item.disabledState != null) {
                Text(item.disabledState, style = PicnicTheme.type.caption.copy(color = c.inkSoft), modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

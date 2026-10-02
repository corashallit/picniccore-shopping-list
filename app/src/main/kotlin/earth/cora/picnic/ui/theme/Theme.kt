package earth.cora.picnic.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CornerBasedShape
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.icons.IconImages
import earth.cora.picnic.platform.DefaultTimeouts
import earth.cora.picnic.platform.Timeouts
import java.time.LocalTime

val LocalPicnicColors = staticCompositionLocalOf { DayColors }
val LocalPicnicTypography = staticCompositionLocalOf { typographyFor(DayColors) }
val LocalPicnicShapes = staticCompositionLocalOf { PicnicShapeTokens }
val LocalPicnicDimens = staticCompositionLocalOf { PicnicDimens() }
val LocalPicnicShadows = staticCompositionLocalOf { DayShadows }
val LocalPicnicMotion = staticCompositionLocalOf { ThemeMotion(reduced = false) }

/**
 * Reduced motion (§8.8): on when the animator OR the transition scale is 0, read explicitly from Settings.Global at
 * start and on every resume (MainActivity, [readReducedMotion]). Every movement then becomes a 120 ms fade at the
 * same beat; holds never shrink.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** The resolved theme variant (§2.5). */
val LocalThemeVariant = staticCompositionLocalOf { ThemeVariant.Day }

/**
 * Android's timing settings (§9, §15): recommended timeouts and the hold threshold. MainActivity provides
 * AppContainer.timeouts (WP10); previews and tests get the defaults.
 */
val LocalTimeouts = staticCompositionLocalOf<Timeouts> { DefaultTimeouts }

/**
 * The four looks (§2.1, §2.4, §2.5): Day "Mint Binder", Glow "Glow-in-the-dark" (dark), Dusk (egg 1) and Golden
 * (egg 12). Resolved by ThemeVariantSource (WP9, through [resolveThemeVariant]); the celebration always composes in
 * Day.
 */
enum class ThemeVariant {
    Day, Glow, Dusk, Golden;

    /** Bars with dark icons (light ground): Day, Dusk and Golden. Glow gets light icons. */
    val lightBars: Boolean get() = this != Glow
}

/**
 * The §2.5 resolution order, evaluated at composition and on every minute tick (pure; ThemeVariantTest):
 * 1. Easter eggs on and local time in [23:00, 04:00) → Glow (+ midnight extras, egg 5);
 * 2. system dark mode → Glow;
 * 3. Easter eggs on and the Dusk binder saved (egg 1) → Dusk;
 * 4. Easter eggs on and within the 30 min before [sunset] (egg 12) → Golden;
 * 5. otherwise Day.
 * With Easter eggs off no egg effect renders (the saved Dusk flag is kept for when they return).
 */
fun resolveThemeVariant(
    eggs: Boolean,
    systemDark: Boolean,
    duskSaved: Boolean,
    now: LocalTime,
    sunset: LocalTime?,
): ThemeVariant = when {
    isMidnight(eggs, now) -> ThemeVariant.Glow
    systemDark -> ThemeVariant.Glow
    eggs && duskSaved -> ThemeVariant.Dusk
    eggs && sunset != null && isGoldenHour(now, sunset) -> ThemeVariant.Golden
    else -> ThemeVariant.Day
}

/** Egg 5's window: Easter eggs on and local time in [23:00, 04:00). */
fun isMidnight(eggs: Boolean, now: LocalTime): Boolean =
    eggs && (!now.isBefore(MIDNIGHT_START) || now.isBefore(MIDNIGHT_END))

/** Egg 12's window: the 30 minutes before sunset, [sunset − 30 min, sunset). */
fun isGoldenHour(now: LocalTime, sunset: LocalTime): Boolean {
    val start = sunset.minusMinutes(GOLDEN_MINUTES)
    return if (start.isBefore(sunset)) !now.isBefore(start) && now.isBefore(sunset)
    else !now.isBefore(start) || now.isBefore(sunset) // a sunset just after midnight (never in Ontario)
}

private val MIDNIGHT_START: LocalTime = LocalTime.of(23, 0)
private val MIDNIGHT_END: LocalTime = LocalTime.of(4, 0)
private const val GOLDEN_MINUTES = 30L

/** Bitmap loader for ItemIcon's live stickers. MainActivity provides AppContainer.iconImages; previews get none. */
val LocalIconImages = staticCompositionLocalOf<IconImages> { IconImages.None }

/** Icon catalog (category fallbacks for keys without library art). */
val LocalIconCatalog = staticCompositionLocalOf { IconCatalog.default }

/** Token access for screens: PicnicTheme.colors.ink, PicnicTheme.type.itemName, ... */
object PicnicTheme {
    val colors: PicnicColors @Composable @ReadOnlyComposable get() = LocalPicnicColors.current
    val type: PicnicTypography @Composable @ReadOnlyComposable get() = LocalPicnicTypography.current
    val shapes: PicnicShapes @Composable @ReadOnlyComposable get() = LocalPicnicShapes.current
    val dimens: PicnicDimens @Composable @ReadOnlyComposable get() = LocalPicnicDimens.current
    val shadows: PicnicShadows @Composable @ReadOnlyComposable get() = LocalPicnicShadows.current
    val motion: ThemeMotion @Composable @ReadOnlyComposable get() = LocalPicnicMotion.current
    val variant: ThemeVariant @Composable @ReadOnlyComposable get() = LocalThemeVariant.current
    val reducedMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/** v1 root theme: dark = Glow, light = Day. Kept for v1 call sites (screens' tests and previews). */
@Composable
fun PicnicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    PicnicTheme(variant = if (darkTheme) ThemeVariant.Glow else ThemeVariant.Day, reducedMotion = reducedMotion, content = content)
}

/**
 * Root theme (D12, §19.2). Provides the Picnic tokens for [variant] AND a MaterialTheme mapped from them, so the
 * Material3 building blocks still in use (ModalBottomSheet, SnackbarHost, Scaffold) never show Material defaults.
 * A nested `PicnicTheme(variant = Day)` (the celebration, §2.5) keeps the outer reduced-motion setting.
 */
@Composable
fun PicnicTheme(
    variant: ThemeVariant,
    reducedMotion: Boolean = LocalReducedMotion.current,
    content: @Composable () -> Unit,
) {
    val colors = colorsFor(variant)
    val type = remember(variant) { typographyFor(colors) }
    val shapes = PicnicShapeTokens
    CompositionLocalProvider(
        LocalPicnicColors provides colors,
        LocalPicnicTypography provides type,
        LocalPicnicShapes provides shapes,
        LocalPicnicShadows provides shadowsFor(variant),
        LocalPicnicMotion provides ThemeMotion(reduced = reducedMotion),
        LocalReducedMotion provides reducedMotion,
        LocalThemeVariant provides variant,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = Typography(
                displaySmall = type.wordmark,
                headlineSmall = type.screenTitle,
                titleLarge = type.screenTitle,
                titleMedium = type.aisleTitle,
                titleSmall = type.label,
                bodyLarge = type.body,
                bodyMedium = type.detail,
                bodySmall = type.caption,
                labelLarge = type.label,
                labelMedium = type.caption,
                labelSmall = type.caption,
            ),
            shapes = Shapes(
                small = shapes.chip as? CornerBasedShape ?: Shapes().small,
                medium = shapes.card as? CornerBasedShape ?: Shapes().medium,
                large = shapes.sheet as? CornerBasedShape ?: Shapes().large,
                extraLarge = shapes.sheet as? CornerBasedShape ?: Shapes().extraLarge,
            ),
        ) {
            // Bare Text()/Material pieces default to the page ink on the body face, never Material's defaults.
            CompositionLocalProvider(
                LocalContentColor provides colors.ink,
                LocalTextStyle provides type.body,
                content = content,
            )
        }
    }
}

private fun PicnicColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        tertiary = highlight, onTertiary = onHighlight, background = binder, onBackground = ink,
        surface = pad, onSurface = ink, surfaceVariant = surfaceSunken, onSurfaceVariant = inkSoft,
        surfaceContainerLowest = pad, surfaceContainerLow = pad, surfaceContainer = pad,
        surfaceContainerHigh = pad, surfaceContainerHighest = pad, surfaceTint = Color.Transparent,
        primaryContainer = highlight, onPrimaryContainer = onHighlight, secondaryContainer = surfaceSunken,
        onSecondaryContainer = ink, errorContainer = hanko, onErrorContainer = onError, inversePrimary = primaryShadow,
        outline = outline, outlineVariant = rule, error = hanko, onError = onError, scrim = scrim,
        inverseSurface = ink, inverseOnSurface = binder,
    )
} else {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        tertiary = highlight, onTertiary = onHighlight, background = binder, onBackground = ink,
        surface = pad, onSurface = ink, surfaceVariant = surfaceSunken, onSurfaceVariant = inkSoft,
        surfaceContainerLowest = pad, surfaceContainerLow = pad, surfaceContainer = pad,
        surfaceContainerHigh = pad, surfaceContainerHighest = pad, surfaceTint = Color.Transparent,
        primaryContainer = highlight, onPrimaryContainer = onHighlight, secondaryContainer = surfaceSunken,
        onSecondaryContainer = ink, errorContainer = hanko, onErrorContainer = onError, inversePrimary = primaryShadow,
        outline = outline, outlineVariant = rule, error = hanko, onError = onError, scrim = scrim,
        inverseSurface = ink, inverseOnSurface = binder,
    )
}

/* ------------------------------------------------------------------------------------------------
 * System bars (§2.5, D12): the icon styles follow the RESOLVED variant, not the system theme; the
 * celebration forces Day (dark icons) for as long as it shows.
 * ------------------------------------------------------------------------------------------------ */

/** Counts the scenes that force Day bars (the celebration). Provided by [SystemBarsHost]. */
@Stable
class SystemBarsOverride {
    var forcedDay by mutableIntStateOf(0)
        internal set
}

/** The nearest [SystemBarsHost]'s override, or null outside one (previews, tests). */
val LocalSystemBarsOverride = staticCompositionLocalOf<SystemBarsOverride?> { null }

/**
 * Sets the window's bar icon styles from [variant] (dark icons on Day, Dusk and Golden; light on Glow), unless a
 * [ForceDayBars] scene is showing, and lets such scenes register. Transparent bars, edge to edge.
 */
@Composable
fun SystemBarsHost(variant: ThemeVariant, content: @Composable () -> Unit) {
    val override = remember { SystemBarsOverride() }
    val effective = if (override.forcedDay > 0) ThemeVariant.Day else variant
    val activity = LocalActivity.current
    SideEffect { activity?.applySystemBars(effective) }
    CompositionLocalProvider(LocalSystemBarsOverride provides override, content = content)
}

/** While in composition, the bars show dark icons (Day), whatever the variant: the celebration (§2.5). */
@Composable
fun ForceDayBars() {
    val override = LocalSystemBarsOverride.current ?: return
    DisposableEffect(override) {
        override.forcedDay++
        onDispose { override.forcedDay-- }
    }
}

/** Applies [variant]'s bar icon styles to this activity's window (transparent bars, edge to edge). */
fun Activity.applySystemBars(variant: ThemeVariant) {
    val activity = this as? ComponentActivity ?: return
    val style = if (variant.lightBars) {
        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    } else {
        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    }
    activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

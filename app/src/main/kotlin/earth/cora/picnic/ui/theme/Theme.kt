package earth.cora.picnic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.icons.IconImages

val LocalPicnicColors = staticCompositionLocalOf { PlaceholderLightColors }
val LocalPicnicTypography = staticCompositionLocalOf { PlaceholderTypography }
val LocalPicnicShapes = staticCompositionLocalOf { PlaceholderShapes }
val LocalPicnicDimens = staticCompositionLocalOf { PicnicDimens() }
val LocalPicnicMotion = staticCompositionLocalOf { ThemeMotion(reduced = false) }

/**
 * Reduced motion (§8.8): on when the animator OR the transition scale is 0, read from Settings.Global at
 * start and on every resume (MainActivity, WP4). Every movement then becomes a 120 ms fade at the same beat.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** The resolved theme variant (§2.5). */
val LocalThemeVariant = staticCompositionLocalOf { ThemeVariant.Day }

/**
 * The four looks (§2.1, §2.4, §2.5): Day "Mint Binder", Glow "Glow-in-the-dark" (dark), Dusk (egg 1) and
 * Golden (egg 12). Resolved by ThemeVariantSource (WP9); the celebration always composes in Day.
 */
enum class ThemeVariant { Day, Glow, Dusk, Golden }

/** Bitmap loader for ItemIcon/AisleBasket. MainActivity provides AppContainer.iconImages; previews get placeholder art. */
val LocalIconImages = staticCompositionLocalOf<IconImages> { IconImages.None }

/** Icon catalog (category lookup for fallbacks and placeholder art). */
val LocalIconCatalog = staticCompositionLocalOf { IconCatalog.default }

/** Token access for screens: PicnicTheme.colors.ink, PicnicTheme.type.itemName, ... */
object PicnicTheme {
    val colors: PicnicColors @Composable @ReadOnlyComposable get() = LocalPicnicColors.current
    val type: PicnicTypography @Composable @ReadOnlyComposable get() = LocalPicnicTypography.current
    val shapes: PicnicShapes @Composable @ReadOnlyComposable get() = LocalPicnicShapes.current
    val dimens: PicnicDimens @Composable @ReadOnlyComposable get() = LocalPicnicDimens.current
    val motion: ThemeMotion @Composable @ReadOnlyComposable get() = LocalPicnicMotion.current
    val variant: ThemeVariant @Composable @ReadOnlyComposable get() = LocalThemeVariant.current
}

/**
 * v1 root theme: dark = Glow, light = Day. Kept for the v1 call sites (MainActivity, previews, tests)
 * until WP4 moves them to the variant form below.
 */
@Composable
fun PicnicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    PicnicTheme(variant = if (darkTheme) ThemeVariant.Glow else ThemeVariant.Day, reducedMotion = reducedMotion, content = content)
}

/**
 * Root theme (D12, §19.2). Provides the Picnic tokens AND a MaterialTheme mapped from them, so Material3
 * building blocks we still use (ModalBottomSheet, SwipeToDismissBox, SnackbarHost, Scaffold, DropdownMenu)
 * never show Material defaults. A nested `PicnicTheme(variant = Day)` (the celebration, §2.5) keeps the
 * outer reduced-motion setting.
 * PHASE-0 STUB (WP4): Glow uses the v1 dusk palette and Day, Dusk and Golden the v1 noon palette until
 * WP4's C tokens land.
 */
@Composable
fun PicnicTheme(
    variant: ThemeVariant,
    reducedMotion: Boolean = LocalReducedMotion.current,
    content: @Composable () -> Unit,
) {
    val colors = if (variant == ThemeVariant.Glow) PlaceholderDarkColors else PlaceholderLightColors
    val type = PlaceholderTypography
    val shapes = PlaceholderShapes
    CompositionLocalProvider(
        LocalPicnicColors provides colors,
        LocalPicnicTypography provides type,
        LocalPicnicShapes provides shapes,
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
            // Bare Text()/Material pieces default to ink on the body face, never Material's defaults.
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
        tertiary = highlight, onTertiary = onHighlight, background = background, onBackground = ink,
        surface = surfaceRaised, onSurface = ink, surfaceVariant = surfaceSunken, onSurfaceVariant = inkSoft,
        surfaceContainerLowest = surfaceRaised, surfaceContainerLow = surfaceRaised, surfaceContainer = surfaceRaised,
        surfaceContainerHigh = surfaceRaised, surfaceContainerHighest = surfaceRaised, surfaceTint = Color.Transparent,
        primaryContainer = highlight, onPrimaryContainer = onHighlight, secondaryContainer = surfaceSunken,
        onSecondaryContainer = ink, errorContainer = error, onErrorContainer = onError, inversePrimary = primaryShadow,
        outline = outline, outlineVariant = inkFaint, error = error, onError = onError, scrim = scrim,
        inverseSurface = ink, inverseOnSurface = background,
    )
} else {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        tertiary = highlight, onTertiary = onHighlight, background = background, onBackground = ink,
        surface = surfaceRaised, onSurface = ink, surfaceVariant = surfaceSunken, onSurfaceVariant = inkSoft,
        surfaceContainerLowest = surfaceRaised, surfaceContainerLow = surfaceRaised, surfaceContainer = surfaceRaised,
        surfaceContainerHigh = surfaceRaised, surfaceContainerHighest = surfaceRaised, surfaceTint = Color.Transparent,
        primaryContainer = highlight, onPrimaryContainer = onHighlight, secondaryContainer = surfaceSunken,
        onSecondaryContainer = ink, errorContainer = error, onErrorContainer = onError, inversePrimary = primaryShadow,
        outline = outline, outlineVariant = inkFaint, error = error, onError = onError, scrim = scrim,
        inverseSurface = ink, inverseOnSurface = background,
    )
}

package earth.cora.picnic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.icons.IconImages

val LocalPicnicColors = staticCompositionLocalOf { PlaceholderLightColors }
val LocalPicnicTypography = staticCompositionLocalOf { PlaceholderTypography }
val LocalPicnicShapes = staticCompositionLocalOf { PlaceholderShapes }
val LocalPicnicDimens = staticCompositionLocalOf { PicnicDimens() }
val LocalPicnicMotion = staticCompositionLocalOf { PicnicMotion(reduced = false) }

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
    val motion: PicnicMotion @Composable @ReadOnlyComposable get() = LocalPicnicMotion.current
}

/**
 * Root theme. Provides the Picnic tokens AND a MaterialTheme mapped from them, so Material3
 * building blocks we still use (ModalBottomSheet, SwipeToDismissBox, SnackbarHost, Scaffold,
 * DropdownMenu) never show Material defaults.
 */
@Composable
fun PicnicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) PlaceholderDarkColors else PlaceholderLightColors
    val type = PlaceholderTypography
    val shapes = PlaceholderShapes
    CompositionLocalProvider(
        LocalPicnicColors provides colors,
        LocalPicnicTypography provides type,
        LocalPicnicShapes provides shapes,
        LocalPicnicMotion provides PicnicMotion(reduced = reducedMotion),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = Typography(
                titleLarge = type.screenTitle,
                titleMedium = type.aisleTitle,
                bodyLarge = type.body,
                bodyMedium = type.detail,
                labelLarge = type.label,
                labelMedium = type.caption,
            ),
            shapes = Shapes(
                medium = shapes.card as? CornerBasedShape ?: Shapes().medium,
                large = shapes.sheet as? CornerBasedShape ?: Shapes().large,
                extraLarge = shapes.sheet as? CornerBasedShape ?: Shapes().extraLarge,
            ),
            content = content,
        )
    }
}

private fun PicnicColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        tertiary = highlight, onTertiary = onHighlight, background = background, onBackground = ink,
        surface = surfaceRaised, onSurface = ink, surfaceVariant = surfaceSunken, onSurfaceVariant = inkSoft,
        surfaceContainerLow = surfaceRaised, surfaceContainer = surfaceRaised, surfaceContainerHigh = surfaceRaised,
        outline = outline, outlineVariant = inkFaint, error = error, onError = onError, scrim = scrim,
        inverseSurface = ink, inverseOnSurface = background,
    )
} else {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        tertiary = highlight, onTertiary = onHighlight, background = background, onBackground = ink,
        surface = surfaceRaised, onSurface = ink, surfaceVariant = surfaceSunken, onSurfaceVariant = inkSoft,
        surfaceContainerLow = surfaceRaised, surfaceContainer = surfaceRaised, surfaceContainerHigh = surfaceRaised,
        outline = outline, outlineVariant = inkFaint, error = error, onError = onError, scrim = scrim,
        inverseSurface = ink, inverseOnSurface = background,
    )
}

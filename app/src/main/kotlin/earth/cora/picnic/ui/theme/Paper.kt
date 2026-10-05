package earth.cora.picnic.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import earth.cora.picnic.ui.a11y.LocalFocusRingInk

/*
 * "The paper decides the ink; the night decides the shadow" (docs/DESIGN_STICKER.md §2.4.1, ARCHITECTURE §11.2).
 * Content takes its ink from the paper under it: light paper (white, the pastels, the mids used as faces, the lemon
 * notes, the LCD, the vinyl, the receipt) gets the Day ink set, dark paper (Glow's binder, page and backing) gets
 * Glow's. Shadows never follow the paper (in Glow every hard shadow stays the night's #0B0D24), and the variant never
 * changes. Day, Dusk and Golden have no dark paper, so for them only the paper, its focus ink and the family-pastel
 * soft-ink copy are provided, exactly as before.
 */

/**
 * The paper under the content here: provided by [OnPaper] (a card or sheet: [PicnicSurface], the letter-set sheet, the
 * backing slabs, the trays' bands); null on the binder. A round sticker button whose white face and border would sit
 * on white paper reads it to draw its rim (§17).
 */
val LocalPaperColor = compositionLocalOf<Color?> { null }

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/**
 * Light paper: the ballpoint reads at least as well on it as white does. White, every pastel, the mids used as tab
 * faces, the lemon notes, the LCD, the vinyl, the receipt and the cocoa cabinet are light; Glow's binder `#161A3E`,
 * page `#222861`, backing `#2C2A72` and rule navy `#3A438A` are dark.
 */
fun isLightPaper(paper: Color): Boolean = contrast(DayColors.ballpoint, paper) >= contrast(Color.White, paper)

/**
 * The ten aisle pastels (§2.2), where text is ballpoint and soft text is ballpoint too (ballpointSoft falls below 4.5 on
 * some of them). Not the neutral pastel: it is the same hex as Day's backing, which keeps the Day inks.
 */
val FamilyPastels: Set<Color> = Families.rotation.map { it.pastel }.toSet()

/**
 * The ten aisle mids, as faces (the selected Add tab): soft text there is ballpoint too, since ballpointSoft falls to
 * 3.3 : 1 on the ramune and sakura mids (pastels and mids are fills, never soft text, §2.2).
 */
val FamilyMids: Set<Color> = Families.rotation.map { it.mid }.toSet()

/**
 * Glow's ink set for light paper: Day's inks, spots, faces and bands (isDark false, so the accent inks, the stamp
 * circles, the sticker silhouettes and the dashed add spot take their Day forms), on Glow's ground, with Glow's night
 * shadows. The variant stays Glow.
 */
val GlowLightColors: PicnicColors = DayColors.copy(
    variant = ThemeVariant.Glow,
    background = GlowColors.background,
    backgroundPattern = GlowColors.backgroundPattern,
    binder = GlowColors.binder,
    binderBottom = GlowColors.binderBottom,
    binderStripe = GlowColors.binderStripe,
    binderStripeAlpha = GlowColors.binderStripeAlpha,
    duskBands = GlowColors.duskBands,
    glowStar = GlowColors.glowStar,
    scrim = GlowColors.scrim,
    hardShadow = GlowColors.hardShadow,
    stickerShadow = GlowColors.stickerShadow,
    primaryShadow = GlowColors.primaryShadow,
)

/** The §2.7 roles in [GlowLightColors]' inks, made once. */
val GlowLightType: PicnicTypography by lazy { typographyFor(GlowLightColors) }

private val VariantTypes: Map<ThemeVariant, Lazy<PicnicTypography>> =
    ThemeVariant.entries.associateWith { v -> lazy { typographyFor(colorsFor(v)) } }

/** The §2.7 roles in [variant]'s colours, made once per variant (PicnicTheme and [OnPaper] share them). */
fun typographyOf(variant: ThemeVariant): PicnicTypography = VariantTypes.getValue(variant).value

/** Text on an aisle pastel or mid face: ballpoint, soft text ballpoint too, the rules periwinkle pastel (§2.4). */
private fun PicnicColors.onPastel(): PicnicColors = copy(ink = ballpoint, inkSoft = ballpoint, rule = Families.periwinkle.pastel)

private val DayPastel by lazy { DayColors.onPastel() }
private val DuskPastel by lazy { DuskColors.onPastel() }
private val GoldenPastel by lazy { GoldenColors.onPastel() }
private val GlowLightPastel by lazy { GlowLightColors.onPastel() }

private fun pastelCopy(base: PicnicColors): PicnicColors = when {
    base === DayColors -> DayPastel
    base === GlowLightColors -> GlowLightPastel
    base === DuskColors -> DuskPastel
    base === GoldenColors -> GoldenPastel
    else -> base.onPastel()
}

/** The ink set a [root] variant's colours give content on [paper] (null: the binder). Pure: PaperTest. */
fun paperColors(root: PicnicColors, paper: Color?): PicnicColors {
    val base = if (root.isDark && paper != null && isLightPaper(paper)) GlowLightColors else root
    return if (paper != null && (paper in FamilyPastels || paper in FamilyMids)) pastelCopy(base) else base
}

/** The focus ring's ink on [paper]: the ballpoint on light paper, white on dark, the page's ink on the binder. */
fun paperFocusInk(root: PicnicColors, paper: Color?): Color = when {
    paper == null -> root.ink
    isLightPaper(paper) -> root.ballpoint
    else -> Color.White
}

/**
 * A family's accent ink on the paper here (§2.4.1): the trace on light paper, the mid on dark paper (Glow's binder, page
 * and backing), where the traces fall to 1.7-2.8 : 1 and the mids reach 5-10 : 1, as the stamp pictograms do (§4.13).
 * For a decorative line glyph on a card and a focused field's underline.
 */
@Composable
fun accentInk(family: StickerFamily): Color = if (PicnicTheme.colors.isDark) family.mid else family.trace

/**
 * Runs [content] on [paper] (null: the binder, which re-roots a floating layer such as a menu or a sheet). Always
 * provides the paper and its focus-ring ink; provides the colours, the typography and the default text colour only
 * where they differ from the scope's, so in Day, Dusk and Golden only the paper, the focus ink and the family-pastel
 * copy ever change. Never touches the variant, the shadows or MaterialTheme. Only content goes inside: an object's own
 * shadow, white border and fill are drawn outside, on the paper it stands on; colours are read inside.
 */
@Composable
fun OnPaper(paper: Color?, content: @Composable () -> Unit) {
    val root = colorsFor(PicnicTheme.variant)
    val resolved = remember(root, paper) { paperColors(root, paper) }
    val current = LocalPicnicColors.current
    val provided = ArrayList<ProvidedValue<*>>(6)
    provided += LocalPaperColor provides paper
    provided += LocalFocusRingInk provides paperFocusInk(root, paper)
    if (resolved !== current) {
        provided += LocalPicnicColors provides resolved
        val base = if (root.isDark && paper != null && isLightPaper(paper)) GlowLightColors else root
        val type = if (base === GlowLightColors) GlowLightType else typographyOf(root.variant)
        if (LocalPicnicTypography.current !== type) provided += LocalPicnicTypography provides type
        if (resolved.ink != current.ink) {
            provided += LocalContentColor provides resolved.ink
            provided += LocalTextStyle provides LocalTextStyle.current.copy(color = resolved.ink)
        }
    }
    CompositionLocalProvider(*provided.toTypedArray(), content = content)
}

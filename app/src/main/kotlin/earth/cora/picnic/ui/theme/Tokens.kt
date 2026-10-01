package earth.cora.picnic.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * THEME LAYER TOKENS: CONTRACT between app-shell (owner) and app-screens (consumer).
 * Screens read ONLY these tokens and the theme components; they never use raw Color(...),
 * MaterialTheme.colorScheme, or hardcoded sizes for themed things. The art pass changes values
 * (and may add fields), never removes or renames fields.
 */

/** One colour family of the aisle rotation (cel base, hard-shadow shade, pale fill, deep text colour). */
@Immutable
data class AisleColors(val base: Color, val shadow: Color, val pastel: Color, val deep: Color)

@Immutable
data class PicnicColors(
    val isDark: Boolean,
    /** App ground behind everything. */
    val background: Color,
    /** Pattern colour drawn on the ground (gingham stripes, stars...), already including its alpha. */
    val backgroundPattern: Color,
    /** Cards and rows. */
    val surface: Color,
    /** Sheets, dialogs, menus. */
    val surfaceRaised: Color,
    /** Text-field fill, wells. */
    val surfaceSunken: Color,
    /** Hard offset cel shadow under cards and buttons (never a blur). */
    val hardShadow: Color,
    /** Outlines ("ink line"). */
    val outline: Color,
    /** Primary text and icons on surfaces. */
    val ink: Color,
    /** Secondary text (detail lines). */
    val inkSoft: Color,
    /** Placeholder / disabled text. */
    val inkFaint: Color,
    val primary: Color,
    val primaryShadow: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    /** Focus rings, selected tabs, the checked mark, highlighter bars. */
    val highlight: Color,
    val onHighlight: Color,
    val link: Color,
    val error: Color,
    val onError: Color,
    /** Strike-through on checked items. */
    val strike: Color,
    /** Behind modal sheets. */
    val scrim: Color,
    /** Aisle rotation, in walking order; use [aisle]. */
    val aisles: List<AisleColors>,
) {
    /** Colours for an aisle by 0-based index (position - 1); negative = Unsorted/Checked (neutral family). */
    fun aisle(index: Int): AisleColors =
        if (index < 0) AisleColors(base = inkSoft, shadow = outline, pastel = surfaceSunken, deep = ink)
        else aisles[index % aisles.size]
}

@Immutable
data class PicnicTypography(
    /** App name / hero titles. */
    val wordmark: TextStyle,
    val screenTitle: TextStyle,
    val aisleTitle: TextStyle,
    /** Numbers in aisle medallions. */
    val aisleNumber: TextStyle,
    /** Item names: always >= 16 sp, body family. */
    val itemName: TextStyle,
    val body: TextStyle,
    /** Quantity / note line: >= 13 sp. */
    val detail: TextStyle,
    /** Buttons, chips, tabs. */
    val label: TextStyle,
    val caption: TextStyle,
    /** Decorative subtitle (no accessibility meaning). */
    val script: TextStyle,
    /** Transcript / photo echo. */
    val handwriting: TextStyle,
    /** Count badges, timers. */
    val counter: TextStyle,
)

@Immutable
data class PicnicShapes(
    val card: Shape,
    val row: Shape,
    val chip: Shape,
    val button: Shape,
    val field: Shape,
    /** Top corners only (bottom sheets). */
    val sheet: Shape,
    val badge: Shape,
    val medallion: Shape,
    /**
     * Aisle section container (header band + its rows, see AisleSectionRow). A CornerBasedShape so each
     * lazy-list slice can square off the edges it shares with its neighbours.
     */
    val section: CornerBasedShape,
    /** The lighter inner surface the rows sit on, inside the section's tinted tray. */
    val sectionInner: CornerBasedShape,
)

@Immutable
data class PicnicDimens(
    /** Screen side gutter. */
    val gutter: Dp = 16.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Gap between free-standing cards (list switcher rows...). Rows inside an aisle section have none. */
    val rowGap: Dp = 8.dp,
    /** Gap between aisle sections. */
    val sectionGap: Dp = 14.dp,
    /** Aisle section tray wall: from the container's outer edge to its inner row surface. */
    val sectionInset: Dp = 8.dp,
    /** Thin rules: between rows of a section and round its inner surface. */
    val rule: Dp = 1.5.dp,
    val rowMinHeight: Dp = 64.dp,
    /** Minimum touch target. */
    val touchTarget: Dp = 48.dp,
    val itemIcon: Dp = 40.dp,
    /** Item icons peeking out of a collapsed aisle basket. */
    val miniIcon: Dp = 20.dp,
    val basket: Dp = 64.dp,
    val medallion: Dp = 44.dp,
    /** Ink outline width. */
    val outline: Dp = 2.dp,
    /** Hard offset shadow under cards and buttons. */
    val hardShadow: DpOffset = DpOffset(0.dp, 3.dp),
)

@Immutable
data class PicnicMotion(
    /** System "Remove animations" (animator duration scale 0): no decorative loops, fades <= 150 ms. */
    val reduced: Boolean,
    /** Decorative loops animate "on threes": stepped frames at this rate. */
    val stepFps: Int = 8,
    val shortMillis: Int = 150,
    val mediumMillis: Int = 280,
)

/* ---------------- Placeholder values (art pass replaces these) ----------------
 * Text pairs meet WCAG AA (>= 4.5:1): ink/inkSoft on every surface, inkFaint (placeholders) on
 * surfaceSunken, onPrimary on primary, aisle deep on aisle pastel. TokensContrastTest enforces it. */

private val Rotation = listOf(
    AisleColors(Color(0xFFF04462), Color(0xFFC92A55), Color(0xFFFFD6DE), Color(0xFFB5193C)), // strawberry
    AisleColors(Color(0xFF6CC9F2), Color(0xFF45A0E0), Color(0xFFD6F1FF), Color(0xFF1F5E96)), // sky
    AisleColors(Color(0xFFFFD43B), Color(0xFFF2A63A), Color(0xFFFFF1B0), Color(0xFF7A5800)), // lemon
    AisleColors(Color(0xFF55C26A), Color(0xFF2E9E63), Color(0xFFD5F3D3), Color(0xFF1E6B3C)), // leaf
    AisleColors(Color(0xFF9A78EC), Color(0xFF7553D2), Color(0xFFE6DCFF), Color(0xFF5B3CC4)), // grape
    AisleColors(Color(0xFFFF9548), Color(0xFFE5652F), Color(0xFFFFE0C7), Color(0xFF9E4310)), // tangerine
    AisleColors(Color(0xFFFF9EC4), Color(0xFFE674AB), Color(0xFFFFDDEB), Color(0xFFA82366)), // bubblegum
    AisleColors(Color(0xFFC98455), Color(0xFFA45C3B), Color(0xFFF3E1D0), Color(0xFF7A4220)), // cocoa
)

/** "Picnic at noon". */
val PlaceholderLightColors = PicnicColors(
    isDark = false,
    background = Color(0xFFFFFBF2),
    backgroundPattern = Color(0x1AF04462),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFD6F1FF),
    hardShadow = Color(0xFFDDD5F5),
    outline = Color(0xFF2A1730),
    ink = Color(0xFF2A1730),
    inkSoft = Color(0xFF5E4466),
    inkFaint = Color(0xFF725F7B),
    primary = Color(0xFFD62F55),
    primaryShadow = Color(0xFFA8233A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF6CC9F2),
    onSecondary = Color(0xFF2A1730),
    highlight = Color(0xFFFFD43B),
    onHighlight = Color(0xFF2A1730),
    link = Color(0xFF1F5E96),
    error = Color(0xFFB5193C),
    onError = Color(0xFFFFFFFF),
    strike = Color(0xFFB5193C),
    scrim = Color(0x992A1730),
    aisles = Rotation,
)

/** "Picnic at dusk". */
val PlaceholderDarkColors = PicnicColors(
    isDark = true,
    background = Color(0xFF2B2140),
    backgroundPattern = Color(0x14F7C8D8),
    surface = Color(0xFF3A2C55),
    surfaceRaised = Color(0xFF433364),
    surfaceSunken = Color(0xFF241B36),
    hardShadow = Color(0xFF7553D2),
    outline = Color(0xFFFFF4E6),
    ink = Color(0xFFFFF4E6),
    inkSoft = Color(0xFFCFC3E8),
    inkFaint = Color(0xFF9A8CB8),
    primary = Color(0xFFF7C8D8),
    primaryShadow = Color(0xFFE674AB),
    onPrimary = Color(0xFF2B2140),
    secondary = Color(0xFF8FD6F5),
    onSecondary = Color(0xFF2B2140),
    highlight = Color(0xFFFFD43B),
    onHighlight = Color(0xFF2B2140),
    link = Color(0xFF8FD6F5),
    error = Color(0xFFFF7A93),
    onError = Color(0xFF2B2140),
    strike = Color(0xFFFF7A93),
    scrim = Color(0xB3120C1E),
    aisles = Rotation,
)

/** Placeholder type: system families until the art pass bundles fonts in res/font (Robolectric can't use downloadable fonts). */
val PlaceholderTypography = PicnicTypography(
    wordmark = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    screenTitle = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    aisleTitle = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    aisleNumber = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 18.sp),
    itemName = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    detail = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
    caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    script = TextStyle(fontFamily = FontFamily.Cursive, fontSize = 18.sp, lineHeight = 22.sp),
    handwriting = TextStyle(fontFamily = FontFamily.Cursive, fontSize = 18.sp, lineHeight = 24.sp),
    counter = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 13.sp),
)

val PlaceholderShapes = PicnicShapes(
    card = RoundedCornerShape(18.dp),
    row = RoundedCornerShape(14.dp),
    chip = RoundedCornerShape(50),
    button = RoundedCornerShape(50),
    field = RoundedCornerShape(14.dp),
    sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    badge = CircleShape,
    medallion = CircleShape,
    section = RoundedCornerShape(18.dp),
    sectionInner = RoundedCornerShape(12.dp),
)

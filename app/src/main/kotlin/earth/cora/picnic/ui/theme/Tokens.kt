package earth.cora.picnic.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.model.AisleStyle

/*
 * THEME LAYER TOKENS (docs/DESIGN_STICKER.md §2): direction C, "Furoku Sticker Album". CONTRACT between the
 * theme (WP4, owner) and every screen package. Screens read ONLY these tokens and the theme components; they
 * never use raw Color(...), MaterialTheme.colorScheme or hard-coded sizes for themed things (ARCHITECTURE §11).
 * The art pass changes values and adds fields; it never removes or renames one, so the v1 names below stay and
 * map onto the C values (`ink` = the variant's primary text, `background` = the binder, …).
 * Contrast pairs are asserted by TokensContrastTest, the values themselves by TokensTest.
 */

/* ------------------------------------------------------------------------------------------------
 * Sticker families (§2.2): shared by every variant; stickers never change colour in the dark.
 * ------------------------------------------------------------------------------------------------ */

/**
 * One sticker colour family (§2.2). [pastel] = body fill; [mid] = accent fill and puffy band; [deep] = mid mixed
 * 32 % toward [trace] (the puffy band on mid-bodied stickers); [trace] = outline, and text only at ≥20 sp bold on
 * its own pastel (any size on white). Pastel and mid are fills only, never text.
 */
@Immutable
data class StickerFamily(val name: String, val pastel: Color, val mid: Color, val deep: Color, val trace: Color)

/** The 10 sticker families and the neutral "Everything else" family (§2.2), exactly as specified. */
object Families {
    val strawberry = StickerFamily("strawberry", Color(0xFFFFC4D1), Color(0xFFFF6F8E), Color(0xFFEC587A), Color(0xFFC3264F))
    val peach = StickerFamily("peach", Color(0xFFFFDCC6), Color(0xFFFFA477), Color(0xFFE5885A), Color(0xFFAE4D1C))
    val lemon = StickerFamily("lemon", Color(0xFFFFF3A6), Color(0xFFFFDA4A), Color(0xFFDAB632), Color(0xFF8A6800))
    val melon = StickerFamily("melon", Color(0xFFD3F4C5), Color(0xFF8EDB76), Color(0xFF6FBF62), Color(0xFF2E8239))
    val mint = StickerFamily("mint", Color(0xFFC9F2E3), Color(0xFF6DD3B0), Color(0xFF53B594), Color(0xFF1C7558))
    val ramune = StickerFamily("ramune", Color(0xFFCCEBFF), Color(0xFF7CC6F5), Color(0xFF5EA9DB), Color(0xFF1E6AA3))
    val periwinkle = StickerFamily("periwinkle", Color(0xFFDCE1FF), Color(0xFF9AA8F5), Color(0xFF7D8ADF), Color(0xFF3E4BB0))
    val grape = StickerFamily("grape", Color(0xFFE4D6FF), Color(0xFFB597F2), Color(0xFF9B7CDE), Color(0xFF6542B3))
    val sakura = StickerFamily("sakura", Color(0xFFFFD6E8), Color(0xFFFF9FC8), Color(0xFFE67BAA), Color(0xFFB02E6B))
    val cocoa = StickerFamily("cocoa", Color(0xFFF1DEC9), Color(0xFFCB9C73), Color(0xFFB1825B), Color(0xFF7A4A28))

    /** "Everything else" (the Unsorted section). */
    val neutral = StickerFamily("neutral", Color(0xFFEEF0FA), Color(0xFFC9CFF5), Color(0xFFAEB6E8), Color(0xFF4F5BA6))

    /** The aisle rotation's order (§2.3): index i = (walking position − 1) mod 10. */
    val rotation: List<StickerFamily> = listOf(strawberry, ramune, lemon, melon, grape, peach, mint, sakura, cocoa, periwinkle)

    /** Every family, the neutral one last. */
    val all: List<StickerFamily> = listOf(strawberry, peach, lemon, melon, mint, ramune, periwinkle, grape, sakura, cocoa, neutral)

    /** A family by its §2.2 name; null for unknown names. */
    fun byName(name: String?): StickerFamily? = all.firstOrNull { it.name == name }
}

/** The ten tab patterns (§2.3, §2.12), named as `PatternTiles` names them. */
enum class TabPattern(val key: String) {
    Gingham("gingham"), Polka("polka"), Stripes("stripes"), Hearts("hearts"), Stars("stars"),
    Clouds("clouds"), SmallCheck("check"), Waves("waves"), Flowers("flowers"), Plain("plain");

    companion object {
        fun byKey(key: String?): TabPattern? = entries.firstOrNull { it.key == key }
    }
}

/** Stamp pictograms (§4.13), named as `DecorManifest.pictograms` names them; [Capsule] is "Everything else". */
enum class Pictogram(val key: String) {
    Strawberry("strawberry"), Bee("bee"), Star("star"), Heart("heart"), Cloud("cloud"), Flower("flower"),
    Ladybug("ladybug"), Teacup("teacup"), Sandwich("sandwich"), Sun("sun"), Capsule("capsule"),
    Maple("maple"), Pumpkin("pumpkin"), Ghost("ghost"), Snowflake("snowflake"), Envelope("envelope"),
}

/**
 * An aisle's identity look (§2.3): colour family + tab pattern + stamp pictogram. An aisle is always identified by
 * colour + pattern + number + name, never colour alone. [position] is the 1-based walking position (0 = "Everything
 * else").
 */
@Immutable
data class AisleLook(val position: Int, val family: StickerFamily, val pattern: TabPattern, val pictogram: Pictogram) {
    val isEverythingElse: Boolean get() = position <= 0
}

/** The aisle rotation (§2.3) and the editor's overrides (§4.26). Pure: AisleStyleTest. */
object AisleLooks {
    private val patterns = listOf(
        TabPattern.Gingham, TabPattern.Polka, TabPattern.Stripes, TabPattern.Hearts, TabPattern.Stars,
        TabPattern.Clouds, TabPattern.SmallCheck, TabPattern.Waves, TabPattern.Flowers, TabPattern.Plain,
    )
    private val pictograms = listOf(
        Pictogram.Strawberry, Pictogram.Bee, Pictogram.Star, Pictogram.Heart, Pictogram.Cloud,
        Pictogram.Flower, Pictogram.Ladybug, Pictogram.Teacup, Pictogram.Sandwich, Pictogram.Sun,
    )

    /** "Everything else": neutral, plain, capsule, no numeral. */
    val everythingElse: AisleLook = AisleLook(0, Families.neutral, TabPattern.Plain, Pictogram.Capsule)

    /**
     * The look for walking position [position] (1-based; ≤0 = "Everything else"). An [override] from the aisle
     * editor (`StoreProfile.aisleStyles`) replaces the colour and/or the pattern; the pictogram always follows the
     * position, so a colour override keeps the pictogram of its position.
     */
    fun of(position: Int, override: AisleStyle? = null): AisleLook {
        if (position <= 0) return everythingElse
        val i = (position - 1) % 10
        return AisleLook(
            position = position,
            family = Families.byName(override?.family)?.takeIf { it != Families.neutral } ?: Families.rotation[i],
            pattern = TabPattern.byKey(override?.pattern) ?: patterns[i],
            pictogram = pictograms[i],
        )
    }
}

/* ------------------------------------------------------------------------------------------------
 * Colours.
 * ------------------------------------------------------------------------------------------------ */

/** v1 shape of an aisle's colours, kept for v1 call sites: base = mid, shadow = deep, pastel, deep = trace. */
@Immutable
data class AisleColors(val base: Color, val shadow: Color, val pastel: Color, val deep: Color)

private fun StickerFamily.toAisleColors() = AisleColors(base = mid, shadow = deep, pastel = pastel, deep = trace)

/** The trip sticker book's surfaces (§2.6). Drawn by WP5's StickerBook. */
@Immutable
data class BookColors(
    val page: Color,
    val releasePaper: Color,
    val paperDots: Color,
    val spotDash: Color,
    val spotFill: Color,
    val spineStripes: List<Color>,
    val ringFace: Color,
    val ringRim: Color,
    val foreEdge: Color,
    /** Text on the release paper (ballpoint in Day, white in Glow). */
    val text: Color,
)

@Immutable
data class PicnicColors(
    val isDark: Boolean,
    /** App ground behind everything (= [binder]). */
    val background: Color,
    /** Pattern colour drawn on the ground (= [binderStripe] at its drawn alpha). */
    val backgroundPattern: Color,
    /** Cards and rows (= [pad]). */
    val surface: Color,
    /** Sheets, dialogs, menus (= [pad]). */
    val surfaceRaised: Color,
    /** Wells, disabled fills, sticker-sheet backing (= [backing] in Day, the rule navy in Glow). */
    val surfaceSunken: Color,
    /** Hard offset shadow (= [stickerShadow]). */
    val hardShadow: Color,
    /** Lines that edge a shape (periwinkle trace in Day, soft lavender in Glow). */
    val outline: Color,
    /** Primary text on pages and the binder (ballpoint in Day, white in Glow). */
    val ink: Color,
    /** Secondary text on pages and the binder (ballpointSoft in Day, #C9CFF5 in Glow). Never on a pastel. */
    val inkSoft: Color,
    /** Placeholder / disabled text. */
    val inkFaint: Color,
    /** The candy pill's face (strawberry mid). */
    val primary: Color,
    val primaryShadow: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    /** Selected and pressed bands (the highlighter). */
    val highlight: Color,
    val onHighlight: Color,
    val link: Color,
    val error: Color,
    val onError: Color,
    /** Strike-through on checked items (hanko). */
    val strike: Color,
    /** Behind modal sheets (38 % ballpoint). */
    val scrim: Color,
    /** Aisle rotation in walking order (v1 shape); prefer [AisleLooks]. */
    val aisles: List<AisleColors>,
    /** The neutral family of "Everything else" (v1 shape). */
    val neutral: AisleColors,
    /** v1 button lens gloss; C buttons draw the R3 crescent + dot instead (§4.6), so this is unused (null). */
    val buttonGloss: Color?,

    // ---- C tokens (§2.1, §2.4, §2.5) ----
    /** The resolved variant these colours belong to. */
    val variant: ThemeVariant = if (isDark) ThemeVariant.Glow else ThemeVariant.Day,
    /** App background (mint vinyl; navy in Glow). Golden: the gradient's top colour. */
    val binder: Color = background,
    /** Golden only: the gradient's bottom colour (top to bottom); null = a flat binder. */
    val binderBottom: Color? = null,
    /** Vertical pinstripes, 2 dp wide every 10 dp, drawn at [binderStripeAlpha]. */
    val binderStripe: Color = backgroundPattern,
    val binderStripeAlpha: Float = 1f,
    /** Dusk only: the three posterised bands at the top of the binder (0–40, 40–80, 80–120 dp). */
    val duskBands: List<Color> = emptyList(),
    /** Letter-pad pages, tray panels, strips, sheets, cards, the add bar (navy page in Glow). */
    val pad: Color = surface,
    /** 1 dp ruled lines. */
    val rule: Color = outline,
    /** 2 dp pad margin line. */
    val margin: Color = primary,
    /** Die-cut and kiss-cut borders. */
    val stickerWhite: Color = Color.White,
    /** Hard shadow behind every sticker, card, chip and button. */
    val stickerShadow: Color = hardShadow,
    /** "Blue gel pen": primary text on white and on every pastel, in every variant (pastels never darken). */
    val ballpoint: Color = ink,
    /** Secondary text on pad and binder only. */
    val ballpointSoft: Color = inkSoft,
    /** GOT IT stamp, strike-through, errors. */
    val hanko: Color = error,
    /** LCD toy-screen face (stays light in Glow). */
    val lcd: Color = Color(0xFFC9E5B8),
    /** LCD pixel grid and unlit dots (drawn at 50 %). */
    val lcdGrid: Color = Color(0xFFB6D4A4),
    /** Pressed / selected row band, keyword highlight. */
    val highlighter: Color = highlight,
    /** Sticker-sheet backing paper (editor, empty state, value pills, disabled fill, tear slot). */
    val backing: Color = surfaceSunken,
    /** Unchecked checkbox dashes. */
    val spotDash: Color = outline,
    /** Unchecked checkbox fill (Transparent = hollow, Glow). */
    val spotFill: Color = pad,
    /** Peeled ghost: the cut colour (white in Day, #3A438A in Glow). */
    val ghostFill: Color = Color.White,
    /** Peeled ghost: the dashed outline. */
    val ghostDash: Color = Color(0xFF9AA8F5),
    /** Glow stars and the Glow current-aisle ring. */
    val glowStar: Color = Color(0xFFC9FFDE),
    /** The stroke under puffy titles (white; #3A438A in Glow). */
    val puffyStroke: Color = Color.White,
    /** Hachi Maru doodles (decorative). */
    val doodle: Color = Color(0xFFB02E6B),
    /** The pressed row band behind the text. */
    val pressedBand: Color = highlighter,
    /** Information text on the binder sits on this pad patch (§4.1). */
    val patch: Color = pad,
    /** Text on [patch] (and on [pad]). */
    val onPad: Color = ink,
    val onPadSoft: Color = inkSoft,
    /** The trip sticker book (§2.6). */
    val book: BookColors = DayBook,
    /** Album vinyl (§2.6). */
    val albumVinyl: Color = Color(0xFFFFE9F2),
    val albumStripe: Color = Color(0xFFFFD6E8),
    /** Receipt thermal paper (§2.6). */
    val receiptPaper: Color = Color(0xFFFFF9E8),
    /** Sticky-note toast paper (§2.6). */
    val toastNote: Color = Families.lemon.pastel,
) {
    /** Colours for an aisle by 0-based index (position − 1); negative = "Everything else" (v1 shape). */
    fun aisle(index: Int): AisleColors =
        if (index < 0) neutral
        else aisles[index % aisles.size]

    /** The sticker family of an aisle by 0-based index (position − 1); negative = neutral. */
    fun family(index: Int): StickerFamily = if (index < 0) Families.neutral else Families.rotation[index % 10]
}

/** Hard shadows (§2.10): the element's own shape, filled with [color], drawn at an offset. No blur, ever. */
@Immutable
data class PicnicShadows(
    val color: Color,
    /** Stickers, chips, badges, tabs, small cards, round buttons. */
    val hs: DpOffset,
    /** Sticker book, primary pill, next card, toast, large cards, wordmark. */
    val hs2: DpOffset,
    /** A dragged aisle label. */
    val hsLift: DpOffset,
)

val DayShadows = PicnicShadows(
    color = Color(0xFF6E7BD0),
    hs = DpOffset(1.6.dp, 2.2.dp),
    hs2 = DpOffset(2.4.dp, 3.4.dp),
    hsLift = DpOffset(3.2.dp, 4.4.dp),
)
val GlowShadows = DayShadows.copy(color = Color(0xFF0B0D24))

/** Golden (egg 12): every hard shadow becomes (+3, +4) dp in a rosy #E59A7A. */
val GoldenShadows = PicnicShadows(
    color = Color(0xFFE59A7A),
    hs = DpOffset(3.dp, 4.dp),
    hs2 = DpOffset(3.dp, 4.dp),
    hsLift = DpOffset(3.2.dp, 4.4.dp),
)

private val DayBook = BookColors(
    page = Color(0xFFFFFFFF),
    releasePaper = Color(0xFFFFF0F7),
    paperDots = Color.White.copy(alpha = 0.95f),
    spotDash = Color(0xFF9AA8F5),
    spotFill = Color.White.copy(alpha = 0.75f),
    spineStripes = listOf(Color(0xFFB9C3FA), Color(0xFFDCE1FF), Color(0xFFC3CBF7)),
    ringFace = Color(0xFFF4F6FF),
    ringRim = Color(0xFFB9C3FA),
    foreEdge = Color(0xFF9AA8F5),
    text = Color(0xFF25307C),
)

private val GlowBook = BookColors(
    page = Color(0xFF222861),
    releasePaper = Color(0xFF2C2A72),
    paperDots = Color(0xFFC9FFDE).copy(alpha = 0.28f),
    spotDash = Color(0xFFC9CFF5),
    spotFill = Color(0xFF222861).copy(alpha = 0.6f),
    spineStripes = listOf(Color(0xFF2A3170), Color(0xFF3A438A)),
    ringFace = Color(0xFFE4E8FF),
    ringRim = Color(0xFF9AA8F5),
    foreEdge = Color(0xFF3A438A),
    text = Color(0xFFFFFFFF),
)

private val Ballpoint = Color(0xFF25307C)
private val BallpointSoft = Color(0xFF4F5BA6)

/** Day, "Mint Binder" (§2.1). */
val DayColors = PicnicColors(
    isDark = false,
    background = Color(0xFFE6F6EE),
    backgroundPattern = Color(0xFFCDEEDD),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFEEF0FA),
    hardShadow = DayShadows.color,
    outline = Color(0xFF3E4BB0),
    ink = Ballpoint,
    inkSoft = BallpointSoft,
    inkFaint = BallpointSoft,
    primary = Families.strawberry.mid,
    primaryShadow = DayShadows.color,
    onPrimary = Color.White,
    secondary = Families.ramune.mid,
    onSecondary = Ballpoint,
    highlight = Color(0xFFFFF3A6),
    onHighlight = Ballpoint,
    link = Families.ramune.trace,
    error = Color(0xFFC8321F),
    onError = Color.White,
    strike = Color(0xFFC8321F),
    scrim = Ballpoint.copy(alpha = 0.38f),
    aisles = Families.rotation.map { it.toAisleColors() },
    neutral = Families.neutral.toAisleColors(),
    buttonGloss = null,
    variant = ThemeVariant.Day,
    binder = Color(0xFFE6F6EE),
    binderStripe = Color(0xFFCDEEDD),
    pad = Color(0xFFFFFFFF),
    rule = Color(0xFFBCD0FF),
    margin = Color(0xFFFF9CC2),
    stickerWhite = Color(0xFFFFFFFF),
    stickerShadow = DayShadows.color,
    ballpoint = Ballpoint,
    ballpointSoft = BallpointSoft,
    hanko = Color(0xFFC8321F),
    lcd = Color(0xFFC9E5B8),
    lcdGrid = Color(0xFFB6D4A4),
    highlighter = Color(0xFFFFF3A6),
    backing = Color(0xFFEEF0FA),
    spotDash = Color(0xFF3E4BB0),
    spotFill = Color(0xFFFFFFFF),
    ghostFill = Color(0xFFFFFFFF),
    ghostDash = Color(0xFF9AA8F5),
    glowStar = Color(0xFFC9FFDE),
    puffyStroke = Color(0xFFFFFFFF),
    doodle = Color(0xFFB02E6B),
    pressedBand = Color(0xFFFFF3A6),
    patch = Color(0xFFFFFFFF),
    onPad = Ballpoint,
    onPadSoft = BallpointSoft,
    book = DayBook,
)

/** Glow, "Glow-in-the-dark" (§2.4). Stickers, baskets, tray pastels and the LCD keep their colours. */
val GlowColors = PicnicColors(
    isDark = true,
    background = Color(0xFF161A3E),
    backgroundPattern = Color(0xFF1E2350),
    surface = Color(0xFF222861),
    surfaceRaised = Color(0xFF222861),
    surfaceSunken = Color(0xFF3A438A),
    hardShadow = GlowShadows.color,
    outline = Color(0xFFC9CFF5),
    ink = Color(0xFFFFFFFF),
    inkSoft = Color(0xFFC9CFF5),
    inkFaint = Color(0xFFC9CFF5),
    primary = Families.strawberry.mid,
    primaryShadow = GlowShadows.color,
    onPrimary = Color.White,
    secondary = Families.ramune.mid,
    onSecondary = Ballpoint,
    highlight = Color(0xFF3A438A),
    onHighlight = Color(0xFFFFFFFF),
    link = Families.ramune.pastel,
    error = Color(0xFFFF8A7A),
    onError = Ballpoint,
    strike = Color(0xFFFF8A7A),
    scrim = Color(0xFF0B0D24).copy(alpha = 0.55f),
    aisles = Families.rotation.map { it.toAisleColors() },
    neutral = Families.neutral.toAisleColors(),
    buttonGloss = null,
    variant = ThemeVariant.Glow,
    binder = Color(0xFF161A3E),
    binderStripe = Color(0xFF1E2350),
    pad = Color(0xFF222861),
    rule = Color(0xFF3A438A),
    margin = Color(0xFFFF7FB0),
    stickerWhite = Color(0xFFFFFFFF),
    stickerShadow = GlowShadows.color,
    ballpoint = Ballpoint,
    ballpointSoft = BallpointSoft,
    hanko = Color(0xFFFF8A7A),
    lcd = Color(0xFFC9E5B8),
    lcdGrid = Color(0xFFB6D4A4),
    highlighter = Color(0xFFFFF3A6),
    backing = Color(0xFFEEF0FA),
    spotDash = Color(0xFFC9CFF5),
    spotFill = Color.Transparent,
    ghostFill = Color(0xFF3A438A),
    ghostDash = Color(0xFFC9CFF5),
    glowStar = Color(0xFFC9FFDE),
    puffyStroke = Color(0xFF3A438A),
    doodle = Color(0xFFFF9FC8),
    pressedBand = Color(0xFF3A438A),
    patch = Color(0xFF222861),
    onPad = Color(0xFFFFFFFF),
    onPadSoft = Color(0xFFC9CFF5),
    book = GlowBook,
)

/** Dusk (egg 1, §2.5): Day with a lilac binder, white 40 % pinstripes and three posterised bands at the top. */
val DuskColors = DayColors.copy(
    background = Color(0xFFE9DDF7),
    backgroundPattern = Color.White.copy(alpha = 0.4f),
    variant = ThemeVariant.Dusk,
    binder = Color(0xFFE9DDF7),
    binderStripe = Color.White,
    binderStripeAlpha = 0.4f,
    duskBands = listOf(Color(0xFFFFDCC6), Color(0xFFE4D6FF), Color(0xFFDCE1FF)),
)

/**
 * Golden hour (egg 12, §2.5): a #FFE7C2 → #FFD6E8 binder gradient (top to bottom) with #FFDCC6 stripes at 70 %, pad
 * patches on #FFF1E6, and every hard shadow (+3, +4) dp in #E59A7A ([GoldenShadows]).
 */
val GoldenColors = DayColors.copy(
    background = Color(0xFFFFE7C2),
    backgroundPattern = Color(0xFFFFDCC6).copy(alpha = 0.7f),
    hardShadow = GoldenShadows.color,
    primaryShadow = GoldenShadows.color,
    variant = ThemeVariant.Golden,
    binder = Color(0xFFFFE7C2),
    binderBottom = Color(0xFFFFD6E8),
    binderStripe = Color(0xFFFFDCC6),
    binderStripeAlpha = 0.7f,
    stickerShadow = GoldenShadows.color,
    patch = Color(0xFFFFF1E6),
)

/** The colours of a variant. */
fun colorsFor(variant: ThemeVariant): PicnicColors = when (variant) {
    ThemeVariant.Day -> DayColors
    ThemeVariant.Glow -> GlowColors
    ThemeVariant.Dusk -> DuskColors
    ThemeVariant.Golden -> GoldenColors
}

/** The hard shadows of a variant. */
fun shadowsFor(variant: ThemeVariant): PicnicShadows = when (variant) {
    ThemeVariant.Glow -> GlowShadows
    ThemeVariant.Golden -> GoldenShadows
    ThemeVariant.Day, ThemeVariant.Dusk -> DayShadows
}

/** v1 names, kept for v1 call sites: the C Day and Glow palettes. */
val PlaceholderLightColors: PicnicColors get() = DayColors
val PlaceholderDarkColors: PicnicColors get() = GlowColors

/* ------------------------------------------------------------------------------------------------
 * Typography (§2.7).
 * ------------------------------------------------------------------------------------------------ */

/** The four bundled families (SIL OFL 1.1; files in res/font, subset by WP2's design/fonts script). */
object PicnicFonts {
    /** Wordmark, titles, tabs, buttons, numerals. */
    val dynaPuff: FontFamily = FontFamily(
        Font(R.font.dynapuff_semibold, FontWeight.SemiBold),
        Font(R.font.dynapuff_bold, FontWeight.Bold),
    )

    /** Item names, body, UI labels; 900 for hanko lettering. */
    val zenMaru: FontFamily = FontFamily(
        Font(R.font.zen_maru_gothic_medium, FontWeight.Medium),
        Font(R.font.zen_maru_gothic_bold, FontWeight.Bold),
        Font(R.font.zen_maru_gothic_black, FontWeight.Black),
    )

    /** Raw transcription, notes, the aisles text. */
    val kleeOne: FontFamily = FontFamily(Font(R.font.klee_one_semibold, FontWeight.SemiBold))

    /** Doodles (decorative only). */
    val hachiMaru: FontFamily = FontFamily(Font(R.font.hachi_maru_pop_regular, FontWeight.Normal))
}

@Immutable
data class PicnicTypography(
    /** App name / hero titles (v1 name; = [wordmarkLarge]). */
    val wordmark: TextStyle,
    /** Screen title, 24 sp expanded. */
    val screenTitle: TextStyle,
    /** Aisle tab label (v1 name; = [aisleTab]). */
    val aisleTitle: TextStyle,
    /** Numbers in aisle number circles (v1 name; = [tabNumber]). */
    val aisleNumber: TextStyle,
    /** Item names: Zen Maru 700, 17 sp, always ≥16 sp. */
    val itemName: TextStyle,
    /** Body: Zen Maru 500 16 sp. */
    val body: TextStyle,
    /** Item detail line: Zen Maru 500 13.5 sp (v1 name; = [itemDetail]). */
    val detail: TextStyle,
    /** Labels and chips: Zen Maru 700 13.5 sp. */
    val label: TextStyle,
    /** Small secondary text: Zen Maru 500 13 sp. */
    val caption: TextStyle,
    /** Decorative doodle (v1 name; = [doodle]). */
    val script: TextStyle,
    /** Transcript / photo echo (v1 name; = [transcript]). */
    val handwriting: TextStyle,
    /** Count badges, timers: DynaPuff 700 13 sp. */
    val counter: TextStyle,

    // ---- C roles (§2.7) ----
    val wordmarkLarge: TextStyle = wordmark,
    val wordmarkCompact: TextStyle = wordmark,
    val screenTitleCollapsed: TextStyle = screenTitle,
    val appBarSubtitle: TextStyle = caption,
    val aisleTab: TextStyle = aisleTitle,
    val tabNumber: TextStyle = aisleNumber,
    val itemNameCompact: TextStyle = itemName,
    val itemDetail: TextStyle = detail,
    val itemDetailCompact: TextStyle = detail,
    val stripMeta: TextStyle = caption,
    val stripMetaCount: TextStyle = caption,
    val hanko: TextStyle = label,
    val primaryButton: TextStyle = label,
    val secondaryButton: TextStyle = label,
    val textButton: TextStyle = label,
    val transcript: TextStyle = handwriting,
    val voiceTranscript: TextStyle = handwriting,
    val aislesText: TextStyle = handwriting,
    val doodle: TextStyle = script,
    val nextLabel: TextStyle = screenTitle,
    val nextAisle: TextStyle = screenTitle,
    val celebrationTitle: TextStyle = screenTitle,
    val toast: TextStyle = label,
    val stampNumeral: TextStyle = counter,
    val chipNumber: TextStyle = counter,
    val badgeNumeral: TextStyle = counter,
    val fieldLabel: TextStyle = label,
    val fieldValue: TextStyle = body,
    /** DynaPuff 700 20 sp card titles (recipe card). */
    val cardTitle: TextStyle = screenTitle,
    /** DynaPuff 700 22 sp (the empty state's title). */
    val emptyTitle: TextStyle = screenTitle,
    /** DynaPuff 700 15 sp small titles (album cards, peek note uses 14). */
    val smallTitle: TextStyle = screenTitle,
)

private val NoPadding = PlatformTextStyle(includeFontPadding = false)
private val CenteredLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(family: FontFamily, weight: FontWeight, size: TextUnit, line: TextUnit, color: Color, spacing: TextUnit = 0.sp) =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = line,
        color = color,
        letterSpacing = spacing,
        platformStyle = NoPadding,
        lineHeightStyle = CenteredLines,
    )

/** The §2.7 roles in [c]'s colours (text on pages: ballpoint in Day, white in Glow). */
fun typographyFor(c: PicnicColors): PicnicTypography {
    val puff = PicnicFonts.dynaPuff
    val zen = PicnicFonts.zenMaru
    val title = style(puff, FontWeight.Bold, 24.sp, 30.sp, c.ink)
    val label = style(zen, FontWeight.Bold, 13.5.sp, 18.sp, c.ink, 0.02.em)
    val caption = style(zen, FontWeight.Medium, 13.sp, 18.sp, c.inkSoft, 0.02.em)
    val doodle = style(PicnicFonts.hachiMaru, FontWeight.Normal, 14.sp, 20.sp, c.doodle)
    val transcript = style(PicnicFonts.kleeOne, FontWeight.SemiBold, 15.sp, 22.sp, c.inkSoft)
    val counter = style(puff, FontWeight.Bold, 13.sp, 16.sp, c.ink)
    val itemName = style(zen, FontWeight.Bold, 17.sp, 22.sp, c.ink, 0.01.em)
    val detail = style(zen, FontWeight.Medium, 13.5.sp, 18.sp, c.inkSoft, 0.02.em)
    val wordmark = style(puff, FontWeight.Bold, 40.sp, 46.sp, Families.strawberry.mid)
    val aisleTab = style(puff, FontWeight.Bold, 20.sp, 24.sp, c.ballpoint)
    val tabNumber = style(puff, FontWeight.Bold, 14.sp, 16.sp, c.ballpoint)
    return PicnicTypography(
        wordmark = wordmark,
        screenTitle = title,
        aisleTitle = aisleTab,
        aisleNumber = tabNumber,
        itemName = itemName,
        body = style(zen, FontWeight.Medium, 16.sp, 23.sp, c.ink, 0.01.em),
        detail = detail,
        label = label,
        caption = caption,
        script = doodle,
        handwriting = transcript,
        counter = counter,
        wordmarkLarge = wordmark,
        wordmarkCompact = wordmark.copy(fontSize = 30.sp, lineHeight = 36.sp),
        screenTitleCollapsed = title.copy(fontSize = 20.sp, lineHeight = 26.sp),
        appBarSubtitle = style(zen, FontWeight.Bold, 13.sp, 17.sp, c.inkSoft, 0.02.em),
        aisleTab = aisleTab,
        tabNumber = tabNumber,
        itemNameCompact = itemName.copy(fontSize = 16.sp, lineHeight = 21.sp),
        itemDetail = detail,
        itemDetailCompact = detail.copy(fontSize = 13.sp, lineHeight = 17.sp),
        stripMeta = caption,
        stripMetaCount = caption.copy(fontWeight = FontWeight.Bold, color = c.ink),
        hanko = style(zen, FontWeight.Black, 10.sp, 10.sp, c.hanko, 0.02.em),
        primaryButton = style(puff, FontWeight.Bold, 18.sp, 22.sp, Color.White),
        secondaryButton = style(puff, FontWeight.Bold, 16.sp, 20.sp, c.ink),
        textButton = style(zen, FontWeight.Bold, 15.sp, 20.sp, c.ink, 0.02.em),
        transcript = transcript,
        voiceTranscript = transcript.copy(fontSize = 16.sp, lineHeight = 22.sp),
        aislesText = transcript.copy(fontSize = 16.sp, lineHeight = 24.sp),
        doodle = doodle,
        nextLabel = style(puff, FontWeight.Bold, 26.sp, 30.sp, c.ballpoint),
        nextAisle = style(puff, FontWeight.Bold, 23.sp, 28.sp, c.ballpoint),
        celebrationTitle = style(puff, FontWeight.Bold, 34.sp, 40.sp, Families.strawberry.mid),
        toast = style(zen, FontWeight.Bold, 14.sp, 20.sp, c.ballpoint, 0.02.em),
        stampNumeral = style(puff, FontWeight.Bold, 12.sp, 14.sp, c.inkSoft),
        chipNumber = style(puff, FontWeight.Bold, 11.sp, 13.sp, c.ballpoint),
        badgeNumeral = counter,
        fieldLabel = style(zen, FontWeight.Bold, 13.sp, 17.sp, c.inkSoft, 0.02.em),
        fieldValue = style(zen, FontWeight.Bold, 16.sp, 22.sp, c.ink, 0.01.em),
        cardTitle = style(puff, FontWeight.Bold, 20.sp, 25.sp, c.ink),
        emptyTitle = style(puff, FontWeight.Bold, 22.sp, 28.sp, c.ink),
        smallTitle = style(puff, FontWeight.Bold, 15.sp, 19.sp, c.ink),
    )
}

/** v1 name: the Day roles. */
val PlaceholderTypography: PicnicTypography get() = typographyFor(DayColors)

/* ------------------------------------------------------------------------------------------------
 * Shapes (§2.8).
 * ------------------------------------------------------------------------------------------------ */

@Immutable
data class PicnicShapes(
    /** Kiss-cut cards (10 dp). */
    val card: Shape,
    val row: Shape,
    /** Chips, pills, buttons: full stadium. */
    val chip: Shape,
    val button: Shape,
    val field: Shape,
    /** Bottom sheet: 20 dp top corners. */
    val sheet: Shape,
    val badge: Shape,
    val medallion: Shape,
    /** Aisle tray (outer), 12 dp. */
    val section: CornerBasedShape,
    /** The tray's inner row panel, 8 dp. */
    val sectionInner: CornerBasedShape,

    // ---- C shapes (§2.8) ----
    /** Letter-pad page and folded strip, 6 dp. */
    val pad: CornerBasedShape = RoundedCornerShape(6.dp),
    val strip: CornerBasedShape = RoundedCornerShape(6.dp),
    /** Index tab: 12 dp top, 4 dp bottom. */
    val tab: CornerBasedShape = RoundedCornerShape(12.dp, 12.dp, 4.dp, 4.dp),
    /** Pinned aisle plate: 12 dp top, 0 bottom. */
    val pinnedPlate: CornerBasedShape = RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp),
    val bookOuter: CornerBasedShape = RoundedCornerShape(12.dp),
    val bookPage: CornerBasedShape = RoundedCornerShape(9.dp),
    val nextCard: CornerBasedShape = RoundedCornerShape(14.dp),
    val pill: CornerBasedShape = RoundedCornerShape(percent = 50),
    val lcd: CornerBasedShape = RoundedCornerShape(9.dp),
    /** Sticky note: a paper square (2 dp). */
    val toast: CornerBasedShape = RoundedCornerShape(2.dp),
    /** Pad patch under information text on the binder (radius 9). */
    val patch: CornerBasedShape = RoundedCornerShape(9.dp),
)

val PicnicShapeTokens = PicnicShapes(
    card = RoundedCornerShape(10.dp),
    row = RoundedCornerShape(8.dp),
    chip = RoundedCornerShape(percent = 50),
    button = RoundedCornerShape(percent = 50),
    field = RoundedCornerShape(0.dp),
    sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    badge = CircleShape,
    medallion = CircleShape,
    section = RoundedCornerShape(12.dp),
    sectionInner = RoundedCornerShape(8.dp),
)

/** v1 name. */
val PlaceholderShapes: PicnicShapes get() = PicnicShapeTokens

/* ------------------------------------------------------------------------------------------------
 * Spacing and sizes (§2.9).
 * ------------------------------------------------------------------------------------------------ */

@Immutable
data class PicnicDimens(
    /** Screen side gutter for secondary screens. */
    val gutter: Dp = 16.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Gap between free-standing cards (list switcher rows...). */
    val rowGap: Dp = 8.dp,
    /** Gap between folded strips (§2.9 `strip`: 7 dp). */
    val sectionGap: Dp = 7.dp,
    /** Aisle tray side padding (open tray padding 0 5 18 dp). */
    val sectionInset: Dp = 5.dp,
    /** Ruled lines. */
    val rule: Dp = 1.dp,
    /** List rows: 56 dp (64 dp at font scale ≥1.3, [rowMinLarge]). */
    val rowMinHeight: Dp = 56.dp,
    /** Minimum touch target. */
    val touchTarget: Dp = 48.dp,
    /** Row sticker box (48-unit art + border + shadow). */
    val itemIcon: Dp = 52.dp,
    /** Stickers in a basket. */
    val miniIcon: Dp = 30.dp,
    /** Basket in a tray header (64 × 51). */
    val basket: Dp = 64.dp,
    /** Aisle number circle on a tab. */
    val medallion: Dp = 24.dp,
    /** Dashed rings and focus rings. */
    val outline: Dp = 2.dp,
    /** v1 name for the small hard shadow ([hs]). */
    val hardShadow: DpOffset = DpOffset(1.6.dp, 2.2.dp),

    // ---- C sizes (§2.9) ----
    val listGutter: Dp = 8.dp,
    val stackMargin: Dp = 12.dp,
    val rowMinLarge: Dp = 64.dp,
    val rowCompact: Dp = 48.dp,
    val sticker: Dp = 52.dp,
    val stickerCompact: Dp = 40.dp,
    val mini: Dp = 30.dp,
    val bookSticker: Dp = 26.dp,
    val bookStickerCollapsed: Dp = 22.dp,
    val pocket: Dp = 56.dp,
    val touch: Dp = 48.dp,
    /** Checkbox spot face (in the 48 dp target). */
    val spot: Dp = 30.dp,
    /** Checked hanko. */
    val hanko: Dp = 40.dp,
    val basketStrip: DpSize = DpSize(80.dp, 64.dp),
    val basketTray: DpSize = DpSize(64.dp, 51.dp),
    val basketCard: DpSize = DpSize(120.dp, 96.dp),
    val badge: Dp = 22.dp,
    val tabTray: Dp = 38.dp,
    val tabStrip: Dp = 32.dp,
    val strip: Dp = 56.dp,
    val stripGap: Dp = 7.dp,
    val trayHeader: Dp = 50.dp,
    val endCap: Dp = 15.dp,
    val pinnedPlate: Dp = 53.dp,
    val appBar: Dp = 72.dp,
    val appBarCollapsed: Dp = 56.dp,
    val book: Dp = 76.dp,
    val bookThreeRows: Dp = 102.dp,
    val bookCollapsed: Dp = 40.dp,
    val addBar: Dp = 84.dp,
    val fab: Dp = 64.dp,
    val roundButton: Dp = 40.dp,
    val addBarButton: Dp = 52.dp,
    val primary: Dp = 52.dp,
    val secondary: Dp = 48.dp,
    val chip: Dp = 32.dp,
    val field: Dp = 56.dp,
    val toast: Dp = 220.dp,
    val nextCard: Dp = 236.dp,
    /** Corner flags on a sticker (new star, favourite heart, shiny star). */
    val flag: Dp = 20.dp,
    /** The white die-cut / kiss-cut border round chips, cards and buttons. */
    val border: Dp = 3.dp,
    /** Hard shadows (§2.10); the variant's offsets live in [PicnicShadows]. */
    val hs: DpOffset = DpOffset(1.6.dp, 2.2.dp),
    val hs2: DpOffset = DpOffset(2.4.dp, 3.4.dp),
    val hsLift: DpOffset = DpOffset(3.2.dp, 4.4.dp),
)

/**
 * The v1 motion settings read through PicnicTheme.motion (the §8.2 tokens live in MotionSpec.kt, `PicnicMotion`).
 * Kept for v1 call sites: [reduced] mirrors LocalReducedMotion.
 */
@Immutable
data class ThemeMotion(
    /** Reduced motion (§8.8): either Android animation scale at 0. */
    val reduced: Boolean,
    /** Sparkles twinkle at 8 fps; stickers pose at 12 fps. */
    val stepFps: Int = 8,
    /** The reduced-motion fade (§8.2 rmFade). */
    val shortMillis: Int = PicnicMotion.rmFade.durationMillis,
    /** The sheet entrance (§8.2 sheetIn). */
    val mediumMillis: Int = PicnicMotion.sheetIn.durationMillis,
)

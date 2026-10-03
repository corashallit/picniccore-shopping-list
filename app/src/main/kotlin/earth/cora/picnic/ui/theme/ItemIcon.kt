package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.icons.LiveSticker
import earth.cora.picnic.icons.StickerArt
import earth.cora.picnic.ui.theme.gen.DecorManifest
import earth.cora.picnic.ui.theme.gen.StickerEntry
import earth.cora.picnic.ui.theme.gen.StickerManifest
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/*
 * The one sticker render path (docs/DESIGN_STICKER.md §3.3, items 36–41, 47). Library vectors (WP1's generated
 * `sk_i_<key>` drawables with named groups) and live stickers ([StickerArt]: a bitmap plus its stored cut path) go
 * through the same steps: a hard shadow drawn at render time from the theme (Day #6E7BD0, Glow #0B0D24, Golden (+3, +4)
 * #E59A7A), then the sticker, then its variant (ghost, holo, pixel, mono, capsule), then the corner flags. Owner: WP4.
 */

/** The flags at a sticker's top-right (§3.3 step 4), each a 20 dp sticker with its own white border. */
enum class StickerFlag {
    /** Lemon 5-point star: added after the list's new-anchor (§7.1). */
    New,

    /** Sakura heart: a favourite (D17). */
    Favourite,

    /** Star for a shiny or jackpot sticker (the row adds the "shiny!" / "jackpot!" chip). */
    Shiny,
}

/** How a sticker is drawn (§3.3 step 3). */
enum class StickerLook { Normal, Ghost, Holo, Pixel, Mono }

/* ------------------------------------------------------------------------------------------------
 * Resolution: which library art draws a ref.
 * ------------------------------------------------------------------------------------------------ */

/** Library stickers by key (WP1's generated manifest). */
object StickerLibrary {
    private val byKey: Map<String, StickerEntry> by lazy { StickerManifest.entries.associateBy { it.key } }

    /** The gashapon capsule sentinel's entry (D5). */
    val capsule: StickerEntry? get() = byKey[StickerManifest.CAPSULE]

    fun entry(key: String): StickerEntry? = byKey[key]

    /**
     * The library art for [key]: its own, else its category's generic, else the capsule (unknown and pending items,
     * D5: `paper_bag` is no longer a placeholder).
     */
    fun resolve(key: String, catalog: IconCatalog): StickerEntry? {
        if (key == StickerManifest.CAPSULE || key == IconCatalog.DEFAULT_ICON) return capsule
        return byKey[key] ?: byKey[catalog.fallbackFor(key)] ?: capsule
    }

    fun isCapsule(entry: StickerEntry?): Boolean = entry?.key == StickerManifest.CAPSULE
}

/** The gold capsule (egg 13, §2.6). */
private val GoldBody = Color(0xFFFFDA4A)
private val GoldTrace = Color(0xFF8A6800)
private val GoldHighlight = Color(0xFFFFF3A6)

/* ------------------------------------------------------------------------------------------------
 * ItemIcon.
 * ------------------------------------------------------------------------------------------------ */

/**
 * An item's sticker (§3.3). CONTRACT (§19.2):
 * - Library vectors by key (else the category's generic, else the capsule); a bespoke [IconRef.customIconId] draws the
 *   live sticker from [LocalStickerArt] (its bitmap, and its cut path for the shadow, the die-cut edge and the ghost),
 *   falling back to the library art while it loads or when its file is gone.
 * - [aisleIndex] tints the capsule sentinel (0-based aisle rotation index; −1 = neutral "Everything else").
 * - [ghost] = a checked row's peeled outline; [holo] = shiny / jackpot foil; [pixel] = the bonus-stage 16 px sprite
 *   (egg 10); [flags] = the corner flags; [drawing] = the busy sparkle while an icon is being decided or drawn.
 * - [contentDescription] null = decorative (rows already read the item name).
 * - [glint] false keeps the static holo foil but never runs the shiny glint loop (500 ms every 4 s): for a holo sticker
 *   on a text plate whose reading hold a glint must not cross (§9, the celebration's bonus card).
 * Sizes: 52 dp rows, 40 dp compact rows, 30 dp basket minis, 26 / 22 dp book, 56 dp album, 120 dp cards and the hero.
 */
@Composable
fun ItemIcon(
    icon: IconRef,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = PicnicTheme.dimens.itemIcon,
    aisleIndex: Int = -1,
    ghost: Boolean = false,
    holo: Boolean = false,
    pixel: Boolean = false,
    flags: Set<StickerFlag> = emptySet(),
    drawing: Boolean = false,
    lifted: Boolean = false,
    /** A jackpot capsule is gold (egg 13). */
    jackpot: Boolean = false,
    /** The monochrome silhouette (themed launcher, widget). */
    mono: Boolean = false,
    /** The kawaii face overlay (album, achievements, empty states only; never on list icons). */
    face: Boolean = false,
    /** Draw the hard shadow (off for stickers that sit inside something drawing its own). */
    shadow: Boolean = true,
    /** Run the shiny glint loop on [holo] foil (false: the foil stays still). */
    glint: Boolean = true,
) {
    @Suppress("UNUSED_VARIABLE") val unusedLifted = lifted
    val catalog = LocalIconCatalog.current
    val stickers = LocalStickerArt.current
    val customId = icon.customIconId
    // Restarts whenever the bespoke id changes (a drawing got attached or replaced): the previous sticker stays until
    // the new one is decoded, so rows never flash.
    val live: LiveSticker? by produceState(customId?.let(stickers::cached), customId, stickers) {
        value = customId?.let { stickers.cached(it) ?: stickers.load(it) }
    }
    val entry = remember(icon.key, catalog) { StickerLibrary.resolve(icon.key, catalog) }
    val look = when {
        ghost -> StickerLook.Ghost
        pixel -> StickerLook.Pixel
        mono -> StickerLook.Mono
        holo -> StickerLook.Holo
        else -> StickerLook.Normal
    }
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    val colors = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    val family = if (jackpot) null else colors.family(aisleIndex)
    val resources = LocalResources.current
    val art = entry?.let { remember(it.drawable) { VectorArtCache.get(resources, it.drawable) } }
    val faceArt = if (face) entry?.face?.let { remember(it) { VectorArtCache.get(resources, it) } } else null
    val reduced = LocalReducedMotion.current
    val glintTime by rememberLoopTime(enabled = holo && glint && !reduced && !ghost)
    Box(modifier.then(semantics).size(size)) {
        Canvas(Modifier.size(size)) {
            val liveSticker = live
            val style = StickerStyle(
                look = look,
                shadow = if (shadow) shadows else null,
                ghostFill = colors.ghostFill,
                ghostDash = colors.ghostDash,
                silhouette = if (colors.isDark) colors.inkSoft else colors.ballpoint,
                recolor = if (StickerLibrary.isCapsule(entry)) capsuleRecolor(family) else null,
                glint = if (holo && glint) Holo.shinyGlint.progressAt(glintTime % PicnicMotion.shinyGlintPeriod) else 0f,
            )
            when {
                liveSticker != null -> drawLiveSticker(liveSticker, style)
                look == StickerLook.Pixel && art != null && entry != null -> drawPixelSticker(entry.key, art, style)
                art != null -> drawSticker(art, style, faceArt)
            }
        }
        if (flags.isNotEmpty()) StickerFlags(flags, size)
        if (drawing) {
            BusySparkle(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size((size * 0.3f).coerceIn(10.dp, 16.dp)),
            )
        }
    }
}

/** Paint overrides for the capsule's named paths: cap = aisle mid, cap_p = pastel band, cap_tr = trace. Gold for null. */
private fun capsuleRecolor(family: StickerFamily?): Map<String, Color> = if (family == null) {
    mapOf("cap" to GoldBody, "cap_p" to GoldHighlight, "cap_tr" to GoldTrace)
} else {
    mapOf("cap" to family.mid, "cap_p" to family.pastel, "cap_tr" to family.trace)
}

/** Everything a sticker draw needs from the theme. */
@Immutable
class StickerStyle(
    val look: StickerLook = StickerLook.Normal,
    val shadow: PicnicShadows? = DayShadows,
    val ghostFill: Color = Color.White,
    val ghostDash: Color = Color(0xFF9AA8F5),
    val silhouette: Color = Color(0xFF25307C),
    val recolor: Map<String, Color>? = null,
    /** The shiny glint's stepped progress (0 = none). */
    val glint: Float = 0f,
)

/** Units of the sticker box: the 52-unit viewport (§3.1 viewBox −2 −2 52 52). */
private const val STICKER_UNITS = 52f

/**
 * Draws a library sticker into this square area (§3.3): the shadow (the cut tinted, at the variant's offset scaled to
 * the sticker), then the look, then the optional face.
 */
fun DrawScope.drawSticker(art: VectorArt, style: StickerStyle, face: VectorArt? = null) {
    val u = size.minDimension / STICKER_UNITS
    val recolor = style.recolor
    val paint: ArtPaint? = recolor?.let { map ->
        val brushes = map.mapValues { SolidColor(it.value) as Brush }
        val fn: ArtPaint = { p, _ -> brushes[p.name] }
        fn
    }
    val box = Size(size.minDimension, size.minDimension)
    when (style.look) {
        StickerLook.Normal, StickerLook.Holo -> {
            drawStickerShadow(art, style.shadow, u, box)
            if (style.look == StickerLook.Holo) {
                drawArt(art, box, groups = setOf("cut", "fills"), paint = paint)
                val clip = art.groupClips["dominant"]
                if (clip != null) {
                    withTransform({ scale(u, u, pivot = Offset.Zero) }) {
                        clipPath(clip) {
                            drawRect(stickerHoloBrush(Size(STICKER_UNITS, STICKER_UNITS)), alpha = Holo.STICKER_ALPHA, size = Size(STICKER_UNITS, STICKER_UNITS))
                        }
                    }
                }
                drawArt(art, box, groups = setOf("lines", "gloss"), paint = paint)
                if (style.glint > 0f) {
                    val cut = art.paths.firstOrNull { it.group == "cut" }?.path
                    if (cut != null) {
                        val scaled = Path().apply { addPath(cut) }
                        scaled.transform(androidx.compose.ui.graphics.Matrix().apply { scale(u, u) })
                        drawGlint(style.glint, Holo.shinyGlint, scaled)
                    }
                }
            } else {
                drawArt(art, box, paint = paint)
            }
            if (face != null) drawArt(face, box)
        }
        StickerLook.Ghost -> drawGhost(art, style, u, box)
        StickerLook.Mono -> {
            drawStickerShadow(art, style.shadow, u, box)
            drawArt(art, box, groups = setOf("cut"))
            val fill = SolidColor(style.silhouette)
            val white = SolidColor(Color.White)
            drawArt(art, box, groups = setOf("fills", "lines"), paint = { _, isStroke -> if (isStroke) white else fill })
        }
        StickerLook.Pixel -> drawArt(art, box, paint = paint)
    }
}

/** The hard shadow: the cut silhouette in the shadow colour at the variant's offset (in sticker units: 1u = 1 dp at 52 dp). */
private fun DrawScope.drawStickerShadow(art: VectorArt, shadows: PicnicShadows?, u: Float, box: Size) {
    if (shadows == null) return
    val dx = shadows.hs.x.value * u
    val dy = shadows.hs.y.value * u
    translate(dx, dy) { drawArt(art, box, groups = setOf("cut"), paint = solid(shadows.color)) }
}

/** The die-cut border: the `cut` group's white stroke is 8u, so the border reaches 4u outside the art (§3.1). */
private const val CUT_STROKE_U = 8f

/** The ghost's dashed outline is stroked 11u on the art's silhouette: 1.5u of it shows outside the 8u die-cut. */
private const val GHOST_STROKE_U = 11f

/** The ghost's dash pattern (§3.3), in sticker units; DashSpec fits it to each contour exactly. */
private const val GHOST_DASH_U = 3f
private const val GHOST_GAP_U = 2f

/** The ghost's monochrome silhouette (§3.3). */
private const val GHOST_SILHOUETTE_ALPHA = 0.18f

/**
 * The peeled ghost (§3.3): the cut silhouette stroked 11u in dashed [StickerStyle.ghostDash] (dash 3 / gap 2, exact),
 * the 8u cut drawn over it (white; Glow #3A438A) so a dashed line shows just outside the border, and the art as a
 * monochrome silhouette at 18 % inside.
 */
private fun DrawScope.drawGhost(art: VectorArt, style: StickerStyle, u: Float, box: Size) {
    val cut = art.paths.filter { it.group == "cut" }
    withTransform({ scale(u, u, pivot = Offset.Zero) }) {
        for (p in cut) drawDashedPath(p.path, style.ghostDash, strokeWidth = GHOST_STROKE_U, nominalDash = GHOST_DASH_U, nominalGap = GHOST_GAP_U)
    }
    drawArt(art, box, groups = setOf("cut"), paint = solid(style.ghostFill))
    // The mono silhouette (a ballpoint shape with white interior lines) at 18 %, so the peeled shape keeps its detail.
    val fill = SolidColor(style.silhouette)
    val white = SolidColor(Color.White)
    inLayer(GHOST_SILHOUETTE_ALPHA, Rect(Offset.Zero, box)) {
        drawArt(art, box, groups = setOf("fills", "lines"), paint = { _, isStroke -> if (isStroke) white else fill })
    }
}

/**
 * A live sticker (§3.3), in the same steps as a library one.
 *
 * - **v4 with its cut path** (`<id>.cut`, the outer edge of the white die-cut in the bitmap's 208 px space): the shadow
 *   is the cut in the shadow colour at the variant's offset; the die-cut is the cut filled white under the bitmap, so
 *   its edge stays a crisp vector at every size (the bitmap is 4 px per unit: exact at 52 dp on xxxhdpi, upscaled for
 *   the 120 dp hero); then the bitmap. The ghost is drawn from the cut like a library sticker's: a dashed line 1.5u wide
 *   just outside the border, the border in the ghost fill, and the art as a monochrome silhouette at 18 % inside it.
 *   Mono is the same silhouette, opaque, on the white die-cut.
 * - **v4 without a readable cut**: the shadow comes from the bitmap's alpha (it carries its white die-cut); the ghost
 *   falls back to the monochrome silhouette at 18 %.
 * - **Legacy** (pre-v4, §3.3 step 5: a 192 px direction-A raster with its outline, border and shadow baked in): drawn as
 *   it is, with no added shadow and no variant that needs a cut (the ghost is the monochrome silhouette at 18 %).
 *
 * Holo clips the foil to the bitmap's alpha; pixel is the sticker as a 16 × 16 px sprite, scaled up nearest-neighbour.
 */
private fun DrawScope.drawLiveSticker(sticker: LiveSticker, style: StickerStyle) {
    val image = sticker.bitmap
    val src = IntSize(image.width, image.height)
    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
    val u = size.minDimension / STICKER_UNITS
    // Bitmap pixels per sticker unit (4 for a v4 sticker), and the scale from bitmap pixels to this canvas.
    val pu = src.width / STICKER_UNITS
    val scale = size.width / src.width
    val cut = if (sticker.legacy) null else sticker.cut
    val shadows = if (sticker.legacy) null else style.shadow
    fun DrawScope.bitmap(filter: ColorFilter? = null) =
        drawImage(image, IntOffset.Zero, src, IntOffset.Zero, dst, colorFilter = filter, filterQuality = FilterQuality.High)
    fun DrawScope.inBitmapSpace(block: DrawScope.() -> Unit) = withTransform({ scale(scale, scale, pivot = Offset.Zero) }, block)
    fun DrawScope.hardShadow() {
        if (shadows == null) return
        translate(shadows.hs.x.value * u, shadows.hs.y.value * u) {
            if (cut != null) inBitmapSpace { drawPath(cut, shadows.color) } else bitmap(ColorFilter.tint(shadows.color, BlendMode.SrcIn))
        }
    }
    // The art alone, as a flat silhouette: the whole bitmap tinted, then its 4u border repainted from the cut path.
    fun DrawScope.silhouette(cut: Path, alpha: Float, border: Color) {
        inLayer(alpha) { bitmap(ColorFilter.tint(style.silhouette, BlendMode.SrcIn)) }
        inBitmapSpace { clipPath(cut) { drawPath(cut, border, style = Stroke(width = CUT_STROKE_U * pu)) } }
    }
    when (style.look) {
        StickerLook.Ghost -> if (cut != null) {
            inBitmapSpace {
                drawDashedPath(cut, style.ghostDash, strokeWidth = (GHOST_STROKE_U - CUT_STROKE_U) * pu, nominalDash = GHOST_DASH_U * pu, nominalGap = GHOST_GAP_U * pu)
                drawPath(cut, style.ghostFill)
            }
            silhouette(cut, GHOST_SILHOUETTE_ALPHA, style.ghostFill)
        } else {
            inLayer(GHOST_SILHOUETTE_ALPHA) { bitmap(ColorFilter.tint(style.silhouette, BlendMode.SrcIn)) }
        }
        StickerLook.Mono -> {
            hardShadow()
            if (cut != null) {
                inBitmapSpace { drawPath(cut, Color.White) }
                silhouette(cut, 1f, Color.White)
            } else {
                bitmap()
            }
        }
        StickerLook.Pixel -> {
            val sprite = PixelSprites.get(image)
            val spriteSrc = IntSize(sprite.width, sprite.height)
            shadows?.let { s ->
                translate(s.hs.x.value * u, s.hs.y.value * u) {
                    drawImage(sprite, IntOffset.Zero, spriteSrc, IntOffset.Zero, dst, colorFilter = ColorFilter.tint(s.color, BlendMode.SrcIn), filterQuality = FilterQuality.None)
                }
            }
            drawImage(sprite, IntOffset.Zero, spriteSrc, IntOffset.Zero, dst, filterQuality = FilterQuality.None)
        }
        StickerLook.Normal, StickerLook.Holo -> {
            hardShadow()
            if (cut != null) inBitmapSpace { drawPath(cut, Color.White) }
            bitmap()
            if (style.look == StickerLook.Holo) {
                // Bitmaps clip the holo to their alpha (SrcAtop over the sticker just drawn, in a layer).
                inLayer(1f) {
                    bitmap()
                    drawRect(stickerHoloBrush(size), alpha = Holo.STICKER_ALPHA, blendMode = BlendMode.SrcAtop)
                }
            }
        }
    }
}

/** The pixel look (egg 10): the sticker drawn into a 16 × 16 px bitmap, scaled up nearest-neighbour. */
private fun DrawScope.drawPixelSticker(key: String, art: VectorArt, style: StickerStyle) {
    val sprite = PixelSprites.get(key, art)
    val dst = IntSize(size.width.toInt(), size.height.toInt())
    val src = IntSize(sprite.width, sprite.height)
    style.shadow?.let { s ->
        val u = size.minDimension / STICKER_UNITS
        translate(s.hs.x.value * u, s.hs.y.value * u) {
            drawImage(sprite, IntOffset.Zero, src, IntOffset.Zero, dst, colorFilter = ColorFilter.tint(s.color, BlendMode.SrcIn), filterQuality = FilterQuality.None)
        }
    }
    drawImage(sprite, IntOffset.Zero, src, IntOffset.Zero, dst, filterQuality = FilterQuality.None)
}

/** 16 × 16 px sprites of library stickers (rendered once per key) and of live stickers (once per bitmap). */
internal object PixelSprites {
    const val PX = 16
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    /** Keyed by the decoded bitmap itself, so a sprite goes when StickerArt drops its sticker. */
    private val live: MutableMap<ImageBitmap, ImageBitmap> = Collections.synchronizedMap(WeakHashMap())

    fun get(key: String, art: VectorArt): ImageBitmap = cache.getOrPut(key) { sprite { drawArt(art) } }

    fun get(bitmap: ImageBitmap): ImageBitmap = live.getOrPut(bitmap) {
        sprite { drawImage(bitmap, IntOffset.Zero, IntSize(bitmap.width, bitmap.height), IntOffset.Zero, IntSize(PX, PX), filterQuality = FilterQuality.High) }
    }

    private fun sprite(block: DrawScope.() -> Unit): ImageBitmap {
        val image = ImageBitmap(PX, PX)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(PX.toFloat(), PX.toFloat()), block)
        return image
    }
}

/* ------------------------------------------------------------------------------------------------
 * Flags, decor stickers and the busy sparkle.
 * ------------------------------------------------------------------------------------------------ */

/**
 * The corner flags (§3.3 step 4): 20 dp (scaled with the sticker) at the top-right (−6, −6), each its own die-cut
 * decor sticker: lemon star = new, sakura heart = favourite, star = shiny. Several flags step leftwards.
 */
@Composable
private fun StickerFlags(flags: Set<StickerFlag>, size: Dp) {
    val flagSize = (20.dp * (size / 52.dp)).coerceAtLeast(12.dp)
    val overhang = 6.dp * (size / 52.dp)
    val ordered = StickerFlag.entries.filter { it in flags }
    ordered.forEachIndexed { i, flag ->
        val name = if (flag == StickerFlag.Favourite) "heart" else "star"
        DecorSticker(
            name,
            Modifier.offset(x = size - flagSize + overhang - flagSize * 0.72f * i, y = -overhang),
            size = flagSize,
        )
    }
}

/**
 * A decor sticker by name (§4.1, §4.27: `star`, `heart`, `bee`, `ladybug`, `cloud`, `sun`, `moon`, `glowstar`,
 * `kira`, …) at [size], with its hard shadow. `kira` draws in holo foil. Decorative (no semantics).
 */
@Composable
fun DecorSticker(name: String, modifier: Modifier = Modifier, size: Dp = 22.dp, shadow: Boolean = true) {
    val id = DecorManifest.stickers[name] ?: return
    val art = rememberVectorArt(id)
    val shadows = PicnicTheme.shadows
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        drawSticker(art, StickerStyle(look = if (name == "kira") StickerLook.Holo else StickerLook.Normal, shadow = if (shadow) shadows else null))
    }
}

/**
 * The busy sparkle (§4.22): a sparkle twinkling at 8 fps over an icon being decided or drawn; static under reduced
 * motion. Decorative.
 */
@Composable
fun BusySparkle(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val pose = if (reduced) Pose() else Twinkle.at(t)
    val c = PicnicTheme.colors
    Canvas(modifier.clearAndSetSemantics { }) {
        val r = size.minDimension / 2f * pose.scale
        val center = Offset(size.width / 2f, size.height / 2f)
        rotate(pose.rotation, center) {
            drawPath(sparklePath(center, r + 1.5.dp.toPx()), c.stickerWhite)
            drawPath(sparklePath(center, r), Families.sakura.mid)
            drawPath(sparklePath(center, r), Families.sakura.trace, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
}

/** v1 name for the busy sparkle. */
@Composable
fun DrawingSparkle(modifier: Modifier = Modifier) = BusySparkle(modifier)

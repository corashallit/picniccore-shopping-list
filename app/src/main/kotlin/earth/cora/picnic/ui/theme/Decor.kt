package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import earth.cora.picnic.R
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.theme.gen.DecorManifest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * DECORATIVE SLOTS (docs/DESIGN_STICKER.md §4.1, §4.14, §4.21, §4.22, §4.27; ARCHITECTURE §11.3): the binder ground with
 * its gutter texture, dividers, the empty state, loaders, twinkling sparkles and inline vector hearts and stars. All
 * decoration is semantics-free unless a parameter says otherwise; loops stop under reduced motion.
 */

/**
 * Full-bleed screen ground (§4.1): the binder colour and window-aligned pinstripes (Golden: the gradient; Dusk: the
 * three bands and five gutter sparkles; Glow: glow stars in the gutters only). Draws under the system bars. Pinned
 * pieces repaint it in line with [groundBacking]. [gutter] is where the texture may sit (the list's side gutters).
 */
@Composable
fun PicnicBackground(modifier: Modifier = Modifier, gutter: Dp = 8.dp, content: @Composable BoxScope.() -> Unit) {
    val c = PicnicTheme.colors
    val anchor = remember { GroundAnchor() }
    Box(
        modifier
            .onPlaced { anchor.coordinates = it }
            .binderGround(c),
    ) {
        when (c.variant) {
            ThemeVariant.Glow -> GutterTexture(gutter, glow = true, Modifier.matchParentSize())
            ThemeVariant.Dusk -> GutterTexture(gutter, glow = false, Modifier.matchParentSize())
            else -> Unit
        }
        CompositionLocalProvider(LocalGround provides anchor) { content() }
    }
}

/**
 * Glow stars (Glow) or Dusk's four-point sparkles in the binder gutters only (§2.12): texture, exempt from the
 * page-sticker budget, never on a page. Glow stars rest at 85 % and charge to 100 % at 1.15× for 2 frames every 5 s;
 * Dusk sparkles twinkle by the one rule. Still under reduced motion.
 */
@Composable
private fun GutterTexture(gutter: Dp, glow: Boolean, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val art = DecorManifest.stickers["glowstar"]?.let { rememberVectorArt(it) }
    val c = PicnicTheme.colors
    Canvas(modifier.clearAndSetSemantics { }) {
        val mark = (if (glow) 12.dp else 9.dp).toPx()
        val marks = gutterMarks(size.width, size.height, gutter.toPx(), mark, count = if (glow) 6 else 5, seed = if (glow) 7 else 4)
        for (m in marks) {
            if (glow && art != null) {
                val charging = !reduced && ((t + m.delayMillis) % PicnicMotion.glowChargePeriod) < PicnicMotion.pose * PicnicMotion.glowChargeFrames
                val s = if (charging) 1.15f else 1f
                val half = m.size * s / 2f
                translate(m.center.x - half, m.center.y - half) {
                    val area = Size(m.size * s, m.size * s)
                    inLayer(if (charging) 1f else 0.85f, Rect(Offset.Zero, area)) {
                        drawArt(art, area)
                    }
                }
            } else if (!glow) {
                val pose = if (reduced) Pose() else Twinkle.at(t, m.delayMillis)
                rotate(pose.rotation, m.center) {
                    scale(pose.scale, m.center) {
                        drawPath(sparklePath(m.center, m.size / 2f), c.stickerWhite)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Dividers (§4.14).
 * ------------------------------------------------------------------------------------------------ */

/**
 * A divider (§4.14). Minor: the 1 dp rule line. Major (a page break): a perforation of 2 dp dots (2 on / 4 off) in
 * ballpointSoft with an 18 dp scissors sticker at the left.
 */
@Composable
fun SectionDivider(modifier: Modifier = Modifier, major: Boolean = false) {
    val c = PicnicTheme.colors
    if (!major) {
        Box(modifier.fillMaxWidth().height(1.dp).background(c.rule).clearAndSetSemantics { })
        return
    }
    Row(modifier.fillMaxWidth().height(24.dp).clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        DecorSticker("scissors", size = 18.dp)
        Canvas(Modifier.padding(start = 6.dp).weight(1f).height(2.dp)) {
            val d = 2.dp.toPx()
            drawLine(
                c.inkSoft, Offset(d / 2, size.height / 2), Offset(size.width, size.height / 2), d,
                cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, d * 3f)),
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * v1 pieces kept for v1 call sites.
 * ------------------------------------------------------------------------------------------------ */

/** v1 aisle medallion: now the C number circle (§2.7) in the aisle's trace; [aisleIndex] < 0 = "Everything else". */
@Composable
fun AisleMedallion(number: Int?, aisleIndex: Int, modifier: Modifier = Modifier, size: Dp = PicnicTheme.dimens.medallion) {
    val look = if (aisleIndex < 0) AisleLooks.everythingElse else AisleLooks.of(aisleIndex + 1)
    NumberCircle(number ?: if (aisleIndex < 0) null else aisleIndex + 1, look, modifier, size = size)
}

/** v1 trip progress (replaced by WP5's sticker book): the LCD count. */
@Composable
fun TripProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    LcdChip(LcdState(mode = LcdMode.Count, count = done, total = total), modifier)
}

/* ------------------------------------------------------------------------------------------------
 * Inline hearts, stars and notes (§2.7): never glyphs.
 * ------------------------------------------------------------------------------------------------ */

/** The characters copy may carry that are drawn as inline vectors, never as font glyphs. */
const val INLINE_GLYPHS: String = "♡☆★♪✦♥"

/**
 * Text whose ♡ ☆ ★ ♪ ✦ are drawn as inline vector stickers (§2.7: hearts, stars and notes in copy are never glyphs).
 * TalkBack reads the words without them. [overflow] and [onTextLayout] as for Text (a [maxLines] text that may not
 * fit passes TextOverflow.Ellipsis, never a silent clip).
 */
@Composable
fun PicnicText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = style.color,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    /** The laid-out text (a caller that hides it from TalkBack can still report clipping, §15). */
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            for (ch in text) {
                if (ch in INLINE_GLYPHS) appendInlineContent(ch.toString(), ch.toString()) else append(ch)
            }
        }
    }
    val spoken = remember(text) { spokenWithoutGlyphs(text) }
    val inline = remember(style.fontSize) {
        INLINE_GLYPHS.associate { ch ->
            ch.toString() to InlineTextContent(Placeholder(1.05.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                InlineGlyph(ch, Modifier.fillMaxSizeCompat())
            }
        }
    }
    Text(
        annotated,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = onTextLayout ?: {},
        inlineContent = inline,
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(spoken) },
    )
}

private fun Modifier.fillMaxSizeCompat(): Modifier = this.fillMaxSize()

/** [text] without the inline glyphs, spaces collapsed (what TalkBack reads). */
fun spokenWithoutGlyphs(text: String): String =
    text.filterNot { it in INLINE_GLYPHS }.replace(Regex("\\s+"), " ").trim()

/** One inline vector: ♡/♥ a sakura heart, ☆/★/✦ a lemon star, ♪ a note. */
@Composable
private fun InlineGlyph(ch: Char, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val s = size.minDimension
        when (ch) {
            '♡', '♥' -> {
                val heart = heartPath(c, s * 0.82f)
                drawPath(heart, Families.sakura.mid)
                drawPath(heart, Families.sakura.trace, style = Stroke(s * 0.08f))
                drawCircle(Color.White, s * 0.07f, Offset(c.x - s * 0.18f, c.y - s * 0.12f))
            }
            '♪' -> {
                drawCircle(Families.periwinkle.trace, s * 0.16f, Offset(c.x - s * 0.12f, c.y + s * 0.22f))
                drawLine(Families.periwinkle.trace, Offset(c.x + s * 0.03f, c.y + s * 0.22f), Offset(c.x + s * 0.03f, c.y - s * 0.34f), s * 0.09f)
                drawLine(Families.periwinkle.trace, Offset(c.x + s * 0.03f, c.y - s * 0.34f), Offset(c.x + s * 0.28f, c.y - s * 0.22f), s * 0.09f, cap = StrokeCap.Round)
            }
            '✦' -> {
                // Outlined like the star, so it holds its shape on lemon paper (the new-book note).
                val sparkle = sparklePath(c, s * 0.42f)
                drawPath(sparkle, Families.lemon.mid)
                drawPath(sparkle, Families.lemon.trace, style = Stroke(s * 0.07f))
            }
            else -> {
                val star = starPath(c, s * 0.46f, s * 0.22f)
                drawPath(star, Families.lemon.mid)
                drawPath(star, Families.lemon.trace, style = Stroke(s * 0.07f))
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Sparkles (§4.27).
 * ------------------------------------------------------------------------------------------------ */

/**
 * A four-point sparkle that twinkles by the one rule (three scale poses at 8 fps, offset by [delayMillis]): a shape
 * change, never a flash. Static under reduced motion. Decorative.
 */
@Composable
fun TwinkleSparkle(modifier: Modifier = Modifier, color: Color = Families.sakura.mid, trace: Color = Families.sakura.trace, delayMillis: Long = 0) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val pose = if (reduced) Pose() else Twinkle.at(t, delayMillis)
    Canvas(modifier.clearAndSetSemantics { }.graphicsLayer { scaleX = pose.scale; scaleY = pose.scale; rotationZ = pose.rotation }) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        drawPath(sparklePath(c, r), Color.White)
        drawPath(sparklePath(c, r * 0.8f), color)
        drawPath(sparklePath(c, r * 0.8f), trace, style = Stroke(r * 0.09f))
    }
}

/* ------------------------------------------------------------------------------------------------
 * The empty state (§4.21).
 * ------------------------------------------------------------------------------------------------ */

/**
 * "A brand-new sticker sheet!" (§4.21): a backing card with punch holes, a fresh sticker sheet with five unpeeled
 * stickers (gingham blanket, picnic basket, strawberries, bee, lemonade; faces allowed here) and an envelope with a
 * heart seal, the sheet's printed caption "sticker sheet 1 of 2", the [title] (DynaPuff 22 sp), the [body] (Zen Maru 500
 * 16 sp), the doodle "♡ let's go shopping ♡" and the [action] (primary "Start my list").
 */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    PicnicSurface(modifier.fillMaxWidth(), color = c.backing, large = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .punchHoles(c.binder)
                .padding(start = 30.dp, end = 18.dp, top = 22.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StickerSheetArt()
            Text(title, style = t.emptyTitle.copy(color = c.ballpoint), textAlign = TextAlign.Center)
            // Balanced lines (§4.6, the balanced Heading strategy): never a one-word last line ("…say it out / loud.").
            Text(body, style = t.body.copy(color = c.ballpoint, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading), textAlign = TextAlign.Center)
            PicnicText(
                "♡ " + stringResource(R.string.theme_empty_doodle) + " ♡",
                style = t.doodle.copy(color = Families.sakura.trace),
                modifier = Modifier.clearAndSetSemantics { },
            )
            if (action != null) Box(Modifier.padding(top = 4.dp)) { action() }
        }
    }
}

/** The fresh sticker sheet with its five stickers and the envelope (decorative). */
@Composable
private fun StickerSheetArt() {
    val caption = stringResource(R.string.theme_empty_sheet_caption)
    val c = PicnicTheme.colors
    Box(Modifier.width(272.dp).height(146.dp).clearAndSetSemantics { }) {
        Box(
            Modifier
                .offset(x = 4.dp, y = 4.dp)
                .size(width = 204.dp, height = 136.dp)
                .hardShadow(PicnicTheme.shapes.card, PicnicTheme.shadows)
                .background(Color.White, PicnicTheme.shapes.card)
                .drawBehind {
                    val inset = 8.dp.toPx()
                    val p = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(6.dp.toPx())))
                    }
                    drawDashedPath(p, Families.periwinkle.mid, 1.5.dp.toPx(), 3.dp.toPx(), 2.5.dp.toPx())
                },
        ) {
            DecorSticker("blanket", Modifier.offset(18.dp, 12.dp).rotate(-6f), size = 44.dp)
            DecorSticker("basketdeco", Modifier.offset(78.dp, 9.dp), size = 48.dp)
            ItemIcon(IconRef("strawberry"), null, Modifier.offset(140.dp, 11.dp), size = 44.dp)
            DecorSticker("bee", Modifier.offset(44.dp, 58.dp).rotate(4f), size = 42.dp)
            ItemIcon(IconRef("lemonade"), null, Modifier.offset(106.dp, 56.dp), size = 44.dp)
            // The caption is printed on the sheet: like the stickers it keeps the art's size at any font scale, so it
            // always fits its fixed 204 dp sheet (decorative; the empty state's own title and body grow).
            val art = 1f / LocalDensity.current.fontScale
            Text(
                caption,
                style = PicnicTheme.type.doodle.copy(
                    color = c.ballpointSoft,
                    fontSize = PicnicTheme.type.caption.fontSize * art,
                    lineHeight = PicnicTheme.type.caption.lineHeight * art,
                ),
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }
        DecorSticker("envelope", Modifier.offset(x = 196.dp, y = 56.dp).rotate(8f), size = 72.dp)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Loaders (§4.22).
 * ------------------------------------------------------------------------------------------------ */

/** The four loaders (§4.22). */
enum class LoaderKind {
    /** AI text / recipe: the gashapon capsule wobbling ±8° on threes. */
    Gashapon,

    /** Photo and voice: a bee flying a dotted figure-8 (2.4 s loop). */
    BeeLoop,

    /** Re-sort, sync, icon drawing in the Edit sheet: a ladybug walking a 110 dp dashed ring (3 s) while an LCD counts. */
    LadybugRing,

    /** An icon being decided or drawn: a 14 dp sparkle twinkling at 8 fps. */
    BusySparkle,
}

/**
 * A loader (§4.22) with its [label] announced once (polite). Under reduced motion it becomes the static art plus
 * "working…". [family] tints the gashapon (the target aisle's mid); [count] is the ladybug ring's LCD.
 */
@Composable
fun PicnicLoader(
    kind: LoaderKind,
    label: String?,
    modifier: Modifier = Modifier,
    family: StickerFamily = Families.grape,
    count: String? = null,
) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val c = PicnicTheme.colors
    val working = stringResource(R.string.theme_loader_working)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (kind) {
            LoaderKind.Gashapon -> {
                val pose = if (reduced) Pose() else PicnicMotion.gashaponPoses[((t / PicnicMotion.gashaponPoseMillis) % 3).toInt()]
                ItemIcon(
                    IconRef(earth.cora.picnic.ui.theme.gen.StickerManifest.CAPSULE), null,
                    Modifier.graphicsLayer { rotationZ = pose.rotation },
                    size = 52.dp,
                    aisleIndex = Families.rotation.indexOf(family),
                )
            }
            LoaderKind.BeeLoop -> {
                val phase = if (reduced) 0.125f else (t % PicnicMotion.beeLoop).toFloat() / PicnicMotion.beeLoop
                Box(Modifier.size(width = 96.dp, height = 56.dp)) {
                    Canvas(Modifier.matchParentSize()) {
                        val path = Path()
                        for (i in 0..64) {
                            val p = figureEight(i / 64f, size)
                            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        }
                        drawPath(path, c.inkSoft.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 5.dp.toPx()))))
                    }
                    val density = LocalDensity.current
                    var boxSize = Size(with(density) { 96.dp.toPx() }, with(density) { 56.dp.toPx() })
                    val p = figureEight(phase, boxSize)
                    with(density) {
                        DecorSticker("bee", Modifier.offset((p.x.toDp() - 11.dp), (p.y.toDp() - 11.dp)), size = 22.dp)
                    }
                }
            }
            LoaderKind.LadybugRing -> {
                val phase = if (reduced) 0.875f else (t % PicnicMotion.ladybugRing).toFloat() / PicnicMotion.ladybugRing
                Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        drawDashedCircle(Families.periwinkle.mid, Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f - 8.dp.toPx(), 2.dp.toPx(), 4.dp.toPx(), 3.dp.toPx())
                    }
                    val a = phase * 2 * PI - PI / 2
                    val r = 47f
                    DecorSticker(
                        "ladybug",
                        Modifier
                            .offset(x = (r * cos(a)).toFloat().dp, y = (r * sin(a)).toFloat().dp)
                            .rotate((phase * 360f) + 90f),
                        size = 20.dp,
                    )
                    if (count != null) LcdChip(LcdState(mode = LcdMode.Text, text = count))
                }
            }
            LoaderKind.BusySparkle -> BusySparkle(Modifier.size(14.dp))
        }
        val shown = if (reduced) (label?.let { "$it · $working" } ?: working) else label
        if (shown != null) {
            Text(
                shown,
                style = PicnicTheme.type.label.copy(color = c.inkSoft),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** A figure-8 (lemniscate) through [size] at [t] (0..1). */
private fun figureEight(t: Float, size: Size): Offset {
    val a = t * 2 * PI
    val x = size.width / 2f + (size.width / 2f - 12f) * sin(a).toFloat()
    val y = size.height / 2f + (size.height / 2f - 12f) * (sin(a) * cos(a)).toFloat()
    return Offset(x, y)
}

/** v1 loader: the bee loop with its label. */
@Composable
fun LoadingSparkle(modifier: Modifier = Modifier, label: String? = null) {
    PicnicLoader(LoaderKind.BeeLoop, label, modifier)
}

/** A rule line under text: helper for ruled pads (decorative). */
internal fun DrawScope.drawRule(y: Float, color: Color) {
    drawRect(color, Offset(0f, y), Size(size.width, 1.dp.toPx()))
}

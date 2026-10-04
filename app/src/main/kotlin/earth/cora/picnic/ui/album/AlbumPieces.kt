package earth.cora.picnic.ui.album

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import earth.cora.picnic.ui.theme.PadPatch
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.common.MagicAppearBox
import earth.cora.picnic.ui.common.highlighterPress
import earth.cora.picnic.ui.theme.ChipFace
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.HankoStamp
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.Holo
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalIconCatalog
import earth.cora.picnic.ui.theme.StickerLibrary
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicColors
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.Stepped
import earth.cora.picnic.ui.theme.StickerFamily
import earth.cora.picnic.ui.theme.Twinkle
import earth.cora.picnic.ui.theme.WashiTape
import earth.cora.picnic.ui.theme.drawArt
import earth.cora.picnic.ui.theme.drawDashedCircle
import earth.cora.picnic.ui.theme.drawGlint
import earth.cora.picnic.ui.theme.gen.DecorManifest
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.rememberLoopTime
import earth.cora.picnic.ui.theme.rememberVectorArt
import earth.cora.picnic.ui.theme.starPath
import earth.cora.picnic.ui.theme.stickerHoloBrush
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The album's pieces (docs/DESIGN_STICKER.md §4.25, §6.10): the index tabs, the vinyl page, the pockets (stars, "?",
 * holo foil + glint, lift + corner curl, face blink), the trip-book rows, the perfect-weeks card, the seasonal cards and
 * the back pocket. Owner: WP8. Holo is drawn here only for holo pockets and the perfect-week card (HoloUsageTest).
 */

/** The album's colours (§2.6): the pocket's clear vinyl and sleeve lip (the variant's sticker white), the empty pocket's inset outline. */
internal object AlbumInk {
    fun pocketFill(c: PicnicColors): Color = c.stickerWhite.copy(alpha = 0.55f)
    fun sleeveLip(c: PicnicColors): Color = c.stickerWhite.copy(alpha = 0.7f)
    val emptyOutline: Color = Families.sakura.mid.copy(alpha = 0.45f)
    val question: Color = Families.sakura.mid.copy(alpha = 0.5f)

    /** The three tabs' families (§6.10: sakura, lemon, peach). */
    fun family(tab: AlbumTab): StickerFamily = when (tab) {
        AlbumTab.Groceries -> Families.sakura
        AlbumTab.Picnic -> Families.lemon
        AlbumTab.Seasonal -> Families.peach
    }
}

/**
 * The album faces (§6.10: the stickers that ship a kawaii face, `sk_f_<key>`: apple, milk bag and sourdough first, the
 * rest as authored). A face drawable's first two paths are its eyes; the blink redraws them half shut, then shut.
 */
object AlbumFaces {
    /** The blink (§4.25, "2 frames"): eyes half shut, then shut, each 90 ms, then open again. */
    const val FRAME_MILLIS: Long = 90

    /** The face drawable's paths that are eyes (its first two). */
    const val EYES: Int = 2
}

/* ------------------------------------------------------------------------------------------------
 * Tabs and the vinyl page.
 * ------------------------------------------------------------------------------------------------ */

/** How the album's tabs fit the width: as roomy index tabs, as tighter ones, or wrapped onto two rows as label chips. */
internal enum class TabFit { Roomy, Compact, Wrapped }

/** A tab's side padding inside its face for [fit]. */
private fun tabPadding(fit: TabFit, selected: Boolean): Dp = when (fit) {
    TabFit.Roomy -> if (selected) 16.dp else 14.dp
    TabFit.Compact, TabFit.Wrapped -> if (selected) 10.dp else 9.dp
}

/**
 * The index tabs (§6.10): Groceries · Picnic · Seasonal on sakura, lemon and peach; each a Role.Tab. They always show
 * whole (§15): when the three no longer fit the width at their roomy padding (large text), the padding tightens; when even
 * that is too wide (font scale 1.3 and up on a 360 dp phone), they wrap onto two rows as kiss-cut label chips (§4.7 lists
 * the album tabs among its filter chips) and the vinyl page starts under them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlbumTabs(selected: AlbumTab, onSelect: (AlbumTab) -> Unit, modifier: Modifier = Modifier) {
    val labels = mapOf(
        AlbumTab.Groceries to stringResource(R.string.album_tab_groceries),
        AlbumTab.Picnic to stringResource(R.string.album_tab_picnic),
        AlbumTab.Seasonal to stringResource(R.string.album_tab_seasonal),
    )
    val type = PicnicTheme.type
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp)) {
        val density = LocalDensity.current
        val labelWidths = remember(labels, selected, density, type) {
            AlbumTab.entries.associateWith { tab ->
                val style = if (tab == selected) type.smallTitle else type.label
                with(density) { measurer.measure(labels.getValue(tab), style, maxLines = 1).size.width.toDp() }
            }
        }
        fun needed(fit: TabFit): Dp = AlbumTab.entries.fold(TAB_GAP * (AlbumTab.entries.size - 1)) { sum, tab ->
            sum + labelWidths.getValue(tab) + tabPadding(fit, tab == selected) * 2 + TAB_BORDER * 2 + 2.dp
        }
        val fit = when {
            needed(TabFit.Roomy) <= maxWidth -> TabFit.Roomy
            needed(TabFit.Compact) <= maxWidth -> TabFit.Compact
            else -> TabFit.Wrapped
        }
        val tab: @Composable (AlbumTab) -> Unit = { t -> AlbumTabFace(labels.getValue(t), t, t == selected, fit) { onSelect(t) } }
        if (fit == TabFit.Wrapped) {
            FlowRow(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) { AlbumTab.entries.forEach { tab(it) } }
        } else {
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
                verticalAlignment = Alignment.Bottom,
            ) { AlbumTab.entries.forEach { tab(it) } }
        }
    }
}

/**
 * One tab in a 48 dp target: a white kiss-cut edge and hard shadow round the family's pastel (mid when selected, and
 * taller, standing up from the page). Wrapped, it is a whole label (rounded all round) rather than a tab joined to the
 * page. Selected tabs read in DynaPuff (smallTitle), the others in Zen Maru; both ballpoint (§2.7: text on pastels).
 */
@Composable
private fun AlbumTabFace(text: String, tab: AlbumTab, on: Boolean, fit: TabFit, onClick: () -> Unit) {
    val family = AlbumInk.family(tab)
    val wrapped = fit == TabFit.Wrapped
    val outer = if (wrapped) RoundedCornerShape(12.dp) else RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    val inner = if (wrapped) RoundedCornerShape(9.dp) else RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = on, interactionSource = null, indication = null, role = Role.Tab, onClick = onClick),
        contentAlignment = if (wrapped) Alignment.Center else Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .heightIn(min = if (wrapped) 40.dp else if (on) 44.dp else 38.dp)
                .hardShadow(outer, PicnicTheme.shadows)
                .clip(outer)
                .background(PicnicTheme.colors.stickerWhite)
                .padding(start = TAB_BORDER, top = TAB_BORDER, end = TAB_BORDER, bottom = if (wrapped) TAB_BORDER else 0.dp)
                .clip(inner)
                .background(if (on) family.mid else family.pastel)
                .padding(horizontal = tabPadding(fit, on), vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            val style = if (on) PicnicTheme.type.smallTitle else PicnicTheme.type.label
            Text(text, style = style.copy(color = PicnicTheme.colors.ballpoint), maxLines = 1, softWrap = false)
        }
    }
}

/** The tabs' kiss-cut edge and the gap between two tabs. */
private val TAB_BORDER: Dp = 3.dp
private val TAB_GAP: Dp = 6.dp

/**
 * One horizontal slice of the vinyl page (§2.6: `#FFE9F2` with `#FFD6E8` 2 dp pinstripes every 10 dp) inside its 3 dp
 * white kiss-cut edge and hard shadow. The page is drawn slice by slice (a lazy list), rounded at its [top] and
 * [bottom]; the pinstripes line up across slices because they are measured from the page's left edge.
 */
@Composable
internal fun Modifier.vinylSlice(top: Boolean, bottom: Boolean): Modifier {
    val c = PicnicTheme.colors
    val outer = RoundedCornerShape(
        topStart = if (top) 14.dp else 0.dp, topEnd = if (top) 14.dp else 0.dp,
        bottomStart = if (bottom) 14.dp else 0.dp, bottomEnd = if (bottom) 14.dp else 0.dp,
    )
    val inner = RoundedCornerShape(
        topStart = if (top) 11.dp else 0.dp, topEnd = if (top) 11.dp else 0.dp,
        bottomStart = if (bottom) 11.dp else 0.dp, bottomEnd = if (bottom) 11.dp else 0.dp,
    )
    return this
        .hardShadow(outer, PicnicTheme.shadows)
        .clip(outer)
        .background(c.stickerWhite)
        .padding(start = 3.dp, end = 3.dp, top = if (top) 3.dp else 0.dp, bottom = if (bottom) 3.dp else 0.dp)
        .clip(inner)
        .drawBehind {
            drawRect(c.albumVinyl)
            val pitch = 10.dp.toPx()
            val w = 2.dp.toPx()
            var x = pitch - w
            while (x < size.width) {
                drawRect(c.albumStripe, Offset(x, 0f), Size(w, size.height))
                x += pitch
            }
        }
}

/** A group's title on the vinyl ("Produce", "Drawn for you", "Picnic sheet"): Zen Maru 700 13.5 sp, ballpoint. */
@Composable
internal fun VinylTitle(text: String, modifier: Modifier = Modifier) {
    // On a pad patch, never straight on the vinyl's pinstripes (§4.1).
    Box(modifier.padding(start = 6.dp, top = 6.dp, bottom = 4.dp)) {
        PadPatch {
            Text(
                text,
                style = PicnicTheme.type.label.copy(color = PicnicTheme.colors.onPad),
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Pockets.
 * ------------------------------------------------------------------------------------------------ */

/** Test hooks (AlbumTest): the pocket's sticker is lifted; how many times its face has blinked. */
val PocketLifted: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("PocketLifted")
val PocketBlinks: SemanticsPropertyKey<Int> = SemanticsPropertyKey("PocketBlinks")
private var SemanticsPropertyReceiver.pocketLifted: Boolean by PocketLifted
private var SemanticsPropertyReceiver.pocketBlinks: Int by PocketBlinks

/** The pocket's height: sleeve lip, the 56 dp sticker, the stars. */
internal val PocketHeight: Dp = 88.dp

/**
 * One vinyl pocket (§4.25). Collected: the 56 dp sticker under a white sleeve lip, three 9 dp rarity stars; [lifted]
 * lifts the sticker (−1, −2 dp, −4°, 2 steps over 120 ms) while its top-right corner curls off the sleeve (0 → 13 dp,
 * 2 steps over 160 ms); [blinkKey] changing blinks a face pocket (2 frames). Holo: the foil and a glint every 3.2 s,
 * and now and then a kira star visits. Empty: a puffy "?" in an inset outline, which wobbles when [wobble] says so.
 * Fresh pockets magic-appear once ([onAppeared]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Pocket(
    pocket: PocketUi,
    lifted: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    blinkKey: Int = 0,
    wobble: Boolean = false,
    onAppeared: () -> Unit = {},
) {
    val reduced = LocalReducedMotion.current
    val label = when {
        !pocket.collected -> stringResource(R.string.album_pocket_empty)
        pocket.holo -> stringResource(R.string.album_pocket_holo, pocket.name, pocket.stars)
        else -> stringResource(R.string.album_pocket, pocket.name, pocket.stars)
    }
    val liftLabel = stringResource(R.string.album_pocket_lift)
    val colors = PicnicTheme.colors
    val vinyl = colors.albumVinyl
    val curlShadow = PicnicTheme.shadows.color.copy(alpha = 0.35f)
    val lift = remember { Animatable(if (lifted) 1f else 0f) }
    val curl = remember { Animatable(if (lifted) 1f else 0f) }
    LaunchedEffect(lifted, reduced) {
        val target = if (lifted) 1f else 0f
        if (reduced) {
            lift.snapTo(target)
            curl.snapTo(target)
        } else {
            withContext(FixedMotionScale) {
                coroutineScope {
                    launch { lift.animateTo(target, tween(120, easing = Stepped(2))) }
                    launch { curl.animateTo(target, tween(160, easing = Stepped(2))) }
                }
            }
        }
    }
    val loop by rememberLoopTime(enabled = pocket.holo && !reduced)
    val glintPeriod = 3200L
    val kiraPhase = remember(pocket.key) { Math.floorMod(pocket.key.hashCode(), 6) * 1000L }

    Box(
        modifier
            .fillMaxWidth()
            .height(PocketHeight)
            .then(
                if (pocket.collected) {
                    // No ripple: the pressed pocket answers with its own lift and curl (§4.25).
                    Modifier.combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = liftLabel,
                        onClick = onTap,
                        onLongClick = onLongPress,
                    )
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = label
                pocketLifted = lifted
                pocketBlinks = blinkKey
            }
            .drawWithContent {
                val r = 8.dp.toPx()
                val rr = RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))
                val clip = Path().apply { addRoundRect(rr) }
                if (pocket.holo) {
                    clipPath(clip) { drawRect(stickerHoloBrush(size)) }
                } else {
                    drawPath(clip, AlbumInk.pocketFill(colors))
                }
                if (!pocket.collected) {
                    val inset = 4.dp.toPx()
                    drawRoundRect(
                        AlbumInk.emptyOutline,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(r - 2.dp.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )
                }
                drawContent()
                // The sleeve lip lies over the sticker's top edge: the sticker sits IN the pocket.
                clipPath(clip) { drawRect(AlbumInk.sleeveLip(colors), size = Size(size.width, 9.dp.toPx())) }
                if (pocket.holo && !reduced) {
                    val phase = loop % glintPeriod
                    if (phase < Holo.glint.durationMillis) drawGlint(Holo.glint.progressAt(phase), clip = clip)
                }
                val curlPx = 13.dp.toPx() * curl.value
                if (curlPx > 0f) drawPageCurl(curlPx, vinyl, Families.periwinkle.mid, curlShadow, colors.stickerWhite)
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (pocket.collected) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 7.dp)) {
                MagicAppearBox(key = pocket.key, play = pocket.fresh, onPlayed = onAppeared, bloomSize = 40f) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                translationX = -1.dp.toPx() * lift.value
                                translationY = -2.dp.toPx() * lift.value
                                rotationZ = -4f * lift.value
                            },
                    ) {
                        PocketSticker(pocket, blinkKey)
                    }
                }
                Spacer(Modifier.height(5.dp))
                RarityStars(pocket.stars)
            }
            if (pocket.holo && !reduced) {
                val inWindow = Math.floorMod(loop - kiraPhase, 6000L) < Twinkle.periodMillis * 3
                if (inWindow) {
                    val pose = Twinkle.at(loop)
                    DecorSticker(
                        "kira",
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-3).dp, y = 3.dp)
                            .graphicsLayer { scaleX = pose.scale; scaleY = pose.scale; rotationZ = pose.rotation },
                        size = 16.dp,
                        shadow = false,
                    )
                }
            }
        } else {
            QuestionMark(wobble && !reduced, Modifier.align(Alignment.Center))
        }
    }
}

/** The sticker in a pocket: a library or live-drawn sticker (with its authored face, if it has one) or a decor sticker. */
@Composable
private fun PocketSticker(pocket: PocketUi, blinkKey: Int) {
    val icon = pocket.icon
    val catalog = LocalIconCatalog.current
    when {
        icon != null -> {
            val face = if (pocket.face) remember(icon.key, catalog) { StickerLibrary.resolve(icon.key, catalog)?.face } else null
            if (face == null) {
                ItemIcon(icon, contentDescription = null, size = 56.dp)
            } else {
                BlinkingFace(icon, face, blinkKey)
            }
        }
        pocket.decor != null -> DecorSticker(pocket.decor, size = 56.dp)
    }
}

/**
 * A sticker with its authored kawaii face; each change of [blinkKey] blinks it: the face's eyes (its first two paths)
 * half shut, then shut, then open again (§4.25: 2 frames). Open, the face is ItemIcon's own.
 */
@Composable
private fun BlinkingFace(icon: IconRef, @DrawableRes face: Int, blinkKey: Int) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(blinkKey) {
        if (blinkKey == 0) return@LaunchedEffect
        withContext(FixedMotionScale) {
            frame = 1
            delay(AlbumFaces.FRAME_MILLIS)
            frame = 2
            delay(AlbumFaces.FRAME_MILLIS)
            frame = 0
        }
    }
    if (frame == 0) {
        ItemIcon(icon, contentDescription = null, size = 56.dp, face = true)
        return
    }
    val art = rememberVectorArt(face)
    Box(Modifier.size(56.dp)) {
        ItemIcon(icon, contentDescription = null, size = 56.dp)
        Canvas(Modifier.size(56.dp).clearAndSetSemantics { }) {
            val u = size.minDimension / FACE_UNITS
            withTransform({ scale(u, u, pivot = Offset.Zero) }) {
                art.paths.forEachIndexed { i, p ->
                    if (i < AlbumFaces.EYES) {
                        val b = p.path.getBounds()
                        val ink = p.fill ?: return@forEachIndexed
                        if (frame == 1) {
                            drawOval(ink, Offset(b.left, b.center.y - b.height / 4f), Size(b.width, b.height / 2f))
                        } else {
                            drawLine(
                                ink,
                                Offset(b.left - b.width * 0.25f, b.center.y),
                                Offset(b.right + b.width * 0.25f, b.center.y),
                                strokeWidth = b.height * 0.42f,
                                cap = StrokeCap.Round,
                            )
                        }
                    } else {
                        p.fill?.let { drawPath(p.path, it, alpha = p.fillAlpha) }
                        if (p.strokeWidth > 0f) {
                            p.stroke?.let { drawPath(p.path, it, alpha = p.strokeAlpha, style = Stroke(p.strokeWidth, cap = p.cap, join = p.join)) }
                        }
                    }
                }
            }
        }
    }
}

/** The face drawables' viewport (the sticker box's 52 units). */
private const val FACE_UNITS = 52f

/**
 * A page curl at the top-right ([bottom] = bottom-right) corner, [curl] px along each edge: the corner is gone (the
 * [under] colour shows through) and its flap lies folded back over the face, a [flapColor] (sticker white) triangle with
 * an [outline] and a small hard shadow.
 */
internal fun DrawScope.drawPageCurl(curl: Float, under: Color, outline: Color, shadow: Color, flapColor: Color, bottom: Boolean = false) {
    val w = size.width
    val h = size.height
    fun y(v: Float) = if (bottom) h - v else v
    val corner = Path().apply {
        moveTo(w - curl, y(0f))
        lineTo(w, y(0f))
        lineTo(w, y(curl))
        close()
    }
    drawPath(corner, under)
    // The flap is the corner reflected over the fold line: (w − curl, 0) – (w − curl, curl) – (w, curl).
    val flap = Path().apply {
        moveTo(w - curl, y(0f))
        lineTo(w - curl, y(curl))
        lineTo(w, y(curl))
        close()
    }
    val s = 1.5.dp.toPx()
    withTransformTranslate(-s * 0.4f, if (bottom) -s else s) { drawPath(flap, shadow) }
    drawPath(flap, flapColor)
    drawPath(flap, outline, style = Stroke(1.dp.toPx()))
}

private inline fun DrawScope.withTransformTranslate(dx: Float, dy: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(dx, dy)
    block()
    drawContext.transform.translate(-dx, -dy)
}

/** Three 9 dp rarity stars: filled lemon (lemon trace edge) for each star earned, hollow periwinkle for the rest. */
@Composable
internal fun RarityStars(stars: Int, modifier: Modifier = Modifier) {
    val hollow = PicnicTheme.colors.stickerWhite
    Canvas(modifier.size(width = 33.dp, height = 9.dp).clearAndSetSemantics { }) {
        val s = 9.dp.toPx()
        val gap = 3.dp.toPx()
        for (i in 0 until 3) {
            val center = Offset(s / 2f + i * (s + gap), s / 2f)
            val path = starPath(center, s / 2f, s / 2f * 0.5f)
            if (i < stars) {
                drawPath(path, Families.lemon.mid)
                drawPath(path, Families.lemon.trace, style = Stroke(0.9.dp.toPx()))
            } else {
                drawPath(path, hollow)
                drawPath(path, Families.periwinkle.mid, style = Stroke(0.9.dp.toPx()))
            }
        }
    }
}

/** The empty pocket's puffy "?" (22 sp, sakura at 50 %); [wobble] plays the three-pose wobble once. */
@Composable
private fun QuestionMark(wobble: Boolean, modifier: Modifier = Modifier) {
    var angle by remember { mutableStateOf(0f) }
    LaunchedEffect(wobble) {
        if (!wobble) return@LaunchedEffect
        withContext(FixedMotionScale) {
            for (a in listOf(-8f, 6f, 0f)) {
                angle = a
                delay(125)
            }
        }
    }
    Text(
        "?",
        style = PicnicTheme.type.counter.copy(fontSize = 22.sp, lineHeight = 26.sp, color = AlbumInk.question),
        modifier = modifier.rotate(angle).clearAndSetSemantics { },
    )
}

/* ------------------------------------------------------------------------------------------------
 * Trip-book rows, the perfect-weeks card, seasonal cards, the back pocket.
 * ------------------------------------------------------------------------------------------------ */

/**
 * "Trip sticker books": a backing card with one row per filed book, newest first (§4.25). A book filed since the album
 * was last open ([BookRowUi.fresh]) blooms in with the magic appear once (§7.8, R5: in place, never a flight), then
 * [onAppeared] marks it seen.
 */
@Composable
internal fun TripBooksCard(rows: List<BookRowUi>, onOpen: (BookRowUi) -> Unit, modifier: Modifier = Modifier, onAppeared: (String) -> Unit = {}) {
    val c = PicnicTheme.colors
    PicnicSurface(modifier.fillMaxWidth(), color = c.backing) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.album_books_title),
                style = PicnicTheme.type.smallTitle.copy(color = c.ballpoint),
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
            rows.forEach { row -> BookRow(row, onOpen = { onOpen(row) }, onAppeared = { onAppeared(row.id) }) }
        }
    }
}

/**
 * "2 trip sticker books ▾" (§6.10): a text button above the tabs that scrolls down to the Trip sticker books card, so
 * her books are one tap away however long the pocket page above them is.
 */
@Composable
internal fun BooksJump(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().testTag(AlbumTags.JUMP), horizontalArrangement = Arrangement.End) {
        PicnicButton(
            pluralStringResource(R.plurals.album_books_jump, count, count),
            onClick = onClick,
            style = PicnicButtonStyle.Text,
            glyph = PicnicGlyph.Expand,
            // On the binder over the vinyl: on its pad patch, never straight on the pinstripes (§4.1).
            padPatch = true,
        )
    }
}

/** Test hook (AlbumTest): a trip-book row whose magic appear is still to play. */
val BookRowFresh: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("BookRowFresh")
private var SemanticsPropertyReceiver.bookRowFresh: Boolean by BookRowFresh

/** One filed book: up to 6 mini stickers (20 dp), the date (DynaPuff 15 sp), "19 of 19 stickers · perfect · receipt". */
@Composable
private fun BookRow(row: BookRowUi, onOpen: () -> Unit, onAppeared: () -> Unit) {
    val c = PicnicTheme.colors
    val parts = buildList {
        add(pluralStringResource(R.plurals.album_book_count, row.total, row.stickers, row.total))
        if (row.perfect) add(stringResource(R.string.album_book_perfect))
        if (row.receipt) add(stringResource(R.string.album_book_receipt))
    }
    val line = parts.joinToString(" · ")
    PicnicSurface(Modifier.fillMaxWidth(), color = c.stickerWhite) {
        Row(
            Modifier
                .fillMaxWidth()
                // On the white card, so the pressed highlighter lies on the paper, under the minis and the words.
                .clickable(
                    interactionSource = null,
                    indication = highlighterPress(),
                    onClickLabel = stringResource(R.string.album_book_open),
                    role = Role.Button,
                    onClick = onOpen,
                )
                .semantics(mergeDescendants = true) { bookRowFresh = row.fresh }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MagicAppearBox(key = row.id, play = row.fresh, onPlayed = onAppeared, bloomSize = 40f) {
                MiniStickers(row.minis, row.perfect)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.dateLabel, style = PicnicTheme.type.smallTitle.copy(color = c.ballpoint))
                Text(line, style = PicnicTheme.type.caption.copy(color = c.ballpoint))
            }
        }
    }
}

/** The book's thumbnail: its first six stickers at 20 dp, two rows of three, on a little page (lemon when perfect). */
@Composable
private fun MiniStickers(minis: List<IconRef>, perfect: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val paper = PicnicTheme.colors.stickerWhite
    Column(
        Modifier
            .clip(shape)
            .background(Families.sakura.pastel)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (perfect) Families.lemon.pastel else paper)
            .padding(5.dp)
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        minis.chunked(3).take(2).forEach { rowIcons ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                rowIcons.forEach { ItemIcon(it, contentDescription = null, size = 20.dp) }
            }
        }
    }
}

/**
 * The perfect-weeks card (§4.25): a pad card, "Perfect weeks" (DynaPuff 15 sp), "week 4 of 4 · sakura sheet unlocked"
 * (13 sp), four stamped circles, the 44 dp PER-FECT! hanko on its top-right corner once earned. Holo foil for the week
 * after a perfect week ([holo], egg 14).
 */
@Composable
internal fun PerfectWeeksCard(streak: Int, earned: Boolean, holo: Boolean, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val ink = if (holo) c.ballpoint else c.ink
    val inkSoft = if (holo) c.ballpoint else c.inkSoft
    val weeks = if (earned) 4 else streak.coerceIn(0, 4)
    val line = stringResource(R.string.album_perfect_week, weeks) +
        if (earned) " · " + stringResource(R.string.album_perfect_unlocked) else ""
    Box(modifier.fillMaxWidth()) {
        PicnicSurface(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (holo) Modifier.drawBehind { drawRect(stickerHoloBrush(size)) } else Modifier)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.album_perfect_title),
                        style = PicnicTheme.type.smallTitle.copy(color = ink),
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(line, style = PicnicTheme.type.caption.copy(color = inkSoft))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 10.dp)) {
                    WeekStamps.forEachIndexed { i, (family, pictogram) ->
                        val done = i < weeks
                        val desc = if (done) {
                            stringResource(R.string.album_perfect_stamp_done, i + 1)
                        } else {
                            stringResource(R.string.album_perfect_stamp_not_yet, i + 1)
                        }
                        StampCircle(family, pictogram, done, Modifier.semantics { contentDescription = desc })
                    }
                }
            }
        }
        if (earned) {
            HankoStamp(
                HankoText.PerFect,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-14).dp)
                    .rotate(-10f)
                    .clearAndSetSemantics { },
                size = 44.dp,
            )
        }
    }
}

/** The four weeks' stamps: strawberry, bee, star, heart (the mockup's perfect-weeks card). */
private val WeekStamps: List<Pair<StickerFamily, String>> = listOf(
    Families.strawberry to "strawberry",
    Families.ramune to "bee",
    Families.lemon to "star",
    Families.melon to "heart",
)

/**
 * A stamp circle (§4.13, §6.6): a dashed ring in the family's trace (mid in Glow), white inside; once [done], the
 * family's pictogram is pressed in it. [size] 28 dp in the album, 32 dp in the book sheet.
 */
@Composable
internal fun StampCircle(family: StickerFamily, pictogram: String, done: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val c = PicnicTheme.colors
    val ink = if (c.isDark) family.mid else family.trace
    val id = DecorManifest.pictograms[pictogram]
    val art = id?.let { rememberVectorArt(it) }
    val paper = c.stickerWhite
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val center = Offset(r, r)
        drawCircle(paper, r - 1.dp.toPx(), center)
        drawDashedCircle(if (done) ink else Families.periwinkle.mid, center, r - 1.5.dp.toPx(), 2.dp.toPx(), 3.dp.toPx(), 2.dp.toPx())
        if (done && art != null) {
            val box = this.size.minDimension * 0.62f
            drawArt(
                art,
                size = Size(box, box),
                topLeft = Offset(r - box / 2f, r - box / 2f),
                paint = { p, _ -> if (p.name == "paper") SolidColor(paper) else SolidColor(ink) },
            )
        }
    }
}

/**
 * A seasonal card (§4.25): a backing card under the season's washi, the sheet name and, for the upcoming one, the lemon
 * "next! · Thanksgiving Oct 12" chip; three 40 dp stickers with labels, at 55 % until the sheet is unlocked.
 */
@Composable
internal fun SeasonCard(card: SeasonCardUi, nextLabel: String?, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    Box(modifier.fillMaxWidth().padding(top = 10.dp)) {
        PicnicSurface(Modifier.fillMaxWidth(), color = c.backing) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        card.sheet.name,
                        style = PicnicTheme.type.smallTitle.copy(color = c.ballpoint),
                        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                        maxLines = 2,
                    )
                    if (nextLabel != null) {
                        ChipFace(fill = Families.lemon.mid, height = 30.dp, horizontal = 10.dp) {
                            Text(nextLabel, style = PicnicTheme.type.label.copy(color = c.ballpoint), maxLines = 1)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    card.sheet.stickers.forEach { s ->
                        val desc = if (card.unlocked) s.label else stringResource(R.string.album_season_locked, s.label)
                        Column(
                            Modifier.width(88.dp).semantics(mergeDescendants = true) { contentDescription = desc },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(Modifier.alpha(if (card.unlocked) 1f else 0.55f)) { SheetStickerArt(s, 40.dp) }
                            Text(
                                s.label,
                                style = PicnicTheme.type.caption.copy(color = c.ballpoint),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alpha(if (card.unlocked) 1f else 0.7f).clearAndSetSemantics { },
                            )
                        }
                    }
                }
            }
        }
        // The tape straddles the card's top edge (8 dp on the card), clear of the sheet name below it.
        WashiTape(card.sheet.tape, Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = (-10).dp), length = 92.dp, height = 18.dp, rotation = -3f)
    }
}

/** The back pocket's card (tests). */
const val BACK_POCKET_TAG: String = "album_back_pocket"

/** The back pocket's curl opens in 3 poses, 120 ms apart (egg 14's reveal). */
private const val BACK_POCKET_POSES: Int = 3
private const val BACK_POCKET_POSE_MS: Long = 120

/** A sheet sticker: a library sticker or a decor sticker, decorative (its label carries the name). */
@Composable
internal fun SheetStickerArt(s: SheetSticker, size: Dp) {
    if (s.isDecor) DecorSticker(s.key, size = size) else ItemIcon(IconRef(s.key), contentDescription = null, size = size)
}

/**
 * The back pocket (egg 14, after ten finished trips): a vinyl pocket at the back of the album, its corner curled open,
 * "sticker sheet 2 of 2" peeking out with its stickers. [reveal] (the toast's "Look" brought her here, §12.2): the
 * corner curls open (0 → 22 dp in 3 stepped poses) and the sheet's three stickers magic-appear; under reduced motion
 * the corner is open at once and the stickers fade in (120 ms).
 */
@Composable
internal fun BackPocketCard(modifier: Modifier = Modifier, reveal: Boolean = false) {
    val c = PicnicTheme.colors
    val vinyl = c.albumVinyl
    val curlShadow = PicnicTheme.shadows.color.copy(alpha = 0.35f)
    val reduced = LocalReducedMotion.current
    val playing = reveal && !reduced
    // The curl's pose (0–3, 3 = open) and whether the sheet's stickers are out; at rest both done.
    var curlPose by remember { mutableIntStateOf(if (playing) 0 else BACK_POCKET_POSES) }
    var stickersOut by remember { mutableStateOf(!reveal) }
    LaunchedEffect(reveal) {
        if (!reveal) return@LaunchedEffect
        if (!reduced) {
            withContext(FixedMotionScale) {
                for (pose in 1..BACK_POCKET_POSES) {
                    delay(BACK_POCKET_POSE_MS)
                    curlPose = pose
                }
            }
        }
        stickersOut = true
    }
    Column(
        modifier
            .fillMaxWidth()
            .vinylSlice(top = true, bottom = true)
            .padding(12.dp)
            .testTag(BACK_POCKET_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.album_back_pocket),
            style = PicnicTheme.type.smallTitle.copy(color = c.ballpoint),
            modifier = Modifier.semantics { heading() },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .drawBehind {
                    val r = 10.dp.toPx()
                    drawRoundRect(c.stickerWhite, cornerRadius = CornerRadius(r))
                    // The page curl at the bottom-right: the sheet's corner folded back (opening, while it reveals).
                    val curl = 22.dp.toPx() * curlPose / BACK_POCKET_POSES
                    if (curl > 0f) drawPageCurl(curl, vinyl, Families.sakura.mid, curlShadow, c.stickerWhite, bottom = true)
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumSheets.SHEET_2_STICKERS.forEachIndexed { i, sticker ->
                    if (stickersOut) {
                        MagicAppearBox(key = "back-pocket-$i-$reveal", play = reveal, bloomSize = 36f) { SheetStickerArt(sticker, 44.dp) }
                    } else {
                        // Still in the pocket: its room kept, nothing shown.
                        Spacer(Modifier.size(44.dp))
                    }
                }
            }
            Text(
                stringResource(R.string.album_back_pocket_sheet),
                style = PicnicTheme.type.doodle.copy(color = c.ballpointSoft, fontSize = PicnicTheme.type.caption.fontSize),
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.DashSpec
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.VectorArt
import earth.cora.picnic.ui.theme.drawArt
import earth.cora.picnic.ui.theme.drawBasketHanko
import earth.cora.picnic.ui.theme.drawDashedCircle
import earth.cora.picnic.ui.theme.gen.BasketArt
import earth.cora.picnic.ui.theme.gen.BasketManifest
import earth.cora.picnic.ui.theme.gen.DecorManifest
import earth.cora.picnic.ui.theme.rememberLoopTime
import earth.cora.picnic.ui.theme.rememberVectorArt
import earth.cora.picnic.ui.theme.solid
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The aisle's pieces in motion (docs/DESIGN_STICKER.md §5.3, §8.3, §8.4, R4): the basket (landing squash, wobble, rim
 * heart, badge tick, lid, GOT IT on the lid, hop, the reverse on an un-check), the stamp thunking into the stamp page,
 * the tray's curl and fold, the strip's ribbon. WP5's list pieces (AisleBasket, StickerBook, the tray) draw these from
 * the presentation; the chain beats come from the engine. Everything is decorative. Owner: WP6.
 */

/** A gotten sticker in a basket: its item, its sticker, shiny or jackpot holo, the capsule's aisle tint. */
@Immutable
data class BasketMini(val itemId: String, val icon: IconRef, val holo: Boolean = false, val aisleIndex: Int = -1, val pixel: Boolean = false)

/**
 * A basket of [kind] with its gotten [gotten] minis (check-off order) and the [toGet] badge, as the committed state has
 * them ([complete]: the aisle is done, lid shut with its GOT IT), with [presentation]'s overrides played on [clock]:
 * minis not landed yet are hidden and the badge counts them still to get; the newest mini squashes as it lands; the
 * basket wobbles; a sakura heart rises off the rim; the lid shuts (2 frames) and GOT IT slams onto it; the basket hops.
 * [size] 80 × 64 dp on a strip, 64 × 51 in a tray header ([tray]: the badge sits 2 dp from the top), 120 × 96 in cards.
 * The ambient beat (§8.5, a folded basket only): it wobbles from [ambientT0] and [ambientPeekItem] peeks up 3 dp, both
 * read off [ambientClock] (AmbientDirector.clock), never off the engine's [clock].
 */
@Composable
fun MotionBasket(
    kind: BasketKind,
    look: AisleLook,
    gotten: List<BasketMini>,
    toGet: Int,
    complete: Boolean,
    presentation: BasketPresentation?,
    clock: MotionClock,
    modifier: Modifier = Modifier,
    size: DpSize = DpSize(80.dp, 64.dp),
    tray: Boolean = false,
    ambientPeekItem: String? = null,
    ambientT0: Long? = null,
    ambientClock: MotionClock? = null,
) {
    val art = BasketManifest.art(kind.key) ?: return
    val reduced = LocalReducedMotion.current
    val p = presentation
    val shut = p?.lidClosed ?: complete
    val minisHidden = p?.minisHidden ?: shut
    val visible = gotten.filter { p == null || it.itemId !in p.hiddenMinis }
    val fill = if (minisHidden) null else earth.cora.picnic.ui.theme.BasketLayout.fill(visible)
    val shown = fill?.shown.orEmpty()
    val extra = fill?.extra ?: 0
    val count = toGet + (p?.toGetLag ?: 0)
    val unit = size.width / BasketManifest.VIEWPORT_WIDTH
    val back = rememberVectorArt(art.back)
    val front = rememberVectorArt(art.front)
    val lidOpen = rememberVectorArt(art.lidOpen)
    val lidClosed = rememberVectorArt(art.lidClosed)
    val shadows = PicnicTheme.shadows
    val ghostDash = PicnicTheme.colors.ghostDash
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val hankoStyle = PicnicTheme.type.hanko
    Box(
        modifier
            .size(size)
            .decorative()
            .graphicsLayer {
                if (reduced) return@graphicsLayer
                // The ambient wobble (§8.5) plays on the ambient director's clock: the engine's sleeps while idle.
                val ambientWob = if (ambientT0 != null && ambientClock != null) BasketMotion.wobble(ambientClock.now - ambientT0) else 0f
                if (p == null) {
                    rotationZ = ambientWob
                    transformOrigin = TransformOrigin(BasketMotion.wobblePivotX, BasketMotion.wobblePivotY)
                    return@graphicsLayer
                }
                val now = clock.now
                val wob = p.wobbleT0?.let { BasketMotion.wobble(now - it) } ?: 0f
                val hop = p.hopT0?.let { BasketMotion.hop(now - it) } ?: MotionPose.Rest
                rotationZ = wob + ambientWob
                translationY = hop.dy * density
                scaleX = hop.scaleX
                scaleY = hop.scaleY
                transformOrigin = TransformOrigin(BasketMotion.wobblePivotX, BasketMotion.wobblePivotY)
            },
    ) {
        // Behind everything: an open lid that stands behind (bento, cooler, vanity) while the aisle is not done.
        Canvas(Modifier.size(size)) {
            val behind = !shut && (art.lidOpenStandsBehind || p?.peek == true)
            if (behind) drawBasketLayer(lidOpen, shadows.color, shadows.hs.x.toPx(), shadows.hs.y.toPx())
            drawBasketLayer(back, shadows.color, shadows.hs.x.toPx(), shadows.hs.y.toPx())
        }
        // The minis, in their fill-order slots; the newest on top.
        val slots = fill?.slots.orEmpty()
        val miniSize = size.width * (BasketManifest.MINI_SIZE / BasketManifest.VIEWPORT_WIDTH)
        shown.forEachIndexed { i, mini ->
            val slot = slots.getOrNull(i) ?: return@forEachIndexed
            val x = unit * slot.centreX - miniSize / 2
            val y = unit * (art.rimY - BasketManifest.MINI_SIZE * BasketManifest.MINI_LIFT + slot.raise)
            val squashing = p?.squashItem == mini.itemId && p.squashT0 != null
            val peeking = !reduced && ambientPeekItem == mini.itemId && ambientT0 != null && ambientClock != null
            ItemIcon(
                mini.icon, null, size = miniSize, aisleIndex = mini.aisleIndex, holo = mini.holo, pixel = mini.pixel,
                modifier = Modifier
                    .offset(x, y)
                    .graphicsLayer {
                        rotationZ = slot.tilt
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        if (squashing) {
                            val e = clock.now - p!!.squashT0!!
                            if (reduced || p.inPlace) {
                                alpha = fadeIn(e)
                            } else {
                                val (sx, sy) = BasketMotion.squash(e)
                                scaleX = sx
                                scaleY = sy
                            }
                        }
                        if (peeking) translationY = BasketMotion.ambientPeek(ambientClock!!.now - ambientT0!!) * density
                        // A peek (§5.3, WP5): the lid opens and the minis rise 8 dp while she holds the basket.
                        if (p?.peek == true && !reduced) translationY -= PEEK_RISE_DP * density
                    },
            )
        }
        Canvas(Modifier.size(size)) {
            drawArt(front, size = this.size)
            if (shown.isEmpty() && !shut) {
                val u = this.size.width / BasketManifest.VIEWPORT_WIDTH
                drawDashedCircle(
                    ghostDash, Offset(art.landingSpotX * u, art.landingSpotY * u),
                    BasketManifest.LANDING_SPOT_RADIUS * u, 1.8.dp.toPx(), 3.2.dp.toPx(), 2.4.dp.toPx(),
                )
            }
        }
        // The closed lid: shutting from the bottom (.2 → .7 → 1), or re-opening (.7 → .2 → gone).
        Canvas(Modifier.size(size)) {
            val now = clock.now
            val lidT0 = p?.lidT0
            val u = this.size.width / BasketManifest.VIEWPORT_WIDTH
            val lidScale = when {
                shut && lidT0 != null -> if (reduced || p.inPlace) 1f else BasketMotion.lidShut(now - lidT0)
                shut -> 1f
                lidT0 != null -> if (reduced || p.inPlace) 0f else BasketMotion.lidOpen(now - lidT0)
                else -> 0f
            }
            val lidAlpha = when {
                shut && lidT0 != null && (reduced || p.inPlace) -> fadeIn(now - lidT0)
                !shut && lidT0 != null && (reduced || p.inPlace) -> fadeOut(now - lidT0)
                else -> 1f
            }
            if (lidScale > 0f && lidAlpha > 0f) {
                scale(1f, lidScale, pivot = Offset(this.size.width / 2f, (art.rimY + 6f) * u)) {
                    drawBasketLayer(lidClosed, shadows.color, shadows.hs.x.toPx(), shadows.hs.y.toPx(), lidAlpha)
                }
            }
            // GOT IT on the lid (never on the tab): Day vermilion on its white backing in every variant (§5.3).
            val hankoScale = when {
                shut && p?.lidHankoHidden == true -> 0f
                shut && p?.lidHankoT0 != null -> if (reduced || p.inPlace) 1f else BasketMotion.lidHanko(now - p.lidHankoT0)
                shut -> 1f
                p?.lidHankoLiftT0 != null -> if (reduced || p.inPlace) 0f else BasketMotion.lidHankoLift(now - p.lidHankoLiftT0)
                else -> 0f
            }
            val hankoAlpha = when {
                shut && p?.lidHankoT0 != null && (reduced || p.inPlace) -> fadeIn(now - p.lidHankoT0)
                !shut && p?.lidHankoLiftT0 != null && (reduced || p.inPlace) -> fadeOut(now - p.lidHankoLiftT0)
                else -> 1f
            }
            if (hankoScale > 0f && hankoAlpha > 0f && lidScale >= 0.99f || (!shut && hankoScale > 0f)) {
                val s = BasketManifest.HANKO_SIZE * u
                translate(art.hankoX * u - s / 2f, art.hankoY * u - s / 2f) {
                    rotate(BasketManifest.HANKO_ROTATION, Offset(s / 2f, s / 2f)) {
                        scale(hankoScale, Offset(s / 2f, s / 2f)) {
                            drawInSquare(s, hankoAlpha) { drawBasketHanko(HankoText.GotIt, measurer, hankoStyle) }
                        }
                    }
                }
            }
        }
        // The "+n" tag on the right shoulder when more than five are in.
        if (extra > 0) PlusTag(extra, art, unit)
        // The badge: what is still to get (hidden at 0), popping on a change.
        // In place (a ListChange, reduced motion), the badge's count simply changes: no pop (§8.4).
        if (count > 0) Badge(count, look, size, tray, p?.badgePopT0, clock, reduced || p?.inPlace == true)
        // The sakura heart rising off the rim on a landing.
        val heartT0 = p?.heartT0
        if (heartT0 != null && !reduced) {
            DecorSticker(
                "heart",
                size = 14.dp,
                modifier = Modifier
                    .offset(unit * BasketManifest.VIEWPORT_WIDTH / 2 - 7.dp, unit * art.rimY - 7.dp)
                    .graphicsLayer {
                        val pose = BasketMotion.heart(clock.now - heartT0)
                        if (pose == null) alpha = 0f else {
                            translationY = pose.dy * density
                            scaleX = pose.scaleX
                            scaleY = pose.scaleY
                            alpha = pose.alpha
                        }
                    },
            )
        }
    }
}

/** How far the minis rise while a folded basket is peeked (§5.3). */
private const val PEEK_RISE_DP: Float = 8f

/** Draws a basket layer: its `cut` group tinted as the hard shadow, then the art. */
internal fun DrawScope.drawBasketLayer(art: VectorArt, shadow: Color, dx: Float, dy: Float, alpha: Float = 1f) {
    translate(dx, dy) { drawArt(art, size = size, groups = setOf("cut"), paint = solid(shadow), alpha = alpha) }
    drawArt(art, size = size, alpha = alpha)
}

/** Runs [block] with the draw area set to an [s] × [s] square at the current origin. */
internal fun DrawScope.drawInSquare(s: Float, alpha: Float = 1f, block: DrawScope.() -> Unit) {
    val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    if (alpha >= 1f) {
        scope.draw(this, layoutDirection, drawContext.canvas, Size(s, s), block)
    } else {
        val canvas = drawContext.canvas
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, s, s), androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
        scope.draw(this, layoutDirection, canvas, Size(s, s), block)
        canvas.restore()
    }
}

/**
 * The "+n" tag on the basket's right shoulder (§4.12): a 30 × 20 dp white pill with a periwinkle ring and the 13 sp
 * numeral, centred on its manifest point. Like the badge it is sized from the measured numeral, so at large font
 * scales (to 200 %, §15) the pill grows round the numeral instead of the numeral spilling over its ring.
 */
@Composable
private fun PlusTag(extra: Int, art: BasketArt, unit: Dp) {
    val c = PicnicTheme.colors
    val shadow = PicnicTheme.shadows
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val style = PicnicTheme.type.badgeNumeral.copy(color = c.ballpoint, fontSize = 13.sp)
    val layout = measurer.measure("+$extra", style)
    val (w, h) = pillSize(layout.size.width, layout.size.height, minW = 30.dp, minH = 20.dp, padH = 10.dp)
    Canvas(
        Modifier
            .offset(unit * BasketManifest.PLUS_TAG_CENTRE_X - w / 2, unit * BasketManifest.PLUS_TAG_CENTRE_Y - h / 2)
            .size(w, h),
    ) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(shadow.color, Offset(shadow.hs.x.toPx(), shadow.hs.y.toPx()), size, r)
        drawRoundRect(Color.White, Offset.Zero, size, r)
        drawRoundRect(Families.periwinkle.mid, Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()), r, style = Stroke(1.2.dp.toPx()))
        drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f))
    }
}

/**
 * A pill's size round a measured numeral ([textW] × [textH] px): at least [minW] × [minH], else the text plus 4 dp of
 * height and [padH] of width (never narrower than it is tall).
 */
@Composable
private fun pillSize(textW: Int, textH: Int, minW: Dp, minH: Dp, padH: Dp): Pair<Dp, Dp> {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return with(density) {
        val h = maxOf(minH, textH.toDp() + 4.dp)
        val w = maxOf(minW, h, textW.toDp() + padH)
        w to h
    }
}

/**
 * The 22 dp count sticker (§4.12): white, 2 dp aisle-trace border, DynaPuff 13 sp trace numeral, hs; never on the art.
 * It is sized from the measured numeral: a 22 dp circle while the numeral fits, else a pill round it (height = numeral +
 * 4 dp, width = numeral + 10 dp), so the numeral never runs over the ring. Its bottom-left corner stays on the flag
 * slot's and it grows up and to the right, away from the basket, never inward over the art (item 107). The numeral
 * follows the font scale up to [BADGE_MAX_SCALE]: the count it shows is also in the strip's meta line and its TalkBack
 * label, which grow all the way (§15), and past that it would reach the chevron. It pops about its centre. [still]: no
 * pop (reduced motion, or a change played in place).
 */
@Composable
private fun Badge(count: Int, look: AisleLook, basket: DpSize, tray: Boolean, popT0: Long?, clock: MotionClock, still: Boolean) {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val sp = 13f * minOf(fontScale, BADGE_MAX_SCALE) / fontScale
    val style = PicnicTheme.type.badgeNumeral.copy(color = look.family.trace, fontSize = sp.sp, lineHeight = (sp * 1.15f).sp)
    val shadow = PicnicTheme.shadows
    val label = if (count > 99) "99+" else count.toString()
    val layout = measurer.measure(label, style)
    val d = BasketManifest.BADGE_SIZE_DP.dp
    val (w, h) = pillSize(layout.size.width, layout.size.height, minW = d, minH = d, padH = 10.dp)
    val slotTop = (if (tray) BasketManifest.BADGE_TOP_TRAY_DP else BasketManifest.BADGE_TOP_STRIP_DP).dp
    Canvas(
        Modifier
            .offset(basket.width + BasketManifest.BADGE_RIGHT_OUTSIDE_DP.dp - d, slotTop + d - h)
            .size(w, h)
            .testTag(BADGE_TAG)
            .graphicsLayer {
                if (popT0 != null && !still) {
                    val s = BasketMotion.badgePop(clock.now - popT0)
                    scaleX = s
                    scaleY = s
                }
            },
    ) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        val ring = 2.dp.toPx()
        drawRoundRect(shadow.color, Offset(shadow.hs.x.toPx(), shadow.hs.y.toPx()), size, r)
        drawRoundRect(Color.White, Offset.Zero, size, r)
        drawRoundRect(
            look.family.trace,
            Offset(ring / 2f, ring / 2f),
            Size(size.width - ring, size.height - ring),
            androidx.compose.ui.geometry.CornerRadius((size.height - ring) / 2f),
            style = Stroke(ring),
        )
        drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f))
    }
}

/** How far the badge's numeral follows the font scale (§4.12, item 107). */
internal const val BADGE_MAX_SCALE: Float = 1.3f

/** The badge's test tag (in the unmerged tree; the basket is decorative): its bounds are the pill's white face. */
const val BADGE_TAG: String = "picnic_basket_badge"

/* ------------------------------------------------------------------------------------------------
 * The stamp page's circle (§4.13) and its thunk (§8.4).
 * ------------------------------------------------------------------------------------------------ */

/**
 * One stamp circle of the book's stamp page: [size] 24 dp (20 above 8 stops), a 2 dp dashed ballpointSoft ring, the
 * walking [number] (none for "Everything else"); the "now" ring in strawberry trace, turning slowly; stamped = the
 * aisle's pictogram at 0.75, inked by [AisleLook.stampInk], rotated −10°. [phase] overrides the state ([stamped],
 * [now]) while a chain plays: the pictogram thunks in (1.6 → 1.25 → 1, −30° → −16° → −10°, 300 ms) with a 4-pictogram
 * burst in the aisle trace. Reduced motion: a 120 ms fade; the ring does not turn. Decorative.
 */
@Composable
fun StampCircle(
    look: AisleLook,
    number: Int?,
    stamped: Boolean,
    now: Boolean,
    phase: StampPhase?,
    clock: MotionClock,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val reduced = LocalReducedMotion.current
    val variant = PicnicTheme.variant
    val c = PicnicTheme.colors
    val isStamped = when (phase) {
        StampPhase.Waiting, StampPhase.Now -> false
        is StampPhase.Thunking, StampPhase.Stamped -> true
        null -> stamped
    }
    val isNow = when (phase) {
        StampPhase.Now -> true
        StampPhase.Waiting, is StampPhase.Thunking, StampPhase.Stamped -> false
        null -> now && !stamped
    }
    val thunkT0 = (phase as? StampPhase.Thunking)?.t0
    // A ListChange completes the aisle in place: the pictogram fades in (120 ms) with no thunk or burst, as under reduced motion.
    val fade = reduced || (phase as? StampPhase.Thunking)?.inPlace == true
    val pictogram = rememberVectorArt(DecorManifest.pictograms.getValue(look.pictogram.key))
    val ink = SolidColor(look.stampInk(variant))
    val turn by rememberLoopTime(enabled = isNow && !reduced)
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val base = if (size <= 20.dp) PicnicTheme.type.stampNumeral.copy(fontSize = 11.sp) else PicnicTheme.type.stampNumeral
    // The numeral repeats the stop's number: it grows with the font only as far as its circle holds it (§4.13, R2).
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val sp = minOf(base.fontSize.value * fontScale, size.value * 0.55f) / fontScale
    val numeral = base.copy(fontSize = sp.sp, lineHeight = (sp * 1.15f).sp, color = if (isNow) Families.strawberry.trace else c.inkSoft)
    Box(modifier.size(size).decorative(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f - 1.dp.toPx()
            val ringColor = if (isNow) Families.strawberry.trace else c.inkSoft
            rotate(if (isNow) (turn % 6000L) * 360f / 6000f else 0f) {
                drawDashedCircle(ringColor, center, r, 2.dp.toPx(), 3.4.dp.toPx(), 2.6.dp.toPx())
            }
            if (!isStamped && number != null) {
                val layout = measurer.measure(number.toString(), numeral)
                drawText(layout, topLeft = Offset((this.size.width - layout.size.width) / 2f, (this.size.height - layout.size.height) / 2f))
            }
        }
        if (isStamped) {
            Canvas(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        if (thunkT0 == null) {
                            rotationZ = -10f
                        } else if (fade) {
                            rotationZ = -10f
                            alpha = fadeIn(clock.now - thunkT0)
                        } else {
                            val pose = StampMotion.thunk(clock.now - thunkT0)
                            scaleX = pose.scaleX
                            scaleY = pose.scaleY
                            rotationZ = pose.rotation
                            alpha = pose.alpha
                        }
                    },
            ) {
                val s = this.size.width * 0.75f
                drawArt(pictogram, Size(s, s), Offset((this.size.width - s) / 2f, (this.size.height - s) / 2f), paint = { path, _ -> if (path.name == "ink") ink else null })
            }
            if (thunkT0 != null && !fade) {
                for (i in 0 until 4) {
                    Canvas(
                        Modifier
                            .size(size * 0.4f)
                            .offset {
                                val pose = StampMotion.burst(i, clock.now - thunkT0)
                                IntOffset(((pose?.dx ?: 0f) * density).roundToInt(), ((pose?.dy ?: 0f) * density).roundToInt())
                            }
                            .graphicsLayer {
                                val pose = StampMotion.burst(i, clock.now - thunkT0)
                                if (pose == null) alpha = 0f else {
                                    alpha = pose.alpha
                                    scaleX = pose.scaleX
                                    scaleY = pose.scaleY
                                }
                            },
                    ) {
                        val trace = SolidColor(look.family.trace)
                        drawArt(pictogram, this.size, paint = { _, _ -> trace })
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * The tray: its curl (L + 800), its fold (L + 1100), the strip's ribbon (L + 1200, bow L + 1400).
 * ------------------------------------------------------------------------------------------------ */

/**
 * The tray curling away like a page from its bottom-right corner (4 frames, 300 ms): the corner is cut away (the ground
 * shows through) and the flap, its back in [flap] with a periwinkle edge and a hard shadow in [shadow] (the theme's,
 * `PicnicTheme.shadows.color`, so Glow gets its own), folds over it. Draws nothing extra when [curlT0] is null or under
 * reduced motion.
 */
fun Modifier.trayCurl(curlT0: Long?, clock: MotionClock, flap: Color, shadow: Color, edge: Color = Families.periwinkle.mid, reduced: Boolean = false): Modifier =
    drawWithContent {
        val t0 = curlT0
        if (t0 == null || reduced) {
            drawContent()
            return@drawWithContent
        }
        val f = CurlMotion.at(clock.now - t0)
        val leg = min(size.width, size.height) * f
        val content = this
        drawCurl(leg, flap, edge, shadow) { content.drawContent() }
    }

/**
 * Draws [content] with its bottom-right corner folded back by [leg] px (a right triangle with two [leg] legs): the
 * corner is clipped away and the flap (the triangle mirrored over the fold line) is drawn on top.
 */
fun DrawScope.drawCurl(leg: Float, flap: Color, edge: Color, shadow: Color, content: DrawScope.() -> Unit) {
    if (leg <= 0.5f) {
        content()
        return
    }
    val w = size.width
    val h = size.height
    val corner = Path().apply {
        moveTo(w - leg, h)
        lineTo(w, h)
        lineTo(w, h - leg)
        close()
    }
    clipPath(corner, ClipOp.Difference) { this.content() }
    // The flap: the corner reflected across the fold line (w − leg, h) – (w, h − leg) lands at (w − leg, h − leg).
    val flapPath = Path().apply {
        moveTo(w - leg, h)
        lineTo(w, h - leg)
        lineTo(w - leg, h - leg)
        close()
    }
    translate(1.6.dp.toPx(), 2.2.dp.toPx()) { drawPath(flapPath, shadow) }
    drawPath(flapPath, flap)
    drawPath(flapPath, edge, style = Stroke(1.dp.toPx()))
    // A soft crease highlight along the fold.
    drawLine(Color.White, Offset(w - leg, h), Offset(w, h - leg), 1.5.dp.toPx(), cap = StrokeCap.Round)
}

/**
 * strip ↔ tray (§8.5): what appears fades .2 → 1 and grows scaleY .6 → 1 from the top over 240 ms `layout`, from
 * [foldT0]. Instant under reduced motion.
 */
fun Modifier.unfoldMotion(foldT0: Long?, clock: MotionClock, reduced: Boolean = false): Modifier = graphicsLayer {
    val t0 = foldT0 ?: return@graphicsLayer
    if (reduced) return@graphicsLayer
    val (a, sy) = FoldMotion.unfold(clock.now - t0)
    alpha = a
    scaleY = sy
    transformOrigin = TransformOrigin(0.5f, 0f)
}

/**
 * The finished strip's ribbon (§4.10): a vertical sakura ribbon (10 dp: white 1.5 / sakura 7 / white 1.5) [height] tall
 * with a 30 dp bow at its top. It ties down (scaleY 0 → 1 from the top, 3 steps, 300 ms) and the bow pops (300 ms), or
 * unties (un-check). [tied] is the state's; [ap] overrides it while a chain plays. [bowOnly]: the long-name form.
 */
@Composable
fun StripRibbon(tied: Boolean, ap: AislePresentation?, clock: MotionClock, height: Dp, modifier: Modifier = Modifier, bowOnly: Boolean = false) {
    val reduced = LocalReducedMotion.current
    val show = ap?.ribbon ?: tied
    val untieT0 = ap?.untieT0
    if (!show && untieT0 == null) return
    val ribbonT0 = ap?.ribbonT0
    val bowT0 = ap?.bowT0
    Box(modifier.size(30.dp, height).decorative()) {
        if (!bowOnly) {
            Canvas(
                Modifier
                    .size(10.dp, height)
                    .offset(10.dp, 0.dp)
                    .graphicsLayer {
                        val now = clock.now
                        val sy = when {
                            !show && untieT0 != null -> if (reduced) fadeOut(now - untieT0) else RibbonMotion.untie(now - untieT0)
                            ribbonT0 != null && !reduced -> RibbonMotion.tie(now - ribbonT0)
                            else -> 1f
                        }
                        if (reduced) alpha = if (ribbonT0 != null && show) fadeIn(now - ribbonT0) else sy else scaleY = sy
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
            ) {
                drawRect(Color.White)
                drawRect(Families.sakura.mid, Offset(1.5.dp.toPx(), 0f), Size(7.dp.toPx(), size.height))
            }
        }
        DecorSticker(
            "ribbon",
            size = 30.dp,
            modifier = Modifier
                .offset(0.dp, (-12).dp)
                .graphicsLayer {
                    val now = clock.now
                    val s = when {
                        !show && untieT0 != null -> if (reduced) fadeOut(now - untieT0) else RibbonMotion.untie(now - untieT0)
                        bowT0 != null && !reduced -> RibbonMotion.bow(now - bowT0)
                        else -> 1f
                    }
                    if (reduced) alpha = s else {
                        scaleX = s
                        scaleY = s
                    }
                },
        )
    }
}

package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.common.ReceiptPitch
import earth.cora.picnic.ui.common.ReceiptSticker
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.holdOnFrameClock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * Egg 18 (docs/DESIGN_STICKER.md §13.2): the receipt printing out of the LCD's slot, waiting for a tap, and poofing into
 * the book; and the slip the book shows once it is saved (§7.3 "Receipt slip"). Owner: WP9 (ReceiptTest).
 */

/** Where the last printed receipt sat (overlay px), so its poof plays there after the tap. */
internal class ReceiptSpot {
    var rect: Rect? = null
}

internal val LocalReceiptSpot = staticCompositionLocalOf { ReceiptSpot() }

/**
 * The room the list makes for a printed receipt that hangs down into it (large text, a short book; §15): the list's
 * viewport starts [receiptShift] px lower while the receipt waits, so no row can scroll beneath the paper. The overlay
 * sets it; EggListHost eases it ([shown]); the list reads it ([LocalListRoom], EggListScope.listTopExtra). The list and
 * the binder top report where they are as snapshot state ([listTop], [lcd]), so a waiting receipt is placed again
 * whenever either moves.
 */
@androidx.compose.runtime.Stable
class ListRoom {
    var receiptShift: Float by androidx.compose.runtime.mutableFloatStateOf(0f)
        internal set

    /** The inset as it eases towards [receiptShift] (px). */
    internal val shownAnim = androidx.compose.animation.core.Animatable(0f)

    /** The inset the list's viewport has now (px). */
    val shown: Float get() = shownAnim.value

    /** The top of the list's viewport before any inset (window px), as the list last laid it out. */
    var listTop: Float? by androidx.compose.runtime.mutableStateOf(null)

    /** The app bar's LCD (window px), as last laid out: the receipt hangs from it. */
    var lcd: Rect? by androidx.compose.runtime.mutableStateOf(null)
}

/** The list's [ListRoom] (null: the list makes no room; the receipt still keeps off the binder top's words). */
val LocalListRoom = staticCompositionLocalOf<ListRoom?> { null }

/**
 * Where the receipt prints (overlay px): its slot line, the paper's top-left as it feeds out of the slot ([feed]) and
 * where it settles ([settled]), and the room the list makes below it.
 */
internal data class ReceiptPlacement(val slot: Rect, val feed: Offset, val settled: Offset, val listShift: Float)

/**
 * Places the receipt (§13.2, item 259). The slot line is 4 dp tall, 3 dp under the LCD, as wide as the paper and centred
 * under the LCD (inside the screen); the paper ([paperW] × [paperH] px) hangs from it while it feeds, its top edge inside
 * the slot. The binder top's words ([header]: the title, the subtitle, the LCD, the book's date and PER-FECT!) lie over
 * the slot and the paper, which pass under them (ReceiptPrint cuts them out), so no word is ever covered. It settles at
 * (−4, +10) dp turned 3° when that is clear of [header]; otherwise lower, under the lowest word it would meet. If the
 * settled paper then hangs down past the top of the list's viewport ([listTop], before any inset), the list makes room:
 * its viewport starts 4 dp under the paper ([ReceiptPlacement.listShift]), so no row can scroll beneath it.
 */
internal fun placeReceipt(
    lcd: Rect?,
    width: Float,
    paperW: Float,
    paperH: Float,
    density: Float,
    header: List<Rect>,
    listTop: Float?,
): ReceiptPlacement {
    val dp = { v: Float -> v * density }
    val margin = dp(8f)
    val centre = lcd?.center?.x ?: (width - margin - paperW / 2f)
    val left = (centre - paperW / 2f).coerceIn(margin, maxOf(margin, width - margin - paperW))
    val slotTop = (lcd?.bottom ?: dp(96f)) + dp(3f)
    val slot = Rect(left - dp(3f), slotTop, left + paperW + dp(3f), slotTop + dp(4f))
    val feed = Offset(left, slotTop + dp(2f))
    // Everywhere the settled paper is drawn: turned 3° about its centre (one top corner rises by about half its width
    // times sin 3°, the other bottom corner drops as much), with its shadow.
    val above = paperW * 0.03f + dp(2f)
    fun covers(x: Float, y: Float): Rect {
        val padX = paperH * 0.03f
        return Rect(x - padX, y - above, x + paperW + padX + dp(3f), y + paperH + above + dp(1f))
    }
    val x = feed.x - dp(4f)
    var y = feed.y + dp(10f)
    repeat(8) {
        val box = covers(x, y)
        val hit = header.filter { it.inflate(dp(2f)).overlaps(box) }.maxOfOrNull { it.bottom } ?: return@repeat
        // Down until its highest corner is 4 dp under the word.
        y = hit + dp(4f) + above
    }
    val box = covers(x, y)
    // Less than a pixel is no room at all (the paper ends at the list's top).
    val shift = if (listTop != null) (box.bottom + dp(4f) - listTop).takeIf { it >= 1f } ?: 0f else 0f
    return ReceiptPlacement(slot, feed, Offset(x, y), shift)
}

/** The binder top's and the book's words the receipt passes under and settles clear of (the anchors' names after `text:`). */
private val HEADER_WORDS = setOf("title", "subtitle", "lcd", "date", "perfect", "pagelcd")

/**
 * The printing receipt (§13.2): a 4 dp ballpoint slot line under the LCD (the LCD's width); the paper feeds out of it
 * top-down in 14 stepped frames over 1.7 s with a 2-frame dot-matrix jitter; at 1.7 s it tears off (2 frames, 260 ms,
 * "tk"); at 1.96 s it settles at (−4, +10) dp rotated 3°, the slot line goes, and it stays until tapped. It never lies
 * over words ([placeReceipt]). Under reduced motion it fades in printed (120 ms). TalkBack: one button, "Trip receipt:
 * Saturday 3 October, Hillcrest, 24 stickers, 8 baskets, 41 minutes, 1 shiny", double-tap to save it to the sticker book.
 */
@Composable
internal fun ReceiptPrint(fx: ReceiptFx, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine, feedback: Feedback) {
    val reduced = fx.reduced || LocalReducedMotion.current
    val t by rememberEffectTime(fx.id, if (reduced) ReceiptMotion.FADE_MS else ReceiptMotion.SETTLE_AT)
    LaunchedEffect(fx.id) {
        if (reduced) return@LaunchedEffect
        withContext(FixedMotionScale) { holdOnFrameClock(ReceiptMotion.FEED_MS) }
        feedback.cue(Cue.RECEIPT_TEAR)
    }
    val resources = LocalResources.current
    val lines = remember(fx.id) { ReceiptMaker.lines(ReceiptMaker.appName(resources), fx.receipt, fx.listTitle) }
    val spoken = remember(fx.id) { ReceiptMaker.spoken(resources, fx.receipt, fx.listTitle) }
    val saveLabel = stringResource(R.string.egg_receipt_save)
    val d = LocalDensity.current
    val spot = LocalReceiptSpot.current
    val room = LocalListRoom.current
    var paperW by remember(fx.id) { mutableIntStateOf(0) }
    var paperH by remember(fx.id) { mutableIntStateOf(0) }
    if (room != null) androidx.compose.runtime.DisposableEffect(fx.id) { onDispose { room.receiptShift = 0f } }
    val ink = PicnicTheme.colors.ballpoint
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        // Placed again whenever the LCD or the list's top moves (both snapshot state: a waiting receipt is never left
        // behind, nor over the list's words), and on every frame of the print.
        val lcd = room?.lcd ?: anchors.bounds(AnchorRegistry.LCD)
        val listTop = room?.listTop ?: anchors.bounds(EggAnchors.LIST)?.top
        val header = anchors.keys()
            .filter { it.startsWith(EggAnchors.TEXT_PREFIX) && it.removePrefix(EggAnchors.TEXT_PREFIX) in HEADER_WORDS }
            .mapNotNull { anchors.bounds(it)?.let(local) }
        val placed = placeReceipt(
            lcd?.let(local), width, paperW.toFloat(), paperH.toFloat(), d.density,
            header, listTop?.let { local(Rect(0f, it, 0f, it)).top },
        )
        // The list's viewport starts under the paper while it waits (EggListHost eases it there).
        val shift = if (paperW > 0) placed.listShift else 0f
        if (room != null) androidx.compose.runtime.SideEffect { room.receiptShift = shift }
        val (sdx, sdy, rot) = if (reduced) Triple(-4f, 10f, 3f) else ReceiptMotion.settle(t)
        val jitter = if (reduced) 0f else ReceiptMotion.jitterDp(t)
        val fed = if (reduced) 1f else ReceiptMotion.fed(t)
        val alpha = if (reduced) (t.toFloat() / ReceiptMotion.FADE_MS).coerceIn(0f, 1f) else 1f
        // From the slot to where it settles, in step with the settle's own beats (its 10 dp drop: 0, 6, 10).
        val k = (sdy / 10f).coerceIn(0f, 1f)
        val x = placed.feed.x + (placed.settled.x - placed.feed.x) * k + with(d) { jitter.dp.toPx() }
        val y = placed.feed.y + (placed.settled.y - placed.feed.y) * k
        if (paperW > 0) spot.rect = Rect(x, y, x + paperW, y + paperH)
        if (paperW > 0) {
            // Its drawn bounds: the part out of the slot, turned as it is (3° at rest: a few px wider and taller).
            val turn = kotlin.math.abs(rot) / 3f * 0.03f
            Probe(
                "receipt", t,
                Rect(x - paperH * fed * turn, y - paperW * turn, x + paperW + paperH * fed * turn, y + paperH * fed + paperW * turn),
                rot, origin(),
            )
        }
        // The binder top's words lie over the paper and its slot: they are cut out of both (§12.1, item 259).
        val under = remember(header) { Path().apply { header.forEach { addRect(it) } } }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent { clipPath(under, ClipOp.Difference) { this@drawWithContent.drawContent() } },
        ) {
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .testTag(EggTags.RECEIPT)
                    .onSizeChanged { paperW = it.width; paperH = it.height }
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        role = Role.Button
                        onClick(label = saveLabel) {
                            engine.onReceiptTap()
                            true
                        }
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null) { engine.onReceiptTap() }
                    .graphicsLayer {
                        // The sticker draws itself at 3°; while it feeds it hangs straight out of the slot.
                        rotationZ = rot - 3f
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        this.alpha = alpha
                        // Not a pixel before it has been placed (its size is known after the first layout).
                        if (paperW == 0) this.alpha = 0f
                    }
                    .drawWithContent {
                        // The clip reveal: only what has come out of the slot shows.
                        val shown = size.height * fed
                        clipRect(-size.width, 0f, size.width * 2, shown) { this@drawWithContent.drawContent() }
                    },
            ) {
                ReceiptSticker(lines, pitch = ReceiptPitch)
            }
            val slotShows = !reduced && ReceiptMotion.slotVisible(t) && paperW > 0
            if (slotShows) {
                // The slot: a 4 dp ballpoint line under the LCD, as wide as the paper it feeds, over the paper's top edge.
                val slot = placed.slot
                Canvas(
                    Modifier
                        .decorative()
                        .offset { IntOffset(slot.left.roundToInt(), slot.top.roundToInt()) }
                        .width(with(d) { slot.width.toDp() })
                        .height(4.dp),
                ) {
                    drawRoundRect(ink, cornerRadius = CornerRadius(2.dp.toPx()))
                }
                Probe("slot", t, slot, 0f, origin())
            }
        }
    }
}

/** The tapped receipt poofs (1.08 then 0, turning 10°, 4 steps, 400 ms) with a 40 dp kira; reduced motion: it fades. */
@Composable
internal fun ReceiptPoof(id: Long, engine: SurpriseEngine) {
    val reduced = LocalReducedMotion.current
    val t by rememberEffectTime(id, if (reduced) ReceiptMotion.FADE_MS else ReceiptMotion.SLIP_AT + 320) { engine.effectDone(id) }
    val rect = LocalReceiptSpot.current.rect ?: return
    val d = LocalDensity.current
    val c = rect.center
    if (reduced) {
        Box(
            Modifier
                .decorative()
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .graphicsLayer { alpha = 1f - (t.toFloat() / ReceiptMotion.FADE_MS).coerceIn(0f, 1f) },
        ) { PaperGhost(rect.size) }
        return
    }
    val (scale, turn) = ReceiptMotion.poof(t) ?: return
    Box(
        Modifier
            .decorative()
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = turn
            },
    ) { PaperGhost(rect.size) }
    val kira = with(d) { 40.dp.toPx() }
    DecorSticker(
        "kira",
        Modifier
            .offset { IntOffset((c.x - kira / 2).roundToInt(), (c.y - kira / 2).roundToInt()) }
            .graphicsLayer {
                val s = floatArrayOf(0f, 1.4f, 0.9f, 0.5f)[(t * 4 / ReceiptMotion.POOF_MS).toInt().coerceIn(0, 3)]
                scaleX = s
                scaleY = s
            },
        size = 40.dp,
        shadow = false,
    )
}

/** The paper as it poofs (its face without the lines; it is gone in 400 ms). */
@Composable
private fun PaperGhost(size: Size) {
    val d = LocalDensity.current
    val c = PicnicTheme.colors
    Box(
        Modifier
            .size(with(d) { size.width.toDp() }, with(d) { size.height.toDp() })
            .background(c.stickerWhite, earth.cora.picnic.ui.common.ReceiptShape)
            .graphicsLayer { rotationZ = 3f },
    ) {
        Box(Modifier.fillMaxSize().background(c.receiptPaper, earth.cora.picnic.ui.common.ReceiptShape))
    }
}

/**
 * The receipt slip in the sticker book once it is saved (§7.3): a 26 × 32 dp cream slip with three ink lines poking
 * out of the fore-edge behind the page, rotated −7°. [appearing] plays the magic appear (§7.6, 320 ms after its 420 ms
 * wait) with a 26 dp kira. WP5's StickerBook draws it at the fore-edge. Decorative (the book's own node says it).
 */
@Composable
fun BookReceiptSlip(modifier: Modifier = Modifier, appearingId: Long? = null) {
    val reduced = LocalReducedMotion.current
    val c = PicnicTheme.colors
    val t by rememberEffectTime(appearingId ?: "slip", if (appearingId == null) 0 else ReceiptMotion.SLIP_AT + 320)
    val since = if (appearingId == null) Long.MAX_VALUE else t - ReceiptMotion.SLIP_AT
    if (since < 0) return
    val pose = when {
        appearingId == null || since >= 320 -> EggPose(Offset.Zero, -7f)
        reduced -> EggPose(Offset.Zero, -7f, alpha = (since / 120f).coerceIn(0f, 1f))
        else -> EggBeats.magicAppear(since).let { it.copy(rotation = it.rotation - 7f) }
    }
    Box(modifier.size(26.dp, 32.dp).decorative(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(26.dp, 32.dp)
                .graphicsLayer {
                    rotationZ = pose.rotation
                    scaleX = pose.scaleX
                    scaleY = pose.scaleY
                    alpha = pose.alpha
                },
        ) {
            val r = CornerRadius(2.dp.toPx())
            drawRoundRect(c.stickerShadow, Offset(1.6.dp.toPx(), 2.2.dp.toPx()), size, r)
            drawRoundRect(c.stickerWhite, Offset.Zero, size, r)
            val inset = 2.dp.toPx()
            drawRoundRect(c.receiptPaper, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), r)
            val ink = c.ballpoint
            val lineH = 1.6.dp.toPx()
            for ((i, w) in listOf(0.72f, 0.5f, 0.62f).withIndex()) {
                val y = (7 + i * 7).dp.toPx()
                drawRoundRect(ink, Offset(5.dp.toPx(), y), Size((size.width - 10.dp.toPx()) * w, lineH), CornerRadius(lineH / 2))
            }
        }
        if (appearingId != null && !reduced && since < 400) {
            DecorSticker(
                "kira",
                Modifier.graphicsLayer {
                    val s = floatArrayOf(0f, 1.4f, 0.9f, 0.5f, 0f)[(since / 80).toInt().coerceIn(0, 4)]
                    scaleX = s
                    scaleY = s
                },
                size = 26.dp,
                shadow = false,
            )
        }
    }
}

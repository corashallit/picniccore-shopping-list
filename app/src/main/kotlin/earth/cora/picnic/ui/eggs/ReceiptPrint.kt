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
 * The printing receipt (§13.2): a 4 dp ballpoint slot line under the LCD; the paper feeds out of it top-down in 14 stepped
 * frames over 1.7 s with a 2-frame dot-matrix jitter; at 1.7 s it tears off (2 frames, 260 ms, "tk"); at 1.96 s it
 * settles at (−4, +10) dp rotated 3°, the slot line goes, and it stays until tapped. Under reduced motion it fades in
 * printed (120 ms). TalkBack: one button, "Trip receipt: Saturday 3 October, Hillcrest, 24 stickers, 8 baskets, 41
 * minutes, 1 shiny", double-tap to save it to the sticker book.
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
    var paperW by remember(fx.id) { mutableIntStateOf(0) }
    var paperH by remember(fx.id) { mutableIntStateOf(0) }
    val ink = PicnicTheme.colors.ballpoint
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val lcd = anchors.bounds(AnchorRegistry.LCD)?.let(local)
        val margin = with(d) { 8.dp.toPx() }
        // Under the LCD, its right edge with the LCD's (inside the screen); without an LCD, the top-right corner.
        val right = ((lcd?.right ?: (width - margin)) + with(d) { 4.dp.toPx() }).coerceAtMost(width - margin)
        val slotY = (lcd?.bottom ?: with(d) { 96.dp.toPx() }) + with(d) { 3.dp.toPx() }
        val left = (right - paperW).coerceAtLeast(margin)
        val top = slotY + with(d) { 4.dp.toPx() }
        val (sdx, sdy, rot) = if (reduced) Triple(-4f, 10f, 3f) else ReceiptMotion.settle(t)
        val jitter = if (reduced) 0f else ReceiptMotion.jitterDp(t)
        val fed = if (reduced) 1f else ReceiptMotion.fed(t)
        val alpha = if (reduced) (t.toFloat() / ReceiptMotion.FADE_MS).coerceIn(0f, 1f) else 1f
        val x = left + with(d) { (sdx + jitter).dp.toPx() }
        val y = top + with(d) { sdy.dp.toPx() }
        if (paperW > 0) spot.rect = Rect(x, y, x + paperW, y + paperH)
        if (paperW > 0) Probe("receipt", t, Rect(x, y, x + paperW, y + paperH * fed), rot, origin())
        if (!reduced && ReceiptMotion.slotVisible(t) && paperW > 0) {
            // The slot: a 4 dp ballpoint line the paper comes out of.
            Canvas(
                Modifier
                    .decorative()
                    .offset { IntOffset((left - with(d) { 3.dp.toPx() }).roundToInt(), slotY.roundToInt()) }
                    .width(with(d) { (paperW + 6.dp.toPx()).toDp() })
                    .height(4.dp),
            ) {
                drawRoundRect(ink, cornerRadius = CornerRadius(2.dp.toPx()))
            }
        }
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
                }
                .drawWithContent {
                    // The clip reveal: only what has come out of the slot shows.
                    val shown = size.height * fed
                    clipRect(-size.width, 0f, size.width * 2, shown) { this@drawWithContent.drawContent() }
                },
        ) {
            ReceiptSticker(lines, pitch = ReceiptPitch)
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

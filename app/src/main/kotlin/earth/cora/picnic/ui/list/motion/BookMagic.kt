package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.Holo
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.drawGlint
import earth.cora.picnic.ui.theme.drawHanko
import kotlin.math.roundToInt
import kotlin.math.tan

/*
 * The book's magic (docs/DESIGN_STICKER.md §7.6, R5): twins only ever appear and disappear IN PLACE, even when the list
 * is scrolled far below the book; nothing flies to it. WP5's StickerBook draws its pages and spots and places these
 * pieces in them. Owner: WP6.
 *
 * - [BookTwin]: one twin in its spot, at rest (its ROTS tilt), appearing (a 30 dp kira bloom, 6 sparks flying 13 dp
 *   out, the twin in 4 stepped poses, 320 ms) or peeling off (corner lift, 1.12, .55 with sparks running inward, gone
 *   at 320 ms). Reduced motion: a 120 ms fade in or out at the same beat, no bloom, no sparks.
 * - [Modifier.bookPageLift] (the right page lifts 2 dp with each appear), [Modifier.bookPageTurn] (3 frames, 240 ms,
 *   about the spine), [drawBookGlint] (the full book's one holo glint), [SpinePerfect] (PER-FECT! across the spine).
 */

/** A twin in its book spot ([size] 26 dp, 22 collapsed), drawn by [phase] on [clock]. Decorative. */
@Composable
fun BookTwin(
    icon: IconRef,
    slot: Int,
    phase: SpotPhase?,
    clock: MotionClock,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    holo: Boolean = false,
    pixel: Boolean = false,
    aisleIndex: Int = -1,
) {
    val reduced = LocalReducedMotion.current
    Box(modifier.size(size).decorative(), contentAlignment = Alignment.Center) {
        when (phase) {
            is SpotPhase.Appearing -> {
                if (!reduced) {
                    // The kira bloom and the six sparks, behind the twin.
                    DecorSticker(
                        "kira",
                        size = size * (30f / 26f),
                        shadow = false,
                        modifier = Modifier.graphicsLayer {
                            val pose = BookMotion.bloom(clock.now - phase.t0)
                            if (pose == null) alpha = 0f else {
                                scaleX = pose.scaleX
                                scaleY = pose.scaleY
                                rotationZ = pose.rotation
                            }
                        },
                    )
                    for (i in BookMotion.sparkKinds.indices) Spark(i, phase.t0, clock, size, inward = false)
                }
                ItemIcon(
                    icon, null, size = size, aisleIndex = aisleIndex, holo = holo, pixel = pixel,
                    modifier = Modifier
                        .offset {
                            val pose = if (reduced) MotionPose.Rest else BookMotion.twin(clock.now - phase.t0, slot)
                            IntOffset((pose.dx * density).roundToInt(), (pose.dy * density).roundToInt())
                        }
                        .graphicsLayer {
                            if (reduced) {
                                alpha = fadeIn(clock.now - phase.t0)
                                rotationZ = BookMotion.tilt(slot)
                            } else {
                                val pose = BookMotion.twin(clock.now - phase.t0, slot)
                                alpha = pose.alpha
                                scaleX = pose.scaleX
                                scaleY = pose.scaleY
                                rotationZ = pose.rotation
                            }
                        },
                )
            }
            is SpotPhase.Peeling -> {
                ItemIcon(
                    icon, null, size = size, aisleIndex = aisleIndex, holo = holo, pixel = pixel,
                    modifier = Modifier
                        .offset {
                            val pose = if (reduced) MotionPose.Rest else BookMotion.peel(clock.now - phase.t0, slot)
                            IntOffset((pose.dx * density).roundToInt(), (pose.dy * density).roundToInt())
                        }
                        .graphicsLayer {
                            if (reduced) {
                                alpha = fadeOut(clock.now - phase.t0)
                                rotationZ = BookMotion.tilt(slot)
                            } else {
                                val pose = BookMotion.peel(clock.now - phase.t0, slot)
                                alpha = pose.alpha
                                scaleX = pose.scaleX
                                scaleY = pose.scaleY
                                rotationZ = pose.rotation
                                transformOrigin = TransformOrigin(0.2f, 0.9f)
                            }
                        }
                        .skewed(0.2f, 0.9f) { if (reduced) 0f else BookMotion.peel(clock.now - phase.t0, slot).skewX },
                )
                if (!reduced) {
                    for (i in BookMotion.sparkKinds.indices) Spark(i, phase.t0 + BookMotion.PEEL_SPARKS_AT, clock, size, inward = true)
                }
            }
            SpotPhase.Empty, SpotPhase.Next -> Unit
            SpotPhase.Shown, null -> ItemIcon(
                icon, null, size = size, aisleIndex = aisleIndex, holo = holo, pixel = pixel,
                modifier = Modifier.graphicsLayer { rotationZ = BookMotion.tilt(slot) },
            )
        }
    }
}

@Composable
private fun Spark(index: Int, t0: Long, clock: MotionClock, spot: Dp, inward: Boolean) {
    val kind = BookMotion.sparkKinds[index]
    val s = spot * (9f / 26f)
    DecorSticker(
        kind,
        size = s,
        shadow = false,
        modifier = Modifier
            .offset {
                val pose = BookMotion.spark(index, clock.now - t0, inward)
                IntOffset(((pose?.dx ?: 0f) * density).roundToInt(), ((pose?.dy ?: 0f) * density).roundToInt())
            }
            .graphicsLayer {
                val pose = BookMotion.spark(index, clock.now - t0, inward)
                if (pose == null) alpha = 0f else {
                    alpha = pose.alpha
                    scaleX = pose.scaleX
                    scaleY = pose.scaleY
                }
            },
    )
}

/** Skews the content by [skewDeg]() degrees about ([px], [py]) of its size (the peel's corner lift). */
fun Modifier.skewed(px: Float, py: Float, skewDeg: () -> Float): Modifier = drawWithContent {
    val deg = skewDeg()
    if (deg == 0f) {
        drawContent()
    } else {
        drawSkewed(deg, px, py)
    }
}

private fun ContentDrawScope.drawSkewed(deg: Float, px: Float, py: Float) {
    val pivotX = size.width * px
    val pivotY = size.height * py
    val m = Matrix()
    m.translate(pivotX, pivotY)
    val skew = Matrix().apply { values[Matrix.SkewX] = tan(Math.toRadians(deg.toDouble())).toFloat() }
    m.timesAssign(skew)
    m.translate(-pivotX, -pivotY)
    drawContext.transform.transform(m)
    drawContent()
}

/** The right page lifts 2 dp with each magic appear (2 steps, 160 ms). */
fun Modifier.bookPageLift(fx: BookFx, clock: MotionClock): Modifier = offset {
    val t0 = fx.liftT0 ?: return@offset IntOffset.Zero
    IntOffset(0, (BookMotion.lift(clock.now - t0) * density).roundToInt())
}

/**
 * The page turn about the spine (the right page's left edge): rotateY −70° on the old page, then +70° and 0° on the new
 * one (3 frames, 240 ms). [showsNewPage] tells the book which page to draw. Off under reduced motion (the book shows
 * the page at once).
 */
fun Modifier.bookPageTurn(fx: BookFx, clock: MotionClock): Modifier = graphicsLayer {
    val turn = fx.pageTurn ?: return@graphicsLayer
    val (rotY, _) = BookMotion.pageTurn(clock.now - turn.t0)
    rotationY = rotY
    cameraDistance = 12f * density
    transformOrigin = TransformOrigin(0f, 0.5f)
}

/** True while a page turn shows the page it turns to (from its second frame). */
fun BookFx.showsNewPage(now: Long): Boolean = pageTurn?.let { BookMotion.pageTurn(now - it.t0).second } ?: true

/** The full book's one holo glint across the release paper (12 steps, 1.2 s), 360 ms after its last twin. */
fun DrawScope.drawBookGlint(fx: BookFx, now: Long) {
    val t0 = fx.fullGlintT0 ?: return
    drawGlint(Holo.glint.progressAt(now - t0))
}

/**
 * PER-FECT! slammed across the spine after the finale (2.2 → 1.25 → 1, −30° → −12°, 3 steps, 260 ms), [size] 40 dp,
 * with an 8-sticker burst; at rest when [fx] has no press in progress. Reduced motion: a 120 ms fade. Decorative.
 */
@Composable
fun SpinePerfect(fx: BookFx, clock: MotionClock, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val reduced = LocalReducedMotion.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val style = PicnicTheme.type.hanko
    val color = PicnicTheme.colors.hanko
    val t0 = fx.perfectT0
    Box(modifier.size(size).decorative(), contentAlignment = Alignment.Center) {
        if (t0 != null && !reduced) {
            val particles = HankoBurst.ring(0x5eed)
            for (p in particles) {
                DecorSticker(
                    p.kind,
                    size = p.sizeDp.dp,
                    modifier = Modifier
                        .offset {
                            val pose = HankoBurst.at(p, clock.now - t0 - 260)
                            IntOffset(((pose?.dx ?: 0f) * density).roundToInt(), ((pose?.dy ?: 0f) * density).roundToInt())
                        }
                        .graphicsLayer {
                            val pose = HankoBurst.at(p, clock.now - t0 - 260)
                            alpha = pose?.alpha ?: 0f
                        },
                )
            }
        }
        Canvas(
            Modifier
                .size(size)
                .graphicsLayer {
                    if (t0 == null) {
                        rotationZ = -12f
                    } else if (reduced) {
                        alpha = fadeIn(clock.now - t0)
                        rotationZ = -12f
                    } else {
                        val pose = BookMotion.perfect(clock.now - t0)
                        alpha = pose.alpha
                        scaleX = pose.scaleX
                        scaleY = pose.scaleY
                        rotationZ = pose.rotation
                    }
                },
        ) { drawHanko(HankoText.PerFect, color, measurer, style) }
    }
}

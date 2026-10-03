package earth.cora.picnic.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The book's magic appear (docs/DESIGN_STICKER.md §7.6, R5), for WP8's keepsake surfaces: a new pantry sticker on its
 * shelf (§6.13), a newly collected album pocket on first view (§6.10, item 231), a toggle's sticker pressed on (§6.3).
 * Nothing flies in: the sticker appears IN PLACE, in four stepped poses, with a kira star blooming behind it. Under
 * reduced motion it fades in over 120 ms at the same moment.
 */

/** One stepped pose of the magic appear: scale, rotation (degrees) and the pressed offset (dp). */
@Immutable
data class AppearPose(val scale: Float, val rotation: Float, val dx: Float = 0f, val dy: Float = 0f)

object MagicAppear {
    /** §7.6: .2 at −16° (0 ms) → 1.3 at −8° (+64) → .86 at +5° (+144) → pressed .95 (+0.8, +1.2) dp (+224) → 1.0 (+320). */
    val poses: List<Pair<Long, AppearPose>> = listOf(
        0L to AppearPose(0.2f, -16f),
        64L to AppearPose(1.3f, -8f),
        144L to AppearPose(0.86f, 5f),
        224L to AppearPose(0.95f, 0f, 0.8f, 1.2f),
        320L to AppearPose(1f, 0f),
    )

    /** The kira bloom: 0 → 1.4 → .9 → .5 → 0, turning −30° → +30°, 4 steps over 400 ms. */
    val bloom: List<Pair<Long, AppearPose>> = listOf(
        0L to AppearPose(0f, -30f),
        100L to AppearPose(1.4f, -10f),
        200L to AppearPose(0.9f, 10f),
        300L to AppearPose(0.5f, 30f),
        400L to AppearPose(0f, 30f),
    )

    const val DURATION_MILLIS: Long = 400
}

/** Test hook: the pose a magic-appearing sticker shows now (AlbumTest, PantryScreenTest). */
val AppearScale: SemanticsPropertyKey<Float> = SemanticsPropertyKey("AppearScale")
private var SemanticsPropertyReceiver.appearScale: Float by AppearScale

/**
 * Shows [content] and, when [play] is true for this [key], plays the magic appear once ([onPlayed] then fires, e.g. to
 * mark an album pocket seen). [tilt] is the sticker's resting angle.
 */
@Composable
fun MagicAppearBox(
    key: Any,
    play: Boolean,
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    bloomSize: Float = 30f,
    onPlayed: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    var pose by remember(key) { mutableStateOf(if (play && !reduced) MagicAppear.poses.first().second else AppearPose(1f, 0f)) }
    var bloom by remember(key) { mutableStateOf<AppearPose?>(null) }
    val fade = remember(key) { Animatable(if (play && reduced) 0f else 1f) }
    LaunchedEffect(key, play) {
        if (!play) return@LaunchedEffect
        withContext(FixedMotionScale) {
            if (reduced) {
                fade.animateTo(1f, tween(PicnicMotion.rmFade.durationMillis))
            } else {
                var elapsed = 0L
                val steps = (MagicAppear.poses.map { it.first } + MagicAppear.bloom.map { it.first }).distinct().sorted()
                for (t in steps) {
                    delay(t - elapsed)
                    elapsed = t
                    MagicAppear.poses.lastOrNull { it.first <= t }?.let { pose = it.second }
                    bloom = MagicAppear.bloom.lastOrNull { it.first <= t }?.second?.takeIf { t < MagicAppear.DURATION_MILLIS }
                }
                pose = AppearPose(1f, 0f)
                bloom = null
            }
        }
        onPlayed()
    }
    Box(modifier.semantics { appearScale = pose.scale }, contentAlignment = Alignment.Center) {
        bloom?.let { b ->
            DecorSticker(
                "kira",
                Modifier
                    .size(bloomSize.dp)
                    .graphicsLayer { scaleX = b.scale; scaleY = b.scale; rotationZ = b.rotation },
                size = bloomSize.dp,
                shadow = false,
            )
        }
        Box(
            Modifier.graphicsLayer {
                scaleX = pose.scale
                scaleY = pose.scale
                rotationZ = tilt + pose.rotation
                translationX = pose.dx.dp.toPx()
                translationY = pose.dy.dp.toPx()
                alpha = fade.value
            },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

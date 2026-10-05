package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.ItemIcon
import kotlin.math.roundToInt

/*
 * The overlay layer (docs/DESIGN_STICKER.md §8.7): above the list and the add bar, below sheets. It draws the
 * hopping sticker copies and the hanko's bursts; it never intercepts a touch (the Next card, toasts and the egg
 * overlays it hosts are mounted by MotionOverlay). Every flight is a row's sticker and its own basket (R5); nothing
 * here ever travels to the book, a stamp, the LCD, the album or the receipt. Owner: WP6.
 */

/** Draws [engine]'s flights and bursts. Fill the screen with it, above the list. */
@Composable
fun FlightLayer(engine: MotionChoreographer, presentation: ListPresentation, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .decorative()
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        for (f in presentation.flights) {
            key(f.itemId, f.t0) { FlightSticker(engine, f) { origin } }
        }
        for (b in presentation.bursts) {
            key(b.id) { HankoBurstView(b, engine.clock) { origin } }
        }
    }
}

/** One sticker copy on its arc: its row size, posed every frame from the engine's clock (no recomposition). */
@Composable
private fun FlightSticker(engine: MotionChoreographer, f: Flight, origin: () -> Offset) {
    val density = LocalDensity.current
    val sizePx = f.from.width.coerceAtLeast(1f)
    val size = with(density) { sizePx.toDp() }
    ItemIcon(
        icon = f.icon ?: IconRef(CAPSULE),
        contentDescription = null,
        size = size,
        aisleIndex = f.aisleIndex,
        holo = f.holo,
        modifier = Modifier
            .offset {
                val frame = engine.flightFrame(f, engine.clock.now)
                val o = origin()
                IntOffset((frame.center.x - o.x - sizePx / 2f).roundToInt(), (frame.center.y - o.y - sizePx / 2f).roundToInt())
            }
            .graphicsLayer {
                val frame = engine.flightFrame(f, engine.clock.now)
                scaleX = frame.scaleX
                scaleY = frame.scaleY
                rotationZ = frame.rotation
                transformOrigin = TransformOrigin.Center
            },
    )
}

/** The hanko's 8 stickers bursting from the checkbox (5 steps, 500 ms). */
@Composable
private fun HankoBurstView(b: Burst, clock: MotionClock, origin: () -> Offset) {
    val particles = remember(b.seed) { HankoBurst.particles(b.seed) }
    val density = LocalDensity.current
    for (p in particles) {
        val sizePx = with(density) { p.sizeDp.dp.toPx() }
        DecorSticker(
            p.kind,
            size = p.sizeDp.dp,
            modifier = Modifier
                .offset {
                    val pose = HankoBurst.at(p, clock.now - b.t0)
                    val o = origin()
                    val dx = (pose?.dx ?: 0f) * density.density
                    val dy = (pose?.dy ?: 0f) * density.density
                    IntOffset((b.center.x - o.x + dx - sizePx / 2f).roundToInt(), (b.center.y - o.y + dy - sizePx / 2f).roundToInt())
                }
                .graphicsLayer {
                    val pose = HankoBurst.at(p, clock.now - b.t0)
                    if (pose == null) {
                        alpha = 0f
                    } else {
                        alpha = pose.alpha
                        scaleX = pose.scaleX
                        scaleY = pose.scaleY
                        rotationZ = pose.rotation
                    }
                },
        )
    }
}

/** The capsule sentinel key (D5): what a flight draws when its event carried no sticker. */
internal const val CAPSULE: String = "_capsule"

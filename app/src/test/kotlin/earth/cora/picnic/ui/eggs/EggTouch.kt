package earth.cora.picnic.ui.eggs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import earth.cora.picnic.ui.list.motion.AnchorRegistry

/*
 * Real touches on the practice list for the egg tests: taps and holds at an anchor's centre (window px = root px here),
 * on the test clock.
 */

/** The window bounds of anchor [key] (it must be laid out). */
fun EggRig.anchor(key: String): Rect = anchors.bounds(key) ?: error("anchor $key is not laid out")

/** A tap at [at] (root px). */
fun EggRig.tapAt(at: Offset) {
    compose.onRoot().performTouchInput { click(at) }
    frame()
}

/** A press at [at] held for [ms] on the test clock, then released. */
fun EggRig.holdAt(at: Offset, ms: Long) {
    compose.onRoot().performTouchInput { down(at) }
    advance(ms)
    compose.onRoot().performTouchInput { up() }
    frame()
}

/** Taps the sticker by the title once (the sun, or the moon in Dusk and Glow). */
fun EggRig.tapSun() = tapAt(anchor(AnchorRegistry.SUN).center)

/** Taps the stamp of section [sectionId]. */
fun EggRig.tapStamp(sectionId: String) = tapAt(anchor(AnchorRegistry.stampKey(sectionId)).center)

/** Holds the LCD for [ms]. */
fun EggRig.holdLcd(ms: Long) = holdAt(anchor(AnchorRegistry.LCD).center, ms)

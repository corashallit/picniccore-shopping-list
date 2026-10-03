package earth.cora.picnic.ui.list.motion

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.platform.FakeAnnouncer
import earth.cora.picnic.platform.FakeFeedback
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.ui.theme.LocalTimeouts
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.ToastHostState
import java.io.File

/*
 * WP6 test support: a harness on the Compose test clock (autoAdvance off) with fake feedback, announcer and timeouts,
 * the StillnessProbe wired in, and frame-strip capture (docs/DESIGN_STICKER.md §20 "strip": the same composition
 * captured at fixed times on the test clock, one PNG per frame, `<name>_<variant>_<t>.png`, t in ms).
 */

/** Where goldens go (Roborazzi's outputDir). */
const val SCREENSHOTS: String = "src/test/screenshots"

class HarnessRig(
    val compose: ComposeContentTestRule,
    initial: AppState,
    val variant: ThemeVariant = ThemeVariant.Day,
    val reduced: Boolean = false,
    val timeouts: FakeTimeouts = FakeTimeouts(),
    val fontScale: Float? = null,
    val nextStopCard: Boolean = true,
    val celebration: Boolean = true,
    val talkBack: Boolean = false,
) {
    val feedback = FakeFeedback()
    val announcer = FakeAnnouncer()
    val engine = MotionChoreographer(feedback, announcer, timeouts)
    val model = HarnessModel(initial, engine)
    val probe = StillnessProbe()
    val toasts = ToastHostState()

    fun start(content: @Composable () -> Unit = { MotionHarness(model, toasts, nextStopCard = nextStopCard, celebration = celebration, talkBack = talkBack) }): HarnessRig {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val base = LocalDensity.current
            val d = Density(base.density, fontScale ?: base.fontScale)
            CompositionLocalProvider(LocalDensity provides d, LocalTimeouts provides timeouts, LocalMotionProbe provides probe) {
                PicnicTheme(variant = variant, reducedMotion = reduced) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        frame()
        return this
    }

    /** The test clock's time (= the frame time the engine ticks at). */
    val now: Long get() = compose.mainClock.currentTime

    /** One frame. */
    fun frame() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    fun advance(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    /** Advances to [time] (test-clock ms; frames are 16 ms, so the clock lands on the first frame at or after it). */
    fun advanceTo(time: Long) {
        val d = time - now
        if (d > 0) advance(d)
    }

    /** Toggles [itemId] and runs one frame: returns the chain's t0 (the frame it started on). */
    fun tap(itemId: String): Long {
        compose.runOnIdle { model.toggle(itemId) }
        frame()
        return engine.time
    }

    /** The presentation now. */
    val p: ListPresentation get() = engine.presentation.value

    /** Root bounds of a tagged node (dp). */
    fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    /** Captures the root (or [crop], root px) as golden [name]. */
    fun capture(name: String, crop: Rect? = null) {
        compose.waitForIdle()
        val full = compose.onRoot().captureToImage().asAndroidBitmap()
        val bmp = if (crop == null) full else {
            val l = crop.left.toInt().coerceIn(0, full.width - 1)
            val t = crop.top.toInt().coerceIn(0, full.height - 1)
            val w = crop.width.toInt().coerceAtMost(full.width - l).coerceAtLeast(1)
            val h = crop.height.toInt().coerceAtMost(full.height - t).coerceAtLeast(1)
            Bitmap.createBitmap(full, l, t, w, h)
        }
        bmp.captureRoboImage("$SCREENSHOTS/$name.png")
    }

    /** Writes the root (or [crop]) to [file] for a look outside the goldens (not a golden). */
    fun peek(file: File, crop: Rect? = null) {
        compose.waitForIdle()
        val full = compose.onRoot().captureToImage().asAndroidBitmap()
        val bmp = if (crop == null) full else Bitmap.createBitmap(full, crop.left.toInt(), crop.top.toInt(), crop.width.toInt(), crop.height.toInt())
        file.parentFile?.mkdirs()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A frame strip: [name]_<t>.png at each of [times] ms after [t0] (each frame captured on the first frame at or after it). */
    fun strip(name: String, t0: Long, times: List<Long>, crop: (() -> Rect?)? = null) {
        for (t in times) {
            advanceTo(t0 + t)
            capture("${name}_$t", crop?.invoke())
        }
    }
}

/** The variant's golden suffix. */
val ThemeVariant.suffix: String get() = name.lowercase()

/** Remember a value across recompositions in tests. */
@Composable
fun <T> rememberOnce(block: () -> T): T = remember { block() }

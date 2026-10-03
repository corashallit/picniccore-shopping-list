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
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.platform.Announcer
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.platform.Feedback
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
    album: Album = Album(),
) {
    private var engineRef: MotionChoreographer? = null
    val feedback: TimedFeedback = TimedFeedback { engineRef?.time ?: 0L }
    val announcer: TimedAnnouncer = TimedAnnouncer { engineRef?.time ?: 0L }
    val engine: MotionChoreographer = MotionChoreographer(feedback, announcer, timeouts).also { engineRef = it }
    val model = HarnessModel(initial, engine, album = album)
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

    /**
     * Advances one frame at a time up to [until] (test-clock ms), recording the presentation at every frame, the
     * current one first; [each] runs after every frame (and once now).
     */
    fun sampleTo(until: Long, each: ((Long) -> Unit)? = null): List<Pair<Long, ListPresentation>> {
        val out = ArrayList<Pair<Long, ListPresentation>>()
        out += now to p
        each?.invoke(now)
        while (now < until) {
            frame()
            out += now to p
            each?.invoke(now)
        }
        return out
    }

    /** A frame strip: [name]_<t>.png at each of [times] ms after [t0] (each frame captured on the first frame at or after it). */
    fun strip(name: String, t0: Long, times: List<Long>, crop: (() -> Rect?)? = null) {
        for (t in times) {
            advanceTo(t0 + t)
            capture("${name}_$t", crop?.invoke())
        }
    }
}

/** Cues with the engine time they were asked for at (the beat's frame). */
class TimedFeedback(private val now: () -> Long) : Feedback {
    val log: MutableList<Pair<Long, Cue>> = ArrayList()

    override fun cue(cue: Cue) {
        log += now() to cue
    }

    fun times(cue: Cue): List<Long> = log.filter { it.second == cue }.map { it.first }

    fun count(cue: Cue): Int = log.count { it.second == cue }

    val cues: List<Cue> get() = log.map { it.second }
}

/** Announcements with the engine time they were made at. */
class TimedAnnouncer(private val now: () -> Long) : Announcer {
    val log: MutableList<Pair<Long, String>> = ArrayList()

    override fun polite(text: String) {
        log += now() to text
    }

    val messages: List<String> get() = log.map { it.second }
}

/** Asserts [actual] is [expected] to within one frame after it (beats run on the first frame at or after their time). */
fun assertBeat(what: String, expected: Long, actual: Long?) {
    org.junit.Assert.assertNotNull("$what never happened", actual)
    val d = actual!! - expected
    org.junit.Assert.assertTrue("$what at +$d ms from its beat (expected within one frame)", d in 0..16)
}

/** The variant's golden suffix. */
val ThemeVariant.suffix: String get() = name.lowercase()

/** Remember a value across recompositions in tests. */
@Composable
fun <T> rememberOnce(block: () -> T): T = remember { block() }

/** The first sampled time at which [pred] holds (at or after [from]), or null. */
fun List<Pair<Long, ListPresentation>>.firstTime(from: Long = Long.MIN_VALUE, pred: (ListPresentation) -> Boolean): Long? =
    firstOrNull { it.first >= from && pred(it.second) }?.first

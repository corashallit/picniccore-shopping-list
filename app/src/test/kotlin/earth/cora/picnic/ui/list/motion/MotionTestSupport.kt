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
import androidx.compose.ui.test.onAllNodesWithTag
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
import earth.cora.picnic.ui.theme.TOAST_TAG
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

    /** TalkBack's state for the harness (a test may turn it on between scenes). */
    val talkBackState = androidx.compose.runtime.mutableStateOf(talkBack)

    fun start(content: @Composable () -> Unit = { MotionHarness(model, toasts, nextStopCard = nextStopCard, celebration = celebration, talkBack = talkBackState.value) }): HarnessRig {
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
        sampleReal()
    }

    /** Advances [ms] one frame at a time, sampling where the card and the toast are really drawn on every frame. */
    fun advance(ms: Long) {
        compose.waitForIdle()
        val until = now + ms
        while (now < until) {
            if (p.nextCard == null && toasts.current == null) {
                // Nothing to sample: let the clock run (it still steps frame by frame).
                compose.mainClock.advanceTimeBy(until - now)
                compose.waitForIdle()
                break
            }
            frame()
        }
    }

    /**
     * Where the Next card's face and the toast's note are really drawn now (their semantics nodes' bounds, which pass
     * through the motion layers), reported to the probe as their real geometry and as overlays to each other
     * (StillnessProbe): the stillness of each is measured on what the screen shows, not on its own report.
     */
    private fun sampleReal() {
        if (p.nextCard == null && toasts.current == null) return
        val t = now
        val d = compose.density.density
        compose.onAllNodesWithTag(NEXT_CARD_TAG, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { n ->
            val face = n.boundsInRoot.deflate(8f * d)
            probe.real(NEXT_CARD_TAG, t, face)
            probe.overlay(NEXT_CARD_TAG, t, face)
        }
        toastNote()?.let { note ->
            probe.text(TOAST_TAG, t, note)
            probe.real(TOAST_TAG, t, note)
            probe.overlay(TOAST_TAG, t, note)
        }
    }

    /**
     * The toast's note as drawn (root px): its live-region body, which sits inside the note's motion layer, grown by the
     * note's padding, border and the washi strip above it (WP4's StickyNote: 17 / 29 / 13 dp, and 11 dp below with an
     * action chip, 19 without).
     */
    fun toastNote(): Rect? {
        val d = compose.density.density
        val below = if (toasts.current?.actionLabel != null) 11f else 19f
        val body = compose.onAllNodes(
            androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(TOAST_TAG)) and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.LiveRegion),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: return null
        if (body.width <= 0f || body.height <= 0f) return null
        val note = Rect(body.left - 17f * d, body.top - 29f * d, body.right + 13f * d, body.bottom + below * d)
        // Under a stacked card the toast's slot clips it: what is drawn is the part inside the slot.
        val slot = compose.onAllNodesWithTag(TOAST_SLOT_TAG, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: return note
        return if (note.overlaps(slot)) note.intersect(slot) else null
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
    val p: ListPresentation get() = engine.current.value

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

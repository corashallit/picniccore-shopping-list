package earth.cora.picnic.ui.eggs

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.getOrNull
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.TimedAnnouncer
import earth.cora.picnic.ui.list.motion.TimedFeedback
import earth.cora.picnic.ui.theme.LocalTimeouts
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant

/*
 * WP9 test support: the Surprise lab's sandbox (the Appendix A fixture in memory, its own SurpriseEngine with a seeded
 * Random and its own clock) rendered as the practice list, on the Compose test clock (autoAdvance off). The sandbox's
 * clock runs with the test clock (as it runs with real time on the phone), so the eggs' wall-clock rules (1.5 s between
 * sun taps, the 6 s bonus limit, the 5.2 s petal cooldown) and the overlays' frame-clock beats run on one time line.
 */

/** Where goldens go (Roborazzi's outputDir). */
const val EGG_SHOTS: String = "src/test/screenshots"

class EggRig(
    val compose: ComposeContentTestRule,
    /** Day or Glow: the phone's dark mode (Glow); Dusk, Golden and midnight come from the sandbox's own rules. */
    val variant: ThemeVariant = ThemeVariant.Day,
    val reduced: Boolean = false,
    val fontScale: Float? = null,
    fixture: AppState = PracticeFixture.state(),
    val timeouts: FakeTimeouts = FakeTimeouts(),
    seed: Int = 7,
    startAt: Long = PracticeFixture.NOW,
    val talkBack: Boolean = false,
) {
    val feedback: TimedFeedback = TimedFeedback { compose.mainClock.currentTime }
    val announcer: TimedAnnouncer = TimedAnnouncer { compose.mainClock.currentTime }
    /** The sandbox's clock runs with the test clock (as it runs with real time on the phone). */
    val clock = SandboxClock(startAt) { compose.mainClock.currentTime }
    val sandbox = SurpriseLabSandbox(
        fixture = fixture,
        clock = clock,
        feedback = feedback,
        announcer = announcer,
        timeouts = timeouts,
        seed = seed,
        // The tests move the clock to the minute themselves (tickNow), so no tick waits on a real delay.
        minuteTicks = false,
    )
    val engine: AppSurpriseEngine get() = sandbox.engine
    val model: PracticeModel get() = sandbox.model
    val probe = EggProbe()

    fun start(content: @Composable () -> Unit = { PracticeList(model, feedback = feedback, talkBack = talkBack) }): EggRig {
        compose.mainClock.autoAdvance = false
        sandbox.variants.onSystemDark(variant == ThemeVariant.Glow)
        compose.setContent {
            val base = LocalDensity.current
            val d = Density(base.density, fontScale ?: base.fontScale)
            val v by sandbox.variants.variant.collectAsState()
            CompositionLocalProvider(LocalDensity provides d, LocalTimeouts provides timeouts, LocalEggProbe provides probe) {
                PicnicTheme(variant = v, reducedMotion = reduced) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        SandboxFollowUps(sandbox)
                    }
                }
            }
        }
        frame()
        return this
    }

    /** The test clock's time. */
    val now: Long get() = compose.mainClock.currentTime

    /** One frame (the sandbox clock follows the test clock by itself). */
    fun frame() {
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
    }

    /** Advances [ms] one frame at a time (bounded: the loop ends at the target time). */
    fun advance(ms: Long) {
        val until = now + ms
        var guard = 0
        while (now < until && guard++ < 10_000) frame()
    }

    /** Advances to test-clock time [t]. */
    fun advanceTo(t: Long) {
        if (t > now) advance(t - now)
    }

    /** Runs [block] on the main thread, then a frame. */
    fun act(block: () -> Unit) {
        compose.runOnIdle(block)
        frame()
    }

    /** Fires lab egg [n] (§12.3's action) and returns the frame time it started on. */
    fun fire(n: Int): Long {
        act { sandbox.fire(n) }
        return now
    }

    val eggs: SurpriseState get() = engine.state.value

    val state: AppState get() = sandbox.store.value

    val list get() = state.list(model.listId)!!

    val anchors: AnchorRegistry get() = model.anchors

    /** Captures the root (or [crop], root px) as golden [name]. */
    fun capture(name: String, crop: Rect? = null) {
        frame()
        grab(crop).captureRoboImage("$EGG_SHOTS/$name.png")
    }

    /** The root as it is now (or [crop], root px), without a frame first: one frame of a strip. */
    fun grab(crop: Rect? = null): Bitmap {
        val full = compose.onRoot().captureToImage().asAndroidBitmap()
        if (crop == null) return full
        val l = crop.left.toInt().coerceIn(0, full.width - 1)
        val t = crop.top.toInt().coerceIn(0, full.height - 1)
        val w = crop.width.toInt().coerceAtMost(full.width - l).coerceAtLeast(1)
        val h = crop.height.toInt().coerceAtMost(full.height - t).coerceAtLeast(1)
        return Bitmap.createBitmap(full, l, t, w, h)
    }

    /** The variant the sandbox resolves right now (§2.5: Dusk, Golden and midnight Glow on its own clock). */
    val resolved: ThemeVariant get() = sandbox.variants.variant.value

    /** The golden suffix of this rig's variant ("day" / "glow"). */
    val v: String get() = if (variant == ThemeVariant.Glow) "glow" else "day"
}

/** Day and Glow, for ParameterizedRobolectricTestRunner. */
fun dayAndGlow(): List<Array<Any>> = listOf(arrayOf(ThemeVariant.Day), arrayOf(ThemeVariant.Glow))

/** The fixture with every item checked, the last ones [minutes] after the trip started: complete, its shop time frozen. */
fun completedFixture(minutes: Long = 41): AppState {
    val s = PracticeFixture.state()
    val at = PracticeFixture.TRIP_START + minutes * PracticeFixture.MIN
    return s.list(PracticeFixture.LIST_ID)!!.items.filter { !it.checked }
        .foldIndexed(s) { i, acc, item -> acc.toggleChecked(PracticeFixture.LIST_ID, item.id, at - (13 - i).coerceAtLeast(0) * 1_000L) }
}

/**
 * Every text node on screen (unmerged), in root px: the visible part of each, since the list scrolls under the opaque
 * add bar (a row's words under the bar are hidden by it; the bar's own words are not). The printed receipt's own words
 * are left out (it is never over itself).
 */
fun textBoxes(compose: ComposeContentTestRule): List<Pair<String, Rect>> {
    val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
    var barTop = Float.MAX_VALUE
    fun find(n: androidx.compose.ui.semantics.SemanticsNode) {
        if (n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == PracticeTags.ADD_BAR) barTop = n.boundsInRoot.top
        n.children.forEach(::find)
    }
    find(root)
    val out = ArrayList<Pair<String, Rect>>()
    fun walk(n: androidx.compose.ui.semantics.SemanticsNode, inBar: Boolean) {
        val tag = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
        // The printed receipt's own words are its own (the unmerged tree keeps the words its one node replaces).
        if (tag == EggTags.RECEIPT) return
        val mine = inBar || tag == PracticeTags.ADD_BAR
        val text = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
        val box = n.boundsInRoot.let { b -> if (mine) b else Rect(b.left, b.top, b.right, minOf(b.bottom, barTop)) }
        if (text != null && box.width > 0f && box.height > 0f) out += text.joinToString() to box
        n.children.forEach { walk(it, mine) }
    }
    walk(root, false)
    return out
}

/**
 * Every word on screen: the text nodes ([textBoxes]) and the practice list's `text:` keep-outs (the strips and the book
 * speak as one node each, so their tab names, metas and date are not text nodes of their own), the list's words cut
 * at the add bar's top as the text nodes are. The add bar's keep-out is its whole bar, so its words come from its text
 * nodes only.
 */
fun wordBoxes(compose: ComposeContentTestRule, anchors: AnchorRegistry): List<Pair<String, Rect>> {
    val barTop = anchors.bounds(AnchorRegistry.ADD_BAR)?.top ?: Float.MAX_VALUE
    val keepOuts = anchors.keys().filter { it.startsWith(EggAnchors.TEXT_PREFIX) && it != EggAnchors.TEXT_PREFIX + "addbar" }
        .mapNotNull { key -> anchors.bounds(key)?.let { b -> key to Rect(b.left, b.top, b.right, minOf(b.bottom, barTop)) } }
        .filter { (_, b) -> b.width > 0f && b.height > 0f }
    return textBoxes(compose) + keepOuts
}

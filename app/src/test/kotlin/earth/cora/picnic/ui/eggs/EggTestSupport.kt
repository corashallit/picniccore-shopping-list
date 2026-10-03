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
 * Random and a fake clock) rendered as the practice list, on the Compose test clock (autoAdvance off). The sandbox's
 * fake clock moves with the test clock frame by frame, so the eggs' wall-clock rules (1.5 s between sun taps, the 6 s
 * bonus limit, the 5.2 s petal cooldown) and the overlays' frame-clock beats run on one time line.
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
    val clock = FakeClock(startAt)
    val sandbox = SurpriseLabSandbox(
        fixture = fixture,
        clock = clock,
        feedback = feedback,
        announcer = announcer,
        timeouts = timeouts,
        seed = seed,
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

    /** One frame; the sandbox clock moves with it. */
    fun frame() {
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
        val before = now
        compose.mainClock.advanceTimeByFrame()
        clock.advance(now - before)
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
 * A frame-strip golden (§20 "strip"): [frames] in rows of [cols], each scaled by [scale] and labelled with its time in
 * ms, on white.
 */
fun stripOf(frames: List<Pair<Long, Bitmap>>, cols: Int = 4, scale: Float = 0.5f): Bitmap {
    val w = (frames.maxOf { it.second.width } * scale).toInt()
    val h = (frames.maxOf { it.second.height } * scale).toInt()
    val label = 40
    val gap = 12
    val rows = (frames.size + cols - 1) / cols
    val out = Bitmap.createBitmap(cols * (w + gap) + gap, rows * (h + label + gap) + gap, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(0x2E, 0x3A, 0x8C)
        textSize = 28f
        typeface = android.graphics.Typeface.MONOSPACE
    }
    val filter = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    frames.forEachIndexed { i, (t, bmp) ->
        val x = gap + (i % cols) * (w + gap)
        val y = gap + (i / cols) * (h + label + gap)
        canvas.drawText("$t ms", x.toFloat(), (y + 30).toFloat(), text)
        canvas.drawBitmap(bmp, null, android.graphics.Rect(x, y + label, x + (bmp.width * scale).toInt(), y + label + (bmp.height * scale).toInt()), filter)
    }
    return out
}

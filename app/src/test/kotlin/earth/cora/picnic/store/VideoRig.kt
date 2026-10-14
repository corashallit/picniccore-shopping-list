package earth.cora.picnic.store

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.MainTestClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.fetchRobolectricWindowRoots
import earth.cora.picnic.AppContainer
import earth.cora.picnic.PicnicApp
import earth.cora.picnic.core.assistant.AudioClip
import earth.cora.picnic.core.assistant.ConnectionCheck
import earth.cora.picnic.core.assistant.DrawnIcon
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.assistant.IconDecision
import earth.cora.picnic.core.assistant.IconDrawRequest
import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.assistant.Transcript
import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.weather.WeatherSource
import earth.cora.picnic.core.weather.WeatherToday
import earth.cora.picnic.platform.FakeNetworkStatus
import earth.cora.picnic.platform.InMemoryWeatherCache
import earth.cora.picnic.platform.WeatherService
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.roundToLong
import kotlin.random.Random

/*
 * The feature video's rig (FeatureVideoRecorder): where the frames go, the container's fakes, the camera that steps the
 * held clock at 30 frames a second and writes each frame of the whole screen (every window, a popup over the activity
 * included) as a PNG, and the PNG writer itself. The frame contract with design/store/make_feature_video.py:
 * <root>/<segment>/00000.png … (1080 × 2280 RGB) and segment.json {name, fps, width, height, frames, theme, marks,
 * taps}; frame k is the app at t0 + round(k × 1000 / 30) ms of its clock; marks name the storyboard's beats, taps say
 * where a finger came down (frame, x, y in screen px).
 */

/** The frames' root folder: the PICNIC_VIDEO_FRAMES environment variable, or null (the recorder is skipped). */
internal val videoRoot: File? get() = System.getenv(VIDEO_ENV)?.takeIf { it.isNotBlank() }?.let(::File)

internal const val VIDEO_ENV: String = "PICNIC_VIDEO_FRAMES"

/** Frames per second of app time. */
internal const val VIDEO_FPS: Int = 30

/** Saturday 3 October 2026, 10:30 in Toronto: the store screenshots' morning. */
internal const val VIDEO_MORNING: Long = 1_791_037_800_000L

private val TORONTO: TimeZone = TimeZone.getTimeZone("America/Toronto")

/**
 * What a segment starts from: the app's clock reads [startsAt] when its clock is at 0 and runs with the Compose test
 * clock; [seed] writes the store and the album before MainActivity launches.
 */
internal class Scene(val startsAt: Long = VIDEO_MORNING, val seed: suspend (AppContainer) -> Unit = {})

/**
 * Installs the fakes on the real container before MainActivity launches (an outer rule around the Compose rule), seeds
 * the scene the test is named after ([scenes]), and skips the test when [VIDEO_ENV] is not set. The app's clock is
 * [Scene.startsAt] plus the Compose test clock ([clock], read once the Compose rule has started); the zone is Toronto's
 * for the test, as on her phone.
 */
internal class VideoSetup(private val scenes: (String) -> Scene, private val clock: () -> MainTestClock) : TestRule {
    lateinit var fakes: VideoFakes
        private set

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            assumeTrue("the feature video is recorded only when $VIDEO_ENV names a folder", videoRoot != null)
            val zone = TimeZone.getDefault()
            TimeZone.setDefault(TORONTO)
            try {
                val scene = scenes(description.methodName)
                val container = ApplicationProvider.getApplicationContext<PicnicApp>().container
                fakes = VideoFakes(container, scene.startsAt) { runCatching { clock().currentTime }.getOrDefault(0L) }
                fakes.install()
                runBlocking { scene.seed(container) }
                base.evaluate()
                fakes.assertNoNetwork()
            } finally {
                TimeZone.setDefault(zone)
            }
        }
    }
}

/**
 * The container's stand-ins, swapped in by reflection for its lazy fields before anything reads them (no production
 * code knows about the video): a dummy key (so the add flows take the with-a-key path), Surprises chosen for a calm
 * shot, an online network, a [ScriptedAssistant], library stickers only, Toronto, no weather feed, and the clock pinned
 * to [startsAt]. Nothing here reaches a network: [assertNoNetwork] checks the HTTP client was never built.
 */
internal class VideoFakes(private val container: AppContainer, private val startsAt: Long, elapsed: () -> Long) {
    val clock: () -> Long = { startsAt + elapsed() }

    /**
     * A key that is not an OpenAI key (no "sk-" prefix). Surprises: Easter eggs on, so the seasonal tape shows as on
     * her phone (Thanksgiving's window holds 3 October), but the random rolls are held off ([NoShinyLuck]); sounds and
     * haptics off (the video is silent); the Next stop card and the celebration on; weather and "Use my area" off (no
     * feed, Toronto). The one-time hold hint and move note were seen long ago: she has shopped with the app before.
     * Drawing new stickers is off: every sticker in the video is from the library.
     */
    val settings = InMemorySettingsRepository(
        AppSettings(
            drawIcons = false, eggs = true, sounds = false, haptics = false, nextStopCard = true, celebration = true,
            weather = false, useMyArea = false, moveNoteShown = true, holdHintDone = true,
        ),
        storedToken = "video-demo-key",
    )
    val network = FakeNetworkStatus(online = true)
    val assistant = ScriptedAssistant()
    val decider = LibraryIconDecider(IconCatalog.default)
    val forecast = CountingForecast()

    fun install() {
        container.swap("clock", clock)
        container.swapLazy("settings", lazyOf(settings))
        container.swapLazy("network", lazyOf(network))
        container.swapLazy("assistant", lazyOf<GroceryAssistant>(assistant))
        container.swapLazy("iconDecider", lazyOf<IconDecider>(decider))
        container.swapLazy("places", lazyOf<PlaceSource>(PlaceSource.Toronto))
        container.swapLazy("weather", lazyOf(WeatherService(forecast, InMemoryWeatherCache(), clock)))
        // The container's own Surprise engine, with its injected Random held off the 1-in-200 shiny roll.
        val engine = container.delegate("surprises")
        container.swapLazy("surprises", lazy { engine.value!!.also { it.swap("random", NoShinyLuck) } })
        // Egg 17 marched earlier today: its once-a-day parade never starts in the middle of a shot.
        runBlocking { settings.markAntsDay(java.time.Instant.ofEpochMilli(startsAt).atZone(TORONTO.toZoneId()).toLocalDate()) }
    }

    /** No request was made: the HTTP client was never even built, and the fakes saw no weather or drawing request. */
    fun assertNoNetwork() {
        assertEquals("the HTTP client was never built", false, container.delegate("client").isInitialized())
        assertEquals("no forecast was asked for", 0, forecast.calls)
        assertEquals("no sticker was drawn", 0, assistant.drawn)
    }
}

/** Never rolls the 1 in N: no shiny (egg 6) and no jackpot (egg 13) in the video. */
private object NoShinyLuck : Random() {
    override fun nextBits(bitCount: Int): Int = 0

    override fun nextInt(until: Int): Int = until - 1
}

private fun Any.field(name: String): java.lang.reflect.Field {
    var c: Class<*>? = javaClass
    while (c != null) {
        runCatching { return c.getDeclaredField(name).apply { isAccessible = true } }
        c = c.superclass
    }
    error("no field $name on ${javaClass.name}")
}

private fun Any.swap(name: String, value: Any) = field(name).set(this, value)

private fun AppContainer.swapLazy(name: String, value: Lazy<*>) = swap("$name\$delegate", value)

private fun AppContainer.delegate(name: String): Lazy<*> = field("$name\$delegate").get(this) as Lazy<*>

/**
 * The assistant's answers, in order: each [answer] waits at its [Answer.gate] until the script lets it through, so the
 * add bar's capsule wobbles (or the thinking box reads) for exactly as long as the storyboard says. The answers are
 * written as the real assistant returns them after its post-processing (aisles filed, library stickers, names already
 * on the list marked). Nothing else is expected: a sort or a basket guess answers as "no idea" (the app's own
 * fallbacks).
 */
internal class ScriptedAssistant : GroceryAssistant {
    class Answer(val result: IngestResult) {
        val gate = CompletableDeferred<Unit>()
    }

    private val answers = ArrayDeque<Answer>()
    val asked = mutableListOf<String>()
    @Volatile var drawn = 0

    fun answer(result: IngestResult): Answer = Answer(result).also { synchronized(answers) { answers.addLast(it) } }

    override suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult {
        val answer = synchronized(answers) {
            asked += (input as? IngestInput.Text)?.text ?: input.javaClass.simpleName
            answers.removeFirstOrNull()
        } ?: error("no answer scripted for $input")
        answer.gate.await()
        return answer.result
    }

    override suspend fun transcribe(clip: AudioClip, keywords: List<String>): Transcript = error("no voice note in the video")

    override suspend fun recategorize(items: List<NamedItem>, aisles: List<Aisle>, corrections: List<Correction>): Map<String, String?> =
        items.associate { it.id to null }

    override suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind> = emptyMap()

    override suspend fun drawIcon(request: IconDrawRequest): DrawnIcon {
        drawn++
        error("nothing is drawn in the video")
    }

    override suspend fun testConnection(): ConnectionCheck = error("no connection test in the video")
}

/** Decides every name from the sticker library (the catalog's own match), at once. */
internal class LibraryIconDecider(private val catalog: IconCatalog) : IconDecider {
    override suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> =
        items.associate { it.id to IconDecision(icon = catalog.match(it.name)?.key, category = null) }
}

/** The weather feed's stand-in: counts asks (there should be none: Weather surprises are off) and knows no forecast. */
internal class CountingForecast : WeatherSource {
    @Volatile var calls = 0

    override suspend fun today(): WeatherToday? {
        calls++
        return null
    }
}

/**
 * The camera: steps the held Compose clock at exactly [VIDEO_FPS] frames a second and writes frame k, the whole screen
 * at t0 + round(k × 1000 / fps) ms, to [dir]. The test frame clock is set to deliver a frame every millisecond, so each
 * captured frame shows the app exactly at its time (Compose's motion runs on frame time); background work the script
 * waits for is waited for with the clock held ([settle]), never filmed as frozen frames. [marks] name the storyboard's
 * beats for the compositor's captions; [taps] are where a finger came down (screen px).
 */
internal class FrameCamera(
    private val compose: ComposeTestRule,
    root: File,
    private val name: String,
    private val theme: String,
) {
    private val dir = File(root, name).apply {
        deleteRecursively()
        mkdirs()
    }
    private val start = compose.mainClock.currentTime
    private val marks = mutableListOf<Pair<String, Int>>()
    private val taps = mutableListOf<Triple<Int, Int, Int>>()
    private val writes = mutableListOf<Future<*>>()
    private var captureNanos = 0L

    /** The next frame's index. */
    var frame: Int = 0
        private set

    /** Ms of app time since the segment's first frame. */
    val elapsed: Long get() = compose.mainClock.currentTime - start

    /** Names a beat at the next frame, or at [at] (a frame the script knows is coming). */
    fun mark(beat: String, at: Int = frame) {
        marks += beat to at
    }

    fun tap(x: Float, y: Float) {
        taps += Triple(frame, x.toInt(), y.toInt())
    }

    /** Captures the screen as frame [frame], then runs the clock on to the next frame's time. */
    fun shoot() {
        settle()
        val began = System.nanoTime()
        val shot = captureScreen()
        val pixels = IntArray(shot.width * shot.height)
        shot.getPixels(pixels, 0, shot.width, 0, 0, shot.width, shot.height)
        val w = shot.width
        val h = shot.height
        shot.recycle()
        captureNanos += System.nanoTime() - began
        val file = File(dir, "%05d.png".format(frame))
        writes += PngWriter.submit(file, w, h, pixels)
        frame++
        val next = start + (frame * 1000.0 / VIDEO_FPS).roundToLong()
        val step = next - compose.mainClock.currentTime
        if (step > 0) compose.mainClock.advanceTimeBy(step, ignoreFrameDuration = true)
    }

    /** Films [ms] of app time. */
    fun roll(ms: Long) {
        val until = elapsed + ms
        while (elapsed < until) shoot()
    }

    /** Films until [done] holds, at most [maxMs] of app time; fails with [what] otherwise. */
    fun rollUntil(what: String, maxMs: Long = 10_000, done: () -> Boolean) {
        val until = elapsed + maxMs
        while (!done()) {
            check(elapsed < until) { "timed out filming until $what" }
            shoot()
        }
    }

    /**
     * Waits with the clock held (real time, at most [timeoutMs]) until [done] holds: the store's fsync, a ViewModel's
     * coroutine on the main looper, the album sync on the app scope. Nothing is filmed meanwhile.
     */
    fun settle(what: String = "the app to settle", timeoutMs: Long = 15_000, done: () -> Boolean = { true }) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            Snapshot.sendApplyNotifications()
            compose.waitForIdle()
            if (done()) return
            check(System.currentTimeMillis() < deadline) { "timed out waiting (clock held) for $what" }
            Thread.sleep(5)
        }
    }

    /** Waits for the PNGs and writes segment.json; returns ms of capture per frame (the screen grab only). */
    fun finish(): Double {
        writes.forEach { it.get(2, TimeUnit.MINUTES) }
        val json = buildString {
            append("{\"name\": \"").append(name).append("\", \"fps\": ").append(VIDEO_FPS)
            append(", \"width\": ").append(WIDTH).append(", \"height\": ").append(HEIGHT)
            append(", \"frames\": ").append(frame).append(", \"theme\": \"").append(theme).append("\",\n")
            append(" \"marks\": [")
            append(marks.sortedBy { it.second }.joinToString(", ") { (beat, at) -> "{\"name\": \"$beat\", \"frame\": $at}" })
            append("],\n \"taps\": [")
            append(taps.joinToString(", ") { (at, x, y) -> "{\"frame\": $at, \"x\": $x, \"y\": $y}" })
            append("]}\n")
        }
        File(dir, "segment.json").writeText(json)
        return if (frame == 0) 0.0 else captureNanos / 1e6 / frame
    }

    /**
     * The whole screen as the phone shows it: every window bottom to top (the activity's, then any popup or dialog over
     * it; the app's sheets draw in the activity's own window, scrim included), each at its place on the screen, a
     * window that dims what is behind it dimming it first, as Roborazzi's screen capture composes the same window list.
     */
    @OptIn(InternalRoborazziApi::class)
    private fun captureScreen(): Bitmap {
        val roots = fetchRobolectricWindowRoots()
        val screen = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screen)
        roots.forEachIndexed { i, root ->
            val view = root.decorView
            if (view.width <= 0 || view.height <= 0) return@forEachIndexed
            val params = root.windowLayoutParams.orNull()
            if (i > 0 && params != null && params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply { color = android.graphics.Color.argb((params.dimAmount * 255).toInt(), 0, 0, 0) })
            }
            val at = IntArray(2).also(view::getLocationOnScreen)
            val image = windowImage(view)
            canvas.drawBitmap(image, at[0].toFloat(), at[1].toFloat(), null)
            image.recycle()
        }
        return screen
    }

    /**
     * One window's pixels: an activity's or a dialog's window through PixelCopy (Robolectric renders its display lists,
     * hardware mode); a window with no Window of its own (a Compose Popup's layout, added straight to the window
     * manager) drawn into a bitmap.
     */
    private fun windowImage(root: View): Bitmap {
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val window = root.window()
        if (window == null) {
            root.draw(Canvas(image))
            return image
        }
        var result = -1
        PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper()))
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        check(result == PixelCopy.SUCCESS) { "PixelCopy failed: $result" }
        return image
    }

    private fun View.window(): Window? {
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
        if (activity != null && activity.window.decorView === rootView) return activity.window
        return runCatching { rootView.field("mWindow").get(rootView) as? Window }.getOrNull()
    }

    companion object {
        const val WIDTH: Int = 1080
        const val HEIGHT: Int = 2280
    }
}

/**
 * Writes RGB PNGs off the test thread (a few frames at once, so the encoding overlaps the next frames' work):
 * Up-filtered rows (the binder's pinstripes run down the page, so most rows match the one above) deflated at a quick
 * level.
 */
internal object PngWriter {
    private val pool = Executors.newFixedThreadPool(3) { r -> Thread(r, "video-png").apply { isDaemon = true } }
    private val inFlight = Semaphore(6)

    fun submit(file: File, w: Int, h: Int, argb: IntArray): Future<*> {
        inFlight.acquire()
        return pool.submit {
            try {
                file.writeBytes(encode(w, h, argb))
            } finally {
                inFlight.release()
            }
        }
    }

    fun encode(w: Int, h: Int, argb: IntArray): ByteArray {
        val stride = w * 3
        val raw = ByteArray(h * (stride + 1))
        val prev = ByteArray(stride)
        val cur = ByteArray(stride)
        for (y in 0 until h) {
            var o = 0
            for (x in 0 until w) {
                val p = argb[y * w + x]
                cur[o++] = (p shr 16).toByte()
                cur[o++] = (p shr 8).toByte()
                cur[o++] = p.toByte()
            }
            val row = y * (stride + 1)
            raw[row] = if (y == 0) 0 else 2
            for (i in 0 until stride) raw[row + 1 + i] = if (y == 0) cur[i] else (cur[i] - prev[i]).toByte()
            cur.copyInto(prev)
        }
        val idat = ByteArrayOutputStream(raw.size / 8)
        DeflaterOutputStream(idat, Deflater(4), 1 shl 16).use { it.write(raw) }
        val out = ByteArrayOutputStream(idat.size() + 64)
        val data = DataOutputStream(out)
        data.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, body: ByteArray) {
            data.writeInt(body.size)
            val head = type.toByteArray(Charsets.US_ASCII)
            data.write(head)
            data.write(body)
            val crc = CRC32().apply { update(head); update(body) }
            data.writeInt(crc.value.toInt())
        }
        val header = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).apply {
                writeInt(w)
                writeInt(h)
                writeByte(8) // bit depth
                writeByte(2) // colour type: RGB
                writeByte(0)
                writeByte(0)
                writeByte(0)
            }
        }.toByteArray()
        chunk("IHDR", header)
        chunk("IDAT", idat.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }
}

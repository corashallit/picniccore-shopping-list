package earth.cora.picnic.platform

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import earth.cora.picnic.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicReference

/*
 * Sounds and haptics as one service (docs/DESIGN_STICKER.md §14). Owner: WP10 (FeedbackGateTest, CueTableTest).
 * CONTRACT: [Feedback], [Cue], [Sound], [Haptic]. [AndroidFeedback] is the phone's implementation (AppContainer
 * builds it with [AndroidFeedback.create]); [NoOpFeedback] is for previews and screenshots; tests record cues
 * with FakeFeedback (test sources).
 */

/** The synthesized clips (§14.2), `res/raw/sk_<clip>.wav` (WP2). */
enum class Sound(val clip: String) {
    THUNK("thunk"),
    TK("tk"),
    PFF("pff"),
    CLUNK("clunk"),
    BEEP("beep"),
    PON("pon"),
    KIRA("kira"),
    PEEL("peel"),
    JINGLE("jingle"),
    PRINT("print"),
    ;

    /** The raw resource's name: `sk_thunk` for `R.raw.sk_thunk`. */
    val resourceName: String get() = "sk_$clip"
}

/**
 * The haptics (§14.3), mapped to HapticFeedbackConstants by [hapticConstant]: CONFIRM (API 30+, else
 * VIRTUAL_KEY), CLOCK_TICK, TEXT_HANDLE_MOVE (API 27+, else CLOCK_TICK), KEYBOARD_TAP.
 */
enum class Haptic { CONFIRM, CLOCK_TICK, TEXT_HANDLE_MOVE, KEYBOARD_TAP }

/** One moment of the cue map (§14.3): its sound and its haptic (either may be absent). */
enum class Cue(val sound: Sound?, val haptic: Haptic?) {
    /** The hanko on a check-off. */
    HANKO(Sound.THUNK, Haptic.CONFIRM),

    /** The peel before the hop. */
    PEEL(Sound.TK, Haptic.CLOCK_TICK),

    /** The sticker lands in its basket. */
    LAND(Sound.PFF, Haptic.TEXT_HANDLE_MOVE),

    /** The LCD digits tick (sound only). */
    LCD_TICK(Sound.BEEP, null),

    /** The twin's magic appear in the book. */
    MAGIC_APPEAR(Sound.KIRA, null),

    /** Un-check, page turn, tray curl. */
    UNCHECK(Sound.PEEL, null),

    /** Tear-off delete. */
    TEAR_OFF(Sound.PEEL, Haptic.CLOCK_TICK),

    /** Aisle done: the lid and its GOT IT. */
    AISLE_DONE(Sound.THUNK, Haptic.CONFIRM),

    /** The pictogram thunks into its stamp. */
    STAMP(Sound.THUNK, Haptic.CONFIRM),

    /** The Next card rises. */
    NEXT_CARD(Sound.PON, null),

    /** The celebration starts. */
    CELEBRATION(Sound.JINGLE, null),

    /** PER-FECT! (scene and book). */
    PERFECT(Sound.THUNK, Haptic.CONFIRM),

    /** A capsule pops (AI results, jackpot, bonus). */
    CAPSULE_POP(Sound.PON, Haptic.CONFIRM),

    /** The cassette starts or stops. */
    CASSETTE(Sound.CLUNK, Haptic.KEYBOARD_TAP),

    /** Egg 4: one soft tick. */
    PETAL_SHAKE(Sound.KIRA, Haptic.CLOCK_TICK),

    /** Egg 15: the pet is fed. */
    PET_FED(Sound.BEEP, null),

    /** Egg 18: the receipt prints. */
    RECEIPT_PRINT(Sound.PRINT, Haptic.CLOCK_TICK),

    /** Egg 18: the receipt tears off. */
    RECEIPT_TEAR(Sound.TK, null),

    /** Pull to refresh is released. */
    PULL_REFRESH(Sound.PON, Haptic.CLOCK_TICK),

    /** Egg 10: the bonus-stage jingle (every 4 s, [RepeatingCue] with [BONUS_JINGLE_PERIOD_MS]). */
    BONUS_JINGLE(Sound.JINGLE, null),
}

/** Egg 10 repeats its jingle this often while the list is resumed (§14.3). */
const val BONUS_JINGLE_PERIOD_MS: Long = 4000

/**
 * Plays a cue, gated by Surprises → Sounds (off by default; never in silent or vibrate) and Haptics (on by
 * default; the phone's own touch-feedback setting applies). Reduced motion does not silence it. Main-safe,
 * never blocks, never throws.
 */
interface Feedback {
    fun cue(cue: Cue)
}

/** Plays nothing. For previews and screenshots. */
object NoOpFeedback : Feedback {
    override fun cue(cue: Cue) = Unit
}

/**
 * The phone's [Feedback] (§14.1).
 *
 * - **Sounds** play only when [AppSettings.sounds] is on (it is off by default) **and** the ringer is in normal
 *   mode (never in silent or vibrate), through a [SoundPool] built with `USAGE_ASSISTANCE_SONIFICATION`, so they
 *   follow the phone's sonification volume, not the media volume. The pool exists only while Sounds is on.
 * - **Haptics** play when [AppSettings.haptics] is on (the default), through `View.performHapticFeedback` on
 *   the resumed activity's window, which also respects the phone's own touch-feedback setting.
 * - Until the stored settings are read, the defaults apply: sounds off, haptics on.
 * - Reduced motion changes nothing here.
 */
class AndroidFeedback internal constructor(
    private val switches: () -> AppSettings,
    private val ringerMode: () -> Int,
    private val sounds: SoundPlayer,
    private val haptics: HapticPlayer,
) : Feedback {
    override fun cue(cue: Cue) {
        try {
            val now = switches()
            val sound = cue.sound
            if (sound != null && now.sounds && ringerMode() == AudioManager.RINGER_MODE_NORMAL) sounds.play(sound)
            val haptic = cue.haptic
            if (haptic != null && now.haptics) haptics.perform(haptic)
        } catch (e: RuntimeException) {
            // A cue is decoration: a failing audio or window service must never break a check-off.
            PicnicLog.w("cue ${cue.name} failed", e)
        }
    }

    companion object {
        /**
         * The production wiring: reads the Surprises switches from [settings] in [scope] (building the sound
         * pool when Sounds turns on and releasing it when it turns off) and tracks the resumed activity for
         * haptics. Create it before the first activity resumes (AppContainer.startOnce), so the window
         * tracker sees it.
         */
        fun create(app: Application, settings: Flow<AppSettings>, scope: CoroutineScope): AndroidFeedback {
            val audio = app.getSystemService(AudioManager::class.java)
            val pool = SoundPoolPlayer(app)
            val window = ResumedWindow(app)
            val current = AtomicReference(AppSettings())
            scope.launch {
                try {
                    settings.collect { now ->
                        current.set(now)
                        try {
                            pool.setEnabled(now.sounds)
                        } catch (e: RuntimeException) {
                            PicnicLog.w("sound pool unavailable", e)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PicnicLog.w("feedback switches unreadable; keeping the last ones", e)
                }
            }
            return AndroidFeedback(
                switches = current::get,
                ringerMode = { audio?.ringerMode ?: AudioManager.RINGER_MODE_SILENT },
                sounds = pool,
                haptics = ViewHaptics(window::decorView),
            )
        }
    }
}

/** Plays one clip at once (or not at all). Implementations never block and never throw. */
internal fun interface SoundPlayer {
    fun play(sound: Sound)
}

/** Performs one haptic at once (or not at all). Implementations never block and never throw. */
internal fun interface HapticPlayer {
    fun perform(haptic: Haptic)
}

/**
 * The HapticFeedbackConstants value for [haptic] on [sdkInt] (§14.3): CONFIRM needs API 30 (VIRTUAL_KEY
 * before it) and TEXT_HANDLE_MOVE API 27 (CLOCK_TICK before it). Older constants exist on every supported API.
 */
@SuppressLint("InlinedApi") // The newer constants are only returned on the API levels that define them.
internal fun hapticConstant(haptic: Haptic, sdkInt: Int = Build.VERSION.SDK_INT): Int = when (haptic) {
    Haptic.CONFIRM -> if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    Haptic.CLOCK_TICK -> HapticFeedbackConstants.CLOCK_TICK
    Haptic.TEXT_HANDLE_MOVE ->
        if (sdkInt >= Build.VERSION_CODES.O_MR1) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK
    Haptic.KEYBOARD_TAP -> HapticFeedbackConstants.KEYBOARD_TAP
}

/**
 * Haptics through `View.performHapticFeedback` on [host] (the resumed window's decor view): the platform
 * applies the phone's touch-feedback setting. Off the main thread the call is posted to it.
 */
internal class ViewHaptics(private val host: () -> View?) : HapticPlayer {
    private val main by lazy { Handler(Looper.getMainLooper()) }

    override fun perform(haptic: Haptic) {
        val constant = hapticConstant(haptic)
        val run = Runnable {
            try {
                host()?.performHapticFeedback(constant)
            } catch (e: RuntimeException) {
                PicnicLog.w("haptic $haptic failed", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else main.post(run)
    }
}

/** Tracks the resumed activity's decor view (the haptics host); null while no activity of the app is resumed. */
internal class ResumedWindow(app: Application) : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var resumed: WeakReference<Activity>? = null

    init {
        app.registerActivityLifecycleCallbacks(this)
    }

    fun decorView(): View? = resumed?.get()?.window?.peekDecorView()

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

/**
 * The ten clips in a [SoundPool] with sonification attributes (§14.1). The pool is built and the clips
 * loaded only while [setEnabled] is on; a clip plays once its load completes (a cue before that is silent,
 * never queued). A clip whose raw resource is missing is skipped, so a build without WP2's sounds still runs.
 */
internal class SoundPoolPlayer(
    private val context: Context,
    private val clipResource: (Sound) -> Int = { rawClipId(context, it) },
) : SoundPlayer {
    private val lock = Any()
    private var pool: SoundPool? = null
    private val loading = HashMap<Int, Sound>()
    private val loaded = EnumMap<Sound, Int>(Sound::class.java)

    /** The pool while Sounds is on, for tests. */
    @get:VisibleForTesting
    internal val currentPool: SoundPool? get() = synchronized(lock) { pool }

    fun setEnabled(enabled: Boolean) {
        if (enabled) prepare() else release()
    }

    private fun prepare(): Unit = synchronized(lock) {
        if (pool != null) return
        val created = SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(SONIFICATION).build()
        created.setOnLoadCompleteListener { source, sampleId, status ->
            synchronized(lock) {
                val sound = loading.remove(sampleId)
                if (source === pool && sound != null && status == 0) loaded[sound] = sampleId
            }
        }
        pool = created
        for (sound in Sound.entries) {
            try {
                val res = clipResource(sound)
                if (res != 0) loading[created.load(context, res, 1)] = sound
            } catch (e: RuntimeException) {
                PicnicLog.w("clip ${sound.clip} not loaded", e) // that one cue stays silent
            }
        }
    }

    private fun release(): Unit = synchronized(lock) {
        pool?.release()
        pool = null
        loading.clear()
        loaded.clear()
    }

    override fun play(sound: Sound) {
        val (target, sampleId) = synchronized(lock) {
            val p = pool ?: return
            val id = loaded[sound] ?: return
            p to id
        }
        target.play(sampleId, 1f, 1f, 1, 0, 1f)
    }

    companion object {
        const val MAX_STREAMS = 4

        /** Sonification: the phone's sonification volume, independent of the media volume (§14.1). */
        val SONIFICATION: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

/**
 * `R.raw.sk_<clip>` by name, 0 when the clip is not in this build. The clips are WP2's generated files; looking
 * them up by name keeps this service compiling and running without them. `res/raw/a11y_keep.xml` keeps
 * them through resource shrinking (`tools:keep="@raw/sk_*"`).
 */
@SuppressLint("DiscouragedApi") // See above: the set is fixed (Sound.entries) and kept by tools:keep.
internal fun rawClipId(context: Context, sound: Sound): Int =
    context.resources.getIdentifier(sound.resourceName, "raw", context.packageName)

/**
 * Repeats [cue] every [periodMs] while [active] and the screen is resumed, starting at once (egg 10's
 * bonus-stage jingle every 4 s, §14.3). It stops when the screen pauses or leaves the composition and starts
 * again on resume. The period runs on the composition's clock, so a Compose test drives it with `mainClock`.
 */
@Composable
fun RepeatingCue(feedback: Feedback, cue: Cue, periodMs: Long, active: Boolean = true) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = active && state.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(feedback, cue, periodMs, running) {
        if (!running) return@LaunchedEffect
        while (true) {
            feedback.cue(cue)
            delay(periodMs)
        }
    }
}

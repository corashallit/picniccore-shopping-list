package earth.cora.picnic.platform

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Checklist 276–277 (docs/DESIGN_STICKER.md §14.1, §14.3): sounds are off by default and play only when on
 * with the ringer in normal mode, as sonification; haptics are on by default with the §14.3 constants and their
 * fallbacks below API 30 and 27; nothing fires unless its switch allows it; a cue never throws.
 */
@RunWith(AndroidJUnit4::class)
class FeedbackGateTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val audio: AudioManager = app.getSystemService(AudioManager::class.java)

    private class Recorder : SoundPlayer, HapticPlayer {
        val sounds = mutableListOf<Sound>()
        val haptics = mutableListOf<Haptic>()

        override fun play(sound: Sound) {
            sounds += sound
        }

        override fun perform(haptic: Haptic) {
            haptics += haptic
        }
    }

    private fun feedback(settings: AppSettings, recorder: Recorder) =
        AndroidFeedback({ settings }, { audio.ringerMode }, recorder, recorder)

    @Test
    fun sounds() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL

        // Off by default: the hanko still thunks in the hand, never out loud.
        val byDefault = Recorder()
        feedback(AppSettings(), byDefault).cue(Cue.HANKO)
        assertFalse(AppSettings().sounds)
        assertEquals(emptyList<Sound>(), byDefault.sounds)
        assertEquals(listOf(Haptic.CONFIRM), byDefault.haptics)

        // On, ringer normal: every cue with a sound plays it.
        val on = Recorder()
        val soundsOn = feedback(AppSettings(sounds = true), on)
        Cue.entries.forEach(soundsOn::cue)
        assertEquals(Cue.entries.mapNotNull { it.sound }, on.sounds)

        // On, but the phone is on vibrate or silent: no sound, haptics unchanged.
        for (mode in listOf(AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT)) {
            audio.ringerMode = mode
            val quiet = Recorder()
            feedback(AppSettings(sounds = true), quiet).cue(Cue.HANKO)
            assertEquals("ringer mode $mode", emptyList<Sound>(), quiet.sounds)
            assertEquals(listOf(Haptic.CONFIRM), quiet.haptics)
        }
    }

    @Test
    fun hapticsOnByDefaultAndSwitchable() {
        val on = Recorder()
        val defaults = feedback(AppSettings(), on)
        Cue.entries.forEach(defaults::cue)
        assertTrue(AppSettings().haptics)
        assertEquals(Cue.entries.mapNotNull { it.haptic }, on.haptics)

        val off = Recorder()
        val hapticsOff = feedback(AppSettings(haptics = false, sounds = true), off)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        Cue.entries.forEach(hapticsOff::cue)
        assertEquals(emptyList<Haptic>(), off.haptics)
        assertEquals(Cue.entries.mapNotNull { it.sound }, off.sounds)

        val nothing = Recorder()
        val bothOff = feedback(AppSettings(haptics = false, sounds = false), nothing)
        Cue.entries.forEach(bothOff::cue)
        assertEquals(emptyList<Haptic>(), nothing.haptics)
        assertEquals(emptyList<Sound>(), nothing.sounds)
    }

    /** Run once per SDK: the §14.3 constants on API 30+, VIRTUAL_KEY for CONFIRM below 30, CLOCK_TICK for TEXT_HANDLE_MOVE below 27. */
    @Test
    @Config(sdk = [26, 29, 30, 36])
    fun hapticsMapping() {
        val sdk = Build.VERSION.SDK_INT
        val expected = mapOf(
            Haptic.CONFIRM to if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
            Haptic.CLOCK_TICK to HapticFeedbackConstants.CLOCK_TICK,
            Haptic.TEXT_HANDLE_MOVE to if (sdk >= 27) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK,
            Haptic.KEYBOARD_TAP to HapticFeedbackConstants.KEYBOARD_TAP,
        )
        val view = View(app)
        val haptics = ViewHaptics { view }
        for ((haptic, constant) in expected) {
            assertEquals("$haptic on API $sdk", constant, hapticConstant(haptic))
            haptics.perform(haptic)
            assertEquals("$haptic performed on API $sdk", constant, shadowOf(view).lastHapticFeedbackPerformed())
        }
        // The raw values, so a renamed constant can't hide a change: CONFIRM 16 / VIRTUAL_KEY 1, TEXT_HANDLE_MOVE 9 / CLOCK_TICK 4.
        assertEquals(if (sdk >= 30) 16 else 1, hapticConstant(Haptic.CONFIRM))
        assertEquals(if (sdk >= 27) 9 else 4, hapticConstant(Haptic.TEXT_HANDLE_MOVE))
    }

    @Test
    fun hapticConstantFallbacksByApiLevel() {
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, hapticConstant(Haptic.CONFIRM, sdkInt = 29))
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstant(Haptic.CONFIRM, sdkInt = 30))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(Haptic.TEXT_HANDLE_MOVE, sdkInt = 26))
        assertEquals(HapticFeedbackConstants.TEXT_HANDLE_MOVE, hapticConstant(Haptic.TEXT_HANDLE_MOVE, sdkInt = 27))
    }

    @Test
    fun soundPoolPlaysLoadedClipsAsSonification() {
        val ids = Sound.entries.associateWith { 0x7f130000 + it.ordinal }
        val player = SoundPoolPlayer(app) { ids.getValue(it) }

        // No pool while Sounds is off.
        player.play(Sound.THUNK)
        assertNull(player.currentPool)

        player.setEnabled(true)
        val pool = checkNotNull(player.currentPool) { "Sounds on builds the pool" }
        player.setEnabled(true) // idempotent: same pool, clips not loaded twice
        assertTrue(pool === player.currentPool)

        // A cue before its clip has loaded is silent (never queued).
        player.play(Sound.THUNK)
        assertFalse(shadowOf(pool).wasResourcePlayed(ids.getValue(Sound.THUNK)))

        shadowOf(pool).notifyResourceLoaded(ids.getValue(Sound.THUNK), true)
        player.play(Sound.THUNK)
        assertTrue(shadowOf(pool).wasResourcePlayed(ids.getValue(Sound.THUNK)))
        val playback = shadowOf(pool).getResourcePlaybacks(ids.getValue(Sound.THUNK)).single()
        assertEquals(1f, playback.leftVolume)
        assertEquals(0, playback.loop)

        // A clip that failed to decode never plays.
        shadowOf(pool).notifyResourceLoaded(ids.getValue(Sound.PON), false)
        player.play(Sound.PON)
        assertFalse(shadowOf(pool).wasResourcePlayed(ids.getValue(Sound.PON)))

        // Sonification, not media.
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, SoundPoolPlayer.SONIFICATION.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, SoundPoolPlayer.SONIFICATION.contentType)

        // Sounds off releases the pool.
        player.setEnabled(false)
        assertNull(player.currentPool)
        player.play(Sound.THUNK) // nothing to play, no crash
    }

    @Test
    fun clipsAreFoundByNameAndToleratedWhenMissing() {
        // Each clip is looked up as R.raw.sk_<clip>; a build without WP2's clips gets 0 and stays silent.
        for (sound in Sound.entries) {
            assertEquals("sk_${sound.clip}", sound.resourceName)
            val id = rawClipId(app, sound)
            if (id != 0) assertEquals(sound.resourceName, app.resources.getResourceEntryName(id))
        }
        val player = SoundPoolPlayer(app)
        player.setEnabled(true)
        player.play(Sound.THUNK)
        player.setEnabled(false)
    }

    @Test
    fun aFailingServiceNeverThrows() {
        val broken = AndroidFeedback(
            switches = { AppSettings(sounds = true) },
            ringerMode = { throw SecurityException("no audio service") },
            sounds = { throw IllegalStateException("pool released") },
            haptics = { throw IllegalStateException("no window") },
        )
        Cue.entries.forEach(broken::cue)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun productionWiringFollowsTheSwitchesAndTheResumedWindow() = runTest {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val settings = MutableStateFlow(AppSettings())
        val feedback = AndroidFeedback.create(app, settings, backgroundScope)
        runCurrent()

        // Created before the activity resumes (AppContainer.startOnce), so the window tracker sees it.
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val decor = controller.get().window.decorView
        feedback.cue(Cue.HANKO)
        assertEquals(hapticConstant(Haptic.CONFIRM), shadowOf(decor).lastHapticFeedbackPerformed())

        settings.value = AppSettings(haptics = false)
        runCurrent()
        feedback.cue(Cue.PEEL)
        assertEquals(hapticConstant(Haptic.CONFIRM), shadowOf(decor).lastHapticFeedbackPerformed())

        settings.value = AppSettings(haptics = true, sounds = true)
        runCurrent()
        feedback.cue(Cue.PEEL) // this build may have no clips: silent, never a crash
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, shadowOf(decor).lastHapticFeedbackPerformed())

        // Paused: no window to buzz.
        controller.pause()
        feedback.cue(Cue.HANKO)
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, shadowOf(decor).lastHapticFeedbackPerformed())
        controller.resume()
        feedback.cue(Cue.HANKO)
        assertEquals(hapticConstant(Haptic.CONFIRM), shadowOf(decor).lastHapticFeedbackPerformed())
        controller.pause().stop().destroy()
    }
}

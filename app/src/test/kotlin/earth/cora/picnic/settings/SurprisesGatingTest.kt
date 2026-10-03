package earth.cora.picnic.settings

import android.app.Application
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.calendar.Place
import earth.cora.picnic.platform.AndroidFeedback
import earth.cora.picnic.platform.AreaPlaceSource
import earth.cora.picnic.platform.CoarseLocator
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.Haptic
import earth.cora.picnic.platform.HapticPlayer
import earth.cora.picnic.platform.InMemoryPlaceCache
import earth.cora.picnic.platform.LatLon
import earth.cora.picnic.platform.Sound
import earth.cora.picnic.platform.SoundPlayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checklist 228 (docs/DESIGN_STICKER.md §6.8, §0.4): each Surprises switch gates exactly its own feature, table-driven.
 * For every switch, flipping it from its default changes that feature and nothing else, measured on everything that
 * reads the switches: the real Feedback (sounds, haptics), the real "Use my area" place, and the gate every other
 * consumer asks ([AppSettings.allows]: Easter eggs for WP9's engine, the Next card and the Celebration for WP6's scenes,
 * Weather surprises for WP9's forecast).
 */
@RunWith(AndroidJUnit4::class)
class SurprisesGatingTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Everything a switch can change, observed from the outside. */
    private data class Observed(
        val soundPlayed: Boolean,
        val hapticFelt: Boolean,
        val areaUsed: Boolean,
        val eggs: Boolean,
        val nextStopCard: Boolean,
        val celebration: Boolean,
        val weather: Boolean,
    )

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

    private val hamilton = object : CoarseLocator {
        override fun hasPermission() = true
        override suspend fun locate() = LatLon(43.26, -79.87)
    }

    private fun observe(settings: AppSettings): Observed = runBlocking {
        app.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_NORMAL
        val recorder = Recorder()
        AndroidFeedback({ settings }, { AudioManager.RINGER_MODE_NORMAL }, recorder, recorder).cue(Cue.HANKO)
        val place = AreaPlaceSource({ settings.useMyArea }, hamilton, InMemoryPlaceCache(), { 0L }).place()
        Observed(
            soundPlayed = recorder.sounds.isNotEmpty(),
            hapticFelt = recorder.haptics.isNotEmpty(),
            areaUsed = place != Place.TORONTO,
            eggs = settings.allows(Surprise.EasterEggs),
            nextStopCard = settings.allows(Surprise.NextStopCard),
            celebration = settings.allows(Surprise.Celebration),
            weather = settings.allows(Surprise.Weather),
        )
    }

    /** The one field of [Observed] each switch owns. */
    private val owns: Map<Surprise, (Observed) -> Boolean> = mapOf(
        Surprise.EasterEggs to { it.eggs },
        Surprise.Sounds to { it.soundPlayed },
        Surprise.Haptics to { it.hapticFelt },
        Surprise.NextStopCard to { it.nextStopCard },
        Surprise.Celebration to { it.celebration },
        Surprise.Weather to { it.weather },
        Surprise.UseMyArea to { it.areaUsed },
    )

    @Test
    fun eachSwitchGatesExactlyItsFeature() {
        val defaults = observe(AppSettings())
        assertEquals(Surprise.entries.toSet(), owns.keys)
        for (surprise in Surprise.entries) {
            val flipped = observe(AppSettings().with(surprise, !surprise.default))
            val own = owns.getValue(surprise)
            assertEquals("$surprise turns its own feature ${if (surprise.default) "off" else "on"}", !surprise.default, own(flipped))
            assertEquals("$surprise is on by default exactly when its feature is", surprise.default, own(defaults))
            for ((other, read) in owns) {
                if (other != surprise) assertEquals("$surprise leaves $other alone", read(defaults), read(flipped))
            }
        }
    }

    @Test
    fun allOffIsQuiet() {
        val off = Surprise.entries.fold(AppSettings()) { s, surprise -> s.with(surprise, false) }
        assertEquals(Observed(false, false, false, false, false, false, false), observe(off))
    }
}

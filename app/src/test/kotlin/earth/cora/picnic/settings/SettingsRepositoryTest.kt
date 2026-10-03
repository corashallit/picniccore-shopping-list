package earth.cora.picnic.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Checklist 227 (docs/DESIGN_STICKER.md §6.8, §0.4, §19.2): the Surprises defaults are eggs on, sounds off, haptics on,
 * Next card on, celebration on, weather off and Use my area off; every switch, the Dusk binder and the Surprise lab
 * persist in DataStore under their keys and come back after a restart.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private fun store(): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("settings.preferences_pb") }

    @Test
    fun surprisesDefaults() = runBlocking {
        val s = DataStoreSettingsRepository(store(), InMemoryTokenStore()).current()
        assertTrue("Easter eggs on", s.eggs)
        assertFalse("Sounds off", s.sounds)
        assertTrue("Haptics on", s.haptics)
        assertTrue("Next stop card on", s.nextStopCard)
        assertTrue("Celebration on", s.celebration)
        assertFalse("Weather surprises off", s.weather)
        assertFalse("Use my area off", s.useMyArea)
        assertFalse(s.duskBinder)
        assertFalse(s.surpriseLab)
        assertEquals(AppSettings(), s)
        for (surprise in Surprise.entries) assertEquals(surprise.name, surprise.default, s.allows(surprise))
        assertEquals(
            listOf("eggs", "sounds", "haptics", "next_stop_card", "celebration", "weather", "use_my_area"),
            Surprise.entries.map { it.key },
        )
    }

    @Test
    fun everySwitchPersistsAndComesBackAfterARestart() = runBlocking {
        val dataStore = store()
        val repo = DataStoreSettingsRepository(dataStore, InMemoryTokenStore())
        for (surprise in Surprise.entries) repo.setSurprise(surprise, !surprise.default)
        repo.setDuskBinder(true)
        repo.setSurpriseLab(true)

        // A new repository over the same file: a cold start.
        val restarted = DataStoreSettingsRepository(dataStore, InMemoryTokenStore()).settings.first()
        for (surprise in Surprise.entries) assertEquals(surprise.name, !surprise.default, restarted.allows(surprise))
        assertTrue(restarted.duskBinder)
        assertTrue(restarted.surpriseLab)
        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(Surprise.entries.map { it.key }.toSet() + setOf("dusk_binder", "surprise_lab"), keys)

        // Back to the defaults: the keys go (absent = default).
        for (surprise in Surprise.entries) repo.setSurprise(surprise, surprise.default)
        repo.setDuskBinder(false)
        assertEquals(setOf("surprise_lab"), dataStore.data.first().asMap().keys.map { it.name }.toSet())
        assertEquals(AppSettings(surpriseLab = true), repo.current())
    }

    @Test
    fun theInMemoryRepositoryBehavesTheSame() = runBlocking {
        val repo = InMemorySettingsRepository()
        repo.setSurprise(Surprise.UseMyArea, true)
        repo.setSurprise(Surprise.EasterEggs, false)
        repo.setDuskBinder(true)
        repo.setSurpriseLab(true)
        assertEquals(AppSettings(useMyArea = true, eggs = false, duskBinder = true, surpriseLab = true), repo.current())
    }
}

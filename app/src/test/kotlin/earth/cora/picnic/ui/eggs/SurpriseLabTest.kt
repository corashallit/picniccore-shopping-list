package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.PicnicApp
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.tripBook
import earth.cora.picnic.core.store.file
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.settings.Surprise
import earth.cora.picnic.ui.about.SurpriseLabScreen
import earth.cora.picnic.ui.theme.LocalTimeouts
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Item 260 (§6.9, §12.3): the Surprise lab's Try it plays each of the 18 eggs on its sandbox copy of the fixture (table
 * driven, through the lab screen's own buttons), with Easter eggs off too; afterwards the phone's state.json, album.json
 * and DataStore files are byte-identical and every simulated condition is gone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class SurpriseLabTest {
    @get:Rule
    val compose = createComposeRule()

    private val names = listOf(
        "Dusk binder", "Ladybug stroll", "Rolling produce", "Petal shake", "Midnight snack", "Shiny sticker",
        "Magic word", "Nap time", "Birthday cake", "Bonus stage", "Seasonal sheets", "Golden hour", "Gashapon jackpot",
        "Perfect week", "LCD pet", "Rainy day", "Ant parade", "LCD receipt",
    )

    /** What each egg shows on the practice copy right after its Try it, then a little later (follow-ups). */
    private val now: Map<Int, (SurpriseState, SurpriseLabSandbox) -> Unit> = mapOf(
        1 to { e, _ -> assertEquals(true, e.duskFx?.toDusk) },
        2 to { e, _ -> assertNotNull(e.ladybug) },
        3 to { e, s -> assertNotNull(e.rolling); assertTrue(s.store.value.list(s.listId)!!.items.any { it.name == "Strawberries" && !it.checked }) },
        4 to { e, _ -> assertNotNull(e.petalsId) },
        5 to { e, _ -> assertTrue(e.midnight); assertEquals(EggRules.SNACK, e.lcdWord?.text) },
        6 to { _, s -> assertEquals(Rarity.SHINY, s.store.value.list(s.listId)!!.items.last { it.name == "Maple syrup" }.rarity) },
        7 to { e, _ -> assertTrue(e.kirakira) },
        8 to { e, _ -> assertEquals("frozen", e.napSectionId) },
        9 to { e, _ -> assertNotNull(e.confettiId) },
        10 to { e, _ -> assertTrue(e.bonusStage) },
        11 to { e, _ -> assertEquals(Season.HALLOWEEN, e.season) },
        12 to { e, _ -> assertTrue(e.golden) },
        13 to { e, _ -> assertTrue("baking-powder" in e.goldCapsules) },
        14 to { e, _ -> assertTrue(e.perfectWeek) },
        15 to { e, _ -> assertTrue(e.pet); assertEquals(EggRules.FED, e.lcdWord?.text) },
        16 to { e, _ -> assertTrue(e.umbrella) },
        17 to { e, _ -> assertNotNull(e.antsId) },
        18 to { e, _ -> assertNotNull(e.receipt) },
    )
    private val later: Map<Int, (SurpriseState, SurpriseLabSandbox) -> Unit> = mapOf(
        1 to { e, s -> assertTrue(e.dusk); assertEquals(ThemeVariant.Dusk, s.variants.variant.value) },
        9 to { _, s -> assertTrue(s.store.value.list(s.listId)!!.items.single { it.name == "Birthday cake" }.checked) },
        12 to { _, s -> assertEquals(ThemeVariant.Golden, s.variants.variant.value) },
        13 to { e, s -> assertEquals(Rarity.JACKPOT, s.store.value.list(s.listId)!!.items.single { it.id == "baking-powder" }.rarity); assertNotNull(e.jackpot) },
    )

    private fun frame() {
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
    }

    private fun frames(ms: Long) {
        val until = compose.mainClock.currentTime + ms
        var guard = 0
        while (compose.mainClock.currentTime < until && guard++ < 5_000) frame()
    }

    private fun show(lab: SurpriseLabViewModel) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalTimeouts provides FakeTimeouts()) {
                PicnicTheme(variant = ThemeVariant.Day, reducedMotion = false) { SurpriseLabScreen(onBack = {}, lab = lab) }
            }
        }
        frame()
    }

    private fun tryIt(n: Int) {
        // Its click action, as TalkBack's double-tap would: no scroll, so a practice list scrolling into view after
        // the egg before (the lab's bring-into-view) never races the test's own scroll on the held clock.
        compose.onNodeWithContentDescription("Try ${names[n - 1]}").performSemanticsAction(SemanticsActions.OnClick)
        frame()
        frame()
    }

    @Test
    fun everyEggFires() {
        val lab = previewLab()
        show(lab)
        for (n in 1..18) {
            tryIt(n)
            val box = lab.sandbox
            assertNotNull("egg $n made its sandbox", box)
            assertEquals(n, box!!.fired.value)
            now.getValue(n).invoke(box.engine.state.value, box)
            later[n]?.let { check ->
                frames(2_600)
                check(box.engine.state.value, box)
            }
            // Leaving the lab discards the sandbox; the next egg starts from a fresh practice copy.
            lab.close()
            frame()
            assertNull(lab.sandbox)
        }
    }

    @Test
    fun itWorksWithEasterEggsOff() {
        val app = ApplicationProvider.getApplicationContext<PicnicApp>()
        runBlocking { app.container.settings.setSurprise(Surprise.EasterEggs, false) }
        val lab = app.container.surpriseLab()
        show(lab)
        tryIt(7)
        assertTrue("the sandbox forces its own eggs on", lab.sandbox!!.engine.state.value.kirakira)
        tryIt(15)
        assertTrue(lab.sandbox!!.engine.state.value.pet)
        assertFalse("her switch is untouched", runBlocking { app.container.settings.current() }.eggs)
        lab.close()
    }

    @Test
    fun leavesRealDataUntouched() {
        // The phone's own files, as the app keeps them: a list mid-trip, a filed book, her settings.
        val app = ApplicationProvider.getApplicationContext<PicnicApp>()
        val c = app.container
        val fixture = PracticeFixture.state()
        runBlocking {
            c.store.update { fixture }
            val book = requireNotNull(fixture.tripBook(PracticeFixture.LIST_ID, PracticeFixture.NOW - 7 * 86_400_000L))
            c.album.update { it.file(book.copy(id = "t-earlier")) }
            c.album.flush()
            c.settings.setSurprise(Surprise.Weather, true)
            c.settings.setDuskBinder(false)
        }
        val files = listOf(File(app.filesDir, "state.json"), File(app.filesDir, "album.json")) +
            (File(app.filesDir, "datastore").listFiles()?.toList().orEmpty())
        assertTrue("the real files exist: $files", files.size >= 3 && files.all { it.exists() })
        val before = files.associateWith { it.readBytes() }
        val settingsBefore = runBlocking { c.settings.current() }

        // Every egg, one after the other on one sandbox (the lab's own factory, as the phone builds it), then she leaves.
        val lab = c.surpriseLab()
        show(lab)
        for (n in 1..18) {
            tryIt(n)
            frames(400)
        }
        val box = lab.sandbox!!
        assertTrue("the sandbox did change its own copy", box.store.value != fixture)
        lab.close()
        frames(500)

        // Byte-identical.
        for ((f, bytes) in before) assertArrayEquals("${f.name} untouched", bytes, f.readBytes())
        assertEquals(settingsBefore, runBlocking { c.settings.current() })
        // Every simulated condition is gone: a new visit starts from the fixture at 10:24, no Dusk, no rain, no pet.
        assertNull(lab.sandbox)
        tryIt(4)
        val fresh = lab.sandbox!!
        assertEquals(PracticeFixture.NOW, fresh.clock.now())
        assertFalse(fresh.engine.state.value.dusk)
        assertFalse(fresh.engine.state.value.rain)
        assertFalse(fresh.engine.state.value.midnight)
        assertFalse(fresh.store.value.list(fresh.listId)!!.trip!!.pet)
        assertTrue(runBlocking { fresh.album.current() }.books.isEmpty())
        lab.close()
    }
}

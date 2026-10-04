package earth.cora.picnic.ui.eggs

import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.hatchPet
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.ui.add.AddRules
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.core.model.addItems
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Item 237: with Easter eggs off no egg fires and no persisted egg state renders (Dusk, pet, LCD words, tapes, umbrella,
 * birthday hanko; §2.5), while what she collected stays; the Surprise lab's sandbox still fires with them off.
 */
@RunWith(AndroidJUnit4::class)
class SurpriseEngineTest {
    /** Egg 1's trigger: one tap on the sun. */
    private val oneTap: EngineRig.() -> Unit = { engine.onSunTap() }

    /**
     * Item 237's LCD wiggle (WP6's beat, §2.5 and §13.1: the "ready" hint for egg 18): with Easter eggs off, finishing
     * the trip presses the book's PERFECT but never wiggles the LCD; with them on, it wiggles at that beat.
     */
    @Test
    fun theLcdWiggleNeedsEggs() {
        fun wiggle(eggs: Boolean): Pair<Long?, Long?> {
            val engine = earth.cora.picnic.ui.list.motion.MotionChoreographer()
            // The celebration switched off: PERFECT is pressed on the book as the trip completes, with no scene to close.
            engine.settings = earth.cora.picnic.ui.list.motion.MotionSettings(eggs = eggs, celebration = false)
            engine.listId = PracticeFixture.LIST_ID
            var t = 1_000L
            engine.tick(t)
            engine.onToggle(
                earth.cora.picnic.ui.list.motion.ToggleEvent(
                    itemId = "toothpaste", sectionId = "household", checked = true, slot = 23,
                    aisleCompleted = true, tripCompleted = true, nextSectionId = null,
                    personality = earth.cora.picnic.core.catalog.Personality.entries.first(),
                ),
            )
            var perfect: Long? = null
            var wiggled: Long? = null
            while (t < 12_000L) {
                t += 16
                engine.tick(t)
                val p = engine.current.value
                if (perfect == null) perfect = p.bookFx.perfectT0
                if (wiggled == null) wiggled = p.lcdWiggleT0
            }
            return perfect to wiggled
        }
        val (offPerfect, offWiggle) = wiggle(eggs = false)
        assertNotNull("eggs off: PERFECT is still pressed", offPerfect)
        assertNull("eggs off: the LCD never wiggles", offWiggle)
        val (onPerfect, onWiggle) = wiggle(eggs = true)
        assertNotNull(onPerfect)
        assertEquals("eggs on: the LCD wiggles with PERFECT", onPerfect, onWiggle)
    }

    @Test
    fun gating() {
        // Persisted egg state: the Dusk binder saved, a pet on the trip, a shiny row, a cake checked, sheets collected.
        val base = PracticeFixture.state().let { s -> s.hatchPet(s.currentListId) }
        val album = Album(sheets = setOf("sakura", "thanksgiving-2026"), perfectStreak = 4, perfectUntil = PracticeFixture.NOW + 86_400_000)
        val rig = EngineRig(
            state = base,
            settings = AppSettings(eggs = false, weather = true, duskBinder = true),
            album = album,
            random = Random(0),
            weatherSource = CountingWeather { day -> earth.cora.picnic.core.weather.WeatherToday(day, rain = true, snow = false) },
        )
        try {
            val e = rig.engine
            e.onListShown(rig.listId)
            // Nothing fires.
            assertFalse("the magic word is just text", e.interceptText("kirakira"))
            assertTrue("no shiny rolls", (1..2_000).all { e.rollRarity() == Rarity.NONE })
            rig.oneTap()
            e.onLcdHold()
            e.onShake()
            e.onBasketHold("produce", 5_000)
            listOf(0, 0, 1, 1, 2, 3, 2, 3).forEach { e.onStampTap(it) }
            val cake = rig.item("c1", "Birthday cake", "aisle-2-baking", "cake")
            val lemons = rig.item("l1", "Lemons", "produce", "lemon")
            e.onCommitted(listOf(cake, lemons))
            e.onIconDrawing(lemons)
            assertEquals(Rarity.NONE, e.onIconDrawn(lemons))
            val s = rig.eggs
            assertEquals(SurpriseState.Off, s)
            assertEquals("no request with eggs off", 0, rig.weatherSource!!.requests)
            assertTrue("no toasts", rig.toasts.isEmpty())

            // No persisted egg state renders: no Dusk, no pet in the LCD, no words, the default tape, no umbrella,
            // GOT IT on a birthday item.
            assertFalse(s.dusk)
            val lcd = s.lcd(LcdState(mode = LcdMode.Count, count = 10, total = 24, receiptReady = true))
            assertEquals(LcdMode.Count, lcd.mode)
            assertFalse("the receipt action hides with eggs off", lcd.receiptReady)
            assertEquals(WashiPattern.SakuraStripe, AddRules.addBarTape(s))
            assertFalse(s.umbrella)
            assertEquals(HankoText.GotIt, s.hankoFor(cake.copy(checked = true), rig.catalog))
            assertFalse(s.pixelStickers)

            // What she collected stays: the saved flag, the pet, the shiny row, the album.
            runBlocking {
                assertTrue("the saved binder is kept for later", rig.settings.current().duskBinder)
                assertEquals(album.sheets, rig.albumStore.current().sheets)
            }
            assertTrue(rig.list.trip!!.pet)
            assertEquals(Rarity.SHINY, rig.list.items.single { it.id == "maple" }.rarity)
            assertEquals(base, rig.store.value)

            // Eggs back on: the kept state shows again.
            runBlocking { rig.settings.setSurprise(earth.cora.picnic.settings.Surprise.EasterEggs, true) }
            assertTrue(rig.eggs.enabled)
            assertTrue(rig.eggs.dusk)
            assertTrue(rig.eggs.pet)
            assertTrue(rig.eggs.perfectWeek)
            assertEquals(HankoText.HappyBday, rig.eggs.hankoFor(cake.copy(checked = true), rig.catalog))
        } finally {
            rig.close()
        }
    }

    /**
     * A cold start straight into the list (§12.2 eggs 5, 16, 17): the list says it is showing before the switches have
     * loaded from DataStore. Once they load, SNACK? shows at midnight, the forecast is asked and the ants set off, as if
     * she had come in with them known.
     */
    @Test
    fun aColdStartStraightIntoTheListRunsItsChecksOnceTheSwitchesLoad() {
        val loaded = kotlinx.coroutines.flow.MutableStateFlow(false)
        val inner = earth.cora.picnic.settings.InMemorySettingsRepository(AppSettings(eggs = true, weather = true))
        val slow = object : earth.cora.picnic.settings.SettingsRepository by inner {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            override val settings = loaded.filter { it }.flatMapLatest { inner.settings }
        }
        // Midnight snack time, with three picnic things to get on the list.
        val picnic = PracticeFixture.state().let { s ->
            listOf("watermelon" to "Watermelon", "lemonade" to "Lemonade", "sliced_bread" to "Sandwich bread").foldIndexed(s) { i, acc, (key, name) ->
                acc.addItems(s.currentListId, listOf(earth.cora.picnic.core.model.GroceryItem(id = "p$i", name = name, icon = key, addedAt = PracticeFixture.NOW)), PracticeFixture.NOW).first
            }
        }
        val rig = EngineRig(
            state = picnic,
            repo = slow,
            clock = FakeClock(java.time.LocalDate.of(2026, 10, 3).atTime(23, 30).atZone(java.time.ZoneId.of("America/Toronto")).toInstant().toEpochMilli()),
            weatherSource = CountingWeather { day -> earth.cora.picnic.core.weather.WeatherToday(day, rain = true, snow = false) },
        )
        try {
            rig.engine.onListShown(rig.listId)
            assertNull("the switches are not loaded yet", rig.eggs.lcdWord)
            assertEquals(0, rig.weatherSource!!.requests)
            assertNull(rig.eggs.antsId)
            loaded.value = true
            assertEquals("SNACK? as she comes in", EggRules.SNACK, rig.eggs.lcdWord?.text)
            assertEquals("the forecast is asked", 1, rig.weatherSource!!.requests)
            assertTrue(rig.eggs.rain)
            assertEquals(listOf(EggToast.RainToday), rig.toasts)
            assertNotNull("the ants set off", rig.eggs.antsId)
        } finally {
            rig.close()
        }
    }

    @Test
    fun theLabFiresWithEggsOff() {
        val rig = EngineRig(settings = AppSettings(eggs = false), forceEggs = true)
        try {
            val e = rig.engine
            e.onListShown(rig.listId)
            assertTrue(e.interceptText("kirakira"))
            assertNotNull(rig.eggs.kirakiraId)
            rig.oneTap()
            assertNotNull("the sandbox's sun still sets", rig.eggs.duskFx)
            e.forceShinyNext = true
            assertEquals(Rarity.SHINY, e.rollRarity())
        } finally {
            rig.close()
        }
    }

    @Test
    fun eggsOnFireTheSameCalls() {
        val rig = EngineRig(settings = AppSettings(eggs = true))
        try {
            val e = rig.engine
            e.onListShown(rig.listId)
            assertTrue(e.interceptText("Kira Kira!"))
            assertFalse(e.interceptText("kiwi"))
            rig.oneTap()
            assertNotNull(rig.eggs.duskFx)
            assertNull("only one transition at a time", rig.eggs.ladybug)
            assertEquals(rig.list.items.size, rig.store.value.list(rig.listId)!!.items.size)
        } finally {
            rig.close()
        }
    }
}

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
    private val sevenTaps: EngineRig.() -> Unit = {
        repeat(EggRules.SUN_TAPS) {
            clock.advance(300)
            engine.onSunTap()
        }
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
            rig.sevenTaps()
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

    @Test
    fun theLabFiresWithEggsOff() {
        val rig = EngineRig(settings = AppSettings(eggs = false), forceEggs = true)
        try {
            val e = rig.engine
            e.onListShown(rig.listId)
            assertTrue(e.interceptText("kirakira"))
            assertNotNull(rig.eggs.kirakiraId)
            rig.sevenTaps()
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
            rig.sevenTaps()
            assertNotNull(rig.eggs.duskFx)
            assertNull("only one transition at a time", rig.eggs.ladybug)
            assertEquals(rig.list.items.size, rig.store.value.list(rig.listId)!!.items.size)
        } finally {
            rig.close()
        }
    }
}

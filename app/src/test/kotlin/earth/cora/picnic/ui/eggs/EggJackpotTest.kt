package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.AudioClip
import earth.cora.picnic.core.assistant.CallMeta
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
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.list
import earth.cora.picnic.icons.AppIconRepository
import earth.cora.picnic.icons.BespokeIconCache
import earth.cora.picnic.icons.IconQueueTuning
import earth.cora.picnic.icons.LiveDrawJackpot
import earth.cora.picnic.icons.LiveSheet
import earth.cora.picnic.icons.ProcessedSticker
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Item 252 (egg 13): a live drawing for an item with no catalog fit (APPROXIMATE) comes out gold 1 time in 100 on the
 * injected Random: its capsule draws gold while it waits, then wobbles twice, pops ("pon!") and JACK / POT! slams beside
 * it and holds 1.8 s; the sticker is saved JACKPOT (holo everywhere). Driven through the real icon repository's hook.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggJackpotTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private fun approximate(id: String, name: String = "Gochujang") =
        GroceryItem(id = id, name = name, icon = "condiments_generic", iconFit = IconFit.APPROXIMATE, addedAt = PracticeFixture.NOW, source = ItemSource.TYPED)

    @Test
    fun oneIn100ForDrawingsWithNoCatalogFitOnly() {
        val seed = 100
        val e = EngineRig(random = Random(seed))
        try {
            val reference = Random(seed)
            var jackpots = 0
            repeat(3_000) { i ->
                val item = approximate("a$i")
                e.engine.onIconDrawing(item)
                val gold = reference.nextInt(EggRules.JACKPOT_ODDS) == 0
                assertEquals("the capsule is gold while it draws ($i)", gold, item.id in e.eggs.goldCapsules)
                val rarity = e.engine.onIconDrawn(item)
                assertEquals(if (gold) Rarity.JACKPOT else Rarity.NONE, rarity)
                assertFalse(item.id in e.eggs.goldCapsules)
                if (gold) jackpots++
            }
            assertTrue("about 30 in 3000: $jackpots", jackpots in 15..45)
            // An item the catalog fits never rolls (and draws nothing from the Random).
            val exact = approximate("x").copy(iconFit = IconFit.EXACT, icon = "lemon")
            e.engine.onIconDrawing(exact)
            assertEquals(Rarity.NONE, e.engine.onIconDrawn(exact))
            val next = approximate("after")
            e.engine.onIconDrawing(next)
            assertEquals(reference.nextInt(EggRules.JACKPOT_ODDS) == 0, next.id in e.eggs.goldCapsules)
            // A drawing that fails leaves no gold capsule behind.
            e.engine.forceJackpotNext = true
            val failed = approximate("fails")
            e.engine.onIconDrawing(failed)
            assertTrue(failed.id in e.eggs.goldCapsules)
            e.engine.onIconDrawFailed(failed)
            assertFalse(failed.id in e.eggs.goldCapsules)
        } finally {
            e.close()
        }
    }

    // ---- Through AppIconRepository (WP1) and its LiveDrawJackpot hook. ----

    private class Decider : IconDecider {
        override suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> = items.associate { it.id to IconDecision.NONE }
    }

    private class Drawer(val fail: Boolean) : GroceryAssistant {
        var drawn = 0
        override suspend fun drawIcon(request: IconDrawRequest): DrawnIcon {
            drawn++
            if (fail) throw java.io.IOException("offline")
            return DrawnIcon(byteArrayOf(9, 9, 9), meta = CallMeta(model = "test", requestId = null, latencyMs = 1))
        }
        override suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult = error("unused")
        override suspend fun transcribe(clip: AudioClip, keywords: List<String>): Transcript = error("unused")
        override suspend fun recategorize(items: List<NamedItem>, aisles: List<Aisle>, corrections: List<Correction>): Map<String, String?> = error("unused")
        override suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind> = error("unused")
        override suspend fun testConnection(): ConnectionCheck = error("unused")
    }

    private fun jackpotOf(engine: SurpriseEngine) = object : LiveDrawJackpot {
        override fun drawing(item: GroceryItem) = engine.onIconDrawing(item)
        override fun drawn(item: GroceryItem): Rarity = engine.onIconDrawn(item)
        override fun failed(item: GroceryItem) = engine.onIconDrawFailed(item)
    }

    @Test
    fun aLiveDrawingComesOutAJackpotAndIsSaved() = runTest {
        // A seed whose first roll is the 1 in 100.
        val seed = (0..100_000).first { Random(it).nextInt(EggRules.JACKPOT_ODDS) == 0 }
        val start = PracticeFixture.state().addItems(PracticeFixture.LIST_ID, listOf(approximate("g1")), PracticeFixture.NOW).first
        val e = EngineRig(state = start, random = Random(seed))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val drawer = Drawer(fail = false)
        try {
            val repo = AppIconRepository(
                store = e.store,
                settings = InMemorySettingsRepository(AppSettings(drawIcons = true), "sk-test-token-123456"),
                assistant = drawer,
                decider = Decider(),
                catalog = IconCatalog.default,
                scope = scope,
                clock = { PracticeFixture.NOW + testScheduler.currentTime },
                cache = BespokeIconCache(tmp.root, dispatcher),
                bundledKeys = { emptySet() },
                sheets = { _, _, _, _ -> LiveSheet(byteArrayOf(1), byteArrayOf(2), listOf("jam"), listOf(900.0)) },
                processor = { raw, _, _ -> ProcessedSticker(raw + byteArrayOf(0), "M10 10L50 10L50 50Z") },
                albumRefs = { emptySet() },
                onIconReplaced = {},
                tuning = IconQueueTuning(),
                compute = dispatcher,
                io = dispatcher,
                jackpot = jackpotOf(e.engine),
            )
            repo.submit(listOf(e.list.items.single { it.id == "g1" }))
            testScheduler.advanceUntilIdle()
            assertEquals(1, drawer.drawn)
            val g = e.list.items.single { it.id == "g1" }
            assertNotNull("the live sticker is attached", g.customIconId)
            assertEquals("saved as a jackpot (holo everywhere)", Rarity.JACKPOT, g.rarity)
            assertEquals("JACK / POT! plays at its row", "g1", e.eggs.jackpot?.itemId)
            assertFalse(e.eggs.goldCapsules.contains("g1"))
        } finally {
            scope.cancel()
            e.close()
        }
    }

    @Test
    fun aFailedDrawingLeavesNoGold() = runTest {
        val start = PracticeFixture.state().addItems(PracticeFixture.LIST_ID, listOf(approximate("g2", "Doenjang")), PracticeFixture.NOW).first
        val e = EngineRig(state = start)
        e.engine.forceJackpotNext = true
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val repo = AppIconRepository(
                store = e.store,
                settings = InMemorySettingsRepository(AppSettings(drawIcons = true), "sk-test-token-123456"),
                assistant = Drawer(fail = true),
                decider = Decider(),
                catalog = IconCatalog.default,
                scope = scope,
                clock = { PracticeFixture.NOW + testScheduler.currentTime },
                cache = BespokeIconCache(tmp.root, dispatcher),
                bundledKeys = { emptySet() },
                sheets = { _, _, _, _ -> LiveSheet(byteArrayOf(1), byteArrayOf(2), listOf("jam"), listOf(900.0)) },
                processor = { raw, _, _ -> ProcessedSticker(raw, "M0 0Z") },
                albumRefs = { emptySet() },
                onIconReplaced = {},
                tuning = IconQueueTuning(retryDelayMs = 1),
                compute = dispatcher,
                io = dispatcher,
                jackpot = jackpotOf(e.engine),
            )
            repo.submit(listOf(e.list.items.single { it.id == "g2" }))
            testScheduler.advanceUntilIdle()
            val g = e.list.items.single { it.id == "g2" }
            assertNull(g.customIconId)
            assertEquals(Rarity.NONE, g.rarity)
            assertTrue("no gold capsule left behind", e.eggs.goldCapsules.isEmpty())
            assertNull(e.eggs.jackpot)
        } finally {
            scope.cancel()
            e.close()
        }
    }

    // ---- On screen. ----

    @Test
    fun theRevealOnTheList() {
        val r = EggRig(compose).start()
        val t0 = r.fire(13)
        val row = "baking-powder"
        assertTrue("its capsule is gold while it draws", r.eggs.goldCapsule(row))
        // The sticker arrives 1.2 s later.
        r.advanceTo(t0 + 1_250)
        val fx = r.eggs.jackpot
        assertNotNull(fx)
        val arrived = r.now
        assertEquals(Rarity.JACKPOT, r.list.items.single { it.id == row }.rarity)
        assertFalse(r.eggs.goldCapsule(row))
        // Two wobbles (600 ms), then the pop: "pon!".
        r.advanceTo(arrived + JackpotMotion.WOBBLE_MS + 100)
        assertTrue(r.feedback.times(Cue.CAPSULE_POP).any { it in arrived + JackpotMotion.WOBBLE_MS - 40..arrived + JackpotMotion.WOBBLE_MS + 60 })
        // JACK / POT! holds 1.8 s beside it (over its check column, never over its name), then fades.
        r.advanceTo(arrived + JackpotMotion.SLAM_AT + JackpotMotion.SLAM_MS + JackpotMotion.HOLD_MS - 50)
        val held = r.probe.frames.filter { it.tag == "jackpot" && it.time in JackpotMotion.SLAM_AT + JackpotMotion.SLAM_MS until JackpotMotion.SLAM_AT + JackpotMotion.SLAM_MS + JackpotMotion.HOLD_MS }
        assertTrue("held for 1.8 s", held.maxOf { it.time } - held.minOf { it.time } >= JackpotMotion.HOLD_MS - 100)
        val name = r.anchor(EggAnchors.TEXT_PREFIX + "name:" + row)
        held.forEach { assertFalse("never over the item's name", it.bounds.deflate(6f).overlaps(name)) }
        r.advanceTo(arrived + JackpotMotion.DURATION + 100)
        assertNull(r.eggs.jackpot)
        // The "jackpot!" chip on its row.
        assertTrue(r.anchors.isLaidOut(EggAnchors.TEXT_PREFIX + "chip:" + row))
        assertTrue(r.anchors.isLaidOut(AnchorRegistry.rowKey(row)))
    }

    @Test
    fun reducedMotionFadesAndHolds() {
        val r = EggRig(compose, reduced = true).start()
        val t0 = r.fire(13)
        r.advanceTo(t0 + 1_250)
        val fx = r.eggs.jackpot
        assertNotNull(fx)
        assertTrue(fx!!.reduced)
        r.advanceTo(t0 + 1_250 + JackpotMotion.DURATION + 100)
        assertNull(r.eggs.jackpot)
        assertEquals(Rarity.JACKPOT, r.list.items.single { it.id == "baking-powder" }.rarity)
    }
}

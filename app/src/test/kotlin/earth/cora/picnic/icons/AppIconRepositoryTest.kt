package earth.cora.picnic.icons

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
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.setItemIcon
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.InMemorySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneId

/** The device side of the icon cascade (docs/ARCHITECTURE.md §8.3) with fakes for the network. */
class AppIconRepositoryTest {
    private companion object {
        const val BASE_TIME = 1_759_330_000_000L
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private val catalog = IconCatalog.default

    /** The repository's app scope per test: its loops never end on their own. */
    private val jobs = mutableListOf<Job>()

    @After
    fun cancelScopes() = jobs.forEach { it.cancel() }

    private class FakeDecider(var answer: (List<NamedItem>) -> Map<String, IconDecision>) : IconDecider {
        val calls = mutableListOf<List<NamedItem>>()
        override suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> {
            calls += items
            return answer(items)
        }
    }

    private class FakeAssistant(var draw: (IconDrawRequest) -> DrawnIcon) : GroceryAssistant {
        val drawn = mutableListOf<String>()
        override suspend fun drawIcon(request: IconDrawRequest): DrawnIcon {
            drawn += request.itemName
            return draw(request)
        }
        override suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult = error("unused")
        override suspend fun transcribe(clip: AudioClip, keywords: List<String>): Transcript = error("unused")
        override suspend fun recategorize(items: List<NamedItem>, aisles: List<Aisle>, corrections: List<Correction>): Map<String, String?> = error("unused")
        override suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind> = error("unused")
        override suspend fun testConnection(): ConnectionCheck = error("unused")
    }

    private val meta = CallMeta(model = "gpt-image-2.5-flare", requestId = null, latencyMs = 1)
    private fun drawnOk(@Suppress("UNUSED_PARAMETER") r: IconDrawRequest) = DrawnIcon(byteArrayOf(9, 9, 9), meta = meta)

    private fun item(id: String, name: String, fit: IconFit, icon: String = if (fit == IconFit.PENDING) IconCatalog.DEFAULT_ICON else "condiments_generic") =
        GroceryItem(id = id, name = name, icon = icon, iconFit = fit, addedAt = 1L)

    private fun state(vararg items: GroceryItem) =
        AppState(lists = listOf(GroceryList(id = "L", title = "Groceries", createdAt = 0L, items = items.toList())), currentListId = "L")

    private class Harness(
        val repo: AppIconRepository,
        val store: InMemoryStateStore,
        val settings: InMemorySettingsRepository,
        val decider: FakeDecider,
        val assistant: FakeAssistant,
        val cache: BespokeIconCache,
    )

    private fun TestScope.harness(
        initial: AppState,
        decide: (List<NamedItem>) -> Map<String, IconDecision> = { items -> items.associate { it.id to IconDecision.NONE } },
        draw: (IconDrawRequest) -> DrawnIcon = ::drawnOk,
        settings: AppSettings = AppSettings(drawIcons = true),
        token: String? = "sk-test-token-123456",
        tuning: IconQueueTuning = IconQueueTuning(),
        styleRef: ByteArray? = byteArrayOf(1),
    ): Harness {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryStateStore(initial)
        val repoSettings = InMemorySettingsRepository(settings, token)
        val decider = FakeDecider(decide)
        val assistant = FakeAssistant(draw)
        val cache = BespokeIconCache(tmp.root, dispatcher)
        val repo = AppIconRepository(
            store = store,
            settings = repoSettings,
            assistant = assistant,
            decider = decider,
            catalog = catalog,
            scope = CoroutineScope(SupervisorJob().also { jobs += it } + dispatcher),
            clock = { BASE_TIME + testScheduler.currentTime },
            cache = cache,
            bundledKeys = { setOf("lemon") },
            styleReference = { styleRef },
            processor = { raw -> raw + byteArrayOf(0) },
            onIconReplaced = {},
            tuning = tuning,
            compute = dispatcher,
            io = dispatcher,
        )
        return Harness(repo, store, repoSettings, decider, assistant, cache)
    }

    /** Runs the repository's queues (its own foreground scope on the test scheduler) to quiescence. */
    private fun TestScope.settle() = testScheduler.advanceUntilIdle()

    private suspend fun Harness.item(id: String): GroceryItem = store.current().lists.flatMap { it.items }.first { it.id == id }

    @Test
    fun pendingNamesAreBatchedDecidedAndApplied() = runTest {
        val a = item("1", "Oat milk", IconFit.PENDING)
        val b = item("2", "Lemons", IconFit.PENDING)
        val h = harness(state(a, b), decide = { items ->
            items.associate { it.id to if (it.id == "oat milk") IconDecision("plant_milk", "dairy") else IconDecision("lemon", "fruit") }
        })
        h.repo.submit(listOf(a))
        h.repo.submit(listOf(b)) // inside the debounce window: same call
        testScheduler.runCurrent()
        assertEquals(setOf("oat milk", "lemon"), h.repo.busy.value)
        settle()

        assertEquals(1, h.decider.calls.size)
        assertEquals(setOf("oat milk", "lemon"), h.decider.calls[0].map { it.id }.toSet())
        assertEquals("plant_milk", h.item("1").icon)
        assertEquals(IconFit.EXACT, h.item("1").iconFit)
        assertEquals("lemon", h.item("2").icon)
        assertTrue(h.repo.busy.value.isEmpty())
        assertTrue(h.assistant.drawn.isEmpty())
    }

    @Test
    fun noneBecomesApproximateThenDrawnCachedAndReused() = runTest {
        val a = item("1", "Gochujang", IconFit.PENDING)
        val h = harness(state(a), decide = { items -> items.associate { it.id to IconDecision(null, "condiments") } })
        h.repo.submit(listOf(a))
        settle()

        val drawnItem = h.item("1")
        assertEquals("condiments_generic", drawnItem.icon)
        assertEquals(IconFit.APPROXIMATE, drawnItem.iconFit)
        val id = drawnItem.customIconId
        assertNotNull(id)
        id!!
        assertEquals(listOf("Gochujang"), h.assistant.drawn)
        assertTrue(h.cache.fileFor(id).isFile)
        assertEquals(id, h.cache.lookup("gochujang"))

        // A new item with the same name (another list, later) reuses the cached icon: no API calls.
        val again = item("2", "gochujang", IconFit.APPROXIMATE)
        h.store.update { s -> s.copy(lists = s.lists.map { it.copy(items = it.items + again) }) }
        h.repo.submit(listOf(again))
        settle()
        assertEquals(id, h.item("2").customIconId)
        assertEquals(1, h.assistant.drawn.size)
        assertEquals(1, h.decider.calls.size)
    }

    @Test
    fun drawingRespectsTheSettingAndNeedsAToken() = runTest {
        val a = item("1", "Kimchi", IconFit.APPROXIMATE)
        val off = harness(state(a), settings = AppSettings(drawIcons = false))
        off.repo.submit(listOf(a))
        settle()
        assertTrue(off.assistant.drawn.isEmpty())
        assertNull(off.item("1").customIconId)
    }

    @Test
    fun noTokenNoDecisionsEither() = runTest {
        val a = item("1", "Spatula", IconFit.PENDING)
        val h = harness(state(a), token = null)
        h.repo.submit(listOf(a))
        testScheduler.runCurrent()
        assertTrue("no sparkle without a token", h.repo.busy.value.isEmpty())
        settle()
        assertTrue(h.decider.calls.isEmpty())
        assertEquals(IconFit.PENDING, h.item("1").iconFit)

        h.settings.setToken("sk-test-token-123456")
        h.repo.resumePending()
        settle()
        assertEquals(1, h.decider.calls.size)
    }

    @Test
    fun noTokenNoDrawing() = runTest {
        val a = item("1", "Kimchi", IconFit.APPROXIMATE)
        val h = harness(state(a), token = null)
        h.repo.submit(listOf(a))
        settle()
        assertTrue(h.assistant.drawn.isEmpty())
    }

    @Test
    fun moderationRefusalBlocksTheName() = runTest {
        val a = item("1", "Mystery item", IconFit.APPROXIMATE)
        val h = harness(state(a), draw = { throw LlmError.Refusal("moderation_blocked") })
        h.repo.submit(listOf(a))
        settle()
        assertTrue(h.cache.isBlocked("mystery item"))
        assertNull(h.item("1").customIconId)
        h.repo.submit(listOf(h.item("1")))
        settle()
        assertEquals(1, h.assistant.drawn.size)
        assertTrue(h.repo.busy.value.isEmpty())
    }

    @Test
    fun transientErrorsAreRetriedOnce() = runTest {
        var calls = 0
        val a = item("1", "Za'atar", IconFit.APPROXIMATE)
        val h = harness(state(a), draw = { r ->
            calls++
            if (calls == 1) throw LlmError.Network(null) else drawnOk(r)
        })
        h.repo.submit(listOf(a))
        settle()
        assertEquals(2, calls)
        assertNotNull(h.item("1").customIconId)
    }

    @Test
    fun givesUpAfterTheSingleRetry() = runTest {
        val a = item("1", "Za'atar", IconFit.APPROXIMATE)
        val h = harness(state(a), draw = { throw LlmError.Timeout() })
        h.repo.submit(listOf(a))
        settle()
        assertEquals(2, h.assistant.drawn.size)
        assertNull(h.item("1").customIconId)
        assertTrue(h.repo.busy.value.isEmpty())
    }

    @Test
    fun dailyCapLimitsAutomaticDrawings() = runTest {
        val a = item("1", "Gochujang", IconFit.APPROXIMATE)
        val b = item("2", "Doenjang", IconFit.APPROXIMATE)
        val h = harness(state(a, b), tuning = IconQueueTuning(dailyCap = 1))
        h.repo.submit(listOf(a, b))
        settle()
        assertEquals(1, h.assistant.drawn.size)
    }

    @Test
    fun deciderFailureLeavesItemsPending() = runTest {
        val a = item("1", "Spatula", IconFit.PENDING)
        val h = harness(state(a), decide = { throw LlmError.Network(null) })
        h.repo.submit(listOf(a))
        settle()
        assertEquals(IconFit.PENDING, h.item("1").iconFit)
        assertTrue(h.repo.busy.value.isEmpty())

        // Next start: resumePending() retries it.
        h.decider.answer = { items -> items.associate { it.id to IconDecision(null, "household") } }
        h.repo.resumePending()
        settle()
        assertEquals(IconFit.APPROXIMATE, h.item("1").iconFit)
        assertEquals("household_generic", h.item("1").icon)
    }

    @Test
    fun batchesHoldAtMostFortyNames() = runTest {
        val items = (1..95).map { item("$it", "Thing number $it", IconFit.PENDING) }
        val h = harness(state(*items.toTypedArray()), decide = { batch -> batch.associate { it.id to IconDecision("lemon", "fruit") } })
        h.repo.submit(items)
        settle()
        assertTrue(h.decider.calls.all { it.size <= 40 })
        assertEquals(95, h.decider.calls.sumOf { it.size })
    }

    @Test
    fun redrawOverwritesAndDeletesTheOldFile() = runTest {
        val a = item("1", "Gochujang", IconFit.APPROXIMATE)
        var n = 0
        val h = harness(state(a), draw = { DrawnIcon(byteArrayOf((++n).toByte()), meta = meta) })
        h.repo.submit(listOf(a))
        settle()
        val first = h.item("1").customIconId!!

        testScheduler.advanceTimeBy(5_000) // a later second: a new id
        h.repo.redraw("Gochujang")
        settle()
        val second = h.item("1").customIconId!!
        assertFalse(first == second)
        assertFalse(h.cache.fileFor(first).exists())
        assertTrue(h.cache.fileFor(second).exists())
        assertEquals(second, h.cache.lookup("gochujang"))
    }

    @Test
    fun userChoiceDuringDrawingWins() = runTest {
        val a = item("1", "Gochujang", IconFit.APPROXIMATE)
        lateinit var h: Harness
        h = harness(state(a), draw = { r ->
            // While the image model is busy, the user picks a catalog icon in the edit sheet.
            runBlocking { h.store.update { it.setItemIcon("L", "1", "hot_sauce", now = 2L) } }
            drawnOk(r)
        })
        h.repo.submit(listOf(a))
        settle()
        assertEquals("hot_sauce", h.item("1").icon)
        assertNull(h.item("1").customIconId)
        assertNull(h.cache.lookup("gochujang"))
    }

    @Test
    fun forgetBespokeStopsReuse() = runTest {
        val a = item("1", "Gochujang", IconFit.APPROXIMATE)
        val h = harness(state(a), settings = AppSettings(drawIcons = true))
        h.repo.submit(listOf(a))
        settle()
        assertNotNull(h.cache.lookup("gochujang"))
        h.repo.forgetBespoke("Gochujang")
        settle()
        assertNull(h.cache.lookup("gochujang"))
    }

    @Test
    fun missingStyleReferenceDisablesDrawingQuietly() = runTest {
        val a = item("1", "Gochujang", IconFit.APPROXIMATE)
        val h = harness(state(a), styleRef = null, tuning = IconQueueTuning(dailyCap = 1))
        h.repo.submit(listOf(a))
        settle()
        assertTrue(h.assistant.drawn.isEmpty())
        assertTrue(h.repo.busy.value.isEmpty())
        val today = Instant.ofEpochMilli(BASE_TIME).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        assertTrue("budget refunded", h.cache.tryConsumeDaily(today, cap = 1))
    }

    @Test
    fun exactItemsNeedNothing() = runTest {
        val a = item("1", "Lemons", IconFit.EXACT, icon = "lemon")
        val h = harness(state(a))
        h.repo.submit(listOf(a))
        settle()
        assertTrue(h.decider.calls.isEmpty())
        assertTrue(h.assistant.drawn.isEmpty())
        assertTrue(h.repo.isBundled("lemon"))
        assertFalse(h.repo.isBundled("kimchi"))
    }
}

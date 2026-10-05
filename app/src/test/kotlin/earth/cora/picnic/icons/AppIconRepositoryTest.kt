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
        val requests = mutableListOf<IconDrawRequest>()
        override suspend fun drawIcon(request: IconDrawRequest): DrawnIcon {
            drawn += request.itemName
            requests += request
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

    /** One SheetSource call: what the repository asked the neighbour sheet for. */
    private data class SheetCall(val approximateKey: String?, val category: String?, val families: List<String>, val text: String)

    private class Harness(
        val repo: AppIconRepository,
        val store: InMemoryStateStore,
        val settings: InMemorySettingsRepository,
        val decider: FakeDecider,
        val assistant: FakeAssistant,
        val cache: BespokeIconCache,
        val sheetCalls: List<SheetCall>,
        val processed: List<Pair<List<String>, Double>>,
        val replaced: List<String>,
    )

    private val sheetPng = byteArrayOf(0x50, 0x4E, 0x47)
    private val maskPng = byteArrayOf(0x4D)

    private fun TestScope.harness(
        initial: AppState,
        decide: (List<NamedItem>) -> Map<String, IconDecision> = { items -> items.associate { it.id to IconDecision.NONE } },
        draw: (IconDrawRequest) -> DrawnIcon = ::drawnOk,
        settings: AppSettings = AppSettings(drawIcons = true),
        token: String? = "sk-test-token-123456",
        tuning: IconQueueTuning = IconQueueTuning(),
        maskBundled: Boolean = true,
        albumIds: () -> Set<String> = { emptySet() },
        root: java.io.File = tmp.root,
    ): Harness {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryStateStore(initial)
        val repoSettings = InMemorySettingsRepository(settings, token)
        val decider = FakeDecider(decide)
        val assistant = FakeAssistant(draw)
        val cache = BespokeIconCache(root, dispatcher)
        val sheetCalls = mutableListOf<SheetCall>()
        val processed = mutableListOf<Pair<List<String>, Double>>()
        val replaced = mutableListOf<String>()
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
            sheets = { approx, category, families, text ->
                sheetCalls += SheetCall(approx, category, families, text)
                if (maskBundled) LiveSheet(sheetPng, maskPng, listOf("jam", "pasta_sauce", "peanut_butter"), listOf(900.0, 800.0, 700.0)) else null
            },
            processor = { raw, families, area ->
                processed += families to area
                ProcessedSticker(raw + byteArrayOf(0), "M10 10L50 10L50 50Z")
            },
            albumRefs = { albumIds() },
            onIconReplaced = { replaced += it },
            tuning = tuning,
            compute = dispatcher,
            io = dispatcher,
        )
        return Harness(repo, store, repoSettings, decider, assistant, cache, sheetCalls, processed, replaced)
    }

    /** What the decider says about a name the catalog lacks: "none", with the drawing's brief. */
    private val kimchiBrief = IconDecision(
        icon = null,
        category = "pantry",
        look = "one squat glass jar of kimchi with a round screw lid",
        families = listOf("strawberry", "melon"),
    )

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
        val brief = IconDecision(null, "condiments", "one red tub of gochujang with a flat lid", listOf("strawberry", "cocoa"))
        val h = harness(state(a), decide = { items -> items.associate { it.id to brief } })
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
        assertEquals("M10 10L50 10L50 50Z", h.cache.readCut(id))
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
        assertFalse(h.cache.cutFileFor(first).exists())
        assertTrue(h.cache.fileFor(second).exists())
        assertEquals(second, h.cache.lookup("gochujang"))
        assertEquals(listOf(first), h.replaced)
    }

    @Test
    fun redrawKeepsAnIconTheAlbumStillShows() = runTest {
        val a = item("1", "Gochujang", IconFit.APPROXIMATE)
        val album = mutableSetOf<String>()
        val h = harness(state(a), albumIds = { album })
        h.repo.submit(listOf(a))
        settle()
        val first = h.item("1").customIconId!!
        album += first // filed in last week's sticker book

        testScheduler.advanceTimeBy(5_000)
        h.repo.redraw("Gochujang")
        settle()
        val second = h.item("1").customIconId!!
        assertFalse(first == second)
        assertTrue("the album's sticker keeps its art", h.cache.fileFor(first).isFile)
        assertTrue(h.cache.cutFileFor(first).isFile)
        assertTrue(h.replaced.isEmpty())

        // Once nothing references it, the next start's sweep may take it (it is old enough by then).
        album.clear()
        // (File times are wall-clock; the bitmap and its cut both go.)
        assertEquals(2, h.cache.sweep(setOf(second), System.currentTimeMillis() + 3_600_000L, minAgeMs = 0))
        assertFalse(h.cache.fileFor(first).exists())
    }

    @Test
    fun theDecidersBriefTravelsToTheDrawing() = runTest {
        val a = item("1", "Kimchi", IconFit.PENDING)
        val h = harness(state(a), decide = { items -> items.associate { it.id to kimchiBrief } })
        h.repo.submit(listOf(a))
        settle()

        // One decider call: the "none" answer already carried the look and the families.
        assertEquals(1, h.decider.calls.size)
        val request = h.assistant.requests.single()
        assertEquals("Kimchi", request.itemName)
        assertEquals(kimchiBrief.look, request.subject)
        assertEquals(listOf("strawberry", "melon"), request.families)
        assertTrue(request.sheet.contentEquals(sheetPng))
        assertTrue(request.mask.contentEquals(maskPng))
        // The sheet starts from the approximate key the item shows (the category generic) in the brief's colours.
        val call = h.sheetCalls.single()
        assertEquals("pantry_generic", call.approximateKey)
        assertEquals("pantry", call.category)
        assertEquals(listOf("strawberry", "melon"), call.families)
        assertTrue(call.text.contains("Kimchi") && call.text.contains("screw lid"))
        // The post-processor snaps to the same families and scales to the neighbours' mean area.
        assertEquals(listOf(listOf("strawberry", "melon") to 800.0), h.processed)
        assertNotNull(h.item("1").customIconId)
    }

    @Test
    fun anItemThatArrivedApproximateIsBriefedOnce() = runTest {
        // Ingest answered "approximate" itself: the drawing asks the decider about this one name for its brief.
        val a = item("1", "Kimchi", IconFit.APPROXIMATE, icon = "pickles")
        val h = harness(state(a), decide = { items -> items.associate { it.id to kimchiBrief } })
        h.repo.submit(listOf(a))
        settle()
        assertEquals(listOf(listOf(NamedItem("kimchi", "Kimchi"))), h.decider.calls)
        assertEquals(kimchiBrief.look, h.assistant.requests.single().subject)
        assertEquals("pickles", h.sheetCalls.single().approximateKey)
    }

    @Test
    fun withoutABriefTheDrawingUsesThePlainNameAndTheShownStickersColours() = runTest {
        val a = item("1", "Mystery jar", IconFit.APPROXIMATE, icon = "jam")
        val h = harness(state(a), decide = { throw LlmError.Network(null) })
        h.repo.submit(listOf(a))
        settle()
        val request = h.assistant.requests.single()
        assertEquals("one Mystery jar, as it is sold in a Canadian grocery store", request.subject)
        // jam's own families (StickerManifest), so the palette matches its neighbours.
        assertEquals(earth.cora.picnic.ui.theme.gen.StickerManifest.entry("jam")!!.families.take(2), request.families)
        assertNotNull(h.item("1").customIconId)
    }

    @Test
    fun legacyIconsAreRedrawnInV4AndTheAlbumKeepsTheOldArt() = runTest {
        // An icon drawn before v4: a bitmap and no .cut file, still on a list and filed in the album.
        val legacyId = "c-legacy-1"
        val a = item("1", "Gochujang", IconFit.APPROXIMATE).copy(customIconId = legacyId)
        val h = harness(state(a), decide = { items -> items.associate { it.id to kimchiBrief.copy(category = "condiments") } }, albumIds = { setOf(legacyId) })
        h.cache.put("gochujang", legacyId, byteArrayOf(7))
        assertEquals(listOf("gochujang" to legacyId), h.cache.legacyIcons())

        h.repo.resumePending()
        settle()
        val redrawn = h.item("1").customIconId!!
        assertFalse(redrawn == legacyId)
        assertEquals("M10 10L50 10L50 50Z", h.cache.readCut(redrawn))
        assertEquals(redrawn, h.cache.lookup("gochujang"))
        assertTrue(h.cache.legacyIcons().isEmpty())
        assertTrue("album keeps the legacy file", h.cache.fileFor(legacyId).isFile)
        assertEquals(listOf("Gochujang"), h.assistant.drawn)
    }

    @Test
    fun legacyRedrawsNeedTheSettingATokenAndTheDailyCap() = runTest {
        val items = (1..3).map { item("$it", "Thing $it", IconFit.APPROXIMATE).copy(customIconId = "c-old-$it") }
        val off = harness(state(*items.toTypedArray()), settings = AppSettings(drawIcons = false))
        items.forEach { off.cache.put(normalizeKey(it.name), it.customIconId!!, byteArrayOf(1)) }
        off.repo.resumePending()
        settle()
        assertTrue(off.assistant.drawn.isEmpty())

        val capped = harness(state(*items.toTypedArray()), tuning = IconQueueTuning(dailyCap = 2), root = tmp.newFolder("capped"))
        items.forEach { capped.cache.put(normalizeKey(it.name), it.customIconId!!, byteArrayOf(1)) }
        capped.repo.resumePending()
        settle()
        assertEquals(2, capped.assistant.drawn.size)
        // Unreferenced legacy files go once replaced (nothing in the album).
        assertEquals(1, items.count { capped.cache.fileFor(it.customIconId!!).isFile })
    }

    private fun normalizeKey(name: String) = earth.cora.picnic.core.model.normalizeName(name)

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
    fun missingMaskDisablesDrawingQuietly() = runTest {
        val a = item("1", "Gochujang", IconFit.APPROXIMATE)
        val h = harness(state(a), maskBundled = false, tuning = IconQueueTuning(dailyCap = 1))
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

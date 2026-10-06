package earth.cora.picnic.platform

import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.assistant.AisleSyncReport
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.ui.common.FakeAssistant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catch-up when the connection comes back (docs/DESIGN_STICKER.md §0.4, 5 October; item 352): at a cold start
 * online with a key and after each arrival of a connection it resumes the stickers, sorts every item still needing an
 * aisle (one note, "Sorted 1 item"), decides the pantry's stickers and asks a due forecast; never without a key (the
 * forecast aside), never two at once, and runs after arrivals at least a minute apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatchUpTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    private fun item(id: String, name: String, aisle: String? = null) = GroceryItem(id = id, name = name, aisleId = aisle, icon = "paper_bag", addedAt = 0)

    private val start = AppState(
        lists = listOf(
            GroceryList("l", "Saturday shop", 0, listOf(item("a", "Lemons"), item("b", "Bread", "bakery"))),
            GroceryList("m", "Big shop", 0, listOf(item("c", "Croissants"))),
        ),
        currentListId = "l",
        store = StoreProfile(aislesText = "Produce\nBakery"),
    )

    private class Rig(
        val catchUp: CatchUp,
        val network: FakeNetworkStatus,
        val store: InMemoryStateStore,
        val assistant: FakeAssistant,
        val counts: MutableMap<String, Int>,
        /** When each sort started (the test clock). */
        val sortTimes: List<Long>,
    )

    private fun TestScope.rig(
        online: Boolean = true,
        hasKey: Boolean = true,
        sortGate: CompletableDeferred<Unit>? = null,
        active: IntArray = IntArray(2),
        /** [0]: the sort's request never reaches OpenAI (a captive portal: the phone still looks online). */
        unreachable: BooleanArray = booleanArrayOf(false),
    ): Rig {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)).also { scopes += it }
        val store = InMemoryStateStore(start)
        val assistant = FakeAssistant(recategorize = { items, _ ->
            if (unreachable[0]) throw LlmError.Network(null, beforeConnect = true)
            items.associate { it.id to when (it.name) { "Lemons" -> "produce"; "Croissants" -> "bakery"; else -> null } }
        })
        val sync = AisleSync(store, assistant, IconCatalog.default.baskets) { 0L }
        val network = FakeNetworkStatus(online)
        val counts = mutableMapOf("resume" to 0, "sort" to 0, "pantry" to 0, "weather" to 0)
        val sortTimes = mutableListOf<Long>()
        val catchUp = CatchUp(
            network = network,
            hasKey = { hasKey },
            resumeStickers = { counts["resume"] = counts.getValue("resume") + 1 },
            sortPending = {
                counts["sort"] = counts.getValue("sort") + 1
                sortTimes += testScheduler.currentTime
                active[0]++
                active[1] = maxOf(active[1], active[0])
                try {
                    sortGate?.await()
                    sync.sortPending()
                } finally {
                    active[0]--
                }
            },
            pantryStickers = { counts["pantry"] = counts.getValue("pantry") + 1 },
            weather = { counts["weather"] = counts.getValue("weather") + 1 },
            scope = scope,
            clock = { testScheduler.currentTime },
        )
        return Rig(catchUp, network, store, assistant, counts, sortTimes)
    }

    @Test
    fun aColdStartOnlineWithAKeySortsWhatWaitedAndResumesTheStickers() = runTest {
        val r = rig()
        r.catchUp.start()
        advanceUntilIdle()
        assertEquals(1, r.counts["resume"])
        assertEquals(1, r.counts["sort"])
        assertEquals(1, r.counts["pantry"])
        assertEquals("the list opening asks the forecast at a cold start", 0, r.counts["weather"])
        val s = r.store.current()
        assertEquals("produce", s.findItem("a")!!.aisleId)
        assertEquals("every list", "bakery", s.findItem("c")!!.aisleId)
        assertEquals("one call for both lists", 1, r.assistant.recategorized.size)
        val note = r.catchUp.note.value!!
        assertEquals(2, note.sorted)
        assertEquals(0, note.left)
        r.catchUp.shown(note.id)
        assertNull("shown once", r.catchUp.note.value)
    }

    @Test
    fun offlineAtTheColdStartItWaitsForTheConnection() = runTest {
        val r = rig(online = false)
        r.catchUp.start()
        advanceUntilIdle()
        assertEquals("the cascade resumes (it asks nothing offline)", 1, r.counts["resume"])
        assertEquals(0, r.counts["sort"])
        assertTrue(r.assistant.recategorized.isEmpty())

        r.network.goOnline()
        runCurrent()
        advanceTimeBy(CatchUp.DEBOUNCE_MS - 100)
        runCurrent()
        assertEquals("debounced: not yet", 0, r.counts["sort"])
        advanceUntilIdle()
        assertEquals(1, r.counts["sort"])
        assertEquals(2, r.counts["resume"])
        assertEquals(1, r.counts["weather"])
        assertEquals("produce", r.store.current().findItem("a")!!.aisleId)
        assertEquals(2, r.catchUp.note.value!!.sorted)
    }

    @Test
    fun withoutAKeyNothingIsSortedDecidedOrDrawn() = runTest {
        val r = rig(hasKey = false)
        r.catchUp.start()
        advanceUntilIdle()
        r.network.goOffline()
        r.network.goOnline()
        advanceUntilIdle()
        assertEquals("only the cold start's resume (sweep, nothing asked)", 1, r.counts["resume"])
        assertEquals(0, r.counts["sort"])
        assertEquals(0, r.counts["pantry"])
        assertEquals("the forecast needs no key", 1, r.counts["weather"])
        assertTrue(r.assistant.recategorized.isEmpty())
        assertNull(r.catchUp.note.value)
    }

    @Test
    fun neverTwoAtOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val active = IntArray(2)
        val r = rig(sortGate = gate, active = active)
        r.catchUp.start()
        runCurrent()
        assertEquals("the cold start's run is sorting", 1, active[0])
        // The connection changes twice while it waits: one more run, after it, never beside it.
        r.network.switchNetwork()
        advanceTimeBy(CatchUp.DEBOUNCE_MS + 10)
        r.network.switchNetwork()
        advanceTimeBy(CatchUp.MIN_GAP_MS * 3)
        assertEquals(1, r.counts["sort"])
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("never more than one at a time", 1, active[1])
        assertEquals("the arrivals folded into the runs after it", 3, r.counts["sort"])
    }

    @Test
    fun runsAfterArrivalsAreAtLeastAMinuteApart() = runTest {
        val r = rig()
        r.catchUp.start()
        advanceUntilIdle()
        assertEquals(1, r.counts["sort"])
        val first = testScheduler.currentTime
        r.network.goOffline()
        r.network.goOnline()
        advanceTimeBy(CatchUp.DEBOUNCE_MS + 10)
        runCurrent()
        assertEquals("a flapping connection waits for the gap", 1, r.counts["sort"])
        advanceUntilIdle()
        assertEquals(2, r.counts["sort"])
        assertTrue(testScheduler.currentTime - first >= CatchUp.MIN_GAP_MS)
    }

    /**
     * The phone looked online but an add's sorting never reached OpenAI (the add sheet's [CatchUp.request]): no arrival
     * comes, and the catch-up sorts it a minute later all the same.
     */
    @Test
    fun aRequestSortsWhatWaitedWithNoArrival() = runTest {
        val r = rig()
        r.catchUp.start()
        advanceUntilIdle()
        r.catchUp.note.value?.let { r.catchUp.shown(it.id) }
        assertEquals(1, r.counts["sort"])
        // An as-typed add whose sort could not get through.
        r.store.update { s -> s.copy(lists = s.lists.map { if (it.id == "l") it.copy(items = it.items + item("d", "Croissants")) else it }) }
        r.catchUp.request()
        advanceTimeBy(CatchUp.RETRY_BACKOFF_MS[0] - 100)
        runCurrent()
        assertEquals("not yet", 1, r.counts["sort"])
        advanceUntilIdle()
        assertEquals(2, r.counts["sort"])
        assertEquals("bakery", r.store.current().findItem("d")!!.aisleId)
        assertEquals(1, r.catchUp.note.value!!.sorted)
        assertEquals("a retry leaves the forecast alone", 0, r.counts["weather"])
    }

    /**
     * Sorting that keeps failing on the way while the phone looks online is tried again 1, 5 and 15 minutes later, then
     * no more until something new (here an arrival); a request while the retries wait adds nothing.
     */
    @Test
    fun retriesBackOffThenStopUntilTheNextArrival() = runTest {
        val unreachable = booleanArrayOf(true)
        val r = rig(unreachable = unreachable)
        r.catchUp.start()
        runCurrent()
        assertEquals(1, r.counts["sort"])
        r.catchUp.request()
        r.catchUp.request()
        advanceUntilIdle()
        assertEquals(listOf(0L, 60_000L, 360_000L, 1_260_000L), r.sortTimes)
        advanceTimeBy(24 * 60 * 60 * 1000L)
        runCurrent()
        assertEquals("then it stops", 4, r.counts["sort"])
        assertNull(r.catchUp.note.value)

        unreachable[0] = false
        r.network.switchNetwork()
        advanceUntilIdle()
        assertEquals(5, r.counts["sort"])
        assertEquals("produce", r.store.current().findItem("a")!!.aisleId)
        assertEquals(2, r.catchUp.note.value!!.sorted)
    }

    @Test
    fun aFailedSortIsReportedNotThrown() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)).also { scopes += it }
        val catchUp = CatchUp(
            network = FakeNetworkStatus(),
            hasKey = { true },
            resumeStickers = {},
            sortPending = { AisleSyncReport(false, 0, 2, earth.cora.picnic.core.llm.LlmError.Offline()) },
            pantryStickers = { error("pantry broke") },
            weather = {},
            scope = scope,
            clock = { testScheduler.currentTime },
        )
        assertTrue(catchUp.run(coldStart = true))
        assertNull("nothing sorted: no note", catchUp.note.value)
    }
}

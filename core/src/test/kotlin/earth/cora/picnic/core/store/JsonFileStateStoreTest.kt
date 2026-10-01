package earth.cora.picnic.core.store

import earth.cora.picnic.core.CoreJson
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.STATE_SCHEMA_VERSION
import earth.cora.picnic.core.model.StateOps
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.renameList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JsonFileStateStoreTest {
    private val dir: File = Files.createTempDirectory("picnic-store").toFile()
    private val file = File(dir, "state.json")
    private var initialCalls = 0

    private fun initial(): AppState {
        initialCalls++
        return StateOps.initialState("fresh", 1)
    }

    private fun store(f: File = file) = JsonFileStateStore(f, ::initial, Dispatchers.Unconfined)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val milk = GroceryItem(id = "m", name = "Milk", quantity = "4 L bag", aisleId = "dairy-eggs", icon = "milk_bag", addedAt = 5)

    @Test
    fun missingFileStartsFreshWithoutWriting() = runTest {
        val s = store()
        assertEquals("fresh", s.current().currentListId)
        assertFalse(file.exists(), "nothing is written until the first update")
        assertEquals(1, initialCalls)
        s.current()
        assertEquals(1, initialCalls, "loaded once")
    }

    @Test
    fun roundTripThroughDisk() = runTest {
        val s = store()
        val saved = s.update { it.addItems("fresh", listOf(milk)) }
        assertTrue(file.exists())
        assertFalse(File(dir, "state.json.tmp").exists(), "temp file was moved into place")
        val reloaded = store().current()
        assertEquals(saved, reloaded)
        assertEquals("Milk", reloaded.currentList().items.single().name)
        val json = CoreJson.json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(JsonPrimitive(STATE_SCHEMA_VERSION), json["version"])
    }

    @Test
    fun sameInstanceTransformWritesNothing() = runTest {
        val s = store()
        val before = s.current()
        assertSame(before, s.update { it })
        assertFalse(file.exists())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun statesEmitsCurrentThenEachCommit() = runTest {
        val s = store()
        val seen = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            s.states.take(3).toList().forEach { seen += it.currentList().title }
        }
        yield()
        s.update { it.renameList("fresh", "One") }
        yield()
        s.update { it.renameList("fresh", "Two") }
        yield()
        job.join()
        assertEquals(listOf("Groceries", "One", "Two"), seen)
        assertEquals("Two", s.states.first().currentList().title)
    }

    @Test
    fun corruptFileIsMovedAsideAndAFreshStateIsUsed() = runTest {
        file.writeText("{ this is not json")
        val s = store()
        assertEquals("fresh", s.current().currentListId)
        assertFalse(file.exists())
        val aside = dir.listFiles()!!.single { it.name.startsWith("state.json.corrupt-") }
        assertEquals("{ this is not json", aside.readText())
    }

    @Test
    fun wrongShapeCountsAsCorrupt() = runTest {
        file.writeText("""{"lists": "nope", "currentListId": 3}""")
        assertEquals("fresh", store().current().currentListId)
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("state.json.corrupt-") })
    }

    @Test
    fun minimalOldFileDecodesWithDefaultsAndIsRepaired() = runTest {
        file.writeText("""{"lists":[{"id":"l","title":"G","createdAt":0,"items":[{"id":"a","name":"Milk","icon":"milk_bag","addedAt":1,"iconFit":"bogus"}]}],"currentListId":"stale"}""")
        val state = store().current()
        assertEquals("l", state.currentListId, "a stale current list id is repaired")
        assertEquals(STATE_SCHEMA_VERSION, state.version)
        assertTrue(state.iconOverrides.isEmpty())
    }

    @Test
    fun emptyListsAreRepaired() = runTest {
        file.writeText("""{"version":1,"lists":[],"currentListId":"x","corrections":[{"name":"oat milk","aisleId":"dairy-eggs","at":3}]}""")
        val state = store().current()
        assertEquals("fresh", state.currentListId)
        assertEquals(1, state.corrections.size, "other data survives the repair")
    }

    @Test
    fun failedWriteKeepsTheOldStateInMemory() = runTest {
        val notADir = File(dir, "blocker").apply { writeText("x") }
        val s = store(File(notADir, "state.json"))
        val before = s.current()
        assertFailsWith<IOException> { s.update { it.renameList("fresh", "Never saved") } }
        assertSame(before, s.current())
    }

    @Test
    fun staleTempFileFromACrashIsIgnoredAndReplaced() = runTest {
        store().update { it.renameList("fresh", "Saved") }
        File(dir, "state.json.tmp").writeText("half-written garbage")
        val s = store()
        assertEquals("Saved", s.current().currentList().title)
        s.update { it.renameList("fresh", "Saved again") }
        assertFalse(File(dir, "state.json.tmp").exists())
        assertEquals("Saved again", store().current().currentList().title)
    }

    @Test
    fun olderVersionsRunTheMigrationSteps() = runTest {
        // Pretend the app is at schema 3: version 1 -> 2 renames "title" in lists, 2 -> 3 adds a marker.
        val steps = listOf<(JsonObject) -> JsonObject>(
            { json -> JsonObject(json + ("version" to JsonPrimitive(2)) + ("note" to JsonPrimitive("v2"))) },
            { json -> JsonObject(json + ("version" to JsonPrimitive(3))) },
        )
        val calls = mutableListOf<Int>()
        file.writeText("""{"lists":[{"id":"l","title":"Old","createdAt":0}],"currentListId":"l"}""")
        val s = JsonFileStateStore(file, ::initial, Dispatchers.Unconfined, schemaVersion = 3) { raw, from ->
            calls += from
            StateMigrations.migrate(raw, from, target = 3, steps = steps)
        }
        val state = s.current()
        assertEquals(listOf(1), calls)
        assertEquals(3, state.version)
        assertEquals("Old", state.currentList().title)
    }

    @Test
    fun migrateRunsEveryStepInOrder() {
        val order = mutableListOf<String>()
        val steps = listOf<(JsonObject) -> JsonObject>(
            { order += "1->2"; JsonObject(it + ("version" to JsonPrimitive(2))) },
            { order += "2->3"; JsonObject(it + ("version" to JsonPrimitive(3))) },
        )
        val out = StateMigrations.migrate(JsonObject(emptyMap()), fromVersion = 1, target = 3, steps = steps)
        assertEquals(listOf("1->2", "2->3"), order)
        assertEquals(JsonPrimitive(3), out["version"])
        assertEquals(STATE_SCHEMA_VERSION - 1, StateMigrations.steps.size)
        val unchanged = JsonObject(mapOf("version" to JsonPrimitive(9)))
        assertSame(unchanged, StateMigrations.migrate(unchanged, 9))
    }

    @Test
    fun newerVersionLoadsLenientlyAndIsBackedUpBeforeTheFirstWrite() = runTest {
        val newer = """{"version":7,"futureField":{"x":1},"lists":[{"id":"l","title":"From the future","createdAt":0,"items":[]}],"currentListId":"l"}"""
        file.writeText(newer)
        val s = store()
        assertEquals("From the future", s.current().currentList().title)
        val backup = File(dir, "state.json.v7.bak")
        assertFalse(backup.exists(), "nothing is copied until we write")
        s.update { it.renameList("l", "Downgraded") }
        assertEquals(newer, backup.readText())
        assertContains(file.readText(), "\"version\":$STATE_SCHEMA_VERSION")
    }
}

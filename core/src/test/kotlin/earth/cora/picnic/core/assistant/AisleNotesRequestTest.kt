package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.MockApi
import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.ingestContext
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.core.str
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Also here" notes reach the model (Cora, 5 October; docs/PROMPTS.md §2.2, §3): ingest, recategorize and AisleSync send
 * each aisle with its note, the baskets call sends names only, and an aisle without a note reads exactly as before.
 */
class AisleNotesRequestTest {
    private val api = MockApi()
    private val catalog = TestSupport.catalog
    private val assistant by lazy { OpenAiGroceryAssistant(api.client, catalog) }

    private val state = AppState(
        lists = listOf(GroceryList("l", "Groceries", 0, listOf(GroceryItem(id = "t", name = "Toothpaste", icon = "paper_bag", addedAt = 0)))),
        currentListId = "l",
        store = StoreProfile(aislesText = "Produce (also: toothpaste, floss)\nBakery\nAisle 7 - Health & beauty"),
    )

    @AfterTest
    fun close() = api.close()

    private fun JsonObject.context(): String = this["input"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray.single().jsonObject["text"]!!.str()

    private val aisleLines = "Store aisles in walking order (id: name):\n" +
        "- produce: Produce (also here: toothpaste, floss)\n" +
        "- bakery: Bakery\n" +
        "- aisle-7-health-beauty: Aisle 7 - Health & beauty\n" +
        "- unsorted: (no matching aisle)\n"

    @Test
    fun ingestSendsEachAisleWithItsNote() = runTest {
        api.enqueue(200, TestSupport.responseEnvelope("""{"items":[],"message":""}"""))
        assistant.ingest(IngestInput.Text("toothpaste, milk"), state.ingestContext("l"))
        assertTrue(api.takeJson().context().startsWith(aisleLines))
    }

    @Test
    fun recategorizeAndAisleSyncSendTheNotesButBasketsDoNot() = runTest {
        api.enqueue(200, TestSupport.responseEnvelope("""{"assignments":[{"id":"i1","aisle_id":"produce"}]}"""))
        val store = InMemoryStateStore(state)
        val report = AisleSync(store, assistant, catalog.baskets).resortAll()
        assertEquals(1, report.recategorized)
        val recat = api.takeJson()
        assertEquals("aisle_assignments", recat["text"]!!.jsonObject["format"]!!.jsonObject["name"]!!.str())
        assertTrue(recat.context().startsWith(aisleLines))

        api.enqueue(200, TestSupport.responseEnvelope("""{"baskets":[]}"""))
        assistant.assignBaskets(state.store.aisles())
        val baskets = api.takeJson()["input"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.single().jsonObject["text"]!!.str()
        assertEquals("Aisles:\n- produce: Produce\n- bakery: Bakery\n- aisle-7-health-beauty: Aisle 7 - Health & beauty", baskets)
        assertFalse("toothpaste" in baskets)
    }
}

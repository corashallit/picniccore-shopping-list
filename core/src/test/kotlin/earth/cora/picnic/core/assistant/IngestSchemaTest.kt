package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.MockApi
import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.PANTRY_IN_CONTEXT
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.model.ingestContext
import earth.cora.picnic.core.model.setPantryText
import earth.cora.picnic.core.path
import earth.cora.picnic.core.str
import earth.cora.picnic.core.strings
import earth.cora.picnic.core.walk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ingest schema's C additions (docs/DESIGN_STICKER.md §19.1; checklist 209, 321), on MockWebServer fixtures. */
class IngestSchemaTest {
    private val api = MockApi()
    private val catalog = TestSupport.catalog
    private val assistant by lazy { OpenAiGroceryAssistant(api.client, catalog) }
    private val aisles = Aisles.parse(Aisles.DEFAULT_TEXT)

    @AfterTest
    fun close() = api.close()

    @Test
    fun theSchemaCarriesSkipReasonNamedPantryEntryLinesAndRecipe() {
        val schema = Schemas.ingest(aisles.map { it.id }, catalog.keys)
        val item = schema.path("properties", "items", "items")!!.jsonObject
        assertEquals(
            listOf("name", "quantity", "note", "aisle_id", "icon", "icon_fit", "skip_reason", "named", "pantry_entry"),
            item["required"]!!.strings(),
        )
        assertEquals(listOf("none", "already_on_list", "optional", "in_pantry"), item.path("properties", "skip_reason", "enum")!!.strings())
        assertEquals("boolean", item.path("properties", "named", "type")!!.str())
        assertEquals("string", item.path("properties", "pantry_entry", "type")!!.str())
        assertEquals(listOf("items", "message", "lines", "recipe"), schema["required"]!!.strings())
        val line = schema.path("properties", "lines", "items")!!.jsonObject
        assertEquals(listOf("text", "crossed_out", "correction_of", "item_index"), line["required"]!!.strings())
        assertEquals(listOf("integer", "null"), line.path("properties", "item_index", "type")!!.strings())
        val recipe = schema.path("properties", "recipe", "anyOf")!!.jsonArray
        assertEquals(listOf("title", "recipes_compared", "servings"), recipe[0].jsonObject["required"]!!.strings())
        assertEquals("null", recipe[1].jsonObject["type"]!!.str())
        // Strict mode: every object lists all its properties as required and forbids extras.
        schema.walk { node ->
            if (node is JsonObject && node["type"]?.toString() == "\"object\"") {
                assertEquals(node["properties"]!!.jsonObject.keys.toList(), node["required"]!!.strings())
                assertEquals("false", node["additionalProperties"].toString())
            }
        }
        assertEquals(SkipReason.entries.size, Schemas.SKIP_REASONS.size)
    }

    @Test
    fun pantryRecipeAnswer() = runTest {
        api.enqueueFixture(200, "responses/ingest_pantry_pancakes.json")
        val context = IngestContext(aisles, emptyList(), existingNames = listOf("Butter"), pantry = listOf("flour", "sugar", "salt", "baking powder"))
        val result = assistant.ingest(IngestInput.Text("pancakes from scratch for 12"), context)
        val byName = result.items.associateBy { it.name }
        assertEquals(SkipReason.IN_PANTRY to "flour", byName.getValue("All-purpose flour").let { it.skipReason to it.pantryEntry })
        assertEquals(SkipReason.IN_PANTRY to "baking powder", byName.getValue("Baking powder").let { it.skipReason to it.pantryEntry })
        assertEquals(SkipReason.IN_PANTRY to "salt", byName.getValue("Salt").let { it.skipReason to it.pantryEntry }, "the backstop: kept by the model, covered by the pantry")
        assertEquals(SkipReason.NONE, byName.getValue("Milk").skipReason)
        assertEquals(SkipReason.NONE, byName.getValue("Eggs").skipReason)
        assertEquals(SkipReason.ALREADY_ON_LIST, byName.getValue("Butter").skipReason)
        assertEquals("454 g", byName.getValue("Butter").quantity, "model quantities are normalised to metric")
        assertEquals(SkipReason.OPTIONAL, byName.getValue("Maple syrup").skipReason)
        assertEquals("355 mL", byName.getValue("Maple syrup").quantity)
        assertTrue(result.items.none { it.named }, "worked out from a dish")
        assertEquals(listOf("Butter"), result.skippedDuplicates)
        assertEquals(RecipeInfo("Pancakes from scratch", 3, 12), result.recipe)
        assertTrue(result.lines.isEmpty(), "lines are for photos only")

        // The context block carried the pantry.
        val body = api.takeJson()
        val ctx = body["input"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray.single().jsonObject["text"]!!.str()
        assertTrue("\n\nIn the pantry (she keeps these at home): flour, sugar, salt, baking powder\n\nApp language: English." in ctx, ctx)
    }

    @Test
    fun pantryNamedItemsAreSwitchedBackOn() = runTest {
        api.enqueueFixture(200, "responses/ingest_photo_lines.json")
        val context = IngestContext(aisles, emptyList(), existingNames = emptyList(), pantry = listOf("salt"))
        val result = assistant.ingest(IngestInput.Image(byteArrayOf(1, 2, 3)), context)
        val salt = result.items.single { it.name == "Salt" }
        assertEquals(SkipReason.NONE, salt.skipReason, "she wrote salt on the paper: she ran out")
        assertNull(salt.pantryEntry)
        assertTrue(salt.named)
    }

    @Test
    fun photoLinesPointIntoTheProcessedItems() = runTest {
        api.enqueueFixture(200, "responses/ingest_photo_lines.json")
        val result = assistant.ingest(IngestInput.Image(byteArrayOf(1, 2, 3)), IngestContext(aisles, emptyList(), emptyList()))
        assertEquals(listOf("Sourdough", "Canned tomatoes", "Milk", "Salt", "Ground beef"), result.items.map { it.name }, "the two milks merged")
        assertEquals("4 L", result.items[2].quantity, "the repeat lends its quantity")
        assertEquals("907 g", result.items[4].quantity)
        assertEquals("796 mL can", result.items[1].quantity, "a bare paper number became a can size (rule)")
        assertEquals(
            listOf(
                PhotoLine("bread → sourdough", crossedOut = false, correctionOf = "bread", itemIndex = 0),
                PhotoLine("tomatoes 796", crossedOut = false, correctionOf = null, itemIndex = 1),
                PhotoLine("chips", crossedOut = true, correctionOf = null, itemIndex = null),
                PhotoLine("milk", crossedOut = false, correctionOf = null, itemIndex = 2),
                PhotoLine("Milk 4L", crossedOut = false, correctionOf = null, itemIndex = 2),
                PhotoLine("salt", crossedOut = false, correctionOf = null, itemIndex = 3),
                PhotoLine("beef 2 lb", crossedOut = false, correctionOf = null, itemIndex = 4),
                PhotoLine("ghost", crossedOut = false, correctionOf = null, itemIndex = null),
            ),
            result.lines,
            "blank lines go; indices follow the merged and re-numbered items; unknown ones become null",
        )
        assertNull(result.recipe)
    }

    @Test
    fun textAnswersNeverCarryLines() = runTest {
        api.enqueueFixture(200, "responses/ingest_photo_lines.json")
        val result = assistant.ingest(IngestInput.Text("bread, tomatoes"), IngestContext(aisles, emptyList(), emptyList()))
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun anOlderAnswerWithoutTheNewFieldsStillParses() = runTest {
        api.enqueueFixture(200, "responses/ingest_terra_pancakes.json")
        val result = assistant.ingest(IngestInput.Text("pancakes"), IngestContext(aisles, emptyList(), emptyList()))
        assertTrue(result.items.all { it.skipReason == SkipReason.NONE && it.named })
        assertNull(result.recipe)
    }

    @Test
    fun theModelCannotClaimSomethingIsOnTheList() {
        val wire = WireItem("Lemon", null, null, "produce", "lemon", "exact", skipReason = "already_on_list", named = true)
        val processed = assistant.postProcess(listOf(wire), IngestContext(aisles, emptyList(), existingNames = listOf("Milk")))
        assertEquals(SkipReason.NONE, processed.items.single().skipReason)
    }
}

/** The pantry in the request (§6.2 rule 8; checklist 320). */
class IngestContextTest {
    @Test
    fun pantry() {
        val s = AppState(lists = listOf(GroceryList("l", "List", 0)), currentListId = "l")
            .setPantryText((1..230).joinToString("\n") { "Thing $it" }, 1)
        val ctx = s.ingestContext("l")
        assertEquals(PANTRY_IN_CONTEXT, ctx.pantry.size, "the first 200, in shelf order")
        assertEquals((1..200).map { "Thing $it" }, ctx.pantry)
        val block = Prompts.contextBlock(ctx.aisles, ctx.corrections, ctx.existingNames, ctx.appLanguage, ctx.pantry)
        assertTrue("\n\nIn the pantry (she keeps these at home): Thing 1, Thing 2, " in block)
        assertTrue("Thing 200\n\nApp language: English." in block)
        assertTrue("Thing 201" !in block)
        val empty = AppState(lists = listOf(GroceryList("l", "List", 0)), currentListId = "l").ingestContext("l")
        assertTrue(empty.pantry.isEmpty())
        assertTrue(
            "Already on the list: nothing yet\n\nIn the pantry (she keeps these at home): nothing yet\n\nApp language" in
                Prompts.contextBlock(empty.aisles, empty.corrections, empty.existingNames, empty.appLanguage, empty.pantry),
        )
        assertTrue("In the pantry" !in Prompts.contextBlock(empty.aisles, empty.corrections, null), "recategorize has no pantry line")
        val written = AppState(lists = listOf(GroceryList("l", "List", 0)), currentListId = "l", pantry = listOf(PantryEntry("Épices", addedAt = 1)))
        assertEquals(listOf("Épices"), written.ingestContext("l").pantry, "entries as she wrote them")
    }
}

/** The prompt rules the C design adds (§6.2, §19.1; checklist 209, 320, 321). */
class PromptsContractTest {
    private val rules = Prompts.ingestRules

    @Test
    fun ingestRules() {
        assertTrue("Kirakira Basket" in rules, "the app's name (D20)")
        assertTrue("\"bread → sourdough\"" in rules, "arrow corrections")
        assertTrue("\"tomatoes 796\" means a 796 mL can" in rules, "bare paper numbers")
        assertTrue("Never skip a named item for the pantry" in rules, "the pantry rule: named items are never skipped")
        assertTrue("A broad entry covers its members" in rules)
        assertTrue("skip_reason \"in_pantry\"" in rules)
        assertTrue("\"already_on_list\"" in rules && "\"optional\"" in rules)
        assertTrue("named: true when the user named the item" in rules)
        assertTrue("lines: for a photo only" in rules)
        assertTrue("recipe: when the items come from a dish" in rules)
        assertTrue("convert imperial amounts to metric" in rules)
        // DESIGN_STICKER §6.2 rule 4 (quick add): a plain list added as named carries no message; a message says only
        // what needs explaining, never the items again.
        assertTrue("message: \"\" (empty) when the request is a plain list" in rules, "the empty message for a plain list")
        assertTrue("Never just repeat the items you added." in rules)
        assertTrue("saying what you added" !in rules, "the old always-a-message rule is gone")
        // ...and keeps it for what she should know: a dish, a skip, a guess, a correction she made, nothing to buy.
        val message = rules.substringAfter("message: ")
        for (reason in listOf("a dish, meal or recipe", "a skipped item", "a guess", "a correction you applied", "nothing to buy")) {
            assertTrue(reason in message, "the message keeps explaining: $reason")
        }
        assertTrue("nothing skipped, guessed, corrected or left out" in message, "only an untouched plain list is silent")
    }

    @Test
    fun theContextExampleIsByteIdentical() {
        val example = TestSupport.fixture("context_example.txt").trim()
        assertTrue(example.endsWith("Already on the list: Butter, Bananas, Paper towels\n\nIn the pantry (she keeps these at home): nothing yet"))
    }

    @Test
    fun basketRules() {
        assertTrue("Use \"wicker_picnic\" for produce" in Prompts.basketsRules)
        val stable = Prompts.basketsStable(TestSupport.catalog.baskets)
        assertTrue("- wicker_picnic: " in stable && "produce" in stable.lines().single { it.startsWith("- wicker_picnic:") })
        assertTrue("baking" in stable.lines().single { it.startsWith("- mixing_bowl:") })
    }
}

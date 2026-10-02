package earth.cora.picnic.core.store

import earth.cora.picnic.core.CoreJson
import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.STATE_SCHEMA_VERSION
import earth.cora.picnic.core.model.StateOps
import earth.cora.picnic.core.model.Trip
import earth.cora.picnic.core.model.TripRules
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.toggleChecked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The real v1 -> v2 step (docs/DESIGN_STICKER.md §7.2; checklist 139, 140, 319). One fixture per case. */
class StateMigrationV2Test {
    private val now = 1_791_037_440_000L
    private val dir: File = Files.createTempDirectory("picnic-migrate").toFile()
    private val file = File(dir, "state.json")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun load(fixture: String): AppState {
        file.writeText(TestSupport.fixture("state/$fixture"))
        return kotlinx.coroutines.runBlocking { store().current() }
    }

    private fun store() = JsonFileStateStore(file, { StateOps.initialState("fresh", 1) }, Dispatchers.Unconfined, clock = { now })

    @Test
    fun aV1FileDecodes() {
        val s = load("v1_nothing_checked.json")
        assertEquals(STATE_SCHEMA_VERSION, s.version)
        assertEquals(2, STATE_SCHEMA_VERSION)
        val list = s.list("6f1c")!!
        assertEquals(listOf("Lemons", "Dish rack"), list.items.map { it.name })
        assertTrue(list.items.none { it.checked || it.checkedAt != null || it.bookSlot != null })
        assertNull(list.trip, "nothing checked: no trip")
        assertNull(list.openAisle)
        assertNull(list.lastClosedAt)
        assertEquals(setOf("bakery"), list.collapsedAisles, "kept, ignored")
        assertEquals(BasketKind.BERRY_PUNNET, s.store.aisleBaskets["produce"], "a kind that is not the v1 guess is hers or the model's: kept")
        assertEquals(1, s.corrections.size)
        assertEquals(1, s.iconOverrides.size)
        assertTrue(s.favourites.isEmpty())
        assertTrue(s.pantry.isEmpty())
        assertTrue(s.store.aisleStyles.isEmpty())
    }

    @Test
    fun checkedItemsGetCheckOffTimesSlotsAndATrip() {
        val s = load("v1_some_checked.json") // a file without "version" is version 1
        val list = s.list("sat")!!
        val byId = list.items.associateBy { it.id }
        assertEquals(1_759_330_300_000L, byId.getValue("a").checkedAt, "checkedAt = addedAt")
        assertNull(byId.getValue("b").checkedAt)
        assertEquals(mapOf("c" to 0, "d" to 1, "a" to 2), list.items.filter { it.checked }.associate { it.id to it.bookSlot }, "spots in check-off (= addedAt) order")
        val trip = assertNotNull(list.trip)
        assertEquals(1_759_330_100_000L, trip.startedAt, "the earliest check-off")
        assertEquals("t-sat-1759330100000", trip.id)
        assertEquals("Hillcrest Market", trip.storeName)
        assertEquals(now, trip.lastActivityAt)
        assertNull(trip.completedAt, "Milk is still to get")
        assertNull(trip.finaleShownAt, "the finale still plays when she finishes")
        assertNull(trip.shopMinutes)
        assertNull(s.list("party")!!.trip)
        assertEquals(list, TripRules.reconcile(list, now, "Hillcrest Market"), "the migrated list already satisfies the invariant")
    }

    @Test
    fun aFullyCheckedListIsCompleteAndItsFinaleCountsAsShown() {
        val s = load("v1_all_checked.json")
        val list = s.list("done")!!
        val trip = assertNotNull(list.trip)
        assertEquals(Trip("t-done-1759330400000", 1_759_330_400_000L, "My store", now, completedAt = now, finaleShownAt = now), trip)
        assertTrue(TripRules.isComplete(list), "so the LCD hold prints its receipt (D21)")
        assertFalse(TripRules.finaleOwed(list), "no surprise finale on her first open")
        assertEquals(listOf("y" to 0, "x" to 1), list.items.sortedBy { it.bookSlot }.map { it.id to it.bookSlot })
        assertNull(s.list("empty")!!.trip)
        // Un-checking and re-completing later keeps the migrated trip's minutes unknown ("--").
        val again = s.toggleChecked("done", "x", now + 1).toggleChecked("done", "x", now + 2)
        assertNull(again.list("done")!!.trip!!.shopMinutes)
    }

    @Test
    fun reguessesHeuristicBaskets() {
        val s = load("v1_baskets.json")
        assertEquals(
            mapOf(
                "produce" to BasketKind.FLOWER_BUCKET, // hers (v1 guessed fruit_crate): kept
                "bakery" to BasketKind.BREAD_BASKET,
                "deli-cheese" to BasketKind.BENTO_BOX,
                "meat-seafood" to BasketKind.BENTO_BOX,
                "aisle-1-breakfast-coffee-tea" to BasketKind.FRUIT_CRATE,
                "aisle-2-baking-spices" to BasketKind.MIXING_BOWL, // her Baking aisle becomes a mixing bowl
                "aisle-3-pasta-rice-international" to BasketKind.JAR_SHELF,
                "aisle-4-canned-goods-soups" to BasketKind.JAR_SHELF,
                "aisle-5-snacks-drinks" to BasketKind.BOTTLE_CARRIER, // the model's (v1 guessed candy_jar): kept
                "aisle-6-household-cleaning" to BasketKind.CANVAS_TOTE,
                "aisle-7-health-beauty-baby" to BasketKind.VANITY_CASE,
                "dairy-eggs" to BasketKind.EGG_BASKET,
                "frozen" to BasketKind.COOLER,
                "bulk-natural-foods" to BasketKind.JAR_SHELF,
                "gone-aisle" to BasketKind.CANVAS_TOTE, // not an aisle any more: left alone
            ),
            s.store.aisleBaskets,
        )
        assertEquals("canvas_tote", StateMigrations.V1_BASKETS.guess("Aisle 2 - Baking & spices").key, "the frozen v1 heuristic")
        assertEquals("ice_bucket", StateMigrations.V1_BASKETS.guess("Frozen").key)
    }

    @Test
    fun phaseZeroCheckOffTimesAreKept() {
        val s = load("v1_phase0.json")
        val items = s.list("l")!!.items.associateBy { it.id }
        assertEquals(9000L, items.getValue("a").checkedAt)
        assertEquals(5000L, items.getValue("b").checkedAt)
        assertEquals(1, items.getValue("a").bookSlot)
        assertEquals(0, items.getValue("b").bookSlot, "spots follow the check-off times")
        assertNull(items.getValue("c").checkedAt, "an unchecked item carries neither")
        assertNull(items.getValue("c").bookSlot)
        assertEquals(Rarity.SHINY, items.getValue("b").rarity)
        assertEquals("t-l-5000", s.list("l")!!.trip!!.id)
        assertEquals("bakery", s.list("l")!!.openAisle)
        assertEquals(setOf("jam"), s.favourites)
    }

    @Test
    fun pantryDefaultsEmpty() = runTest {
        val s = load("v2_without_pantry.json")
        assertTrue(s.pantry.isEmpty(), "a v2 state without a pantry loads with an empty one (no schema bump)")
        assertFalse(File(dir, "state.json.v2.bak").exists())
        val withPantry = store().update { it.copy(pantry = listOf(PantryEntry("Flour", addedAt = 5))) }
        assertEquals(withPantry, store().current(), "round-trips")
    }

    @Test
    fun theOriginalIsKeptBeforeTheFirstWrite() = runTest {
        val original = TestSupport.fixture("state/v1_some_checked.json")
        file.writeText(original)
        val s = store()
        s.current()
        val backup = File(dir, "state.json.v1.bak")
        assertFalse(backup.exists(), "nothing is copied until we write")
        s.update { it.toggleChecked("sat", "b", now + 1) }
        assertEquals(original, backup.readText())
        val written = CoreJson.json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(JsonPrimitive(2), written["version"])
        s.update { it.toggleChecked("sat", "b", now + 2) }
        assertEquals(original, backup.readText(), "the first backup is never overwritten")
    }

    @Test
    fun theStepIsPureJson() {
        val raw = CoreJson.json.parseToJsonElement(TestSupport.fixture("state/v1_some_checked.json")).jsonObject
        val migrated = StateMigrations.v1ToV2(raw, now)
        assertEquals(JsonPrimitive(2), migrated["version"])
        assertEquals(raw["currentListId"], migrated["currentListId"], "everything else is copied as is")
        assertEquals(migrated, StateMigrations.migrate(raw, 1, now))
        val odd = JsonObject(mapOf("lists" to JsonPrimitive("nope")))
        assertEquals(JsonPrimitive(2), StateMigrations.v1ToV2(odd, now)["version"], "an odd shape is left to the decoder")
    }

    @Test
    fun aBrokenV1FileIsQuarantined() = runTest {
        file.writeText("""{"lists":[{"id":"l","title":"T","createdAt":0,"items":[{"id":"a","checked":true}]}],"currentListId":"l"}""")
        assertEquals("fresh", store().current().currentListId, "missing required fields: moved aside, fresh state")
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("state.json.corrupt-") })
    }
}

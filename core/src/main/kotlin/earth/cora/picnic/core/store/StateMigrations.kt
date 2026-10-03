package earth.cora.picnic.core.store

import earth.cora.picnic.core.CoreJson
import earth.cora.picnic.core.catalog.BasketCatalog
import earth.cora.picnic.core.catalog.BasketEntry
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.STATE_SCHEMA_VERSION
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.TripRules
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Migration hook for state.json. Each step turns the raw JSON of version N into version N+1
 * (and sets "version" to N+1). A file without "version" is version 1.
 * To change the schema: bump STATE_SCHEMA_VERSION, add a step here, add a fixture test.
 * Steps work on the raw JSON, never on today's data classes, so an old step keeps working when the model moves on.
 */
object StateMigrations {
    /** Index i migrates version (i + 1) -> (i + 2), stamping new data with [now]. */
    fun steps(now: Long): List<(JsonObject) -> JsonObject> = listOf(
        { raw -> v1ToV2(raw, now) },
    )

    /** [steps] at the current time. */
    val steps: List<(JsonObject) -> JsonObject> get() = steps(System.currentTimeMillis())

    /** Runs every step from [fromVersion] up to STATE_SCHEMA_VERSION. Versions above current are returned unchanged. */
    fun migrate(raw: JsonObject, fromVersion: Int, now: Long = System.currentTimeMillis()): JsonObject =
        migrate(raw, fromVersion, STATE_SCHEMA_VERSION, steps(now))

    /** The general form (tests pass their own [steps] and [target]). A version below 1 counts as 1. */
    fun migrate(
        raw: JsonObject,
        fromVersion: Int,
        target: Int,
        steps: List<(JsonObject) -> JsonObject>,
    ): JsonObject {
        var json = raw
        for (version in fromVersion.coerceAtLeast(1) until target) {
            val step = steps.getOrNull(version - 1)
                ?: error("No state.json migration from version $version to ${version + 1}")
            json = step(json)
        }
        return json
    }

    /**
     * v1 -> v2 (docs/DESIGN_STICKER.md §7.2, StateMigrationV2Test). For each list:
     * - every checked item gets checkedAt = addedAt (one it already has is kept) and a book spot, in that order
     *   (ties keep list order); unchecked items get neither;
     * - a list with checked items gets a trip: startedAt = the earliest check-off, id "t-<listId>-<startedAt>",
     *   the store's name, lastActivityAt = [now]; when EVERY item is checked it is complete (completedAt = [now])
     *   and its finale counts as shown (finaleShownAt = [now], no surprise finale on her first open), so the LCD
     *   prints its receipt; shopMinutes stays null (shown as "--"). Lists with nothing checked get no trip, and a
     *   list that somehow already has one keeps it.
     * - Stored basket kinds that equal what the v1 keyword heuristic guessed for their aisle are re-guessed once
     *   with today's heuristic (her Baking aisle becomes a mixing bowl); kinds that differ (the model's or hers)
     *   are kept (§5.2).
     * Everything else is copied as is. Bespoke icons drawn before v4 are marked legacy by the icon repository
     * (they have no ".cut" file, §3.3), not here: state.json does not know how a custom icon was drawn.
     */
    fun v1ToV2(raw: JsonObject, now: Long, catalog: IconCatalog = IconCatalog.default): JsonObject {
        val store = raw["store"] as? JsonObject
        val storeName = (store?.get("name") as? JsonPrimitive)?.contentOrNull ?: StoreProfile().name
        val lists = (raw["lists"] as? JsonArray)?.let { array ->
            JsonArray(array.map { element -> (element as? JsonObject)?.let { migrateList(it, storeName, now) } ?: element })
        }
        val newStore = store?.let { reguessBaskets(it, catalog) }
        return JsonObject(
            raw + listOfNotNull(
                "version" to JsonPrimitive(2),
                lists?.let { "lists" to it },
                newStore?.let { "store" to it },
            ),
        )
    }

    private fun migrateList(list: JsonObject, storeName: String, now: Long): JsonObject {
        val array = list["items"] as? JsonArray ?: return list
        val listId = (list["id"] as? JsonPrimitive)?.contentOrNull ?: return list
        val checked = array.withIndex()
            .mapNotNull { (index, element) ->
                val item = element as? JsonObject
                if (item != null && item.bool("checked")) index to (item.long("checkedAt") ?: item.long("addedAt") ?: now) else null
            }
            .sortedWith(compareBy({ it.second }, { it.first }))
        // item index -> (book spot, checkedAt)
        val spots = checked.withIndex().associate { (slot, entry) -> entry.first to (slot to entry.second) }
        val items = JsonArray(
            array.mapIndexed { index, element ->
                val item = element as? JsonObject ?: return@mapIndexed element
                val spot = spots[index]
                if (spot != null) {
                    JsonObject(item + mapOf("checkedAt" to JsonPrimitive(spot.second), "bookSlot" to JsonPrimitive(spot.first)))
                } else {
                    JsonObject(item - "checkedAt" - "bookSlot")
                }
            },
        )
        val hasTrip = list["trip"].let { it != null && it !is JsonNull }
        if (checked.isEmpty() || hasTrip) return JsonObject(list + ("items" to items))
        val startedAt = checked.minOf { it.second }
        val complete = array.isNotEmpty() && array.all { (it as? JsonObject)?.bool("checked") == true }
        val trip = buildJsonObject {
            put("id", TripRules.tripId(listId, startedAt))
            put("startedAt", startedAt)
            put("storeName", storeName)
            put("lastActivityAt", now)
            if (complete) {
                put("completedAt", now)
                put("finaleShownAt", now)
            }
        }
        return JsonObject(list + mapOf("items" to items, "trip" to trip))
    }

    private fun reguessBaskets(store: JsonObject, catalog: IconCatalog): JsonObject {
        val baskets = store["aisleBaskets"] as? JsonObject ?: return store
        val text = (store["aislesText"] as? JsonPrimitive)?.contentOrNull ?: StoreProfile().aislesText
        val aisles = Aisles.parse(text).associateBy { it.id }
        val updated = baskets.mapValues { (id, value) ->
            val aisle = aisles[id] ?: return@mapValues value
            val stored = (value as? JsonPrimitive)?.contentOrNull ?: return@mapValues value
            if (stored == V1_BASKETS.guess(aisle).key) JsonPrimitive(catalog.baskets.guess(aisle).key) else value
        }
        return if (updated == baskets) store else JsonObject(store + ("aisleBaskets" to JsonObject(updated)))
    }

    /** The v1 keyword heuristic, frozen (resources/migrations/baskets_v1.json): what v1 guessed for an aisle. */
    internal val V1_BASKETS: BasketCatalog by lazy {
        val stream = StateMigrations::class.java.getResourceAsStream("/migrations/baskets_v1.json")
            ?: error("migrations/baskets_v1.json missing from core resources")
        val file = stream.bufferedReader(Charsets.UTF_8).use { CoreJson.json.decodeFromString(FrozenBaskets.serializer(), it.readText()) }
        BasketCatalog(file.baskets.map { BasketEntry(it.key, label = it.key, drawHint = "", keywords = it.keywords) })
    }

    @Serializable
    private data class FrozenBaskets(val baskets: List<FrozenBasket>)

    @Serializable
    private data class FrozenBasket(val key: String, val keywords: List<String>)

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.intOrNull?.toLong() }
}

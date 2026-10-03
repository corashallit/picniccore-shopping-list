package earth.cora.picnic.ui.common

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.setFavourite
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.setRarity
import earth.cora.picnic.core.model.toggleChecked

/**
 * The Appendix A fixture "Saturday shop" (docs/DESIGN_STICKER.md) for WP8's screen tests and goldens, the same trip as
 * core's test fixture (`core/src/test/…/SaturdayShop.kt`, which app tests can't see): Hillcrest Market, 8 aisles in
 * walking order with "Aisle 2 - Baking" (tab "Baking", store aisle 2), 24 items, 10 checked on Saturday 3 October 2026
 * between 09:58 and 10:23 (America/Toronto), Baking open. Built through the real StateOps, so it is exactly what the app
 * would hold. [defaultAisles] is Appendix A's `fixtureDefaultAisles`: the same store with the app's 14 default aisles,
 * for the long-name and font-scale goldens.
 */
object SaturdayShop {
    const val LIST_ID = "saturday"
    const val STORE = "Hillcrest Market"
    const val AISLES = "Produce\nBakery\nDeli & meat\nAisle 2 - Baking\nBreakfast\nDairy & eggs\nFrozen\nHousehold"

    /** 2026-10-03 10:24 America/Toronto. */
    const val NOW = 1_791_037_440_000L

    /** 2026-10-03 09:58, the first check-off (bananas). */
    const val TRIP_START = 1_791_035_880_000L

    /** Tuesday 2026-09-29 19:00: when the list was written. */
    const val WRITTEN = 1_790_722_800_000L

    private const val MIN = 60_000L

    /** One item of the fixture: id, name, quantity (the detail line), aisle id, catalog key and when it was added. */
    private data class Row(val id: String, val name: String, val quantity: String, val aisle: String, val icon: String, val addedAt: Long)

    private val rows = listOf(
        Row("strawberries", "Strawberries", "1 L basket", "produce", "strawberry", WRITTEN),
        Row("bananas", "Bananas", "6 bananas", "produce", "banana", WRITTEN + 1),
        Row("apples", "McIntosh apples", "1.36 kg bag", "produce", "apple", WRITTEN + 2),
        Row("carrots", "Carrots", "907 g bag", "produce", "carrot", WRITTEN + 3),
        Row("broccoli", "Broccoli", "1 crown", "produce", "broccoli", WRITTEN + 4),
        Row("sourdough", "Sourdough", "800 g boule", "bakery", "bakery_generic", WRITTEN + 5),
        Row("croissants", "Croissants", "4 pack", "bakery", "croissant", WRITTEN + 6),
        Row("baguette", "Baguette", "1 stick", "bakery", "baguette", WRITTEN + 7),
        Row("ham", "Spiral ham", "1.5 kg", "deli-meat", "deli_meat", WRITTEN + 8),
        Row("peameal", "Peameal bacon", "600 g roast", "deli-meat", "peameal_bacon", WRITTEN + 9),
        Row("flour", "All-purpose flour", "2.5 kg bag", "aisle-2-baking", "flour", WRITTEN + 10),
        Row("sugar", "White sugar", "2 kg bag", "aisle-2-baking", "sugar", WRITTEN + 11),
        Row("baking-powder", "Baking powder", "225 g tin", "aisle-2-baking", "baking_soda", TRIP_START + 13 * MIN),
        Row("coffee", "Coffee beans", "340 g bag", "breakfast", "coffee", WRITTEN + 12),
        Row("maple", "Maple syrup", "540 mL tin", "breakfast", "maple_syrup", WRITTEN + 13),
        Row("milk", "Milk, 2 %", "4 L bag", "dairy-eggs", "milk_bag", WRITTEN + 14),
        Row("eggs", "Eggs, large", "dozen", "dairy-eggs", "eggs", TRIP_START + 12 * MIN),
        Row("butter", "Butter, salted", "454 g brick", "dairy-eggs", "butter", TRIP_START + 10 * MIN),
        Row("peas", "Frozen peas", "750 g bag", "frozen", "frozen_vegetables", WRITTEN + 15),
        Row("ice-cream", "Ice cream", "1.5 L tub", "frozen", "ice_cream", WRITTEN + 16),
        Row("toilet-paper", "Toilet paper", "12 double rolls", "household", "toilet_paper", WRITTEN + 17),
        Row("dish-soap", "Dish soap", "740 mL", "household", "dish_soap", WRITTEN + 18),
        Row("cat-food", "Dry cat food", "7 kg bag", "household", "cat_food", WRITTEN + 19),
        Row("toothpaste", "Toothpaste", "100 mL tube", "household", "toothpaste", WRITTEN + 20),
    )

    /** The check-off order and minutes after [TRIP_START]: what the baskets and the book hold. */
    val CHECKS: List<Pair<String, Long>> = listOf(
        "bananas" to 0L, "carrots" to 2L, "broccoli" to 3L, "croissants" to 6L, "ham" to 9L,
        "peameal" to 10L, "flour" to 17L, "sugar" to 18L, "coffee" to 22L, "butter" to 25L,
    )

    val catalog: IconCatalog get() = IconCatalog.default

    fun item(row: String): GroceryItem {
        val r = rows.single { it.id == row }
        return GroceryItem(
            id = r.id, name = r.name, quantity = r.quantity, aisleId = r.aisle, icon = r.icon, iconFit = IconFit.EXACT,
            addedAt = r.addedAt, source = ItemSource.AI_TEXT,
        )
    }

    /** The store and the 24 items before any check-off. */
    fun unchecked(): AppState {
        val base = AppState(
            lists = listOf(GroceryList(LIST_ID, "Saturday shop", WRITTEN)),
            currentListId = LIST_ID,
            store = StoreProfile(name = STORE, aislesText = AISLES),
        )
        // Written over the week, in addedAt order (the three "new" ones during the trip).
        return rows.sortedBy { it.addedAt }.fold(base) { s, r -> s.addItems(LIST_ID, listOf(item(r.id)), r.addedAt).first }
    }

    /** The fixture at [NOW]: 10 of 24 checked, maple syrup shiny, coffee a favourite, Baking open. */
    fun state(): AppState {
        val checked = CHECKS.fold(unchecked()) { s, (id, minutes) -> s.toggleChecked(LIST_ID, id, TRIP_START + minutes * MIN) }
        return checked
            .setRarity(LIST_ID, "maple", Rarity.SHINY)
            .setFavourite(normalizeName("Coffee beans"), true)
            .setOpenAisle(LIST_ID, "aisle-2-baking")
    }

    /** Where each of the 8 aisles' items go in the app's own 14-aisle text ([defaultAisles]). */
    private val DEFAULT_HOME: Map<String, String> = mapOf(
        "produce" to "Produce",
        "bakery" to "Bakery",
        "deli-meat" to "Deli & cheese",
        "aisle-2-baking" to "Aisle 2 - Baking & spices",
        "breakfast" to "Aisle 1 - Breakfast, coffee & tea",
        "dairy-eggs" to "Dairy & eggs",
        "frozen" to "Frozen",
        "household" to "Aisle 6 - Household & cleaning",
    )

    /**
     * Appendix A's `fixtureDefaultAisles`: the same 24 items, checks and trip filed into the app's own 14-aisle text
     * (`Aisles.DEFAULT_TEXT`, "Aisle 1 - Breakfast, coffee & tea" … "Aisle 7 - Health, beauty & baby"), Baking & spices open.
     */
    fun defaultAisles(): AppState {
        val s = state()
        val ids = Aisles.parse(Aisles.DEFAULT_TEXT).associate { it.name to it.id }
        val home = DEFAULT_HOME.mapValues { (_, name) -> ids.getValue(name) }
        val list = s.lists.single()
        return s.copy(
            store = s.store.copy(aislesText = Aisles.DEFAULT_TEXT),
            lists = listOf(
                list.copy(
                    items = list.items.map { it.copy(aisleId = it.aisleId?.let(home::getValue)) },
                    openAisle = home.getValue("aisle-2-baking"),
                ),
            ),
        )
    }
}

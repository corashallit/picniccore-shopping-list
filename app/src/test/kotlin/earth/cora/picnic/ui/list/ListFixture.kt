package earth.cora.picnic.ui.list

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.Receipt
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.markFinaleShown
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.printReceipt
import earth.cora.picnic.core.model.saveReceipt
import earth.cora.picnic.core.model.setFavourite
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.list.motion.MotionFixture

/*
 * The Appendix A fixture "Saturday shop" (docs/DESIGN_STICKER.md) for WP5's tests and goldens: Hillcrest Market, 8 aisles
 * in walking order with "Aisle 2 - Baking" (tab "Baking", store aisle 2), 24 items, 10 checked between 09:58 and 10:23 on
 * Saturday 3 October 2026, maple syrup shiny, coffee beans a favourite, Baking open. Built through the real StateOps on
 * WP6's MotionFixture, so the list and the motion tests share one trip.
 */
object ListFixture {
    const val LIST_ID = MotionFixture.LIST_ID
    const val NOW = MotionFixture.NOW
    const val TRIP_START = MotionFixture.TRIP_START
    const val MIN = 60_000L
    const val HOUR = 60 * MIN
    val catalog: IconCatalog get() = IconCatalog.default

    /** The fixture at [NOW] (10 of 24, Baking open). */
    fun state(): AppState = MotionFixture.state().setFavourite(normalizeName("Coffee beans"), true)

    /** The 24 items before any check-off (a fresh trip: nothing checked, the book empty). */
    fun fresh(): AppState = MotionFixture.unchecked().setFavourite(normalizeName("Coffee beans"), true)

    /** The same items filed into the app's 14 default aisles (Appendix A's `fixtureDefaultAisles`). */
    fun defaultAisles(): AppState = MotionFixture.defaultAisles()

    /** Everything but [left] checked, open on [open] (the rest checked by TRIP_START + 41 min). */
    fun allBut(left: List<String>, open: String? = null): AppState =
        MotionFixture.allBut(left, open).setFavourite(normalizeName("Coffee beans"), true)

    /** All 24 checked and the finale shown (41 min): the book is PERFECT. */
    fun done(): AppState = allBut(emptyList()).markFinaleShown(LIST_ID, TRIP_START + 42 * MIN)

    /** [done] with the receipt printed and tucked into the book (egg 18). */
    fun doneWithReceipt(): AppState = done()
        .printReceipt(LIST_ID, Receipt(TRIP_START + 43 * MIN, "Sat 3 Oct", MotionFixture.STORE, stickers = 24, baskets = 8, minutes = 41, shiny = 1))
        .saveReceipt(LIST_ID)

    /** An empty list on the fixture's store (no items, no book). */
    fun empty(): AppState = AppState(
        lists = listOf(GroceryList(LIST_ID, "Saturday shop", MotionFixture.WRITTEN)),
        currentListId = LIST_ID,
        store = StoreProfile(name = MotionFixture.STORE, aislesText = MotionFixture.AISLES),
    )

    /** Household, the last stop, with seven more things and open: a tray taller than the viewport (the pinned plate). */
    fun longAisle(): AppState {
        val extra = listOf(
            Triple("paper-towels", "Paper towels", "6 rolls") to "paper_towels",
            Triple("laundry", "Laundry detergent", "2.03 L jug") to "laundry_detergent",
            Triple("sponges", "Sponges", "3 pack") to "sponge",
            Triple("garbage-bags", "Garbage bags", "40 bags") to "garbage_bags",
            Triple("foil", "Aluminum foil", "30 m roll") to "aluminum_foil",
            Triple("batteries", "AA batteries", "8 pack") to "batteries",
            Triple("tissues", "Tissues", "6 boxes") to "tissues",
        )
        var s = state()
        extra.forEachIndexed { i, (t, icon) ->
            val item = GroceryItem(id = t.first, name = t.second, quantity = t.third, aisleId = "household", icon = icon, iconFit = IconFit.EXACT, addedAt = MotionFixture.WRITTEN + 30 + i)
            s = s.addItems(LIST_ID, listOf(item), MotionFixture.WRITTEN + 30 + i).first
        }
        s = s.toggleChecked(LIST_ID, "toilet-paper", TRIP_START + 23 * MIN)
        return s.setOpenAisle(LIST_ID, "household")
    }

    /** The list's UI state for [state] at [now]. */
    fun ui(state: AppState = state(), now: Long = NOW, busy: Set<String> = emptySet(), editingId: String? = null): ListUiState =
        buildListUiState(state, busy, hasToken = true, editingId = editingId, catalog = catalog, now = now)
}

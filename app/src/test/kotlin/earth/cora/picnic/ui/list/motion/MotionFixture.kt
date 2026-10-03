package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.GroupedList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ListGrouping
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.TripRules
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.markFinaleShown
import earth.cora.picnic.core.model.moveItemToAisle
import earth.cora.picnic.core.model.removeItem
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.setRarity
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.ui.theme.AisleLooks

/*
 * The Appendix A fixture "Saturday shop" (docs/DESIGN_STICKER.md) for WP6's tests, built through the real StateOps,
 * and a small stand-in for WP5's ListViewModel: it applies a toggle, a delete or a move to the state and hands the
 * engine the ToggleEvent or ListChange the real ViewModel will build (§8.3, §8.4, §19.2).
 */
object MotionFixture {
    const val LIST_ID = "saturday"
    const val STORE = "Hillcrest Market"
    const val AISLES = "Produce\nBakery\nDeli & meat\nAisle 2 - Baking\nBreakfast\nDairy & eggs\nFrozen\nHousehold"

    /** 2026-10-03 10:24 America/Toronto. */
    const val NOW = 1_791_037_440_000L
    const val TRIP_START = 1_791_035_880_000L
    const val WRITTEN = 1_790_722_800_000L
    private const val MIN = 60_000L

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
        Row("baking-powder", "Baking powder", "225 g tin · from your recipe", "aisle-2-baking", "baking_soda", TRIP_START + 13 * MIN),
        Row("coffee", "Coffee beans", "340 g bag", "breakfast", "coffee", WRITTEN + 12),
        Row("maple", "Maple syrup", "540 mL tin", "breakfast", "maple_syrup", WRITTEN + 13),
        Row("milk", "Milk, 2 %", "4 L bag", "dairy-eggs", "milk_bag", WRITTEN + 14),
        Row("eggs", "Eggs, large", "dozen", "dairy-eggs", "eggs", TRIP_START + 12 * MIN),
        Row("butter", "Butter, salted", "454 g brick", "dairy-eggs", "butter", TRIP_START + 10 * MIN),
        Row("peas", "Frozen peas", "750 g bag", "frozen", "frozen_vegetables", WRITTEN + 15),
        Row("ice-cream", "Ice cream", "1.5 L tub", "frozen", "ice_cream", WRITTEN + 16),
        Row("toilet-paper", "Toilet paper", "12 double rolls", "household", "toilet_paper", WRITTEN + 17),
        Row("dish-soap", "Dish soap", "740 mL", "household", "dish_soap", WRITTEN + 18),
        Row("cat-food", "Dry cat food", "7 kg bag · pets", "household", "cat_food", WRITTEN + 19),
        Row("toothpaste", "Toothpaste", "100 mL tube", "household", "toothpaste", WRITTEN + 20),
    )

    /** The check-off order and minutes after [TRIP_START]. */
    val CHECKS: List<Pair<String, Long>> = listOf(
        "bananas" to 0L, "carrots" to 2L, "broccoli" to 3L, "croissants" to 6L, "ham" to 9L,
        "peameal" to 10L, "flour" to 17L, "sugar" to 18L, "coffee" to 22L, "butter" to 25L,
    )

    fun item(row: String): GroceryItem {
        val r = rows.single { it.id == row }
        return GroceryItem(id = r.id, name = r.name, quantity = r.quantity, aisleId = r.aisle, icon = r.icon, iconFit = IconFit.EXACT, addedAt = r.addedAt)
    }

    /** The store and the 24 items before any check-off ([aisles]: the store's aisle text). */
    fun unchecked(aisles: String = AISLES, title: String = "Saturday shop"): AppState {
        val base = AppState(
            lists = listOf(GroceryList(LIST_ID, title, WRITTEN)),
            currentListId = LIST_ID,
            store = StoreProfile(name = STORE, aislesText = aisles),
        )
        return rows.sortedBy { it.addedAt }.fold(base) { s, r -> s.addItems(LIST_ID, listOf(item(r.id)), r.addedAt).first }
    }

    /** The fixture at [NOW]: 10 of 24 checked, maple syrup shiny, Baking open. */
    fun state(title: String = "Saturday shop"): AppState {
        val checked = CHECKS.fold(unchecked(title = title)) { s, (id, minutes) -> s.toggleChecked(LIST_ID, id, TRIP_START + minutes * MIN) }
        return checked.setRarity(LIST_ID, "maple", Rarity.SHINY).setOpenAisle(LIST_ID, "aisle-2-baking")
    }

    /** Everything checked but [left] (in their aisles), the list still open on [open]; times after the fixture's. */
    fun allBut(left: List<String>, open: String? = null, title: String = "Saturday shop"): AppState {
        var s = state(title)
        var t = TRIP_START + 26 * MIN
        val list = s.list(LIST_ID)!!
        for (it in list.items) if (!it.checked && it.id !in left) s = s.toggleChecked(LIST_ID, it.id, t++)
        return s.setOpenAisle(LIST_ID, open)
    }

    /** The default-aisles variant (the app's own 14-aisle text) with the same 24 items filed into it by name. */
    fun defaultAisles(): AppState = unchecked(Aisles.DEFAULT_TEXT)

    /** A list of [n] items over [stops] aisles, all but the last ("i<n-1>") checked (the ride with 100 items). */
    fun many(n: Int, stops: Int): AppState {
        val names = (1..stops).joinToString("\n") { "Aisle $it" }
        val keys = listOf("strawberry", "banana", "apple", "carrot", "broccoli", "croissant", "baguette", "flour", "sugar", "coffee", "milk_bag", "eggs", "butter", "ice_cream", "toilet_paper", "dish_soap", "cherries", "lemonade")
        var s = AppState(lists = listOf(GroceryList(LIST_ID, "Big shop", WRITTEN)), currentListId = LIST_ID, store = StoreProfile(name = STORE, aislesText = names))
        val aisleIds = s.store.aisles().map { it.id }
        for (i in 0 until n) {
            val item = GroceryItem(id = "i$i", name = "Item $i", aisleId = aisleIds[i % aisleIds.size], icon = keys[i % keys.size], iconFit = IconFit.EXACT, addedAt = WRITTEN + i)
            s = s.addItems(LIST_ID, listOf(item), WRITTEN + i).first
        }
        for (i in 0 until n - 1) s = s.toggleChecked(LIST_ID, "i$i", TRIP_START + i * 10_000L)
        return s
    }

    /** A store with the default 14 aisles: Baking & spices nearly done, spaghetti in Pasta (stop 7, store aisle 3). */
    fun pasta(): AppState {
        var s = AppState(
            lists = listOf(GroceryList(LIST_ID, "Saturday shop", WRITTEN)),
            currentListId = LIST_ID,
            store = StoreProfile(name = STORE, aislesText = Aisles.DEFAULT_TEXT),
        )
        val aisles = s.store.aisles()
        val baking = aisles.first { it.name.contains("Baking") }.id
        val pasta = aisles.first { it.name.contains("Pasta") }.id
        val items = listOf(
            item("flour").copy(aisleId = baking),
            item("sugar").copy(aisleId = baking),
            item("baking-powder").copy(aisleId = baking),
            item("flour").copy(id = "spaghetti", name = "Spaghetti", quantity = "900 g box", aisleId = pasta, icon = "spaghetti"),
        )
        for (it in items) s = s.addItems(LIST_ID, listOf(it), WRITTEN).first
        s = s.toggleChecked(LIST_ID, "flour", TRIP_START)
        s = s.toggleChecked(LIST_ID, "sugar", TRIP_START + 60_000)
        return s.setOpenAisle(LIST_ID, baking)
    }

    val catalog: IconCatalog get() = IconCatalog.default
}

/** The basket kind of a section, as the harness draws it: the store's, else the catalog's guess for its name. */
fun AppState.basketOf(aisleId: String, aisleName: String): BasketKind =
    store.aisleBaskets[aisleId] ?: IconCatalog.default.baskets.guess(aisleName)

/** The grouped list of the fixture's list. */
fun AppState.grouped(listId: String = MotionFixture.LIST_ID): GroupedList = ListGrouping.group(
    list = list(listId)!!,
    aisles = store.aisles(),
    basketFor = { basketOf(it.id, it.name) },
    styleFor = { store.aisleStyles[it.id] },
)

/** The engine's view of every section (§10: the Next card's stop and the "done" announcement). */
fun GroupedList.motionSections(): List<MotionSection> = sections.map { s ->
    MotionSection(
        id = s.id,
        position = s.position,
        displayName = s.displayName,
        fullName = s.aisle?.name ?: s.displayName,
        storeAisle = s.storeAisle,
        look = if (s.kind == SectionKind.UNSORTED) AisleLooks.everythingElse else AisleLooks.of(s.position),
    )
}

/**
 * A stand-in for WP5's ListViewModel: it applies changes through the core ops and tells [engine] what changed, the way
 * §19.2 says the real one will. [now] is the store's clock (ms), independent of the frame clock.
 */
class HarnessModel(initial: AppState, val engine: MotionChoreographer, var now: Long = MotionFixture.NOW, var album: Album = Album()) {
    var state: AppState by mutableStateOf(initial)
        private set

    val listId: String = MotionFixture.LIST_ID

    init {
        engine.listId = listId
        engine.setSections(state.grouped().motionSections())
        engine.celebrationSource = { CelebrationContent.from(state, listId, album, MotionFixture.catalog) }
    }

    fun grouped(): GroupedList = state.grouped(listId)

    fun item(id: String): GroceryItem = state.list(listId)!!.items.first { it.id == id }

    /** Replaces the state without telling the engine (setting a scene up); earlier finales no longer count. */
    fun set(s: AppState) {
        state = s
        eventMark = engine.eventLog.size
        engine.setSections(state.grouped().motionSections())
    }

    private var eventMark = 0

    /** A check-off tap (or its TalkBack / keyboard / swipe equivalent) on [itemId]. */
    fun toggle(itemId: String): ToggleEvent {
        syncFinale()
        val before = state
        val beforeGrouped = before.grouped(listId)
        now += 1000
        val after = before.toggleChecked(listId, itemId, now)
        state = after
        val afterGrouped = after.grouped(listId)
        engine.setSections(afterGrouped.motionSections())
        val item = after.list(listId)!!.items.first { it.id == itemId }
        val sectionId = afterGrouped.sections.first { s -> s.rows.any { it.id == itemId } }.id
        val beforeSection = beforeGrouped.sections.firstOrNull { it.id == sectionId }
        val afterSection = afterGrouped.sections.first { it.id == sectionId }
        val beforeList = before.list(listId)!!
        val afterList = after.list(listId)!!
        val checked = item.checked
        val aisleChanged = if (checked) afterSection.complete && beforeSection?.complete != true else beforeSection?.complete == true && !afterSection.complete
        val tripChanged = if (checked) TripRules.isComplete(afterList) && !TripRules.isComplete(beforeList) else TripRules.isComplete(beforeList) && !TripRules.isComplete(afterList)
        val position = afterSection.position
        val event = ToggleEvent(
            itemId = itemId,
            sectionId = sectionId,
            checked = checked,
            slot = if (checked) item.bookSlot else beforeList.items.first { it.id == itemId }.bookSlot,
            aisleCompleted = aisleChanged,
            tripCompleted = tripChanged,
            nextSectionId = if (checked && aisleChanged) nextStop(afterGrouped, sectionId) else null,
            personality = MotionFixture.catalog.personalityOf(item.icon),
            icon = item.iconRef(),
            holo = item.rarity != Rarity.NONE,
            aisleIndex = if (position > 0) position - 1 else -1,
            aisleWasOpen = beforeGrouped.openSectionId == sectionId,
            finaleShown = beforeList.trip?.finaleShownAt != null,
        )
        // D2 / item 90: the next stop opens automatically behind the Next card (the VM sets it; the engine holds the
        // finished aisle open until its fold, then keeps everything folded until the Next card shows).
        if (event.nextSectionId != null) state = state.setOpenAisle(listId, event.nextSectionId)
        // An un-check that re-opens an aisle keeps her in it (its chain is cancelled before the fold).
        if (!checked && aisleChanged) state = state.setOpenAisle(listId, sectionId)
        engine.onToggle(event)
        return event
    }

    /** Deletes [itemId] (a tear-off or the Edit sheet's Delete): a ListChange when it finishes an aisle or the trip. */
    fun delete(itemId: String): ListChange? = change(ChangeCause.DELETE) { it.removeItem(listId, itemId, now) }

    /** Moves [itemId] to [aisleId]. */
    fun move(itemId: String, aisleId: String?): ListChange? = change(ChangeCause.MOVE) { it.moveItemToAisle(listId, itemId, aisleId, now) }

    /** Opens [sectionId] (a tap on its strip). */
    fun open(sectionId: String?) {
        state = state.setOpenAisle(listId, sectionId)
    }

    /**
     * The list opens (§7.1, item 154): an owed finale (completed, never shown) plays at once as the in-place form when
     * the trip completed within 30 min of [now]; later it is only marked shown and PERFECT is pressed in place.
     */
    fun resume(): ListChange? {
        val list = state.list(listId)!!
        if (!TripRules.finaleOwed(list)) return null
        val c = ListChange(
            aisleCompleted = emptySet(),
            aisleReopened = emptySet(),
            tripCompleted = true,
            tripReopened = false,
            nextSectionId = null,
            cause = ChangeCause.RESUME,
            openSectionId = grouped().openSectionId,
            finaleShown = false,
            finaleStale = TripRules.finaleIsStale(list, now),
        )
        engine.onChange(c)
        return c
    }

    /** Mirrors FinaleKeeper on this model's state: the finale is marked shown once the engine started or marked it. */
    fun syncFinale() {
        val list = state.list(listId) ?: return
        if (list.trip?.completedAt == null || list.trip?.finaleShownAt != null) return
        val shown = engine.eventLog.drop(eventMark).any {
            (it is MotionEvent.CelebrationStarted && it.listId == listId) || (it is MotionEvent.FinaleMarked && it.listId == listId)
        }
        if (shown) state = state.markFinaleShown(listId, now)
    }

    private fun change(cause: ChangeCause, op: (AppState) -> AppState): ListChange? {
        syncFinale()
        val before = state
        val bg = before.grouped(listId)
        now += 1000
        val after = op(before)
        state = after
        val ag = after.grouped(listId)
        engine.setSections(ag.motionSections())
        val completed = ag.sections.filter { s -> s.complete && bg.sections.firstOrNull { it.id == s.id }?.complete != true }.map { it.id }.toSet()
        val reopened = bg.sections.filter { s -> s.complete && ag.sections.firstOrNull { it.id == s.id }?.complete == false }.map { it.id }.toSet()
        val bl = before.list(listId)!!
        val al = after.list(listId)!!
        val tripDone = TripRules.isComplete(al) && !TripRules.isComplete(bl)
        val tripReopened = TripRules.isComplete(bl) && !TripRules.isComplete(al)
        if (completed.isEmpty() && reopened.isEmpty() && !tripDone && !tripReopened) return null
        val c = ListChange(
            aisleCompleted = completed,
            aisleReopened = reopened,
            tripCompleted = tripDone,
            tripReopened = tripReopened,
            nextSectionId = completed.firstOrNull()?.let { nextStop(ag, it) },
            cause = cause,
            openSectionId = bg.openSectionId,
            finaleShown = al.trip?.finaleShownAt != null,
        )
        if (c.nextSectionId != null) state = state.setOpenAisle(listId, c.nextSectionId)
        if (reopened.isNotEmpty() && completed.isEmpty()) state = state.setOpenAisle(listId, reopened.first())
        engine.onChange(c)
        return c
    }

    companion object {
        /** The next stop in walking order with something left after [from], wrapping (§10). */
        fun nextStop(g: GroupedList, from: String): String? {
            val order = g.sections
            val i = order.indexOfFirst { it.id == from }
            for (k in 1..order.size) {
                val s = order[(i + k).mod(order.size)]
                if (s.toGet > 0) return s.id
            }
            return null
        }
    }
}

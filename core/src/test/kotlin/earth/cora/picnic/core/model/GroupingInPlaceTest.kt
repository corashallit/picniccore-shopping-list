package earth.cora.picnic.core.model

import earth.cora.picnic.core.SaturdayShop
import earth.cora.picnic.core.SaturdayShop.LIST_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Grouping in place (docs/DESIGN_STICKER.md §19.1, D1, D2; checklist 82, 88, 89, 299, 300). */
class GroupingInPlaceTest {
    private val aisles = Aisles.parse("Produce\nBakery\nAisle 2 - Baking\nDairy & eggs\nFrozen")

    private fun item(id: String, aisle: String?, at: Long, checked: Boolean = false, checkedAt: Long? = null) =
        GroceryItem(id = id, name = id, aisleId = aisle, icon = "paper_bag", addedAt = at, checked = checked, checkedAt = checkedAt)

    private fun group(items: List<GroceryItem>, openAisle: String? = null, styles: Map<String, AisleStyle> = emptyMap()) =
        ListGrouping.group(GroceryList("l", "List", 0, items, openAisle = openAisle), aisles, { BasketKind.FRUIT_CRATE }, { styles[it.id] })

    @Test
    fun emptyListHasNoSections() {
        val g = group(emptyList())
        assertTrue(g.sections.isEmpty())
        assertEquals(0, g.total)
        assertEquals(0, g.checked)
        assertEquals(0, g.stops)
        assertNull(g.currentSectionId)
        assertNull(g.openSectionId)
        assertNull(g.nowSectionId)
    }

    @Test
    fun rowsStayInPlace() {
        val g = group(
            listOf(
                item("milk", "dairy-eggs", 1),
                item("lemon", "produce", 2),
                item("mystery", null, 3),
                item("gone", "vanished-aisle", 4),
                item("bread", "bakery", 5, checked = true),
                item("apple", "produce", 0, checked = true),
            ),
        )
        assertEquals(listOf(SectionIds.UNSORTED, "produce", "bakery", "dairy-eggs"), g.sections.map { it.id }, "no CHECKED section (D1)")
        assertTrue(g.sections.none { it.id == SectionIds.CHECKED })
        assertEquals(listOf("mystery", "gone"), g.sections[0].rows.map { it.id }, "null and vanished aisle ids alike")
        assertEquals(listOf("apple", "lemon"), g.sections[1].rows.map { it.id }, "every row in addedAt order, checked ones in place")
        assertEquals(listOf("bread"), g.sections[2].rows.map { it.id }, "a finished aisle keeps its rows")
        assertEquals(6, g.total)
        assertEquals(2, g.checked)
        assertEquals(4, g.stops)
    }

    @Test
    fun unsortedFirst() {
        val g = group(listOf(item("lemon", "produce", 3), item("loose", null, 5)))
        val unsorted = g.sections.first()
        assertEquals(SectionKind.UNSORTED, unsorted.kind)
        assertEquals(SectionIds.UNSORTED, unsorted.id)
        assertEquals(0, unsorted.position)
        assertNull(unsorted.aisle)
        assertNull(unsorted.storeAisle)
        assertEquals(ListGrouping.UNSORTED_TITLE, unsorted.displayName)
        assertEquals(BasketKind.WIRE_BASKET, unsorted.basket, "Everything else has the wire shopping basket (§5.2)")
        assertEquals(2, g.stops, "Everything else is a stop")
    }

    @Test
    fun unsortedStaysWhenComplete() {
        val g = group(listOf(item("loose", null, 5, checked = true), item("lemon", "produce", 3)))
        val unsorted = g.sections.first()
        assertEquals(SectionKind.UNSORTED, unsorted.kind)
        assertTrue(unsorted.complete, "all gotten, still listed (folded, lidded, ribboned)")
        assertEquals(listOf("loose"), unsorted.gotten.map { it.id })
        assertEquals(2, g.stops)
        assertEquals("produce", g.currentSectionId, "an aisle with something left beats Everything else")
    }

    @Test
    fun gottenIsInCheckOffOrderAndCountsAreRight() {
        val g = group(
            listOf(
                item("lemon", "produce", 4),
                item("apple", "produce", 2, checked = true, checkedAt = 300),
                item("spinach", "produce", 1),
                item("kale", "produce", 3, checked = true, checkedAt = 100),
                item("plum", "produce", 5, checked = true),
            ),
        )
        val produce = g.sections.single()
        assertEquals(listOf("spinach", "apple", "kale", "lemon", "plum"), produce.rows.map { it.id })
        assertEquals(listOf("plum", "kale", "apple"), produce.gotten.map { it.id }, "checkedAt order; a missing checkedAt counts as addedAt")
        assertEquals(2, produce.toGet)
        assertEquals(5, produce.total)
        assertFalse(produce.complete)
        assertEquals(1, produce.position)
        assertEquals(BasketKind.FRUIT_CRATE, produce.basket)
    }

    @Test
    fun displayNameAndStoreAisle() {
        val g = group(
            listOf(item("flour", "aisle-2-baking", 1), item("bread", "bakery", 2)),
            styles = mapOf("bakery" to AisleStyle(storeAisle = 9)),
        )
        val baking = g.sections.single { it.id == "aisle-2-baking" }
        assertEquals("Baking", baking.displayName, "the store prefix is stripped from the tab (D9)")
        assertEquals(2, baking.storeAisle)
        assertEquals(3, baking.position, "the number circle is the walking position")
        val bakery = g.sections.single { it.id == "bakery" }
        assertEquals("Bakery", bakery.displayName)
        assertEquals(9, bakery.storeAisle, "the aisle editor's number wins over the name")
    }

    @Test
    fun currentSection() {
        val items = listOf(
            item("bread", "bakery", 1, checked = true),
            item("milk", "dairy-eggs", 2),
            item("peas", "frozen", 3),
            item("loose", null, 4),
        )
        assertEquals("dairy-eggs", group(items).currentSectionId, "the first stop in walking order with something left")
        val onlyLoose = group(listOf(item("bread", "bakery", 1, checked = true), item("loose", null, 4)))
        assertEquals(SectionIds.UNSORTED, onlyLoose.currentSectionId, "Everything else only when it is all that is left")
        val done = group(listOf(item("bread", "bakery", 1, checked = true)))
        assertNull(done.currentSectionId)
        assertNull(done.openSectionId)
        assertNull(done.nowSectionId)
        assertTrue(done.sections.single().complete)
    }

    @Test
    fun openAisle() {
        val items = listOf(
            item("bread", "bakery", 1, checked = true),
            item("milk", "dairy-eggs", 2),
            item("peas", "frozen", 3),
            item("loose", null, 4),
        )
        val auto = group(items)
        assertEquals("dairy-eggs", auto.openSectionId, "null = automatic: the current stop")
        assertEquals("dairy-eggs", auto.nowSectionId)

        val none = group(items, openAisle = "")
        assertNull(none.openSectionId, "\"\" = all folded")
        assertEquals("dairy-eggs", none.nowSectionId)

        val bakery = group(items, openAisle = "bakery")
        assertEquals("bakery", bakery.openSectionId, "reopening a finished aisle shows its rows")
        assertEquals("dairy-eggs", bakery.nowSectionId, "a finished open aisle hands the now ring to the current one")

        val frozen = group(items, openAisle = "frozen")
        assertEquals("frozen", frozen.openSectionId, "opening a strip folds the others: only one id is open")
        assertEquals("frozen", frozen.nowSectionId)

        assertEquals(SectionIds.UNSORTED, group(items, openAisle = SectionIds.UNSORTED).openSectionId)
        assertEquals("dairy-eggs", group(items, openAisle = "vanished").openSectionId, "a stale id falls back to automatic")
        assertEquals("dairy-eggs", group(items, openAisle = "aisle-2-baking").openSectionId, "an aisle with no items this trip is not a stop")
    }

    /**
     * Item 179 (§4.13, §8.4): the "now" ring is on the open aisle while it has items left, else on the automatic current
     * aisle; it moves to the next stop as the stop it was on is done, and leaves the page once the trip is.
     */
    @Test
    fun nowSection() {
        val s = SaturdayShop.state()
        fun now(state: AppState) = ListGrouping.group(state.list(LIST_ID)!!, state.store.aisles(), { BasketKind.DEFAULT }).nowSectionId
        assertEquals("aisle-2-baking", now(s), "she is standing at stop 4: the open aisle has Baking powder left")

        // Baking powder completes Baking: the open aisle has nothing left, so the ring goes to the automatic current
        // stop (the first in walking order with something left), never stays on a done stop.
        val bakingDone = s.toggleChecked(LIST_ID, "baking-powder", SaturdayShop.NOW + 60_000)
        assertTrue(ListGrouping.group(bakingDone.list(LIST_ID)!!, bakingDone.store.aisles(), { BasketKind.DEFAULT }).sections.single { it.id == "aisle-2-baking" }.complete)
        assertEquals("produce", now(bakingDone))

        // Behind the Next card the next stop opens (D2): the ring moves there with it.
        val atBreakfast = bakingDone.setOpenAisle(LIST_ID, "breakfast")
        assertEquals("breakfast", now(atBreakfast))

        // Folding every aisle leaves the ring on the automatic current stop.
        assertEquals("produce", now(bakingDone.setOpenAisle(LIST_ID, "")))

        // The trip done: no stop is "now".
        var done = atBreakfast
        for (item in done.list(LIST_ID)!!.items.filter { !it.checked }) done = done.toggleChecked(LIST_ID, item.id, SaturdayShop.NOW + 120_000)
        assertNull(now(done))
    }

    @Test
    fun setOpenAisleStoresTheChoice() {
        val s = SaturdayShop.unchecked()
        assertEquals("bakery", s.setOpenAisle(LIST_ID, "bakery").list(LIST_ID)!!.openAisle)
        assertEquals("", s.setOpenAisle(LIST_ID, "").list(LIST_ID)!!.openAisle)
        assertEquals(s, s.setOpenAisle(LIST_ID, null))
    }

    @Test
    fun theFixture() {
        val s = SaturdayShop.state()
        val g = ListGrouping.group(s.list(LIST_ID)!!, s.store.aisles(), { s.store.aisleBaskets[it.id] ?: BasketKind.DEFAULT })
        assertEquals(
            listOf("produce", "bakery", "deli-meat", "aisle-2-baking", "breakfast", "dairy-eggs", "frozen", "household"),
            g.sections.map { it.id },
        )
        assertEquals(24, g.total)
        assertEquals(10, g.checked)
        assertEquals(8, g.stops)
        assertEquals("produce", g.currentSectionId, "the automatic choice would be Produce")
        assertEquals("aisle-2-baking", g.openSectionId, "she is standing at stop 4")
        assertEquals("aisle-2-baking", g.nowSectionId)
        val deli = g.sections.single { it.id == "deli-meat" }
        assertTrue(deli.complete, "Deli & meat is done (stamp 3 in)")
        val baking = g.sections.single { it.id == "aisle-2-baking" }
        assertEquals("Baking", baking.displayName)
        assertEquals(2, baking.storeAisle, "tab 4 Baking + chip A2")
        assertEquals(4, baking.position)
        assertEquals(listOf("flour", "sugar"), baking.gotten.map { it.id })
        assertEquals(1, baking.toGet)
        assertEquals(listOf("bananas", "carrots", "broccoli"), g.sections.first().gotten.map { it.id }, "check-off order")
    }
}

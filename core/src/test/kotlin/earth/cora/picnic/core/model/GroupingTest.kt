package earth.cora.picnic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Grouping in place (§19.1, D1, D2): the phase-0 shape. WP3 extends this as GroupingInPlaceTest.
 */
class GroupingTest {
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
    fun sectionsFollowWalkingOrderWithUnsortedFirstAndRowsStayInPlace() {
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
        assertEquals(listOf("mystery", "gone"), g.sections[0].rows.map { it.id })
        assertEquals(listOf("apple", "lemon"), g.sections[1].rows.map { it.id }, "every row in addedAt order, checked ones in place")
        assertEquals(6, g.total)
        assertEquals(2, g.checked)
        assertEquals(4, g.stops)
    }

    @Test
    fun unsortedIsListedFirstWheneverItHasAnyItem() {
        val g = group(listOf(item("loose", null, 5, checked = true), item("lemon", "produce", 3)))
        val unsorted = g.sections.first()
        assertEquals(SectionKind.UNSORTED, unsorted.kind)
        assertTrue(unsorted.complete, "all gotten, still listed (folded, lid shut)")
        assertEquals(0, unsorted.position)
        assertNull(unsorted.storeAisle)
        assertEquals(ListGrouping.UNSORTED_TITLE, unsorted.displayName)
        assertEquals(BasketKind.DEFAULT, unsorted.basket)
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
    fun openAisleNullIsAutomaticEmptyIsNoneElseTheId() {
        val items = listOf(
            item("bread", "bakery", 1, checked = true),
            item("milk", "dairy-eggs", 2),
            item("peas", "frozen", 3),
            item("loose", null, 4),
        )
        val auto = group(items)
        assertEquals("dairy-eggs", auto.currentSectionId, "the first stop in walking order with something left")
        assertEquals("dairy-eggs", auto.openSectionId)
        assertEquals("dairy-eggs", auto.nowSectionId)

        val none = group(items, openAisle = "")
        assertNull(none.openSectionId)
        assertEquals("dairy-eggs", none.nowSectionId)

        val bakery = group(items, openAisle = "bakery")
        assertEquals("bakery", bakery.openSectionId)
        assertEquals("dairy-eggs", bakery.nowSectionId, "a finished open aisle hands the now ring to the current one")

        val frozen = group(items, openAisle = "frozen")
        assertEquals("frozen", frozen.nowSectionId)

        assertEquals("dairy-eggs", group(items, openAisle = "vanished").openSectionId, "a stale id falls back to automatic")

        val onlyLoose = group(listOf(item("bread", "bakery", 1, checked = true), item("loose", null, 4)))
        assertEquals(SectionIds.UNSORTED, onlyLoose.currentSectionId, "Everything else only when it is all that is left")

        val done = group(listOf(item("bread", "bakery", 1, checked = true)))
        assertNull(done.currentSectionId)
        assertNull(done.openSectionId)
        assertTrue(done.sections.single().complete)
    }

    @Test
    fun equalTimestampsKeepListOrder() {
        val g = group(listOf(item("b", "produce", 1), item("a", "produce", 1), item("c", "produce", 1)))
        assertEquals(listOf("b", "a", "c"), g.sections.single().rows.map { it.id })
    }
}

package earth.cora.picnic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupingTest {
    private val aisles = Aisles.parse("Produce\nBakery\nDairy & eggs\nFrozen")

    private fun item(id: String, aisle: String?, at: Long, checked: Boolean = false) =
        GroceryItem(id = id, name = id, aisleId = aisle, icon = "paper_bag", addedAt = at, checked = checked)

    private fun group(items: List<GroceryItem>, collapsed: Set<String> = emptySet()) =
        ListGrouping.group(GroceryList("l", "List", 0, items, collapsed), aisles) { BasketKind.FRUIT_CRATE }

    @Test
    fun emptyListHasNoSections() {
        val g = group(emptyList())
        assertTrue(g.sections.isEmpty())
        assertEquals(0, g.total)
        assertEquals(0, g.remaining)
    }

    @Test
    fun sectionsFollowWalkingOrderWithUnsortedFirstAndCheckedLast() {
        val g = group(
            listOf(
                item("milk", "dairy-eggs", 1),
                item("lemon", "produce", 2),
                item("mystery", null, 3),
                item("gone", "vanished-aisle", 4),
                item("bread", "bakery", 5, checked = true),
                item("apple", "produce", 0),
            ),
        )
        assertEquals(listOf(SectionIds.UNSORTED, "produce", "bakery", "dairy-eggs", SectionIds.CHECKED), g.sections.map { it.id })
        assertEquals(listOf("mystery", "gone"), g.sections[0].items.map { it.id })
        assertEquals(listOf("apple", "lemon"), g.sections[1].items.map { it.id }, "oldest first")
        assertEquals(6, g.total)
        assertEquals(5, g.remaining)
    }

    @Test
    fun aisleWithOnlyCheckedItemsIsClearedAndCheckedSectionIsInWalkingOrder() {
        val g = group(
            listOf(
                item("ice cream", "frozen", 1, checked = true),
                item("bread", "bakery", 2, checked = true),
                item("bagel", "bakery", 0, checked = true),
                item("loose", null, 5, checked = true),
                item("lemon", "produce", 3),
            ),
        )
        val bakery = g.sections.single { it.id == "bakery" }
        assertTrue(bakery.items.isEmpty())
        assertTrue(bakery.cleared)
        assertEquals(2, bakery.checkedCount)
        assertEquals(listOf("bagel", "bread"), bakery.checkedItems.map { it.id }, "a cleared aisle's basket is full")
        assertFalse(g.sections.single { it.id == "produce" }.cleared)
        // Unsorted has no unchecked items, so it is absent; its checked item still shows under CHECKED (first).
        assertTrue(g.sections.none { it.id == SectionIds.UNSORTED })
        val checked = g.sections.last()
        assertEquals(SectionKind.CHECKED, checked.kind)
        assertEquals(listOf("loose", "bagel", "bread", "ice cream"), checked.items.map { it.id })
        assertTrue(checked.checkedItems.isEmpty(), "CHECKED lists the gotten items as its items")
        assertEquals(0, checked.checkedCount)
        assertFalse(checked.cleared)
    }

    @Test
    fun checkedItemsAreWhatHasBeenGottenFromEachSectionNeverWhatIsLeftToGet() {
        val g = group(
            listOf(
                item("lemon", "produce", 4),
                item("apple", "produce", 2, checked = true),
                item("spinach", "produce", 1),
                item("kale", "produce", 3, checked = true),
                item("milk", "dairy-eggs", 5),
                item("loose", null, 6),
                item("tape", null, 7, checked = true),
            ),
        )
        val produce = g.sections.single { it.id == "produce" }
        assertEquals(listOf("spinach", "lemon"), produce.items.map { it.id }, "still to get")
        assertEquals(listOf("apple", "kale"), produce.checkedItems.map { it.id }, "gotten, oldest first")
        assertEquals(2, produce.checkedCount)
        assertTrue(produce.checkedItems.all { it.checked })
        assertTrue(produce.items.none { it.checked })
        assertFalse(produce.cleared)

        val dairy = g.sections.single { it.id == "dairy-eggs" }
        assertTrue(dairy.checkedItems.isEmpty(), "nothing gotten yet: an empty basket")
        assertEquals(0, dairy.checkedCount)

        val unsorted = g.sections.first()
        assertEquals(SectionKind.UNSORTED, unsorted.kind)
        assertEquals(listOf("tape"), unsorted.checkedItems.map { it.id })

        // The CHECKED section is unchanged: every gotten item, in walking order (Unsorted first).
        assertEquals(listOf("tape", "apple", "kale"), g.sections.last().items.map { it.id })
    }

    @Test
    fun basketCollapsedPositionAndTitles() {
        val g = group(listOf(item("x", null, 1), item("milk", "dairy-eggs", 2), item("done", "produce", 3, true)), setOf("dairy-eggs", SectionIds.UNSORTED))
        val unsorted = g.sections.first()
        assertEquals(BasketKind.DEFAULT, unsorted.basket)
        assertEquals(0, unsorted.position)
        assertTrue(unsorted.collapsed)
        assertEquals(ListGrouping.UNSORTED_TITLE, unsorted.title)
        val dairy = g.sections.single { it.id == "dairy-eggs" }
        assertEquals(BasketKind.FRUIT_CRATE, dairy.basket)
        assertEquals(3, dairy.position)
        assertEquals("Dairy & eggs", dairy.title)
        assertTrue(dairy.collapsed)
        assertFalse(g.sections.single { it.id == "produce" }.collapsed)
        assertEquals(ListGrouping.CHECKED_TITLE, g.sections.last().title)
    }

    @Test
    fun equalTimestampsKeepListOrder() {
        val g = group(listOf(item("b", "produce", 1), item("a", "produce", 1), item("c", "produce", 1)))
        assertEquals(listOf("b", "a", "c"), g.sections.single().items.map { it.id })
    }
}

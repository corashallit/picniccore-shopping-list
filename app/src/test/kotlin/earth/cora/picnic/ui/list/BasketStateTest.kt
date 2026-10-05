package earth.cora.picnic.ui.list

import earth.cora.picnic.core.model.toggleChecked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What each aisle's basket holds and counts (docs/DESIGN_STICKER.md §5.3, R4; items 104, 107). */
class BasketStateTest {
    /** Item 104: a basket holds only checked items, in check-off order; nothing still to get is in, on or beside it. */
    @Test
    fun onlyGotten() {
        val ui = ListFixture.ui()
        for (s in ui.sections) {
            val checked = s.rows.filter { it.checked }.map { it.id }.toSet()
            assertEquals("${s.id}: the minis are exactly the checked rows", checked, s.gotten.map { it.itemId }.toSet())
        }
        val produce = ui.sections.first { it.id == "produce" }
        assertEquals("check-off order (bananas 09:58, carrots 10:00, broccoli 10:01)", listOf("bananas", "carrots", "broccoli"), produce.gotten.map { it.itemId })
        val household = ui.sections.first { it.id == "household" }
        assertTrue("nothing gotten yet: the basket is empty", household.gotten.isEmpty())

        // An un-check takes its mini out; a check puts it on top.
        val after = ListFixture.ui(ListFixture.state().toggleChecked(ListFixture.LIST_ID, "carrots", ListFixture.NOW).toggleChecked(ListFixture.LIST_ID, "apples", ListFixture.NOW + 1))
        assertEquals(listOf("bananas", "broccoli", "apples"), after.sections.first { it.id == "produce" }.gotten.map { it.itemId })
    }

    /** Item 107: the badge counts what is still to get and is 0 (hidden) once the aisle is done. */
    @Test
    fun badgeCountsToGet() {
        val ui = ListFixture.ui()
        assertEquals(
            listOf("produce" to 2, "bakery" to 2, "deli-meat" to 0, "aisle-2-baking" to 1, "breakfast" to 1, "dairy-eggs" to 2, "frozen" to 2, "household" to 4),
            ui.sections.map { it.id to it.toGet },
        )
        val deli = ui.sections.first { it.id == "deli-meat" }
        assertTrue("Deli & meat is done", deli.complete)
        for (s in ui.sections) assertEquals(s.id, s.rows.count { !it.checked }, s.toGet)
    }

    /** A shiny item's mini stays holo in its basket (item 110's data). */
    @Test
    fun shinyMiniStaysHolo() {
        val ui = ListFixture.ui(ListFixture.state().toggleChecked(ListFixture.LIST_ID, "maple", ListFixture.NOW))
        val maple = ui.sections.first { it.id == "breakfast" }.gotten.first { it.itemId == "maple" }
        assertTrue(maple.holo)
    }
}

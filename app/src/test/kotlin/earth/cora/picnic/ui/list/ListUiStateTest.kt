package earth.cora.picnic.ui.list

import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.toggleChecked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list's UI state built from the Appendix A fixture (docs/DESIGN_STICKER.md §4.10, §6.1, §7.3; items 77, 88, 296). */
class ListUiStateTest {
    private val ui = ListFixture.ui()

    /** Item 77: the number circle is the walking position (1–8 in the fixture), never the store's own aisle number. */
    @Test
    fun numberIsWalkingPosition() {
        assertEquals(
            listOf("produce" to 1, "bakery" to 2, "deli-meat" to 3, "aisle-2-baking" to 4, "breakfast" to 5, "dairy-eggs" to 6, "frozen" to 7, "household" to 8),
            ui.sections.map { it.id to it.position },
        )
        assertEquals("the stamp page numbers the stops the same way", (1..8).toList(), ui.book!!.stamps.map { it.position })
        assertEquals("8 stops", 8, ui.stops)
    }

    /** Item 77: "Aisle 2 - Baking" shows as "Baking" with an "A2" chip; TalkBack keeps the full name. */
    @Test
    fun displayNameStripsStorePrefix() {
        val baking = ui.sections.first { it.id == "aisle-2-baking" }
        assertEquals("Baking", baking.displayName)
        assertEquals(2, baking.storeAisle)
        assertEquals("Aisle 2 - Baking", baking.fullName)
        val produce = ui.sections.first { it.id == "produce" }
        assertEquals("Produce", produce.displayName)
        assertNull("no store number, no chip", produce.storeAisle)
        assertEquals("the book's stamps use the display name", "Baking", ui.book!!.stamps.first { it.sectionId == "aisle-2-baking" }.displayName)
    }

    /** Item 88: checked rows stay in their aisle, in place; there is no CHECKED section. */
    @Test
    fun checkedRowsStayInPlace() {
        val produce = ui.sections.first { it.id == "produce" }
        assertEquals(listOf("strawberries", "bananas", "apples", "carrots", "broccoli"), produce.rows.map { it.id })
        assertEquals(listOf(false, true, false, true, true), produce.rows.map { it.checked })
        assertTrue(ui.sections.none { it.id.contains("checked", ignoreCase = true) })
        assertEquals(24, ui.sections.sumOf { it.rows.size })
    }

    /**
     * Item 296: every detail line is metric after normalisation; only a recipe amount after the quantity's "·" may keep
     * a cooking measure (cup, tbsp, tsp, pinch).
     */
    @Test
    fun detailMetric() {
        var s = ListFixture.state()
        val imperial = listOf(
            GroceryItem(id = "x1", name = "Ground beef", quantity = "2 lb pack", aisleId = "deli-meat", icon = "deli_meat", iconFit = IconFit.EXACT, addedAt = 1L),
            GroceryItem(id = "x2", name = "Cheddar", quantity = "12 oz block", aisleId = "dairy-eggs", icon = "butter", iconFit = IconFit.EXACT, addedAt = 2L),
            GroceryItem(id = "x3", name = "Apple cider", quantity = "1 gal jug", aisleId = "produce", icon = "apple", iconFit = IconFit.EXACT, addedAt = 3L),
            GroceryItem(id = "x4", name = "Icing sugar", quantity = "1 kg bag · 2 cups", aisleId = "aisle-2-baking", icon = "sugar", iconFit = IconFit.EXACT, addedAt = 4L),
            GroceryItem(id = "x5", name = "Chocolate chips", quantity = "2-lb bag · 1 tbsp", note = "about 1 lb if loose", aisleId = "aisle-2-baking", icon = "flour", iconFit = IconFit.EXACT, addedAt = 5L),
        )
        for (it in imperial) s = s.addItems(ListFixture.LIST_ID, listOf(it), ListFixture.NOW).first
        val rows = ListFixture.ui(s).sections.flatMap { it.rows }
        val unit = Regex("\\b(lb|lbs|oz|fl oz|qt|gal|gallons?|pounds?|ounces?)\\b")
        val cooking = Regex("\\b(cups?|tbsp|tsp)\\b")
        for (r in rows) {
            val line = r.detail ?: continue
            assertFalse("${r.name}: \"$line\" has no imperial unit", unit.containsMatchIn(line))
            assertFalse("${r.name}: \"$line\" keeps cooking measures only after the dot", cooking.containsMatchIn(line.substringBefore('·')))
        }
        assertEquals("907 g pack", rows.first { it.id == "x1" }.detail)
        assertEquals("340 g block", rows.first { it.id == "x2" }.detail)
        assertEquals("1 kg bag · 2 cups", rows.first { it.id == "x4" }.detail)
        assertEquals("907 g bag · 1 tbsp · about 454 g if loose", rows.first { it.id == "x5" }.detail)
        assertEquals("225 g tin · from your recipe", rows.first { it.id == "baking-powder" }.detail)
    }

    /** The app bar's count, the LCD and the book agree (§4.2, §7.3). */
    @Test
    fun countsAgree() {
        assertEquals(24, ui.total)
        assertEquals(14, ui.remaining)
        assertEquals(10, ui.checked)
        assertEquals(10, ui.book!!.checked)
        assertEquals(24, ui.book!!.total)
        assertEquals(10, ui.lcd.count)
        assertEquals("Sat 3 Oct", ui.book!!.dateLabel)
        assertEquals("aisle-2-baking", ui.openSectionId)
        assertEquals("Hillcrest Market", ui.storeName)
    }

    /** A checked row keeps its book slot; an unchecked one has none (§7.2). */
    @Test
    fun slotsFollowTheBook() {
        val rows = ui.sections.flatMap { it.rows }
        assertTrue(rows.filter { it.checked }.all { it.slot != null })
        assertTrue(rows.filter { !it.checked }.all { it.slot == null })
        assertEquals(rows.filter { it.checked }.mapNotNull { it.slot }.toSet(), ui.book!!.takenSlots)
        val after = ListFixture.ui(ListFixture.state().toggleChecked(ListFixture.LIST_ID, "baking-powder", ListFixture.NOW))
        assertEquals("the next twin takes the lowest free spot", 10, after.row("baking-powder")!!.slot)
    }

    /** Items 71 and 98 (§4.14, egg 11): the season rides on the list itself, so an empty list's add bar wears its tape. */
    @Test
    fun seasonWithoutABook() {
        val ui = ListFixture.ui(ListFixture.empty())
        assertNull(ui.book)
        assertEquals(earth.cora.picnic.core.calendar.Season.THANKSGIVING, ui.season)
        assertEquals(ui.season, ListFixture.ui().book!!.season)
    }

    /**
     * Item 315 (§7.1, §7.8): what she writes down after a finished shop stays out of its book: the spots, the stamps,
     * PERFECT and the Trip book sheet's snapshot keep the shop; the LCD counts the list.
     */
    @Test
    fun theBookKeepsTheShopAfterItsFinale() {
        val evening = ListFixture.TRIP_START + 6 * ListFixture.HOUR
        val salt = GroceryItem(id = "salt", name = "Salt", aisleId = "aisle-2-baking", icon = "salt", iconFit = IconFit.EXACT, addedAt = evening)
        val app = ListFixture.done().addItems(ListFixture.LIST_ID, listOf(salt), evening).first
        val ui = ListFixture.ui(app, now = evening)
        val book = ui.book!!
        assertEquals(24, book.total)
        assertTrue(book.perfect)
        assertTrue(book.full)
        assertTrue(book.stamps.all { it.stamped })
        assertTrue("no now ring on a finished book", book.stamps.none { it.now })
        assertEquals(25, ui.lcd.total)
        assertEquals(24, ui.tripBook!!.total)
        assertTrue(ui.tripBook!!.perfect)
        // Baking's strip has Salt to get; its stamp in the book stays stamped.
        assertEquals(1, ui.sections.first { it.id == "aisle-2-baking" }.toGet)
    }
}

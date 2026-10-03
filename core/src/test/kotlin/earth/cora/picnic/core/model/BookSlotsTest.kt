package earth.cora.picnic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Book spots (docs/DESIGN_STICKER.md §7.2; checklist 136, 302). */
class BookSlotsTest {
    private fun item(id: String, checked: Boolean = false, slot: Int? = null, checkedAt: Long? = null) =
        GroceryItem(id = id, name = id, icon = "apple", addedAt = 0, checked = checked, bookSlot = slot, checkedAt = checkedAt ?: if (checked) 1 else null)

    private fun state(vararg ids: String) = AppState(
        lists = listOf(GroceryList("l", "List", 0, ids.map { item(it) })),
        currentListId = "l",
    )

    private fun AppState.slots(): Map<String, Int?> = list("l")!!.items.associate { it.id to it.bookSlot }

    @Test
    fun aCheckTakesTheLowestFreeSpot() {
        assertEquals(0, BookSlots.assign(listOf(item("a"), item("b")), "a"))
        assertEquals(1, BookSlots.assign(listOf(item("a", true, 0), item("b"), item("c", true, 2)), "b"))
        assertEquals(0, BookSlots.assign(listOf(item("a", true, 0)), "a"), "its own spot does not count as taken")
        assertEquals(0, BookSlots.assign(listOf(item("a", false, 0), item("b")), "b"), "an unchecked item holds no spot")
    }

    @Test
    fun anUncheckLeavesAHoleTheNextCheckFills() {
        val s = state("a", "b", "c", "d").toggleChecked("l", "a", 1).toggleChecked("l", "b", 2).toggleChecked("l", "c", 3)
        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2, "d" to null), s.slots())
        val holed = s.toggleChecked("l", "b", 4)
        assertEquals(mapOf("a" to 0, "b" to null, "c" to 2, "d" to null), holed.slots(), "later stickers never shift")
        val filled = holed.toggleChecked("l", "d", 5)
        assertEquals(1, filled.slots()["d"], "the next check fills the hole")
        assertEquals(3, filled.toggleChecked("l", "b", 6).slots()["b"])
    }

    @Test
    fun addsKeepEverySpot() {
        val s = state("a", "b").toggleChecked("l", "b", 1)
        val added = s.addItems("l", listOf(item("c"), item("d")), 2).first
        assertEquals(mapOf("a" to null, "b" to 0, "c" to null, "d" to null), added.slots(), "new spots open at the end")
        assertEquals(1, added.toggleChecked("l", "d", 3).slots()["d"])
    }

    @Test
    fun deletesCompactStably() {
        // 5 spots; stickers in 0, 3 and 4. Deleting two unchecked items leaves 3 spots: the highest stickers move
        // into the lowest holes, in order.
        val s = state("a", "b", "c", "d", "e")
            .toggleChecked("l", "a", 1).toggleChecked("l", "b", 2).toggleChecked("l", "c", 3).toggleChecked("l", "d", 4).toggleChecked("l", "e", 5)
            .toggleChecked("l", "b", 6).toggleChecked("l", "c", 7)
        assertEquals(mapOf("a" to 0, "b" to null, "c" to null, "d" to 3, "e" to 4), s.slots())
        val one = s.removeItem("l", "b", 8)
        assertEquals(mapOf("a" to 0, "c" to null, "d" to 3, "e" to 1), one.slots(), "4 spots: the sticker in spot 4 moves to hole 1")
        val two = one.removeItem("l", "c", 9)
        assertEquals(mapOf("a" to 0, "d" to 2, "e" to 1), two.slots(), "3 spots: spot 3 moves to hole 2; spot 1 stays")
    }

    @Test
    fun deleteRestore() {
        val s = state("a", "b", "c", "d").toggleChecked("l", "a", 1).toggleChecked("l", "b", 2).toggleChecked("l", "c", 3)
        val b = s.findItem("b")!!
        val removed = s.removeItem("l", "b", 4)
        assertEquals(mapOf("a" to 0, "c" to 2, "d" to null), removed.slots(), "deleting a checked item frees its spot")
        val restored = removed.restoreItem("l", b, 1, 5)
        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2, "d" to null), restored.slots(), "undo reclaims it")

        // When the spot was taken meanwhile, undo takes the lowest free one.
        val taken = removed.toggleChecked("l", "d", 6)
        assertEquals(1, taken.slots()["d"])
        val other = taken.restoreItem("l", b, 1, 7)
        assertEquals(mapOf("a" to 0, "b" to 3, "c" to 2, "d" to 1), other.slots())

        // A restored unchecked item holds no spot, whatever it carried.
        assertNull(removed.restoreItem("l", b.copy(checked = false), 1, 8).slots()["b"])
    }

    @Test
    fun deletingTheHighestStickerWhenSpotsShrinkMovesNothingElse() {
        val s = state("a", "b", "c").toggleChecked("l", "a", 1).toggleChecked("l", "b", 2).toggleChecked("l", "c", 3)
        val after = s.removeItem("l", "b", 4)
        assertEquals(mapOf("a" to 0, "c" to 1), after.slots(), "2 spots: spot 2 moves into the hole spot 1 left")
    }

    @Test
    fun compactContract() {
        val clean = listOf(item("a", true, 0), item("b"), item("c", true, 2))
        assertSame(clean, BookSlots.compact(clean))
        val messy = listOf(
            item("a", true, 1),
            item("b", true, 1, checkedAt = 9),
            item("c", true, null, checkedAt = 3),
            item("d", false, 0),
            item("e", true, 8),
        )
        assertEquals(listOf(1, 0, 3, null, 2), BookSlots.compact(messy).map { it.bookSlot }, "clash -> lowest hole first (by old spot), then out of range, then none")
    }

    @Test
    fun reclaimable() {
        val items = listOf(item("a", true, 0), item("b"))
        assertEquals(1, BookSlots.reclaimable(items, 1, 3))
        assertNull(BookSlots.reclaimable(items, 0, 3), "taken")
        assertNull(BookSlots.reclaimable(items, 3, 3), "out of range")
        assertNull(BookSlots.reclaimable(items, null, 3))
    }
}

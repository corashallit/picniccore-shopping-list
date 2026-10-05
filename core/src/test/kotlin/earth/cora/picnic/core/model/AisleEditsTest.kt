package earth.cora.picnic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun item(id: String, aisle: String?, manual: Boolean = false) =
    GroceryItem(id = id, name = id, aisleId = aisle, icon = "apple", addedAt = 0, manuallyPlaced = manual)

private fun state(text: String, vararg items: GroceryItem, other: List<GroceryItem> = emptyList()) = AppState(
    lists = listOf(GroceryList("l1", "Saturday shop", 0, items.toList()), GroceryList("l2", "Party", 0, other)),
    currentListId = "l1",
    store = StoreProfile(name = "Hillcrest Market", aislesText = text),
)

private val guess: (Aisle) -> BasketKind = { BasketKind.CANDY_JAR }

/** renameAisle (§6.7; checklist 221). */
class RenameAisleTest {
    @Test
    fun itemsBasketsStylesCorrectionsAndOpenAisleFollow() {
        val s = state("Produce\nAisle 2 - Baking\nFrozen", item("flour", "aisle-2-baking"), item("peas", "frozen"), other = listOf(item("sugar", "aisle-2-baking")))
            .copy(corrections = listOf(Correction("flour", "aisle-2-baking", 1), Correction("peas", "frozen", 2)))
            .setAisleBasket("aisle-2-baking", BasketKind.MIXING_BOWL)
            .setAisleStyle("aisle-2-baking", AisleStyle(family = "cocoa", storeAisle = 2))
            .setOpenAisle("l1", "aisle-2-baking")
            .setOpenAisle("l2", "aisle-2-baking")
        val r = s.renameAisle("aisle-2-baking", "Aisle 2 - Baking & spices", 5)
        assertEquals("Produce\nAisle 2 - Baking & spices\nFrozen", r.store.aislesText)
        val newId = r.store.aisles()[1].id
        assertEquals("aisle-2-baking-spices", newId)
        assertEquals(newId, r.findItem("flour")!!.aisleId)
        assertEquals(newId, r.findItem("sugar")!!.aisleId, "every list")
        assertEquals("frozen", r.findItem("peas")!!.aisleId)
        assertEquals(mapOf(newId to BasketKind.MIXING_BOWL), r.store.aisleBaskets)
        assertEquals(mapOf(newId to AisleStyle(family = "cocoa", storeAisle = 2)), r.store.aisleStyles)
        assertEquals(listOf(Correction("flour", newId, 1), Correction("peas", "frozen", 2)), r.corrections)
        assertEquals(newId, r.list("l1")!!.openAisle)
        assertEquals(newId, r.list("l2")!!.openAisle)
    }

    @Test
    fun bulletsAndCommentsStay() {
        val s = state("# my store\n- Produce\n  * Bakery  \n\nFrozen", item("bread", "bakery"))
        val r = s.renameAisle("bakery", "Boulangerie", 1)
        assertEquals("# my store\n- Produce\n  * Boulangerie\n\nFrozen", r.store.aislesText)
        assertEquals("boulangerie", r.findItem("bread")!!.aisleId)
    }

    @Test
    fun duplicateNamesRenumber() {
        // Renaming the first "Bakery" frees the base id: the second one's "bakery-2" becomes "bakery" and its items follow.
        val s = state("Bakery\nBakery\nFrozen", item("a", "bakery"), item("b", "bakery-2"))
            .setAisleBasket("bakery", BasketKind.BREAD_BASKET).setAisleBasket("bakery-2", BasketKind.CANDY_JAR)
        val r = s.renameAisle("bakery", "Pastry", 1)
        assertEquals(listOf("pastry", "bakery", "frozen"), r.store.aisles().map { it.id })
        assertEquals("pastry", r.findItem("a")!!.aisleId)
        assertEquals("bakery", r.findItem("b")!!.aisleId)
        assertEquals(mapOf("pastry" to BasketKind.BREAD_BASKET, "bakery" to BasketKind.CANDY_JAR), r.store.aisleBaskets)
    }

    @Test
    fun noOps() {
        val s = state("Produce\nBakery", item("a", "produce"))
        assertSame(s, s.renameAisle("produce", "  ", 1))
        assertSame(s, s.renameAisle("produce", "Produce", 1))
        assertSame(s, s.renameAisle("nope", "Fruit", 1))
        assertEquals("Fresh fruit", s.renameAisle("produce", "Fresh\nfruit", 1).store.aisles()[0].name, "one line")
        assertEquals("produce", s.renameAisle("produce", "PRODUCE", 1).findItem("a")!!.aisleId, "same slug, same id")
    }
}

/** moveAisle (§6.7; checklist 220). */
class MoveAisleTest {
    @Test
    fun reordersTheTextAndReferencesFollow() {
        val s = state("Produce\nBakery\nFrozen", item("bread", "bakery")).setAisleBasket("bakery", BasketKind.BREAD_BASKET).setOpenAisle("l1", "bakery")
        val r = s.moveAisle(1, 0, guess)
        assertEquals("Bakery\nProduce\nFrozen", r.store.aislesText)
        assertEquals(listOf("bakery" to 1, "produce" to 2, "frozen" to 3), r.store.aisles().map { it.id to it.position })
        assertEquals("bakery", r.findItem("bread")!!.aisleId, "plain names keep their ids")
        assertEquals(BasketKind.BREAD_BASKET, r.store.aisleBaskets["bakery"])
        assertEquals(BasketKind.CANDY_JAR, r.store.aisleBaskets["produce"], "aisles without a basket get the guess")
        assertEquals("bakery", r.list("l1")!!.openAisle)
        assertEquals("Produce\nFrozen\nBakery", s.moveAisle(1, 2, guess).store.aislesText)
    }

    @Test
    fun duplicateNames() {
        val s = state("Bakery\nFrozen\nBakery", item("a", "bakery"), item("b", "bakery-2"), other = listOf(item("c", "bakery-2")))
            .setAisleBasket("bakery", BasketKind.BREAD_BASKET).setAisleBasket("bakery-2", BasketKind.CANDY_JAR)
            .setAisleStyle("bakery-2", AisleStyle(family = "lemon"))
            .copy(corrections = listOf(Correction("b", "bakery-2", 1)))
            .setOpenAisle("l1", "bakery-2")
        val r = s.moveAisle(2, 0, guess)
        assertEquals("Bakery\nBakery\nFrozen", r.store.aislesText)
        // The second Bakery is now first, so it is "bakery"; the old first becomes "bakery-2". Everything follows.
        assertEquals("bakery-2", r.findItem("a")!!.aisleId)
        assertEquals("bakery", r.findItem("b")!!.aisleId)
        assertEquals("bakery", r.findItem("c")!!.aisleId)
        assertEquals(mapOf("bakery" to BasketKind.CANDY_JAR, "bakery-2" to BasketKind.BREAD_BASKET, "frozen" to BasketKind.CANDY_JAR), r.store.aisleBaskets)
        assertEquals(mapOf("bakery" to AisleStyle(family = "lemon")), r.store.aisleStyles)
        assertEquals(listOf(Correction("b", "bakery", 1)), r.corrections)
        assertEquals("bakery", r.list("l1")!!.openAisle)
    }

    @Test
    fun commentsAndBlankLinesStayInPlace() {
        val s = state("# walk\nProduce\n\nBakery\n# back\nFrozen")
        assertEquals("# walk\nFrozen\n\nProduce\n# back\nBakery", s.moveAisle(2, 0, guess).store.aislesText)
    }

    @Test
    fun noOps() {
        val s = state("Produce\nBakery")
        assertSame(s, s.moveAisle(0, 0, guess))
        assertSame(s, s.moveAisle(-1, 0, guess))
        assertSame(s, s.moveAisle(0, 2, guess))
    }
}

/** commitAislesText (§6.7; checklist 223). */
class AislesTextCommitTest {
    @Test
    fun anEditedLineKeepsItsItems() {
        val s = state("Produce\nAisle 2 - Baking\nFrozen", item("flour", "aisle-2-baking", manual = true), item("peas", "frozen"))
            .setAisleBasket("aisle-2-baking", BasketKind.MIXING_BOWL)
            .copy(corrections = listOf(Correction("flour", "aisle-2-baking", 1)))
        val r = s.commitAislesText("Produce\nAisle 2 - Baking & spices\nFrozen", guess, 5)
        assertEquals("aisle-2-baking-spices", r.findItem("flour")!!.aisleId)
        assertTrue(r.findItem("flour")!!.manuallyPlaced, "a rename keeps what she placed by hand")
        assertEquals(BasketKind.MIXING_BOWL, r.store.aisleBaskets["aisle-2-baking-spices"])
        assertEquals("aisle-2-baking-spices", r.corrections.single().aisleId)
        assertEquals("frozen", r.findItem("peas")!!.aisleId)
    }

    @Test
    fun aRemovedLinesItemsGoToEverythingElse() {
        val s = state("Produce\nFlorist\nFrozen", item("tulips", "florist", manual = true), item("peas", "frozen"))
            .setAisleBasket("florist", BasketKind.FLOWER_BUCKET).setAisleStyle("florist", AisleStyle(family = "sakura"))
            .copy(corrections = listOf(Correction("tulip", "florist", 1)))
            .setOpenAisle("l1", "florist")
        val r = s.commitAislesText("Produce\nFrozen", guess, 5)
        val tulips = r.findItem("tulips")!!
        assertNull(tulips.aisleId)
        assertFalse(tulips.manuallyPlaced, "she did not choose Everything else: a re-sort may file it")
        assertTrue(r.corrections.isEmpty())
        assertFalse("florist" in r.store.aisleBaskets)
        assertFalse("florist" in r.store.aisleStyles)
        assertNull(r.list("l1")!!.openAisle, "the open aisle becomes automatic")
        assertEquals("frozen", r.findItem("peas")!!.aisleId)
    }

    @Test
    fun movedAndAddedLines() {
        val s = state("Produce\nBakery\nFrozen", item("bread", "bakery"), item("peas", "frozen"))
        val r = s.commitAislesText("Frozen\nProduce\nDeli\nBakery", guess, 5)
        assertEquals("bakery", r.findItem("bread")!!.aisleId)
        assertEquals("frozen", r.findItem("peas")!!.aisleId)
        assertEquals(BasketKind.CANDY_JAR, r.store.aisleBaskets["deli"], "a new line gets a basket")
        assertEquals(listOf("frozen", "produce", "deli", "bakery"), r.store.aisles().map { it.id })
    }

    @Test
    fun severalRenamesInOneGapPairInOrder() {
        val s = state("Produce\nAisle 1\nAisle 2\nFrozen", item("a", "aisle-1"), item("b", "aisle-2"))
        val r = s.commitAislesText("Produce\nAisle 1 - Breakfast\nAisle 2 - Baking\nFrozen", guess, 5)
        assertEquals("aisle-1-breakfast", r.findItem("a")!!.aisleId)
        assertEquals("aisle-2-baking", r.findItem("b")!!.aisleId)
    }

    @Test
    fun aRemovedDuplicateRenumbersTheOther() {
        val s = state("Bakery\nBakery", item("a", "bakery"), item("b", "bakery-2"))
        val r = s.commitAislesText("Bakery", guess, 5)
        // LCS keeps the first line; the second is removed.
        assertEquals("bakery", r.findItem("a")!!.aisleId)
        assertNull(r.findItem("b")!!.aisleId)
    }

    @Test
    fun theSameTextIsASetAislesText() {
        val s = state("Produce\nBakery", item("a", "produce"))
        val r = s.commitAislesText("Produce\nBakery", guess, 5)
        assertEquals(mapOf("produce" to BasketKind.CANDY_JAR, "bakery" to BasketKind.CANDY_JAR), r.store.aisleBaskets)
        assertSame(r, r.commitAislesText("Produce\nBakery", guess, 6))
    }

    @Test
    fun diffMap() {
        fun ids(text: String) = Aisles.parse(text)
        assertEquals(
            mapOf("a" to "a", "b" to "x", "c" to "c", "d" to null),
            AisleDiff.idMap(ids("A\nB\nC\nD"), ids("A\nX\nC")),
        )
        assertEquals(mapOf("a" to "a", "b" to "b"), AisleDiff.idMap(ids("A\nB"), ids("B\nA")), "moves keep their aisles")
        assertEquals(mapOf("a" to null), AisleDiff.idMap(ids("A"), ids("")))
    }
}

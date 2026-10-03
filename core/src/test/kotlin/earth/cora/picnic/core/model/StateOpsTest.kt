package earth.cora.picnic.core.model

import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.catalog.IconCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StateOpsTest {
    private val catalog = TestSupport.catalog

    private companion object {
        const val NOW = 1_759_500_000_000L
    }

    private fun item(
        id: String,
        name: String,
        aisle: String? = null,
        checked: Boolean = false,
        fit: IconFit = IconFit.EXACT,
        icon: String = "paper_bag",
        custom: String? = null,
        manual: Boolean = false,
    ) = GroceryItem(
        id = id, name = name, aisleId = aisle, icon = icon, iconFit = fit, customIconId = custom,
        checked = checked, addedAt = id.hashCode().toLong(), manuallyPlaced = manual,
    )

    private fun state(vararg items: GroceryItem, other: List<GroceryItem> = emptyList()) = AppState(
        lists = listOf(GroceryList("l1", "Groceries", 0, items.toList()), GroceryList("l2", "Party", 1, other)),
        currentListId = "l1",
    )

    private fun AppState.items(listId: String = "l1") = list(listId)!!.items

    @Test
    fun initialState() {
        val s = StateOps.initialState("x", 5)
        assertEquals("x", s.currentListId)
        assertEquals("Groceries", s.currentList().title)
        assertEquals(STATE_SCHEMA_VERSION, s.version)
        assertEquals(14, s.store.aisles().size)
    }

    @Test
    fun addItemsDedupesAgainstUncheckedItemsAndWithinTheBatch() {
        val s = state(item("a", "Lemons"), item("b", "Kiwi"))
        val (after, outcome) = s.addItems("l1", listOf(item("c", "lemon"), item("e", "Eggs"), item("f", "EGGS"), item("g", " ★ ")), NOW)
        assertEquals(listOf("a", "b", "e"), after.items().map { it.id })
        assertNull(after.findItem("c"))
        assertEquals(AddOutcome(added = listOf("e"), alreadyThere = listOf("a"), revived = emptyList()), outcome)
        assertSame(s, s.addItems("l1", emptyList(), NOW).first)
        assertEquals(s to AddOutcome(emptyList(), listOf("a"), emptyList()), s.addItems("l1", listOf(item("z", "Lemon")), NOW))
        assertEquals(s to AddOutcome.NONE, s.addItems("nope", listOf(item("y", "Kiwi")), NOW))
    }

    @Test
    fun addItemsRevivesChecked() {
        // §6.2 rule 7 / Q8: a duplicate of a checked item un-checks it ("Milk is back on your list"); never a second row.
        val s = state(item("a", "Lemons"), item("b", "Milk"), item("c", "Bread")).toggleChecked("l1", "b", NOW).toggleChecked("l1", "c", NOW + 1)
        val (after, outcome) = s.addItems("l1", listOf(item("d", "milk"), item("e", "Eggs"), item("f", "MILK")), NOW + 5)
        assertEquals(listOf("a", "b", "c", "e"), after.items().map { it.id }, "no second Milk row")
        val milk = after.findItem("b")!!
        assertFalse(milk.checked)
        assertNull(milk.checkedAt)
        assertNull(milk.bookSlot, "its twin peels out of the book")
        assertEquals("Milk", milk.name, "nothing else is rewritten")
        assertEquals(AddOutcome(added = listOf("e"), alreadyThere = emptyList(), revived = listOf("b")), outcome)
        assertEquals(NOW + 5, after.list("l1")!!.trip!!.lastActivityAt, "a revive is an un-check")
        assertEquals(1, after.findItem("c")!!.bookSlot, "other stickers keep their spots")

        // Undo of a revive checks it again (into the lowest free spot).
        val undone = after.toggleChecked("l1", "b", NOW + 6)
        assertEquals(0, undone.findItem("b")!!.bookSlot)

        // An unchecked match wins over a checked one with the same name.
        val both = state(item("x", "Milk"), item("y", "Milk")).toggleChecked("l1", "x", NOW)
        assertEquals(AddOutcome(emptyList(), listOf("y"), emptyList()), both.addItems("l1", listOf(item("z", "milk")), NOW).second)

        // Reviving an item of a complete trip re-opens it.
        val done = state(item("a", "Milk"), item("b", "Eggs")).toggleChecked("l1", "a", NOW).toggleChecked("l1", "b", NOW + 1)
        assertNotNull(done.list("l1")!!.trip!!.completedAt)
        val revived = done.addItems("l1", listOf(item("m", "milk")), NOW + 2).first
        assertNull(revived.list("l1")!!.trip!!.completedAt)
    }

    @Test
    fun addItemsNeverDuplicatesAnId() {
        val s = state(item("a", "Lemons"), other = listOf(item("p", "Plates")))
        val after = s.addItems("l1", listOf(item("p", "Pears")), NOW).first
        assertSame(s, after)
    }

    @Test
    fun aisleLooksAndRarity() {
        val s = state(item("a", "A"), item("b", "B"))
        val style = AisleStyle(family = "lemon", pattern = "polka")
        val styled = s.setAisleStyle("produce", style)
        assertEquals(style, styled.store.aisleStyles["produce"])
        assertSame(s, s.setAisleStyle("not-an-aisle", style))
        assertTrue(styled.setAisleStyle("produce", AisleStyle()).store.aisleStyles.isEmpty(), "an all-default style is removed")
        assertTrue(styled.setAisleStyle("produce", null).store.aisleStyles.isEmpty())

        val bowl = s.setAisleBasket("aisle-2-baking-spices", BasketKind.MIXING_BOWL)
        assertEquals(BasketKind.MIXING_BOWL, bowl.store.aisleBaskets["aisle-2-baking-spices"])
        assertSame(bowl, bowl.setAisleBasket("aisle-2-baking-spices", BasketKind.MIXING_BOWL))
        assertSame(s, s.setAisleBasket("nope", BasketKind.COOLER))
        assertEquals(Rarity.SHINY, s.setRarity("l1", "b", Rarity.SHINY).findItem("b")!!.rarity)
        assertSame(s, s.setRarity("l1", "b", Rarity.NONE))
    }

    @Test
    fun toggleRemoveRestore() {
        val s = state(item("a", "A"), item("b", "B"), item("c", "C"))
        val toggled = s.toggleChecked("l1", "b", NOW)
        assertTrue(toggled.findItem("b")!!.checked)
        assertEquals(NOW, toggled.findItem("b")!!.checkedAt, "checking records the check-off time")
        val unchecked = toggled.toggleChecked("l1", "b", NOW + 1).findItem("b")!!
        assertFalse(unchecked.checked)
        assertNull(unchecked.checkedAt)
        assertSame(s, s.toggleChecked("l1", "nope", NOW))

        val removed = s.removeItem("l1", "b", NOW)
        assertEquals(listOf("a", "c"), removed.items().map { it.id })
        assertSame(s, s.removeItem("l1", "nope", NOW))
        val restored = removed.restoreItem("l1", s.findItem("b")!!, 1, NOW)
        assertEquals(listOf("a", "b", "c"), restored.items().map { it.id })
        assertSame(restored, restored.restoreItem("l1", s.findItem("b")!!, 0, NOW), "already present")
        assertEquals(listOf("a", "c", "b"), removed.restoreItem("l1", s.findItem("b")!!, 99, NOW).items().map { it.id })
        assertEquals(listOf("b", "a", "c"), removed.restoreItem("l1", s.findItem("b")!!, -3, NOW).items().map { it.id })
    }

    @Test
    fun editItemDetailsKeepsIconWhenOnlyCaseOrDetailsChange() {
        val s = state(item("a", "lemons", icon = "lemon", custom = "c-1"))
        val after = s.editItemDetails("l1", "a", " Lemons ", " 2 ", "  ", catalog)
        val edited = after.findItem("a")!!
        assertEquals("Lemons", edited.name)
        assertEquals("2", edited.quantity)
        assertNull(edited.note)
        assertEquals("lemon", edited.icon)
        assertEquals("c-1", edited.customIconId)
        assertSame(s, s.editItemDetails("l1", "a", "lemons", null, null, catalog))
        assertEquals("lemons", s.editItemDetails("l1", "a", "   ", "1", null, catalog).findItem("a")!!.name, "blank name keeps the old one")
    }

    @Test
    fun renameReResolvesTheIconLocally() {
        val s = state(item("a", "Lemons", icon = "lemon", custom = "c-1"))
        val toLimes = s.editItemDetails("l1", "a", "Limes", null, null, catalog).findItem("a")!!
        assertEquals("lime", toLimes.icon)
        assertEquals(IconFit.EXACT, toLimes.iconFit)
        assertNull(toLimes.customIconId)

        val toUnknown = s.editItemDetails("l1", "a", "Gochujang", null, null, catalog).findItem("a")!!
        assertEquals(IconCatalog.DEFAULT_ICON, toUnknown.icon)
        assertEquals(IconFit.PENDING, toUnknown.iconFit)

        val withOverride = s.copy(iconOverrides = listOf(IconOverride("gochujang", "hot_sauce", 1)))
        assertEquals("hot_sauce", withOverride.editItemDetails("l1", "a", "Gochujang", null, null, catalog).findItem("a")!!.icon)
    }

    @Test
    fun moveItemToAisleRecordsAndReplacesCorrections() {
        val s = state(item("a", "Oat milk"), item("b", "oat milks"))
        val moved = s.moveItemToAisle("l1", "a", "dairy-eggs", now = 10)
        assertEquals("dairy-eggs", moved.findItem("a")!!.aisleId)
        assertTrue(moved.findItem("a")!!.manuallyPlaced)
        assertEquals(listOf(Correction("oat milk", "dairy-eggs", 10)), moved.corrections)

        val again = moved.moveItemToAisle("l1", "b", "bulk-natural-foods", now = 20)
        assertEquals(listOf(Correction("oat milk", "bulk-natural-foods", 20)), again.corrections)

        val toUnsorted = again.moveItemToAisle("l1", "a", null, now = 30)
        assertNull(toUnsorted.findItem("a")!!.aisleId)
        assertTrue(toUnsorted.findItem("a")!!.manuallyPlaced)
        assertTrue(toUnsorted.corrections.isEmpty(), "moving to Unsorted drops the stale correction")

        assertSame(s, s.moveItemToAisle("l1", "a", "not-an-aisle", 1))
        assertSame(s, s.moveItemToAisle("l1", "nope", "produce", 1))
    }

    @Test
    fun correctionsAndOverridesAreCapped() {
        var s = state(*Array(StateOps.MAX_CORRECTIONS + 5) { item("i$it", "Thing $it") })
        repeat(StateOps.MAX_CORRECTIONS + 5) { s = s.moveItemToAisle("l1", "i$it", "produce", it.toLong()) }
        assertEquals(StateOps.MAX_CORRECTIONS, s.corrections.size)
        assertEquals("thing 5", s.corrections.first().name, "oldest dropped first")
        repeat(StateOps.MAX_ICON_OVERRIDES + 3) { s = s.setItemIcon("l1", "i$it", "lemon", it.toLong(), catalog) }
        assertEquals(StateOps.MAX_ICON_OVERRIDES, s.iconOverrides.size)
    }

    @Test
    fun setItemIconRecordsAnOverrideAndClearsTheBespokeIcon() {
        val s = state(item("a", "Nooch", fit = IconFit.APPROXIMATE, custom = "c-9"))
        val after = s.setItemIcon("l1", "a", "spice_jar", 50, catalog)
        val a = after.findItem("a")!!
        assertEquals("spice_jar", a.icon)
        assertEquals(IconFit.EXACT, a.iconFit)
        assertNull(a.customIconId)
        assertEquals(listOf(IconOverride("nooch", "spice_jar", 50)), after.iconOverrides)
        assertEquals("spice_jar", after.localIcon("NOOCH", catalog))
        assertSame(s, s.setItemIcon("l1", "a", "no_such_icon", 50, catalog))
    }

    @Test
    @Suppress("DEPRECATION") // clearChecked and setSectionCollapsed stay until WP5's New trip and open aisle land.
    fun clearCheckedAndCollapse() {
        val s = state(item("a", "A", checked = true), item("b", "B"))
        assertEquals(listOf("b"), s.clearChecked("l1").items().map { it.id })
        val cleared = s.clearChecked("l1")
        assertSame(cleared, cleared.clearChecked("l1"))
        val collapsed = s.setSectionCollapsed("l1", "produce", true)
        assertEquals(setOf("produce"), collapsed.list("l1")!!.collapsedAisles)
        assertSame(collapsed, collapsed.setSectionCollapsed("l1", "produce", true))
        assertTrue(collapsed.setSectionCollapsed("l1", "produce", false).list("l1")!!.collapsedAisles.isEmpty())
        val noChecked = state(item("b", "B"))
        assertSame(noChecked, noChecked.clearChecked("l1"))
        val withTrip = state(item("a", "A"), item("b", "B")).toggleChecked("l1", "a", NOW)
        assertNull(withTrip.clearChecked("l1").list("l1")!!.trip, "v1 Clear checked closes the trip without filing")
    }

    @Test
    fun listLifecycle() {
        val s = state()
        val created = s.newList("l3", "  ", now = 9)
        assertEquals("l3", created.currentListId)
        assertEquals(DEFAULT_LIST_TITLE, created.list("l3")!!.title)
        assertSame(created, created.newList("l3", "Again", 10))
        assertEquals("l2", created.switchList("l2").currentListId)
        assertSame(created, created.switchList("missing"))
        assertEquals("BBQ", created.renameList("l3", " BBQ ").list("l3")!!.title)
        assertSame(created, created.renameList("l3", "   "))

        // Deleting the current list makes the neighbour current; the last list is never deleted.
        val (deleted, book) = created.deleteList("l3", NOW)
        assertNull(book, "no trip, nothing to file")
        assertEquals(listOf("l1", "l2"), deleted.lists.map { it.id })
        assertEquals("l2", deleted.currentListId)
        val first = created.switchList("l1").deleteList("l1", NOW).first
        assertEquals("l2", first.currentListId)
        val single = AppState(lists = listOf(GroceryList("only", "Only", 0)), currentListId = "only")
        assertSame(single, single.deleteList("only", NOW).first)
        assertEquals("l3", created.deleteList("l2", NOW).first.currentListId, "deleting another list keeps the current one")
    }

    @Test
    fun storeNameAndAisleText() {
        val s = state()
        assertEquals("No Frills", s.setStoreName("  No Frills ").store.name)
        assertSame(s, s.setStoreName("  "))

        val withBaskets = s.copy(store = s.store.copy(aisleBaskets = mapOf("produce" to BasketKind.BERRY_PUNNET, "frozen" to BasketKind.COOLER)))
        val after = withBaskets.setAislesText("Produce\nFleurs\nBoulangerie", { BasketKind.WIRE_BASKET }, NOW)
        assertEquals(
            mapOf("produce" to BasketKind.BERRY_PUNNET, "fleurs" to BasketKind.WIRE_BASKET, "boulangerie" to BasketKind.WIRE_BASKET),
            after.store.aisleBaskets,
        )
        assertSame(after, after.setAislesText("Produce\nFleurs\nBoulangerie", { BasketKind.COOLER }, NOW))

        val merged = after.setAisleBaskets(mapOf("fleurs" to BasketKind.FLOWER_BUCKET, "ghost" to BasketKind.COOLER))
        assertEquals(BasketKind.FLOWER_BUCKET, merged.store.aisleBaskets["fleurs"])
        assertFalse("ghost" in merged.store.aisleBaskets)
        assertSame(merged, merged.setAisleBaskets(mapOf("fleurs" to BasketKind.FLOWER_BUCKET)))
    }

    @Test
    fun applyAisleAssignmentsRespectsManualPlacements() {
        val s = state(
            item("a", "Hummus"),
            item("b", "Diapers", aisle = "dairy-eggs", manual = true),
            item("c", "Flowers", aisle = "gone", manual = true),
            item("d", "Glitter", manual = true),
            other = listOf(item("e", "Chips")),
        )
        val after = s.applyAisleAssignments(
            mapOf("a" to "deli-cheese", "b" to "aisle-7-health-beauty-baby", "c" to "produce", "d" to "produce", "e" to "not-an-aisle"),
            NOW,
        )
        assertEquals("deli-cheese", after.findItem("a")!!.aisleId)
        assertEquals("dairy-eggs", after.findItem("b")!!.aisleId, "manual placement into an existing aisle wins")
        assertEquals("produce", after.findItem("c")!!.aisleId, "its aisle vanished, so it is re-placed")
        assertFalse(after.findItem("c")!!.manuallyPlaced)
        assertNull(after.findItem("d")!!.aisleId, "deliberately Unsorted stays Unsorted")
        assertNull(after.findItem("e")!!.aisleId, "unknown ids become Unsorted")
        assertSame(s, s.applyAisleAssignments(emptyMap(), NOW))
    }

    @Test
    fun iconDecisionsOnlyTouchPendingItemsAcrossLists() {
        val s = state(
            item("a", "Gochujang", fit = IconFit.PENDING),
            item("b", "gochujang", fit = IconFit.EXACT, icon = "hot_sauce"),
            other = listOf(item("c", "Gochujang", fit = IconFit.PENDING)),
        )
        val after = s.applyIconDecisions(mapOf("gochujang" to ResolvedIcon("condiments_generic", IconFit.APPROXIMATE)))
        assertEquals("condiments_generic", after.findItem("a")!!.icon)
        assertEquals(IconFit.APPROXIMATE, after.findItem("c")!!.iconFit)
        assertEquals("hot_sauce", after.findItem("b")!!.icon, "a user choice made meanwhile wins")
        assertSame(s, s.applyIconDecisions(mapOf("kimchi" to ResolvedIcon("pickles", IconFit.EXACT))))
    }

    @Test
    fun attachCustomIconWithAndWithoutOverwrite() {
        val s = state(item("a", "Gochujang"), item("b", "gochujangs", custom = "old"), other = listOf(item("c", "Kimchi")))
        val soft = s.attachCustomIcon("gochujang", "new", overwrite = false)
        assertEquals("new", soft.findItem("a")!!.customIconId)
        assertEquals("old", soft.findItem("b")!!.customIconId)
        assertNull(soft.findItem("c")!!.customIconId)
        val hard = s.attachCustomIcon("gochujang", "new", overwrite = true)
        assertEquals("new", hard.findItem("b")!!.customIconId)
        assertSame(hard, hard.attachCustomIcon("gochujang", "new", overwrite = true))
    }

    @Test
    fun localIconPrefersTheNewestValidOverride() {
        val s = state().copy(
            iconOverrides = listOf(
                IconOverride("lemon", "lime", 1),
                IconOverride("lemon", "orange", 5),
                IconOverride("kiwi", "retired_key", 9),
            ),
        )
        assertEquals("orange", s.localIcon("Lemons", catalog))
        assertEquals("kiwi", s.localIcon("Kiwis", catalog), "invalid override falls back to the catalog match")
        assertNull(s.localIcon("Gochujang", catalog))
        assertEquals(mapOf("lemon" to "orange", "kiwi" to "retired_key"), s.iconOverrideMap())
    }

    @Test
    fun itemsNeedingIconDecisionAreDistinctNormalizedNames() {
        val s = state(
            item("a", "Gochujang", fit = IconFit.PENDING),
            item("b", "gochujangs", fit = IconFit.PENDING),
            item("c", "Lemons", fit = IconFit.EXACT),
            other = listOf(item("d", "Spatula", fit = IconFit.PENDING)),
        )
        assertEquals(listOf(NamedItem("gochujang", "Gochujang"), NamedItem("spatula", "Spatula")), s.itemsNeedingIconDecision())
    }

    @Test
    fun itemsNeedingAisle() {
        val s = state(
            item("a", "Hummus"),
            item("b", "Milk", aisle = "dairy-eggs"),
            item("c", "Ghost", aisle = "vanished"),
            item("d", "Glitter", manual = true),
            item("e", "Done", checked = true),
            other = listOf(item("f", "Chips")),
        )
        assertEquals(listOf("a", "c", "e", "f"), s.itemsNeedingAisle().map { it.id })
    }

    @Test
    fun ingestContextUsesNewestValidCorrectionsAndUncheckedNames() {
        val s = state(item("a", "Butter"), item("b", "Bananas", checked = true)).copy(
            corrections = listOf(
                Correction("tortilla", "aisle-3-pasta-rice-international", 1),
                Correction("ghost", "vanished", 2),
                Correction("tortilla", "bakery", 3),
                Correction("oat milk", "dairy-eggs", 4),
            ),
            iconOverrides = listOf(IconOverride("nooch", "spice_jar", 1)),
        )
        val ctx = s.ingestContext("l1")
        assertEquals(14, ctx.aisles.size)
        assertEquals(listOf("oat milk", "tortilla"), ctx.corrections.map { it.name })
        assertEquals("bakery", ctx.corrections[1].aisleId, "newest correction per name")
        assertEquals(listOf("Butter"), ctx.existingNames)
        assertEquals(mapOf("nooch" to "spice_jar"), ctx.iconOverrides)
    }

    @Test
    fun unknownIdsAreNoOps() {
        val s = state(item("a", "A"))
        assertSame(s, s.updateList("nope") { it.copy(title = "x") })
        assertSame(s, s.updateList("l1") { it })
        assertNotSame(s, s.updateList("l1") { it.copy(title = "x") })
        assertSame(s, s.editItemDetails("l1", "nope", "B", null, null, catalog))
        assertSame(s, s.setItemIcon("l1", "nope", "lemon", 1, catalog))
    }
}

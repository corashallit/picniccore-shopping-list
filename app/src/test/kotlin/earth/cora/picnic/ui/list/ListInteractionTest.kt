package earth.cora.picnic.ui.list

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.newList
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.switchList
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.add.ADD_FIELD_TAG
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.BookTags
import earth.cora.picnic.ui.theme.TOAST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The list's interactions through the real List destination (docs/DESIGN_STICKER.md §4.4, §4.10, §4.11, §4.24, §6.1,
 * §6.4, §0.4 Q9; items 58, 71, 78, 84, 89, 91, 104, 121, 164, 193, 315, 331).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String) = compose.onNodeWithTag(ListTags.row(id))

    private fun config(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config

    /** Item 58: the row is a checkbox (role, on/off state, "got it" / "not checked"); Space and Enter toggle it. */
    @Test
    fun checkboxSemanticsAndKeyboard() {
        // Produce is open: checking Strawberries leaves Apples to get, so the tray stays open for the second look.
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        val before = config(ListTags.row("strawberries"))
        assertEquals(Role.Checkbox, before.getOrNull(SemanticsProperties.Role))
        assertEquals(ToggleableState.Off, before.getOrNull(SemanticsProperties.ToggleableState))
        assertEquals("not checked", before.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(listOf("Strawberries, 1 litre basket"), before.getOrNull(SemanticsProperties.ContentDescription))
        val actions = before.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertEquals(listOf("Check", "Delete", "Edit"), actions)
        assertEquals("Check", before.getOrNull(SemanticsActions.OnClick)?.label)

        // Keyboard: focus the row, Space checks it.
        row("strawberries").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        row("strawberries").assertIsFocused()
        row("strawberries").performKeyInput { pressKey(Key.Spacebar) }
        compose.waitForIdle()
        assertTrue("Space checked it", rig.app.isChecked("strawberries"))
        val after = config(ListTags.row("strawberries"))
        assertEquals(ToggleableState.On, after.getOrNull(SemanticsProperties.ToggleableState))
        assertEquals("got it", after.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(listOf("Uncheck", "Delete", "Edit"), after.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label })

        // Enter un-checks a checked row.
        row("bananas").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        row("bananas").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertFalse("Enter un-checked it", rig.app.isChecked("bananas"))
        // And the TalkBack action does the same as a tap.
        val check = config(ListTags.row("bananas"))[SemanticsActions.CustomActions].first { it.label == "Check" }
        compose.runOnIdle { check.action() }
        compose.waitForIdle()
        assertTrue(rig.app.isChecked("bananas"))
    }

    /** Item 71: the empty list has no book; "Start my list" focuses the add field. */
    @Test
    fun startMyListFocusesField() {
        val rig = ListRig(ListFixture.empty())
        compose.show(rig)
        compose.onNodeWithTag(ListTags.EMPTY).assertExists()
        assertTrue("no book while the list is empty", compose.onAllNodesWithTag(BookTags.BOOK).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Start my list").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_FIELD_TAG).assert(isFocused())
    }

    /** Item 78: a folded strip is one button read as "Aisle 1, Produce: 2 left of 5", with the action "Expand". */
    @Test
    fun stripLabelAndToggle() {
        val rig = ListRig()
        compose.show(rig)
        val c = config(ListTags.strip("produce"))
        assertEquals(listOf("Aisle 1, Produce: 2 left of 5"), c.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(Role.Button, c.getOrNull(SemanticsProperties.Role))
        assertEquals("Expand", c.getOrNull(SemanticsActions.OnClick)?.label)
        // TalkBack hears "and" for "&" (§15).
        assertEquals(listOf("Aisle 3, Deli and meat: 2 of 2 in the basket"), config(ListTags.strip("deli-meat")).getOrNull(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag(ListTags.strip("produce")).performClick()
        compose.waitForIdle()
        assertEquals("produce", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
        compose.onNodeWithTag(ListTags.header("produce")).assertExists()
        assertEquals("Collapse Produce", config(ListTags.chevron("produce")).getOrNull(SemanticsProperties.ContentDescription)?.single())
        // A store number is read as the Next card says it, never as a second "aisle" (§15): the strip, the tray's
        // heading and its chevron.
        assertEquals(listOf("Aisle 4, Baking, store aisle 2: 1 left of 3"), config(ListTags.strip("aisle-2-baking")).getOrNull(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("strip:aisle-2-baking")
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.strip("aisle-2-baking")).performClick()
        compose.waitForIdle()
        val heading = config(ListTags.headerTab("aisle-2-baking"))
        assertEquals(listOf("Aisle 4, Baking, store aisle 2"), heading.getOrNull(SemanticsProperties.ContentDescription))
        assertTrue("the tab is the tray's heading", heading.contains(SemanticsProperties.Heading))
        assertEquals("Collapse Baking", config(ListTags.chevron("aisle-2-baking")).getOrNull(SemanticsProperties.ContentDescription)?.single())
    }

    /** Item 84: rows are at least 56 dp with a 52 dp sticker; a tap anywhere on the row toggles it; long-press edits. */
    @Test
    fun wholeRowTogglesLongPressEdits() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        val probe = RecordingProbe()
        compose.show(rig, probe)
        val dp = compose.density.density
        val slot = compose.onNodeWithTag(ListTags.rowSlot("strawberries")).fetchSemanticsNode().boundsInRoot
        assertTrue("row ≥ 56 dp: ${slot.height / dp}", slot.height / dp >= 56f - 0.5f)
        assertEquals(52f, probe["sticker:strawberries"].width / dp, 0.5f)
        // A tap on the sticker, on the text and on the spot each toggle.
        row("strawberries").performTouchInput { click(centerLeft.copy(x = 30f * dp)) }
        compose.waitForIdle()
        assertTrue(rig.app.isChecked("strawberries"))
        row("strawberries").performTouchInput { click(center) }
        compose.waitForIdle()
        assertFalse(rig.app.isChecked("strawberries"))
        row("strawberries").performTouchInput { click(centerRight.copy(x = right - 24f * dp)) }
        compose.waitForIdle()
        assertTrue(rig.app.isChecked("strawberries"))
        // Long-press opens Edit and leaves the checked state alone.
        row("bananas").performTouchInput { longClick(center) }
        compose.waitForIdle()
        assertEquals("bananas", rig.vm.state.value.editing?.item?.id)
        assertTrue(rig.app.isChecked("bananas"))
    }

    /** Item 89: exactly one aisle is open; opening a strip folds the others. */
    @Test
    fun singleOpenAisle() {
        val rig = ListRig()
        compose.show(rig)
        fun headers() = compose.onAllNodes(hasTagPrefix("picnic_tray_header_")).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(listOf(ListTags.header("aisle-2-baking")), headers())
        compose.onNodeWithTag(ListTags.strip("bakery")).performClick()
        compose.waitForIdle()
        assertEquals(listOf(ListTags.header("bakery")), headers())
        compose.onNodeWithTag(ListTags.strip("aisle-2-baking")).assertExists()
        // The chevron folds it, and everything stays folded: no tray, and the list remembers "none open" ("").
        compose.onNodeWithTag(ListTags.chevron("bakery")).performClick()
        compose.waitForIdle()
        assertEquals("every aisle folded", emptyList<String>(), headers())
        assertEquals("", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
        compose.onNodeWithTag(ListTags.strip("bakery")).assertExists()
    }

    /** Item 89 (D2): with no choice of hers, the open aisle is the automatic current one, the first stop with items left. */
    @Test
    fun automaticCurrentAisleIsFirstStopWithItemsLeft() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, null))
        compose.show(rig)
        val sections = rig.vm.state.value.sections
        val first = sections.first { it.toGet > 0 }
        assertEquals("Produce, the first stop with something left", "produce", first.id)
        assertEquals(first.id, rig.vm.state.value.openSectionId)
        assertEquals(listOf(ListTags.header(first.id)), compose.onAllNodes(hasTagPrefix("picnic_tray_header_")).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] })
        // Once Produce is done, the next stop in walking order with items left is the current one.
        val produceDone = ListFixture.allBut(sections.flatMap { it.rows }.filter { !it.checked && it.sectionId != "produce" }.map { it.id })
        val after = ListFixture.ui(produceDone)
        val next = after.sections.first { it.toGet > 0 }
        assertTrue("a later stop (${next.id})", after.sections.indexOf(next) > after.sections.indexOfFirst { it.id == "produce" })
        assertEquals(next.id, after.openSectionId)
    }

    /** Item 91: reopening a finished aisle shows all its rows stamped. */
    @Test
    fun reopenDoneAisleShowsStamps() {
        val rig = ListRig()
        compose.show(rig)
        compose.onNodeWithTag(ListTags.strip("deli-meat")).performClick()
        compose.waitForIdle()
        for (id in listOf("ham", "peameal")) {
            assertEquals(ToggleableState.On, config(ListTags.row(id)).getOrNull(SemanticsProperties.ToggleableState))
        }
    }

    /**
     * Item 104: a basket only ever holds checked items: through a check and an un-check (and their chains), the
     * presented basket never shows an item still to get.
     */
    @Test
    fun basketHoldsOnlyGotten() {
        val rig = ListRig()
        compose.show(rig)
        compose.onNodeWithTag(ListTags.strip("produce")).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        fun check(frame: Int) {
            val s = rig.vm.state.value.sections.first { it.id == "produce" }
            val p = rig.engine.current.value.baskets["produce"]
            val shown = s.gotten.map { it.itemId }.filter { p == null || it !in p.hiddenMinis }
            val checked = s.rows.filter { it.checked }.map { it.id }.toSet()
            assertTrue("frame $frame: shown $shown ⊆ checked $checked", checked.containsAll(shown))
        }
        row("strawberries").performClick()
        repeat(150) { f ->
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
            check(f)
        }
        row("bananas").performClick()
        repeat(150) { f ->
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
            check(150 + f)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(listOf("carrots", "broccoli", "strawberries"), rig.vm.state.value.sections.first { it.id == "produce" }.gotten.map { it.itemId })
    }

    /** Items 121 and 331: the overflow menu's seven items, each doing its job; New trip is disabled without a trip. */
    @Test
    fun overflowMenuActions() {
        val rig = ListRig()
        compose.show(rig)
        fun open() {
            compose.onNodeWithContentDescription("More options").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(LIST_MENU_TAG).assertExists()
        }
        fun pick(label: String) {
            open()
            compose.onNodeWithText(label).performClick()
            compose.waitForIdle()
        }
        pick("My store & aisles")
        assertEquals(listOf("aisles"), rig.aisles)
        pick("My pantry")
        pick("Sticker album")
        assertEquals(listOf("pantry", "album"), rig.nav.opened)
        pick("Settings")
        assertEquals(1, rig.settingsOpened.size)
        pick("Refresh")
        assertEquals(1, rig.icons.resumed)
        pick("Switch list…")
        compose.onNodeWithText("New list", substring = true).assertExists()
    }

    /** Item 121: with no trip yet, New trip is disabled ("No trip yet"). */
    @Test
    fun newTripDisabledWithoutTrip() {
        val rig = ListRig(ListFixture.fresh())
        compose.show(rig)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New trip").assertIsNotEnabled()
        compose.onNodeWithText("New trip").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(NewTripTags.SHEET).assertDoesNotExist()
    }

    /** Item 164: switching lists switches the book. */
    @Test
    fun switchListSwitchesBook() {
        var s = ListFixture.state().newList("party", "Party", ListFixture.NOW - 1000)
        s = s.addItems("party", listOf(GroceryItem(id = "chips", name = "Potato chips", aisleId = null, icon = "potato_chips", iconFit = IconFit.EXACT, addedAt = 1L)), ListFixture.NOW - 900).first
        s = s.toggleChecked("party", "chips", ListFixture.NOW - 800).switchList(ListFixture.LIST_ID)
        val rig = ListRig(s)
        compose.show(rig)
        fun summary() = config(BookTags.BOOK).getOrNull(SemanticsProperties.ContentDescription)?.single()
        assertEquals("Trip sticker book, Sat 3 Oct: 10 of 24 stickers, 1 of 8 aisles stamped", summary())
        rig.vm.switchList("party")
        compose.waitForIdle()
        assertEquals("Trip sticker book, Sat 3 Oct: 1 of 1 sticker, 1 of 1 aisle stamped", summary())
        rig.vm.switchList(ListFixture.LIST_ID)
        compose.waitForIdle()
        assertEquals("Trip sticker book, Sat 3 Oct: 10 of 24 stickers, 1 of 8 aisles stamped", summary())
    }

    /**
     * Item 315 (§0.4 Q9): the first check-off 13 h after the trip's last activity files the old book, clears what was
     * got, starts a new book with the tapped item and shows the toast with Undo; Undo restores everything and checks the
     * item into the old book. Nothing asks.
     */
    @Test
    fun autoNewBookToast() {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        compose.show(rig)
        val oldTrip = rig.app.list(ListFixture.LIST_ID)!!.trip!!.id
        compose.mainClock.autoAdvance = false
        row("baking-powder").performClick()
        repeat(40) {
            compose.waitForIdle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onNodeWithTag(NewTripTags.SHEET).assertDoesNotExist()
        val list = rig.app.list(ListFixture.LIST_ID)!!
        assertTrue("a new trip", list.trip!!.id != oldTrip)
        assertEquals("what was got is cleared (14 left + baking powder)", 14, list.items.size)
        assertTrue(rig.app.isChecked("baking-powder"))
        assertEquals("the old book is in the album", 1, rig.album.flowValue().books.size)
        compose.onNodeWithTag(TOAST_TAG).assertExists()
        // The ✦ is drawn as a vector between the words (§16), so the text node reads without it.
        compose.onNodeWithText("New sticker book for today your last shop is in the album", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val back = rig.app.list(ListFixture.LIST_ID)!!
        assertEquals("the old trip is back", oldTrip, back.trip!!.id)
        assertEquals(24, back.items.size)
        assertEquals("ten got + baking powder", 11, back.items.count { it.checked })
        assertTrue(rig.app.isChecked("baking-powder"))
        assertEquals("the album lost the filed book", 0, rig.album.flowValue().books.size)
    }

    /** Item 193, the list's half: her unfold moves focus to the chevron, her fold to the strip. */
    @Test
    fun foldUnfoldFocus() {
        val rig = ListRig()
        compose.show(rig)
        compose.onNodeWithTag(ListTags.strip("bakery")).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.chevron("bakery")).assertIsFocused()
        compose.onNodeWithTag(ListTags.chevron("bakery")).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("strip:bakery")
        compose.onNodeWithTag(ListTags.strip("bakery")).assertIsFocused()
        assertNull(rig.vm.state.value.editing)
        assertNotNull(rig.vm.state.value.book)
    }
}

private fun earth.cora.picnic.core.store.InMemoryAlbumStore.flowValue() = (album as kotlinx.coroutines.flow.StateFlow<earth.cora.picnic.core.store.Album>).value

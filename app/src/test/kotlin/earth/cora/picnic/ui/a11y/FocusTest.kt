package earth.cora.picnic.ui.a11y

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.add.ADD_FIELD_TAG
import earth.cora.picnic.ui.add.AddMode
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.LIST_MENU_TAG
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.ListRig
import earth.cora.picnic.ui.list.ListTags
import earth.cora.picnic.ui.list.NewTripTags
import earth.cora.picnic.ui.list.boundsOf
import earth.cora.picnic.ui.list.exists
import earth.cora.picnic.ui.list.framesUntil
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import earth.cora.picnic.ui.theme.BookTags
import earth.cora.picnic.ui.theme.LETTERSET_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 285 (docs/DESIGN_STICKER.md §15 "Focus"), on the real List destination with a keyboard:
 * - her fold and unfold move focus to the strip (folded) or the open tray's chevron, never losing it;
 * - every sheet and the overflow menu take focus when opened from the keyboard, keep it (Tab and Shift+Tab stay
 *   inside), and hand it back to the control that opened them when they close;
 * - the Next card and the celebration never take focus from where she is;
 * - nothing that holds focus is hidden under the pinned plate (or the dock) as focus moves through a long aisle.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class FocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val keys = Keyboard(compose)

    private fun show(rig: ListRig) {
        compose.setContent {
            keys.Capture()
            rig.Themed()
        }
        compose.waitForIdle()
        keys.on()
    }

    private fun focus(tag: String) = focus(hasTestTag(tag))

    private fun focus(matcher: SemanticsMatcher) {
        compose.onNode(matcher).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
    }

    @Test
    fun foldAndUnfoldMoveFocus() {
        val rig = ListRig()
        show(rig)
        focus(ListTags.strip("bakery"))
        keys.press(Key.Enter)
        assertEquals("Bakery open: focus on its chevron", ListTags.chevron("bakery"), keys.focusedTag())
        keys.press(Key.Enter)
        assertEquals("Bakery folded: focus on its strip", ListTags.strip("bakery"), keys.focusedTag())
    }

    /**
     * Opens a layer from the control [opener] matches with [open] (Enter, or Enter held): focus goes into the layer tagged
     * [layer], stays there however far Tab and Shift+Tab go, and comes back to the opener when Escape closes it.
     */
    private fun trapsAndReturns(opener: SemanticsMatcher, layer: String, stops: Int = 2, open: () -> Unit = { keys.press(Key.Enter) }) {
        focus(opener)
        val from = KeyRules.key(keys.focused()!!)
        open()
        val first = keys.focused()
        assertNotNull("$layer took focus", first)
        assertTrue("$layer took focus (it is on ${first!!.spoken()})", first.inside(layer))
        val seen = HashSet<Int>()
        repeat(24) { i ->
            keys.press(Key.Tab)
            val f = keys.focused()
            assertTrue("Tab $i left $layer (focus on ${f?.spoken()})", f != null && f.inside(layer))
            seen += f!!.id
        }
        repeat(6) { i ->
            keys.press(Key.Tab, shift = true)
            val f = keys.focused()
            assertTrue("Shift+Tab $i left $layer (focus on ${f?.spoken()})", f != null && f.inside(layer))
        }
        assertTrue("Tab moved through $layer's controls: $seen", seen.size >= stops)
        keys.press(Key.Escape)
        assertFalse("Escape closed $layer", compose.exists(layer))
        assertEquals("focus is back on what opened $layer", from, keys.focused()?.let(KeyRules::key))
    }

    @Test
    fun sheetsAndMenuTrapAndReturnFocus() {
        val rig = ListRig()
        show(rig)
        // The sticker book opens the Trip book: one page, nothing to press, so the sheet holds focus itself (Escape
        // reaches it there).
        trapsAndReturns(hasTestTag(BookTags.BOOK), LETTERSET_TAG, stops = 1)
        // More opens the menu.
        val more = hasContentDescription("More options")
        trapsAndReturns(more, LIST_MENU_TAG)
        // The menu's New trip opens its sheet; closing it brings focus back to More.
        trapsAndReturns(more, NewTripTags.SHEET) {
            keys.press(Key.Enter)
            focus(hasText("New trip") and hasClickAction())
            keys.press(Key.Enter)
        }
        // A row held down (Enter held, as a long press) opens Edit.
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("flour")
        compose.waitForIdle()
        trapsAndReturns(hasTestTag(ListTags.row("flour")), LETTERSET_TAG) { keys.hold(Key.Enter, 1_000) }
        // The Add sheet, beside the list (WP7's host), opened from the add bar's field (as a send that needs the sheet
        // opens it).
        trapsAndReturns(hasTestTag(ADD_FIELD_TAG), LETTERSET_TAG) { compose.runOnIdle { rig.addVm.openSheet(AddMode.WRITE) } }
    }

    @Test
    fun theNextCardAndTheCelebrationNeverTakeFocus() {
        val rig = ListRig(ListFixture.allBut(listOf("baking-powder", "maple"), open = "aisle-2-baking"), now = ListFixture.TRIP_START + 41 * ListFixture.MIN)
        show(rig)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("baking-powder")
        compose.waitForIdle()
        focus(ListTags.row("baking-powder"))
        compose.mainClock.autoAdvance = false
        keys.press(Key.Spacebar) { compose.framesUntil(64) { false } }
        fun notOnTheCard() = assertFalse("the Next card never takes focus", keys.focused()?.inside(NEXT_CARD_TAG) == true)
        assertTrue(
            "the Next card showed",
            compose.framesUntil(4_000) {
                notOnTheCard()
                compose.exists(NEXT_CARD_TAG)
            },
        )
        // Into its hold.
        compose.framesUntil(500) {
            notOnTheCard()
            false
        }
        // Baking folded away under her focus: it went on to Baking's strip, where she was (not to the card, not to the
        // top of the screen).
        assertEquals(ListTags.strip("aisle-2-baking"), keys.focusedTag())
        val during = keys.focused()
        // Escape skips the card from wherever focus is; focus stays put.
        keys.press(Key.Escape) { compose.framesUntil(600) { false } }
        assertFalse("Escape skipped the Next card", compose.exists(NEXT_CARD_TAG))
        assertEquals("focus did not move", during!!.id, keys.focused()?.id)
        // The last thing, from the keyboard: the celebration plays over the list and leaves focus where it was.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("maple")
        compose.waitForIdle()
        focus(ListTags.row("maple"))
        compose.mainClock.autoAdvance = false
        keys.press(Key.Spacebar) { compose.framesUntil(64) { false } }
        assertTrue("the celebration plays", compose.framesUntil(3_000) { compose.exists(CELEBRATION_TAG) })
        compose.framesUntil(2_000) { false }
        val f = keys.focused()
        assertFalse("the celebration never takes focus", f?.inside(CELEBRATION_TAG) == true)
        assertEquals("Breakfast folded away: focus is on its strip", ListTags.strip("breakfast"), keys.focusedTag())
        keys.press(Key.Escape) { compose.framesUntil(1_000) { false } }
        assertFalse("Escape closes the celebration", compose.exists(CELEBRATION_TAG))
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun nothingFocusedHidesUnderThePlateOrTheDock() {
        val rig = ListRig(ListFixture.longAisle())
        show(rig)
        val rows = rig.vm.state.value.sections.first { it.id == "household" }.rows.map { it.id }
        assertTrue("a long aisle: ${rows.size} rows", rows.size >= 10)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey(rows.last())
        compose.waitForIdle()
        focus(ListTags.row(rows.last()))
        val list = compose.boundsOf(ListTags.LIST)
        var checked = 0
        // Up the aisle with Shift+Tab, then back down with Tab: every row that takes focus is wholly in view, clear of
        // the plate pinned at the top and of the dock at the bottom.
        for (shift in listOf(true, false)) {
            repeat(rows.size + 2) {
                keys.press(Key.Tab, shift = shift)
                val tag = keys.focusedTag() ?: return@repeat
                if (!tag.startsWith("picnic_row_")) return@repeat
                val row = compose.boundsOf(tag)
                val plate = compose.onAllNodes(hasTestTag(ListTags.header("household"))).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
                if (plate != null && plate.top <= list.top + 1f) {
                    assertTrue("$tag (top ${row.top}) is under the pinned plate (bottom ${plate.bottom})", row.top >= plate.bottom - 1f)
                }
                val dock = compose.onAllNodes(hasTestTag(earth.cora.picnic.ui.add.ADD_BAR_TAG)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
                if (dock != null) assertTrue("$tag (bottom ${row.bottom}) is under the dock (top ${dock.top})", row.bottom <= dock.top + 1f)
                checked++
            }
        }
        assertTrue("rows were focused: $checked", checked >= rows.size)
    }
}

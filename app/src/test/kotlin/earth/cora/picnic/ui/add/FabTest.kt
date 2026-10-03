package earth.cora.picnic.ui.add

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The add bar folding into the FAB (docs/DESIGN_STICKER.md §6.1 "Collapsed add bar", item 116): the rules on
 * [AddBarCollapseState] with a fake clock (1 px = 1 dp), then a real fling in the harness and the FAB tap.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class FabTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun collapseRules() {
        var now = 10_000L
        val s = AddBarCollapseState(density = 1f) { now }

        // ≥ 70 dp of downward scroll past 140 dp from the top folds it.
        s.onScrolled(100f)
        assertFalse("still within the first 140 dp", s.collapsed)
        s.onScrolled(100f)
        assertFalse("60 dp past 140", s.collapsed)
        s.onScrolled(10f)
        assertTrue("70 dp past 140", s.collapsed)

        // 40 dp up brings it back.
        s.onScrolled(-39f)
        assertTrue(s.collapsed)
        s.onScrolled(-1f)
        assertFalse(s.collapsed)

        // Down again: a fresh 70 dp run; reaching the top brings it back.
        s.onScrolled(69f)
        assertFalse(s.collapsed)
        s.onScrolled(1f)
        assertTrue(s.collapsed)
        s.atTop(true)
        assertFalse(s.collapsed)

        // Programmatic scrolls (the next aisle's auto-open) are ignored for 1.2 s.
        s.onScrolled(300f)
        s.expand()
        s.ignoreProgrammaticScroll()
        s.onScrolled(400f)
        now += 1_199
        s.onScrolled(100f)
        assertFalse("ignored for 1.2 s", s.collapsed)
        now += 1
        s.onScrolled(70f)
        assertTrue("counts again after 1.2 s", s.collapsed)

        // Never while the field is focused or a sheet is open; the FAB (expand) restores it.
        s.expand()
        s.fieldFocused = true
        s.onScrolled(500f)
        assertFalse("never while focused", s.collapsed)
        s.fieldFocused = false
        s.sheetOpen = true
        s.onScrolled(500f)
        assertFalse("never under a sheet", s.collapsed)
        s.sheetOpen = false
        s.onScrolled(70f)
        assertTrue(s.collapsed)
        s.expand()
        assertFalse(s.collapsed)
    }

    @Test
    fun aFlingFoldsTheBarAndTheFabFocusesTheField() {
        compose.setHarness()
        val list = compose.onNodeWithTag(HARNESS_LIST_TAG)
        // Drag above the bar (it overlaps the list's bottom).
        list.performTouchInput { swipeUp(startY = centerY + 150f, endY = top + 40f) }
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_FAB_TAG).assertExists()
        compose.onNodeWithTag(ADD_BAR_TAG).assertDoesNotExist()

        compose.onNodeWithTag(ADD_FAB_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_BAR_TAG).assertExists()
        compose.onNodeWithTag(ADD_FIELD_TAG).assertIsFocused()
        compose.onNodeWithTag(ADD_FAB_TAG).assertDoesNotExist()
    }

    @Test
    fun theTopBringsTheBarBack() {
        compose.setHarness()
        val list = compose.onNodeWithTag(HARNESS_LIST_TAG)
        list.performTouchInput { swipeUp(startY = centerY + 150f, endY = top + 40f) }
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_FAB_TAG).assertExists()
        repeat(4) { list.performTouchInput { swipeDown(startY = top + 40f, endY = centerY + 150f) } }
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_BAR_TAG).assertExists()
    }
}

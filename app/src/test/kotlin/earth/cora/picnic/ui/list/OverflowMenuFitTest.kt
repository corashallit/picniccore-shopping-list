package earth.cora.picnic.ui.list

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The overflow menu in a short window (docs/DESIGN_STICKER.md §4.24, §15 "every control reachable"; items 121 and 331):
 * a phone on its side, at font scale 1.0 and 2.0. The card stops above the window's bottom edge and its rows scroll, so
 * each item can be brought fully inside the window and tapped; Settings opens from the List.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
class OverflowMenuFitTest {
    @get:Rule
    val compose = createComposeRule()

    private val labels = listOf("New trip", "Switch list…", "Refresh", "My store & aisles", "My pantry", "Sticker album", "Settings")

    private fun everyItemReachable(fontScale: Float) {
        val rig = ListRig()
        compose.show(rig, fontScale = fontScale)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag(LIST_MENU_TAG).fetchSemanticsNode().boundsInRoot
        val slack = compose.density.density
        assertTrue("the card ends inside the window ($menu in $root)", menu.bottom <= root.bottom - 8 * compose.density.density + slack && menu.top >= root.top)
        for (label in labels) {
            val item = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(LIST_MENU_TAG)), useUnmergedTree = true)
            item.performScrollTo()
            compose.waitForIdle()
            val b = item.fetchSemanticsNode().boundsInRoot
            assertTrue("\"$label\" inside the window at $fontScale ($b in $root)", b.top >= root.top && b.bottom <= root.bottom)
            assertTrue("\"$label\" inside the card at $fontScale ($b in $menu)", b.top >= menu.top - slack && b.bottom <= menu.bottom + slack)
        }
        // The last one works from here.
        compose.onNode(hasText("Settings") and hasAnyAncestor(hasTestTag(LIST_MENU_TAG)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, rig.settingsOpened.size)
    }

    @Test
    fun everyItemReachableOnItsSide() = everyItemReachable(1f)

    @Test
    fun everyItemReachableOnItsSideAtLargeText() = everyItemReachable(2f)

    /** A pane too short for three rows under the More button: the card rises to end 8 dp above the bottom instead. */
    @Test
    @Config(qualifiers = "w780dp-h200dp-land-xxhdpi")
    fun theCardRisesInAShortPane() {
        val rig = ListRig()
        compose.show(rig)
        val more = compose.onNodeWithContentDescription("More options").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag(LIST_MENU_TAG).fetchSemanticsNode().boundsInRoot
        val d = compose.density.density
        assertTrue("it rose above the More button's bottom ($menu, button $more)", menu.top < more.bottom)
        assertEquals("it ends 8 dp above the bottom", root.bottom - 8 * d, menu.bottom, 2 * d)
        assertTrue(menu.top >= root.top + 8 * d - d)
        compose.onNode(hasText("Settings") and hasAnyAncestor(hasTestTag(LIST_MENU_TAG)), useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
        compose.onNode(hasText("Settings") and hasAnyAncestor(hasTestTag(LIST_MENU_TAG)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, rig.settingsOpened.size)
    }
}

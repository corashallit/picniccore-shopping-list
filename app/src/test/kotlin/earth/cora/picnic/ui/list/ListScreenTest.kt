package earth.cora.picnic.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import org.junit.Assert.assertEquals
import androidx.compose.foundation.layout.fillMaxSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The List screen's structure (docs/DESIGN_STICKER.md §6.1, D1; items 83, 88). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun endNote(n: Int) {
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("end-note")
        compose.waitForIdle()
        compose.onNodeWithText("end of the list · $n stickers", substring = true, useUnmergedTree = true).assertExists()
    }

    /**
     * §4.2, §6.1, §15 (items 94, 98, 281): with large text the bar grows to two rows and the subtitle keeps the whole
     * width: at 1.3 and 2.0 "Hillcrest Market · 8 stops" reads on one line (it fits there), never squeezed beside the
     * LCD; the LCD, on a line of its own at the right, overlaps neither the subtitle, the title nor More.
     */
    @Test
    fun largeTextBarKeepsTheSubtitleWhole() {
        for (scale in listOf(1.3f, 2f)) largeTextBar(scale)
    }

    private var scaleShown by androidx.compose.runtime.mutableFloatStateOf(1f)

    private fun largeTextBar(scale: Float) {
        if (scaleShown == 1f) {
            scaleShown = scale
            compose.setContent {
                val base = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, scaleShown)) {
                    earth.cora.picnic.ui.theme.PicnicTheme { ListScreen(ListFixture.ui(), ListActions()) }
                }
            }
        } else {
            compose.runOnIdle { scaleShown = scale }
        }
        compose.waitForIdle()
        val subtitle = compose.onNodeWithText("Hillcrest Market", substring = true, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        subtitle.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals("fs $scale: the subtitle reads on one line", 1, layouts.single().lineCount)
        val lcd = compose.onNodeWithTag(ListTags.LCD_SLOT).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Saturday shop", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithTag(ListTags.MORE).fetchSemanticsNode().boundsInRoot
        val patch = subtitle.parent!!.boundsInRoot
        for ((what, r) in listOf("subtitle" to patch, "title" to title, "More" to more)) {
            assertFalse("fs $scale: the LCD $lcd overlaps the $what $r", lcd.overlaps(r) && lcd.intersect(r).width > 0.5f && lcd.intersect(r).height > 0.5f)
        }
        assertTrue("fs $scale: the LCD stands at the right", lcd.right >= more.left)
    }

    /** Item 83: the end note reads "♡ end of the list · N stickers" with the live total (the heart is drawn). */
    @Test
    fun endNoteCount() {
        val rig = ListRig()
        compose.show(rig)
        endNote(24)
        // A tear-off takes one off; Undo puts it back.
        val item = rig.item("toothpaste")
        val index = rig.app.items().indexOf(item)
        rig.vm.delete("toothpaste")
        compose.waitForIdle()
        endNote(23)
        rig.vm.undoDelete(item, index, ListFixture.LIST_ID)
        compose.waitForIdle()
        endNote(24)
    }

    /**
     * Item 88 (D1): checked rows stay in their aisle in place; there is no CHECKED section. Every stop is a strip or the
     * one open tray, the end note last; checking a row keeps it where it was, between the rows still to get.
     */
    @Test
    fun noCheckedSection() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        fun sections(): List<String> = compose.onAllNodes(hasTagPrefix("picnic_strip_").or(hasTagPrefix("picnic_tray_header_")))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            .filter { it.startsWith("picnic_tray_header_") || (it.startsWith("picnic_strip_") && !it.startsWith("picnic_strip_tab_") && !it.startsWith("picnic_strip_meta_") && !it.startsWith("picnic_strip_ribbon_")) }
        // The sections on screen are the fixture's aisles, nothing else (no "In the basket" / CHECKED section).
        val all = HashSet<String>()
        for (key in listOf("header:produce", "strip:bakery", "strip:deli-meat", "strip:aisle-2-baking", "strip:breakfast", "strip:dairy-eggs", "strip:frozen", "strip:household", "end-note")) {
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey(key)
            compose.waitForIdle()
            all += sections()
        }
        val ids = all.map { it.removePrefix("picnic_tray_header_").removePrefix("picnic_strip_") }.toSet()
        assertEquals(setOf("produce", "bakery", "deli-meat", "aisle-2-baking", "breakfast", "dairy-eggs", "frozen", "household"), ids)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:produce")
        compose.waitForIdle()

        fun order(): List<String> = listOf("strawberries", "bananas", "apples", "carrots", "broccoli")
            .sortedBy { compose.boundsOf(ListTags.rowSlot(it)).top }
        assertEquals(listOf("strawberries", "bananas", "apples", "carrots", "broccoli"), order())
        // Checking Strawberries (and the chain) leaves it first, in Produce, stamped.
        compose.onNodeWithTag(ListTags.row("strawberries")).performClick()
        compose.waitForIdle()
        assertTrue(rig.app.isChecked("strawberries"))
        assertEquals(listOf("strawberries", "bananas", "apples", "carrots", "broccoli"), order())
        assertEquals("produce", rig.app.list(ListFixture.LIST_ID)!!.openAisle)
        assertEquals("still 24 rows, each in its aisle", 24, rig.vm.state.value.sections.sumOf { it.rows.size })
    }

    /**
     * §6.5, §6.6: turning the phone or a dark-mode switch recreates the screen; the open menu, the New trip and Trip book
     * sheets and the folded bar come back as they were (the list keeps its own scroll).
     */
    @Test
    fun screenStateSurvivesRecreation() {
        val restore = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        var screen: ListScreenState? = null
        restore.setContent {
            earth.cora.picnic.ui.theme.PicnicTheme {
                val s = rememberListScreenState()
                screen = s
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { ListScreen(ListFixture.ui(), ListActions(), screen = s) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("end-note")
        compose.waitForIdle()
        val before = screen!!
        compose.runOnIdle {
            before.menuOpen = true
            before.tripBookOpen = true
            before.scrolledDown = true
        }
        compose.waitForIdle()
        restore.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        val after = screen!!
        assertTrue("a fresh state object after recreation", after !== before)
        assertTrue("the menu is still open", after.menuOpen)
        assertTrue("the Trip book sheet is still open", after.tripBookOpen)
        assertFalse(after.newTripOpen)
        assertTrue("the bar and the book stay folded", after.scrolledDown)
        compose.onNodeWithTag(LIST_MENU_TAG).assertExists()
        assertTrue("the list kept its scroll", compose.onAllNodesWithTag(ListTags.END_NOTE).fetchSemanticsNodes().isNotEmpty())
    }
}

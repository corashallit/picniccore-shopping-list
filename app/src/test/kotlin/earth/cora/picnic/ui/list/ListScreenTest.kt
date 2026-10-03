package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import org.junit.Assert.assertEquals
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
}

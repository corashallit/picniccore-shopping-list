package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.BookTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The trip sticker book for TalkBack (docs/DESIGN_STICKER.md §7.7, §15; items 158, 159): one node with the summary and
 * "Open trip book"; the twins are decor with no nodes; one polite announcement per twin.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class BookA11yTest {
    @get:Rule
    val compose = createComposeRule()

    /** Item 158. */
    @Test
    fun singleSummaryNode() {
        val rig = ListRig()
        compose.show(rig)
        // What TalkBack walks (the merged tree): the book and nothing under it.
        val node = compose.onNodeWithTag(BookTags.BOOK).fetchSemanticsNode()
        assertEquals(listOf("Trip sticker book, Sat 3 Oct: 10 of 24 stickers, 1 of 8 aisles stamped"), node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals("Open trip book", node.config.getOrNull(SemanticsActions.OnClick)?.label)
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertTrue("no child nodes: the date, twins, stamps and pages are decor (${node.children.map { it.config }})", node.children.isEmpty())
        for (prefix in listOf("picnic_book_twin_", "picnic_book_spot_", "picnic_book_stamp_", "picnic_book_left", "picnic_book_right")) {
            assertTrue("no $prefix nodes", compose.onAllNodes(hasTagPrefix(prefix)).fetchSemanticsNodes().isEmpty())
        }
        // The action opens the Trip book sheet.
        compose.onNodeWithTag(BookTags.BOOK).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(TripBookTags.SHEET).assertExists()
    }

    /** Item 159: "<Name> added to today's sticker book" / "<Name> peeled out of today's sticker book", once each. */
    @Test
    fun announcements() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        compose.onNodeWithTag(ListTags.row("strawberries")).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.row("bananas")).performClick()
        compose.waitForIdle()
        val said = rig.announcer.messages
        assertEquals(1, said.count { it == "Strawberries added to today's sticker book" })
        assertEquals(1, said.count { it == "Bananas peeled out of today's sticker book" })
        assertEquals("one line per twin", 2, said.count { it.contains("today's sticker book") })
    }
}

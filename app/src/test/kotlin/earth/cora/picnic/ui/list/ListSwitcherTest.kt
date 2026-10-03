package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.PicnicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The list switcher (§6.4, checklist 144, 219): each list with its LCD count; switch, new, rename, delete. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListSwitcherTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun launch(lists: List<ListSummary> = SwitcherSamples.lists, stickers: Map<String, Int> = emptyMap()) {
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    ListSwitcherSheet(
                        lists = lists,
                        onDismiss = { calls += "dismiss" },
                        onSwitch = { calls += "switch:$it" },
                        onNew = { calls += "new:$it" },
                        onRename = { id, t -> calls += "rename:$id:$t" },
                        onDelete = { calls += "delete:$it" },
                        stickers = stickers,
                    )
                }
            }
        }
    }

    @Test
    fun listsWithCountsAndSwitch() {
        launch()
        compose.onNodeWithText("Saturday shop", substring = true).assertIsSelected()
        compose.onNodeWithText("9 to get", substring = true).assertExists()
        compose.onNodeWithText("All picked up", substring = true).assertExists()
        compose.onNodeWithText("Costco run", substring = true).performClick()
        assertEquals(listOf("switch:costco"), calls)
    }

    @Test
    fun newList() {
        launch()
        compose.onNodeWithText("New list").performClick()
        compose.onNodeWithContentDescription("New list").performTextInput("Party snacks")
        compose.onNodeWithContentDescription("New list").performImeAction()
        assertEquals(listOf("new:Party snacks"), calls)
    }

    @Test
    fun rename() {
        launch()
        compose.onNodeWithContentDescription("Rename Costco run").performClick()
        compose.onNodeWithContentDescription("List name").performTextReplacement("Big shop")
        compose.onNodeWithContentDescription("List name").performImeAction()
        assertEquals(listOf("rename:costco:Big shop"), calls)
    }

    @Test
    fun lastListCannotBeDeleted() {
        launch(lists = SwitcherSamples.lists.take(1))
        compose.onNodeWithContentDescription("Delete Saturday shop").assertIsNotEnabled()
    }

    @Test
    fun deleteFilesBook() {
        launch(stickers = mapOf("saturday" to 10))
        compose.onNodeWithContentDescription("Delete Saturday shop").performClick()
        compose.onNodeWithText("Delete Saturday shop?").assertExists()
        // A list with a trip says where its book goes (TripFiling.deleteList files it before the list goes).
        compose.onNodeWithText("Its sticker book goes into your album.").assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("delete:saturday"), calls)
    }

    @Test
    fun deleteWithoutBookSaysNothingOfIt() {
        launch(stickers = emptyMap())
        compose.onNodeWithContentDescription("Delete Costco run").performClick()
        compose.onNodeWithText("Delete Costco run?").assertExists()
        compose.onNodeWithText("Its sticker book goes into your album.").assertDoesNotExist()
        compose.onNodeWithText("Not yet").performClick()
        assertEquals(emptyList<String>(), calls)
    }
}

/** Switcher samples: Saturday shop (current, 9 left), Costco run (2 left), Party snacks (all picked up). */
object SwitcherSamples {
    val lists: List<ListSummary> = listOf(
        ListSummary("saturday", "Saturday shop", remaining = 9, current = true),
        ListSummary("costco", "Costco run", remaining = 2, current = false),
        ListSummary("party", "Party snacks", remaining = 0, current = false),
    )
}

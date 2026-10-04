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
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.common.SaturdayShop
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

    private fun launch(lists: List<ListSummary> = SwitcherSamples.lists) {
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
                    )
                }
            }
        }
    }

    @Test
    fun listsWithCountsAndSwitch() {
        launch()
        compose.onNodeWithText("Saturday shop", substring = true).assertIsSelected()
        // The count is said once: the LCD while something is left, "All picked up" in its place when nothing is.
        compose.onNodeWithText("9 to get", substring = true).assertDoesNotExist()
        compose.onNodeWithText("All picked up", substring = true).assertExists()
        compose.onNodeWithText("Big shop", substring = true).performClick()
        assertEquals(listOf("switch:big"), calls)
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
        compose.onNodeWithContentDescription("Rename Big shop").performClick()
        compose.onNodeWithContentDescription("List name").performTextReplacement("Monthly shop")
        compose.onNodeWithContentDescription("List name").performImeAction()
        assertEquals(listOf("rename:big:Monthly shop"), calls)
    }

    @Test
    fun lastListCannotBeDeleted() {
        launch(lists = SwitcherSamples.lists.take(1))
        compose.onNodeWithContentDescription("Delete Saturday shop").assertIsNotEnabled()
    }

    /**
     * Checklist 144, through the List screen's own state (buildListUiState, what ListViewModel shows): a list whose open
     * trip has stickers in its book says the book goes into the album before it is deleted (TripFiling files it first).
     */
    @Test
    fun deleteFilesBook() {
        val lists = SwitcherSamples.fromState()
        assertEquals(3, lists.first { it.id == SaturdayShop.LIST_ID }.bookStickers)
        launch(lists)
        compose.onNodeWithContentDescription("Delete Saturday shop").performClick()
        compose.onNodeWithText("Delete Saturday shop?").assertExists()
        compose.onNodeWithText("Its 24 items go with it. Its sticker book goes into your album.").assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("delete:${SaturdayShop.LIST_ID}"), calls)
    }

    /** A list with no trip (nothing checked since its last New trip) has no book: the sheet says only what goes with it. */
    @Test
    fun deleteWithoutBookSaysNothingOfIt() {
        val lists = SwitcherSamples.fromState()
        assertEquals(0, lists.first { it.id == "big" }.bookStickers)
        launch(lists)
        compose.onNodeWithContentDescription("Delete Big shop").performClick()
        compose.onNodeWithText("Delete Big shop?").assertExists()
        compose.onNodeWithText("Its 2 items go with it.").assertExists()
        compose.onNodeWithText("Its sticker book goes into your album.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Not yet").performClick()
        assertEquals(emptyList<String>(), calls)
    }
}

/** Switcher samples: Saturday shop (current, 9 left, 10 stickers in its book), Big shop (2 left), Party snacks (all picked up). */
object SwitcherSamples {
    val lists: List<ListSummary> = listOf(
        ListSummary(SaturdayShop.LIST_ID, "Saturday shop", remaining = 9, current = true, bookStickers = 10),
        ListSummary("big", "Big shop", remaining = 2, current = false),
        ListSummary("party", "Party snacks", remaining = 0, current = false),
    )

    /**
     * The switcher's lists as the List screen builds them: Saturday shop with a trip of 3 check-offs (bananas, carrots,
     * broccoli), and Big shop with two items and no trip.
     */
    fun fromState(): List<ListSummary> {
        val start = SaturdayShop.TRIP_START
        val saturday = listOf("bananas", "carrots", "broccoli").foldIndexed(SaturdayShop.unchecked()) { i, s, id ->
            s.toggleChecked(SaturdayShop.LIST_ID, id, start + i * 60_000L)
        }
        val big = GroceryList(
            "big", "Big shop", SaturdayShop.WRITTEN,
            listOf(SaturdayShop.item("toilet-paper"), SaturdayShop.item("cat-food")).map { it.copy(id = "big-" + it.id) },
        )
        val state = saturday.copy(lists = saturday.lists + big)
        return buildListUiState(state, emptySet(), hasToken = false, editingId = null, catalog = IconCatalog.default).lists
    }
}

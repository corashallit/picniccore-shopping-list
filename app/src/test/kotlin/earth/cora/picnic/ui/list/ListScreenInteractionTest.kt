package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.theme.PicnicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Gestures of the stateless List screen map to the right actions. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ListScreenInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(app: AppState = Samples.shoppingState) {
        val state = buildListUiState(app, emptySet(), hasToken = true, editingId = null, catalog = IconCatalog.default)
        compose.setContent {
            PicnicTheme {
                ListScreen(
                    state,
                    ListActions(
                        onToggle = { calls += "toggle:$it" },
                        onOpenEdit = { calls += "edit:$it" },
                        onDelete = { calls += "delete:$it" },
                        onToggleSection = { calls += "section:$it" },
                    ),
                )
            }
        }
    }

    @Test
    fun tapTogglesLongPressEditsAndHeadersCollapse() {
        show()
        compose.onNodeWithText("Dish rack").performClick()
        compose.onNodeWithText("Birthday candles").performSemanticsAction(SemanticsActions.OnLongClick)
        // Headers expose "<title>, <n> to get, <m> in the basket" (the theme clears the inner text).
        compose.onNodeWithContentDescription("Produce, 3 to get, 0 in the basket").performClick()
        assertEquals(listOf("toggle:i-rack", "edit:i-candles", "section:produce"), calls)
    }

    @Test
    fun headersAnnounceWhatIsLeftToGetAndWhatIsInTheBasket() {
        // Dairy is collapsed (Samples); tick the eggs: 3 left to get, 1 in its basket.
        show(Samples.shoppingState.toggleChecked(Samples.LIST_ID, "i-eggs"))
        compose.onNodeWithContentDescription("Bakery, 0 to get, 2 in the basket, All picked up!").assertExists()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasContentDescription("Dairy & eggs", substring = true))
        compose.onNodeWithContentDescription("Dairy & eggs, 3 to get, 1 in the basket")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed"))
        // The checked section lists the gotten items itself.
        list.performScrollToNode(hasContentDescription("In the basket", substring = true))
        compose.onNodeWithContentDescription("In the basket, 4 items")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded"))
    }

    @Test
    fun swipingARowDeletesIt() {
        show()
        compose.onNodeWithText("Dish rack").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf("delete:i-rack"), calls)
    }
}

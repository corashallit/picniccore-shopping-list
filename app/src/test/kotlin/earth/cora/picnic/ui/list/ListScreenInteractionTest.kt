package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.catalog.IconCatalog
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

    private fun show() {
        val state = buildListUiState(Samples.shoppingState, emptySet(), hasToken = true, editingId = null, catalog = IconCatalog.default)
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
        // Headers expose "<title>, <count> items" (the theme clears the inner text).
        compose.onNodeWithContentDescription("Produce, 3 items").performClick()
        assertEquals(listOf("toggle:i-rack", "edit:i-candles", "section:produce"), calls)
    }

    @Test
    fun swipingARowDeletesIt() {
        show()
        compose.onNodeWithText("Dish rack").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf("delete:i-rack"), calls)
    }
}

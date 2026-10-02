package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.core.model.updateList
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.common.snap
import earth.cora.picnic.ui.common.themeParameters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens: list_empty, list_mixed + list_mixed_end (unsorted, collapsed baskets, cleared aisles, sparkles,
 * checked), list_pinned (rows scrolling under a pinned band), list_switcher.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ListScreenshotTest(private val dark: Boolean, @Suppress("unused") private val theme: String) {
    @get:Rule
    val compose = createComposeRule()

    private fun ui(state: AppState, busy: Set<String> = emptySet(), editingId: String? = null) =
        buildListUiState(state, busy, hasToken = true, editingId = editingId, catalog = IconCatalog.default)

    @Test
    fun empty() = compose.snap("list_empty", dark) {
        ListScreen(ui(Samples.emptyState), ListActions())
    }

    /**
     * Collapsed baskets hold what has been GOTTEN: Produce (lemons and avocados ticked: two in the
     * basket, one to get), Bakery (cleared: a full basket and the stamp) and Dairy (nothing ticked
     * yet: an empty crate, four to get).
     */
    private val midTrip = Samples.shoppingState
        .toggleChecked(Samples.LIST_ID, "i-lemons", Samples.NOW)
        .toggleChecked(Samples.LIST_ID, "i-avocado", Samples.NOW)
        .updateList(Samples.LIST_ID) { it.copy(collapsedAisles = setOf("produce", "bakery", "dairy-eggs")) }

    @Test
    fun mixed() = compose.snap("list_mixed", dark) {
        ListScreen(ui(midTrip, busy = Samples.busy), ListActions())
    }

    /** The end of the same list: the drawing sparkle, a collapsed basket, the checked section and "Clear checked". */
    @Test
    fun mixedEnd() = compose.snap(
        "list_mixed_end",
        dark,
        before = { onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tap to tick off", substring = true)) },
    ) {
        ListScreen(ui(midTrip, busy = Samples.busy), ListActions())
    }

    /**
     * Aisle 2 pinned under the top bar while its rows scroll beneath it: the first row straddles the
     * viewport's top edge and the second runs under the band. Nothing of either may show above or
     * beside the pinned band.
     */
    @Test
    fun pinned() = compose.snap(
        "list_pinned",
        dark,
        before = {
            val aisle2 = hasContentDescription("Aisle 2 - Baking", substring = true)
            val list = onNode(hasScrollToIndexAction())
            list.performScrollToNode(aisle2)
            waitForIdle()
            val viewport = list.fetchSemanticsNode().boundsInRoot
            val header = onNode(aisle2).fetchSemanticsNode().boundsInRoot
            val row = onNodeWithText("All-purpose flour").fetchSemanticsNode().boundsInRoot
            // Header off the top, then half the first row: it is cut by the viewport edge mid-row.
            val dy = header.top - viewport.top + header.height + row.height / 2
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
        },
    ) {
        ListScreen(ui(midTrip, busy = Samples.busy), ListActions())
    }

    @Test
    fun switcher() = compose.snap("list_switcher", dark, wholeScreen = true) {
        ListScreen(ui(Samples.shoppingState), ListActions(), showLists = true)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = themeParameters()
    }
}

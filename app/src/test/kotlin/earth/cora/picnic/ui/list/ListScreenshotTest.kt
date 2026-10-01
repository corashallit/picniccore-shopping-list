package earth.cora.picnic.ui.list

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
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

/** Goldens: list_empty, list_mixed + list_mixed_end (unsorted, collapsed baskets, cleared aisles, sparkles, checked), list_switcher. */
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

    /** Produce and dairy collapsed: baskets with mini icons above the fold. */
    private val midTrip = Samples.shoppingState.updateList(Samples.LIST_ID) {
        it.copy(collapsedAisles = setOf("produce", "dairy-eggs"))
    }

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

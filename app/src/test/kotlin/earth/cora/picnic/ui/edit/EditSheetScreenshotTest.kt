package earth.cora.picnic.ui.edit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.common.snap
import earth.cora.picnic.ui.common.themeParameters
import earth.cora.picnic.ui.list.ListActions
import earth.cora.picnic.ui.list.ListScreen
import earth.cora.picnic.ui.list.buildListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Goldens: edit_details (aisle chips, quick icon picks, draw button) and edit_icons (full catalog grid). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class EditSheetScreenshotTest(private val dark: Boolean, @Suppress("unused") private val theme: String) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun details() = compose.snap("edit_details", dark, wholeScreen = true) {
        val state = buildListUiState(Samples.shoppingState, emptySet(), hasToken = true, editingId = "i-avocado", catalog = IconCatalog.default)
        ListScreen(state, ListActions())
    }

    @Test
    fun iconGrid() = compose.snap("edit_icons", dark, wholeScreen = true) {
        val state = buildListUiState(Samples.shoppingState, Samples.busy, hasToken = true, editingId = "i-gochujang", catalog = IconCatalog.default)
        ListScreen(state.copy(editing = null), ListActions())
        EditItemSheet(
            state = state.editing!!,
            onSave = { _, _, _ -> },
            onClose = {},
            onMoveToAisle = {},
            onSetIcon = {},
            onRedraw = {},
            onDelete = {},
            initialPage = EditPage.Icons,
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = themeParameters()
    }
}

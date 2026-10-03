package earth.cora.picnic.ui.aisles

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AisleStyle
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * My store goldens (checklist 225, §6.7 states): `mystore_{default,editing}_{day,glow}`; Day only: `mystore_dragging`,
 * `mystore_resorting`, `mystore_text_focused`, `mystore_error`, `mystore_fs130`, `mystore_fs200`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class MyStoreGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    private val day get() = variant == ThemeVariant.Day

    private fun record(
        name: String,
        state: AislesUiState,
        exact: Boolean = false,
        fontScale: Float? = null,
        frozen: Boolean = false,
        drag: DragPreview? = null,
        focusText: Boolean = false,
        toast: String? = null,
        before: ComposeContentTestRule.() -> Unit = {},
    ) = compose.recordScreen(if (exact) name else goldenName(name, variant), variant, fontScale = fontScale, frozen = frozen, before = before) {
        val toasts = remember { ToastHostState() }
        if (toast != null) LaunchedEffect(Unit) { toasts.show(ToastMessage(toast, kind = "notice")) }
        AislesScreen(state, AislesActions(), toasts = toasts, initialDrag = drag, focusText = focusText)
    }

    @Test
    fun default() = record("mystore_default", MyStoreSamples.default)

    @Test
    fun editing() = record("mystore_editing", MyStoreSamples.default.copy(editing = "bakery"))

    @Test
    fun dragging() {
        // "Aisle 1 - Breakfast…" lifted a little past the next slot: only its shadow meets a neighbour's border.
        if (day) record("mystore_dragging", MyStoreSamples.default, exact = true, drag = DragPreview(index = 4, offsetSlots = 1.15f))
    }

    @Test
    fun resorting() {
        if (day) {
            record("mystore_resorting", MyStoreSamples.default.copy(syncing = true), exact = true, before = {
                onNodeWithText("Re-sort my list").performScrollTo()
            })
        }
    }

    @Test
    fun textFocused() {
        if (day) {
            record("mystore_text_focused", MyStoreSamples.default, exact = true, focusText = true, before = {
                onNodeWithTag(AislesTags.TEXT).performScrollTo()
            })
        }
    }

    @Test
    fun error() {
        if (day) record("mystore_error", MyStoreSamples.default, exact = true, frozen = true, toast = "Couldn't save. Try again.")
    }

    @Test
    fun fontScale130() {
        if (day) record("mystore_fs130", MyStoreSamples.default, exact = true, fontScale = 1.3f)
    }

    @Test
    fun fontScale200() {
        if (day) record("mystore_fs200", MyStoreSamples.default, exact = true, fontScale = 2f)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

/** My store's sample: the default Ontario store at Hillcrest Market, Frozen restyled mint check, Baking in store aisle 2. */
object MyStoreSamples {
    val app: AppState = Samples.shoppingState.let { s ->
        s.copy(
            store = s.store.copy(
                name = "Hillcrest Market",
                aisleStyles = mapOf("frozen" to AisleStyle(family = "mint", pattern = "check")),
            ),
        )
    }

    val default: AislesUiState = AislesViewModel.stateOf(app, IconCatalog.default.baskets)
}

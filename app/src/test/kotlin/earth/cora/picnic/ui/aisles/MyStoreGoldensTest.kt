package earth.cora.picnic.ui.aisles

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.SaturdayShop
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
 * My store goldens (checklist 225, §6.7 states, Appendix C), from the Appendix A fixture (Hillcrest Market, 8 aisles,
 * "Aisle 2 - Baking" in store aisle 2), compared with the mockup's phone 4: `mystore_{default,editing,dragging,resorting,
 * error,text_focused}_{day,glow}`; Day only, with the app's 14 default aisles (Appendix A `fixtureDefaultAisles`, the
 * long names): `mystore_fs130`, `mystore_fs200`.
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

    /** The mockup's open label: 5 · Breakfast, its name field in focus, the swatches and baskets under it. */
    @Test
    fun editing() = record("mystore_editing", MyStoreSamples.default.copy(editing = "breakfast"))

    /** 7 · Frozen lifted (tilted, hsLift) a third of a slot down: only its shadow meets Household's border. */
    @Test
    fun dragging() = record("mystore_dragging", MyStoreSamples.default, drag = DragPreview(index = 6, offsetSlots = 0.3f))

    @Test
    fun resorting() = record("mystore_resorting", MyStoreSamples.default.copy(syncing = true), before = {
        onNodeWithText("Re-sort my list").performScrollTo()
    })

    @Test
    fun textFocused() = record("mystore_text_focused", MyStoreSamples.default, before = {
        onNodeWithTag(AislesTags.TEXT).performScrollTo().performClick()
        waitForIdle()
        onNodeWithContentDescription("Aisles as text:").assertIsFocused()
    })

    @Test
    fun error() = record("mystore_error", MyStoreSamples.default, frozen = true, toast = "Couldn't save. Try again.")

    @Test
    fun fontScale130() {
        if (day) record("mystore_fs130", MyStoreSamples.longNames, exact = true, fontScale = 1.3f)
    }

    @Test
    fun fontScale200() {
        if (day) record("mystore_fs200", MyStoreSamples.longNames, exact = true, fontScale = 2f)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

/** My store's samples: the Appendix A store (Hillcrest Market, 8 aisles), and the same store on the 14 default aisles. */
object MyStoreSamples {
    val app: AppState = SaturdayShop.state()

    val default: AislesUiState = AislesViewModel.stateOf(app, IconCatalog.default.baskets)

    val longNames: AislesUiState = AislesViewModel.stateOf(SaturdayShop.defaultAisles(), IconCatalog.default.baskets)
}

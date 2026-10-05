package earth.cora.picnic.ui.aisles

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import earth.cora.picnic.ui.common.focusWithoutTouch
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.setAisleNote
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
        toast: String? = null,
        before: ComposeContentTestRule.() -> Unit = {},
    ) = compose.recordScreen(if (exact) name else goldenName(name, variant), variant, fontScale = fontScale, frozen = frozen, before = before) {
        val toasts = remember { ToastHostState() }
        if (toast != null) LaunchedEffect(Unit) { toasts.show(ToastMessage(toast, kind = "notice")) }
        AislesScreen(state, AislesActions(), toasts = toasts, initialDrag = drag)
    }

    @Test
    fun default() = record("mystore_default", MyStoreSamples.default)

    /** The mockup's open label: 5 · Breakfast, its name field in focus, the swatches and baskets under it. */
    @Test
    fun editing() = record("mystore_editing", MyStoreSamples.default.copy(editing = "breakfast"), before = {
        focusWithoutTouch(onNodeWithContentDescription("Aisle name"))
    })

    /** 7 · Frozen lifted (tilted, hsLift) a little way down, as in the mockup: only its shadow meets Household's border. */
    @Test
    fun dragging() = record("mystore_dragging", MyStoreSamples.default, drag = DragPreview(index = 6, offsetSlots = 0.12f))

    @Test
    fun resorting() = record("mystore_resorting", MyStoreSamples.default.copy(syncing = true), before = {
        // The button and the line under it ("Sorting your list into the aisles…") in view.
        onNodeWithText("Sorting your list into the aisles…").performScrollTo()
        val more = with(density) { 96.dp.toPx() }
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, more) }
    })

    @Test
    fun textFocused() = record("mystore_text_focused", MyStoreSamples.default, before = {
        onNodeWithTag(AislesTags.TEXT).performScrollTo()
        focusWithoutTouch(onNodeWithContentDescription("Aisles as text:"))
    })

    @Test
    fun error() = record("mystore_error", MyStoreSamples.default, frozen = true, toast = "Couldn’t save. Try again.")

    /**
     * "Also here" (Cora, 5 October; §6.7): two labels carry a note on a quiet line under the name ("also here: toothpaste,
     * floss"); the others read as before.
     */
    @Test
    fun notes() = record("mystore_notes", MyStoreSamples.notes)

    /** The open label's "Also here" field (the editor's last field), written and in focus, with its hint. */
    @Test
    fun editingNote() = record("mystore_editing_note", MyStoreSamples.notes.copy(editing = "produce"), before = {
        onNodeWithTag(AislesTags.noteField()).performScrollTo()
        focusWithoutTouch(onNodeWithContentDescription("Also here"))
    })

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

    /** The Appendix A store with two "Also here" notes (Cora, 5 October): Produce and Household. */
    val notes: AislesUiState = AislesViewModel.stateOf(
        app.setAisleNote("produce", "toothpaste, floss").setAisleNote("household", "birthday candles, paper cups"),
        IconCatalog.default.baskets,
    )
}

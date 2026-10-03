package earth.cora.picnic.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.list.EditItemUi
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Edit sheet goldens (checklist 217, 329, Appendix C), from the Appendix A fixture (Milk, 2 % in 6 · Dairy & eggs at
 * Hillcrest Market): `edit_{default,renamed,drawing,nokey}_{day,glow}`; Day only: `edit_sheet_pantry_on`, `edit_fs130`,
 * `edit_fs200`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class EditGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    private val day get() = variant == ThemeVariant.Day

    private fun record(
        name: String,
        state: EditItemUi,
        favourite: Boolean = false,
        inPantry: Boolean = false,
        fontScale: Float? = null,
        before: ComposeContentTestRule.() -> Unit = {},
    ) = compose.recordScreen(name, variant, fontScale = fontScale, before = before) {
        Box(Modifier.fillMaxSize()) {
            PicnicBackground(Modifier.fillMaxSize()) {}
            EditItemSheetContent(
                state, { _, _, _ -> }, {}, {}, {}, {}, {},
                favourite = favourite, inPantry = inPantry, aisleStyles = EditSamples.app.store.aisleStyles,
            )
        }
    }

    /** The sheet scrolled to its end, the last line clear of the footer's fade. */
    private fun ComposeContentTestRule.toTheEnd() {
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
    }

    @Test
    fun default() = record(goldenName("edit_default", variant), EditSamples.milk())

    /** "renamed (dirty)" (§6.3): a new name typed, so Save is live. */
    @Test
    fun renamed() = record(goldenName("edit_renamed", variant), EditSamples.milk(), before = {
        onNodeWithContentDescription("Name").performTextReplacement("Milk, 1 %")
        waitForIdle()
        onNodeWithText("Save").assertIsEnabled()
    })

    @Test
    fun drawing() = record(goldenName("edit_drawing", variant), EditSamples.milk(drawing = true), before = { toTheEnd() })

    @Test
    fun noKey() = record(goldenName("edit_nokey", variant), EditSamples.milk(canDraw = false), before = { toTheEnd() })

    @Test
    fun pantryOn() {
        if (day) record("edit_sheet_pantry_on", EditSamples.milk(), favourite = true, inPantry = true)
    }

    @Test
    fun fontScale130() {
        if (day) record("edit_fs130", EditSamples.milk(), fontScale = 1.3f)
    }

    @Test
    fun fontScale200() {
        if (day) record("edit_fs200", EditSamples.milk(), fontScale = 2f)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

package earth.cora.picnic.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
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
 * Edit sheet goldens (checklist 217, 329): `edit_default_{day,glow}`; Day only: `edit_drawing`, `edit_nokey`,
 * `edit_sheet_pantry_on`, `edit_fs200`.
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
            EditItemSheet(state, { _, _, _ -> }, {}, {}, {}, {}, {}, favourite = favourite, inPantry = inPantry)
        }
    }

    /** The sheet scrolled to its end, the last line clear of the footer's fade. */
    private fun ComposeContentTestRule.toTheEnd() {
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
    }

    @Test
    fun default() = record(goldenName("edit_default", variant), EditSamples.milk())

    @Test
    fun drawing() {
        if (day) {
            record("edit_drawing", EditSamples.milk(drawing = true), before = { toTheEnd() })
        }
    }

    @Test
    fun noKey() {
        if (day) {
            record("edit_nokey", EditSamples.milk(canDraw = false), before = { toTheEnd() })
        }
    }

    @Test
    fun pantryOn() {
        if (day) record("edit_sheet_pantry_on", EditSamples.milk(), favourite = true, inPantry = true)
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

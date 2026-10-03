package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** List switcher goldens (checklist 144, 219): `list_switcher_{day,glow}`, Day `deletelist_sheet_with_book`. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListSwitcherGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    private fun record(name: String, confirm: String? = null) = compose.recordScreen(name, variant) {
        Box(Modifier.fillMaxSize()) {
            PicnicBackground(Modifier.fillMaxSize()) {}
            ListSwitcherSheet(
                lists = SwitcherSamples.lists,
                onDismiss = {},
                onSwitch = {},
                onNew = {},
                onRename = { _, _ -> },
                onDelete = {},
                stickers = mapOf("saturday" to 10),
                initialConfirmDelete = confirm,
            )
        }
    }

    @Test
    fun switcher() = record(goldenName("list_switcher", variant))

    @Test
    fun deleteWithBook() {
        if (variant == ThemeVariant.Day) record("deletelist_sheet_with_book", confirm = "saturday")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

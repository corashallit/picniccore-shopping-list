package earth.cora.picnic.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.recordGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sticker contact goldens (WP1, docs/DESIGN_STICKER.md §20.3 items 33 and 34, Appendix C), drawn through the app's
 * own renderer, [ItemIcon], on the Day, Glow and Dusk binders: every sticker at 52 dp (a list row) with its 30 dp (a
 * basket mini) and 22 dp (a book slot) beside it underneath, so the art is reviewed at the sizes it ships at.
 *
 * - `stickers_calibration_contact_<variant>`: the calibration set of 12 (§3.1);
 * - `stickers_ontario_contact_<variant>`: the Ontario set (§3.1);
 * - `stickers_contact_<category>_<variant>`: every catalog key of each of the 20 categories, in catalog order.
 *
 * Record: ./gradlew :app:recordRoborazziDebug --tests '*StickerContactGoldensTest*'.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class StickerContactGoldensTest(private val sheet: String, private val keys: List<String>, private val variant: ThemeVariant, private val v: String) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        /** §3.1: berry basket, milk bag, maple tin, McIntosh, egg carton, loaf, butter brick, tomato tin, carrots, chicken tray, toilet roll, capsule. */
        val CALIBRATION = listOf(
            "strawberry", "milk_bag", "maple_syrup", "apple", "eggs", "sliced_bread", "butter", "canned_tomatoes", "carrot",
            "chicken_breast", "toilet_paper", "_capsule",
        )

        /** §3.1: milk bag in a pitcher, 1 L berry basket, 3 L peach basket, unlabelled maple tin, McIntosh, butter tarts, peameal, curds. */
        val ONTARIO = listOf("milk_bag", "strawberry", "peach", "maple_syrup", "apple", "butter_tart", "peameal_bacon", "cheese_curds")

        private val VARIANTS = listOf(ThemeVariant.Day to "day", ThemeVariant.Glow to "glow", ThemeVariant.Dusk to "dusk")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{3}")
        fun params(): List<Array<Any>> {
            val catalog = IconCatalog.default
            val sheets = listOf("stickers_calibration_contact" to CALIBRATION, "stickers_ontario_contact" to ONTARIO) +
                catalog.categories.map { c -> "stickers_contact_${c.key}" to catalog.icons.filter { it.category == c.key }.map { it.key } }
            return sheets.flatMap { (name, keys) -> VARIANTS.map { (variant, v) -> arrayOf(name, keys, variant, v) } }
        }
    }

    @Test fun contact() = compose.recordGolden("${sheet}_$v", variant) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in keys.chunked(5)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (key in row) StickerCell(key)
                }
            }
        }
    }
}

@Composable
private fun StickerCell(key: String) {
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ItemIcon(IconRef(key), null, size = 52.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            ItemIcon(IconRef(key), null, size = 30.dp)
            ItemIcon(IconRef(key), null, size = 22.dp)
        }
    }
}

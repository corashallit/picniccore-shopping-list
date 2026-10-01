package earth.cora.picnic.ui.aisles

import androidx.compose.ui.test.junit4.v2.createComposeRule
import earth.cora.picnic.core.assistant.AisleSyncReport
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.ui.common.snap
import earth.cora.picnic.ui.common.themeParameters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden: aisles_editor (an edited draft with its live preview, baskets, counts and the last sync report). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AislesScreenshotTest(private val dark: Boolean, @Suppress("unused") private val theme: String) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editor() = compose.snap("aisles_editor", dark) {
        val text = """
            Produce
            Bakery
            Deli & cheese
            Aisle 2 - Baking & spices
            Aisle 3 - Pasta, rice & international
            Dairy & eggs
            Frozen
            Flowers
        """.trimIndent()
        val baskets = IconCatalog.default.baskets
        val counts = mapOf("produce" to 3, "aisle-2-baking-spices" to 2, "dairy-eggs" to 4)
        AislesScreen(
            AislesUiState(
                storeName = "No Frills on Bank St",
                text = text,
                parsed = Aisles.parse(text).map { AislePreviewUi(it, baskets.guess(it), counts[it.id] ?: 0) },
                dirty = true,
                syncing = false,
                lastReport = AisleSyncReport(basketsFromModel = true, recategorized = 6, leftUnsorted = 1, error = null),
                loading = false,
                needingAisle = 2,
            ),
            AislesActions(),
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = themeParameters()
    }
}

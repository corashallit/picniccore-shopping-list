package earth.cora.picnic.ui.pantry

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.gen.StickerManifest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * My pantry goldens (checklist 325–327): `pantry_{empty,few,full,text_focused,fs200,popcard}` in Day, `pantry_glow`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class PantryGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    private val day get() = variant == ThemeVariant.Day

    private fun record(
        name: String,
        state: PantryUiState,
        fontScale: Float? = null,
        popCard: String? = null,
        focusText: Boolean = false,
        wholeScreen: Boolean = false,
        before: ComposeContentTestRule.() -> Unit = {},
    ) = compose.recordScreen(name, variant, fontScale = fontScale, wholeScreen = wholeScreen, before = before) {
        PantryScreen(state, PantryActions(), initialPopCard = popCard, focusText = focusText)
    }

    @Test
    fun empty() {
        if (day) record("pantry_empty", PantrySamples.state())
    }

    @Test
    fun few() {
        if (day) record("pantry_few", PantrySamples.few)
    }

    @Test
    fun full() {
        if (day) record("pantry_full", PantrySamples.full)
    }

    @Test
    fun glow() {
        if (!day) record("pantry_glow", PantrySamples.few)
    }

    @Test
    fun textFocused() {
        if (day) {
            record("pantry_text_focused", PantrySamples.few, focusText = true, before = {
                onNodeWithContentDescription("Pantry as text:").performScrollTo()
            })
        }
    }

    @Test
    fun fontScale200() {
        if (day) record("pantry_fs200", PantrySamples.few, fontScale = 2f)
    }

    @Test
    fun popCard() {
        if (day) record("pantry_popcard", PantrySamples.few, popCard = "olive oil", wholeScreen = true)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

/** Pantry states for the goldens: stickers resolved as the app would (local match, else the capsule). */
object PantrySamples {
    private val catalog = IconCatalog.default

    fun state(vararg names: String): PantryUiState = PantryUiState(
        loading = false,
        stickers = names.map { n ->
            ShelfSticker(normalizeName(n), n, IconRef(catalog.match(n)?.key ?: StickerManifest.CAPSULE), busy = false)
        },
        text = names.joinToString("\n"),
    )

    val few: PantryUiState = state("Salt", "Black pepper", "Olive oil", "Flour", "Sugar", "Rice")

    val full: PantryUiState = state(
        "Salt", "Black pepper", "Olive oil", "Canola oil", "Flour", "Sugar", "Rice", "Honey", "Vinegar", "Spices",
        "Pasta", "Oats", "Maple syrup", "Soy sauce", "Baking soda", "Gochujang",
    )
}

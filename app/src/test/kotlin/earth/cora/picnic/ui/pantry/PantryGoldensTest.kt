package earth.cora.picnic.ui.pantry

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import earth.cora.picnic.ui.common.focusWithoutTouch
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
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
 * My pantry goldens (checklist 325–327, §6.13 states): `pantry_{empty,few,full,text_focused,fs200,popcard,takeoff_toast}`
 * in Day, `pantry_glow` and `pantry_popcard_glow`.
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
        wholeScreen: Boolean = false,
        frozen: Boolean = false,
        toast: ToastMessage? = null,
        before: ComposeContentTestRule.() -> Unit = {},
    ) = compose.recordScreen(name, variant, fontScale = fontScale, wholeScreen = wholeScreen, frozen = frozen, before = before) {
        val toasts = remember { ToastHostState() }
        if (toast != null) LaunchedEffect(Unit) { toasts.show(toast) }
        PantryScreen(state, PantryActions(), toasts = toasts, initialPopCard = popCard)
    }

    @Test
    fun empty() {
        if (day) record("pantry_empty", PantrySamples.state())
    }

    @Test
    fun few() {
        if (day) record("pantry_few", PantrySamples.few)
    }

    /** Full (24 entries, six shelves), scrolled so the last shelf shows. */
    @Test
    fun full() {
        if (day) {
            record("pantry_full", PantrySamples.full, before = {
                // The last sticker into view, then 140 dp more: its shelf and the names under it, the text field below.
                onNodeWithContentDescription("Gochujang, in your pantry").performScrollTo()
                val more = with(density) { 140.dp.toPx() }
                onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, more) }
            })
        }
    }

    @Test
    fun glow() {
        if (!day) record("pantry_glow", PantrySamples.few)
    }

    /** The text field in focus (§4.8: the 3 dp strawberry line and the heart, a caret): scrolled to, tapped, focused. */
    @Test
    fun textFocused() {
        if (day) {
            record("pantry_text_focused", PantrySamples.few, before = {
                focusWithoutTouch(onNodeWithContentDescription("Pantry as text:").performScrollTo())
            })
        }
    }

    /** After "Take off the shelf" (§6.13): the shelf without Black pepper and its Undo toast, caught mid-hold. */
    @Test
    fun takeOffToast() {
        if (day) {
            record(
                "pantry_takeoff_toast",
                PantrySamples.state("Salt", "Olive oil", "Flour", "Sugar", "Rice"),
                frozen = true,
                toast = ToastMessage("Black pepper is off your pantry shelf", kind = "pantry-off", actionLabel = "Undo", onAction = {}),
            )
        }
    }

    @Test
    fun fontScale200() {
        if (day) record("pantry_fs200", PantrySamples.few, fontScale = 2f)
    }

    /** The pop card: Glow too, where the cabinet is light paper and the card a floating navy menu over it (§2.4.1). */
    @Test
    fun popCard() {
        record(if (day) "pantry_popcard" else "pantry_popcard_glow", PantrySamples.few, popCard = "olive oil", wholeScreen = true)
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

    /** 24 entries (§6.13 "full (24, scrolled)"): six shelves of four. */
    val full: PantryUiState = state(
        "Salt", "Black pepper", "Olive oil", "Canola oil", "Flour", "Sugar", "Rice", "Honey", "Vinegar", "Spices",
        "Pasta", "Oats", "Maple syrup", "Soy sauce", "Baking soda", "Baking powder", "Brown sugar", "Cinnamon",
        "Peanut butter", "Ketchup", "Mustard", "Tea", "Coffee", "Gochujang",
    )
}

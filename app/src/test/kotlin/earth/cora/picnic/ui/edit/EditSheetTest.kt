package earth.cora.picnic.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.ui.common.SaturdayShop
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.EditItemUi
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ToastMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Edit sheet (§6.3, checklist 217, 329). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class EditSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun detailsFieldsAislesStickersAndDelete() {
        var saved: Triple<String, String?, String?>? = null
        var moved: String? = "unset"
        var deleted = false
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    EditItemSheet(
                        state = EditSamples.milk(),
                        onSave = { n, q, note -> saved = Triple(n, q, note) },
                        onClose = {},
                        onMoveToAisle = { moved = it },
                        onSetIcon = {},
                        onRedraw = {},
                        onDelete = { deleted = true },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Milk, 2 % sticker").assertExists()
        compose.onNodeWithText("Favourite").assertIsOff()
        compose.onNodeWithText("In my pantry").assertIsOff()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Amount").performTextReplacement("2 L bag")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(Triple("Milk, 2 %", "2 L bag", ""), saved)

        // Move to aisle: another aisle's chip moves it there (the current one, 6 · Dairy & eggs, is a no-op).
        compose.onNodeWithText("Dairy & eggs").performScrollTo().performClick()
        assertEquals("unset", moved)
        compose.onNodeWithText("Frozen").performScrollTo().performClick()
        assertEquals("frozen", moved)
        compose.onNodeWithText("Delete").performClick()
        assertTrue(deleted)
    }

    /** "Change sticker" (§6.3): the item's category first, the current sticker in a lemon-ringed (selected) cell; a tap picks. */
    @Test
    fun stickerGridPicksAndRings() {
        val catalog = IconCatalog.default
        var item by mutableStateOf(EditSamples.item())
        val picked = mutableListOf<String>()
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    EditItemSheet(
                        EditSamples.milk(item = item), { _, _, _ -> }, {}, {},
                        onSetIcon = { key ->
                            picked += key
                            item = item.copy(icon = key)
                        },
                        onRedraw = {}, onDelete = {},
                    )
                }
            }
        }
        val bag = catalog["milk_bag"]!!.label
        val carton = catalog["milk_carton"]!!.label
        fun cell(label: String) = compose.onNode(hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        // The item's own category (dairy) leads, its current sticker first and ringed.
        cell(bag).performScrollTo().assertIsSelected()
        cell(carton).assertIsNotSelected()
        cell(carton).performClick()
        assertEquals(listOf("milk_carton"), picked)
        // The host saves it: the ring moves to the new sticker.
        cell(carton).assertIsSelected()
        cell(bag).assertIsNotSelected()
        // Only 40 dp stickers in ≥48 dp cells.
        val small = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes()
            .filter { n -> with(compose.density) { n.size.width.toDp() < 48.dp || n.size.height.toDp() < 48.dp } }
        assertTrue("sticker cells under 48 dp: ${small.size}", small.isEmpty())
    }

    /** "Draw a new sticker" (with a key) asks for a drawing; while it draws, the ladybug ring and its label replace it. */
    @Test
    fun drawNewStickerAndTheLadybugRing() {
        var redraws = 0
        var drawing by mutableStateOf(false)
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    EditItemSheet(EditSamples.milk(drawing = drawing), { _, _, _ -> }, {}, {}, {}, onRedraw = { redraws++; drawing = true }, onDelete = {})
                }
            }
        }
        compose.onNodeWithText("Draw a new sticker").performScrollTo().performClick()
        assertEquals(1, redraws)
        compose.onNodeWithText("Drawing a new sticker…").performScrollTo().assertExists()
        compose.onNodeWithText("Draw a new sticker").assertDoesNotExist()
        // The hero shows the sticker being drawn (the sparkle on it); TalkBack still names it.
        compose.onNodeWithContentDescription("Milk, 2 % sticker").assertExists()
    }

    @Test
    fun noKeySaysSo() {
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    EditItemSheet(EditSamples.milk(canDraw = false), { _, _, _ -> }, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Drawing a new sticker needs an OpenAI key (Settings).").performScrollTo().assertExists()
    }

    @Test
    fun favouritePersists() {
        val store = InMemoryStateStore(EditSamples.app)
        val vm = EditExtrasViewModel(store, { SaturdayShop.NOW }, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    EditItemRoute(EditSamples.milk(), { _, _, _ -> }, {}, {}, {}, {}, {}, onToast = {}, vm = vm)
                }
            }
        }
        compose.onNodeWithText("Favourite").performClick()
        compose.waitUntil(5_000) { runBlocking { store.current() }.favourites.contains(normalizeName("Milk, 2 %")) }
        compose.onNodeWithText("Favourite").assertIsOn()
    }

    @Test
    fun pantryToggle() {
        val store = InMemoryStateStore(EditSamples.app)
        val vm = EditExtrasViewModel(store, { SaturdayShop.NOW }, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        val toasts = mutableListOf<ToastMessage>()
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    EditItemRoute(EditSamples.milk(), { _, _, _ -> }, {}, {}, {}, {}, {}, onToast = { toasts += it }, vm = vm)
                }
            }
        }
        compose.onNodeWithText("In my pantry").performClick()
        compose.waitUntil(5_000) { pantry(store).size == 1 }
        val entry = pantry(store).single()
        compose.waitUntil(5_000) { toasts.isNotEmpty() }
        assertEquals("$entry is in your pantry · recipes will leave it out", toasts.last().text)
        compose.onNodeWithText("In my pantry").assertIsOn()

        compose.onNodeWithText("In my pantry").performClick()
        compose.waitUntil(5_000) { pantry(store).isEmpty() }
        compose.waitUntil(5_000) { toasts.size == 2 }
        assertEquals("$entry is off your pantry shelf", toasts.last().text)
    }

    private fun pantry(store: InMemoryStateStore): List<String> = runBlocking { store.current().pantry.map { it.name } }
}

/** Edit sheet states for tests and goldens: Appendix A's Milk, 2 % (4 L bag) in 6 · Dairy & eggs at Hillcrest Market. */
object EditSamples {
    val app: AppState = SaturdayShop.state()

    fun item(id: String = "milk"): GroceryItem = app.lists.first().items.first { it.id == id }

    fun milk(canDraw: Boolean = true, drawing: Boolean = false, item: GroceryItem = item()): EditItemUi = EditItemUi(
        item = item,
        aisles = app.store.aisles(),
        iconChoices = IconCatalog.default.keys,
        canDraw = canDraw,
        drawing = drawing,
    )
}

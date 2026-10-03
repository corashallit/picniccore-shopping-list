package earth.cora.picnic.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.Samples
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
        var icon: String? = null
        var deleted = false
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    EditItemSheet(
                        state = EditSamples.milk(),
                        onSave = { n, q, note -> saved = Triple(n, q, note) },
                        onClose = {},
                        onMoveToAisle = { moved = it },
                        onSetIcon = { icon = it },
                        onRedraw = {},
                        onDelete = { deleted = true },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Milk sticker").assertExists()
        compose.onNodeWithText("Favourite").assertIsOff()
        compose.onNodeWithText("In my pantry").assertIsOff()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Amount").performTextReplacement("2 L bag")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(Triple("Milk", "2 L bag", ""), saved)

        compose.onNodeWithText("Frozen").performScrollTo().performClick()
        assertEquals("frozen", moved)
        compose.onNodeWithText("Draw a new sticker").performScrollTo().assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertTrue(deleted)
        assertEquals(null, icon)
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
        val store = InMemoryStateStore(Samples.shoppingState)
        val vm = EditExtrasViewModel(store, { Samples.NOW }, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    EditItemRoute(EditSamples.milk(), { _, _, _ -> }, {}, {}, {}, {}, {}, onToast = {}, vm = vm)
                }
            }
        }
        compose.onNodeWithText("Favourite").performClick()
        compose.waitUntil(5_000) { runBlocking { store.current() }.favourites.contains("milk") }
        compose.onNodeWithText("Favourite").assertIsOn()
    }

    @Test
    fun pantryToggle() {
        val store = InMemoryStateStore(Samples.shoppingState)
        val vm = EditExtrasViewModel(store, { Samples.NOW }, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        val toasts = mutableListOf<ToastMessage>()
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    EditItemRoute(EditSamples.milk(), { _, _, _ -> }, {}, {}, {}, {}, {}, onToast = { toasts += it }, vm = vm)
                }
            }
        }
        compose.onNodeWithText("In my pantry").performClick()
        compose.waitUntil(5_000) { pantry(store) == listOf("Milk") }
        compose.waitUntil(5_000) { toasts.isNotEmpty() }
        assertEquals("Milk is in your pantry · recipes will leave it out", toasts.last().text)
        compose.onNodeWithText("In my pantry").assertIsOn()

        compose.onNodeWithText("In my pantry").performClick()
        compose.waitUntil(5_000) { pantry(store).isEmpty() }
        compose.waitUntil(5_000) { toasts.size == 2 }
        assertEquals("Milk is off your pantry shelf", toasts.last().text)
    }

    private fun pantry(store: InMemoryStateStore): List<String> = runBlocking { store.current().pantry.map { it.name } }
}

/** Edit sheet states for tests and goldens: Milk (4 L bag) in Dairy & eggs, the default store. */
object EditSamples {
    val app: AppState = Samples.shoppingState

    fun item(name: String = "Milk"): GroceryItem = app.lists.first().items.first { it.name == name }

    fun milk(canDraw: Boolean = true, drawing: Boolean = false, item: GroceryItem = item()): EditItemUi = EditItemUi(
        item = item,
        aisles = Aisles.parse(app.store.aislesText),
        iconChoices = IconCatalog.default.keys,
        canDraw = canDraw,
        drawing = drawing,
    )
}

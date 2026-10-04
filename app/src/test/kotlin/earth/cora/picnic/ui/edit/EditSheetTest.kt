package earth.cora.picnic.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import android.os.Looper
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.ui.common.AppearScale
import earth.cora.picnic.ui.pantry.putOnShelf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.robolectric.Shadows.shadowOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.ui.list.ListActions
import earth.cora.picnic.ui.list.ListScreen
import earth.cora.picnic.ui.list.buildListUiState
import earth.cora.picnic.ui.theme.TOAST_TAG
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
                    EditItemSheetContent(
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
                    EditItemSheetContent(
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
                    EditItemSheetContent(EditSamples.milk(drawing = drawing), { _, _, _ -> }, {}, {}, {}, onRedraw = { redraws++; drawing = true }, onDelete = {})
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
                    EditItemSheetContent(EditSamples.milk(canDraw = false), { _, _, _ -> }, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Add your OpenAI key in Settings to draw new stickers.").performScrollTo().assertExists()
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

    /**
     * The sheet exactly as the List screen mounts it (ListScreen calls [EditItemSheet] with the list's own actions only):
     * the heart and the jar write to the store and the jar's toast shows on the sheet's own sticky-note host.
     */
    @Test
    fun listMountedSheetTogglesWorkAndToast() {
        val store = InMemoryStateStore(EditSamples.app)
        val vm = EditExtrasViewModel(store, { SaturdayShop.NOW }, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        val owner = ownerWith(vm)
        val ui = buildListUiState(EditSamples.app, emptySet(), hasToken = true, editingId = "milk", catalog = IconCatalog.default)
        assertTrue(ui.editing != null)
        compose.setContent {
            PicnicTheme {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    ListScreen(state = ui, actions = ListActions())
                }
            }
        }
        compose.onNodeWithText("Favourite").assertIsOff().performClick()
        compose.waitUntil(5_000) { runBlocking { store.current() }.favourites.contains(normalizeName("Milk, 2 %")) }
        compose.onNodeWithText("Favourite").assertIsOn()

        // The toast holds on the frame clock: step it by hand so the note is still up when we look.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("In my pantry").assertIsOff().performClick()
        compose.waitUntil(5_000) { pantry(store).size == 1 }
        assertEquals("Milk, 2 %", pantry(store).single())
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("In my pantry").assertIsOn()
        // The note is spoken as words ("2 percent", no "·"): match the words around the name.
        compose.onNodeWithText("is in your pantry", substring = true).assertExists()
        compose.onNodeWithText("recipes will leave it out", substring = true).assertExists()
        // The note sits clear of the footer: Save and Delete stay tappable under it.
        val note = compose.onNodeWithTag(TOAST_TAG).getBoundsInRoot()
        val delete = compose.onNodeWithText("Delete").getBoundsInRoot()
        assertTrue("toast $note overlaps Delete $delete", note.bottom <= delete.top)
    }

    /** A toast that lands after the sheet has closed is dropped: the next sheet never opens with a stale one. */
    @Test
    fun noStaleToastAfterClose() {
        val store = InMemoryStateStore(EditSamples.app)
        val vm = EditExtrasViewModel(store, { SaturdayShop.NOW }, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        // Nobody is showing a sheet: the toggle still writes, its toast goes nowhere.
        vm.setInPantry("Milk, 2 %", true, { "$it on" }, { "$it off" })
        compose.waitUntil(5_000) { pantry(store).size == 1 }
        compose.setContent {
            PicnicTheme {
                EditItemSheet(EditSamples.milk(), { _, _, _ -> }, {}, {}, {}, {}, {}, vm = vm)
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(TOAST_TAG).assertDoesNotExist()
        compose.onNodeWithText("In my pantry").assertIsOn()
    }

    /**
     * The heart and the jar open as stored, with no magic appear when the stored values arrive a moment after the sheet
     * (the appear marks her switching one on, §6.3).
     */
    @Test
    fun storedTogglesOpenWithoutTheAppear() {
        val app = EditSamples.app.let { a ->
            a.copy(favourites = a.favourites + normalizeName("Milk, 2 %")).putOnShelf("Milk, 2 %", SaturdayShop.NOW)
        }
        val inner = InMemoryStateStore(app)
        val gate = CompletableDeferred<Unit>()
        val slow = object : StateStore by inner {
            override val states: Flow<AppState> = flow {
                gate.await()
                emitAll(inner.states)
            }
        }
        val vm = EditExtrasViewModel(slow, { SaturdayShop.NOW }, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        compose.mainClock.autoAdvance = false
        compose.setContent { PicnicTheme { EditItemSheet(EditSamples.milk(), { _, _, _ -> }, {}, {}, {}, {}, {}, vm = vm) } }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Favourite").assertIsOff()
        gate.complete(Unit)
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onNodeWithText("Favourite").assertIsOn()
        compose.onNodeWithText("In my pantry").assertIsOn()
        val appearing = compose.onAllNodes(SemanticsMatcher.keyIsDefined(AppearScale), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[AppearScale] }
        assertTrue("no magic appear on open: $appearing", appearing.all { it == 1f })
    }

    private fun ownerWith(vm: EditExtrasViewModel): ViewModelStoreOwner {
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        // The sheet asks for the default-keyed EditExtrasViewModel: this one is already there.
        ViewModelProvider.create(owner, viewModelFactory { initializer { vm } })[EditExtrasViewModel::class]
        return owner
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

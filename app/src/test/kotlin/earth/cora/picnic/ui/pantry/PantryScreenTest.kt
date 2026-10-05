package earth.cora.picnic.ui.pantry

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.assistant.IconDecision
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.core.store.StateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.common.AppearScale
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** My pantry (§6.13, checklist 325–327). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class PantryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var vm: PantryViewModel

    private fun state(vararg names: String): AppState =
        Samples.emptyState.copy(pantry = names.map { PantryEntry(name = it, addedAt = Samples.NOW) })

    private fun launch(state: AppState = Samples.emptyState): InMemoryStateStore {
        val store = InMemoryStateStore(state)
        launchWith(store)
        return store
    }

    private fun launchWith(store: StateStore) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val decider = object : IconDecider {
            override suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> = emptyMap()
        }
        vm = PantryViewModel(store, InMemorySettingsRepository(), decider, IconCatalog.default, { Samples.NOW }, scope)
        compose.setContent { PicnicTheme { PantryRoute(vm, onBack = {}) } }
        compose.waitForIdle()
    }

    /** A store whose writes wait on [gate] (as the real one waits on its fsync before it emits), or fail with [fail]. */
    private class GatedStore(initial: AppState) : StateStore {
        val inner = InMemoryStateStore(initial)
        val gate = CompletableDeferred<Unit>()
        var fail = false
        override val states: Flow<AppState> = inner.states
        override suspend fun current(): AppState = inner.current()
        override suspend fun update(transform: (AppState) -> AppState): AppState {
            gate.await()
            if (fail) throw IOException("disk full")
            return inner.update(transform)
        }
    }

    private fun idle(millis: Long = 0) {
        if (millis > 0) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)) else shadowOf(Looper.getMainLooper()).idle()
    }

    private fun shelves(): List<String> = vm.state.value.stickers.map { it.name }

    private fun InMemoryStateStore.names(): List<String> = runBlocking { current().pantry.map { it.name } }

    private fun waitForNames(store: InMemoryStateStore, names: List<String>) = compose.waitUntil(5_000) { store.names() == names }

    /** Freezes the clock and lets the store write and the event land, so a toast can be seen during its hold. */
    private fun settleFrozen() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(400)
    }

    private fun SemanticsNodeInteraction.customAction(label: String) {
        val node = fetchSemanticsNode()
        compose.runOnIdle { node.config[SemanticsActions.CustomActions].first { it.label == label }.action() }
    }

    @Test
    fun addSplitsAndDropsQuantities() {
        val store = launch()
        compose.onNodeWithContentDescription("Add to pantry:").performTextInput("2 kg flour, salt and pepper\nolive oil")
        compose.onNodeWithContentDescription("Add to pantry").performClick()
        waitForNames(store, listOf("Flour", "Salt", "Pepper", "Olive oil"))
        compose.onNodeWithContentDescription("Olive oil, in your pantry").assertExists()
    }

    @Test
    fun duplicatePopsWithItsToast() {
        val store = launch(state("Salt", "Pepper"))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add to pantry:").performTextInput("salt")
        compose.onNodeWithContentDescription("Add to pantry").performClick()
        settleFrozen()
        assertEquals(listOf("Salt", "Pepper"), store.names())
        assertEquals("salt", vm.state.value.popped?.first)
        compose.onNodeWithText("Salt is already in your pantry").assertExists()
    }

    @Test
    fun shelvesInTextOrderFourPerShelf() {
        launch(state("Salt", "Pepper", "Oil", "Flour", "Rice", "Sugar"))
        val tops = listOf("Salt", "Pepper", "Oil", "Flour", "Rice").map {
            compose.onNodeWithContentDescription("$it, in your pantry").getBoundsInRoot()
        }
        // Text order, left to right: the first four share a shelf, the fifth starts the next.
        assertTrue(tops.take(4).zipWithNext().all { (a, b) -> a.top == b.top && a.left < b.left })
        assertTrue(tops[4].top > tops[3].top)
        assertEquals(4, stickersPerShelf(360.dp))
        assertEquals(4, stickersPerShelf(411.dp))
        assertEquals(5, stickersPerShelf(412.dp))
        // Large text: one fewer, so the names keep their two lines.
        assertEquals(3, stickersPerShelf(360.dp, fontScale = 2f))
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun fivePerShelfFrom412() {
        launch(state("Salt", "Pepper", "Oil", "Flour", "Rice", "Sugar"))
        val five = listOf("Salt", "Pepper", "Oil", "Flour", "Rice").map {
            compose.onNodeWithContentDescription("$it, in your pantry").getBoundsInRoot().top
        }
        assertTrue(five.all { it == five.first() })
        assertTrue(compose.onNodeWithContentDescription("Sugar, in your pantry").getBoundsInRoot().top > five.first())
    }

    @Test
    fun textPreviewsThenCommitsOnDone() {
        val store = launch(state("Salt", "Pepper"))
        compose.onNodeWithContentDescription("Pantry as text:").performScrollTo().performTextReplacement("Salt\nRice\nPepper")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(450))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Rice, in your pantry").assertExists()
        assertEquals("nothing stored before Done", listOf("Salt", "Pepper"), store.names())
        compose.onNodeWithText("Done").performClick()
        waitForNames(store, listOf("Salt", "Rice", "Pepper"))
    }

    /**
     * The draft stays until the write lands: while the store is still writing (its fsync), the field and the shelves keep
     * what she wrote; nothing falls back to the old pantry and nothing plays its magic appear twice.
     */
    @Test
    fun commitKeepsTextUntilSaved() {
        val store = GatedStore(state("Salt"))
        launchWith(store)
        vm.setText("Salt\nPepper")
        idle(450)
        assertEquals("Salt\nPepper", vm.state.value.text)
        assertEquals(listOf("Salt", "Pepper"), shelves())
        vm.commitText()
        idle()
        // During the write.
        assertEquals("Salt\nPepper", vm.state.value.text)
        assertEquals(listOf("Salt", "Pepper"), shelves())
        assertEquals(listOf("Salt"), runBlocking { store.current() }.pantry.map { it.name })
        store.gate.complete(Unit)
        idle()
        compose.waitUntil(5_000) { runBlocking { store.current() }.pantry.map { it.name } == listOf("Salt", "Pepper") }
        idle()
        assertEquals(listOf("Salt", "Pepper"), shelves())
        assertEquals("Salt\nPepper", vm.state.value.text)
        assertTrue("saved: no longer dirty", !vm.state.value.textDirty)
    }

    /** A failed write keeps everything she typed in the field, still unsaved, and says so. */
    @Test
    fun failedCommitKeepsDraft() {
        val store = GatedStore(state("Salt")).apply {
            fail = true
            gate.complete(Unit)
        }
        launchWith(store)
        compose.mainClock.autoAdvance = false
        vm.setText("Salt\nPepper\nOats")
        vm.commitText()
        settleFrozen()
        assertEquals(listOf("Salt"), runBlocking { store.current() }.pantry.map { it.name })
        assertEquals("Salt\nPepper\nOats", vm.state.value.text)
        assertTrue("still unsaved", vm.state.value.textDirty)
        // The shelves keep previewing her text.
        idle(450)
        assertEquals(listOf("Salt", "Pepper", "Oats"), shelves())
        compose.onNodeWithText("Couldn’t save", substring = true).assertExists()
    }

    /** "Pantry as text" also commits when the field loses focus (§6.13), not only on Done. */
    @Test
    fun textCommitsOnBlur() {
        val store = launch(state("Salt", "Pepper"))
        compose.onNodeWithContentDescription("Pantry as text:").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pantry as text:").performTextReplacement("Salt\nRice, oats\nPepper")
        compose.waitForIdle()
        assertEquals("nothing stored while she types", listOf("Salt", "Pepper"), store.names())
        // Focus moves on to the add field: the text commits (a line with a comma becomes two entries).
        compose.onNodeWithContentDescription("Add to pantry:").performScrollTo().performClick()
        waitForNames(store, listOf("Salt", "Rice", "Oats", "Pepper"))
    }

    /**
     * The pop card opens under the shelf's name row, centred on the tapped sticker's slot, so the sticker and its name
     * stay in view; with no room below it, it opens above the shelf (§6.13 polish).
     */
    @Test
    fun popCardKeepsTheNameInView() {
        val window = IntSize(1080, 2280)
        val card = IntSize(624, 300)
        val shelf = IntRect(36, 900, 1044, 1200)
        val below = ShelfCardPosition(index = 1, perShelf = 4, margin = 24).calculatePosition(shelf, window, LayoutDirection.Ltr, card)
        assertEquals("under the names", shelf.bottom, below.y)
        val slot = shelf.width / 4f
        assertEquals("centred on the slot", (shelf.left + slot * 1.5f - card.width / 2f).toInt(), below.x)
        // The last slot's card stays inside the window.
        val right = ShelfCardPosition(index = 3, perShelf = 4, margin = 24).calculatePosition(shelf, window, LayoutDirection.Ltr, card)
        assertEquals(window.width - card.width - 24, right.x)
        // A shelf near the bottom: the card opens above the shelf, never over the tapped sticker's name.
        val low = IntRect(36, 1950, 1044, 2250)
        val above = ShelfCardPosition(index = 0, perShelf = 4, margin = 24).calculatePosition(low, window, LayoutDirection.Ltr, card)
        assertEquals(low.top - card.height, above.y)
    }

    @Test
    fun newEntriesMagicAppear() {
        launch(state("Salt"))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add to pantry:").performTextInput("rice")
        compose.onNodeWithContentDescription("Add to pantry").performClick()
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
        }
        val rice = compose.onNodeWithContentDescription("Rice, in your pantry")
        assertTrue("appearing", rice.fetchSemanticsNode().config[AppearScale] != 1f)
        assertEquals(1f, compose.onNodeWithContentDescription("Salt, in your pantry").fetchSemanticsNode().config[AppearScale])
        compose.mainClock.advanceTimeBy(600)
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, rice.fetchSemanticsNode().config[AppearScale])
        assertTrue("played once", "rice" !in vm.state.value.fresh)
    }

    @Test
    fun takeOffAndUndo() {
        val store = launch(state("Salt", "Pepper", "Oil"))
        compose.onNodeWithContentDescription("Pepper, in your pantry").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Take off the shelf").performClick()
        settleFrozen()
        assertEquals(listOf("Salt", "Oil"), store.names())
        compose.onNodeWithText("Pepper is off your pantry shelf").assertExists()
        compose.onNodeWithText("Undo").performClick()
        settleFrozen()
        assertEquals(listOf("Salt", "Pepper", "Oil"), store.names())
    }

    @Test
    fun talkBackActions() {
        val store = launch(state("Salt", "Pepper"))
        val salt = compose.onNodeWithContentDescription("Salt, in your pantry")
        val labels = salt.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Take off the shelf", "Change sticker"), labels)
        salt.customAction("Change sticker")
        compose.onNodeWithText("A sticker for Salt").assertExists()
        compose.onNodeWithContentDescription("Pepper, in your pantry").customAction("Take off the shelf")
        waitForNames(store, listOf("Salt"))
    }

    @Test
    fun suggestionChips() {
        val store = launch()
        listOf("Salt", "Pepper", "Oil", "Flour").forEach {
            compose.onNodeWithContentDescription("Add $it to your pantry").assertExists()
        }
        compose.onNodeWithText("Nothing on the shelves yet", substring = true).assertExists()
        compose.onNodeWithContentDescription("Add Oil to your pantry").performClick()
        waitForNames(store, listOf("Oil"))
        assertTrue(compose.onAllNodesWithContentDescription("Add Salt to your pantry").fetchSemanticsNodes().isEmpty())
    }
}

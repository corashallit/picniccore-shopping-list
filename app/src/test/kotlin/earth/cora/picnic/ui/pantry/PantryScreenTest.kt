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
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.assistant.IconDecision
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.store.InMemoryStateStore
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val decider = object : IconDecider {
            override suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> = emptyMap()
        }
        vm = PantryViewModel(store, InMemorySettingsRepository(), decider, IconCatalog.default, { Samples.NOW }, scope)
        compose.setContent { PicnicTheme { PantryRoute(vm, onBack = {}) } }
        compose.waitForIdle()
        return store
    }

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

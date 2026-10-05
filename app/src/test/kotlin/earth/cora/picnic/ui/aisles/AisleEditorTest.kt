package earth.cora.picnic.ui.aisles

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.displayName
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.isCut
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Pictogram
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** My store (§6.7, checklist 8, 220–224): the labels, their editor, drag, Add aisle, the raw text and Re-sort. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class AisleEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private val baskets = IconCatalog.default.baskets

    private lateinit var vm: AislesViewModel

    /** Times the screen asked to leave (Done or Back). */
    private var backs = 0

    private fun launch(state: AppState = Samples.shoppingState, assistant: FakeAssistant = FakeAssistant()): InMemoryStateStore {
        val store = InMemoryStateStore(state)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        vm = AislesViewModel(store, AisleSync(store, assistant, baskets) { Samples.NOW }, baskets, scope, { Samples.NOW })
        compose.setContent { PicnicTheme { AislesRoute(vm, onBack = { backs++ }) } }
        compose.waitForIdle()
        return store
    }

    private fun InMemoryStateStore.now(): AppState = runBlocking { current() }

    /**
     * One frame with the clock held (autoAdvance off). State written outside composition (the drag handle's pointer
     * input) reaches the recomposer only through the global snapshot's apply notifications; Compose schedules those on
     * a process-wide dispatcher that an earlier Robolectric test in the same JVM can leave stuck, so they are sent here
     * explicitly, as the add tests do. Without this the drag tests pass alone and fail after some other tests.
     */
    private fun frame() {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeByFrame()
    }

    /** Runs the TalkBack custom action labelled [label]. */
    private fun SemanticsNodeInteraction.customAction(label: String) {
        fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action()
    }

    private fun waitFor(store: InMemoryStateStore, condition: (AppState) -> Boolean) =
        compose.waitUntil(5_000) { condition(store.now()) }

    @Test
    fun colourOverridePersists() {
        val store = launch()
        compose.onNodeWithContentDescription("Edit Produce").performClick()
        compose.onNodeWithContentDescription("Grape").performClick()
        waitFor(store) { it.store.aisleStyles["produce"]?.family == "grape" }
        compose.onNodeWithContentDescription("Stars").performClick()
        waitFor(store) { it.store.aisleStyles["produce"]?.pattern == "stars" }
        // The override beats the rotation and keeps the position's pictogram.
        val look = AisleLooks.of(1, store.now().store.aisleStyles["produce"])
        assertEquals(Families.grape, look.family)
        assertEquals(Pictogram.Strawberry, look.pictogram)
    }

    @Test
    fun dragReorders() {
        val store = launch()
        val slot = with(compose.density) { 54.dp.toPx() }
        compose.onNodeWithTag(AislesTags.handle(1), useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, 12f))
            repeat(20) { moveBy(Offset(0f, (slot * 2f - 12f) / 20f)) }
            up()
        }
        waitFor(store) { s -> s.store.aisles().map { it.name }.take(3) == listOf("Bakery", "Deli & cheese", "Produce") }
        // Produce's items followed it.
        assertEquals("produce", store.now().findItem("i-lemons")?.aisleId)
        assertEquals("Produce", store.now().store.aisles().first { it.id == "produce" }.name)
    }

    /**
     * Labels grow with their names, so the drag measures them: "Aisle 1 - Breakfast, coffee & tea" (two lines and its
     * store number) dragged until its centre is just past the next label's centre drops after it, and only one place on.
     */
    @Test
    fun dragOverLabelsOfDifferentHeights() {
        val store = launch()
        val five = compose.onNodeWithTag(AislesTags.label(5)).getBoundsInRoot()
        val six = compose.onNodeWithTag(AislesTags.label(6)).getBoundsInRoot()
        assertTrue("the label grew with its name", five.bottom - five.top > 48.dp)
        val distance = with(compose.density) { ((six.top + six.bottom) / 2 - (five.top + five.bottom) / 2 + 16.dp).toPx() }
        compose.onNodeWithTag(AislesTags.handle(5), useUnmergedTree = true).performScrollTo().performTouchInput {
            down(center)
            moveBy(Offset(0f, 12f))
            repeat(20) { moveBy(Offset(0f, (distance - 12f) / 20f)) }
            up()
        }
        waitFor(store) { s -> s.store.aisles().map { it.displayName() }.subList(4, 7) == listOf("Baking & spices", "Breakfast, coffee & tea", "Pasta, rice & international") }
        assertEquals("aisle-1-breakfast-coffee-tea", store.now().findItem("i-coffee")?.aisleId)
    }

    @Test
    fun moveActionsForTalkBack() {
        val store = launch()
        val produce = compose.onNodeWithContentDescription("Produce, aisle 1")
        compose.runOnIdle { produce.customAction("Move down") }
        waitFor(store) { s -> s.store.aisles().first().name == "Bakery" }
    }

    @Test
    fun editLabelPersists() {
        val store = launch()
        compose.onNodeWithContentDescription("Edit Bakery").performClick()
        compose.onNodeWithContentDescription("Store aisle").performTextReplacement("4")
        compose.onNodeWithContentDescription("Store aisle").performImeAction()
        waitFor(store) { it.store.aisleStyles["bakery"]?.storeAisle == 4 }
        compose.onNodeWithContentDescription("Peach").performClick()
        waitFor(store) { it.store.aisleStyles["bakery"]?.family == "peach" }
        compose.onNodeWithContentDescription("Cooler").performScrollTo().performClick()
        waitFor(store) { it.store.aisleBaskets["bakery"] == BasketKind.COOLER }
        compose.onNodeWithContentDescription("Bakery, aisle 2, store aisle 4").assertExists()
    }

    @Test
    fun renameKeepsItems() {
        val store = launch()
        compose.onNodeWithContentDescription("Edit Produce").performClick()
        compose.onNodeWithContentDescription("Aisle name").performTextReplacement("Fruit & veg")
        compose.onNodeWithContentDescription("Aisle name").performImeAction()
        waitFor(store) { s -> s.store.aisles().first().name == "Fruit & veg" }
        val id = store.now().store.aisles().first().id
        assertEquals(id, store.now().findItem("i-lemons")?.aisleId)
        assertEquals(id, store.now().findItem("i-avocado")?.aisleId)
        // The label stays open on its renamed aisle.
        compose.onNodeWithTag(AislesTags.nameField()).assertExists()
    }

    /**
     * "Add aisle" (§4.26): a label at the end, open, its name field focused and EMPTY with "New aisle" as its hint, so she
     * types the name straight away; a second tap while it is still unnamed opens it again instead of adding another.
     */
    @Test
    fun addAisle() {
        val store = launch()
        val before = store.now().store.aisles().size
        compose.onNodeWithText("Add aisle").performScrollTo().performClick()
        waitFor(store) { s -> s.store.aisles().last().name == "New aisle" }
        val count = store.now().store.aisles().size
        assertEquals(before + 1, count)
        compose.onNodeWithTag(AislesTags.label(count)).assertExists()
        val field = compose.onNodeWithContentDescription("Aisle name")
        field.assertIsFocused()
        assertEquals("", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        // The label's name and the field's hint.
        assertEquals(2, compose.onAllNodesWithText("New aisle", useUnmergedTree = true).fetchSemanticsNodes().size)

        compose.onNodeWithText("Add aisle").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("still one new label", count, store.now().store.aisles().size)

        compose.onNodeWithContentDescription("Aisle name").performTextReplacement("Frozen treats")
        compose.onNodeWithContentDescription("Aisle name").performImeAction()
        waitFor(store) { s -> s.store.aisles().last().name == "Frozen treats" }
        assertEquals(count, store.now().store.aisles().size)
    }

    /** Left blank, the new label keeps "New aisle". */
    @Test
    fun addAisleLeftBlankKeepsDefault() {
        val store = launch()
        compose.onNodeWithText("Add aisle").performScrollTo().performClick()
        waitFor(store) { s -> s.store.aisles().last().name == "New aisle" }
        compose.onNodeWithContentDescription("Aisle name").performImeAction()
        compose.waitForIdle()
        assertEquals("New aisle", store.now().store.aisles().last().name)
    }

    /**
     * §4.26 "blank = none": clearing the store number of "Aisle 2 - Baking & spices" takes the number out of the name
     * (its items follow the renamed aisle), so no A-chip and no "store aisle 2" come back; another number on a numbered
     * name says it in the name, so the name, the raw text and the chip agree.
     */
    @Test
    fun clearingStoreNumberRemovesIt() {
        val store = launch()
        compose.onNodeWithContentDescription("Edit Baking & spices").performScrollTo().performClick()
        val number = compose.onNodeWithContentDescription("Store aisle")
        assertEquals("2", number.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        number.performTextReplacement("")
        number.performImeAction()
        waitFor(store) { s -> s.store.aisles().any { it.name == "Baking & spices" } }
        val s = store.now()
        val baking = s.store.aisles().first { it.name == "Baking & spices" }
        assertEquals(baking.id, s.findItem("i-flour")?.aisleId)
        assertEquals(baking.id, s.findItem("i-bp")?.aisleId)
        assertNull(s.store.aisleStyles[baking.id]?.storeAisle)
        assertTrue("the raw text says it too", s.store.aislesText.lines().any { it.trim() == "Baking & spices" })
        // The label stays open on the renamed aisle, its number field blank, and the label shows no store number.
        compose.waitForIdle()
        assertEquals("", compose.onNodeWithContentDescription("Store aisle").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithContentDescription("Baking & spices, aisle ${baking.position}").assertExists()
        // Closed and opened again: still none.
        compose.onNodeWithContentDescription("Done editing Baking & spices").performClick()
        compose.onNodeWithContentDescription("Edit Baking & spices").performScrollTo().performClick()
        assertEquals("", compose.onNodeWithContentDescription("Store aisle").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithContentDescription("Done editing Baking & spices").performClick()

        // Another number on a numbered name: the name says it.
        compose.onNodeWithContentDescription("Edit Pasta, rice & international").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Store aisle").performTextReplacement("5")
        compose.onNodeWithContentDescription("Store aisle").performImeAction()
        waitFor(store) { st -> st.store.aisles().any { it.name == "Aisle 5 - Pasta, rice & international" } }
        val pasta = store.now().store.aisles().first { it.name == "Aisle 5 - Pasta, rice & international" }
        assertEquals(pasta.id, store.now().findItem("i-gochujang")?.aisleId)
        compose.onNodeWithContentDescription("Aisle 5 - Pasta, rice & international, aisle ${pasta.position}, store aisle 5").assertExists()
    }

    /**
     * §4.26 with the 14 default aisles the sheet is taller than the screen: holding a lifted label near the bottom edge
     * scrolls the page under it, so one gesture carries Produce from the top of the store to the bottom.
     */
    @Test
    fun dragAutoScrolls() {
        val store = launch()
        assertEquals(14, store.now().store.aisles().size)
        val root = compose.onRoot().getBoundsInRoot()
        val handle = compose.onNodeWithTag(AislesTags.handle(1), useUnmergedTree = true)
        val start = handle.getBoundsInRoot()
        val last = compose.onNodeWithTag(AislesTags.label(14)).getUnclippedBoundsInRoot()
        assertTrue("the last label starts off screen", last.top > root.bottom)
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, 12f))
            // To 24 dp above the bottom of the screen: inside the 64 dp edge.
            val to = with(compose.density) { (root.bottom - start.top - 24.dp).toPx() } - center.y
            repeat(20) { moveBy(Offset(0f, (to - 12f) / 20f)) }
        }
        // Hold still: the page scrolls on its own, the label with it.
        repeat(300) {
            frame()
        }
        // The page scrolled to its end under the held label: the last label is now near the top of the screen.
        assertTrue(compose.onNodeWithTag(AislesTags.label(14)).getUnclippedBoundsInRoot().bottom < root.bottom)
        handle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        waitFor(store) { s -> s.store.aisles().last().name == "Produce" }
        assertEquals("produce", store.now().findItem("i-lemons")?.aisleId)
        assertEquals("Bakery", store.now().store.aisles().first().name)
    }

    /** Held in the middle of the page, nothing scrolls: the drop is where the finger is. */
    @Test
    fun noAutoScrollAwayFromTheEdges() {
        val store = launch()
        val slot = with(compose.density) { 54.dp.toPx() }
        compose.mainClock.autoAdvance = false
        val handle = compose.onNodeWithTag(AislesTags.handle(1), useUnmergedTree = true)
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, 12f))
            repeat(10) { moveBy(Offset(0f, (slot - 12f) / 10f)) }
        }
        repeat(120) {
            frame()
        }
        handle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        waitFor(store) { s -> s.store.aisles().map { it.name }.take(2) == listOf("Bakery", "Produce") }
    }

    @Test
    fun rawTextReparses() {
        val store = launch()
        val text = store.now().store.aislesText.lines()
            .filterNot { it.trim() == "Bakery" }
            .joinToString("\n") { if (it.trim() == "Produce") "Fruit & vegetables" else it }
        compose.onNodeWithContentDescription("Aisles as text:").performScrollTo().performTextReplacement(text)
        // The labels preview the text 400 ms after typing stops; nothing is saved yet.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(450))
        compose.waitForIdle()
        // The label keeps "&" with the word after it (a no-break space, §0.4).
        val label = SemanticsMatcher("shows Fruit & vegetables") { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.replace('\u00A0', ' ') == "Fruit & vegetables" } }
        assertTrue(compose.onAllNodes(label).fetchSemanticsNodes().isNotEmpty())
        assertEquals("produce", store.now().store.aisles().first().id)
        // Done commits through the line diff: the edited line keeps its items, the removed line's go to Everything else.
        compose.onNodeWithText("Done").performClick()
        waitFor(store) { s -> s.store.aisles().first().name == "Fruit & vegetables" }
        val s = store.now()
        assertEquals(s.store.aisles().first().id, s.findItem("i-lemons")?.aisleId)
        assertNull(s.findItem("i-baguette")?.aisleId)
        assertNull(s.findItem("i-bagels")?.aisleId)
    }

    /**
     * One select-all and delete in "Aisles as text", then Done: the screen stays to say what it removed (leaving would
     * take the toast with it), Undo brings every aisle and its items back, and the next Done leaves.
     */
    @Test
    fun removedAislesComeBackWithUndo() {
        val store = launch()
        val before = store.now()
        val count = before.store.aisles().size
        compose.onNodeWithContentDescription("Aisles as text:").performScrollTo().performTextReplacement("")
        compose.onNodeWithText("Done").performClick()
        waitFor(store) { it.store.aisles().isEmpty() }
        assertEquals("Done stays while the removal toasts", 0, backs)
        assertNull(store.now().findItem("i-lemons")?.aisleId)
        compose.mainClock.autoAdvance = false
        repeat(20) { frame() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Removed $count aisles · their items are in Everything else", substring = true).assertExists()
        compose.onNodeWithText("Undo").performClick()
        repeat(5) { frame() }
        compose.mainClock.autoAdvance = true
        waitFor(store) { it.store.aislesText == before.store.aislesText }
        val after = store.now()
        assertEquals("colours, patterns and store numbers", before.store.aisleStyles, after.store.aisleStyles)
        assertTrue("baskets", before.store.aisleBaskets.all { (id, kind) -> after.store.aisleBaskets[id] == kind })
        assertEquals(before.findItem("i-lemons")?.aisleId, after.findItem("i-lemons")?.aisleId)
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals("the next Done leaves", 1, backs)
    }

    /** "Aisles as text" also commits when the field loses focus (§6.7), not only on Done. */
    @Test
    fun rawTextCommitsOnBlur() {
        val store = launch()
        val text = store.now().store.aislesText.lines().joinToString("\n") { if (it.trim() == "Produce") "Fruit & vegetables" else it }
        compose.onNodeWithContentDescription("Aisles as text:").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Aisles as text:").performTextReplacement(text)
        compose.waitForIdle()
        // Typing previews; nothing is saved yet.
        assertEquals("Produce", store.now().store.aisles().first().name)
        // Focus moves on to the Store field: the text commits through the line diff, so the edited line keeps its items.
        compose.onNodeWithTag(AislesTags.STORE).performScrollTo().performClick()
        waitFor(store) { s -> s.store.aisles().first().name == "Fruit & vegetables" }
        val s = store.now()
        assertEquals(s.store.aisles().first().id, s.findItem("i-lemons")?.aisleId)
    }

    /** §15 (swatches are named among the 48 dp targets): each colour and pattern swatch, and each basket, is ≥48 × 48 dp. */
    @Test
    fun swatchTargets() {
        launch()
        compose.onNodeWithContentDescription("Edit Bakery").performClick()
        compose.waitForIdle()
        val radios = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes()
        // 10 colours + 10 patterns + 17 baskets.
        assertEquals(37, radios.size)
        val small = radios.filter { n ->
            with(compose.density) { n.size.width.toDp() < 48.dp || n.size.height.toDp() < 48.dp }
        }.map { "${it.config.getOrNull(SemanticsProperties.ContentDescription)} ${it.size}" }
        assertTrue("targets under 48 dp: $small", small.isEmpty())
    }

    /**
     * §15 (names wrap, never clip) and §4.26: with the app's 14 default aisles, no label's name or "store aisle N" is
     * ellipsized or cut, at font scale 1.0, 1.3 and 2.0; every label that has a store number shows it.
     */
    @Test
    fun labelsNeverClip() {
        val ui = AislesViewModel.stateOf(Samples.shoppingState, baskets)
        val numbered = ui.labels.count { it.storeAisle != null }
        assertEquals(7, numbered)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                PicnicTheme { AislesScreen(ui, AislesActions()) }
            }
        }
        for (s in listOf(1f, 1.3f, 2f)) {
            scale = s
            compose.waitForIdle()
            val cut = mutableListOf<String>()
            var storeLines = 0
            for (position in 1..ui.labels.size) {
                compose.onAllNodes(hasAnyAncestor(hasTestTag(AislesTags.label(position))), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                    val results = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                    results.forEach { r ->
                        val text = r.layoutInput.text.text
                        if (text.startsWith("store aisle ")) storeLines++
                        if (r.isCut()) cut += "$s: '$text'"
                    }
                }
            }
            assertTrue("cut label texts: $cut", cut.isEmpty())
            assertEquals("store numbers shown at font scale $s", numbered, storeLines)
        }
    }

    @Test
    fun resortShowsRingAndToast() {
        val gate = CompletableDeferred<Unit>()
        val assistant = FakeAssistant(
            recategorize = { items, _ ->
                gate.await()
                items.associate { it.id to if (it.id in setOf("i-rack", "i-candles")) null else "produce" }
            },
        )
        launch(assistant = assistant)
        compose.onNodeWithText("Re-sort my list").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.state.value.syncing }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Sorting your list into the aisles…").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(AislesTags.RESORT_RING, useUnmergedTree = true).assertExists()
        // The clock stands still from here, so the toast can be seen during its hold (an auto-advancing clock would play
        // its whole drop, hold and lift inside one wait).
        compose.mainClock.autoAdvance = false
        gate.complete(Unit)
        repeat(20) {
            frame()
        }
        val report = vm.state.value.lastReport!!
        assertEquals(15 to 2, report.recategorized to report.leftUnsorted)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Sorted 15 items · 2 left in Everything else", substring = true).assertExists()
        compose.onNodeWithTag(AislesTags.RESORT_RING, useUnmergedTree = true).assertDoesNotExist()
    }
}

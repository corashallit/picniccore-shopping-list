package earth.cora.picnic.ui.add

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Add sheet (docs/DESIGN_STICKER.md §4.16–§4.19, §6.2; items 205, 206, 208, 323): the tabs switch modes, reduced
 * motion drops the fan-out, the "Choose a photo" chip, and the trip to My pantry and back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class AddSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val f = AddFixtures

    private fun tab(label: String) = compose.onNode(hasRole(Role.Tab) and hasText(label))

    @Test
    fun tabsSwitchModes() {
        val h = compose.setHarness()
        h.vm.openSheet(AddMode.WRITE, f.LIST_ID)
        compose.waitForIdle()
        tab("Write").assertIsSelected()
        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasContentDescription("Note")).assertExists()

        tab("Snap").performClick()
        compose.waitForIdle()
        assertEquals(AddMode.SNAP, h.vm.state.value.mode)
        tab("Snap").assertIsSelected()
        compose.onNodeWithTag(CHOOSE_PHOTO_CHIP_TAG).assertExists()
        compose.onNode(hasText("Take a photo")).assertExists()

        tab("Speak").performClick()
        compose.waitForIdle()
        assertEquals(AddMode.SPEAK, h.vm.state.value.mode)
        compose.onNodeWithText("Say your list out loud.").assertExists()
        compose.onNodeWithTag(RECORD_STICKER_TAG).assertExists()

        tab("Write").performClick()
        compose.waitForIdle()
        assertEquals(AddMode.WRITE, h.vm.state.value.mode)
        compose.onNodeWithTag(NOTE_FIELD_TAG).assertExists()
    }

    /** Reveals [review] in the stateless sheet with the frame clock held, and returns a reader of the footer. */
    private fun reveal(reduced: Boolean) {
        val state = AddUiState(
            listId = f.LIST_ID, sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES, phase = AddPhase.Preview,
            review = f.review(7, f.pancakes(), ItemSource.AI_TEXT, PANCAKES, revealed = false),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(ThemeVariant.Day, reducedMotion = reduced) {
                SheetStage { AddSheet(state, AddActions()) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun footerSays(text: String) = compose.onNode(hasText(text), useUnmergedTree = true).assertExists()

    @Test
    fun reducedMotion() {
        // Three steps land 250 ms apart (as fades), the capsule pops at 750 ms, and every row appears with it.
        val plan = RevealPlan.thinking(3, (0 until 10).toList(), reduced = true)
        assertEquals(setOf(750L), plan.rowAt.values.toSet())
        reveal(reduced = true)
        compose.mainClock.advanceTimeBy(800)
        footerSays("Reading… 10 of 10")
        compose.mainClock.advanceTimeBy(300)
        footerSays("Stick all 6 on my list")
    }

    @Test
    fun fanOutIs80MsApart() {
        val plan = RevealPlan.thinking(3, (0 until 10).toList(), reduced = false)
        assertEquals((0 until 10).map { 833L + it * 80 }, (0 until 10).map { plan.rowAt.getValue(it) })
        reveal(reduced = false)
        compose.mainClock.advanceTimeBy(800)
        footerSays("Reading… 0 of 10")
        compose.mainClock.advanceTimeBy(80)
        footerSays("Reading… 1 of 10")
        compose.mainClock.advanceTimeBy(80)
        footerSays("Reading… 2 of 10")
        compose.mainClock.advanceTimeBy(1_000)
        footerSays("Stick all 6 on my list")
    }

    @Test
    fun choosePhotoChip() {
        val h = compose.setHarness()
        h.vm.openSheet(AddMode.SNAP, f.LIST_ID)
        compose.waitForIdle()
        compose.onNodeWithTag(CHOOSE_PHOTO_CHIP_TAG).assert(hasRole(Role.Button)).assert(hasText("Choose a photo")).performClick()
        compose.waitForIdle()
        assertEquals(1, h.sources.picks)
        assertEquals(0, h.sources.photos)
    }

    @Test
    fun editPantryRoundTrip() {
        val rig = addRig(
            CoroutineScope(SupervisorJob() + Dispatchers.Main), f.stateWithPantry,
            FakeAssistant(ingest = { _, _ -> f.pancakes(withPantry = true) }),
        )
        val sources = RecordingSources()
        var onList by mutableStateOf(true)
        var opened = 0
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                if (onList) {
                    val flows = rememberAddFlows(rig.vm, rig.feedback, sources.sources)
                    AddFlowsHarness(flows, harnessRows(f.stateWithPantry), onEditPantry = { opened++; onList = false })
                } else {
                    Text("My pantry")
                }
            }
        }
        rig.vm.setText(PANCAKES)
        rig.vm.send()
        compose.waitForIdle()
        compose.onNode(reviewRow("Salt") and isToggleable()).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(EDIT_PANTRY_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, opened)
        compose.onNodeWithText("My pantry").assertExists()
        compose.onNodeWithTag(RECIPE_CARD_TAG).assertDoesNotExist()

        // Back: the same card with its keep switches, and the reveal does not play again (a replay would still be on
        // its steps 600 ms in: the card shows only after the pop at 1 s).
        compose.mainClock.autoAdvance = false
        onList = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag(RECIPE_CARD_TAG).assertExists()
        compose.onNode(reviewRow("Salt") and isToggleable()).assertIsOn()
        footerSays("Stick all 3 on my list")
        assertEquals(f.stateWithPantry.pantry, kotlinx.coroutines.runBlocking { rig.store.current().pantry })
    }
}

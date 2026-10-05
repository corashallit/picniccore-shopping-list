package earth.cora.picnic.ui.list

import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import earth.cora.picnic.ui.theme.LETTERSET_TAG
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.TOAST_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.junit4.createComposeRule

/**
 * Moving items, discoverable and explained (Cora, 5 October; §6.1, §6.3, §4.15), Day and Glow on the Appendix A list:
 * `toast_move` (a move from the Edit sheet, opened at "Move to aisle": its note over the sheet, above the footer),
 * `toast_move_first` (the first move's fuller note, over the list) and `list_hold_hint` (the one-time hint). Each note
 * is held still after its 220 ms drop.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class MoveNotesGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun moveToast() {
        val rig = ListRig().inSeason()
        compose.recordScreen(goldenName("toast_move", variant), variant, settleMillis = 0, before = {
            mainClock.autoAdvance = false
            // Opened as the add note's Move opens it (at "Move to aisle"), then her tap on Produce's chip.
            runOnIdle { rig.vm.openMove("toothpaste") }
            advanceMs(700)
            onNode(hasText("Produce") and hasAnyAncestor(hasTestTag(LETTERSET_TAG)), useUnmergedTree = true).performClick()
            assertTrue("the note dropped in", framesUntil(3000) { exists(TOAST_TAG) })
            advanceMs(220 + 600)
        }) { rig.Content() }
    }

    @Test
    fun firstMoveNote() {
        val rig = ListRig(firstTime = true).inSeason()
        runBlocking { rig.settings.markHoldHintDone() }
        compose.recordScreen(goldenName("toast_move_first", variant), variant, settleMillis = 0, before = {
            mainClock.autoAdvance = false
            runOnIdle { rig.vm.moveToAisle("toothpaste", "produce") }
            assertTrue("the note dropped in", framesUntil(3000) { exists(TOAST_TAG) })
            advanceMs(220 + 600)
        }) { rig.Content() }
    }

    @Test
    fun holdHint() {
        val rig = ListRig(firstTime = true).inSeason()
        compose.recordScreen(goldenName("list_hold_hint", variant), variant, settleMillis = 0, frozen = true, before = {
            assertTrue("the hint dropped in", framesUntil(3000) { exists(TOAST_TAG) })
            advanceMs(220 + 600)
        }) { rig.Content() }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

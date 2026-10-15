package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.LETTERSET_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Moving items, discoverable and explained (Cora, 5 October; docs/DESIGN_STICKER.md §6.1, §6.3, §4.15): every move says
 * what it changed, with Undo; the first move to an aisle says a little more, once; a one-time hint says an item can be
 * held, and a held row retires it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class MoveNotesTest {
    @get:Rule
    val compose = createComposeRule()

    private val moved = "Toothpaste goes in Produce from now on"
    private val first = "Toothpaste goes in Produce from now on. Smart sorting puts other kinds of toothpaste there too."
    private val hint = "Hold an item to edit it or move it to another aisle"

    private fun shows(text: String): Boolean = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun open(rig: ListRig) {
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        compose.frames(5)
    }

    /** A move from the Edit sheet: its note shows over the sheet, carries on over the list when the sheet closes, and Undo puts it all back. */
    @Test
    fun aMoveSaysSoAndUndoPutsTheAisleAndTheCorrectionBack() {
        val rig = ListRig()
        val before = rig.app
        open(rig)
        compose.runOnIdle { rig.vm.openEdit("toothpaste") }
        compose.advanceMs(600)
        compose.runOnIdle { rig.vm.moveToAisle("toothpaste", "produce") }
        assertTrue("the note shows over the sheet", compose.framesUntil(1500) { shows(moved) })
        assertEquals("produce", rig.item("toothpaste").aisleId)
        assertEquals("produce", rig.app.corrections.last { it.name == "toothpaste" }.aisleId)

        compose.runOnIdle { rig.vm.closeEdit() }
        compose.frames(3)
        assertTrue("it carries on over the list as the sheet closes", shows(moved))
        // The sheet slides away over it (§4.16, item 367); then its Undo is there to tap.
        assertTrue("the sheet slides away", compose.framesUntil(600) { !compose.exists(LETTERSET_TAG) })
        assertTrue("it carries on over the list once the sheet has closed", shows(moved))
        compose.onNodeWithText("Undo", useUnmergedTree = true).performClick()
        assertTrue(compose.framesUntil(1000) { rig.item("toothpaste").aisleId == "household" })
        assertEquals("the item as it was", before.list(ListFixture.LIST_ID)!!.items.first { it.id == "toothpaste" }, rig.item("toothpaste"))
        assertEquals("the corrections as they were", before.corrections, rig.app.corrections)
        compose.mainClock.autoAdvance = true
    }

    /** The first move to an aisle ever explains a little more, once (persisted); every later move gets the short note. */
    @Test
    fun theFirstMoveExplainsOnce() {
        val rig = ListRig(firstTime = true)
        runBlocking { rig.settings.markHoldHintDone() }
        open(rig)
        compose.runOnIdle { rig.vm.moveToAisle("toothpaste", "produce") }
        assertTrue(compose.framesUntil(1500) { shows(first) })
        assertTrue("remembered on the phone", runBlocking { rig.settings.current().moveNoteShown })
        assertTrue("it goes after its hold", compose.framesUntil(20_000) { !shows(first) })

        compose.runOnIdle { rig.vm.moveToAisle("dish-soap", "frozen") }
        assertTrue(compose.framesUntil(1500) { shows("Dish soap goes in Frozen from now on") })
        assertFalse(shows("Smart sorting"))
        compose.mainClock.autoAdvance = true
    }

    /** A move to Everything else learns no aisle, so it says only where the item went (and never spends the first note). */
    @Test
    fun aMoveToEverythingElseSaysOnlyThat() {
        val rig = ListRig(firstTime = true)
        runBlocking { rig.settings.markHoldHintDone() }
        open(rig)
        compose.runOnIdle { rig.vm.moveToAisle("toothpaste", null) }
        assertTrue(compose.framesUntil(1500) { shows("Toothpaste moved to Everything else") })
        assertFalse(runBlocking { rig.settings.current().moveNoteShown })
        compose.mainClock.autoAdvance = true
    }

    /** The hold hint: once, at a quiet moment, spoken politely, remembered; "Got it" sends it off and it never returns. */
    @Test
    fun theHoldHintShowsOnce() {
        val rig = ListRig(firstTime = true)
        open(rig)
        assertTrue("the list shows sorted items: the hint", compose.framesUntil(1500) { shows(hint) })
        assertTrue("remembered on the phone", runBlocking { rig.settings.current().holdHintDone })
        assertTrue("TalkBack hears it", rig.announcer.messages.any { hint in it })
        compose.onNodeWithText("Got it", useUnmergedTree = true).performClick()
        assertTrue(compose.framesUntil(1000) { !shows(hint) })
        compose.advanceMs(8000)
        assertFalse("never again", shows(hint))
        assertFalse(rig.vm.state.value.holdHint)
        compose.mainClock.autoAdvance = true
    }

    /** Holding a row (the long-press that opens Edit) is all the hint was for: it goes at once and never returns. */
    @Test
    fun aHeldRowRetiresTheHint() {
        val rig = ListRig(firstTime = true)
        open(rig)
        assertTrue(compose.framesUntil(1500) { shows(hint) })
        compose.onNodeWithTag(ListTags.row("baking-powder")).performSemanticsAction(SemanticsActions.OnLongClick)
        assertTrue("it lifts off now", compose.framesUntil(1000) { !shows(hint) })
        assertTrue("the Edit sheet opened", compose.framesUntil(1000) { rig.vm.state.value.editing != null })
        compose.runOnIdle { rig.vm.closeEdit() }
        compose.advanceMs(3000)
        assertFalse(shows(hint))
        compose.mainClock.autoAdvance = true
    }
}

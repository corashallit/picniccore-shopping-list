package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.hatchPet
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.moveItemToAisle
import earth.cora.picnic.core.model.removeItem
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Items 254, 265 and 270 (eggs 15 and 18, D21): a real press on the practice list's LCD, held for the hold threshold.
 * Before the trip is complete the pixel bee moves in ("FED" 1.5 s, saved on the trip); with no trip it only visits and
 * nothing is saved; once the trip is complete, however it was completed (a check-off, a tear-off, a move), the hold prints
 * the receipt and never hatches or feeds the pet.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class LcdHoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val listId = PracticeFixture.LIST_ID

    private fun EggRig.hold() = holdLcd(timeouts.holdThresholdMs + 20)

    @Test
    fun holdBeforeCompleteHatchesPet() {
        val r = EggRig(compose).start()
        assertFalse(r.list.trip!!.pet)
        // Shorter than the hold threshold: nothing.
        r.holdLcd(r.timeouts.holdThresholdMs - 100)
        assertFalse(r.eggs.pet)
        assertNull(r.eggs.lcdWord)
        // The hold: the bee moves in for this trip, saved; the LCD reads FED for 1.5 s, then the count beside the bee.
        r.hold()
        val t0 = r.now
        assertTrue(r.list.trip!!.pet)
        assertTrue(r.eggs.pet)
        assertEquals(EggRules.FED, r.eggs.lcdWord?.text)
        assertEquals(1, r.feedback.count(Cue.PET_FED))
        val shown = r.eggs.lcd(LcdState(LcdMode.Count, count = 10, total = 24))
        assertEquals(LcdMode.Pet, shown.mode)
        assertEquals("FED", shown.text)
        assertNull("no receipt before completion", r.eggs.receipt)
        r.advanceTo(t0 + EggRules.FED_MS - 100)
        assertEquals(EggRules.FED, r.eggs.lcdWord?.text)
        r.advanceTo(t0 + EggRules.FED_MS + 100)
        assertNull(r.eggs.lcdWord)
        assertEquals("", r.eggs.lcd(LcdState(LcdMode.Count, count = 10, total = 24)).text)
        // Holding again shows FED again.
        r.hold()
        assertEquals(EggRules.FED, r.eggs.lcdWord?.text)
        assertEquals(2, r.feedback.count(Cue.PET_FED))
        assertNull(r.eggs.receipt)
    }

    @Test
    fun holdWithNoTripPersistsNothing() {
        val r = EggRig(compose, fixture = PracticeFixture.unchecked()).start()
        assertNull(r.list.trip)
        val before = r.state
        r.hold()
        val t0 = r.now
        assertNotNull("the bee visits", r.eggs.petVisitId)
        assertEquals(EggRules.FED, r.eggs.lcdWord?.text)
        assertEquals(LcdMode.Pet, r.eggs.lcd(LcdState(LcdMode.Count, count = 0, total = 24)).mode)
        assertEquals("nothing is saved", before, r.state)
        assertFalse(r.eggs.pet)
        r.advanceTo(t0 + EggRules.FED_MS + 100)
        assertNull("and leaves after 1.5 s", r.eggs.petVisitId)
        assertEquals(LcdMode.Count, r.eggs.lcd(LcdState(LcdMode.Count, count = 0, total = 24)).mode)
        assertEquals(before, r.state)
    }

    @Test
    fun holdAfterCompletePrintsReceipt() {
        // Completed by check-offs, with a pet already living there: the hold prints; the pet is neither fed nor re-hatched.
        val r = EggRig(compose, fixture = completedFixture().hatchPet(listId)).start()
        r.hold()
        assertNotNull(r.eggs.receipt)
        assertNotNull(r.list.trip!!.receipt)
        assertEquals(EggRules.PRINT, r.eggs.lcdWord?.text)
        assertEquals(1, r.feedback.count(Cue.RECEIPT_PRINT))
        assertEquals("no FED", 0, r.feedback.count(Cue.PET_FED))
        assertNull("no chomp", r.eggs.petChompId)
        assertTrue("the pet stays as it was", r.list.trip!!.pet)
    }

    @Test
    fun receiptAfterTearOffCompletes() {
        // One item left; she tears it off with a swipe left on its row (WP5's gesture, §8.6): the delete completes the
        // trip, and the hold prints its receipt (item 270).
        val s = completedFixture().toggleChecked(listId, "toothpaste", PracticeFixture.NOW + 3_600_000)
        assertNull(s.list(listId)!!.trip!!.completedAt)
        val r = EggRig(compose, fixture = s).start()
        r.hold()
        assertNull("not complete: the pet", r.eggs.receipt)
        assertTrue(r.eggs.pet)
        r.advance(2_000)
        // Her last row, in view: she opens Household, the toothpaste row comes above the add bar.
        r.act { r.model.open("household") }
        r.advance(400)
        r.act { r.model.focus("household", "toothpaste", aisleFirst = false) }
        r.advance(800)
        val dp = compose.density.density
        compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.row("toothpaste")).performTouchInput {
            down(center)
            repeat(4) {
                moveBy(androidx.compose.ui.geometry.Offset(-20 * dp, 0f))
                advanceEventTime(16)
            }
            up()
        }
        // The perforation prints, the halves tumble off, then the row leaves the list (§8.6).
        var guard = 0
        while (r.list.items.any { it.id == "toothpaste" } && guard++ < 120) r.frame()
        assertTrue("torn out", r.list.items.none { it.id == "toothpaste" })
        assertNotNull("the tear-off completed the trip", r.list.trip!!.completedAt)
        // A trip finished by a tear-off is finished all the same (§7.1, item 176): the finale plays; she closes it.
        guard = 0
        while (r.model.motion.current.value.celebration == null && guard++ < 400) r.frame()
        assertNotNull("the finale plays", r.model.motion.current.value.celebration)
        r.advance(1_000)
        r.act { r.model.motion.closeCelebration() }
        guard = 0
        while (r.model.motion.current.value.celebration != null && guard++ < 400) r.frame()
        r.advance(800)
        r.hold()
        assertNotNull("the tear-off completed it: the receipt prints", r.eggs.receipt)
        assertEquals(23, r.eggs.receipt!!.receipt.stickers)
        assertEquals(1, r.feedback.count(Cue.PET_FED))
    }

    @Test
    fun completedByAMoveOrAnUndoPrintsToo() {
        // Completion is a property of the state (§7.1): an un-check undone (re-checked) completes the trip again, and the
        // hold prints.
        val s = completedFixture()
            .toggleChecked(listId, "toothpaste", PracticeFixture.NOW + 3_600_000)
            .toggleChecked(listId, "toothpaste", PracticeFixture.NOW + 3_610_000)
        val r = EggRig(compose, fixture = s).start()
        r.hold()
        assertNotNull(r.eggs.receipt)
        // Moving an item to another aisle keeps the trip complete: the hold still prints, and never hatches the pet.
        val moved = completedFixture().moveItemToAisle(listId, "toothpaste", "frozen", PracticeFixture.NOW + 3_600_000)
        val e = EngineRig(state = moved)
        try {
            e.engine.onListShown(listId)
            e.engine.onLcdHold()
            assertNotNull(e.eggs.receipt)
            assertFalse(e.list.trip!!.pet)
        } finally {
            e.close()
        }
    }
}

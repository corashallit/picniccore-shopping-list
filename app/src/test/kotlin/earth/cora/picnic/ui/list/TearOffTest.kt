package earth.cora.picnic.ui.list

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.TOAST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tear-off (docs/DESIGN_STICKER.md §8.6, §4.15; item 118): a perforation prints in (120 ms), the halves tumble off
 * (600 ms), then the row leaves the list and the note "Bananas torn out" holds at least 4 s with Undo; Undo puts the
 * row back exactly (checked state, its book spot, its sticker, its place). A second tear-off during the hold merges.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TearOffTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    private fun tear(id: String) {
        compose.onNodeWithTag(ListTags.row(id)).performTouchInput {
            down(center)
            repeat(4) { moveBy(Offset(-20 * dp, 0f)) }
            up()
        }
    }

    @Test
    fun undoRestoresExactly() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        val before = rig.item("bananas")
        val index = rig.app.items().indexOfFirst { it.id == "bananas" }
        assertTrue("Bananas is checked, in the book", before.checked && before.bookSlot != null)

        compose.mainClock.autoAdvance = false
        tear("bananas")
        compose.advanceMs(100)
        assertTrue("the perforation prints first: still on the list at 100 ms", rig.app.items().any { it.id == "bananas" })
        compose.advanceMs(560)
        assertTrue("the halves are still tumbling at 660 ms", rig.app.items().any { it.id == "bananas" })
        // Then the slot closes (380 ms), and only then is the row gone (§8.6).
        assertTrue("torn once its slot has closed, by 1300 ms", compose.framesUntil(640) { rig.app.items().none { it.id == "bananas" } })
        assertEquals("peel + tick", 1, rig.feedback.count(Cue.TEAR_OFF))

        // The note: "Bananas torn out" + Undo, held still for at least 4 s.
        assertTrue(compose.framesUntil(400) { compose.exists(TOAST_TAG) })
        compose.onNodeWithText("Bananas torn out", useUnmergedTree = true).assertExists()
        compose.advanceMs(4000)
        assertTrue("the note holds at least 4 s", compose.exists(TOAST_TAG))

        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val after = rig.item("bananas")
        assertEquals("back in its place", index, rig.app.items().indexOfFirst { it.id == "bananas" })
        assertTrue("still checked", after.checked)
        assertEquals("the same book spot", before.bookSlot, after.bookSlot)
        assertEquals("the same sticker", before.icon, after.icon)
        assertEquals("the same check-off time (the basket order)", before.checkedAt, after.checkedAt)
        assertTrue("its twin is back in the book", rig.vm.state.value.book!!.stickers.any { it.itemId == "bananas" && it.slot == before.bookSlot })
        assertTrue("the row is back on the tray", compose.exists(ListTags.row("bananas")))
    }

    /** A second tear-off during the note's hold merges into "2 items torn out"; Undo restores both in place. */
    @Test
    fun secondTearMerges() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        val order = rig.app.items().map { it.id }
        // The note holds on the frame clock: hold the clock so the second tear-off lands inside the first one's hold.
        compose.mainClock.autoAdvance = false
        tear("strawberries")
        compose.advanceMs(1300)
        tear("apples")
        compose.advanceMs(1300)
        assertFalse(rig.app.items().any { it.id == "strawberries" || it.id == "apples" })
        compose.onNodeWithText("2 items torn out", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("both back where they were", order, rig.app.items().map { it.id })
        assertEquals(24, rig.app.list(ListFixture.LIST_ID)!!.items.size)
    }

    /**
     * The slot closes on the tray's own paper (§8.6): after the halves have fallen (720 ms) its height shrinks to nothing
     * over 380 ms while the rows below follow it, so no hole ever opens in the tray; the halves' words are gone before
     * the halves can reach the strip below.
     */
    @Test
    fun slotClosesOnPaper() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        val full = compose.boundsOf(ListTags.rowSlot("apples")).height
        val below = compose.boundsOf(ListTags.rowSlot("carrots")).top
        compose.mainClock.autoAdvance = false
        tear("apples")
        compose.advanceMs(700)
        assertEquals("the slot keeps its height while the halves fall", full, compose.boundsOf(ListTags.rowSlot("apples")).height, 1f)
        var last = full
        var shrank = 0
        repeat(20) {
            compose.frames(1)
            if (!compose.exists(ListTags.rowSlot("apples"))) return@repeat
            val slot = compose.boundsOf(ListTags.rowSlot("apples"))
            val next = compose.boundsOf(ListTags.rowSlot("carrots"))
            assertTrue("the slot only shrinks", slot.height <= last + 0.5f)
            if (slot.height < last - 0.5f) shrank++
            last = slot.height
            // The row below follows the slot's edge frame by frame: no gap between them.
            assertEquals("the row below meets the closing slot", slot.bottom, next.top, 1.5f)
        }
        assertTrue("the slot closed over several frames ($shrank)", shrank >= 10)
        assertTrue("then the row is gone", compose.framesUntil(300) { rig.app.items().none { it.id == "apples" } })
        compose.frames(2)
        assertEquals("the row below took its place", below - full, compose.boundsOf(ListTags.rowSlot("carrots")).top, 1.5f)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun tornWordsLeaveFirst() {
        assertEquals(1f, tearWordsAlpha(0f), 0f)
        assertEquals(0f, tearWordsAlpha(TEAR_WORDS_FADE), 0f)
        assertEquals(0f, tearWordsAlpha(0.6f), 0f)
    }

    /**
     * A tear she released is never lost (item 118): folding the aisle 200 ms into the tear (the row leaves the
     * composition before the halves have fallen) still deletes it and still offers Undo.
     */
    @Test
    fun foldMidTearStillTears() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        compose.mainClock.autoAdvance = false
        tear("apples")
        compose.advanceMs(200)
        assertTrue("still tearing at 200 ms", rig.app.items().any { it.id == "apples" })
        compose.onNodeWithTag(ListTags.chevron("produce")).performClick()
        assertTrue("torn out although the aisle folded", compose.framesUntil(1000) { rig.app.items().none { it.id == "apples" } })
        assertTrue("the Undo note shows", compose.framesUntil(1000) { compose.exists(TOAST_TAG) })
        compose.onNodeWithText("McIntosh apples torn out", useUnmergedTree = true).assertExists()
        compose.advanceMs(1500)
        assertEquals("deleted once", 23, rig.app.items().size)
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("Undo puts it back", rig.app.items().any { it.id == "apples" })
    }
}

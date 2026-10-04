package earth.cora.picnic.ui.a11y

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.platform.FakeAnnouncer
import earth.cora.picnic.ui.add.ADD_FIELD_TAG
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.ListRig
import earth.cora.picnic.ui.list.ListTags
import earth.cora.picnic.ui.list.advanceMs
import earth.cora.picnic.ui.list.exists
import earth.cora.picnic.ui.list.frames
import earth.cora.picnic.ui.list.framesUntil
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import earth.cora.picnic.ui.list.show
import earth.cora.picnic.ui.theme.TOAST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 284 (docs/DESIGN_STICKER.md §15 Announcements): the polite announcements fire at their moments, once each and in
 * order, through the real path: the List destination's own view models, WP6's engine and the toasts' host, into the
 * Announcer the app hands them (here a FakeAnnouncer on the test clock, with the production 1 s de-duplication). A
 * check-off's own state is never one of them (the row's checkbox says that).
 * - A trip to its end: the check-off ("… added to today's sticker book"), the aisle done, the Next stop, the last
 *   check-off, its aisle, and the trip complete.
 * - The first check-off after 12 idle hours: the check-off, then the new book's note in words; its Undo says what it
 *   undid.
 * - An add from the bar: the loader's label once, the add's result in words; its Undo says what it undid.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class AnnouncementsFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun rig(state: AppState = ListFixture.state(), now: Long = ListFixture.NOW) =
        ListRig(state, now = now, announcer = FakeAnnouncer { compose.mainClock.currentTime })

    /** Nothing was spoken twice: every line once (the 1 s rule drops a repeat; none should be needed past it). */
    private fun assertOnceEach(a: FakeAnnouncer) {
        assertEquals("no line spoken twice: ${a.spoken}", a.spoken.distinct(), a.spoken)
    }

    /** Taps a row of the list on the held clock, as her finger would. */
    private fun tapRow(id: String) {
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey(id)
        compose.frames(2)
        compose.onNodeWithTag(ListTags.row(id)).performClick()
    }

    @Test
    fun aTripToItsEnd() {
        // The last two things, 41 minutes in (Appendix A): Baking powder closes Baking, Maple syrup the trip.
        val r = rig(ListFixture.allBut(listOf("baking-powder", "maple"), open = "aisle-2-baking"), now = ListFixture.TRIP_START + 41 * ListFixture.MIN)
        compose.show(r)
        compose.mainClock.autoAdvance = false
        tapRow("baking-powder")
        assertTrue("the Next stop is announced", compose.framesUntil(5_000) { r.announcer.spoken.any { it.startsWith("Next:") } })
        // One tap skips the card (§10), and Breakfast is open: the last thing.
        assertTrue("the Next card rises", compose.framesUntil(1_000) { compose.exists(NEXT_CARD_TAG) })
        compose.onNodeWithTag(NEXT_CARD_TAG).performClick()
        assertTrue("the Next card leaves", compose.framesUntil(1_000) { !compose.exists(NEXT_CARD_TAG) })
        tapRow("maple")
        assertTrue("the trip complete is announced", compose.framesUntil(6_000) { r.announcer.spoken.any { it.startsWith("List complete!") } })
        compose.advanceMs(1_500)
        assertEquals(
            listOf(
                "Baking powder added to today's sticker book",
                "Baking done",
                "Next: aisle 5, Breakfast",
                "Maple syrup added to today's sticker book",
                "Breakfast done",
                "List complete! 24 stickers in 41 minutes. Perfect!",
            ),
            r.announcer.spoken,
        )
        assertOnceEach(r.announcer)
        assertTrue("over the celebration", compose.exists(CELEBRATION_TAG))
    }

    @Test
    fun aNewBookAndItsUndo() {
        val r = rig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        compose.show(r)
        compose.mainClock.autoAdvance = false
        // Opened after 12 idle hours, the nudge drops in and is heard (§6.1).
        val nudge = "New shop? Start a fresh sticker book"
        assertTrue("the nudge is heard", compose.framesUntil(1_000) { r.announcer.spoken == listOf(nudge) })
        tapRow("baking-powder")
        assertTrue("the new book's note drops in", compose.framesUntil(3_000) { r.announcer.spoken.size >= 3 })
        compose.advanceMs(1_000)
        val note = "New sticker book for today, your last shop is in the album"
        assertEquals(listOf(nudge, "Baking powder added to today's sticker book", note), r.announcer.spoken)
        // Its Undo, as a double tap with TalkBack presses it.
        compose.onNode(hasText("Undo") and hasClickAction()).performClick()
        compose.advanceMs(1_500)
        assertEquals(
            "the Undo says what it undid, first",
            listOf(nudge, "Baking powder added to today's sticker book", note, "Undone: $note"),
            r.announcer.spoken.take(4),
        )
        // Back in the old book, Baking powder was Baking's last thing: its aisle is done there, and the next stop
        // follows, each heard at its moment.
        assertEquals(listOf("Baking done", "Next: aisle 5, Breakfast"), r.announcer.spoken.drop(4))
        assertOnceEach(r.announcer)
    }

    @Test
    fun anAddAndItsUndo() {
        val r = rig()
        r.assistant.ingest = { _, _ ->
            FakeAssistant.result(listOf(ProposedItem("Honey", "500 g jar", null, "breakfast", "honey", IconFit.EXACT, named = true)))
        }
        compose.show(r)
        compose.onNodeWithTag(ADD_FIELD_TAG).performClick()
        compose.onNodeWithTag(ADD_FIELD_TAG).performTextInput("honey")
        compose.mainClock.autoAdvance = false
        compose.onNode(hasContentDescription("Send")).performClick()
        assertTrue("the add's note drops in", compose.framesUntil(4_000) { compose.exists(TOAST_TAG) })
        compose.advanceMs(1_000)
        assertEquals(listOf("Reading your note…", "Added Honey to Breakfast"), r.announcer.spoken)
        compose.onNode(hasText("Undo") and hasClickAction()).performClick()
        compose.advanceMs(1_500)
        assertEquals(listOf("Reading your note…", "Added Honey to Breakfast", "Undone: Added Honey to Breakfast"), r.announcer.spoken)
        assertOnceEach(r.announcer)
    }
}

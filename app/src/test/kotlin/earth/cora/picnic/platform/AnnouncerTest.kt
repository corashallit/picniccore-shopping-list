package earth.cora.picnic.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checklist 284, unit part (docs/DESIGN_STICKER.md §15): announcements are de-duplicated within 1 s. */
class AnnouncerTest {
    private var now = 10_000L
    private val deduper = AnnouncementDeduper(clock = { now })

    @Test
    fun theSameTextWithinOneSecondIsDropped() {
        assertTrue(deduper.admit("Strawberries added to today's sticker book"))
        now += 999
        assertFalse(deduper.admit("Strawberries added to today's sticker book"))
        now += 1 // 1000 ms after the first: a new announcement
        assertTrue(deduper.admit("Strawberries added to today's sticker book"))
    }

    @Test
    fun aDroppedRepeatDoesNotExtendTheWindow() {
        assertTrue(deduper.admit("Produce done"))
        now += 600
        assertFalse(deduper.admit("Produce done"))
        now += 400 // 1000 after the admitted one, 400 after the dropped one
        assertTrue(deduper.admit("Produce done"))
    }

    @Test
    fun differentTextsNeverBlockEachOther() {
        assertTrue(deduper.admit("Strawberries added to today's sticker book"))
        assertTrue(deduper.admit("Produce done"))
        assertTrue(deduper.admit("Next: aisle 5, Breakfast"))
        now += 300
        assertFalse(deduper.admit("Produce done"))
        assertFalse(deduper.admit("Strawberries added to today's sticker book"))
    }

    @Test
    fun blankIsNeverSpokenAndSpacesDoNotMakeANewText() {
        assertFalse(deduper.admit(""))
        assertFalse(deduper.admit("   "))
        assertTrue(deduper.admit("Receipt saved to your sticker book"))
        assertFalse(deduper.admit("  Receipt saved to your sticker book "))
    }

    @Test
    fun fakeAnnouncerRecordsEveryCallAndSpeaksLikeThePhone() {
        val fake = FakeAnnouncer(clock = { now })
        fake.polite("Produce done")
        fake.polite("Produce done")
        fake.polite(" ")
        now += 1000
        fake.polite("Produce done")
        assertEquals(listOf("Produce done", "Produce done", " ", "Produce done"), fake.messages)
        assertEquals(listOf("Produce done", "Produce done"), fake.spoken)

        val noClock = FakeAnnouncer()
        noClock.polite("Produce done")
        noClock.polite("Produce done")
        noClock.polite("")
        assertEquals(listOf("Produce done", "Produce done"), noClock.spoken)
    }

    @Test
    fun noOpAnnouncerIsSilent() {
        NoOpAnnouncer.polite("anything")
    }
}

package earth.cora.picnic.ui.list.motion

import earth.cora.picnic.core.model.TripStatsValue
import earth.cora.picnic.ui.theme.readHold
import earth.cora.picnic.ui.theme.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The celebration's computed storyboard (docs/DESIGN_STICKER.md §9, §11.2; item 182): each text beat starts at the
 * previous text's settle + readHold(its words, counted from the real name and stats), and no glint, curl or shake falls
 * inside any hold. Unit.
 */
class CelebrationTimelineTest {
    private val fixture = TripStatsValue(stickers = 24, baskets = 8, minutes = 41, shiny = 2)

    private fun CelebrationPlan.at(name: BeatName) = beat(name).at

    @Test
    fun fixture() {
        val p = CelebrationTimeline.plan("Saturday shop", fixture, petHatched = false)
        val title = p.beat(BeatName.Title)
        assertEquals(700L, title.settle)
        assertEquals(8, title.words)
        assertEquals(3600L, title.hold)
        assertEquals(4300L, p.at(BeatName.Perfect))
        assertEquals(4730L, p.beat(BeatName.Perfect).settle)
        assertEquals(1500L, p.beat(BeatName.Perfect).hold)
        assertEquals(6230L, p.at(BeatName.Banner))
        assertEquals(6530L, p.beat(BeatName.Banner).settle)
        assertEquals(4, p.beat(BeatName.Banner).words)
        assertEquals(2400L, p.beat(BeatName.Banner).hold)
        assertEquals("24 stickers · 41 min", p.banner)
        assertEquals(8930L, p.at(BeatName.Bonus))
        assertEquals(9430L, p.beat(BeatName.Bonus).settle)
        assertEquals(1800L, p.beat(BeatName.Bonus).hold)
        assertEquals(8330L, p.at(BeatName.CapsuleWobble))
        assertEquals(9030L, p.at(BeatName.CapsulePop))
        assertEquals(11230L, p.at(BeatName.Curl))
        assertEquals(11750L, p.at(BeatName.PocketKira))
        assertEquals(11830L, p.at(BeatName.PocketMagic))
        assertEquals(12150L, p.summaryAt)
        // Everything on screen at the summary, with the progress bar's label "Closing… tap to keep it" (§0.4 Q7, §9):
        // 12 words of results + 5 of the label = 17, so max(4000, readHold(17)) = 6.3 s and it closes at 18.45 s.
        assertEquals(17, p.summaryWords)
        assertEquals(6300L, p.summaryHold)
        assertEquals(18450L, p.closeAt)
        // Without the bar (TalkBack: no countdown) the label is not on screen and does not count.
        val talkBack = CelebrationTimeline.plan("Saturday shop", fixture, petHatched = false, closingBar = false)
        assertEquals(12, talkBack.summaryWords)
        assertEquals(4800L, talkBack.summaryHold)
        assertEquals("♡ Saturday shop", p.listChip)
        assertEquals("24/24★", p.lcd)
        // The §19.2 contract returns the same beats.
        assertEquals(p.beats, CelebrationTimeline.of("Saturday shop", fixture, petHatched = false))
        assertNoMotionInsideHolds(p)
    }

    @Test
    fun fiveWordName() {
        val name = "Big family Sunday dinner shop"
        val p = CelebrationTimeline.plan(name, fixture, petHatched = false)
        val title = p.beat(BeatName.Title)
        assertEquals(11, title.words)
        assertEquals(readHold(11, 1500), title.hold)
        assertEquals(4500L, title.hold)
        // Every later beat moves on by the longer title hold.
        assertEquals(700L + 4500, p.at(BeatName.Perfect))
        assertEquals(20, p.summaryWords)
        assertEquals(7200L, p.summaryHold)
        assertEquals(p.summaryAt + 7200, p.closeAt)
        assertNoMotionInsideHolds(p)
    }

    @Test
    fun oneHourFiveMinutes() {
        val p = CelebrationTimeline.plan("Saturday shop", fixture.copy(minutes = 65), petHatched = false)
        val banner = p.beat(BeatName.Banner)
        assertEquals("24 stickers · 1 h 5 min", banner.text)
        // Counted from the real banner by words(): 24 · stickers · 1 · h · 5 · min.
        assertEquals(words("24 stickers · 1 h 5 min"), banner.words)
        assertEquals(readHold(banner.words, 1500), banner.hold)
        assertTrue("an hour or more holds longer than 41 min", banner.hold > 2400)
        assertEquals(banner.settle!! + banner.hold, p.at(BeatName.Bonus))
        assertNoMotionInsideHolds(p)
    }

    @Test
    fun petDanceOnlyWhenHatched() {
        assertFalse(CelebrationTimeline.plan("Saturday shop", fixture, false).beats.any { it.name == BeatName.PetDance })
        val p = CelebrationTimeline.plan("Saturday shop", fixture, true)
        assertEquals(p.at(BeatName.Banner), p.at(BeatName.PetDance))
    }

    @Test
    fun reducedMotion() {
        val p = CelebrationTimeline.plan("Saturday shop", fixture, petHatched = false, reduced = true)
        assertTrue(p.reduced)
        assertEquals(0L, p.summaryAt)
        assertEquals(120L, p.beat(BeatName.Summary).settle)
        // 17 words with the bar's label: max(6000, readHold(17)) = 6.3 s.
        assertEquals(17, p.summaryWords)
        assertEquals(6300L, p.summaryHold)
        assertEquals(120L + 6300, p.closeAt)
        // Without the bar: 12 words, the 6 s floor.
        assertEquals(6000L, CelebrationTimeline.plan("Saturday shop", fixture, petHatched = false, reduced = true, closingBar = false).summaryHold)
        // A long name lifts the hold above the 6 s floor.
        val long = CelebrationTimeline.plan("Big family Sunday dinner shop for the cottage weekend", fixture, false, reduced = true)
        assertEquals(readHold(long.summaryWords, 6000), long.summaryHold)
        assertTrue(long.summaryHold > 6000)
    }

    /** No glint, curl or shake starts or runs inside any text's hold (§9). */
    private fun assertNoMotionInsideHolds(p: CelebrationPlan) {
        val crossing = listOf(BeatName.TitleGlint, BeatName.CardGlint, BeatName.Curl, BeatName.Shake)
        // Which texts each can cross: the title glint the title; the card glint the stamp card (no text until PERFECT);
        // the shake everything; the curl the bottom-right corner, beside the bonus card.
        val holds = p.textBeats.associate { it.name to (it.settle!! until it.settle!! + it.hold) }
        val title = holds.getValue(BeatName.Title)
        assertTrue("title glint after the title's hold", p.at(BeatName.TitleGlint) >= title.last + 1)
        val shake = p.beat(BeatName.Shake)
        for ((name, h) in holds) {
            assertFalse("shake inside $name's hold", shake.at in h || (shake.endsAt - 1) in h)
        }
        val curl = p.beat(BeatName.Curl)
        val bonus = holds.getValue(BeatName.Bonus)
        assertTrue("curl after the bonus hold", curl.at >= bonus.last + 1)
        val card = p.beat(BeatName.CardGlint)
        val perfect = holds.getValue(BeatName.Perfect)
        assertTrue("card glint ends before PERFECT settles", card.endsAt <= perfect.first)
        for (b in crossing) assertTrue(p.beats.any { it.name == b })
    }
}

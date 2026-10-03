package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.platform.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The check-off chain and its reverse (docs/DESIGN_STICKER.md §8.3, §8.4; items 87, 122, 127–130), on the harness with
 * the Compose test clock: the presentation is sampled every frame and every beat lands within one frame of its time.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class CheckOffChoreographyTest {
    @get:Rule
    val compose = createComposeRule()

    private fun produceOpen(): AppState = MotionFixture.state().setOpenAisle(MotionFixture.LIST_ID, "produce")

    /** Item 122: hanko 0 (2 steps, 160 ms), ink 160, peel 200 / 260, hop 320 (300 ms), land 620, magic 940, settled 1260. */
    @Test
    fun timeline() {
        val rig = HarnessRig(compose, produceOpen()).start()
        val t0 = rig.tap("apples")
        val slot = rig.model.item("apples").bookSlot!!
        val frames = rig.sampleTo(t0 + 1400)
        val reduced = false
        fun hanko(time: Long, p: ListPresentation) = RowMotion.hankoScale(true, p.rows["apples"], p.rowStarts["apples"], time, reduced)

        // The hanko slams on the tap's frame: 1.4, then 1.15 at 80, then 1 at 160 (2 steps); the burst with it.
        val first = frames.first { it.first == t0 }.second
        assertEquals(RowPhase.Slam, first.rows["apples"])
        assertEquals(t0, first.rowStarts["apples"])
        assertEquals(1.4f, hanko(t0, first), 0.001f)
        assertTrue("the burst starts with the slam", first.bursts.isNotEmpty())
        assertBeat("hanko cue", t0, rig.feedback.times(Cue.HANKO).firstOrNull())
        assertBeat("hanko 1.15", t0 + 80, frames.firstOrNull { (t, p) -> t >= t0 && hanko(t, p) == 1.15f }?.first)
        assertBeat("hanko settles", t0 + 160, frames.firstOrNull { (t, p) -> t >= t0 && p.rows["apples"] != null && hanko(t, p) == 1f }?.first)
        // Ink at 160 (from the row's start).
        assertBeat("ink", t0 + Beats.INK, frames.firstOrNull { (t, p) -> p.rowStarts["apples"]?.let { HankoMotion.ink(t - it) } != null }?.first)
        // Peel at 200 and its second pose at 260.
        assertBeat("peel", t0 + Beats.PEEL, frames.firstTime { it.rows["apples"] == RowPhase.Peeling })
        assertBeat("peel cue", t0 + Beats.PEEL, rig.feedback.times(Cue.PEEL).firstOrNull())
        assertBeat("peel 2", t0 + Beats.PEEL_2, frames.firstOrNull { (t, p) -> p.rows["apples"] == RowPhase.Peeling && PeelMotion.at(t - p.rowStarts["apples"]!!).dy == -4f }?.first)
        // The hop at 320 for 300 ms.
        val hopAt = frames.firstTime { it.flights.isNotEmpty() }
        assertBeat("hop", t0 + Beats.HOP, hopAt)
        val flight = frames.first { it.second.flights.isNotEmpty() }.second.flights.single()
        assertEquals(t0 + Beats.HOP, flight.t0)
        assertEquals(300L, flight.durationMs)
        assertEquals(RowPhase.Away, frames.first { it.first == hopAt }.second.rows["apples"])
        // The landing at 620: the flight ends, the basket squashes, the cues sound, the badge pops.
        assertBeat("land", t0 + 620, frames.firstTime(from = hopAt!!) { it.flights.isEmpty() })
        assertBeat("land cue", t0 + 620, rig.feedback.times(Cue.LAND).firstOrNull())
        assertBeat("lcd tick", t0 + 620, rig.feedback.times(Cue.LCD_TICK).firstOrNull())
        val landed = frames.first { it.first >= t0 + 620 }.second.baskets["produce"]!!
        assertEquals(t0 + 620, landed.squashT0)
        assertEquals(t0 + 620, landed.badgePopT0)
        // The twin's magic at 940; settled by 1260 (the spot shows the state's twin again).
        assertBeat("magic", t0 + 940, frames.firstTime { it.spots[slot] is SpotPhase.Appearing })
        assertBeat("magic cue", t0 + 940, rig.feedback.times(Cue.MAGIC_APPEAR).firstOrNull())
        assertEquals(SpotPhase.Appearing(t0 + 940), frames.first { it.second.spots[slot] is SpotPhase.Appearing }.second.spots[slot])
        assertBeat("settled", t0 + 1260, frames.firstTime(from = t0 + 940) { it.spots[slot] == null })
        val end = frames.last().second
        assertTrue("nothing left in flight", end.rows.isEmpty() && end.flights.isEmpty() && end.spots.isEmpty())
    }

    /** Item 127: hanko lift, hop back (340 ms), ghost until it lands (360), badge and LCD at once, twin peels in place. */
    @Test
    fun uncheckTimeline() {
        val rig = HarnessRig(compose, produceOpen()).start()
        val t0 = rig.tap("apples")
        val slot = rig.model.item("apples").bookSlot!!
        rig.advanceTo(t0 + 1400)
        val checkedBefore = rig.model.state.list(MotionFixture.LIST_ID)!!.items.count { it.checked }
        val u0 = rig.tap("apples")
        val checkedAfter = rig.model.state.list(MotionFixture.LIST_ID)!!.items.count { it.checked }
        assertEquals(checkedBefore - 1, checkedAfter)
        val frames = rig.sampleTo(u0 + 600)
        val at0 = frames.first().second
        // At once: the LCD shows the new count and the badge pops, with no lag.
        assertEquals(checkedAfter, at0.lcdCount(checkedAfter))
        assertEquals(0, at0.lcdLag)
        assertEquals(0, at0.baskets["produce"]?.toGetLag ?: 0)
        assertEquals(u0, at0.baskets["produce"]?.badgePopT0)
        assertBeat("uncheck cue", u0, rig.feedback.times(Cue.UNCHECK).lastOrNull())
        // The hanko lifts (1 → 1.1 → gone in 160 ms) and the row keeps its ghost until 360.
        fun hanko(t: Long, p: ListPresentation) = RowMotion.hankoScale(false, p.rows["apples"], p.rowStarts["apples"], t, false)
        assertEquals(RowPhase.Landing, at0.rows["apples"])
        assertEquals(1f, hanko(u0, at0), 0.001f)
        assertBeat("hanko 1.1", u0 + 80, frames.firstOrNull { (t, p) -> hanko(t, p) == 1.1f }?.first)
        assertBeat("hanko gone", u0 + 160, frames.firstOrNull { (t, p) -> p.rows["apples"] == RowPhase.Landing && hanko(t, p) == 0f }?.first)
        for ((t, p) in frames) {
            if (t < u0 + Beats.GHOST_UNTIL) assertFalse("ghost until the sticker is back (at ${t - u0})", RowMotion.showsSticker(false, p.rows["apples"]))
        }
        assertBeat("sticker back", u0 + Beats.GHOST_UNTIL, frames.firstOrNull { (_, p) -> RowMotion.showsSticker(false, p.rows["apples"]) }?.first)
        // The overlay copy hops back from the basket to the row in 340 ms.
        val back = at0.flights.single()
        assertTrue(back.back)
        assertEquals(AnchorRegistry.basketKey("produce"), back.fromKey)
        assertEquals(AnchorRegistry.rowKey("apples"), back.toKey)
        assertEquals(340L, back.durationMs)
        assertBeat("hop back ends", u0 + 340, frames.firstTime { it.flights.isEmpty() })
        // The twin peels off in place and is gone at 320; the spot is free again.
        assertEquals(SpotPhase.Peeling(u0, rig.model.item("apples").iconRef(), false), at0.spots[slot])
        assertBeat("peeled off", u0 + Beats.PEEL_OFF, frames.firstTime { it.spots[slot] == null })
        assertNull(rig.model.item("apples").bookSlot)
    }

    /** Item 128: re-tapping mid-chain reverses it from where the sticker is, and nothing is left stuck. */
    @Test
    fun rapidRetap() {
        val rig = HarnessRig(compose, produceOpen()).start()
        val t0 = rig.tap("apples")
        rig.advanceTo(t0 + 450) // mid-hop
        val u0 = rig.tap("apples")
        val back = rig.p.flights.single()
        assertTrue("the un-check hops back from mid-air", back.back && back.startAt != null)
        rig.advanceTo(u0 + 150) // mid-way back
        val c0 = rig.tap("apples")
        val again = rig.p.flights.single()
        assertFalse(again.back)
        assertTrue("the re-check flies on from where it was", again.startAt != null && again.t0 == c0)
        val frames = rig.sampleTo(c0 + 1600)
        for ((t, p) in frames) assertTrue("one sticker in the air at a time (at $t)", p.flights.count { it.itemId == "apples" } <= 1)
        val end = frames.last().second
        assertTrue("no stuck overlay", end.flights.isEmpty() && end.bursts.isEmpty() && end.rows.isEmpty() && end.spots.isEmpty())
        assertTrue(rig.model.item("apples").checked)
        assertEquals(listOf(false, true, false), rig.engine.flightLog.filter { it.itemId == "apples" }.map { it.back })
    }

    /** Item 129: a basket that is not laid out: the chain lands in place and the book still updates in place. */
    @Test
    fun noBasketLandsInPlace() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        // Household is far below the open Baking tray: its strip (and basket) is not composed.
        assertFalse(rig.engine.anchors.isLaidOut(AnchorRegistry.basketKey("household")))
        val t0 = rig.tap("toilet-paper")
        val slot = rig.model.item("toilet-paper").bookSlot!!
        val frames = rig.sampleTo(t0 + 1400)
        assertTrue("no flight", frames.all { it.second.flights.isEmpty() })
        assertTrue(rig.engine.flightLog.none { it.itemId == "toilet-paper" })
        assertBeat("lands in place", t0 + 620, rig.feedback.times(Cue.LAND).firstOrNull())
        assertEquals(t0 + 620, frames.first { it.first >= t0 + 620 }.second.baskets["household"]?.squashT0)
        assertBeat("the book still updates", t0 + 940, frames.firstTime { it.spots[slot] is SpotPhase.Appearing })
        assertTrue(rig.engine.eventLog.any { it is MotionEvent.MagicAppeared && it.itemId == "toilet-paper" && it.slot == slot })
    }

    /** Item 130: un-checking or moving between L and L + 1350 cancels the pending beats and reverses the shown ones. */
    @Test
    fun revertDuringAisleDone() {
        // (a) Un-check after the lid and the stamp, before the curl: no fold, no "done", no Next card; the lid reopens.
        run {
            val rig = HarnessRig(compose, MotionFixture.state()).start()
            val t0 = rig.tap("baking-powder")
            val L = t0 + 620
            rig.advanceTo(L + 600)
            assertEquals(true, rig.p.baskets["aisle-2-baking"]?.lidClosed)
            val u0 = rig.tap("baking-powder")
            val at0 = rig.p.baskets["aisle-2-baking"]!!
            assertEquals(false, at0.lidClosed)
            assertEquals(u0, at0.lidT0)
            assertEquals(u0, at0.lidHankoLiftT0)
            val frames = rig.sampleTo(L + 2600)
            assertTrue("no Next card", frames.all { it.second.nextCard == null })
            assertTrue("no curl or fold", frames.all { it.second.aisles["aisle-2-baking"]?.let { a -> a.curlT0 == null && a.foldT0 == null } ?: true })
            assertTrue(rig.engine.eventLog.none { it is MotionEvent.AisleDone || it is MotionEvent.NextShown })
            assertEquals("she stays in Baking", "aisle-2-baking", rig.model.grouped().openSectionId)
        }
    }

    @Test
    fun revertDuringAisleDoneCancelsTheCelebration() {
        // (b) The trip's last aisle: un-checking at L + 1000 cancels the celebration and PERFECT.
        val rig = HarnessRig(compose, MotionFixture.allBut(listOf("toothpaste"), open = "household")).start()
        val t0 = rig.tap("toothpaste")
        val L = t0 + 620
        rig.advanceTo(L + 1000)
        rig.tap("toothpaste")
        val frames = rig.sampleTo(L + 3200)
        assertTrue("no scene", frames.all { it.second.celebration == null })
        assertTrue(rig.engine.eventLog.none { it is MotionEvent.CelebrationStarted || it is MotionEvent.TripComplete || it is MotionEvent.AisleDone || it is MotionEvent.BookPerfect })
        assertEquals(0, rig.feedback.count(Cue.PERFECT))
        assertEquals(0, rig.feedback.count(Cue.CELEBRATION))
    }

    @Test
    fun revertDuringAisleDoneByMove() {
        // (c) Moving an unchecked item into the finished aisle at L + 700 re-opens it: no fold, no Next card.
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        rig.advanceTo(L + 700)
        compose.runOnIdle { rig.model.move("milk", "aisle-2-baking") }
        rig.frame()
        val frames = rig.sampleTo(L + 2600)
        assertEquals(false, frames.first().second.baskets["aisle-2-baking"]?.lidClosed)
        assertTrue("no Next card", frames.all { it.second.nextCard == null })
        assertTrue("events: ${rig.engine.eventLog}", rig.engine.eventLog.none { it is MotionEvent.AisleDone || it is MotionEvent.NextShown })
    }

    /** Item 87: the item's name and detail never move through the check and the un-check chains. */
    @Test
    fun itemTextNeverMoves() {
        val rig = HarnessRig(compose, produceOpen()).start()
        val name0 = rig.bounds(HarnessTags.name("apples"))
        val detail0 = rig.bounds(HarnessTags.detail("apples"))
        fun check(t: Long) {
            assertEquals("name at $t", name0, rig.bounds(HarnessTags.name("apples")))
            assertEquals("detail at $t", detail0, rig.bounds(HarnessTags.detail("apples")))
        }
        val t0 = rig.tap("apples")
        rig.sampleTo(t0 + 1400) { check(it) }
        val u0 = rig.tap("apples")
        rig.sampleTo(u0 + 700) { check(it) }
    }
}

package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.platform.Cue
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

/** The book's magic in place (docs/DESIGN_STICKER.md §7.6; items 148–152, 155). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class BookMagicTest {
    @get:Rule
    val compose = createComposeRule()

    private fun produceOpen(): AppState = MotionFixture.state().setOpenAisle(MotionFixture.LIST_ID, "produce")

    /** The times (from [t0]) at which [pose] changes over the sampled frames. */
    private fun changes(frames: List<Pair<Long, ListPresentation>>, t0: Long, pose: (Long) -> Any?): List<Long> {
        val out = ArrayList<Long>()
        var last: Any? = Unit
        for ((t, _) in frames) {
            val p = pose(t - t0)
            if (last != Unit && p != last) out += t - t0
            last = p
        }
        return out
    }

    /** Item 148: the twin appears 320 ms after the landing with its bloom, 6 sparks, 4 poses and a 2 dp page lift. */
    @Test
    fun timeline() {
        val rig = HarnessRig(compose, produceOpen()).start()
        val t0 = rig.tap("apples")
        val slot = rig.model.item("apples").bookSlot!!
        val frames = rig.sampleTo(t0 + 1400)
        val appear = frames.firstTime { it.spots[slot] is SpotPhase.Appearing }
        assertBeat("magic (940)", t0 + 940, appear)
        assertBeat("magic cue", t0 + 940, rig.feedback.times(Cue.MAGIC_APPEAR).firstOrNull())
        val a = (frames.first { it.first == appear }.second.spots[slot] as SpotPhase.Appearing).t0
        assertEquals(t0 + 940, a)
        // The 4 poses at +64 / +144 / +224 / +320, sampled on the frames (each within a frame of its time).
        val poseChanges = changes(frames.filter { it.first >= a }, a) { e -> BookMotion.twin(e, slot) }
        assertEquals(4, poseChanges.size)
        listOf(64L, 144L, 224L, 320L).zip(poseChanges).forEach { (want, got) -> assertBeat("twin pose +$want", want, got) }
        assertEquals(1f, BookMotion.twin(320, slot).scaleX, 0f)
        assertEquals(BookMotion.tilt(slot), BookMotion.twin(320, slot).rotation, 0f)
        // The kira bloom (400 ms) and 6 sparks fly with it.
        assertNotNull(BookMotion.bloom(0))
        assertNull(BookMotion.bloom(400))
        assertEquals(6, BookMotion.sparkKinds.size)
        assertTrue((0 until 6).all { BookMotion.spark(it, 0) != null && BookMotion.spark(it, 400) == null })
        // The page lifts 2 dp (2 steps, 160 ms) with the appear.
        assertEquals(a, frames.first { it.first == appear }.second.bookFx.liftT0)
        assertEquals(-2f, BookMotion.lift(0), 0f)
        assertNull("the lift ends by 160 ms", frames.first { it.first >= a + 160 }.second.bookFx.liftT0)
        assertTrue(compose.onNodeWithTag(HarnessTags.twin(slot), useUnmergedTree = true).fetchSemanticsNode().layoutInfo.isPlaced)

        // A personality hop (strawberries: berry, 360 ms) lands at 680 and the twin appears at 1000.
        val t1 = rig.tap("strawberries")
        val slot1 = rig.model.item("strawberries").bookSlot!!
        val f1 = rig.sampleTo(t1 + 1100)
        assertBeat("magic after a personality hop (1000)", t1 + 1000, f1.firstTime { it.spots[slot1] is SpotPhase.Appearing })
    }

    /** Item 149: a twin on another page turns the book first (3 frames, 240 ms), then appears. */
    @Test
    fun turnsPageFirst() {
        val rig = HarnessRig(compose, produceOpen()).start()
        rig.engine.bookPaging = BookPaging(perPage = 8, shownPage = 0)
        val t0 = rig.tap("apples")
        val slot = rig.model.item("apples").bookSlot!!
        assertEquals(1, slot / 8)
        val frames = rig.sampleTo(t0 + 1500)
        val turnAt = frames.firstTime { it.bookFx.pageTurn != null }
        assertBeat("turn (land + 320)", t0 + 940, turnAt)
        assertEquals(PageTurn(1, t0 + 940), frames.first { it.first == turnAt }.second.bookFx.pageTurn)
        assertEquals(1, frames.first { it.first == turnAt }.second.bookFx.showPage)
        assertBeat("turn sound", t0 + 940, rig.feedback.times(Cue.UNCHECK).firstOrNull())
        assertBeat("magic after the turn", t0 + 940 + 240, frames.firstTime { it.spots[slot] is SpotPhase.Appearing })
        assertEquals(listOf(-70f to false, 70f to true, 0f to true), listOf(0L, 80L, 160L).map { BookMotion.pageTurn(it) })
    }

    /** Item 150: un-check peels the twin off in place (lift, 1.12, .55 with inward sparks, gone at 320); the spot is free. */
    @Test
    fun peelOff() {
        val rig = HarnessRig(compose, produceOpen()).start()
        val t0 = rig.tap("apples")
        val slot = rig.model.item("apples").bookSlot!!
        rig.advanceTo(t0 + 1400)
        val u0 = rig.tap("apples")
        val frames = rig.sampleTo(u0 + 500)
        assertTrue(frames.first().second.spots[slot] is SpotPhase.Peeling)
        assertBeat("gone at 320", u0 + 320, frames.firstTime { it.spots[slot] == null })
        val poses = changes(frames.filter { it.second.spots[slot] is SpotPhase.Peeling }, u0) { e -> BookMotion.peel(e, slot) }
        listOf(64L, 144L, 224L).zip(poses).forEach { (want, got) -> assertBeat("peel pose +$want", want, got) }
        assertEquals(-12f, BookMotion.peel(64, slot).rotation, 0f)
        assertEquals(1.12f, BookMotion.peel(144, slot).scaleX, 0f)
        assertEquals(0.55f, BookMotion.peel(224, slot).scaleX, 0f)
        assertNotNull("inward sparks at +224", BookMotion.spark(0, 0, inward = true))
        assertEquals(MotionPose.Gone, BookMotion.peel(320, slot))
        // The spot is free: the state no longer holds it and it is the book's next spot again.
        val list = rig.model.state.list(MotionFixture.LIST_ID)!!
        assertTrue(list.items.none { it.bookSlot == slot })
        val used = list.items.mapNotNull { it.bookSlot }.toSet()
        assertEquals(slot, (0 until list.items.size).first { it !in used })
    }

    /** Item 151: the book updates while the list is scrolled far below it (no anchors needed). */
    @Test
    fun updatesWhenScrolled() {
        val listState = LazyListState()
        val rig = HarnessRig(compose, MotionFixture.state())
        rig.start { MotionHarness(rig.model, rig.toasts, listState = listState) }
        compose.onNodeWithTag(HarnessTags.LIST).performScrollToIndex(7)
        rig.frame()
        assertTrue(listState.firstVisibleItemIndex > 0)
        val t0 = rig.tap("toilet-paper")
        val slot = rig.model.item("toilet-paper").bookSlot!!
        val frames = rig.sampleTo(t0 + 1400)
        assertBeat("the twin still appears", t0 + 940, frames.firstTime { it.spots[slot] is SpotPhase.Appearing })
        assertTrue(compose.onNodeWithTag(HarnessTags.twin(slot), useUnmergedTree = true).fetchSemanticsNode().layoutInfo.isPlaced)
    }

    /** Item 152: a full book glints once, 360 ms after its last twin. */
    @Test
    fun fullGlint() {
        val rig = HarnessRig(compose, MotionFixture.allBut(listOf("toothpaste"), open = "household")).start()
        val t0 = rig.tap("toothpaste")
        val frames = rig.sampleTo(t0 + 2600)
        val glintAt = frames.firstTime { it.bookFx.fullGlintT0 != null }
        assertBeat("full glint (magic + 360)", t0 + 940 + 360, glintAt)
        assertEquals("one glint", setOf(t0 + 1300), frames.mapNotNull { it.second.bookFx.fullGlintT0 }.toSet())
        assertTrue("held until it glints", frames.filter { it.first < t0 + 1300 }.all { it.second.bookFx.fullHeld })
    }

    /** Item 155: reduced motion: the twin fades at the same beat (940), no bloom, sparks, lift or turn. */
    @Test
    fun reducedMotion() {
        val rig = HarnessRig(compose, produceOpen(), reduced = true).start()
        rig.engine.bookPaging = BookPaging(perPage = 8, shownPage = 0)
        val t0 = rig.tap("apples")
        val slot = rig.model.item("apples").bookSlot!!
        val frames = rig.sampleTo(t0 + 1400)
        // With no turn the twin appears at land + 320 even on another page.
        assertBeat("magic at 940", t0 + 940, frames.firstTime { it.spots[slot] is SpotPhase.Appearing })
        assertTrue(frames.all { it.second.bookFx.liftT0 == null && it.second.bookFx.pageTurn == null })
        assertTrue(frames.all { it.second.flights.isEmpty() && it.second.bursts.isEmpty() })
        assertEquals(0, rig.feedback.count(Cue.UNCHECK))
        // Un-check: the twin fades out over 120 ms.
        val u0 = rig.tap("apples")
        val f2 = rig.sampleTo(u0 + 400)
        assertBeat("faded out at 120", u0 + 120, f2.firstTime { it.spots[slot] == null })
    }

    /**
     * §0.4 Q9, §7.6 "New trip": the first check-off more than 12 h after the trip's last activity starts a new book on its
     * own. The book turns its page (3 frames, 240 ms) to the fresh page on the tap's frame, so the old book's twins leave
     * with the turn ("peel" with it), and the twin then magic-appears in the fresh book at 940 as usual.
     */
    @Test
    fun autoNewBookTurnsToFreshPage() {
        val rig = HarnessRig(compose, produceOpen()).start()
        rig.model.now += TWELVE_HOURS + 60_000
        val t0 = rig.tap("apples")
        assertNotNull("the tap started a new book", rig.model.lastAutoNewBook)
        val slot = rig.model.item("apples").bookSlot!!
        assertEquals("the fresh book's first spot", 0, slot)
        val frames = rig.sampleTo(t0 + 1400)
        val first = frames.first().second
        assertEquals("the turn starts on the tap's frame", PageTurn(0, t0), first.bookFx.pageTurn)
        assertEquals(0, first.bookFx.showPage)
        assertFalse("its first frame turns the old page away", first.bookFx.showsNewPage(t0))
        assertTrue("the fresh page from 80 ms", frames.filter { it.first in (t0 + 80) until (t0 + 240) }.all { (t, p) -> p.bookFx.showsNewPage(t) })
        assertBeat("the turn is over by 240", t0 + 240, frames.firstTime { it.bookFx.pageTurn == null })
        assertBeat("peel with the turn", t0, rig.feedback.times(Cue.UNCHECK).firstOrNull())
        assertBeat("hanko with the tap", t0, rig.feedback.times(Cue.HANKO).firstOrNull())
        assertBeat("magic in the fresh book (940)", t0 + 940, frames.firstTime { it.spots[slot] is SpotPhase.Appearing })
        assertTrue("the spot waits empty until then", frames.filter { it.first < t0 + 940 }.all { it.second.spots[slot] == SpotPhase.Empty })
        // An ordinary check-off (not idle) never turns the page.
        val c0 = rig.tap("strawberries")
        assertNull(rig.model.lastAutoNewBook)
        assertTrue(rig.sampleTo(c0 + 1400).all { it.second.bookFx.pageTurn == null })
    }

    /**
     * The new book's Undo (§0.4 Q9): the old book shows again with a page turn back (240 ms), and the tapped item's twin
     * fades into its old spot (120 ms) once the turn has shown the page; no bloom, no flight.
     */
    @Test
    fun autoNewBookUndoTurnsBack() {
        val rig = HarnessRig(compose, produceOpen()).start()
        rig.model.now += TWELVE_HOURS + 60_000
        val t0 = rig.tap("apples")
        rig.advanceTo(t0 + 2000)
        var change: ListChange? = null
        compose.runOnIdle { change = rig.model.undoNewBook() }
        rig.frame()
        val u0 = rig.now
        assertEquals(ChangeCause.NEW_BOOK_UNDO, change!!.cause)
        val slot = change!!.slot!!
        assertEquals("checked into the old book", slot, rig.model.item("apples").bookSlot)
        assertTrue("the old book's stickers are back", rig.model.state.list(MotionFixture.LIST_ID)!!.items.count { it.checked } > 1)
        val frames = rig.sampleTo(u0 + 600)
        assertEquals(PageTurn(0, u0), frames.first().second.bookFx.pageTurn)
        assertBeat("turned back by 240", u0 + 240, frames.firstTime { it.bookFx.pageTurn == null })
        assertTrue(frames.filter { it.first < u0 + 240 }.all { it.second.spots[slot] == SpotPhase.Empty })
        assertBeat("the twin fades in at 240", u0 + 240, frames.firstTime { it.spots[slot] is SpotPhase.Appearing })
        assertEquals(SpotPhase.Appearing(u0 + 240, inPlace = true), frames.first { it.second.spots[slot] is SpotPhase.Appearing }.second.spots[slot])
        assertBeat("settled at 360", u0 + 360, frames.firstTime(from = u0 + 240) { it.spots[slot] == null })
        assertTrue(frames.all { it.second.flights.isEmpty() && it.second.bursts.isEmpty() })
        assertFalse(rig.engine.busy())
    }

    /** An Undo before the fresh book's twin appeared: the twin never appears in the old book's spot of that number. */
    @Test
    fun autoNewBookUndoBeforeTheMagic() {
        val rig = HarnessRig(compose, produceOpen()).start()
        rig.model.now += TWELVE_HOURS + 60_000
        val t0 = rig.tap("apples")
        rig.advanceTo(t0 + 500)
        var change: ListChange? = null
        compose.runOnIdle { change = rig.model.undoNewBook() }
        rig.frame()
        val slot = change!!.slot!!
        val frames = rig.sampleTo(t0 + 1600)
        assertTrue("only the old spot's fade", frames.all { (_, p) -> p.spots.keys.all { it == slot } })
        assertEquals("no magic cue", 0, rig.feedback.count(Cue.MAGIC_APPEAR))
        assertTrue(rig.engine.eventLog.none { it is MotionEvent.MagicAppeared })
    }

    private companion object {
        const val TWELVE_HOURS: Long = 12L * 60 * 60 * 1000
    }
}

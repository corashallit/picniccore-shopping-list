package earth.cora.picnic.ui.list

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import earth.cora.picnic.ui.add.ADD_BAR_TAG
import earth.cora.picnic.ui.add.ADD_FAB_TAG
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.toggleChecked
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
 * The nudge (docs/DESIGN_STICKER.md §6.1, §9; item 120): a list that opens on a trip idle for more than 12 h gets the
 * sticky note "New shop? Start a fresh sticker book" with "New trip", held at least 6 s; nothing is cleared.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TripNudgeTest {
    @get:Rule
    val compose = createComposeRule()

    private val nudge = "New shop? Start a fresh sticker book"

    @Test
    fun nudgeAfterTwelveHoursIdle() {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        val before = rig.app
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        assertTrue("the nudge shows", compose.framesUntil(1500) { compose.exists(TOAST_TAG) })
        compose.onNodeWithText(nudge, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("New trip", useUnmergedTree = true).assertExists()
        // It holds max(6000, readHold) (it carries an action, §9).
        compose.advanceMs(5700)
        compose.onNodeWithText(nudge, useUnmergedTree = true).assertExists()
        // Nothing was cleared or filed.
        assertEquals(before.list(ListFixture.LIST_ID), rig.app.list(ListFixture.LIST_ID))
        assertTrue((rig.album.album as kotlinx.coroutines.flow.StateFlow<earth.cora.picnic.core.store.Album>).value.books.isEmpty())
        // Its action opens the New trip sheet.
        compose.onNodeWithText("New trip", useUnmergedTree = true).performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(compose.exists(NewTripTags.SHEET))
    }

    /** A lifecycle the test moves by hand (the app going to the background and coming back). */
    private class HandLifecycle : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    private fun showWith(rig: ListRig, owner: HandLifecycle) {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) { rig.Themed() }
        }
    }

    /** The app waits in the background, then comes back to the front [later] ms on. */
    private fun backgroundFor(rig: ListRig, owner: HandLifecycle, later: Long) {
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        }
        rig.now += later
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        }
    }

    /**
     * Items 120 and 143 (§6.1, §7.1): the app stays alive in the background and she comes back 13 h later. Returning to
     * the list checks the trip again: it is idle now, so the nudge shows, once for that idle period (another return
     * without a check-off in between says nothing more).
     */
    @Test
    fun comingBackAfterTwelveHoursNudges() {
        val rig = ListRig(now = ListFixture.NOW + ListFixture.HOUR)
        val owner = HandLifecycle()
        compose.mainClock.autoAdvance = false
        showWith(rig, owner)
        compose.advanceMs(1500)
        assertFalse("not idle yet", compose.exists(TOAST_TAG))
        backgroundFor(rig, owner, 13 * ListFixture.HOUR)
        assertTrue("the nudge shows on her return", compose.framesUntil(1500) { compose.exists(TOAST_TAG) })
        compose.onNodeWithText(nudge, useUnmergedTree = true).assertExists()
        assertTrue("it goes after its hold", compose.framesUntil(15_000) { !compose.exists(TOAST_TAG) })
        backgroundFor(rig, owner, ListFixture.HOUR)
        compose.advanceMs(1500)
        assertFalse("once per idle period", compose.exists(TOAST_TAG))
        compose.mainClock.autoAdvance = true
    }

    /** Item 143 (§7.1): a fresh book shows today; back from the background the next day, its date says the new day. */
    @Test
    fun aFreshBookRollsOverToToday() {
        val rig = ListRig(ListFixture.fresh(), now = ListFixture.NOW)
        val owner = HandLifecycle()
        showWith(rig, owner)
        compose.waitForIdle()
        assertEquals("Sat 3 Oct", rig.vm.state.value.book!!.dateLabel)
        backgroundFor(rig, owner, 24 * ListFixture.HOUR)
        compose.waitForIdle()
        assertEquals("the new day", "Sun 4 Oct", rig.vm.state.value.book!!.dateLabel)
    }

    private fun nudgeShows(): Boolean =
        compose.onAllNodes(hasText(nudge), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Opens the idle list (13 h on) and files its book through the menu's New trip → "Clear what I got". */
    private fun newTripFromTheMenu(rig: ListRig) {
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        assertTrue("the nudge shows", compose.framesUntil(1500) { nudgeShows() })
        compose.onNodeWithContentDescription("More options").performClick()
        compose.frames(20)
        compose.onNode(hasText("New trip") and hasAnyAncestor(hasTestTag(LIST_MENU_TAG)), useUnmergedTree = true).performClick()
        compose.frames(40)
        compose.onNodeWithText("Clear what I got").performClick()
        compose.frames(2)
        assertEquals("the book was filed", null, rig.app.list(ListFixture.LIST_ID)!!.trip)
    }

    /** Items 120 and 162 (§6.1, §6.5, §4.15): a New trip from the menu while the nudge shows retires it at once. */
    @Test
    fun menuNewTripRetiresTheNudge() {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        newTripFromTheMenu(rig)
        assertTrue("the nudge lifts off at once, long before its 6 s hold", compose.framesUntil(600) { !nudgeShows() })
        compose.onNodeWithText("New trip · a fresh sticker book", substring = true, useUnmergedTree = true).assertExists()
        compose.mainClock.autoAdvance = true
    }

    /**
     * Items 120, 162 and 116: a nudge whose trip has gone another way (she peeled its only sticker, so the trip closed
     * without a New trip) opens nothing when she taps its "New trip"; the next check-off opens no sheet either, and the
     * add bar still folds into the FAB (no sheet request is left waiting).
     */
    @Test
    fun aStaleNudgeOpensNothing() {
        val start = ListFixture.longAisle().let { s ->
            s.copy(lists = s.lists.map { l -> l.copy(items = l.items.map { it.copy(checked = false, checkedAt = null, bookSlot = null) }, trip = null) })
        }.toggleChecked(ListFixture.LIST_ID, "bananas", ListFixture.NOW)
        val rig = ListRig(start, now = ListFixture.NOW + 13 * ListFixture.HOUR)
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        assertTrue("the nudge shows", compose.framesUntil(1500) { nudgeShows() })
        compose.runOnIdle { rig.vm.toggle("bananas") }
        compose.frames(20)
        assertEquals("the trip closed with its last sticker", null, rig.app.list(ListFixture.LIST_ID)!!.trip)
        assertTrue("the nudge is still up", nudgeShows())
        compose.onNode(hasText("New trip") and hasAnyAncestor(hasTestTag(TOAST_TAG)), useUnmergedTree = true).performClick()
        compose.frames(30)
        assertFalse("no sheet over a list without a trip", compose.exists(NewTripTags.SHEET))
        // The first check-off opens a fresh trip as usual: no sheet pops up unasked.
        compose.runOnIdle { rig.vm.toggle("paper-towels") }
        compose.frames(60)
        assertFalse(compose.exists(NewTripTags.SHEET))
        assertTrue(rig.app.list(ListFixture.LIST_ID)!!.items.single { it.id == "paper-towels" }.checked)
        // And the add bar still folds into the FAB on her scroll down (household holds 11 rows).
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(ListTags.LIST).performScrollToIndex(0)
        compose.waitForIdle()
        assertTrue("at the top the bar is docked", compose.exists(ADD_BAR_TAG))
        val dp = compose.density.density
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 120 * dp, endY = top + 10 * dp, durationMillis = 500) }
        compose.waitForIdle()
        assertTrue("the bar folded into the FAB", compose.exists(ADD_FAB_TAG))
        assertFalse(compose.exists(ADD_BAR_TAG))
    }

    /** §6.5 (item 116): a New trip request on a list with no trip to file is dropped, so the add bar can fold. */
    @Test
    fun aSheetRequestWithoutATripIsDropped() {
        val screen = ListScreenState(newTripOpen = true)
        compose.setContent {
            earth.cora.picnic.ui.theme.PicnicTheme {
                ListScreen(state = ListFixture.ui(ListFixture.fresh()), actions = ListActions(), screen = screen)
            }
        }
        compose.waitForIdle()
        assertFalse(screen.newTripOpen)
        assertFalse(compose.exists(NewTripTags.SHEET))
    }

    @Test
    fun noNudgeWithinTwelveHours() {
        val rig = ListRig(now = ListFixture.NOW + 11 * ListFixture.HOUR)
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        compose.advanceMs(1500)
        assertFalse(compose.exists(TOAST_TAG))
    }

    @Test
    fun noNudgeWithoutATrip() {
        val rig = ListRig(ListFixture.fresh(), now = ListFixture.NOW + 13 * ListFixture.HOUR)
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        compose.advanceMs(1500)
        assertFalse(compose.exists(TOAST_TAG))
    }

    /**
     * The trip review (§0.4 Q9, §7.1, items 120 and 314): the evening after a finished shop she writes down paper towels,
     * and 13 h after the shop checks them off. That tap files Saturday's book, starts a fresh one and finishes it (1/1).
     * The fresh trip's finale counts as shown at once: no celebration, no bonus sticker; only the aisle's own chain plays,
     * and the new book's note with its Undo stays readable for its whole hold, and its Undo works.
     */
    @Test
    fun autoNewBookSameTapCompletion() {
        val rig = ListRig(ListFixture.done(), now = ListFixture.TRIP_START + 8 * ListFixture.HOUR)
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        compose.frames(5)
        compose.runOnIdle {
            rig.addVm.setText("Paper towels")
            rig.addVm.addAsTyped()
        }
        // The add's own note has come and gone.
        assertTrue(compose.framesUntil(3_000) { compose.exists(TOAST_TAG) })
        assertTrue(compose.framesUntil(12_000) { !compose.exists(TOAST_TAG) })
        val towels = rig.app.list(ListFixture.LIST_ID)!!.items.single { it.name == "Paper towels" }
        assertTrue("written down for next time, the same trip", rig.app.list(ListFixture.LIST_ID)!!.trip!!.finaleShownAt != null)
        // 13 h after the shop's last check-off.
        rig.now = ListFixture.TRIP_START + 14 * ListFixture.HOUR
        compose.runOnIdle { rig.vm.toggle(towels.id) }
        compose.frames(3)
        val album = (rig.album.album as kotlinx.coroutines.flow.StateFlow<earth.cora.picnic.core.store.Album>).value
        assertEquals("Saturday's book is filed", 1, album.books.size)
        val trip = rig.app.list(ListFixture.LIST_ID)!!.trip!!
        assertEquals(listOf("Paper towels"), rig.app.list(ListFixture.LIST_ID)!!.items.map { it.name })
        assertTrue("the fresh trip is complete", trip.completedAt != null)
        assertEquals("its finale counts as shown at the tap", rig.now, trip.finaleShownAt)
        // No scene, no bonus, for the whole of the note's hold; the note stays on top.
        val note = "New sticker book for today"
        fun noteShows() = compose.onAllNodes(hasText(note, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        val shown = compose.framesUntil(3000) { noteShows() }
        val texts = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() }
        assertTrue("the new-book note shows ($texts, celebration ${rig.engine.current.value.celebration})", shown)
        var frames = 0
        while (frames < 4000 / 16) {
            compose.frames(1)
            frames++
            assertEquals("no celebration (${frames * 16} ms)", null, rig.engine.current.value.celebration)
        }
        assertTrue("the note is still there to read after 4 s", noteShows())
        assertEquals("no bonus sticker spent", null, rig.app.list(ListFixture.LIST_ID)!!.trip!!.bonus)
        // Its Undo still works: Saturday's book and its 24 stickers come back, the paper towels checked into it.
        compose.onNodeWithText("Undo", useUnmergedTree = true).performClick()
        compose.frames(30)
        val back = rig.app.list(ListFixture.LIST_ID)!!
        assertEquals(25, back.items.size)
        assertTrue(back.items.all { it.checked })
        assertTrue("the book left the album", (rig.album.album as kotlinx.coroutines.flow.StateFlow<earth.cora.picnic.core.store.Album>).value.books.isEmpty())
        compose.mainClock.autoAdvance = true
    }
}

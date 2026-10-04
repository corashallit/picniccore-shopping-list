package earth.cora.picnic.ui.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.add.ADD_BAR_TAG
import earth.cora.picnic.ui.add.ADD_FAB_TAG
import earth.cora.picnic.ui.add.ADD_FIELD_TAG
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.TOAST_TAG
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The docked add bar on the real list (docs/DESIGN_STICKER.md §6.1, §4.15; the list's half of item 116): WP7's dock
 * placed through rememberAddFlows, the list's bottom padding its height + 26 dp, the toasts 20 dp above it; her scroll
 * down folds it into the FAB and back up brings it back; the FAB restores the bar and focuses the field.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class FabTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    @Test
    fun paddingAndToastInset() {
        val rig = ListRig()
        compose.show(rig)
        val dock = compose.boundsOf(ADD_BAR_TAG)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("end-note")
        compose.waitForIdle()
        // The end note's item ends 4 dp under its pad patch, and the list's content ends dock + 26 dp above the bottom.
        val note = compose.boundsOf(ListTags.END_NOTE)
        assertEquals("the list ends 26 dp above the add bar", dock.top - 26 * dp, note.bottom + 4 * dp, 1.5f * dp)
        assertTrue("still docked: a programmatic scroll never folds the bar", compose.exists(ADD_BAR_TAG) && !compose.exists(ADD_FAB_TAG))
        // A note sits 20 dp above the bar (its −2° tilt adds a little).
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { rig.vm.delete("toothpaste") }
        assertTrue(compose.framesUntil(1500) { compose.exists(TOAST_TAG) })
        compose.advanceMs(500)
        val toast = compose.boundsOf(TOAST_TAG)
        assertEquals("20 dp above the add bar", dock.top - 20 * dp, toast.bottom, 5 * dp)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun scrollFoldsIntoFabAndBack() {
        val rig = ListRig(ListFixture.longAisle())
        compose.show(rig)
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 120 * dp, endY = top + 10 * dp, durationMillis = 500) }
        compose.waitForIdle()
        assertTrue("scrolled down past 140 dp: the FAB", compose.exists(ADD_FAB_TAG))
        assertFalse(compose.exists(ADD_BAR_TAG))
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeDown(startY = centerY - 60 * dp, endY = centerY + 20 * dp, durationMillis = 400) }
        compose.waitForIdle()
        assertTrue("up 40 dp: the bar is back", compose.exists(ADD_BAR_TAG))
        // Down again, then the FAB itself brings the bar back and focuses the field.
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 120 * dp, endY = top + 10 * dp, durationMillis = 500) }
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_FAB_TAG).performClick()
        compose.waitForIdle()
        assertTrue(compose.exists(ADD_BAR_TAG))
        assertTrue(compose.onNodeWithTag(ADD_FIELD_TAG).fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false })
    }

    /**
     * Items 116 and 71: when the list empties while she is scrolled down (here every item goes at once, as after New
     * trip → "Clear what I got" on a list that was all got), the empty state comes with the docked add bar, not the
     * FAB, and the full 72 dp app bar with its subtitle.
     */
    @Test
    fun emptyListShowsTheBar() {
        val rig = ListRig(ListFixture.longAisle())
        compose.show(rig)
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 120 * dp, endY = top + 10 * dp, durationMillis = 500) }
        compose.waitForIdle()
        assertTrue("scrolled down: the FAB", compose.exists(ADD_FAB_TAG))
        rig.scope.launch {
            rig.store.update { s -> s.copy(lists = s.lists.map { if (it.id == ListFixture.LIST_ID) it.copy(items = emptyList(), trip = null) else it }) }
        }
        compose.waitForIdle()
        assertTrue("the empty state", compose.exists(ListTags.EMPTY))
        assertTrue("with the add bar docked", compose.exists(ADD_BAR_TAG))
        assertFalse("not the FAB", compose.exists(ADD_FAB_TAG))
        assertEquals("the full app bar", 72f, compose.boundsOf(ListTags.TOP_STACK).height / dp, 1f)
        compose.onNodeWithText(ListFixture.ui().storeName, substring = true).assertExists()
    }

    /** A quick add's answer: one named thing for Household (§6.2 rule 4). */
    private fun ListRig.answers(name: String, icon: String, aisle: String = "household") {
        assistant.ingest = { _, _ ->
            earth.cora.picnic.ui.common.FakeAssistant.result(
                listOf(earth.cora.picnic.core.assistant.ProposedItem(name, "1 pack", null, aisle, icon, earth.cora.picnic.core.model.IconFit.EXACT, named = true)),
            )
        }
    }

    private fun send(text: String) {
        compose.onNodeWithTag(ADD_FIELD_TAG).performClick()
        compose.onNodeWithTag(ADD_FIELD_TAG).performTextInput(text)
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitForIdle()
    }

    private fun fieldFocused(): Boolean =
        compose.onNodeWithTag(ADD_FIELD_TAG).fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }

    /**
     * Item 116, D11: once a send has committed, the field lets go (and the keyboard goes down), so the bar folds into the
     * FAB as she scrolls down the aisles, as the collapse rules say.
     */
    @Test
    fun afterSendTheBarFolds() {
        val rig = ListRig(ListFixture.longAisle())
        // Into Produce, on screen at the top: the list has no reason to move (whose scroll the bar would ignore).
        rig.answers("Lemons", "lemon", aisle = "produce")
        compose.show(rig)
        send("lemons")
        assertTrue("committed", rig.app.items().any { it.name == "Lemons" })
        assertFalse("the field let go after the send", fieldFocused())
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 120 * dp, endY = top + 10 * dp, durationMillis = 500) }
        compose.waitForIdle()
        assertTrue("scrolled down past 140 dp: the FAB", compose.exists(ADD_FAB_TAG))
    }

    /**
     * §6.2: a quick add into a folded aisle below the add bar brings that aisle's strip into view (the add bar ignores
     * the scroll), so the new row's place is seen; nothing moves when it already shows.
     */
    @Test
    fun commitBringsTheNewRowIntoView() {
        val rig = ListRig()
        rig.answers("Rubber gloves", "dish_soap")
        compose.show(rig)
        val dockTop = compose.boundsOf(ADD_BAR_TAG).top
        assertTrue("Household starts below the add bar", !compose.exists(ListTags.strip("household")) || compose.boundsOf(ListTags.strip("household")).bottom > dockTop)
        send("rubber gloves")
        compose.waitForIdle()
        val strip = compose.boundsOf(ListTags.strip("household"))
        assertTrue("Household's strip shows above the add bar ($strip vs $dockTop)", strip.bottom <= compose.boundsOf(ADD_BAR_TAG).top + 1f && strip.top >= compose.boundsOf(ListTags.LIST).top - 1f)
        assertTrue("the add bar stays docked", compose.exists(ADD_BAR_TAG) && !compose.exists(ADD_FAB_TAG))
        // A second add into Household, now on screen: the list stays where it is.
        rig.answers("Sponges", "sponge")
        send("sponges")
        compose.waitForIdle()
        assertEquals("nothing moved", strip.top, compose.boundsOf(ListTags.strip("household")).top, 1f)
    }

    /**
     * Trip review (item 337, §6.1): at font scale 2.0 with the bar folded into the FAB and the list at its end, the last
     * row's check spot (and its GOT IT hanko) and the end note stand clear of the FAB, at least 16 dp above it: the
     * list's bottom padding is the FAB's dock + 26 dp while it shows.
     */
    @Test
    fun theListEndsClearOfTheFab() {
        val rig = ListRig(ListFixture.longAisle())
        compose.show(rig, fontScale = 2f)
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 120 * dp, endY = top + 10 * dp, durationMillis = 500) }
        compose.waitForIdle()
        assertTrue("scrolled down: the FAB", compose.exists(ADD_FAB_TAG))
        // To the very end (a programmatic scroll leaves the FAB folded).
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("end-note")
        compose.waitForIdle()
        assertTrue("still the FAB", compose.exists(ADD_FAB_TAG))
        val fab = compose.boundsOf(ADD_FAB_TAG)
        val last = rig.vm.state.value.sections.single { it.id == "household" }.rows.last()
        val spot = compose.boundsOf(ListTags.check(last.id), unmerged = true)
        assertTrue("the last row's check spot ${spot.bottom / dp} dp ends 16 dp above the FAB's top ${fab.top / dp} dp", spot.bottom <= fab.top - 16 * dp + 0.5f)
        val note = compose.boundsOf(ListTags.END_NOTE)
        assertTrue("the end note ${note.bottom / dp} dp ends 16 dp above the FAB's top ${fab.top / dp} dp", note.bottom <= fab.top - 16 * dp + 0.5f)
    }
}

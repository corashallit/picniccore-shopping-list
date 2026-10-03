package earth.cora.picnic.ui.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}

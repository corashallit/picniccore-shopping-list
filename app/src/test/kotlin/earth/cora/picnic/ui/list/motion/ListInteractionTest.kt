package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fold's motion side (docs/DESIGN_STICKER.md §8.5; item 193): folding and unfolding take 240 ms and then move focus
 * to the chevron (open) or the strip (folded), through [FoldFocusEffect], on the harness list.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ListInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun foldUnfoldFocus() {
        assertEquals(240L, FoldMotion.DURATION)
        assertEquals(0.2f to 0.6f, FoldMotion.unfold(0))
        assertEquals(1f to 1f, FoldMotion.unfold(240))
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        // Unfold Produce: after the 240 ms fold, focus is on its chevron.
        compose.onNodeWithTag(HarnessTags.strip("produce")).performClick()
        rig.frame()
        compose.onNodeWithTag(HarnessTags.chevron("produce")).assertIsNotFocused()
        rig.advance(Beats.RM_FADE)
        compose.onNodeWithTag(HarnessTags.chevron("produce")).assertIsNotFocused()
        rig.advance(240)
        compose.onNodeWithTag(HarnessTags.chevron("produce")).assertIsFocused()
        // Fold it: after 240 ms, focus is on the folded strip.
        compose.onNodeWithTag(HarnessTags.chevron("produce")).performClick()
        rig.advance(260)
        compose.onNodeWithTag(HarnessTags.strip("produce")).assertIsFocused()
    }
}

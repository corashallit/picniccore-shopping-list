package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertTrue
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

    /**
     * §15 Focus ("the Next card and celebration never steal focus"), item 193: the folds and opens an aisle-done chain
     * makes by itself (the finished tray folding at L + 1100, the next stop opening at L + 1300) never move focus; only
     * her own toggles do. Focus sits on the Produce strip and stays there on every frame of the chain.
     */
    @Test
    fun chainNeverMovesFocus() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        compose.onNodeWithTag(HarnessTags.strip("produce")).performSemanticsAction(SemanticsActions.RequestFocus)
        rig.frame()
        compose.onNodeWithTag(HarnessTags.strip("produce")).assertIsFocused()
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        rig.sampleTo(L + 2200) { now ->
            val focused = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).fetchSemanticsNodes()
            assertTrue(
                "focus moved at L + ${now - L}: ${focused.map { it.config.getOrNull(SemanticsProperties.TestTag) }}",
                // On the Produce strip (or nowhere, if the list's own scroll took that item away), never on another node.
                focused.all { it.config.getOrNull(SemanticsProperties.TestTag) == HarnessTags.strip("produce") },
            )
        }
        assertEquals("breakfast", rig.p.openSection(rig.model.grouped().openSectionId))
        // Her own toggle still hands focus off: she taps Breakfast's chevron to fold it; focus goes to its strip.
        compose.onNodeWithTag(HarnessTags.chevron("breakfast")).performClick()
        rig.advance(260)
        compose.onNodeWithTag(HarnessTags.strip("breakfast")).assertIsFocused()
    }
}

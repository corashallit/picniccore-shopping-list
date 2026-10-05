package earth.cora.picnic.platform

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checklist 279, mechanism (docs/DESIGN_STICKER.md §14.3): the bonus-stage jingle repeats every 4 s while the
 * list is resumed, stops on leaving and starts again on return. Driven by the Compose test clock.
 */
@RunWith(AndroidJUnit4::class)
class RepeatingCueTest {
    @get:Rule
    val rule = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun bonusJingleEveryFourSecondsWhileResumed() {
        val feedback = FakeFeedback()
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var active by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RepeatingCue(feedback, Cue.BONUS_JINGLE, BONUS_JINGLE_PERIOD_MS, active)
            }
        }
        fun jingles() = feedback.count(Cue.BONUS_JINGLE)

        rule.mainClock.advanceTimeBy(100)
        assertEquals("at once", 1, jingles())
        rule.mainClock.advanceTimeBy(3800) // t ≈ 3.9 s
        assertEquals(1, jingles())
        rule.mainClock.advanceTimeBy(200) // t ≈ 4.1 s
        assertEquals(2, jingles())
        rule.mainClock.advanceTimeBy(8000) // t ≈ 12.1 s
        assertEquals(4, jingles())

        // Leaving the list (paused): silence, however long.
        owner.registry.currentState = Lifecycle.State.STARTED
        rule.mainClock.advanceTimeBy(100)
        rule.mainClock.advanceTimeBy(20_000)
        assertEquals(4, jingles())

        // Back: the jingle plays at once and keeps its rhythm.
        owner.registry.currentState = Lifecycle.State.RESUMED
        rule.mainClock.advanceTimeBy(100)
        assertEquals(5, jingles())
        rule.mainClock.advanceTimeBy(4000)
        assertEquals(6, jingles())

        // The bonus stage ends.
        // (A state write from the test thread reaches the composition once its snapshot is applied.)
        Snapshot.withMutableSnapshot { active = false }
        rule.mainClock.advanceTimeBy(100)
        rule.mainClock.advanceTimeBy(20_000)
        assertEquals(6, jingles())
        assertEquals(listOf(Cue.BONUS_JINGLE), feedback.cues.distinct())
    }

    @Test
    fun stopsWhenTheScreenLeavesTheComposition() {
        val feedback = FakeFeedback()
        var shown by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            if (shown) RepeatingCue(feedback, Cue.BONUS_JINGLE, BONUS_JINGLE_PERIOD_MS)
        }
        rule.mainClock.advanceTimeBy(4100)
        assertEquals(2, feedback.count(Cue.BONUS_JINGLE))
        Snapshot.withMutableSnapshot { shown = false }
        rule.mainClock.advanceTimeBy(100)
        rule.mainClock.advanceTimeBy(20_000)
        assertEquals(2, feedback.count(Cue.BONUS_JINGLE))
    }
}

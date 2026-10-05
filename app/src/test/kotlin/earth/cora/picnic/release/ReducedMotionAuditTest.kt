package earth.cora.picnic.release

import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 197 (docs/DESIGN_STICKER.md §8.8, §15): under reduced motion no infinite animation runs on any screen fixture of
 * the walker, Day and Glow: once the fixture's own moment has played out (an entrance, a toast's or the Next card's
 * hold, the celebration's summary: every one finite, a 120 ms fade in place), the test clock idles. The clock is held
 * from before the screen is composed (so no loop is stopped by the test framework's own infinite-animation policy, which
 * only steps in on an auto-advancing clock), the fixture is left for [SETTLE_MS], and then looked at every fourth frame for
 * [WATCH_MS], longer than the slowest loop's period (the 9 s ambient sparkle, §8.5): no look may find anything
 * waiting for the next one (a frame awaiter: an Animatable, a transition, a `withFrameNanos` loop, a stepped pose).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ReducedMotionAuditTest(private val fixture: Fixture) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun day() = audit(Combo(reduced = true))

    @Test
    fun glow() = audit(Combo(variant = ThemeVariant.Glow, reduced = true))

    private fun audit(combo: Combo) {
        val stage = FixtureStage(compose, combo)
        try {
            fixture.run(stage)
            // The fixture's own moment plays out (the longest, the celebration's summary hold, ends well within).
            var t = 0L
            while (t < SETTLE_MS) {
                compose.mainClock.advanceTimeBy(500)
                compose.waitForIdle()
                Snapshot.sendApplyNotifications()
                t += 500
            }
            // Looked at every fourth frame: a running animation waits for every frame it runs, so a loop shows at each
            // look; a burst of a frame or two between looks would be the only thing to slip by.
            val start = compose.mainClock.currentTime
            val busy = ArrayList<Long>()
            while (compose.mainClock.currentTime - start < WATCH_MS) {
                compose.mainClock.advanceTimeBy(LOOK_MS)
                compose.waitForIdle()
                Snapshot.sendApplyNotifications()
                if (awaitingAFrame()) busy += compose.mainClock.currentTime - start
            }
            assertTrue(
                "${fixture.name} [$combo]: something still animates under reduced motion: ${busy.size} of the watch's frames wait for " +
                    "the next one (at ${busy.take(6)}${if (busy.size > 6) "…" else ""} ms of $WATCH_MS ms)",
                busy.isEmpty(),
            )
        } finally {
            stage.close()
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ScreenFixtures.params()

        /** What the fixtures' finite moments take at most under reduced motion (the celebration's summary holds longest). */
        const val SETTLE_MS = 20_000L

        /** Longer than the slowest loop's period (the ambient sparkle, every 9 s, §8.5). */
        const val WATCH_MS = 10_000L

        /** Four frames between looks. */
        const val LOOK_MS = 64L

        /** Whether a composition waits for the next frame (Compose's recomposers: pending recomposition or frame awaiters). */
        fun awaitingAFrame(): Boolean = Recomposer.runningRecomposers.value.any { it.hasPendingWork }
    }
}

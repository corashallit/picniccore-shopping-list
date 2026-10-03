package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.theme.TOAST_TAG
import earth.cora.picnic.ui.theme.ToastMessage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Android's animation scales never move a beat or shorten a hold (docs/DESIGN_STICKER.md §8.8; item 196): with
 * MotionDurationScale injected at 0, 0.5 and 2, the check-off beats keep their times and the Next card, the toast and
 * the celebration summary stay visible for their full holds.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class MotionScaleTest(private val scale: Float) {
    @get:Rule
    val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float = scale
    })

    @Test
    fun checkOffBeatsAndNextCardHold() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        rig.advanceTo(L + 1300 + 400 + 2600 + 400)
        assertBeat("land ×$scale", t0 + 620, rig.feedback.times(Cue.LAND).firstOrNull())
        assertBeat("magic ×$scale", t0 + 940, rig.feedback.times(Cue.MAGIC_APPEAR).firstOrNull())
        assertBeat("stamp ×$scale", L + 500, rig.feedback.times(Cue.STAMP).firstOrNull())
        assertBeat("card ×$scale", L + 1300, rig.feedback.times(Cue.NEXT_CARD).firstOrNull())
        val still = rig.probe.stillFrom(NEXT_CARD_TAG, L + 1700)
        assertTrue("card still $still ms ×$scale", still >= 2600)
    }

    @Test
    fun toastHoldsItsTime() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        compose.runOnIdle { rig.toasts.show(ToastMessage("Added 5 items to Saturday shop ♡")) }
        val shown = rig.now
        var appeared: Long? = null
        var gone: Long? = null
        rig.sampleTo(shown + 6000) {
            val on = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(TOAST_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            if (on && appeared == null) appeared = it
            if (!on && appeared != null && gone == null) gone = it
        }
        assertTrue("the toast showed ×$scale", appeared != null && appeared!! - shown <= 32)
        // Drop 220 + hold 4000 + lift 260 on the frame clock, whatever the scale.
        val visible = (gone ?: Long.MAX_VALUE) - shown
        assertTrue("toast visible $visible ms ×$scale", visible >= 220 + 4000)
    }

    @Test
    fun celebrationSummaryHolds() {
        val rig = HarnessRig(compose, MotionFixture.allBut(listOf("toothpaste"), open = "household")).start()
        val t0 = rig.tap("toothpaste")
        val s = t0 + 620 + Beats.CELEBRATION
        rig.advanceTo(s + 17000)
        for (tag in listOf(earth.cora.picnic.ui.theme.CelebrationTags.TITLE, earth.cora.picnic.ui.theme.CelebrationTags.BANNER, earth.cora.picnic.ui.theme.CelebrationTags.BONUS)) {
            val still = rig.probe.stillFrom(tag, s + 12150)
            assertTrue("$tag summary still $still ms ×$scale", still >= 4800)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "scale {0}")
        fun scales(): List<Array<Any>> = listOf(0f, 0.5f, 2f).map { arrayOf<Any>(it) }
    }
}

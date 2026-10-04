package earth.cora.picnic.release

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.ListTags
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 198 (docs/DESIGN_STICKER.md §8.8, §15 "No flashing"): nothing flashes or blinks faster than 3 Hz. The motion
 * spec's own blinks are under it (the REC dot 0.83 Hz, the LCD star 0.25 Hz, the shiny glint, the Glow stars' charge;
 * sparkles are shape changes, but their cycle is under it too), and the strips the app really draws flash at most three
 * times in any second (FlashMeter, WCAG 2.3.1's count): the celebration from the last check-off through its summary
 * (one white flash), the List at rest with its blinks, glints and ambient loops, the Speak tab recording, the album with
 * its holo glints, Day and Glow. The eggs' moments are NoFlashEggsTest's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class NoFlashTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun motionSpecBlinksAreSlowerThan3Hz() {
        val periodic = mapOf(
            "the LCD star" to PicnicMotion.lcdBlinkPeriod,
            "the REC dot" to PicnicMotion.recBlinkPeriod,
            "the shiny glint" to PicnicMotion.shinyGlintPeriod,
            "the Glow stars' charge" to PicnicMotion.glowChargePeriod,
            "the washi flutter" to PicnicMotion.washiFlutterPeriod,
            "a sparkle's three poses" to PicnicMotion.sparkle.durationMillis.toLong(),
        )
        for ((what, period) in periodic) {
            assertTrue("$what: once every $period ms is ${"%.2f".format(1000f / period)} Hz", 1000.0 / period < 3.0)
        }
        // §4.3, §4.20, §15: the two blinks the spec names, at the rates it names.
        assertEquals("the LCD star at 0.25 Hz", 0.25, 1000.0 / PicnicMotion.lcdBlinkPeriod, 0.001)
        assertEquals("the REC dot at 0.83 Hz", 0.83, 1000.0 / PicnicMotion.recBlinkPeriod, 0.01)
        // The star's dip is one change down and one back each period: well apart.
        assertTrue("the LCD star's dip is short of its period", PicnicMotion.lcdBlinkOn * 2 < PicnicMotion.lcdBlinkPeriod)
    }

    @Test
    fun theCelebrationFlashesOnceDay() = celebration(ThemeVariant.Day)

    @Test
    fun theCelebrationFlashesOnceGlow() = celebration(ThemeVariant.Glow)

    @Test
    fun theListAtRestDay() = atRest("list_midtrip", Combo(), 9_500)

    @Test
    fun theListAtRestGlow() = atRest("list_midtrip", Combo(variant = ThemeVariant.Glow), 9_500)

    @Test
    fun recording() = atRest("add_speak_recording", Combo(), 3_600)

    @Test
    fun theAlbum() = atRest("album", Combo(), 7_000)

    /** The last check-off, the flash, the iris, the confetti and the ride, through the summary. */
    private fun celebration(variant: ThemeVariant) {
        val stage = FixtureStage(compose, Combo(variant = variant))
        try {
            stage.showList(ScreenFixtures.listRig(ListFixture.allBut(listOf("toothpaste"), open = "household")))
            stage.settle()
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey("toothpaste")
            stage.frames(4)
            val meter = FlashMeter(compose)
            compose.onNodeWithTag(ListTags.row("toothpaste")).performClick()
            assertTrue("the celebration starts", stage.framesUntil(2600) { stage.exists(CELEBRATION_TAG) })
            watch(stage, meter, 9_000)
            check("the celebration [$variant]", meter)
        } finally {
            stage.close()
        }
    }

    private fun atRest(name: String, combo: Combo, ms: Long) {
        val stage = FixtureStage(compose, combo)
        try {
            ScreenFixtures.named(name).single().run(stage)
            val meter = FlashMeter(compose)
            watch(stage, meter, ms)
            check("$name [$combo]", meter)
        } finally {
            stage.close()
        }
    }

    companion object {
        /** Samples every third frame (20 a second: a 3 Hz flash is near seven samples) for [ms]. */
        fun watch(stage: FixtureStage, meter: FlashMeter, ms: Long) {
            val start = stage.compose.mainClock.currentTime
            while (stage.compose.mainClock.currentTime - start < ms) {
                meter.sample(stage.compose.mainClock.currentTime - start)
                stage.frames(3)
            }
        }

        fun check(what: String, meter: FlashMeter) {
            assertTrue("$what: sampled (${meter.samples})", meter.samples > 20)
            val worst = meter.worst()
            assertTrue("$what flashes faster than 3 Hz: $worst", worst.changes <= FlashMeter.MAX_CHANGES)
        }
    }
}

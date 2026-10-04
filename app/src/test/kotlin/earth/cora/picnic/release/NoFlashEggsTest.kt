package earth.cora.picnic.release

import androidx.compose.ui.test.junit4.createComposeRule
import earth.cora.picnic.ui.eggs.EggRig
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 198 for the eggs (docs/DESIGN_STICKER.md §12, §15 "No flashing"): each of the 18, fired as the Surprise lab
 * fires it on the real List, flashes at most three times in any second through its moment (FlashMeter): the petals,
 * the confetti, the kirakira twinkles, the jackpot, the bonus stage, the Dusk and Golden washes, the receipt printing.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class NoFlashEggsTest(private val egg: Int) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun day() = watch(ThemeVariant.Day)

    private fun watch(variant: ThemeVariant) {
        val rig = EggRig(compose, variant = variant).start()
        rig.advance(600)
        val meter = FlashMeter(compose)
        val t0 = rig.fire(egg)
        while (rig.now - t0 < WATCH_MS) {
            meter.sample(rig.now - t0)
            rig.frame()
            rig.frame()
            rig.frame()
        }
        assertTrue("egg $egg: sampled (${meter.samples})", meter.samples > 20)
        val worst = meter.worst()
        assertTrue("egg $egg flashes faster than 3 Hz: $worst", worst.changes <= FlashMeter.MAX_CHANGES)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "egg {0}")
        fun params(): List<Array<Any>> = (1..18).map { arrayOf<Any>(it) }

        /** An egg's moment, start to rest (the longest, the rain and the bonus stage, settle within it). */
        const val WATCH_MS = 5_000L
    }
}

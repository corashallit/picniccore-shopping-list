package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.ui.about.SurpriseLabScreen
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.LocalTimeouts
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 261, table driven over the 18 eggs (§12.3 "Reduced motion"): with reduced motion on, the four pure-motion eggs (2,
 * 3, 4, 17) do not run (egg 3's item is still added); the rest apply in place: Dusk fades in, midnight applies, the shiny
 * row is holo without a burst, the title is holo without twinkles, the cat is curled still, no confetti but HAPPY / B-DAY!,
 * the pixel look, the season, Golden, the jackpot fades, the perfect week, the bee sits still with FED, the umbrella
 * fades in, the receipt fades in printed. Sounds are not silenced by reduced motion (§14.3).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggReducedMotionTest(private val egg: Int) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun asSection12Point3Says() {
        val r = EggRig(compose, reduced = true).start()
        r.probe.clear()
        val t0 = r.fire(egg)
        val e = { r.eggs }
        when (egg) {
            1 -> {
                assertEquals(true, e().duskFx?.reduced)
                r.frame()
                assertEquals("the moon and the Dusk palette at once (a 120 ms fade)", ThemeVariant.Dusk, r.resolved)
                r.advance(200)
                assertNull(e().duskFx)
                assertTrue(e().dusk)
            }
            2 -> {
                r.advance(600)
                assertNull("pure motion: does not run", e().ladybug)
            }
            3 -> {
                assertNull("pure motion: does not run", e().rolling)
                assertTrue("the item is added normally", r.list.items.any { it.name == "Strawberries" })
            }
            4 -> {
                assertNull("pure motion: does not run", e().petalsId)
                assertEquals(0, r.feedback.count(Cue.PETAL_SHAKE))
            }
            5 -> {
                assertTrue(e().midnight)
                assertEquals(ThemeVariant.Glow, r.resolved)
                assertEquals("frozen", e().snackSectionId)
                assertEquals(EggRules.SNACK, e().lcdWord?.text)
            }
            6 -> {
                assertEquals(Rarity.SHINY, r.list.items.last { it.name == "Maple syrup" }.rarity)
                assertTrue("holo, static: no burst", e().shinyBursts.isEmpty())
                r.advance(400)
                assertTrue(r.probe.frames.none { it.tag.startsWith("shiny") })
            }
            7 -> {
                assertTrue("the title is holo (static) for its 5 s", e().kirakira)
                r.advanceTo(t0 + EggRules.KIRAKIRA_MS + 200)
                assertFalse(e().kirakira)
            }
            8 -> {
                r.advance(1_000)
                assertEquals("frozen", e().napSectionId)
                val curled = r.probe.frames.filter { it.tag == "nap" && it.time >= 200 }
                assertTrue(curled.isNotEmpty())
                assertTrue("curled, still", curled.map { it.bounds }.distinct().size == 1)
            }
            9 -> {
                assertNull("no confetti", e().confettiId)
                r.advanceTo(t0 + 3_400)
                val cake = r.list.items.single { it.name == "Birthday cake" }
                assertTrue(cake.checked)
                assertEquals(HankoText.HappyBday, e().hankoFor(cake))
            }
            10 -> {
                assertTrue("the pixel look applies", e().bonusStage)
                assertTrue(e().pixelStickers)
            }
            11 -> assertEquals(Season.HALLOWEEN, e().season)
            12 -> {
                r.advance(300)
                assertEquals(ThemeVariant.Golden, r.resolved)
            }
            13 -> {
                r.advanceTo(t0 + 1_300)
                assertEquals("the gold capsule fades to the sticker; JACK / POT! fades in", true, e().jackpot?.reduced)
                r.advanceTo(t0 + 1_300 + JackpotMotion.DURATION)
                assertNull(e().jackpot)
                assertEquals(Rarity.JACKPOT, r.list.items.single { it.id == "baking-powder" }.rarity)
            }
            14 -> {
                r.advance(300)
                assertTrue(e().perfectWeek)
            }
            15 -> {
                assertTrue(e().pet)
                assertEquals("FED shows", EggRules.FED, e().lcdWord?.text)
            }
            16 -> {
                assertTrue(e().umbrella)
                r.advance(300)
                val umbrella = r.probe.frames.filter { it.tag == "umbrella" }
                assertTrue("it fades in: no turn", umbrella.all { it.rotation == 0f })
            }
            17 -> {
                r.advance(600)
                assertNull("pure motion: does not run", e().antsId)
            }
            18 -> {
                assertEquals("it fades in printed", true, e().receipt?.reduced)
                r.advance(ReceiptMotion.FADE_MS + 50)
                assertNotNull(e().receipt)
                assertEquals(0, r.feedback.count(Cue.RECEIPT_TEAR))
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "egg {0}")
        fun params(): List<Array<Any>> = (1..18).map { arrayOf<Any>(it) }
    }
}

/** Item 261, the lab's half: under reduced motion the four pure-motion eggs say "needs animations on" instead of Try it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggReducedMotionLabTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fourTilesNeedAnimationsOn() {
        val lab = previewLab()
        compose.setContent {
            CompositionLocalProvider(LocalTimeouts provides FakeTimeouts()) {
                PicnicTheme(variant = ThemeVariant.Day, reducedMotion = true) { SurpriseLabScreen(onBack = {}, lab = lab) }
            }
        }
        compose.waitForIdle()
        assertEquals(4, compose.onAllNodesWithText("needs animations on").fetchSemanticsNodes().size)
        assertEquals(14, compose.onAllNodesWithText("Try it").fetchSemanticsNodes().size)
    }
}

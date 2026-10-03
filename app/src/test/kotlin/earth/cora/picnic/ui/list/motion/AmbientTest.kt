package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.random.Random

/** The ambient loops (docs/DESIGN_STICKER.md §8.5; item 194): only while visible, never under reduced motion, white sweep. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class AmbientTest {
    @get:Rule
    val compose = createComposeRule()

    private val baskets = listOf(AmbientBasket("produce", listOf("bananas", "carrots")), AmbientBasket("bakery", listOf("croissants")))
    private val stickers = listOf("strawberries", "apples")

    @Test
    fun runsOnlyWhileVisibleAndNeverReduced() {
        compose.mainClock.autoAdvance = false
        val director = AmbientDirector(Random(3))
        var visible by mutableStateOf(true)
        var reduced by mutableStateOf(false)
        var shown: AmbientPresentation? = null
        compose.setContent {
            val state = rememberAmbient(visible, reduced, { baskets }, { stickers }, director, frame = { withFrameMillis { it } }).state
            shown = state.value
        }
        compose.mainClock.advanceTimeBy(5200)
        compose.waitForIdle()
        // Every 5 s a folded basket wobbles with a mini peeking.
        assertTrue("a wobble by 5 s: $shown", shown?.wobbleSection in baskets.map { it.sectionId })
        assertTrue(shown?.peekItem != null)
        compose.mainClock.advanceTimeBy(4000)
        compose.waitForIdle()
        // Every 9 s a sticker gets the sweep.
        assertTrue("a sweep by 9 s: $shown", shown?.sweepItem in stickers)
        val beats = director.beats
        // Not visible: it stops at once and nothing more plays.
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeBy(20_000)
        compose.waitForIdle()
        assertNull(shown)
        assertEquals(beats, director.beats)
        // Visible again, but under reduced motion: still nothing.
        compose.runOnIdle {
            visible = true
            reduced = true
        }
        compose.mainClock.advanceTimeBy(20_000)
        compose.waitForIdle()
        assertNull(shown)
        assertEquals(beats, director.beats)
        // Reduced motion off: it runs again.
        compose.runOnIdle { reduced = false }
        compose.mainClock.advanceTimeBy(5200)
        compose.waitForIdle()
        assertTrue(director.beats > beats)
    }

    /**
     * Item 194 in the app's own pieces: with the engine idle (its clock never ticks), the director's beats still play on
     * a basket and a sticker, because they read the director's clock: the peeking mini and the wobble move across the
     * beat's frames, the gloss band sweeps across the sticker, and both rest again afterwards.
     */
    @Test
    fun beatsPlayWhileTheEngineIsIdle() {
        compose.mainClock.autoAdvance = false
        val director = AmbientDirector(Random(3))
        val engineClock = MotionClock() // the engine's: idle, never ticks
        compose.setContent {
            earth.cora.picnic.ui.theme.PicnicTheme {
                val ambient = rememberAmbient(true, false, { listOf(AmbientBasket("produce", listOf("m1", "m2"))) }, { listOf("s1") }, director, frame = { withFrameMillis { it } })
                val beat = ambient.state.value
                androidx.compose.foundation.layout.Column {
                    MotionBasket(
                        kind = earth.cora.picnic.core.model.BasketKind.WICKER_PICNIC,
                        look = earth.cora.picnic.ui.theme.AisleLooks.of(1),
                        gotten = listOf(BasketMini("m1", earth.cora.picnic.core.model.IconRef("banana")), BasketMini("m2", earth.cora.picnic.core.model.IconRef("apple"))),
                        toGet = 2,
                        complete = false,
                        presentation = null,
                        clock = engineClock,
                        modifier = androidx.compose.ui.Modifier.testTag("basket"),
                        ambientPeekItem = beat?.peekItem,
                        ambientT0 = beat?.wobbleT0?.takeIf { beat.wobbleSection == "produce" },
                        ambientClock = ambient.clock,
                    )
                    earth.cora.picnic.ui.theme.ItemIcon(
                        earth.cora.picnic.core.model.IconRef("strawberry"), null,
                        androidx.compose.ui.Modifier.testTag("sticker").glossSweep(beat?.sweepT0?.takeIf { beat.sweepItem == "s1" }, ambient.clock),
                        size = 64.dp,
                    )
                }
            }
        }
        fun shot(tag: String) = compose.onNodeWithTag(tag).captureToImage().toPixelMap().let { m -> IntArray(m.width * m.height) { i -> m[i % m.width, i / m.width].hashCode() } }
        fun differs(a: IntArray, b: IntArray) = a.indices.count { a[it] != b[it] }
        val restBasket = shot("basket")
        val restSticker = shot("sticker")
        fun until(cond: () -> Boolean) {
            var n = 0
            while (!cond()) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                check(++n < 1000) { "never happened" }
            }
        }
        // The wobble and peek at 5 s.
        compose.mainClock.advanceTimeBy(4900)
        compose.waitForIdle()
        until { director.state.value?.wobbleT0 != null }
        val wobble = director.state.value!!
        assertEquals("produce", wobble.wobbleSection)
        assertEquals(0L, engineClock.now)
        val clock0 = director.clock.now
        compose.mainClock.advanceTimeBy(40)
        compose.waitForIdle()
        val early = shot("basket")
        compose.mainClock.advanceTimeBy(150)
        compose.waitForIdle()
        val mid = shot("basket")
        assertTrue("the director's clock runs while the beat plays", director.clock.now > clock0)
        assertTrue("the basket moves across the beat", differs(early, mid) > 50)
        assertTrue("and away from rest", differs(restBasket, early) > 50)
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        assertTrue("it rests again after the beat", differs(restBasket, shot("basket")) == 0)
        // The gloss sweep at 9 s: the white band crosses the sticker in 6 steps over 900 ms.
        until { director.state.value?.sweepT0 != null }
        assertEquals("s1", director.state.value?.sweepItem)
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        val sweepA = shot("sticker")
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val sweepB = shot("sticker")
        assertTrue("the band shows", differs(restSticker, sweepA) > 20)
        assertTrue("and moves", differs(sweepA, sweepB) > 20)
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        assertTrue("gone after 900 ms", differs(restSticker, shot("sticker")) == 0)
        assertEquals("the engine never ticked", 0L, engineClock.now)
    }

    /** Item 194's gate (§8.5): the loops run only while the screen is resumed and at least 10 % of the list shows. */
    @Test
    fun runsOnlyWhileTenPercentVisible() {
        assertTrue(ambientVisible(resumed = true, visibleFraction = 0.1f))
        assertTrue(ambientVisible(resumed = true, visibleFraction = 1f))
        assertFalse(ambientVisible(resumed = true, visibleFraction = 0.09f))
        assertFalse(ambientVisible(resumed = false, visibleFraction = 1f))
        compose.mainClock.autoAdvance = false
        var fraction by mutableStateOf(-1f)
        var offset by mutableStateOf(0)
        val director = AmbientDirector(Random(3))
        compose.setContent {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                // A 400 dp list pushed down the 760 dp window: all but its top [760 - offset] dp is below the bottom edge.
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(0, offset.dp.roundToPx()) }
                        .size(360.dp, 400.dp)
                        .visibleFraction { fraction = it },
                )
                rememberAmbient(ambientVisible(true, fraction), false, { baskets }, { stickers }, director, frame = { withFrameMillis { it } })
            }
        }
        compose.runOnIdle { offset = 760 - 20 }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertEquals("20 of 400 dp show", 0.05f, fraction, 0.01f)
        compose.mainClock.advanceTimeBy(12_000)
        compose.waitForIdle()
        assertEquals("under 10 % visible: nothing plays", 0, director.beats)
        compose.runOnIdle { offset = 760 - 80 }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertEquals(0.2f, fraction, 0.01f)
        compose.mainClock.advanceTimeBy(5200)
        compose.waitForIdle()
        assertTrue("20 % visible: it plays", director.beats > 0)
    }

    @Test
    fun sweepIsWhite() {
        assertEquals(Color.White, AmbientSweep.color)
        val base = Color(0xFF3366CC)
        val bitmap = ImageBitmap(120, 120)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(120f, 120f)) {
            drawRect(base)
            drawAmbientSweep(0.5f)
        }
        val px = bitmap.toPixelMap()
        // The band crosses the centre: there it is the base mixed 80 % towards white, with no hue of its own.
        val c = px[60, 60]
        val a = AmbientSweep.spec.alpha
        fun mix(ch: Float) = ch + (1f - ch) * a
        assertTrue("$c", abs(c.red - mix(base.red)) < 0.03f && abs(c.green - mix(base.green)) < 0.03f && abs(c.blue - mix(base.blue)) < 0.03f)
        // Outside the band the sticker is untouched.
        assertTrue(abs(px[2, 2].red - base.red) < 0.01f)
    }
}

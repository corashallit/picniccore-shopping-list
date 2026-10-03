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
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
            val state = rememberAmbient(visible, reduced, { baskets }, { stickers }, director, frame = { withFrameMillis { it } })
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

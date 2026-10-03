package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.platform.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 241 (egg 4 on screen): a shake scatters 14 petals over the gutters, settles them for 3 s and blows them away,
 * with one soft tick and "kira" (PETAL_SHAKE); at most once per 5.2 s; only while the list shows; never under reduced
 * motion. The detection itself is ShakeDetectorTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggPetalTest {
    @get:Rule
    val compose = createComposeRule()

    private fun EggRig.shake() = act { engine.onShake() }

    @Test
    fun aShakeScattersPetalsInTheGutters() {
        val r = EggRig(compose).start()
        r.shake()
        val t0 = r.now
        assertNotNull(r.eggs.petalsId)
        assertEquals("one tick and kira", 1, r.feedback.count(Cue.PETAL_SHAKE))
        r.advanceTo(t0 + 1_500)
        val settled = r.probe.frames.filter { it.tag.startsWith("petal") && it.time in 1_400..1_520 }.distinctBy { it.tag }
        assertEquals(PetalMotion.COUNT, settled.size)
        val width = compose.onRootWidth()
        val gutter = 20f * 3f
        settled.forEach { f -> assertTrue("in a gutter: ${f.center}", f.center.x < gutter || f.center.x > width - gutter) }
        // Fully on screen (none clipped by the screen's edge).
        settled.forEach { f -> assertTrue(f.bounds.left >= 0f && f.bounds.right <= width) }
        // Gone after the 3 s rest and the blow-away.
        r.advanceTo(t0 + PetalMotion.TOTAL + 100)
        assertNull(r.eggs.petalsId)
    }

    @Test
    fun atMostOnceEvery5Point2Seconds() {
        val r = EggRig(compose).start()
        r.shake()
        val first = r.eggs.petalsId
        r.advance(2_000)
        r.shake()
        assertEquals("ignored within 5.2 s", first, r.eggs.petalsId)
        assertEquals(1, r.feedback.count(Cue.PETAL_SHAKE))
        r.advance(3_300)
        r.shake()
        assertEquals(2, r.feedback.count(Cue.PETAL_SHAKE))
    }

    @Test
    fun onlyWhileTheListShows() {
        val e = EngineRig()
        try {
            e.engine.onShake()
            assertNull("the list is not showing", e.eggs.petalsId)
            e.engine.onListShown(e.listId)
            e.engine.onShake()
            assertNotNull(e.eggs.petalsId)
        } finally {
            e.close()
        }
    }

    @Test
    fun neverUnderReducedMotion() {
        val r = EggRig(compose, reduced = true).start()
        r.shake()
        r.advance(1_000)
        assertNull(r.eggs.petalsId)
        assertEquals(0, r.feedback.count(Cue.PETAL_SHAKE))
        assertTrue(r.probe.frames.none { it.tag.startsWith("petal") })
    }
}

/** The root's width in px. */
fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRootWidth(): Float =
    onRoot().fetchSemanticsNode().size.width.toFloat()

package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 239 (egg 2): a hold of the threshold + 560 ms (1 s by default, longer with "Touch & hold delay") on a folded
 * basket sends the ladybug along the strip for 5 s, never over text, and it keeps walking after release; a hold of
 * just the threshold does not.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggLadybugTest {
    @get:Rule
    val compose = createComposeRule()

    /** A real press on [sectionId]'s folded basket, held [ms] on the test clock. */
    private fun EggRig.holdBasket(sectionId: String, ms: Long) = holdAt(anchor(AnchorRegistry.basketKey(sectionId)).center, ms)

    @Test
    fun aOneSecondHoldSendsTheLadybug() {
        val r = EggRig(compose).start()
        r.holdBasket("produce", 1_040)
        val fx = r.eggs.ladybug
        assertNotNull(fx)
        assertEquals("produce", fx!!.sectionId)
        val t0 = r.now
        r.probe.clear()
        // It keeps walking after the release, never over text, and is gone after its 5 s walk and 600 ms flight.
        r.advanceTo(t0 + LadybugMotion.WALK_MS)
        val text = keepOutRects(r.anchors)
        val steps = r.probe.frames.filter { it.tag == "ladybug" }
        assertTrue("it walked", steps.map { it.center.x }.distinct().size >= 6)
        steps.forEach { f -> assertTrue("over text at ${f.time} ms", text.none { it.overlaps(f.bounds.deflate(2f)) }) }
        // Along the strip's bottom edge (its centre 2 dp below it, its feet on the binder).
        val strip = r.anchor(EggAnchors.strip("produce"))
        steps.forEach { f -> assertEquals(strip.bottom + 2 * 3f, f.center.y, 3f) }
        // Then it flies off the nearer screen edge (the left one here), low along the strip's edge, past no words.
        r.probe.clear()
        r.advanceTo(t0 + LadybugMotion.DURATION - 20)
        val flight = r.probe.frames.filter { it.tag == "ladybug" }.distinctBy { it.time }.sortedBy { it.time }
        assertTrue("it flew", flight.size >= 4)
        flight.zipWithNext { a, b -> assertTrue("towards the left edge", b.center.x <= a.center.x) }
        assertTrue("off the edge (${flight.last().center.x})", flight.last().center.x < 0f)
        val words = textBoxes(compose)
        flight.forEach { f ->
            assertEquals("level with the strip's edge", strip.bottom + 2 * 3f, f.center.y, 3 * 3f)
            words.forEach { (w, box) -> assertFalse("over \"$w\" at ${f.time} ms", box.overlaps(f.bounds.deflate(2f))) }
        }
        r.advanceTo(t0 + LadybugMotion.DURATION + 100)
        assertNull(r.eggs.ladybug)
    }

    @Test
    fun theThresholdAlonePeeksOnly() {
        val r = EggRig(compose).start()
        r.holdBasket("produce", 460)
        r.advance(1_000)
        assertNull(r.eggs.ladybug)
    }

    @Test
    fun touchAndHoldDelayLengthensIt() {
        // "Touch & hold delay" at 1.5 s: the ladybug waits for 1.5 s + 560 ms.
        val r = EggRig(compose, timeouts = FakeTimeouts(holdThresholdMs = 1_500)).start()
        // 1.8 s is past the delay (a hold: it only peeks and opens nothing) but short of the delay + 560 ms.
        r.holdBasket("produce", 1_800)
        assertNull("1.8 s is not enough with a 1.5 s hold delay", r.eggs.ladybug)
        assertEquals("a hold never opens the strip", "aisle-2-baking", r.model.grouped().openSectionId)
        r.holdBasket("produce", 2_100)
        assertNotNull(r.eggs.ladybug)
    }

    @Test
    fun underALongDelayAShortPressIsStillATap() {
        // With "Touch & hold delay" at 1.5 s, a 1.2 s press is a tap: it opens the strip and sends no ladybug.
        val r = EggRig(compose, timeouts = FakeTimeouts(holdThresholdMs = 1_500)).start()
        r.holdBasket("produce", 1_200)
        r.advance(300)
        assertNull(r.eggs.ladybug)
        assertEquals("produce", r.model.grouped().openSectionId)
    }

    @Test
    fun notUnderReducedMotion() {
        val r = EggRig(compose, reduced = true).start()
        r.holdBasket("produce", 1_100)
        assertNull(r.eggs.ladybug)
        assertFalse(r.probe.frames.any { it.tag == "ladybug" })
    }
}

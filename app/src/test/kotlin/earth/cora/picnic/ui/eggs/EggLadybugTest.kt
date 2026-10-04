package earth.cora.picnic.ui.eggs

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
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
        // Along the strip's bottom edge, in the lane just under it (its centre 4.5 dp below the card: the list's own
        // lane, clear of this strip's meta line and the next strip's tab).
        val strip = r.anchor(EggAnchors.strip("produce"))
        steps.forEach { f -> assertEquals(strip.bottom + LANE * 3f, f.center.y, 3f) }
        // Then it flies off the nearer screen edge (the left one here), low along the strip's edge, past no words.
        r.probe.clear()
        r.advanceTo(t0 + LadybugMotion.DURATION - 20)
        val flight = r.probe.frames.filter { it.tag == "ladybug" }.distinctBy { it.time }.sortedBy { it.time }
        assertTrue("it flew", flight.size >= 4)
        flight.zipWithNext { a, b -> assertTrue("towards the left edge", b.center.x <= a.center.x) }
        assertTrue("off the edge (${flight.last().center.x})", flight.last().center.x < 0f)
        val words = textBoxes(compose)
        flight.forEach { f ->
            assertEquals("level with the strip's edge", strip.bottom + LANE * 3f, f.center.y, 3 * 3f)
            words.forEach { (w, box) -> assertFalse("over \"$w\" at ${f.time} ms", box.overlaps(f.bounds.deflate(2f))) }
        }
        r.advanceTo(t0 + LadybugMotion.DURATION + 100)
        assertNull(r.eggs.ladybug)
    }

    /**
     * It walks WITH its strip (§12.2 "never crossing item text"): she scrolls the list mid-walk, and the ladybug
     * stays on the strip's bottom edge, over no word, frame by frame.
     */
    @Test
    fun itFollowsItsStripAsTheListScrolls() {
        val r = EggRig(compose).start()
        r.holdBasket("bakery", 1_040)
        assertNotNull(r.eggs.ladybug)
        val t0 = r.now
        r.advanceTo(t0 + 1_500)
        val before = r.anchor(EggAnchors.strip("bakery"))
        compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.LIST).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 60f * 3) }
        r.probe.clear()
        // Frame by frame while the list scrolls, and after: the ladybug on the strip's bottom edge.
        repeat(30) {
            r.frame()
            val strip = r.anchor(EggAnchors.strip("bakery"))
            r.probe.frames.lastOrNull { it.tag == "ladybug" }?.let { f ->
                assertEquals("on the strip's bottom edge while the list scrolls", strip.bottom + LANE * 3f, f.center.y, 4 * 3f)
            }
        }
        val after = r.anchor(EggAnchors.strip("bakery"))
        assertTrue("the list scrolled (${before.top} → ${after.top})", before.top - after.top >= 40f * 3)
        r.probe.clear()
        var samples = 0
        while (r.now < t0 + LadybugMotion.WALK_MS - 100) {
            r.advance(48)
            val strip = r.anchor(EggAnchors.strip("bakery"))
            val f = r.probe.frames.lastOrNull { it.tag == "ladybug" } ?: continue
            samples++
            assertEquals("on the strip's bottom edge at ${r.now - t0} ms", strip.bottom + LANE * 3f, f.center.y, 4 * 3f)
            val words = wordBoxes(compose, r.anchors)
            words.forEach { (w, box) -> assertFalse("over \"$w\" at ${r.now - t0} ms", box.overlaps(f.bounds.deflate(2f))) }
        }
        assertTrue("it kept walking ($samples)", samples >= 5)
    }

    /** Its strip scrolled away out of the list: the walk ends (nothing walks on where the strip was). */
    @Test
    fun itGoesWithItsStrip() {
        val r = EggRig(compose).start()
        r.holdBasket("bakery", 1_040)
        val fx = r.eggs.ladybug
        assertNotNull(fx)
        r.advance(600)
        // Far down the list: the strip leaves the list's composition.
        compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.LIST).performScrollToIndex(10)
        r.advance(200)
        assertNull("the walk ended with its strip", r.eggs.ladybug)
        // Another hold sends a new one.
        compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.LIST).performScrollToIndex(0)
        r.advance(200)
        r.holdBasket("bakery", 1_040)
        assertNotNull(r.eggs.ladybug)
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

    private companion object {
        /** The ladybug's lane: its centre this far (dp) under its strip's card (StripLadybug's 2 dp + the list's 2.5 dp). */
        const val LANE = 4.5f
    }
}

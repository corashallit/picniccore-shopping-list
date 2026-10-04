package earth.cora.picnic.ui.eggs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.a11y.Decorative
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The page stickers on screen now (§4.1: the bee, the ladybug, the nap cat, the umbrella), by name. */
fun EggRig.pageStickers(): List<String> {
    val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
    val screen = root.boundsInRoot
    val out = ArrayList<String>()
    fun walk(n: SemanticsNode) {
        n.config.getOrNull(PageSticker)?.let { name ->
            val b = n.boundsInRoot
            if (b.width > 0f && b.height > 0f && b.overlaps(screen)) out += name
        }
        n.children.forEach(::walk)
    }
    walk(root)
    return out
}

/** Every decorative node with anything TalkBack would read or act on (§15: decor has no semantics). */
fun EggRig.decorWithSemantics(): List<String> {
    val out = ArrayList<String>()
    fun walk(n: SemanticsNode) {
        if (n.config.getOrNull(Decorative) != null) {
            val c = n.config
            val loud = listOfNotNull(
                c.getOrNull(SemanticsProperties.ContentDescription)?.let { "description $it" },
                c.getOrNull(SemanticsProperties.Text)?.let { "text $it" },
                c.getOrNull(SemanticsProperties.Role)?.let { "role $it" },
                c.getOrNull(SemanticsProperties.StateDescription)?.let { "state $it" },
                c.getOrNull(SemanticsActions.OnClick)?.let { "click" },
                c.getOrNull(SemanticsActions.OnLongClick)?.let { "long click" },
            )
            if (loud.isNotEmpty()) out += "${n.boundsInRoot}: $loud"
        }
        n.children.forEach(::walk)
    }
    walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    return out
}

/**
 * Item 73, the eggs' half (§4.1 decor taxonomy) on WP5's real List screen as the Surprise lab shows it
 * ([EggListUnderTest]) in every egg state (Day; Glow for the plain list,
 * midnight, the nap and the rain): at most 3 page
 * stickers on the screen at any time (the list's standing bee and ladybug, the nap cat, the umbrella), and no decorative
 * node carries anything TalkBack reads. (Transient bursts never over text: EggOverlayBoundsTest. The gutter-only texture
 * is the theme's ground: WP4's GlowStarsTest.)
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class DecorBudgetTest(private val egg: Int, private val variant: ThemeVariant, private val list: EggListUnderTest) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun atMostThreePageStickersAndNoneSpeaks() {
        val r = EggRig(compose, variant = variant).start(list)
        if (egg > 0) r.fire(egg)
        var most = 0
        repeat(16) {
            r.advance(200)
            val on = r.pageStickers()
            most = maxOf(most, on.size)
            assertTrue("egg $egg at ${it * 200} ms: $on", on.size <= 3)
        }
        assertEquals("egg $egg: decor with semantics", emptyList<String>(), r.decorWithSemantics())
        if (egg == 0) assertTrue("the plain list shows its standing pair", most >= 2)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "egg {0} {1} {2}")
        fun params(): List<Array<Any>> = EggListUnderTest.all.flatMap { list ->
            (0..18).map { arrayOf<Any>(it, ThemeVariant.Day, list) } + listOf(0, 5, 8, 16).map { arrayOf<Any>(it, ThemeVariant.Glow, list) }
        }
    }
}

/**
 * Item 73: the list's standing pair is the bee and the ladybug, so an egg can add one page sticker, never two: when the
 * nap cat and the rainy-day umbrella show together, the ladybug steps off for as long as both are there (§4.1).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class DecorBudgetStepOffTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Brings [sectionId]'s open tray's end cap (where the ladybug stands) into the list's room above the add bar, as her
     * thumb would: a few bounded drags (never an open-ended scroll-to-node, which the scenes' own scrolling could fight).
     */
    private fun EggRig.showCap(sectionId: String) {
        val list = compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.LIST)
        val tag = earth.cora.picnic.ui.list.ListTags.cap(sectionId)
        val d = compose.density.density
        repeat(24) {
            val view = list.fetchSemanticsNode().boundsInRoot
            val floor = minOf(view.bottom, anchor(earth.cora.picnic.ui.list.motion.AnchorRegistry.ADD_BAR).top)
            val cap = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            val dy = when {
                cap == null -> 120 * d
                cap.bottom > floor -> cap.bottom - floor + 8 * d
                cap.top < view.top -> cap.top - view.top - 8 * d
                else -> {
                    advance(300)
                    return
                }
            }
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
            advance(100)
        }
        throw AssertionError("$sectionId's end cap never came into the list's room")
    }

    @Test
    fun theLadybugStepsOffForTheCatAndTheUmbrella() {
        val r = EggRig(compose).start()
        r.advance(300)
        assertEquals(setOf("bee", "ladybug"), r.pageStickers().toSet())
        // Every stop but Frozen done: the cat curls up in Frozen's basket; she opens Frozen (the current stop) and looks
        // down to its end cap, the cat's basket on the plate above it.
        r.fire(8)
        r.act { r.model.open("frozen") }
        r.advance(1_500)
        r.showCap("frozen")
        assertEquals(setOf("bee", "ladybug", "nap cat"), r.pageStickers().toSet())
        // Rain: the umbrella is one more, so the ladybug steps off.
        r.fire(16)
        r.advance(600)
        r.showCap("frozen")
        val on = r.pageStickers()
        assertEquals(setOf("bee", "nap cat", "umbrella"), on.toSet())
        assertTrue(on.size <= 3)
        assertFalse("ladybug" in on)
        // The cat trots off: the ladybug comes back.
        r.act { r.engine.onNapTap() }
        r.advance(NapMotion.LEAVE_MS + 300)
        r.showCap("frozen")
        assertEquals(setOf("bee", "ladybug", "umbrella"), r.pageStickers().toSet())
    }

    /**
     * Egg 2 (§12.2 "a ladybug appears"): there is one ladybug on the list, and it is the one out for its stroll. While
     * it walks a strip (and flies off), the end cap's ladybug has stepped off; when the stroll is over it is back. Never
     * two ladybugs at once.
     */
    @Test
    fun oneLadybugAtATime() {
        val r = EggRig(compose).start()
        r.advance(300)
        assertTrue("the end cap's ladybug", "ladybug" in r.pageStickers())
        r.probe.clear()
        r.fire(2)
        val t0 = r.now
        var walked = 0
        while (r.now < t0 + LadybugMotion.DURATION + 400) {
            r.advance(84)
            // One frame, sampled whole: the strolling ladybug drawn in it, and the stickers standing in it.
            r.probe.clear()
            r.frame()
            val strolling = r.probe.frames.any { it.tag == "ladybug" }
            val standing = r.pageStickers().count { it == "ladybug" }
            if (strolling) walked++
            assertTrue("one ladybug at ${r.now - t0} ms (strolling $strolling, standing $standing)", standing + (if (strolling) 1 else 0) <= 1)
        }
        assertTrue("it strolled ($walked samples)", walked > 20)
        r.advance(300)
        assertTrue("the end cap's ladybug is back", "ladybug" in r.pageStickers())
    }
}

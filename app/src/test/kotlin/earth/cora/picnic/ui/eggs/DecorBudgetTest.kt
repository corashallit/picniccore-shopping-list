package earth.cora.picnic.ui.eggs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * Item 73, the eggs' half (§4.1 decor taxonomy) on the practice list in every egg state (Day; Glow for the plain list,
 * midnight, the nap and the rain): at most 3 page
 * stickers on the screen at any time (the list's standing bee and ladybug, the nap cat, the umbrella), and no decorative
 * node carries anything TalkBack reads. (Transient bursts never over text: EggOverlayBoundsTest. The gutter-only texture
 * is the theme's ground: WP4's GlowStarsTest.)
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class DecorBudgetTest(private val egg: Int, private val variant: ThemeVariant) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun atMostThreePageStickersAndNoneSpeaks() {
        val r = EggRig(compose, variant = variant).start()
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
        @ParameterizedRobolectricTestRunner.Parameters(name = "egg {0} {1}")
        fun params(): List<Array<Any>> =
            (0..18).map { arrayOf<Any>(it, ThemeVariant.Day) } + listOf(0, 5, 8, 16).map { arrayOf<Any>(it, ThemeVariant.Glow) }
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

    @Test
    fun theLadybugStepsOffForTheCatAndTheUmbrella() {
        val r = EggRig(compose).start()
        r.advance(300)
        assertEquals(setOf("bee", "ladybug"), r.pageStickers().toSet())
        // Every stop but Frozen done: the cat curls up in Frozen's basket; she opens Frozen (the current stop).
        r.fire(8)
        r.act { r.model.open("frozen") }
        r.advance(1_500)
        assertEquals(setOf("bee", "ladybug", "nap cat"), r.pageStickers().toSet())
        // Rain: the umbrella is one more, so the ladybug steps off.
        r.fire(16)
        r.advance(600)
        val on = r.pageStickers()
        assertEquals(setOf("bee", "nap cat", "umbrella"), on.toSet())
        assertTrue(on.size <= 3)
        assertFalse("ladybug" in on)
        // The cat trots off: the ladybug comes back.
        r.act { r.engine.onNapTap() }
        r.advance(NapMotion.LEAVE_MS + 300)
        assertEquals(setOf("bee", "ladybug", "umbrella"), r.pageStickers().toSet())
    }
}

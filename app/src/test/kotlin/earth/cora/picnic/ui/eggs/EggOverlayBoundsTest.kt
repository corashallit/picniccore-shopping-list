package earth.cora.picnic.ui.eggs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 259 (§4.1, §12.1): egg overlays never cover text. Every overlay piece reports where it is drawn on every frame
 * (EggProbe). Pieces drawn as they are (the ladybug, the nap cat, JACK / POT!, the umbrella) never overlap a text node;
 * the bursts (the Dusk sparkles, petals, ants, confetti, the shiny row's burst) and the produce rolling across the page
 * are drawn with every text box cut out, so wherever one crosses a text node that text must be one of the boxes they
 * cut out (they pass behind the words). Sampled over each egg's whole run on the practice list, Day and Glow.
 *
 * The printed receipt is not decoration but an interactive card with its own words, which waits over the list until it
 * is tapped (§13.2), as the Next card and the toasts do; it is checked only against the title.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggOverlayBoundsTest(private val egg: Int, private val variant: ThemeVariant) {
    @get:Rule
    val compose = createComposeRule()

    /** How long each egg's overlays play (ms after its Try it). */
    private val runs = mapOf(
        1 to 1_400L, 2 to 5_800L, 3 to 3_300L, 4 to 4_600L, 6 to 1_200L, 8 to 2_600L, 9 to 3_400L, 13 to 4_300L,
        16 to 1_000L, 17 to 6_100L, 18 to 2_400L,
    )

    /** The bursts and the roller: drawn with every text box cut out. */
    private val clipped = listOf("dusk", "petal", "ant", "confetti", "shiny", "roller")

    /**
     * Every text node on screen (unmerged), in root px: the visible part of each, since the list scrolls under the
     * opaque add bar (a row's words under the bar are hidden by it; the bar's own words are not).
     */
    private fun texts(bar: Rect): List<Pair<String, Rect>> {
        val out = ArrayList<Pair<String, Rect>>()
        fun walk(n: SemanticsNode, inBar: Boolean) {
            val mine = inBar || n.config.getOrNull(SemanticsProperties.TestTag) == PracticeTags.ADD_BAR
            val text = n.config.getOrNull(SemanticsProperties.Text)
            val box = n.boundsInRoot.let { b -> if (mine) b else Rect(b.left, b.top, b.right, minOf(b.bottom, bar.top)) }
            if (text != null && box.width > 0f && box.height > 0f) out += text.joinToString() to box
            n.children.forEach { walk(it, mine) }
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), false)
        return out
    }

    @Test
    fun neverOverText() {
        val r = EggRig(compose, variant = variant).start()
        if (egg == 1 && variant == ThemeVariant.Glow) return // the Glow moon is static: no transition to check
        r.probe.clear()
        val t0 = r.fire(egg)
        val until = t0 + runs.getValue(egg)
        var samples = 0
        val wrong = LinkedHashSet<String>()
        while (r.now < until) {
            r.advance(48)
            val text = texts(r.anchor(AnchorRegistry.ADD_BAR))
            val cutOut = keepOutRects(r.anchors)
            val frames = r.probe.frames.filter { it.time >= 0 }.groupBy { it.tag }.mapValues { it.value.last() }
            r.probe.clear()
            for ((tag, f) in frames) {
                val piece = f.bounds.deflate(2f)
                if (piece.width <= 0f || piece.height <= 0f) continue
                val over = text.filter { (_, box) -> box.overlaps(piece) }
                val kind = tag.trimEnd { it.isDigit() }
                if (tag == "receipt") {
                    val title = r.anchors.bounds(EggAnchors.TEXT_PREFIX + "title")!!
                    if (title.deflate(2f).overlaps(piece)) wrong += "the receipt over the title"
                } else if (clipped.any { tag.startsWith(it) }) {
                    // Wherever a clipped piece crosses words, they are one of the boxes its clip cuts out.
                    over.filterNot { (_, box) -> cutOut.any { it.inflate(1f).contains(box.topLeft) && it.inflate(1f).contains(box.bottomRight) } }
                        .forEach { (words, _) -> wrong += "$kind crosses \"$words\", which its clip does not cut out" }
                } else {
                    over.forEach { (words, _) -> wrong += "$kind over \"$words\"" }
                }
            }
            samples++
        }
        assertTrue(samples > 10)
        assertEquals("egg $egg ${variant.name}", emptyList<String>(), wrong.toList())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "egg {0} {1}")
        fun params(): List<Array<Any>> = listOf(1, 2, 3, 4, 6, 8, 9, 13, 16, 17, 18).flatMap { n ->
            listOf(arrayOf<Any>(n, ThemeVariant.Day), arrayOf<Any>(n, ThemeVariant.Glow))
        }
    }
}

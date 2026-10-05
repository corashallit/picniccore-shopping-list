package earth.cora.picnic.ui.eggs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
 * (EggProbe), and it is checked against every word on screen: the text nodes and the list's word keep-outs
 * (wordBoxes). The pieces that go about the page (the ladybug, the nap cat, JACK / POT!, the umbrella, the rolling
 * produce, the confetti and the petals in their lanes, the Dusk sun, moon and sparkles) never overlap a word at all: they go
 * round the words, so nothing is ever cut out of them. The roll home along the new row's own line is drawn by the row
 * beneath its words (it may pass under that row's words only). The ants on the add bar's edge and the shiny row's burst
 * keep to their lanes and are clipped clear of any word all the same, so where one crosses words those words must be one
 * of the boxes their clip cuts out. Sampled over each egg's whole run on WP5's real List screen as the Surprise lab shows
 * it ([EggListUnderTest]), Day and Glow.
 *
 * The printed receipt is not decoration but an interactive card with its own words, which waits over the list until it
 * is tapped (§13.2), as the Next card and the toasts do: ReceiptTest.neverOverWords keeps it and its slot line off
 * every word at font scales 1.0, 1.3 and 2.0.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggOverlayBoundsTest(private val egg: Int, private val variant: ThemeVariant, private val list: EggListUnderTest) {
    @get:Rule
    val compose = createComposeRule()

    /** How long each egg's overlays play (ms after its Try it). */
    private val runs = mapOf(
        1 to 1_400L, 2 to 5_800L, 3 to 3_300L, 4 to 4_600L, 6 to 1_200L, 8 to 2_600L, 9 to 3_400L, 13 to 4_300L,
        16 to 1_000L, 17 to 6_100L, 18 to 2_400L,
    )

    /**
     * Clipped clear of words as well as kept to their lanes. Egg 1's sun, moon and six-star burst are not among them:
     * they are laid out on the free side of the moon and never meet a word, so nothing of them is ever cut.
     */
    private val clipped = listOf("ant", "shiny")

    @Test
    fun neverOverText() {
        val r = EggRig(compose, variant = variant).start(list)
        if (egg == 1 && variant == ThemeVariant.Glow) return // the Glow moon is static: no transition to check
        r.probe.clear()
        val t0 = r.fire(egg)
        val until = t0 + runs.getValue(egg)
        var samples = 0
        val wrong = LinkedHashSet<String>()
        while (r.now < until) {
            r.advance(48)
            val text = wordBoxes(compose, r.anchors)
            val cutOut = keepOutRects(r.anchors)
            val frames = r.probe.frames.filter { it.time >= 0 }.groupBy { it.tag }.mapValues { it.value.last() }
            r.probe.clear()
            for ((tag, f) in frames) {
                val piece = f.bounds.deflate(2f)
                if (piece.width <= 0f || piece.height <= 0f) continue
                val over = text.filter { (_, box) -> box.overlaps(piece) }
                val kind = tag.trimEnd { it.isDigit() }
                if (tag == "receipt" || tag == "slot") {
                    continue
                } else if (tag == "rollhome") {
                    // Beneath its own row's words only.
                    val row = r.list.items.lastOrNull { it.name == "Strawberries" || it.name == "Lemons" }
                    val line = row?.let { r.anchors.bounds(EggAnchors.line(it.id)) }
                    over.filterNot { (_, box) -> line != null && line.inflate(1f).contains(box.topLeft) && line.inflate(1f).contains(box.bottomRight) }
                        .forEach { (words, _) -> wrong += "the roll home under \"$words\", not its own row's words" }
                } else if (tag == "umbrella") {
                    // Its canopy rises into the band where the list fades into the bar (§12.2 egg 16): the list's words
                    // washed out there may lie under it; no other word may.
                    val barTop = r.anchors.bounds(AnchorRegistry.ADD_BAR)?.top ?: Float.MAX_VALUE
                    over.filterNot { (_, box) -> inTheBarsFade(box, barTop, 14 * 3f) }.forEach { (words, _) -> wrong += "$kind over \"$words\"" }
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
        @ParameterizedRobolectricTestRunner.Parameters(name = "egg {0} {1} {2}")
        fun params(): List<Array<Any>> = EggListUnderTest.all.flatMap { list ->
            listOf(1, 2, 3, 4, 6, 8, 9, 13, 16, 17, 18).flatMap { n ->
                listOf(arrayOf<Any>(n, ThemeVariant.Day, list), arrayOf<Any>(n, ThemeVariant.Glow, list))
            }
        }
    }
}

/**
 * Egg 16's raindrop washi (§12.2: "the raindrop washi replaces the tape"): while the umbrella stands at the bar's left
 * end the tape moves along, after the doodle ("♡ picnic day ☆") as it is laid out at the font scale, so it never lies
 * over the doodle, any word or the umbrella, at font scales 1.0, 1.3 and 2.0.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggRainTapeBoundsTest(private val scale: Float) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theTapeKeepsOffTheWords() {
        val r = EggRig(compose, fontScale = scale).start()
        val t0 = r.fire(16)
        r.advanceTo(t0 + 600)
        assertTrue(r.eggs.umbrella)
        val doodle = compose.onNodeWithTag(earth.cora.picnic.ui.add.ADD_BAR_DOODLE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val umbrella = r.probe.frames.last { it.tag == "umbrella" }.bounds
        val bar = r.anchor(AnchorRegistry.ADD_BAR)
        val tapes = compose.onAllNodesWithTag(earth.cora.picnic.ui.add.ADD_BAR_WASHI_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        if (tapes.isEmpty()) {
            // No room after the doodle (very large text): no tape at all, rather than one over the doodle or the umbrella.
            assertTrue("fs $scale: no tape only when there is no room after the doodle", doodle.right + (8 + 56 + 8) * 3f > bar.right)
            return
        }
        assertTrue("fs $scale: the tape shows wherever there is room for it after the doodle", scale <= 1.3f || doodle.right + (8 + 56 + 8) * 3f <= bar.right)
        val tape = tapes.single().boundsInRoot
        assertTrue("fs $scale: the tape on the bar ($tape, bar $bar)", tape.left >= bar.left && tape.right <= bar.right)
        assertTrue("fs $scale: clear of the doodle ($tape, doodle $doodle)", !tape.overlaps(doodle))
        assertTrue("fs $scale: clear of the umbrella ($tape, umbrella $umbrella)", !tape.overlaps(umbrella.deflate(2f)))
        textBoxes(compose).forEach { (w, box) -> assertTrue("fs $scale: the tape over \"$w\" ($tape, $box)", !tape.overlaps(box)) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "fs {0}")
        fun params(): List<Array<Any>> = listOf(1.0f, 1.3f, 2.0f).map { arrayOf<Any>(it) }
    }
}

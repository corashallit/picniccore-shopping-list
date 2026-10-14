package earth.cora.picnic.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.theme.LETTERSET_TAG
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The Add sheet in motion (docs/DESIGN_STICKER.md §4.16, §8.1, §8.2; items 363–365): its height glides over the 240 ms
 * `layout` as thinking steps and the review card arrive (its top edge never jumps), the 38 % scrim fades in over the
 * 280 ms `sheetIn` and out with the sheet, and after "Stick all" the sheet slides away still showing the card it
 * stuck. Under reduced motion the height changes at once (§8.8).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class AddSheetMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val f = AddFixtures

    private fun sheetTop(): Float = compose.onNodeWithTag(LETTERSET_TAG).fetchSemanticsNode().boundsInRoot.top

    private fun frames(n: Int, each: () -> Unit = {}) = repeat(n) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        each()
    }

    /** Waiting on the answer (the note and the thinking box), then the pancakes' review, its steps and its card. */
    private fun sheetOverAnswer(reduced: Boolean): (Boolean) -> Unit {
        val waiting = AddUiState(
            listId = f.LIST_ID, sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES,
            phase = AddPhase.Thinking(ItemSource.AI_TEXT),
        )
        val answered = waiting.copy(phase = AddPhase.Preview, review = f.review(9, f.pancakes(), ItemSource.AI_TEXT, PANCAKES, revealed = false))
        var state by mutableStateOf(waiting)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(ThemeVariant.Day, reducedMotion = reduced) {
                SheetStage { AddSheet(state, AddActions()) }
            }
        }
        return { answer -> compose.runOnIdle { state = if (answer) answered else waiting } }
    }

    /**
     * The sheet's top edge across the review's arrival: every move (a run of frames in which it moves) takes several
     * frames, none covering more than about a third of its way (§8.2 `layout`: 29 % in its first 16 ms frame).
     */
    @Test
    fun heightGlidesAsTheReviewArrives() {
        val answer = sheetOverAnswer(reduced = false)
        frames(40)
        val rest = sheetTop()
        answer(true)
        val tops = ArrayList<Float>()
        frames(200) { tops += sheetTop() }
        assertTrue("the sheet grows for the card (${rest} → ${tops.min()})", rest - tops.min() > 150 * compose.density.density)
        // The runs of frames in which the top edge moves, each with its steps.
        val runs = ArrayList<MutableList<Float>>()
        var last = rest
        var current: MutableList<Float>? = null
        for (t in tops) {
            val step = t - last
            if (abs(step) > 0.5f) {
                if (current == null) current = ArrayList<Float>().also { runs += it }
                current += step
            } else {
                current = null
            }
            last = t
        }
        assertTrue("the sheet moved", runs.isNotEmpty())
        for (run in runs) {
            val way = abs(run.sum())
            if (way < 4 * compose.density.density) continue
            val biggest = run.maxOf { abs(it) }
            assertTrue("a glide, never a jump: ${run.map { it.toInt() }} px", biggest <= way * 0.36f)
            assertTrue("over several frames: ${run.map { it.toInt() }} px", run.size >= 4)
        }
    }

    /** §8.8: under reduced motion the review's card changes the sheet's height at once (one frame, no glide). */
    @Test
    fun reducedMotionChangesHeightAtOnce() {
        val answer = sheetOverAnswer(reduced = true)
        frames(40)
        val rest = sheetTop()
        answer(true)
        val tops = ArrayList<Float>()
        frames(160) { tops += sheetTop() }
        val moves = tops.zipWithNext { a, b -> b - a }.filter { abs(it) > 0.5f }
        assertTrue("the sheet grows for the card", rest - tops.last() > 150 * compose.density.density)
        assertTrue("every height change in one frame: ${moves.map { it.toInt() }}", moves.size <= 6)
        val first = tops.first() - rest
        val total = tops.last() - rest
        assertTrue("the biggest move is the whole card at once ($first, $total)", moves.maxOf { abs(it) } > abs(total) * 0.5f || abs(first) > abs(total) * 0.5f)
    }

    /**
     * §4.16: the 38 % scrim fades in evenly over the sheet's 280 ms (never ahead of a straight ramp; the sheet's own
     * overshooting curve put it at nearly half in the first frame) and out over its 260 ms as the sheet leaves.
     */
    @Test
    fun scrimFadesWithTheSheet() {
        var visible by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    LetterSetSheet(visible = visible, onDismissRequest = { visible = false }) { Text("Start a new trip?") }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        fun shade(): Int = 255 - android.graphics.Color.red(compose.onRoot().captureToImage().asAndroidBitmap().getPixel(10, 10))
        compose.runOnIdle { visible = true }
        val start = compose.mainClock.currentTime
        val fadeIn = ArrayList<Pair<Long, Int>>()
        frames(24) { fadeIn += (compose.mainClock.currentTime - start) to shade() }
        val full = fadeIn.last().second
        assertTrue("the scrim darkens the screen (${fadeIn.map { it.second }})", full > 40)
        var last = 0
        for ((t, s) in fadeIn) {
            val share = s.toFloat() / full
            assertTrue("in at $t ms: ${(share * 100).toInt()} % is ahead of an even 280 ms fade", share <= (t + 17) / 280f + 0.08f)
            assertTrue("in at $t ms: it only darkens", s >= last - 1)
            last = s
        }
        assertTrue("half way only after 100 ms", fadeIn.filter { it.second >= full / 2 }.minOf { it.first } >= 100)
        compose.runOnIdle { visible = false }
        val out0 = compose.mainClock.currentTime
        val fadeOut = ArrayList<Pair<Long, Int>>()
        frames(24) { fadeOut += (compose.mainClock.currentTime - out0) to shade() }
        assertTrue("out: still more than half on at 100 ms (${fadeOut.map { it.second }})", fadeOut.first { it.first >= 100 }.second > full / 3)
        assertEquals("out: gone with the sheet", 0, fadeOut.last().second)
    }

    /**
     * §4.16, §6.2: after "Stick all 6 on my list" the sheet slides away still showing the card: it keeps its height to
     * the last frame (it never goes back to the empty Write tab and shrinks first), and nothing in it reads to TalkBack or
     * takes a tap while it leaves.
     */
    @Test
    fun keepsItsCardWhileItLeaves() {
        val h = compose.setHarness(FakeAssistant(ingest = { _, _ -> f.pancakes() }))
        h.vm.openSheet(AddMode.WRITE, f.LIST_ID)
        h.vm.setText(PANCAKES)
        h.vm.send(AddOrigin.SHEET)
        compose.waitForIdle()
        compose.onNodeWithTag(RECIPE_CARD_TAG).assertExists()
        // Where the sheet is laid out (not cut by the screen's edge as it slides off).
        fun laidOut(node: androidx.compose.ui.semantics.SemanticsNode) = androidx.compose.ui.geometry.Rect(node.positionInRoot, node.size.toSize())
        val before = laidOut(compose.onNodeWithTag(LETTERSET_TAG).fetchSemanticsNode())
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Stick all 6 on my list") and hasClickAction()).performClick()
        var shown = 0
        var lastTop = before.top
        repeat(30) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            val node = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(LETTERSET_TAG)).fetchSemanticsNodes().firstOrNull() ?: return@repeat
            shown++
            val b = laidOut(node)
            assertEquals("frame $it: the sheet keeps the card's height as it leaves", before.height, b.height, 1f)
            assertTrue("frame $it: it only slides down", b.top >= lastTop - 1f)
            lastTop = b.top
            compose.onNodeWithTag(RECIPE_CARD_TAG).assertDoesNotExist()
            compose.onNodeWithTag(NOTE_FIELD_TAG).assertDoesNotExist()
        }
        assertTrue("the sheet was seen leaving ($shown frames)", shown >= 4)
        assertEquals("committed once", 1, h.commits.size)
        assertEquals("the Write tab is reset behind it", false, h.vm.state.value.sheetOpen)
    }
}

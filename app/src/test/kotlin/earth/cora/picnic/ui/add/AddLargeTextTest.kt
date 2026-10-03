package earth.cora.picnic.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.ui.theme.LETTERSET_TAG
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.WashiPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Large text in the add flows (docs/DESIGN_STICKER.md §15 font scale up to 200 %, §2.7, items 115, 206, 207, 215):
 * every bar state and every sheet state at font scale 1.3 and 2.0 shows every word (the clipped-text check of
 * [clippedTexts]), the doodle never sits on the field or the buttons, the tabs keep their 13 sp floor, and the goldens
 * `addbar_fs130_day`, `addbar_fs200_day`, `add_tabs_fs200_day`, `add_error_fs200_day`, `snap_reading_fs200_day`,
 * `snap_popped_fs200_day` and `speak_heard_fs200_day` show it. Day only (the layout does not change with the variant).
 * Record: ./gradlew :app:recordRoborazziDebug --tests '*AddLargeTextTest*'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class AddLargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val f = AddFixtures
    private val base = AddUiState(listId = f.LIST_ID)

    private fun at(scale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                PicnicTheme(ThemeVariant.Day, reducedMotion = false) { content() }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
    }

    // ---- the add bar (item 115) ----

    @Test fun bar130() = bar(1.3f, "addbar_fs130_day")

    @Test fun bar200() = bar(2f, "addbar_fs200_day")

    /** The bar idle, typing and waiting at [scale]: every word shows, and the doodle stays clear of the field and buttons. */
    private fun bar(scale: Float, golden: String) {
        val states = listOf(AddBarUi(), AddBarUi(text = PANCAKES), AddBarUi(text = PANCAKES, waiting = true))
        at(scale) {
            Box(Modifier.fillMaxSize()) {
                PicnicBackground {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(30.dp, androidx.compose.ui.Alignment.Bottom)) {
                        for (ui in states) {
                            AddBar(ui, onTextChange = {}, onSend = {}, onSnap = {}, onChoosePhoto = {}, onSpeak = {}, tape = WashiPattern.Thanksgiving, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$golden.png")
        compose.assertNoClippedText("bar at $scale", ADD_BAR_TAG)

        val bars = compose.onAllNodesWithTag(ADD_BAR_TAG).fetchSemanticsNodes().map { it.boundsInRoot }
        val doodles = compose.onAllNodesWithTag(ADD_BAR_DOODLE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        val placeholders = compose.onAllNodesWithTag(ADD_BAR_PLACEHOLDER_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        val fields = compose.onAllNodesWithTag(ADD_FIELD_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        val buttons = compose.onAllNodes(hasClickAction() and (hasContentDescription("Snap a photo of a list") or hasContentDescription("Speak a voice note") or hasContentDescription("Send") or hasContentDescription("Stop reading")))
            .fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(3, doodles.size)
        val pad = 2.dp.value * compose.density.density
        for (doodle in doodles) {
            // The doodle's line box (untilted) and the field's line boxes keep a little blank padding; inside it, the
            // doodle never touches a word or a button (the goldens show the tilt).
            val inked = doodle.deflate(pad)
            for (b in buttons) assertFalse("doodle $doodle on a button $b at $scale", inked.overlaps(b))
            for (p in placeholders) assertFalse("doodle $doodle on the placeholder $p at $scale", inked.overlaps(p.deflate(pad)))
            for (fd in fields) assertFalse("doodle $doodle on the field $fd at $scale", inked.overlaps(fd.deflate(pad)))
        }
        // The bar grows with the text (never under 84 dp).
        for (b in bars) assertTrue("bar ${b.height / compose.density.density} dp", b.height / compose.density.density >= 84f)
    }

    // ---- the sheet: every state at 1.3 and 2.0 (items 206, 207, 210, 215) ----

    private fun photo() = SnapPhoto(f.paperPhoto(), f.NOW)

    private fun states(): List<Pair<String, AddUiState>> {
        val photoReview = f.review(4, f.photo(), ItemSource.PHOTO, null)
        val heard = AddRules.rows(f.voice().items.take(2), earth.cora.picnic.core.model.Aisles.parse(f.AISLES), emptyMap(), RowBatch.HEARD, firstKey = 100)
        return listOf(
            "write" to base.copy(sheetOpen = true, mode = AddMode.WRITE),
            "write typing" to base.copy(sheetOpen = true, mode = AddMode.WRITE, text = "2 lemons, oat milk, the big jug of litter for Mochi"),
            "thinking" to base.copy(sheetOpen = true, mode = AddMode.WRITE, origin = AddOrigin.SHEET, text = PANCAKES, sentText = PANCAKES, phase = AddPhase.Thinking(ItemSource.AI_TEXT)),
            "recipe" to base.copy(sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES, phase = AddPhase.Preview, review = f.review(3, f.pancakes(withPantry = true), ItemSource.AI_TEXT, PANCAKES)),
            "error" to errorState(),
            "settings error" to base.copy(
                sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES,
                phase = AddPhase.Failed(AddProblem.AI, LlmError.Unauthorized(401).friendlyMessage, needsSettings = true, canUsePlainLines = true),
            ),
            "snap" to base.copy(sheetOpen = true, mode = AddMode.SNAP),
            "snap reading" to base.copy(sheetOpen = true, mode = AddMode.SNAP, phase = AddPhase.Thinking(ItemSource.PHOTO), photo = photo()),
            "snap popped" to base.copy(sheetOpen = true, mode = AddMode.SNAP, phase = AddPhase.Preview, photo = photo(), review = photoReview),
            "speak" to base.copy(sheetOpen = true, mode = AddMode.SPEAK),
            "speak recording" to base.copy(sheetOpen = true, mode = AddMode.SPEAK, phase = AddPhase.Recording(12_400, 0.72f, LEVELS)),
            "speak transcribing" to base.copy(sheetOpen = true, mode = AddMode.SPEAK, phase = AddPhase.Transcribing),
            "speak heard" to base.copy(
                sheetOpen = true, mode = AddMode.SPEAK, phase = AddPhase.Preview, transcript = f.TRANSCRIPT,
                review = f.review(5, f.voice(), ItemSource.VOICE, f.TRANSCRIPT),
            ),
            "snap + speak" to base.copy(
                sheetOpen = true, mode = AddMode.SNAP, phase = AddPhase.Preview, photo = photo(), transcript = f.TRANSCRIPT,
                review = photoReview.copy(rows = photoReview.rows + heard, heardId = 9, heardSteps = listOf(ThinkingStep.SortedAisles(2)), revealed = photoReview.revealed + 9),
            ),
        )
    }

    private fun errorState() = base.copy(
        sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES,
        phase = AddPhase.Failed(AddProblem.AI, message = LlmError.Network(null).friendlyMessage, retryable = true, canUsePlainLines = true),
    )

    @Test fun everySheetStateAt130() = sheetStates(1.3f)

    @Test fun everySheetStateAt200() = sheetStates(2f)

    private fun sheetStates(scale: Float) {
        val all = states()
        var current by mutableStateOf(all.first().second)
        at(scale) { SheetStage { AddSheet(current, AddActions(onEditPantry = {})) } }
        val failures = mutableListOf<String>()
        for ((name, state) in all) {
            current = state
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(800)
            compose.waitForIdle()
            val clipped = compose.clippedTexts(LETTERSET_TAG)
            if (clipped.isNotEmpty()) failures += "$name: $clipped"
        }
        assertTrue("clipped at font scale $scale: $failures", failures.isEmpty())
    }

    /** Item 206 at 2.0: the tabs grow with the text, their labels never fall under the 13 sp floor (26 dp here). */
    @Test
    fun tabsAt200() {
        at(2f) { SheetStage { AddSheet(base.copy(sheetOpen = true, mode = AddMode.WRITE), AddActions()) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/add_tabs_fs200_day.png")
        val labels = compose.onAllNodesWithTag(ADD_TAB_LABEL_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(3, labels.size)
        for (label in labels) {
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            label.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)!!.action!!(results)
            val r = results.single()
            val sp = r.layoutInput.style.fontSize.value
            val name = label.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            assertTrue("$name at $sp sp", sp >= 13f)
            assertTrue("$name: ${lostWords(r, compose.density.density)}", lostWords(r, compose.density.density) == null)
        }
        compose.assertNoClippedText("tabs at 2.0", ADD_TABS_TAG)
    }

    /**
     * Item 215 at 2.0 (and 337): every error pill shows its whole label, the same words as at 1.0: a long one wraps and
     * its pill grows round it (no short form for large text), and nothing in the sheet is clipped.
     */
    @Test
    fun errorAt200() {
        at(2f) { SheetStage { AddSheet(errorState(), AddActions()) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(ADD_ERROR_TAG))
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/add_error_fs200_day.png")
        compose.onNode(hasText("Add as plain lines") and hasClickAction()).assertExists()
        compose.onNode(hasText("Try again") and hasClickAction()).assertExists()
        compose.assertNoClippedText("error at 2.0", LETTERSET_TAG)
    }

    /** Item 207 at 2.0: the polaroid keeps its whole caption inside its lip ("fridge list · Sat"). */
    @Test
    fun snapReadingAt200() {
        at(2f) { SheetStage { AddSheet(base.copy(sheetOpen = true, mode = AddMode.SNAP, phase = AddPhase.Thinking(ItemSource.PHOTO), photo = photo()), AddActions()) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/snap_reading_fs200_day.png")
        val caption = compose.onNodeWithTag(POLAROID_CAPTION_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the caption reports no lost text", caption.config.getOrNull(earth.cora.picnic.ui.a11y.TextOverflowed) != true)
        val polaroid = compose.onNodeWithTag(POLAROID_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the caption sits on the polaroid", polaroid.inflate(4f).contains(caption.boundsInRoot.center))
        compose.assertNoClippedText("snap reading at 2.0", LETTERSET_TAG)
    }

    @Test
    fun snapPoppedAt200() {
        at(2f) { SheetStage { AddSheet(states().first { it.first == "snap popped" }.second, AddActions()) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(POPPED_TRAY_TAG))
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/snap_popped_fs200_day.png")
    }

    @Test
    fun speakHeardAt200() {
        at(2f) { SheetStage { AddSheet(states().first { it.first == "speak heard" }.second, AddActions()) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(HEARD_TRAY_TAG))
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/speak_heard_fs200_day.png")
    }

    private fun Rect.overlaps(other: Rect): Boolean = left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

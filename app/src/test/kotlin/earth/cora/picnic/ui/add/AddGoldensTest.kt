package earth.cora.picnic.ui.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.ToastHost
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.ui.theme.dayAndGlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WP7's goldens (docs/DESIGN_STICKER.md §6.2 "States (goldens)", Appendix C items 115, 200, 203, 206, 207, 210, 215,
 * 323 and the Add rows): the add bar idle, focused, typing and waiting; the sheet's tabs; Write with thinking; the recipe
 * card with kept and skipped rows, and with pantry rows; the error with plain lines; Snap reading, lines, popped; Speak
 * recording, transcribing, heard; Snap + Speak merged; the toast after a commit. Day and Glow, at w360dp-h760dp-xxhdpi.
 * Record: ./gradlew :app:recordRoborazziDebug --tests '*AddGoldensTest*'.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class AddGoldensTest(private val variant: ThemeVariant, private val v: String) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlow()
    }

    private val base = AddUiState(listId = AddFixtures.LIST_ID)

    // ---- the add bar (§6.2) ----

    @Test fun addbarIdle() = bar("addbar_idle_$v", AddBarUi())

    @Test fun addbarFocused() = bar("addbar_focused_$v", AddBarUi(), focus = true)

    @Test fun addbarTyping() = bar("addbar_typing_$v", AddBarUi(text = "ingredients for pancakes from scratch"), focus = true)

    @Test fun addbarWaiting() = bar("addbar_waiting_$v", AddBarUi(text = "ingredients for pancakes from scratch", waiting = true))

    @Test fun toastAfterCommit() = record("toast_after_commit_$v", settleMillis = 900, stage = true) {
        val toasts = remember { ToastHostState() }
        val resources = LocalResources.current
        LaunchedEffect(Unit) {
            toasts.show(sampleCommit().toast(resources) {})
        }
        BarStage(height = 300.dp) {
            AddBar(AddBarUi(), onTextChange = {}, onSend = {}, onSnap = {}, onChoosePhoto = {}, onSpeak = {}, tape = WashiPattern.Thanksgiving)
            ToastHost(toasts, bottomInset = 84.dp)
        }
    }

    // ---- the sheet's tabs (§4.16) ----

    @Test fun tabsWrite() = sheet("add_tabs_write_$v", base.copy(sheetOpen = true, mode = AddMode.WRITE))

    @Test fun tabsSnap() = sheet("add_tabs_snap_$v", base.copy(sheetOpen = true, mode = AddMode.SNAP))

    @Test fun tabsSpeak() = sheet("add_tabs_speak_$v", base.copy(sheetOpen = true, mode = AddMode.SPEAK))

    // ---- Write ----

    @Test fun thinking() = sheet(
        "add_thinking_$v",
        base.copy(
            sheetOpen = true, mode = AddMode.WRITE, origin = AddOrigin.SHEET, text = PANCAKES, sentText = PANCAKES,
            phase = AddPhase.Thinking(ItemSource.AI_TEXT),
        ),
    )

    // Item 203 on a tall phone, so the whole card shows at once: the postage edge, the title, "from the kitchen of ♡ the
    // web", the "web search" chip and "3 recipes · 6 of 10 kept", every tray with its kept stars and skipped rows.
    @Test
    @Config(qualifiers = "w360dp-h1800dp-xxhdpi")
    fun recipeCard() = sheet("recipe_card_$v", recipeState(withPantry = false))

    @Test fun recipeCardPantry() = sheet("recipe_card_pantry_$v", recipeState(withPantry = true), scrollTo = EDIT_PANTRY_TAG)

    @Test fun error() = sheet(
        "add_error_$v",
        base.copy(
            sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES,
            phase = AddPhase.Failed(
                // Core's own words for a network error (§4.8, §16: "Couldn't reach OpenAI. Check your connection?").
                AddProblem.AI, message = earth.cora.picnic.core.llm.LlmError.Network(null).friendlyMessage,
                retryable = true, canUsePlainLines = true,
            ),
        ),
    )

    // ---- Snap ----

    @Test fun snapReading() = sheet(
        "snap_reading_$v",
        base.copy(sheetOpen = true, mode = AddMode.SNAP, phase = AddPhase.Thinking(ItemSource.PHOTO), photo = photo()),
    )

    @Test fun snapLines() = sheet("snap_lines_$v", photoState(revealed = false), settleMillis = 1180)

    @Test fun snapPopped() = sheet("snap_popped_$v", photoState(revealed = true))

    // ---- Speak ----

    @Test fun speakRecording() = sheet(
        "speak_recording_$v",
        base.copy(
            sheetOpen = true, mode = AddMode.SPEAK,
            phase = AddPhase.Recording(elapsedMs = 12_400, level = 0.72f, levels = LEVELS),
        ),
    )

    @Test fun speakTranscribing() = sheet("speak_transcribing_$v", base.copy(sheetOpen = true, mode = AddMode.SPEAK, phase = AddPhase.Transcribing))

    @Test fun speakHeard() = sheet(
        "speak_heard_$v",
        base.copy(
            sheetOpen = true, mode = AddMode.SPEAK, phase = AddPhase.Preview, transcript = AddFixtures.TRANSCRIPT,
            review = AddFixtures.review(5, AddFixtures.voice(), ItemSource.VOICE, AddFixtures.TRANSCRIPT),
        ),
    )

    @Test fun snapSpeakMerged() {
        val photo = photoState(revealed = true)
        val review = photo.review!!
        val heard = AddRules.rows(AddFixtures.voice().items.take(2), earth.cora.picnic.core.model.Aisles.parse(AddFixtures.AISLES), emptyMap(), RowBatch.HEARD, firstKey = 100)
        val merged = review.copy(
            rows = review.rows + heard,
            heardId = 9,
            heardSteps = listOf(ThinkingStep.SortedAisles(2)),
            revealed = review.revealed + 9,
        )
        sheet("add_snap_speak_merged_$v", photo.copy(review = merged, transcript = AddFixtures.TRANSCRIPT), scrollTo = HEARD_TRAY_TAG)
    }

    // ---- helpers ----

    private fun recipeState(withPantry: Boolean): AddUiState = base.copy(
        sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES, phase = AddPhase.Preview,
        review = AddFixtures.review(if (withPantry) 3 else 2, AddFixtures.pancakes(withPantry), ItemSource.AI_TEXT, PANCAKES),
    )

    private fun photo() = SnapPhoto(AddFixtures.paperPhoto(), AddFixtures.NOW)

    private fun photoState(revealed: Boolean): AddUiState = base.copy(
        sheetOpen = true, mode = AddMode.SNAP, phase = AddPhase.Preview, photo = photo(),
        review = AddFixtures.review(4, AddFixtures.photo(), ItemSource.PHOTO, null, revealed = revealed),
    )

    private fun sampleCommit(): AddCommit {
        val added = AddFixtures.pancakes().items.filter { it.skipReason == earth.cora.picnic.core.assistant.SkipReason.NONE }.mapIndexed { i, p ->
            GroceryItem(id = "n$i", name = p.name, quantity = p.quantity, aisleId = p.aisleId, icon = p.icon, addedAt = AddFixtures.NOW)
        }
        return AddCommit(1, AddFixtures.LIST_ID, "Saturday shop", added, emptyList(), emptyList())
    }

    private fun bar(name: String, ui: AddBarUi, focus: Boolean = false) = record(name, stage = true) {
        val focusRequester = remember { FocusRequester() }
        BarStage {
            AddBar(
                ui, onTextChange = {}, onSend = {}, onSnap = {}, onChoosePhoto = {}, onSpeak = {},
                tape = WashiPattern.Thanksgiving, focusRequester = focusRequester,
                modifier = Modifier,
            )
        }
        if (focus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    private fun sheet(name: String, state: AddUiState, settleMillis: Long? = null, scrollTo: String? = null) =
        record(name, settleMillis, scrollTo = scrollTo) {
            SheetStage { AddSheet(state, AddActions(onEditPantry = {})) }
        }

    private fun record(
        name: String,
        settleMillis: Long? = null,
        fontScale: Float? = null,
        stage: Boolean = false,
        scrollTo: String? = null,
        content: @Composable BoxScope.() -> Unit,
    ) {
        if (settleMillis != null) compose.mainClock.autoAdvance = false
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale ?: d.fontScale)) {
                PicnicTheme(variant = variant, reducedMotion = false) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { content() }
                }
            }
        }
        if (settleMillis != null) {
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(settleMillis)
        } else {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(800)
        }
        if (scrollTo != null) {
            val target = if (scrollTo == EDIT_ROW_EGGS) reviewRow("Eggs") else hasTestTag(scrollTo)
            compose.onNode(hasScrollAction()).performScrollToNode(target)
            compose.waitForIdle()
        }
        compose.waitForIdle()
        val node = if (stage) compose.onNodeWithTag(STAGE_TAG) else compose.onRoot()
        node.captureRoboImage("src/test/screenshots/$name.png")
    }
}

internal const val PANCAKES = "ingredients for pancakes from scratch"

/** AddGoldensTest's scroll target for the recipe card: the Eggs row. */
private const val EDIT_ROW_EGGS = "row:Eggs"

/** A minute of mic levels (0..1), 100 ms apart. */
internal val LEVELS: List<Float> = listOf(0.2f, 0.45f, 0.8f, 0.55f, 0.3f, 0.65f, 0.95f, 0.7f, 0.4f, 0.6f, 0.85f, 0.5f, 0.35f, 0.72f)

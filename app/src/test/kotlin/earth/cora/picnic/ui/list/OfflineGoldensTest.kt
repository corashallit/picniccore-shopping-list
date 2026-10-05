package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.icons.DrawFailure
import earth.cora.picnic.platform.CaptureKind
import earth.cora.picnic.platform.FakeNetworkStatus
import earth.cora.picnic.ui.add.AddActions
import earth.cora.picnic.ui.add.AddBar
import earth.cora.picnic.ui.add.AddBarUi
import earth.cora.picnic.ui.add.AddCommit
import earth.cora.picnic.ui.add.AddFixtures
import earth.cora.picnic.ui.add.AddMode
import earth.cora.picnic.ui.add.AddPhase
import earth.cora.picnic.ui.add.AddProblem
import earth.cora.picnic.ui.add.AddSheet
import earth.cora.picnic.ui.add.AddUiState
import earth.cora.picnic.ui.add.BarStage
import earth.cora.picnic.ui.add.STAGE_TAG
import earth.cora.picnic.ui.add.SheetStage
import earth.cora.picnic.ui.add.SnapPhoto
import earth.cora.picnic.ui.add.toast
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.edit.EditItemSheetContent
import earth.cora.picnic.ui.edit.EditSamples
import earth.cora.picnic.ui.settings.SettingsActions
import earth.cora.picnic.ui.settings.SettingsSamples
import earth.cora.picnic.ui.settings.SettingsScreen
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.TOAST_TAG
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.ToastHost
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.WashiPattern
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The offline states' goldens (docs/DESIGN_STICKER.md §0.4, 5 October; Appendix C, items 350, 351, 353, 356–358), Day and Glow at
 * w360dp-h760dp-xxhdpi: the add bar's offline note `toast_add_offline`, the offline refresh's note
 * `list_refresh_offline` (over the real list, the LCD reading SYNC), Settings' neutral line `settings_unchecked_offline`,
 * the Edit sheet's `edit_draw_offline` and `edit_draw_failed`, the Write tab's offline error with its "As typed" footer
 * `add_write_offline`, and a photo kept after an offline Snap `add_snap_kept`.
 * Record: ./gradlew :app:recordRoborazziDebug --tests '*OfflineGoldensTest*'.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class OfflineGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()

        private const val PANCAKES = "ingredients for pancakes from scratch"
        private val OFFLINE = LlmError.Offline().friendlyMessage
    }

    // ---- the add bar's offline Send (rule 2b) ----

    @Test
    fun addToast() = stage(goldenName("toast_add_offline", variant)) {
        val toasts = remember { ToastHostState() }
        val resources = LocalResources.current
        LaunchedEffect(Unit) {
            val added = listOf("Paper towels", "Dish soap").mapIndexed { i, name ->
                GroceryItem(id = "n$i", name = name, icon = "paper_bag", addedAt = AddFixtures.NOW)
            }
            val commit = AddCommit(1, AddFixtures.LIST_ID, "Saturday shop", added, emptyList(), emptyList(), asTypedOffline = true, fromBar = true)
            toasts.show(commit.toast(resources) {})
        }
        BarStage(height = 300.dp) {
            AddBar(AddBarUi(), onTextChange = {}, onSend = {}, onSnap = {}, onChoosePhoto = {}, onSpeak = {}, tape = WashiPattern.Thanksgiving)
            ToastHost(toasts, bottomInset = 84.dp)
        }
    }

    // ---- the list's Refresh offline, over the real List destination ----

    @Test
    fun refreshOffline() {
        val rig = ListRig(network = FakeNetworkStatus(online = false)).inSeason()
        compose.recordScreen(goldenName("list_refresh_offline", variant), variant, settleMillis = 0, before = {
            onNodeWithContentDescription("More options").performClick()
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithText("Refresh").performClick()
            assertTrue("the note dropped in", framesUntil(3000) { exists(TOAST_TAG) })
            advanceMs(220 + 600)
        }) { rig.Content() }
    }

    // ---- Settings: saved, not checked ----

    @Test
    fun settingsUnchecked() = compose.recordScreen(goldenName("settings_unchecked_offline", variant), variant) {
        SettingsScreen(SettingsSamples.uncheckedOffline, SettingsActions())
    }

    // ---- the Edit sheet's drawing ----

    @Test
    fun editDrawOffline() = edit(goldenName("edit_draw_offline", variant), EditSamples.milk().copy(offline = true))

    @Test
    fun editDrawFailed() = edit(goldenName("edit_draw_failed", variant), EditSamples.milk().copy(drawFailed = DrawFailure("milk")))

    // ---- the Add sheet offline ----

    @Test
    fun writeOffline() = sheet(
        goldenName("add_write_offline", variant),
        AddUiState(
            listId = AddFixtures.LIST_ID, sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES,
            phase = AddPhase.Failed(AddProblem.AI, message = OFFLINE, retryable = true, canUsePlainLines = true, offline = true),
        ),
    )

    @Test
    fun snapKept() = sheet(
        goldenName("add_snap_kept", variant),
        AddUiState(
            listId = AddFixtures.LIST_ID, sheetOpen = true, mode = AddMode.SNAP, kept = CaptureKind.PHOTO,
            photo = SnapPhoto(AddFixtures.paperPhoto(), AddFixtures.NOW),
            phase = AddPhase.Failed(AddProblem.AI, message = OFFLINE, retryable = true, offline = true),
        ),
    )

    // ---- helpers ----

    private fun edit(name: String, state: earth.cora.picnic.ui.list.EditItemUi) =
        compose.recordScreen(name, variant, before = { toTheEnd() }) {
            Box(Modifier.fillMaxSize()) {
                PicnicBackground(Modifier.fillMaxSize()) {}
                EditItemSheetContent(state, { _, _, _ -> }, {}, {}, {}, {}, {}, aisleStyles = EditSamples.app.store.aisleStyles)
            }
        }

    /** The sheet scrolled to its end, the drawing's line clear of the footer's fade. */
    private fun ComposeContentTestRule.toTheEnd() {
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
    }

    private fun sheet(name: String, state: AddUiState) =
        compose.recordScreen(name, variant) { SheetStage { AddSheet(state, AddActions(onEditPantry = {})) } }

    /** A stage smaller than the screen ([STAGE_TAG]), its clock held from the first frame, captured after a toast's drop. */
    private fun stage(name: String, content: @Composable BoxScope.() -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(variant = variant, reducedMotion = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { content() }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        compose.onNodeWithTag(STAGE_TAG).captureRoboImage("src/test/screenshots/$name.png")
    }
}

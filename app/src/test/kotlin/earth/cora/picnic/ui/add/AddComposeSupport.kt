package earth.cora.picnic.ui.add

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.list
import earth.cora.picnic.platform.AddSources
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.FakeRecorder
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/** Launchers that count what the bar and the sheet asked for (no camera app or permission dialog in tests). */
class RecordingSources {
    var photos = 0
    var picks = 0
    var voices = 0
    val sources = AddSources(takePhoto = { photos++ }, pickPhoto = { picks++ }, recordVoice = { voices++ })
}

/** The add flows on Robolectric's main looper: the rig, every toast shown, every commit the list heard of. */
class HarnessRig(val rig: AddRig, val sources: RecordingSources, val toasts: ToastHostState) {
    val vm: AddViewModel get() = rig.vm
    val shown = mutableListOf<ToastMessage>()
    val commits = mutableListOf<AddCommit>()
    var pantryOpened = 0

    fun items() = runBlocking { rig.store.current().list(AddFixtures.LIST_ID)!!.items }
}

/** The harness rows for [state]'s list. */
fun harnessRows(state: AppState): List<HarnessRow> =
    state.list(AddFixtures.LIST_ID)!!.items.map { HarnessRow(it.id, it.name, it.icon, it.checked) }

/**
 * Sets [AddFlowsHarness] over a fresh ViewModel (the app scope on the main looper) and returns the rig. Toasts are
 * recorded as they show, so a test reads them even after their hold ends.
 */
fun ComposeContentTestRule.setHarness(
    assistant: FakeAssistant = FakeAssistant(),
    hasKey: Boolean = true,
    state: AppState = AddFixtures.state,
    recorder: FakeRecorder = FakeRecorder(),
    variant: ThemeVariant = ThemeVariant.Day,
    reducedMotion: Boolean = false,
    editPantry: Boolean = true,
    /** False: the real launchers (the permission request, the camera app), as the app has them. */
    fakeSources: Boolean = true,
): HarnessRig {
    val rig = addRig(CoroutineScope(SupervisorJob() + Dispatchers.Main), state, assistant, hasKey, recorder = recorder)
    val h = HarnessRig(rig, RecordingSources(), ToastHostState())
    val rows = harnessRows(state)
    setContent {
        PicnicTheme(variant = variant, reducedMotion = reducedMotion) {
            val flows = rememberAddFlows(rig.vm, rig.feedback, if (fakeSources) h.sources.sources else null)
            LaunchedEffect(Unit) { snapshotFlow { h.toasts.current }.collect { if (it != null) h.shown += it } }
            AddFlowsHarness(
                flows,
                rows,
                onCommitted = { h.commits += it },
                onEditPantry = if (editPantry) ({ h.pantryOpened++ }) else null,
                toasts = h.toasts,
            )
        }
    }
    waitForIdle()
    return h
}

/** The node's custom accessibility action called [label] (TalkBack's actions menu). */
fun SemanticsNodeInteraction.performCustomAction(label: String) {
    val actions = fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    val action = actions.firstOrNull { it.label == label } ?: throw AssertionError("no custom action \"$label\" in ${actions.map { it.label }}")
    action.action()
}

fun hasRole(role: androidx.compose.ui.semantics.Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

fun hasStateDescription(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

package earth.cora.picnic.ui.add

import android.content.res.Resources
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import earth.cora.picnic.R
import earth.cora.picnic.platform.AddSources
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.rememberAddSources
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage

/*
 * The add flows as one holder for the List screen (WP5): the ViewModel, the platform launchers (camera, picker,
 * microphone), the bar's fold state and its focus. WP5 places, in z order: [AddDock] under the list's overlay layer,
 * the toasts ([AddEventsEffect] feeds them), then [AddSheetHost] above. Owner: WP7.
 */

/** Toasts from the add flows share this kind, so a second commit's Undo merges into the first (§4.15). */
const val ADD_TOAST_KIND: String = "add"

@Stable
class AddFlows internal constructor(
    val vm: AddViewModel,
    val sources: AddSources,
    val collapse: AddBarCollapseState,
    val focusRequester: FocusRequester,
    val feedback: Feedback,
    private val keyboard: SoftwareKeyboardController?,
) {
    /** The field takes focus as soon as the bar is on screen ([AddDock] does it; the FAB unfolds the bar first). */
    internal var focusPending by mutableStateOf(false)

    /** Focuses the bar's field and opens the keyboard (the FAB, "Start my list"). */
    fun focusField() {
        collapse.expand()
        focusPending = true
    }

    /** Called by [AddDock] once the bar is composed. */
    internal fun focusNow() {
        try {
            focusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            return
        }
        keyboard?.show()
        focusPending = false
    }
}

/**
 * Remembers the add flows for [vm]: the launchers (TakePicture with no CAMERA permission, the photo picker, RECORD_AUDIO
 * at runtime; [sources] replaces them in tests and the harness), the fold state and the field's focus. Recording stops on ON_STOP (foreground only), except across a
 * configuration change.
 */
@Composable
fun rememberAddFlows(vm: AddViewModel, feedback: Feedback = NoOpFeedback, sources: AddSources? = null): AddFlows {
    val platform = rememberAddSources(
        onImage = vm::onPhoto,
        onMicGranted = vm::startRecording,
        onMicDenied = vm::micDenied,
        onCameraUnavailable = vm::cameraUnavailable,
    )
    // Tests and the harness pass their own launchers (no camera app or permission dialog there).
    val sources = sources ?: platform
    val collapse = rememberAddBarCollapseState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) vm.onStop()
    }
    return remember(vm, sources, collapse, focus, feedback, keyboard) { AddFlows(vm, sources, collapse, focus, feedback, keyboard) }
}

/**
 * Turns the add flows' events into sticky-note toasts (§4.15, §16) with Undo (which removes exactly the added items),
 * and hands each commit to [onCommitted] so the list pops the new rows 80 ms apart, flashes the ones already there and
 * plays the un-check chain for revived ones. [onFocusField] runs for the FAB and "Start my list".
 */
@Composable
fun AddEventsEffect(
    flows: AddFlows,
    toasts: ToastHostState,
    onCommitted: (AddCommit) -> Unit = {},
    onUndone: (Long) -> Unit = {},
) {
    val resources = LocalResources.current
    val committed by rememberUpdatedState(onCommitted)
    val undone by rememberUpdatedState(onUndone)
    LaunchedEffect(flows) {
        flows.vm.events.collect { event ->
            when (event) {
                is AddEvent.Committed -> {
                    committed(event.commit)
                    toasts.show(event.commit.toast(resources) { flows.vm.undo(event.commit.id) })
                }
                is AddEvent.Undone -> undone(event.commitId)
                AddEvent.SaveFailed -> toasts.show(ToastMessage(resources.getString(R.string.add_problem_save), kind = "save"))
                is AddEvent.SortFailed -> toasts.show(
                    ToastMessage(
                        resources.getQuantityString(R.plurals.add_toast_sort_failed, event.leftUnsorted, event.friendlyMessage, event.leftUnsorted),
                        kind = "sort",
                    ),
                )
                AddEvent.FocusField -> flows.focusField()
            }
        }
    }
}

/** The commit's sticky note: its text (§16) and, when something changed, Undo. */
fun AddCommit.toast(resources: Resources, onUndo: () -> Unit): ToastMessage {
    val undo = resources.getString(R.string.add_toast_undo)
    return ToastMessage(
        text = toastText(resources),
        kind = ADD_TOAST_KIND,
        actionLabel = if (canUndo) undo else null,
        onAction = if (canUndo) onUndo else null,
    )
}

/**
 * The commit toast's words (§16): "Added Milk to Dairy & eggs ♡" (one item), "Added 3 items to Saturday shop ♡",
 * "Added as typed · add your OpenAI key for smart sorting" (rule 2), "Milk is back on your list" (rule 7, joined after
 * the added part), "Milk is already on your list" (nothing new), plus "★ shiny!" for a shiny pull (egg 6).
 */
fun AddCommit.toastText(resources: Resources): String {
    val parts = mutableListOf<String>()
    when {
        added.isEmpty() -> Unit
        asTypedNoKey -> parts += resources.getString(R.string.add_toast_as_typed)
        added.size == 1 -> parts += resources.getString(
            R.string.add_toast_added_one,
            added.single().name,
            aisleTitle?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.add_everything_else),
        )
        else -> parts += resources.getQuantityString(R.plurals.add_toast_added_many, added.size, added.size, listTitle)
    }
    if (revived.isNotEmpty()) {
        parts += if (revived.size == 1) {
            resources.getString(R.string.add_toast_back_one, revived.single().name)
        } else {
            resources.getQuantityString(R.plurals.add_toast_back_many, revived.size, revived.size)
        }
    }
    if (parts.isEmpty() && alreadyThere.isNotEmpty()) {
        parts += if (alreadyThere.size == 1) {
            resources.getString(R.string.add_toast_already_one, alreadyThere.single().name)
        } else {
            resources.getQuantityString(R.plurals.add_toast_already_many, alreadyThere.size, alreadyThere.size)
        }
    }
    if (parts.isEmpty()) parts += resources.getString(R.string.add_toast_nothing)
    var text = parts.reduce { a, b -> resources.getString(R.string.add_toast_join, a, b) }
    if (shiny) text += " " + resources.getString(R.string.add_toast_shiny)
    return text
}

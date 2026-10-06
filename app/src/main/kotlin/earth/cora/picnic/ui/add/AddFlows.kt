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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import earth.cora.picnic.R
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.platform.AddSources
import earth.cora.picnic.platform.rememberAddSources
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage

/*
 * The add flows as one holder for the List screen (WP5): the ViewModel, the platform launchers (camera, picker,
 * microphone), the bar's fold state and its focus. WP5 places, in z order: [AddDock] under the list's overlay layer,
 * the toasts ([AddEventsEffect] feeds them), then [AddSheetHost] above. Sounds and haptics go through the ViewModel's
 * own Feedback, so no caller can forget them. Owner: WP7.
 */

/** Toasts from the add flows share this kind, so a second commit's Undo merges into the first (§4.15). */
const val ADD_TOAST_KIND: String = "add"

@Stable
class AddFlows internal constructor(
    val vm: AddViewModel,
    val sources: AddSources,
    val collapse: AddBarCollapseState,
    val focusRequester: FocusRequester,
    private val keyboard: SoftwareKeyboardController?,
    private val focusManager: FocusManager? = null,
    private val inputModes: InputModeManager? = null,
) {
    /** The field takes focus as soon as the bar is on screen ([AddDock] does it; the FAB unfolds the bar first). */
    internal var focusPending by mutableStateOf(false)

    /** The docked bar as [AddDock] laid it out, with the keyboard's or the navigation bar's inset under it. */
    internal var barHeight by mutableStateOf(84.dp)

    /** The folded dock's room as laid out: the FAB, its 12 dp margin and the inset under it. */
    internal var fabHeight by mutableStateOf(76.dp)

    /**
     * How far the dock reaches up from the bottom of the screen, measured: the bar (it grows with large text, §15, and
     * rides above the keyboard) or, folded, the FAB. Toasts sit 20 dp above it (§4.15: above the add bar, the FAB or
     * the IME) and the list's bottom padding is this + 26 dp (§6.1).
     */
    val dockHeight: Dp get() = if (collapse.collapsed) fabHeight else barHeight

    /**
     * The camera, the photo picker and the microphone, once there is a key: without one the sheet opens on Snap or
     * Speak with "Add your OpenAI key in Settings first." and nothing is captured ([AddViewModel.requireKey]).
     */
    fun takePhoto() = vm.requireKey(AddMode.SNAP, sources.takePhoto)

    fun pickPhoto() = vm.requireKey(AddMode.SNAP, sources.pickPhoto)

    fun recordVoice() = vm.requireKey(AddMode.SPEAK, sources.recordVoice)

    /**
     * The bar's camera button: a photo that could not be sent comes back first (the sheet on Snap, with Try again and
     * "Take a photo" for a new one, §0.4 5 October); otherwise the camera, also when the kept file turns out to be gone.
     */
    fun snapFromBar() {
        if (!vm.showKept(AddMode.SNAP, fallback = ::takePhoto)) takePhoto()
    }

    /**
     * The bar's cassette button: a voice note that could not be sent comes back first; otherwise the microphone, also
     * when the kept file turns out to be gone.
     */
    fun speakFromBar() {
        if (!vm.showKept(AddMode.SPEAK, fallback = ::recordVoice)) recordVoice()
    }

    /** Focuses the bar's field and opens the keyboard (the FAB, "Start my list"). */
    fun focusField() {
        collapse.expand()
        focusPending = true
    }

    /**
     * The field lets go once a send has committed or opened the sheet, and again when the sheet closes (D11, item 116):
     * the keyboard goes down and the bar is free to fold into the FAB as she scrolls down the aisles.
     */
    fun releaseField() {
        focusPending = false
        collapse.fieldFocused = false
        // With a hardware keyboard or switch access, focus stays where she is (§15: she keeps her place, and a sheet
        // that closes gives focus back to what opened it); there is no on-screen keyboard to put away then.
        if (inputModes?.inputMode != InputMode.Keyboard) focusManager?.clearFocus(force = true)
        keyboard?.hide()
    }

    /**
     * A send from the bar committed while its field still had focus: the field keeps it, and the keyboard (put away
     * while the note was read) comes back, so she can write the next thing at once (milk, Send, eggs, Send). It still
     * lets go when a review opens the sheet or the sheet closes ([releaseField]).
     */
    fun keepField(): Boolean {
        if (!collapse.fieldFocused) return false
        if (inputModes?.inputMode != InputMode.Keyboard) keyboard?.show()
        return true
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
 * at runtime; [sources] replaces them in tests and the harness), the fold state and the field's focus. Recording stops
 * on ON_STOP (foreground only), except across a configuration change.
 */
@Composable
fun rememberAddFlows(vm: AddViewModel, sources: AddSources? = null): AddFlows {
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
    val focusManager = LocalFocusManager.current
    val inputModes = LocalInputModeManager.current
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) vm.onStop()
    }
    return remember(vm, sources, collapse, focus, keyboard, focusManager, inputModes) {
        AddFlows(vm, sources, collapse, focus, keyboard, focusManager, inputModes)
    }
}

/**
 * Turns the add flows' events into sticky-note toasts (§4.15, §16) with Undo (which removes exactly the added items),
 * and hands each commit to [onCommitted] so the list pops the new rows 80 ms apart, flashes the ones already there and
 * plays the un-check chain for revived ones. Two commits during one toast's hold merge into one note that says what
 * its Undo takes back ([AddToasts]). The FAB and "Start my list" focus the field.
 */
@Composable
fun AddEventsEffect(
    flows: AddFlows,
    toasts: ToastHostState,
    onCommitted: (AddCommit) -> Unit = {},
    onUndone: (Long) -> Unit = {},
    /**
     * "Move" on a note that put one item in an aisle (§6.2, §4.15, Cora 5 October): the host opens the Edit sheet at
     * "Move to aisle" for [GroceryItem]. Null: the notes offer no Move.
     */
    onMove: ((GroceryItem) -> Unit)? = null,
) {
    val resources = LocalResources.current
    val committed by rememberUpdatedState(onCommitted)
    val undone by rememberUpdatedState(onUndone)
    val move by rememberUpdatedState(onMove)
    val canMove = onMove != null
    val notes = remember(flows, resources, canMove) {
        AddToasts(resources, flows.vm::undo, move = if (canMove) ({ item -> move?.invoke(item) }) else null)
    }
    LaunchedEffect(flows) {
        flows.vm.events.collect { event ->
            when (event) {
                is AddEvent.Committed -> {
                    // A review's commit is done: the field lets go, so the bar can fold again (item 116). A quick add
                    // from the bar keeps the field and the keyboard for the next thing she writes.
                    if (!(event.commit.fromBar && flows.keepField())) flows.releaseField()
                    committed(event.commit)
                    toasts.show(notes.toast(event.commit))
                }
                is AddEvent.Undone -> undone(event.commitId)
                AddEvent.SaveFailed -> toasts.show(ToastMessage(resources.getString(R.string.add_problem_save), kind = "save"))
                is AddEvent.SortFailed -> toasts.show(
                    ToastMessage(
                        resources.getQuantityString(R.plurals.add_toast_sort_failed, event.leftUnsorted, event.friendlyMessage, event.leftUnsorted),
                        kind = "sort",
                    ),
                )
                is AddEvent.SortWaits -> toasts.show(ToastMessage(resources.getString(R.string.add_toast_sort_waits), kind = "sort"))
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
 * The add flows' sticky notes (§4.15). A second commit during a note's hold merges into it: the note then counts both
 * ("Added 3 items to Saturday shop ♡"), so it says everything its single Undo takes back, and that Undo undoes every
 * commit in it, newest first. [undo] is the ViewModel's Undo by commit id. A note that put one item in an aisle
 * ([AddCommit.movable]) also offers "Move" when [move] is given (§6.2, §4.15: the second chip, before Undo), which opens
 * the Edit sheet at "Move to aisle" for that item. Main thread only.
 */
class AddToasts(
    private val resources: Resources,
    private val undo: (Long) -> Unit,
    private val move: ((GroceryItem) -> Unit)? = null,
) {
    /** The commits behind each note still on screen or queued, by toast id (the last few only). */
    private val commits = object : LinkedHashMap<Long, List<AddCommit>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<AddCommit>>?): Boolean = size > KEEP
    }

    /** The note for [commit]. */
    fun toast(commit: AddCommit): ToastMessage {
        val note = note(listOf(commit), id = null)
        commits[note.id] = listOf(commit)
        return note
    }

    private fun note(all: List<AddCommit>, id: Long?): ToastMessage {
        val merged = all.reduce(AddCommit::plus)
        val undoLabel = resources.getString(R.string.add_toast_undo)
        val ids = all.map { it.id }
        val moving = merged.added.singleOrNull()?.takeIf { merged.movable }
        val onMove = move
        val base = ToastMessage(
            text = merged.toastText(resources),
            kind = ADD_TOAST_KIND,
            actionLabel = if (merged.canUndo) undoLabel else null,
            onAction = if (merged.canUndo) ({ ids.asReversed().forEach(undo) }) else null,
            merge = ::merge,
            secondaryLabel = if (moving != null && onMove != null) resources.getString(R.string.add_toast_move) else null,
            onSecondary = if (moving != null && onMove != null) ({ onMove(moving) }) else null,
            secondaryGlyph = if (moving != null && onMove != null) PicnicGlyph.Aisles else null,
        )
        return if (id == null) base else base.copy(id = id)
    }

    private fun merge(current: ToastMessage, incoming: ToastMessage): ToastMessage {
        val all = commits.remove(current.id).orEmpty() + commits.remove(incoming.id).orEmpty()
        if (all.isEmpty()) return incoming.copy(id = current.id)
        commits[current.id] = all
        return note(all, current.id)
    }

    private companion object {
        const val KEEP = 8
    }
}

/**
 * Two commits as one (a merged toast): every added, revived and already-there row; the single added row's aisle only
 * when there is one; "as typed" only when every commit that added something was.
 */
operator fun AddCommit.plus(other: AddCommit): AddCommit {
    val adding = listOf(this, other).filter { it.added.isNotEmpty() }
    val added = added + other.added
    return AddCommit(
        id = other.id,
        listId = other.listId,
        listTitle = other.listTitle.ifBlank { listTitle },
        added = added,
        alreadyThere = (alreadyThere + other.alreadyThere).distinctBy { it.id },
        revived = (revived + other.revived).distinctBy { it.id },
        aisleTitle = if (added.size == 1) adding.single().aisleTitle else null,
        asTypedNoKey = adding.isNotEmpty() && adding.all { it.asTypedNoKey },
        asTypedOffline = adding.isNotEmpty() && adding.all { it.asTypedOffline },
        fromBar = fromBar && other.fromBar,
        otherList = if (added.size == 1) adding.single().otherList else other.otherList,
    )
}

/** "Saturday shop" → "Saturday shop" with a no-break space: the note never leaves "shop ♡" alone on its last line (§4.6). */
internal fun lastTwoTogether(name: String): String {
    val trimmed = name.trim()
    val at = trimmed.lastIndexOf(' ')
    return if (at <= 0) trimmed else trimmed.substring(0, at) + '\u00A0' + trimmed.substring(at + 1)
}

/**
 * The commit toast's words (§16): "Added Milk to Dairy & eggs ♡" (one item), "Added 3 items to Saturday shop ♡",
 * "Added as typed · add your OpenAI key to sort it" (rule 2), "Added as typed · sorting waits until you’re back online"
 * (rule 2b, offline with a key: the catch-up sorts it), "Milk is back on your list" (rule 7, joined after the added
 * part), "Milk is already on your list" (nothing new), plus "★ shiny!" for a shiny pull (egg 6). One item names the list
 * instead of its aisle when the aisle would be wrong or out of sight: a typed item with no aisle yet (the background sort
 * is about to place it) and an add to a list she has switched away from.
 */
fun AddCommit.toastText(resources: Resources): String {
    val parts = mutableListOf<String>()
    when {
        added.isEmpty() -> Unit
        asTypedNoKey -> parts += resources.getString(R.string.add_toast_as_typed)
        asTypedOffline -> parts += resources.getString(R.string.add_toast_as_typed_offline)
        added.size == 1 -> {
            val item = added.single()
            val everythingElse = resources.getString(R.string.add_everything_else)
            val sortComing = item.source == ItemSource.TYPED && item.aisleId == null
            val where = if ((otherList || sortComing) && listTitle.isNotBlank()) listTitle else aisleTitle?.takeIf { it.isNotBlank() } ?: everythingElse
            parts += resources.getString(R.string.add_toast_added_one, item.name, lastTwoTogether(where))
        }
        else -> parts += resources.getQuantityString(R.plurals.add_toast_added_many, added.size, added.size, lastTwoTogether(listTitle))
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

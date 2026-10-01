package earth.cora.picnic.ui.add

import android.net.Uri
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.WebSource
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ItemSource

/*
 * UI state of the Add sheet. Names are binding (docs/ARCHITECTURE.md §10.3); fields may be added.
 */

enum class AddMode { WRITE, SNAP, SPEAK }

sealed interface AddPhase {
    data object Idle : AddPhase

    /** Recording voice. [levels] holds the most recent input levels (0..1, oldest first) for the meter. */
    data class Recording(val elapsedMs: Long, val level: Float, val levels: List<Float> = emptyList()) : AddPhase

    data object Transcribing : AddPhase

    /**
     * Waiting for the smart model. [kind] says what it is doing (AI_TEXT, PHOTO, VOICE); [recipe] is a
     * guess that the request is a dish or recipe ("Looking up a recipe") rather than a plain list.
     */
    data class Thinking(val kind: ItemSource, val recipe: Boolean = false) : AddPhase

    /** Results to review: AddUiState.preview with keep toggles. */
    data object Preview : AddPhase

    /**
     * [message] is user-facing. [retryable] offers "Try again", [canUsePlainLines] offers "Add as plain
     * lines" (text and transcripts only), [needsSettings] offers "Open Settings".
     */
    data class Failed(
        val message: String,
        val retryable: Boolean,
        val canUsePlainLines: Boolean,
        val needsSettings: Boolean,
    ) : AddPhase

    /** Anything is running that Cancel can stop. */
    val busy: Boolean get() = this is Recording || this is Transcribing || this is Thinking
}

data class AddUiState(
    /** The list that was current when the sheet opened: items are added there. */
    val listId: String,
    val mode: AddMode = AddMode.WRITE,
    val text: String = "",
    val phase: AddPhase = AddPhase.Idle,
    /** The voice transcript, once there is one. */
    val transcript: String? = null,
    /** The photo being read (camera capture or picker Uri). */
    val photo: Uri? = null,
    /** Proposed items, in walking order (Unsorted first), with keep toggles. */
    val preview: List<PreviewItemUi> = emptyList(),
    /** The model's short note about the result. */
    val message: String? = null,
    /** Web pages the model looked at (shown as Sources chips). */
    val sources: List<WebSource> = emptyList(),
    /** Names left out because they are already on the list. */
    val skipped: List<String> = emptyList(),
    /** How the preview was made: AI_TEXT / PHOTO / VOICE, or TYPED for "Add as plain lines". */
    val previewSource: ItemSource? = null,
    /** "Add N items" was tapped; the sheet closes once the items are saved. */
    val committing: Boolean = false,
) {
    val keptCount: Int get() = preview.count { it.keep }
}

data class PreviewItemUi(
    val key: Int,
    val item: ProposedItem,
    val keep: Boolean,
    /** Aisle name ("Unsorted" when the model found no aisle; the screen shows its own label then). */
    val aisleTitle: String,
    val icon: IconRef,
    /** Aisle position - 1, or -1 for Unsorted (colour family and grouping). */
    val aisleIndex: Int = -1,
)

sealed interface AddEvent {
    /** Items were saved: close the sheet and say "Added N items". */
    data class Added(val count: Int, val listId: String) : AddEvent

    /** Nothing was added (all duplicates) or saving failed: close the sheet and show [text]. */
    data class Message(val text: String) : AddEvent

    /** Background aisle placement of typed items failed (the items stay in Unsorted). */
    data class SortFailed(val friendlyMessage: String, val leftUnsorted: Int) : AddEvent
}

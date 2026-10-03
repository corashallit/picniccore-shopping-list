package earth.cora.picnic.ui.add

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import earth.cora.picnic.core.assistant.PhotoLine
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.RecipeInfo
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.assistant.WebSource
import earth.cora.picnic.core.model.AisleStyle
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ItemSource

/*
 * UI state of the add flows (docs/DESIGN_STICKER.md §6.2, §4.16–§4.21; ARCHITECTURE §10.3). Owner: WP7.
 * The docked add bar and the letter-set Add sheet read the same state: the bar's field and the Write tab's note are
 * one draft ([AddUiState.text]). Names from ARCHITECTURE §10.3 are kept; fields are added.
 */

/** The Add sheet's index tabs (§4.16): Write · Snap · Speak. */
enum class AddMode { WRITE, SNAP, SPEAK }

/** Where a note was sent from: the docked bar (waiting shows as the bar's capsule) or the sheet's Write tab. */
enum class AddOrigin { BAR, SHEET }

/** What the add flows are doing right now. A review ([AddUiState.review]) can stay while Speak records after Snap (D19). */
sealed interface AddPhase {
    data object Idle : AddPhase

    /** Recording voice. [levels] holds the most recent input levels (0..1, oldest first) for the dot equaliser. */
    data class Recording(val elapsedMs: Long, val level: Float, val levels: List<Float> = emptyList()) : AddPhase

    /** The voice note is being turned into words (bee loop). */
    data object Transcribing : AddPhase

    /**
     * The request is out (rule 3): the bar's Send is a wobbling capsule and its field is read-only; in the sheet the
     * thinking box reads "Reading your note…" (it claims nothing, D16) or the polaroid is being read. [kind] is
     * AI_TEXT, PHOTO or VOICE.
     */
    data class Thinking(val kind: ItemSource) : AddPhase

    /** The answer is here and needs her review ([AddUiState.review]). */
    data object Preview : AddPhase

    /**
     * Something went wrong (rule 6). [message] is core's friendly text for an AI error; otherwise [problem] names the
     * app's own copy (strings_add.xml). [retryable] offers "Try again", [canUsePlainLines] "Add as plain lines",
     * [needsSettings] "Open Settings".
     */
    data class Failed(
        val problem: AddProblem,
        val message: String? = null,
        val retryable: Boolean = false,
        val canUsePlainLines: Boolean = false,
        val needsSettings: Boolean = false,
    ) : AddPhase

    /** Anything is running that Cancel can stop. */
    val busy: Boolean get() = this is Recording || this is Transcribing || this is Thinking
}

/** The app's own failure copy (rule 6, §6.12); AI errors carry core's friendly message instead. */
enum class AddProblem { AI, PHOTO_UNREADABLE, CAMERA_UNAVAILABLE, MIC_UNAVAILABLE, MIC_DENIED, TOO_SHORT, NOTHING_HEARD, SAVE_FAILED }

/** The photo being read (§4.19): a small decoded copy for the polaroid (the capture file itself is deleted after reading). */
@Immutable
data class SnapPhoto(val thumb: ImageBitmap?, val takenAt: Long)

data class AddUiState(
    /** The list items go to (the current list when the bar or the sheet was used). */
    val listId: String,
    val mode: AddMode = AddMode.WRITE,
    /** The draft: the add bar's field and the Write tab's note are the same text. */
    val text: String = "",
    val phase: AddPhase = AddPhase.Idle,
    /** The letter-set Add sheet is up. The ViewModel opens it for a review (rule 5), Snap and Speak. */
    val sheetOpen: Boolean = false,
    /** Where the last note was sent from. */
    val origin: AddOrigin = AddOrigin.BAR,
    /** The note that was sent (the Write review shows it; the web-search step quotes it). */
    val sentText: String? = null,
    /** The photo being read or reviewed. */
    val photo: SnapPhoto? = null,
    /** The voice transcript, once there is one. */
    val transcript: String? = null,
    /** The answer under review: the recipe card, the polaroid's stickers, the Heard tray. */
    val review: AddReview? = null,
    /** "Stick all" was tapped; the sheet closes once the items are saved. */
    val committing: Boolean = false,
) {
    /** The rows that "Stick all n on my list" adds. */
    val keptCount: Int get() = review?.kept ?: 0

    /** v1 name: the rows of the review. */
    val preview: List<ReviewRow> get() = review?.rows.orEmpty()

    /** The add bar's waiting state (rule 3). */
    val waiting: Boolean get() = phase is AddPhase.Thinking

    /** Speak is recording (the REC dot on the Speak tab, §4.16). */
    val recording: Boolean get() = phase is AddPhase.Recording

    /**
     * A tab she can switch to: none while something runs (a photo being read, a voice note recording or being written
     * down, a note being read, a save), since the tap would do nothing; otherwise every tab while no review is open,
     * and with one only the tabs that show it. A new note or photo would replace a review she has not committed, so
     * those tabs are dimmed until it is stuck on or closed. A dimmed tab reads "disabled" to TalkBack.
     */
    fun tabEnabled(mode: AddMode): Boolean = !phase.busy && !committing && (review?.showsOn(mode) ?: true)
}

/**
 * The tabs a review shows on: a note's on Write, a photo's on Snap (D19), a voice note's on Speak; and every review on
 * Speak, since a voice note adds to any open review as heard rows.
 */
fun AddReview.showsOn(mode: AddMode): Boolean = when (source) {
    ItemSource.PHOTO -> mode != AddMode.WRITE
    ItemSource.VOICE -> mode == AddMode.SPEAK
    else -> mode != AddMode.SNAP
}

/** Which batch a review row belongs to: the answer itself, or the voice note spoken after a photo (D19). */
enum class RowBatch { MAIN, HEARD }

/**
 * One proposed row (§4.17). [keep] = it goes on the list. A row already on the list ([canKeep] false) can never be
 * kept (duplicates are never added twice, rule 7). [aisleIndex] is the walking position − 1, or −1 for "Everything else";
 * [style] is the aisle editor's look override, so the tray matches the list.
 */
@Immutable
data class ReviewRow(
    val key: Int,
    val item: ProposedItem,
    val keep: Boolean,
    val batch: RowBatch = RowBatch.MAIN,
    /** The aisle's display name (store prefix stripped); blank for "Everything else". */
    val aisleTitle: String = "",
    val aisleIndex: Int = -1,
    /** The store's own aisle number (the "A2" chip), or null. */
    val storeAisle: Int? = null,
    val style: AisleStyle? = null,
    val icon: IconRef,
    /** The row came from the offline parse ("Add as plain lines"): it commits as TYPED, so the aisle sort places it. */
    val typed: Boolean = false,
) {
    val skipReason: SkipReason get() = item.skipReason
    val canKeep: Boolean get() = item.skipReason != SkipReason.ALREADY_ON_LIST
}

/**
 * The answer under review (rule 5). [id] changes with every new answer (the thinking steps and the fan-out replay only
 * for a new id); [heardId] is the latest voice batch's own reveal (D19: after a photo, a note or another voice note).
 * [revealed] holds the reveal ids that have played, so returning from My pantry shows the card as she left it, not a
 * replay.
 */
@Immutable
data class AddReview(
    val id: Long,
    val source: ItemSource,
    val rows: List<ReviewRow>,
    val steps: List<ThinkingStep>,
    val recipe: RecipeInfo? = null,
    val webSearch: Boolean = false,
    val sources: List<WebSource> = emptyList(),
    /** The model's note when it is worth showing (rule 4), else null. */
    val message: String? = null,
    /** Photos: the lines read off the paper (§4.19). */
    val lines: List<PhotoLine> = emptyList(),
    val heardId: Long? = null,
    val heardSteps: List<ThinkingStep> = emptyList(),
    /** The keys of the latest heard batch (they fan out on [heardId]; earlier heard rows stay as they were). */
    val heardFresh: Set<Int> = emptySet(),
    val revealed: Set<Long> = emptySet(),
    /**
     * The rows are the note's or the transcript's plain lines after a failed AI call (ARCHITECTURE §10.4): no AI ran,
     * so no thinking box and no steps; the rows fan out straight into the card or the Heard tray.
     */
    val plain: Boolean = false,
) {
    val kept: Int get() = rows.count { it.keep }
    val mainRows: List<ReviewRow> get() = rows.filter { it.batch == RowBatch.MAIN }
    val heardRows: List<ReviewRow> get() = rows.filter { it.batch == RowBatch.HEARD }
    val hasPantryRow: Boolean get() = rows.any { it.skipReason == SkipReason.IN_PANTRY }

    /** Nothing here could be switched on (no rows, or only names already on her list): the footer offers "OK". */
    val nothingToKeep: Boolean get() = rows.none { it.canKeep }
}

/**
 * One line of the thinking box (§4.18), revealed only from real fields of the answer (D16): the web search only if it
 * ran, the recipe meta only if present, the pantry only with `in_pantry` rows (their count), the sort always.
 */
sealed interface ThinkingStep {
    /** "Searched the web: “<query>”" with the model's own query, or "Searched the web" when the answer names none. */
    data class SearchedWeb(val query: String?) : ThinkingStep
    data class ComparedRecipes(val recipes: Int?, val servings: Int?) : ThinkingStep
    data class CheckedPantry(val leftOut: Int) : ThinkingStep
    data class SortedAisles(val aisles: Int) : ThinkingStep
}

/**
 * A commit (§6.2 "Commit"): what the list shows next. [added] rows are laid out at once and their stickers pop in
 * [popDelayMillis] apart, in their aisles; [alreadyThere] rows pop with a highlighter flash ("Milk is already on your
 * list"); [revived] rows were un-checked ("Milk is back on your list"; the un-check chain plays). The toast's Undo calls
 * [AddViewModel.undo] with [id] and removes exactly the added items (and checks the revived ones again).
 */
@Immutable
data class AddCommit(
    val id: Long,
    val listId: String,
    val listTitle: String,
    val added: List<GroceryItem>,
    val alreadyThere: List<GroceryItem>,
    val revived: List<GroceryItem>,
    /** The single added item's aisle display name ("Dairy & eggs"), "" for Everything else; null with 0 or 2+ items. */
    val aisleTitle: String? = null,
    /** Rule 2: no key, so the text went on as typed ("Added as typed · add your OpenAI key for smart sorting"). */
    val asTypedNoKey: Boolean = false,
) {
    val canUndo: Boolean get() = added.isNotEmpty() || revived.isNotEmpty()

    /** True when nothing changed (no new, revived or already-there rows). */
    val empty: Boolean get() = added.isEmpty() && revived.isEmpty() && alreadyThere.isEmpty()

    /** Shiny pulls among the added rows (egg 6: the toast adds "★ shiny!"). */
    val shiny: Boolean get() = added.any { it.rarity != earth.cora.picnic.core.model.Rarity.NONE }

    /** When the [index]th added row's sticker pops in (§6.2: 80 ms apart). */
    fun popDelayMillis(index: Int): Long = index * POP_STAGGER_MS

    companion object {
        /** New rows pop in this far apart (§6.2 "Commit"). */
        const val POP_STAGGER_MS: Long = 80
    }
}

/** One-off events for the List screen (WP5 places the bar, the sheet and the toasts). */
sealed interface AddEvent {
    /** Items were saved (or found already there): show the toast, pop the rows, offer Undo when [AddCommit.canUndo]. */
    data class Committed(val commit: AddCommit) : AddEvent

    /** Undo of [commitId] ran: the items are gone again. */
    data class Undone(val commitId: Long) : AddEvent

    /** Saving failed ("Couldn't save. Try again."). */
    data object SaveFailed : AddEvent

    /** Background aisle placement of typed items failed (they stay in Everything else). */
    data class SortFailed(val friendlyMessage: String, val leftUnsorted: Int) : AddEvent

    /** The field should take focus and open the keyboard (the FAB was tapped, "Start my list"). */
    data object FocusField : AddEvent
}

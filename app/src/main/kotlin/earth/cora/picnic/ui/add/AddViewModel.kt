package earth.cora.picnic.ui.add

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.assistant.AudioClip
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.OfflineParser
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.assistant.toGroceryItem
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.AddOutcome
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.PantryMatch
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.displayName
import earth.cora.picnic.core.model.ingestContext
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.removeItem
import earth.cora.picnic.core.model.setRarity
import earth.cora.picnic.core.model.restoreItem
import earth.cora.picnic.core.model.storeAisleNumber
import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.core.store.TripFiling
import earth.cora.picnic.R
import earth.cora.picnic.icons.IconRepository
import earth.cora.picnic.platform.Announcer
import earth.cora.picnic.platform.AudioRecorder
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpAnnouncer
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.PhotoPreparer
import earth.cora.picnic.ui.eggs.NoOpSurpriseEngine
import earth.cora.picnic.ui.eggs.SurpriseEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * The add flows (docs/DESIGN_STICKER.md §6.2; ARCHITECTURE §10.4): the docked bar's Send, the letter-set sheet's Write,
 * Snap and Speak tabs, quick add versus review, the recipe card's keep switches, Snap + Speak merged (D19), duplicates
 * (rule 7), the commit and its Undo. Owner: WP7.
 *
 * Send rules (§6.2), in order: 1 the magic word is intercepted before any network call (nothing is added, the field
 * clears); 2 no key: the text is parsed as typed and committed at once; 3 otherwise one ingest call, the bar's Send a
 * wobbling capsule meanwhile; 4 a small sure answer commits at once; 5 anything else opens the sheet for review; 6 a
 * failure shows the friendly message with Try again and Add as plain lines; 7 duplicates are never added twice (an
 * unchecked one pops, a checked one is revived); 8 the pantry: worked-out items it covers come back skipped and force a
 * review.
 *
 * Scopes (ARCHITECTURE §10.3, §13): the calls she waits for (photo prep, transcription, ingest) run in viewModelScope
 * as [work] and are cancelled by [cancel] / [closeSheet]; every commit (quick add, As typed, no key, Stick all), the
 * background aisle sort after it, and Undo run in [appScope], so closing the sheet or stopping a later request never
 * loses a save or leaves typed items unsorted. Process death: the draft, the mode, the target list and
 * whether the sheet was up live in [savedState]; a camera result can reach a fresh VM before the List screen calls
 * [showList], so the target list then falls back to the current list.
 */
class AddViewModel(
    private val store: StateStore,
    private val assistant: GroceryAssistant,
    private val icons: IconRepository,
    private val aisleSync: AisleSync,
    private val catalog: IconCatalog,
    private val photos: PhotoPreparer,
    private val recorderFactory: () -> AudioRecorder,
    private val clock: () -> Long,
    private val newId: () -> String,
    private val appScope: CoroutineScope,
    private val savedState: SavedStateHandle,
    /** Whether an OpenAI key is set (rule 2). */
    private val hasKey: suspend () -> Boolean = { true },
    private val surprises: SurpriseEngine = NoOpSurpriseEngine,
    private val feedback: Feedback = NoOpFeedback,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Decodes the photo's JPEG into the polaroid's small copy (null when it cannot). */
    private val thumbs: suspend (ByteArray) -> ImageBitmap? = ::decodeThumb,
    /** Polite TalkBack lines (§15: the loader's label, once). */
    private val announcer: Announcer = NoOpAnnouncer,
    /** The app's words for [announcer] (a string resource id to its text). */
    private val words: (Int) -> String = { "" },
    /**
     * The album: an add more than 12 h after a trip's last activity files that trip's book first (§0.4 Q9's add-side
     * twin, TripFiling.addItems). Null (tests that only add) adds straight to the list.
     */
    private val album: AlbumStore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        AddUiState(
            listId = savedState.get<String>(KEY_LIST).orEmpty(),
            mode = savedState.get<String>(KEY_MODE)?.let { m -> AddMode.entries.firstOrNull { it.name == m } } ?: AddMode.WRITE,
            text = savedState.get<String>(KEY_TEXT).orEmpty(),
            sheetOpen = savedState.get<Boolean>(KEY_SHEET) ?: false,
        ),
    )
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    init {
        // What survives process death: the draft, the mode, the target list and the open sheet (never photos or audio).
        viewModelScope.launch {
            _state.map { Saved(it.listId, it.mode, it.text, it.sheetOpen) }.distinctUntilChanged().collect { saved ->
                savedState[KEY_LIST] = saved.listId
                savedState[KEY_MODE] = saved.mode.name
                savedState[KEY_TEXT] = saved.text
                savedState[KEY_SHEET] = saved.sheetOpen
            }
        }
        // Back from My pantry (the recipe card's "Edit my pantry"): the open review is checked against the pantry as it
        // is now.
        viewModelScope.launch {
            store.states.map { st -> st.pantry.map { it.name } }.distinctUntilChanged().collect(::recheckPantry)
        }
    }

    /**
     * The pantry changed while a review is open: a worked-out row the pantry now covers is switched off and reads "in
     * your pantry", as the answer would have marked it (§4.17; she can switch it back on). An "in your pantry" row whose
     * entry she took off the shelves (she ran out) is switched back on and reads like any worked-out row, unless
     * another entry still covers it. Rows she named are never skipped for the pantry; a pantry row she switched on by
     * hand, and rows skipped for another reason, stay as they are.
     */
    private fun recheckPantry(pantry: List<String>) {
        _state.update { s ->
            val review = s.review?.takeIf { !s.committing } ?: return@update s
            var changed = false
            val rows = review.rows.map { row ->
                val item = row.item
                if (item.named) return@map row
                when (item.skipReason) {
                    SkipReason.NONE -> {
                        val entry = PantryMatch.coveringEntry(pantry, item.name) ?: return@map row
                        changed = true
                        row.copy(item = item.copy(skipReason = SkipReason.IN_PANTRY, pantryEntry = entry), keep = false)
                    }
                    SkipReason.IN_PANTRY -> {
                        // Switched on by hand, no entry named (the answer's own judgement, "spices" for cinnamon), or the
                        // entry still on the shelves: as it is.
                        val was = item.pantryEntry?.takeIf { it.isNotBlank() }
                        if (row.keep || was == null || pantry.any { PantryMatch.covers(it, was) }) return@map row
                        changed = true
                        val entry = PantryMatch.coveringEntry(pantry, item.name)
                        if (entry != null) {
                            row.copy(item = item.copy(pantryEntry = entry))
                        } else {
                            row.copy(item = item.copy(skipReason = SkipReason.NONE, pantryEntry = null), keep = true)
                        }
                    }
                    else -> row
                }
            }
            if (changed) s.copy(review = review.copy(rows = rows)) else s
        }
    }

    private val _events = Channel<AddEvent>(Channel.BUFFERED)

    /** One-off events for the List screen: the commit (toast, pops, Undo), sort failures, save failures, focus. */
    val events: Flow<AddEvent> = _events.receiveAsFlow()

    /** The call she is waiting for (photo prep, transcription, ingest). Main thread only. */
    private var work: Job? = null

    /** Level polling while recording. Main thread only. */
    private var meter: Job? = null
    private var recorder: AudioRecorder? = null

    /** What "Try again" repeats (holds the photo or recording bytes while the sheet is open). */
    @Volatile
    private var retryAction: (() -> Unit)? = null

    private val ids = AtomicLong(0)

    /** Reveals whose capsule pop has been cued (once each). Main thread only. */
    private val poppedReveals = HashSet<Long>()

    /** What each recent commit's Undo takes back (the last [MAX_UNDO] commits). */
    private val undoRecords = LinkedHashMap<Long, UndoRecord>()

    /* ---------------- the target list and the sheet ---------------- */

    /**
     * The list the List screen shows now. [AddUiState.listId] is where the add in flight goes; it follows this one as soon
     * as the flows are idle again ([withShownList]), so a list switched while a note was being read never keeps every
     * later add aimed at the old list.
     */
    @Volatile
    private var shownListId: String = ""

    /**
     * The List screen shows [listId]: the bar adds there. Non-destructive: while something runs or a review is up, the
     * target stays (a camera result may already be being read after process death), and the flows move to [listId] once
     * that is done.
     */
    fun showList(listId: String) {
        if (listId.isEmpty()) return
        shownListId = listId
        _state.update { s ->
            val idle = !s.phase.busy && s.review == null && !s.committing
            if (s.listId.isEmpty() || (idle && s.listId != listId)) s.copy(listId = listId) else s
        }
    }

    /** v1 name for [showList] (after process death). */
    fun attachList(listId: String) = showList(listId)

    /** Opens the letter-set sheet on [mode] for [listId] (the List screen's Add, the empty state). Keeps the draft. */
    fun openSheet(mode: AddMode = AddMode.WRITE, listId: String? = null) {
        if (listId != null) showList(listId)
        _state.update { it.copy(sheetOpen = true, mode = if (it.phase.busy || it.review != null) it.mode else mode) }
    }

    /** v1 name: opens the sheet on [mode] for [listId]. */
    fun open(listId: String, mode: AddMode = AddMode.WRITE) = openSheet(mode, listId)

    /**
     * The sheet was dismissed (Back, the scrim, swipe): cancels whatever runs and forgets the review, the photo and the
     * transcript; the draft text stays, so a sheet closed by accident loses no typing.
     */
    fun closeSheet() {
        stopWork()
        _state.update { it.cleared().copy(sheetOpen = false, committing = it.committing) }
    }

    /** v1 name for [closeSheet]. */
    fun close() = closeSheet()

    /**
     * Switches the sheet's tab. With a review open, only the tabs that show it switch (Speak adds to any review, D19);
     * a new note or photo would replace a review she has not committed, so those tabs wait (dimmed) until it is done.
     */
    fun setMode(mode: AddMode) {
        val s = _state.value
        if (s.mode == mode || s.phase.busy || s.committing || !s.tabEnabled(mode)) return
        _state.update { it.copy(mode = mode, phase = if (it.phase is AddPhase.Failed) it.restingPhase() else it.phase) }
    }

    fun setText(text: String) {
        val s = _state.value
        if (s.phase is AddPhase.Thinking && s.origin == AddOrigin.BAR) return // the field is read-only while waiting
        // Editing after a failure brings the normal buttons back.
        _state.update { it.copy(text = text, phase = if (it.phase is AddPhase.Failed && it.mode == AddMode.WRITE) it.restingPhase() else it.phase) }
    }

    /** Asks the List screen to focus the bar's field (the FAB, "Start my list"). */
    fun requestFieldFocus() {
        _events.trySend(AddEvent.FocusField)
    }

    /* ---------------- Write: send, as typed ---------------- */

    /** Send (the bar's envelope, Enter, the Write tab's Send): the §6.2 send rules. */
    fun send(origin: AddOrigin = AddOrigin.BAR) {
        val s = _state.value
        val text = s.text.trim()
        // A review she has not committed is never replaced by a new note (its tab shows no Send meanwhile).
        if (s.phase.busy || s.committing || s.review != null || work?.isActive == true) return
        if (text.isEmpty()) {
            if (origin == AddOrigin.BAR) requestFieldFocus()
            return
        }
        // Rule 1: the magic word never reaches the network and adds nothing; the field clears.
        if (surprises.interceptText(text)) {
            _state.update { it.copy(text = "") }
            return
        }
        retryAction = null
        _state.update { it.withShownList() }
        work = viewModelScope.launch {
            if (!hasKey()) {
                // Rule 2: no key, so the text goes on as typed, with the toast that says how to get smart sorting.
                commitTyped(text, noKey = true, fromBar = origin == AddOrigin.BAR)
                return@launch
            }
            runTextIngest(text, origin)
        }
    }

    /**
     * Snap and Speak need the key before anything is captured (rule 2 covers only text): without one, the sheet opens on
     * [mode] with "Add your OpenAI key in Settings first." and Open Settings, and [capture] (the camera, the picker, the
     * microphone and its permission prompt) never runs, so no photo is taken and no voice note recorded only to be
     * refused. With a key, [capture] runs at once.
     */
    fun requireKey(mode: AddMode, capture: () -> Unit) {
        viewModelScope.launch {
            if (!needsKey()) {
                capture()
                return@launch
            }
            val s = _state.value
            if (s.phase.busy || s.committing) return@launch
            retryAction = null
            _state.update {
                it.copy(
                    sheetOpen = true,
                    // An open review stays; the tab moves only where that review shows (Speak always does, D19).
                    mode = if (it.review == null || it.review.showsOn(mode)) mode else it.mode,
                    phase = AddPhase.Failed(AddProblem.AI, message = LlmError.NotConfigured("no key").friendlyMessage, needsSettings = true),
                )
            }
        }
    }

    /** True when no OpenAI key is set, so Snap and Speak would only be refused ([requireKey]). */
    suspend fun needsKey(): Boolean = !hasKey()

    /** v1 name for [send] from the sheet. */
    fun submitText() = send(AddOrigin.SHEET)

    /** "As typed" (Write tab): no AI; the offline parse commits at once. */
    fun addAsTyped() {
        val s = _state.value
        val text = s.text.trim()
        if (text.isEmpty() || s.phase.busy || s.committing || s.review != null || work?.isActive == true) return
        retryAction = null
        _state.update { it.withShownList() }
        commitTyped(text, noKey = false)
    }

    /** v1 name for [addAsTyped]. */
    fun addTyped() = addAsTyped()

    /** The note under review goes back to editing (the Write review's pencil): the review is dropped, the text kept. */
    fun editNote() {
        stopWork()
        _state.update { it.cleared().copy(mode = AddMode.WRITE) }
    }

    /** v1 name for [editNote]. */
    fun startOver() = editNote()

    /* ---------------- Snap ---------------- */

    /**
     * A photo from the camera or the picker: the sheet opens on Snap with the polaroid being read. A review she has not
     * committed is never dropped for it (the Snap tab offers no photo while one is open), so a stray result is discarded.
     */
    fun onPhoto(uri: Uri) {
        val s = _state.value
        if (s.phase.busy || s.committing || s.review != null) {
            appScope.launch { photos.discard(uri) }
            return
        }
        work?.cancel()
        retryAction = null
        _state.update {
            it.cleared().copy(
                sheetOpen = true, mode = AddMode.SNAP, phase = AddPhase.Thinking(ItemSource.PHOTO),
                photo = SnapPhoto(thumb = null, takenAt = clock()),
            )
        }
        work = viewModelScope.launch {
            val bytes = try {
                photos.jpegBytes(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                photoUnreadable()
                return@launch
            } catch (e: RuntimeException) {
                photoUnreadable()
                return@launch
            } finally {
                withContext(NonCancellable) { photos.discard(uri) }
            }
            val thumb = try {
                thumbs(bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update { it.copy(photo = it.photo?.copy(thumb = thumb)) }
            runPhotoIngest(bytes)
        }
    }

    fun cameraUnavailable() = fail(AddProblem.CAMERA_UNAVAILABLE, mode = AddMode.SNAP, open = true)

    /** The photo could not be opened: no polaroid stays behind, so the Snap tab offers another photo (chip and pill). */
    private fun photoUnreadable() {
        _state.update { it.copy(photo = null, committing = false, phase = AddPhase.Failed(AddProblem.PHOTO_UNREADABLE)) }
    }

    /* ---------------- Speak ---------------- */

    /**
     * Call once RECORD_AUDIO is granted (the route asks): the sheet opens on Speak with the cassette recording ("clunk"
     * + KEYBOARD_TAP). With a review open (a photo's, D19, or a note's or an earlier voice note's) the recording adds to
     * it as heard rows, de-duplicated, so nothing she has not committed is lost; the tab stays where it is.
     */
    fun startRecording() {
        val s = _state.value
        if (s.phase.busy || s.committing) return
        retryAction = null
        val r = recorderFactory()
        try {
            r.start()
        } catch (e: IOException) {
            fail(AddProblem.MIC_UNAVAILABLE, mode = AddMode.SPEAK, open = true)
            return
        } catch (e: RuntimeException) {
            fail(AddProblem.MIC_UNAVAILABLE, mode = AddMode.SPEAK, open = true)
            return
        }
        recorder = r
        feedback.cue(Cue.CASSETTE)
        val started = clock()
        val merging = s.review != null
        _state.update {
            val base = if (merging) it else it.cleared()
            base.copy(
                sheetOpen = true,
                mode = if (merging && it.mode != AddMode.WRITE) it.mode else AddMode.SPEAK,
                phase = AddPhase.Recording(0, 0f),
                transcript = null,
            )
        }
        meter = viewModelScope.launch {
            val levels = ArrayDeque<Float>()
            while (true) {
                delay(LEVEL_POLL_MS)
                val level = r.level().coerceIn(0f, 1f)
                levels.addLast(level)
                if (levels.size > METER_SAMPLES) levels.removeFirst()
                _state.update { it.copy(phase = AddPhase.Recording(clock() - started, level, levels.toList())) }
                if (r.reachedLimit()) {
                    stopRecording()
                    break
                }
            }
        }
    }

    fun micDenied() = fail(AddProblem.MIC_DENIED, mode = AddMode.SPEAK, open = true)

    /** The stop sticker (also on Lifecycle ON_STOP): stop ("clunk"), transcribe, then read the transcript. */
    fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        meter?.cancel()
        meter = null
        feedback.cue(Cue.CASSETTE)
        // The LCD keeps the recording's length while the words are written down.
        _state.update { it.copy(phase = AddPhase.Transcribing((it.phase as? AddPhase.Recording)?.elapsedMs ?: 0L)) }
        work = viewModelScope.launch {
            val file = r.stop()
            if (file == null) {
                fail(AddProblem.TOO_SHORT, mode = _state.value.mode)
                return@launch
            }
            val bytes = try {
                withContext(ioDispatcher) { file.readBytes() }
            } catch (e: IOException) {
                fail(AddProblem.TOO_SHORT, mode = _state.value.mode)
                return@launch
            } finally {
                withContext(NonCancellable + ioDispatcher) { file.delete() }
            }
            transcribe(bytes)
        }
    }

    /** Lifecycle ON_STOP: recording is foreground-only, so stop and keep what was said. */
    fun onStop() {
        if (recorder != null) stopRecording()
    }

    /* ---------------- the review ---------------- */

    /** Cancel while busy: stops the call or the recording; a review that was already there stays. */
    fun cancel() {
        stopWork()
        _state.update { it.copy(phase = it.restingPhase(), committing = false).withShownList() }
    }

    /** A keep switch (§4.17): a pantry row switched on is added this time only; the pantry never changes. */
    fun toggleKeep(key: Int) {
        _state.update { s ->
            val review = s.review ?: return@update s
            s.copy(review = review.copy(rows = review.rows.map { if (it.key == key && it.canKeep) it.copy(keep = !it.keep) else it }))
        }
    }

    /**
     * "OK" under a review with nothing she could switch on (an answer with no items, or only names already on her
     * list): the names already there pop on the list (rule 7, "Milk is already on your list"); otherwise the sheet closes
     * and the draft stays.
     */
    fun acknowledge() {
        val s = _state.value
        val review = s.review ?: return
        if (s.committing || s.phase.busy) return
        if (review.rows.any { it.skipReason == SkipReason.ALREADY_ON_LIST }) commit() else closeSheet()
    }

    /** The thinking box's capsule popped for [revealId] ("pon!" + CONFIRM, §14.3): once per reveal, never on a replay. */
    fun capsulePopped(revealId: Long) {
        if (poppedReveals.add(revealId)) feedback.cue(Cue.CAPSULE_POP)
    }

    /** The sheet finished revealing [revealId] (steps, lines, fan-out): it never replays for that answer. */
    fun revealFinished(revealId: Long) {
        _state.update { s ->
            val review = s.review ?: return@update s
            if (revealId in review.revealed) s else s.copy(review = review.copy(revealed = review.revealed + revealId))
        }
    }

    /** "Try again" after a retryable failure (the photo or recording bytes are still in memory). */
    fun retry() {
        val action = retryAction ?: return
        if (_state.value.phase.busy) return
        action()
    }

    /**
     * "Add as plain lines" after a failed AI call (ARCHITECTURE §10.4): the note (or the transcript) split into lines with
     * no AI (OfflineParser), then a review, so she can switch off any fragment before it lands ("oh and frozen corn" is
     * a spoken sentence, not a name). A note's lines open the Write tab's card, a voice note's the Speak tab's Heard
     * tray; with a review already open the transcript's lines join it as heard rows (D19). The rows commit as TYPED, so
     * the ones with no aisle are placed in the background after "Stick all".
     */
    fun usePlainLines() {
        val s = _state.value
        if (s.phase.busy || s.committing) return
        val voice = s.transcript?.takeIf { it.isNotBlank() && (s.mode != AddMode.WRITE) }
        val text = voice ?: s.sentText ?: s.text
        if (text.isBlank()) return
        retryAction = null
        work?.cancel()
        work = viewModelScope.launch {
            val st = store.current()
            val ctx = st.ingestContext(targetListId())
            val lines = plainLines(text, ctx)
            if (voice != null && _state.value.review != null) {
                appendHeard(lines, st, ctx, steps = emptyList(), typed = true)
            } else {
                val source = if (voice != null) ItemSource.VOICE else ItemSource.TYPED
                val review = AddReview(
                    id = ids.incrementAndGet(),
                    source = source,
                    rows = AddRules.rows(lines, ctx.aisles, st.store.aisleStyles, RowBatch.MAIN, firstKey = 0).map { it.copy(typed = true) },
                    steps = emptyList(),
                    plain = true,
                )
                _state.update {
                    it.copy(
                        sheetOpen = true, phase = AddPhase.Preview, review = review, committing = false,
                        mode = if (source == ItemSource.VOICE) AddMode.SPEAK else AddMode.WRITE,
                    )
                }
            }
        }
    }

    /**
     * The offline parse of [text] for a review: every line, a name already unchecked on the list marked "already on your
     * list" (rule 7: it pops on commit and is never added twice; a checked one stays a row and revives).
     */
    private fun plainLines(text: String, ctx: IngestContext): List<ProposedItem> {
        val unchecked = ctx.existingNames.mapTo(HashSet()) { normalizeName(it) }
        return OfflineParser.parse(text, catalog, ctx.copy(existingNames = emptyList())).map {
            if (normalizeName(it.name) in unchecked) it.copy(skipReason = SkipReason.ALREADY_ON_LIST) else it
        }
    }

    /**
     * "Stick all n on my list": commits the kept rows in the app scope; the sheet closes once they are saved. Rows
     * already on her list go along so their stickers pop (rule 7); they are never added twice.
     */
    fun commit() {
        val s = _state.value
        val review = s.review ?: return
        if (s.committing || s.phase.busy) return
        // The answer's rows, then the heard rows (D19), each in row order; plain lines commit as TYPED (sorted later).
        val rows = review.rows.filter { it.keep || it.skipReason == SkipReason.ALREADY_ON_LIST }.sortedBy { it.key }
        if (rows.isEmpty()) return
        _state.update { it.copy(committing = true) }
        val batches = rows
            .groupBy { row -> if (row.typed) ItemSource.TYPED else if (row.batch == RowBatch.HEARD) ItemSource.VOICE else review.source }
            .map { (source, group) -> source to group.map { it.item } }
        // A note's review (its answer or its plain lines) used the draft: it clears once the rows are on the list.
        val clearsText = review.source == ItemSource.AI_TEXT || review.source == ItemSource.TYPED
        appScope.launch { commitItems(targetListId(), batches, noKey = false, clearsText = clearsText) }
    }

    /** The toast's Undo for [commitId]: removes exactly the items that commit added and checks the revived ones again. */
    fun undo(commitId: Long) {
        val record = synchronized(undoRecords) { undoRecords.remove(commitId) } ?: return
        appScope.launch {
            val now = clock()
            try {
                store.update { st ->
                    var next = st
                    for (id in record.added) next = next.removeItem(record.listId, id, now)
                    for ((index, before) in record.revived) {
                        next = next.removeItem(record.listId, before.id, now).restoreItem(record.listId, before, index, now)
                    }
                    next
                }
                _events.send(AddEvent.Undone(commitId))
            } catch (e: IOException) {
                _events.send(AddEvent.SaveFailed)
            }
        }
    }

    override fun onCleared() {
        recorder?.cancel()
        recorder = null
    }

    /* ---------------- internals ---------------- */

    private suspend fun runTextIngest(text: String, origin: AddOrigin) {
        _state.update {
            it.copy(
                phase = AddPhase.Thinking(ItemSource.AI_TEXT), origin = origin, sentText = text, review = null,
                photo = null, transcript = null, mode = if (origin == AddOrigin.SHEET) AddMode.WRITE else it.mode,
            )
        }
        // §15 "loader labels once": the bar's Send became the capsule (the focused node is gone), so TalkBack hears what
        // is happening. The sheet's thinking box says it through its own polite headline.
        if (origin == AddOrigin.BAR) announcer.polite(words(R.string.add_thinking_reading))
        val st = store.current()
        val ctx = st.ingestContext(targetListId())
        val result = try {
            assistant.ingest(IngestInput.Text(text), ctx)
        } catch (e: LlmError) {
            retryAction = { send(origin) }
            failAi(e, canUsePlainLines = true, mode = AddMode.WRITE)
            return
        }
        if (AddRules.isQuickAdd(result, text)) {
            // Rule 4: small and sure (nothing skipped), so it commits at once; a checked name revives (rule 7).
            feedback.cue(Cue.CAPSULE_POP)
            _state.update { it.copy(committing = true, phase = AddPhase.Idle) }
            val listId = targetListId()
            val fromBar = origin == AddOrigin.BAR
            appScope.launch { commitItems(listId, listOf(ItemSource.AI_TEXT to result.items), noKey = false, clearsText = true, fromBar = fromBar) }
            return
        }
        // Rule 5: review in the sheet's Write tab, the thinking box first.
        showReview(result, ItemSource.AI_TEXT, query = text, st = st, ctx = ctx, mode = AddMode.WRITE)
    }

    private suspend fun runPhotoIngest(bytes: ByteArray) {
        _state.update { it.copy(phase = AddPhase.Thinking(ItemSource.PHOTO)) }
        val st = store.current()
        val ctx = st.ingestContext(targetListId())
        val result = try {
            assistant.ingest(IngestInput.Image(bytes), ctx)
        } catch (e: LlmError) {
            retryAction = { work = viewModelScope.launch { runPhotoIngest(bytes) } }
            failAi(e, canUsePlainLines = false, mode = AddMode.SNAP)
            return
        }
        showReview(result, ItemSource.PHOTO, query = null, st = st, ctx = ctx, mode = AddMode.SNAP)
    }

    private suspend fun transcribe(bytes: ByteArray) {
        _state.update { it.copy(phase = it.phase as? AddPhase.Transcribing ?: AddPhase.Transcribing()) }
        val transcript = try {
            assistant.transcribe(AudioClip(bytes), keywords())
        } catch (e: LlmError) {
            retryAction = { work = viewModelScope.launch { transcribe(bytes) } }
            failAi(e, canUsePlainLines = false, mode = _state.value.mode)
            return
        }
        if (transcript.isEmpty) {
            fail(AddProblem.NOTHING_HEARD, mode = _state.value.mode)
            return
        }
        val text = transcript.text.trim()
        _state.update { it.copy(transcript = text) }
        runVoiceIngest(text)
    }

    private suspend fun runVoiceIngest(text: String) {
        _state.update { it.copy(phase = AddPhase.Thinking(ItemSource.VOICE)) }
        val st = store.current()
        val ctx = st.ingestContext(targetListId())
        val result = try {
            assistant.ingest(IngestInput.Transcript(text), ctx)
        } catch (e: LlmError) {
            retryAction = { work = viewModelScope.launch { runVoiceIngest(text) } }
            failAi(e, canUsePlainLines = true, mode = _state.value.mode)
            return
        }
        val review = _state.value.review
        if (review != null) {
            // D19: Speak after Snap (or after a note, or a second voice note) appends a batch to the same preview,
            // merged and de-duplicated; what she has not committed stays.
            appendHeard(result.items, st, ctx, AddRules.thinkingSteps(result))
        } else {
            showReview(result, ItemSource.VOICE, query = text, st = st, ctx = ctx, mode = AddMode.SPEAK)
        }
    }

    /** D19: [items] join the open review as a heard batch; [typed] rows came from plain lines and commit as TYPED. */
    private fun appendHeard(items: List<ProposedItem>, st: AppState, ctx: IngestContext, steps: List<ThinkingStep>, typed: Boolean = false) {
        _state.update { s ->
            val review = s.review ?: return@update s.copy(phase = AddPhase.Idle)
            val fresh = AddRules.newHeard(review.rows.map { it.item.name }, items)
            val firstKey = (review.rows.maxOfOrNull { it.key } ?: -1) + 1
            val rows = AddRules.rows(fresh, ctx.aisles, st.store.aisleStyles, RowBatch.HEARD, firstKey).map { it.copy(typed = typed) }
            s.copy(
                phase = AddPhase.Preview,
                review = review.copy(
                    rows = review.rows + rows,
                    heardId = ids.incrementAndGet(),
                    heardSteps = steps,
                    heardFresh = rows.mapTo(HashSet()) { it.key },
                ),
            )
        }
    }

    private fun showReview(result: IngestResult, source: ItemSource, query: String?, st: AppState, ctx: IngestContext, mode: AddMode) {
        val review = AddRules.review(ids.incrementAndGet(), result, source, query, ctx.aisles, st.store.aisleStyles)
        _state.update { it.copy(sheetOpen = true, mode = mode, phase = AddPhase.Preview, review = review, committing = false) }
    }

    /**
     * Rules 2 and "As typed": the offline parse commits at once (duplicates included, so they pop, rule 7), in [appScope]:
     * nothing she does next (a new note, closing the sheet, a photo) can cancel the save or the aisle sort after it.
     */
    private fun commitTyped(text: String, noKey: Boolean, fromBar: Boolean = false) {
        _state.update { it.copy(committing = true) }
        appScope.launch {
            val listId = targetListId()
            val ctx = store.current().ingestContext(listId).copy(existingNames = emptyList())
            val items = OfflineParser.parse(text, catalog, ctx)
            commitItems(listId, listOf(ItemSource.TYPED to items), noKey = noKey, clearsText = true, fromBar = fromBar)
        }
    }

    /**
     * The shared commit (§6.2 "Commit"), always in [appScope]: rarity rolls (egg 6), addItems (duplicates pop, checked
     * ones revive; Q8), the commit event (toast, pops 80 ms apart, Undo), the sheet closes, the SurpriseEngine hears of
     * the new rows (eggs 3, 9, 17), the icon cascade, and typed rows without an aisle are placed in the background (a
     * network call with a key). The add flows are free again as soon as the rows are saved: the sort never holds them.
     */
    private suspend fun commitItems(
        listId: String,
        batches: List<Pair<ItemSource, List<ProposedItem>>>,
        noKey: Boolean,
        clearsText: Boolean,
        fromBar: Boolean = false,
    ) {
        val now = clock()
        val items = batches.flatMap { (source, proposed) -> proposed.map { it.toGroceryItem(newId(), now, source) } }
        var outcome = AddOutcome.NONE
        var before: List<GroceryItem> = emptyList()
        // Egg 6: only a new sticker rolls (optional and pantry rows she switched on too); a revived row or one already
        // there keeps its own. Remembered per id, so the roll is the same however often the update runs.
        val rolled = HashMap<String, Rarity>()
        val roll = { next: AppState, o: AddOutcome ->
            o.added.fold(next) { acc, id ->
                val rarity = rolled.getOrPut(id) { surprises.rollRarity() }
                if (rarity == Rarity.NONE) acc else acc.setRarity(listId, id, rarity)
            }
        }
        // §0.4 Q9's add-side twin: the first add more than 12 h after the trip's last activity files its book first
        // (the album before the state, §7.2) and the items go onto a fresh list; the List turns the book and offers Undo.
        var newBook: TripFiling.Toggle? = null
        val after = try {
            val albumStore = album
            if (albumStore != null) {
                val add = TripFiling.addItems(store, albumStore, listId, items, now) { st, next, o ->
                    before = st.list(listId)?.items.orEmpty()
                    outcome = o
                    roll(next, o)
                }
                newBook = add.newBook
                add.state
            } else {
                store.update { st ->
                    before = st.list(listId)?.items.orEmpty()
                    val (next, o) = st.addItems(listId, items, now)
                    outcome = o
                    roll(next, o)
                }
            }
        } catch (e: IOException) {
            retryAction = {
                _state.update { it.copy(committing = true) }
                appScope.launch { commitItems(listId, batches, noKey, clearsText, fromBar) }
            }
            _state.update { it.copy(committing = false, phase = AddPhase.Failed(AddProblem.SAVE_FAILED, retryable = true)) }
            _events.send(AddEvent.SaveFailed)
            return
        }
        val list = after.list(listId)
        val now2 = list?.items.orEmpty().associateBy { it.id }
        val added = outcome.added.mapNotNull(now2::get)
        val revived = outcome.revived.mapNotNull(now2::get)
        val already = outcome.alreadyThere.mapNotNull(now2::get)
        val revivedBefore = outcome.revived.mapNotNull { id -> before.withIndex().firstOrNull { it.value.id == id } }
        val commitId = ids.incrementAndGet()
        if (added.isNotEmpty() || revived.isNotEmpty()) {
            synchronized(undoRecords) {
                undoRecords[commitId] = UndoRecord(listId, added.map { it.id }, revivedBefore)
                while (undoRecords.size > MAX_UNDO) undoRecords.remove(undoRecords.keys.first())
            }
        }
        val aisleTitle = added.singleOrNull()?.let { item ->
            after.store.aisles().firstOrNull { it.id == item.aisleId }?.displayName().orEmpty()
        }
        val commit = AddCommit(
            id = commitId,
            listId = listId,
            listTitle = list?.title.orEmpty(),
            added = added,
            alreadyThere = already,
            revived = revived,
            aisleTitle = aisleTitle,
            asTypedNoKey = noKey && added.isNotEmpty(),
            fromBar = fromBar,
            otherList = shownListId.let { it.isNotEmpty() && it != listId },
            newBook = newBook,
        )
        _state.update {
            it.cleared().copy(
                sheetOpen = false,
                text = if (clearsText) "" else it.text,
                mode = it.mode,
            )
        }
        _events.send(AddEvent.Committed(commit))
        if (added.isEmpty()) return
        surprises.onCommitted(added)
        icons.submit(added)
        val unsorted = added.filter { it.source == ItemSource.TYPED && it.aisleId == null }.map { it.id }
        if (unsorted.isNotEmpty()) {
            val report = aisleSync.sortItems(unsorted)
            val error = report.error
            // No key means "AI is off": typed items simply stay in Everything else, no need to nag.
            if (error != null && error !is LlmError.NotConfigured) {
                _events.send(AddEvent.SortFailed(error.friendlyMessage, report.leftUnsorted))
            }
        }
    }

    /** Transcription vocabulary: names on the target list and learned names (core caps it at 100). */
    private suspend fun keywords(): List<String> {
        val st = store.current()
        val names = st.list(targetListId())?.items.orEmpty().map { it.name } + st.corrections.map { it.name }
        return names.distinct().take(MAX_KEYWORDS)
    }

    /** The list items go to: the one shown, else the current list (a camera result after process death). */
    private suspend fun targetListId(): String {
        _state.value.listId.takeIf { it.isNotEmpty() }?.let { return it }
        val current = store.current().currentListId
        _state.update { if (it.listId.isEmpty()) it.copy(listId = current) else it }
        return _state.value.listId
    }

    private fun failAi(e: LlmError, canUsePlainLines: Boolean, mode: AddMode) {
        val settingsError = e is LlmError.NotConfigured || e is LlmError.Unauthorized
        _state.update {
            it.withShownList().copy(
                sheetOpen = true,
                mode = mode,
                committing = false,
                phase = AddPhase.Failed(
                    problem = AddProblem.AI,
                    message = e.friendlyMessage,
                    // A key problem keeps what she captured (the photo or recording bytes, the note): once the key is
                    // fixed in Settings, Try again picks up where it stopped.
                    retryable = e.retryable || (settingsError && retryAction != null),
                    canUsePlainLines = canUsePlainLines,
                    needsSettings = settingsError,
                ),
            )
        }
    }

    private fun fail(problem: AddProblem, mode: AddMode? = null, open: Boolean = false) {
        _state.update {
            it.copy(
                mode = mode ?: it.mode,
                sheetOpen = it.sheetOpen || open,
                committing = false,
                phase = AddPhase.Failed(problem),
            )
        }
    }

    private fun stopWork() {
        work?.cancel()
        work = null
        meter?.cancel()
        meter = null
        recorder?.cancel()
        recorder = null
        retryAction = null
    }

    /**
     * Back to the start: no review, photo, transcript or failure (the draft text and the tab stay), and the next add goes
     * to the list on screen.
     */
    private fun AddUiState.cleared() = copy(
        phase = AddPhase.Idle, review = null, photo = null, transcript = null, sentText = null, committing = false,
        listId = shownListId.ifEmpty { listId },
    )

    /**
     * With no review waiting, the next add goes to the list on screen ([showList] keeps the old target while a note is
     * read; a failure, a Stop or a new send lets it go). A review keeps its list: its rows were checked against it.
     */
    private fun AddUiState.withShownList(): AddUiState =
        if (review == null && shownListId.isNotEmpty() && shownListId != listId) copy(listId = shownListId) else this

    /** The phase to rest in when nothing runs: the review if there is one. */
    private fun AddUiState.restingPhase(): AddPhase = if (review != null) AddPhase.Preview else AddPhase.Idle

    /** What survives process death. */
    private data class Saved(val listId: String, val mode: AddMode, val text: String, val sheetOpen: Boolean)

    /** What one commit's Undo takes back: the new rows, and the revived rows as they were (with their indices). */
    private class UndoRecord(val listId: String, val added: List<String>, val revived: List<IndexedValue<GroceryItem>>)

    companion object {
        private const val LEVEL_POLL_MS = 100L
        private const val METER_SAMPLES = 28
        private const val MAX_KEYWORDS = 100
        private const val MAX_UNDO = 8
        private const val KEY_LIST = "add.listId"
        private const val KEY_MODE = "add.mode"
        private const val KEY_TEXT = "add.text"
        private const val KEY_SHEET = "add.sheet"

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]
                AddViewModel(
                    store = c.store,
                    assistant = c.assistant,
                    icons = c.icons,
                    aisleSync = c.aisleSync,
                    catalog = c.catalog,
                    photos = c.photos,
                    recorderFactory = c::newVoiceRecorder,
                    clock = c.clock,
                    newId = c::newId,
                    appScope = c.appScope,
                    savedState = createSavedStateHandle(),
                    hasKey = { c.settings.current().hasToken },
                    surprises = c.surprises,
                    feedback = c.feedback,
                    announcer = c.announcer,
                    words = { id -> app?.getString(id).orEmpty() },
                    album = c.album,
                )
            }
        }
    }
}

/** The polaroid's copy of the photo: decoded at most [THUMB_EDGE] px on its long edge, off the main thread. */
internal suspend fun decodeThumb(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_EDGE) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private const val THUMB_EDGE = 480

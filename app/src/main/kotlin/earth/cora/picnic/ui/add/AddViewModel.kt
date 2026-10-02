package earth.cora.picnic.ui.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import earth.cora.picnic.core.assistant.OfflineParser
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.WebSource
import earth.cora.picnic.core.assistant.toGroceryItem
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.ListGrouping
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.model.ingestContext
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.icons.IconRepository
import earth.cora.picnic.platform.AudioRecorder
import earth.cora.picnic.platform.PhotoPreparer
import earth.cora.picnic.ui.common.ScreenText
import earth.cora.picnic.ui.common.needsSettings
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

/**
 * The Add sheet: write (AI or as typed), snap (camera / gallery) and speak (record, transcribe),
 * then a preview with keep toggles, then commit (docs/ARCHITECTURE.md §10.4).
 *
 * Scopes: the LLM calls the user waits for run in viewModelScope and are cancelled by [cancel] /
 * [close]; the commit runs in [appScope] so closing the sheet never loses a save. The VM lives as
 * long as the List destination; [open] starts a session, [close] ends it (keeping the typed text as
 * a draft, so a sheet swiped away by accident loses nothing).
 *
 * Process death: the draft text, the mode and the target list live in [savedState], so the sheet
 * that ListRoute restores comes back with them. A camera result can reach the fresh VM before the
 * List screen calls [attachList]; the target list then falls back to the current list.
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(
        AddUiState(
            listId = savedState.get<String>(KEY_LIST).orEmpty(),
            mode = savedState.get<String>(KEY_MODE)?.let { m -> AddMode.entries.firstOrNull { it.name == m } } ?: AddMode.WRITE,
            text = savedState.get<String>(KEY_TEXT).orEmpty(),
        ),
    )
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    init {
        // What survives process death: the draft, the mode and the target list (never photos or audio).
        viewModelScope.launch {
            _state.map { Triple(it.listId, it.mode, it.text) }.distinctUntilChanged().collect { (listId, mode, text) ->
                savedState[KEY_LIST] = listId
                savedState[KEY_MODE] = mode.name
                savedState[KEY_TEXT] = text
            }
        }
    }

    private val _events = Channel<AddEvent>(Channel.BUFFERED)

    /** One-off events for the List screen (close the sheet, snackbars). */
    val events: Flow<AddEvent> = _events.receiveAsFlow()

    /** The call the user is waiting for (photo prep, transcription, ingest). Main thread only. */
    private var work: Job? = null

    /** Level polling while recording. Main thread only. */
    private var meter: Job? = null
    private var recorder: AudioRecorder? = null

    /** What "Try again" repeats (holds the photo or recording bytes while the sheet is open). */
    @Volatile
    private var retryAction: (() -> Unit)? = null

    /** Starts a session for [listId] (the list current when the sheet opened). Keeps a draft text. */
    fun open(listId: String, mode: AddMode = AddMode.WRITE) {
        stopWork()
        _state.value = AddUiState(listId = listId, mode = mode, text = _state.value.text)
    }

    /**
     * Non-destructive [open] for a sheet restored after process death: sets the target list only if
     * none is known yet, and never cancels what is running (a camera result may already be read).
     */
    fun attachList(listId: String) {
        if (listId.isEmpty()) return
        _state.update { if (it.listId.isEmpty()) it.copy(listId = listId) else it }
    }

    /** The sheet was dismissed: cancels whatever runs and forgets everything but the draft text. */
    fun close() {
        stopWork()
        _state.value = AddUiState(listId = "", text = _state.value.text)
    }

    fun setMode(mode: AddMode) {
        val s = _state.value
        if (s.mode == mode || s.phase.busy || s.committing) return
        retryAction = null
        _state.update { it.cleared().copy(mode = mode) }
    }

    fun setText(text: String) {
        // Editing after a failure brings the normal buttons back.
        _state.update { it.copy(text = text, phase = if (it.phase is AddPhase.Failed && it.mode == AddMode.WRITE) AddPhase.Idle else it.phase) }
    }

    /** "Sort it out": the smart model (with web search) turns the text into categorized items. */
    fun submitText() {
        val s = _state.value
        val text = s.text.trim()
        if (text.isEmpty() || s.phase.busy || s.committing) return
        ingest(IngestInput.Text(text), ItemSource.AI_TEXT, plainLines = text, recipe = looksLikeRecipe(text))
    }

    /** "Add as typed": no AI, commits at once; unsorted items are placed in the background. */
    fun addTyped() {
        val s = _state.value
        if (s.text.isBlank() || s.phase.busy || s.committing) return
        _state.update { it.copy(committing = true) }
        appScope.launch {
            val listId = targetListId()
            val ctx = store.current().ingestContext(listId)
            val items = OfflineParser.parse(s.text, catalog, ctx)
            if (items.isEmpty()) {
                _state.update { it.copy(committing = false, text = "") }
                _events.send(AddEvent.Message(ScreenText.NOTHING_NEW))
            } else {
                commitItems(listId, items, ItemSource.TYPED)
            }
        }
    }

    /** A photo from the camera or the picker: read the handwriting. The capture file is deleted after reading. */
    fun onPhoto(uri: Uri) {
        val s = _state.value
        if (s.phase.busy || s.committing) {
            appScope.launch { photos.discard(uri) }
            return
        }
        work?.cancel()
        retryAction = null
        _state.update { it.cleared().copy(mode = AddMode.SNAP, photo = uri, phase = AddPhase.Thinking(ItemSource.PHOTO)) }
        work = viewModelScope.launch {
            val bytes = try {
                photos.jpegBytes(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                fail(ScreenText.PHOTO_UNREADABLE)
                return@launch
            } catch (e: RuntimeException) {
                fail(ScreenText.PHOTO_UNREADABLE)
                return@launch
            } finally {
                withContext(NonCancellable) { photos.discard(uri) }
            }
            runIngest(IngestInput.Image(bytes), ItemSource.PHOTO, plainLines = null, recipe = false)
        }
    }

    fun cameraUnavailable() = fail(ScreenText.CAMERA_UNAVAILABLE, mode = AddMode.SNAP)

    /** Call once RECORD_AUDIO is granted (rememberAddSources does the asking). */
    fun startRecording() {
        val s = _state.value
        if (s.phase.busy || s.committing) return
        retryAction = null
        val r = recorderFactory()
        try {
            r.start()
        } catch (e: IOException) {
            fail(ScreenText.MIC_UNAVAILABLE, mode = AddMode.SPEAK)
            return
        } catch (e: RuntimeException) {
            fail(ScreenText.MIC_UNAVAILABLE, mode = AddMode.SPEAK)
            return
        }
        recorder = r
        val started = clock()
        _state.update { it.cleared().copy(mode = AddMode.SPEAK, phase = AddPhase.Recording(0, 0f)) }
        meter = viewModelScope.launch {
            val levels = ArrayDeque<Float>()
            while (true) {
                delay(LEVEL_POLL_MS)
                val level = r.level()
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

    fun micDenied() = fail(ScreenText.MIC_DENIED, mode = AddMode.SPEAK)

    /** "Done" while recording (also on Lifecycle ON_STOP): stop, transcribe, then ingest the transcript. */
    fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        meter?.cancel()
        meter = null
        _state.update { it.copy(phase = AddPhase.Transcribing) }
        work = viewModelScope.launch {
            val file = r.stop()
            if (file == null) {
                fail(ScreenText.TOO_SHORT, mode = AddMode.SPEAK)
                return@launch
            }
            val bytes = try {
                withContext(ioDispatcher) { file.readBytes() }
            } catch (e: IOException) {
                fail(ScreenText.TOO_SHORT, mode = AddMode.SPEAK)
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

    /** Cancel button while busy: stops the call or the recording and returns to the start (text kept). */
    fun cancel() {
        stopWork()
        _state.update { it.copy(phase = AddPhase.Idle, committing = false) }
    }

    fun toggleKeep(key: Int) {
        _state.update { s -> s.copy(preview = s.preview.map { if (it.key == key) it.copy(keep = !it.keep) else it }) }
    }

    /** "Try again" after a retryable failure (the photo / recording bytes are still in memory). */
    fun retry() {
        val action = retryAction ?: return
        if (_state.value.phase.busy) return
        action()
    }

    /** "Add as plain lines" after a failed AI call: OfflineParser on the text or transcript, then the preview. */
    fun usePlainLines() {
        val s = _state.value
        if (s.phase.busy || s.committing) return
        val text = (if (s.mode == AddMode.SPEAK) s.transcript else s.text)?.takeIf { it.isNotBlank() } ?: return
        work?.cancel()
        retryAction = null
        work = viewModelScope.launch {
            val ctx = store.current().ingestContext(targetListId())
            showPreview(OfflineParser.parse(text, catalog, ctx), ctx, ItemSource.TYPED, message = null, sources = emptyList(), skipped = emptyList())
        }
    }

    /** "Start over" from the preview or a failure: back to the start of the current mode (text kept). */
    fun startOver() {
        stopWork()
        _state.update { it.cleared() }
    }

    /** Moves the voice transcript into the Write tab so the user can fix it before sending. */
    fun editTranscript() {
        val transcript = _state.value.transcript ?: return
        stopWork()
        _state.update { it.cleared().copy(mode = AddMode.WRITE, text = transcript) }
    }

    /** "Add N items": commits the kept preview items in the app scope; [AddEvent.Added] closes the sheet. */
    fun commit() {
        val s = _state.value
        if (s.phase != AddPhase.Preview || s.committing) return
        // The model's own order (the preview is sorted by aisle only for display).
        val kept = s.preview.filter { it.keep }.sortedBy { it.key }.map { it.item }
        if (kept.isEmpty()) return
        val source = s.previewSource ?: ItemSource.AI_TEXT
        _state.update { it.copy(committing = true) }
        appScope.launch { commitItems(targetListId(), kept, source) }
    }

    override fun onCleared() {
        recorder?.cancel()
        recorder = null
    }

    /* ---------------- internals ---------------- */

    private fun ingest(input: IngestInput, source: ItemSource, plainLines: String?, recipe: Boolean) {
        work?.cancel()
        work = viewModelScope.launch { runIngest(input, source, plainLines, recipe) }
    }

    private suspend fun runIngest(input: IngestInput, source: ItemSource, plainLines: String?, recipe: Boolean) {
        retryAction = null
        _state.update {
            it.copy(
                phase = AddPhase.Thinking(source, recipe),
                preview = emptyList(), message = null, sources = emptyList(), skipped = emptyList(), previewSource = null,
            )
        }
        val ctx = store.current().ingestContext(targetListId())
        val result = try {
            assistant.ingest(input, ctx)
        } catch (e: LlmError) {
            retryAction = { ingest(input, source, plainLines, recipe) }
            fail(e, canUsePlainLines = plainLines != null)
            return
        }
        showPreview(result.items, ctx, source, result.message.ifBlank { null }, result.sources, result.skippedDuplicates)
    }

    private suspend fun transcribe(bytes: ByteArray) {
        _state.update { it.copy(phase = AddPhase.Transcribing) }
        val transcript = try {
            assistant.transcribe(AudioClip(bytes), keywords())
        } catch (e: LlmError) {
            retryAction = { work = viewModelScope.launch { transcribe(bytes) } }
            fail(e, canUsePlainLines = false)
            return
        }
        if (transcript.isEmpty) {
            fail(ScreenText.NOTHING_HEARD, mode = AddMode.SPEAK)
            return
        }
        val text = transcript.text.trim()
        _state.update { it.copy(transcript = text) }
        runIngest(IngestInput.Transcript(text), ItemSource.VOICE, plainLines = text, recipe = looksLikeRecipe(text))
    }

    /** Transcription vocabulary: names on the target list and learned names (core caps it at 100). */
    private suspend fun keywords(): List<String> {
        val st = store.current()
        val names = st.list(targetListId())?.items.orEmpty().map { it.name } + st.corrections.map { it.name }
        return names.distinct().take(MAX_KEYWORDS)
    }

    private fun showPreview(
        items: List<ProposedItem>,
        ctx: IngestContext,
        source: ItemSource,
        message: String?,
        sources: List<WebSource>,
        skipped: List<String>,
    ) {
        val aisles = ctx.aisles.associateBy { it.id }
        val rows = items.mapIndexed { i, item ->
            val aisle = item.aisleId?.let(aisles::get)
            PreviewItemUi(
                key = i,
                item = item,
                keep = true,
                aisleTitle = aisle?.name ?: ListGrouping.UNSORTED_TITLE,
                icon = if (item.iconFit == IconFit.PENDING) IconRef(catalog.fallbackFor(item.icon)) else IconRef(item.icon),
                aisleIndex = aisle?.let { it.position - 1 } ?: -1,
            )
        }.sortedWith(compareBy({ it.aisleIndex }, { it.key }))
        _state.update {
            it.copy(
                phase = AddPhase.Preview, preview = rows, message = message, sources = sources, skipped = skipped,
                previewSource = source, committing = false,
            )
        }
    }

    /**
     * The list items go to: the one the sheet opened on, else the current list. The fallback covers
     * a camera result that a fresh VM receives after process death before ListRoute attaches the list.
     */
    private suspend fun targetListId(): String {
        _state.value.listId.takeIf { it.isNotEmpty() }?.let { return it }
        val current = store.current().currentListId
        _state.update { if (it.listId.isEmpty()) it.copy(listId = current) else it }
        return _state.value.listId
    }

    /** Commit procedure (§10.4): addItems -> icons.submit(added) -> sortItems for typed items left Unsorted. */
    private suspend fun commitItems(listId: String, proposed: List<ProposedItem>, source: ItemSource) {
        // One timestamp for the batch: equal addedAt keeps the model's order inside each aisle.
        val now = clock()
        val items = proposed.map { it.toGroceryItem(newId(), now, source) }
        val after = try {
            // TODO(WP7): use the AddOutcome (alreadyThere pops, revived un-checks; §6.2 rule 7).
            store.update { it.addItems(listId, items, now).first }
        } catch (e: IOException) {
            retryAction = {
                _state.update { it.copy(committing = true) }
                appScope.launch { commitItems(listId, proposed, source) }
            }
            _state.update { it.copy(committing = false, phase = AddPhase.Failed(ScreenText.SAVE_FAILED, true, false, false)) }
            return
        }
        val added = items.filter { after.findItem(it.id) != null }
        _state.update { it.copy(text = "", committing = false) }
        _events.send(if (added.isEmpty()) AddEvent.Message(ScreenText.NOTHING_NEW) else AddEvent.Added(added.size, listId))
        if (added.isEmpty()) return
        icons.submit(added)
        val unsorted = added.filter { it.source == ItemSource.TYPED && it.aisleId == null }.map { it.id }
        if (unsorted.isNotEmpty()) {
            val report = aisleSync.sortItems(unsorted)
            val error = report.error
            // No token means "AI is off": typed items simply stay in Unsorted, no need to nag.
            if (error != null && error !is LlmError.NotConfigured) {
                _events.send(AddEvent.SortFailed(error.friendlyMessage, report.leftUnsorted))
            }
        }
    }

    private fun fail(e: LlmError, canUsePlainLines: Boolean) {
        _state.update {
            it.copy(phase = AddPhase.Failed(e.friendlyMessage, e.retryable, canUsePlainLines, e.needsSettings()), committing = false)
        }
    }

    private fun fail(message: String, mode: AddMode? = null) {
        _state.update {
            it.copy(mode = mode ?: it.mode, phase = AddPhase.Failed(message, retryable = false, canUsePlainLines = false, needsSettings = false))
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

    /** Back to the start of the current mode: no preview, transcript, photo or failure (text kept). */
    private fun AddUiState.cleared() = copy(
        phase = AddPhase.Idle, transcript = null, photo = null, preview = emptyList(), message = null,
        sources = emptyList(), skipped = emptyList(), previewSource = null, committing = false,
    )

    companion object {
        private const val LEVEL_POLL_MS = 100L
        private const val METER_SAMPLES = 28
        private const val MAX_KEYWORDS = 100
        private const val KEY_LIST = "add.listId"
        private const val KEY_MODE = "add.mode"
        private const val KEY_TEXT = "add.text"

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
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
                )
            }
        }
    }
}

private val URL = Regex("""https?://""", RegexOption.IGNORE_CASE)
private val LIST_SEPARATORS = Regex("""[,;\n]""")
private val RECIPE_WORDS = listOf(
    "recipe", "ingredient", "ingrédient", "from scratch", "to make", "for making", "homemade", "home made",
    "dinner", "lunch", "breakfast", "brunch", "supper", "party", "potluck", "bake ", "baking", "cook",
    "for a ", "for the ", "for my ", "for some ", "stuff for", "things for", "what i need",
)

/**
 * Guess for the "thinking" label only: a URL or dish-like phrasing ("ingredients for pancakes",
 * "stuff for tacos") reads as a recipe; a list with several separators reads as a plain list.
 */
internal fun looksLikeRecipe(text: String): Boolean {
    val t = " " + text.lowercase().trim() + " "
    if (URL.containsMatchIn(t)) return true
    if (LIST_SEPARATORS.findAll(t).count() >= 2) return false
    return RECIPE_WORDS.any { it in t }
}

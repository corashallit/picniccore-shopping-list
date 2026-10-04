package earth.cora.picnic.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.GroupedList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.TripRules
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.editItemDetails
import earth.cora.picnic.core.model.findItem
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.moveItemToAisle
import earth.cora.picnic.core.model.newList
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.removeItem
import earth.cora.picnic.core.model.renameList
import earth.cora.picnic.core.model.restoreItem
import earth.cora.picnic.core.model.setItemIcon
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.switchList
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.core.store.TripFiling
import earth.cora.picnic.icons.IconRepository
import earth.cora.picnic.platform.Announcer
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.DefaultTimeouts
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpAnnouncer
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.Timeouts
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.settings.SettingsRepository
import earth.cora.picnic.ui.common.ScreenText
import earth.cora.picnic.ui.eggs.NoOpSurpriseEngine
import earth.cora.picnic.ui.eggs.SurpriseEngine
import earth.cora.picnic.ui.eggs.SurpriseState
import earth.cora.picnic.ui.eggs.hankoFor
import earth.cora.picnic.ui.eggs.pixelStickers
import earth.cora.picnic.ui.list.motion.CelebrationContent
import earth.cora.picnic.ui.list.motion.ChangeCause
import earth.cora.picnic.ui.list.motion.FinaleKeeper
import earth.cora.picnic.ui.list.motion.ListChange
import earth.cora.picnic.ui.list.motion.MotionChoreographer
import earth.cora.picnic.ui.list.motion.ToggleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.ZoneId

/**
 * The shopping list (docs/DESIGN_STICKER.md §6.1, §7, §8; ARCHITECTURE §10.3). Every change goes through StateOps inside
 * store.update, launched in [appScope] so leaving the screen never cancels a save. Item actions look up the list that
 * holds the item, so they stay correct if the current list changes meanwhile.
 *
 * It owns the motion [engine] (WP6) for as long as the List destination lives (a rotation keeps every chain), and
 * feeds it from ONE place: every committed state passes through [onStates] on the main thread, which compares it with
 * the last one it saw (§8.4) and, in the same main-thread task that publishes the new [state], tells the engine
 * - a [ToggleEvent] for each check or un-check: her taps (and their TalkBack, keyboard and swipe equivalents, which
 *   [toggle] marks as pending), a revived duplicate (§6.2 rule 7: the un-check chain), an Undo that checks again;
 * - a [ListChange] for every other change that completes or re-opens an aisle or the trip (a delete, a move, a re-sort,
 *   an aisle-text edit, an add, an Undo), so completion plays wherever it came from (item 176);
 * and opens the next stop, folds everything when the trip completes, and re-opens an aisle on an un-check (D2, item 90).
 * The frame that first shows the new state therefore already shows its chain.
 */
class ListViewModel(
    private val store: StateStore,
    private val album: AlbumStore,
    private val icons: IconRepository,
    private val settings: SettingsRepository,
    private val catalog: IconCatalog,
    private val clock: () -> Long,
    private val newId: () -> String,
    private val appScope: CoroutineScope,
    internal val feedback: Feedback = NoOpFeedback,
    private val announcer: Announcer = NoOpAnnouncer,
    private val surprises: SurpriseEngine = NoOpSurpriseEngine,
    private val aisleSync: AisleSync? = null,
    /** The choreography engine; one per List destination. */
    val engine: MotionChoreographer = MotionChoreographer(),
    private val zone: ZoneId = TORONTO,
) : ViewModel() {
    /** The app's constructor: the engine speaks and plays cues through the app's services. */
    constructor(
        store: StateStore,
        album: AlbumStore,
        icons: IconRepository,
        settings: SettingsRepository,
        catalog: IconCatalog,
        clock: () -> Long,
        newId: () -> String,
        appScope: CoroutineScope,
        feedback: Feedback,
        announcer: Announcer,
        timeouts: Timeouts,
        surprises: SurpriseEngine,
        aisleSync: AisleSync?,
    ) : this(
        store, album, icons, settings, catalog, clock, newId, appScope, feedback, announcer, surprises, aisleSync,
        MotionChoreographer(feedback, announcer, timeouts),
    )

    private val editingId = MutableStateFlow<String?>(null)
    private val bookTurn = MutableStateFlow(0)
    private val _events = Channel<ListEvent>(Channel.BUFFERED)

    /** One-off UI events (sticky notes). Collect once, from the screen. */
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    private val _state = MutableStateFlow(ListUiState())

    /** The screen's state; changes in the same main-thread task that hands the engine its events. */
    val state: StateFlow<ListUiState> = _state.asStateFlow()

    /** The Surprises switches the engine follows (the Next card, the celebration, the eggs). */
    private val _surprises = MutableStateFlow(AppSettings())
    val surpriseSettings: StateFlow<AppSettings> = _surprises.asStateFlow()

    /** What the eggs show (the add bar's seasonal tape, §4.14). */
    val surprisesState: StateFlow<SurpriseState> = surprises.state

    /** WP9's engine, for the screen's EggListHost (the overlays, the shake, the scenes' scrolls, §19.2). */
    val surpriseEngine: SurpriseEngine get() = surprises

    /** What TalkBack hears for the book (§7.7); the screen sets it from its resources. */
    var strings: ListStrings = ListStrings.English

    /** The last committed state seen, and the list it was grouped for. */
    private var seen: Seen? = null
    private var latestAlbum: Album = Album()

    /** Taps in flight: item id -> the checked state the tap asked for. */
    private val pending = HashMap<String, Boolean>()

    /** Set by [undoNewBook] until the state that restores the old book arrives (main thread only). */
    private var hint: NewBookUndoHint? = null

    /** The last automatic new book (§0.4 Q9), for its toast's Undo. */
    private var lastToggle: TripFiling.Toggle? = null

    /** Lists the finale was owed on, this session (once each). */
    private val resumed = HashSet<String>()

    /** The idle periods the nudge was offered for (once each): "<list id>:<the trip's last activity>". */
    private val nudged = HashSet<String>()

    /** The inputs of the last state built, so a return to the list can build it again on the current clock. */
    private var lastInputs: Inputs? = null

    init {
        engine.celebrationSource = {
            val s = seen
            s?.let { CelebrationContent.from(it.state, it.listId, latestAlbum, catalog, eggs = _surprises.value.eggs) }
        }
        val keeper = FinaleKeeper(store, album, clock)
        viewModelScope.launch { engine.events.collect { e -> appScope.launch { runCatching { keeper.handle(e) } } } }
        viewModelScope.launch { album.album.collect { latestAlbum = it } }
        viewModelScope.launch {
            combine(store.states, icons.busy, settings.settings, editingId, surprises.state) { s, busy, set, editing, eggs ->
                Inputs(s, busy, set, editing, eggs)
            }.combine(bookTurn) { inputs, turn -> inputs.copy(turn = turn) }
                .collect { onStates(it) }
        }
    }

    private data class Inputs(
        val state: AppState,
        val busy: Set<String>,
        val settings: AppSettings,
        val editing: String?,
        val eggs: SurpriseState,
        val turn: Int = 0,
    )

    private class Seen(val listId: String, val state: AppState, val list: GroceryList, val grouped: GroupedList)

    /**
     * The automatic new book's Undo (§0.4 Q9) is on its way: the change on [listId] that brings back [oldTripId] (and
     * only that change) plays the turn back, [itemId] (the tapped item, null after an add) landing in its old spot.
     */
    private class NewBookUndoHint(val listId: String, val itemId: String?, val oldTripId: String)

    // =============================================================================================
    // The one place states arrive (§8.3, §8.4).
    // =============================================================================================

    private fun onStates(input: Inputs) {
        lastInputs = input
        val app = input.state
        val list = app.currentList()
        val grouped = groupedOf(app, list, catalog)
        _surprises.value = input.settings
        val now = clock()
        val ui = buildListUiState(
            state = app,
            busy = input.busy,
            hasToken = input.settings.hasToken,
            editingId = input.editing,
            catalog = catalog,
            now = now,
            zone = zone,
            eggs = input.settings.eggs,
            season = input.eggs.season.takeIf { input.eggs.enabled },
            bonusStage = input.eggs.pixelStickers,
            bookTurn = input.turn,
            perfectWeek = input.eggs.enabled && input.eggs.perfectWeek,
            hankoFor = { item -> input.eggs.hankoFor(item, catalog) },
        ).copy(nextStopCard = input.settings.nextStopCard, celebration = input.settings.celebration)
        val prev = seen
        seen = Seen(list.id, app, list, grouped)
        engine.listId = list.id
        engine.setSections(ui.motionSections())
        when {
            prev == null || prev.listId != list.id -> {
                // A list shown for the first time, or another list: the old chains are moot (§6.4: switching lists
                // switches the book). Nothing plays for what is already there.
                if (prev != null) {
                    engine.finishAll()
                    // Sent before this list's own nudge (opened), so the screen retires only the last list's notes.
                    viewModelScope.launch { _events.send(ListEvent.Switched(list.id)) }
                }
                pending.clear()
                hint = null
                opened(list, grouped, now)
            }
            prev.state !== app -> diff(prev, app, list, grouped)
        }
        _state.value = ui
    }

    /** A list opens (§7.1, items 120 and 154): an owed finale plays or is marked; an idle trip gets the nudge. */
    private fun opened(list: GroceryList, grouped: GroupedList, now: Long) {
        if (resumed.add(list.id) && TripRules.finaleOwed(list)) {
            engine.onChange(
                ListChange(
                    aisleCompleted = emptySet(),
                    aisleReopened = emptySet(),
                    tripCompleted = true,
                    tripReopened = false,
                    nextSectionId = null,
                    cause = ChangeCause.RESUME,
                    openSectionId = grouped.openSectionId,
                    finaleShown = false,
                    finaleStale = TripRules.finaleIsStale(list, now),
                ),
            )
        }
        nudgeIfIdle(list, now)
    }

    /**
     * The nudge (§6.1, item 120): an open trip idle for more than 12 h gets "New shop? Start a fresh sticker book", once
     * per idle period (a check-off starts a new period, so a later idle stretch is offered again).
     */
    private fun nudgeIfIdle(list: GroceryList, now: Long) {
        val trip = list.trip ?: return
        if (TripRules.startsNewBook(list, now) && nudged.add("${list.id}:${trip.lastActivityAt}")) {
            viewModelScope.launch { _events.send(ListEvent.Nudge(list.id)) }
        }
    }

    /**
     * The List is back in front (its ON_RESUME; §6.1, §7.1, items 120 and 143). The app may have waited in the
     * background for hours: the state is built again on the current clock, so a fresh book's date and the season roll
     * over to today, and an open trip that has gone idle since gets its nudge (once per idle period).
     */
    fun onResumed() {
        val inputs = lastInputs ?: return
        onStates(inputs)
        val s = seen ?: return
        nudgeIfIdle(s.list, clock())
    }

    /** Compares two successive states of the same list and tells the engine what changed (§8.3, §8.4). */
    private fun diff(prev: Seen, app: AppState, list: GroceryList, grouped: GroupedList) {
        val before = prev.list
        val beforeById = before.items.associateBy { it.id }
        val tripReplaced = before.trip?.id != list.trip?.id
        val currentHint = hint

        // The automatic new book's Undo (§0.4 Q9): the old book comes back as it was; only the turn back plays. Only the
        // change that restores that very book counts: another list's, or a later New trip's, plays as itself.
        if (currentHint != null && tripReplaced && currentHint.listId == list.id && list.trip?.id == currentHint.oldTripId) {
            hint = null
            engine.onChange(
                ListChange(
                    aisleCompleted = emptySet(),
                    aisleReopened = emptySet(),
                    tripCompleted = false,
                    tripReopened = false,
                    nextSectionId = null,
                    cause = ChangeCause.NEW_BOOK_UNDO,
                    openSectionId = grouped.openSectionId,
                    finaleShown = list.trip?.finaleShownAt != null,
                    slot = list.items.firstOrNull { it.id == currentHint.itemId }?.bookSlot,
                ),
            )
            pending.clear()
            return
        }

        val newBook = before.trip != null && list.trip != null && tripReplaced
        val tripClosed = before.trip != null && list.trip == null
        val flips = list.items.filter { a -> beforeById[a.id]?.let { it.checked != a.checked } == true }
        val taps = flips.filter { pending[it.id] == it.checked }
        taps.forEach { pending.remove(it.id) }
        // A tap whose change never showed (two taps that cancelled out between two states) is stale.
        pending.keys.removeAll { id -> list.items.none { it.id == id } || list.items.first { it.id == id }.checked == pending[id] }
        val others = flips - taps.toSet()
        // A bulk change (New trip, "Keep everything, uncheck it all", a big revive) plays no row chains.
        val bulk = (tripClosed && taps.isEmpty()) || (newBook && taps.isEmpty()) || others.size > MAX_ROW_CHAINS
        val toggled = if (bulk) taps else taps + others

        val handledAisles = HashSet<String>()
        var tripHandled = false
        var openTo: String? = null
        var foldAll = false
        val toggles = ArrayList<ToggleEvent>()
        for ((i, item) in toggled.withIndex()) {
            val section = grouped.sections.firstOrNull { s -> s.rows.any { it.id == item.id } } ?: continue
            // A fresh book (§0.4 Q9) starts with nothing done: its aisles complete anew.
            val beforeSection = if (newBook) null else prev.grouped.sections.firstOrNull { it.id == section.id }
            val checked = item.checked
            val aisleChanged = if (checked) section.complete && beforeSection?.complete != true else beforeSection?.complete == true && !section.complete
            val last = i == toggled.lastIndex
            // A fresh book finished by the tap that started it is no trip completion: its finale counts as shown (core),
            // so only the aisle's in-place chain plays and the new book's Undo note stays readable (§0.4 Q9, §7.1).
            val sameTapFinish = newBook && list.trip?.finaleShownAt != null
            val tripChanged = last && if (checked) {
                TripRules.isComplete(list) && !sameTapFinish && (newBook || !TripRules.isComplete(before))
            } else {
                !newBook && TripRules.isComplete(before) && !TripRules.isComplete(list)
            }
            if (aisleChanged) handledAisles += section.id
            if (tripChanged) tripHandled = true
            val next = if (checked && aisleChanged && !(last && TripRules.isComplete(list))) nextStop(grouped, section.id) else null
            val position = section.position
            toggles += ToggleEvent(
                itemId = item.id,
                sectionId = section.id,
                checked = checked,
                slot = if (checked) item.bookSlot else beforeById[item.id]?.bookSlot,
                aisleCompleted = aisleChanged,
                tripCompleted = tripChanged,
                nextSectionId = next,
                personality = catalog.personalityOf(item.icon),
                icon = catalog.displayRef(item, false),
                holo = item.rarity != Rarity.NONE,
                aisleIndex = if (position > 0) position - 1 else -1,
                aisleWasOpen = prev.grouped.openSectionId == section.id,
                finaleShown = if (newBook) list.trip?.finaleShownAt != null else before.trip?.finaleShownAt != null,
                newBook = newBook && checked && item in taps,
            )
            if (next != null) openTo = next
            if (checked && tripChanged) foldAll = true
            if (!checked && aisleChanged) openTo = section.id
        }

        // Every other completion or re-opening, wherever it came from (item 176).
        var change: ListChange? = null
        if (!tripClosed && !newBook) {
            val beforeSections = prev.grouped.sections.associateBy { it.id }
            val completed = grouped.sections
                .filter { s -> s.complete && beforeSections[s.id]?.complete != true && s.id !in handledAisles }
                .map { it.id }.toSet()
            val reopened = grouped.sections
                .filter { s -> !s.complete && beforeSections[s.id]?.complete == true && s.id !in handledAisles }
                .map { it.id }.toSet()
            val sameTrip = before.trip?.id == list.trip?.id
            val tripDone = !tripHandled && sameTrip && TripRules.isComplete(list) && !TripRules.isComplete(before)
            val tripReopened = !tripHandled && sameTrip && TripRules.isComplete(before) && !TripRules.isComplete(list)
            if (completed.isNotEmpty() || reopened.isNotEmpty() || tripDone || tripReopened) {
                val next = if (tripDone || TripRules.isComplete(list)) null else completed.firstOrNull()?.let { nextStop(grouped, it) }
                // A row this change brought back into an aisle it re-opened (an Undo of a tear-off): the list shows it
                // (§8.4, item 176).
                val revealed = if (completed.isEmpty()) {
                    list.items.asSequence().filter { it.id !in beforeById }.firstNotNullOfOrNull { item ->
                        grouped.sections.firstOrNull { s -> s.id in reopened && s.rows.any { it.id == item.id } }?.let { item.id to it.id }
                    }
                } else {
                    null
                }
                change = ListChange(
                    aisleCompleted = completed,
                    aisleReopened = reopened,
                    tripCompleted = tripDone,
                    tripReopened = tripReopened,
                    nextSectionId = next,
                    cause = causeOf(before, list, prev.state, app),
                    openSectionId = prev.grouped.openSectionId,
                    finaleShown = list.trip?.finaleShownAt != null,
                    revealItemId = revealed?.first,
                    revealSectionId = revealed?.second,
                )
                if (next != null) openTo = next
                if (tripDone) foldAll = true
                if (reopened.isNotEmpty() && completed.isEmpty() && toggles.isEmpty()) openTo = reopened.first()
            }
        }

        // D2 / item 90: the next stop opens behind the Next card; a finished trip folds everything; an un-check that
        // re-opens an aisle keeps her in it. The engine holds what shows meanwhile (OpenOverride).
        val target = if (foldAll) "" else openTo
        if (target != null && target != list.openAisle) setOpenAisleIfUnchanged(list.id, list.openAisle, target)

        for (t in toggles) {
            engine.onToggle(t)
            announceBook(t, list)
        }
        change?.let(engine::onChange)
    }

    /** "<Name> added to today's sticker book" / "peeled out of today's sticker book" (§7.7, item 159). */
    private fun announceBook(t: ToggleEvent, list: GroceryList) {
        val name = list.items.firstOrNull { it.id == t.itemId }?.name ?: return
        announcer.polite(if (t.checked) strings.addedToBook(name) else strings.peeledFromBook(name))
    }

    /** Why a change happened, told from the diff (the engine plays every cause the same way, §8.4). */
    private fun causeOf(before: GroceryList, after: GroceryList, beforeState: AppState, afterState: AppState): ChangeCause {
        val beforeIds = before.items.mapTo(HashSet()) { it.id }
        val afterIds = after.items.mapTo(HashSet()) { it.id }
        return when {
            beforeState.store.aislesText != afterState.store.aislesText -> ChangeCause.AISLES_EDIT
            !beforeIds.containsAll(afterIds) -> ChangeCause.ADD
            !afterIds.containsAll(beforeIds) -> ChangeCause.DELETE
            after.items.any { a -> before.items.firstOrNull { it.id == a.id }?.let { it.aisleId != a.aisleId } == true } -> ChangeCause.MOVE
            else -> ChangeCause.SYNC
        }
    }

    private fun setOpenAisleIfUnchanged(listId: String, expected: String?, target: String) {
        appScope.launch {
            save {
                store.update { s ->
                    val l = s.list(listId) ?: return@update s
                    if (l.openAisle != expected) s else s.setOpenAisle(listId, target)
                }
            }
        }
    }

    // =============================================================================================
    // Actions.
    // =============================================================================================

    /**
     * A check-off tap (or its TalkBack, keyboard or swipe equivalent). The idle rule (§0.4 Q9) runs inside: the first
     * check-off more than 12 h after the trip's last activity files the old book, clears what was got and checks the
     * item into a fresh book, and the toast "New sticker book for today ✦ your last shop is in the album" offers Undo.
     * Nothing ever asks.
     */
    fun toggle(itemId: String) {
        val s = seen?.state ?: return
        val listId = s.listIdOf(itemId) ?: return
        val item = s.list(listId)?.items?.firstOrNull { it.id == itemId } ?: return
        pending[itemId] = pending[itemId]?.not() ?: !item.checked
        val now = clock()
        appScope.launch {
            var result: TripFiling.Toggle? = null
            val ok = save { result = TripFiling.toggle(store, album, catalog, listId, itemId, now) }
            val toggle = result
            if (ok && toggle?.autoNewBook != null) {
                lastToggle = toggle
                _events.send(ListEvent.NewBook(listId) { undoNewBook(toggle) })
            }
        }
    }

    /**
     * Undo on "New sticker book for today" (§0.4 Q9): the old book and its items come back; the item goes in the old
     * book. Only while that new book stands (TripFiling.undoNewBook checks it in the same write): after a New trip, a
     * deleted list or a later book a stale Undo changes nothing, so the album never rolls back past a newer filing.
     */
    fun undoNewBook(toggle: TripFiling.Toggle? = lastToggle) {
        val t = toggle ?: return
        val undo = t.autoNewBook?.undo ?: return
        if (lastToggle === t) lastToggle = null
        val now = clock()
        // Set before the write, so the state it brings (which may reach onStates before the write returns) plays the
        // turn back; keyed to that exact change, and dropped again when the Undo no longer applied.
        val h = NewBookUndoHint(undo.listId, undo.itemId, undo.trip.id)
        hint = h
        appScope.launch {
            var undone = false
            save { undone = TripFiling.undoNewBook(store, album, t, now) }
            if (!undone) viewModelScope.launch { if (hint === h) hint = null }
        }
    }

    /**
     * An add started a new book (§0.4 Q9's add-side twin, TripFiling.addItems): the old book is in the album and the
     * list starts afresh. The book turns to a fresh page and the toast "Fresh sticker book ✦ your last shop is in the
     * album" offers Undo ([undoNewBook]).
     */
    fun addStartedNewBook(listId: String, toggle: TripFiling.Toggle) {
        if (toggle.autoNewBook == null) return
        lastToggle = toggle
        bookTurn.value = bookTurn.value + 1
        viewModelScope.launch { _events.send(ListEvent.NewBook(listId, byAdd = true) { undoNewBook(toggle) }) }
    }

    /** A tear-off or Delete (§8.6): emits [ListEvent.Deleted] so the screen can offer Undo. */
    fun delete(itemId: String) {
        if (editingId.value == itemId) editingId.value = null
        val now = clock()
        appScope.launch {
            var removed: ListEvent.Deleted? = null
            val ok = save {
                store.update { s ->
                    val listId = s.listIdOf(itemId) ?: return@update s
                    val items = s.list(listId)?.items.orEmpty()
                    val index = items.indexOfFirst { it.id == itemId }
                    removed = ListEvent.Deleted(items[index], index, listId)
                    s.removeItem(listId, itemId, now)
                }
            }
            if (ok) removed?.let { _events.send(it) }
        }
    }

    /** Undo of a tear-off: the row comes back exactly as it was (checked state, book spot, icon). */
    fun undoDelete(item: GroceryItem, index: Int, listId: String? = null) {
        val now = clock()
        mutate { s -> s.restoreItem(listId?.takeIf { s.list(it) != null } ?: s.currentList().id, item, index, now) }
    }

    /** Her tap on a folded strip: it opens and the others fold (D2); her choice wins over any chain at once. */
    fun openSection(sectionId: String) {
        val listId = seen?.listId ?: return
        engine.onUserOpen(sectionId)
        mutate { it.setOpenAisle(listId, sectionId) }
    }

    /** Her tap on the open tray's chevron: everything folds. */
    fun foldAll() {
        val listId = seen?.listId ?: return
        engine.onUserOpen(null)
        mutate { it.setOpenAisle(listId, "") }
    }

    /** v1 name for a strip or chevron tap. */
    fun toggleSection(sectionId: String) {
        if (state.value.openSectionId == sectionId) foldAll() else openSection(sectionId)
    }

    /**
     * New trip (§6.5): the book is filed in the album first, then the list starts afresh ("Clear what I got" removes
     * checked items, "Keep everything" un-checks them); the book turns to a fresh page and the toast says so.
     */
    fun newTrip(mode: NewTripMode) {
        val listId = seen?.listId ?: return
        val now = clock()
        appScope.launch {
            var filed = false
            val ok = save { filed = TripFiling.newTrip(store, album, listId, mode, now) != null }
            if (ok && filed) {
                bookTurn.value = bookTurn.value + 1
                _events.send(ListEvent.NewTrip)
            }
        }
    }

    /**
     * Pull to refresh, the overflow menu's Refresh and the list's custom action (§6.1, D18): pending stickers resume and
     * the items still in "Everything else" are sorted into aisles. [ListEvent.Refreshed] says what changed: the sorting
     * at once, and the stickers once the icon repository has finished the ones it resumed (their names have left its busy
     * set, within [DRAW_WAIT_MS]); that second event carries the sorting too, so the list folds it into the note still
     * showing ("Sorted 2 items · 1 new sticker drawn", §6.1). Nothing is said when nothing changed.
     */
    fun refresh() {
        appScope.launch {
            val s = try {
                store.current()
            } catch (e: IOException) {
                null
            }
            val list = s?.currentList()
            // The stickers this refresh resumes: the current list's items still waiting for theirs.
            val waiting = list?.items?.filter { it.iconFit == IconFit.PENDING && it.customIconId == null }.orEmpty()
            icons.resumePending()
            val sync = aisleSync
            var sorted = 0
            var left = 0
            if (s != null && list != null && sync != null) {
                val unsorted = groupedOf(s, list, catalog).sections.firstOrNull { it.kind == SectionKind.UNSORTED }
                val ids = unsorted?.rows?.filterNot { it.manuallyPlaced }?.map { it.id }.orEmpty()
                val report = if (ids.isEmpty()) null else runCatching { sync.sortItems(ids) }.getOrNull()
                if (report != null && report.recategorized > 0) {
                    sorted = report.recategorized
                    left = report.leftUnsorted
                    _events.send(ListEvent.Refreshed(sorted, left, 0))
                }
            }
            if (list != null && waiting.isNotEmpty()) {
                val drawn = awaitResumedStickers(list.id, waiting)
                if (drawn > 0) _events.send(ListEvent.Refreshed(sorted, left, drawn))
            }
        }
    }

    /**
     * Waits (bounded) for the icon repository to finish the stickers of [waiting] and returns how many of them now
     * show a new sticker. Their names join the busy set once the repository has queued them (it may not: without a key
     * nothing is decided), and leave it when they are done.
     */
    private suspend fun awaitResumedStickers(listId: String, waiting: List<GroceryItem>): Int {
        val names = waiting.map { normalizeName(it.name) }.toSet()
        val started = withTimeoutOrNull(DRAW_START_MS) { icons.busy.first { busy -> busy.any { it in names } } } != null
        if (started) withTimeoutOrNull(DRAW_WAIT_MS) { icons.busy.first { busy -> busy.none { it in names } } }
        val now = runCatching { store.current().list(listId) }.getOrNull() ?: return 0
        val before = waiting.associateBy { it.id }
        return now.items.count { item ->
            val was = before[item.id] ?: return@count false
            item.customIconId != was.customIconId || item.icon != was.icon || item.iconFit != was.iconFit
        }
    }

    fun newList(title: String) {
        val id = newId()
        val now = clock()
        mutate { it.newList(id, title, now) }
    }

    fun switchList(id: String) {
        editingId.value = null
        mutate { it.switchList(id) }
    }

    fun renameList(id: String, title: String) = mutate { it.renameList(id, title) }

    /**
     * Deletes a list (§6.4). A list with a trip has its sticker book filed in the album first (a printed receipt as
     * saved), and only then leaves state.json (TripFiling.deleteList, §7.2, checklist 144): a failed album write keeps
     * the list.
     */
    fun deleteList(id: String) {
        val now = clock()
        appScope.launch { save { TripFiling.deleteList(store, album, id, now) } }
    }

    fun openEdit(itemId: String) {
        editingId.value = itemId
    }

    fun closeEdit() {
        editingId.value = null
    }

    /**
     * Edit sheet "Save": name, quantity, note of the item being edited, then closes the sheet. A rename re-resolves the
     * icon locally (StateOps.editItemDetails) and hands the item to the icon cascade.
     */
    fun saveEdit(name: String, quantity: String?, note: String?) {
        val itemId = editingId.value ?: return
        editingId.value = null
        appScope.launch {
            val before = store.current().findItem(itemId) ?: return@launch
            var after: AppState? = null
            save {
                after = store.update { s ->
                    s.listIdOf(itemId)?.let { s.editItemDetails(it, itemId, name, quantity, note, catalog) } ?: s
                }
            }
            val updated = after?.findItem(itemId) ?: return@launch
            if (normalizeName(updated.name) != normalizeName(before.name)) icons.submit(listOf(updated))
        }
    }

    /** "Move to aisle" (null = Unsorted): also teaches the app where this item lives (a learned correction). */
    fun moveToAisle(itemId: String, aisleId: String?) {
        val now = clock()
        mutate { s -> s.listIdOf(itemId)?.let { s.moveItemToAisle(it, itemId, aisleId, now) } ?: s }
    }

    /** "Change icon": a learned icon override; the bespoke icon cached for the name is forgotten. */
    fun setIcon(itemId: String, key: String) {
        appScope.launch {
            val name = store.current().findItem(itemId)?.name ?: return@launch
            val now = clock()
            val ok = save {
                store.update { s -> s.listIdOf(itemId)?.let { s.setItemIcon(it, itemId, key, now, catalog) } ?: s }
            }
            if (ok) icons.forgetBespoke(name)
        }
    }

    /** "Draw a new icon": a forced live drawing that replaces any bespoke icon for this name. */
    fun redrawIcon(itemId: String) {
        appScope.launch {
            val name = store.current().findItem(itemId)?.name ?: return@launch
            icons.redraw(name)
        }
    }

    /** A sound and haptic the screen's own gestures play (the tear-off, the pull to refresh, §14.3). */
    fun cue(cue: Cue) = feedback.cue(cue)

    // ---- Egg hooks (§19.2; WP9's SurpriseEngine) ----

    fun onSunTap() = surprises.onSunTap()

    fun onMoonTap() = surprises.onMoonTap()

    fun onStampTap(position: Int) = surprises.onStampTap(position)

    fun onLcdHold() = surprises.onLcdHold()

    fun onBasketHold(sectionId: String, heldMs: Long) = surprises.onBasketHold(sectionId, heldMs)

    /** A commit landed (§6.2): eggs 3, 9 and 17 fire from it. */
    fun onCommitted(items: List<GroceryItem>) {
        if (items.isNotEmpty()) surprises.onCommitted(items)
    }

    private fun mutate(transform: (AppState) -> AppState) {
        appScope.launch { save { store.update(transform) } }
    }

    /** Runs a store write; a failed write keeps the old state and tells the user. */
    private suspend fun save(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: IOException) {
        _events.send(ListEvent.Message(ScreenText.SAVE_FAILED))
        false
    }

    override fun onCleared() {
        engine.finishAll()
    }

    companion object {
        /** More flips than this in one change (a bulk un-check) play no row chains. */
        private const val MAX_ROW_CHAINS = 6

        /** How long a refresh waits for the icon repository to pick up the stickers it resumed (it debounces). */
        private const val DRAW_START_MS = 3_000L

        /** How long a refresh waits for those stickers before it reports what is done (a drawing takes about 10 s). */
        private const val DRAW_WAIT_MS = 60_000L

        /** The next stop in walking order with something left after [from], wrapping (§10). */
        fun nextStop(g: GroupedList, from: String): String? {
            val order = g.sections
            val i = order.indexOfFirst { it.id == from }
            for (k in 1..order.size) {
                val s = order[(i + k).mod(order.size)]
                if (s.toGet > 0 && s.id != from) return s.id
            }
            return null
        }

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ListViewModel(
                    c.store, c.album, c.icons, c.settings, c.catalog, c.clock, c::newId, c.appScope,
                    feedback = c.feedback, announcer = c.announcer, timeouts = c.timeouts, surprises = c.surprises,
                    aisleSync = c.aisleSync,
                )
            }
        }
    }
}

/** Id of the list that holds [itemId], or null. */
internal fun AppState.listIdOf(itemId: String): String? = lists.firstOrNull { l -> l.items.any { it.id == itemId } }?.id

/** What TalkBack hears for the book (§7.7, §15); [ListStrings.from] reads strings_list.xml. */
data class ListStrings(
    val addedToBook: (String) -> String = { "$it added to today's sticker book" },
    val peeledFromBook: (String) -> String = { "$it peeled out of today's sticker book" },
) {
    companion object {
        val English: ListStrings = ListStrings()
    }
}

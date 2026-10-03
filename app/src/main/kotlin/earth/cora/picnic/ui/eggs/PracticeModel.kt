package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.Stable
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.GroupedList
import earth.cora.picnic.core.model.ListGrouping
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.TripRules
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.setRarity
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.platform.Announcer
import earth.cora.picnic.platform.DefaultTimeouts
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpAnnouncer
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.Timeouts
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.CelebrationContent
import earth.cora.picnic.ui.list.motion.ChangeCause
import earth.cora.picnic.ui.list.motion.ListChange
import earth.cora.picnic.ui.list.motion.MotionChoreographer
import earth.cora.picnic.ui.list.motion.MotionSection
import earth.cora.picnic.ui.list.motion.ToggleEvent
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.ToastHostState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The Surprise lab's practice list (docs/DESIGN_STICKER.md §6.9): its store, its chains and its eggs, all in memory.
 * A small stand-in for WP5's ListViewModel over the sandbox: a check-off goes through the same StateOps and hands WP6's
 * engine the ToggleEvent the real list builds (§8.3, §19.2), so the hanko, the hop, the book magic, the aisle-done chain,
 * the Next card and the celebration all play on the practice copy. Owner: WP9.
 */

/** An in-memory StateStore that can also be read and written at once (the practice list's ops run on the main thread). */
class PracticeStore(initial: AppState) : StateStore {
    private val flow = MutableStateFlow(initial)

    override val states: StateFlow<AppState> = flow.asStateFlow()

    val value: AppState get() = flow.value

    override suspend fun current(): AppState = flow.value

    override suspend fun update(transform: (AppState) -> AppState): AppState = apply(transform)

    /** Applies [transform] now and returns the new state. */
    fun apply(transform: (AppState) -> AppState): AppState = synchronized(this) {
        val next = transform(flow.value)
        if (next !== flow.value) flow.value = next
        flow.value
    }
}

@Stable
class PracticeModel(
    val store: PracticeStore,
    val album: AlbumStore,
    val engine: AppSurpriseEngine,
    val catalog: IconCatalog,
    private val clock: () -> Long,
    feedback: Feedback = NoOpFeedback,
    announcer: Announcer = NoOpAnnouncer,
    timeouts: Timeouts = DefaultTimeouts,
    val listId: String = PracticeFixture.LIST_ID,
) {
    val motion: MotionChoreographer = MotionChoreographer(feedback, announcer, timeouts)
    val anchors: AnchorRegistry get() = motion.anchors
    val toasts: ToastHostState = ToastHostState()

    init {
        motion.listId = listId
        motion.setSections(sections(store.value))
        motion.celebrationSource = { albumNow?.let { CelebrationContent.from(store.value, listId, it, catalog) } }
    }

    /** The album as last read (the celebration's bonus rule reads it). */
    @Volatile var albumNow: earth.cora.picnic.core.store.Album? = earth.cora.picnic.core.store.Album()

    val state: AppState get() = store.value

    /** The practice clock (the sandbox's fake clock). */
    fun now(): Long = clock()

    private var ids = 0

    /** A fresh id for a practice item. */
    fun nextId(): Int = ++ids

    fun list(s: AppState = store.value): GroceryList = s.list(listId) ?: s.lists.first()

    fun grouped(s: AppState = store.value): GroupedList = ListGrouping.group(
        list = list(s),
        aisles = s.store.aisles(),
        basketFor = { s.store.aisleBaskets[it.id] ?: catalog.baskets.guess(it.name) },
        styleFor = { s.store.aisleStyles[it.id] },
    )

    /** Sets the practice copy up without telling anyone (a lab action preparing a scene). */
    fun set(transform: (AppState) -> AppState) {
        store.apply(transform)
        motion.setSections(sections(store.value))
    }

    /** She taps [sectionId]'s strip: it opens and the others fold (§6.1). */
    fun open(sectionId: String?) {
        set { it.setOpenAisle(listId, sectionId) }
        motion.onUserOpen(sectionId)
    }

    /**
     * A check-off tap on [itemId] (§8.3): the store first, then the engine hears the ToggleEvent (the next stop opens
     * behind the Next card; the finished trip folds everything).
     */
    fun toggle(itemId: String, now: Long = clock()) {
        val before = store.value
        val bg = grouped(before)
        val after = store.apply { it.toggleChecked(listId, itemId, now) }
        val ag = grouped(after)
        motion.setSections(sections(after))
        val item = list(after).items.firstOrNull { it.id == itemId } ?: return
        val section = ag.sections.firstOrNull { s -> s.rows.any { it.id == itemId } } ?: return
        val beforeSection = bg.sections.firstOrNull { it.id == section.id }
        val bl = list(before)
        val al = list(after)
        val checked = item.checked
        val aisleChanged = if (checked) section.complete && beforeSection?.complete != true else beforeSection?.complete == true && !section.complete
        val tripChanged = if (checked) TripRules.isComplete(al) && !TripRules.isComplete(bl) else TripRules.isComplete(bl) && !TripRules.isComplete(al)
        val next = if (checked && aisleChanged) nextStop(ag, section.id) else null
        val event = ToggleEvent(
            itemId = itemId,
            sectionId = section.id,
            checked = checked,
            slot = if (checked) item.bookSlot else bl.items.firstOrNull { it.id == itemId }?.bookSlot,
            aisleCompleted = aisleChanged,
            tripCompleted = tripChanged,
            nextSectionId = next,
            personality = catalog.personalityOf(item.icon),
            icon = item.iconRef(),
            holo = item.rarity != Rarity.NONE,
            aisleIndex = if (section.position > 0) section.position - 1 else -1,
            aisleWasOpen = bg.openSectionId == section.id,
            finaleShown = bl.trip?.finaleShownAt != null,
        )
        if (next != null) store.apply { it.setOpenAisle(listId, next) }
        if (tripChanged && checked) store.apply { it.setOpenAisle(listId, "") }
        if (!checked && aisleChanged) store.apply { it.setOpenAisle(listId, section.id) }
        motion.onToggle(event)
    }

    /**
     * Commits [items] as the add flows do (§6.2): new ones roll their rarity (egg 6), the eggs hear of the commit (3, 6,
     * 9, 17), and an aisle or trip the add re-opens plays its in-place form. Returns the ids added.
     */
    fun add(items: List<GroceryItem>, now: Long = clock()): List<String> {
        val before = store.value
        val bg = grouped(before)
        var added: List<String> = emptyList()
        val after = store.apply { s ->
            val (next, outcome) = s.addItems(listId, items, now)
            added = outcome.added
            added.fold(next) { acc, id ->
                val rarity = engine.rollRarity()
                if (rarity == Rarity.NONE) acc else acc.setRarity(listId, id, rarity)
            }
        }
        val ag = grouped(after)
        motion.setSections(sections(after))
        val reopened = bg.sections.filter { s -> s.complete && ag.sections.firstOrNull { it.id == s.id }?.complete == false }.map { it.id }.toSet()
        val tripReopened = TripRules.isComplete(list(before)) && !TripRules.isComplete(list(after))
        if (reopened.isNotEmpty() || tripReopened) {
            motion.onChange(ListChange(emptySet(), reopened, false, tripReopened, null, ChangeCause.ADD, openSectionId = bg.openSectionId))
        }
        val committed = list(after).items.filter { it.id in added }
        if (committed.isNotEmpty()) engine.onCommitted(committed)
        return added
    }

    companion object {
        /** The next stop in walking order with something left after [from], wrapping (§10). */
        fun nextStop(g: GroupedList, from: String): String? {
            val order = g.sections
            val i = order.indexOfFirst { it.id == from }
            for (k in 1..order.size) {
                val s = order[(i + k).mod(order.size)]
                if (s.toGet > 0) return s.id
            }
            return null
        }

        /** The engine's view of every section (the Next card's stop, the "done" announcement). */
        fun sections(s: AppState): List<MotionSection> {
            val list = s.lists.firstOrNull { it.id == s.currentListId } ?: return emptyList()
            val g = ListGrouping.group(list, s.store.aisles(), { s.store.aisleBaskets[it.id] ?: IconCatalog.default.baskets.guess(it.name) }, { s.store.aisleStyles[it.id] })
            return g.sections.map { sec ->
                MotionSection(
                    id = sec.id,
                    position = sec.position,
                    displayName = if (sec.kind == SectionKind.UNSORTED) EVERYTHING_ELSE else sec.displayName,
                    fullName = sec.aisle?.name ?: EVERYTHING_ELSE,
                    storeAisle = sec.storeAisle,
                    look = if (sec.kind == SectionKind.UNSORTED) AisleLooks.everythingElse else AisleLooks.of(sec.position, sec.aisle?.let { s.store.aisleStyles[it.id] }),
                )
            }
        }

        private const val EVERYTHING_ELSE = "Everything else"
    }
}

package earth.cora.picnic.ui.list

import androidx.compose.runtime.Immutable
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.catalog.Personality
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.EVERYTHING_ELSE_NAME
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.GroupedList
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ListGrouping
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.TripRules
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.TripStatsValue
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.stamps
import earth.cora.picnic.core.model.tripBook
import earth.cora.picnic.core.store.TripBook
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.ui.a11y.spokenItemLabel
import earth.cora.picnic.ui.list.motion.BasketMini
import earth.cora.picnic.ui.list.motion.MotionSection
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.StickerFlag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * UI state of the List screen in C (docs/DESIGN_STICKER.md §6.1, §19.2): the top stack (title, subtitle, LCD, the trip's
 * sticker book), one entry per stop in walking order ("Everything else" first whenever it has an item this trip), each
 * a folded strip or the one open tray, rows in place (D1). Pure: [buildListUiState] maps an AppState (screenshot tests
 * build realistic states with it). Names are binding (ARCHITECTURE §10.3); fields may be added. Owner: WP5.
 */

@Immutable
data class ListUiState(
    val loading: Boolean = true,
    val listId: String = "",
    val title: String = "",
    val lists: List<ListSummary> = emptyList(),
    /** Every stop in walking order ("Everything else" first when it has items), each with its rows in place. */
    val sections: List<SectionUi> = emptyList(),
    /** Items still to get. */
    val remaining: Int = 0,
    /** Items on the list. */
    val total: Int = 0,
    val editing: EditItemUi? = null,
    /** StoreProfile.name: the app bar's subtitle ("★ Hillcrest Market · 8 stops"). */
    val storeName: String = "",
    /** The trip's sticker book; null while the list is empty (§4.21). */
    val book: BookUi? = null,
    /** The app bar's LCD toy (§13.1). */
    val lcd: LcdState = LcdState(),
    /** The open trip, or null before the first check-off. */
    val trip: TripUi? = null,
    /** The open section (GroceryList.openAisle resolved, D2); null = all folded. */
    val openSectionId: String? = null,
    /** The automatic current aisle (D2): the first stop with something left. */
    val currentSectionId: String? = null,
    /** The "now" ring on the stamp page (§4.13). */
    val nowSectionId: String? = null,
    /** Stops ("Everything else" included). */
    val stops: Int = 0,
    /** New trip is offered only while the list has a trip (§6.5). */
    val canStartNewTrip: Boolean = false,
    /** Surprises → Easter eggs (the LCD hold, the hanko variants). */
    val eggs: Boolean = true,
    /** Bumped each time a New trip turns the book to a fresh page (the book plays its page turn on a change). */
    val bookTurn: Int = 0,
    /** Surprises → "Next stop" card (§10). */
    val nextStopCard: Boolean = true,
    /** Surprises → Celebration (§11). */
    val celebration: Boolean = true,
    /** The book as the Trip book sheet shows it (§6.6): a snapshot of the open trip (or of today's empty book). */
    val tripBook: TripBook? = null,
) {
    val checked: Int get() = total - remaining

    /** The section that is open (its tray), if any. */
    val openSection: SectionUi? get() = sections.firstOrNull { it.id == openSectionId }

    /** The row with [itemId], anywhere on the list. */
    fun row(itemId: String): ItemRowUi? = sections.firstNotNullOfOrNull { s -> s.rows.firstOrNull { it.id == itemId } }

    /** The engine's view of every section: the Next card's stop and the "done" announcement (§10, §15). */
    fun motionSections(): List<MotionSection> = sections.map { s ->
        MotionSection(id = s.id, position = s.position, displayName = s.displayName, fullName = s.fullName, storeAisle = s.storeAisle, look = s.look)
    }
}

/**
 * One list in the switcher (§6.4). [bookStickers]: stickers in its open trip's book (its checked items while it has a
 * trip, else 0); the delete confirm sheet says "Its sticker book goes into your album." when it is above 0 (checklist 144).
 */
data class ListSummary(val id: String, val title: String, val remaining: Int, val current: Boolean, val bookStickers: Int = 0)

/** One stop (§4.10): a folded strip, or the open tray with its rows in place. */
@Immutable
data class SectionUi(
    /** Aisle id or SectionIds.UNSORTED (also the value GroceryList.openAisle names). */
    val id: String,
    val kind: SectionKind,
    /** Walking position (the number circle, D9); 0 for "Everything else" (the capsule, no numeral). */
    val position: Int,
    /** The tab's name: the aisle name without its store prefix (§4.10); "Everything else" for the Unsorted stop. */
    val displayName: String,
    /** The full aisle name, for TalkBack (tabs and strips read it even when the tab ellipsizes). */
    val fullName: String,
    /** The store's own aisle number (the "A2" chip), or null. */
    val storeAisle: Int?,
    /** Colour + pattern + pictogram (§2.3), with the aisle editor's overrides. */
    val look: AisleLook,
    val basket: BasketKind,
    /** Every item of the stop, checked or not, in place (D1). */
    val rows: List<ItemRowUi>,
    /** What the basket holds (R4): the checked items in check-off order. */
    val gotten: List<BasketMini>,
    /** Items still to get: the badge and the strip meta. */
    val toGet: Int,
    val total: Int,
    /** Every item is checked: lid shut, GOT IT on the lid, ribbon tied, stamped. */
    val complete: Boolean,
    /** A Hachi Maru doodle beside the tab ("brrr…" by Frozen), decorative. */
    val doodle: String? = null,
) {
    /** The capsule's rotation index (position − 1), −1 for "Everything else". */
    val aisleIndex: Int get() = if (position > 0) position - 1 else -1

    val isEverythingElse: Boolean get() = kind == SectionKind.UNSORTED
}

/** One row (§4.11). */
@Immutable
data class ItemRowUi(
    val id: String,
    val name: String,
    /** The detail line after metric normalisation (§16, checklist 296): "1 L basket", "225 g tin · from your recipe". */
    val detail: String?,
    /** IconCatalog.displayRef(item, busy): the capsule while an icon is decided or drawn (D5). */
    val icon: IconRef,
    /** The icon is being decided or drawn (the busy sparkle). */
    val drawing: Boolean,
    val checked: Boolean,
    /** What TalkBack hears: "Strawberries, 1 litre basket" (units in words, §15). */
    val spoken: String = name,
    /** New star, favourite heart, shiny star (§3.3 step 4). */
    val flags: Set<StickerFlag> = emptySet(),
    val rarity: Rarity = Rarity.NONE,
    /** The book spot its twin sits in; null while unchecked. */
    val slot: Int? = null,
    /** Its stop. */
    val sectionId: String = "",
    /** The capsule's tint (0-based rotation index, −1 neutral). */
    val aisleIndex: Int = -1,
    /** The hop's personality (§8.3). */
    val personality: Personality = Personality.NONE,
    /** GOT / IT! (HAPPY / B-DAY! for egg 9, JACK / POT! for egg 13). */
    val hanko: HankoText = HankoText.GotIt,
) {
    val holo: Boolean get() = rarity != Rarity.NONE
}

/** The trip's sticker book (§7.3, §19.2). */
@Immutable
data class BookUi(
    /** The open trip's id; null before the first check-off (the book shows today and empty spots). */
    val tripId: String?,
    /** "Sat 3 Oct": the first check-off of the trip's last session, or today before the trip opens (§7.1). */
    val dateLabel: String,
    /** The active seasonal sheet (egg 11): its pictogram rides on the date tag. */
    val season: Season? = null,
    /** Spots: the items on the list. */
    val total: Int,
    /** The twins, in spot order. */
    val stickers: List<BookStickerUi>,
    /** One circle per stop, walking order, "Everything else" first. */
    val stamps: List<StampUi>,
    /** PER-FECT! across the spine and the holo stamp page (after the finale). */
    val perfect: Boolean,
    /** Every item checked: holo release paper and fore-edge. */
    val full: Boolean,
    /** The printed receipt is tucked in (the cream slip on the fore-edge). */
    val receiptSaved: Boolean,
    /** Bonus stage (egg 10): every twin is a pixel sprite. */
    val pixel: Boolean = false,
    /** A perfect week (egg 14): the stamp page is holo on every book, PERFECT or not (the PER-FECT! hanko stays the finale's). */
    val holoStamps: Boolean = false,
) {
    val checked: Int get() = stickers.size
    val stamped: Int get() = stamps.count { it.stamped }
    val takenSlots: Set<Int> get() = stickers.mapTo(HashSet()) { it.slot }
}

/** A twin in its spot. */
@Immutable
data class BookStickerUi(
    val slot: Int,
    val itemId: String,
    val name: String,
    val icon: IconRef,
    val holo: Boolean,
    val aisleIndex: Int,
)

/** A stamp circle on the stamp page (§4.13). */
@Immutable
data class StampUi(
    val sectionId: String,
    /** The walking number; 0 = "Everything else" (capsule, no numeral). */
    val position: Int,
    val look: AisleLook,
    val displayName: String,
    val stamped: Boolean,
    /** The "now" ring. */
    val now: Boolean,
    val toGet: Int,
)

/** The open trip (§7.1). */
@Immutable
data class TripUi(
    val id: String,
    val complete: Boolean,
    val finaleShown: Boolean,
    val stats: TripStatsValue,
    val receiptSaved: Boolean,
    val printed: Boolean,
    val lastActivityAt: Long,
)

data class EditItemUi(
    val item: GroceryItem,
    /** Current aisles in walking order (for "Move to aisle"). */
    val aisles: List<Aisle>,
    /** Catalog keys for "Change icon": the item's category first, then catalog order. */
    val iconChoices: List<String>,
    /** "Draw a new icon" needs a token (it calls the image model). */
    val canDraw: Boolean,
    /** What the row shows right now (IconCatalog.displayRef). */
    val icon: IconRef = item.iconRef(),
    /** The icon is being decided or drawn. */
    val drawing: Boolean = false,
) {
    /** The aisle the item is listed under (null = Unsorted; vanished ids count as Unsorted). */
    val currentAisleId: String? get() = item.aisleId?.takeIf { id -> aisles.any { it.id == id } }
}

/** One-off events for the screen's sticky notes (§4.15, §16). */
sealed interface ListEvent {
    /** A tear-off or a Delete: "Oat cereal torn out" with Undo (merges into "2 items torn out"). */
    data class Deleted(val item: GroceryItem, val index: Int, val listId: String = "") : ListEvent

    /** The first check-off after 12 h idle started a new book (§0.4 Q9): the toast with Undo. */
    data class NewBook(val listId: String, val undo: () -> Unit) : ListEvent

    /** New trip filed the book: "New trip · a fresh sticker book ♡". */
    data object NewTrip : ListEvent

    /** The list opened on a trip idle for over 12 h: "New shop? Start a fresh sticker book" + New trip (§6.1). */
    data class Nudge(val listId: String) : ListEvent

    /** Pull to refresh finished (§6.1): what changed; nothing shows when nothing did. */
    data class Refreshed(val sorted: Int, val left: Int, val drawn: Int) : ListEvent

    data class Message(val text: String) : ListEvent
}

/**
 * "1 L basket · from your recipe": the quantity and the note, metric after normalisation (§16, checklist 296), or null.
 * The same line the add flows show (a recipe amount after the quantity's "·" may stay in cooking measures; the note may
 * not), so a row reads the same in the recipe card and on the list.
 */
internal fun GroceryItem.detailLine(): String? = earth.cora.picnic.ui.add.AddRules.detailLine(quantity, note)

/** Catalog keys with [item]'s category first (catalog order otherwise; the paper bag gets plain catalog order). */
internal fun iconChoicesFor(item: GroceryItem, catalog: IconCatalog): List<String> {
    val category = if (item.icon == IconCatalog.DEFAULT_ICON) null else catalog.categoryOf(item.icon)?.key
    if (category == null) return catalog.keys
    val (first, rest) = catalog.keys.partition { catalog[it]?.category == category }
    return first + rest
}

/** The book's date format (§7.3 "Sat 3 Oct"), as the album prints it. */
internal val BOOK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

/** The zone trips are dated in (the album's, §7.1). */
internal val TORONTO: ZoneId = ZoneId.of("America/Toronto")

/** The grouped list of [list] in [state] (§19.1), with the store's baskets and styles. */
internal fun groupedOf(state: AppState, list: GroceryList, catalog: IconCatalog): GroupedList = ListGrouping.group(
    list = list,
    aisles = state.store.aisles(),
    basketFor = { aisle -> state.store.aisleBaskets[aisle.id] ?: catalog.baskets.guess(aisle) },
    styleFor = { aisle -> state.store.aisleStyles[aisle.id] },
)

/**
 * Pure mapping AppState -> ListUiState for the current list (also used by screenshot tests to build realistic states).
 * [busy] holds normalized names whose icon is being decided or drawn. [now] dates the book before the trip opens.
 */
internal fun buildListUiState(
    state: AppState,
    busy: Set<String>,
    hasToken: Boolean,
    editingId: String?,
    catalog: IconCatalog,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = TORONTO,
    eggs: Boolean = true,
    season: Season? = null,
    bonusStage: Boolean = false,
    bookTurn: Int = 0,
    /** Egg 14: a perfect week makes every stamp page holo. */
    perfectWeek: Boolean = false,
    /** The hanko a checked item wears (egg 9's HAPPY / B-DAY! while Easter eggs are on; WP9's SurpriseState.hankoFor). */
    hankoFor: (GroceryItem) -> HankoText = { HankoText.GotIt },
): ListUiState {
    val list = state.currentList()
    val aisles = state.store.aisles()
    val grouped = groupedOf(state, list, catalog)
    val newAnchor = TripRules.newAnchor(list)

    fun look(sectionAisle: Aisle?): AisleLook =
        if (sectionAisle == null) AisleLooks.everythingElse else AisleLooks.of(sectionAisle.position, state.store.aisleStyles[sectionAisle.id])

    fun row(item: GroceryItem, sectionId: String, aisleIndex: Int): ItemRowUi {
        val isBusy = normalizeName(item.name) in busy
        val detail = item.detailLine()
        val flags = buildSet {
            // A shiny or jackpot pull wears ONE lemon star (§4.11, §12.2 eggs 6 and 13): it is always new too, and the
            // New flag is the same lemon star, so the shiny one stands for both.
            if (newAnchor != null && item.addedAt > newAnchor && item.rarity == Rarity.NONE) add(StickerFlag.New)
            if (normalizeName(item.name) in state.favourites) add(StickerFlag.Favourite)
            if (item.rarity != Rarity.NONE) add(StickerFlag.Shiny)
        }
        return ItemRowUi(
            id = item.id,
            name = item.name,
            detail = detail,
            icon = catalog.displayRef(item, isBusy),
            drawing = isBusy,
            checked = item.checked,
            spoken = spokenItemLabel(item.name, detail),
            flags = flags,
            rarity = item.rarity,
            slot = item.bookSlot,
            sectionId = sectionId,
            aisleIndex = aisleIndex,
            personality = catalog.personalityOf(item.icon),
            hanko = hankoFor(item),
        )
    }

    val sections = grouped.sections.map { section ->
        val aisle = section.aisle
        val look = look(aisle)
        val unsorted = section.kind == SectionKind.UNSORTED
        val aisleIndex = if (section.position > 0) section.position - 1 else -1
        SectionUi(
            id = section.id,
            kind = section.kind,
            position = section.position,
            displayName = if (unsorted) EVERYTHING_ELSE_NAME else section.displayName,
            fullName = aisle?.name ?: EVERYTHING_ELSE_NAME,
            storeAisle = section.storeAisle,
            look = look,
            basket = section.basket,
            rows = section.rows.map { row(it, section.id, aisleIndex) },
            gotten = section.gotten.map { item ->
                BasketMini(item.id, catalog.displayRef(item, normalizeName(item.name) in busy), holo = item.rarity != Rarity.NONE, aisleIndex = aisleIndex, pixel = bonusStage)
            },
            toGet = section.toGet,
            total = section.total,
            complete = section.complete,
            doodle = if (aisle != null && FROZEN.containsMatchIn(section.displayName)) FROZEN_DOODLE else null,
        )
    }

    val trip = list.trip
    val checked = grouped.checked
    val stats = TripStats.of(list, grouped.stops)
    val book = if (grouped.total == 0) {
        null
    } else {
        val dateMillis = TripStats.lastSessionStart(list) ?: now
        val perfect = trip?.completedAt != null && trip.finaleShownAt != null
        BookUi(
            tripId = trip?.id,
            dateLabel = BOOK_DATE.format(Instant.ofEpochMilli(dateMillis).atZone(zone)),
            season = season,
            total = grouped.total,
            stickers = list.items.filter { it.checked && it.bookSlot != null }.sortedBy { it.bookSlot }.map { item ->
                val section = sections.firstOrNull { s -> s.rows.any { it.id == item.id } }
                BookStickerUi(
                    slot = item.bookSlot!!,
                    itemId = item.id,
                    name = item.name,
                    icon = catalog.displayRef(item, normalizeName(item.name) in busy),
                    holo = item.rarity != Rarity.NONE,
                    aisleIndex = section?.aisleIndex ?: -1,
                )
            },
            stamps = sections.map { s ->
                StampUi(s.id, s.position, s.look, s.displayName, stamped = s.complete, now = s.id == grouped.nowSectionId, toGet = s.toGet)
            },
            perfect = perfect,
            full = checked == grouped.total,
            receiptSaved = trip?.receiptSaved == true && trip.receipt != null,
            pixel = bonusStage,
            holoStamps = perfectWeek,
        )
    }

    val editing = editingId
        ?.let { id -> list.items.firstOrNull { it.id == id } }
        ?.let { item ->
            val isBusy = normalizeName(item.name) in busy
            EditItemUi(
                item = item,
                aisles = aisles,
                iconChoices = iconChoicesFor(item, catalog),
                canDraw = hasToken,
                icon = catalog.displayRef(item, isBusy),
                drawing = isBusy,
            )
        }

    val complete = trip?.completedAt != null
    val tripBook = if (book == null) {
        null
    } else {
        val snapshot = state.tripBook(list.id, now) ?: TripBook(
            id = "",
            listTitle = list.title,
            storeName = state.store.name,
            startedAt = now,
            completedAt = null,
            closedAt = now,
            total = list.items.size,
            minutes = null,
            stickers = emptyList(),
            stamps = state.stamps(list),
            perfect = false,
            receipt = null,
        )
        // PER-FECT! shows once the finale is over (§7.5); a printed receipt shows once it is tucked in (§13.2).
        // The nap cat (egg 8) shows on a FILED book's page only (§12.2): the open trip's cat sleeps in the list's basket.
        snapshot.copy(perfect = book.perfect, receipt = snapshot.receipt.takeIf { book.receiptSaved }, napAisleId = null)
    }
    return ListUiState(
        loading = false,
        listId = list.id,
        title = list.title,
        lists = state.lists.map { l ->
            ListSummary(
                id = l.id,
                title = l.title,
                remaining = l.items.count { !it.checked },
                current = l.id == list.id,
                bookStickers = if (l.trip == null) 0 else l.items.count { it.checked },
            )
        },
        sections = sections,
        remaining = grouped.total - checked,
        total = grouped.total,
        editing = editing,
        storeName = state.store.name,
        book = book,
        lcd = LcdState(
            mode = if (eggs && trip?.pet == true) LcdMode.Pet else LcdMode.Count,
            count = checked,
            total = grouped.total,
            receiptReady = eggs && complete,
        ),
        trip = trip?.let {
            TripUi(
                id = it.id,
                complete = complete,
                finaleShown = it.finaleShownAt != null,
                stats = stats,
                receiptSaved = it.receiptSaved,
                printed = it.receipt != null,
                lastActivityAt = it.lastActivityAt,
            )
        },
        openSectionId = grouped.openSectionId,
        currentSectionId = grouped.currentSectionId,
        nowSectionId = grouped.nowSectionId,
        stops = grouped.stops,
        canStartNewTrip = trip != null,
        eggs = eggs,
        bookTurn = bookTurn,
        tripBook = tripBook,
    )
}

/** Frozen's doodle (§4.10): "brrr…" beside its tab. */
private val FROZEN = Regex("""\bfrozen\b""", RegexOption.IGNORE_CASE)
private const val FROZEN_DOODLE = "brrr…"

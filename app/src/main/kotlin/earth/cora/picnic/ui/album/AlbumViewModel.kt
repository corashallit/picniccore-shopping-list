package earth.cora.picnic.ui.album

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.calendar.Seasons
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.CUSTOM_KEY_PREFIX
import earth.cora.picnic.core.store.Collected
import earth.cora.picnic.core.store.TripBook
import earth.cora.picnic.core.store.markSeen
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.ui.theme.StickerLibrary
import earth.cora.picnic.ui.theme.gen.StickerManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The sticker album (docs/DESIGN_STICKER.md §6.10, §4.25, checklist 165, 230, 231) over WP3's AlbumStore. Owner: WP8
 * (AlbumTest). What was really bought (Album.collected), the filed trip books (newest first), the picnic, sakura and
 * seasonal sheets, the perfect-week streak and the back pocket.
 */

/** The album's three tabs (§6.10). */
enum class AlbumTab { Groceries, Picnic, Seasonal }

/**
 * One vinyl pocket. [icon] null = not collected yet (the puffy "?"). [stars] = rarity 1–3 (§4.25): one for a sticker
 * bought on a trip, two once bought on [TWO_STARS] trips, three for a holo pull (shiny, jackpot, bonus) or [THREE_STARS]
 * trips. [fresh] = collected and not yet shown: it magic-appears once (R5), then is marked seen.
 */
@Immutable
data class PocketUi(
    val key: String,
    val name: String,
    val icon: IconRef?,
    val decor: String? = null,
    val holo: Boolean = false,
    val stars: Int = 0,
    val face: Boolean = false,
    val fresh: Boolean = false,
) {
    val collected: Boolean get() = icon != null || (decor != null && stars > 0)

    companion object {
        const val TWO_STARS = 3
        const val THREE_STARS = 10
    }
}

/** A labelled run of pockets (a catalog category, "Drawn for you", a sheet). */
@Immutable
data class PocketGroup(val title: String, val pockets: List<PocketUi>)

/**
 * One filed trip book's row (§4.25): up to 6 minis, the date, "19 of 19 stickers · perfect · receipt". [fresh] = filed
 * since the album last showed it: the row blooms in with the magic appear once (§7.8), then it is marked seen.
 */
@Immutable
data class BookRowUi(
    val id: String,
    val dateLabel: String,
    val minis: List<IconRef>,
    val stickers: Int,
    val total: Int,
    val perfect: Boolean,
    val receipt: Boolean,
    val book: TripBook,
    val fresh: Boolean = false,
    /** The trip's date (§7.1: the first check-off of its last session), as [dateLabel] shows it; printed on the receipt. */
    val date: LocalDate? = null,
)

/** A seasonal card (§4.25): unlocked or not (55 % until then), flagged "next!" with its date. */
@Immutable
data class SeasonCardUi(val sheet: SeasonSheet, val unlocked: Boolean, val nextDate: LocalDate?)

@Immutable
data class AlbumUiState(
    val loading: Boolean = true,
    val tab: AlbumTab = AlbumTab.Groceries,
    /** "★ 64 of 216 collected · 3 holo": collected and collectible count live-drawn stickers too. */
    val collected: Int = 0,
    val collectible: Int = 0,
    val holo: Int = 0,
    val groceries: List<PocketGroup> = emptyList(),
    val picnic: List<PocketUi> = emptyList(),
    /** The sakura sheet, once a perfect week unlocked it (else null). */
    val sakura: List<PocketUi>? = null,
    val seasons: List<SeasonCardUi> = emptyList(),
    val books: List<BookRowUi> = emptyList(),
    /** Complete trips in a row (0–4 shown as stamped circles). */
    val perfectStreak: Int = 0,
    /** The sakura sheet is unlocked: "week 4 of 4 · sakura sheet unlocked" and the PER-FECT! hanko. */
    val perfectEarned: Boolean = false,
    /** The perfect-week card is holo for the week after a perfect week (egg 14, Album.perfectUntil). */
    val perfectHolo: Boolean = false,
    val backPocketOpen: Boolean = false,
    val listTitle: String = "",
) {
    /** Nothing collected and no book filed: the album's empty state (§6.12). */
    val empty: Boolean get() = !loading && collected == 0 && books.isEmpty() && picnic.none { it.collected }
}

class AlbumViewModel(
    private val album: AlbumStore,
    private val catalog: IconCatalog,
    private val today: () -> LocalDate,
    listTitle: Flow<String> = flowOf(""),
    private val zone: ZoneId = ZoneId.of("America/Toronto"),
    private val now: () -> Long = System::currentTimeMillis,
    /** Which filed books the album has shown (device-local, SettingsRepository.seenBooks); null: every book counts as seen. */
    private val seenBooks: Flow<Set<String>>? = null,
    private val markBooksSeen: suspend (Collection<String>) -> Unit = {},
) : ViewModel() {
    private val tab = MutableStateFlow(AlbumTab.Groceries)
    private val builder = AlbumStateBuilder(catalog, today, zone, now)

    /** Keys that were fresh when the album opened: they keep their magic appear until it has played on screen. */
    private val playing = MutableStateFlow<Set<String>?>(null)

    /** Books filed since the album last showed them, fixed when it opens: each row blooms once (§7.8). */
    private val playingBooks = MutableStateFlow<Set<String>?>(null)

    private val seen: Flow<Set<String>?> = seenBooks ?: flowOf(null)

    val state: StateFlow<AlbumUiState> = combine(album.album, tab, listTitle, playing, combine(playingBooks, seen) { b, s -> b to s }) { a, t, title, p, (b, s) ->
        val books = b ?: (if (s == null) emptySet() else a.books.map { it.id }.toSet() - s).also { playingBooks.value = it }
        builder.build(a, t, title, p ?: freshKeys(a).also { playing.value = it }, books)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AlbumUiState())

    fun selectTab(t: AlbumTab) = tab.update { t }

    /** A filed book's row played its magic appear (§7.8): it is marked seen and never blooms again. */
    fun bookAppeared(id: String) {
        playingBooks.update { it?.minus(id) }
        viewModelScope.launch {
            try {
                markBooksSeen(listOf(id))
            } catch (e: IOException) {
                PicnicLog.w("couldn't mark a trip book seen", e)
            }
        }
    }

    /** The magic appear of [key] played on screen (R5): it is marked seen and never plays again. */
    fun appeared(key: String) {
        playing.update { it?.minus(key) }
        viewModelScope.launch {
            try {
                album.update { it.markSeen(listOf(key)) }
            } catch (e: IOException) {
                PicnicLog.w("couldn't mark a pocket seen", e)
            }
        }
    }

    private fun freshKeys(a: Album): Set<String> = a.collected.filterValues { !it.seen }.keys

    companion object {
        /** Trips in a row that make a perfect week (egg 14). */
        const val PERFECT_WEEK = 4

        /** The group of live-drawn stickers. */
        const val DRAWN_FOR_YOU = "Drawn for you"

        /** "Sat 26 Sep" (§7.3's date label; the US English short names carry no full stops, unlike en-CA's "Sat."). */
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

        /** "Oct 12" (the seasonal card's "next!" chip). */
        val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        fun stars(c: Collected?): Int = when {
            c == null -> 0
            c.holo || c.count >= PocketUi.THREE_STARS -> 3
            c.count >= PocketUi.TWO_STARS -> 2
            else -> 1
        }

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AlbumViewModel(
                    c.album,
                    c.catalog,
                    { Instant.ofEpochMilli(c.clock()).atZone(ZoneId.of("America/Toronto")).toLocalDate() },
                    c.store.states.map { it.currentList().title },
                    now = c.clock,
                    seenBooks = c.settings.seenBooks,
                    markBooksSeen = c.settings::markBooksSeen,
                )
            }
        }
    }
}

/**
 * Builds the album's UI state from the album (pure, no I/O): the ViewModel, the goldens and the tests build it the same
 * way. [today] picks the upcoming season ("next!"), [now] tells whether the perfect-week card is holo.
 */
class AlbumStateBuilder(
    private val catalog: IconCatalog,
    private val today: () -> LocalDate,
    private val zone: ZoneId = ZoneId.of("America/Toronto"),
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun build(a: Album, t: AlbumTab, title: String, fresh: Set<String>, freshBooks: Set<String> = emptySet()): AlbumUiState {
        val collectibleKeys = catalog.collectibleKeys
        val collectibleSet = collectibleKeys.toSet()
        val drawn = a.collected.filterKeys { it.startsWith(CUSTOM_KEY_PREFIX) }
        val collectedCount = a.collected.keys.count { it in collectibleSet || it.startsWith(CUSTOM_KEY_PREFIX) }
        val holo = a.collected.values.count { it.holo }

        fun pocket(key: String, name: String, c: Collected?, icon: IconRef) = PocketUi(
            key = key,
            name = c?.name?.takeIf { key.startsWith(CUSTOM_KEY_PREFIX) } ?: name,
            icon = if (c != null) icon else null,
            holo = c?.holo == true,
            stars = AlbumViewModel.stars(c),
            face = StickerLibrary.resolve(key, catalog)?.hasFace == true,
            fresh = c != null && key in fresh,
        )

        val byCategory = collectibleKeys.groupBy { catalog[it]?.category.orEmpty() }
        val groceries = catalog.categories.mapNotNull { category ->
            val keys = byCategory[category.key] ?: return@mapNotNull null
            PocketGroup(category.label, keys.map { key -> pocket(key, catalog[key]?.label ?: key, a.collected[key], IconRef(key)) })
        } + listOfNotNull(
            drawn.takeIf { it.isNotEmpty() }?.let { d ->
                PocketGroup(
                    AlbumViewModel.DRAWN_FOR_YOU,
                    d.entries.sortedBy { it.value.firstAt }.map { (key, c) ->
                        pocket(key, c.name, c, IconRef(StickerManifest.CAPSULE, key.removePrefix(CUSTOM_KEY_PREFIX)))
                    },
                )
            },
        )

        fun sheetPocket(s: SheetSticker, won: Boolean) = PocketUi(
            key = s.ref,
            name = s.label,
            icon = if (won && !s.isDecor) IconRef(s.key) else null,
            decor = s.key.takeIf { s.isDecor },
            stars = if (won) 1 else 0,
        )
        val picnic = AlbumSheets.PICNIC.map { sheetPocket(it, it.ref in a.picnic || it.key in a.picnic) }
        val sakuraOpen = AlbumSheets.SAKURA in a.sheets
        val sakura = if (sakuraOpen) AlbumSheets.SAKURA_STICKERS.map { sheetPocket(it, true) } else null

        val next = upcoming(today(), a.sheets)
        val seasons = AlbumSheets.SEASONS.map { sheet ->
            SeasonCardUi(sheet, AlbumSheets.unlocked(sheet.season, a.sheets), next?.takeIf { it.first == sheet.season }?.second)
        }

        return AlbumUiState(
            loading = false,
            tab = t,
            collected = collectedCount,
            collectible = collectibleKeys.size + drawn.size,
            holo = holo,
            groceries = groceries,
            picnic = picnic,
            sakura = sakura,
            seasons = seasons,
            books = a.books.map { bookRow(it, fresh = it.id in freshBooks) },
            perfectStreak = a.perfectStreak,
            perfectEarned = sakuraOpen || a.perfectStreak >= AlbumViewModel.PERFECT_WEEK,
            perfectHolo = (a.perfectUntil ?: 0L) > now(),
            backPocketOpen = a.backPocketOpen,
            listTitle = title,
        )
    }

    /**
     * The season the Seasonal tab flags "next!" (§6.10: "Thanksgiving Oct 12" on 3 October): the one whose window she
     * is in, while its day is still to come and its sheet not yet unlocked this year; otherwise the next window to start.
     */
    fun upcoming(date: LocalDate, sheets: Set<String>): Pair<Season, LocalDate>? {
        val current = Seasons.active(date)?.let { season ->
            (date.year - 1..date.year + 1).mapNotNull { y -> Seasons.dayOf(season, y) }
                .firstOrNull { day -> date in Seasons.window(season to day) }
                ?.let { day -> season to day }
        }
        if (current != null && !current.second.isBefore(date) && Seasons.sheetId(current.first, current.second) !in sheets) return current
        return Seasons.next(date)
    }

    fun bookRow(b: TripBook, fresh: Boolean = false): BookRowUi {
        val times = b.stickers.map { it.at }.sorted()
        var start = times.lastOrNull() ?: b.startedAt
        for (i in times.size - 2 downTo 0) {
            if (start - times[i] > TripStats.SESSION_BREAK_MILLIS) break
            start = times[i]
        }
        return BookRowUi(
            id = b.id,
            dateLabel = AlbumViewModel.DATE.format(Instant.ofEpochMilli(start).atZone(zone)),
            date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate(),
            minis = b.stickers.sortedBy { it.slot }.take(6).map { it.icon },
            stickers = b.stickers.size,
            total = b.total,
            perfect = b.perfect,
            receipt = b.receipt != null,
            book = b,
            fresh = fresh,
        )
    }
}

/** Seasons' display names for the "next!" chip. */
internal fun Season.holidayName(): String = AlbumSheets.sheetOf(this).holiday

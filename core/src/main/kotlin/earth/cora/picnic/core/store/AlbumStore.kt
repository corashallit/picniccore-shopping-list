package earth.cora.picnic.core.store

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.Receipt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException

/*
 * The sticker album (filesDir/album.json, docs/DESIGN_STICKER.md §7.2): what was really bought, the filed
 * trip books, unlocked sheets and the perfect-week streak. A second file next to state.json, on the backup
 * allow-list. Owner: WP3 (AlbumStoreTest). Persisted names (every field below) need a migration to change,
 * like state.json (ARCHITECTURE §6.4).
 */

/** album.json's schema version. */
const val ALBUM_SCHEMA_VERSION: Int = 1

@Serializable
data class Album(
    val version: Int = ALBUM_SCHEMA_VERSION,
    /** Key: a catalog key, or "c:<customIconId>" for a live-drawn sticker. Collected = checked with a real sticker (§7.2). */
    val collected: Map<String, Collected> = emptyMap(),
    /** Filed trip books, newest first (cap [Album.MAX_BOOKS]; the oldest drop off). */
    val books: List<TripBook> = emptyList(),
    /** Unlocked sheets: "sakura", "sheet2", "thanksgiving-2026", "first-snow-2026", ... */
    val sheets: Set<String> = emptySet(),
    /** Picnic-sheet stickers won as celebration bonuses. */
    val picnic: List<String> = emptyList(),
    /** Trips filed with at least one sticker, across all lists (egg 14's back pocket opens at ten). */
    val finishedTrips: Int = 0,
    /** Complete trips in a row, across all lists in filing order (egg 14, §7.8). */
    val perfectStreak: Int = 0,
    /** Every book's stamp page is holo until then (egg 14). */
    val perfectUntil: Long? = null,
    val backPocketOpen: Boolean = false,
    /**
     * Bookkeeping that makes collecting idempotent: "<tripId>|<itemId>|<key>" for each sticker counted on a trip
     * that is still open. Filing the trip drops its entries; [AlbumReconciler] drops those of trips that closed
     * without filing. Never shown.
     */
    val tally: Set<String> = emptySet(),
) {
    companion object {
        /** Filed books kept (§7.2). */
        const val MAX_BOOKS: Int = 200
    }
}

@Serializable
data class Collected(
    val name: String,
    val firstAt: Long,
    /** How many trips it was bought on (one per trip, however often it was un-checked and re-checked). */
    val count: Int,
    /** A holo pocket: collected as a shiny or jackpot sticker, or won holo as a celebration bonus. */
    val holo: Boolean = false,
    /** The album has shown its magic appear (R5: it appears in place on first view). */
    val seen: Boolean = false,
)

/** A filed trip sticker book (§7.8). Filing is idempotent by [id] (= Trip.id). */
@Serializable
data class TripBook(
    val id: String,
    val listTitle: String,
    val storeName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val closedAt: Long,
    /** Items on the list when it was filed: the book's spots. */
    val total: Int,
    /** Frozen shop time; null for a trip that never completed or a migrated one ("--"). */
    val minutes: Int?,
    /** The twins, in spot order. */
    val stickers: List<BookSticker>,
    val stamps: List<BookStamp>,
    /** Every item was checked when it was filed (a complete trip, §7.8). */
    val perfect: Boolean,
    /** The printed receipt (filed as saved), or null. */
    val receipt: Receipt?,
    /** The celebration's bonus sticker. */
    val bonus: BookSticker? = null,
    /** Egg 8: the basket the nap cat slept in, shown on this book's page. */
    val napAisleId: String? = null,
)

@Serializable
data class BookSticker(val slot: Int, val name: String, val icon: IconRef, val rarity: Rarity, val at: Long)

/** One stamp circle: colour + pattern + number + name (§4.13). Names are AisleRotation's. */
@Serializable
data class BookStamp(
    /** Walking position; 0 = Everything else. */
    val position: Int,
    val aisleName: String,
    val family: String,
    val pattern: String,
    val pictogram: String,
    val done: Boolean,
)

/**
 * CONTRACT (mirrors [StateStore]): [album] emits the current album (loading it on first use), then every
 * committed update. [update] is serialized. Writes are COALESCED (one write per 500 ms burst, §7.2), so an
 * update is in memory (and emitted) before it is on disk; [flush] makes it durable. Filing (New trip, deleting a
 * list, the automatic new book) must be durable before state.json changes: use [file], which flushes. A write
 * lost to process death inside the coalesce window is redone on start by [AlbumReconciler].
 */
interface AlbumStore {
    val album: Flow<Album>

    suspend fun current(): Album

    suspend fun update(transform: (Album) -> Album): Album

    /** Writes any pending update now. Throws IOException when that fails (it stays pending). */
    suspend fun flush() {}

    /**
     * Every customIconId the album references (filed books' stickers and bonus, and "c:" collected keys).
     * The bespoke-icon sweep and the redraw path keep these files (§7.2; consumer: WP1's BespokeIconCache).
     */
    suspend fun referencedCustomIconIds(): Set<String>
}

/** Files [book] (idempotent by id, [Album.file]) and makes it durable before returning (§7.2 write order). */
suspend fun AlbumStore.file(book: TripBook): Album = update { it.file(book) }.also { flush() }

/** Custom icon ids [album] references; shared by every implementation. */
fun Album.customIconIds(): Set<String> = buildSet {
    collected.keys.forEach { key -> if (key.startsWith(CUSTOM_KEY_PREFIX)) add(key.removePrefix(CUSTOM_KEY_PREFIX)) }
    books.forEach { book ->
        book.stickers.forEach { sticker -> sticker.icon.customIconId?.let(::add) }
        book.bonus?.icon?.customIconId?.let(::add)
    }
}

/** Album collected-key prefix for live-drawn stickers: "c:<customIconId>". */
const val CUSTOM_KEY_PREFIX: String = IconCatalog.COLLECTED_CUSTOM_PREFIX

/**
 * Files [book] (§7.8, AlbumStoreTest.fileTwiceIsNoOp): prepends it (newest first, capped at [Album.MAX_BOOKS]);
 * a book with at least one sticker counts as a finished trip; the perfect streak adds one for a complete
 * ([TripBook.perfect]) book and resets for a finished-but-incomplete one. The trip's [Album.tally] entries go.
 * Idempotent by id: filing the same book again returns this; a different snapshot under an id already filed (a
 * New trip retried after a crash between album.json and state.json) replaces it in place without counting again.
 */
fun Album.file(book: TripBook): Album {
    val prefix = book.id + TALLY_SEPARATOR
    val tally = if (tally.any { it.startsWith(prefix) }) tally.filterNotTo(LinkedHashSet()) { it.startsWith(prefix) } else tally
    val existing = books.indexOfFirst { it.id == book.id }
    if (existing >= 0) {
        if (books[existing] == book && tally === this.tally) return this
        return copy(books = books.toMutableList().also { it[existing] = book }, tally = tally)
    }
    val finished = book.stickers.isNotEmpty()
    return copy(
        books = (listOf(book) + books).take(Album.MAX_BOOKS),
        finishedTrips = finishedTrips + if (finished) 1 else 0,
        perfectStreak = when {
            !finished -> perfectStreak
            book.perfect -> perfectStreak + 1
            else -> 0
        },
        tally = tally,
    )
}

/**
 * Takes [book] back out (Undo of the automatic new book, §0.4 Q9): removes it, and restores the counters
 * (finished trips, perfect streak) and the trip's tally entries from [before], the album as it was just before
 * [file]. Collected stickers, sheets and everything else stay as they are now. Not filed -> this.
 */
fun Album.unfile(book: TripBook, before: Album): Album {
    if (books.none { it.id == book.id }) return this
    val prefix = book.id + TALLY_SEPARATOR
    return copy(
        books = books.filterNot { it.id == book.id },
        finishedTrips = before.finishedTrips,
        perfectStreak = before.perfectStreak,
        tally = tally + before.tally.filter { it.startsWith(prefix) },
    )
}

/**
 * Collects [item], checked on trip [tripId], if its sticker is real (IconCatalog.collectKey: an EXACT catalog key
 * or a live-drawn sticker; §7.2 "collected = really bought"). Idempotent per (trip, item, sticker): un-checking
 * and re-checking on the same trip, or a reconcile, never counts twice; a redraw (a new custom id) is collected
 * again. A first collection records firstAt = the check-off; shiny and jackpot stickers make the pocket holo.
 * Once collected, always collected. Not collectible, or already counted -> this.
 */
fun Album.collect(item: GroceryItem, tripId: String, catalog: IconCatalog, now: Long): Album {
    if (!item.checked) return this
    val key = catalog.collectKey(item) ?: return this
    val token = tripId + TALLY_SEPARATOR + item.id + TALLY_SEPARATOR + key
    if (token in tally) return this
    val holo = item.rarity != Rarity.NONE
    val entry = collected[key]?.let { it.copy(count = it.count + 1, holo = it.holo || holo) }
        ?: Collected(name = item.name, firstAt = item.checkedAt ?: now, count = 1, holo = holo)
    return copy(collected = collected + (key to entry), tally = tally + token)
}

/** The celebration's bonus (§11.2): the sticker [key] becomes holo in the album (collected if it was not). */
fun Album.addHolo(key: String, name: String, now: Long): Album {
    val current = collected[key]
    if (current?.holo == true) return this
    val entry = current?.copy(holo = true) ?: Collected(name = name, firstAt = now, count = 1, holo = true)
    return copy(collected = collected + (key to entry))
}

/** The celebration's bonus when every trip sticker is already holo: the next picnic-sheet sticker (§11.2). */
fun Album.addPicnic(key: String): Album = if (key in picnic) this else copy(picnic = picnic + key)

/** The album showed these pockets' magic appear (R5): they do not play it again. */
fun Album.markSeen(keys: Collection<String>): Album {
    var changed = false
    val updated = collected.mapValues { (key, entry) ->
        if (key in keys && !entry.seen) {
            changed = true
            entry.copy(seen = true)
        } else {
            entry
        }
    }
    return if (changed) copy(collected = updated) else this
}

/** Unlocks an album sheet ("sakura", "thanksgiving-2026", ...). */
fun Album.unlockSheet(sheet: String): Album = if (sheet in sheets) this else copy(sheets = sheets + sheet)

private const val TALLY_SEPARATOR: Char = '|'

/** Non-persistent album for tests, previews and the Surprise lab sandbox. */
class InMemoryAlbumStore(initial: Album = Album()) : AlbumStore {
    private val flow = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val album: Flow<Album> = flow.asStateFlow()

    override suspend fun current(): Album = flow.value

    override suspend fun update(transform: (Album) -> Album): Album = mutex.withLock {
        val next = transform(flow.value)
        if (next !== flow.value) flow.value = next
        next
    }

    override suspend fun referencedCustomIconIds(): Set<String> = flow.value.customIconIds()
}

/**
 * album.json on disk (Android: File(context.filesDir, "album.json")), on the generic [JsonFileStore] (atomic
 * write, migrations, corrupt-file recovery; ARCHITECTURE §6.1, §6.2). Updates apply in memory at once and are
 * written COALESCED: the first update of a burst schedules one write [coalesceMillis] later on [scope], which
 * writes the latest album; [flush] writes now. A failed background write stays pending until the next update or
 * [flush] (which throws).
 */
class JsonFileAlbumStore(
    private val file: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coalesceMillis: Long = COALESCE_MILLIS,
) : AlbumStore {
    private val disk = JsonFileStore(
        file = file,
        serializer = Album.serializer(),
        version = ALBUM_SCHEMA_VERSION,
        migrate = { raw, _ -> raw },
        repair = { it.copy(version = ALBUM_SCHEMA_VERSION) },
        initial = { Album() },
    )
    private val mutex = Mutex()
    private val writeMutex = Mutex()
    private val loaded = MutableStateFlow<Album?>(null)

    /** The album last written (or read); a write is due while [loaded] is not this instance. */
    private var written: Album? = null
    private var pending: Job? = null

    override val album: Flow<Album> = flow {
        if (loaded.value == null) current()
        emitAll(loaded.filterNotNull())
    }

    override suspend fun current(): Album = loaded.value ?: mutex.withLock { loadLocked() }

    override suspend fun update(transform: (Album) -> Album): Album = mutex.withLock {
        val current = loadLocked()
        val next = transform(current)
        if (next === current) return@withLock current
        loaded.value = next
        if (pending?.isActive != true) {
            pending = scope.launch {
                delay(coalesceMillis)
                mutex.withLock { pending = null }
                try {
                    writeLatest()
                } catch (e: IOException) {
                    // Stays pending: the next update schedules another write, and flush() reports it.
                }
            }
        }
        next
    }

    override suspend fun flush() {
        current()
        writeLatest()
    }

    override suspend fun referencedCustomIconIds(): Set<String> = current().customIconIds()

    private suspend fun writeLatest() = writeMutex.withLock {
        val latest = loaded.value ?: return@withLock
        if (latest === written) return@withLock
        withContext(ioDispatcher) { disk.write(latest) }
        written = latest
    }

    private suspend fun loadLocked(): Album {
        loaded.value?.let { return it }
        val album = withContext(ioDispatcher) { disk.read() }
        written = album
        loaded.value = album
        return album
    }

    companion object {
        /** One album write per burst (§7.2). */
        const val COALESCE_MILLIS: Long = 500
    }
}

/**
 * Start-up (and every-state) repair of the album (§7.2, AlbumStoreTest): collects every checked item of every
 * open trip that is not filed yet (so a collect lost inside the coalesce window, or a sticker that resolved
 * after its check-off, is counted now; idempotent through [Album.tally]), skips trip ids already filed (a New trip
 * interrupted between album.json and state.json), and drops tally entries of trips that are no longer open.
 * Run it on start and after every committed state (it returns the same album when there is nothing to do), so a
 * checked PENDING or APPROXIMATE item is collected the moment it resolves. Pure.
 */
object AlbumReconciler {
    fun reconcile(state: AppState, album: Album, catalog: IconCatalog, now: Long): Album {
        val filed = album.books.mapTo(HashSet()) { it.id }
        var result = album
        val open = HashSet<String>()
        for (list in state.lists) {
            val trip = list.trip ?: continue
            open += trip.id
            if (trip.id in filed) continue
            for (item in list.items) if (item.checked) result = result.collect(item, trip.id, catalog, now)
        }
        val stale = result.tally.filterTo(HashSet()) { it.substringBefore(TALLY_SEPARATOR) !in open }
        return if (stale.isEmpty()) result else result.copy(tally = result.tally - stale)
    }
}

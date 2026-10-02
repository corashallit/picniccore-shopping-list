package earth.cora.picnic.core.store

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.Receipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File

/*
 * The sticker album (filesDir/album.json, docs/DESIGN_STICKER.md §7.2): what was really bought, the filed
 * trip books, unlocked sheets and the perfect-week streak. A second file next to state.json, on the backup
 * allow-list. Owner: WP3 (AlbumStoreTest). PHASE-0 STUB: the types and [AlbumStore] are the contract;
 * [JsonFileAlbumStore] keeps the album in memory until WP3 persists it on the generic JsonFileStore<T>.
 * Persisted names (every field below) need a migration to change, like state.json (ARCHITECTURE §6.4).
 */

@Serializable
data class Album(
    val version: Int = 1,
    /** Key: a catalog key, or "c:<customIconId>" for a live-drawn sticker. Collected = checked with a real sticker (§7.2). */
    val collected: Map<String, Collected> = emptyMap(),
    /** Filed trip books, newest first (cap 200; the oldest drop off). */
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
)

@Serializable
data class Collected(
    val name: String,
    val firstAt: Long,
    val count: Int,
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
    val total: Int,
    val minutes: Int?,
    val stickers: List<BookSticker>,
    val stamps: List<BookStamp>,
    val perfect: Boolean,
    val receipt: Receipt?,
    /** The celebration's bonus sticker. */
    val bonus: BookSticker? = null,
    /** Egg 8: the basket the nap cat slept in, shown on this book's page. */
    val napAisleId: String? = null,
)

@Serializable
data class BookSticker(val slot: Int, val name: String, val icon: IconRef, val rarity: Rarity, val at: Long)

/** One stamp circle: colour + pattern + number + name (§4.13). */
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
 * committed update. [update] is serialized and persists before it emits (writes are coalesced, one per
 * 500 ms burst, §7.2). New trip writes the album FIRST, then state.json.
 */
interface AlbumStore {
    val album: Flow<Album>

    suspend fun current(): Album

    suspend fun update(transform: (Album) -> Album): Album

    /**
     * Every customIconId the album references (filed books' stickers and bonus, and "c:" collected keys).
     * The bespoke-icon sweep and the redraw path keep these files (§7.2; consumer: WP1's BespokeIconCache).
     */
    suspend fun referencedCustomIconIds(): Set<String>
}

/** Custom icon ids [album] references; shared by every implementation. */
fun Album.customIconIds(): Set<String> = buildSet {
    collected.keys.forEach { key -> if (key.startsWith(CUSTOM_KEY_PREFIX)) add(key.removePrefix(CUSTOM_KEY_PREFIX)) }
    books.forEach { book ->
        book.stickers.forEach { sticker -> sticker.icon.customIconId?.let(::add) }
        book.bonus?.icon?.customIconId?.let(::add)
    }
}

/** Album collected-key prefix for live-drawn stickers: "c:<customIconId>". */
const val CUSTOM_KEY_PREFIX: String = "c:"

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
 * album.json on disk (Android: File(context.filesDir, "album.json")), on the generic JsonFileStore<T> that
 * JsonFileStateStore is refactored onto (atomic write, migrations, repair; ARCHITECTURE §6.1, §6.2).
 * PHASE-0 STUB: holds the album in memory only (nothing files a book before WP3's trips exist).
 * TODO(WP3): persist [file] with the coalesced atomic write (AlbumStoreTest).
 */
class JsonFileAlbumStore(
    @Suppress("unused") private val file: File,
) : AlbumStore by InMemoryAlbumStore()

/**
 * Start-up repair (§7.2): collects every checked item of every open trip (a coalesced album write lost to
 * process death is redone) and skips trip ids already filed, so no crash ordering loses a book or files it
 * twice. Pure. Owner: WP3. PHASE-0 STUB; signature provisional (its only consumer is AppContainer).
 */
object AlbumReconciler {
    fun reconcile(state: AppState, album: Album, catalog: IconCatalog, now: Long): Album =
        TODO("WP3: AlbumReconciler.reconcile (§7.2, AlbumStoreTest)")
}

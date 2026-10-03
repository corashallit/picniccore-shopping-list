package earth.cora.picnic.core.store

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AutoNewBook
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.core.model.deleteList
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.newTrip
import earth.cora.picnic.core.model.toggleCheckedStartingNewBook
import earth.cora.picnic.core.model.undoNewBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The trip operations that write BOTH files, in the §7.2 order (AlbumStoreTest.crashBetweenAlbumAndState):
 * a book is filed in album.json and flushed BEFORE state.json loses the trip, so no crash ordering loses a book
 * (and filing is idempotent by trip id, so none is filed twice). ViewModels call these instead of composing
 * StateOps and album writes themselves. Every function throws IOException when a write fails; the state is then
 * unchanged when the album write failed. Owner: WP3.
 */
object TripFiling {
    /**
     * New trip (§6.5): files the list's book, then resets the list ([newTrip]). Returns the filed book, or null
     * when the list has no trip (nothing changes then).
     */
    suspend fun newTrip(store: StateStore, album: AlbumStore, listId: String, mode: NewTripMode, now: Long): TripBook? {
        val (_, book) = store.current().newTrip(listId, mode, now)
        book?.let { album.file(it) }
        var filed: TripBook? = null
        store.update { state ->
            val (next, b) = state.newTrip(listId, mode, now)
            filed = b
            next
        }
        // A trip that changed between the snapshot and the update (another check-off) is filed again, replacing
        // the earlier snapshot under the same id.
        filed?.let { if (it != book) album.file(it) }
        return filed
    }

    /** Deletes a list (§6.4): a non-empty book is filed first (a printed receipt as saved). Returns it, or null. */
    suspend fun deleteList(store: StateStore, album: AlbumStore, listId: String, now: Long): TripBook? {
        val (_, book) = store.current().deleteList(listId, now)
        book?.let { album.file(it) }
        var filed: TripBook? = null
        store.update { state ->
            val (next, b) = state.deleteList(listId, now)
            filed = b
            next
        }
        filed?.let { if (it != book) album.file(it) }
        return filed
    }

    /** What a check-off tap did: [autoNewBook] is set when it started a new book on its own (§0.4 Q9). */
    data class Toggle(val autoNewBook: AutoNewBook?, val albumBefore: Album?)

    /**
     * A check-off tap (or its TalkBack, keyboard or swipe equivalent): the idle rule (§0.4 Q9) files the old book
     * first when it applies, then the toggle is saved, then the sticker is collected (coalesced; [AlbumReconciler]
     * redoes it after a crash). Keep the result for the toast's Undo ([undoNewBook]).
     */
    suspend fun toggle(
        store: StateStore,
        album: AlbumStore,
        catalog: IconCatalog,
        listId: String,
        itemId: String,
        now: Long,
    ): Toggle {
        val (_, preview) = store.current().toggleCheckedStartingNewBook(listId, itemId, now)
        var before: Album? = null
        if (preview != null) {
            album.update { a ->
                before = a
                a.file(preview.book)
            }
            album.flush()
        }
        var auto: AutoNewBook? = null
        val state = store.update { s ->
            val (next, a) = s.toggleCheckedStartingNewBook(listId, itemId, now)
            auto = a
            next
        }
        val result = auto
        if (result != null && result.book != preview?.book) {
            album.update { a ->
                if (before == null) before = a
                a.file(result.book)
            }
            album.flush()
        }
        val list = state.list(listId)
        val trip = list?.trip
        val item = list?.items?.firstOrNull { it.id == itemId }
        if (trip != null && item != null && item.checked) album.update { it.collect(item, trip.id, catalog, now) }
        return Toggle(result, if (result != null) before else null)
    }

    /**
     * Undo of an automatic new book (§0.4 Q9): the state first ([undoNewBook]: the old trip and its stickers come
     * back, the tapped item goes into the old book), then the book leaves the album with the counters as they were
     * ([Album.unfile]). A crash between the two leaves the book filed under the old trip's id, and the next New trip
     * replaces it, so nothing is lost.
     */
    suspend fun undoNewBook(store: StateStore, album: AlbumStore, toggle: Toggle, now: Long) {
        val auto = toggle.autoNewBook ?: return
        store.update { it.undoNewBook(auto.undo, now) }
        val before = toggle.albumBefore ?: return
        album.update { it.unfile(auto.book, before) }
        album.flush()
    }

    /**
     * Keeps the album in step with the state (§7.2): reconciles once now and after every committed state
     * ([AlbumReconciler]), so check-offs are collected, stickers that resolve later are collected then, and a
     * collect lost to process death is redone on start. Launch it once in the app scope.
     */
    fun launchAlbumSync(scope: CoroutineScope, store: StateStore, album: AlbumStore, catalog: IconCatalog, clock: () -> Long): Job =
        scope.launch {
            store.states.collect { state -> album.update { AlbumReconciler.reconcile(state, it, catalog, clock()) } }
        }
}

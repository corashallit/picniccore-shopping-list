package earth.cora.picnic.ui.list.motion

import earth.cora.picnic.core.store.AlbumStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.core.store.addHolo
import earth.cora.picnic.core.store.addPicnic
import earth.cora.picnic.core.model.markFinaleShown
import earth.cora.picnic.core.model.setTripBonus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * The finale's state (docs/DESIGN_STICKER.md §7.1, §11.1, §11.2): when the scene STARTS, the trip's finaleShownAt is set
 * (so it never replays) and the bonus sticker is saved to the trip (Trip.bonus, filed as TripBook.bonus) and to the album
 * (holo, or the next picnic-sheet sticker), so skipping or closing never loses it. With the celebration switched off,
 * or an owed finale too old to play, only finaleShownAt is set. The engine is presentation only; this keeper is the one
 * place it touches the stores. Part 3 wires it (AppContainer's scope). Owner: WP6.
 */
class FinaleKeeper(
    private val state: StateStore,
    private val album: AlbumStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Persists [engine]'s finale events until [scope] ends. */
    fun attach(engine: MotionChoreographer, scope: CoroutineScope): Job = scope.launch {
        engine.events.collect { handle(it) }
    }

    suspend fun handle(event: MotionEvent) {
        val now = clock()
        when (event) {
            is MotionEvent.CelebrationStarted -> {
                val bonus = event.bonus
                state.update { s ->
                    val shown = s.markFinaleShown(event.listId, now)
                    if (bonus != null) shown.setTripBonus(event.listId, bonus.bookSticker(now)) else shown
                }
                if (bonus != null) {
                    album.update { a -> if (bonus.picnic) a.addPicnic(bonus.albumKey) else a.addHolo(bonus.albumKey, bonus.name, now) }
                }
            }
            is MotionEvent.FinaleMarked -> {
                val id = event.listId ?: return
                state.update { it.markFinaleShown(id, now) }
            }
            else -> Unit
        }
    }
}

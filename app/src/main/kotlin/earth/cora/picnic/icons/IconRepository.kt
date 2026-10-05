package earth.cora.picnic.icons

import earth.cora.picnic.core.model.GroceryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device side of the icon cascade (docs/ARCHITECTURE.md §8): library stickers, bespoke (live-drawn)
 * icons, the IconDecider batcher (step 2) and the drawing queue (step 3). CROSS-MODULE CONTRACT
 * between app-shell (implements: AppIconRepository) and app-screens (calls it from ViewModels).
 *
 * Storage: library stickers = res/drawable/sk_i_<key>.xml (StickerManifest); bespoke (live stickers v4,
 * docs/DESIGN_STICKER.md §18.3) = filesDir/icons/custom/<customIconId>.webp + its die-cut path <customIconId>.cut
 * (no .cut = a legacy icon drawn before v4) + filesDir/icons/index.json (normalizedName -> customIconId, blocked
 * names, daily counter). ItemIcon draws both through [StickerArt] (LocalStickerArt), not through this interface.
 * Every method returns immediately, never throws and never blocks adding items; the work runs in
 * the app scope.
 */
interface IconRepository {
    /**
     * normalizeName(item name) values whose icon is being decided (step 2) or drawn (step 3).
     * Rows pass `normalizeName(item.name) in busy` to IconCatalog.displayRef and show the sparkle.
     */
    val busy: StateFlow<Set<String>>

    /** True if [key] has library art (StickerManifest; §19.3). */
    fun isBundled(key: String): Boolean

    /**
     * Run the cascade for items just committed (any source) or renamed in the edit sheet. Per item
     * without customIconId:
     * - a cached bespoke icon for its normalized name -> attach it (whatever the fit; [forgetBespoke]
     *   removes the cache entry when the user picks a catalog icon instead);
     * - iconFit PENDING -> batch to the IconDecider (debounced ~300 ms, <= 40 names per call)
     *   -> store.update { applyIconDecisions(...) } -> "none" results continue as APPROXIMATE;
     * - iconFit APPROXIMATE, "Draw new icons with AI" on, name not blocked, under the daily cap
     *   -> enqueue a drawing (max 2 concurrent, retried once) -> attachCustomIcon.
     */
    fun submit(items: List<GroceryItem>)

    /**
     * App start (MainActivity.onCreate / first list load), a refresh, and the catch-up when the connection comes back:
     * submit every PENDING item left from an offline session and every name waiting to be drawn (a drawing that was
     * offline or failed on the way, under the daily cap); once per process, sweep orphaned icon files (keeping everything a
     * list, the pantry or the album shows) and queue v4 redraws of legacy icons (oldest first, under the daily cap, only
     * with a token and drawing on). Offline it asks nothing: the items stay queued.
     */
    fun resumePending()

    /** Edit sheet "Draw a new icon": always enqueues (ignores the setting, cache and cap); the result overwrites. */
    fun redraw(itemName: String)

    /**
     * A drawing she asked for ([redraw]) that did not work: the Edit sheet says so instead of quietly going back to the old
     * sticker. Hot: only failures after collection starts.
     */
    val drawFailures: Flow<DrawFailure> get() = emptyFlow()

    /** The user picked a catalog icon for this name: forget its bespoke icon so it isn't re-attached later. */
    fun forgetBespoke(itemName: String)
}

/**
 * A drawing she asked for that did not work, for normalizeName(name) [key]: [offline] when it never reached the drawing
 * service (no network, or no connection), [refused] when image moderation declined the name; otherwise something else
 * went wrong (a timeout, a busy service, an unusable answer).
 */
data class DrawFailure(val key: String, val offline: Boolean = false, val refused: Boolean = false)

/** Does nothing; for previews and tests. [busyNames] lets screenshot tests show the sparkle. */
class NoOpIconRepository(busyNames: Set<String> = emptySet()) : IconRepository {
    override val busy: StateFlow<Set<String>> = MutableStateFlow(busyNames).asStateFlow()
    override fun isBundled(key: String): Boolean = false
    override fun submit(items: List<GroceryItem>) = Unit
    override fun resumePending() = Unit
    override fun redraw(itemName: String) = Unit
    override fun forgetBespoke(itemName: String) = Unit
}

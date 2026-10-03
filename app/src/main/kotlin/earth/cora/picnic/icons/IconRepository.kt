package earth.cora.picnic.icons

import androidx.compose.ui.graphics.ImageBitmap
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device side of the icon cascade (docs/ARCHITECTURE.md §8): bundled icons, bespoke (live-drawn)
 * icons, the IconDecider batcher (step 2) and the drawing queue (step 3). CROSS-MODULE CONTRACT
 * between app-shell (implements: AppIconRepository) and app-screens (calls it from ViewModels).
 *
 * Storage: library stickers = res/drawable/sk_i_<key>.xml (StickerManifest); bespoke (live stickers v4,
 * docs/DESIGN_STICKER.md §18.3) = filesDir/icons/custom/<customIconId>.webp + its die-cut path <customIconId>.cut
 * (no .cut = a legacy icon drawn before v4) + filesDir/icons/index.json (normalizedName -> customIconId, blocked
 * names, daily counter).
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
     * App start (MainActivity.onCreate / first list load): submit every PENDING item left from an offline session;
     * once per process, sweep orphaned icon files (keeping everything a list, the pantry or the album shows) and
     * queue v4 redraws of legacy icons (oldest first, under the daily cap, only with a token and drawing on).
     */
    fun resumePending()

    /** Edit sheet "Draw a new icon": always enqueues (ignores the setting, cache and cap); the result overwrites. */
    fun redraw(itemName: String)

    /** The user picked a catalog icon for this name: forget its bespoke icon so it isn't re-attached later. */
    fun forgetBespoke(itemName: String)
}

/** Decoded bitmaps for ItemIcon / AisleBasket (provided through LocalIconImages). */
interface IconImages {
    /** Memory cache only; must never touch disk (called during composition). */
    fun cached(ref: IconRef): ImageBitmap?

    /**
     * Best available image following the rendering order: custom file -> bundled key ->
     * bundled category fallback. Null means "draw the placeholder art". Main-safe.
     */
    suspend fun load(ref: IconRef): ImageBitmap?

    /** Nothing bundled: every icon renders as placeholder art (previews, early skeleton). */
    object None : IconImages {
        override fun cached(ref: IconRef): ImageBitmap? = null
        override suspend fun load(ref: IconRef): ImageBitmap? = null
    }
}

/** Does nothing; for previews and tests. [busyNames] lets screenshot tests show the sparkle. */
class NoOpIconRepository(busyNames: Set<String> = emptySet()) : IconRepository {
    override val busy: StateFlow<Set<String>> = MutableStateFlow(busyNames).asStateFlow()
    override fun isBundled(key: String): Boolean = false
    override fun submit(items: List<GroceryItem>) = Unit
    override fun resumePending() = Unit
    override fun redraw(itemName: String) = Unit
    override fun forgetBespoke(itemName: String) = Unit
}

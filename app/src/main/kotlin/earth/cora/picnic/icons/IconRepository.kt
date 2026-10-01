package earth.cora.picnic.icons

import androidx.compose.ui.graphics.ImageBitmap
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bundled + bespoke item icons and the live drawing queue. CONTRACT (docs/ARCHITECTURE.md §8).
 * Production implementation (app-shell): bundled = assets/icons/<key>.webp; bespoke =
 * filesDir/icons/custom/<customIconId>.webp + filesDir/icons/index.json (normalizedName -> id, blocked names).
 */
interface IconRepository {
    /** normalizeName(item name) values whose bespoke icon is queued or being drawn (rows show a sparkle). */
    val drawing: StateFlow<Set<String>>

    /** True if assets/icons/<key>.webp is bundled. */
    fun isBundled(key: String): Boolean

    /**
     * Call after committing new items. For each item without customIconId: reuse a cached bespoke icon
     * for its normalized name (attached via StateStore.update { attachCustomIcon(...) }); otherwise, if
     * iconFit == APPROXIMATE and Settings "Draw new icons" is on and the name isn't blocked, enqueue it.
     * Returns immediately; never throws.
     */
    fun onItemsAdded(items: List<GroceryItem>)

    /** Edit sheet "Draw a new icon": always enqueues (even if cached or setting off); result overwrites. */
    fun redraw(itemName: String)
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

/** Does nothing; for previews and tests. */
class NoOpIconRepository : IconRepository {
    private val empty = MutableStateFlow<Set<String>>(emptySet())
    override val drawing: StateFlow<Set<String>> = empty.asStateFlow()
    override fun isBundled(key: String): Boolean = false
    override fun onItemsAdded(items: List<GroceryItem>) = Unit
    override fun redraw(itemName: String) = Unit
}

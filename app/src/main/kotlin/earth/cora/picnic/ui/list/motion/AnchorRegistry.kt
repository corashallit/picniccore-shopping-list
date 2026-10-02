package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Window bounds of the things chains fly between (docs/DESIGN_STICKER.md §8.7). Keys: `row:<itemId>` (a row's
 * sticker), `basket:<sectionId>` (the open header's basket wins over a folded strip's), `spot:<slot>`,
 * `stamp:<sectionId>`, `lcd`, `sun`, `addbar`. Owner: WP6. PHASE-0 CONTRACT (the body records bounds on
 * every placement; WP6 may refine it, e.g. forgetting keys that leave composition).
 */
@Stable
class AnchorRegistry {
    private val rects = mutableStateMapOf<String, Rect>()

    /** Records this element's window bounds under [key] whenever it is placed. */
    fun Modifier.anchor(key: String): Modifier = onGloballyPositioned { rects[key] = it.boundsInWindow() }

    /** The last recorded window bounds for [key], or null if it was never laid out. */
    fun bounds(key: String): Rect? = rects[key]
}

package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.ui.theme.gen.BasketManifest

/**
 * Window bounds of the things chains fly between (docs/DESIGN_STICKER.md §8.7). Keys: `row:<itemId>` (a row's
 * sticker), `check:<itemId>` (its checkbox, where the hanko's burst starts), `basket:<sectionId>` (the open header's
 * basket wins over a folded strip's: register it with a higher priority), `spot:<slot>`, `stamp:<sectionId>`, `lcd`,
 * `sun`, `addbar`. Owner: WP6.
 *
 * An anchor is recorded on every placement and forgotten when its element leaves composition (a row scrolled out of
 * a lazy list has no anchor, so its chain lands in place, §8.3). Read on the main thread at event and frame time;
 * not snapshot state (nothing recomposes when an anchor moves). Put an anchor BEFORE any motion graphicsLayer in a
 * modifier chain, so it records where the element sits, not where a pose has moved it.
 */
@Stable
class AnchorRegistry {
    private val entries = HashMap<String, MutableList<Entry>>()

    /** One registered element: its window bounds, its priority and, for a basket, its mouth. */
    internal class Entry(val key: String, var priority: Int) {
        var bounds: Rect? = null
        var mouth: Offset? = null
        var mouthFraction: Float? = null
        var mouthLiftPx: Float = 0f
    }

    /** Records this element's window bounds under [key] whenever it is placed; [priority] breaks ties between two. */
    fun Modifier.anchor(key: String, priority: Int = 0): Modifier = this then AnchorElement(this@AnchorRegistry, key, priority, null)

    /**
     * Records a basket under `basket:<sectionId>` with its mouth: x = the basket's centre, y = its top +
     * rimY / 64 × its height − 6 dp (§8.3), from [kind]'s manifest rim. The open tray header's basket should pass a
     * higher [priority] than the folded strip's.
     */
    fun Modifier.basketAnchor(sectionId: String, kind: BasketKind, priority: Int = 0): Modifier {
        val rim = BasketManifest.art(kind.key)?.rimY ?: DEFAULT_RIM
        return this then AnchorElement(this@AnchorRegistry, basketKey(sectionId), priority, rim / BasketManifest.VIEWPORT_HEIGHT)
    }

    /** The last recorded window bounds for [key] (the highest priority element), or null if none is laid out. */
    fun bounds(key: String): Rect? = best(key)?.bounds

    /** A basket's mouth in window px (§8.3), or the centre of [key]'s bounds for anything else; null if not laid out. */
    fun mouth(key: String): Offset? {
        val e = best(key) ?: return null
        return e.mouth ?: e.bounds?.center
    }

    /** True when [key] is laid out now. */
    fun isLaidOut(key: String): Boolean = bounds(key) != null

    /** Every key laid out now (tests). */
    fun keys(): Set<String> = entries.filterValues { list -> list.any { it.bounds != null } }.keys

    /** Records [bounds] for [key] by hand (tests and non-Compose hosts). */
    fun put(key: String, bounds: Rect, mouth: Offset? = null) {
        val list = entries.getOrPut(key) { mutableListOf() }
        val e = list.firstOrNull { it.priority == MANUAL_PRIORITY } ?: Entry(key, MANUAL_PRIORITY).also { list += it }
        e.bounds = bounds
        e.mouth = mouth
    }

    /** Forgets a hand-recorded key. */
    fun remove(key: String) {
        entries[key]?.removeAll { it.priority == MANUAL_PRIORITY }
    }

    private fun best(key: String): Entry? = entries[key]?.filter { it.bounds != null }?.maxByOrNull { it.priority }

    internal fun attach(entry: Entry) {
        entries.getOrPut(entry.key) { mutableListOf() }.add(entry)
    }

    internal fun detach(entry: Entry) {
        val list = entries[entry.key] ?: return
        list.remove(entry)
        if (list.isEmpty()) entries.remove(entry.key)
    }

    companion object {
        fun rowKey(itemId: String): String = "row:$itemId"
        fun checkKey(itemId: String): String = "check:$itemId"
        fun basketKey(sectionId: String): String = "basket:$sectionId"
        fun spotKey(slot: Int): String = "spot:$slot"
        fun stampKey(sectionId: String): String = "stamp:$sectionId"
        const val LCD: String = "lcd"
        const val SUN: String = "sun"
        const val ADD_BAR: String = "addbar"

        /** The keepsakes nothing ever flies to (R5): the book, its stamps, the LCD, the album and the receipt. */
        val KEEPSAKE_PREFIXES: List<String> = listOf("spot:", "stamp:", "lcd", "album", "receipt", "book")

        private const val MANUAL_PRIORITY = Int.MAX_VALUE
        private const val DEFAULT_RIM = 30f
        internal const val MOUTH_LIFT_DP = 6f
    }
}

private class AnchorElement(
    private val registry: AnchorRegistry,
    private val key: String,
    private val priority: Int,
    private val mouthFraction: Float?,
) : ModifierNodeElement<AnchorNode>() {
    override fun create(): AnchorNode = AnchorNode(registry, key, priority, mouthFraction)

    override fun update(node: AnchorNode) {
        node.update(registry, key, priority, mouthFraction)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "anchor"
        properties["key"] = key
        properties["priority"] = priority
    }

    override fun equals(other: Any?): Boolean =
        other is AnchorElement && other.registry === registry && other.key == key && other.priority == priority && other.mouthFraction == mouthFraction

    override fun hashCode(): Int = ((System.identityHashCode(registry) * 31 + key.hashCode()) * 31 + priority) * 31 + mouthFraction.hashCode()
}

private class AnchorNode(
    private var registry: AnchorRegistry,
    key: String,
    priority: Int,
    private var mouthFraction: Float?,
) : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var entry = AnchorRegistry.Entry(key, priority)

    override fun onAttach() {
        registry.attach(entry)
    }

    override fun onDetach() {
        registry.detach(entry)
    }

    fun update(registry: AnchorRegistry, key: String, priority: Int, mouthFraction: Float?) {
        if (registry !== this.registry || key != entry.key) {
            if (isAttached) this.registry.detach(entry)
            this.registry = registry
            entry = AnchorRegistry.Entry(key, priority)
            if (isAttached) registry.attach(entry)
        }
        entry.priority = priority
        this.mouthFraction = mouthFraction
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        // Unclipped: a row half under the pinned plate still hops from where its sticker really is.
        val b = Rect(coordinates.positionInWindow(), coordinates.size.toSize())
        entry.bounds = b
        val f = mouthFraction
        entry.mouth = if (f != null) {
            val lift = AnchorRegistry.MOUTH_LIFT_DP * currentValueOf(LocalDensity).density
            Offset(b.center.x, b.top + f * b.height - lift)
        } else {
            null
        }
    }
}

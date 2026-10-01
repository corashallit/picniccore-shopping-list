package earth.cora.picnic.core.catalog

import earth.cora.picnic.core.CoreJson
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconRef
import kotlinx.serialization.Serializable

/*
 * The icon + basket catalog: core/src/main/resources/icon_catalog.json (source:
 * research/icon_catalog.json, validated by research/validate_icon_catalog.py).
 * Icon keys are the enum the LLM chooses from; the bundled art lives in the app at
 * assets/icons/<key>.webp (192 px, transparent). Keys are persisted: never rename one.
 */

@Serializable
data class IconCategory(val key: String, val label: String, val fallbackIcon: String)

@Serializable
data class IconEntry(
    val key: String,
    val label: String,
    val category: String,
    val aliases: List<String>,
    /** One sentence for image generation (library sheets and live icons). */
    val drawHint: String,
)

@Serializable
data class BasketEntry(val key: String, val label: String, val drawHint: String, val keywords: List<String>)

@Serializable
data class CatalogFile(
    val version: Int,
    val categories: List<IconCategory>,
    val icons: List<IconEntry>,
    val baskets: List<BasketEntry>,
)

class IconCatalog(val file: CatalogFile) {
    val version: Int get() = file.version
    val categories: List<IconCategory> get() = file.categories
    val icons: List<IconEntry> get() = file.icons

    /** All icon keys in catalog order: the strict-schema enum. */
    val keys: List<String> = file.icons.map { it.key }

    private val byKey: Map<String, IconEntry> = file.icons.associateBy { it.key }
    private val categoryByKey: Map<String, IconCategory> = file.categories.associateBy { it.key }

    operator fun get(key: String): IconEntry? = byKey[key]

    operator fun contains(key: String): Boolean = key in byKey

    fun categoryOf(iconKey: String): IconCategory? = byKey[iconKey]?.let { categoryByKey[it.category] }

    /** The category fallback icon for [iconKey] ("lemon" -> "fruit_generic"); [DEFAULT_ICON] if unknown. */
    fun fallbackFor(iconKey: String): String = categoryOf(iconKey)?.fallbackIcon ?: DEFAULT_ICON

    /** [key] if it is a catalog key, otherwise [DEFAULT_ICON]. */
    fun sanitize(key: String?): String = if (key != null && key in byKey) key else DEFAULT_ICON

    /**
     * Local, offline icon lookup for a free-text item name (used by OfflineParser and as a hint).
     * CONTRACT: compares normalizeName(name) against normalizeName of each icon's key (underscores as
     * spaces), label and aliases; exact match first, then the longest alias contained in the name as
     * whole words. Returns null when nothing matches.
     */
    fun match(name: String): IconEntry? {
        TODO("core: IconCatalog.match")
    }

    /**
     * What a row shows for [item]. CONTRACT: bespoke icon if attached; while a bespoke icon is being
     * drawn ([drawing]), the category fallback (the row overlays a sparkle); otherwise the item's own key.
     */
    fun displayRef(item: GroceryItem, drawing: Boolean): IconRef = when {
        item.customIconId != null -> IconRef(item.icon, item.customIconId)
        drawing -> IconRef(fallbackFor(item.icon))
        else -> IconRef(item.icon)
    }

    val baskets: BasketCatalog by lazy { BasketCatalog(file.baskets) }

    companion object {
        /** Generic "some groceries" icon for unknown items. */
        const val DEFAULT_ICON: String = "paper_bag"

        const val RESOURCE: String = "/icon_catalog.json"

        fun parse(json: String): IconCatalog = IconCatalog(CoreJson.json.decodeFromString(CatalogFile.serializer(), json))

        /** Loads the bundled catalog from core's classpath resources (works on the JVM and on Android). */
        fun load(): IconCatalog {
            val stream = IconCatalog::class.java.getResourceAsStream(RESOURCE)
                ?: error("icon_catalog.json missing from core resources")
            return stream.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        }

        /** Process-wide lazily loaded catalog (immutable), for previews and places without DI. */
        val default: IconCatalog by lazy { load() }
    }
}

class BasketCatalog(val entries: List<BasketEntry>) {
    private val byKind: Map<BasketKind, BasketEntry> =
        entries.mapNotNull { e -> BasketKind.fromKey(e.key)?.let { it to e } }.toMap()

    fun entry(kind: BasketKind): BasketEntry? = byKind[kind]

    /**
     * Offline keyword heuristic (reference: research/validate_icon_catalog.py basket_for, 43/43 samples).
     * CONTRACT: fold the aisle name (lowercase, accents removed), pad with spaces; for every keyword of
     * every basket find its earliest whole-word position; the earliest position wins, ties go to the
     * longest keyword. No match -> BasketKind.DEFAULT.
     */
    fun guess(aisleName: String): BasketKind {
        TODO("core: BasketCatalog.guess")
    }

    fun guess(aisle: Aisle): BasketKind = guess(aisle.name)
}

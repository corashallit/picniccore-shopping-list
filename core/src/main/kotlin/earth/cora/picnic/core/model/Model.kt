package earth.cora.picnic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Domain model. This file is a CROSS-MODULE CONTRACT (see docs/ARCHITECTURE.md §4):
 * field names are persisted in state.json, so renaming one needs a schema migration.
 * Everything here is immutable; change state only through the pure operations in StateOps.kt.
 */

/** Current state.json schema version. Bump together with a migration in StateMigrations. */
const val STATE_SCHEMA_VERSION: Int = 1

/** Where an item came from. Persisted by name. */
@Serializable
enum class ItemSource { TYPED, AI_TEXT, PHOTO, VOICE }

/**
 * How well [GroceryItem.icon] depicts the item (the icon cascade, docs/ARCHITECTURE.md §8.2). Persisted by serial name.
 * - EXACT: the catalog icon depicts this item (local match, learned override, ingest "exact", or IconDecider match).
 * - APPROXIMATE: only a near neighbour / category icon fits (ingest "approximate", or IconDecider "none").
 *   Candidate for a live-drawn bespoke icon (step 3).
 * - PENDING: no local match yet and the IconDecider (step 2) has not answered (offline, or still running).
 *   [GroceryItem.icon] is IconCatalog.DEFAULT_ICON meanwhile. IconRepository.resumePending() retries these.
 */
@Serializable
enum class IconFit {
    @SerialName("exact") EXACT,
    @SerialName("approximate") APPROXIMATE,
    @SerialName("pending") PENDING,
}

@Serializable
data class GroceryItem(
    /** Random UUID string, unique across all lists. */
    val id: String,
    /** Display name as shown in the list ("All-purpose flour"). */
    val name: String,
    /** Free text, metric ("2", "1 kg", "4 L bag"), or null. */
    val quantity: String? = null,
    /** Short in-store hint, or null. */
    val note: String? = null,
    /** Aisle id ([Aisle.id]); null means Unsorted. An id that is not in the current aisle list also renders as Unsorted. */
    val aisleId: String? = null,
    /** Icon catalog key (always a valid key; see IconCatalog.sanitize). DEFAULT_ICON while iconFit == PENDING. */
    val icon: String,
    val iconFit: IconFit = IconFit.EXACT,
    /** Id of a bespoke icon drawn on this device (IconRepository), or null. */
    val customIconId: String? = null,
    val checked: Boolean = false,
    /** Epoch millis. */
    val addedAt: Long,
    val source: ItemSource = ItemSource.TYPED,
    /** True once the user moved the item to an aisle by hand (it then also produced a [Correction]). */
    val manuallyPlaced: Boolean = false,
)

@Serializable
data class GroceryList(
    val id: String,
    val title: String,
    val createdAt: Long,
    /** Insertion order (oldest first). Display order comes from ListGrouping, never from this list. */
    val items: List<GroceryItem> = emptyList(),
    /** Section ids ([Aisle.id], [SectionIds.UNSORTED], [SectionIds.CHECKED]) the user collapsed. */
    val collapsedAisles: Set<String> = emptySet(),
)

@Serializable
data class StoreProfile(
    val name: String = "My store",
    /** The user's editable aisle list: one aisle per line, in walking order. Parsed by [Aisles.parse]. */
    val aislesText: String = Aisles.DEFAULT_TEXT,
    /** Aisle id -> basket kind. Missing entries fall back to BasketCatalog.guess(aisle name). */
    val aisleBaskets: Map<String, BasketKind> = emptyMap(),
)

/** Result of icon resolution (local match, IconDecider, ingest): what to store in GroceryItem.icon / iconFit. */
data class ResolvedIcon(val icon: String, val fit: IconFit)

/** A learned "this item lives in that aisle" fact, recorded when the user moves an item by hand. */
@Serializable
data class Correction(
    /** normalizeName(item name). */
    val name: String,
    val aisleId: String,
    /** Epoch millis; newest wins, the list is capped (see StateOps.MAX_CORRECTIONS). */
    val at: Long,
)

/**
 * A learned "this item uses that icon" fact, recorded when the user picks a catalog icon by hand
 * (step 1 of the icon cascade: it beats the catalog match, the ingest model's choice and the IconDecider).
 */
@Serializable
data class IconOverride(
    /** normalizeName(item name). */
    val name: String,
    /** Catalog icon key. */
    val icon: String,
    /** Epoch millis; newest wins, the list is capped (see StateOps.MAX_ICON_OVERRIDES). */
    val at: Long,
)

@Serializable
data class AppState(
    val version: Int = STATE_SCHEMA_VERSION,
    val lists: List<GroceryList>,
    val currentListId: String,
    val store: StoreProfile = StoreProfile(),
    /** Learned aisle corrections, oldest first. */
    val corrections: List<Correction> = emptyList(),
    /** Learned icon choices, oldest first. */
    val iconOverrides: List<IconOverride> = emptyList(),
)

/** One parsed line of [StoreProfile.aislesText]. Derived, never persisted on its own. */
@Serializable
data class Aisle(
    /** Stable slug of the normalized name, e.g. "aisle-2-baking-spices". Never starts with '_' and is never "unsorted". */
    val id: String,
    /** The line as the user typed it (trimmed, bullet removed), e.g. "Aisle 2 - Baking & spices". */
    val name: String,
    /** 1-based walking position. Drives the medallion number and the aisle colour rotation. */
    val position: Int,
)

/** Reserved section ids. Aisle slugs can never collide with these (they never start with '_'). */
object SectionIds {
    const val UNSORTED: String = "_unsorted"
    const val CHECKED: String = "_checked"
}

/** The literal used for "no aisle fits" in LLM schemas. Mapped to aisleId = null client-side. */
const val UNSORTED_WIRE_ID: String = "unsorted"

/**
 * What to draw for an item: a catalog key plus an optional bespoke icon id. Build it with
 * IconCatalog.displayRef(item, busy). Rendering order (theme layer, ItemIcon via IconImages):
 * custom file -> bundled asset for key -> bundled asset for the key's category fallback -> placeholder art.
 */
@Serializable
data class IconRef(val key: String, val customIconId: String? = null)

fun GroceryItem.iconRef(): IconRef = IconRef(icon, customIconId)

/**
 * Basket/container drawn for a collapsed aisle. Keys match icon_catalog.json "baskets"
 * (a core unit test asserts the two sets are equal). Persisted by key.
 */
@Serializable
enum class BasketKind(val key: String) {
    @SerialName("wicker_picnic") WICKER_PICNIC("wicker_picnic"),
    @SerialName("bread_basket") BREAD_BASKET("bread_basket"),
    @SerialName("fruit_crate") FRUIT_CRATE("fruit_crate"),
    @SerialName("berry_punnet") BERRY_PUNNET("berry_punnet"),
    @SerialName("wire_basket") WIRE_BASKET("wire_basket"),
    @SerialName("egg_basket") EGG_BASKET("egg_basket"),
    @SerialName("milk_crate") MILK_CRATE("milk_crate"),
    @SerialName("cooler") COOLER("cooler"),
    @SerialName("ice_bucket") ICE_BUCKET("ice_bucket"),
    @SerialName("bento_box") BENTO_BOX("bento_box"),
    @SerialName("jar_shelf") JAR_SHELF("jar_shelf"),
    @SerialName("canvas_tote") CANVAS_TOTE("canvas_tote"),
    @SerialName("flower_bucket") FLOWER_BUCKET("flower_bucket"),
    @SerialName("bottle_carrier") BOTTLE_CARRIER("bottle_carrier"),
    @SerialName("candy_jar") CANDY_JAR("candy_jar"),
    @SerialName("vanity_case") VANITY_CASE("vanity_case"),
    ;

    companion object {
        /** Used for Unsorted, for the checked section and when nothing matches. */
        val DEFAULT: BasketKind = WICKER_PICNIC

        fun fromKey(key: String): BasketKind? = entries.firstOrNull { it.key == key }
    }
}

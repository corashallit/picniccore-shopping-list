package earth.cora.picnic.core.model

import earth.cora.picnic.core.store.BookSticker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Domain model. This file is a CROSS-MODULE CONTRACT (see docs/ARCHITECTURE.md §4):
 * field names are persisted in state.json, so renaming one needs a schema migration.
 * Everything here is immutable; change state only through the pure operations in StateOps.kt.
 */

/**
 * Current state.json schema version. Bump together with a migration in StateMigrations.
 * v2 (docs/DESIGN_STICKER.md §7.2): trips, book slots, check-off times, rarity, the open aisle, aisle styles,
 * favourites and the pantry. The v1 -> v2 step is real (StateMigrations.v1ToV2, StateMigrationV2Test).
 */
const val STATE_SCHEMA_VERSION: Int = 2

/** Where an item came from. Persisted by name. */
@Serializable
enum class ItemSource { TYPED, AI_TEXT, PHOTO, VOICE }

/** How rare an item's sticker is (eggs 6 and 13, §12.2). Persisted by name; NONE for almost everything. */
@Serializable
enum class Rarity { NONE, SHINY, JACKPOT }

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
    /** Epoch millis of the check-off; null while unchecked. Check-off order (baskets) and shop time (§7.1, §7.2). */
    val checkedAt: Long? = null,
    /** The trip sticker book spot this item's twin sits in (BookSlots); null while unchecked (§7.2). */
    val bookSlot: Int? = null,
    /** Shiny (egg 6) or jackpot (egg 13) sticker; it stays with the item everywhere (§12.2). */
    val rarity: Rarity = Rarity.NONE,
)

@Serializable
data class GroceryList(
    val id: String,
    val title: String,
    val createdAt: Long,
    /** Insertion order (oldest first). Display order comes from ListGrouping, never from this list. */
    val items: List<GroceryItem> = emptyList(),
    /**
     * Section ids the user collapsed (v1 UI). Kept so old files load; the C design ignores it and uses
     * [openAisle] (D2). Only the phase-0 list bridge still reads it, until WP5 lands one open aisle.
     */
    val collapsedAisles: Set<String> = emptySet(),
    /** D2: null = automatic (the current stop), "" = all folded, otherwise the open section id. */
    val openAisle: String? = null,
    /** The open trip (amendment A5, §7.1): at most one per list; null before the first check-off. */
    val trip: Trip? = null,
    /** When the last trip was filed (New trip): the "new" star's anchor while there is no trip (§7.1). */
    val lastClosedAt: Long? = null,
)

/**
 * One shopping trip on one list (amendment A5, §7.1, §7.2). Opened by the first check-off on a list
 * without one; closed (filed into the album) by New trip or deleting the list.
 */
@Serializable
data class Trip(
    /** "t-<listId>-<startedAt>": deterministic, so the pure toggleChecked can mint it; filing is idempotent by it. */
    val id: String,
    val startedAt: Long,
    val storeName: String,
    /** The last check-off or un-check (the idle-trip prompt, §7.1). */
    val lastActivityAt: Long,
    /** Invariant (TripRules.reconcile): set iff the list has >= 1 item and every item is checked. */
    val completedAt: Long? = null,
    val finaleShownAt: Long? = null,
    /** Shop time frozen at the first completion; null for a migrated trip (shown as "--"). */
    val shopMinutes: Int? = null,
    /** Egg 15: the pixel bee lives in the LCD for this trip. */
    val pet: Boolean = false,
    /** Egg 18: the printed receipt (stats frozen at the first print). */
    val receipt: Receipt? = null,
    /** The receipt was tucked into the book (tap, leave, list switch, New trip; §13.2). */
    val receiptSaved: Boolean = false,
    /** Egg 8: the nap cat trotted off; it does not return this trip. */
    val napDismissed: Boolean = false,
    /**
     * The celebration's bonus sticker (§11.2), saved when the scene starts so skipping never loses it;
     * filed as TripBook.bonus. Set once per trip (setTripBonus).
     */
    val bonus: BookSticker? = null,
)

/** The LCD receipt (egg 18, §13.2). Values are frozen when it is first printed. */
@Serializable
data class Receipt(
    val printedAt: Long,
    val dateLabel: String,
    val store: String,
    val stickers: Int,
    val baskets: Int,
    val minutes: Int?,
    val shiny: Int,
)

/** What New trip does to the list (§6.5, Q2): clear the gotten items (default) or un-check everything. */
enum class NewTripMode { CLEAR_GOTTEN, UNCHECK_ALL }

/**
 * Per-aisle look overrides set in the aisle editor (§4.26). Missing entry or null field = the default
 * for the aisle's walking position (§2.3).
 */
@Serializable
data class AisleStyle(
    /** Sticker family name ("strawberry", "ramune", ... §2.2). */
    val family: String? = null,
    /** Tab pattern name ("gingham", "polka", ... §2.3). */
    val pattern: String? = null,
    /** The store's own aisle number (the "A2" chip); null = parsed from the name ([storeAisleNumber]). */
    val storeAisle: Int? = null,
)

@Serializable
data class StoreProfile(
    val name: String = "My store",
    /** The user's editable aisle list: one aisle per line, in walking order. Parsed by [Aisles.parse]. */
    val aislesText: String = Aisles.DEFAULT_TEXT,
    /** Aisle id -> basket kind. Missing entries fall back to BasketCatalog.guess(aisle name). */
    val aisleBaskets: Map<String, BasketKind> = emptyMap(),
    /** Aisle id -> look overrides from the aisle editor (§4.26). */
    val aisleStyles: Map<String, AisleStyle> = emptyMap(),
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
    /** normalizeName of the user's favourite items (D17): the sakura heart flag and the "Favourites" chip. */
    val favourites: Set<String> = emptySet(),
    /** What she keeps at home (§0.4 Q5, §6.13): one pantry for the app, in shelf order. Recipes leave it out. */
    val pantry: List<PantryEntry> = emptyList(),
)

/** One pantry shelf sticker (§6.13). [name] as she wrote it (capitalised like an item name). */
@Serializable
data class PantryEntry(
    val name: String,
    /** The sticker: a learned override, the local match or the IconDecider's answer; null = not resolved yet (the capsule). */
    val icon: IconRef? = null,
    val addedAt: Long,
)

/** One parsed line of [StoreProfile.aislesText]. Derived, never persisted on its own. */
@Serializable
data class Aisle(
    /** Stable slug of the normalized name, e.g. "aisle-2-baking-spices". Never starts with '_' and is never "unsorted". */
    val id: String,
    /** The line as the user typed it (trimmed, bullet and "Also here" note removed), e.g. "Aisle 2 - Baking & spices". */
    val name: String,
    /** 1-based walking position. Drives the tab's number circle and the aisle colour rotation (D9). */
    val position: Int,
    /**
     * "Also here" (Cora, 5 October; §6.7): what this store also keeps in this aisle, "toothpaste, floss", from a trailing
     * "(also: …)" on its line ([Aisles.parse]); null = none. Never part of [name] or [id], so editing it never moves an
     * item. Shown only in My store, and sent to the model with the aisle so sorting follows it.
     */
    val note: String? = null,
)

/** Reserved section ids. Aisle slugs can never collide with these (they never start with '_'). */
object SectionIds {
    const val UNSORTED: String = "_unsorted"

    /**
     * The v1 "In the basket" section. Core no longer groups one (D1: checked rows stay in their aisle);
     * only the phase-0 list bridge (app ui/list) still shows it, until WP5 lays rows in place.
     */
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
    /** D8: the 17th kind (Baking). The keywords are tuned so the default store maps per §5.2 (BasketCatalogTest). */
    @SerialName("mixing_bowl") MIXING_BOWL("mixing_bowl"),
    ;

    companion object {
        /** When nothing matches (the keyword heuristic's answer for an unclear aisle, §5.2). */
        val DEFAULT: BasketKind = WICKER_PICNIC

        /** "Everything else" (the UNSORTED section) always uses the wire shopping basket (§4.10, §5.2). */
        val EVERYTHING_ELSE: BasketKind = WIRE_BASKET

        fun fromKey(key: String): BasketKind? = entries.firstOrNull { it.key == key }
    }
}

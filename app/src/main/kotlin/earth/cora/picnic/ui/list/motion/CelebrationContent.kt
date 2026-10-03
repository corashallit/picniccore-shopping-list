package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.Immutable
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ListGrouping
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.TripStatsValue
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.bookIcon
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.BookSticker
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.AisleLooks

/*
 * What the celebration shows (docs/DESIGN_STICKER.md §11.2): the trip's real list name and stats, one shelf basket
 * per stop, the riders of the ribbon ride, the stamp card and the bonus sticker. Built from the committed state when
 * the scene starts (WP5 hands the engine a source; [CelebrationContent.from] is that source). Pure; Owner: WP6.
 */

/** One stop on the celebration's shelf and stamp card: its look, its basket and what was got there. */
@Immutable
data class CelebrationStop(
    val sectionId: String,
    val look: AisleLook,
    val basket: BasketKind,
    /** The gotten stickers in check-off order (the shelf basket's minis before the ride). */
    val gotten: List<IconRef>,
)

/** One sticker riding the ribbon into the hamper, from its stop's basket ([stopIndex]). */
@Immutable
data class Rider(val icon: IconRef, val stopIndex: Int, val holo: Boolean)

/**
 * The bonus sticker (§11.2): a library sticker ([icon], catalog key or live-drawn), or a picnic-sheet decor sticker
 * ([decor]). [albumKey] is what the album records: a collected key ("<catalog key>" / "c:<customIconId>") made holo,
 * or a picnic-sheet ref ("i:<key>" / "d:<name>", [picnic] true) added to Album.picnic.
 */
@Immutable
data class BonusSticker(
    val name: String,
    val icon: IconRef?,
    val decor: String?,
    val albumKey: String,
    val picnic: Boolean,
) {
    /** What the trip keeps (Trip.bonus, filed as TripBook.bonus): the sticker, holo. Decor stickers as IconRef("d:<name>"). */
    fun bookSticker(now: Long): BookSticker =
        BookSticker(slot = -1, name = name, icon = icon ?: IconRef("d:${decor.orEmpty()}"), rarity = Rarity.SHINY, at = now)
}

@Immutable
data class CelebrationContent(
    val listId: String,
    val listName: String,
    val stats: TripStatsValue,
    /** Items on the list (the LCD's denominator: every one is checked). */
    val total: Int,
    /** Egg 15 hatched this trip (and Easter eggs are on): the pixel bee dances in the LCD. */
    val petHatched: Boolean,
    val stops: List<CelebrationStop>,
    val riders: List<Rider>,
    val bonus: BonusSticker?,
) {
    companion object {
        /**
         * The content for [listId]'s complete trip in [state]: stops in the list's order ("Everything else" first when
         * it is a stop), riders by [RideRules], the bonus by [BonusRule] against [album]. Null without that list.
         */
        fun from(state: AppState, listId: String, album: Album, catalog: IconCatalog, eggs: Boolean = true): CelebrationContent? {
            val list = state.lists.firstOrNull { it.id == listId } ?: return null
            val store = state.store
            val grouped = ListGrouping.group(
                list = list,
                aisles = store.aisles(),
                basketFor = { store.aisleBaskets[it.id] ?: catalog.baskets.guess(it.name) },
                styleFor = { store.aisleStyles[it.id] },
            )
            val stops = grouped.sections.map { s ->
                val look = if (s.kind == SectionKind.UNSORTED) AisleLooks.everythingElse else AisleLooks.of(s.position, s.aisle?.let { store.aisleStyles[it.id] })
                CelebrationStop(s.id, look, s.basket, s.gotten.map { it.bookIcon() })
            }
            val gottenByStop = grouped.sections.map { it.gotten }
            return CelebrationContent(
                listId = list.id,
                listName = list.title,
                stats = TripStats.of(list, grouped.stops),
                total = list.items.size,
                petHatched = eggs && list.trip?.pet == true,
                stops = stops,
                riders = RideRules.riders(gottenByStop),
                bonus = BonusRule.pick(list.items.filter { it.checked }, album, catalog),
            )
        }
    }
}

/** The ribbon ride's riders (§11.2): the newest 24 gotten stickers, with at least one from every basket. */
object RideRules {
    /**
     * [gottenByStop]: each stop's gotten items in check-off order. Every stop with a sticker gets its newest one first;
     * the rest of the [max] places go to the newest remaining stickers. Riders ride in check-off order (the newest lands
     * last); stickers beyond them leave with their baskets.
     */
    fun riders(gottenByStop: List<List<GroceryItem>>, max: Int = CelebrationTimeline.MAX_RIDERS): List<Rider> {
        val all = gottenByStop.flatMapIndexed { stop, items -> items.map { stop to it } }
        val chosen = LinkedHashSet<Pair<Int, GroceryItem>>()
        // One per basket (the newest), as long as there is room.
        for ((stop, items) in gottenByStop.withIndex()) {
            val newest = items.maxByOrNull { it.checkedAt ?: it.addedAt } ?: continue
            if (chosen.size < max) chosen += stop to newest
        }
        for (pair in all.sortedByDescending { it.second.checkedAt ?: it.second.addedAt }) {
            if (chosen.size >= max) break
            chosen += pair
        }
        return chosen.sortedBy { it.second.checkedAt ?: it.second.addedAt }
            .map { (stop, item) -> Rider(item.bookIcon(), stop, item.rarity != Rarity.NONE) }
    }
}

/**
 * The bonus sticker (§11.2): the holo variant of one sticker from this trip that is not yet holo in the album (the
 * most-checked across trips; ties by check order); if every one is already holo, the next picnic-sheet sticker.
 */
object BonusRule {
    /**
     * The picnic sheet in order, as album refs ("i:<catalog key>" / "d:<decor name>"). Mirrors WP8's
     * AlbumSheets.PICNIC (the lead points this at it when the two land together).
     */
    val PICNIC_SHEET: List<Pair<String, String>> = listOf(
        "i:picnic_blanket" to "Picnic blanket",
        "d:basketdeco" to "Picnic basket",
        "i:lemonade" to "Lemonade",
        "d:sandwich" to "Sandwich",
        "i:watermelon" to "Watermelon",
        "i:cherries" to "Cherries",
        "d:teacup" to "Teacup",
        "d:bee" to "Bee",
        "d:ladybug" to "Ladybug",
        "i:strawberry" to "Strawberries",
        "i:cookies" to "Cookies",
        "d:flower" to "Daisy",
    )

    /** [checked]: the trip's checked items. Null only when every trip sticker is holo and the picnic sheet is full. */
    fun pick(checked: List<GroceryItem>, album: Album, catalog: IconCatalog, picnicSheet: List<Pair<String, String>> = PICNIC_SHEET): BonusSticker? {
        val candidates = checked.mapNotNull { item -> catalog.collectKey(item)?.let { key -> item to key } }
            .filter { (_, key) -> album.collected[key]?.holo != true }
            .distinctBy { it.second }
        val best = candidates.sortedWith(
            compareByDescending<Pair<GroceryItem, String>> { album.collected[it.second]?.count ?: 0 }
                .thenBy { it.first.checkedAt ?: Long.MAX_VALUE },
        ).firstOrNull()
        if (best != null) {
            val (item, key) = best
            return BonusSticker(name = item.name, icon = IconRef(item.icon, item.customIconId), decor = null, albumKey = key, picnic = false)
        }
        val next = picnicSheet.firstOrNull { (ref, _) -> ref !in album.picnic } ?: return null
        val (ref, label) = next
        val key = ref.substringAfter(':')
        return if (ref.startsWith("d:")) {
            BonusSticker(name = label, icon = null, decor = key, albumKey = ref, picnic = true)
        } else {
            BonusSticker(name = label, icon = IconRef(key), decor = null, albumKey = ref, picnic = true)
        }
    }
}

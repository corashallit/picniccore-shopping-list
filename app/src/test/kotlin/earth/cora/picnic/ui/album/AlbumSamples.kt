package earth.cora.picnic.ui.album

import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.Receipt
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.BookStamp
import earth.cora.picnic.core.store.BookSticker
import earth.cora.picnic.core.store.Collected
import earth.cora.picnic.core.store.TripBook
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Albums for the album goldens and tests (AlbumTest, AlbumGoldensTest): Saturday 3 October 2026, Toronto. */
object AlbumSamples {
    val catalog: IconCatalog = IconCatalog.default
    val zone: ZoneId = ZoneId.of("America/Toronto")
    val today: LocalDate = LocalDate.of(2026, 10, 3)
    val now: Long = ZonedDateTime.of(2026, 10, 3, 11, 10, 0, 0, zone).toInstant().toEpochMilli()

    private fun at(month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun c(name: String, count: Int = 1, holo: Boolean = false) = Collected(name = name, firstAt = at(8, 1, 10, 0), count = count, holo = holo, seen = true)

    /** The mockup's first pockets (§6.10 screen 5): apple 3★ with its face, maple holo, croissants holo, … */
    private val favourites: Map<String, Collected> = mapOf(
        "apple" to c("Apples", 12),
        "maple_syrup" to c("Maple syrup", 4, holo = true),
        "strawberry" to c("Strawberries", 1),
        "croissant" to c("Croissants", 3, holo = true),
        "milk_bag" to c("Milk", 5),
        "butter_tart" to c("Butter tarts", 11),
        "peameal_bacon" to c("Peameal bacon", 3),
        "cheese_curds" to c("Cheese curds", 4),
        "sliced_bread" to c("Bread", 1),
        "peach" to c("Peaches", 3),
        "eggs" to c("Eggs", 1),
        "coffee" to c("Coffee", 1),
        "jam" to c("Jam", 1),
        "banana" to c("Bananas", 6),
        "lemon" to c("Lemons", 2, holo = true),
        "butter" to c("Butter", 2),
    )

    private fun stamps(done: Int): List<BookStamp> {
        val aisles = listOf(
            Triple("Produce", "strawberry", "strawberry"), Triple("Bakery", "ramune", "bee"), Triple("Deli & meat", "lemon", "star"),
            Triple("Baking", "melon", "heart"), Triple("Breakfast", "grape", "cloud"), Triple("Dairy & eggs", "peach", "flower"),
            Triple("Frozen", "mint", "ladybug"), Triple("Household", "sakura", "teacup"),
        )
        return aisles.mapIndexed { i, (name, family, pictogram) ->
            BookStamp(position = i + 1, aisleName = name, family = family, pattern = "plain", pictogram = pictogram, done = i < done)
        }
    }

    private fun stickers(keys: List<String>, start: Long): List<BookSticker> = keys.mapIndexed { i, key ->
        BookSticker(slot = i, name = key.replace('_', ' ').replaceFirstChar { it.uppercase() }, icon = IconRef(key), rarity = if (key == "maple_syrup") Rarity.SHINY else Rarity.NONE, at = start + i * 90_000L)
    }

    /** Sat 26 Sep: 19 of 19, perfect, receipt printed. */
    val perfectBook: TripBook = run {
        val start = at(9, 26, 10, 2)
        val keys = listOf("banana", "milk_bag", "eggs", "sliced_bread", "coffee", "apple", "strawberry", "croissant", "butter", "peas", "jam", "lemon", "carrot", "broccoli", "peach", "cheese_curds", "peameal_bacon", "butter_tart", "maple_syrup")
        TripBook(
            id = "trip-0926", listTitle = "Saturday shop", storeName = "Hillcrest Market",
            startedAt = start, completedAt = start + 41 * 60_000L, closedAt = start + 50 * 60_000L,
            total = 19, minutes = 41, stickers = stickers(keys, start), stamps = stamps(8), perfect = true,
            receipt = Receipt(printedAt = start + 42 * 60_000L, dateLabel = "Sat 26 Sep", store = "Hillcrest Market", stickers = 19, baskets = 8, minutes = 41, shiny = 1),
        )
    }

    /** Sat 19 Sep: 21 of 22, not perfect, no receipt. */
    val partBook: TripBook = run {
        val start = at(9, 19, 9, 48)
        val keys = listOf("strawberry", "croissant", "butter", "peas", "jam", "lemon", "apple", "banana", "milk_bag", "eggs", "coffee", "sliced_bread", "carrot", "broccoli", "peach", "cheese_curds", "peameal_bacon", "butter_tart", "banana", "lemon", "apple")
        TripBook(
            id = "trip-0919", listTitle = "Saturday shop", storeName = "Hillcrest Market",
            startedAt = start, completedAt = null, closedAt = start + 60 * 60_000L,
            total = 22, minutes = null, stickers = stickers(keys, start), stamps = stamps(6), perfect = false, receipt = null,
        )
    }

    /** "Full-ish": the favourites plus most of the catalog; two books; sakura and Thanksgiving 2025 unlocked; streak 4. */
    val full: Album = run {
        val rest = catalog.collectibleKeys.filter { it !in favourites }.take(120)
            .associateWith { key -> c(catalog[key]?.label ?: key, count = 1 + key.length % 4) }
        Album(
            collected = favourites + rest,
            books = listOf(perfectBook, partBook),
            sheets = setOf(AlbumSheets.SAKURA, "thanksgiving-2025", "halloween-2025"),
            picnic = listOf("i:picnic_blanket", "d:basketdeco", "i:lemonade", "d:sandwich", "i:watermelon", "i:cherries", "d:teacup", "d:bee", "i:strawberry"),
            finishedTrips = 9,
            perfectStreak = 4,
        )
    }

    /** The mockup's album: the 16 favourites, two books, picnic stickers, perfect week. */
    val mockup: Album = full.copy(collected = favourites)

    /** Early days: five stickers, one book. */
    val early: Album = Album(
        collected = favourites.filterKeys { it in setOf("apple", "banana", "milk_bag", "eggs", "strawberry") }.mapValues { it.value.copy(count = 1, holo = false) },
        books = listOf(partBook),
        finishedTrips = 1,
        perfectStreak = 0,
    )

    /** Ten finished trips: the back pocket is open. */
    val backPocket: Album = full.copy(finishedTrips = 10, backPocketOpen = true, sheets = full.sheets + AlbumSheets.SHEET_2)

    /** Fresh pockets (not yet shown): they magic-appear once. */
    val fresh: Album = early.copy(collected = early.collected + ("peach" to Collected("Peaches", now, 1, seen = false)))

    fun state(album: Album, tab: AlbumTab = AlbumTab.Groceries, fresh: Set<String> = emptySet()): AlbumUiState =
        AlbumStateBuilder(catalog, { today }, zone, { now }).build(album, tab, "Saturday shop", fresh)
}

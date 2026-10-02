package earth.cora.picnic.core.model

import earth.cora.picnic.core.CoreJson
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.BookSticker
import earth.cora.picnic.core.store.Collected
import earth.cora.picnic.core.store.InMemoryAlbumStore
import earth.cora.picnic.core.store.TripBook
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The parts of the phase-0 contract (docs/DESIGN_STICKER.md §19.1, §21.3) that already have bodies.
 * WP3 owns these files and folds this into its own tests (TripOpsTest, AislesTest, FavouritesTest, ...).
 */
class Phase0ContractsTest {
    private fun item(id: String, checked: Boolean = false, aisle: String? = "produce") =
        GroceryItem(id = id, name = id, aisleId = aisle, icon = "paper_bag", addedAt = 1, checked = checked)

    private fun state(trip: Trip? = null) = AppState(
        lists = listOf(GroceryList("l1", "Saturday shop", 0, listOf(item("a", checked = true), item("b")), trip = trip)),
        currentListId = "l1",
    )

    private val trip = Trip(id = "t-l1-5", startedAt = 5, storeName = "Hillcrest Market", lastActivityAt = 5)

    @Test
    fun v2FieldsDefaultWhenAbsentAndRoundTrip() {
        val minimal = """{"lists":[{"id":"l","title":"T","createdAt":0,"items":[{"id":"i","name":"Milk","icon":"milk_bag","addedAt":1}]}],"currentListId":"l"}"""
        val s = CoreJson.json.decodeFromString(AppState.serializer(), minimal)
        val milk = s.lists.single().items.single()
        assertNull(milk.checkedAt)
        assertNull(milk.bookSlot)
        assertEquals(Rarity.NONE, milk.rarity)
        assertNull(s.lists.single().openAisle)
        assertNull(s.lists.single().trip)
        assertNull(s.lists.single().lastClosedAt)
        assertTrue(s.favourites.isEmpty())
        assertTrue(s.store.aisleStyles.isEmpty())

        val full = state(trip.copy(receipt = Receipt(9, "SAT 3 OCT", "HILLCREST", 24, 8, 41, 1)))
            .setFavourite("milk", true)
            .copy(store = StoreProfile(aisleStyles = mapOf("produce" to AisleStyle(family = "melon", storeAisle = 3))))
        assertEquals(full, CoreJson.json.decodeFromString(AppState.serializer(), CoreJson.json.encodeToString(AppState.serializer(), full)))
        assertEquals("\"mixing_bowl\"", CoreJson.json.encodeToString(BasketKind.serializer(), BasketKind.MIXING_BOWL))
    }

    @Test
    fun openAisleFavouritesStylesAndBaskets() {
        val s = state()
        assertEquals("bakery", s.setOpenAisle("l1", "bakery").list("l1")!!.openAisle)
        assertSame(s, s.setOpenAisle("l1", null))
        assertEquals("", s.setOpenAisle("l1", "").list("l1")!!.openAisle)

        val fav = s.setFavourite("milk", true)
        assertEquals(setOf("milk"), fav.favourites)
        assertSame(fav, fav.setFavourite("milk", true))
        assertTrue(fav.setFavourite("milk", false).favourites.isEmpty())
        assertSame(s, s.setFavourite("", true))

        val style = AisleStyle(family = "lemon", pattern = "polka")
        val styled = s.setAisleStyle("produce", style)
        assertEquals(style, styled.store.aisleStyles["produce"])
        assertSame(s, s.setAisleStyle("not-an-aisle", style))
        assertTrue(styled.setAisleStyle("produce", AisleStyle()).store.aisleStyles.isEmpty(), "an all-default style is removed")

        val bowl = s.setAisleBasket("aisle-2-baking-spices", BasketKind.MIXING_BOWL)
        assertEquals(BasketKind.MIXING_BOWL, bowl.store.aisleBaskets["aisle-2-baking-spices"])
        assertSame(bowl, bowl.setAisleBasket("aisle-2-baking-spices", BasketKind.MIXING_BOWL))
        assertSame(s, s.setAisleBasket("nope", BasketKind.COOLER))
        assertEquals(Rarity.SHINY, s.setRarity("l1", "b", Rarity.SHINY).findItem("b")!!.rarity)
    }

    @Test
    fun tripOpsAreNoOpsWithoutATrip() {
        val s = state()
        assertSame(s, s.hatchPet("l1"))
        assertSame(s, s.markFinaleShown("l1", 9))
        assertSame(s, s.dismissNap("l1"))
        assertSame(s, s.saveReceipt("l1"))
        assertSame(s, s.printReceipt("l1", Receipt(9, "SAT 3 OCT", "HILLCREST", 1, 1, 1, 0)))
        assertEquals(s to null, s.newTrip("l1", NewTripMode.CLEAR_GOTTEN, 9), "New trip is disabled while there is no trip")
    }

    @Test
    fun tripOpsOnAnOpenTrip() {
        val s = state(trip)
        val receipt = Receipt(9, "SAT 3 OCT", "HILLCREST", 1, 1, 1, 0)
        assertTrue(s.hatchPet("l1").list("l1")!!.trip!!.pet)
        assertTrue(s.dismissNap("l1").list("l1")!!.trip!!.napDismissed)
        val shown = s.markFinaleShown("l1", 9)
        assertEquals(9L, shown.list("l1")!!.trip!!.finaleShownAt)
        assertSame(shown, shown.markFinaleShown("l1", 99), "the finale is marked once")
        val printed = s.printReceipt("l1", receipt)
        assertEquals(receipt, printed.list("l1")!!.trip!!.receipt)
        assertEquals(receipt, printed.printReceipt("l1", receipt.copy(stickers = 2)).list("l1")!!.trip!!.receipt, "frozen at the first print")
        assertSame(s, s.saveReceipt("l1"), "nothing printed yet")
        assertTrue(printed.saveReceipt("l1").list("l1")!!.trip!!.receiptSaved)
    }

    @Test
    fun idleTripStartsANewBook() {
        val list = state(trip).list("l1")!!
        assertFalse(TripRules.startsNewBook(list, 5 + TripRules.IDLE_MILLIS))
        assertTrue(TripRules.startsNewBook(list, 6 + TripRules.IDLE_MILLIS))
        assertFalse(TripRules.startsNewBook(state().list("l1")!!, Long.MAX_VALUE), "no trip, nothing to file")
    }

    @Test
    fun storeAisleNumberAndDisplayName() {
        assertEquals(2, storeAisleNumber("Aisle 2 - Baking & spices"))
        assertEquals(4, storeAisleNumber("Allée 4 : conserves"))
        assertNull(storeAisleNumber("1% milk fridge"))
        val names = Aisles.parse("Aisle 3 - Pasta, rice & international\n12 Frozen\nAisle 7\nProduce\nAisle5 - Snacks & drinks")
            .map { it.displayName() }
        assertEquals(listOf("Pasta, rice & international", "Frozen", "Aisle 7", "Produce", "Snacks & drinks"), names)
        assertEquals("Snacks", Aisle("snacks", "#3 Snacks", 1).displayName(), "(a '#' line is a comment in aisle text)")
    }

    @Test
    fun albumReferencesCustomIcons() = runTest {
        val sticker = BookSticker(0, "Gochujang", IconRef("condiments_generic", "c-123"), Rarity.NONE, 1)
        val book = TripBook(
            id = "t-l1-5", listTitle = "Saturday shop", storeName = "Hillcrest Market", startedAt = 5, completedAt = null,
            closedAt = 9, total = 2, minutes = null, stickers = listOf(sticker), stamps = emptyList(), perfect = false,
            receipt = null, bonus = sticker.copy(icon = IconRef("cookie", "c-bonus")),
        )
        val album = InMemoryAlbumStore(Album(collected = mapOf("c:c-456" to Collected("Kimchi", 1, 1), "lemon" to Collected("Lemons", 1, 2)), books = listOf(book)))
        assertEquals(setOf("c-123", "c-456", "c-bonus"), album.referencedCustomIconIds())
        val updated = album.update { it.copy(finishedTrips = 1) }
        assertEquals(1, album.current().finishedTrips)
        assertSame(updated, album.current())
    }
}

package earth.cora.picnic.core.model

import earth.cora.picnic.core.SaturdayShop
import earth.cora.picnic.core.SaturdayShop.LIST_ID
import earth.cora.picnic.core.SaturdayShop.TRIP_START
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Shop time, the date label and the stats (docs/DESIGN_STICKER.md §7.1; checklist 137, 143). */
class TripStatsTest {
    private val min = 60_000L
    private val hour = 60 * min

    private fun item(id: String, checkedAt: Long?) =
        GroceryItem(id = id, name = id, icon = "apple", addedAt = 0, checked = checkedAt != null, checkedAt = checkedAt)

    private fun state(vararg ids: String) = AppState(
        lists = listOf(GroceryList("l", "List", 0, ids.map { GroceryItem(id = it, name = it, icon = "apple", addedAt = 0) })),
        currentListId = "l",
    )

    @Test
    fun sessionsBreakAtGapsOverFortyFiveMinutes() {
        val tuesday = 0L
        val saturday = 4 * 24 * hour
        val items = listOf(item("a", tuesday), item("b", saturday), item("c", saturday + 44 * min), item("d", saturday + 88 * min), item("e", null))
        assertEquals(saturday, TripStats.lastSessionStart(items), "a 44 min gap continues the session; Tuesday's check is another one")
        assertEquals(saturday + 134 * min, TripStats.lastSessionStart(items + item("f", saturday + 134 * min)), "a 46 min gap breaks it")
        assertNull(TripStats.lastSessionStart(listOf(item("x", null))), "before the trip opens: today")
    }

    @Test
    fun shopMinutesRunFromTheLastSessionToTheCompletion() {
        val saturday = 10 * 24 * hour
        val items = listOf(item("a", 0), item("b", saturday), item("c", saturday + 41 * min))
        assertEquals(41, TripStats.shopMinutes(items, saturday + 41 * min), "a check at home on Tuesday does not count Saturday as four days")
        assertEquals(65, TripStats.shopMinutes(listOf(item("a", 0), item("b", 30 * min), item("c", 65 * min)), 65 * min))
        assertEquals(1, TripStats.shopMinutes(listOf(item("a", 5)), 5), "at least 1 min")
        assertEquals(42, TripStats.shopMinutes(listOf(item("a", 0), item("b", 41 * min + 31_000)), 41 * min + 31_000), "rounded to the nearest minute")
        assertEquals(41, TripStats.shopMinutes(listOf(item("a", 0), item("b", 41 * min)), 41 * min + 3 * hour), "completed hours later at home: the shop ended at its last check-off")
        assertNull(TripStats.shopMinutes(listOf(item("a", null)), 9))
    }

    @Test
    fun frozenAfterRecheck() {
        val s = state("a", "b").toggleChecked("l", "a", 0).toggleChecked("l", "b", 41 * min)
        assertEquals(41, s.list("l")!!.trip!!.shopMinutes)
        val later = s.toggleChecked("l", "b", 2 * hour).toggleChecked("l", "b", 2 * hour + min)
        assertEquals(41, later.list("l")!!.trip!!.shopMinutes, "un-checking and re-checking an hour later never turns 41 min into 1 min")
    }

    @Test
    fun formatMinutes() {
        assertEquals("41 min", TripStats.formatMinutes(41))
        assertEquals("1 min", TripStats.formatMinutes(1))
        assertEquals("1 h 5 min", TripStats.formatMinutes(65))
        assertEquals("2 h", TripStats.formatMinutes(120))
        assertEquals("--", TripStats.formatMinutes(null), "a migrated trip")
    }

    @Test
    fun dateIsLastSession() {
        val fixture = SaturdayShop.state().list(LIST_ID)!!
        assertEquals(TRIP_START, TripStats.lastSessionStart(fixture), "the fixture's book reads Sat 3 Oct from the 09:58 check-off")
        // A stray check at home on Tuesday, then the Saturday shop: the date follows the last session.
        val s = state("a", "b", "c").toggleChecked("l", "a", 0).toggleChecked("l", "b", 4 * 24 * hour)
        assertEquals(4 * 24 * hour, TripStats.lastSessionStart(s.list("l")!!))
    }

    @Test
    fun statsOfTheFixture() {
        val fixture = SaturdayShop.state()
        val list = fixture.list(LIST_ID)!!
        val stops = ListGrouping.group(list, fixture.store.aisles(), { BasketKind.DEFAULT }).stops
        assertEquals(TripStatsValue(stickers = 10, baskets = 8, minutes = null, shiny = 0), TripStats.of(list, stops), "the maple tin is shiny but not gotten yet")
        val withShiny = fixture.setRarity(LIST_ID, "butter", Rarity.SHINY).list(LIST_ID)!!
        assertEquals(1, TripStats.of(withShiny, stops).shiny)
        assertEquals(TripStatsValue(0, 0, null, 0), TripStats.of(SaturdayShop.unchecked().list(LIST_ID)!!.copy(items = emptyList()), 0))
    }
}

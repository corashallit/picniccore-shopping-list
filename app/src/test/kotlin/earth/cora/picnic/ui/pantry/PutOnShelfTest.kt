package earth.cora.picnic.ui.pantry

import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.ui.common.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The Edit sheet's "In my pantry" (§6.3, checklist 329): the entry it puts on the shelf always covers the item, so the
 * switch turns on and a second tap takes it off again.
 */
class PutOnShelfTest {
    private val empty: AppState = Samples.emptyState.copy(pantry = emptyList())

    private fun shelve(name: String): String {
        val next = empty.putOnShelf(name, Samples.NOW)
        val entry = next.pantryEntryFor(name)
        assertNotNull("'$name' is covered once on the shelf: ${next.pantry}", entry)
        assertEquals(1, next.pantry.size)
        return entry!!.name
    }

    /** A number that is part of the name stays: cleaning it away would leave an entry that doesn't cover the item. */
    @Test
    fun namesWithNumbersStayWhole() {
        assertEquals("3 bean salad", shelve("3 bean salad"))
        assertEquals("1 L oat milk", shelve("1 L oat milk"))
        assertEquals("Tomatoes 796 mL", shelve("Tomatoes 796 mL"))
    }

    /** Plain names are cleaned and capitalised like any pantry entry, never split. */
    @Test
    fun plainNamesRoundTrip() {
        assertEquals("Milk, 2 %", shelve("Milk, 2 %"))
        assertEquals("Eggs, large", shelve("Eggs, large"))
        assertEquals("Mac and cheese", shelve("mac and cheese"))
        assertEquals("Flour", shelve("  flour "))
    }

    /** Already covered (singular/plural folding): nothing changes. */
    @Test
    fun alreadyCovered() {
        val state = empty.copy(pantry = listOf(PantryEntry(name = "Egg", addedAt = 1L)))
        assertSame(state, state.putOnShelf("Eggs", Samples.NOW))
    }
}

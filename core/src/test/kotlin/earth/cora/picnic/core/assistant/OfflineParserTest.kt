package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.Quantities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineParserTest {
    private val catalog = TestSupport.catalog
    private val aisles = Aisles.parse(Aisles.DEFAULT_TEXT)
    private val emptyContext = IngestContext(aisles, emptyList(), emptyList())

    private fun parse(text: String, ctx: IngestContext = emptyContext) = OfflineParser.parse(text, catalog, ctx)

    @Test
    fun quantitiesLeadingAndTrailing() {
        val cases = mapOf(
            "2 lemons" to ("lemons" to "2"),
            "2x lemons" to ("lemons" to "2"),
            "2 x lemons" to ("lemons" to "2"),
            "lemons x2" to ("lemons" to "2"),
            "lemons ×2" to ("lemons" to "2"),
            "lemons 2x" to ("lemons" to "2"),
            "500g flour" to ("flour" to "500 g"),
            "500 grams flour" to ("flour" to "500 g"),
            "1 L milk" to ("milk" to "1 L"),
            "1.5 kg potatoes" to ("potatoes" to "1.5 kg"),
            "1,5 kg potatoes" to ("potatoes" to "1,5 kg"),
            "250 ml cream" to ("cream" to "250 mL"),
            "2 bags of chips" to ("chips" to "2 bags"),
            "a dozen eggs" to ("eggs" to "1 dozen"),
            "dozen eggs" to ("eggs" to "1 dozen"),
            "two avocados" to ("avocados" to "2"),
            "milk 2L" to ("milk" to "2 L"),
            "flour 500 g" to ("flour" to "500 g"),
            "avocados (3)" to ("avocados" to "3"),
            "avocados(3)" to ("avocados" to "3"),
            "1/2 lb ham" to ("ham" to "1/2 lb"),
        )
        cases.forEach { (input, expected) ->
            val split = Quantities.split(input)
            assertEquals(expected, split.name to split.quantity, input)
        }
    }

    @Test
    fun thingsThatAreNotQuantities() {
        listOf("Diapers size 4", "2% milk", "V8", "7up", "a lemon", "an apple", "500", "AA batteries", "Coke Zero").forEach {
            val split = Quantities.split(it)
            assertNull(split.quantity, it)
            assertEquals(it, split.name)
        }
    }

    @Test
    fun splitsLinesCommasSemicolonsAndStripsBullets() {
        val items = parse("- milk\n* 2 lemons; eggs x12\n• bread, 1,5 kg potatoes\n3) avocados (3)\n[ ] coffee\n+ birthday candles!!\n\n,,;")
        assertEquals(
            listOf("Milk" to null, "Lemons" to "2", "Eggs" to "12", "Bread" to null, "Potatoes" to "1,5 kg", "Avocados" to "3", "Coffee" to null, "Birthday candles" to null),
            items.map { it.name to it.quantity },
        )
        assertTrue(items.all { it.note == null })
    }

    @Test
    fun iconsFromLocalMatchOrPending() {
        val items = parse("Lemons, Gochujang, 4L milk")
        assertEquals("lemon", items[0].icon)
        assertEquals(IconFit.EXACT, items[0].iconFit)
        assertEquals(IconCatalog.DEFAULT_ICON, items[1].icon)
        assertEquals(IconFit.PENDING, items[1].iconFit)
        assertEquals("milk_bag", items[2].icon, "the whole piece is matched first")
        assertEquals("4 L", items[2].quantity)
    }

    @Test
    fun overridesCorrectionsAndDedupe() {
        val ctx = IngestContext(
            aisles = aisles,
            corrections = listOf(
                Correction("oat milk", "dairy-eggs", 9),
                Correction("oat milk", "bulk-natural-foods", 1),
                Correction("ghost", "not-an-aisle", 5),
            ),
            existingNames = listOf("Butter", "Bananas"),
            iconOverrides = mapOf("gochujang" to "hot_sauce", "tahini" to "retired_key"),
        )
        val items = parse("oat milk, butter, banana, Gochujang, gochujangs, Tahini, ghost", ctx)
        assertEquals(listOf("Oat milk", "Gochujang", "Tahini", "Ghost"), items.map { it.name })
        assertEquals("dairy-eggs", items[0].aisleId, "newest correction (first in the list) wins")
        assertTrue(items[0].aisleFromCorrection)
        assertEquals("hot_sauce", items[1].icon)
        assertEquals(IconFit.EXACT, items[1].iconFit)
        assertEquals(IconFit.PENDING, items[2].iconFit, "an override to a retired key is ignored")
        assertNull(items[3].aisleId, "corrections into vanished aisles are ignored")
        assertFalse(items[3].aisleFromCorrection)
    }

    @Test
    fun nothingUsefulGivesNothing() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("\n - \n 2 \n !!!").isEmpty())
    }
}

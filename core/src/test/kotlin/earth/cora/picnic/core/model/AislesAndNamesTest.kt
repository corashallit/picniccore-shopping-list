package earth.cora.picnic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AislesAndNamesTest {
    @Test
    fun defaultAislesMatchTheBenchmarkIds() {
        assertEquals(
            listOf(
                "produce", "bakery", "deli-cheese", "meat-seafood", "aisle-1-breakfast-coffee-tea",
                "aisle-2-baking-spices", "aisle-3-pasta-rice-international", "aisle-4-canned-goods-soups",
                "aisle-5-snacks-drinks", "aisle-6-household-cleaning", "aisle-7-health-beauty-baby",
                "dairy-eggs", "frozen", "bulk-natural-foods",
            ),
            Aisles.parse(Aisles.DEFAULT_TEXT).map { it.id },
        )
    }

    @Test
    fun bulletsAndNumberingAreStrippedButAisleTextIsKept() {
        val aisles = Aisles.parse("1. Produce\n2) Bakery\n* Frozen\n• Fleurs\n· Dairy\nAisle 2 - Baking & spices")
        assertEquals(listOf("Produce", "Bakery", "Frozen", "Fleurs", "Dairy", "Aisle 2 - Baking & spices"), aisles.map { it.name })
        assertEquals("aisle-2-baking-spices", aisles.last().id)
    }

    @Test
    fun blankCommentAndWhitespaceLinesAreSkippedAndPositionsAreDense() {
        val aisles = Aisles.parse("\r\n   \n# my store\nProduce\r\n\t\n  Bakery  \n#Frozen\n")
        assertEquals(listOf("produce" to 1, "bakery" to 2), aisles.map { it.id to it.position })
    }

    @Test
    fun accentsAndFrenchNamesFold() {
        val aisles = Aisles.parse("Fruits & Légumes\nProduits laitiers / Œufs\nSurgelés")
        assertEquals(listOf("fruits-legumes", "produits-laitiers-oeufs", "surgeles"), aisles.map { it.id })
    }

    @Test
    fun duplicatesReservedAndSymbolOnlyNames() {
        val aisles = Aisles.parse("Bakery\nBAKERY\nbakery!\nUnsorted\n★★★\nunsorted aisle")
        assertEquals(
            listOf("bakery", "bakery-2", "bakery-3", "unsorted-aisle", "aisle-5", "unsorted-aisle-2"),
            aisles.map { it.id },
        )
        assertTrue(aisles.none { it.id == UNSORTED_WIRE_ID || it.id.startsWith("_") })
    }

    @Test
    fun emptyTextHasNoAisles() {
        assertTrue(Aisles.parse("").isEmpty())
        assertTrue(Aisles.parse("\n# only a comment\n").isEmpty())
    }

    @Test
    fun normalizeNameSingularizesOnlyTheLastWord() {
        assertEquals("green onion", normalizeName("Green Onions"))
        assertEquals("glass", normalizeName("Glass"))
        assertEquals("asparagus", normalizeName("Asparagus"))
        assertEquals("pea", normalizeName("Peas"))
        assertEquals("gas", normalizeName("gas"))
        assertEquals("oeuf", normalizeName("Œufs"))
        assertEquals("franks red hot", normalizeName("Frank’s Red Hot"))
        assertEquals("2 lemon", normalizeName("2 Lemons")) // callers pass names, not quantities
        assertEquals("", normalizeName("  ★ "))
    }
}

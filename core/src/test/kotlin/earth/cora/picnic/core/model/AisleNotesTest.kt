package earth.cora.picnic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun item(id: String, aisle: String?) = GroceryItem(id = id, name = id, aisleId = aisle, icon = "apple", addedAt = 0)

private fun state(text: String, vararg items: GroceryItem) = AppState(
    lists = listOf(GroceryList("l1", "Saturday shop", 0, items.toList())),
    currentListId = "l1",
    store = StoreProfile(name = "Hillcrest Market", aislesText = text),
)

private val guess: (Aisle) -> BasketKind = { BasketKind.CANDY_JAR }

/** "Also here" notes on aisles (Cora, 5 October; §6.7): the parser's syntax, and every edit keeps a note with its aisle. */
class AisleNotesTest {
    @Test
    fun aTrailingAlsoIsTheNoteNeverTheNameOrId() {
        val aisles = Aisles.parse("Produce (also: toothpaste, floss)\n- Aisle 2 - Baking (Also here: birthday candles)\nFrozen")
        assertEquals(listOf("produce", "aisle-2-baking", "frozen"), aisles.map { it.id })
        assertEquals(listOf("Produce", "Aisle 2 - Baking", "Frozen"), aisles.map { it.name })
        assertEquals(listOf("toothpaste, floss", "birthday candles", null), aisles.map { it.note })
        assertEquals("Baking", aisles[1].displayName())
        assertEquals(2, storeAisleNumber(aisles[1].name))
    }

    @Test
    fun theSyntaxForgivesAPhoneKeyboard() {
        fun one(line: String) = Aisles.parse(line).single().let { it.name to it.note }
        assertEquals("Produce" to "toothpaste", one("Produce (also: toothpaste"), "the closing bracket is optional")
        assertEquals("Produce" to "toothpaste", one("Produce(ALSO:toothpaste)  "))
        assertEquals("Produce" to "floss (mint)", one("Produce (also: floss (mint))"), "brackets inside the note stay")
        assertEquals("Produce" to "dish soap", one("Produce (also:   dish\tsoap )"))
        assertEquals("Produce" to null, one("Produce (also: )"), "an empty note is no note")
        assertEquals("(also: toothpaste)" to null, one("(also: toothpaste)"), "a note needs a name before it")
        assertEquals("Snacks (and chips)" to null, one("Snacks (and chips)"), "other brackets are part of the name")
        assertEquals("Also aisle" to null, one("Also aisle"))
    }

    @Test
    fun linesWithoutNotesParseExactlyAsBefore() {
        val ids = Aisles.parse(Aisles.DEFAULT_TEXT).map { it.id }
        assertEquals("produce", ids.first())
        assertEquals("aisle-3-pasta-rice-international", ids[6])
        assertEquals("bulk-natural-foods", ids.last())
        assertTrue(Aisles.parse(Aisles.DEFAULT_TEXT).all { it.note == null })
        val noted = Aisles.DEFAULT_TEXT.lines().joinToString("\n") { "$it (also: something)" }
        assertEquals(ids, Aisles.parse(noted).map { it.id }, "a note on every line changes no id")
    }

    @Test
    fun notesAreKeptShort() {
        val long = (1..30).joinToString(", ") { "thing$it" }
        val note = Aisles.parse("Household (also: $long)").single().note!!
        assertTrue(note.length <= Aisles.NOTE_MAX, note)
        assertTrue(note.startsWith("thing1, thing2"), note)
        assertTrue(!note.endsWith(",") && long.startsWith(note), "cut after a whole word: $note")
    }

    @Test
    fun setAisleNoteChangesOnlyTheNote() {
        val s = state("# store\n- Produce\nAisle 2 - Baking\nFrozen", item("bread", "aisle-2-baking"), item("peas", "frozen"))
            .copy(corrections = listOf(Correction("flour", "aisle-2-baking", 1)))
            .setAisleBasket("aisle-2-baking", BasketKind.MIXING_BOWL)
            .setAisleStyle("aisle-2-baking", AisleStyle(family = "cocoa"))
        val noted = s.setAisleNote("produce", "toothpaste,\nfloss")
        assertEquals("# store\n- Produce (also: toothpaste, floss)\nAisle 2 - Baking\nFrozen", noted.store.aislesText)
        assertEquals(s.store.aisles().map { it.id }, noted.store.aisles().map { it.id })
        assertEquals(s.lists, noted.lists, "no item moves")
        assertEquals(s.corrections, noted.corrections)
        assertEquals(s.store.aisleBaskets, noted.store.aisleBaskets)
        assertEquals(s.store.aisleStyles, noted.store.aisleStyles)
        val changed = noted.setAisleNote("produce", "batteries")
        assertEquals("batteries", changed.store.aisles()[0].note)
        assertEquals("# store\n- Produce\nAisle 2 - Baking\nFrozen", changed.setAisleNote("produce", "  ").store.aislesText, "blank removes it")
        assertSame(noted, noted.setAisleNote("produce", " toothpaste, floss "), "the same note")
        assertSame(s, s.setAisleNote("nope", "x"))
    }

    @Test
    fun renameAndMoveKeepEachNoteWithItsAisle() {
        val s = state("Produce (also: toothpaste)\n  * Bakery (also: candles)\nFrozen", item("bread", "bakery"))
        val renamed = s.renameAisle("bakery", "Boulangerie", 1)
        assertEquals("Produce (also: toothpaste)\n  * Boulangerie (also: candles)\nFrozen", renamed.store.aislesText)
        assertEquals("boulangerie", renamed.findItem("bread")!!.aisleId)
        assertEquals("candles", renamed.store.aisles()[1].note)
        val typed = s.renameAisle("bakery", "Bakery (also: cake boxes)", 1)
        assertEquals("cake boxes", typed.store.aisles()[1].note, "a name typed with a note brings it")
        assertEquals("bakery", typed.findItem("bread")!!.aisleId)

        val moved = s.moveAisle(0, 2, guess)
        assertEquals("  * Bakery (also: candles)\nFrozen\nProduce (also: toothpaste)", moved.store.aislesText)
        assertEquals(listOf("candles", null, "toothpaste"), moved.store.aisles().map { it.note })
        assertEquals("bakery", moved.findItem("bread")!!.aisleId)
    }

    @Test
    fun aislesAsTextRoundTripsNotesAndANoteEditMovesNothing() {
        val s = state("Produce\nBakery\nFrozen", item("bread", "bakery"), item("peas", "frozen"))
        val withNotes = s.commitAislesText("Produce (also: toothpaste)\nBakery\nFrozen (also: ice)", guess, 1)
        assertEquals(s.store.aisles().map { it.id }, withNotes.store.aisles().map { it.id })
        assertEquals(s.lists.single().items, withNotes.lists.single().items, "a note edit moves nothing")
        assertEquals(listOf("toothpaste", null, "ice"), withNotes.store.aisles().map { it.note })
        // The text the editor shows is the stored text: notes come back exactly as written.
        assertEquals("Produce (also: toothpaste)\nBakery\nFrozen (also: ice)", withNotes.store.aislesText)
        // A note set from the label shows up in the text, and the text parses back to it.
        val viaLabel = withNotes.setAisleNote("bakery", "cake boxes")
        assertEquals("cake boxes", Aisles.parse(viaLabel.store.aislesText)[1].note)
        // Renamed in the text with its note: still the same aisle (a rename), so its items follow.
        val renamed = withNotes.commitAislesText("Produce (also: toothpaste)\nBoulangerie\nFrozen (also: ice)", guess, 1)
        assertEquals("boulangerie", renamed.findItem("bread")!!.aisleId)
        assertNull(renamed.store.aisles()[1].note)
    }

    @Test
    fun ingestContextCarriesTheNotes() {
        val s = state("Produce (also: toothpaste)\nBakery")
        assertEquals(listOf("toothpaste", null), s.ingestContext("l1").aisles.map { it.note })
    }
}

/** A move's Undo (Cora, 5 October; §4.15): the old aisle and the previous correction come back. */
class MoveUndoTest {
    private val base = state("Produce\nHousehold\nFrozen", item("Toothpaste", "household"), item("Peas", "frozen"))

    @Test
    fun undoPutsTheAisleAndThePreviousCorrectionBack() {
        val s = base.copy(corrections = listOf(Correction("toothpaste", "household", 1), Correction("pea", "frozen", 2)))
        val (moved, undo) = s.moveItemToAisleWithUndo("l1", "Toothpaste", "produce", 10)
        assertEquals("produce", moved.findItem("Toothpaste")!!.aisleId)
        assertEquals(listOf(Correction("pea", "frozen", 2), Correction("toothpaste", "produce", 10)), moved.corrections)
        val back = moved.undoMove(undo!!, 11)
        assertEquals("household", back.findItem("Toothpaste")!!.aisleId)
        assertEquals(false, back.findItem("Toothpaste")!!.manuallyPlaced)
        assertEquals(s.corrections, back.corrections, "the previous correction, where it was")
    }

    @Test
    fun undoOfAFirstMoveLeavesNoCorrection() {
        val (moved, undo) = base.moveItemToAisleWithUndo("l1", "Toothpaste", "produce", 10)
        val back = moved.undoMove(undo!!, 11)
        assertEquals("household", back.findItem("Toothpaste")!!.aisleId)
        assertTrue(back.corrections.isEmpty())
    }

    @Test
    fun undoKeepsWhatHappenedSince() {
        val (moved, undo) = base.moveItemToAisleWithUndo("l1", "Toothpaste", "produce", 10)
        // Another correction arrived meanwhile: only the move's own is taken back.
        val later = moved.moveItemToAisle("l1", "Peas", "produce", 12)
        val back = later.undoMove(undo!!, 13)
        assertEquals("household", back.findItem("Toothpaste")!!.aisleId)
        assertEquals(listOf(Correction("pea", "produce", 12)), back.corrections)
        // Moved again since: that later move stands.
        val again = moved.moveItemToAisle("l1", "Toothpaste", "frozen", 12)
        val stale = again.undoMove(undo, 13)
        assertEquals("frozen", stale.findItem("Toothpaste")!!.aisleId)
        assertEquals(listOf(Correction("toothpaste", "frozen", 12)), stale.corrections)
    }

    @Test
    fun aMoveToEverythingElseUndoesToo() {
        val s = base.copy(corrections = listOf(Correction("toothpaste", "household", 1)))
        val (moved, undo) = s.moveItemToAisleWithUndo("l1", "Toothpaste", null, 10)
        assertNull(moved.findItem("Toothpaste")!!.aisleId)
        assertTrue(moved.corrections.isEmpty())
        val back = moved.undoMove(undo!!, 11)
        assertEquals("household", back.findItem("Toothpaste")!!.aisleId)
        assertEquals(s.corrections, back.corrections)
    }

    @Test
    fun nothingToUndoWhenNothingMoved() {
        assertNull(base.moveItemToAisleWithUndo("l1", "Toothpaste", "nope", 10).second)
        assertNull(base.moveItemToAisleWithUndo("l1", "nobody", "produce", 10).second)
    }
}

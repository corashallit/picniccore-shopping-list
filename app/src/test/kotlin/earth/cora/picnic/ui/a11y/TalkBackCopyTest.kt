package earth.cora.picnic.ui.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 282 (docs/DESIGN_STICKER.md §15 "TalkBack copy", §16): what TalkBack is handed on every screen fixture, read from
 * the AccessibilityNodeInfo tree Android gives TalkBack (TalkBackTree), with TalkBack on.
 * - On every stop of every fixture and page: whatever can be acted on says what it is in plain words (no blank label, no
 *   id, no format leftovers, no glyph read as a symbol name); a check or a switch says its state; a decorative piece never
 *   speaks (its stickers, critters, doodles and sparkles have no node, DecorSemanticsTest).
 * - The §15 table, line by line, on the fixture it names: the row, the folded strip, the open tray's chevron, the LCD,
 *   the book, the add bar, a toast, the Next card, the stamps in the Trip book, the celebration, the receipt and the album
 *   pocket, and the tabs' whole names under a large font.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TalkBackCopyTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun talkBackOn() = TalkBackTree.screenReader(true)

    @After
    fun talkBackOff() = TalkBackTree.screenReader(false)

    @Test
    fun everyStopSpeaksPlainWordsDay1() = everyStop(Look.Day1)

    @Test
    fun everyStopSpeaksPlainWordsGlow2() = everyStop(Look.Glow2)

    private fun everyStop(look: Look) {
        val problems = LinkedHashSet<String>()
        var stops = 0
        ScreenWalker(compose).walk(A11yScreens.all, listOf(look)) {
            pages {
                val byId = unmergedNodes().associateBy { it.id }
                for (s in TalkBackTree.stops()) {
                    // A stop cut by a scroll's edge (a chip one pixel above a sheet's footer) has its words cut off with
                    // it; every stop is wholly on screen on some page, and is judged there.
                    if (!readable(s) || byId[s.id]?.let { wholly(it) } == false) continue
                    stops++
                    TalkBackRules.problems(s).forEach { problems += "$where: $s: $it" }
                }
            }
        }
        assertTrue("stops read: $stops", stops > 600)
        assertTrue("TalkBack copy problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** A stop of a readable size (a sliver at the screen's edge is read whole on another page). */
    private fun readable(s: Stop): Boolean = s.bounds.height() >= 24 * 3 && s.bounds.width() >= 24 * 3

    private fun Visit.stops(): List<Stop> = TalkBackTree.stops()

    private fun List<Stop>.one(label: String): Stop {
        val found = filter { it.label == label }
        assertTrue("one stop says \"$label\"; stops: ${map { it.label }}", found.isNotEmpty())
        return found.first()
    }

    private fun fixture(id: String): ScreenFixture = A11yScreens.all.single { it.id == id }

    /** §15's table, on the fixtures it describes (Appendix A, Day, font scale 1.0). */
    @Test
    fun copyMatchesTheTable() {
        val walker = ScreenWalker(compose)
        walker.visit(fixture("list/fresh"), Look.Day1) {
            val s = stops()
            val row = s.one("Strawberries, 1 litre basket")
            assertEquals("CheckBox", row.role)
            assertEquals("not checked", row.state)
            assertTrue(row.checkable && !row.checked)
            assertEquals(listOf("Check", "Delete", "Edit"), row.actions)
            assertEquals("Check", row.clickLabel)
        }
        walker.visit(fixture("list/mid-trip"), Look.Day1) {
            val s = stops()
            s.one("Saturday shop").let { assertTrue("the title is a heading", it.heading) }
            assertTrue("the sun is a sticker, no action", !s.one("Sun sticker").actionable)
            s.one("10 of 24 in the basket")
            assertEquals("Button", s.one("More options").role)
            val book = s.one("Trip sticker book, Sat 3 Oct: 10 of 24 stickers, 1 of 8 aisles stamped")
            assertEquals("Open trip book", book.clickLabel)
            val strip = s.one("Aisle 1, Produce: 2 left of 5")
            assertEquals("Button", strip.role)
            assertEquals("Expand", strip.clickLabel)
            assertEquals("Button", s.one("Collapse Baking").role)
            assertTrue("the open tray's tab is its heading", s.one("Aisle 4, Baking, store aisle 2").heading)
            val got = s.one("All-purpose flour, 2.5 kilogram bag")
            assertEquals("got it", got.state)
            assertTrue(got.checked)
            assertEquals(listOf("Uncheck", "Delete", "Edit"), got.actions)
            assertEquals("EditText", s.one("Write a note").role)
            assertEquals(listOf("Choose a photo"), s.one("Snap a photo of a list").actions)
            assertEquals("Button", s.one("Speak a voice note").role)
            assertEquals("Button", s.one("Send").role)
        }
        walker.visit(fixture("list/tear-off-toast"), Look.Day1) {
            val s = stops()
            s.one("McIntosh apples torn out")
            assertEquals("Button", s.one("Undo").role)
        }
        walker.visit(fixture("list/next-card"), Look.Day1) {
            assertNotNull(stops().one("Next: aisle 5, Breakfast"))
        }
        walker.visit(fixture("list/celebration-summary"), Look.Day1) {
            // One node for the whole scene: "Celebration. Double-tap to close" (§11.3).
            val s = stops()
            assertEquals("close", s.one("Celebration").clickLabel)
            assertEquals("nothing else on the scene is a stop: ${s.map { it.label }}", 1, s.size)
        }
        walker.visit(fixture("sheet/trip-book"), Look.Day1) {
            val s = stops()
            s.one("Produce: not yet")
            s.one("Deli & meat: stamped")
        }
        walker.visit(fixture("list/receipt"), Look.Day1) {
            val receipt = stops().one("Trip receipt: Saturday 3 October, Hillcrest, 24 stickers, 8 baskets, 41 minutes, 1 shiny")
            assertEquals("Button", receipt.role)
            assertEquals("save it to the sticker book", receipt.clickLabel)
        }
        walker.visit(fixture("album/default"), Look.Day1) {
            val s = mutableListOf<Stop>()
            pages { s += stops() }
            s.one("Strawberries, collected, rarity 1 of 3")
            s.one("Not collected yet")
        }
        // The tabs and strips read the aisle's whole name, however the tab lays it out (§15).
        walker.visit(fixture("list/default-aisles"), Look.Day2) {
            val s = mutableListOf<Stop>()
            pages { s += stops() }
            s.one("Aisle 7, Pasta, rice and international, store aisle 3: 1 left of 1")
            s.one("Aisle 11, Health, beauty and baby, store aisle 7: 1 left of 1")
        }
        walker.finish()
    }
}

/** The rules every TalkBack stop keeps (§15, §16). */
object TalkBackRules {
    /** Words that only a decorative piece would say: never a stop's whole label (§15 Decor). */
    private val decorWords = setOf(
        "bee", "ladybug", "ladybird", "ant", "ants", "sparkle", "sparkles", "washi", "washi tape", "tape", "doodle",
        "confetti", "petal", "petals", "critter", "cat", "nap cat", "star", "stars", "heart", "hearts", "glint", "bunting",
        "umbrella", "sticker", "decoration", "image", "icon",
    )

    private val glyphs = Regex("""[♡♥☆★✦♪]""")
    private val idLike = Regex("""\b[a-z]+_[a-z0-9_]+\b""")
    private val formatLeft = Regex("""%\d*\$?[sd]|\{\w*\}""")

    fun problems(s: Stop): List<String> = buildList {
        val label = s.label.trim()
        if (s.actionable && label.isEmpty()) add("can be acted on but says nothing")
        if (label.isNotEmpty()) {
            if (label.none { it.isLetterOrDigit() }) add("says no words")
            if (idLike.containsMatchIn(label)) add("reads an id")
            if (formatLeft.containsMatchIn(label) || label.contains("null")) add("reads a format leftover")
            if (glyphs.containsMatchIn(label)) add("reads a glyph as a symbol name")
            // A choice may name an option after a decor shape (My store's pattern "Hearts", "Stars", §6.7): it says its
            // state, so it is a control, not decor.
            if (label.lowercase() in decorWords && !s.checkable) add("decor speaks")
        }
        if (s.role == "CheckBox" || s.role == "Switch" || s.role == "RadioButton") {
            if (!s.checkable && s.state == null) add("a ${s.role} that never says its state")
        }
    }
}

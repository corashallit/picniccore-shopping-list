package earth.cora.picnic.ui.eggs

import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.theme.Families
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The Surprise lab's practice list (§6.9) as the list draws it, until the lab hosts WP5's List screen: the sticker book
 * for 1–24 stops and its pages (§7.4), the pinned plate with its lemon ring unbroken into the tray (R1, R6, item 313), tab
 * names that shrink 20 → 16 sp then ellipsize on one line (item 76), its words from strings (§16), TalkBack (§15), no
 * capture buttons, and the add flows' toast (egg 6's "★ shiny!").
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class PracticeListTest {
    @get:Rule
    val compose = createComposeRule()

    private val d = 3f // xxhdpi

    /** A list with one item in each of [stops] aisles, nothing checked yet. */
    private fun stopsFixture(stops: Int): AppState {
        val names = (1..stops).joinToString("\n") { "Shelf ${'A' + (it - 1) / 10}${(it - 1) % 10}" }
        val store = StoreProfile(name = PracticeFixture.STORE, aislesText = names)
        val base = AppState(lists = listOf(GroceryList(PracticeFixture.LIST_ID, "Saturday shop", PracticeFixture.WRITTEN)), currentListId = PracticeFixture.LIST_ID, store = store)
        return store.aisles().foldIndexed(base) { i, s, a ->
            s.addItems(PracticeFixture.LIST_ID, listOf(GroceryItem(id = "i$i", name = "Thing $i", aisleId = a.id, icon = "apple", iconFit = IconFit.EXACT, addedAt = PracticeFixture.WRITTEN + i)), PracticeFixture.WRITTEN + i).first
        }.setOpenAisle(PracticeFixture.LIST_ID, "")
    }

    @Test
    fun theBookLayoutFollowsSection7Point4() {
        // §7.4's worked examples.
        PracticeBookLayout.of(336f, 8, 24).let {
            assertEquals(24f, it.stampSize)
            assertEquals(117f, it.leftWidth)
            assertEquals(8, it.cols)
            assertEquals(24, it.perPage)
            assertEquals(1, it.pages)
        }
        assertEquals(2, PracticeBookLayout.of(336f, 8, 30).pages)
        assertEquals(10, PracticeBookLayout.of(387f, 8, 24).cols)
        PracticeBookLayout.of(336f, 14, 24).let {
            assertEquals(20f, it.stampSize)
            assertEquals(7, it.stampCols)
            assertEquals(170f, it.leftWidth)
            assertEquals(15, it.perPage)
        }
        PracticeBookLayout.of(336f, 20, 24).let {
            assertEquals(3, it.stampRows)
            assertEquals(102f, it.height)
            assertEquals(20, it.perPage)
        }
        PracticeBookLayout.of(336f, 1, 24).let {
            assertEquals(4, it.stampCols)
            assertEquals(24, it.perPage)
        }
        PracticeBookLayout.of(336f, 30, 30).let {
            assertEquals(24, it.circles)
            assertEquals("23 circles and a +7", 7, it.moreStops)
        }
    }

    /** Every stop has its stamp on the stamp page: 9 stops (the fixture after the ants' picnic things), 20 (three rows). */
    @Test
    fun nineStopsAllOnThePage() = stampsOnThePage(9)

    @Test
    fun twentyStopsOnThreeRows() = stampsOnThePage(20)

    private fun stampsOnThePage(stops: Int) {
        val r = EggRig(compose, fixture = stopsFixture(stops)).start()
        val book = compose.onNodeWithTag(PracticeTags.BOOK).fetchSemanticsNode().boundsInRoot
        val layout = PracticeBookLayout.of(book.width / d, stops, stops)
        assertEquals("$stops stops: the book's height", layout.height, book.height / d, 1.5f)
        // The stamp page starts inside the book's 3 dp white border.
        val left = Rect(book.left + 3 * d, book.top + 3 * d, book.left + (3 + layout.leftWidth) * d, book.bottom - 3 * d)
        val stamps = r.model.grouped().sections.map { r.anchor(AnchorRegistry.stampKey(it.id)) }
        assertEquals(stops, stamps.size)
        stamps.forEach { st -> assertTrue("$stops stops: $st inside the stamp page $left", st.deflate(1f).let { left.contains(it.topLeft) && left.contains(it.bottomRight) }) }
        assertEquals("no two stamps share a spot", stops, stamps.map { it.center.x.toInt() to it.center.y.toInt() }.distinct().size)
    }

    /** 27 spots at 24 per page: the page the next sticker goes on shows, with its "1/2" page number. */
    @Test
    fun morePagesShowThePageNumber() {
        val r = EggRig(compose).start()
        r.fire(17)
        r.advance(300)
        assertEquals(27, r.list.items.size)
        compose.onNodeWithTag(PracticeTags.PAGE_LCD, useUnmergedTree = true).assertExists()
        assertEquals("1/2", compose.onNodeWithTag(PracticeTags.PAGE_LCD, useUnmergedTree = true).fetchSemanticsNode().let { n ->
            n.children.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text } ?: "1/2"
        })
        // The book is one TalkBack node with its words.
        compose.onNodeWithContentDescription("Trip sticker book, Sat 3 Oct: 10 of 27 stickers, 1 of 9 aisles stamped").assertExists()
    }

    /**
     * R1, R6, item 313: scrolled so the open tray's rows run under it, the header pins as an opaque plate at the top of the
     * list, and the lemon ring runs unbroken from the plate down the tray.
     */
    @Test
    fun thePinnedPlateKeepsItsRingUnbroken() {
        val ls = LazyListState()
        val r = EggRig(compose)
        r.start { PracticeList(r.model, feedback = r.feedback, listState = ls) }
        // Produce (5 rows) is the open tray; scroll its rows to the top of the list.
        r.act { r.model.open("produce") }
        r.advance(600)
        compose.onNodeWithTag(PracticeTags.LIST).performScrollToIndex(1)
        r.advance(400)
        val list = compose.onNodeWithTag(PracticeTags.LIST).fetchSemanticsNode().boundsInRoot
        val plate = compose.onNodeWithTag(PracticeTags.header("produce"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("the plate pins at the top of the list", list.top, plate.top, 2f)
        val firstRow = compose.onNodeWithTag(PracticeTags.row("strawberries"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the first row runs under the plate", firstRow.top < plate.bottom)
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        // The ring's left side: x = 8 dp gutter + 1.5 dp into the 3 dp ring, from the plate's middle down into the rows.
        val x = (plate.left + 9.5f * d).toInt()
        val lemon = Families.lemon.mid.toArgb()
        val from = (plate.top + plate.height / 2).toInt()
        val to = (plate.bottom + 60 * d).toInt()
        for (y in from until to) assertTrue("the ring at y=$y is lemon (${Integer.toHexString(shot.getPixel(x, y))})", near(shot.getPixel(x, y), lemon))
        // Opaque: near the plate's bottom edge, over the row that runs under it, the band's pastel shows (the same as
        // in its middle), never the row's white panel or its words.
        val px = (plate.left + 190 * d).toInt()
        val band = shot.getPixel(px, (plate.top + plate.height / 2).toInt())
        for (y in (plate.bottom - 8 * d).toInt() until (plate.bottom - 2 * d).toInt()) {
            assertTrue("the plate covers the row at y=$y (${Integer.toHexString(shot.getPixel(px, y))})", near(shot.getPixel(px, y), band))
        }
    }

    /** Item 76: at 200 % a long tab name stays on one line, shrunk to 16 sp and ellipsized, clear of the basket. */
    @Test
    fun tabNamesStayOnOneLine() {
        val r = EggRig(compose, fontScale = 2f).start()
        for (s in r.model.grouped().sections) {
            if (s.id == r.model.grouped().openSectionId) continue
            val tab = r.anchors.bounds(EggAnchors.TEXT_PREFIX + "tab:" + s.id) ?: continue
            // One line of DynaPuff at 16–20 sp × 2.0 is at most 24 sp × 2 = 48 sp tall (144 px); two lines would be twice.
            assertTrue("${s.id}: one line (${tab.height} px)", tab.height <= 48 * d * 1.05f)
            val basket = r.anchor(AnchorRegistry.basketKey(s.id))
            assertTrue("${s.id}: the name ends before the basket", tab.right <= r.anchor(EggAnchors.strip(s.id)).right - 124 * d)
            assertTrue(basket.width > 0)
        }
    }

    /** §16 and §15: the words come from strings; strips, rows and the fold button speak. */
    @Test
    fun itsWordsAndTalkBack() {
        EggRig(compose).start()
        compose.onNodeWithText("Hillcrest Market · 8 stops", substring = true).assertExists()
        compose.onNodeWithContentDescription("Aisle 1, Produce: 2 left of 5. Expand").assertExists()
        compose.onNodeWithContentDescription("Aisle 3, Deli & meat: 2 of 2 in the basket. Expand").assertExists()
        compose.onNodeWithContentDescription("Collapse Baking").assertExists()
        val flour = compose.onNodeWithTag(PracticeTags.row("flour")).fetchSemanticsNode().config
        assertEquals(ToggleableState.On, flour[SemanticsProperties.ToggleableState])
        assertEquals("got it", flour[SemanticsProperties.StateDescription])
        assertTrue(flour[SemanticsProperties.ContentDescription].single().startsWith("All-purpose flour, 2.5 kilogram"))
        assertEquals("Uncheck", flour[SemanticsActions.OnClick].label)
        val powder = compose.onNodeWithTag(PracticeTags.row("baking-powder")).fetchSemanticsNode().config
        assertEquals(ToggleableState.Off, powder[SemanticsProperties.ToggleableState])
        assertEquals("not checked", powder[SemanticsProperties.StateDescription])
    }

    /** The practice copy has no camera or microphone: Snap and Speak are off; the add shows the add flows' toast. */
    @Test
    fun noCaptureAndTheAddToast() {
        val r = EggRig(compose).start()
        compose.onNodeWithContentDescription("Snap a photo of a list").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Speak a voice note").assertIsNotEnabled()
        r.fire(6)
        r.advance(400)
        // The hearts and stars in copy are drawn as vectors; the words around them are the add flows' (§16).
        compose.onNodeWithText("Added Maple syrup to Breakfast", substring = true).assertExists()
        compose.onNodeWithText("shiny!", substring = true).assertExists()
    }

    private fun near(a: Int, b: Int, tolerance: Int = 24): Boolean =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= tolerance &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= tolerance &&
            abs((a and 0xFF) - (b and 0xFF)) <= tolerance

    @Suppress("unused")
    private fun Bitmap.at(x: Float, y: Float): Int = getPixel(x.toInt(), y.toInt())
}

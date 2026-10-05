package earth.cora.picnic.ui.list

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.BookLayout
import earth.cora.picnic.ui.theme.BookTags
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.readHold
import earth.cora.picnic.ui.theme.stampCentre
import earth.cora.picnic.ui.theme.stampTop
import earth.cora.picnic.ui.theme.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Touching the trip sticker book (docs/DESIGN_STICKER.md §7.4, §7.7; items 156, 157): the right page opens the Trip
 * book sheet and swipes turn its pages (back to the shown page after 6 s idle); the left page boings the nearest stamp,
 * clips its name tag onto the right page for its reading hold and feeds egg 10.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class BookInteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Back, as the system's Back closes a sheet. */
    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private val dp get() = compose.density.density

    /** Item 156: 32 spots over two pages (24 a page at 360 dp, 8 stops), 26 checked: the book shows page 2. */
    @Test
    fun openSheetAndPaging() {
        val rig = ListRig(ListFixture.stops(stops = 8, perAisle = 4, checked = 26))
        val probe = RecordingProbe()
        compose.show(rig, probe)
        assertTrue("page 2 of 2 is shown (the next spot, 26, is on it): ${probe.keys("pageLcd:")}", "pageLcd:2/2" in probe.bounds)
        assertTrue(rig.engine.anchors.isLaidOut("spot:26"))
        assertTrue(!rig.engine.anchors.isLaidOut("spot:0"))

        // A swipe to the right turns back to page 1; after 6 s without a touch the book returns to page 2.
        compose.mainClock.autoAdvance = false
        val right = probe["bookRight"]
        val book = compose.boundsOf(BookTags.BOOK)
        compose.onNodeWithTag(BookTags.BOOK).performTouchInput {
            val y = right.center.y - book.top
            down(Offset(right.left - book.left + 30 * dp, y))
            repeat(5) { moveBy(Offset(16 * dp, 0f)) }
            up()
        }
        compose.advanceMs(300)
        assertTrue("turned to page 1: ${probe.keys("pageLcd:")}", "pageLcd:1/2" in probe.bounds)
        assertTrue(rig.engine.anchors.isLaidOut("spot:0"))
        compose.advanceMs(5400)
        assertTrue("still page 1 just before 6 s", "pageLcd:1/2" in probe.bounds)
        assertTrue("back to page 2 after 6 s idle", compose.framesUntil(1000) { "pageLcd:2/2" in probe.bounds })
        compose.mainClock.autoAdvance = true

        // A tap on the right page opens the Trip book sheet.
        compose.onNodeWithTag(BookTags.BOOK).performTouchInput { click(Offset(right.center.x - book.left, right.center.y - book.top)) }
        compose.waitForIdle()
        compose.onNodeWithTag(TripBookTags.SHEET).assertExists()
    }

    /**
     * Items 156 and 163 (§6.6): the Trip book sheet opens on the page the book shows: the next free spot's (26, page 2),
     * or the page she swiped to (page 1).
     */
    @Test
    fun sheetOpensOnTheBooksPage() {
        val rig = ListRig(ListFixture.stops(stops = 8, perAisle = 4, checked = 26))
        val probe = RecordingProbe()
        compose.show(rig, probe)
        val right = probe["bookRight"]
        val book = compose.boundsOf(BookTags.BOOK)
        fun openSheet() {
            compose.onNodeWithTag(BookTags.BOOK).performTouchInput { click(Offset(right.center.x - book.left, right.center.y - book.top)) }
            if (compose.mainClock.autoAdvance) compose.waitForIdle() else compose.advanceMs(600)
            compose.onNodeWithTag(TripBookTags.SHEET).assertExists()
        }
        openSheet()
        assertTrue("the sheet shows spot 25 (page 2)", compose.exists(TripBookTags.sticker(25), unmerged = true))
        assertTrue("not spot 0", !compose.exists(TripBookTags.sticker(0), unmerged = true))
        compose.onNodeWithContentDescription("Page 2 of 2", useUnmergedTree = true).assertExists()
        back()
        // She swipes the book back to page 1, then opens it: the sheet opens there too.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(BookTags.BOOK).performTouchInput {
            val y = right.center.y - book.top
            down(Offset(right.left - book.left + 30 * dp, y))
            repeat(5) { moveBy(Offset(16 * dp, 0f)) }
            up()
        }
        compose.advanceMs(300)
        assertTrue("turned to page 1", "pageLcd:1/2" in probe.bounds)
        // (The clock stays held: the book goes back to its own page after 6 s idle.)
        openSheet()
        assertTrue("the sheet shows spot 0 (page 1)", compose.exists(TripBookTags.sticker(0), unmerged = true))
        compose.onNodeWithContentDescription("Page 1 of 2", useUnmergedTree = true).assertExists()
        compose.mainClock.autoAdvance = true
    }

    /** Items 156 and 161 (§7.4, §7.7): collapsed to its 40 dp strip, a tap anywhere on the book opens the Trip book sheet. */
    @Test
    @Config(qualifiers = "w360dp-h560dp-xxhdpi")
    fun collapsedTapOpensSheet() {
        val rig = ListRig()
        compose.show(rig)
        assertEquals("collapsed", 40f, compose.boundsOf(BookTags.BOOK).height / dp, 1f)
        val strip = compose.boundsOf(BookTags.COLLAPSED, unmerged = true)
        val book = compose.boundsOf(BookTags.BOOK)
        for (fx in listOf(0.1f, 0.5f, 0.9f)) {
            compose.onNodeWithTag(BookTags.BOOK).performTouchInput { click(Offset(strip.left - book.left + strip.width * fx, strip.center.y - book.top)) }
            compose.waitForIdle()
            compose.onNodeWithTag(TripBookTags.SHEET).assertExists()
            assertTrue("the sheet is up ($fx)", compose.onAllNodesWithText("aisles stamped", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            back()
        }
    }

    /**
     * Item 157: a tap near stamp 4 (Baking, store aisle 2) boings it and clips "4 · Baking · 1 left" onto the right page,
     * inside the book, for max(2600 ms, readHold) at least; the stamp tap feeds egg 10 with its place on the page.
     */
    @Test
    fun stampTagAndEggFeed() {
        val rig = ListRig()
        val probe = RecordingProbe()
        compose.show(rig, probe)
        val book = compose.boundsOf(BookTags.BOOK)
        val leftPage = probe["bookLeft"]
        val rightPage = probe["bookRight"]
        val layout = BookLayout.of(book.width / dp, 8, 24)
        val (cx, cy) = stampCentre(3, layout, top = 19f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(BookTags.BOOK).performTouchInput { click(Offset(leftPage.left - book.left + cx * dp, leftPage.top - book.top + cy * dp)) }
        compose.advanceMs(200)
        val text = "4 · Baking · 1 left"
        val key = "nameTag:$text"
        assertTrue("the tag shows: ${probe.keys("nameTag:")}", key in probe.bounds)
        val tag = probe[key]
        assertTrue("on the right page ($tag in $rightPage)", tag.left >= rightPage.left - 0.5f && tag.right <= rightPage.right + 0.5f)
        assertTrue("inside the book ($tag in $book)", tag.top >= book.top && tag.bottom <= book.bottom)
        assertEquals("egg 10 hears the stamp's place on the page", listOf(3), rig.surprises.stampTaps)
        val hold = maxOf(PicnicMotion.floorCard, readHold(words(text), PicnicMotion.floorCard))
        compose.advanceMs(hold - 300)
        assertTrue("the tag holds at least ${hold} ms", key in probe.bounds)
        assertTrue("then it goes", compose.framesUntil(800) { key !in probe.bounds })
        compose.mainClock.autoAdvance = true
    }

    /**
     * Item 157 at large text, with the longest default aisle name ("7 · Pasta, rice & international · 1 left", stamp 7 of
     * the 14 default aisles): at 1.3 the tag stays on the right page at the page's art scale and wraps; at 2.0 it joins
     * the date's caption row above the book and grows with the text, widening over the caption when the column after the
     * date is too short for it. Never a lost word, never past the book's bottom (so never over the list).
     */
    @Test
    fun stampTagAtLargeText130() = longTagFits(1.3f)

    @Test
    fun stampTagAtLargeText200() = longTagFits(2f)

    private fun longTagFits(fontScale: Float) {
        val rig = ListRig(ListFixture.everyDefaultAisle())
        val probe = RecordingProbe()
        compose.show(rig, probe, fontScale = fontScale)
        val book = compose.boundsOf(BookTags.BOOK)
        val leftPage = probe["bookLeft"]
        val stops = rig.vm.state.value.book!!.stamps.size
        val layout = BookLayout.of(book.width / dp, stops, rig.vm.state.value.total)
        val stamp = rig.vm.state.value.book!!.stamps.indexOfFirst { it.displayName == "Pasta, rice & international" }
        assertEquals("stamp 7", 6, stamp)
        val (cx, cy) = stampCentre(stamp, layout, stampTop(fontScale <= 1.3f, layout))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(BookTags.BOOK).performTouchInput { click(Offset(leftPage.left - book.left + cx * dp, leftPage.top - book.top + cy * dp)) }
        compose.advanceMs(300)
        val keys = probe.keys("nameTag:")
        assertEquals("the tag for stamp 7 shows: $keys", listOf("nameTag:7 · Pasta, rice & international · 1 left"), keys)
        assertTrue("no word lost (${probe.keys("nameTagOverflow")})", probe.keys("nameTagOverflow").isEmpty())
        val tag = probe[keys.single()]
        assertTrue("never below the book ($tag, book $book)", tag.bottom <= book.bottom + 0.5f)
        if (fontScale <= 1.3f) {
            val right = probe["bookRight"]
            assertTrue("on the right page at 1.3 ($tag in $right)", tag.left >= right.left - 0.5f && tag.right <= right.right + 0.5f && tag.top >= book.top)
        } else {
            val top = compose.boundsOf(ListTags.TOP_STACK)
            assertTrue("in the caption row and over the book at 2.0 ($tag, stack $top)", tag.top >= top.top && tag.right <= book.right + 0.5f)
        }
        compose.mainClock.autoAdvance = true
    }
}

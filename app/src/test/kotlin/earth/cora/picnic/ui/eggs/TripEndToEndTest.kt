package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import earth.cora.picnic.ui.theme.TOAST_TAG
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 274 end to end, on WP5's real List screen as the Surprise lab shows it (WP6's chains, the real overlays), by her
 * own taps: go round the store in walking order (a tap on a folded strip opens it, a tap on each row checks it off) →
 * the finale → a tap closes it → hold the LCD → the receipt prints → tap it → the slip is in the book → the overflow
 * menu's New trip → "Clear what I got" → the album's book carries the receipt. WholeTripInTheAppTest runs the same trip
 * on MainActivity, through her album.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class TripEndToEndTest {
    @get:Rule
    val compose = createComposeRule()

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Every node whose test tag starts with [prefix] (or is [prefix]), in root px. */
    private fun boxes(prefix: String): List<Rect> {
        val out = ArrayList<Rect>()
        fun walk(n: SemanticsNode) {
            val t = n.config.getOrNull(SemanticsProperties.TestTag)
            if (t != null && t.startsWith(prefix)) out += n.boundsInRoot
            n.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out
    }

    /** The screen's own coroutine scope: her drags run in it, as the list's gestures do. */
    private var screenScope: kotlinx.coroutines.CoroutineScope? = null

    /**
     * Her thumb moving the list by [delta] px: a scroll at user-input priority, so it stops a scroll the list is making
     * (the next stop coming into view) as her drag would, rather than adding to it. One frame lets it run.
     */
    private fun EggRig.drag(list: LazyListState, delta: Float) {
        act {
            screenScope!!.launch { list.scroll(androidx.compose.foundation.MutatePriority.UserInput) { scrollBy(delta) } }
        }
    }

    /**
     * Brings [tag]'s node into view just below the pinned plate, as her thumb would scroll the list (by the list's own
     * state: no animation waits on the held clock), and waits (bounded) while a toast or the Next card lies over it.
     */
    private fun EggRig.bringIntoView(list: LazyListState, tag: String) {
        // The next stop's rows show once the aisle-done chain has played (a second or two): look for them meanwhile.
        var tries = 0
        while (!exists(tag) && tries++ < 30) {
            for (i in 0 until list.layoutInfo.totalItemsCount) {
                act { list.requestScrollToItem(i) }
                if (exists(tag)) break
            }
            if (!exists(tag)) advance(150)
        }
        assertTrue("$tag is in the list", exists(tag))
        repeat(3) {
            val top = compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.LIST).fetchSemanticsNode().boundsInRoot.top
            // A sticky plate pinned at the list's top covers what scrolls under it.
            val pinned = boxes("picnic_tray_header_").filter { it.top <= top + 2f && it.bottom > top }.maxOfOrNull { it.bottom } ?: top
            val delta = bounds(tag).top - (pinned + 16 * 3f)
            if (kotlin.math.abs(delta) < 3f) return@repeat
            drag(list, delta)
        }
        val bar = anchor(AnchorRegistry.ADD_BAR).top
        assertTrue("$tag above the add bar (${bounds(tag)}, bar $bar)", bounds(tag).center.y < bar)
        var waited = 0
        while (waited++ < 50) {
            val at = bounds(tag).center
            if ((boxes(TOAST_TAG) + boxes(NEXT_CARD_TAG)).none { it.contains(at) }) break
            advance(200)
        }
    }

    @Test
    fun checkEverythingToTheReceiptInTheAlbum() {
        val scroll = LazyListState()
        val r = EggRig(compose, fixture = PracticeFixture.unchecked())
        r.start {
            screenScope = androidx.compose.runtime.rememberCoroutineScope()
            PracticeListScreen(r.sandbox, listState = scroll)
        }
        assertNull(r.list.trip)
        assertEquals(24, r.list.items.size)
        // Go round the store as she would: in walking order, open each stop with a tap on its strip if it is folded,
        // and tick each row with a tap, a third of a second apart.
        var taps = 0
        var guard = 0
        while (r.list.items.any { !it.checked } && guard++ < 80) {
            val g = r.model.grouped()
            val order = g.sections.map { it.id }
            val next = r.list.items.filter { !it.checked }.minBy { order.indexOf(it.aisleId) * 1_000 + r.list.items.indexOf(it) }
            val aisle = next.aisleId!!
            if (r.model.grouped().openSectionId != aisle) {
                r.bringIntoView(scroll, earth.cora.picnic.ui.list.ListTags.strip(aisle))
                compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.strip(aisle), useUnmergedTree = true).performClick()
                r.advance(700)
                assertEquals("the tap opened $aisle", aisle, r.model.grouped().openSectionId)
            }
            r.bringIntoView(scroll, earth.cora.picnic.ui.list.ListTags.row(next.id))
            val at = bounds(earth.cora.picnic.ui.list.ListTags.row(next.id))
            compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.row(next.id), useUnmergedTree = true).performClick()
            r.frame()
            assertTrue(
                "the tap checked ${next.name} (row $at; toasts ${boxes(TOAST_TAG)}; card ${boxes(NEXT_CARD_TAG)}; plates ${boxes("picnic_tray_header_")})",
                r.list.items.single { it.id == next.id }.checked,
            )
            taps++
            r.advance(320)
        }
        assertEquals("one tap per item", 24, taps)
        assertTrue(r.list.items.all { it.checked })
        assertNotNull(r.list.trip!!.completedAt)
        // The finale plays; she closes it with a tap.
        guard = 0
        while (r.model.motion.current.value.celebration?.plan == null && guard++ < 3_000) r.frame()
        assertNotNull("the celebration plays", r.model.motion.current.value.celebration)
        r.advance(2_000)
        compose.onNodeWithTag(CELEBRATION_TAG).performClick()
        guard = 0
        while (r.model.motion.current.value.celebration != null && guard++ < 1_000) r.frame()
        assertNull("closed", r.model.motion.current.value.celebration)
        assertNotNull("the finale was shown", r.list.trip!!.finaleShownAt)
        // Hold the LCD: the receipt prints (never the pet).
        r.advance(500)
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        val printed = r.list.trip!!.receipt
        assertNotNull(printed)
        assertEquals(24, printed!!.stickers)
        assertEquals(8, printed.baskets)
        assertTrue(!r.list.trip!!.pet)
        r.advance(2_100)
        // Tap it: into the book, the slip at the fore-edge.
        compose.onNodeWithTag(EggTags.RECEIPT).performClick()
        r.advance(900)
        assertTrue(r.list.trip!!.receiptSaved)
        compose.onNodeWithTag(earth.cora.picnic.ui.theme.BookTags.SLIP, useUnmergedTree = true).assertExists()
        // New trip, from the overflow menu's sheet: the book is filed in the album with its receipt; the list starts over.
        r.advance(4_500)
        compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.MORE).performClick()
        r.advance(300)
        compose.onNodeWithText("New trip").performClick()
        r.advance(600)
        compose.onNodeWithText("Clear what I got").performClick()
        r.advance(600)
        val album = runBlocking { r.sandbox.album.current() }
        val book = album.books.first()
        assertEquals(printed, book.receipt)
        assertTrue(book.perfect)
        assertEquals(24, book.stickers.size)
        assertNull(r.state.list(PracticeFixture.LIST_ID)!!.trip)
    }
}

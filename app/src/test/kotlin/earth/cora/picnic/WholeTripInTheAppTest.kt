package earth.cora.picnic

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.ui.eggs.EggTags
import earth.cora.picnic.ui.list.ListTags
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import earth.cora.picnic.ui.theme.BookTags
import earth.cora.picnic.ui.theme.TOAST_TAG
import earth.cora.picnic.core.store.file
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A whole trip in the real app (docs/DESIGN_STICKER.md §6.1, §7, §10, §11, §13; items 274 and 314 end to end), on
 * MainActivity with the real container: the JSON store, DataStore settings, WP6's chains, WP9's engine and overlays, and
 * no key (so no network). She has shopped at Hillcrest Market before (Appendix A's store, and the aisles she once moved
 * these things to, so her typed list files itself, §6.2), and:
 * 1. types her list into the add bar from the empty state and sends it;
 * 2. goes round the store aisle by aisle in walking order, ticking each row with a tap; the sticker book fills a spot per
 *    tick, and after each finished aisle but the last the Next card says where to go;
 * 3. sees the celebration, which closes by itself behind its progress bar (§0.4 Q7) and leaves the book PERFECT;
 * 4. holds the LCD: the trip is complete, so the receipt prints (D21), never the pet; a tap tucks it into the book;
 * 5. starts a New trip from the overflow menu ("Clear what I got"): the book is filed in her album with the receipt;
 * 6. opens her Sticker album: the filed book's row holds the receipt (item 274 end to end).
 * The clock is held and stepped a frame at a time; every wait is bounded.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WholeTripInTheAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app: PicnicApp get() = compose.activity.application as PicnicApp

    private val state: AppState get() = runBlocking { app.container.store.current() }

    /** One frame on the held clock (the store's writes land on the main looper in the idles around it). */
    private fun frame() {
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
    }

    /** [ms] of frames. */
    private fun advance(ms: Long) {
        val until = compose.mainClock.currentTime + ms
        var guard = 0
        while (compose.mainClock.currentTime < until && guard++ < 20_000) frame()
    }

    /** Frames until [done] holds, at most [maxMs] of frame time; fails with [what] if it never does. */
    private fun until(what: String, maxMs: Long = 30_000, done: () -> Boolean) {
        val until = compose.mainClock.currentTime + maxMs
        while (!done()) {
            if (compose.mainClock.currentTime >= until) {
                val tree = runCatching { compose.onRoot(useUnmergedTree = false).printToString() }.getOrDefault("?")
                throw AssertionError("timed out waiting for $what\n$tree")
            }
            frame()
        }
    }

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The row whose spoken label starts with [name] ("Strawberries"), or null while it is not on screen. */
    private fun row(name: String): SemanticsNodeInteraction? {
        val nodes = compose.onAllNodes(hasContentDescription(name, substring = true)).fetchSemanticsNodes()
        val node = nodes.firstOrNull { n ->
            n.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Checkbox &&
                n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.startsWith(name) == true
        } ?: return null
        return compose.onNode(androidx.compose.ui.test.SemanticsMatcher("node ${node.id}") { it.id == node.id })
    }

    /** Every node tagged [tag]. */
    private fun exactly(tag: String): List<SemanticsNode> = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    /** Every node whose test tag starts with [prefix]. */
    private fun tagged(prefix: String): List<SemanticsNode> {
        val out = ArrayList<SemanticsNode>()
        fun walk(n: SemanticsNode) {
            if (n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true) out += n
            n.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out
    }

    @Test
    fun aWholeTripFromTheTypedListToTheReceipt() {
        // The list starts empty (§4.21).
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("A brand-new sticker sheet!").fetchSemanticsNodes().isNotEmpty() }
        // Her store and the aisles she has moved these things to before (the offline parser files typed lines by them).
        val aisles = Aisles.parse(AISLES)
        fun aisleId(name: String) = aisles.first { it.name == name }.id
        val home = mapOf(
            "Strawberries" to aisleId("Produce"), "Bananas" to aisleId("Produce"),
            "Bagels" to aisleId("Bakery"),
            "Milk" to aisleId("Dairy & eggs"), "Eggs" to aisleId("Dairy & eggs"),
        )
        runBlocking {
            app.container.store.update { s ->
                s.copy(
                    store = StoreProfile(name = "Hillcrest Market", aislesText = AISLES),
                    corrections = home.map { (name, aisle) -> Correction(normalizeName(name), aisle, 1L) },
                )
            }
        }

        // 1. She types her list and sends it.
        compose.onNodeWithText("Start my list").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("strawberries, bananas, bagels, milk, eggs")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Send").performClick()
        until("the five rows to be on the list") { state.currentList().items.size == 5 }
        val items = state.currentList().items
        assertEquals(listOf("Strawberries", "Bananas", "Bagels", "Milk", "Eggs"), items.map { it.name })
        assertEquals("each line filed in her aisle", home.values.toList(), items.map { it.aisleId })
        // The first stop opens by itself: Produce's rows are on screen, the book has an empty spot per item.
        until("Produce's rows") { row("Strawberries") != null }
        until("the sticker book") { exists(BookTags.BOOK) }
        assertTrue("no twin before the first tick", tagged("picnic_book_twin_").isEmpty())

        // 2. Round the store, aisle by aisle, in walking order.
        val walk = listOf(listOf("Strawberries", "Bananas"), listOf("Bagels"), listOf("Milk", "Eggs"))
        var ticked = 0
        var cards = 0
        walk.forEachIndexed { stop, names ->
            for (name in names) {
                until("$name's row, clear of the Next card and any note", maxMs = 12_000) {
                    val r = row(name) ?: return@until false
                    val at = r.fetchSemanticsNode().boundsInRoot.center
                    (exactly(NEXT_CARD_TAG) + exactly(TOAST_TAG)).none { it.boundsInRoot.contains(at) }
                }
                row(name)!!.performClick()
                until("$name to be checked", maxMs = 3_000) { state.currentList().items.single { it.name == name }.checked }
                ticked++
                // The twin appears in the book a beat after the sticker lands in its basket (§7.6, R5).
                until("the book's twin for $name", maxMs = 3_000) { tagged("picnic_book_twin_").size == ticked }
                advance(320)
            }
            if (stop < walk.lastIndex) {
                // The aisle is done: the Next card says where to go (§10), and the next stop opens behind it.
                until("the Next card after stop ${stop + 1}", maxMs = 8_000) { exists(NEXT_CARD_TAG) }
                cards++
                until("the Next card to finish its hold", maxMs = 20_000) { !exists(NEXT_CARD_TAG) }
            }
        }
        assertEquals("a Next card between the stops", walk.size - 1, cards)
        val list = state.currentList()
        assertTrue(list.items.all { it.checked })
        assertNotNull("the trip is complete", list.trip?.completedAt)

        // 3. The celebration plays and closes by itself behind its progress bar (§0.4 Q7, §11.2); the book turns PERFECT.
        until("the celebration", maxMs = 8_000) { exists(CELEBRATION_TAG) }
        until("the summary's progress bar", maxMs = 40_000) {
            compose.onAllNodesWithText("Closing… tap to keep it", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        until("the celebration to close by itself", maxMs = 20_000) { !exists(CELEBRATION_TAG) }
        until("the finale to be marked as shown", maxMs = 3_000) { state.currentList().trip?.finaleShownAt != null }

        // 4. She holds the LCD: a complete trip prints its receipt (D21), never the pet.
        advance(600)
        val lcd = compose.onNode(hasContentDescription("5 of 5 in the basket"))
        lcd.assertExistsAndHasHoldAction()
        lcd.performTouchInput { down(center) }
        advance(app.container.timeouts.holdThresholdMs + 80)
        lcd.performTouchInput { up() }
        until("the receipt to print", maxMs = 3_000) { state.currentList().trip?.receipt != null }
        assertFalse("the pet never hatches on a complete trip", state.currentList().trip!!.pet)
        until("the printed receipt on screen", maxMs = 4_000) { exists(EggTags.RECEIPT) }
        advance(2_200)
        compose.onNodeWithTag(EggTags.RECEIPT).performClick()
        until("the receipt tucked into the book", maxMs = 3_000) { state.currentList().trip?.receiptSaved == true }
        val printed = state.currentList().trip!!.receipt!!
        until("the slip at the book's fore-edge", maxMs = 3_000) { exists(BookTags.SLIP) }

        // 5. New trip, from the overflow menu's sheet: "Clear what I got" files the book in her album, with its receipt.
        advance(4_500)
        compose.onNodeWithTag(ListTags.MORE).performClick()
        advance(400)
        compose.onNodeWithText("New trip").performClick()
        until("the New trip sheet", maxMs = 3_000) { compose.onAllNodesWithText("Clear what I got").fetchSemanticsNodes().isNotEmpty() }
        advance(400)
        compose.onNodeWithText("Clear what I got").performClick()
        until("the book filed and the list cleared", maxMs = 5_000) { state.currentList().trip == null && state.currentList().items.isEmpty() }
        val newest = runBlocking { app.container.album.current() }.books.maxBy { it.closedAt }
        assertEquals("the album's newest book carries the receipt", printed, newest.receipt)
        assertTrue(newest.perfect)

        // 6. Her Sticker album: the book's row says it holds the receipt.
        advance(4_500)
        compose.onNodeWithTag(ListTags.MORE).performClick()
        advance(400)
        compose.onNodeWithText("Sticker album").performClick()
        // Past the pocket page: the link above the tabs goes down to the trip books.
        until("the album's link to its trip books", maxMs = 5_000) { exists(earth.cora.picnic.ui.album.AlbumTags.JUMP) }
        advance(600)
        // The link is the button at the row's right end ("1 trip sticker book").
        compose.onNodeWithText("1 trip sticker book").performClick()
        until("the album with its trip book", maxMs = 5_000) {
            compose.onAllNodesWithText("receipt", substring = true).fetchSemanticsNodes().any { n ->
                n.config.getOrNull(SemanticsProperties.Text)?.any { "perfect · receipt" in it.text } == true
            }
        }
        compose.mainClock.autoAdvance = true
    }

    /**
     * Egg 14's back pocket, end to end (§12.2): ten finished trips in her album open the back pocket; the toast "Your
     * album's back pocket opened! · Look" shows on her list, and Look opens the Sticker album at the back pocket, its
     * corner curling open over "sticker sheet 2 of 2", on screen.
     */
    @Test
    fun theBackPocketsLookOpensTheAlbumThere() {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("A brand-new sticker sheet!").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.autoAdvance = false
        val day = 86_400_000L
        runBlocking {
            app.container.album.update { a ->
                (1..10).fold(a) { acc, n ->
                    val at = 1_759_000_000_000L + n * day
                    acc.file(
                        earth.cora.picnic.core.store.TripBook(
                            id = "t-$n", listTitle = "Saturday shop", storeName = "Hillcrest Market", startedAt = at - 3_600_000,
                            completedAt = at - 60_000, closedAt = at, total = 2, minutes = 30,
                            stickers = (0 until 2).map { earth.cora.picnic.core.store.BookSticker(it, "Apple $it", earth.cora.picnic.core.model.IconRef("apple"), earth.cora.picnic.core.model.Rarity.NONE, at - 60_000) },
                            stamps = emptyList(), perfect = true, receipt = null,
                        ),
                    )
                }
            }
        }
        until("the back pocket's toast", maxMs = 10_000) { compose.onAllNodesWithText("Look").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your album’s back pocket opened!", substring = true).assertExists()
        compose.onNodeWithText("Look").performClick()
        until("the album at its back pocket, on screen", maxMs = 10_000) {
            val nodes = compose.onAllNodesWithText("sticker sheet 2 of 2").fetchSemanticsNodes()
            val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
            nodes.any { it.boundsInRoot.top >= screen.top && it.boundsInRoot.bottom <= screen.bottom }
        }
        advance(1_200)
        compose.onNodeWithText("sticker sheet 2 of 2").assertIsDisplayed()
        compose.onNodeWithTag(earth.cora.picnic.ui.album.BACK_POCKET_TAG).assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    /** The LCD is the hold target with the receipt's TalkBack action while the trip is complete (§13.1, §15). */
    private fun SemanticsNodeInteraction.assertExistsAndHasHoldAction() {
        val node = fetchSemanticsNode()
        val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertTrue("the LCD offers \"Print the trip receipt\" ($actions)", "Print the trip receipt" in actions)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** Appendix A's Hillcrest Market, in walking order. */
        const val AISLES = "Produce\nBakery\nDeli & meat\nAisle 2 - Baking\nBreakfast\nDairy & eggs\nFrozen\nHousehold"
    }
}

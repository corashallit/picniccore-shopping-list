package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.Receipt
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.printReceipt
import earth.cora.picnic.core.model.setRarity
import earth.cora.picnic.core.store.TripFiling
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.a11y.TextOverflowed
import earth.cora.picnic.ui.common.ReceiptLines
import earth.cora.picnic.ui.common.ReceiptSticker
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Items 266–272 (egg 18, §13.2): the receipt's 9 lines at 21 columns from the trip's real stats; line 2 fitted; its width
 * following the LCD pitch up to 1.6×; the print sequence (14 frames over 1.7 s with jitter, the tear at 1.7 s, settled
 * at 1.96 s, PRINT★ and its cues, no timeout) and its strip `receipt_print_strip`; the tap that tucks it into the book
 * and the album; the auto-tuck; reduced motion; TalkBack.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ReceiptTest {
    @get:Rule
    val compose = createComposeRule()

    private val listId = PracticeFixture.LIST_ID
    private val px = 3f

    private fun list(s: AppState): GroceryList = s.list(listId)!!

    // ---- 266: content and line 2 ----

    @Test
    fun contentFromTrip() {
        val s = completedFixture(41)
        val receipt = ReceiptMaker.receipt(list(s), stops = 8, storeName = PracticeFixture.STORE, now = PracticeFixture.NOW + 3_600_000)
        assertEquals(24, receipt.stickers)
        assertEquals(8, receipt.baskets)
        assertEquals("the shop time frozen at the first completion", 41, receipt.minutes)
        assertEquals(1, receipt.shiny)
        assertEquals("the last session's date", "2026-10-03", receipt.dateLabel)
        val lines = ReceiptMaker.lines("Kirakira Basket", receipt, "Saturday shop").map { it.text }
        assertEquals(
            listOf(
                "KIRAKIRA ★ BASKET",
                "SAT 3 OCT · HILLCREST",
                "---------------------",
                "STICKERS ......... 24",
                "BASKETS ........... 8",
                "TIME ......... 41 MIN",
                "SHINY ............. 1",
                "---------------------",
                "THANK YOU ♪",
            ),
            lines,
        )
        lines.forEach { assertTrue("\"$it\" fits 21 columns", it.length <= ReceiptLines.COLUMNS) }
        lines.filter { "..." in it || it.startsWith("-") }.forEach { assertEquals(ReceiptLines.COLUMNS, it.length) }
        // Shiny counts jackpots too.
        val two = list(completedFixture().setRarity(listId, "coffee", Rarity.JACKPOT))
        assertEquals(2, ReceiptMaker.receipt(two, 8, PracticeFixture.STORE, PracticeFixture.NOW).shiny)
        // Printed again later: the same receipt, its stats frozen at the first print.
        val printed = s.printReceipt(listId, receipt).setRarity(listId, "coffee", Rarity.JACKPOT)
        assertEquals(receipt, ReceiptMaker.receipt(list(printed), 5, "Elsewhere", PracticeFixture.NOW + 9_000_000))
        // A migrated trip has no shop time: "--".
        val migrated = receipt.copy(minutes = null)
        assertEquals("TIME ............. --", ReceiptMaker.lines("Kirakira Basket", migrated, "Saturday shop")[5].text)
    }

    @Test
    fun line2Fits() {
        fun line2(date: LocalDate, store: String, title: String = "Saturday shop") =
            ReceiptMaker.lines("Kirakira Basket", Receipt(0, date.toString(), store, 1, 1, 1, 0), title)[1].text
        val sat = LocalDate.of(2026, 10, 3)
        val twoDigits = LocalDate.of(2026, 10, 10)
        assertEquals("SAT 3 OCT · HILLCREST", line2(sat, "Hillcrest Market"))
        // A 2-digit day: 22 columns with the weekday, so the weekday goes first.
        assertEquals("10 OCT · HILLCREST", line2(twoDigits, "Hillcrest Market"))
        // A long word: the weekday goes, and then it fits.
        assertEquals("3 OCT · SUPERMARCHE", line2(sat, "Supermarché Lefebvre"))
        // Longer still: cut to the budget (21 − date − 3).
        assertEquals("3 OCT · KAWARTHALAKES", line2(sat, "Kawarthalakesfoodemporium"))
        assertEquals("10 OCT · KAWARTHALAKE", line2(twoDigits, "Kawarthalakesfoodemporium"))
        // Stopwords are skipped; with none left, the list title's first word.
        assertEquals("SAT 3 OCT · SATURDAY", line2(sat, "My store"))
        assertEquals("SAT 3 OCT · COTTAGE", line2(sat, "The Market", "Cottage run"))
        assertEquals("SAT 3 OCT · BELANGER", line2(sat, "Épicerie Bélanger"))
        for (store in listOf("Hillcrest Market", "Supermarché Lefebvre", "Kawarthalakesfoodemporium", "My store", "Épicerie Bélanger")) {
            for (d in 1..31) {
                val text = line2(LocalDate.of(2026, 12, d), store)
                assertTrue("\"$text\" fits", text.length <= ReceiptLines.COLUMNS)
            }
        }
    }

    @Test
    fun fontScale200() {
        // 21 × 6 × p + 16 dp: 180 dp at 1.0, 278 dp at 2.0 (the pitch scales up to 1.6×); nothing clips.
        val receipt = ReceiptMaker.receipt(list(completedFixture()), 8, PracticeFixture.STORE, PracticeFixture.NOW)
        val lines = ReceiptMaker.lines("Kirakira Basket", receipt, "Saturday shop")
        var scale by mutableStateOf(1f)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                PicnicTheme(variant = ThemeVariant.Day, reducedMotion = false) {
                    Box(Modifier.fillMaxSize().wrapContentSize()) { ReceiptSticker(lines, Modifier.testTag("receipt")) }
                }
            }
        }
        val dp = compose.density.density
        assertEquals(180f, compose.onNodeWithTag("receipt").fetchSemanticsNode().size.width / dp, 1.5f)
        scale = 2f
        compose.waitForIdle()
        assertEquals(278f, compose.onNodeWithTag("receipt").fetchSemanticsNode().size.width / dp, 1.5f)
        val overflowed = compose.onAllNodes(SemanticsMatcher("overflowed") { it.config.getOrNull(TextOverflowed) == true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("no line clips at 2.0", overflowed.isEmpty())
    }

    // ---- 267: the print sequence and its strip ----

    @Test
    fun printSequence() {
        val r = EggRig(compose, fixture = completedFixture()).start()
        val lcd = r.anchor(AnchorRegistry.LCD)
        val crop = Rect(lcd.right - 236 * px, lcd.top - 8 * px, lcd.right + 16 * px, lcd.bottom + 250 * px)
        val frames = mutableListOf<Pair<Long, android.graphics.Bitmap>>()
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        val t0 = r.now
        assertNotNull(r.eggs.receipt)
        assertEquals(EggRules.PRINT, r.eggs.lcdWord?.text)
        assertEquals(1, r.feedback.count(Cue.RECEIPT_PRINT))
        assertTrue(r.list.trip!!.receipt != null)
        assertFalse("waiting for its tap", r.list.trip!!.receiptSaved)
        // The pure beats: 14 stepped frames over 1.7 s, a 2-frame jitter every 120 ms, the tear, the settle.
        val fed = (0L until ReceiptMotion.FEED_MS step 10).map { ReceiptMotion.fed(it) }.distinct()
        assertEquals(ReceiptMotion.FRAMES, fed.size)
        assertEquals(1f, ReceiptMotion.fed(ReceiptMotion.FEED_MS), 0f)
        assertTrue((0L until ReceiptMotion.FEED_MS step 120).zipWithNext().all { (a, b) -> ReceiptMotion.jitterDp(a) == -ReceiptMotion.jitterDp(b) })
        assertEquals(0f, ReceiptMotion.jitterDp(ReceiptMotion.FEED_MS), 0f)
        assertEquals(Triple(-4f, 10f, 3f), ReceiptMotion.settle(ReceiptMotion.SETTLE_AT))
        assertTrue(ReceiptMotion.slotVisible(ReceiptMotion.SETTLE_AT - 1))
        assertFalse(ReceiptMotion.slotVisible(ReceiptMotion.SETTLE_AT))
        // On the frame clock: strip frames, the tear's "tk" at 1.7 s, PRINT★ for 1.7 s, then the count.
        for (t in listOf(0L, 240L, 600L, 1_000L, 1_400L, 1_700L, 1_830L, 1_960L)) {
            r.advanceTo(t0 + t)
            frames += t to r.grab(crop)
        }
        val tear = r.feedback.times(Cue.RECEIPT_TEAR)
        assertEquals(1, tear.size)
        assertEquals((t0 + ReceiptMotion.FEED_MS).toDouble(), tear.single().toDouble(), 20.0)
        r.advanceTo(t0 + ReceiptMotion.FEED_MS + 120)
        assertNull("the LCD back to the count", r.eggs.lcdWord)
        stripOf(frames, cols = 4, scale = 0.5f).captureRoboImage("$EGG_SHOTS/receipt_print_strip.png")
        // It stays until tapped: no timeout.
        r.advance(12_000)
        assertNotNull(r.eggs.receipt)
        assertFalse(r.list.trip!!.receiptSaved)
    }

    // ---- 268: the tap ----

    @Test
    fun tapSavesToBookAndAlbum() {
        val r = EggRig(compose, fixture = completedFixture()).start()
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        val printed = r.list.trip!!.receipt!!
        r.advance(2_100)
        compose.onNodeWithTag(EggTags.RECEIPT).performClick()
        r.frame()
        assertNull(r.eggs.receipt)
        assertNotNull("it poofs into the book", r.eggs.receiptTuckId)
        assertTrue(r.list.trip!!.receiptSaved)
        assertEquals("Receipt saved to your sticker book ♡", r.model.toasts.current?.text)
        // The slip appears at the book's fore-edge (its magic appear, 420 ms on).
        r.advance(900)
        compose.onNodeWithTag(PracticeTags.SLIP, useUnmergedTree = true).assertExists()
        // Holding the LCD again prints the same receipt.
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        assertEquals(printed, r.eggs.receipt?.receipt)
        assertEquals(printed, r.list.trip!!.receipt)
        // New trip: the album's book carries it.
        r.act { r.sandbox.newTrip() }
        r.advance(200)
        val book = runBlocking { r.sandbox.album.current() }.books.first()
        assertEquals(printed, book.receipt)
        assertNull("the overlay goes with its trip", r.eggs.receipt)
    }

    // ---- 269: the auto-tuck ----

    @Test
    fun autoTuck() {
        // She leaves the list screen.
        val leave = EngineRig(state = completedFixture())
        try {
            leave.engine.onListShown(leave.listId)
            leave.engine.onLcdHold()
            assertNotNull(leave.eggs.receipt)
            leave.engine.onListHidden()
            assertNull(leave.eggs.receipt)
            assertNull("tucked in silently: no poof", leave.eggs.receiptTuckId)
            assertTrue(leave.list.trip!!.receiptSaved)
            assertTrue("no toast", leave.toasts.isEmpty())
        } finally {
            leave.close()
        }
        // She switches lists.
        val two = completedFixture().let { s -> s.copy(lists = s.lists + GroceryList("cottage", "Cottage run", PracticeFixture.WRITTEN)) }
        val switch = EngineRig(state = two)
        try {
            switch.engine.onListShown(listId)
            switch.engine.onLcdHold()
            switch.store.apply { it.copy(currentListId = "cottage") }
            assertNull(switch.eggs.receipt)
            assertTrue(switch.store.value.list(listId)!!.trip!!.receiptSaved)
        } finally {
            switch.close()
        }
        // She starts a New trip: the book is filed with the receipt, and the overlay goes.
        val newTrip = EngineRig(state = completedFixture())
        try {
            newTrip.engine.onListShown(listId)
            newTrip.engine.onLcdHold()
            val printed = newTrip.list.trip!!.receipt
            runBlocking { TripFiling.newTrip(newTrip.store, newTrip.albumStore, listId, NewTripMode.CLEAR_GOTTEN, PracticeFixture.NOW + 7_200_000) }
            assertNull(newTrip.eggs.receipt)
            assertEquals(printed, runBlocking { newTrip.albumStore.current() }.books.first().receipt)
        } finally {
            newTrip.close()
        }
        // The process died with a printed receipt untapped: the next start tucks it in.
        val printed = ReceiptMaker.receipt(list(completedFixture()), 8, PracticeFixture.STORE, PracticeFixture.NOW)
        val died = completedFixture().printReceipt(listId, printed)
        assertFalse(died.list(listId)!!.trip!!.receiptSaved)
        val restart = EngineRig(state = died)
        try {
            assertTrue(restart.list.trip!!.receiptSaved)
            assertTrue(restart.toasts.isEmpty())
        } finally {
            restart.close()
        }
    }

    // ---- 271: reduced motion ----

    @Test
    fun reducedMotion() {
        val r = EggRig(compose, reduced = true, fixture = completedFixture()).start()
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        val fx = r.eggs.receipt
        assertNotNull(fx)
        assertTrue("it fades in printed", fx!!.reduced)
        r.advance(ReceiptMotion.FADE_MS + 50)
        assertEquals("no tear under reduced motion", 0, r.feedback.count(Cue.RECEIPT_TEAR))
        compose.onNodeWithTag(EggTags.RECEIPT).performClick()
        r.frame()
        assertTrue(r.list.trip!!.receiptSaved)
        r.advance(ReceiptMotion.FADE_MS + 50)
        assertNull("the tap tucks it in with a fade", r.eggs.receiptTuckId)
    }

    // ---- 272: TalkBack ----

    @Test
    fun talkBack() {
        // Not complete: the LCD offers no print action (the pet stays a secret).
        val r = EggRig(compose).start()
        val actions = { compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true).fetchSemanticsNodes() }
        assertTrue(actions().none { n -> n.config[SemanticsActions.CustomActions].any { it.label == "Print the trip receipt" } })
        // Complete it: the action shows, and it prints.
        r.act { r.model.set { completedFixture() } }
        val lcdNode = actions().single { n -> n.config[SemanticsActions.CustomActions].any { it.label == "Print the trip receipt" } }
        r.act { lcdNode.config[SemanticsActions.CustomActions].single { it.label == "Print the trip receipt" }.action() }
        assertNotNull(r.eggs.receipt)
        r.advance(2_100)
        // The receipt is one button that reads the whole receipt, double-tap to save it to the sticker book.
        val node = compose.onNodeWithTag(EggTags.RECEIPT).fetchSemanticsNode()
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertEquals(
            "Trip receipt: Saturday 3 October, Hillcrest, 24 stickers, 8 baskets, 41 minutes, 1 shiny",
            node.config[SemanticsProperties.ContentDescription].single(),
        )
        assertEquals("save it to the sticker book", node.config[SemanticsActions.OnClick].label)
        compose.onNodeWithTag(EggTags.RECEIPT).performSemanticsAction(SemanticsActions.OnClick)
        r.frame()
        assertTrue(r.list.trip!!.receiptSaved)
    }
}

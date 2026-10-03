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
import androidx.compose.ui.test.onRoot
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
 * at 1.96 s, PRINT★ and its cues, no timeout) and its strip `receipt_print_strip_{day,glow}_<t>`; the slot line and the
 * paper never over words (item 259) at font scales 1.0, 1.3 and 2.0; the tap that tucks it into the book and the album;
 * the auto-tuck; reduced motion; TalkBack.
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
        // Invented names built from the Appendix A fixture store, for long words, accents and stopwords.
        assertEquals("SAT 3 OCT · HILLCREST", line2(sat, PracticeFixture.STORE))
        // A 2-digit day: 22 columns with the weekday, so the weekday goes first.
        assertEquals("10 OCT · HILLCREST", line2(twoDigits, PracticeFixture.STORE))
        // A long word: the weekday goes, and then it fits (the accent folded).
        assertEquals("3 OCT · SUPERMARCHE", line2(sat, "Supermarché Hillcrest"))
        // Longer still: cut to the budget (21 − date − 3).
        assertEquals("3 OCT · HILLCRESTFOOD", line2(sat, "Hillcrestfoodemporium Market"))
        assertEquals("10 OCT · HILLCRESTFOO", line2(twoDigits, "Hillcrestfoodemporium Market"))
        // Stopwords are skipped; with none left, the list title's first word.
        assertEquals("SAT 3 OCT · SATURDAY", line2(sat, "My store"))
        assertEquals("SAT 3 OCT · COTTAGE", line2(sat, "The Market", "Cottage run"))
        assertEquals("SAT 3 OCT · HILLCREST", line2(sat, "Épicerie Hillcrest"))
        val stores = listOf(PracticeFixture.STORE, "Supermarché Hillcrest", "Hillcrestfoodemporium Market", "My store", "Épicerie Hillcrest")
        for (store in stores) {
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

    /** Appendix C: a strip frame every 83 ms over the motion, from 0 to the settle at 1.96 s. */
    private val stripTimes: List<Long> =
        (0L..ReceiptMotion.SETTLE_AT step 83).toList().let { if (it.last() == ReceiptMotion.SETTLE_AT) it else it + ReceiptMotion.SETTLE_AT }

    /**
     * Holds the LCD and records `receipt_print_strip_<v>_<t>.png` (Appendix C: one PNG per frame, full resolution) at
     * [stripTimes], each cropped to the LCD and the paper (both, with room for the settle's offset and turn); returns
     * the print's start time.
     */
    private fun EggRig.recordStrip(): Long {
        holdLcd(timeouts.holdThresholdMs + 20)
        val t0 = now
        assertNotNull(eggs.receipt)
        frame()
        val lcd = anchor(AnchorRegistry.LCD)
        val paper = compose.onNodeWithTag(EggTags.RECEIPT).fetchSemanticsNode().boundsInRoot
        val crop = Rect(minOf(lcd.left, paper.left), lcd.top, maxOf(lcd.right, paper.right), paper.bottom).inflate(16 * px)
        for (t in stripTimes) {
            advanceTo(t0 + t)
            grab(crop).captureRoboImage("$EGG_SHOTS/receipt_print_strip_${v}_$t.png")
        }
        return t0
    }

    @Test
    fun printSequence() {
        val r = EggRig(compose, fixture = completedFixture(41)).start()
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
        // On the frame clock: the paper feeds out (more of it shows, frame by frame), the tear's "tk" at 1.7 s, PRINT★
        // for 1.7 s, then the count.
        var shown = 0f
        var grew = 0
        r.probe.clear()
        while (r.now < t0 + ReceiptMotion.FEED_MS - 20) {
            r.advance(83)
            val h = r.probe.frames.lastOrNull { it.tag == "receipt" }?.bounds?.height ?: continue
            if (h > shown + 1f) grew++
            shown = maxOf(shown, h)
        }
        assertTrue("the paper feeds out in steps ($grew)", grew >= ReceiptMotion.FRAMES - 3)
        r.advanceTo(t0 + ReceiptMotion.SETTLE_AT)
        val tear = r.feedback.times(Cue.RECEIPT_TEAR)
        assertEquals(1, tear.size)
        assertEquals((t0 + ReceiptMotion.FEED_MS).toDouble(), tear.single().toDouble(), 20.0)
        r.advanceTo(t0 + ReceiptMotion.FEED_MS + 120)
        assertNull("the LCD back to the count", r.eggs.lcdWord)
        // It stays until tapped: no timeout.
        r.advance(12_000)
        assertNotNull(r.eggs.receipt)
        assertFalse(r.list.trip!!.receiptSaved)
    }

    /** The strip `receipt_print_strip_day_<t>` (Appendix C, item 267). */
    @Test
    fun printStripDay() {
        EggRig(compose, fixture = completedFixture(41)).start().recordStrip()
    }

    /** The same strip in Glow (`receipt_print_strip_glow_<t>`): the LCD and the paper stay light on the night binder. */
    @Test
    fun printStripGlow() {
        EggRig(compose, variant = ThemeVariant.Glow, fixture = completedFixture(41)).start().recordStrip()
    }

    // ---- 259: never over words, at 1.0, 1.3 and 2.0 ----

    @Test
    fun neverOverWords100() = neverOverWords(1f)

    @Test
    fun neverOverWords130() = neverOverWords(1.3f)

    @Test
    fun neverOverWords200() = neverOverWords(2f)

    /**
     * Item 259 (§4.1, §13.2) at font scale [scale]: the slot line is the LCD's width, centred under it, and neither it
     * nor the paper lies over any word on screen (the text nodes and the list's word keep-outs: the title, the
     * subtitle, the LCD, the book's date, the tabs, the metas, the rows), on every frame from the hold to well after
     * the settle; where the paper would cover the list, the list makes room below it.
     */
    private fun neverOverWords(scale: Float) {
        val r = EggRig(compose, fontScale = scale, fixture = completedFixture(41)).start()
        r.probe.clear()
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        val t0 = r.now
        assertNotNull(r.eggs.receipt)
        val wrong = LinkedHashSet<String>()
        var slots = 0
        var papers = 0
        while (r.now < t0 + ReceiptMotion.SETTLE_AT + 600) {
            r.advance(32)
            val words = wordBoxes(compose, r.anchors)
            val frames = r.probe.frames.groupBy { it.tag }.mapValues { it.value.last() }
            r.probe.clear()
            frames["slot"]?.let { slot ->
                slots++
                val lcd = r.anchor(AnchorRegistry.LCD)
                assertEquals("the slot line is the LCD's width", lcd.width, slot.bounds.width, 1f)
                assertEquals("centred under the LCD", lcd.center.x, slot.bounds.center.x, 1f)
                assertTrue("under the LCD", slot.bounds.top >= lcd.bottom - 1f)
                assertEquals("4 dp", 4 * px, slot.bounds.height, 1f)
            }
            for (tag in listOf("receipt", "slot")) {
                val f = frames[tag] ?: continue
                if (tag == "receipt") papers++
                val piece = f.bounds.deflate(2f)
                if (piece.width <= 0f || piece.height <= 0f) continue
                words.filter { (_, box) -> box.overlaps(piece) }.forEach { (w, _) -> wrong += "$tag over \"$w\"" }
            }
        }
        assertTrue("the slot line showed ($slots frames)", slots > 10)
        assertTrue("the paper showed ($papers frames)", papers > 10)
        assertEquals("font scale $scale", emptyList<String>(), wrong.toList())
        // Still a working list: its first strip is on screen below the receipt.
        val paper = r.probe.frames.lastOrNull { it.tag == "receipt" }?.bounds ?: compose.onNodeWithTag(EggTags.RECEIPT).fetchSemanticsNode().boundsInRoot
        val tabs = wordBoxes(compose, r.anchors).filter { it.first.startsWith(EggAnchors.TEXT_PREFIX + "tab:") }.map { it.second }
        assertTrue("a tab shows on screen", tabs.any { it.top >= paper.bottom - 1f && it.bottom <= compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom })
    }

    // ---- 268: the tap ----

    @Test
    fun tapSavesToBookAndAlbum() {
        val r = EggRig(compose, fixture = completedFixture(41)).start()
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
        val r = EggRig(compose, reduced = true, fixture = completedFixture(41)).start()
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

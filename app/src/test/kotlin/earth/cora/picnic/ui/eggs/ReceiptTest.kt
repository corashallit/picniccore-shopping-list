package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
import earth.cora.picnic.core.model.removeItem
import earth.cora.picnic.core.model.toggleChecked
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
 * at 1.96 s, PRINT★ until then and its cues, no timeout) and its strip `receipt_print_strip_{day,glow}_<t>`; the slot
 * line under the LCD and the paper hanging from it, never over words (item 259), at font scales 1.0, 1.3 and 2.0, on
 * WP5's real List screen as the Surprise lab shows it; the tap that tucks it into the book and the album;
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

    /**
     * Item 259 (§13.1): the app bar's LCD keeps one width whatever it shows. Its room is as wide as the widest word it
     * can show (PRINT★, SNACK?, BONUS★, FED, SYNC) or its count, whichever is wider, in every mode, so PRINT★ on a
     * 6-item trip, SNACK? at 3/24 or FED at 0/1 never widen it. While the pet lives there, FED beside the bee and every
     * other word standing in for the bee and the count are exactly as wide as the bee and the count, and the bee's
     * chip is no wider than it needs (at 10/24 and 24/24 at font scale 1, as wide as the bee and the count). All at font
     * scales 1.0, 1.3 and 2.0. Off the app bar (no words to keep room for), a word is still never narrower than the count.
     */
    @Test
    fun theLcdKeepsTheCountsWidthWhileAWordShows() {
        var scale by mutableStateOf(1f)
        val words = listOf("SYNC") + EggRules.LCD_WORDS
        val counts = listOf(24 to 24, 6 to 6, 3 to 24, 0 to 1, 10 to 24, 100 to 120)
        fun state(count: Int, total: Int, mode: earth.cora.picnic.ui.theme.LcdMode = earth.cora.picnic.ui.theme.LcdMode.Count, text: String = "") =
            earth.cora.picnic.ui.theme.LcdState(mode = mode, count = count, total = total, text = text)
        val pet = earth.cora.picnic.ui.theme.LcdMode.Pet
        val word = earth.cora.picnic.ui.theme.LcdMode.Text
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                PicnicTheme(variant = ThemeVariant.Day, reducedMotion = false) {
                    // Scrollable: every chip gets its full height, however many there are.
                    androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        for ((c, t) in counts) {
                            // As the eggs show them (SurpriseState.lcd): FED beside the bee; any other word stands in for both.
                            val chips = listOf(
                                "count" to state(c, t),
                                "pet" to state(c, t, pet),
                                "pet ${EggRules.FED}" to state(c, t, pet, EggRules.FED),
                            ) + words.map { w -> w to state(c, t, word, w) } +
                                (words - EggRules.FED).map { w -> "pet $w" to state(c, t, word, w).copy(petLives = true) }
                            for ((tag, st) in chips) {
                                earth.cora.picnic.ui.theme.LcdChip(st, Modifier.testTag("$c/$t $tag"), words = words)
                            }
                            // Off the app bar: no words to keep room for.
                            earth.cora.picnic.ui.theme.LcdChip(state(c, t), Modifier.testTag("$c/$t plain count"))
                            earth.cora.picnic.ui.theme.LcdChip(state(c, t, word, EggRules.FED), Modifier.testTag("$c/$t plain FED"))
                            earth.cora.picnic.ui.theme.LcdChip(state(c, t, pet), Modifier.testTag("$c/$t plain pet"))
                        }
                    }
                }
            }
        }
        for (s in listOf(1f, 1.3f, 2f)) {
            scale = s
            compose.waitForIdle()
            fun width(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().size.width
            for ((c, t) in counts) {
                val count = width("$c/$t count")
                for (w in words) assertEquals("fs $s: $w as wide as $c/$t★", count, width("$c/$t $w"))
                val withBee = width("$c/$t pet")
                for (w in words) {
                    assertEquals("fs $s: $w (the pet living here) as wide as the bee and $c/$t", withBee, width("$c/$t pet $w"))
                }
                // At font scale 1 (the bee does not scale with the text, so at large text the words may be the wider).
                if (c >= 10 && s == 1f) assertEquals("the bee's chip at $c/$t no wider than the bee and the count", width("$c/$t plain pet"), withBee)
                assertTrue("fs $s: off the app bar FED is never narrower than $c/$t★", width("$c/$t plain FED") >= width("$c/$t plain count"))
            }
        }
    }

    /**
     * Item 259 on the real List screen: a complete 6-item trip (its count, 6/6★, far narrower than PRINT★). From the hold
     * through the print to well after the settle, the title, the subtitle and the LCD never move by a pixel.
     */
    @Test
    fun theBinderTopHoldsStillWhilePrintShows() {
        val r = EggRig(compose, fixture = sixItemTrip()).start()
        r.advance(1_500)
        assertEquals("6/6", "${r.list.items.count { it.checked }}/${r.list.items.size}")
        val still = r.binderTop()
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        val t0 = r.now
        assertEquals(EggRules.PRINT, r.eggs.lcdWord?.text)
        val frames = r.assertBinderTopStill(still, t0 + ReceiptMotion.SETTLE_AT + 600, "PRINT★ on 6/6")
        assertTrue("checked every frame ($frames)", frames > 60)
        assertNull("the count is back", r.eggs.lcdWord)
    }

    /** The fixture's list cut down to six items, all checked: a complete trip of 6. */
    private fun sixItemTrip(): AppState {
        val keep = listOf("strawberries", "bananas", "apples", "carrots", "sourdough", "croissants")
        var s = PracticeFixture.unchecked()
        for (id in list(s).items.map { it.id }) if (id !in keep) s = s.removeItem(listId, id, PracticeFixture.WRITTEN + 100)
        keep.forEachIndexed { i, id -> s = s.toggleChecked(listId, id, PracticeFixture.TRIP_START + i * 60_000L) }
        return s
    }

    /**
     * Item 259, 267: she scrolls her list while a printed receipt waits for its tap. The binder top and the book stay
     * open (the LCD does not move), the paper still hangs under the LCD where it settled, it still waits, and no word of
     * the list lies under it, at font scales 1.0 and 2.0 (where the paper hangs into the list's room and the list's
     * viewport starts under it).
     */
    @Test
    fun scrollWhileItWaits100() = scrollWhileItWaits(1f)

    @Test
    fun scrollWhileItWaits200() = scrollWhileItWaits(2f)

    private fun scrollWhileItWaits(scale: Float) {
        val r = EggRig(compose, fontScale = scale, fixture = completedFixture(41)).start()
        r.advance(1_200)
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        r.advance(2_600)
        fun paper() = compose.onNodeWithTag(EggTags.RECEIPT, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val lcd0 = r.anchor(AnchorRegistry.LCD)
        val paper0 = paper()
        /** Her thumb on the part of the list she can see (between its top and the add bar): [dy] px up. */
        fun drag(dy: Float) {
            val list = r.anchor(EggAnchors.LIST)
            // The add bar may have folded into the FAB by now (at the screen's right edge, clear of her thumb here).
            val floor = minOf(list.bottom, r.anchors.bounds(AnchorRegistry.ADD_BAR)?.top ?: list.bottom)
            val from = if (dy > 0) floor - 30f else list.top + 30f
            val to = (from - dy).coerceIn(list.top + 10f, floor - 10f)
            compose.onRoot().performTouchInput { swipe(Offset(540f, from), Offset(540f, to), durationMillis = 300) }
            r.advance(1_000)
        }
        /** The words she can see under the paper: the list's are cut at its viewport's top (rows scroll under it). */
        fun underThePaper(): List<String> {
            val top = r.anchor(EggAnchors.LIST).top
            val header = setOf("title", "subtitle", "lcd", "date", "perfect", "pagelcd").map { EggAnchors.TEXT_PREFIX + it }
            return wordBoxes(compose, r.anchors).mapNotNull { (w, box) ->
                // Text nodes come cut by the list's clip already; the list's keep-outs are cut here.
                val listWord = w.startsWith(EggAnchors.TEXT_PREFIX) && w !in header
                val seen = if (listWord) Rect(box.left, maxOf(box.top, top), box.right, box.bottom) else box
                w.takeIf { seen.height > 0f && seen.overlaps(paper0.deflate(6f)) }
            }
        }
        val tabs0 = wordBoxes(compose, r.anchors).filter { it.first.startsWith(EggAnchors.TEXT_PREFIX + "tab:") }
        drag(400f)
        assertNotNull("fs $scale: it still waits for its tap", r.eggs.receipt)
        val tabs1 = wordBoxes(compose, r.anchors).filter { it.first.startsWith(EggAnchors.TEXT_PREFIX + "tab:") }
        assertTrue("fs $scale: the list scrolled", tabs0 != tabs1)
        assertEquals("fs $scale: the binder top stays open (the LCD did not move)", lcd0, r.anchor(AnchorRegistry.LCD))
        val paper1 = paper()
        assertEquals("fs $scale: the paper stays where it settled", paper0, paper1)
        val lcd = r.anchor(AnchorRegistry.LCD)
        assertTrue("fs $scale: it hangs under the LCD ($paper1, LCD $lcd)", paper1.top >= lcd.bottom - 2f)
        assertTrue("fs $scale: under the LCD's middle ($paper1, LCD $lcd)", lcd.center.x in paper1.left..paper1.right)
        assertTrue("fs $scale: the list's viewport starts under the paper (${r.anchor(EggAnchors.LIST)}, paper $paper1)", r.anchor(EggAnchors.LIST).top >= paper1.bottom)
        assertEquals("fs $scale: no word under the waiting receipt", emptyList<String>(), underThePaper())
        // Further down and back up: still none, and the paper is where it was.
        drag(600f)
        drag(-200f)
        assertEquals(paper0, paper())
        assertEquals("fs $scale: no word under it after more scrolling", emptyList<String>(), underThePaper())
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
        // until the settle at 1.96 s, then the count.
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
        // PRINT★ shows while it prints, until the settle (the slot line goes with it), then the count.
        r.advanceTo(t0 + ReceiptMotion.SETTLE_AT - 40)
        assertEquals("PRINT★ while the slot shows", EggRules.PRINT, r.eggs.lcdWord?.text)
        val tear = r.feedback.times(Cue.RECEIPT_TEAR)
        assertEquals(1, tear.size)
        assertEquals((t0 + ReceiptMotion.FEED_MS).toDouble(), tear.single().toDouble(), 20.0)
        r.advanceTo(t0 + ReceiptMotion.SETTLE_AT + 60)
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
     * Item 259 (§4.1, §13.2) on the real List screen at font scale [scale], on every frame from the hold to well after
     * the settle: the slot line is 4 dp, its top within 4 dp of the LCD's bottom, as wide as the paper and centred on
     * it; while the paper feeds, its top edge comes out of the slot and it hangs from it; the binder top's and the book's
     * words (the title, the subtitle, the book's date, PER-FECT!) lie over the slot and the paper, untouched pixel for
     * pixel; the paper and the slot lie over no other word (the tabs, the metas, the rows, the text nodes) and never
     * over the LCD; the settled paper is clear of every word, and where it would cover the list, the list makes room.
     */
    private fun neverOverWords(scale: Float) {
        val r = EggRig(compose, fontScale = scale, fixture = completedFixture(41)).start()
        r.advance(300)
        // She is at the top of her list (the receipt brings it there anyway), the binder top settled.
        compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.LIST).performScrollToIndex(0)
        r.advance(900)
        val headerKeys = listOf("title", "subtitle", "date", "perfect", "pagelcd")
        val header = headerKeys.mapNotNull { k -> r.anchors.bounds(EggAnchors.TEXT_PREFIX + k)?.let { k to it } }
        assertTrue("fs $scale: the book's date is a keep-out", header.any { it.first == "date" })
        val before = r.grab()
        val headerPixels = header.associate { (k, b) -> k to pixels(before, b.deflate(px)) }
        r.probe.clear()
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        val t0 = r.now
        assertNotNull(r.eggs.receipt)
        val wrong = LinkedHashSet<String>()
        var slots = 0
        var papers = 0
        var settled: Rect? = null
        while (r.now < t0 + ReceiptMotion.SETTLE_AT + 600) {
            r.advance(32)
            val t = r.now - t0
            val now = r.grab()
            val lcd = r.anchor(AnchorRegistry.LCD)
            val under = headerKeys.mapNotNull { r.anchors.bounds(EggAnchors.TEXT_PREFIX + it) } + lcd
            // The words the paper may pass under are cut out of it: they must be untouched.
            for ((k, b) in header) {
                val at = r.anchors.bounds(EggAnchors.TEXT_PREFIX + k)
                if (at != b) wrong += "the $k moved from $b to $at at $t ms"
                else if (!pixels(now, b.deflate(px)).contentEquals(headerPixels.getValue(k))) wrong += "the $k changed at $t ms"
            }
            val words = wordBoxes(compose, r.anchors).filterNot { (_, box) -> under.any { it.inflate(1f).contains(box.topLeft) && it.inflate(1f).contains(box.bottomRight) } }
            val frames = r.probe.frames.groupBy { it.tag }.mapValues { it.value.last() }
            r.probe.clear()
            val slot = frames["slot"]?.bounds
            val paper = frames["receipt"]?.bounds
            if (slot != null) {
                slots++
                assertTrue("fs $scale: the slot's top within 4 dp of the LCD's bottom (${slot.top} vs ${lcd.bottom})", slot.top - lcd.bottom in 0f..4 * px + 1f)
                assertEquals("fs $scale: 4 dp", 4 * px, slot.height, 1f)
                if (paper != null && t < ReceiptMotion.FEED_MS) {
                    assertTrue("fs $scale, $t ms: the paper's top edge comes out of the slot (${paper.top} in ${slot.top}..${slot.bottom})", paper.top in slot.top..slot.bottom)
                    assertTrue("fs $scale, $t ms: it hangs from the slot ($paper under $slot)", paper.left >= slot.left - 1f && paper.right <= slot.right + 1f)
                    assertEquals("fs $scale: the slot is as wide as the paper, centred on it", paper.center.x, slot.center.x, 2f)
                }
                if (slot.overlaps(lcd)) wrong += "the slot over the LCD at $t ms"
            }
            if (paper != null) {
                papers++
                if (paper.deflate(2f).overlaps(lcd)) wrong += "the paper over the LCD at $t ms"
                if (t >= ReceiptMotion.SETTLE_AT) settled = paper
            }
            for ((tag, f) in listOf("receipt" to paper, "slot" to slot)) {
                val piece = f?.deflate(2f) ?: continue
                if (piece.width <= 0f || piece.height <= 0f) continue
                words.filter { (_, box) -> box.overlaps(piece) }.forEach { (w, _) -> wrong += "$tag over \"$w\"" }
            }
        }
        assertTrue("the slot line showed ($slots frames)", slots > 10)
        assertTrue("the paper showed ($papers frames)", papers > 10)
        assertEquals("font scale $scale", emptyList<String>(), wrong.toList())
        // Settled, it is clear of every word (it passes under none of them any more).
        val rest = settled!!.deflate(2f)
        header.forEach { (k, b) -> assertFalse("fs $scale: the settled paper over the $k ($rest, $b)", rest.overlaps(b)) }
        // Still a working list: its first strip is on screen below the receipt.
        val tabs = wordBoxes(compose, r.anchors).filter { it.first.startsWith(EggAnchors.TEXT_PREFIX + "tab:") }.map { it.second }
        assertTrue("a tab shows on screen", tabs.any { it.top >= rest.bottom - 1f && it.bottom <= compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom })
    }

    private fun pixels(b: android.graphics.Bitmap, box: Rect): IntArray {
        val x = box.left.toInt().coerceAtLeast(0)
        val y = box.top.toInt().coerceAtLeast(0)
        val w = box.width.toInt().coerceAtMost(b.width - x).coerceAtLeast(1)
        val h = box.height.toInt().coerceAtMost(b.height - y).coerceAtLeast(1)
        return IntArray(w * h).also { b.getPixels(it, 0, w, x, y, w, h) }
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
        compose.onNodeWithTag(earth.cora.picnic.ui.theme.BookTags.SLIP, useUnmergedTree = true).assertExists()
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

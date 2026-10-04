package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Toronto wall-clock time on the fixture's day (or [date]) as epoch ms. */
fun torontoMs(time: LocalTime, date: LocalDate = LocalDate.of(2026, 10, 3)): Long =
    date.atTime(time).atZone(ZoneId.of("America/Toronto")).toInstant().toEpochMilli()

/**
 * Item 242 (egg 5): from 23:00 to 04:00 the list is Glow with the moon (even on a light system), the LCD reads
 * "SNACK?" for 2.6 s on entering, then the count, and the snack star and "snack?" doodle sit by the frozen aisle's tab;
 * crossing 23:00 while open does the same; the walking order is unchanged.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggMidnightTest {
    @get:Rule
    val compose = createComposeRule()


    @Test
    fun midnightOnEntry() {
        val day = EngineRig()
        val dayOrder = day.engine.grouped()!!.sections.map { it.id }
        day.close()

        val r = EggRig(compose, startAt = torontoMs(LocalTime.of(23, 30))).start()
        assertEquals("Glow even with the system light", ThemeVariant.Glow, r.resolved)
        assertTrue(r.eggs.midnight)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        assertEquals("frozen", r.eggs.snackSectionId)
        assertEquals("walking order unchanged", dayOrder, r.engine.grouped()!!.sections.map { it.id })
        // The doodle is decorative: TalkBack has no text node for it.
        assertEquals(0, compose.onAllNodesWithText("snack?").fetchSemanticsNodes().size)
        val t0 = r.now
        // The Surprise lab brings the frozen aisle into view (her own list does not: herListNeverScrollsByItself), and
        // the snack star and doodle sit by its tab: right of the tab in its row
        // when the tab leaves room for it, else right of the meta line ("2 left of 2"), never over either; within the
        // strip's words (clear of the basket), on screen above the add bar.
        r.advance(800)
        assertSnackBesideTheWords(r)
        // "SNACK?" holds 2.6 s, then the count.
        r.advanceTo(t0 + EggRules.SNACK_MS - 100)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        r.advanceTo(t0 + EggRules.SNACK_MS + 100)
        assertNull(r.eggs.lcdWord)
    }

    /** The snack doodle by the frozen tab, clear of the tab's and the meta line's words, and of the basket. */
    private fun assertSnackBesideTheWords(r: EggRig) {
        val snack = compose.onNodeWithTag(EggTags.SNACK, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tab = r.anchor(EggAnchors.tab("frozen"))
        val meta = r.anchor(EggAnchors.TEXT_PREFIX + "meta:frozen")
        val strip = r.anchor(EggAnchors.strip("frozen"))
        val basket = r.anchor(AnchorRegistry.basketKey("frozen"))
        val bar = r.anchor(AnchorRegistry.ADD_BAR)
        assertTrue("drawn ($snack)", snack.width > 20 * 3f && snack.height > 10 * 3f)
        val besideTab = snack.left >= tab.right - 1f && snack.center.y in tab.top..tab.bottom
        val afterMeta = snack.left >= meta.right - 1f && snack.top >= tab.bottom - 3 * 3f
        val underMeta = snack.top >= meta.bottom - 1f && snack.left <= meta.left + 6 * 3f
        assertTrue("by the tab ($snack, tab $tab, meta $meta)", besideTab || afterMeta || underMeta)
        assertFalse("never over the tab ($snack, tab $tab)", snack.deflate(2f).overlaps(tab))
        assertFalse("never over the meta line ($snack, meta $meta)", snack.deflate(2f).overlaps(meta))
        assertTrue("clear of the basket ($snack, basket $basket)", snack.right <= basket.left + 2f)
        assertTrue("within the strip ($snack, strip $strip)", snack.left >= strip.left && snack.bottom <= strip.bottom)
        assertTrue("on screen above the add bar ($snack, bar $bar)", snack.top >= 0f && snack.bottom <= bar.top)
    }

    /**
     * §4.10 tab fit, §12.1 "none hides information": the doodle never takes room from the frozen tab, at font scales 1.3
     * and 2.0 as at 1.0: the tab's name has the same room at midnight as by day, and the full "Frozen" fits it (at 20 sp,
     * or stepped down to no less than 16 sp, §4.10).
     */
    @Test
    fun theDoodleNeverShrinksTheTabAt130() = tabKeepsItsRoom(1.3f)

    @Test
    fun theDoodleNeverShrinksTheTabAt200() = tabKeepsItsRoom(2.0f)

    private fun tabKeepsItsRoom(scale: Float) {
        var fits: ((String, Float) -> Int)? = null
        val r = EggRig(compose, fontScale = scale)
        r.start {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val style = earth.cora.picnic.ui.theme.PicnicTheme.type.aisleTab
            androidx.compose.runtime.SideEffect {
                fits = { text, sp -> measurer.measure(text, style.copy(fontSize = androidx.compose.ui.unit.TextUnit(sp, androidx.compose.ui.unit.TextUnitType.Sp)), maxLines = 1, softWrap = false).size.width }
            }
            EggListUnderTest.Real.content(r)
        }
        // By day: bring Frozen into view, and read the room its name has.
        r.act { r.model.focus("frozen") }
        r.advance(1_200)
        val day = r.anchor(EggAnchors.TEXT_PREFIX + "tab:frozen").width
        // Midnight: the doodle by the tab.
        r.fire(5)
        r.advance(1_200)
        val night = r.anchor(EggAnchors.TEXT_PREFIX + "tab:frozen").width
        assertEquals("fs $scale: the tab's name keeps its room", day, night, 1f)
        val need = fits!!("Frozen", 16f)
        assertTrue("fs $scale: the full \"Frozen\" fits at 16 sp or more ($need px in $night px)", need.toFloat() <= night + 1f)
        assertSnackBesideTheWords(r)
        r.sandbox.close()
    }

    @Test
    fun crossingElevenWhileOpen() {
        val r = EggRig(compose, startAt = torontoMs(LocalTime.of(22, 59, 30))).start()
        assertEquals(ThemeVariant.Day, r.resolved)
        assertNull(r.eggs.lcdWord)
        r.advance(40_000)
        // The minute tick (ThemeVariantSource's onTick in the app) re-reads the clock.
        r.act {
            r.engine.refresh()
            runBlocking { r.sandbox.variants.tickNow() }
        }
        assertTrue(r.eggs.midnight)
        assertEquals(ThemeVariant.Glow, r.resolved)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
    }

    @Test
    fun fourOClockEndsIt() {
        val e = EngineRig(clock = FakeClock(torontoMs(LocalTime.of(3, 59, 30))))
        try {
            e.engine.onListShown(e.listId)
            assertTrue(e.eggs.midnight)
            e.clock.advance(60_000)
            e.engine.refresh()
            assertFalse(e.eggs.midnight)
            assertNull(e.eggs.snackSectionId)
            // 22:59 is not midnight yet; 23:00 is.
            e.at(LocalDate.of(2026, 10, 3), LocalTime.of(22, 59))
            assertFalse(e.eggs.midnight)
            e.at(LocalDate.of(2026, 10, 3), LocalTime.of(23, 0))
            assertTrue(e.eggs.midnight)
        } finally {
            e.close()
        }
    }

    /**
     * §12.1 (none changes where she is): on her own list (ListRoute: no scene scrolls) the midnight snack never moves
     * the list. Entering at 23:30 leaves it at its top; she scrolls a little, leaves for another screen and comes back,
     * and it is where she left it. Egg 5 still shows (SNACK?, the snack doodle by the frozen tab when she gets there).
     */
    @Test
    fun herListNeverScrollsByItself() {
        val shown = androidx.compose.runtime.mutableStateOf(true)
        val ls = androidx.compose.foundation.lazy.LazyListState()
        val r = EggRig(compose, startAt = torontoMs(LocalTime.of(23, 30)))
        r.start { if (shown.value) PracticeListScreen(r.sandbox, listState = ls, sceneScrolls = false) }
        r.advance(1_500)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        assertEquals("frozen", r.eggs.snackSectionId)
        assertEquals("entry: still at the top", 0 to 0, ls.firstVisibleItemIndex to ls.firstVisibleItemScrollOffset)
        // The nap cat (every stop but Frozen done) does not move it either.
        r.fire(8)
        r.advance(1_500)
        assertEquals("frozen", r.eggs.napSectionId)
        assertEquals("the nap: still at the top", 0 to 0, ls.firstVisibleItemIndex to ls.firstVisibleItemScrollOffset)
        // She scrolls a little (one strip), then goes to Settings and back: the list leaves composition and returns.
        r.act { runBlocking { ls.scrollToItem(1) } }
        r.advance(300)
        val left = ls.firstVisibleItemIndex to ls.firstVisibleItemScrollOffset
        r.act { shown.value = false }
        r.advance(300)
        r.act { shown.value = true }
        r.advance(1_500)
        assertEquals("re-entry: where she left it", left, ls.firstVisibleItemIndex to ls.firstVisibleItemScrollOffset)
    }

    /**
     * Item 259 on the real List screen at 3/24 (a count far narrower than SNACK?): from entry at 23:30 through the 2.6 s
     * of SNACK? to the count again, the title, the subtitle and the LCD never move by a pixel.
     */
    @Test
    fun theBinderTopHoldsStillWhileSnackShows() {
        val ids = PracticeFixture.unchecked().list(PracticeFixture.LIST_ID)!!.items.take(3).map { it.id }
        val s = ids.foldIndexed(PracticeFixture.unchecked()) { i, acc, id ->
            acc.toggleChecked(PracticeFixture.LIST_ID, id, PracticeFixture.TRIP_START + i * 60_000L)
        }
        val r = EggRig(compose, fixture = s, startAt = torontoMs(LocalTime.of(23, 30)))
        r.start { PracticeListScreen(r.sandbox, sceneScrolls = false) }
        val t0 = r.now
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        assertEquals(3, r.list.items.count { it.checked })
        r.advance(100)
        val still = r.binderTop()
        val frames = r.assertBinderTopStill(still, t0 + EggRules.SNACK_MS + 500, "SNACK? at 3/24")
        assertTrue("checked every frame ($frames)", frames > 60)
        assertNull("the count is back", r.eggs.lcdWord)
    }

    @Test
    fun reducedMotionAppliesInPlace() {
        val r = EggRig(compose, reduced = true, startAt = torontoMs(LocalTime.of(23, 30))).start()
        assertEquals(ThemeVariant.Glow, r.resolved)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        assertEquals("frozen", r.eggs.snackSectionId)
    }
}

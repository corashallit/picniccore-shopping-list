package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.removeItem
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.lcdShowsMoon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 247 (egg 10): tapping the first four AISLE stamps by position in the order 1st 1st 2nd 2nd 3rd 4th 3rd 4th starts
 * the bonus stage (every sticker a pixel sprite, BONUS★ on the LCD, the toast, the 8-bit jingle every 4 s while the list
 * shows); a wrong tap restarts, a tap on the 1st restarts at step 1, 6 s overall, at least 4 aisle stops; any stamp tap
 * or leaving the list ends it. `gapsAndEverythingElse`: the "Everything else" capsule stamp is skipped and printed
 * numbers may skip (stops 1 2 4 6).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggBonusStageTest {
    @get:Rule
    val compose = createComposeRule()

    private val order = EggRules.BONUS_SEQUENCE

    /** Taps stamp page indexes [positions] 250 ms apart. */
    private fun EngineRig.tap(positions: List<Int>, gapMs: Long = 250) = positions.forEach {
        clock.advance(gapMs)
        engine.onStampTap(it)
    }

    /**
     * §13.1: in Dusk and Glow only the count's star becomes the full moon; the star inside a word stays a star, so the
     * LCD reads BONUS★ (and PRINT★), never "BONUS●".
     */
    @Test
    fun wordStarsStayStarsInGlow() {
        for (v in listOf(ThemeVariant.Glow, ThemeVariant.Dusk)) {
            assertTrue(lcdShowsMoon(v, LcdState(mode = LcdMode.Count, count = 10, total = 24)))
            assertFalse("$v BONUS★", lcdShowsMoon(v, LcdState(mode = LcdMode.Text, text = EggRules.BONUS)))
            assertFalse("$v PRINT★", lcdShowsMoon(v, LcdState(mode = LcdMode.Text, text = EggRules.PRINT)))
        }
        assertFalse(lcdShowsMoon(ThemeVariant.Day, LcdState(mode = LcdMode.Count, count = 10, total = 24)))
        assertFalse(lcdShowsMoon(ThemeVariant.Golden, LcdState(mode = LcdMode.Count, count = 10, total = 24)))
    }

    @Test
    fun theSequenceStartsTheStage() {
        val e = EngineRig()
        try {
            e.engine.onListShown(e.listId)
            // The fixture's stamp page: 8 aisle stops, no "Everything else"; positions 0..3 are the first four.
            assertTrue(e.engine.grouped()!!.sections.none { it.kind == SectionKind.UNSORTED })
            e.tap(order.dropLast(1))
            assertFalse("seven taps are not enough", e.eggs.bonusStage)
            e.tap(order.takeLast(1))
            assertTrue(e.eggs.bonusStage)
            assertTrue(e.eggs.pixelStickers)
            assertEquals(EggRules.BONUS, e.eggs.lcdWord?.text)
            assertEquals(listOf(EggToast.BonusStage), e.toasts)
            // Any stamp tap ends it.
            e.engine.onStampTap(5)
            assertFalse(e.eggs.bonusStage)
            assertNull(e.eggs.lcdWord)
        } finally {
            e.close()
        }
    }

    @Test
    fun aWrongTapRestartsAndTheFirstRestartsAtStepOne() {
        val e = EngineRig()
        try {
            e.engine.onListShown(e.listId)
            // 1st 1st 2nd, then a wrong 4th: start over.
            e.tap(listOf(0, 0, 1, 3))
            e.tap(order.drop(1))
            assertFalse("the restart needs the whole sequence", e.eggs.bonusStage)
            // 1st 1st 2nd 2nd 3rd, then the 1st again: that is step 1 of a new run, which then completes.
            e.tap(listOf(0, 0, 1, 1, 2, 0))
            e.tap(order.drop(1))
            assertTrue(e.eggs.bonusStage)
        } finally {
            e.close()
        }
    }

    @Test
    fun sixSecondsOverall() {
        val e = EngineRig()
        try {
            e.engine.onListShown(e.listId)
            // 8 taps 900 ms apart = 6.3 s: too slow.
            e.tap(order, gapMs = 900)
            assertFalse(e.eggs.bonusStage)
            // 8 taps 700 ms apart = 4.9 s from the first: in time.
            e.tap(order, gapMs = 700)
            assertTrue(e.eggs.bonusStage)
        } finally {
            e.close()
        }
    }

    @Test
    fun needsFourAisleStops() {
        // Only Produce, Bakery and Deli have items: three aisle stops.
        val s = PracticeFixture.state().let { st ->
            st.list(PracticeFixture.LIST_ID)!!.items.filter { it.aisleId !in setOf("produce", "bakery", "deli-meat") }
                .fold(st) { acc, it -> acc.removeItem(PracticeFixture.LIST_ID, it.id, PracticeFixture.NOW) }
        }
        val e = EngineRig(state = s)
        try {
            e.engine.onListShown(e.listId)
            assertEquals(3, EggRules.aisleStops(e.engine.grouped()!!))
            e.tap(listOf(0, 0, 1, 1, 2, 2, 2, 2))
            e.tap(order)
            assertFalse(e.eggs.bonusStage)
        } finally {
            e.close()
        }
    }

    @Test
    fun gapsAndEverythingElse() {
        // "Everything else" (first on the stamp page, its capsule stamp) plus aisle stops whose walking positions skip:
        // only Produce (1), Bakery (2), Baking (4) and Dairy (6) keep items.
        val keep = setOf("produce", "bakery", "aisle-2-baking", "dairy-eggs")
        val s: AppState = PracticeFixture.state().let { st ->
            st.list(PracticeFixture.LIST_ID)!!.items.filter { it.aisleId !in keep }
                .fold(st) { acc, it -> acc.removeItem(PracticeFixture.LIST_ID, it.id, PracticeFixture.NOW) }
        }.addItems(
            PracticeFixture.LIST_ID,
            listOf(GroceryItem(id = "card", name = "Birthday card", icon = "paper_bag", addedAt = PracticeFixture.NOW, source = ItemSource.TYPED)),
            PracticeFixture.NOW,
        ).first
        val e = EngineRig(state = s)
        try {
            e.engine.onListShown(e.listId)
            val g = e.engine.grouped()!!
            assertEquals(SectionKind.UNSORTED, g.sections.first().kind)
            assertEquals(listOf(1, 2, 4, 6), g.sections.drop(1).map { it.position })
            // The capsule stamp is not one of the four: tapping it mid-way restarts.
            e.tap(listOf(1, 1, 2, 0))
            e.tap(listOf(2, 3, 4, 3, 4))
            assertFalse(e.eggs.bonusStage)
            // By position among the aisle stamps (page indexes 1..4), whatever their printed numbers.
            e.tap(order.map { it + 1 })
            assertTrue(e.eggs.bonusStage)
        } finally {
            e.close()
        }
    }

    @Test
    fun onTheListRealTaps() {
        val r = EggRig(compose).start()
        val g = r.model.grouped()
        val firstFour = g.sections.filter { it.kind == SectionKind.AISLE }.take(4).map { it.id }
        order.forEach { k ->
            r.tapAt(r.anchor(AnchorRegistry.stampKey(firstFour[k])).center)
            r.advance(200)
        }
        val t0 = r.now
        assertTrue(r.eggs.bonusStage)
        assertTrue(r.eggs.pixelStickers)
        assertEquals("BONUS★ on the LCD", EggRules.BONUS, r.eggs.lcdWord?.text)
        assertEquals("BONUS STAGE! ♪ · tap\u00A0a\u00A0stamp\u00A0to\u00A0stop", r.model.toasts.current?.text)
        // The jingle every 4 s while the list shows.
        r.advanceTo(t0 + 8_500)
        val jingles = r.feedback.times(Cue.BONUS_JINGLE)
        assertTrue("jingles: $jingles", jingles.size >= 2)
        jingles.zipWithNext { a, b -> assertEquals(4_000.0, (b - a).toDouble(), 50.0) }
        // BONUS★ stays while the stage lasts (it has no hold of its own).
        assertEquals(EggRules.BONUS, r.eggs.lcdWord?.text)
        // A stamp tap ends it.
        r.tapAt(r.anchor(AnchorRegistry.stampKey(firstFour[2])).center)
        assertFalse(r.eggs.bonusStage)
        val after = r.feedback.times(Cue.BONUS_JINGLE).size
        r.advance(5_000)
        assertEquals("no jingle after it ends", after, r.feedback.times(Cue.BONUS_JINGLE).size)
    }

    @Test
    fun leavingTheListEndsIt() {
        val e = EngineRig()
        try {
            e.engine.onListShown(e.listId)
            e.tap(order)
            assertTrue(e.eggs.bonusStage)
            e.engine.onListHidden()
            assertFalse(e.eggs.bonusStage)
            assertFalse(e.eggs.pixelStickers)
        } finally {
            e.close()
        }
    }
}

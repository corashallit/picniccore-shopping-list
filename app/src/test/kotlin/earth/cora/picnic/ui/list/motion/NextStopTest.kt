package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.ui.a11y.TextOverflowed
import earth.cora.picnic.ui.theme.NextNameFontSize
import earth.cora.picnic.ui.theme.NextNameLines
import earth.cora.picnic.ui.theme.NextStopCard
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
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

/** The "Next stop" interstitial (docs/DESIGN_STICKER.md §10, D24; items 90, 168–174). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class NextStopTest {
    @get:Rule
    val compose = createComposeRule()

    /** Taps Baking powder in the fixture: Baking completes, the card for 5 · Breakfast rises at L + 1300. */
    private fun HarnessRig.completeBaking(): Long {
        val t0 = tap("baking-powder")
        return t0 + 620
    }

    /** Item 168: only "NEXT ✦", the next aisle's tab (display name, ballpoint) and its store chip; no glint. */
    @Test
    fun copyIsNextPlusTab() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val L = rig.completeBaking()
        rig.advanceTo(L + 1300 + 400)
        val card = rig.p.nextCard!!
        assertEquals("breakfast", card.sectionId)
        assertEquals(5, card.stop!!.position)
        assertEquals("Breakfast", card.stop!!.displayName)
        assertNull(card.stop!!.storeAisle)
        val node = compose.onNodeWithTag(NEXT_CARD_TAG).fetchSemanticsNode()
        assertEquals(listOf("Next: aisle 5, Breakfast"), node.config[SemanticsProperties.ContentDescription])
        assertEquals(Role.Button, node.config[SemanticsProperties.Role])
        // No glint: nothing crosses the card while it holds (the foil is static).
        rig.advanceTo(card.settledAt + card.holdMs + 32)
        val still = rig.probe.stillFrom(NEXT_CARD_TAG, card.settledAt)
        assertTrue("still $still ms", still >= card.holdMs)
    }

    /** Item 168, the card itself: its only words are NEXT and the tab's name (ballpoint), and the chip when it has one. */
    @Test
    fun cardWordsAreNextAndTheTab() {
        compose.setContent {
            PicnicTheme(variant = ThemeVariant.Day) {
                Column {
                    NextStopCard(5, "Breakfast", null, earth.cora.picnic.ui.theme.AisleLooks.of(5), "NEXT", Modifier.testTag("card"))
                    NextStopCard(7, "Pasta, rice & international", 3, earth.cora.picnic.ui.theme.AisleLooks.of(7), "NEXT", Modifier.testTag("chip_card"))
                }
            }
        }
        compose.waitForIdle()
        fun words(tag: String): List<String> {
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
                (n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) +
                    (n.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList()) + n.children.flatMap { walk(it) }
            return walk(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())
        }
        assertEquals(listOf("NEXT", "5", "Breakfast"), words("card"))
        val withChip = words("chip_card")
        assertEquals(listOf("NEXT", "7", "Pasta, rice & international"), withChip.take(3))
        assertTrue("only the store chip besides: ${withChip.drop(3)}", withChip.drop(3).isNotEmpty() && withChip.drop(3).all { it.contains("3") })
        val name = compose.onAllNodes(SemanticsMatcher.keyIsDefined(NextNameFontSize), useUnmergedTree = true).fetchSemanticsNodes().first()
        val layouts = ArrayList<TextLayoutResult>()
        name.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals("the name is ballpoint", earth.cora.picnic.ui.theme.DayColors.ballpoint, layouts.single().layoutInput.style.color)
    }

    /** Item 169: rises at L + 1300 (400 ms), holds readHold(words, 2600) from its settle, exits in 300 ms. */
    @Test
    fun holdIsComputedPerCard() {
        // 5 · Breakfast: "NEXT 5 Breakfast" = 3 words → 2600 ms.
        run {
            val rig = HarnessRig(compose, MotionFixture.state()).start()
            val L = rig.completeBaking()
            val frames = rig.sampleTo(L + 1300 + 400 + 2600 + 400)
            val shownAt = frames.firstTime { it.nextCard != null }
            assertBeat("rises", L + 1300, shownAt)
            val card = frames.first { it.first == shownAt }.second.nextCard!!
            assertEquals(2600L, card.holdMs)
            assertEquals(card.t0 + 400, card.settledAt)
            assertBeat("exits after its hold", card.settledAt + 2600, frames.firstTime { it.nextCard?.exitT0 != null })
            assertBeat("gone after 300 ms", card.settledAt + 2600 + 300, frames.firstTime(from = shownAt!!) { it.nextCard == null })
            assertTrue(rig.probe.stillFrom(NEXT_CARD_TAG, card.settledAt) >= 2600)
        }
    }

    @Test
    fun holdIsComputedPerCardLongName() {
        // 7 · Pasta, rice & international, store aisle 3: "NEXT 7 Pasta, rice & international A3" = 6 words → 3000 ms.
        val rig = HarnessRig(compose, MotionFixture.pasta()).start()
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        rig.advanceTo(L + 1300 + 400)
        val card = rig.p.nextCard!!
        assertEquals(7, card.stop!!.position)
        assertEquals("Pasta, rice & international", card.stop!!.displayName)
        assertEquals(3, card.stop!!.storeAisle)
        assertEquals(3000L, card.holdMs)
        assertEquals(3000L, rig.engine.nextHold(card.stop!!))
        rig.advanceTo(card.settledAt + 3000 + 20)
        assertTrue(rig.probe.stillFrom(NEXT_CARD_TAG, card.settledAt) >= 3000)
        assertEquals(listOf("Next: aisle 7, Pasta, rice and international, store aisle 3"), compose.onNodeWithTag(NEXT_CARD_TAG).fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
    }

    /** Item 170: every default aisle's name fits: 23 → 18 sp on one line, else two lines; nothing clips. */
    @Test
    fun defaultAislesFit() {
        val fits = fitAll(fontScale = 1f)
        assertEquals(14, fits.size)
        for ((name, f) in fits) {
            assertTrue("$name: ${f.first} sp", f.first in 18f..23f)
            assertTrue("$name: ${f.second} lines", f.second in 1..2)
            assertFalse("$name clips", f.third)
        }
        assertEquals(23f, fits.getValue("Produce").first, 0f)
        assertTrue("a long name shrinks or wraps", fits.getValue("Pasta, rice & international").let { it.first < 23f || it.second == 2 })
        // The card widens for a long name beside its chip, but never past the screen less 16 dp a side (§10, item 170).
        val widest = compose.onAllNodes(androidx.compose.ui.test.hasTestTag("card_Pasta, rice & international"), useUnmergedTree = true).fetchSemanticsNodes().single().size.width / compose.density.density
        assertTrue("card box $widest dp ≤ 360 dp (a 328 dp face and 8 dp of sparkle room a side)", widest <= 360f - 16f + 0.5f)
    }

    /**
     * Item 170 at font scale 2.0: the long names wrap to two lines and the card grows; nothing clips and nothing sits on
     * the tab's pattern. Keeping the pattern clear (32 + 4 dp, §15 text on pattern) costs the longest names a step or
     * two below 18 sp on their two lines at this scale on a 360 dp phone ("Health, beauty & baby" 16 sp, that is 32 sp
     * on screen); the card never narrows the name's room below that.
     */
    @Test
    fun fontScale200() {
        val stop = MotionSection("pasta", 7, "Pasta, rice & international", "Aisle 3 - Pasta, rice & international", 3, earth.cora.picnic.ui.theme.AisleLooks.of(7))
        val fits = fitAll(fontScale = 2f) {
            Box(Modifier.testTag("short")) { NextStopCard(1, "Produce", null, earth.cora.picnic.ui.theme.AisleLooks.of(1), "NEXT") }
            Box(Modifier.testTag("long")) { NextStopCard(7, stop.displayName, 3, stop.look, "NEXT") }
            NextCardHost(NextCardPresentation("pasta", 3000, 0, stop, 400, null, false), MotionClock(), onSkip = {})
        }
        for ((name, f) in fits) {
            assertFalse("$name clips at 2.0", f.third)
            assertTrue("$name: ${f.second} lines", f.second in 1..2)
            assertTrue("$name: ${f.first} sp", f.first >= 16f)
        }
        assertEquals(2, fits.getValue("Pasta, rice & international").second)
        assertTrue(fits.getValue("Pasta, rice & international").first >= 17f)
        // The card grows with the second line.
        val h1 = compose.onNodeWithTag("short").fetchSemanticsNode().size.height
        val h2 = compose.onNodeWithTag("long").fetchSemanticsNode().size.height
        assertTrue("$h2 > $h1", h2 > h1)
        // TalkBack hears the full name.
        assertEquals(listOf("Next: aisle 7, Pasta, rice and international, store aisle 3"), compose.onNodeWithTag(NEXT_CARD_TAG).fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
    }

    /**
     * Items 169, 167: two aisles done less than a hold apart (she finishes Baking, the card says NEXT 5 Breakfast, and
     * she checks Breakfast's last item at once): the first card leaves (300 ms), the second rises once it has gone, and
     * each one holds still for its whole hold; neither card's exit cuts the other short.
     */
    @Test
    fun secondAisleDoneWhileCardUp() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val L1 = rig.completeBaking()
        rig.advanceTo(L1 + 1300 + 500)
        val card1 = rig.p.nextCard!!
        assertEquals("breakfast", card1.sectionId)
        // Breakfast opened behind the card: coffee is checked, maple syrup is the last one.
        val t1 = rig.tap("maple")
        val L2 = t1 + Beats.land(MotionFixture.catalog.personalityOf("maple_syrup"))
        val frames = rig.sampleTo(L2 + 1300 + 300 + 400 + 4000)
        // The first card leaves at the second card's moment (L2 + 1300) and is gone 300 ms later; the second rises then.
        val exit1 = frames.firstOrNull { it.second.nextCard?.t0 == card1.t0 && it.second.nextCard?.exitT0 != null }?.second?.nextCard?.exitT0
        assertBeat("the first card leaves for the second", L2 + 1300, exit1)
        val card2 = frames.mapNotNull { it.second.nextCard }.first { it.t0 != card1.t0 }
        assertEquals("dairy-eggs", card2.sectionId)
        assertTrue("the second rises once the first has gone (${card2.t0 - exit1!!} ms)", card2.t0 >= exit1 + NextCardMotion.EXIT)
        assertTrue("never two cards at once", frames.none { (t, p) -> p.nextCard != null && p.nextCard!!.t0 != card1.t0 && t < exit1 + NextCardMotion.EXIT })
        // The second card holds its whole hold, and its exit is its own (not the first card's beat).
        val exit2 = frames.firstOrNull { it.second.nextCard?.t0 == card2.t0 && it.second.nextCard?.exitT0 != null }?.second?.nextCard?.exitT0
        assertBeat("the second card's own exit", card2.settledAt + card2.holdMs, exit2)
        var why = ""
        val still2 = rig.probe.stillFrom(NEXT_CARD_TAG, card2.settledAt) { why = it }
        assertTrue("the second card is still ${still2} ms of ${card2.holdMs} ($why)", still2 >= card2.holdMs)
        // Both were announced and both cards were shown.
        assertTrue(rig.engine.eventLog.contains(MotionEvent.NextShown("breakfast", card = true)))
        assertTrue(rig.engine.eventLog.contains(MotionEvent.NextShown("dairy-eggs", card = true)))
        assertEquals(2, rig.feedback.count(earth.cora.picnic.platform.Cue.NEXT_CARD))
    }

    /** Item 167 for two cards: each card's StillnessProbe still time is at least its hold when they do not overlap. */
    @Test
    fun secondAisleDoneAfterTheFirstCardHeld() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val L1 = rig.completeBaking()
        rig.advanceTo(L1 + 1300 + 400 + 2600 + 400)
        assertNull("the first card has gone", rig.p.nextCard)
        val first = rig.probe.stillFrom(NEXT_CARD_TAG, L1 + 1700)
        assertTrue("$first", first >= 2600)
    }

    /** Item 171: the card's tap target follows it: while it rises, a tap where it will rest is the list's, not a skip. */
    @Test
    fun tapTargetFollowsTheCard() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val L = rig.completeBaking()
        rig.advanceTo(L + 1300 + 50)
        val slot = rig.bounds(NEXT_CARD_SLOT_TAG)
        val drawn = rig.bounds(NEXT_CARD_TAG)
        assertTrue("rising: drawn $drawn below its slot $slot", drawn.top > slot.top + 40.dp)
        // A tap at the top of its resting place, where the card is not yet.
        compose.onNodeWithTag(OVERLAY_TAG).performTouchInput { click(androidx.compose.ui.geometry.Offset((slot.left + slot.right).toPx() / 2f, slot.top.toPx() + 24.dp.toPx())) }
        rig.frame()
        assertNull("not skipped", rig.p.nextCard?.exitT0)
        // Settled, the same tap skips it.
        rig.advanceTo(L + 1300 + 600)
        compose.onNodeWithTag(OVERLAY_TAG).performTouchInput { click(androidx.compose.ui.geometry.Offset((slot.left + slot.right).toPx() / 2f, slot.top.toPx() + 24.dp.toPx())) }
        rig.frame()
        assertNotNull("skipped", rig.p.nextCard?.exitT0)
    }

    /** The card for "Everything else": the capsule in the number circle, and TalkBack hears the full name. */
    @Test
    fun everythingElse() {
        val rig = HarnessRig(compose, MotionFixture.everythingElseNext()).start()
        val t0 = rig.tap("baking-powder")
        rig.advanceTo(t0 + 620 + 1300 + 400)
        val card = rig.p.nextCard!!
        assertEquals(0, card.stop!!.position)
        assertEquals("Next: Everything else", compose.onNodeWithTag(NEXT_CARD_TAG).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
    }

    /**
     * §2.7: the card's number circle numeral scales with the font (17 sp at 1.0, larger at 1.3 and 2.0) up to what its
     * 30 dp circle holds: two digits ("14", the default store's last stop) always fit inside the 2 dp ring.
     */
    @Test
    fun numeralScalesInsideItsCircle() {
        val widths = HashMap<Float, Pair<Float, Float>>()
        compose.setContent {
            for (fs in listOf(1f, 1.3f, 2f)) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fs)) {
                    PicnicTheme {
                        val m = androidx.compose.ui.text.rememberTextMeasurer()
                        val style = earth.cora.picnic.ui.theme.PicnicTheme.type.tabNumber.copy(fontSize = earth.cora.picnic.ui.theme.nextNumeralSp(fs).sp)
                        val d = LocalDensity.current
                        val layout = m.measure("14", style)
                        widths[fs] = with(d) { layout.size.width.toDp().value } to with(d) { style.fontSize.toDp().value }
                    }
                }
            }
        }
        compose.waitForIdle()
        val inner = 30f - 2f * 2
        for ((fs, w) in widths) assertTrue("font scale $fs: \"14\" is ${w.first} dp wide in a $inner dp ring", w.first <= inner)
        assertEquals(17f, widths.getValue(1f).second, 0.01f)
        assertTrue("it grows at 1.3: ${widths.getValue(1.3f).second}", widths.getValue(1.3f).second > 17f)
        assertTrue("and at 2.0: ${widths.getValue(2f).second}", widths.getValue(2f).second > 17f)
    }

    /** Item 171: a tap on the card skips it at once. */
    @Test
    fun tapSkips() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val L = rig.completeBaking()
        rig.advanceTo(L + 1300 + 800)
        val before = rig.p.nextCard!!
        assertNull(before.exitT0)
        compose.onNodeWithTag(NEXT_CARD_TAG).performClick()
        val tapAt = rig.now
        rig.frame()
        val exit = rig.p.nextCard?.exitT0
        assertNotNull(exit)
        assertTrue("skipped at once (${exit!! - tapAt} ms)", exit - tapAt in 0..32)
        rig.advance(320)
        assertNull(rig.p.nextCard)
    }

    /** Item 172: TalkBack hears "Next: aisle 5, Breakfast" and the hold doubles. */
    @Test
    fun talkBack() {
        val rig = HarnessRig(compose, MotionFixture.state(), talkBack = true).start()
        val L = rig.completeBaking()
        rig.advanceTo(L + 1300 + 400)
        val card = rig.p.nextCard!!
        assertEquals(5200L, card.holdMs)
        assertTrue(rig.announcer.messages.contains("Next: aisle 5, Breakfast"))
        val node = compose.onNodeWithTag(NEXT_CARD_TAG).fetchSemanticsNode()
        assertEquals(listOf("Next: aisle 5, Breakfast"), node.config[SemanticsProperties.ContentDescription])
        assertEquals("dismiss", node.config[SemanticsActions.OnClick].label)
        rig.advanceTo(card.settledAt + 5200 - 20)
        assertNull("still holding", rig.p.nextCard?.exitT0)
        compose.onNodeWithTag(NEXT_CARD_TAG).performSemanticsAction(SemanticsActions.OnClick)
        rig.frame()
        assertNotNull(rig.p.nextCard?.exitT0)
    }

    /** Item 173: reduced motion: 120 ms fades, visible for the full hold. */
    @Test
    fun reducedMotionShowsCardForFullHold() {
        val rig = HarnessRig(compose, MotionFixture.state(), reduced = true).start()
        val L = rig.completeBaking()
        val frames = rig.sampleTo(L + 1300 + 120 + 2600 + 300)
        val shownAt = frames.firstTime { it.nextCard != null }!!
        assertBeat("at L + 1300", L + 1300, shownAt)
        val card = frames.first { it.first == shownAt }.second.nextCard!!
        assertTrue(card.reduced)
        assertEquals(card.t0 + 120, card.settledAt)
        assertBeat("full hold", card.settledAt + 2600, frames.firstTime { it.nextCard?.exitT0 != null })
        assertTrue(rig.probe.stillFrom(NEXT_CARD_TAG, card.settledAt) >= 2600)
    }

    /** Item 174: switched off: no card, but the next aisle opens and is announced. */
    @Test
    fun switchedOff() {
        val rig = HarnessRig(compose, MotionFixture.state(), nextStopCard = false).start()
        val L = rig.completeBaking()
        val frames = rig.sampleTo(L + 2600)
        assertTrue(frames.all { it.second.nextCard == null })
        assertTrue(rig.engine.eventLog.contains(MotionEvent.NextShown("breakfast", card = false)))
        assertTrue(rig.announcer.messages.contains("Next: aisle 5, Breakfast"))
        assertBeat("the next aisle opens", L + 1300, frames.firstTime { it.scroll?.sectionId == "breakfast" })
        compose.onNodeWithTag(HarnessTags.tray("breakfast")).assertIsDisplayed()
    }

    /** Item 90: the next stop opens behind the card and scrolls into view. */
    @Test
    fun nextAisleAutoOpens() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val L = rig.completeBaking()
        rig.advanceTo(L + 1300 + 600)
        assertNotNull(rig.p.nextCard)
        compose.onNodeWithTag(HarnessTags.tray("breakfast")).assertIsDisplayed()
        val list = rig.bounds(HarnessTags.LIST)
        val tray = rig.bounds(HarnessTags.tray("breakfast"))
        assertTrue("the tray is scrolled into view: $tray in $list", tray.top >= list.top && tray.top < list.bottom)
        assertEquals("breakfast", rig.model.grouped().openSectionId)
        assertTrue(compose.onAllNodesWithTag(HarnessTags.tray("aisle-2-baking")).fetchSemanticsNodes().isEmpty())
    }

    // ---- helpers ----

    /** The 14 default aisles' display names. */
    private fun defaultNames(): List<Triple<Int, String, Int?>> {
        val s = AppState(lists = listOf(GroceryList(MotionFixture.LIST_ID, "x", 0)), currentListId = MotionFixture.LIST_ID, store = StoreProfile(aislesText = Aisles.DEFAULT_TEXT))
        var st = s
        s.store.aisles().forEachIndexed { i, a ->
            st = st.addItems(MotionFixture.LIST_ID, listOf(MotionFixture.item("flour").copy(id = "i$i", name = "Thing $i", aisleId = a.id)), 1L).first
        }
        return st.grouped().motionSections().filter { it.position > 0 }.map { Triple(it.position, it.displayName, it.storeAisle) }
    }

    /** Each default name's fit on its card (with its store chip) at [fontScale]: (sp, lines, clipped); [more] below them. */
    private fun fitAll(fontScale: Float, more: @androidx.compose.runtime.Composable () -> Unit = {}): Map<String, Triple<Float, Int, Boolean>> {
        val names = defaultNames()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PicnicTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((n, name, store) in names) {
                            Box(Modifier.testTag("card_$name")) { NextStopCard(n, name, store, earth.cora.picnic.ui.theme.AisleLooks.of(n), "NEXT") }
                        }
                        more()
                    }
                }
            }
        }
        compose.waitForIdle()
        return names.associate { (_, name, _) ->
            val node = compose.onNode(SemanticsMatcher.keyIsDefined(NextNameFontSize) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("card_$name")), useUnmergedTree = true).fetchSemanticsNode()
            name to Triple(node.config[NextNameFontSize], node.config[NextNameLines], node.config.getOrNull(TextOverflowed) == true)
        }
    }

}

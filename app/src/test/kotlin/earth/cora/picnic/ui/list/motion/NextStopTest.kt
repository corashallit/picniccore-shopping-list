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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
        val rig = HarnessRig(compose, pastaState()).start()
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
    }

    /** Item 170 at font scale 2.0: the long names wrap to two lines at 18 sp and the card grows; nothing clips. */
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
        }
        assertEquals(2, fits.getValue("Pasta, rice & international").second)
        assertEquals(18f, fits.getValue("Pasta, rice & international").first, 0f)
        // The card grows with the second line.
        val h1 = compose.onNodeWithTag("short").fetchSemanticsNode().size.height
        val h2 = compose.onNodeWithTag("long").fetchSemanticsNode().size.height
        assertTrue("$h2 > $h1", h2 > h1)
        // TalkBack hears the full name.
        assertEquals(listOf("Next: aisle 7, Pasta, rice and international, store aisle 3"), compose.onNodeWithTag(NEXT_CARD_TAG).fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
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

    /** A store with the default 14 aisles: Baking & spices nearly done, spaghetti in Pasta (stop 7, store aisle 3). */
    private fun pastaState(): AppState {
        var s = AppState(
            lists = listOf(GroceryList(MotionFixture.LIST_ID, "Saturday shop", MotionFixture.WRITTEN)),
            currentListId = MotionFixture.LIST_ID,
            store = StoreProfile(name = MotionFixture.STORE, aislesText = Aisles.DEFAULT_TEXT),
        )
        val aisles = s.store.aisles()
        val baking = aisles.first { it.name.contains("Baking") }.id
        val pasta = aisles.first { it.name.contains("Pasta") }.id
        val items = listOf(
            MotionFixture.item("flour").copy(aisleId = baking),
            MotionFixture.item("sugar").copy(aisleId = baking),
            MotionFixture.item("baking-powder").copy(aisleId = baking),
            MotionFixture.item("flour").copy(id = "spaghetti", name = "Spaghetti", quantity = "900 g box", aisleId = pasta, icon = "spaghetti"),
        )
        for (it in items) s = s.addItems(MotionFixture.LIST_ID, listOf(it), MotionFixture.WRITTEN).first
        s = s.toggleChecked(MotionFixture.LIST_ID, "flour", MotionFixture.TRIP_START)
        s = s.toggleChecked(MotionFixture.LIST_ID, "sugar", MotionFixture.TRIP_START + 60_000)
        return s.setOpenAisle(MotionFixture.LIST_ID, baking)
    }

    /** The 14 default aisles' display names. */
    private fun defaultNames(): List<Pair<Int, String>> {
        val s = AppState(lists = listOf(GroceryList(MotionFixture.LIST_ID, "x", 0)), currentListId = MotionFixture.LIST_ID, store = StoreProfile(aislesText = Aisles.DEFAULT_TEXT))
        var st = s
        s.store.aisles().forEachIndexed { i, a ->
            st = st.addItems(MotionFixture.LIST_ID, listOf(MotionFixture.item("flour").copy(id = "i$i", name = "Thing $i", aisleId = a.id)), 1L).first
        }
        return st.grouped().motionSections().filter { it.position > 0 }.map { it.position to it.displayName }
    }

    /** Each default name's fit on the card at [fontScale]: (sp, lines, clipped); [more] is composed below them. */
    private fun fitAll(fontScale: Float, more: @androidx.compose.runtime.Composable () -> Unit = {}): Map<String, Triple<Float, Int, Boolean>> {
        val names = defaultNames()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PicnicTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((n, name) in names) {
                            Box(Modifier.testTag("card_$name")) { NextStopCard(n, name, null, earth.cora.picnic.ui.theme.AisleLooks.of(n), "NEXT") }
                        }
                        more()
                    }
                }
            }
        }
        compose.waitForIdle()
        return names.associate { (_, name) ->
            val node = compose.onNode(SemanticsMatcher.keyIsDefined(NextNameFontSize) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("card_$name")), useUnmergedTree = true).fetchSemanticsNode()
            name to Triple(node.config[NextNameFontSize], node.config[NextNameLines], node.config.getOrNull(TextOverflowed) == true)
        }
    }

}

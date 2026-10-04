package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.store.BookStamp
import earth.cora.picnic.core.store.BookSticker
import earth.cora.picnic.core.store.TripBook
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.isCut
import earth.cora.picnic.ui.theme.PicnicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Trip book sheet's sticker pages (docs/DESIGN_STICKER.md §6.6, §15; item 163): a book of 30 holds two pages at
 * 360 dp, and page 2 is reachable without the swipe, by the pages' TalkBack actions and by the round next and previous
 * buttons; the dots and the LCD say "Page 2 of 2".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TripBookSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val keys = listOf("strawberry", "banana", "apple", "carrot", "broccoli", "croissant", "baguette", "flour", "sugar", "coffee")

    private fun book(total: Int) = TripBook(
        id = "trip-1",
        listTitle = "Saturday shop",
        storeName = "Hillcrest Market",
        startedAt = ListFixture.TRIP_START,
        completedAt = null,
        closedAt = ListFixture.NOW,
        total = total,
        minutes = null,
        stickers = (0 until total).map { BookSticker(it, "Thing ${it + 1}", IconRef(keys[it % keys.size]), Rarity.NONE, ListFixture.TRIP_START + it) },
        stamps = listOf(BookStamp(1, "Produce", "strawberry", "gingham", "strawberry", done = true)),
        perfect = false,
        receipt = null,
    )

    private fun actions() = compose.onNodeWithTag(TripBookTags.PAGES).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()

    private fun pageOf(): String = compose.onNodeWithTag(TripBookTags.PAGE_OF).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

    @Test
    fun pagesTurnWithoutTheGesture() {
        compose.setContent { PicnicTheme { Box(Modifier.fillMaxSize()) { TripBookSheet(book(30), dateLabel = "Sat 3 Oct", onClose = {}) } } }
        compose.waitForIdle()
        assertEquals("Page 1 of 2", pageOf())
        assertTrue("sticker 25 waits on page 2", !compose.exists(TripBookTags.sticker(24)))
        assertEquals("page 1 offers only the next page", listOf("Next page"), actions().map { it.label })

        // TalkBack: the pages' own action.
        compose.runOnIdle { actions().single { it.label == "Next page" }.action() }
        compose.waitForIdle()
        assertEquals("Page 2 of 2", pageOf())
        assertTrue("the sticker in slot 25 shows", compose.exists(TripBookTags.sticker(24)))
        assertFalse("page 1's stickers are gone", compose.exists(TripBookTags.sticker(0)))
        assertEquals(listOf("Previous page"), actions().map { it.label })
        assertEquals("the dots and the LCD announce a turn", androidx.compose.ui.semantics.LiveRegionMode.Polite, compose.onNodeWithTag(TripBookTags.PAGE_OF).fetchSemanticsNode().config[SemanticsProperties.LiveRegion])

        // The visible buttons (48 dp targets) turn back and forth.
        val previous = compose.onNodeWithTag(TripBookTags.PREVIOUS)
        val bounds = previous.fetchSemanticsNode().boundsInRoot
        assertTrue("a 48 dp target", bounds.width >= 48 * compose.density.density - 0.5f && bounds.height >= 48 * compose.density.density - 0.5f)
        previous.performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("Page 1 of 2", pageOf())
        compose.onNodeWithTag(TripBookTags.NEXT).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("Page 2 of 2", pageOf())
    }

    /**
     * §6.6, §15 "names wrap, never clip" (items 163, 165, 281): at font scale 1.3 and 2.0 every aisle name under a stamp
     * is whole (no ellipsis, no line wider than its cell, nothing cut at the bottom) and wraps only between words, for
     * the fixture's trip and for the 14 default aisles' long names ("Pasta, rice & international").
     */
    @Test
    fun stampNamesWrapBetweenWordsAtLargeText() {
        for (scale in listOf(1.3f, 2f)) {
            stampNamesWhole(ListFixture.ui(), scale)
            stampNamesWhole(ListFixture.ui(ListFixture.everyDefaultAisle()), scale)
        }
    }

    /** §6.6: the sheet's facts break only between facts, after the dot; a fact that fits a line is never split. */
    @Test
    fun factsBreakBetweenFacts() {
        val facts = listOf("24 of 24 stickers", "8 of 8 aisles stamped", "41 min")
        assertEquals("24 of 24 stickers · 8 of 8 aisles stamped · 41 min", factLines(facts, 60) { it.length })
        assertEquals("24 of 24 stickers ·\n8 of 8 aisles stamped · 41 min", factLines(facts, 30) { it.length })
        assertEquals("24 of 24 stickers ·\n8 of 8 aisles stamped ·\n41 min", factLines(facts, 23) { it.length })
    }

    private var content by androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)

    private fun stampNamesWhole(ui: ListUiState, scale: Float) {
        val probe = RecordingProbe()
        if (content == null) compose.setContent { content?.invoke() }
        compose.runOnIdle {
            content = {
                val base = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    earth.cora.picnic.ui.theme.LocalPieceProbe provides probe,
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, scale),
                ) {
                    PicnicTheme {
                        Box(Modifier.fillMaxSize()) {
                            androidx.compose.runtime.key(ui, scale) { TripBookSheet(ui.tripBook, dateLabel = "Sat 3 Oct", onClose = {}) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val stamps = ui.tripBook!!.stamps
        assertTrue("the fixture has stamps", stamps.isNotEmpty())
        stamps.forEachIndexed { i, s ->
            val layout = probe.texts[TripBookTags.stampName(i)] ?: error("no name laid out under stamp $i (${s.aisleName}) at $scale")
            val text = layout.layoutInput.text.text
            assertEquals(stampName(s.aisleName), text)
            assertFalse("\"$text\" is cut at font scale $scale", layout.isCut())
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                val last = text[end - 1]
                // "&" goes with the word after it: "Deli / & meat", like "Dairy / & eggs".
                assertFalse("\"$text\" leaves \"&\" at a line's end at font scale $scale", text.substring(layout.getLineStart(line), end).trimEnd().endsWith("&"))
                assertTrue(
                    "\"$text\" breaks inside a word at font scale $scale: \"${text.substring(layout.getLineStart(line), end)}\" / \"${text.substring(end)}\"",
                    last.isWhitespace() || last == '-',
                )
            }
        }
    }

    @Test
    fun onePageHasNoPaging() {
        compose.setContent { PicnicTheme { Box(Modifier.fillMaxSize()) { TripBookSheet(book(12), dateLabel = "Sat 3 Oct", onClose = {}) } } }
        compose.waitForIdle()
        assertTrue(actions().isEmpty())
        assertFalse(compose.exists(TripBookTags.NEXT))
        assertFalse(compose.exists(TripBookTags.PAGE_OF))
    }

    /**
     * Egg 8 on a filed book (§12.2, item 245; integration of part 3): a trip closed with a basket still empty files the
     * aisle the nap cat slept in (TripBook.napAisleId), and the book's stamp page shows the cat by that aisle's stamp,
     * matched by the aisle's id ("aisle-2-baking" for the shown name "Baking"); none on a book without one.
     */
    @Test
    fun theNapCatSleepsOnItsFiledStamp() {
        val stamps = listOf(
            BookStamp(1, "Produce", "strawberry", "gingham", "strawberry", done = true),
            BookStamp(4, "Baking", "lemon", "dots", "flower", done = false),
        )
        val napped = book(4).copy(stamps = stamps, napAisleId = "aisle-2-baking")
        assertEquals(1, napped.napStampIndex())
        assertEquals(null, napped.copy(napAisleId = null).napStampIndex())
        assertEquals(null, napped.copy(napAisleId = "frozen").napStampIndex())
        assertEquals(0, napped.copy(napAisleId = "produce").napStampIndex())
        var shown by androidx.compose.runtime.mutableStateOf(napped)
        compose.setContent { PicnicTheme { Box(Modifier.fillMaxSize()) { TripBookSheet(shown, dateLabel = "Sat 3 Oct", onClose = {}) } } }
        compose.waitForIdle()
        assertTrue("the cat on the filed book's page", compose.exists(TripBookTags.NAP_CAT, unmerged = true))
        assertTrue("decorative: TalkBack reads the stamps, not the cat", compose.onNodeWithTag(TripBookTags.NAP_CAT, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription) == null)
        compose.runOnIdle { shown = napped.copy(napAisleId = null) }
        compose.waitForIdle()
        assertFalse("no cat without one", compose.exists(TripBookTags.NAP_CAT, unmerged = true))
    }
}

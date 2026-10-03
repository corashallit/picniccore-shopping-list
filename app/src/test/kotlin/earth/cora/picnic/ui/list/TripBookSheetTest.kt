package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

    @Test
    fun onePageHasNoPaging() {
        compose.setContent { PicnicTheme { Box(Modifier.fillMaxSize()) { TripBookSheet(book(12), dateLabel = "Sat 3 Oct", onClose = {}) } } }
        compose.waitForIdle()
        assertTrue(actions().isEmpty())
        assertFalse(compose.exists(TripBookTags.NEXT))
        assertFalse(compose.exists(TripBookTags.PAGE_OF))
    }
}

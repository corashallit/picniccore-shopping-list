package earth.cora.picnic.ui.album

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.store.InMemoryAlbumStore
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sticker album (checklist 165, 230, 231): counts, tabs, pockets, the magic appear, the filed books. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class AlbumTest {
    @get:Rule
    val compose = createComposeRule()

    private fun lifted(on: Boolean) = SemanticsMatcher.expectValue(PocketLifted, on)

    @Test
    fun tabsAndCounts() {
        var state by mutableStateOf(AlbumSamples.state(AlbumSamples.mockup))
        compose.setContent {
            PicnicTheme { AlbumScreen(state, AlbumActions(onSelectTab = { state = AlbumSamples.state(AlbumSamples.mockup, it) })) }
        }
        // Collectible = the catalog's keys minus its generics: 216 (§6.10).
        assertEquals(216, state.collectible)
        assertEquals(16, state.collected)
        assertEquals(3, state.holo)
        compose.onNodeWithText("16 of 216 collected · 3 holo").assertExists()
        compose.onNodeWithContentDescription("16 of 216 stickers collected").assertExists()

        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(3)
        compose.onNodeWithText("Groceries").assertIsSelected()
        compose.onNodeWithText("Fruit").assertExists()
        assertTrue(compose.onAllNodesWithContentDescription("Not collected yet").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("Apples, rarity 3 of 3").assertExists()
        compose.onNodeWithContentDescription("Lemons, holo, rarity 3 of 3").assertExists()

        compose.onNodeWithText("Picnic").performClick()
        compose.onNodeWithText("Picnic").assertIsSelected()
        compose.onNodeWithText("Groceries").assertIsNotSelected()
        compose.onNodeWithText("Picnic sheet").assertExists()
        compose.onNodeWithText("Sakura sheet").assertExists()
        compose.onNodeWithContentDescription("Bee, rarity 1 of 3").assertExists()

        compose.onNodeWithText("Seasonal").performClick()
        compose.onNodeWithText("Seasonal").assertIsSelected()
        compose.onNodeWithText("Autumn sheet").assertExists()
        // 3 October 2026: Thanksgiving (the 12th) is the one coming up, though its window has begun.
        compose.onNodeWithText("next! · Thanksgiving Oct 12").assertExists()
        compose.onNodeWithContentDescription("Red envelope, not collected yet", substring = true).assertExists()
    }

    @Test
    fun earlyAlbumHasNoHoloInItsSubtitle() {
        compose.setContent { PicnicTheme { AlbumScreen(AlbumSamples.state(AlbumSamples.early), AlbumActions()) } }
        compose.onNodeWithText("5 of 216 collected").assertExists()
    }

    @Test
    fun pocketTapLifts() {
        compose.setContent { PicnicTheme { AlbumScreen(AlbumSamples.state(AlbumSamples.mockup), AlbumActions()) } }
        val apple = compose.onNodeWithTag(AlbumTags.pocket("apple"))
        apple.assert(lifted(false))
        apple.performClick()
        apple.assert(lifted(true))
        // Tap again: it drops.
        apple.performClick()
        apple.assert(lifted(false))
        // Lift, then tap elsewhere (an empty pocket): it drops.
        apple.performClick()
        apple.assert(lifted(true))
        compose.onAllNodesWithContentDescription("Not collected yet")[0].performClick()
        apple.assert(lifted(false))
        // Lifting another drops the first.
        apple.performClick()
        compose.onNodeWithTag(AlbumTags.pocket("banana")).performClick()
        apple.assert(lifted(false))
        compose.onNodeWithTag(AlbumTags.pocket("banana")).assert(lifted(true))
    }

    @Test
    fun facePocketBlinksOnLongPress() {
        compose.setContent { PicnicTheme { AlbumScreen(AlbumSamples.state(AlbumSamples.mockup), AlbumActions()) } }
        compose.onNodeWithTag(AlbumTags.pocket("apple")).performTouchInput { longClick() }
        compose.onNodeWithTag(AlbumTags.pocket("apple")).assert(SemanticsMatcher.expectValue(PocketBlinks, 1))
        // A pocket without a face doesn't blink.
        compose.onNodeWithTag(AlbumTags.pocket("banana")).performTouchInput { longClick() }
        compose.onNodeWithTag(AlbumTags.pocket("banana")).assert(SemanticsMatcher.expectValue(PocketBlinks, 0))
    }

    @Test
    fun newPocketsAppearOnce() {
        val store = InMemoryAlbumStore(AlbumSamples.fresh)
        var appeared by mutableIntStateOf(0)
        val vm = AlbumViewModel(store, AlbumSamples.catalog, { AlbumSamples.today }, now = { AlbumSamples.now })
        var screenOnly by mutableStateOf(false)
        compose.setContent {
            PicnicTheme {
                if (!screenOnly) {
                    AlbumRoute(vm, onBack = {}, onBackToList = {}, onOpenMyStore = {}, onOpenSettings = {})
                } else {
                    AlbumScreen(
                        AlbumSamples.state(AlbumSamples.fresh, fresh = setOf("peach")),
                        AlbumActions(onAppeared = { if (it == "peach") appeared++ }),
                    )
                }
            }
        }
        // The peach pocket was collected and never shown: it magic-appears, then it is marked seen.
        compose.waitUntil(5_000) { runBlocking { store.album.first() }.collected["peach"]?.seen == true }
        assertFalse(vm.state.value.groceries.flatMap { it.pockets }.first { it.key == "peach" }.fresh)
        // Only the fresh pocket was marked; the others were seen already.
        assertTrue(runBlocking { store.album.first() }.collected.values.all { it.seen })

        // Opening the album again: nothing is fresh, nothing plays.
        val again = AlbumViewModel(store, AlbumSamples.catalog, { AlbumSamples.today }, now = { AlbumSamples.now })
        compose.waitUntil(5_000) { !again.state.value.loading }
        assertTrue(again.state.value.groceries.flatMap { it.pockets }.none { it.fresh })

        // The screen reports each appear once.
        screenOnly = true
        compose.waitUntil(5_000) { appeared > 0 }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(1, appeared)
    }

    @Test
    fun tripBooksListedAndOpen() {
        compose.setContent { PicnicTheme { AlbumScreen(AlbumSamples.state(AlbumSamples.mockup), AlbumActions()) } }
        compose.onNodeWithTag(AlbumTags.LIST).performScrollToKey("books")
        compose.onNodeWithText("Trip sticker books").assertExists()
        val newest = compose.onNodeWithText("Sat 26 Sep", substring = true).getBoundsInRoot()
        val older = compose.onNodeWithText("Sat 19 Sep", substring = true).getBoundsInRoot()
        assertTrue("newest first", newest.top < older.top)
        compose.onNodeWithText("19 of 19 stickers · perfect · receipt", substring = true).assertExists()
        compose.onNodeWithText("21 of 22 stickers", substring = true).assertExists()

        compose.onNodeWithText("Sat 26 Sep", substring = true).performClick()
        compose.onNodeWithText("19 of 19 stickers · 8 of 8 aisles stamped · 41 min").assertExists()
        compose.onNodeWithContentDescription("Produce: stamped").assertExists()
        compose.onNodeWithText("Receipt").assertExists()
        compose.onAllNodesWithText("Hillcrest Market", substring = true).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        // Read-only: nothing in the book can be edited.
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}

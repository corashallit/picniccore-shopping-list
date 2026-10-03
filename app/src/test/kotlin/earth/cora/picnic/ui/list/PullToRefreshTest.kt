package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.PicnicMotion
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
 * Pull to refresh (docs/DESIGN_STICKER.md §6.1, D18; item 119): at the top, a pull sags the bunting; a release past
 * 40 dp spins the sun, shows SYNC on the LCD for 1500 ms and re-syncs the icons and "Everything else" (never prices);
 * the overflow item "Refresh" and the list's custom action "Refresh stickers and aisles" do the same without the gesture.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class PullToRefreshTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    /** The fixture plus two things still in "Everything else". */
    private fun unsorted(): AppState {
        var s = ListFixture.state()
        for ((i, name) in listOf("Birthday candles", "Paper plates").withIndex()) {
            val item = GroceryItem(id = "u$i", name = name, aisleId = null, icon = "paper_bag", iconFit = IconFit.PENDING, addedAt = ListFixture.NOW - 1000 + i)
            s = s.addItems(ListFixture.LIST_ID, listOf(item), item.addedAt).first
        }
        return s
    }

    private fun pull(distanceDp: Float) {
        compose.onNodeWithTag(ListTags.LIST).performTouchInput {
            down(Offset(centerX, top + 20 * dp))
            val steps = 6
            repeat(steps) { moveBy(Offset(0f, distanceDp * dp / steps)) }
        }
    }

    /** The gesture on the stateless screen: the bunting sags while held, the release spins the sun and shows SYNC. */
    @Test
    fun gestureSagsBuntingSpinsSunAndSyncs() {
        var refreshed = 0
        val screen = ListScreenState()
        compose.setContent {
            PicnicTheme { Box(Modifier.fillMaxSize()) { ListScreen(ListFixture.ui(), ListActions(onRefresh = { refreshed++ }), screen = screen) } }
        }
        compose.waitForIdle()
        // A short pull (30 dp of finger, 18 dp of list) does nothing on release.
        pull(30f)
        compose.waitForIdle()
        assertTrue("the bunting shows while she pulls", compose.exists(ListTags.BUNTING))
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(0, refreshed)
        assertFalse("the bunting rises away", compose.exists(ListTags.BUNTING))

        compose.mainClock.autoAdvance = false
        pull(140f)
        compose.frames(2)
        assertEquals("the list follows at 0.6×, at most 70 dp", 70f, screen.pull / dp, 0.5f)
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { up() }
        compose.frames(3)
        assertEquals("one re-sync", 1, refreshed)
        assertEquals("the sun spins once", 1, screen.sunSpin)
        assertTrue("the LCD reads SYNC", screen.syncing)
        compose.advanceMs(PicnicMotion.floorLcdWord - 100)
        assertTrue("SYNC holds 1500 ms", screen.syncing)
        assertTrue(compose.framesUntil(300) { !screen.syncing })
        compose.mainClock.autoAdvance = true
    }

    /** The real list: the gesture resumes pending stickers and sorts "Everything else"; the note says what changed. */
    @Test
    fun resyncsIconsAndAisles() {
        val rig = ListRig(unsorted())
        rig.assistant.recategorize = { items, _ -> items.associate { it.id to "aisle-2-baking" } }
        compose.show(rig)
        compose.mainClock.autoAdvance = false
        pull(140f)
        compose.frames(2)
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { up() }
        compose.advanceMs(400)
        assertEquals("pending stickers resume", 1, rig.icons.resumed)
        assertEquals(1, rig.feedback.count(Cue.PULL_REFRESH))
        assertTrue("both sorted into Baking", rig.app.items().filter { it.id.startsWith("u") }.all { it.aisleId == "aisle-2-baking" })
        assertTrue(compose.framesUntil(600) { compose.exists(earth.cora.picnic.ui.theme.TOAST_TAG) })
        compose.onNodeWithText("Sorted 2 items", useUnmergedTree = true).assertExists()
        val words = compose.onAllNodes(hasTagPrefix(earth.cora.picnic.ui.theme.TOAST_TAG), useUnmergedTree = true).fetchSemanticsNodes().toString()
        assertFalse("never prices", Regex("price|\\$|sale", RegexOption.IGNORE_CASE).containsMatchIn(words))
        compose.mainClock.autoAdvance = true
    }

    /** The overflow menu's Refresh and the list's custom action re-sync without the gesture. */
    @Test
    fun refreshAction() {
        val rig = ListRig(unsorted())
        rig.assistant.recategorize = { items, _ -> items.associate { it.id to null } }
        compose.show(rig)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Refresh").performClick()
        compose.waitForIdle()
        assertEquals(1, rig.icons.resumed)
        assertEquals("asked about the two in Everything else", 1, rig.assistant.recategorized.size)
        val action = compose.onNodeWithTag(ListTags.LIST).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Refresh stickers and aisles" }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
        assertEquals(2, rig.icons.resumed)
        assertEquals(2, rig.assistant.recategorized.size)
    }
}

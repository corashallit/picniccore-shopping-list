package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.assistant.IconDecision
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.core.store.TripBook
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.album.AlbumSamples
import earth.cora.picnic.ui.album.FiledBookSheet
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.pantry.PantryRoute
import earth.cora.picnic.ui.pantry.PantryViewModel
import earth.cora.picnic.ui.theme.LETTERSET_TAG
import earth.cora.picnic.ui.theme.LocalPieceProbe
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every letter-set sheet slides away as she saw it (docs/DESIGN_STICKER.md §4.16, item 367), as the Add sheet does with
 * its card (AddSheetMotionTest.keepsItsCardWhileItLeaves): closed, it keeps showing what it showed open, at that height,
 * to its last frame (it never glides down to empty paper because its host has already let go of the book, the list or
 * the item), and it is seen sliding for several frames (it never vanishes in one).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class SheetsLeaveTest {
    @get:Rule
    val compose = createComposeRule()

    /** Where a sheet's paper is laid out (not cut by the screen's edge as it slides off), the [which]th one up. */
    private fun paper(which: Int = 0): Rect? =
        compose.onAllNodes(hasTestTag(LETTERSET_TAG)).fetchSemanticsNodes().getOrNull(which)?.let(::laidOut)

    private fun laidOut(node: SemanticsNode) = Rect(node.positionInRoot, node.size.toSize())

    /**
     * Runs [close] on the held clock and follows the [which]th sheet out frame by frame: it keeps the height it had open
     * and only slides down, [also] holds in every frame it shows, it shows for several frames, and then it is gone.
     */
    private fun leaves(close: () -> Unit, which: Int = 0, also: (frame: Int) -> Unit = {}) {
        compose.waitForIdle()
        val before = paper(which) ?: error("no sheet up")
        compose.mainClock.autoAdvance = false
        close()
        var shown = 0
        var lastTop = before.top
        repeat(30) { frame ->
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            val b = paper(which) ?: return@repeat
            shown++
            assertEquals("frame $frame: the sheet keeps its height as it leaves", before.height, b.height, 1f)
            assertTrue("frame $frame: it only slides down (${b.top} after $lastTop)", b.top >= lastTop - 1f)
            lastTop = b.top
            also(frame)
        }
        assertTrue("the sheet was seen leaving ($shown frames)", shown >= 4)
        assertEquals("then it is gone", null, paper(which))
        compose.mainClock.autoAdvance = true
    }

    /** §6.6: the Trip book sheet leaves with the book her list has already let go of (book null), its stamps still laid out. */
    @Test
    fun tripBookLeavesWithItsBook() {
        val probe = RecordingProbe()
        var book by mutableStateOf<TripBook?>(AlbumSamples.perfectBook)
        compose.setContent {
            CompositionLocalProvider(LocalPieceProbe provides probe) {
                PicnicTheme { Box(Modifier.fillMaxSize()) { TripBookSheet(book, dateLabel = if (book != null) "Sat 26 Sep" else "", onClose = { book = null }) } }
            }
        }
        compose.waitForIdle()
        assertTrue("its stamps are laid out", probe.bounds.containsKey(TripBookTags.stampName(0)))
        leaves(close = { book = null }) { frame ->
            assertTrue("frame $frame: its stamps are still laid out", probe.bounds.containsKey(TripBookTags.stampName(0)))
        }
    }

    /** §6.10: the album's filed book sheet does the same. */
    @Test
    fun filedBookLeavesWithItsBook() {
        var book by mutableStateOf<TripBook?>(AlbumSamples.partBook)
        compose.setContent {
            PicnicTheme { Box(Modifier.fillMaxSize()) { FiledBookSheet(book, dateLabel = if (book != null) "Sat 19 Sep" else "", onClose = { book = null }) } }
        }
        leaves(close = { book = null })
    }

    /** §6.5: answered, New trip leaves with the book it was closing, not the fresh page the list turned to (or none). */
    @Test
    fun newTripLeavesWithTheBookItClosed() {
        var open by mutableStateOf(true)
        var book by mutableStateOf(ListFixture.ui().book)
        compose.setContent {
            PicnicTheme { NewTripSheet(visible = open, book = book, onClear = {}, onKeep = {}, onDismiss = { open = false }) }
        }
        leaves(close = {
            open = false
            book = null
        })
    }

    /** §6.4: "Delete Big shop?" answered, the confirm sheet slides away still asking about the list that has gone. */
    @Test
    fun deleteConfirmLeavesAskingAboutTheList() {
        var lists by mutableStateOf(SwitcherSamples.lists)
        compose.setContent {
            PicnicTheme {
                Box(Modifier.fillMaxSize()) {
                    ListSwitcherSheet(
                        lists = lists,
                        onDismiss = {},
                        onSwitch = {},
                        onNew = {},
                        onRename = { _, _ -> },
                        onDelete = { id -> lists = lists.filter { it.id != id } },
                        initialConfirmDelete = "big",
                    )
                }
            }
        }
        // The switcher is the first sheet up, the confirm sheet over it the second.
        leaves(close = { compose.onNode(hasText("Delete") and hasClickAction()).performClick() }, which = 1)
    }

    /** §6.4: the list switcher slides away when it closes, instead of vanishing with its scrim in one frame. */
    @Test
    fun listSwitcherSlidesAway() {
        var showLists by mutableStateOf(true)
        compose.setContent { PicnicTheme { ListScreen(state = ListFixture.ui(), actions = ListActions(onCloseLists = { showLists = false }), showLists = showLists) } }
        leaves(close = { showLists = false })
    }

    /** §6.3: the Edit sheet slides away when it closes (here by Save, on the real list), as she last saw it. */
    @Test
    fun editSheetSlidesAway() {
        val rig = ListRig()
        compose.show(rig)
        compose.runOnIdle { rig.vm.openEdit("flour") }
        leaves(close = { compose.runOnIdle { rig.vm.closeEdit() } })
    }

    /**
     * §4.16: sliding away the sheet is only a picture of what it showed: a tap on its Delete as it leaves deletes
     * nothing, and TalkBack no longer reads it.
     */
    @Test
    fun aLeavingSheetTakesNoTouch() {
        val rig = ListRig()
        compose.show(rig)
        compose.runOnIdle { rig.vm.openEdit("flour") }
        compose.waitForIdle()
        val delete = compose.onNode(hasText("Delete") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { rig.vm.closeEdit() }
        compose.frames(2)
        assertTrue("the sheet is still sliding away", compose.exists(LETTERSET_TAG))
        assertTrue("TalkBack no longer reads it", compose.onAllNodes(hasText("Delete") and hasClickAction()).fetchSemanticsNodes().isEmpty())
        compose.onRoot().performTouchInput { click(delete.center) }
        compose.frames(40)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("flour is still on the list", rig.app.list(ListFixture.LIST_ID)!!.items.any { it.id == "flour" })
    }

    /** §6.13: My pantry's sticker sheet slides away still showing the stickers she was choosing from. */
    @Test
    fun pantrySheetLeavesWithItsStickers() {
        val store = InMemoryStateStore(Samples.emptyState.copy(pantry = listOf(PantryEntry(name = "Salt", addedAt = Samples.NOW))))
        val decider = object : IconDecider {
            override suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> = emptyMap()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = PantryViewModel(store, InMemorySettingsRepository(), decider, IconCatalog.default, { Samples.NOW }, scope)
        compose.setContent { PicnicTheme { PantryRoute(vm, onBack = {}) } }
        compose.waitForIdle()
        val salt = compose.onNodeWithContentDescription("Salt, in your pantry").fetchSemanticsNode()
        compose.runOnIdle { salt.config[SemanticsActions.CustomActions].first { it.label == "Change sticker" }.action() }
        leaves(close = { compose.onNodeWithContentDescription("Close sheet").performClick() })
    }
}

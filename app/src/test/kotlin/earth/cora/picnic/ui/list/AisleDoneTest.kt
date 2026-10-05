package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.motion.StampPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The aisle-done chain on the real list (docs/DESIGN_STICKER.md §5.3, §8.4; items 108, 109): the lid shuts and GOT IT
 * is pressed on the LID (never on the tab), the badge hides and the minis hide under the lid; un-checking in a done
 * aisle re-opens the lid and restores the badge, the stamp's number and the untied ribbon.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class AisleDoneTest {
    @get:Rule
    val compose = createComposeRule()

    /** Item 108: Baking powder completes Baking. */
    @Test
    fun hankoOnLidNotTab() {
        val rig = ListRig()
        compose.show(rig)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("baking-powder")).performClick()
        val frames = compose.sample(rig, 2400)
        val tap = frames.tapTime("baking-powder")
        val land = landTime(rig, "baking-powder", tap)
        val baking = "aisle-2-baking"
        assertBeat("the lid shuts", land + 300, frames.firstTime { it.baskets[baking]?.lidClosed == true })
        // GOT IT slams onto the lid 140 ms after it: the basket's own hanko, at its hanko point.
        val hanko = frames.firstOrNull { it.p.baskets[baking]?.lidHankoT0 != null }?.p?.baskets?.get(baking)?.lidHankoT0
        assertNotNull("GOT IT is pressed on the lid", hanko)
        assertTrue("140 ms after the lid (${hanko!! - (land + 300)})", hanko - (land + 300) in 120L..180L)
        // The badge hides with the lid: nothing left to get, no lag.
        val after = frames.last { it.time >= land + 300 }
        assertEquals(0, rig.vm.state.value.sections.first { it.id == baking }.toGet + (after.p.baskets[baking]?.toGetLag ?: 0))
        // The minis hide once the lid has landed (240 ms): until then they show under the closing lid.
        fun minisHidden(p: earth.cora.picnic.ui.list.motion.ListPresentation): Boolean {
            val b = p.baskets[baking]
            return b?.minisHidden ?: b?.lidClosed ?: true
        }
        val hidden = frames.firstTime { minisHidden(it) }
        assertTrue("the minis hide after the lid ($hidden vs ${land + 300})", hidden != null && hidden >= land + 300 + 200)
        assertTrue("and stay hidden", frames.filter { it.time >= hidden!! }.all { minisHidden(it.p) })
        // The tab never carries a hanko: the stamp goes onto the book's stamp page, the GOT IT onto the lid.
        assertTrue(frames.any { it.p.stamps[baking] is StampPhase.Thunking })
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("folded, done", rig.vm.state.value.sections.first { it.id == baking }.complete)
    }

    /** Item 109: un-checking Spiral ham in the finished Deli & meat (folded) re-opens it at once. */
    @Test
    fun uncheckReopens() {
        val rig = ListRig()
        val probe = RecordingProbe()
        compose.show(rig, probe)
        val deli = "deli-meat"
        fun label() = compose.onNodeWithTag(ListTags.strip(deli)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single()
        assertEquals("Aisle 3, Deli and meat: 2 of 2 in the basket", label())
        assertTrue("tied", probe.bounds.containsKey("ribbon:$deli") || probe.bounds.containsKey("bow:$deli"))
        assertTrue(rig.vm.state.value.book!!.stamps.first { it.sectionId == deli }.stamped)
        compose.mainClock.autoAdvance = false
        // The TalkBack action (the folded aisle's row is not on screen): an un-check.
        compose.runOnIdle { rig.vm.toggle("ham") }
        val frames = compose.sample(rig, 700)
        val tap = frames.tapTime("ham")
        val first = frames.first { it.time >= tap }
        assertEquals("the lid re-opens at once", false, first.p.baskets[deli]?.lidClosed)
        assertNotNull("the GOT IT lifts off the lid", frames.firstOrNull { it.p.baskets[deli]?.lidHankoLiftT0 != null })
        assertNotNull("the ribbon unties", frames.firstOrNull { it.p.aisles[deli]?.untieT0 != null })
        assertEquals("the badge is back at once", 1, rig.vm.state.value.sections.first { it.id == deli }.toGet + (first.p.baskets[deli]?.toGetLag ?: 0))
        assertFalse("the stamp returns to its number", rig.vm.state.value.book!!.stamps.first { it.sectionId == deli }.stamped)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // The aisle re-opened is where she is now (D2): its tray shows, a row to get again.
        assertEquals(deli, rig.vm.state.value.openSectionId)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:$deli")
        assertFalse(rig.app.isChecked("ham"))
        compose.onNodeWithTag(ListTags.row("ham")).performClick()
        compose.waitForIdle()
        assertTrue("checked again", rig.app.isChecked("ham"))
    }
}

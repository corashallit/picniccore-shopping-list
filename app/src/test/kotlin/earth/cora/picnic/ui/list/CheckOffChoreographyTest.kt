package earth.cora.picnic.ui.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The check-off chain on the real list (docs/DESIGN_STICKER.md §4.11, §8.3, §8.4; the list's half of item 87, and
 * item 111): the row's text never moves while the chain plays (nor while a duplicate add pops its sticker), and the
 * badge ticks down at the landing, not the tap, and back up at once on an un-check.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class CheckOffChoreographyTest {
    @get:Rule
    val compose = createComposeRule()

    /** Item 87, the real list: name and detail bounds are constant through the check, the un-check and a duplicate's pop. */
    @Test
    fun itemTextNeverMoves() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        val probe = RecordingProbe()
        compose.show(rig, probe)
        val name = probe["name:strawberries"]
        val detail = probe["detail:strawberries"]
        fun still(what: String) {
            assertEquals("$what: the name stays put", name, probe["name:strawberries"])
            assertEquals("$what: the detail stays put", detail, probe["detail:strawberries"])
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("strawberries")).performClick()
        compose.sample(rig, 1500) { still("check at ${it.time}") }
        assertTrue(rig.app.isChecked("strawberries"))
        compose.onNodeWithTag(ListTags.row("strawberries")).performClick()
        compose.sample(rig, 1000) { still("un-check at ${it.time}") }
        // A duplicate add (as typed): the existing row's sticker pops and its text band flashes; the text stays.
        compose.runOnIdle {
            rig.addVm.setText("Strawberries")
            rig.addVm.addAsTyped()
        }
        compose.sample(rig, 900) { still("duplicate pop at ${it.time}") }
        assertEquals("never a second row", 1, rig.app.items().count { it.name == "Strawberries" })
        compose.mainClock.autoAdvance = true
    }

    /** Item 111: the badge (what is still to get) ticks at the landing (620 ms), not at the tap; up at once on an un-check. */
    @Test
    fun badgeTiming() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        fun shown(f: ListFrame): Int = rig.vm.state.value.sections.first { it.id == "produce" }.toGet + (f.p.baskets["produce"]?.toGetLag ?: 0)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("strawberries")).performClick()
        val counts = ArrayList<Pair<Long, Int>>()
        val frames = compose.sample(rig, 1500) { counts += it.time to shown(it) }
        val tap = frames.tapTime("strawberries")
        assertTrue("2 left until the landing", counts.filter { it.first in tap until landTime(rig, "strawberries", tap) - 20 }.all { it.second == 2 })
        val ticked = counts.first { it.first >= tap && it.second == 1 }.first
        // Strawberries hop like berries: 360 ms, so they land at 680 ms (§8.3).
        assertBeat("the badge tick", landTime(rig, "strawberries", tap), ticked)
        assertTrue("and it stays 1", counts.filter { it.first > ticked }.all { it.second == 1 })

        // Un-check: back up to 2 on the very next frame.
        compose.onNodeWithTag(ListTags.row("strawberries")).performClick()
        val back = compose.sample(rig, 300)
        val untap = back.tapTime("strawberries")
        assertTrue("2 at once", back.filter { it.time >= untap }.all { shown(it) == 2 })
        compose.mainClock.autoAdvance = true
    }
}

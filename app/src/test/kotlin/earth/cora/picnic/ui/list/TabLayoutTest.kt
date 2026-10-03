package earth.cora.picnic.ui.list

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
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
 * Index tabs and ribbons on the real list (docs/DESIGN_STICKER.md §4.10; items 76, 79): with the app's 14 default aisles
 * the tab names fit 20 → 16 sp then ellipsize, never reaching the basket or the store chip, while TalkBack hears the
 * whole name; a finished strip's ribbon (or its bow) is never over text.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TabLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    /** Scrolls to each stop in turn and runs [check] on it while it is on screen (off-screen items leave the probe). */
    private fun visit(rig: ListRig, check: (SectionUi, open: Boolean) -> Unit) {
        val state = rig.vm.state.value
        for (s in state.sections) {
            val open = s.id == state.openSectionId
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey(if (open) "header:${s.id}" else "strip:${s.id}")
            compose.waitForIdle()
            check(s, open)
        }
    }

    private fun Rect.overlaps(o: Rect) = left < o.right - 0.5f && o.left < right - 0.5f && top < o.bottom - 0.5f && o.top < bottom - 0.5f

    /** Item 76. */
    @Test
    fun defaultAislesFit() {
        val rig = ListRig(ListFixture.everyDefaultAisle())
        val probe = RecordingProbe()
        compose.show(rig, probe)
        assertEquals("all 14 aisles are stops", 14, rig.vm.state.value.sections.size)
        var shrunk = 0
        visit(rig) { s, open ->
            if (open) {
                val tab = probe["trayTab:${s.id}"]
                val chip = probe.bounds["chip:${s.id}"]
                val basket = probe["trayBasket:${s.id}"]
                val limit = (chip ?: basket).left - 8 * dp
                assertTrue("${s.displayName}: the tray tab ends 8 dp before the ${if (chip != null) "store chip" else "basket"} (${tab.right} ≤ $limit)", tab.right <= limit + 0.5f)
                val font = probe.fonts.getValue("trayTab:${s.id}")
                assertTrue("${s.displayName}: 16–20 sp ($font)", font in 16f..20f)
            } else {
                val tab = probe["tab:${s.id}"]
                val basket = probe["basket:${s.id}"]
                assertTrue("${s.displayName}: the tab ends 8 dp before the basket (${tab.right} ≤ ${basket.left - 8 * dp})", tab.right <= basket.left - 8 * dp + 0.5f)
                val font = probe.fonts.getValue("tab:${s.id}")
                assertTrue("${s.displayName}: 16–20 sp ($font)", font in 16f..20f)
                if (font < 20f) shrunk++
                // TalkBack hears the whole display name (with "&" read as "and") and the store number.
                val label = compose.onNodeWithTag(ListTags.strip(s.id)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.single()
                assertTrue("$label names ${s.displayName}", label.contains(s.displayName.replace("&", "and")))
                s.storeAisle?.let { assertTrue("$label says store aisle $it", label.contains("store aisle $it")) }
            }
        }
        assertTrue("the long names shrink below 20 sp ($shrunk)", shrunk > 0)
    }

    private fun ribbons(state: earth.cora.picnic.core.model.AppState) {
        val rig = ListRig(state)
        val probe = RecordingProbe()
        compose.show(rig, probe)
        var done = 0
        visit(rig) { s, open ->
            if (!s.complete || open) return@visit
            done++
            val ribbon = probe.bounds["ribbon:${s.id}"]
            val bow = probe.bounds["bow:${s.id}"]
            assertTrue("${s.displayName}: tied with a ribbon or a bow", ribbon != null || bow != null)
            val text = listOf(probe["tab:${s.id}"], probe["meta:${s.id}"])
            for (r in listOfNotNull(ribbon, bow)) {
                for (t in text) assertTrue("${s.displayName}: the ribbon $r is over text $t", !r.overlaps(t))
                assertTrue("${s.displayName}: the ribbon stays left of the basket's right edge", r.left < probe["basket:${s.id}"].right)
            }
        }
        assertTrue("finished strips to check", done > 0)
    }

    /** Item 79: the default aisles' long names (three finished aisles). */
    @Test
    fun ribbonNeverOverText() = ribbons(ListFixture.everyDefaultAisle())

    /** Item 79: the fixture's Deli & meat, a short name: the full ribbon from the end of the text. */
    @Test
    fun ribbonNeverOverTextShortName() = ribbons(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, ""))
}

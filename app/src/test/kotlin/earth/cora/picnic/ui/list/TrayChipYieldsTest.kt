package earth.cora.picnic.ui.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.a11y.TextOverflowed
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.LocalPieceProbe
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The open tray's store chip gives way to the aisle's name (§4.7, §4.10, item 337). The store chip's basket glyph scales
 * with its label under a large font, so the chip is wider than it was; in the tray header it is then drawn smaller (never
 * under its 1.0 size, so "A2" stays at least 13 sp) before the name it sits beside is cut, and when even its 1.0 size
 * would cut the name it leaves the header (the tab's heading still says "store aisle 2"). The fixture's open tray,
 * "Baking" with store aisle 2, on a 360 dp phone: at 1.0 the chip keeps its natural width; at 1.15 and 1.3 the name stays
 * whole ("Baking", never "Baki…"), the tab ends 8 dp before the chip and the chip 2 dp before the basket's box; at 1.5,
 * 1.8 and 2.0 (the tab's number circle grown with its numeral), and on the phone itself at 200 % (Android's non-linear
 * font scaling), the name is whole and the chip gone, the tab ending 8 dp before the basket.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TrayChipYieldsTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { walk(it) }

    private fun bakingAt(fontScale: Float?): Triple<RecordingProbe, String, Boolean> {
        val rig = ListRig()
        val probe = RecordingProbe()
        if (fontScale != null) {
            compose.show(rig, probe, fontScale = fontScale)
        } else {
            // The phone's own density, its font scale and converter untouched (show() would make it linear).
            compose.setContent { CompositionLocalProvider(LocalPieceProbe provides probe) { rig.Themed(ThemeVariant.Day, false) } }
            compose.waitForIdle()
        }
        val baking = rig.vm.state.value.sections.single { it.displayName == "Baking" }
        assertEquals("Baking is the open tray", baking.id, rig.vm.state.value.openSectionId)
        assertEquals("Baking has its store aisle", 2, baking.storeAisle)
        val tab = compose.onNodeWithTag(ListTags.headerTab(baking.id), useUnmergedTree = true).fetchSemanticsNode()
        val cut = walk(tab).any { it.config.getOrNull(TextOverflowed) == true }
        return Triple(probe, baking.id, cut)
    }

    private fun assertRoom(probe: RecordingProbe, id: String, what: String) {
        val tab = probe["trayTab:$id"]
        val chip = probe["chip:$id"]
        val basket = probe["trayBasket:$id"]
        assertTrue("$what: the tab ends 8 dp before the store chip (${tab.right} ≤ ${chip.left - 8 * dp})", tab.right <= chip.left - 8 * dp + 0.5f)
        assertTrue("$what: the chip ends before the basket (${chip.right} ≤ ${basket.left})", chip.right <= basket.left + 0.5f)
    }

    /** The chip has left the header: nothing of it is laid out there, and the tab ends 8 dp before the basket. */
    private fun assertChipGone(probe: RecordingProbe, id: String, what: String) {
        assertTrue("$what: the store chip leaves the header (${probe.keys("chip:")})", probe.bounds["chip:$id"] == null)
        val tab = probe["trayTab:$id"]
        val basket = probe["trayBasket:$id"]
        assertTrue("$what: the tab ends 8 dp before the basket (${tab.right} ≤ ${basket.left - 8 * dp})", tab.right <= basket.left - 8 * dp + 0.5f)
    }

    @Test
    fun atOneTheChipKeepsItsWidth() {
        val (probe, id, cut) = bakingAt(1f)
        assertTrue("Baking is whole at 1.0", !cut)
        assertRoom(probe, id, "1.0")
        val font = probe.fonts.getValue("trayTab:$id")
        assertEquals("Baking at its full 20 sp at 1.0", 20f, font, 0f)
    }

    @Test
    fun at115TheNameStaysWhole() {
        val (probe, id, cut) = bakingAt(1.15f)
        assertTrue("Baking is whole at 1.15 (the chip gives way first)", !cut)
        assertRoom(probe, id, "1.15")
    }

    @Test
    fun at130TheNameStaysWhole() {
        val (probe, id, cut) = bakingAt(1.3f)
        assertTrue("Baking is whole at 1.3, not \"Baki…\" (the chip gives way first)", !cut)
        assertRoom(probe, id, "1.3")
    }

    /** At 1.5 the tab's number circle has grown with its numeral (item 337), and even the chip's 1.0 size would cut the name. */
    @Test
    fun at150TheChipLeavesTheHeader() {
        val (probe, id, cut) = bakingAt(1.5f)
        assertTrue("Baking is whole at 1.5 (the chip leaves)", !cut)
        assertChipGone(probe, id, "1.5")
    }

    @Test
    fun at180TheChipLeavesTheHeader() {
        val (probe, id, cut) = bakingAt(1.8f)
        assertTrue("Baking is whole at 1.8, not \"Baki…\" (the chip leaves)", !cut)
        assertChipGone(probe, id, "1.8")
    }

    @Test
    fun at200TheChipLeavesTheHeader() {
        val (probe, id, cut) = bakingAt(2f)
        assertTrue("Baking is whole at 2.0, not \"Baki…\" (the chip leaves)", !cut)
        assertChipGone(probe, id, "2.0")
    }

    /** The phone itself at 200 %: Android 14's non-linear font scaling (16 sp is 28 dp, not 32). */
    @Test
    @Config(qualifiers = PHONE_360, fontScale = 2.0f)
    fun thePhoneAt200PercentKeepsTheName() {
        val (probe, id, cut) = bakingAt(null)
        assertTrue("Baking is whole on the phone at 200 %", !cut)
        assertChipGone(probe, id, "the phone at 200 %")
    }
}

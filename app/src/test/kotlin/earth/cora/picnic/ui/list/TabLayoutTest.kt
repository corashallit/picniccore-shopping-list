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
        // Every tray, not only the one open at load: open each aisle in turn and check its band (§4.10: the tab ends
        // 8 dp before the store chip, or before the basket when there is no chip).
        for (s in rig.vm.state.value.sections) {
            compose.runOnIdle { rig.vm.openSection(s.id) }
            compose.waitForIdle()
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:${s.id}")
            compose.waitForIdle()
            val tab = probe["trayTab:${s.id}"]
            val chip = probe.bounds["chip:${s.id}"]
            val basket = probe["trayBasket:${s.id}"]
            val limit = (chip ?: basket).left - 8 * dp
            assertTrue("${s.displayName} (open): the tray tab ends 8 dp before the ${if (chip != null) "store chip" else "basket"} (${tab.right} ≤ $limit)", tab.right <= limit + 0.5f)
            if (chip != null) assertEquals("${s.displayName}: the store chip sits 8 dp after the tab", 8f, (chip.left - tab.right) / dp, 0.6f)
            assertTrue("${s.displayName} (open): the chip ends before the basket", (chip?.right ?: tab.right) <= basket.left + 0.5f)
            val font = probe.fonts.getValue("trayTab:${s.id}")
            assertTrue("${s.displayName} (open): 16–20 sp ($font)", font in 16f..20f)
        }
    }

    /** What [ribbons] found: finished strips tied with the full ribbon, and with the bow alone. */
    private data class Tied(val bands: Int, val bows: Int)

    private fun ribbons(state: earth.cora.picnic.core.model.AppState, fontScale: Float? = null): Tied {
        val rig = ListRig(state)
        val probe = RecordingProbe()
        compose.show(rig, probe, fontScale = fontScale)
        var bands = 0
        var bows = 0
        val at = "fs ${fontScale ?: 1f}"
        visit(rig) { s, open ->
            if (!s.complete || open) return@visit
            val ribbon = probe.bounds["ribbon:${s.id}"]
            val bow = probe.bounds["bow:${s.id}"]
            assertTrue("$at, ${s.displayName}: tied with a ribbon or a bow", ribbon != null || bow != null)
            val text = listOfNotNull(probe["tab:${s.id}"], probe["meta:${s.id}"], probe.bounds["stripChip:${s.id}"])
            val basket = probe["basket:${s.id}"]
            // The bow sticker is drawn 12 dp above its box (StripRibbon): check where it shows, not only its box.
            val drawnBow = bow?.translate(0f, -12 * dp)
            for (r in listOfNotNull(ribbon, bow, drawnBow)) {
                for (t in text) assertTrue("$at, ${s.displayName}: the ribbon $r is over text $t", !r.overlaps(t))
                assertTrue("$at, ${s.displayName}: the ribbon stays left of the basket's right edge", r.left < basket.right)
            }
            val strip = compose.boundsOf(ListTags.strip(s.id))
            if (ribbon != null) {
                bands++
                // §4.10, §15: the band runs the strip's whole height, however tall large text makes the strip.
                assertEquals("$at, ${s.displayName}: the ribbon starts at the strip's top", strip.top, ribbon.top, 1f)
                assertEquals("$at, ${s.displayName}: the ribbon ends at the strip face's bottom (${ribbon.bottom / dp} vs ${strip.bottom / dp} dp)", strip.bottom, ribbon.bottom, 1f)
            }
            if (drawnBow != null) {
                bows++
                // The bow alone ties at the basket's left shoulder: it overlaps the basket's top-left quadrant.
                val quadrant = Rect(basket.left, basket.top, basket.center.x, basket.center.y)
                assertTrue("$at, ${s.displayName}: the bow $drawnBow sits on the basket's left shoulder $quadrant", drawnBow.overlaps(quadrant))
                assertTrue("$at, ${s.displayName}: the bow does not float above the basket (${drawnBow.bottom / dp} > ${basket.top / dp} dp)", drawnBow.bottom > basket.top + 6 * dp)
            }
        }
        assertTrue("$at: finished strips to check", bands + bows > 0)
        return Tied(bands, bows)
    }

    /** Item 79: the default aisles' long names (three finished aisles). */
    @Test
    fun ribbonNeverOverText() {
        ribbons(ListFixture.everyDefaultAisle())
    }

    /** Item 79: the fixture's Deli & meat (its meta line runs close to the basket, so the bow ties alone). */
    @Test
    fun ribbonNeverOverTextShortName() {
        ribbons(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, ""))
    }

    /**
     * Item 79 at large text (§4.10, §15): the strips grow taller, the ribbon's band still runs from the strip's top to the
     * face's bottom, and a bow tied alone sits on its basket's left shoulder (the basket stands on the strip's bottom
     * edge), never floating above it; never over text. At 1.3 the default aisles show both forms (Produce keeps its band,
     * the long names tie bows); at 2.0 every finished strip ties the bow alone.
     */
    @Test
    fun ribbonFollowsTheStripAtFs130() {
        assertTrue(ribbons(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, ""), 1.3f).bows > 0)
    }

    @Test
    fun ribbonFollowsTheStripAtFs200() {
        assertTrue(ribbons(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, ""), 2f).bows > 0)
    }

    @Test
    fun ribbonFollowsLongNamesAtFs130() {
        val tied = ribbons(ListFixture.everyDefaultAisle(), 1.3f)
        assertTrue("a full band at 1.3 ($tied)", tied.bands > 0)
        assertTrue("a bow alone at 1.3 ($tied)", tied.bows > 0)
    }

    @Test
    fun ribbonFollowsLongNamesAtFs200() {
        assertTrue(ribbons(ListFixture.everyDefaultAisle(), 2f).bows > 0)
    }

    /**
     * Item 107 (§4.12): the badge overhangs its basket by 12 dp and, with large text or a long count, grows to the right
     * into a pill; the basket stands far enough left that the badge's white face and its hard shadow keep clear of the
     * chevron's round face (8 dp inside its 48 dp target), on the open tray's band and on a folded strip. Household holds
     * eleven things with one got: its badge reads "10".
     */
    @Test
    fun badgeClearsTheChevronAtLargeText() = badgeClearsTheChevron(2f, grows = true)

    @Test
    fun badgeClearsTheChevron() = badgeClearsTheChevron(1f, grows = false)

    private fun badgeClearsTheChevron(fontScale: Float, grows: Boolean) {
        val rig = ListRig(ListFixture.longAisle())
        val probe = RecordingProbe()
        compose.show(rig, probe, fontScale = fontScale)
        val id = "household"
        assertEquals(10, rig.vm.state.value.sections.first { it.id == id }.toGet)
        // The open tray: the badge itself (in the unmerged tree), and the chevron's target.
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:$id")
        compose.waitForIdle()
        val badge = compose.onAllNodes(
            androidx.compose.ui.test.hasTestTag(earth.cora.picnic.ui.list.motion.BADGE_TAG) and
                androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(ListTags.header(id))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single().boundsInRoot
        val trayBasket = probe["trayBasket:$id"]
        assertEquals("fs $fontScale: the badge's left edge 10 dp inside the basket's right edge", trayBasket.right - 10 * dp, badge.left, 1f)
        if (grows) assertTrue("fs $fontScale: \"10\" is a pill wider than the 22 dp circle (${badge.width / dp} dp)", badge.width > 23 * dp)
        val chevron = compose.boundsOf(ListTags.chevron(id))
        val shadow = 1.6f * dp
        // The air between the badge's shadow and the chevron's face: at least what the 22 dp badge has on a strip at
        // font scale 1.0 (2 dp from face to face, the 1.6 dp shadow in it), however wide the badge grows.
        fun air(badgeRight: Float, chevronLeft: Float) = (chevronLeft + CHEVRON_FACE_INSET * dp - badgeRight - shadow) / dp
        val trayAir = air(badge.right, chevron.left)
        assertTrue("fs $fontScale, tray: the badge and its shadow end before the chevron's face ($trayAir dp of air)", trayAir >= 0.3f)

        // The folded strip (another aisle open): the same badge, on the strip's basket, before the strip's chevron.
        compose.runOnIdle { rig.vm.openSection("produce") }
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("strip:$id")
        compose.waitForIdle()
        val strip = compose.boundsOf(ListTags.strip(id))
        val basket = probe["basket:$id"]
        val stripBadgeRight = basket.right - 10 * dp + badge.width
        val stripAir = air(stripBadgeRight, strip.right - 48 * dp)
        assertTrue("fs $fontScale, strip: the badge and its shadow end before the chevron's face ($stripAir dp of air)", stripAir >= 0.3f)
    }

    private companion object {
        /** ChevronFace: its round face starts 8 dp inside the 48 dp target. */
        const val CHEVRON_FACE_INSET = 8f
    }
}

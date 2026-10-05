package earth.cora.picnic.ui.list

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
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
 * the tab names fit 20 → 16 sp then wrap between words (§0.4: never cut, "&" never ends a line), never reaching the
 * basket or the store chip, while TalkBack hears the whole name; a finished strip's ribbon (or its bow) is never over
 * text.
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

    /** The lines of the name drawn on the tab tagged [tabTag] (its text node, in the unmerged tree). */
    private fun tabNameLines(tabTag: String): List<String> = compose.onAllNodes(
        androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(tabTag)) and
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString("") }.single { n -> n.any { it.isLetter() } }.split('\n')

    /** §0.4: the whole [name] on the tab, broken only between words, no line ending in "&". */
    private fun assertWhole(name: String, lines: List<String>, at: String) {
        assertEquals("$at: $name is whole on its tab ($lines)", name, lines.joinToString(" ").replace('\u00A0', ' '))
        for (line in lines.dropLast(1)) assertTrue("$at: $name: no line ends in \"&\" ($lines)", !line.trimEnd().endsWith("&"))
    }

    /** Item 76. */
    @Test
    fun defaultAislesFit() {
        val rig = ListRig(ListFixture.everyDefaultAisle())
        val probe = RecordingProbe()
        compose.show(rig, probe)
        assertEquals("all 14 aisles are stops", 14, rig.vm.state.value.sections.size)
        var shrunk = 0
        var wrapped = 0
        visit(rig) { s, open ->
            if (open) {
                val tab = probe["trayTab:${s.id}"]
                val chip = probe.bounds["chip:${s.id}"]
                val basket = probe["trayBasket:${s.id}"]
                val limit = (chip ?: basket).left - 8 * dp
                assertTrue("${s.displayName}: the tray tab ends 8 dp before the ${if (chip != null) "store chip" else "basket"} (${tab.right} ≤ $limit)", tab.right <= limit + 0.5f)
                val font = probe.fonts.getValue("trayTab:${s.id}")
                assertTrue("${s.displayName}: 16–20 sp ($font)", font in 16f..20f)
                assertWhole(s.displayName, tabNameLines(ListTags.headerTab(s.id)), "tray")
            } else {
                val tab = probe["tab:${s.id}"]
                val basket = probe["basket:${s.id}"]
                assertTrue("${s.displayName}: the tab ends 8 dp before the basket (${tab.right} ≤ ${basket.left - 8 * dp})", tab.right <= basket.left - 8 * dp + 0.5f)
                val font = probe.fonts.getValue("tab:${s.id}")
                assertTrue("${s.displayName}: 16–20 sp ($font)", font in 16f..20f)
                if (font < 20f) shrunk++
                val lines = tabNameLines(ListTags.stripTab(s.id))
                assertWhole(s.displayName, lines, "strip")
                if (lines.size > 1) wrapped++
                // The strip grows round a wrapped name: the tab and the meta line under it stay inside the strip.
                val strip = compose.boundsOf(ListTags.strip(s.id))
                assertTrue("${s.displayName}: the strip holds its tab and meta", tab.top >= strip.top - 0.5f && probe["meta:${s.id}"].bottom <= strip.bottom + 0.5f)
                // TalkBack hears the whole display name (with "&" read as "and") and the store number.
                val label = compose.onNodeWithTag(ListTags.strip(s.id)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.single()
                assertTrue("$label names ${s.displayName}", label.contains(s.displayName.replace("&", "and")))
                s.storeAisle?.let { assertTrue("$label says store aisle $it", label.contains("store aisle $it")) }
            }
        }
        assertTrue("the long names shrink below 20 sp ($shrunk)", shrunk > 0)
        assertTrue("the longest names wrap ($wrapped)", wrapped > 0)
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
            assertWhole(s.displayName, tabNameLines(ListTags.headerTab(s.id)), "open tray")
            // The band grows round a wrapped tab: the tab stays inside the header, 6 dp clear of its band's edges.
            val header = compose.boundsOf(ListTags.header(s.id))
            assertTrue("${s.displayName} (open): the tab ${tab} inside the band $header", tab.top >= header.top + 6 * dp - 0.5f && tab.bottom <= header.bottom - 6 * dp + 0.5f)
            // The store chip stays beside a name that fits by it on two lines ("Baking / & spices"): it is the A-number on
            // the store's signs.
            if (s.displayName == "Baking & spices") assertTrue("${s.displayName} (open): the store chip stays", chip != null)
        }
    }

    /**
     * §0.4 at large text on a slightly narrower phone (352 dp: at 1.3 "& international" does not fit a strip's tab beside
     * even the 12 dp pattern end): the pattern end goes instead, and "&" keeps its word on every folded strip and on
     * the open tray.
     */
    @Test
    @Config(qualifiers = "w352dp-h760dp-xxhdpi")
    fun ampersandKeepsItsWordAtFs130() {
        val rig = ListRig(ListFixture.everyDefaultAisle())
        compose.show(rig, fontScale = 1.3f)
        visit(rig) { s, open ->
            assertWhole(s.displayName, tabNameLines(if (open) ListTags.headerTab(s.id) else ListTags.stripTab(s.id)), "352 dp, fs 1.3")
        }
    }

    /** The strip meta line's text as laid out. */
    private fun metaLayout(id: String): androidx.compose.ui.text.TextLayoutResult {
        val node = compose.onNodeWithTag(ListTags.stripMeta(id), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
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
            // The meta line's words, line by line (a wrapped text's box runs on past its longest line).
            val meta = probe["meta:${s.id}"]
            val metaText = metaLayout(s.id)
            val metaLines = (0 until metaText.lineCount).map { Rect(meta.left + metaText.getLineLeft(it), meta.top + metaText.getLineTop(it), meta.left + metaText.getLineRight(it), meta.top + metaText.getLineBottom(it)) }
            val text = listOfNotNull(probe["tab:${s.id}"], probe.bounds["stripChip:${s.id}"]) + metaLines
            val basket = probe["basket:${s.id}"]
            // The bow sticker is drawn 12 dp above its box (StripRibbon); its ink (sk_d_ribbon's cut line) runs 5.3 to
            // 24.7 dp across the 30 dp sticker and 9.7 to 25 dp down it. What must keep off the text is that ink.
            val drawnBow = bow?.translate(0f, -12 * dp)
            val bowInk = drawnBow?.let { Rect(it.left + 5.3f * dp, it.top + 9.7f * dp, it.left + 24.7f * dp, it.top + 25f * dp) }
            for (r in listOfNotNull(ribbon, bowInk)) {
                for (t in text) assertTrue("$at, ${s.displayName}: the ribbon $r is over text $t", !r.overlaps(t))
                assertTrue("$at, ${s.displayName}: the ribbon stays left of the basket's right edge", r.left < basket.right)
            }
            // The bow stays whole: at most 2 dp right of its spot on the shoulder (14 dp into the basket's box), so its
            // knot is tucked no further behind the basket than on a short name.
            if (bow != null) assertTrue("$at, ${s.displayName}: the bow at most 2 dp right of its spot (${(bow.left - basket.left) / dp} dp)", bow.left <= basket.left - 12 * dp + 0.5f)
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

    /**
     * §4.10, §0.4 tab fit: a name too long for one line even at 16 sp wraps between words into the fewest lines, balanced,
     * "&" kept with the word after it; a word wider than the whole room on its own leaves the wrapping to the text.
     */
    @Test
    fun tabLinesBreakBetweenWordsBalanced() {
        fun lines(name: String, room: Int) = earth.cora.picnic.ui.theme.tabLines(name, room) { it.length }?.map { it.replace('\u00A0', ' ') }
        assertEquals(listOf("Frozen"), lines("Frozen", 6))
        assertEquals(listOf("Meat", "& seafood"), lines("Meat & seafood", 12))
        assertEquals(listOf("Pasta, rice", "& international"), lines("Pasta, rice & international", 20))
        assertEquals(listOf("Breakfast,", "coffee & tea"), lines("Breakfast, coffee & tea", 18))
        assertEquals(listOf("Health,", "beauty & baby"), lines("Health, beauty & baby", 16))
        assertEquals(listOf("Bulk", "& natural", "foods"), lines("Bulk & natural foods", 9))
        assertEquals(null, lines("Supercalifragilistic shop", 10))
    }

    /**
     * Item 337 at font scale 2.0 (§4.10, §15), and item 281: a tab name shrinks to its 1.0 size before it wraps, so the
     * fixture's names (all on one line at 1.0) are on one line at 2.0 too; none is ever cut (§0.4).
     * A meta line that would wrap a word a line beside the basket takes the strip's whole width under the tab instead,
     * the basket on the tab's line, and "in the basket" stays together; no line of a meta holds a lone word.
     */
    @Test
    fun largeTextStripsKeepTheirWordsTogether() {
        val state = ListFixture.state()
            .toggleChecked(ListFixture.LIST_ID, "baking-powder", ListFixture.TRIP_START + 20 * ListFixture.MIN)
            .setOpenAisle(ListFixture.LIST_ID, "")
        val rig = ListRig(state)
        val probe = RecordingProbe()
        compose.show(rig, probe, fontScale = 2f)
        var stacked = 0
        var wrapped = 0
        visit(rig) { s, _ ->
            val name = tabNameLines(ListTags.stripTab(s.id))
            assertWhole(s.displayName, name, "fs 2.0")
            if (name.size > 1) wrapped++
            val metaNode = compose.onNodeWithTag(ListTags.stripMeta(s.id), useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            metaNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val text = layouts.single()
            val lines = (0 until text.lineCount).map { text.layoutInput.text.text.substring(text.getLineStart(it), text.getLineEnd(it)).trim() }
            if (lines.size > 1) lines.forEach { assertTrue("${s.displayName}: no lone word on a line of the meta ($lines)", it.any { c -> c == ' ' || c == '\u00A0' }) }
            if (s.toGet == 0) assertTrue("${s.displayName}: \"in the basket\" together ($lines)", lines.any { "in\u00A0the\u00A0basket" in it })
            val meta = probe["meta:${s.id}"]
            val basket = probe["basket:${s.id}"]
            val tab = probe["tab:${s.id}"]
            assertTrue("${s.displayName}: the meta $meta clear of the basket $basket", !meta.overlaps(basket))
            if (meta.right > basket.left) {
                stacked++
                assertTrue("${s.displayName}: the basket stands on the tab's line (${basket.center.y} in ${tab.top}..${tab.bottom})", basket.center.y in tab.top..tab.bottom)
                assertTrue("${s.displayName}: the meta line under the basket", meta.top >= basket.bottom - 1f)
                assertEquals("${s.displayName}: one line at the strip's whole width ($lines)", 1, lines.size)
            }
            assertTrue("${s.displayName}: the strip holds its words", compose.boundsOf(ListTags.strip(s.id)).let { it.bottom >= meta.bottom - 0.5f })
        }
        assertEquals("no name wraps at 2.0: each shrinks to its 1.0 size first", 0, wrapped)
        assertTrue("some strips stack their meta under the basket at 2.0 ($stacked)", stacked > 0)
    }

    /**
     * §4.6, §4.10 (trip review): wherever a strip's meta wraps, its count stays whole ("1 of 1", "2 left", "of 3"), "in the
     * basket" stays together, and no line ends in the chip's "·": at 1.0, 1.3 and 2.0, finished strips and ones to go,
     * with and without a store-aisle chip.
     */
    @Test
    fun stripMetaKeepsItsCountsTogether() = metaCountsTogether(1f)

    @Test
    fun stripMetaKeepsItsCountsTogetherAtFs130() = metaCountsTogether(1.3f)

    @Test
    fun stripMetaKeepsItsCountsTogetherAtFs200() = metaCountsTogether(2f)

    private fun metaCountsTogether(scale: Float) {
        run {
            var state = ListFixture.everyDefaultAisle()
            for (i in listOf(6, 10)) state = state.toggleChecked(ListFixture.LIST_ID, "d$i", ListFixture.TRIP_START + (20 + i) * ListFixture.MIN)
            state = state.setOpenAisle(ListFixture.LIST_ID, "")
            val rig = ListRig(state)
            compose.show(rig, fontScale = scale)
            var done = 0
            var wrapped = 0
            visit(rig) { s, _ ->
                val metaNode = compose.onNodeWithTag(ListTags.stripMeta(s.id), useUnmergedTree = true).fetchSemanticsNode()
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                metaNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                val text = layouts.single()
                val lines = (0 until text.lineCount).map { text.layoutInput.text.text.substring(text.getLineStart(it), text.getLineEnd(it)).trim() }
                fun together(what: String) = assertTrue("${s.displayName} at $scale: \"$what\" on one line ($lines)", lines.any { what in it.replace('\u00A0', ' ') })
                val toGet = s.toGet
                if (toGet == 0) {
                    done++
                    together("${s.total} of ${s.total}")
                    together("in the basket")
                } else {
                    together("$toGet left")
                    together("of ${s.total}")
                }
                if (lines.size > 1) wrapped++
                lines.forEach { assertTrue("${s.displayName} at $scale: no line ends in \"·\" ($lines)", !it.endsWith("·")) }
            }
            assertTrue("finished strips seen at $scale", done >= 2)
            // At 1.3 the meta still sits beside the basket and wraps there (at 2.0 it takes the strip's whole width).
            if (scale == 1.3f) assertTrue("some metas wrap at $scale ($wrapped)", wrapped > 0)
        }
    }

    private companion object {
        /** ChevronFace: its round face starts 8 dp inside the 48 dp target. */
        const val CHEVRON_FACE_INSET = 8f
    }
}

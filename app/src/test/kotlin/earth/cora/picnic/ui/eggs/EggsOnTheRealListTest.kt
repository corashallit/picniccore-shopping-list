package earth.cora.picnic.ui.eggs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The eggs on WP5's real List screen (integration of part 3; §12, §19.2 hand-off): the pieces the list itself places
 * through EggListHost are there and where the spec puts them — the nap cat curled in the empty basket (egg 8), the snack
 * star and doodle by the frozen aisle's tab, clear of its words (egg 5), the ladybug walking along a folded strip's bottom
 * edge (egg 2), the sun turning into the moon after seven taps (egg 1), and the umbrella's band above the bar that no
 * row lies in while it rains (egg 16). The list-agnostic bounds and decor tests run on it too (EggListUnderTest.Real).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggsOnTheRealListTest {
    @get:Rule
    val compose = createComposeRule()

    private fun bounds(tag: String): List<Rect> = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

    @Test
    fun theNapCatCurlsUpInTheListsOwnBasket() {
        val r = EggRig(compose).start(EggListUnderTest.Real)
        r.fire(8)
        r.advance(2_000)
        val nap = r.eggs.napSectionId
        assertNotNull("the cat has a basket", nap)
        val cat = bounds(EggTags.NAP_CAT).singleOrNull()
        assertNotNull("the cat is on screen", cat)
        val basket = r.anchors.bounds(AnchorRegistry.basketKey(nap!!))!!
        assertTrue("in its basket ($cat, basket $basket)", basket.inflate(12 * 3f).contains(cat!!.center))
        assertTrue("a page sticker", "nap cat" in r.pageStickers())
    }

    @Test
    fun theSnackDoodleSitsByTheFrozenTabClearOfItsWords() {
        val r = EggRig(compose).start(EggListUnderTest.Real)
        r.fire(5)
        r.advance(1_500)
        val snack = r.eggs.snackSectionId
        assertNotNull("midnight: the frozen aisle has its snack", snack)
        val doodle = bounds(EggTags.SNACK).singleOrNull()
        assertNotNull("the list draws the doodle", doodle)
        val words = listOfNotNull(
            r.anchors.bounds(EggAnchors.TEXT_PREFIX + "tab:" + snack),
            r.anchors.bounds(EggAnchors.TEXT_PREFIX + "num:" + snack),
            r.anchors.bounds(EggAnchors.TEXT_PREFIX + "meta:" + snack),
        )
        assertTrue("the tab's words are anchored", words.isNotEmpty())
        words.forEach { w -> assertTrue("the doodle $doodle clear of $w", !doodle!!.deflate(2f).overlaps(w)) }
        val tab = r.anchors.bounds(EggAnchors.tab(snack!!))!!
        assertTrue("by the tab ($doodle, tab $tab)", doodle!!.top < tab.bottom + 40 * 3f && doodle.left >= tab.left)
    }

    @Test
    fun theLadybugWalksAFoldedStripsBottomEdge() {
        val r = EggRig(compose).start(EggListUnderTest.Real)
        r.probe.clear()
        r.fire(2)
        val strip = r.eggs.ladybug?.sectionId
        assertNotNull(strip)
        r.advance(2_000)
        val card = r.anchors.bounds(EggAnchors.strip(strip!!))!!
        val walk = r.probe.frames.filter { it.tag == "ladybug" && it.time in 100..4_800 }
        assertTrue("it walks", walk.size > 5)
        walk.forEach { f ->
            assertTrue("on the strip's bottom edge (${f.bounds.center.y} vs ${card.bottom})", f.bounds.center.y in card.bottom..card.bottom + 8 * 3f)
        }
    }

    @Test
    fun sevenSunTapsTurnTheListDusk() {
        val r = EggRig(compose, variant = ThemeVariant.Day).start(EggListUnderTest.Real)
        assertTrue(compose.onAllNodesWithContentDescription("Sun sticker").fetchSemanticsNodes().isNotEmpty())
        r.fire(1)
        r.advance(1_600)
        assertTrue(r.eggs.dusk)
        assertEquals(ThemeVariant.Dusk, r.resolved)
        assertTrue("the moon by the title", compose.onAllNodesWithContentDescription("Moon sticker").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun noRowLiesUnderTheUmbrella() {
        val r = EggRig(compose).start(EggListUnderTest.Real)
        r.fire(16)
        r.advance(600)
        assertTrue(r.eggs.umbrella)
        val bar = r.anchors.bounds(AnchorRegistry.ADD_BAR)!!
        val list = r.anchors.bounds(EggAnchors.LIST)!!
        val umbrella = bounds(EggTags.UMBRELLA).single()
        assertTrue("the list ends above the canopy (${list.bottom} ≤ ${umbrella.top})", list.bottom <= umbrella.top + 1f)
        assertTrue("and the bar's top below it", bar.top > list.bottom)
        val rows = ArrayList<Rect>()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
            if (n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("picnic_row_slot_") == true) rows += n.boundsInRoot
            n.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue("the open tray's rows are on screen", rows.isNotEmpty())
        rows.forEach { assertTrue("no row under the umbrella ($it)", !it.overlaps(umbrella.deflate(2f))) }
    }
}

package earth.cora.picnic.ui.eggs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
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
 * edge (egg 2), the sun turning into the moon after one tap (egg 1), and the umbrella on the bar's edge that leaves the
 * list's viewport whole while it rains (egg 16). The list-agnostic bounds and decor tests run on it too (EggListUnderTest.Real).
 * Goldens `eggs_on_list_{nap,ladybug,rain}_day` and `eggs_on_list_midnight` (midnight is Glow by its own rule).
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
        r.capture("eggs_on_list_nap_day")
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
        r.capture("eggs_on_list_midnight")
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
        r.capture("eggs_on_list_ladybug_day")
    }

    @Test
    fun oneSunTapTurnsTheListDusk() {
        val r = EggRig(compose, variant = ThemeVariant.Day).start(EggListUnderTest.Real)
        assertTrue(compose.onAllNodesWithContentDescription("Sun sticker").fetchSemanticsNodes().isNotEmpty())
        r.fire(1)
        r.advance(1_600)
        assertTrue(r.eggs.dusk)
        assertEquals(ThemeVariant.Dusk, r.resolved)
        assertTrue("the moon by the title", compose.onAllNodesWithContentDescription("Moon sticker").fetchSemanticsNodes().isNotEmpty())
    }

    /**
     * Egg 16 keeps the list whole (§12.2): the viewport still reaches the bar, so no strip turns to haze above it, and the
     * umbrella's canopy rises at most about 16 dp above the bar's top, into the band where the list fades into the bar.
     */
    @Test
    fun theUmbrellaKeepsToTheBarsEdge() {
        val r = EggRig(compose).start(EggListUnderTest.Real)
        r.fire(16)
        r.advance(600)
        assertTrue(r.eggs.umbrella)
        val bar = r.anchors.bounds(AnchorRegistry.ADD_BAR)!!
        val list = r.anchors.bounds(EggAnchors.LIST)!!
        val umbrella = r.probe.frames.last { it.tag == "umbrella" }.bounds
        val dp = 3f
        assertTrue("the list keeps its whole viewport (${list.bottom} ≥ ${bar.top})", list.bottom >= bar.top)
        assertTrue("the canopy rises at most 16 dp above the bar (${(bar.top - umbrella.top) / dp} dp)", bar.top - umbrella.top <= 16.5f * dp)
        r.capture("eggs_on_list_rain_day")
    }
}

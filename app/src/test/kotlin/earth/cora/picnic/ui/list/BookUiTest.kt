package earth.cora.picnic.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.add.ADD_FIELD_TAG
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.focusWithoutTouch
import earth.cora.picnic.ui.list.motion.BookFx
import earth.cora.picnic.ui.theme.BookRules
import earth.cora.picnic.ui.theme.BookTags
import earth.cora.picnic.core.model.setOpenAisle
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The trip sticker book's rules (docs/DESIGN_STICKER.md §7.3–§7.5; items 147, 161): the hand-stuck tilts, the holo next
 * spot, the plain fore-edge until full or PERFECT, and when the book collapses to its 40 dp strip.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class BookUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val rots = listOf(-6f, 4f, -2f, 7f, -5f, 3f, 6f, -4f, 2f, -7f, 5f, -3f)

    /** Item 147: twins tilt by ROTS[slot % 12]; the lowest free spot is the holo "next" one, holes first. */
    @Test
    fun tiltsAndNextSpot() {
        for (slot in 0 until 40) assertEquals("slot $slot", rots[slot % 12], BookRules.tilt(slot), 0f)
        val book = ListFixture.ui().book!!
        assertEquals("ten twins in spots 0–9: the next is 10", 10, BookRules.nextSpot(book, BookFx()))
        // An un-check leaves a hole; the next check fills it (§7.2).
        val holed = ListFixture.ui(ListFixture.state().toggleChecked(ListFixture.LIST_ID, "carrots", ListFixture.NOW)).book!!
        val carrots = ListFixture.ui().row("carrots")!!.slot!!
        assertEquals("the hole is the next spot", carrots, BookRules.nextSpot(holed, BookFx()))
        // While a twin waits to appear, its spot stays the holo one.
        assertEquals(10, BookRules.nextSpot(holed, BookFx(holdNext = 10)))
        assertNull("a full book has no next spot", BookRules.nextSpot(ListFixture.ui(ListFixture.allBut(emptyList())).book!!, BookFx()))
    }

    /** Item 147: the fore-edge stays plain periwinkle until the book is full or PERFECT. */
    @Test
    fun foreEdgeHoloOnlyWhenFull() {
        assertFalse("mid-trip", BookRules.foreEdgeHolo(ListFixture.ui().book!!, BookFx()))
        assertFalse("a fresh trip", BookRules.foreEdgeHolo(ListFixture.ui(ListFixture.fresh()).book!!, BookFx()))
        val full = ListFixture.ui(ListFixture.allBut(emptyList())).book!!
        assertTrue("full", BookRules.foreEdgeHolo(full, BookFx()))
        assertFalse("full, but its last twin has not appeared yet", BookRules.foreEdgeHolo(full, BookFx(fullHeld = true)))
        assertTrue("PERFECT", BookRules.foreEdgeHolo(ListFixture.ui(ListFixture.done()).book!!, BookFx()))
    }

    /**
     * Item 161: the book collapses to the 40 dp strip on scroll-down (with the app bar) and expands on scroll-up; it
     * collapses while the IME is open; an empty list has no book.
     */
    @Test
    fun collapseRules() {
        val rig = ListRig(ListFixture.longAisle())
        compose.show(rig)
        val tall = compose.boundsOf(BookTags.BOOK).height / compose.density.density
        assertTrue("expanded: 76 dp or more ($tall)", tall >= 75f)
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 120 * compose.density.density, endY = top + 20 * compose.density.density, durationMillis = 400) }
        compose.waitForIdle()
        val strip = compose.boundsOf(BookTags.BOOK).height / compose.density.density
        assertEquals("scrolled down: the 40 dp strip", 40f, strip, 1f)
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeDown(startY = top + 20 * compose.density.density, endY = centerY + 120 * compose.density.density, durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeDown(startY = top + 20 * compose.density.density, endY = centerY + 120 * compose.density.density, durationMillis = 400) }
        compose.waitForIdle()
        val back = compose.boundsOf(BookTags.BOOK).height / compose.density.density
        assertTrue("expanded again on the way up ($back)", back >= 75f)

        // The keyboard: the book folds to its strip so the add bar has room.
        compose.focusWithoutTouch(compose.onNodeWithTag(ADD_FIELD_TAG))
        val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (300 * compose.density.density).toInt()))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
        val ime = compose.boundsOf(BookTags.BOOK).height / compose.density.density
        assertEquals("IME open: the 40 dp strip", 40f, ime, 1f)
    }

    /**
     * Item 161: the height change animates (`standard`, 200 ms) so the list never jumps: sampled on the held clock, the
     * book passes through heights between 76 and 40 dp on the way down and on the way back, and settles by about 200 ms.
     */
    @Test
    fun collapseAnimatesItsHeight() {
        val screen = ListScreenState()
        compose.setContent { earth.cora.picnic.ui.theme.PicnicTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { ListScreen(ListFixture.ui(), ListActions(), screen = screen) } } }
        compose.waitForIdle()
        val dp = compose.density.density
        fun height() = compose.boundsOf(BookTags.BOOK).height / dp
        assertEquals(76f, height(), 1f)
        compose.mainClock.autoAdvance = false
        fun samples(): List<Float> = buildList { repeat(16) { compose.frames(1); add(height()) } }
        screen.scrolledDown = true
        val down = samples()
        assertTrue("passes between 76 and 40 dp on the way down: $down", down.any { it in 45f..71f })
        assertTrue("never jumps more than half the way in one frame: $down", down.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) < 18f })
        assertEquals("settled on the strip by about 200 ms: $down", 40f, down.drop(14).last(), 1f)
        screen.scrolledDown = false
        val up = samples()
        assertTrue("and on the way up: $up", up.any { it in 45f..71f })
        assertEquals(76f, up.last(), 1f)
        compose.mainClock.autoAdvance = true
    }

    /** Under reduced motion the book swaps at once (§8.8): no in-between heights. */
    @Test
    fun collapseSnapsUnderReducedMotion() {
        val screen = ListScreenState()
        compose.setContent { earth.cora.picnic.ui.theme.PicnicTheme(reducedMotion = true) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { ListScreen(ListFixture.ui(), ListActions(), screen = screen) } } }
        compose.waitForIdle()
        val dp = compose.density.density
        compose.mainClock.autoAdvance = false
        screen.scrolledDown = true
        compose.frames(2)
        assertEquals(40f, compose.boundsOf(BookTags.BOOK).height / dp, 1f)
        compose.mainClock.autoAdvance = true
    }

    /**
     * Items 146 and R2: the on-page date tag, its −2° turn and its hard shadow included, never touches a stamp circle,
     * with 8 (24 dp stamps), 14 (20 dp) and 20 stops (three rows), at font scale 1.0 and 1.3; the grid stays inside the page.
     */
    @Test
    fun dateTagClearsTheStamps() {
        val cases = listOf(ListFixture.state() to 8, ListFixture.everyDefaultAisle() to 14, ListFixture.stops(20, 1, 3) to 20)
        val probe = RecordingProbe()
        // One composition (the rule takes one setContent): the case and the font scale change under it.
        val current = androidx.compose.runtime.mutableStateOf(ListRig(cases.first().first) to 1f)
        compose.setContent {
            val (rig, fontScale) = current.value
            val base = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.key(rig, fontScale) {
                androidx.compose.runtime.CompositionLocalProvider(
                    earth.cora.picnic.ui.theme.LocalPieceProbe provides probe,
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale),
                ) { rig.Themed() }
            }
        }
        for (fontScale in listOf(1f, 1.3f)) {
            for ((state, stops) in cases) {
                val rig = ListRig(state)
                compose.runOnIdle { current.value = rig to fontScale }
                compose.waitForIdle()
                val d = compose.density.density
                val book = rig.vm.state.value.book!!
                assertEquals(stops, book.stamps.size)
                val left = probe["bookLeft"]
                val tag = probe["dateTag"]
                val layout = earth.cora.picnic.ui.theme.BookLayout.of(compose.boundsOf(BookTags.BOOK).width / d, stops, book.total)
                val top = earth.cora.picnic.ui.theme.stampTop(true, layout)
                for (i in 0 until minOf(stops, layout.circles)) {
                    val (cx, cy) = earth.cora.picnic.ui.theme.stampCentre(i, layout, top)
                    val centre = androidx.compose.ui.geometry.Offset(left.left + cx * d, left.top + cy * d)
                    val gap = tagGap(tag, centre, layout.stampSize / 2f * d, d)
                    assertTrue("fs $fontScale, $stops stops, stamp ${i + 1}: the tag or its shadow is ${-gap / d} dp into the circle", gap >= 0f)
                    assertTrue("fs $fontScale, $stops stops, stamp ${i + 1}: inside the page", cy + layout.stampSize / 2f <= layout.height - 6f + 0.01f)
                }
            }
        }
    }

    /**
     * How far (px) the circle at [centre] with radius [r] is from the date tag laid out at [tag] (its box before the turn)
     * once turned −2° about its centre, and from its hard shadow (the same box moved by hs, 1.6 × 2.2 dp, in the turned
     * frame). Negative when they overlap.
     */
    private fun tagGap(tag: androidx.compose.ui.geometry.Rect, centre: androidx.compose.ui.geometry.Offset, r: Float, d: Float): Float {
        val a = Math.toRadians(2.0)
        val dx = centre.x - tag.center.x
        val dy = centre.y - tag.center.y
        // Into the tag's own frame: undo the −2° turn.
        val lx = (dx * kotlin.math.cos(a) - dy * kotlin.math.sin(a)).toFloat() + tag.width / 2f
        val ly = (dx * kotlin.math.sin(a) + dy * kotlin.math.cos(a)).toFloat() + tag.height / 2f
        fun dist(l: Float, t: Float, rr: Float, b: Float): Float {
            val ex = maxOf(l - lx, 0f, lx - rr)
            val ey = maxOf(t - ly, 0f, ly - b)
            return kotlin.math.hypot(ex, ey)
        }
        val body = dist(0f, 0f, tag.width, tag.height)
        val shadow = dist(1.6f * d, 2.2f * d, tag.width + 1.6f * d, tag.height + 2.2f * d)
        return minOf(body, shadow) - r
    }

    /**
     * §7.3 and egg 11: with a seasonal sheet active the date tag carries its pictogram (the pumpkin in the Thanksgiving
     * window, Appendix A's date); with the eggs off it does not; at font scale 2.0 the caption carries it.
     */
    @Test
    fun seasonPictogramOnTheDate() {
        val rig = ListRig()
        val probe = RecordingProbe()
        rig.surprises.states.value = earth.cora.picnic.ui.eggs.SurpriseState(season = earth.cora.picnic.core.calendar.Season.THANKSGIVING)
        compose.show(rig, probe)
        assertEquals(earth.cora.picnic.core.calendar.Season.THANKSGIVING, rig.vm.state.value.book!!.season)
        val mark = probe["season:THANKSGIVING"]
        val tag = probe["dateTag"]
        assertTrue("the pumpkin rides on the date tag ($mark in $tag)", mark.left >= tag.left && mark.right <= tag.right + 0.5f && mark.top >= tag.top - 0.5f && mark.bottom <= tag.bottom + 0.5f)
        // Eggs off: no pictogram.
        rig.surprises.states.value = earth.cora.picnic.ui.eggs.SurpriseState(enabled = false, season = earth.cora.picnic.core.calendar.Season.THANKSGIVING)
        compose.waitForIdle()
        assertTrue(probe.keys("season:").isEmpty())
    }

    @Test
    fun seasonPictogramInTheCaption() {
        val rig = ListRig()
        val probe = RecordingProbe()
        rig.surprises.states.value = earth.cora.picnic.ui.eggs.SurpriseState(season = earth.cora.picnic.core.calendar.Season.THANKSGIVING)
        compose.show(rig, probe, fontScale = 2f)
        val book = compose.boundsOf(BookTags.BOOK)
        val mark = probe["season:THANKSGIVING"]
        assertTrue("in the caption row above the book ($mark, book $book)", mark.bottom <= book.top)
    }

    /** Item 161: a window under 600 dp keeps the book collapsed; the magic then lands in the newest position. */
    @Test
    @Config(qualifiers = "w360dp-h560dp-xxhdpi")
    fun shortWindowCollapsed() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        val probe = RecordingProbe()
        compose.show(rig, probe)
        val h = compose.boundsOf(BookTags.BOOK).height / compose.density.density
        assertEquals("a short window: the 40 dp strip", 40f, h, 1f)
        assertTrue("no expanded pages", probe.keys("bookRight").isEmpty())
        compose.onNodeWithTag(ListTags.row("strawberries")).performTouchInput { click(center) }
        compose.waitForIdle()
        val twins = probe.keys("collapsedTwin:").associateWith { probe[it] }
        assertTrue("the new twin (spot 10) shows in the strip: ${twins.keys}", "collapsedTwin:10" in twins)
        assertEquals("in the newest (right-most) place", "collapsedTwin:10", twins.maxByOrNull { it.value.right }!!.key)
    }

    /** Item 161: an empty list has no book (the empty state takes the screen, §4.21). */
    @Test
    fun emptyListHasNoBook() {
        compose.show(ListRig(ListFixture.empty()))
        assertFalse(compose.exists(BookTags.BOOK))
    }
}

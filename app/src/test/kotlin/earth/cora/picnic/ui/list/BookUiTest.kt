package earth.cora.picnic.ui.list

import androidx.activity.ComponentActivity
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

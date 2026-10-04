package earth.cora.picnic.ui.list

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.BookTags
import org.junit.Assert.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Rule R6 (docs/DESIGN_STICKER.md §0.1, §4.10, §6.1; items 95, 96): the top stack and the pinned plate repaint the
 * binder ground behind themselves. Pixel probes beside and below the book, in the gap above the plate, in the gutters
 * beside it and in its rounded top corners must show the ground (the mint binder or its pinstripe, Day) while the
 * list's rows scroll underneath, never a row's white panel, its ink or its stickers.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class PinnedBackingProbeTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    /** The Day ground (§2.1): the binder and its pinstripe, and every anti-aliased blend between them. */
    private fun isGround(argb: Int): Boolean {
        val binder = intArrayOf(0xE6, 0xF6, 0xEE)
        val stripe = intArrayOf(0xCD, 0xEE, 0xDD)
        val c = intArrayOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
        var best = Int.MAX_VALUE
        for (k in 0..20) {
            val t = k / 20f
            var worst = 0
            for (i in 0..2) worst = max(worst, abs(c[i] - (binder[i] + (stripe[i] - binder[i]) * t).roundToInt()))
            best = minOf(best, worst)
        }
        return best <= 6
    }

    private fun shot(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    /** Every pixel of [r] (root px) in [bmp] is ground; [what] names the zone in the failure. */
    private fun assertGround(bmp: Bitmap, r: Rect, what: String) {
        if (r.width < 1f || r.height < 1f) return
        val x0 = r.left.roundToInt().coerceIn(0, bmp.width - 1)
        val x1 = r.right.roundToInt().coerceIn(0, bmp.width)
        val y0 = r.top.roundToInt().coerceIn(0, bmp.height - 1)
        val y1 = r.bottom.roundToInt().coerceIn(0, bmp.height)
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val p = bmp.getPixel(x, y)
                assertTrue("$what: pixel ($x, $y) = #${Integer.toHexString(p)} is not the ground", isGround(p))
            }
        }
    }

    private fun assertGroundAt(bmp: Bitmap, x: Float, y: Float, what: String) =
        assertGround(bmp, Rect(x - 0.5f, y - 0.5f, x + 0.5f, y + 0.5f), what)

    /** The top stack's margins: beside the book (12 dp) and the 8 dp under it, outside its hard shadow (+2.4, +3.4). */
    private fun probeTopStack(bmp: Bitmap, label: String) {
        val book = compose.boundsOf(BookTags.BOOK)
        val list = compose.boundsOf(ListTags.LIST)
        assertGround(bmp, Rect(1 * dp, book.top + 2 * dp, book.left - 1 * dp, book.bottom - 2 * dp), "$label: left of the book")
        assertGround(bmp, Rect(book.right + 3.5f * dp, book.top + 4 * dp, bmp.width - 1 * dp, book.bottom - 2 * dp), "$label: right of the book")
        val gapTop = book.bottom + 4 * dp
        val gapBottom = list.top - 0.5f
        // A book of more than one page hangs its page number into the gap (§7.3): that chip is the book's, not the list's;
        // the ground is checked on both sides of it (and of its hard shadow on the right).
        val lcd = if (compose.exists(BookTags.PAGE_LCD, unmerged = true)) compose.boundsOf(BookTags.PAGE_LCD, unmerged = true) else null
        if (lcd == null) {
            assertGround(bmp, Rect(1 * dp, gapTop, bmp.width - 1 * dp, gapBottom), "$label: between the book and the list")
        } else {
            assertTrue("$label: the page number ends where the list begins (${lcd.bottom} vs ${list.top})", lcd.bottom <= list.top + 0.5f)
            assertGround(bmp, Rect(1 * dp, gapTop, lcd.left - 1 * dp, gapBottom), "$label: between the book and the list, left of the page number")
            assertGround(bmp, Rect(lcd.right + 3.5f * dp, gapTop, bmp.width - 1 * dp, gapBottom), "$label: between the book and the list, right of the page number")
        }
    }

    /**
     * Item 95: the top stack has a solid ground. Household's eleven rows scroll by in steps (and mid-fling); every probe
     * around the book stays ground.
     */
    @Test
    fun topStack() {
        val rig = ListRig(ListFixture.longAisle())
        compose.show(rig)
        probeTopStack(shot(), "at rest")
        val firstRow = compose.boundsOf(ListTags.strip("produce")).top
        compose.mainClock.autoAdvance = false
        repeat(4) { step ->
            // From the middle of the list (its bottom is under the docked add bar).
            compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 80 * dp, endY = centerY - 80 * dp, durationMillis = 300) }
            // Mid-fling and at rest.
            repeat(3) {
                compose.advanceMs(120)
                probeTopStack(shot(), "scroll $step, frame $it")
            }
            compose.advanceMs(1200)
            probeTopStack(shot(), "scroll $step, settled")
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the list really scrolled", !compose.exists(ListTags.strip("produce")) || compose.boundsOf(ListTags.strip("produce")).top < firstRow - 100 * dp)
    }

    /**
     * Item 96: the pinned plate is backed by the ground behind its rounded corners, in the gutters beside it and in any
     * gap above it, with Household's rows under it at three scroll positions.
     */
    @Test
    fun plateCornersAndGap() {
        val rig = ListRig(ListFixture.longAisle())
        compose.show(rig)
        val ring = 3 * dp
        for (key in listOf("dish-soap", "laundry", "garbage-bags", "foil")) {
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey(key)
            compose.waitForIdle()
            val list = compose.boundsOf(ListTags.LIST)
            val plate = compose.boundsOf(ListTags.header("household"))
            assertTrue("$key: the plate is pinned", abs(plate.top - list.top) <= 1f)
            val bmp = shot()
            // Above the plate (flush, so usually empty) and in the gutters beside it, outside the lemon ring.
            assertGround(bmp, Rect(list.left, list.top, list.right, plate.top), "$key: above the plate")
            assertGround(bmp, Rect(list.left + 1 * dp, plate.top, plate.left - ring - 0.6f * dp, plate.bottom), "$key: left of the plate")
            assertGround(bmp, Rect(plate.right + ring + 2.6f * dp + 0.6f * dp, plate.top, list.right - 0.5f * dp, plate.bottom), "$key: right of the plate")
            // The rounded top corners, outside the ring's 15 dp arc: rows scrolled under the plate must not show there.
            assertGroundAt(bmp, plate.left - ring + 0.8f * dp, plate.top + 0.8f * dp, "$key: top-left corner")
            assertGroundAt(bmp, plate.left - ring + 2f * dp, plate.top + 0.6f * dp, "$key: top-left corner")
            assertGroundAt(bmp, plate.right + ring - 0.8f * dp, plate.top + 0.8f * dp, "$key: top-right corner")
            assertGroundAt(bmp, plate.right + ring - 2f * dp, plate.top + 0.6f * dp, "$key: top-right corner")
        }
    }

    /**
     * Item 313, R6: a plate never rests partly pushed. With each aisle open in turn, she scrolls to the list's end (her
     * own drags); at rest, the open tray's plate is either whole or gone, never a sliced band at the top.
     */
    @Test
    fun listEndNoSlicedPlate() {
        val opens = listOf("produce", "bakery", "deli-meat", "aisle-2-baking", "breakfast", "dairy-eggs", "frozen", "household")
        val rigs = opens.map { ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, it)) }
        var current by androidx.compose.runtime.mutableStateOf(rigs.first())
        compose.setContent { androidx.compose.runtime.key(current) { current.Themed() } }
        for ((i, open) in opens.withIndex()) {
            compose.runOnIdle { current = rigs[i] }
            compose.waitForIdle()
            repeat(6) {
                compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 80 * dp, endY = centerY - 140 * dp, durationMillis = 250) }
                compose.waitForIdle()
            }
            val list = compose.boundsOf(ListTags.LIST)
            if (compose.exists(ListTags.header(open))) {
                val plate = compose.boundsOf(ListTags.header(open))
                assertTrue("$open: the plate rests whole or gone (${plate.top}..${plate.bottom} vs ${list.top})", plate.top >= list.top - 1f || plate.bottom <= list.top + 1f)
            }
        }
    }
}

package earth.cora.picnic.ui.list

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The top stack grows into the book when the first items land on an empty list (docs/DESIGN_STICKER.md §6.1, §4.21,
 * §8.2; item 369): the book card slides out from under the bar over the 240 ms `layout` while the LCD and the stop count
 * fade in, and the list below moves down with it in the same frames, never left behind and never jumping. Under reduced
 * motion the top stack takes its height at once and the book, the LCD and the stop count fade in place over 120 ms
 * (§8.8). The recording showed all of it in one frame (write 341 → 342).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TopStackGrowthTest {
    @get:Rule
    val compose = createComposeRule()

    /** One held frame: the top stack, the list's viewport and the subtitle's patch. */
    private class Shot(val stack: Rect, val list: Rect?, val subtitle: Rect?)

    private class Landing(val empty: Shot, val shots: List<Shot>, val lcdShown: List<Float>)

    /**
     * Shows the empty list, lands the fixture's items on it in one write (as "Stick all" does) and records [frames] held
     * frames, with how much of the LCD shows in the first [lcdFrames] of them (0: none of it, 1: as at rest).
     */
    private fun land(reduced: Boolean, frames: Int = 30, lcdFrames: Int = 12): Landing {
        val rig = ListRig(ListFixture.empty())
        compose.show(rig, reducedMotion = reduced)
        compose.mainClock.autoAdvance = false
        compose.frames(2)
        fun shot() = Shot(
            stack = compose.boundsOf(ListTags.TOP_STACK),
            list = if (compose.exists(ListTags.LIST)) compose.boundsOf(ListTags.LIST) else null,
            subtitle = rig.engine.anchors.bounds("text:subtitle"),
        )
        val empty = shot()
        val before = root()
        rig.scope.launch { rig.store.update { ListFixture.fresh() } }
        val shots = ArrayList<Shot>()
        val bitmaps = ArrayList<Bitmap>()
        repeat(frames) { i ->
            compose.frames(1)
            shots += shot()
            if (i < lcdFrames) bitmaps += root()
        }
        compose.frames(20)
        val after = root()
        val lcd = compose.boundsOf(ListTags.LCD_SLOT)
        compose.mainClock.autoAdvance = true
        // How far each frame's LCD region is from the one at rest, as a share of how far the empty bar was from it.
        val absent = difference(before, after, lcd)
        assertTrue("the LCD shows at rest ($absent)", absent > 4f)
        return Landing(empty, shots, bitmaps.map { 1f - difference(it, after, lcd) / absent })
    }

    private fun root(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    /** The mean difference per channel between [a] and [b] inside [r]. */
    private fun difference(a: Bitmap, b: Bitmap, r: Rect): Float {
        var sum = 0L
        var n = 0
        for (y in r.top.toInt() until r.bottom.toInt()) for (x in r.left.toInt() until r.right.toInt()) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            sum += abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)) + abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)) + abs((p and 0xFF) - (q and 0xFF))
            n += 3
        }
        return sum.toFloat() / n
    }

    /**
     * The top stack grows over several frames, never more than half the way in one, and only ever grows; the list's
     * viewport starts at its foot in every frame (it moves down with it); the subtitle's patch widens for the stop count
     * over several frames; the LCD fades in.
     */
    @Test
    fun theBookGlidesInWithTheFirstItems() {
        val (empty, shots, lcd) = land(reduced = false).let { Triple(it.empty, it.shots, it.lcdShown) }
        val px = compose.density.density
        val full = shots.last().stack.height
        val way = full - empty.stack.height
        assertTrue("the top stack grows by the book ($way px)", way > 60 * px)
        var last = empty.stack.height
        val steps = ArrayList<Float>()
        for ((i, s) in shots.withIndex()) {
            val h = s.stack.height
            assertTrue("frame $i: it only grows ($last → $h)", h >= last - 1f)
            steps += h - last
            last = h
            s.list?.let { assertEquals("frame $i: the list starts at the top stack's foot", s.stack.bottom, it.top, 1.5f) }
        }
        assertTrue("a glide: no frame covers half the way (${steps.map { it.toInt() }})", steps.max() < way * 0.5f)
        assertTrue("over several frames (${steps.map { it.toInt() }})", steps.count { it > 1f } >= 3)
        val widths = (listOf(empty) + shots).mapNotNull { it.subtitle?.width }
        val growth = widths.last() - widths.first()
        assertTrue("the stop count joins the store's name (${widths.first()} → ${widths.last()})", growth > 20 * px)
        val widthSteps = widths.zipWithNext { a, b -> b - a }.filter { it > 0.5f }
        assertTrue("its patch widens over several frames (${widthSteps.map { it.toInt() }})", widthSteps.size >= 3 && widthSteps.max() < growth * 0.6f)
        assertTrue("the LCD fades in: part shown in some frames (${lcd.map { (it * 100).toInt() }})", lcd.count { it in 0.1f..0.9f } >= 2)
        assertTrue("then wholly", lcd.last() > 0.9f)
    }

    /**
     * §8.8: under reduced motion the top stack takes the book's height in one frame (no glide) and the LCD fades in
     * place over 120 ms.
     */
    @Test
    fun reducedMotionFadesInPlace() {
        val (empty, shots, lcd) = land(reduced = true).let { Triple(it.empty, it.shots, it.lcdShown) }
        val heights = listOf(empty.stack.height) + shots.map { it.stack.height }
        val moves = heights.zipWithNext { a, b -> b - a }.filter { abs(it) > 0.5f }
        assertEquals("the height changes in one frame (${moves.map { it.toInt() }})", 1, moves.size)
        shots.forEach { s -> s.list?.let { assertEquals("the list starts at the top stack's foot", s.stack.bottom, it.top, 1.5f) } }
        assertTrue("the LCD fades in place (${lcd.map { (it * 100).toInt() }})", lcd.count { it in 0.1f..0.9f } >= 2)
        assertTrue("then wholly", lcd.last() > 0.9f)
    }
}

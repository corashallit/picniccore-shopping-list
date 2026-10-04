package earth.cora.picnic.ui.list

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.TrayMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Rule R1 (docs/DESIGN_STICKER.md §0.1, §4.10; items 92–94): the open aisle is one tray (band, ruled panel, end cap),
 * every one of its rows lies inside it and no other aisle's row does; while its rows scroll, the pinned plate is that
 * tray's band.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TrayContainmentTest {
    @get:Rule
    val compose = createComposeRule()

    private val produce = listOf("strawberries", "bananas", "apples", "carrots", "broccoli")

    private fun rowSlots(): List<String> = compose.onAllNodes(hasTagPrefix("picnic_row_slot_")).fetchSemanticsNodes()
        .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag)?.removePrefix("picnic_row_slot_") }

    /** The node's laid-out bounds in the root, not clipped by the list's viewport (a row half under its edge is whole). */
    private fun laidOut(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun containment(fontScale: Float?, variant: ThemeVariant) {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig, variant = variant, fontScale = fontScale)
        val px = compose.density.density
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:produce")
        compose.waitForIdle()
        val header = compose.boundsOf(ListTags.header("produce"))
        var last = header.bottom
        // Every row sits on the tray's panel between its band and its cap, edge to edge with the next: no gaps, no
        // floating cards (R1).
        val visible = produce.filter { compose.exists(ListTags.rowSlot(it)) }
        assertTrue("rows on screen at font scale ${fontScale ?: 1f}", visible.size >= 2)
        val rows = visible.map { it to laidOut(ListTags.rowSlot(it)) }
        for ((id, r) in rows) {
            assertEquals("$id spans the tray's width", header.left, r.left, 0.5f)
            assertEquals("$id spans the tray's width", header.right, r.right, 0.5f)
            assertTrue("$id starts where the slice above ends (${r.top} vs $last)", abs(r.top - last) <= 0.5f)
            last = r.bottom
        }
        if (visible.size == produce.size) {
            var cap = if (compose.exists(ListTags.cap("produce"))) laidOut(ListTags.cap("produce")) else null
            if (cap == null) {
                // Large text: the last row runs under the viewport's edge and the cap waits below it. The last row
                // scrolls to the top with the cap after it; the shift maps the cap back into the rows' frame.
                val before = rows.last().second
                compose.onNodeWithTag(ListTags.LIST).performScrollToKey(produce.last())
                compose.waitForIdle()
                val after = laidOut(ListTags.rowSlot(produce.last()))
                cap = laidOut(ListTags.cap("produce")).translate(0f, before.top - after.top)
            }
            assertTrue("the cap closes the tray under its last row", abs(cap.top - last) <= 0.5f)
            val tray = Rect(header.left, header.top, header.right, cap.bottom)
            for ((id, r) in rows) assertTrue("$id inside the tray $tray: $r", tray.contains(r.topLeft) && r.right <= tray.right + 0.5f && r.bottom <= tray.bottom + 0.5f)
        }
        // No other aisle's row is laid out at all: the folded strips hold none.
        assertTrue("only Produce rows: ${rowSlots()}", rowSlots().all { it in produce })
        assertTrue("a row is at least 56 dp", rows.all { it.second.height / px >= 55.5f })
    }

    /** Item 92: Produce open, every row inside its tray, edge to edge. */
    @Test
    fun rowsInsideTray() = containment(null, ThemeVariant.Day)

    /** Item 94: the same in Glow and at font scale 2.0 (rows grow, the tray grows with them). */
    @Test
    fun rowsInsideTrayGlowAndLargeText() {
        containment(2f, ThemeVariant.Glow)
    }

    /**
     * Item 93: while Household's eleven rows scroll, its band pins flush to the top of the list as the plate; the plate
     * is the same tray (its width, its section) and the rows run on under it inside the tray.
     */
    @Test
    fun pinnedPlateBelongsToTray() {
        val rig = ListRig(ListFixture.longAisle())
        compose.show(rig)
        val list = compose.boundsOf(ListTags.LIST)
        for (key in listOf("dish-soap", "laundry", "foil")) {
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey(key)
            compose.waitForIdle()
            val headers = compose.onAllNodes(hasTagPrefix("picnic_tray_header_")).fetchSemanticsNodes()
            assertEquals("one plate", 1, headers.size)
            assertEquals(ListTags.header("household"), headers.single().config[SemanticsProperties.TestTag])
            val plate = headers.single().boundsInRoot
            assertEquals("pinned flush to the list's top ($key)", list.top, plate.top, 1f)
            val rows = rowSlots().map { compose.boundsOf(ListTags.rowSlot(it)) }.filter { it.bottom > plate.bottom }
            assertTrue("rows show under the plate", rows.isNotEmpty())
            for (r in rows) {
                assertEquals("same tray width", plate.left, r.left, 0.5f)
                assertEquals("same tray width", plate.right, r.right, 0.5f)
            }
            // The row the plate rests on runs on under it: the tray continues, no gap opens between band and panel.
            assertTrue("a row passes under the plate's bottom edge", rows.any { it.top <= plate.bottom + 0.5f })
            assertTrue("only Household rows", rowSlots().all { rig.vm.state.value.row(it)?.sectionId == "household" })
        }
    }

    /**
     * §4.10, §8.4 (trip review): while the next stop's tray unfolds and the strips below slide down to make room, the
     * strip below never covers the opening tray's cap. In every frame where the cap and the next strip overlap, the
     * overlap shows the cap (drawn over the strip), not the strip's own face.
     */
    @Test
    fun openingTrayCapDrawsOverTheStripBelow() {
        val rig = ListRig(ListFixture.state())
        compose.show(rig)
        val px = compose.density.density
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.check("baking-powder"), useUnmergedTree = true).performClick()
        class Overlap(val t: Int, val strip: IntRect, val band: IntRect, val pixels: IntArray)
        val overlaps = mutableListOf<Overlap>()
        for (i in 0 until 260) {
            compose.frames(1)
            if (!compose.exists(ListTags.cap("breakfast")) || !compose.exists(ListTags.strip("dairy-eggs"))) continue
            val cap = compose.boundsOf(ListTags.cap("breakfast")).roundToIntRect()
            val strip = compose.boundsOf(ListTags.strip("dairy-eggs")).roundToIntRect()
            val band = cap.intersect(strip)
            if (band.width <= 0 || band.height < (2 * px).toInt()) continue
            val shot = compose.onRoot().captureToImage().asAndroidBitmap()
            val pixels = IntArray(band.width * band.height)
            shot.getPixels(pixels, 0, band.width, band.left, band.top, band.width, band.height)
            overlaps += Overlap(i * 16, strip, band, pixels)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the strip below slides past the opening tray's cap", overlaps.isNotEmpty())
        // The strip's own face, settled: what the overlap would show if the strip were drawn over the cap.
        val settled = compose.boundsOf(ListTags.strip("dairy-eggs")).roundToIntRect()
        val face = compose.onRoot().captureToImage().asAndroidBitmap()
        for (o in overlaps) {
            var same = 0
            for (y in 0 until o.band.height) for (x in 0 until o.band.width) {
                val sx = settled.left + (o.band.left + x - o.strip.left)
                val sy = settled.top + (o.band.top + y - o.strip.top)
                if (o.pixels[y * o.band.width + x] == face.getPixel(sx, sy)) same++
            }
            val share = same.toFloat() / o.pixels.size
            assertTrue("t=${o.t}: the cap shows over the strip in ${o.band} (${(share * 100).toInt()} % is the strip's face)", share < 0.5f)
        }
    }

    /**
     * §8.4 (trip review): the aisle-done curl folds back from the tray panel's inner corner (inside its white border and
     * side band, so nothing reaches the screen's edge), its legs at most min(64 dp, 18 % of the tray, the row's 52 dp
     * check column), so at any text size it never covers the last row's name or detail.
     */
    private fun curlStaysInTheCheckColumn(fontScale: Float?) {
        val baking = "aisle-2-baking"
        val rig = ListRig(ListFixture.state())
        compose.show(rig, fontScale = fontScale)
        val px = compose.density.density
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:$baking")
        compose.waitForIdle()
        // The tray's cap clear of the add bar (its doodle and washi tape included), so the whole corner shows.
        val dock = listOf(earth.cora.picnic.ui.add.ADD_BAR_TAG, earth.cora.picnic.ui.add.ADD_BAR_DOODLE_TAG, earth.cora.picnic.ui.add.ADD_BAR_WASHI_TAG)
            .filter { compose.exists(it, unmerged = true) }.minOf { compose.boundsOf(it, unmerged = true).top }
        val overlap = compose.boundsOf(ListTags.cap(baking)).bottom + 8 * px - dock
        if (overlap > 0f) {
            compose.onNodeWithTag(ListTags.LIST).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, overlap) }
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.check("baking-powder"), useUnmergedTree = true).performClick()
        fun curlT0(): Long? = rig.engine.current.value.aisles[baking]?.curlT0
        // The frame before the curl, then the curl at its fullest (its last step, 225 ms on).
        var before: android.graphics.Bitmap? = null
        assertTrue("the curl starts", compose.framesUntil(3000) { curlT0() != null || run { before = compose.onRoot().captureToImage().asAndroidBitmap(); false } })
        val ids = listOf("flour", "sugar", "baking-powder")
        val last = ids.filter { compose.exists(ListTags.rowSlot(it)) }.maxBy { laidOut(ListTags.rowSlot(it)).bottom }
        val slot = compose.boundsOf(ListTags.rowSlot(last))
        val texts = listOf(ListTags.name(last), ListTags.detail(last)).filter { compose.exists(it, unmerged = true) }.map { compose.boundsOf(it, unmerged = true) }
        assertTrue("the last row's words are laid out", texts.isNotEmpty())
        assertTrue("the curl grows", compose.framesUntil(600) { rig.engine.time - curlT0()!! >= 230 })
        val after = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.autoAdvance = true
        val inset = TrayMetrics.panelInset.value * px
        val leg = minOf(64 * px, slot.width * 0.18f, ROW_CHECK_COLUMN.value * px)
        val corner = androidx.compose.ui.geometry.Offset(slot.right - inset, slot.bottom)
        // The shadow sits 1.6 × 2.2 dp off the flap; antialiasing adds a pixel.
        val allowed = Rect(corner.x - leg - 1.5f * px, corner.y - leg - 1.5f * px, corner.x + 2.6f * px, corner.y + 3.2f * px)
        val scan = Rect(slot.left, slot.top, slot.right, slot.bottom + 8 * px).intersect(Rect(0f, 0f, after.width.toFloat(), after.height.toFloat()))
        var changed = 0
        var outside = 0
        var overWords = 0
        for (y in scan.top.toInt() until scan.bottom.toInt()) for (x in scan.left.toInt() until scan.right.toInt()) {
            // A visible change (the tray's edges shimmer by a step or two of antialiasing as the ring breathes).
            if (inkDistance(after.getPixel(x, y), before!!.getPixel(x, y)) <= 16) continue
            changed++
            val p = androidx.compose.ui.geometry.Offset(x + 0.5f, y + 0.5f)
            if (!allowed.contains(p)) outside++
            if (texts.any { it.contains(p) }) overWords++
        }
        val scale = fontScale ?: 1f
        assertTrue("the curl shows on $last at $scale", changed > (leg * leg / 4).toInt())
        assertEquals("the curl stays in the panel's inner corner on $last at $scale (allowed $allowed)", 0, outside)
        assertEquals("the curl covers none of $last's words at $scale", 0, overWords)
        assertTrue("legs ≤ 52 dp", leg <= ROW_CHECK_COLUMN.value * px + 0.5f)
    }

    private fun inkDistance(a: Int, b: Int): Int = maxOf(
        kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
        kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
        kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)),
    )

    @Test
    fun curlStaysInTheCheckColumn() = curlStaysInTheCheckColumn(null)

    @Test
    fun curlStaysInTheCheckColumnLargeText() = curlStaysInTheCheckColumn(2f)
}

package earth.cora.picnic.ui.list

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.ThemeVariant
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
}

package earth.cora.picnic.ui.list

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The finished tray folds into its strip as one sheet (docs/DESIGN_STICKER.md §8.4 L + 1100, §8.1; item 361): the strip
 * takes its band's place and the rows and cap roll up under it over the 240 ms `layout`, the strips below moving with
 * them in the same frames. No frame shows the tray gone with a blank band where it was, or a strip below sliding over
 * the folded one; the next stop then unfolds with its rows showing as it opens (item 362). Under reduced motion the
 * fold is the one swap (§8.8).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TrayFoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val baking = "aisle-2-baking"

    /** One held frame: the engine's time and where the pieces were laid out (null: not in the list then). */
    private class Shot(
        val t: Long,
        val header: Rect?,
        val strip: Rect?,
        val cap: Rect?,
        val rows: Map<String, Rect>,
        val below: Rect?,
    )

    private fun bounds(tag: String): Rect? = if (compose.exists(tag)) compose.boundsOf(tag) else null

    /** Where [section]'s band or strip, its cap and rows ([rowIds]) and the strip [below] it are in this frame. */
    private fun shot(rig: ListRig, probe: RecordingProbe, section: String, rowIds: List<String>, below: String): Shot = Shot(
        t = rig.engine.time,
        header = bounds(ListTags.header(section)),
        strip = bounds(ListTags.strip(section)),
        cap = probe.bounds["slice:cap:$section"],
        rows = rowIds.mapNotNull { id -> probe.bounds["slice:$id"]?.let { id to it } }.toMap(),
        below = bounds(ListTags.strip(below)),
    )

    /** Ticks [itemId], the last thing left in its aisle, and records [ms] of held frames. */
    private fun finish(rig: ListRig, probe: RecordingProbe, itemId: String, section: String, rowIds: List<String>, below: String, ms: Long): Pair<Long, List<Shot>> {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row(itemId)).performClick()
        val shots = ArrayList<Shot>()
        var tap = -1L
        repeat(((ms + 15) / 16).toInt()) {
            compose.frames(1)
            if (tap < 0) rig.engine.current.value.rowStarts[itemId]?.let { tap = it }
            shots += shot(rig, probe, section, rowIds, below)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the check-off started", tap >= 0)
        return landTime(rig, itemId, tap) to shots
    }

    /**
     * §8.4 L + 1100: Baking, open in place, finishes. From the fold's first frame its strip stands where its band was and
     * its rows and cap are still under it, rolling up: the strip below keeps the 7 dp gap to the tray's foot in every
     * frame (no blank band, never over the folded strip) and climbs in steps of under half the way (a glide, not a jump)
     * until the rows are gone, all within the 240 ms and a frame or two.
     */
    @Test
    fun finishedTrayFoldsAsOneSheet() {
        val probe = RecordingProbe()
        val rig = ListRig(ListFixture.state())
        compose.show(rig, probe)
        val px = compose.density.density
        val gap = 7 * px
        val rowIds = listOf("flour", "sugar", "baking-powder")
        val (land, shots) = finish(rig, probe, "baking-powder", baking, rowIds, below = "breakfast", ms = 2600)
        val fold = land + 1100
        val before = shots.last { it.t < fold }
        assertTrue("Baking is open in place before its fold", before.header != null && before.cap != null && before.strip == null)
        val start = before.cap!!.bottom
        val during = shots.filter { it.t >= fold && it.t < land + 1300 && it.below != null }
        assertTrue("frames between the fold and the Next card (${during.size})", during.size >= 8)
        var last = start
        val steps = ArrayList<Float>()
        for (s in during) {
            val at = "at L + ${s.t - land}"
            assertTrue("$at: the strip stands where the band was, in the same frame", s.strip != null && s.header == null)
            assertEquals("$at: the strip takes the band's top", before.header!!.top, s.strip!!.top, 1.5f)
            // The tray's foot: its cap while any of it shows (a folded slice takes no room), else the strip's.
            val foot = s.cap?.takeIf { it.height > 0.5f }?.bottom ?: s.strip.bottom
            // The strips below move with the tray's foot: never a blank band above them, never over the folded strip.
            assertEquals("$at: the strip below keeps its gap to the tray's foot", gap, s.below!!.top - foot, 1.5f)
            assertTrue("$at: the strip below never reaches the folded strip", s.below.top >= s.strip.bottom + gap - 1.5f)
            // The rows roll up under the strip, the first one right below it.
            s.rows["flour"]?.takeIf { it.height > 1.5f }?.let { assertEquals("$at: the first row hangs from the strip", s.strip.bottom, it.top, 1.5f) }
            assertTrue("$at: the foot only ever rises ($last → $foot)", foot <= last + 1f)
            steps += last - foot
            last = foot
        }
        val travel = start - during.last().strip!!.bottom
        assertTrue("the tray folds by its rows' height ($travel px)", travel > 100 * px)
        assertTrue("a glide: no frame covers half the way (${steps.map { it.toInt() }})", steps.max() < travel * 0.5f)
        assertTrue("a glide: the foot passes through at least three places on the way (${steps.map { it.toInt() }})", steps.count { it > 1f } >= 4)
        val gone = shots.first { it.t > fold && it.cap == null && it.rows.isEmpty() }.t
        assertTrue("the rows are gone within the fold's 240 ms and a frame or two (${gone - fold} ms)", gone - fold <= 240 + 34)
    }

    /**
     * §8.4 L + 1300, §10: Breakfast unfolds behind the Next card as one sheet, its rows showing as it opens: in every
     * frame it is part open, its first row hangs from its band with some of its height, the tray only ever grows, and the
     * strip below keeps its 7 dp gap to the cap.
     */
    @Test
    fun nextStopUnfoldsWithItsRows() {
        val probe = RecordingProbe()
        val rig = ListRig(ListFixture.state())
        compose.show(rig, probe)
        val px = compose.density.density
        val gap = 7 * px
        val (land, shots) = finish(rig, probe, "baking-powder", "breakfast", listOf("coffee", "maple"), below = "dairy-eggs", ms = 2800)
        val opening = shots.filter { it.t >= land + 1300 && it.t < land + 1300 + 300 && it.header != null && it.cap != null }
        assertTrue("frames while Breakfast unfolds (${opening.size})", opening.size >= 8)
        val full = opening.last().let { it.cap!!.bottom - it.header!!.bottom }
        // The rows' share of the open tray (the rest is the cap): they show in that share as it opens, never after the cap.
        val rowsShare = opening.last().rows.values.sumOf { it.height.toDouble() }.toFloat() / full
        var last = 0f
        var part = 0
        for (s in opening) {
            val at = "at L + ${s.t - land}"
            val open = s.cap!!.bottom - s.header!!.bottom
            assertTrue("$at: the tray only ever grows ($last → $open)", open >= last - 1f)
            last = open
            // Part open: past the pixel each slice keeps before it is reached, short of its height.
            if (open > 6 * px && open < full - 2f) {
                part++
                val first = s.rows["coffee"]
                assertTrue("$at: the first row shows as the tray opens (${first?.height})", first != null && first.height > 2f)
                assertEquals("$at: the first row hangs from the band", s.header.bottom, first!!.top, 1.5f)
                val rows = s.rows.values.sumOf { it.height.toDouble() }.toFloat()
                assertTrue("$at: the rows take their share of the opening (${rows / open} of ${rowsShare})", rows / open >= rowsShare - 0.15f)
            }
            s.below?.let { assertEquals("$at: the strip below keeps its gap to the cap", gap, it.top - s.cap.bottom, 1.5f) }
        }
        assertTrue("it unfolds over several frames ($part)", part >= 3)
        // §10: then the list glides it to the top over the card's 400 ms, never leaping (a step is a small part of the way).
        val list = compose.boundsOf(ListTags.LIST)
        val tops = shots.filter { it.t >= land + 1300 && it.header != null }.map { it.t to it.header!!.top }
        val moving = tops.zipWithNext().filter { (a, b) -> abs(b.second - a.second) > 0.5f }
        assertTrue("the list scrolls to Breakfast", moving.isNotEmpty())
        val way = moving.first().first.second - tops.last().second
        assertEquals("Breakfast lands flush at the top", list.top, tops.last().second, 1.5f)
        assertTrue("a glide: no frame covers more than 15 % of the way (${moving.map { (a, b) -> (a.second - b.second).toInt() }})", moving.all { (a, b) -> abs(b.second - a.second) <= way * 0.15f })
        assertTrue("over the card's rise (${moving.last().second.first - moving.first().first.first} ms)", moving.last().second.first - moving.first().first.first >= 300)
    }

    /**
     * The last stop at the list's end (Household, the trip's last item): as it folds, the list's end stays put and the
     * strips above come down to meet it in the same frames, never sliding over one another or leaving a band between
     * them, until the celebration covers the list.
     */
    @Test
    fun lastTrayFoldsAtTheListEnd() {
        val probe = RecordingProbe()
        val rig = ListRig(ListFixture.allBut(listOf("toothpaste"), open = "household"))
        compose.show(rig, probe)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:household")
        compose.waitForIdle()
        val px = compose.density.density
        val gap = 7 * px
        val rowIds = listOf("toilet-paper", "dish-soap", "cat-food", "toothpaste")
        val above = ArrayList<Rect?>()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("toothpaste")).performClick()
        val shots = ArrayList<Shot>()
        var tap = -1L
        repeat(140) {
            compose.frames(1)
            if (tap < 0) rig.engine.current.value.rowStarts["toothpaste"]?.let { tap = it }
            shots += shot(rig, probe, "household", rowIds, below = "frozen")
            above += bounds(ListTags.strip("frozen"))
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val land = landTime(rig, "toothpaste", tap)
        val fold = land + 1100
        val window = shots.indices.filter { shots[it].t >= fold && shots[it].t < land + 1350 }
        assertTrue("frames between the fold and the celebration (${window.size})", window.size >= 8)
        var lastTop = Float.NEGATIVE_INFINITY
        for (i in window) {
            val s = shots[i]
            val frozen = above[i]!!
            val at = "at L + ${s.t - land}"
            assertTrue("$at: Household's strip shows", s.strip != null && s.header == null)
            assertEquals("$at: Frozen keeps its gap to Household's strip", gap, s.strip!!.top - frozen.bottom, 1.5f)
            assertTrue("$at: the strips above only ever come down (${frozen.top} after $lastTop)", frozen.top >= lastTop - 1f)
            lastTop = frozen.top
        }
        assertFalse("folded once the trip is done", compose.exists(ListTags.header("household")))
    }

    /** §8.8: under reduced motion the fold is the one swap: no frame shows Baking's strip with any of its rows. */
    @Test
    fun reducedMotionFoldIsOneSwap() {
        val probe = RecordingProbe()
        val rig = ListRig(ListFixture.state())
        compose.show(rig, probe, reducedMotion = true)
        val rowIds = listOf("flour", "sugar", "baking-powder")
        val (land, shots) = finish(rig, probe, "baking-powder", baking, rowIds, below = "breakfast", ms = 2300)
        val after = shots.filter { it.t >= land + 1100 + 17 && it.strip != null }
        assertTrue("Baking folds", after.isNotEmpty())
        for (s in shots) {
            assertFalse("at L + ${s.t - land}: the strip and the rows never show together", s.strip != null && (s.rows.isNotEmpty() || s.cap != null))
            // Until the next stop's jump scrolls Baking away.
            if (s.t < land + 1300) assertFalse("at L + ${s.t - land}: never neither band nor strip", s.strip == null && s.header == null)
        }
    }
}

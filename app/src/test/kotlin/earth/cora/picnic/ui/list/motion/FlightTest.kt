package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * R5's one flight (docs/DESIGN_STICKER.md §8.3, §8.7; items 123, 124): each check is exactly one overlay flight, from the
 * row's sticker to its own basket's mouth, and nothing ever travels to a keepsake (book, stamps, LCD, album, receipt).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class FlightTest {
    @get:Rule
    val compose = createComposeRule()

    /** Item 123: one flight per check, row sticker → that aisle's basket mouth (recorded overlay paths). */
    @Test
    fun onlyFlightIsRowToBasket() {
        val start = MotionFixture.state().setOpenAisle(MotionFixture.LIST_ID, "produce")
        val rig = HarnessRig(compose, start).start()
        val checks = listOf("apples" to "produce", "strawberries" to "produce")
        for ((item, section) in checks) {
            val rowKey = AnchorRegistry.rowKey(item)
            val basketKey = AnchorRegistry.basketKey(section)
            val rowCentre = rig.engine.anchors.bounds(rowKey)!!.center
            val t0 = rig.tap(item)
            val seen = LinkedHashSet<Flight>()
            val path = ArrayList<HopFrame>()
            rig.sampleTo(t0 + 1300) {
                for (f in rig.p.flights) {
                    seen += f
                    path += rig.engine.flightFrame(f, rig.now)
                }
            }
            val f = seen.single()
            assertEquals(item, f.itemId)
            assertEquals(rowKey, f.fromKey)
            assertEquals(basketKey, f.toKey)
            assertFalse(f.back)
            // The recorded path starts at the row's sticker and ends at the basket's mouth.
            val mouth = rig.engine.anchors.mouth(basketKey)!!
            val first = rig.engine.flightFrame(f, f.t0).center
            val last = rig.engine.flightFrame(f, f.t0 + f.durationMs).center
            assertTrue("starts at the row sticker: $first vs $rowCentre", abs(first.x - rowCentre.x) < 2f && abs(first.y - rowCentre.y) < 2f)
            assertTrue("ends at the basket mouth: $last vs $mouth", abs(last.x - mouth.x) < 2f && abs(last.y - mouth.y) < 2f)
            assertTrue("it was drawn along the way", path.size >= 15)
            // The arc rises above the straight chord between them (the apex, 46 dp at most).
            val above = path.maxOf { h ->
                val u = ((h.center.x - first.x) / (last.x - first.x)).coerceIn(0f, 1f)
                (first.y + (last.y - first.y) * u) - h.center.y
            }
            assertTrue("the arc rises above the chord ($above px)", above > 10f)
        }
        val forward = rig.engine.flightLog.filter { !it.back }
        assertEquals(checks.map { it.first }, forward.map { it.itemId })
        assertEquals(checks.map { AnchorRegistry.basketKey(it.second) }, forward.map { it.toKey })
    }

    /** Item 124: through a whole trip's end (aisle done, celebration, PERFECT) nothing flies to a keepsake. */
    @Test
    fun noFlightToKeepsakes() {
        val rig = HarnessRig(compose, MotionFixture.allBut(listOf("toothpaste", "dish-soap"), open = "household")).start()
        fun assertNoKeepsake(f: Flight) {
            for (key in listOf(f.fromKey, f.toKey)) {
                assertTrue("flight ${f.fromKey} → ${f.toKey}", key.startsWith("row:") || key.startsWith("basket:"))
                assertFalse(AnchorRegistry.KEEPSAKE_PREFIXES.any { key.startsWith(it) })
            }
        }
        val t1 = rig.tap("dish-soap")
        rig.sampleTo(t1 + 700) { rig.p.flights.forEach(::assertNoKeepsake) }
        // Un-check and re-check (the reverse flight too), then finish the trip.
        rig.tap("dish-soap")
        rig.sampleTo(rig.now + 500) { rig.p.flights.forEach(::assertNoKeepsake) }
        rig.tap("dish-soap")
        rig.advance(1400)
        val t2 = rig.tap("toothpaste")
        val start = t2 + 620 + Beats.CELEBRATION
        rig.sampleTo(start + 3000) { rig.p.flights.forEach(::assertNoKeepsake) }
        assertNotNull("the celebration is up", rig.p.celebration)
        compose.runOnIdle { rig.engine.closeCelebration() }
        rig.sampleTo(rig.now + 1200) { rig.p.flights.forEach(::assertNoKeepsake) }
        assertTrue(rig.engine.eventLog.any { it is MotionEvent.BookPerfect })
        val log = rig.engine.flightLog
        assertEquals(4, log.size)
        for (r in log) {
            assertTrue(r.fromKey.startsWith("row:") || r.fromKey.startsWith("basket:"))
            assertTrue(r.toKey.startsWith("row:") || r.toKey.startsWith("basket:"))
        }
    }
}

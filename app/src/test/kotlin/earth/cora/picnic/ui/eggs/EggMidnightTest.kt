package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Toronto wall-clock time on the fixture's day (or [date]) as epoch ms. */
fun torontoMs(time: LocalTime, date: LocalDate = LocalDate.of(2026, 10, 3)): Long =
    date.atTime(time).atZone(ZoneId.of("America/Toronto")).toInstant().toEpochMilli()

/**
 * Item 242 (egg 5): from 23:00 to 04:00 the list is Glow with the moon (even on a light system), the LCD reads
 * "SNACK?" for 2.6 s on entering, then the count, and the snack star and "snack?" doodle sit by the frozen aisle's tab;
 * crossing 23:00 while open does the same; the walking order is unchanged.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggMidnightTest {
    @get:Rule
    val compose = createComposeRule()


    @Test
    fun midnightOnEntry() {
        val day = EngineRig()
        val dayOrder = day.engine.grouped()!!.sections.map { it.id }
        day.close()

        val r = EggRig(compose, startAt = torontoMs(LocalTime.of(23, 30))).start()
        assertEquals("Glow even with the system light", ThemeVariant.Glow, r.resolved)
        assertTrue(r.eggs.midnight)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        assertEquals("frozen", r.eggs.snackSectionId)
        assertEquals("walking order unchanged", dayOrder, r.engine.grouped()!!.sections.map { it.id })
        // The doodle is decorative: TalkBack has no text node for it.
        assertEquals(0, compose.onAllNodesWithText("snack?").fetchSemanticsNodes().size)
        val t0 = r.now
        // The frozen aisle comes into view, and the snack star and doodle sit in its tab row: right of the tab, within
        // the strip's width, level with the tab, on screen above the add bar.
        r.advance(800)
        val snack = compose.onNodeWithTag(EggTags.SNACK, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tab = r.anchor(EggAnchors.tab("frozen"))
        val strip = r.anchor(EggAnchors.strip("frozen"))
        val bar = r.anchor(AnchorRegistry.ADD_BAR)
        assertTrue("drawn ($snack)", snack.width > 20 * 3f && snack.height > 10 * 3f)
        assertTrue("after the tab ($snack, tab $tab)", snack.left >= tab.right - 1f)
        assertTrue("within the strip's width ($snack, strip $strip)", snack.left >= strip.left && snack.right <= strip.right)
        assertTrue("in the tab's row ($snack, tab $tab)", snack.center.y in tab.top..tab.bottom && snack.top >= tab.top - 3 * 3f && snack.bottom <= strip.bottom)
        assertTrue("on screen above the add bar ($snack, bar $bar)", snack.top >= 0f && snack.bottom <= bar.top)
        // "SNACK?" holds 2.6 s, then the count.
        r.advanceTo(t0 + EggRules.SNACK_MS - 100)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        r.advanceTo(t0 + EggRules.SNACK_MS + 100)
        assertNull(r.eggs.lcdWord)
    }

    @Test
    fun crossingElevenWhileOpen() {
        val r = EggRig(compose, startAt = torontoMs(LocalTime.of(22, 59, 30))).start()
        assertEquals(ThemeVariant.Day, r.resolved)
        assertNull(r.eggs.lcdWord)
        r.advance(40_000)
        // The minute tick (ThemeVariantSource's onTick in the app) re-reads the clock.
        r.act {
            r.engine.refresh()
            runBlocking { r.sandbox.variants.tickNow() }
        }
        assertTrue(r.eggs.midnight)
        assertEquals(ThemeVariant.Glow, r.resolved)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
    }

    @Test
    fun fourOClockEndsIt() {
        val e = EngineRig(clock = FakeClock(torontoMs(LocalTime.of(3, 59, 30))))
        try {
            e.engine.onListShown(e.listId)
            assertTrue(e.eggs.midnight)
            e.clock.advance(60_000)
            e.engine.refresh()
            assertFalse(e.eggs.midnight)
            assertNull(e.eggs.snackSectionId)
            // 22:59 is not midnight yet; 23:00 is.
            e.at(LocalDate.of(2026, 10, 3), LocalTime.of(22, 59))
            assertFalse(e.eggs.midnight)
            e.at(LocalDate.of(2026, 10, 3), LocalTime.of(23, 0))
            assertTrue(e.eggs.midnight)
        } finally {
            e.close()
        }
    }

    @Test
    fun reducedMotionAppliesInPlace() {
        val r = EggRig(compose, reduced = true, startAt = torontoMs(LocalTime.of(23, 30))).start()
        assertEquals(ThemeVariant.Glow, r.resolved)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        assertEquals("frozen", r.eggs.snackSectionId)
    }
}

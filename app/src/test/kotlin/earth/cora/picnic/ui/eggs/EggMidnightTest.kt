package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    private val EggRig.variant: ThemeVariant get() = sandbox.variants.variant.value

    @Test
    fun midnightOnEntry() {
        val day = EngineRig()
        val dayOrder = day.engine.grouped()!!.sections.map { it.id }
        day.close()

        val r = EggRig(compose, startAt = torontoMs(LocalTime.of(23, 30))).start()
        assertEquals("Glow even with the system light", ThemeVariant.Glow, r.variant)
        assertTrue(r.eggs.midnight)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        assertEquals("frozen", r.eggs.snackSectionId)
        assertEquals("walking order unchanged", dayOrder, r.engine.grouped()!!.sections.map { it.id })
        // The doodle draws by the frozen tab (decorative: no text node for TalkBack).
        assertEquals(0, compose.onAllNodesWithText("snack?").fetchSemanticsNodes().size)
        // "SNACK?" holds 2.6 s, then the count.
        val t0 = r.now
        r.advanceTo(t0 + EggRules.SNACK_MS - 100)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        r.advanceTo(t0 + EggRules.SNACK_MS + 100)
        assertNull(r.eggs.lcdWord)
    }

    @Test
    fun crossingElevenWhileOpen() {
        val r = EggRig(compose, startAt = torontoMs(LocalTime.of(22, 59, 30))).start()
        assertEquals(ThemeVariant.Day, r.variant)
        assertNull(r.eggs.lcdWord)
        r.advance(40_000)
        // The minute tick (ThemeVariantSource's onTick in the app) re-reads the clock.
        r.act {
            r.engine.refresh()
            runBlocking { r.sandbox.variants.tickNow() }
        }
        assertTrue(r.eggs.midnight)
        assertEquals(ThemeVariant.Glow, r.variant)
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
        assertEquals(ThemeVariant.Glow, r.variant)
        assertEquals(EggRules.SNACK, r.eggs.lcdWord?.text)
        assertEquals("frozen", r.eggs.snackSectionId)
    }
}

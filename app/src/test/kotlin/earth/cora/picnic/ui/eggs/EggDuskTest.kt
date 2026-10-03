package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.calendar.SunClock
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 238 (egg 1): seven taps on the sun, each within 1.5 s of the last, turn on Dusk over 1.2 s (the sun sets in
 * 600 ms, then the Dusk palette and the moon); a tap on the moon undoes it; it persists across restarts. Real taps on the
 * practice list's sun on the test clock.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggDuskTest {
    @get:Rule
    val compose = createComposeRule()


    private fun EggRig.duskSaved(): Boolean = runBlocking { sandbox.settings.current().duskBinder }

    @Test
    fun sevenTapsTurnOnDusk() {
        val r = EggRig(compose).start()
        r.tapSun(6, 1400)
        assertNull("six taps are not enough", r.eggs.duskFx)
        r.advance(1400)
        r.tapSun(1, 0)
        val t7 = r.now
        val fx = r.eggs.duskFx
        assertNotNull(fx)
        assertTrue(fx!!.toDusk)
        assertTrue("saved at once", r.duskSaved())
        // The sun sets behind the title first (600 ms): still Day.
        r.advanceTo(t7 + 400)
        assertEquals(ThemeVariant.Day, r.resolved)
        val probe = groundProbe(r)
        val day = r.grab().getPixel(probe.first, probe.second)
        // Then the Dusk palette washes in over the moon's rise, the moon rises; done by 1.2 s.
        r.advanceTo(t7 + EggRules.DUSK_SINK_MS + 50)
        assertEquals(ThemeVariant.Dusk, r.resolved)
        r.advanceTo(t7 + EggRules.DUSK_SINK_MS + 300)
        val mid = r.grab().getPixel(probe.first, probe.second)
        r.advanceTo(t7 + EggRules.DUSK_TRANSITION_MS + 100)
        val dusk = r.grab().getPixel(probe.first, probe.second)
        assertBetween("the ground mid-way through the wash", day, mid, dusk)
        assertNull("the transition has played out", r.eggs.duskFx)
        assertTrue(r.eggs.dusk)
    }

    /** A point of bare binder ground: in the gap under the Produce strip, halfway across. */
    private fun groundProbe(r: EggRig): Pair<Int, Int> {
        val strip = r.anchor(EggAnchors.strip("produce"))
        return strip.center.x.toInt() to (strip.bottom + 4.5f * 3f).toInt()
    }

    /** [mid] lies between [from] and [to] in every channel and is neither of them (a wash, not a swap). */
    private fun assertBetween(what: String, from: Int, mid: Int, to: Int) {
        val shifts = (0..2).map { ch ->
            val a = (from shr (8 * ch)) and 0xFF
            val m = (mid shr (8 * ch)) and 0xFF
            val b = (to shr (8 * ch)) and 0xFF
            assertTrue("$what: channel $ch $m not between $a and $b", m in minOf(a, b) - 3..maxOf(a, b) + 3)
            Triple(a, m, b)
        }
        val widest = shifts.maxBy { abs(it.first - it.third) }
        assertTrue("$what: the colours differ (${Integer.toHexString(from)} → ${Integer.toHexString(to)})", abs(widest.first - widest.third) > 20)
        assertTrue("$what is neither end: ${Integer.toHexString(mid)}", abs(widest.second - widest.first) > 6 && abs(widest.second - widest.third) > 6)
    }

    @Test
    fun tapsTooFarApartDoNothing() {
        val r = EggRig(compose).start()
        r.tapSun(7, EggRules.SUN_TAP_GAP_MS + 100)
        r.advance(1500)
        assertNull(r.eggs.duskFx)
        assertEquals(ThemeVariant.Day, r.resolved)
        assertFalse(r.duskSaved())
    }

    @Test
    fun moonTapUndoesIt() {
        val r = EggRig(compose).start()
        r.tapSun(7, 300)
        r.advance(1500)
        assertEquals(ThemeVariant.Dusk, r.resolved)
        // The sticker by the title is the moon now: one tap undoes Dusk.
        val probe = groundProbe(r)
        val dusk = r.grab().getPixel(probe.first, probe.second)
        r.tapSun(1, 0)
        val fx = r.eggs.duskFx
        assertNotNull(fx)
        assertFalse(fx!!.toDusk)
        // The Dusk ground fades back out over the undo.
        r.advance(330)
        val mid = r.grab().getPixel(probe.first, probe.second)
        r.advance(1500)
        assertBetween("the ground mid-way back", dusk, mid, r.grab().getPixel(probe.first, probe.second))
        assertEquals(ThemeVariant.Day, r.resolved)
        assertFalse(r.duskSaved())
        assertFalse(r.eggs.dusk)
    }

    @Test
    fun fromGoldenToo() {
        val r = EggRig(compose).start()
        r.fire(12)
        r.advance(300)
        assertEquals(ThemeVariant.Golden, r.resolved)
        r.tapSun(7, 300)
        r.advance(1500)
        assertEquals(ThemeVariant.Dusk, r.resolved)
    }

    @Test
    fun glowMoonIsStatic() {
        val r = EggRig(compose, variant = ThemeVariant.Glow).start()
        r.tapSun(7, 300)
        r.advance(1500)
        assertNull(r.eggs.duskFx)
        assertFalse(r.duskSaved())
        assertEquals(ThemeVariant.Glow, r.resolved)
    }

    @Test
    fun persistsAcrossRestarts() {
        // The first run saves the binder; a fresh engine and variant source over the same settings start in Dusk.
        val first = EngineRig()
        first.engine.onListShown(first.listId)
        repeat(EggRules.SUN_TAPS) {
            first.clock.advance(300)
            first.engine.onSunTap()
        }
        val saved = runBlocking { first.settings.current() }
        first.close()
        assertTrue(saved.duskBinder)
        val again = EngineRig(settings = saved)
        try {
            assertTrue("Dusk without a transition after the restart", again.eggs.dusk)
            val variants = ThemeVariantSource(again.settings, again.clock::now, SunClock, duskShown = null)
            variants.start(again.scope, everyMinute = false)
            assertEquals(ThemeVariant.Dusk, variants.variant.value)
        } finally {
            again.close()
        }
    }

    @Test
    fun reducedMotionSwapsAtOnce() {
        val r = EggRig(compose, reduced = true).start()
        r.tapSun(7, 300)
        val fx = r.eggs.duskFx
        assertNotNull(fx)
        assertTrue("a 120 ms fade, no sunset", fx!!.reduced)
        r.frame()
        assertEquals(ThemeVariant.Dusk, r.resolved)
        // The Dusk palette fades in over 120 ms (§12.3).
        assertEquals(1f, duskWashAlpha(fx, 0), 0f)
        assertEquals(0.5f, duskWashAlpha(fx, 60), 0.01f)
        assertEquals(0f, duskWashAlpha(fx, 120), 0f)
        r.advance(300)
        assertNull(r.eggs.duskFx)
        assertTrue(r.feedback.log.none { it.second == Cue.CELEBRATION })
    }
}

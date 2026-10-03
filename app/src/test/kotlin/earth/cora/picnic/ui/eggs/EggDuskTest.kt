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

    private val EggRig.variant: ThemeVariant get() = sandbox.variants.variant.value

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
        assertEquals(ThemeVariant.Day, r.variant)
        // Then the Dusk palette washes in, the moon rises; done by 1.2 s.
        r.advanceTo(t7 + EggRules.DUSK_SINK_MS + 50)
        assertEquals(ThemeVariant.Dusk, r.variant)
        r.advanceTo(t7 + EggRules.DUSK_TRANSITION_MS + 100)
        assertNull("the transition has played out", r.eggs.duskFx)
        assertTrue(r.eggs.dusk)
    }

    @Test
    fun tapsTooFarApartDoNothing() {
        val r = EggRig(compose).start()
        r.tapSun(7, EggRules.SUN_TAP_GAP_MS + 100)
        r.advance(1500)
        assertNull(r.eggs.duskFx)
        assertEquals(ThemeVariant.Day, r.variant)
        assertFalse(r.duskSaved())
    }

    @Test
    fun moonTapUndoesIt() {
        val r = EggRig(compose).start()
        r.tapSun(7, 300)
        r.advance(1500)
        assertEquals(ThemeVariant.Dusk, r.variant)
        // The sticker by the title is the moon now: one tap undoes Dusk.
        r.tapSun(1, 0)
        val fx = r.eggs.duskFx
        assertNotNull(fx)
        assertFalse(fx!!.toDusk)
        r.advance(1500)
        assertEquals(ThemeVariant.Day, r.variant)
        assertFalse(r.duskSaved())
        assertFalse(r.eggs.dusk)
    }

    @Test
    fun fromGoldenToo() {
        val r = EggRig(compose).start()
        r.fire(12)
        r.advance(300)
        assertEquals(ThemeVariant.Golden, r.variant)
        r.tapSun(7, 300)
        r.advance(1500)
        assertEquals(ThemeVariant.Dusk, r.variant)
    }

    @Test
    fun glowMoonIsStatic() {
        val r = EggRig(compose, variant = ThemeVariant.Glow).start()
        r.tapSun(7, 300)
        r.advance(1500)
        assertNull(r.eggs.duskFx)
        assertFalse(r.duskSaved())
        assertEquals(ThemeVariant.Glow, r.variant)
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
        assertEquals(ThemeVariant.Dusk, r.variant)
        r.advance(300)
        assertNull(r.eggs.duskFx)
        assertTrue(r.feedback.log.none { it.second == Cue.CELEBRATION })
    }
}

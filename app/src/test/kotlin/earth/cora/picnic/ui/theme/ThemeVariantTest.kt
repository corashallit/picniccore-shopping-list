package earth.cora.picnic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * The §2.5 resolution order: midnight → system dark → dusk → golden → day (checklist 16, with WP9's
 * ThemeVariantSource, which evaluates it every minute).
 */
class ThemeVariantTest {
    private val sunset = LocalTime.of(19, 0)

    private fun resolve(eggs: Boolean = true, dark: Boolean = false, dusk: Boolean = false, now: String = "12:00", sunsetAt: LocalTime? = sunset) =
        resolveThemeVariant(eggs, dark, dusk, LocalTime.parse(now), sunsetAt)

    @Test
    fun resolutionOrder() {
        // 1. Midnight beats everything (Glow + extras) when eggs are on.
        assertEquals(ThemeVariant.Glow, resolve(dusk = true, now = "23:00"))
        assertEquals(ThemeVariant.Glow, resolve(dusk = true, now = "03:59"))
        // 2. System dark beats Dusk and Golden.
        assertEquals(ThemeVariant.Glow, resolve(dark = true, dusk = true, now = "18:45"))
        // 3. Dusk beats Golden.
        assertEquals(ThemeVariant.Dusk, resolve(dusk = true, now = "18:45"))
        // 4. Golden within the 30 min before sunset.
        assertEquals(ThemeVariant.Golden, resolve(now = "18:30"))
        assertEquals(ThemeVariant.Golden, resolve(now = "18:59"))
        assertEquals(ThemeVariant.Day, resolve(now = "18:29"))
        assertEquals(ThemeVariant.Day, resolve(now = "19:00"))
        // 5. Day.
        assertEquals(ThemeVariant.Day, resolve(now = "04:00"))
        assertEquals(ThemeVariant.Day, resolve(now = "22:59"))
    }

    @Test
    fun eggsOffShowsNoEggVariant() {
        assertEquals(ThemeVariant.Day, resolve(eggs = false, dusk = true, now = "23:30"))
        assertEquals(ThemeVariant.Day, resolve(eggs = false, now = "18:45"))
        assertEquals(ThemeVariant.Glow, resolve(eggs = false, dark = true))
        assertFalse(isMidnight(false, LocalTime.of(23, 30)))
        assertTrue(isMidnight(true, LocalTime.of(0, 0)))
    }

    @Test
    fun barsFollowTheVariant() {
        assertTrue(ThemeVariant.Day.lightBars)
        assertTrue(ThemeVariant.Dusk.lightBars)
        assertTrue(ThemeVariant.Golden.lightBars)
        assertFalse(ThemeVariant.Glow.lightBars)
    }
}

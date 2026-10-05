package earth.cora.picnic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Holo foil (§2.11; checklist 12) and hard shadows (§2.10; checklist 11). */
class HoloTest {
    @Test
    fun stopsAndGlint() {
        // The foil brush: a closed 6-stop loop from 210°.
        assertEquals(
            listOf(Color(0xFFFFD6E8), Color(0xFFCCEBFF), Color(0xFFC9F2E3), Color(0xFFFFF3A6), Color(0xFFE4D6FF), Color(0xFFFFD6E8)),
            Holo.sweepColors,
        )
        assertEquals(Holo.sweepColors.first(), Holo.sweepColors.last())
        assertEquals(listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f), Holo.sweepStops)
        assertEquals(210f, Holo.SWEEP_FROM_DEG)
        // Sticker holo: (0,0) → (1,1), 62 %.
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.72f, 1f), Holo.stickerStops)
        assertEquals(0.62f, Holo.STICKER_ALPHA)
        // The glint: a 30 dp band of white at 35 %, 60° from the horizontal, 12 steps over 1.2 s.
        val g = Holo.glint
        assertEquals(30f, g.widthDp)
        assertEquals(0.35f, g.alpha)
        assertEquals(60f, g.angleDeg)
        assertEquals(12, g.steps)
        assertEquals(1200, g.durationMillis)
        // Stepped progress: 12 distinct poses, held 100 ms each.
        val poses = (0 until 1200 step 10).map { g.progressAt(it.toLong()) }.toSet()
        assertEquals(12, poses.size)
        assertEquals(g.progressAt(0), g.progressAt(99))
        assertTrue(g.progressAt(100) > g.progressAt(99))
        // The shiny glint: 80 % white, 6 steps.
        assertEquals(0.8f, Holo.shinyGlint.alpha)
        assertEquals(6, Holo.shinyGlint.steps)
    }
}

class ShadowTest {
    @Test
    fun hardOffsetsInTheVariantColour() {
        assertEquals(DpOffset(1.6.dp, 2.2.dp), DayShadows.hs)
        assertEquals(DpOffset(2.4.dp, 3.4.dp), DayShadows.hs2)
        assertEquals(DpOffset(3.2.dp, 4.4.dp), DayShadows.hsLift)
        assertEquals(Color(0xFF6E7BD0), DayShadows.color)
        assertEquals(DayShadows.hs, GlowShadows.hs)
        assertEquals(DayShadows.hs2, GlowShadows.hs2)
        assertEquals(Color(0xFF0B0D24), GlowShadows.color)
        assertEquals(DpOffset(3.dp, 4.dp), GoldenShadows.hs)
        assertEquals(DpOffset(3.dp, 4.dp), GoldenShadows.hs2)
        assertEquals(Color(0xFFE59A7A), GoldenShadows.color)
        // The tokens agree with the colours each variant carries.
        assertEquals(DayColors.stickerShadow, DayShadows.color)
        assertEquals(GlowColors.stickerShadow, GlowShadows.color)
        assertEquals(GoldenColors.stickerShadow, GoldenShadows.color)
    }
}

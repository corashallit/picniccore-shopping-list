package earth.cora.picnic.ui.list

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.theme.BOOK_MORE_CIRCLE
import earth.cora.picnic.ui.theme.COLLAPSED_MORE_TEXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The book's "+n" stop counters and the 13 sp floor (docs/DESIGN_STICKER.md §2.7, §4.13, §7.4; item 292). They are set
 * like the stamp numerals, the floor's exemption: 11 sp, never smaller. The stamp page's "+n" circle holds 11 sp in its
 * 20 dp circle at every font scale (as the stamp numerals do); the collapsed strip's "+n" pill grows with the font, its
 * pill round it, and never cuts its count.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookCountersTest {
    @get:Rule
    val compose = createComposeRule()

    /** The numeral's size as drawn, in sp at font scale 1.0. */
    private fun drawnSp(t: TextLayoutResult): Float = t.layoutInput.style.fontSize.value * t.layoutInput.density.fontScale

    private fun circleAt(fontScale: Float) {
        // 30 stops: the stamp page shows 23 circles and a "+7".
        val rig = ListRig(ListFixture.stops(stops = 30, perAisle = 1, checked = 3))
        val probe = RecordingProbe()
        compose.show(rig, probe, fontScale = fontScale)
        val text = probe.texts[BOOK_MORE_CIRCLE] ?: error("no \"+n\" circle laid out (${probe.texts.keys})")
        assertEquals("+7", text.layoutInput.text.text)
        assertTrue("at least the stamp numerals' 11 sp at $fontScale (${drawnSp(text)})", drawnSp(text) >= 11f - 0.01f)
        assertFalse("never cut at $fontScale", text.hasVisualOverflow)
        val d = compose.density.density
        assertTrue("inside its 20 dp circle at $fontScale (${text.size})", text.size.width <= 20 * d)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun moreCircle() = circleAt(1f)

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun moreCircleAtLargeText() = circleAt(2f)

    private fun pillAt(fontScale: Float): Float {
        // 30 stops in a short window: the strip's dots leave stops out, counted in "+n" pills.
        val rig = ListRig(ListFixture.stops(stops = 30, perAisle = 1, checked = 5))
        val probe = RecordingProbe()
        compose.show(rig, probe, fontScale = fontScale)
        val pills = probe.keys("collapsedMore:")
        assertTrue("a \"+n\" pill shows at $fontScale", pills.isNotEmpty())
        var size = 0f
        for (key in pills) {
            val n = key.substringAfterLast(':')
            val text = probe.texts["$COLLAPSED_MORE_TEXT:$n"] ?: error("no text for $key")
            assertEquals("+$n", text.layoutInput.text.text)
            assertTrue("11 sp at least at $fontScale (${drawnSp(text)})", drawnSp(text) >= 11f * fontScale - 0.01f)
            assertFalse("never cut at $fontScale", text.hasVisualOverflow)
            val pill = probe[key]
            assertTrue("the pill holds its count at $fontScale ($pill, text ${text.size})", pill.height >= text.size.height - 1f && pill.width >= text.size.width)
            size = drawnSp(text)
        }
        return size
    }

    @Test
    @Config(qualifiers = "w360dp-h560dp-xxhdpi")
    fun collapsedPill() {
        assertEquals(11f, pillAt(1f), 0.01f)
    }

    @Test
    @Config(qualifiers = "w360dp-h560dp-xxhdpi")
    fun collapsedPillGrowsWithTheFont() {
        assertEquals("the numeral grows with the font", 22f, pillAt(2f), 0.01f)
    }
}

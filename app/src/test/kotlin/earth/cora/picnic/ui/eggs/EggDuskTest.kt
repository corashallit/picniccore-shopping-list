package earth.cora.picnic.ui.eggs

import androidx.compose.ui.geometry.Rect
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
 * real List screen's sun (as the Surprise lab shows it) on the test clock; the binder top's words stay whole on every
 * frame of the change and of its undo (§2.7 item 334, §4.1, item 259).
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
    fun theBinderTopStaysWholeFrameByFrame() = binderTopStaysWhole(1f)

    @Test
    fun theBinderTopStaysWholeFrameByFrameAt200() = binderTopStaysWhole(2f)

    /**
     * The change as she sees it on the real List screen, `egg_1_strip_day_<t>` ms after the seventh tap: the sun sinks
     * under the horizon behind the title (0 to 600 ms), the Dusk palette washes in as the moon rises and its six sparkles
     * burst on the moon's free side (600 to 1200 ms), settled at 1400 ms. Each frame is also checked whole above.
     */
    @Test
    fun theDuskStrip() {
        val r = EggRig(compose).start()
        r.advance(300)
        r.tapSun(6, 300)
        r.advance(300)
        r.tapSun(1, 0)
        val t7 = r.now
        for (t in listOf(0L, 300L, 600L, 800L, 1_000L, 1_400L)) {
            r.advanceTo(t7 + t)
            r.capture("egg_1_strip_day_$t")
        }
        assertEquals(ThemeVariant.Dusk, r.resolved)
    }

    /**
     * Every frame from the seventh tap to 1.4 s after it, and of the moon tap's undo: the title's ink (its fill and its
     * white stroke) is exactly where and what it was before the taps, its ink box the same; the LCD's face (its left part:
     * the star becoming the moon is the LCD's own change) and the subtitle's patch are untouched; the sun, the moon and
     * the six sparkles never lie over a word, the LCD or More, and the six sparkles are whole on screen.
     */
    private fun binderTopStaysWhole(scale: Float) {
        val r = EggRig(compose, fontScale = scale).start()
        r.advance(300)
        val d = 3f
        val title = r.anchor(EggAnchors.TEXT_PREFIX + "title")
        // The LCD's face: the middle half of its chip's height (its 48 dp touch target reaches onto the binder ground,
        // which the wash rightly changes), from its left edge to 55 % across (the star becoming the moon is the LCD's own).
        val lcd = r.anchor(EggAnchors.TEXT_PREFIX + "lcd").let { Rect(it.left + 5 * d, it.center.y - it.height / 4f, it.left + it.width * 0.55f, it.center.y + it.height / 4f) }
        val subtitle = r.anchor(EggAnchors.TEXT_PREFIX + "subtitle").deflate(4 * d)
        val before = r.grab()
        val ink = inkOf(before, title)
        assertTrue("fs $scale: the title has ink (${ink.size})", ink.size > 500)
        val inkBox = inkBox(before, title)
        val lcdBefore = pixels(before, lcd)
        val subtitleBefore = pixels(before, subtitle)
        val words = keepOutRects(r.anchors) + listOfNotNull(r.anchors.bounds(EggAnchors.MORE))
        fun check(label: String) {
            val now = r.grab()
            val moved = ink.count { (x, y, c) -> !close(now.getPixel(x, y), c) }
            assertEquals("fs $scale, $label: the title's ink is whole", 0, moved)
            assertEquals("fs $scale, $label: the title's ink box", inkBox, inkBox(now, title))
            assertTrue("fs $scale, $label: the LCD's face is untouched", pixels(now, lcd).contentEquals(lcdBefore))
            assertTrue("fs $scale, $label: the subtitle's patch is untouched", pixels(now, subtitle).contentEquals(subtitleBefore))
            for (f in r.probe.frames.filter { it.tag == "duskSun" || it.tag.startsWith("dusk") }) {
                val piece = f.bounds.deflate(0.5f)
                words.forEach { w -> assertFalse("fs $scale, $label: ${f.tag} ${f.bounds} over $w", w.overlaps(piece)) }
                assertTrue("fs $scale, $label: ${f.tag} on screen", f.bounds.left >= 0f && f.bounds.top >= 0f && f.bounds.right <= now.width)
            }
        }
        // Seven taps (the change starts on the seventh), then every frame of the change.
        r.tapSun(6, 300)
        r.advance(300)
        r.probe.clear()
        r.tapSun(1, 0)
        val t7 = r.now
        assertNotNull(r.eggs.duskFx)
        var stars = 0
        while (r.now < t7 + 1_400) {
            r.frame()
            check("${r.now - t7} ms")
            stars = maxOf(stars, r.probe.frames.filter { it.tag.matches(Regex("dusk\\d")) }.map { it.tag }.distinct().size)
            r.probe.clear()
        }
        assertEquals("fs $scale: six sparkles, every one whole", 6, stars)
        assertEquals(ThemeVariant.Dusk, r.resolved)
        // The moon tapped: every frame of the undo.
        r.advance(300)
        r.tapSun(1, 0)
        val u = r.now
        assertNotNull(r.eggs.duskFx)
        while (r.now < u + 900) {
            r.frame()
            check("undo ${r.now - u} ms")
            r.probe.clear()
        }
        assertEquals(ThemeVariant.Day, r.resolved)
    }

    /** The title's ink in [b] within [box]: its navy fill and its white stroke (x, y, colour). */
    private fun inkOf(b: android.graphics.Bitmap, box: Rect): List<Triple<Int, Int, Int>> {
        val out = ArrayList<Triple<Int, Int, Int>>()
        for (y in box.top.toInt().coerceAtLeast(0) until box.bottom.toInt().coerceAtMost(b.height)) {
            for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(b.width)) {
                val c = b.getPixel(x, y)
                if (isFill(c) || isStroke(c)) out += Triple(x, y, c)
            }
        }
        return out
    }

    private fun channels(c: Int) = intArrayOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
    private fun isFill(c: Int) = channels(c).max() < 0x80
    private fun isStroke(c: Int) = channels(c).min() >= 0xF8
    private fun close(a: Int, b: Int) = channels(a).zip(channels(b)).all { (x, y) -> abs(x - y) <= 12 }

    /** The bounding box of the title's navy fill in [b] within [box]. */
    private fun inkBox(b: android.graphics.Bitmap, box: Rect): Rect {
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var rr = -1; var bb = -1
        for (y in box.top.toInt().coerceAtLeast(0) until box.bottom.toInt().coerceAtMost(b.height)) {
            for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(b.width)) {
                if (isFill(b.getPixel(x, y))) {
                    l = minOf(l, x); t = minOf(t, y); rr = maxOf(rr, x); bb = maxOf(bb, y)
                }
            }
        }
        return Rect(l.toFloat(), t.toFloat(), rr.toFloat(), bb.toFloat())
    }

    private fun pixels(b: android.graphics.Bitmap, box: Rect): IntArray {
        val x = box.left.toInt().coerceAtLeast(0)
        val y = box.top.toInt().coerceAtLeast(0)
        val w = box.width.toInt().coerceAtMost(b.width - x)
        val h = box.height.toInt().coerceAtMost(b.height - y)
        return IntArray(w * h).also { b.getPixels(it, 0, w, x, y, w, h) }
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

    /**
     * The Glow moon is static (§12.2 egg 1), whoever passes the tap on: the practice list's sticker, or a list that calls
     * the engine's onSunTap / onMoonTap straight away (WP5's ListViewModel). With the phone in dark mode, seven taps save
     * nothing and start nothing; at midnight with Dusk saved, a tap on the midnight moon keeps Dusk.
     */
    @Test
    fun glowMoonIsStatic() {
        val r = EggRig(compose, variant = ThemeVariant.Glow).start()
        assertEquals(ThemeVariant.Glow, r.resolved)
        // Her taps on the sticker by the title.
        r.tapSun(7, 300)
        r.advance(1500)
        assertNull(r.eggs.duskFx)
        assertFalse(r.duskSaved())
        // The engine's own hooks, as a list that does not know the variant calls them.
        r.act { repeat(EggRules.SUN_TAPS) { r.clock.advance(300); r.engine.onSunTap() } }
        r.advance(1500)
        assertNull("seven taps on the Glow moon start nothing", r.eggs.duskFx)
        assertFalse("and save nothing", r.duskSaved())
        assertEquals(ThemeVariant.Glow, r.resolved)
        // The phone goes light again: still Day, no Dusk waiting.
        r.act { r.sandbox.onSystemDark(false) }
        r.advance(200)
        assertEquals(ThemeVariant.Day, r.resolved)
    }

    @Test
    fun midnightMoonKeepsDusk() {
        val r = EggRig(compose).start()
        r.tapSun(7, 300)
        r.advance(1500)
        assertEquals(ThemeVariant.Dusk, r.resolved)
        // 23:30: midnight forces Glow, with its static moon by the title.
        r.fire(5)
        r.advance(400)
        assertEquals(ThemeVariant.Glow, r.resolved)
        assertTrue(r.eggs.midnight)
        r.act { r.engine.onSunTap() }
        r.act { r.engine.onMoonTap() }
        r.tapSun(1, 0)
        r.advance(1500)
        assertNull("a tap on the midnight moon plays nothing", r.eggs.duskFx)
        assertTrue("Dusk stays saved", r.duskSaved())
        assertTrue(r.eggs.dusk)
    }

    /** In dark mode the system is Glow: a Dusk binder saved earlier stays out of sight, and no wash plays. */
    @Test
    fun noWashInGlow() {
        val r = EggRig(compose, variant = ThemeVariant.Glow).start()
        val probe = groundProbe(r)
        val before = r.grab().getPixel(probe.first, probe.second)
        // A transition already under way when the phone turned dark (the engine's own taps in Day, then dark mode).
        r.act { r.sandbox.onSystemDark(false) }
        r.act { repeat(EggRules.SUN_TAPS) { r.clock.advance(300); r.engine.onSunTap() } }
        assertNotNull(r.eggs.duskFx)
        r.act { r.sandbox.onSystemDark(true) }
        r.advance(300)
        assertEquals(ThemeVariant.Glow, r.resolved)
        assertEquals("the Glow ground, with no Day ground washed over it", before, r.grab().getPixel(probe.first, probe.second))
        r.advance(1500)
        assertNull(r.eggs.duskFx)
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

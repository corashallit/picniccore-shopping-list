package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performSemanticsAction
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.theme.TOAST_TAG
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.weather.WeatherToday
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.ui.add.AddRules
import earth.cora.picnic.ui.theme.WashiPattern
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

/**
 * Item 255 (egg 16) with a fake WeatherSource: with Weather surprises on and rain in today's forecast, the umbrella
 * shelters the add bar's left end, the raindrop washi replaces the tape and "Rain on the way · bring the tote!" shows
 * once a day; offline, switched off or with Easter eggs off nothing shows and (switched off) nothing is requested.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggRainTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 3)

    private fun rig(weatherOn: Boolean = true, eggs: Boolean = true, answer: (LocalDate) -> WeatherToday? = { WeatherToday(it, rain = true, snow = false) }) =
        CountingWeather(answer).let { w -> EngineRig(settings = AppSettings(eggs = eggs, weather = weatherOn), weatherSource = w) to w }

    @Test
    fun rainTodayShowsTheUmbrellaTapeAndOneToastADay() {
        val (e, w) = rig()
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals(1, w.requests)
            assertTrue(e.eggs.rain)
            assertTrue(e.eggs.umbrella)
            assertEquals(WashiPattern.Raindrop, AddRules.addBarTape(e.eggs))
            assertEquals(listOf(EggToast.RainToday), e.toasts)
            // The list again an hour later: no new request (3 h cache), no second toast today.
            e.at(today, LocalTime.of(10, 0))
            e.engine.onListShown(e.listId)
            assertEquals(1, w.requests)
            assertEquals(1, e.toasts.size)
            assertTrue(e.eggs.umbrella)
            // Three hours on, it asks again; still no second toast today.
            e.at(today, LocalTime.of(12, 1))
            e.engine.onListShown(e.listId)
            assertEquals(2, w.requests)
            assertEquals(1, e.toasts.size)
            // Tomorrow, rain again: one more toast.
            e.at(today.plusDays(1), LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals(2, e.toasts.size)
        } finally {
            e.close()
        }
    }

    /**
     * The rain toast waits for the toast host (she left the list during a slow fetch): it is shown when the host takes it,
     * and only then does today count as toasted, so it is never lost and never shown twice.
     */
    @Test
    fun theRainToastWaitsForTheToastHost() {
        val w = CountingWeather { WeatherToday(it, rain = true, snow = false) }
        val e = EngineRig(settings = AppSettings(eggs = true, weather = true), weatherSource = w, collectToasts = false)
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            e.engine.onListHidden()
            e.engine.onListShown(e.listId)
            assertTrue(e.eggs.umbrella)
            assertEquals("not counted before it shows", null, kotlinx.coroutines.runBlocking { e.weatherCache.read() }.rainToastDay)
            // The host comes back and takes what waited: one toast, now counted as today's.
            e.collectToasts()
            assertEquals(listOf(EggToast.RainToday), e.toasts)
            assertEquals(today, kotlinx.coroutines.runBlocking { e.weatherCache.read() }.rainToastDay)
            e.engine.onListShown(e.listId)
            assertEquals(1, e.toasts.size)
        } finally {
            e.close()
        }
    }

    @Test
    fun noRainNoUmbrella() {
        val (e, _) = rig(answer = { WeatherToday(it, rain = false, snow = false) })
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertFalse(e.eggs.umbrella)
            assertEquals(WashiPattern.Thanksgiving, AddRules.addBarTape(e.eggs))
            assertTrue(e.toasts.isEmpty())
        } finally {
            e.close()
        }
    }

    @Test
    fun offlineIsSilent() {
        val (e, w) = rig(answer = { null })
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals(1, w.requests)
            assertFalse(e.eggs.umbrella)
            assertTrue(e.toasts.isEmpty())
        } finally {
            e.close()
        }
        // A source that throws is just as silent.
        val (t, _) = rig(answer = { throw java.io.IOException("no network") })
        try {
            t.at(today, LocalTime.of(9, 0))
            t.engine.onListShown(t.listId)
            assertFalse(t.eggs.umbrella)
            assertTrue(t.toasts.isEmpty())
        } finally {
            t.close()
        }
    }

    @Test
    fun switchedOffNothingIsRequested() {
        val (e, w) = rig(weatherOn = false)
        try {
            e.at(today, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals("nothing is requested with Weather surprises off", 0, w.requests)
            assertFalse(e.eggs.umbrella)
            assertTrue(e.toasts.isEmpty())
        } finally {
            e.close()
        }
        val (off, w2) = rig(eggs = false)
        try {
            off.at(today, LocalTime.of(9, 0))
            off.engine.onListShown(off.listId)
            assertEquals("nor with Easter eggs off", 0, w2.requests)
            assertFalse(off.eggs.umbrella)
        } finally {
            off.close()
        }
    }

    @Test
    fun onTheListTheUmbrellaAppears() {
        val r = EggRig(compose).start()
        val t0 = r.fire(16)
        assertTrue(r.eggs.umbrella)
        assertEquals("Rain on the way · bring\u00A0the\u00A0tote!", r.model.toasts.current?.text)
        r.advanceTo(t0 + 600)
        val umbrella = r.probe.frames.last { it.tag == "umbrella" }
        val bar = r.anchor(earth.cora.picnic.ui.list.motion.AnchorRegistry.ADD_BAR)
        assertTrue("at the add bar's left end", umbrella.center.x < bar.left + bar.width * 0.25f)
        // A big umbrella (the mockup's), standing on the bar's left shoulder: its canopy over the bar's edge.
        assertTrue("a big umbrella: its leaning canopy at least 26 dp across (${umbrella.bounds.width / 3f} dp)", umbrella.bounds.width >= 26 * 3f)
        // On the bar's edge: its canopy rises at most about 16 dp above the bar (the band where the list fades into it),
        // its handle in the bar's top band, above the field's words.
        assertTrue("it stands on the bar's edge (${umbrella.bounds}, bar $bar)", umbrella.bounds.top in bar.top - 16.5f * 3f..bar.top && umbrella.bounds.bottom in bar.top..bar.top + 24 * 3f)
        // Clear of the tape, which has moved along the bar, and of every word.
        val tape = compose.onNodeWithTag(earth.cora.picnic.ui.add.ADD_BAR_WASHI_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertFalse("clear of the tape", tape.overlaps(umbrella.bounds.deflate(2f)))
        textBoxes(compose).filterNot { (_, box) -> inTheBarsFade(box, bar.top, 14 * 3f) }
            .forEach { (w, box) -> assertFalse("over \"$w\"", box.overlaps(umbrella.bounds.deflate(2f))) }
        // The list keeps room at its end for the canopy.
        assertEquals(WashiPattern.Raindrop, AddRules.addBarTape(r.eggs))
    }

    /**
     * Items 255, 259, 73: the umbrella stands on the docked bar wherever the bar is. She scrolls down her list and the bar
     * folds into the FAB: the umbrella goes with it (none floats over the binder, none over a word). She scrolls back up
     * and the bar returns: the umbrella pops back on its shoulder.
     */
    @Test
    fun umbrellaFollowsTheBar() {
        val r = EggRig(compose).start()
        r.advance(600)
        val t0 = r.fire(16)
        r.advanceTo(t0 + 1_500)
        fun umbrella(): androidx.compose.ui.geometry.Rect? =
            compose.onAllNodesWithTag(EggTags.UMBRELLA, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        fun onTheShoulder(what: String) {
            val bar = r.anchor(AnchorRegistry.ADD_BAR)
            umbrella() ?: throw AssertionError("$what: the umbrella shows")
            // As drawn: its outline, turned (its node's box is the whole turned square).
            val u = r.probe.frames.last { it.tag == "umbrella" }.bounds
            assertTrue("$what: at the bar's left end ($u, bar $bar)", u.center.x < bar.left + bar.width * 0.25f)
            assertTrue("$what: it stands on the bar's edge ($u, bar $bar)", u.top in bar.top - 16.5f * 3f..bar.top && u.bottom in bar.top..bar.top + 24 * 3f)
            val under = wordBoxes(compose, r.anchors).filter { (_, box) -> box.overlaps(u.deflate(6f)) && !inTheBarsFade(box, bar.top, 14 * 3f) }.map { it.first }
            assertEquals("$what: no word under it", emptyList<String>(), under)
        }
        onTheShoulder("before")
        val list = compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.LIST)
        list.performTouchInput { swipeUp(startY = centerY + 150f, endY = top + 40f) }
        r.advance(1_500)
        assertNull("folded: the bar is gone", r.anchors.bounds(AnchorRegistry.ADD_BAR))
        compose.onNodeWithTag(earth.cora.picnic.ui.add.ADD_FAB_TAG).assertExists()
        assertTrue("it still rains", r.eggs.umbrella)
        assertNull("folded: the umbrella went with the bar (${umbrella()})", umbrella())
        r.advance(3_000)
        assertNull("and stays away while the bar is folded", umbrella())
        // She scrolls back up: the bar returns, the umbrella with it.
        list.performTouchInput { swipeDown(startY = centerY - 100f, endY = centerY + 200f) }
        r.advance(1_500)
        onTheShoulder("the bar back")
    }

    /**
     * Large text (§15; round 1's "toasts sit above the canopy"): the list and the toasts follow the add bar's MEASURED
     * height (about 160 dp at font scale 2.0), not the 84 dp bar of font scale 1: the last strip scrolls wholly above
     * the bar, the rain toast sits above the bar's top, clear of the umbrella, and the list keeps the canopy's room.
     */
    @Test
    fun largeTextKeepsTheToastAndTheListClearOfTheBar() {
        val r = EggRig(compose, fontScale = 2f).start()
        val list = compose.onNodeWithTag(earth.cora.picnic.ui.list.ListTags.LIST)
        fun lastStripClearOfTheBar(what: String) {
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
            r.advance(100)
            val bar = r.anchor(AnchorRegistry.ADD_BAR)
            val household = r.anchor(EggAnchors.strip("household"))
            assertTrue("$what: the last strip wholly above the bar ($household, bar $bar)", household.bottom <= bar.top)
        }
        val bar = r.anchor(AnchorRegistry.ADD_BAR)
        assertTrue("the bar grew with the text (${bar.height / 3f} dp)", bar.height > 120 * 3f)
        lastStripClearOfTheBar("dry")
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
        r.advance(100)
        val t0 = r.fire(16)
        r.advanceTo(t0 + 900)
        val toast = compose.onNodeWithTag(TOAST_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the toast sits above the bar's top ($toast, bar $bar)", toast.bottom <= bar.top)
        val umbrella = r.probe.frames.last { it.tag == "umbrella" }
        assertFalse("the umbrella clear of the toast (${umbrella.bounds}, toast $toast)", umbrella.bounds.deflate(2f).overlaps(toast))
        lastStripClearOfTheBar("rain")
        val household = r.anchor(EggAnchors.strip("household"))
        assertTrue("the canopy's room kept under the last strip", household.bottom <= umbrella.bounds.top + 2f)
    }
}

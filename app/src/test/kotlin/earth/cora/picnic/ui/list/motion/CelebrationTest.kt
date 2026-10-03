package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.InMemoryAlbumStore
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.theme.CelebrationScene
import earth.cora.picnic.ui.theme.CelebrationTags
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.closingFill
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

/** The celebration (docs/DESIGN_STICKER.md §7.1, §11, §0.4 Q7; items 153, 154, 180–191, 314). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class CelebrationTest {
    @get:Rule
    val compose = createComposeRule()

    private val household = "household"

    private fun lastOne(title: String = "Saturday shop") = MotionFixture.allBut(listOf("toothpaste"), open = household, title = title)

    /** Taps Toothpaste (the trip's last item) and runs to the scene's start: returns the scene's t0. */
    private fun HarnessRig.finish(): Long {
        val t0 = tap("toothpaste")
        val at = t0 + 620 + Beats.CELEBRATION
        advanceTo(at)
        return p.celebration!!.t0.also { assertEquals(at, it) }
    }

    /** A fresh trip one tap from the end (the finale owed again). */
    private fun HarnessRig.resetTrip() {
        compose.runOnIdle { model.set(lastOne()) }
        frame()
    }

    /** Item 153: 450 ms after the scene closes, PER-FECT! is pressed across the book's spine. */
    @Test
    fun bookPerfectAfterClose() {
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.finish()
        assertTrue("held while the scene shows", rig.p.bookFx.perfectHeld)
        rig.advanceTo(s + 3000)
        compose.runOnIdle { rig.engine.closeCelebration() }
        rig.frame()
        val c = rig.p.celebration!!.closingT0!!
        val frames = rig.sampleTo(c + 1200)
        assertBeat("closed (200 ms)", c + 200, frames.firstTime { it.celebration == null })
        assertBeat("PERFECT", c + 450, frames.firstTime { it.bookFx.perfectT0 != null })
        assertTrue(frames.filter { it.first < c + 450 }.all { it.second.bookFx.perfectHeld })
        assertBeat("thunk", c + 450, rig.feedback.times(Cue.PERFECT).lastOrNull())
        assertTrue(rig.engine.eventLog.contains(MotionEvent.BookPerfect))
        assertEquals("the LCD's ready wiggle", c + 450, frames.last().second.lcdWiggleT0)
    }

    /** Item 154: an owed finale plays when the list opens within 30 min of completion; later it is marked, PERFECT in place. */
    @Test
    fun resumesAfterProcessDeath() {
        val done = MotionFixture.allBut(emptyList(), open = household)
        val completedAt = done.list(MotionFixture.LIST_ID)!!.trip!!.completedAt!!
        assertNull(done.list(MotionFixture.LIST_ID)!!.trip!!.finaleShownAt)
        // Within 30 min: the scene plays (the in-place form, from the screen centre).
        val rig = HarnessRig(compose, done).start()
        rig.model.now = completedAt + 10 * 60_000L
        var change: ListChange? = null
        compose.runOnIdle { change = rig.model.resume() }
        rig.frame()
        val t = rig.engine.time
        assertEquals(ChangeCause.RESUME, change!!.cause)
        assertFalse(change!!.finaleStale)
        rig.advanceTo(t + Beats.CELEBRATION)
        assertNotNull(rig.p.celebration)
        assertNull(rig.p.celebration!!.origin)
        rig.model.syncFinale()
        assertNotNull(rig.model.state.list(MotionFixture.LIST_ID)!!.trip!!.finaleShownAt)

        // After 30 min (an engine on its own clock): marked shown, PERFECT pressed in place, no scene.
        val engine = MotionChoreographer()
        val model = HarnessModel(done, engine, now = completedAt + 40 * 60_000L)
        engine.tick(0)
        val c = model.resume()!!
        assertTrue(c.finaleStale)
        for (time in 16L..3000L step 16) engine.tick(time)
        assertTrue(engine.eventLog.contains(MotionEvent.FinaleMarked(MotionFixture.LIST_ID)))
        assertTrue(engine.eventLog.contains(MotionEvent.BookPerfect))
        assertTrue(engine.eventLog.none { it is MotionEvent.CelebrationStarted })
        assertNull(engine.presentation.value.celebration)
        model.syncFinale()
        assertNotNull(model.state.list(MotionFixture.LIST_ID)!!.trip!!.finaleShownAt)
    }

    /** Item 180: once per trip at L + 1350, from the last tapped checkbox; a re-completion only re-presses PERFECT. */
    @Test
    fun triggersOncePerTrip() {
        val rig = HarnessRig(compose, lastOne()).start()
        val check = rig.engine.anchors.bounds(AnchorRegistry.checkKey("toothpaste"))!!.center
        val s = rig.finish()
        assertEquals(check, rig.p.celebration!!.origin)
        assertEquals(1, rig.engine.eventLog.count { it is MotionEvent.CelebrationStarted })
        rig.advanceTo(s + 1000)
        compose.runOnIdle { rig.engine.closeCelebration() }
        rig.advance(1000)
        // Un-check and re-check: the trip completes again; no second scene, PERFECT again at L + 1800.
        rig.tap("toothpaste")
        rig.advance(800)
        val t1 = rig.tap("toothpaste")
        val L1 = t1 + 620
        val frames = rig.sampleTo(L1 + 2600)
        assertTrue(frames.all { it.second.celebration == null })
        assertEquals(1, rig.engine.eventLog.count { it is MotionEvent.CelebrationStarted })
        assertBeat("PERFECT again", L1 + 1800, rig.feedback.times(Cue.PERFECT).lastOrNull())
    }

    /** Item 181: the storyboard's beats at the times CelebrationTimeline computes. */
    @Test
    fun storyboard() {
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.finish()
        val plan = rig.p.celebration!!.plan!!
        assertEquals(CelebrationTimeline.plan("Saturday shop", rig.p.celebration!!.content!!.stats, false).beats, plan.beats)
        assertEquals(4300L, plan.beat(BeatName.Perfect).at)
        assertEquals(4730L, plan.beat(BeatName.Perfect).settle)
        assertEquals(6230L, plan.beat(BeatName.Banner).at)
        assertEquals(8930L, plan.beat(BeatName.Bonus).at)
        assertEquals(11230L, plan.beat(BeatName.Curl).at)
        assertEquals(12150L, plan.summaryAt)
        assertEquals(16950L, plan.closeAt)
        val frames = rig.sampleTo(s + 17300)
        assertBeat("jingle", s, rig.feedback.times(Cue.CELEBRATION).firstOrNull()?.let { maxOf(it, s) })
        assertBeat("PER-FECT! thunk", s + 4300, rig.feedback.times(Cue.PERFECT).firstOrNull())
        assertBeat("pon!", s + 9030, rig.feedback.times(Cue.CAPSULE_POP).firstOrNull())
        assertBeat("peel", s + 11230, rig.feedback.times(Cue.UNCHECK).lastOrNull())
        assertBeat("kira", s + 11830, rig.feedback.times(Cue.MAGIC_APPEAR).lastOrNull())
        assertBeat("closes by itself", s + 16950, frames.firstTime { it.celebration?.closingT0 != null })
        assertBeat("gone", s + 16950 + 200, frames.firstTime(from = s + 16950) { it.celebration == null })
    }

    /** Item 182: each text beat holds still its rule from its settle (StillnessProbe). */
    @Test
    fun readHolds() {
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.finish()
        rig.advanceTo(s + 17000)
        fun check(tag: String, settle: Long, hold: Long) {
            var why = ""
            val still = rig.probe.stillFrom(tag, s + settle) { why = it }
            assertTrue("$tag from $settle: still $still ms < $hold ($why)", still >= hold)
        }
        check(CelebrationTags.TITLE, 700, 3600)
        check(CelebrationTags.CHIP, 700, 3600)
        check(CelebrationTags.LCD, 700, 3600)
        check(CelebrationTags.PERFECT, 4730, 1500)
        check(CelebrationTags.BANNER, 6530, 2400)
        check(CelebrationTags.BONUS, 9430, 1800)
        for (tag in listOf(CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD, CelebrationTags.PERFECT, CelebrationTags.BANNER, CelebrationTags.BONUS)) {
            check(tag, 12150, 4800)
        }
    }

    /** Item 183: confetti draws behind every text plate. */
    @Test
    fun confettiBehindText() {
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.finish()
        rig.advanceTo(s + 6800)
        val confetti = compose.onNodeWithTag(CelebrationTags.CONFETTI, useUnmergedTree = true).fetchSemanticsNode()
        val content = compose.onNodeWithTag(CelebrationTags.CONTENT, useUnmergedTree = true).fetchSemanticsNode()
        // Siblings in one box: drawn in order, so the earlier one is behind.
        val (a, b) = siblingsUnder(confetti, content)
        assertEquals(a.parent!!.id, b.parent!!.id)
        val kids = a.parent!!.children.map { it.id }
        assertTrue("confetti before the plates", kids.indexOf(a.id) < kids.indexOf(b.id))
        val overlays = rig.probe.overlaysAt(rig.now)
        assertTrue(overlays.any { it.tag == "cele_confetti" })
        assertTrue(overlays.filter { it.tag == "cele_confetti" || it.tag == "cele_rays" }.all { it.behind })
    }

    /** Item 184: one tap or Back closes it; it closes by itself at 16.95 s, except with TalkBack on (then it waits). */
    @Test
    fun closeRules() {
        var activity: androidx.activity.ComponentActivity? = null
        val rig = HarnessRig(compose, lastOne())
        rig.start {
            activity = androidx.activity.compose.LocalActivity.current as androidx.activity.ComponentActivity
            MotionHarness(rig.model, rig.toasts, talkBack = rig.talkBackState.value)
        }
        // One tap anywhere, mid-scene.
        var s = rig.finish()
        rig.advanceTo(s + 2500)
        compose.onNodeWithTag(CELEBRATION_TAG).performClick()
        rig.frame()
        assertNotNull("#1 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration?.closingT0)
        rig.advance(250)
        assertNull("#6 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration)
        // Back.
        rig.resetTrip()
        s = rig.finish()
        rig.advanceTo(s + 5000)
        compose.runOnIdle { activity!!.onBackPressedDispatcher.onBackPressed() }
        rig.frame()
        assertNotNull("#2 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration?.closingT0)
        rig.advance(250)
        assertNull("#7 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration)
        // By itself at the end of the summary hold.
        rig.resetTrip()
        s = rig.finish()
        val frames = rig.sampleTo(s + 17300)
        assertBeat("auto-close", s + 16950, frames.firstTime { it.celebration?.closingT0 != null })
        // TalkBack: no countdown; it waits for a tap; the announcement plays.
        compose.runOnIdle { rig.talkBackState.value = true }
        rig.resetTrip()
        s = rig.finish()
        assertTrue(rig.p.celebration!!.talkBack)
        rig.advanceTo(s + 22000)
        assertNotNull("still waiting", rig.p.celebration)
        assertNull("#8 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration!!.closingT0)
        assertTrue(rig.announcer.messages.any { it.startsWith("List complete! 24 stickers in ") && it.endsWith("Perfect!") })
        val node = compose.onNodeWithTag(CELEBRATION_TAG).fetchSemanticsNode()
        assertEquals(listOf("Celebration"), node.config[SemanticsProperties.ContentDescription])
        assertEquals("close", node.config[SemanticsActions.OnClick].label)
        compose.onNodeWithTag(CELEBRATION_TAG).performClick()
        rig.advance(250)
        assertNull("#9 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration)
    }

    /** Item 185: the chip and the banner show the list's real name and the trip's real stats. */
    @Test
    fun realStats() {
        val rig = HarnessRig(compose, lastOne(title = "Sunday market")).start()
        val s = rig.finish()
        val content = rig.p.celebration!!.content!!
        val plan = rig.p.celebration!!.plan!!
        rig.model.syncFinale()
        val list = rig.model.state.list(MotionFixture.LIST_ID)!!
        val stats = TripStats.of(list, rig.model.grouped().stops)
        assertEquals("Sunday market", content.listName)
        assertEquals(stats, content.stats)
        assertEquals("♡ Sunday market", plan.listChip)
        assertEquals("24/24★", plan.lcd)
        assertEquals("24 stickers · ${TripStats.formatMinutes(stats.minutes)}", plan.banner)
        rig.advanceTo(s + 7000)
        assertTrue(texts(CelebrationTags.CHIP).any { it.contains("Sunday market") })
        assertTrue(texts(CelebrationTags.BANNER).contains(plan.banner))
        assertTrue(texts(CelebrationTags.TITLE).contains("List complete!"))
    }

    /** Item 186: the bonus follows its rule, is saved to the album and the trip when the scene starts, and shows. */
    @Test
    fun bonusSavedAtStart() {
        val rig = HarnessRig(compose, lastOne()).start()
        val stateStore = InMemoryStateStore(rig.model.state)
        val albumStore = InMemoryAlbumStore(Album())
        val keeper = FinaleKeeper(stateStore, albumStore) { MotionFixture.NOW }
        val s = rig.finish()
        val started = rig.engine.eventLog.filterIsInstance<MotionEvent.CelebrationStarted>().single()
        runBlocking {
            stateStore.update { rig.model.state }
            keeper.handle(started)
        }
        // The rule: the most-checked across trips, ties by check order: an empty album → the first check, Bananas.
        val bonus = started.bonus!!
        assertEquals("Bananas", bonus.name)
        assertEquals("banana", bonus.albumKey)
        assertFalse(bonus.picnic)
        val album = runBlocking { albumStore.current() }
        assertEquals(true, album.collected["banana"]?.holo)
        val trip = runBlocking { stateStore.current() }.list(MotionFixture.LIST_ID)!!.trip!!
        assertNotNull(trip.finaleShownAt)
        assertNotNull(trip.bonus)
        // With bananas already holo the next pick follows the rule; with every trip sticker holo, the picnic sheet.
        val holo = album
        val next = BonusRule.pick(rig.model.state.list(MotionFixture.LIST_ID)!!.items.filter { it.checked }, holo, MotionFixture.catalog)!!
        assertTrue(next.albumKey != "banana")
        // It magic-appears in its pocket at 11.83 s and the card keeps its ghost (golden celebration_summary).
        assertEquals(11830L, rig.p.celebration!!.plan!!.beat(BeatName.PocketMagic).at)
        assertEquals(s, rig.p.celebration!!.t0)
    }

    /** Item 188: reduced motion: the summary fades in at L + 1350 and holds max(6000, readHold(all its words)). */
    @Test
    fun reducedMotion() {
        val rig = HarnessRig(compose, lastOne(), reduced = true).start()
        val s = rig.finish()
        val plan = rig.p.celebration!!.plan!!
        assertTrue(plan.reduced)
        assertEquals(6000L, plan.summaryHold)
        val frames = rig.sampleTo(s + 6500)
        assertBeat("fades out after its hold", s + 120 + 6000, frames.firstTime { it.celebration?.closingT0 != null })
        for (tag in listOf(CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD, CelebrationTags.PERFECT, CelebrationTags.BANNER, CelebrationTags.BONUS)) {
            assertTrue("$tag still for the hold", rig.probe.stillFrom(tag, s + 120) >= 6000)
        }
        assertTrue("no ride, confetti or curl", rig.probe.overlaysAt(s + 3000).none { it.tag == "cele_confetti" || it.tag == "cele_curl" || it.tag == "cele_shake" })
    }

    /** Item 189: at most 24 riders (the newest, at least one per basket), done by 2.45 s; shelf and card for 1–24 stops. */
    @Test
    fun ridersCapped() {
        val big = MotionFixture.many(100, 8)
        val content = CelebrationContent.from(big, MotionFixture.LIST_ID, Album(), MotionFixture.catalog)!!
        assertEquals(24, content.riders.size)
        assertEquals((0 until 8).toSet(), content.riders.map { it.stopIndex }.toSet())
        val lastLands = CelebrationTimeline.RIDE_AT + (content.riders.size - 1) * CelebrationTimeline.RIDE_GAP + CelebrationTimeline.RIDE_EACH
        assertTrue("$lastLands", lastLands <= 2450)
        // The shelf and the stamp card lay out 1 to 24 stops inside the scene.
        var stops by mutableIntStateOf(1)
        val clock = MotionClock()
        compose.setContent {
            PicnicTheme {
                Box(Modifier.size(360.dp, 760.dp)) {
                    val c = remember(stops) { CelebrationContent.from(MotionFixture.many(stops * 2, stops), MotionFixture.LIST_ID, Album(), MotionFixture.catalog)!! }
                    val plan = remember(c) { CelebrationTimeline.plan(c.listName, c.stats, false, total = c.total) }
                    CelebrationScene(CelebrationPresentation(MotionFixture.LIST_ID, 0, c, plan, null, false, false), clock, CelebrationCopy.English)
                }
            }
        }
        for (n in listOf(1, 8, 9, 16, 20, 24)) {
            compose.runOnIdle {
                stops = n
                clock.now = 15000
            }
            compose.waitForIdle()
            for (tag in listOf(CelebrationTags.SHELF, CelebrationTags.STAMP_CARD)) {
                val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("$tag at $n stops: $b", b.left >= 0f && b.right <= 360f * 3 + 0.5f && b.width > 0f)
            }
        }
    }

    /** Item 190: switched off: no scene, but PERFECT at L + 1800 and the announcement. */
    @Test
    fun switchedOff() {
        val rig = HarnessRig(compose, lastOne(), celebration = false).start()
        val t0 = rig.tap("toothpaste")
        val L = t0 + 620
        val frames = rig.sampleTo(L + 2400)
        assertTrue(frames.all { it.second.celebration == null })
        assertTrue(rig.engine.eventLog.contains(MotionEvent.TripComplete(MotionFixture.LIST_ID, scene = false)))
        assertTrue(rig.engine.eventLog.contains(MotionEvent.FinaleMarked(MotionFixture.LIST_ID)))
        assertBeat("PERFECT", L + 1800, rig.feedback.times(Cue.PERFECT).firstOrNull())
        assertTrue(rig.announcer.messages.any { it.startsWith("List complete!") })
    }

    /** Item 191: "tap to skip" is 13 sp and shows from the title's arrival until the curl (11.23 s). */
    @Test
    fun skipChip() {
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.finish()
        rig.advanceTo(s + 1000)
        val text = compose.onAllNodesWithTag(CelebrationTags.SKIP, useUnmergedTree = true).fetchSemanticsNodes().single().let { findText(it, "tap to skip") }!!
        val layouts = ArrayList<TextLayoutResult>()
        text.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(13.sp, layouts.single().layoutInput.style.fontSize)
        rig.advanceTo(s + 11600)
        val frames = rig.probe.frames(CelebrationTags.SKIP)
        assertTrue(frames.filter { it.time in (s + 300)..(s + 11230) }.all { it.alpha >= 0.99f })
        assertTrue(frames.filter { it.time >= s + 11230 + 120 }.all { it.alpha <= 0.01f })
        assertTrue(frames.any { it.time >= s + 11230 + 120 })
    }

    /** Item 314: the summary's progress bar; a tap on the summary keeps it (Done closes it); elsewhere closes; TalkBack has none. */
    @Test
    fun progressBarAndKeep() {
        assertEquals(0f, closingFill(0, 4800, false), 0f)
        assertEquals(0.5f, closingFill(2400, 4800, false), 0.001f)
        assertEquals(1f, closingFill(4800, 4800, false), 0f)
        assertEquals(0.375f, closingFill(2400 - 1, 4800, true), 0.001f)
        val rig = HarnessRig(compose, lastOne()).start()
        var s = rig.finish()
        rig.advanceTo(s + 12150 - 200)
        assertTrue("no bar before the summary", compose.onAllNodesWithTag(CelebrationTags.CLOSING, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() || rig.p.celebration!!.keptAt == null)
        rig.advanceTo(s + 13000)
        compose.onNodeWithTag(CelebrationTags.CLOSING, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("P1", texts(CelebrationTags.CLOSING).contains("Closing… tap to keep it"))
        // A tap on the summary keeps it: the bar goes, Done shows, and it no longer closes by itself.
        compose.onNodeWithTag(CelebrationTags.CONTENT, useUnmergedTree = true).performTouchInput { click(Offset(width / 2f, height / 3f)) }
        rig.frame()
        assertNotNull("#3 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration!!.keptAt)
        rig.advanceTo(s + 18000)
        assertNotNull("#4 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration)
        assertTrue("P2", compose.onAllNodesWithTag(CelebrationTags.CLOSING, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag(CelebrationTags.DONE, useUnmergedTree = true).performClick()
        rig.advance(250)
        assertNull("#10 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration)
        // A tap elsewhere during the summary closes it at once.
        rig.resetTrip()
        s = rig.finish()
        rig.advanceTo(s + 13000)
        compose.onNodeWithTag(CELEBRATION_TAG).performTouchInput { click(Offset(width - 20f, height - 20f)) }
        rig.frame()
        assertNotNull("#5 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration!!.closingT0)
        assertNull("#11 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration!!.keptAt)
        rig.advance(250)
        // TalkBack: no countdown bar.
        compose.runOnIdle { rig.talkBackState.value = true }
        rig.resetTrip()
        s = rig.finish()
        rig.advanceTo(s + 13000)
        assertTrue("P3", compose.onAllNodesWithTag(CelebrationTags.CLOSING, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    // ---- helpers ----

    private fun texts(tag: String): List<String> {
        fun walk(n: SemanticsNode): List<String> = (n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + n.children.flatMap { walk(it) }
        return compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().flatMap { walk(it) }
    }

    private fun findText(n: SemanticsNode, text: String): SemanticsNode? {
        if (n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true) return n
        return n.children.firstNotNullOfOrNull { findText(it, text) }
    }

    /** The ancestors of [a] and [b] that are siblings (children of their nearest common ancestor). */
    private fun siblingsUnder(a: SemanticsNode, b: SemanticsNode): Pair<SemanticsNode, SemanticsNode> {
        fun chain(n: SemanticsNode): List<SemanticsNode> = generateSequence(n) { it.parent }.toList()
        val ca = chain(a)
        val cb = chain(b)
        val common = ca.first { x -> cb.any { it.id == x.id } }
        val sa = ca[ca.indexOfFirst { it.id == common.id } - 1]
        val sb = cb[cb.indexOfFirst { it.id == common.id } - 1]
        return sa to sb
    }
}

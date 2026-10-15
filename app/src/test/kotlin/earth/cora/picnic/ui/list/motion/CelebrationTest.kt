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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.updateList
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.InMemoryAlbumStore
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.theme.CelebrationScene
import earth.cora.picnic.ui.theme.CelebrationTags
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.closingFill
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
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

    /** §7.1, §8.3: an un-check in the 450 ms after the scene closes re-opens the trip: PERFECT is not pressed. */
    @Test
    fun uncheckAfterCloseCancelsPerfect() {
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.finish()
        rig.advanceTo(s + 3000)
        compose.runOnIdle { rig.engine.closeCelebration() }
        rig.frame()
        val c = rig.p.celebration!!.closingT0!!
        rig.advanceTo(c + 272)
        val cues = rig.feedback.count(Cue.PERFECT)
        rig.tap("toothpaste")
        val frames = rig.sampleTo(c + 1400)
        assertEquals("no PERFECT after the close", cues, rig.feedback.count(Cue.PERFECT))
        assertTrue("no BookPerfect event", rig.engine.eventLog.none { it == MotionEvent.BookPerfect })
        assertTrue("never pressed on the spine", frames.all { it.second.bookFx.perfectT0 == null && it.second.lcdWiggleT0 == null })
    }

    /** A re-open that arrives while the scene is up (an item added from another device): its close presses no PERFECT. */
    @Test
    fun reopenDuringSceneSkipsPerfect() {
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.finish()
        rig.advanceTo(s + 4000)
        var change: ListChange? = null
        compose.runOnIdle { change = rig.model.apply(ChangeCause.SYNC) { st -> st.addItems(MotionFixture.LIST_ID, listOf(MotionFixture.item("sugar").copy(id = "vanilla", name = "Vanilla", aisleId = "aisle-2-baking", icon = "vanilla")), rig.model.now).first } }
        rig.frame()
        assertTrue("the trip re-opened", change!!.tripReopened)
        assertNotNull("the scene stays until she closes it", rig.p.celebration)
        compose.runOnIdle { rig.engine.closeCelebration() }
        rig.frame()
        val c = rig.p.celebration!!.closingT0!!
        val frames = rig.sampleTo(c + 1200)
        assertEquals("closed", null, frames.last().second.celebration)
        // The scene's own PERFECT beat (4.30 s) sounded; the book's is never pressed.
        assertTrue(rig.engine.eventLog.none { it == MotionEvent.BookPerfect })
        assertTrue(frames.all { it.second.bookFx.perfectT0 == null })
        // Completing the trip again presses it as a re-completion (L + 1800), not on the old close.
        compose.runOnIdle { rig.model.delete("vanilla") }
        rig.frame()
        val d0 = rig.now
        rig.advanceTo(d0 + 1350 + 450 + 32)
        assertEquals(1, rig.engine.eventLog.count { it == MotionEvent.BookPerfect })
    }

    /** §11.3: with nothing to show (the list is gone at L + 1350), no scene: no empty node takes her tap or Back. */
    @Test
    fun noContentNoScene() {
        val rig = HarnessRig(compose, lastOne()).start()
        rig.engine.celebrationSource = { null }
        val t0 = rig.tap("toothpaste")
        val L = t0 + 620
        val frames = rig.sampleTo(L + 2400)
        assertTrue("no scene", frames.all { it.second.celebration == null })
        assertTrue(compose.onAllNodesWithTag(CELEBRATION_TAG).fetchSemanticsNodes().isEmpty())
        assertTrue(rig.engine.eventLog.contains(MotionEvent.TripComplete(MotionFixture.LIST_ID, scene = false)))
        assertTrue(rig.engine.eventLog.contains(MotionEvent.FinaleMarked(MotionFixture.LIST_ID)))
        assertTrue(rig.engine.eventLog.none { it is MotionEvent.CelebrationStarted })
        assertBeat("PERFECT at L + 1800", L + 1800, rig.feedback.times(Cue.PERFECT).firstOrNull())
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
        assertEquals(18450L, plan.closeAt)
        val frames = rig.sampleTo(s + 18800)
        assertBeat("jingle", s, rig.feedback.times(Cue.CELEBRATION).firstOrNull()?.let { maxOf(it, s) })
        assertBeat("PER-FECT! thunk", s + 4300, rig.feedback.times(Cue.PERFECT).firstOrNull())
        assertBeat("pon!", s + 9030, rig.feedback.times(Cue.CAPSULE_POP).firstOrNull())
        assertBeat("peel", s + 11230, rig.feedback.times(Cue.UNCHECK).lastOrNull())
        assertBeat("kira", s + 11830, rig.feedback.times(Cue.MAGIC_APPEAR).lastOrNull())
        assertBeat("closes by itself", s + 18450, frames.firstTime { it.celebration?.closingT0 != null })
        assertBeat("gone", s + 18450 + 200, frames.firstTime(from = s + 18450) { it.celebration == null })
    }

    /**
     * Item 366 (§0.4, 14 October): the celebration opens from the last tick with no white flash. Every frame from the
     * tap that completes the trip through the scene's first 0.5 s is read off the screen: none is mostly white; the
     * scene shows from its first frame, in a heart that already reads as its arrival; once it has started, the plain list
     * never shows again (the share of the screen the scene has taken from the list only grows); and it has the whole
     * screen by 0.25 s, the end of the iris. Over a Day list (mint, like the scene's own ground) and a Glow one.
     */
    @Test
    fun entranceHandsStraightOverDay() = entrance(ThemeVariant.Day)

    @Test
    fun entranceHandsStraightOverGlow() = entrance(ThemeVariant.Glow)

    private fun entrance(variant: ThemeVariant) {
        val rig = HarnessRig(compose, lastOne(), variant = variant).start()
        // Toothpaste's row on screen, so the iris opens from the checkbox she taps.
        compose.onNodeWithTag(HarnessTags.LIST).performScrollToIndex(rig.model.grouped().sections.indexOfFirst { it.id == household })
        rig.advance(300)
        val t0 = rig.tap("toothpaste")
        val due = t0 + 620 + Beats.CELEBRATION
        var list: IntArray? = null
        var start: Long? = null
        val white = ArrayList<Pair<Long, Float>>()
        val taken = ArrayList<Pair<Long, Float>>()
        rig.sampleTo(due + 500) { now ->
            val px = screenPixels()
            white += (now - t0) to share(px.size) { isWhite(px[it]) }
            val cele = rig.p.celebration
            if (cele == null) {
                list = px
            } else {
                // Times from the scene's own start (its t0, on the frame clock).
                val s = start ?: cele.t0.also { start = it }
                val before = list!!
                taken += (now - s) to share(px.size) { differs(px[it], before[it]) }
            }
        }
        val fmt = { l: List<Pair<Long, Float>> -> l.joinToString { (t, f) -> "$t: ${"%.3f".format(f)}" } }
        println("entrance [$variant] white from the tap: ${fmt(white)}")
        println("entrance [$variant] scene's share from its start: ${fmt(taken)}")
        val s = start
        assertNotNull("the scene starts", s)
        assertBeat("the scene", due, s)
        val mostlyWhite = white.filter { it.second > MOSTLY_WHITE }
        assertTrue("[$variant] mostly white frames (ms from the tap: share): ${fmt(mostlyWhite)}", mostlyWhite.isEmpty())
        // The scene from its first frame: the heart's first pose reads at once.
        assertTrue("[$variant] the scene's first frame shows it: ${fmt(taken.take(1))}", taken.first().second >= FIRST_POSE)
        // Never the plain list again: the scene's share only grows (a frame's own small changes aside).
        var most = 0f
        for ((t, f) in taken) {
            assertTrue("[$variant] the list shows again at +$t ms (${"%.3f".format(f)} after ${"%.3f".format(most)}): ${fmt(taken)}", f >= most - SLACK)
            most = maxOf(most, f)
        }
        // And it has the whole screen from the end of the iris.
        val covered = taken.first { it.first >= CelebrationTimeline.IRIS }.second
        assertTrue("[$variant] the scene has the screen by 0.25 s: ${fmt(taken)}", covered >= COVERED && covered >= most - SLACK)
        // Its text shows only from then, never cut by the heart's edge.
        val open = s!! + CelebrationTimeline.IRIS
        for (tag in listOf(CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD, CelebrationTags.SKIP)) {
            val shown = rig.probe.frames(tag).filter { it.time >= s && it.alpha > 0f }
            assertTrue("[$variant] $tag shows inside the heart: ${shown.map { it.time - s }}", shown.none { it.time < open })
            assertTrue("[$variant] $tag shows once the scene has the screen", shown.any { it.time < open + 100 })
        }
    }

    /** The screen as drawn now, every 4th pixel each way (ARGB). */
    private fun screenPixels(): IntArray {
        compose.waitForIdle()
        val full = compose.onRoot().captureToImage().asAndroidBitmap()
        val small = android.graphics.Bitmap.createScaledBitmap(full, full.width / 4, full.height / 4, false)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        return px
    }

    /** The share of [n] pixels for which [pred] holds. */
    private fun share(n: Int, pred: (Int) -> Boolean): Float = (0 until n).count(pred).toFloat() / n

    /** Paper white or near it: every channel at least 240 (the old flash's 92 % white over any list is). */
    private fun isWhite(argb: Int): Boolean =
        (argb shr 16 and 0xFF) >= 240 && (argb shr 8 and 0xFF) >= 240 && (argb and 0xFF) >= 240

    /** A pixel the scene has changed from the list: some channel moved by more than 40. */
    private fun differs(a: Int, b: Int): Boolean =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > 40 || abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > 40 || abs((a and 0xFF) - (b and 0xFF)) > 40

    /** Item 182: each text beat holds still its rule from its settle (StillnessProbe). */
    @Test
    fun readHolds() {
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.finish()
        rig.advanceTo(s + 18500)
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
        // The summary: 17 words with the progress bar's label, 6.3 s.
        for (tag in listOf(CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD, CelebrationTags.PERFECT, CelebrationTags.BANNER, CelebrationTags.BONUS, CelebrationTags.CLOSING_LABEL)) {
            check(tag, 12150, 6300)
        }
    }

    /**
     * The performance gate (§20.20): the scene's motion plays in draw and layer lambdas, so the shelf (8 baskets with
     * their minis) recomposes only as riders leave it (24 of them), not on every frame of the 17 s scene, and the
     * dancing pet's LCD only when its pose changes.
     */
    @Test
    fun shelfAndPetDoNotRecomposeEveryFrame() {
        val state = lastOne().let { s -> s.updateList(MotionFixture.LIST_ID) { it.copy(trip = it.trip!!.copy(pet = true)) } }
        val rig = HarnessRig(compose, state).start()
        val s = rig.finish()
        val shelf0 = rig.probe.recompositions(CelebrationTags.SHELF)
        val frames = rig.sampleTo(s + 3000).size
        val shelf = rig.probe.recompositions(CelebrationTags.SHELF) - shelf0
        assertTrue("the shelf recomposed $shelf times over $frames frames", shelf <= 30)
        val lcd0 = rig.probe.recompositions(CelebrationTags.LCD)
        val danceFrames = rig.sampleTo(s + 6230 + 2400).size
        val lcd = rig.probe.recompositions(CelebrationTags.LCD) - lcd0
        assertTrue("the pet LCD recomposed $lcd times over $danceFrames frames", lcd <= 8)
    }

    /**
     * §11.2: the page curl peels the scene's bottom-right corner "beside the bonus card, never over text". The peeled
     * corner covers its whole leg × leg square (the album page in one half, the folded-back flap in the other), so every
     * text plate must end above it or left of it. Checked on the fixture's scene at h760 (150 dp leg), and at the summary
     * for 8, 12, 20 and 24 stops × font scale 1.0, 1.3 and 2.0 × a 360 × 640 and a 360 × 760 dp screen: the leg is never
     * under 96 dp (the content column fits itself to keep that room), and no plate reaches into it.
     */
    @Test
    fun curlNeverCoversText() {
        // The fixture, played: the full 150 dp corner beside the bonus card.
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.finish()
        rig.advanceTo(s + 12600)
        val scene = rig.bounds(CelebrationTags.SCENE)
        val curl = rig.probe.overlaysAt(rig.now).single { it.tag == "cele_curl" }.bounds!!
        assertEquals("the fixture's curl is 150 dp", 150f, curl.width / compose.density.density, 0.5f)
        val corner = androidx.compose.ui.unit.DpRect(scene.right - 150.dp, scene.bottom - 150.dp, scene.right, scene.bottom)
        for (tag in CURL_PLATES) {
            val b = rig.bounds(tag)
            assertTrue("$tag $b reaches into the curl's corner $corner", b.bottom <= corner.top || b.right <= corner.left)
        }
    }

    @Test
    fun curlNeverCoversTextMatrix() {
        data class Case(val stops: Int, val fontScale: Float, val height: Int)
        val cases = listOf(8, 12, 20, 24).flatMap { n -> listOf(1f, 1.3f, 2f).flatMap { fs -> listOf(640, 760).map { h -> Case(n, fs, h) } } }
        var case by androidx.compose.runtime.mutableStateOf(cases.first())
        val clock = MotionClock()
        val probe = StillnessProbe()
        compose.setContent {
            val c = case
            val content = remember(c.stops) {
                val state = if (c.stops == 8) MotionFixture.allBut(emptyList()) else MotionFixture.many(c.stops * 2, c.stops)
                CelebrationContent.from(state, MotionFixture.LIST_ID, Album(), MotionFixture.catalog)!!
            }
            val plan = remember(content) { CelebrationTimeline.plan(content.listName, content.stats, false, total = content.total) }
            val base = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, c.fontScale),
                LocalMotionProbe provides probe,
            ) {
                PicnicTheme {
                    androidx.compose.runtime.key(c) {
                        Box(Modifier.size(360.dp, c.height.dp)) {
                            CelebrationScene(CelebrationPresentation(MotionFixture.LIST_ID, 0, content, plan, null, false, false), clock, CelebrationCopy.English)
                        }
                    }
                }
            }
        }
        val d = compose.density.density
        for (c in cases) {
            compose.runOnIdle {
                probe.clear()
                case = c
            }
            compose.waitForIdle()
            // Well into the summary, the curl fully out (the scene holds its last frame until the engine closes it).
            compose.runOnIdle { clock.now = 30_000 }
            compose.waitForIdle()
            // One more scene frame once the column has fitted itself (the first layout measures the bonus card).
            compose.runOnIdle { clock.now = 30_016 }
            compose.waitForIdle()
            val curl = probe.overlaysAt(clock.now).lastOrNull { it.tag == "cele_curl" }?.bounds
            assertNotNull("$c: no curl reported", curl)
            val leg = curl!!.width / d
            assertTrue("$c: the curl's leg is $leg dp", leg >= 96f - 0.5f && leg <= 150f + 0.5f)
            for (tag in CURL_PLATES) {
                val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("$c: $tag $b reaches into the curl $curl", b.bottom <= curl.top + 0.5f || b.right <= curl.left + 0.5f)
                assertTrue("$c: $tag $b is off the screen", b.top >= -0.5f && b.bottom <= c.height * d + 0.5f)
            }
            // Nothing on the scene is clipped.
            val clipped = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.expectValue(earth.cora.picnic.ui.a11y.TextOverflowed, true), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$c: clipped text", clipped.isEmpty())
        }
    }

    /**
     * §15 ("names wrap, never clip"), §9 (a beat counts the words newly on screen): the list-name chip shows a long name
     * whole on its lines (3 at font scale 2.0), never clipped; a name longer than CHIP_NAME_MAX is cut at a word with "…",
     * and the title beat counts exactly the words the chip shows. A name of words too wide for the chip's lines ends in
     * "…" and is marked TextOverflowed (never a silent clip).
     */
    @Test
    fun longListNameNeverClips() {
        val sixty = "Thanksgiving dinner and cottage weekend groceries for everyone"
        assertTrue(sixty.length in 60..CelebrationContent.CHIP_NAME_MAX)
        val long = "$sixty plus the neighbours, the cousins from Kingston and the dog"
        val wide = List(8) { "WWWWWWWW" }.joinToString(" ")
        assertEquals("cut at a word", "Thanksgiving dinner and cottage weekend groceries for everyone…", CelebrationContent.chipName(long))
        data class Case(val name: String, val fontScale: Float)
        val cases = listOf(Case(sixty, 1f), Case(sixty, 1.3f), Case(sixty, 2f), Case(long, 1f), Case(long, 1.3f), Case(long, 2f), Case(wide, 2f))
        var case by androidx.compose.runtime.mutableStateOf(cases.first())
        val clock = MotionClock()
        compose.setContent {
            val c = case
            val content = remember(c) {
                CelebrationContent.from(MotionFixture.allBut(emptyList(), title = c.name), MotionFixture.LIST_ID, Album(), MotionFixture.catalog)!!
            }
            val plan = remember(content) { CelebrationTimeline.plan(content.listName, content.stats, false, total = content.total) }
            val base = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, c.fontScale)) {
                PicnicTheme {
                    androidx.compose.runtime.key(c) {
                        Box(Modifier.size(360.dp, 760.dp)) {
                            CelebrationScene(CelebrationPresentation(MotionFixture.LIST_ID, 0, content, plan, null, false, false), clock, CelebrationCopy.English)
                        }
                    }
                }
            }
        }
        compose.runOnIdle { clock.now = 3000 }
        for (c in cases) {
            compose.runOnIdle { case = c }
            compose.waitForIdle()
            val shown = texts(CelebrationTags.CHIP).single()
            val overflowed = compose.onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(earth.cora.picnic.ui.a11y.TextOverflowed, true) and
                    androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(CelebrationTags.CHIP)).or(androidx.compose.ui.test.hasTestTag(CelebrationTags.CHIP)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
            val plan = CelebrationTimeline.plan(CelebrationContent.chipName(c.name), TripStats.of(MotionFixture.allBut(emptyList()).list(MotionFixture.LIST_ID)!!, 8), false)
            val titleWords = plan.beat(BeatName.Title).words
            when (c.name) {
                wide -> assertTrue("$c: a name too wide for its lines is marked, never silently clipped", overflowed)
                else -> {
                    assertFalse("$c: the chip clipped its name", overflowed)
                    assertEquals("$c: what TalkBack and the chip carry", CelebrationContent.chipName(c.name), shown)
                    // "List complete!" (2) + the chip + "24/24★" (1) + "tap to skip" (3).
                    assertEquals("$c: the title beat counts the chip's words", 2 + earth.cora.picnic.ui.theme.words("♡ $shown") + 1 + 3, titleWords)
                }
            }
        }
    }

    /**
     * §11.2 "No glint on the bonus card", §9 (glints never inside a hold): with a 5-word list name the bonus card's hold
     * runs 10.33 to 12.13 s, across the 12.0 s phase of the shiny glint loop (500 ms every 4 s) that holo stickers run.
     * The bonus sticker's foil runs no loop, so the card stays still for its whole hold: no glint, nor anything else, is
     * reported over it on any frame (the scene reports every glint that remains, the hamper's minis', as an overlay).
     */
    @Test
    fun noGlintOnTheBonusCardDuringItsHold() {
        val rig = HarnessRig(compose, lastOne(title = "Cottage weekend picnic and barbecue")).start()
        val s = rig.finish()
        val bonus = rig.p.celebration!!.plan!!.beat(BeatName.Bonus)
        val settle = bonus.settle!!
        assertEquals(10_330L, settle)
        assertEquals(1800L, bonus.hold)
        assertTrue("the hold spans the loop's 12.0 s glint", settle < 12_000L && settle + bonus.hold > 12_000L)
        rig.advanceTo(s + settle + bonus.hold + 100)
        var why = ""
        val still = rig.probe.stillFrom(CelebrationTags.BONUS, s + settle) { why = it }
        assertTrue("the bonus card is still $still ms of ${bonus.hold} ($why)", still >= bonus.hold - 16)
        val glints = (s + settle..s + settle + bonus.hold).flatMap { t -> rig.probe.overlaysAt(t) }.filter { "glint" in it.tag }
        val plate = rig.probe.frames(CelebrationTags.BONUS).last().bounds
        assertTrue("a glint over the bonus card: $glints", glints.none { it.bounds?.overlaps(plate) == true })
        // The hamper's holo minis do glint, as an overlay away from every plate.
        assertTrue(rig.probe.overlaysAt(rig.now).any { it.tag == "cele_hamper_glint" } || rig.p.celebration!!.content!!.riders.none { it.holo })
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

    /** Item 184: one tap or Back closes it; it closes by itself at 18.45 s, except with TalkBack on (then it waits). */
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
        val frames = rig.sampleTo(s + 18800)
        assertBeat("auto-close", s + 18450, frames.firstTime { it.celebration?.closingT0 != null })
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
        // max(6000, readHold(17 words, with the progress bar's label)) = 6.3 s.
        assertEquals(6300L, plan.summaryHold)
        val frames = rig.sampleTo(s + 6800)
        assertBeat("fades out after its hold", s + 120 + 6300, frames.firstTime { it.celebration?.closingT0 != null })
        for (tag in listOf(CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD, CelebrationTags.PERFECT, CelebrationTags.BANNER, CelebrationTags.BONUS, CelebrationTags.CLOSING_LABEL)) {
            assertTrue("$tag still for the hold", rig.probe.stillFrom(tag, s + 120) >= 6300)
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

    /**
     * Item 314: the summary card shows a washi progress bar along its bottom edge that fills over the summary hold; a tap
     * on the card keeps it (Done closes it); a tap on the rays, the shelf or the album corner closes it, as "tap to
     * skip" taught; TalkBack has no bar.
     */
    @Test
    fun progressBarAndKeep() {
        assertEquals(0f, closingFill(0, 6300, false), 0f)
        assertEquals(0.5f, closingFill(3150, 6300, false), 0.001f)
        assertEquals(1f, closingFill(6300, 6300, false), 0f)
        assertEquals(0.375f, closingFill(3150 - 1, 6300, true), 0.001f)
        val rig = HarnessRig(compose, lastOne()).start()
        var s = rig.finish()
        rig.advanceTo(s + 12150 - 200)
        assertNull(rig.p.celebration!!.keptAt)
        rig.advanceTo(s + 13000)
        assertTrue("P1", texts(CelebrationTags.CLOSING).contains("Closing… tap to keep it"))
        // The bar runs along the summary card's bottom edge (straddling it), inside the card's width.
        val card = rig.bounds(CelebrationTags.SUMMARY_CARD)
        val bar = rig.bounds(CelebrationTags.CLOSING)
        assertTrue("bar $bar along the card's bottom edge $card", bar.bottom <= card.bottom + 0.5.dp && bar.bottom >= card.bottom - 2.dp && bar.left >= card.left && bar.right <= card.right)
        // A tap on the summary card keeps it: the bar goes, Done shows, and it no longer closes by itself.
        compose.onNodeWithTag(CelebrationTags.SUMMARY_CARD, useUnmergedTree = true).performTouchInput { click(Offset(width / 2f, height / 3f)) }
        rig.frame()
        assertNotNull("#3 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration!!.keptAt)
        rig.advanceTo(s + 20000)
        assertNotNull("#4 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration)
        assertTrue("P2", compose.onAllNodesWithTag(CelebrationTags.CLOSING, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // Done sits on the card where the bar was: nothing below it moved.
        assertEquals(card, rig.bounds(CelebrationTags.SUMMARY_CARD))
        // On the card it closes, centred in its bottom band, under the banner.
        val done = rig.bounds(CelebrationTags.DONE)
        val banner = rig.bounds(CelebrationTags.BANNER)
        assertTrue("Done $done inside the card $card, under the banner $banner", done.left >= card.left && done.right <= card.right && done.top >= banner.bottom && done.bottom <= card.bottom)
        assertEquals("Done centred on the card", ((card.left + card.right) / 2).value, ((done.left + done.right) / 2).value, 1f)
        compose.onNodeWithTag(CelebrationTags.DONE, useUnmergedTree = true).performClick()
        rig.advance(250)
        assertNull("#10 at ${rig.now - s}: ${rig.p.celebration?.let { c -> "t0=${c.t0 - s} closing=${c.closingT0?.minus(s)} kept=${c.keptAt?.minus(s)}" }}", rig.p.celebration)
        // A tap elsewhere during the summary closes it at once: the rays above the shelf, the shelf, the album corner.
        val elsewhere = listOf<(Float, Float) -> Offset>(
            { w, _ -> Offset(w / 2f, rig.bounds(CelebrationTags.SHELF).bottom.value * compose.density.density + 2f) },
            { w, _ -> Offset(w - 30f, 40f) },
            { w, h -> Offset(w - 20f, h - 20f) },
        )
        for ((i, at) in elsewhere.withIndex()) {
            rig.resetTrip()
            s = rig.finish()
            rig.advanceTo(s + 13000)
            compose.onNodeWithTag(CELEBRATION_TAG).performTouchInput { click(at(width.toFloat(), height.toFloat())) }
            rig.frame()
            assertNotNull("#5.$i at ${rig.now - s}", rig.p.celebration!!.closingT0)
            assertNull("#11.$i", rig.p.celebration!!.keptAt)
            rig.advance(250)
        }
        // TalkBack: no countdown bar.
        compose.runOnIdle { rig.talkBackState.value = true }
        rig.resetTrip()
        s = rig.finish()
        rig.advanceTo(s + 13000)
        assertTrue("P3", compose.onAllNodesWithTag(CelebrationTags.CLOSING, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    /**
     * Item 314 with "Time to take action" (§9, §15): the summary carries an action ("tap to keep it"), so it waits the
     * recommended timeout before it closes, and the bar fills over exactly that time.
     */
    @Test
    fun progressBarFollowsTimeToTakeAction() {
        val timeouts = earth.cora.picnic.platform.FakeTimeouts(actionTimeoutMs = 20_000)
        val rig = HarnessRig(compose, lastOne(), timeouts = timeouts).start()
        val s = rig.finish()
        val cele = rig.p.celebration!!
        assertEquals(20_000L, cele.summaryHoldMs)
        assertTrue("asked with the action flag: ${timeouts.requests}", timeouts.requests.contains(6300L to true))
        val frames = rig.sampleTo(s + 12150 + 20_400)
        assertBeat("closes after the recommended timeout", s + 12150 + 20_000, frames.firstTime { it.celebration?.closingT0 != null })
        assertTrue("still open at 30 s", frames.filter { it.first < s + 12150 + 20_000 }.all { it.second.celebration?.closingT0 == null })
        // The bar's fill: half way at 10 s, full at 20 s.
        assertEquals(0.5f, closingFill(10_000, cele.summaryHoldMs, false), 0.001f)
    }

    /**
     * A configuration change (a rotation) re-creates the screen's composition mid-scene: the engine, kept by the
     * screen's ViewModel, keeps the celebration, which carries on where it was and closes on time; nothing replays.
     */
    @Test
    fun survivesRecreation() {
        val shown = androidx.compose.runtime.mutableStateOf(true)
        val rig = HarnessRig(compose, lastOne())
        rig.start { if (shown.value) MotionHarness(rig.model, rig.toasts) }
        val s = rig.finish()
        rig.advanceTo(s + 5000)
        val before = rig.p.celebration!!
        compose.runOnIdle { shown.value = false }
        rig.advance(400)
        compose.runOnIdle { shown.value = true }
        rig.frame()
        val after = rig.p.celebration
        assertNotNull("the scene is still up", after)
        assertEquals(before.t0, after!!.t0)
        compose.onNodeWithTag(CELEBRATION_TAG).fetchSemanticsNode()
        assertEquals(1, rig.feedback.count(Cue.CELEBRATION))
        val frames = rig.sampleTo(s + 18800)
        assertBeat("closes on time", s + 18450, frames.firstTime { it.celebration?.closingT0 != null })
        assertEquals(1, rig.engine.eventLog.count { it is MotionEvent.CelebrationStarted })
    }

    // ---- helpers ----

    /** The text plates the album corner must never cover (§11.2). */
    private val CURL_PLATES = listOf(
        CelebrationTags.BONUS, CelebrationTags.BANNER, CelebrationTags.CLOSING_LABEL, CelebrationTags.PERFECT,
        CelebrationTags.SUMMARY_CARD, CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD,
    )

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

    private companion object {
        /**
         * Mostly white: more than this share of the screen (a Day list's paper is about half of it; the old flash frames
         * were 95 to 100 %).
         */
        const val MOSTLY_WHITE = 0.8f

        /** The heart's first pose (180 dp, cut by the screen's edge beside the checkbox) already takes this much (4 %). */
        const val FIRST_POSE = 0.03f

        /** A frame's own small changes (the scene's motion, anti-aliasing) under the scene's share. */
        const val SLACK = 0.02f

        /**
         * The scene has the screen: over a Glow list it changes 94 % of it, over a Day list 65 % (its mint stripes and
         * white plates fall on the list's own mint and paper).
         */
        const val COVERED = 0.5f
    }
}

package earth.cora.picnic.platform

import android.Manifest
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.add.ADD_FIELD_TAG
import earth.cora.picnic.ui.add.AddFixtures
import earth.cora.picnic.ui.add.STOP_STICKER_TAG
import earth.cora.picnic.ui.eggs.EggListUnderTest
import earth.cora.picnic.ui.eggs.EggRig
import earth.cora.picnic.ui.eggs.EggRules
import earth.cora.picnic.ui.eggs.JackpotMotion
import earth.cora.picnic.ui.eggs.ReceiptMotion
import earth.cora.picnic.ui.eggs.anchor
import earth.cora.picnic.ui.eggs.completedFixture
import earth.cora.picnic.ui.eggs.holdLcd
import earth.cora.picnic.ui.eggs.tapAt
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.ListRig
import earth.cora.picnic.ui.list.ListTags
import earth.cora.picnic.ui.list.frames
import earth.cora.picnic.ui.list.landTime
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.BeatName
import earth.cora.picnic.ui.list.motion.Beats
import earth.cora.picnic.ui.list.show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Items 278 and 279 (docs/DESIGN_STICKER.md §14.3): every moment of the cue map emits its cue, on the real screens.
 *
 * The map is table-driven from the spec itself: [ROWS] names each §14.3 row exactly as the spec words it, with the cue
 * each of its moments asks for and the scenario that plays that moment ([section14_3IsTheMap] fails when a row is added,
 * renamed or left without a scenario). Each scenario is a test below: it drives the app as she would (a tap on a row's
 * spot, a swipe, a pull, the add bar, the LCD, the stamps) on the List destination as the app hosts it (ListRig: the
 * real ListViewModel, AddViewModel and choreographer) or on the real list over the Surprise lab's sandbox with WP9's
 * engine (EggRig), records every cue with its time, and asserts each of its rows' cues at its moment: never before
 * the gesture, and at the beat §8 and §11 give it. The table of sounds and haptics per cue is the spec's too
 * ([everyCueMatchesSection14_3]). Owner: WP10.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class CueMapTest {
    @get:Rule
    val compose = createComposeRule()

    /** One moment of a §14.3 row: the cue it asks for and the scenario (a test of this class) that plays it. */
    private data class Moment(val cue: Cue, val scenario: String, val what: String)

    private val dp get() = compose.density.density

    // ---- The table ----

    @Test
    fun section14_3IsTheMap() {
        val rows = specRows()
        assertEquals("§14.3's rows, as the spec words them", rows.map { it.moment }.toSet(), ROWS.keys)
        val scenarios = CueMapTest::class.java.methods.filter { it.isAnnotationPresent(Test::class.java) }.map { it.name }.toSet()
        for ((row, moments) in ROWS) {
            assertTrue("$row has a moment", moments.isNotEmpty())
            val spec = rows.single { it.moment == row }
            for (m in moments) {
                assertTrue("$row (${m.what}): its scenario ${m.scenario} is a test", m.scenario in scenarios)
                val clip = m.cue.sound?.clip
                assertNotNull("$row: ${m.cue} has a sound", clip)
                assertTrue("$row: ${m.cue}'s sound '$clip' is the spec's '${spec.sound}'", Regex("\\b$clip\\b").containsMatchIn(spec.sound))
                val haptic = m.cue.haptic
                if (haptic != null) assertTrue("$row: ${m.cue}'s haptic $haptic is the spec's '${spec.haptic}'", haptic.name in spec.haptic)
            }
        }
        assertEquals("every cue is some moment's", Cue.entries.toSet(), ROWS.values.flatten().map { it.cue }.toSet())
    }

    /** The cue map is exactly §14.3 (sound + haptic per moment). */
    @Test
    fun everyCueMatchesSection14_3() {
        val table: Map<Cue, Pair<Sound?, Haptic?>> = mapOf(
            Cue.HANKO to (Sound.THUNK to Haptic.CONFIRM),
            Cue.PEEL to (Sound.TK to Haptic.CLOCK_TICK),
            Cue.LAND to (Sound.PFF to Haptic.TEXT_HANDLE_MOVE),
            Cue.LCD_TICK to (Sound.BEEP to null),
            Cue.MAGIC_APPEAR to (Sound.KIRA to null),
            Cue.UNCHECK to (Sound.PEEL to null),
            Cue.TEAR_OFF to (Sound.PEEL to Haptic.CLOCK_TICK),
            Cue.AISLE_DONE to (Sound.THUNK to Haptic.CONFIRM),
            Cue.STAMP to (Sound.THUNK to Haptic.CONFIRM),
            Cue.NEXT_CARD to (Sound.PON to null),
            Cue.CELEBRATION to (Sound.JINGLE to null),
            Cue.PERFECT to (Sound.THUNK to Haptic.CONFIRM),
            Cue.CAPSULE_POP to (Sound.PON to Haptic.CONFIRM),
            Cue.CASSETTE to (Sound.CLUNK to Haptic.KEYBOARD_TAP),
            Cue.PETAL_SHAKE to (Sound.KIRA to Haptic.CLOCK_TICK),
            Cue.PET_FED to (Sound.BEEP to null),
            Cue.RECEIPT_PRINT to (Sound.PRINT to Haptic.CLOCK_TICK),
            Cue.RECEIPT_TEAR to (Sound.TK to null),
            Cue.PULL_REFRESH to (Sound.PON to Haptic.CLOCK_TICK),
            Cue.BONUS_JINGLE to (Sound.JINGLE to null),
        )
        assertEquals(Cue.entries.toSet(), table.keys)
        for (cue in Cue.entries) assertEquals("$cue", table.getValue(cue), cue.sound to cue.haptic)
    }

    /** The ten clips of §14.2, each used by at least one moment. */
    @Test
    fun theTenClipsOfSection14_2() {
        assertEquals(
            listOf("thunk", "tk", "pff", "clunk", "beep", "pon", "kira", "peel", "jingle", "print"),
            Sound.entries.map { it.clip },
        )
        assertEquals(Sound.entries.toSet(), Cue.entries.mapNotNull { it.sound }.toSet())
        assertEquals(4000L, BONUS_JINGLE_PERIOD_MS)
    }

    // ---- The list (ListRig: the List destination as the app hosts it) ----

    /**
     * Baking powder, Baking's last thing, checked with a tap on its spot (the fixture stands at stop 4): the hanko, the
     * peel, the landing and the LCD tick, the twin's magic, then Baking's done chain in place (lid, stamp, the tray's
     * curl) and the Next card (§8.3, §8.4).
     */
    @Test
    fun checkOffChain() {
        val rig = ListRig()
        val log = showList(rig, scrollTo = "baking-powder")
        val tap = log.tap(ListTags.row("baking-powder"))
        log.run(2_200)
        val land = landTime(rig, "baking-powder", tap)
        played(
            "checkOffChain",
            log,
            tap,
            mapOf(
                Cue.HANKO to listOf(tap + Beats.HANKO),
                Cue.PEEL to listOf(tap + Beats.PEEL),
                Cue.LAND to listOf(land),
                Cue.LCD_TICK to listOf(land),
                Cue.MAGIC_APPEAR to listOf(land + Beats.MAGIC_AFTER_LAND),
                Cue.AISLE_DONE to listOf(land + Beats.LID),
                Cue.STAMP to listOf(land + Beats.STAMP),
                // The open tray's curl as it folds (form Tap).
                Cue.UNCHECK to listOf(land + Beats.CURL),
                Cue.NEXT_CARD to listOf(land + Beats.NEXT_CARD),
            ),
        )
    }

    /** Flour, already in the basket, tapped again: the un-check's peel at the tap (§8.4). */
    @Test
    fun uncheck() {
        val rig = ListRig()
        val log = showList(rig, scrollTo = "flour")
        val tap = log.tap(ListTags.row("flour"))
        log.run(600)
        played("uncheck", log, tap, mapOf(Cue.UNCHECK to listOf(tap)))
    }

    /**
     * The first check-off more than 12 h after the trip's last activity starts a fresh book (§0.4 Q9): its page turns
     * with the tap, and it sounds like any page turn.
     */
    @Test
    fun pageTurn() {
        val rig = ListRig(now = ListFixture.NOW + 13 * 3_600_000L)
        val log = showList(rig, scrollTo = "baking-powder")
        val tap = log.tap(ListTags.row("baking-powder"))
        log.run(600)
        assertTrue("a fresh book", rig.app.list(ListFixture.LIST_ID)!!.items.count { it.checked } == 1)
        played("pageTurn", log, tap, mapOf(Cue.UNCHECK to listOf(tap)))
    }

    /** Bananas torn off with a swipe left (§8.6): the peel and its tick as the perforation prints. */
    @Test
    fun tearOff() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        val log = showList(rig, scrollTo = null)
        val t0 = log.now()
        compose.onNodeWithTag(ListTags.row("bananas")).performTouchInput {
            down(center)
            repeat(4) { moveBy(Offset(-20 * dp, 0f)) }
            up()
        }
        log.run(1_400)
        assertTrue("torn out", rig.app.list(ListFixture.LIST_ID)!!.items.none { it.id == "bananas" })
        played("tearOff", log, t0, mapOf(Cue.TEAR_OFF to listOf(t0)), slack = 200)
    }

    /**
     * Toothpaste, the trip's last thing: the celebration starts with its jingle, then PER-FECT!, the bonus capsule's pop,
     * the curl and the pocket's kira at the beats its plan computed from the real words (§11.2).
     */
    @Test
    fun celebration() {
        val rig = ListRig(ListFixture.allBut(listOf("toothpaste"), open = "household"))
        val log = showList(rig, scrollTo = "toothpaste")
        val tap = log.tap(ListTags.row("toothpaste"))
        var scene = rig.engine.current.value.celebration
        var guard = 0
        while (scene?.plan == null && guard++ < 300) {
            log.run(16)
            scene = rig.engine.current.value.celebration
        }
        val plan = assertNotNullAnd("the celebration starts", scene?.plan)
        val s = scene!!.t0
        log.run(s + plan.beat(BeatName.PocketMagic).at + 200 - log.now())
        played(
            "celebration",
            log,
            tap,
            mapOf(
                Cue.CELEBRATION to listOf(s),
                Cue.PERFECT to listOf(s + plan.beat(BeatName.Perfect).at),
                Cue.CAPSULE_POP to listOf(s + plan.beat(BeatName.CapsulePop).at),
                Cue.UNCHECK to listOf(s + plan.beat(BeatName.Curl).at),
                Cue.MAGIC_APPEAR to listOf(s + plan.beat(BeatName.PocketMagic).at),
            ),
        )
    }

    /**
     * The book's own PER-FECT! (§7.6, §8.3): a finished shop (its finale played) completed again, Toothpaste un-checked
     * and checked again, presses PERFECT on the book in place at L + 1800 with its thunk.
     */
    @Test
    fun perfectInTheBook() {
        val rig = ListRig(ListFixture.done().setOpenAisle(ListFixture.LIST_ID, "household"), now = ListFixture.TRIP_START + 50 * ListFixture.MIN)
        val log = showList(rig, scrollTo = "toothpaste")
        val off = log.tap(ListTags.row("toothpaste"))
        log.run(900)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("toothpaste")
        log.run(32)
        val tap = log.tap(ListTags.row("toothpaste"))
        log.run(3_000)
        assertTrue("complete again", rig.app.list(ListFixture.LIST_ID)!!.trip!!.completedAt != null)
        played("perfectInTheBook", log, off, mapOf(Cue.PERFECT to listOf(landTime(rig, "toothpaste", tap) + 1_800)))
    }

    /** Pulled down past 40 dp at the top and let go (§6.1): the refresh's pon at the release. */
    @Test
    fun pullToRefresh() {
        val rig = ListRig()
        val log = showList(rig, scrollTo = null)
        compose.onNodeWithTag(ListTags.LIST).performTouchInput {
            down(Offset(centerX, top + 20 * dp))
            repeat(6) { moveBy(Offset(0f, 140f * dp / 6)) }
        }
        log.run(64)
        assertEquals("nothing before the release", 0, log.count(Cue.PULL_REFRESH))
        val release = log.now()
        compose.onNodeWithTag(ListTags.LIST).performTouchInput { up() }
        log.run(400)
        played("pullToRefresh", log, release, mapOf(Cue.PULL_REFRESH to listOf(release)), slack = 100)
    }

    /** A note written in the add bar and sent (§6.2): the AI's answer lands as stickers with the capsule's pop. */
    @Test
    fun capsulePopAiResults() {
        val rig = ListRig()
        rig.assistant.ingest = { _, _ ->
            IngestResult(
                listOf(AddFixtures.proposed("Lemons", "lemon", "produce", "2"), AddFixtures.proposed("Oat milk", "plant_milk", "dairy-eggs", "1.89 L carton")),
                "", emptyList(), emptyList(), AddFixtures.meta(),
            )
        }
        val log = showList(rig, scrollTo = null)
        compose.onNodeWithTag(ADD_FIELD_TAG).performTextInput("2 lemons, oat milk")
        log.run(32)
        val sent = log.now()
        compose.onNodeWithTag(ADD_FIELD_TAG).performImeAction()
        log.run(1_200)
        assertTrue("added", rig.app.list(ListFixture.LIST_ID)!!.items.any { it.name == "Lemons" })
        played("capsulePopAiResults", log, sent, mapOf(Cue.CAPSULE_POP to listOf(sent)), slack = 1_000)
    }

    /** Speak from the add bar, then stop (§6.2): the cassette's clunk at the start and at the stop. */
    @Test
    fun cassette() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val rig = ListRig()
        val log = showList(rig, scrollTo = null)
        val start = log.now()
        compose.onNode(hasContentDescription("Speak a voice note") and hasClickAction()).performClick()
        log.run(600)
        assertTrue("recording", rig.addVm.state.value.recording)
        val stop = log.now()
        compose.onNodeWithTag(STOP_STICKER_TAG).performClick()
        log.run(400)
        played("cassette", log, start, mapOf(Cue.CASSETTE to listOf(start, stop)), slack = 100)
    }

    // ---- The eggs (EggRig: the real list over the Surprise lab's sandbox, with WP9's engine) ----

    /** Egg 13 from the lab: the gold capsule's sticker arrives, wobbles twice, then pops (§12.2). */
    @Test
    fun capsulePopJackpot() {
        val r = EggRig(compose).start()
        val fired = r.now
        r.fire(13)
        var guard = 0
        while (r.eggs.jackpot == null && guard++ < 200) r.frame()
        assertNotNull("the jackpot sticker arrives", r.eggs.jackpot)
        val arrived = r.now
        r.advance(JackpotMotion.WOBBLE_MS + 200)
        played("capsulePopJackpot", EggLog(r), fired, mapOf(Cue.CAPSULE_POP to listOf(arrived + JackpotMotion.WOBBLE_MS)), slack = 60, early = 40)
    }

    /** A shake on the list (egg 4): petals, with one soft tick and kira. */
    @Test
    fun petalShake() {
        val r = EggRig(compose).start()
        val t0 = r.now
        r.act { r.engine.onShake() }
        r.advance(200)
        played("petalShake", EggLog(r), t0, mapOf(Cue.PETAL_SHAKE to listOf(t0)))
    }

    /** The LCD held mid-trip (egg 15): the bee moves in, "FED" with its beep. */
    @Test
    fun petFed() {
        val r = EggRig(compose).start()
        val t0 = r.now
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        r.advance(200)
        played("petFed", EggLog(r), t0, mapOf(Cue.PET_FED to listOf(t0 + r.timeouts.holdThresholdMs)), slack = 120)
    }

    /** The LCD held on a finished shop (egg 18): the print's ticks as it feeds out, the tear's tk at 1.7 s (§13.2). */
    @Test
    fun receipt() {
        val r = EggRig(compose, fixture = completedFixture()).start()
        val t0 = r.now
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        val held = r.now
        r.advance(2_200)
        played(
            "receipt",
            EggLog(r),
            t0,
            // The print sounds when the hold reaches the threshold; the paper's feed is timed from the frame she lets go on.
            mapOf(Cue.RECEIPT_PRINT to listOf(t0 + r.timeouts.holdThresholdMs), Cue.RECEIPT_TEAR to listOf(held + ReceiptMotion.FEED_MS)),
            slack = 120,
        )
    }

    /**
     * Item 279: the bonus stage (egg 10, the first four stamps tapped 1st 1st 2nd 2nd 3rd 4th 3rd 4th) plays its jingle at
     * once and every 4 s while the list is resumed; it is silent while the app is in the background, picks up again at
     * once on return, and stops for good when she leaves the list.
     */
    @Test
    fun bonusJingleLoop() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        var shown by mutableStateOf(true)
        val r = EggRig(compose)
        r.start {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown) EggListUnderTest.Real.content(r)
            }
        }
        val aisles = r.model.grouped().sections.filter { it.kind == SectionKind.AISLE }.take(4).map { it.id }
        val log = EggLog(r)
        val first = r.now
        EggRules.BONUS_SEQUENCE.forEach { k ->
            assertEquals("no jingle before the stage", 0, log.count(Cue.BONUS_JINGLE))
            r.tapAt(r.anchor(AnchorRegistry.stampKey(aisles[k])).center)
            r.advance(200)
        }
        assertTrue("the bonus stage", r.eggs.bonusStage)
        val start = log.times(Cue.BONUS_JINGLE).firstOrNull()
        assertNotNull("the jingle at once", start)
        r.advanceTo(start!! + 3 * BONUS_JINGLE_PERIOD_MS + 500)
        played("bonusJingleLoop", log, first, mapOf(Cue.BONUS_JINGLE to (0..3).map { start + it * BONUS_JINGLE_PERIOD_MS }))
        assertEquals("every 4 s, no more", 4, log.count(Cue.BONUS_JINGLE))

        // In the background: silence, however long.
        r.act { owner.registry.currentState = Lifecycle.State.STARTED }
        r.advance(10_000)
        assertEquals("silent in the background", 4, log.count(Cue.BONUS_JINGLE))
        // Back: at once, then every 4 s.
        val back = r.now
        r.act { owner.registry.currentState = Lifecycle.State.RESUMED }
        r.advance(BONUS_JINGLE_PERIOD_MS + 300)
        val after = log.times(Cue.BONUS_JINGLE).drop(4)
        assertEquals("at once and 4 s later: $after", 2, after.size)
        assertTrue("at once on return", after[0] - back in 0..120)
        assertEquals(BONUS_JINGLE_PERIOD_MS.toDouble(), (after[1] - after[0]).toDouble(), 50.0)

        // She leaves the list: the jingle stops for good.
        Snapshot.withMutableSnapshot { shown = false }
        r.frame()
        val left = log.count(Cue.BONUS_JINGLE)
        r.advance(3 * BONUS_JINGLE_PERIOD_MS)
        assertEquals("silent once she has left the list", left, log.count(Cue.BONUS_JINGLE))
    }

    // ---- Support ----

    /** Every cue so far with its time. */
    private interface CueLog {
        val log: List<Pair<Long, Cue>>
        fun times(cue: Cue): List<Long> = log.filter { it.second == cue }.map { it.first }
        fun count(cue: Cue): Int = log.count { it.second == cue }
    }

    /** EggRig's own timed record (the test clock). */
    private class EggLog(private val r: EggRig) : CueLog {
        override val log: List<Pair<Long, Cue>> get() = r.feedback.log.toList()
    }

    /**
     * ListRig's cues timed on the test clock (the frame clock the choreographer's beats run on): read after every frame,
     * so each is stamped with the frame that asked for it.
     */
    private inner class ListLog(private val rig: ListRig) : CueLog {
        override val log = ArrayList<Pair<Long, Cue>>()
        private var seen = 0

        fun now(): Long = compose.mainClock.currentTime

        private fun poll() {
            val cues = rig.feedback.cues
            while (seen < cues.size) log += now() to cues[seen++]
        }

        /** [ms] of frames on the held clock, the cues read after each. */
        fun run(ms: Long) {
            repeat(((ms.coerceAtLeast(0) + 15) / 16).toInt()) {
                compose.frames(1)
                poll()
            }
        }

        /** Taps the node tagged [tag] (a row: the whole row is its checkbox, §4.11) and returns the time of the tap. */
        fun tap(tag: String): Long {
            poll()
            val t = now()
            compose.onNodeWithTag(tag).performClick()
            return t
        }
    }

    /** Shows [rig]'s List destination with [scrollTo]'s row on screen, on a held clock, with no cue yet. */
    private fun showList(rig: ListRig, scrollTo: String?): ListLog {
        compose.show(rig)
        if (scrollTo != null) {
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey(scrollTo)
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = false
        compose.frames(30)
        rig.feedback.clear()
        return ListLog(rig)
    }

    /**
     * Asserts every moment [ROWS] gives [scenario]: its cue sounds at each of the times in [expected] (to the frame: from
     * [early] ms before to [slack] ms after, more for a moment that waits on work off the clock), and none of them sounds
     * before the gesture at [since].
     */
    private fun played(scenario: String, log: CueLog, since: Long, expected: Map<Cue, List<Long>>, slack: Long = 50, early: Long = 20) {
        val moments = ROWS.entries.flatMap { (row, ms) -> ms.filter { it.scenario == scenario }.map { row to it } }
        assertTrue("$scenario plays some moment of §14.3", moments.isNotEmpty())
        for ((row, m) in moments) {
            val times = expected[m.cue] ?: throw AssertionError("$scenario: no time given for $row (${m.what})")
            val heard = log.times(m.cue)
            for (t in times) {
                assertTrue("$row (${m.what}): ${m.cue} at $t, heard at $heard (every cue: ${log.log})", heard.any { it in (t - early)..(t + slack) })
            }
            assertTrue("$row (${m.what}): no ${m.cue} before the gesture at $since ($heard)", heard.none { it < since - early })
        }
    }

    private fun <T : Any> assertNotNullAnd(what: String, value: T?): T {
        assertNotNull(what, value)
        return value!!
    }

    /** One row of §14.3's table as the spec has it. */
    private data class SpecRow(val moment: String, val sound: String, val haptic: String)

    /** §14.3's table, read from docs/DESIGN_STICKER.md. */
    private fun specRows(): List<SpecRow> {
        val doc = listOf(File("../docs/DESIGN_STICKER.md"), File("docs/DESIGN_STICKER.md")).first { it.isFile }.readText()
        val section = doc.substringAfter("### 14.3 Cue map").substringBefore("\n## ")
        return section.lines()
            .filter { it.startsWith("| ") && !it.startsWith("| Moment") && !it.startsWith("|---") }
            .map { line ->
                val cells = line.trim().trim('|').split("|").map { it.trim() }
                SpecRow(cells[0], cells[1], cells[2])
            }
    }

    private companion object {
        /** §14.3, row by row (the spec's words), each with its moments' cues and the scenarios that play them. */
        val ROWS: Map<String, List<Moment>> = mapOf(
            "Hanko on check-off" to listOf(Moment(Cue.HANKO, "checkOffChain", "the tap's hanko")),
            "Peel before the hop" to listOf(Moment(Cue.PEEL, "checkOffChain", "the peel at 200 ms")),
            "Sticker lands in the basket" to listOf(Moment(Cue.LAND, "checkOffChain", "the landing")),
            "LCD digits tick" to listOf(Moment(Cue.LCD_TICK, "checkOffChain", "the LCD's tick with the landing")),
            "Book magic appear" to listOf(
                Moment(Cue.MAGIC_APPEAR, "checkOffChain", "the twin 320 ms after the landing"),
                Moment(Cue.MAGIC_APPEAR, "celebration", "the pocket's kira"),
            ),
            "Un-check, page turn, tear-off, curl" to listOf(
                Moment(Cue.UNCHECK, "uncheck", "un-check"),
                Moment(Cue.UNCHECK, "pageTurn", "a fresh book's page turn"),
                Moment(Cue.TEAR_OFF, "tearOff", "tear-off"),
                Moment(Cue.UNCHECK, "checkOffChain", "the done tray's curl"),
                Moment(Cue.UNCHECK, "celebration", "the scene's curl"),
            ),
            "Aisle done (lid + hanko)" to listOf(Moment(Cue.AISLE_DONE, "checkOffChain", "the lid at L + 300")),
            "Stamp thunk on the stamp page" to listOf(Moment(Cue.STAMP, "checkOffChain", "the stamp at L + 500")),
            "Next card" to listOf(Moment(Cue.NEXT_CARD, "checkOffChain", "the card at L + 1300")),
            "Celebration start" to listOf(Moment(Cue.CELEBRATION, "celebration", "the scene's start")),
            "PERFECT! (scene and book)" to listOf(
                Moment(Cue.PERFECT, "celebration", "the scene's PER-FECT!"),
                Moment(Cue.PERFECT, "perfectInTheBook", "the book's PERFECT in place"),
            ),
            "Capsule pops (AI results, jackpot, bonus)" to listOf(
                Moment(Cue.CAPSULE_POP, "capsulePopAiResults", "AI results"),
                Moment(Cue.CAPSULE_POP, "capsulePopJackpot", "the jackpot"),
                Moment(Cue.CAPSULE_POP, "celebration", "the bonus sticker"),
            ),
            "Cassette start / stop" to listOf(Moment(Cue.CASSETTE, "cassette", "start and stop")),
            "Petal shake" to listOf(Moment(Cue.PETAL_SHAKE, "petalShake", "the shake")),
            "Pet \"FED\"" to listOf(Moment(Cue.PET_FED, "petFed", "FED")),
            "Receipt prints / tears" to listOf(
                Moment(Cue.RECEIPT_PRINT, "receipt", "the print"),
                Moment(Cue.RECEIPT_TEAR, "receipt", "the tear"),
            ),
            "Pull-to-refresh release" to listOf(Moment(Cue.PULL_REFRESH, "pullToRefresh", "the release")),
            "Bonus stage" to listOf(Moment(Cue.BONUS_JINGLE, "bonusJingleLoop", "every 4 s while resumed")),
        )
    }
}

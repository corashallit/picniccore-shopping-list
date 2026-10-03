package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.album.PerfectWeeksCard
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 258 (and the goldens items 238, 242, 246, 247, 250 and 273 name): every egg in each theme variant §12.3 lists
 * for it, captured at its end frame on the test clock (`egg_<n>_end_<variant>`), on the Surprise lab's practice list,
 * fired by the lab's own action (§12.3's last column). Recorded with
 * `./gradlew :app:recordRoborazziDebug --tests '*EggGoldensTest*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggGoldensTest(private val variant: ThemeVariant) {
    @get:Rule
    val compose = createComposeRule()

    private val day get() = variant == ThemeVariant.Day
    private val px = 3f

    private fun rig() = EggRig(compose, variant = variant).start()

    /** Fires egg [n] and captures `egg_<n>_end_<v>` [afterMs] later. */
    private fun end(n: Int, afterMs: Long, check: EggRig.() -> Unit = {}): EggRig {
        val r = rig()
        val t0 = r.fire(n)
        r.advanceTo(t0 + afterMs)
        r.check()
        r.capture("egg_${n}_end_${r.v}")
        return r
    }

    /**
     * `surprise_lab_practice_list` (Appendix C): the Surprise lab screen after a Try it, its practice list brought into
     * view under the "Surprise lab · practice list" ribbon (here the LCD pet has just moved in).
     */
    @Test
    fun practiceList() {
        val lab = SurpriseLabViewModel {
            SurpriseLabSandbox(
                clock = SandboxClock(PracticeFixture.NOW) { compose.mainClock.currentTime },
                feedback = earth.cora.picnic.platform.NoOpFeedback,
                timeouts = earth.cora.picnic.platform.FakeTimeouts(),
                minuteTicks = false,
            )
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(earth.cora.picnic.ui.theme.LocalTimeouts provides earth.cora.picnic.platform.FakeTimeouts()) {
                earth.cora.picnic.ui.theme.PicnicTheme(variant = variant, reducedMotion = false) {
                    earth.cora.picnic.ui.about.SurpriseLabScreen(onBack = {}, lab = lab)
                }
            }
        }
        compose.waitForIdle()
        // Its click action (no test scroll on the held clock: the lab brings the practice list into view itself).
        compose.onNodeWithContentDescription("Try LCD pet").performSemanticsAction(SemanticsActions.OnClick)
        fun frames(n: Int) = repeat(n) {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
        }
        frames(60)
        if (!day) {
            // Glow is the phone's dark mode, which the practice list follows as it does on the phone (the test's
            // Robolectric configuration is light, so the sandbox is told directly, after its frame has started).
            compose.runOnIdle { lab.sandbox!!.variants.onSystemDark(true) }
            frames(6)
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$EGG_SHOTS/surprise_lab_practice_list_${if (day) "day" else "glow"}.png")
        lab.close()
    }

    /** The printed receipt at font scale 1.3 (Day; Appendix C `receipt_fs130`). */
    @Test
    fun receiptFontScale130() {
        if (!day) return
        receiptAt("receipt_fs130", 1.3f)
    }

    /** The printed receipt at font scale 2.0 (Day; Appendix C `receipt_fs200`): 278 dp wide, nothing clips. */
    @Test
    fun receiptFontScale200() {
        if (!day) return
        receiptAt("receipt_fs200", 2f)
    }

    private fun receiptAt(name: String, scale: Float) {
        val r = EggRig(compose, variant = variant, fontScale = scale, fixture = completedFixture(41)).start()
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        r.advance(2_000)
        assertNotNull(r.eggs.receipt)
        r.capture(name)
    }

    /** Egg 1, Day → Dusk at the 7th tap + 1.4 s; `list_dusk_day` once settled (item 238). The Glow moon is static. */
    @Test
    fun egg1() {
        if (!day) return
        val r = end(1, 1400) { assertEquals(ThemeVariant.Dusk, sandbox.variants.variant.value) }
        r.advance(1000)
        r.capture("list_dusk_day")
    }

    /** Egg 1 from Golden: the binder is golden before the taps, Dusk after. */
    @Test
    fun egg1FromGolden() {
        if (!day) return
        val r = rig()
        r.fire(12)
        r.advance(300)
        assertEquals(ThemeVariant.Golden, r.sandbox.variants.variant.value)
        r.act { repeat(EggRules.SUN_TAPS) { r.clock.advance(300); r.engine.onSunTap() } }
        val t0 = r.now
        r.advanceTo(t0 + 1400)
        assertEquals(ThemeVariant.Dusk, r.sandbox.variants.variant.value)
        r.capture("egg_1_end_golden")
    }

    /** Egg 2: release + 2.5 s, mid-walk. */
    @Test
    fun egg2() {
        end(2, 2500) { assertNotNull(eggs.ladybug) }
    }

    /** Egg 3: commit + 3.2 s, settled on its line. */
    @Test
    fun egg3() {
        end(3, 3200)
    }

    /** Egg 4: shake + 1.5 s, petals settled. */
    @Test
    fun egg4() {
        end(4, 1500) { assertNotNull(eggs.petalsId) }
    }

    /**
     * Egg 5 (Glow only, forced): list entry + 0.9 s, "SNACK?" showing, the frozen aisle brought into view with the snack
     * star and doodle by its tab (`egg_5_end_glow`); then `list_midnight_glow` (item 242) once "SNACK?" has gone.
     */
    @Test
    fun egg5() {
        if (day) return
        val r = end(5, 900) {
            assertEquals(ThemeVariant.Glow, sandbox.variants.variant.value)
            assertEquals("SNACK?", eggs.lcdWord?.text)
            assertEquals("frozen", eggs.snackSectionId)
            val snack = compose.onNodeWithTag(EggTags.SNACK, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("the doodle is on screen ($snack)", snack.top >= 0f && snack.bottom <= anchor(AnchorRegistry.ADD_BAR).top)
        }
        r.advance(2_000)
        assertEquals("frozen", r.eggs.snackSectionId)
        r.capture("list_midnight_glow")
    }

    /** Egg 6: commit + 1 s, the row settled and holo. */
    @Test
    fun egg6() {
        end(6, 1000)
    }

    /** Egg 7: send + 1 s, the title holo. */
    @Test
    fun egg7() {
        end(7, 1000) { assertTrue(eggs.kirakira) }
    }

    /** Egg 8: chain end + 0.5 s, the cat curled. */
    @Test
    fun egg8() {
        end(8, 900) { assertEquals("frozen", eggs.napSectionId) }
    }

    /** Egg 9: check + 1 s (HAPPY / B-DAY!, confetti gone); `row_hanko_birthday` (item 246). */
    @Test
    fun egg9() {
        val r = end(9, 2200 + 1000) { assertTrue(list.items.single { it.name == "Birthday cake" }.checked) }
        // Appendix C: a name with no variant suffix is recorded in Day and Glow.
        val cake = r.list.items.single { it.name == "Birthday cake" }
        val row = r.anchors.bounds(AnchorRegistry.rowKey(cake.id))!!
        val check = r.anchors.bounds(AnchorRegistry.checkKey(cake.id))!!
        // The row alone: from just above its sticker to just under its check spot (no neighbour's sticker or end cap).
        r.capture("row_hanko_birthday_${r.v}", Rect(row.left - 12 * px, row.top - 3 * px, check.right + 10 * px, maxOf(row.bottom, check.bottom) + 4 * px))
    }

    /**
     * Egg 10: stage start + 0.5 s; `list_bonus_stage_<v>` (item 247). Produce is opened first, so live rows (strawberries
     * and apples, still to get) show their stickers as 8-bit sprites in the open tray, clear of the "BONUS STAGE!" toast.
     */
    @Test
    fun egg10() {
        val r = rig()
        r.act { r.model.open("produce") }
        r.advance(700)
        val t0 = r.fire(10)
        r.advanceTo(t0 + 500)
        assertTrue(r.eggs.bonusStage)
        assertTrue(r.eggs.pixelStickers)
        val strawberries = compose.onNodeWithTag(PracticeTags.row("strawberries"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bar = r.anchor(AnchorRegistry.ADD_BAR)
        assertTrue("a live row on screen ($strawberries)", strawberries.top >= 0f && strawberries.bottom <= bar.top)
        r.capture("egg_10_end_${r.v}")
        r.capture("list_bonus_stage_${r.v}")
    }

    /** Egg 11: static (the next season's tape and date pictogram). */
    @Test
    fun egg11() {
        end(11, 600) { assertNotNull(eggs.season) }
    }

    /** Egg 12 (Golden, Day only): 20 min before sunset; `list_golden_day` (item 250). */
    @Test
    fun egg12() {
        if (!day) return
        val r = rig()
        r.fire(12)
        r.advance(400)
        assertEquals(ThemeVariant.Golden, r.sandbox.variants.variant.value)
        r.capture("egg_12_end_golden")
        r.capture("list_golden_day")
    }

    /** Egg 13: arrival + 1.2 s, JACK / POT! held. */
    @Test
    fun egg13() {
        end(13, 1200 + 1200) { assertNotNull(eggs.jackpot) }
    }

    /** Egg 14: the album's perfect-weeks card, under the practice list whose stamp page turns holo (as the lab shows it). */
    @Test
    fun egg14() {
        val r = EggRig(compose, variant = variant)
        r.start {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { PracticeList(r.model, feedback = r.feedback) }
                val album by r.sandbox.album.album.collectAsState(initial = null)
                PicnicBackground(Modifier.fillMaxWidth()) {
                    album?.let { a ->
                        PerfectWeeksCard(
                            a.perfectStreak,
                            earned = a.perfectStreak >= EggRules.PERFECT_WEEK_TRIPS,
                            holo = (a.perfectUntil ?: 0L) > r.clock.now(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
        r.fire(14)
        r.advance(600)
        assertTrue(r.eggs.perfectWeek)
        r.capture("egg_14_end_${r.v}")
    }

    /** Egg 15: hold + 0.5 s ("FED"). */
    @Test
    fun egg15() {
        end(15, 500) {
            assertTrue(eggs.pet)
            assertEquals("FED", eggs.lcdWord?.text)
        }
    }

    /** Egg 16: static (umbrella, raindrop tape). */
    @Test
    fun egg16() {
        // After the toast (4 s): the umbrella and the raindrop tape stay.
        end(16, 5000) { assertTrue(eggs.umbrella) }
    }

    /** Egg 17: appearance + 2.1 s, mid-march. */
    @Test
    fun egg17() {
        end(17, 2100) { assertNotNull(eggs.antsId) }
    }

    /** Egg 18: hold + 2.0 s, settled, from the lab's own action (which finishes the trip at 41 minutes, as §13.2's). */
    @Test
    fun egg18() {
        end(18, 2000) { assertEquals(41, eggs.receipt?.receipt?.minutes) }
    }

    /**
     * `receipt_printed_<v>` (item 273: the LCD stays light): the §13.2 example receipt, "TIME ......... 41 MIN", printed
     * from the completed fixture (its last check-off 41 minutes into the trip) and settled 2.0 s after the hold.
     */
    @Test
    fun receiptPrinted() {
        val r = EggRig(compose, variant = variant, fixture = completedFixture(41)).start()
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        r.advance(2_000)
        assertEquals(41, r.eggs.receipt?.receipt?.minutes)
        val lcd = r.anchors.bounds(AnchorRegistry.LCD)!!
        val paper = compose.onNodeWithTag(EggTags.RECEIPT).fetchSemanticsNode().boundsInRoot
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        r.capture("receipt_printed_${r.v}", Rect(0f, (lcd.top - 10 * px).coerceAtLeast(0f), width, paper.bottom + 24 * px))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = dayAndGlow()
    }
}

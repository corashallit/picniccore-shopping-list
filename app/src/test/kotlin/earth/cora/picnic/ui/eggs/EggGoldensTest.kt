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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
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

    /** The practice list before any egg (the lab's starting point). */
    @Test
    fun practiceList() {
        val r = rig()
        r.advance(400)
        r.capture("lab_practice_list_${r.v}")
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

    /** Egg 5 (Glow only, forced): list entry + 0.5 s, "SNACK?" showing; `list_midnight_glow` (item 242). */
    @Test
    fun egg5() {
        if (day) return
        val r = end(5, 500) {
            assertEquals(ThemeVariant.Glow, sandbox.variants.variant.value)
            assertEquals("SNACK?", eggs.lcdWord?.text)
        }
        // The snack star and the "snack?" doodle sit by the frozen aisle's tab: bring it on screen.
        val frozen = r.model.grouped().sections.indexOfFirst { it.id == "frozen" }
        compose.onNodeWithTag(PracticeTags.LIST).performScrollToIndex((frozen - 2).coerceAtLeast(0))
        r.advance(300)
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
        if (day) {
            val cake = r.list.items.single { it.name == "Birthday cake" }
            val row = r.anchors.bounds(AnchorRegistry.rowKey(cake.id))!!
            val check = r.anchors.bounds(AnchorRegistry.checkKey(cake.id))!!
            r.capture("row_hanko_birthday", Rect(row.left - 12 * px, row.top - 10 * px, check.right + 10 * px, check.bottom + 10 * px))
        }
    }

    /** Egg 10: stage start + 0.5 s; `list_bonus_stage_<v>` (item 247). */
    @Test
    fun egg10() {
        val r = end(10, 500) { assertTrue(eggs.bonusStage) }
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

    /** Egg 18: hold + 2.0 s, settled; `receipt_printed_<v>` (item 273: the LCD stays light). */
    @Test
    fun egg18() {
        val r = end(18, 2000) { assertNotNull(eggs.receipt) }
        val lcd = r.anchors.bounds(AnchorRegistry.LCD)!!
        r.capture("receipt_printed_${r.v}", Rect(lcd.right - 230 * px, lcd.top - 10 * px, lcd.right + 14 * px, lcd.bottom + 250 * px))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = dayAndGlow()
    }
}

package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.DpRect
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.updateList
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WP6's goldens (docs/DESIGN_STICKER.md §20, Appendix C; items 168, 170, 181, 186–189, 314): the Next card, the
 * celebration's summary (Day, over a Glow list, reduced motion, font scale 2.0, closing), its shelf for 1, 8 and 20 stops,
 * and the celebration strips. The scene always uses the Day tokens. Recorded with
 * `./gradlew :app:recordRoborazziDebug --tests '*MotionGoldensTest*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class MotionGoldensTest(private val variant: ThemeVariant) {
    @get:Rule
    val compose = createComposeRule()

    private val v get() = variant.suffix
    private val day get() = variant == ThemeVariant.Day
    private val px = 3f

    private fun rect(b: DpRect, pad: Float): Rect =
        Rect((b.left.value - pad) * px, (b.top.value - pad) * px, (b.right.value + pad) * px, (b.bottom.value + pad) * px)

    /** The card settled after Baking: [state]'s Baking powder completes the open aisle. */
    private fun nextCard(name: String, state: AppState, fontScale: Float? = null) {
        val rig = HarnessRig(compose, state, variant = variant, fontScale = fontScale).start()
        val t0 = rig.tap("baking-powder")
        rig.advanceTo(t0 + 620 + 1300 + 400 + 200)
        rig.capture(name, rect(rig.bounds(NEXT_CARD_SLOT_TAG), 8f))
    }

    /** Item 168. */
    @Test
    fun nextcard() = nextCard("nextcard_$v", MotionFixture.state())

    /** Item 170: "7 Pasta, rice & international" with its store chip. */
    @Test
    fun nextcardLongName() = nextCard("nextcard_long_name_$v", MotionFixture.pasta())

    /** Item 170 at font scale 2.0 (Day only). */
    @Test
    fun nextcardFs200() {
        if (day) nextCard("nextcard_fs200", MotionFixture.pasta(), fontScale = 2f)
    }

    /** Runs [state]'s last item to the celebration's start: returns the scene's t0. */
    private fun HarnessRig.toScene(last: String = "toothpaste"): Long {
        val t0 = tap(last)
        val s = t0 + 620 + Beats.CELEBRATION
        advanceTo(s + 16)
        return p.celebration!!.t0
    }

    private val lastOne = { MotionFixture.allBut(listOf("toothpaste"), open = "household") }

    /**
     * Items 186, 187: the summary over a Day list and over a Glow list (Appendix C names both `celebration_summary_glow`,
     * by its variant rule, and `celebration_summary_over_glow`, item 187; the scene is always Day, so they are one frame).
     */
    @Test
    fun celebrationSummary() {
        val rig = HarnessRig(compose, lastOne(), variant = variant).start()
        val s = rig.toScene()
        rig.advanceTo(s + 12600)
        rig.capture("celebration_summary_$v")
        if (!day) rig.capture("celebration_summary_over_glow")
    }

    /** Item 314: the summary kept open by a tap on its card: the bar is gone, Done sits on the card's edge (Day only). */
    @Test
    fun celebrationSummaryKept() {
        if (!day) return
        val rig = HarnessRig(compose, lastOne()).start()
        val s = rig.toScene()
        rig.advanceTo(s + 13000)
        compose.runOnIdle { rig.engine.keepCelebration() }
        rig.advance(300)
        rig.capture("celebration_summary_kept_day")
    }

    /** The pixel bee dancing on the scene's LCD with the banner (egg 15 hatched; Day only), cropped to the title beat. */
    @Test
    fun celebrationPetDance() {
        if (!day) return
        val state = lastOne().updateList(MotionFixture.LIST_ID) { it.copy(trip = it.trip!!.copy(pet = true)) }
        val rig = HarnessRig(compose, state).start()
        val s = rig.toScene()
        rig.advanceTo(s + 6230 + 700)
        rig.capture("celebration_pet_dance_day", Rect(0f, 0f, 360f * px, 140f * px))
    }

    /** The Next card for "Everything else": the capsule in the number circle (Day only). */
    @Test
    fun nextcardEverythingElse() {
        if (day) nextCard("nextcard_everything_else_day", MotionFixture.everythingElseNext())
    }

    /** Item 314: the summary with its washi progress bar part filled (Day, over Glow). */
    @Test
    fun celebrationSummaryClosing() {
        val rig = HarnessRig(compose, lastOne(), variant = variant).start()
        val s = rig.toScene()
        rig.advanceTo(s + 12150 + 2900)
        rig.capture("celebration_summary_closing_$v")
    }

    /** Item 188: reduced motion (Day only). */
    @Test
    fun celebrationSummaryRm() {
        if (!day) return
        val rig = HarnessRig(compose, lastOne(), reduced = true).start()
        val s = rig.toScene()
        rig.advanceTo(s + 3000)
        rig.capture("celebration_summary_rm")
    }

    /** The summary at font scale 2.0 (Day only): nothing clips. */
    @Test
    fun celebrationSummaryFs200() {
        if (!day) return
        val rig = HarnessRig(compose, lastOne(), fontScale = 2f).start()
        val s = rig.toScene()
        rig.advanceTo(s + 12600)
        rig.capture("celebration_summary_fs200")
    }

    /** Item 189: the shelf and the stamp card for 1, 8 and 20 stops (at the summary, top of the scene). */
    @Test
    fun celebrationShelf1() = shelf(1)

    @Test
    fun celebrationShelf8() = shelf(8)

    @Test
    fun celebrationShelf20() = shelf(20)

    private fun shelf(stops: Int) {
        val state = MotionFixture.many(stops * 2, stops)
        val rig = HarnessRig(compose, state, variant = variant).start()
        val s = rig.toScene(last = "i${stops * 2 - 1}")
        rig.advanceTo(s + 15000)
        rig.capture("celebration_shelf_${stops}_$v", Rect(0f, 0f, 360f * px, 380f * px))
    }

    /** Item 181: the storyboard at 0.1 … 15.0 s (all of it in Day; the frames that show the list under it in Glow). */
    @Test
    fun celebrationStrip() {
        val rig = HarnessRig(compose, lastOne(), variant = variant).start()
        // Toothpaste's row on screen, so the heart iris opens from the checkbox she tapped.
        compose.onNodeWithTag(HarnessTags.LIST).performScrollToIndex(rig.model.grouped().sections.indexOfFirst { it.id == "household" })
        rig.advance(300)
        val s = rig.toScene()
        // The item's whole time list in both variants (Appendix C): the scene is Day over either list.
        val times = listOf(100L, 700L, 2400L, 3300L, 4600L, 5000L, 6600L, 9500L, 11500L, 12200L, 15000L)
        rig.strip("celebration_strip_$v", s, times)
    }

    /** Item 189: a 100-item trip rides 24 stickers, `celebration_ride_100_items_<variant>_<t>`. */
    @Test
    fun celebrationRide100() {
        val rig = HarnessRig(compose, MotionFixture.many(100, 8), variant = variant).start()
        val s = rig.toScene(last = "i99")
        rig.strip("celebration_ride_100_items_$v", s, listOf(850L, 1100L, 1350L, 1600L, 1850L, 2100L, 2450L)) { Rect(0f, 0f, 360f * px, 600f * px) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = listOf(ThemeVariant.Day, ThemeVariant.Glow).map { arrayOf<Any>(it) }
    }
}

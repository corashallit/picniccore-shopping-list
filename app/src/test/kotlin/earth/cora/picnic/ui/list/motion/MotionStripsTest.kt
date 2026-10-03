package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.DpRect
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WP6's frame-strip goldens (docs/DESIGN_STICKER.md §20 "strip", Appendix C): each choreography captured at fixed
 * times on the Compose test clock, one PNG per frame, `<name>_<variant>_<t>.png` (t in ms from the strip's moment),
 * in Day and Glow. Recorded with `./gradlew :app:recordRoborazziDebug --tests '*MotionStripsTest*'`.
 *
 * PROVISIONAL (see [ProvisionalGoldens]): these strips play on [MotionHarness], a test-only stand-in for WP5's list.
 * Only WP6's own pieces in them are real (the flights, MotionBasket, StampCircle, the book twin, the curl, the ribbon);
 * the tabs, rows, book and add bar around them are hand-drawn stand-ins. They are re-recorded on WP5's ListScreen
 * with the real wiring in part 3, and items 125–127, 148, 150 and 177 are not done until then.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class MotionStripsTest(private val variant: ThemeVariant) {
    @get:Rule
    val compose = createComposeRule()

    private val v get() = variant.suffix
    private val px = 3f

    private fun rect(b: DpRect, padLeft: Float = 0f, padTop: Float = 0f, padRight: Float = 0f, padBottom: Float = 0f): Rect =
        Rect((b.left.value - padLeft) * px, (b.top.value - padTop) * px, (b.right.value + padRight) * px, (b.bottom.value + padBottom) * px)

    private fun open(section: String): AppState = MotionFixture.state().setOpenAisle(MotionFixture.LIST_ID, section)

    /** The open tray from its header (with room above for the hop's arc) down to [item]'s row. */
    private fun HarnessRig.trayCrop(section: String, item: String): Rect {
        val tray = bounds(HarnessTags.tray(section))
        val row = bounds(HarnessTags.row(item))
        return rect(DpRect(tray.left, tray.top, tray.right, row.bottom), padTop = 54f, padBottom = 6f, padLeft = 4f, padRight = 4f)
    }

    /** Item 125: the five personality hops (360 ms), from the hop's start to the landing. */
    @Test
    fun hops() {
        val hops = listOf(
            Triple("hop_berry", "produce", "strawberries"),
            Triple("hop_egg", "dairy-eggs", "eggs"),
            Triple("hop_bread", "bakery", "baguette"),
            Triple("hop_can", "household", "cat-food"),
            Triple("hop_frozen", "frozen", "peas"),
        )
        val rig = HarnessRig(compose, open(hops.first().second), variant = variant).start()
        for ((name, section, item) in hops) {
            compose.runOnIdle { rig.model.open(section) }
            rig.frame()
            val index = rig.model.grouped().sections.indexOfFirst { it.id == section }
            compose.onNodeWithTag(HarnessTags.LIST).performScrollToIndex(index)
            rig.advance(400)
            val crop = rig.trayCrop(section, item)
            val t0 = rig.tap(item)
            rig.strip("${name}_$v", t0 + Beats.HOP, listOf(0L, 60L, 120L, 180L, 240L, 360L)) { crop }
            rig.advanceTo(t0 + 1500)
        }
    }

    /** Item 126: the landing's 2-frame squash, the 4-step wobble and the rim heart. */
    @Test
    fun landing() {
        val rig = HarnessRig(compose, open("produce"), variant = variant).start()
        val crop = rect(rig.bounds(HarnessTags.tray("produce")), padTop = 30f).let { Rect(it.right - 140f * px, it.top, it.right, it.top + 120f * px) }
        val t0 = rig.tap("apples")
        rig.strip("landing_strip_$v", t0 + 620, listOf(0L, 80L, 160L, 240L, 320L, 400L, 520L, 640L)) { crop }
    }

    /** Item 127: the un-check: hanko lift, the hop back, the ghost until it lands, the badge at once. */
    @Test
    fun uncheck() {
        val rig = HarnessRig(compose, open("produce"), variant = variant).start()
        val crop = rig.trayCrop("produce", "apples")
        val t0 = rig.tap("apples")
        rig.advanceTo(t0 + 1500)
        val u0 = rig.tap("apples")
        rig.strip("uncheck_strip_$v", u0, listOf(0L, 80L, 160L, 250L, 340L, 420L)) { crop }
    }

    /** Item 148: the book's magic appear (0, +64, +144, +224, +320) with the bloom, the sparks and the page lift. */
    @Test
    fun bookMagic() {
        val rig = HarnessRig(compose, open("produce"), variant = variant).start()
        val crop = rect(rig.bounds(HarnessTags.BOOK), padTop = 8f, padBottom = 8f, padLeft = 4f, padRight = 4f)
        val t0 = rig.tap("apples")
        rig.strip("book_magic_strip_$v", t0 + 940, listOf(0L, 64L, 144L, 224L, 320L, 420L)) { crop }
    }

    /** Item 150: the twin peels off in place (lift, 1.12, .55 with inward sparks, gone at 320). */
    @Test
    fun bookPeel() {
        val rig = HarnessRig(compose, open("produce"), variant = variant).start()
        val crop = rect(rig.bounds(HarnessTags.BOOK), padTop = 8f, padBottom = 8f, padLeft = 4f, padRight = 4f)
        val t0 = rig.tap("apples")
        rig.advanceTo(t0 + 1500)
        val u0 = rig.tap("apples")
        rig.strip("book_peel_strip_$v", u0, listOf(0L, 64L, 144L, 224L, 320L)) { crop }
    }

    /** Item 177: the aisle-done chain from the landing: lid, GOT IT, stamp, curl, fold, ribbon, the Next card. */
    @Test
    fun aisleDone() {
        val rig = HarnessRig(compose, MotionFixture.state(), variant = variant).start()
        val t0 = rig.tap("baking-powder")
        rig.strip("aisle_done_strip_$v", t0 + 620, listOf(300L, 500L, 800L, 950L, 1100L, 1250L, 1400L, 1800L))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = listOf(ThemeVariant.Day, ThemeVariant.Glow).map { arrayOf<Any>(it) }
    }
}

/**
 * The WP6 goldens recorded on the [MotionHarness] stand-in rather than WP5's real list (Appendix C names that must show
 * the real list): provisional until part 3 re-records them on ListScreen. GoldenCoverageTest and the lead's review can
 * read this list; the celebration strip's frames before the scene covers the list (0.1 s) show the harness list too.
 */
object ProvisionalGoldens {
    val prefixes: List<String> = listOf(
        "hop_berry_", "hop_egg_", "hop_bread_", "hop_can_", "hop_frozen_",
        "landing_strip_", "uncheck_strip_", "book_magic_strip_", "book_peel_strip_", "aisle_done_strip_",
        "celebration_strip_day_100", "celebration_strip_glow_100",
    )

    /** The checklist items that rest on them and stay open until the re-recording. */
    val items: List<Int> = listOf(125, 126, 127, 148, 150, 177)

    fun isProvisional(name: String): Boolean = prefixes.any { name.startsWith(it) }
}

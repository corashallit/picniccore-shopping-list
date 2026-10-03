package earth.cora.picnic.ui.list

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.PHONE_411
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The list's other states (docs/DESIGN_STICKER.md §6.1, Appendix C "State goldens"; items 93, 94, 97, 118, 281, 304):
 * an aisle done, rows mid-swipe, drawing and shiny rows, reduced motion, the 411 dp phone, large text, the pinned plate
 * mid-scroll and under Dusk and Golden, and the tear-off strip.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListStateGoldensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun section(ui: ListUiState, id: String) = ui.sections.first { it.id == id }

    /** Baking powder checked: Baking is done (lid, GOT IT, ribbon, stamp 4) and Breakfast is open, the next stop. */
    private fun aisleDone() = ListFixture.ui(
        ListFixture.state().toggleChecked(ListFixture.LIST_ID, "baking-powder", ListFixture.NOW).setOpenAisle(ListFixture.LIST_ID, "breakfast"),
        now = ListFixture.NOW + 60_000,
    )

    @Test
    fun aisleDoneDay() = compose.recordScreen("list_aisle_done_day") { ListScreen(aisleDone(), ListActions()) }

    @Test
    fun aisleDoneGlow() = compose.recordScreen("list_aisle_done_glow", ThemeVariant.Glow) { ListScreen(aisleDone(), ListActions()) }

    @Test
    fun swipeRightDay() = swipe("row_swipe_right_day", ThemeVariant.Day, right = true)

    @Test
    fun swipeRightGlow() = swipe("row_swipe_right_glow", ThemeVariant.Glow, right = true)

    @Test
    fun swipeLeftDay() = swipe("row_swipe_left_day", ThemeVariant.Day, right = false)

    @Test
    fun swipeLeftGlow() = swipe("row_swipe_left_glow", ThemeVariant.Glow, right = false)

    /** Baking powder held mid-swipe (48 dp, short of the 56 dp threshold). */
    private fun swipe(name: String, variant: ThemeVariant, right: Boolean) = compose.recordPiece(name, variant, pad = 16.dp, before = {
        onNodeWithTag(ListTags.row("baking-powder")).performTouchInput {
            val dx = 48f * density * (if (right) 1f else -1f)
            down(center)
            moveBy(Offset(dx / 3f, 0f))
            moveBy(Offset(dx / 3f, 0f))
            moveBy(Offset(dx / 3f, 0f))
        }
    }) { TrayOf(section(ListFixture.ui(), "aisle-2-baking")) }

    @Test
    fun rowDrawingDay() = rowOf("row_drawing_day", ThemeVariant.Day, "matcha")

    @Test
    fun rowDrawingGlow() = rowOf("row_drawing_glow", ThemeVariant.Glow, "matcha")

    @Test
    fun rowShinyDay() = rowOf("row_shiny_day", ThemeVariant.Day, "maple")

    @Test
    fun rowShinyGlow() = rowOf("row_shiny_glow", ThemeVariant.Glow, "maple")

    private fun rowOf(name: String, variant: ThemeVariant, id: String) = compose.recordPiece(name, variant, pad = 16.dp) {
        val s = section(ListFixture.ui(ListFixture.rowStates(), busy = ListFixture.DRAWING), "breakfast")
        TrayOf(s.copy(rows = s.rows.filter { it.id == id }))
    }

    @Test
    fun reducedMotion() = compose.recordScreen("list_reduced_motion_day", reducedMotion = true) { ListScreen(ListFixture.ui(), ListActions()) }

    @Test
    @Config(qualifiers = PHONE_411)
    fun midtripW411() = compose.recordScreen("list_midtrip_w411") { ListScreen(ListFixture.ui(), ListActions()) }

    @Test
    fun fontScale130() = compose.recordScreen("list_fs130", fontScale = 1.3f) { ListScreen(ListFixture.ui(), ListActions()) }

    @Test
    fun fontScale200() = compose.recordScreen("list_fs200", fontScale = 2f) { ListScreen(ListFixture.ui(), ListActions()) }

    /** Item 94: Produce open (5 rows) at font scale 2.0, still one tray. */
    @Test
    fun trayOpenFs200() = compose.recordScreen("list_tray_open_fs200", fontScale = 2f, before = {
        onNodeWithTag(ListTags.LIST).performScrollToKey("header:produce")
    }) { ListScreen(ListFixture.ui(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce")), ListActions()) }

    /** Item 93: Household's plate pinned while its rows scroll under it. */
    @Test
    fun pinnedMidScroll() = pinned("list_pinned_mid_scroll", ThemeVariant.Day, rows = 3.5f)

    @Test
    fun pinnedUnderBookDusk() = pinned("list_pinned_under_book_dusk", ThemeVariant.Dusk)

    @Test
    fun pinnedUnderBookGolden() = pinned("list_pinned_under_book_golden", ThemeVariant.Golden)

    private fun pinned(name: String, variant: ThemeVariant, rows: Float = 1.5f) {
        val s = ListScreenState()
        val l = LazyListState()
        compose.recordScreen(name, variant, before = {
            onNodeWithTag(ListTags.LIST).performScrollToKey("header:household")
            waitForIdle()
            runOnIdle {
                s.scrolledDown = true
                runBlocking { l.scrollBy(rows * 56f * density.density) }
            }
            waitForIdle()
        }) { ListScreen(ListFixture.ui(ListFixture.longAisle()), ListActions(), screen = remember { s }, listState = remember { l }) }
    }

    /** Item 118: the tear-off after release (perforation 120 ms, halves 600 ms), frame by frame, Day and Glow. */
    /** §6.1's "tear-off in progress with the Undo toast": the slot closing, the note "McIntosh apples torn out" + Undo. */
    @Test
    fun tearOffToastDay() = tearOffToast(ThemeVariant.Day)

    @Test
    fun tearOffToastGlow() = tearOffToast(ThemeVariant.Glow)

    private fun tearOffToast(variant: ThemeVariant) {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce")).inSeason()
        compose.recordScreen("list_tearoff_toast_${variant.name.lowercase()}", variant, settleMillis = 0, before = {
            mainClock.autoAdvance = false
            onNodeWithTag(ListTags.row("apples")).performTouchInput {
                down(center)
                repeat(4) { moveBy(Offset(-20f * density, 0f)) }
                up()
            }
            // The halves have fallen (720 ms), the row is out and its slot closing, the note has dropped in.
            frames(((TEAR_TOTAL + 260) / 16).toInt())
        }) { rig.Content() }
    }

    @Test
    fun tearOffStripDay() = tearOff(ThemeVariant.Day)

    @Test
    fun tearOffStripGlow() = tearOff(ThemeVariant.Glow)

    private fun tearOff(variant: ThemeVariant) {
        val v = variant.name.lowercase()
        compose.recordPiece("tearoff_strip_${v}_0", variant, pad = 16.dp, settleMillis = 0, before = {
            mainClock.autoAdvance = false
            onNodeWithTag(ListTags.row("baking-powder")).performTouchInput {
                down(center)
                repeat(6) { moveBy(Offset(-20f * density, 0f)) }
                up()
            }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            mainClock.advanceTimeByFrame()
        }) { TrayOf(section(ListFixture.ui(), "aisle-2-baking")) }
        var t = 16L
        for (at in listOf(60L, 120L, 300L, 480L, 700L)) {
            compose.mainClock.advanceTimeBy(at - t)
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
            t = at + 16
            compose.onNodeWithTag(PIECE_TAG).captureRoboImage("src/test/screenshots/tearoff_strip_${v}_$at.png")
        }
    }
}


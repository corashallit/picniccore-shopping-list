package earth.cora.picnic.ui.list

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.markFinaleShown
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.PHONE_411
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import earth.cora.picnic.ui.theme.TOAST_TAG
import org.junit.Assert.assertTrue
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

    /**
     * §0.4 Q9, Appendix C (items 304, 315): the first check-off 13 h after the trip's last activity starts a new sticker
     * book by itself; the note "New sticker book for today ✦ your last shop is in the album" with Undo, held still about
     * 600 ms after its 220 ms drop.
     */
    @Test
    fun newBookToastDay() = newBookToast(ThemeVariant.Day)

    @Test
    fun newBookToastGlow() = newBookToast(ThemeVariant.Glow)

    private fun newBookToast(variant: ThemeVariant) {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR).inSeason()
        compose.recordScreen("toast_new_book_undo_${variant.name.lowercase()}", variant, settleMillis = 0, before = {
            mainClock.autoAdvance = false
            onNodeWithTag(ListTags.row("baking-powder")).performClick()
            assertTrue("the note dropped in", framesUntil(3000) { exists(TOAST_TAG) })
            advanceMs(220 + 600)
        }) { rig.Content() }
    }

    /**
     * §6.1, §4.15 (item 120) and the trip review: the nudge "New shop? Start a fresh sticker book" with its "New trip"
     * chip, an offer with no undo arrow on it, held still after its drop.
     */
    @Test
    fun nudgeToastDay() = nudgeToast(ThemeVariant.Day)

    @Test
    fun nudgeToastGlow() = nudgeToast(ThemeVariant.Glow)

    private fun nudgeToast(variant: ThemeVariant) {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR).inSeason()
        compose.recordScreen("list_nudge_toast_${variant.name.lowercase()}", variant, settleMillis = 0, frozen = true, before = {
            assertTrue("the nudge dropped in", framesUntil(3000) { exists(TOAST_TAG) })
            advanceMs(220 + 600)
        }) { rig.Content() }
    }

    /**
     * §7.3 (item 146) and the trip review: after the finale of a 25-sticker trip the book shows its full first sheet,
     * PER-FECT! pressed clear of every sticker and stamp, and the page number "1/2" inside the page, 6 dp from its bottom
     * and its fore-edge.
     */
    @Test
    fun afterFinaleDay() = afterFinale(ThemeVariant.Day)

    @Test
    fun afterFinaleGlow() = afterFinale(ThemeVariant.Glow)

    private fun afterFinale(variant: ThemeVariant) {
        var s = ListFixture.state()
        val towels = earth.cora.picnic.core.model.GroceryItem(
            id = "paper-towels", name = "Paper towels", quantity = "6 rolls", aisleId = "household", icon = "paper_towels",
            iconFit = earth.cora.picnic.core.model.IconFit.EXACT, addedAt = ListFixture.TRIP_START + ListFixture.MIN,
        )
        s = s.addItems(ListFixture.LIST_ID, listOf(towels), ListFixture.TRIP_START + ListFixture.MIN).first
        s.list(ListFixture.LIST_ID)!!.items.filter { !it.checked }.forEachIndexed { i, item ->
            s = s.toggleChecked(ListFixture.LIST_ID, item.id, ListFixture.TRIP_START + (20 + i) * ListFixture.MIN)
        }
        s = s.markFinaleShown(ListFixture.LIST_ID, ListFixture.TRIP_START + 42 * ListFixture.MIN).setOpenAisle(ListFixture.LIST_ID, "")
        val rig = ListRig(s, now = ListFixture.TRIP_START + 44 * ListFixture.MIN).inSeason()
        compose.recordScreen("list_after_finale_${variant.name.lowercase()}", variant, before = {
            waitForIdle()
            val dp = density.density
            val book = boundsOf(earth.cora.picnic.ui.theme.BookTags.BOOK)
            val perfect = boundsOf(earth.cora.picnic.ui.theme.BookTags.PERFECT, unmerged = true)
            // The first sheet: its 22 twins (the page number keeps two cells), none under PER-FECT!.
            val twins = (0 until 22).map { boundsOf(earth.cora.picnic.ui.theme.BookTags.twin(it), unmerged = true) }
            twins.forEachIndexed { i, t ->
                val art = t.deflate(2 * dp)
                assertTrue("PERFECT $perfect clear of sticker $i $art", !(perfect.left < art.right && art.left < perfect.right && perfect.top < art.bottom && art.top < perfect.bottom))
            }
            assertTrue("the first sheet shows", !exists(earth.cora.picnic.ui.theme.BookTags.twin(24), unmerged = true))
            val lcd = boundsOf(earth.cora.picnic.ui.theme.BookTags.PAGE_LCD, unmerged = true)
            assertTrue("the page number inside the book ($lcd in $book)", lcd.bottom <= book.bottom - 6 * dp && lcd.right <= book.right - 6 * dp)
            twins.forEachIndexed { i, t ->
                val art = t.deflate(2 * dp)
                assertTrue("the page number $lcd clear of sticker $i $art", !(lcd.left < art.right && art.left < lcd.right && lcd.top < art.bottom && art.top < lcd.bottom))
            }
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


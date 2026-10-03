package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.list.motion.BookPresentation
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.theme.BookDateCaption
import earth.cora.picnic.ui.theme.StickerBook
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The trip sticker book and its sheets (docs/DESIGN_STICKER.md §6.5, §6.6, §7.3–§7.5, Appendix C; items 76, 146, 160,
 * 161, 163): the book at the phone's 336 dp in each state (Day unless the name gives the variant), the Trip book sheet
 * and the New trip sheet.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListBookGoldensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun book(name: String, ui: ListUiState, variant: ThemeVariant = ThemeVariant.Day, fontScale: Float? = null, collapsed: Boolean = false) =
        compose.recordPiece(name, variant, fontScale = fontScale, pad = 12.dp) {
            val b = ui.book!!
            val clock = remember { MotionClock() }
            val dateOnPage = LocalDensity.current.fontScale <= 1.3f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!dateOnPage) BookDateCaption(b.dateLabel)
                StickerBook(b, BookPresentation.Identity, clock, width = 336.dp, collapsed = collapsed, dateOnPage = dateOnPage)
            }
        }

    @Test
    fun bookNew() = book("book_new", ListFixture.ui(ListFixture.fresh()))

    @Test
    fun bookMidtrip() = book("book_midtrip", ListFixture.ui())

    /** 32 items over 8 stops, 25 checked: 24 spots a page, the lowest free spot on page 2 of 2. */
    @Test
    fun bookPage2() = book("book_page2", ListFixture.ui(ListFixture.stops(8, 4, 25)))

    @Test
    fun bookFull() = book("book_full", ListFixture.ui(ListFixture.allBut(emptyList()), now = ListFixture.TRIP_START + 42 * ListFixture.MIN))

    @Test
    fun bookPerfectReceipt() = book("book_perfect_receipt", ListFixture.ui(ListFixture.doneWithReceipt(), now = ListFixture.TRIP_START + 44 * ListFixture.MIN))

    @Test
    fun bookGlow() = book("book_glow", ListFixture.ui(), ThemeVariant.Glow)

    @Test
    fun bookDusk() = book("book_dusk", ListFixture.ui(), ThemeVariant.Dusk)

    @Test
    fun bookGolden() = book("book_golden", ListFixture.ui(), ThemeVariant.Golden)

    @Test
    fun bookCollapsed() = book("book_collapsed", ListFixture.ui(), collapsed = true)

    @Test
    fun bookStops14() = book("book_stops14", ListFixture.ui(ListFixture.stops(14, 2, 9)))

    @Test
    fun bookStops20() = book("book_stops20", ListFixture.ui(ListFixture.stops(20, 1, 7)))

    @Test
    fun bookFs200() = book("book_fs200", ListFixture.ui(), fontScale = 2f)

    @Test
    fun bookBonusStage() = book("book_bonus_stage", ListFixture.ui(bonusStage = true))

    @Test
    fun bookDefaultAislesDay() = book("book_default_aisles_day", ListFixture.ui(ListFixture.everyDefaultAisle()))

    @Test
    fun bookDefaultAislesGlow() = book("book_default_aisles_glow", ListFixture.ui(ListFixture.everyDefaultAisle()), ThemeVariant.Glow)

    /** An empty list: the top stack has no book (§4.21, §7.4). */
    @Test
    fun bookEmptyListHidden() = compose.recordScreen("book_empty_list_hidden") { ListScreen(ListFixture.ui(ListFixture.empty()), ListActions()) }

    // ---- The Trip book sheet (§6.6, item 163) ----

    @Test
    fun tripBookCurrent() = compose.recordScreen("tripbook_sheet_current") {
        ListScreen(ListFixture.ui(), ListActions(), screen = remember { ListScreenState(tripBookOpen = true) })
    }

    @Test
    fun tripBookFinished() = compose.recordScreen("tripbook_sheet_finished") {
        ListScreen(ListFixture.ui(ListFixture.doneWithReceipt(), now = ListFixture.TRIP_START + 44 * ListFixture.MIN), ListActions(), screen = remember { ListScreenState(tripBookOpen = true) })
    }

    @Test
    fun tripBookGlow() = compose.recordScreen("tripbook_sheet_glow", ThemeVariant.Glow) {
        ListScreen(ListFixture.ui(), ListActions(), screen = remember { ListScreenState(tripBookOpen = true) })
    }

    // ---- The New trip sheet (§6.5) ----

    @Test
    fun newTripDay() = compose.recordScreen("newtrip_sheet_day") {
        ListScreen(ListFixture.ui(), ListActions(), screen = remember { ListScreenState(newTripOpen = true) })
    }

    @Test
    fun newTripGlow() = compose.recordScreen("newtrip_sheet_glow", ThemeVariant.Glow) {
        ListScreen(ListFixture.ui(), ListActions(), screen = remember { ListScreenState(newTripOpen = true) })
    }
}

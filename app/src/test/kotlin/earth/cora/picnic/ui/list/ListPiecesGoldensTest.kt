package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.BasketMini
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.list.motion.MotionFixture
import earth.cora.picnic.ui.theme.AisleBasket
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.BasketSize
import earth.cora.picnic.ui.theme.BasketVisual
import earth.cora.picnic.ui.theme.IndexTab
import earth.cora.picnic.ui.theme.TabSize
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The list's pieces (docs/DESIGN_STICKER.md §4.10, §4.11, §5, Appendix C; items 75, 76, 78–82, 84–86, 99, 100,
 * 105–108, 110, 112–114): tabs, folded strips, the open tray, rows and the 17 baskets, Day and Glow.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListPiecesGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    private fun name(base: String) = goldenName(base, variant)

    private val midtrip by lazy { ListFixture.ui() }
    private val everythingElse by lazy { ListFixture.ui(MotionFixture.everythingElseNext()).sections.first { it.isEverythingElse } }

    private fun section(ui: ListUiState, id: String) = ui.sections.first { it.id == id }

    // ---- Tabs (item 75) ----

    @Test
    fun tabTray() = compose.recordPiece(name("tab_tray"), variant) { Tabs(TabSize.Tray, ballpoint = true) }

    @Test
    fun tabStrip() = compose.recordPiece(name("tab_strip"), variant) { Tabs(TabSize.Strip, ballpoint = false) }

    @Composable
    private fun Tabs(size: TabSize, ballpoint: Boolean) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in listOf(everythingElse) + midtrip.sections) {
                IndexTab(s.position, s.displayName, s.look, size, ballpoint)
            }
        }
    }

    // ---- The default 14 aisles (item 76) ----

    @Test
    @Config(qualifiers = "w360dp-h1100dp-xxhdpi")
    fun stripsDefaultAisles() = compose.recordPiece(name("strips_default_aisles"), variant) {
        StripsOf(ListFixture.ui(ListFixture.everyDefaultAisle()).sections)
    }

    @Test
    @Config(qualifiers = "w360dp-h1100dp-xxhdpi")
    fun trayDefaultAisles() = compose.recordPiece(name("tray_default_aisles"), variant, pad = 14.dp) {
        val clock = remember { MotionClock() }
        val anchors = remember { AnchorRegistry() }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in ListFixture.ui(ListFixture.everyDefaultAisle()).sections) {
                TrayHeader(s, null, clock, anchors, pinned = false, chevronFocus = remember(s.id) { FocusRequester() }, onFold = {}, current = false)
            }
        }
    }

    // ---- Strips (items 78, 79, 82) ----

    @Test
    fun stripStates() = compose.recordPiece(name("strip_states"), variant) {
        StripsOf(midtrip.sections.filter { it.id != "aisle-2-baking" })
    }

    @Test
    fun stripDone() = compose.recordPiece(name("strip_done"), variant) {
        val ui = ListFixture.ui(ListFixture.everyDefaultAisle().toggleChecked(ListFixture.LIST_ID, "d6", ListFixture.NOW).toggleChecked(ListFixture.LIST_ID, "d10", ListFixture.NOW))
        StripsOf(ui.sections.filter { it.complete })
    }

    @Test
    fun stripEverythingElse() = compose.recordPiece(name("strip_everything_else"), variant) { StripsOf(listOf(everythingElse)) }

    @Test
    fun stripEverythingElseDone() = compose.recordPiece(name("strip_everything_else_done"), variant) {
        val ui = ListFixture.ui(MotionFixture.everythingElseNext().toggleChecked(ListFixture.LIST_ID, "napkins", ListFixture.NOW))
        StripsOf(ui.sections.filter { it.isEverythingElse })
    }

    // ---- The open tray and rows (items 80, 81, 84–86) ----

    @Test
    fun trayCurrent() = compose.recordPiece(name("tray_current"), variant, pad = 16.dp) { TrayOf(section(midtrip, "aisle-2-baking")) }

    @Test
    fun rowStates() = compose.recordPiece(name("row_states"), variant, pad = 16.dp) {
        TrayOf(section(ListFixture.ui(ListFixture.rowStates(), busy = ListFixture.DRAWING), "breakfast"))
    }

    /**
     * §4.7, §4.11 at large text (item 337): a jackpot row with a long detail, a shiny honey and the shiny maple syrup;
     * each chip follows its detail and drops under it when the two cannot share a line, the detail keeping its lines.
     */
    @Test
    @Config(qualifiers = "w360dp-h1400dp-xxhdpi")
    fun rowJackpotFs130() = compose.recordPiece(name("row_jackpot_fs130"), variant, fontScale = 1.3f, pad = 16.dp) { TrayOf(rarityRows()) }

    @Test
    @Config(qualifiers = "w360dp-h1400dp-xxhdpi")
    fun rowShinyFs200() = compose.recordPiece(name("row_shiny_fs200"), variant, fontScale = 2f, pad = 16.dp) { TrayOf(rarityRows()) }

    @Test
    fun rowPressed() = compose.recordPiece(goldenName("row_pressed", variant), variant, pad = 16.dp, before = {
        onNodeWithTag(ListTags.row("baking-powder")).performTouchInput { down(center) }
    }) {
        val s = section(midtrip, "aisle-2-baking")
        TrayOf(s.copy(rows = s.rows.takeLast(1)))
    }

    @Test
    fun rowChecked() = compose.recordPiece(goldenName("row_checked", variant), variant, pad = 16.dp) {
        val s = section(midtrip, "aisle-2-baking")
        TrayOf(s.copy(rows = s.rows.take(1)))
    }

    // ---- Baskets (items 99, 100, 105–108, 110, 112, 113) ----

    private fun minis(n: Int, aisle: Int = 0, holo: Int = -1): List<BasketMini> {
        val keys = listOf("strawberry", "banana", "carrot", "broccoli", "apple", "croissant", "baguette", "flour", "coffee", "milk_bag")
        return (0 until n).map { BasketMini("m$it", IconRef(keys[it % keys.size]), holo = it == holo, aisleIndex = aisle) }
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp-xxhdpi")
    fun basketsAllStates() = compose.recordPiece(name("baskets_all_states"), variant, pad = 10.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasketKind.entries.forEachIndexed { i, kind ->
                val look = AisleLooks.of(i + 1)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    AisleBasket(kind, emptyList(), toGet = 3, state = BasketVisual(), look = look)
                    AisleBasket(kind, minis(3, i), toGet = 2, state = BasketVisual(), look = look)
                    AisleBasket(kind, minis(3, i), toGet = 0, state = BasketVisual(complete = true), look = look)
                }
            }
        }
    }

    @Test
    fun basketIceBucketEmpty() = emptyCard(BasketKind.ICE_BUCKET, "basket_ice_bucket_empty")

    @Test
    fun basketFlowerBucketEmpty() = emptyCard(BasketKind.FLOWER_BUCKET, "basket_flower_bucket_empty")

    @Test
    fun basketBottleCarrierEmpty() = emptyCard(BasketKind.BOTTLE_CARRIER, "basket_bottle_carrier_empty")

    @Test
    fun basketCandyJarEmpty() = emptyCard(BasketKind.CANDY_JAR, "basket_candy_jar_empty")

    private fun emptyCard(kind: BasketKind, base: String) = compose.recordPiece(name(base), variant) {
        AisleBasket(kind, emptyList(), toGet = 2, state = BasketVisual(), size = BasketSize.Card, look = AisleLooks.of(kind.ordinal % 10 + 1))
    }

    @Test
    fun basketEmptyLandingSpot() = compose.recordPiece(name("basket_empty_landing_spot"), variant) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AisleBasket(BasketKind.WICKER_PICNIC, emptyList(), toGet = 5, state = BasketVisual(), size = BasketSize.Card, look = AisleLooks.of(1))
            AisleBasket(BasketKind.BREAD_BASKET, emptyList(), toGet = 3, state = BasketVisual(), size = BasketSize.Card, look = AisleLooks.of(2))
        }
    }

    @Test
    fun basketFill1to7() = compose.recordPiece(name("basket_fill_1_to_7"), variant) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (n in 1..4) AisleBasket(BasketKind.WICKER_PICNIC, minis(n), toGet = 1, state = BasketVisual(), look = AisleLooks.of(1)) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (n in 5..7) AisleBasket(BasketKind.WICKER_PICNIC, minis(n), toGet = 1, state = BasketVisual(), look = AisleLooks.of(1)) }
        }
    }

    @Test
    fun basketBadgeStates() = compose.recordPiece(name("basket_badge_states"), variant) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (left in listOf(0, 1, 5, 12)) AisleBasket(BasketKind.BREAD_BASKET, minis(2), toGet = left, state = BasketVisual(), look = AisleLooks.of(2))
        }
    }

    @Test
    fun basketDoneLidHanko() = compose.recordPiece(name("basket_done_lid_hanko"), variant) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((i, kind) in listOf(BasketKind.WICKER_PICNIC, BasketKind.MIXING_BOWL).withIndex()) {
                AisleBasket(kind, minis(4, i), toGet = 0, state = BasketVisual(complete = true), size = BasketSize.Card, look = AisleLooks.of(i * 3 + 1))
            }
        }
    }

    @Test
    fun basketShinyMini() = compose.recordPiece(name("basket_shiny_mini"), variant) {
        // The shiny one in plain view: alone and upright in the wicker basket, and as the newest of three on top.
        // Stickers whose main colour is a big field, where the foil shows: an apple alone, and a milk bag as the newest of three.
        fun mini(id: String, key: String, holo: Boolean = false) = BasketMini(id, IconRef(key), holo = holo)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AisleBasket(BasketKind.WICKER_PICNIC, listOf(mini("a", "apple", holo = true)), toGet = 1, state = BasketVisual(), size = BasketSize.Card, look = AisleLooks.of(1))
            AisleBasket(BasketKind.FRUIT_CRATE, listOf(mini("b", "banana"), mini("c", "carrot"), mini("m", "milk_bag", holo = true)), toGet = 1, state = BasketVisual(), size = BasketSize.Card, look = AisleLooks.of(4))
        }
    }

    @Test
    fun basketTuckedMinis() = compose.recordPiece(name("basket_tucked_minis"), variant) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AisleBasket(BasketKind.BREAD_BASKET, listOf("baguette", "croissant", "bakery_generic").mapIndexed { i, k -> BasketMini("t$i", IconRef(k)) }, toGet = 1, state = BasketVisual(), size = BasketSize.Card, look = AisleLooks.of(2))
            AisleBasket(BasketKind.FRUIT_CRATE, minis(5), toGet = 0, state = BasketVisual(), size = BasketSize.Card, look = AisleLooks.of(1))
        }
    }

    @Test
    fun basketOpenLids() = compose.recordPiece(name("basket_open_lids"), variant) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, kind) in listOf(BasketKind.BENTO_BOX, BasketKind.COOLER, BasketKind.VANITY_CASE).withIndex()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AisleBasket(kind, minis(2, i), toGet = 2, state = BasketVisual(), size = BasketSize.Card, look = AisleLooks.of(i + 5))
                    AisleBasket(kind, minis(2, i), toGet = 0, state = BasketVisual(complete = true), size = BasketSize.Card, look = AisleLooks.of(i + 5))
                }
            }
        }
    }

    // ---- The peek note (item 114) ----

    @Test
    fun peekNoteUpper() = compose.recordScreen(name("peek_note_upper"), variant) {
        ListScreen(midtrip, ListActions(), screen = remember { ListScreenState(peek = "produce") })
    }

    @Test
    fun peekNoteLower() = compose.recordScreen(name("peek_note_lower"), variant) {
        ListScreen(ListFixture.ui(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "deli-meat")), ListActions(), screen = remember { ListScreenState(peek = "breakfast") })
    }

    /**
     * A capped note (a long aisle with too little room, item 114): the first lines, then "+8 more" in ballpoint, which
     * holds on the lemon note in Day and in Glow (§2.2; Glow's soft ink would all but vanish on it).
     */
    @Test
    fun peekNoteMore() = compose.recordPiece(name("peek_note_more"), variant, width = 240.dp, pad = 16.dp) {
        PeekNote(section(ListFixture.ui(ListFixture.longAisle()), "household"), maxHeight = 130.dp)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

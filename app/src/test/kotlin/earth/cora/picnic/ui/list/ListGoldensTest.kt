package earth.cora.picnic.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.add.ADD_FIELD_TAG
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.focusWithoutTouch
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The list screen's goldens (docs/DESIGN_STICKER.md §6.1, Appendix C; items 71, 92–98, 313): the stateless screen with
 * the Appendix A fixture at 360 × 760 dp, Day and Glow; the IME and FAB states through the real List destination
 * ([ListRig]). Record: `./gradlew :app:testDebugUnitTest --tests '*ListGoldensTest*' -Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun screen(name: String, ui: ListUiState, fontScale: Float? = null, before: () -> Unit = {}, screen: ListScreenState? = null, list: LazyListState? = null) =
        compose.recordScreen(name, variant, fontScale = fontScale, before = { before() }) {
            val s = screen ?: rememberListScreenState()
            val l = list ?: remember { LazyListState() }
            ListScreen(ui, ListActions(), screen = s, listState = l)
        }

    @Test
    fun loading() = screen(goldenName("list_loading", variant), ListUiState(loading = true))

    @Test
    fun fresh() = screen(goldenName("list_fresh", variant), ListFixture.ui(ListFixture.fresh()))

    @Test
    fun midtrip() = screen(goldenName("list_midtrip", variant), ListFixture.ui())

    @Test
    fun done() = screen(goldenName("list_done", variant), ListFixture.ui(ListFixture.done(), now = ListFixture.TRIP_START + 43 * ListFixture.MIN))

    @Test
    fun empty() = screen(goldenName("list_empty", variant), ListFixture.ui(ListFixture.empty()))

    /** Items 92/94: Produce open (5 rows, 3 to get): one tray (band, ruled panel, end cap), the current aisle. */
    @Test
    fun trayOpen() = screen(goldenName("list_tray_open", variant), ListFixture.ui(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce")))

    /** Items 96/97/313: Household (11 rows, current) scrolled under the collapsed top stack, its plate pinned. */
    @Test
    fun pinnedUnderBook() {
        val s = ListScreenState()
        val l = LazyListState()
        screen(goldenName("list_pinned_under_book", variant), ListFixture.ui(ListFixture.longAisle()), screen = s, list = l, before = { pinMidScroll(s, l) })
    }

    /** Item 313: the current-aisle plate pinned, rows mid-scroll under it; the lemon ring runs on into the tray. */
    @Test
    fun currentPinned() {
        val s = ListScreenState()
        val l = LazyListState()
        screen(goldenName("tray_current_pinned", variant), ListFixture.ui(ListFixture.longAisle()), screen = s, list = l, before = { pinMidScroll(s, l, rows = 2.4f) })
    }

    /** Item 98: the keyboard open (300 dp): the add bar rides on it and the book collapses. */
    @Test
    fun ime() {
        val rig = ListRig().inSeason()
        compose.recordScreen(goldenName("list_ime", variant), variant, before = {
            compose.focusWithoutTouch(compose.onNodeWithTag(ADD_FIELD_TAG))
            val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val dp = compose.density.density
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (300 * dp).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
            compose.waitForIdle()
        }) { rig.Content() }
    }

    /** Item 98: scrolled down past 140 dp: the add bar folded into the FAB, the bar and the book collapsed. */
    @Test
    fun fab() {
        val rig = ListRig().inSeason()
        compose.recordScreen(goldenName("list_fab", variant), variant, settleMillis = 1500, before = {
            compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = bottom - 300f, endY = top + 40f, durationMillis = 900) }
            compose.waitForIdle()
        }) { rig.Content() }
    }

    /** Scrolls [l] so Household's band pins under the top stack with [rows] rows already under it (as her scroll would). */
    private fun pinMidScroll(s: ListScreenState, l: LazyListState, rows: Float = 1.5f) {
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:household")
        compose.waitForIdle()
        val px = rows * 56f * compose.density.density
        compose.runOnIdle {
            s.scrolledDown = true
            runBlocking { l.scrollBy(px) }
        }
        compose.waitForIdle()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}

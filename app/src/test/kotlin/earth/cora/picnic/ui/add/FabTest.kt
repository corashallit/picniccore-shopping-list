package earth.cora.picnic.ui.add

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsFocused
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import earth.cora.picnic.ui.theme.TOAST_TAG
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The add bar folding into the FAB (docs/DESIGN_STICKER.md §6.1 "Collapsed add bar", item 116): the rules on
 * [AddBarCollapseState] with a fake clock (1 px = 1 dp), then a real fling in the harness and the FAB tap.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class FabTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun collapseRules() {
        var now = 10_000L
        val s = AddBarCollapseState(density = 1f) { now }

        // ≥ 70 dp of downward scroll past 140 dp from the top folds it.
        s.onScrolled(100f)
        assertFalse("still within the first 140 dp", s.collapsed)
        s.onScrolled(100f)
        assertFalse("60 dp past 140", s.collapsed)
        s.onScrolled(10f)
        assertTrue("70 dp past 140", s.collapsed)

        // 40 dp up brings it back.
        s.onScrolled(-39f)
        assertTrue(s.collapsed)
        s.onScrolled(-1f)
        assertFalse(s.collapsed)

        // Down again: a fresh 70 dp run; reaching the top brings it back.
        s.onScrolled(69f)
        assertFalse(s.collapsed)
        s.onScrolled(1f)
        assertTrue(s.collapsed)
        s.atTop(true)
        assertFalse(s.collapsed)

        // Programmatic scrolls (the next aisle's auto-open) are ignored for 1.2 s.
        s.onScrolled(300f)
        s.expand()
        s.ignoreProgrammaticScroll()
        s.onScrolled(400f)
        now += 1_199
        s.onScrolled(100f)
        assertFalse("ignored for 1.2 s", s.collapsed)
        now += 1
        s.onScrolled(70f)
        assertTrue("counts again after 1.2 s", s.collapsed)

        // Never while the field is focused or a sheet is open; the FAB (expand) restores it.
        s.expand()
        s.fieldFocused = true
        s.onScrolled(500f)
        assertFalse("never while focused", s.collapsed)
        s.fieldFocused = false
        s.sheetOpen = true
        s.onScrolled(500f)
        assertFalse("never under a sheet", s.collapsed)
        s.sheetOpen = false
        s.onScrolled(70f)
        assertTrue(s.collapsed)
        s.expand()
        assertFalse(s.collapsed)
    }

    @Test
    fun aFlingFoldsTheBarAndTheFabFocusesTheField() {
        compose.setHarness()
        val list = compose.onNodeWithTag(HARNESS_LIST_TAG)
        // Drag above the bar (it overlaps the list's bottom).
        list.performTouchInput { swipeUp(startY = centerY + 150f, endY = top + 40f) }
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_FAB_TAG).assertExists()
        compose.onNodeWithTag(ADD_BAR_TAG).assertDoesNotExist()

        compose.onNodeWithTag(ADD_FAB_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_BAR_TAG).assertExists()
        compose.onNodeWithTag(ADD_FIELD_TAG).assertIsFocused()
        compose.onNodeWithTag(ADD_FAB_TAG).assertDoesNotExist()
    }

    @Test
    fun theTopBringsTheBarBack() {
        compose.setHarness()
        val list = compose.onNodeWithTag(HARNESS_LIST_TAG)
        list.performTouchInput { swipeUp(startY = centerY + 150f, endY = top + 40f) }
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_FAB_TAG).assertExists()
        repeat(4) { list.performTouchInput { swipeDown(startY = top + 40f, endY = centerY + 150f) } }
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_BAR_TAG).assertExists()
    }

    /**
     * §4.15, §6.1: the dock measures itself. At font scale 2.0 the bar is far taller than 84 dp, and with the keyboard
     * open it rides above it: a toast still sits 20 dp above the bar's top (never on its field or buttons, never behind
     * the keyboard), and the list's last rows clear it by 26 dp.
     */
    @Test
    fun toastsClearTheDockAtLargeTextAndOverTheKeyboard() {
        val rig = addRig(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main))
        val toasts = ToastHostState()
        var flows: AddFlows? = null
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 2f)) {
                earth.cora.picnic.ui.theme.PicnicTheme(earth.cora.picnic.ui.theme.ThemeVariant.Day) {
                    val f = rememberAddFlows(rig.vm, RecordingSources().sources)
                    flows = f
                    AddFlowsHarness(f, harnessRows(AddFixtures.state), toasts = toasts)
                }
            }
        }
        compose.waitForIdle()
        val dp = compose.density.density
        val root = compose.onNodeWithTag(HARNESS_LIST_TAG).fetchSemanticsNode().boundsInRoot
        fun bar() = compose.onNodeWithTag(ADD_BAR_TAG).fetchSemanticsNode().boundsInRoot
        fun gapAboveBar(): Float {
            val toast = compose.onNodeWithTag(TOAST_TAG).fetchSemanticsNode().boundsInRoot
            return (bar().top - toast.bottom) / dp
        }
        assertTrue("the bar grew at 2.0: ${bar().height / dp} dp", bar().height / dp > 120f)
        assertEquals("the dock is the bar as laid out", bar().height / dp, flows!!.dockHeight.value, 1f)

        compose.mainClock.autoAdvance = false
        toasts.show(ToastMessage("Added 2 items to Saturday shop ♡"))
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(600)
        assertEquals("20 dp above the bar at 2.0", 20f, gapAboveBar(), 1f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        // The keyboard opens (300 dp): the bar rides above it, and so does the next toast.
        val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val ime = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (300 * dp).toInt()))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, ime) }
        compose.waitForIdle()
        assertEquals("the bar sits on the keyboard", 300f, (root.bottom - bar().bottom) / dp, 2f)
        assertEquals("the dock counts the keyboard", (root.bottom - bar().top) / dp, flows!!.dockHeight.value, 2f)
        compose.mainClock.autoAdvance = false
        toasts.show(ToastMessage("Milk is back on your list"))
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(600)
        assertTrue("20 dp above the bar over the keyboard: ${gapAboveBar()} dp", gapAboveBar() >= 19f)
    }

    /** Item 116: a sheet WP5 opens (Edit, the list switcher, the Trip book) keeps the bar docked too; ours never overwrites it. */
    @Test
    fun anotherSheetKeepsTheBarDocked() {
        val rig = addRig(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main))
        var other by androidx.compose.runtime.mutableStateOf(true)
        var flows: AddFlows? = null
        compose.setContent {
            earth.cora.picnic.ui.theme.PicnicTheme(earth.cora.picnic.ui.theme.ThemeVariant.Day) {
                val f = rememberAddFlows(rig.vm, RecordingSources().sources)
                flows = f
                val state by rig.vm.state.collectAsState()
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { AddDock(f, state, otherSheetOpen = other) }
            }
        }
        compose.waitForIdle()
        val collapse = flows!!.collapse
        assertTrue("another sheet is open", collapse.sheetOpen)
        collapse.onScrolled(10_000f)
        assertFalse("never folds under another sheet", collapse.collapsed)
        other = false
        compose.waitForIdle()
        assertFalse(collapse.sheetOpen)
        collapse.onScrolled(10_000f)
        assertTrue(collapse.collapsed)
    }
}

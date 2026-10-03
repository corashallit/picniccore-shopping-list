package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.platform.Timeouts
import earth.cora.picnic.ui.a11y.TextOverflowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Timed component behaviour on the Compose test clock (checklist 24, 51, 60, 68, 69, 334): the toast's drop, hold and
 * queue; the letter-set sheet's entrance and handle slap; the primary pill dropping onto its shadow; the app bar's
 * collapse; puffy titles shrinking, ellipsizing and never clipping.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ToastTest {
    @get:Rule
    val compose = createComposeRule()

    /** Applies pending state changes (with autoAdvance off, time alone does not), then advances the frame clock. */
    private fun advance(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
    }


    @Test
    fun holdAndPosition() {
        val state = ToastHostState()
        compose.mainClock.autoAdvance = false
        compose.setContent { PicnicTheme(ThemeVariant.Day) { Box(Modifier.fillMaxSize()) { ToastHost(state, bottomInset = 84.dp) } } }
        compose.runOnIdle { state.show(ToastMessage("Added 5 items to Saturday shop ♡", kind = "add", actionLabel = "Undo", onAction = {})) }
        advance(32)
        val root = compose.onRoot().getBoundsInRoot()
        val note = compose.onNodeWithTag(TOAST_TAG).getBoundsInRoot()
        assertEquals("14 dp from the left", 14f, note.left.value, 0.5f)
        assertEquals("20 dp above the add bar", (root.bottom - 84.dp - 20.dp).value, note.bottom.value, 0.5f)
        assertEquals(228f, (note.right - note.left).value, 0.5f) // the 220 dp note + its washi overhang room
        // Drops in (220 ms), then holds max(4000, readHold) = 4000 ms still, then lifts off (260 ms).
        advance(220L + 4000L - 100L)
        assertTrue(state.current != null)
        advance(100L + 260L + 100L)
        assertNull(state.current)
        compose.onAllNodesWithTag(TOAST_TAG).assertCountEqualsZero()
    }

    @Test
    fun holdFollowsTheTimeoutsSetting() {
        val state = ToastHostState()
        val doubled = object : Timeouts {
            override fun recommended(holdMs: Long, controls: Boolean): Long = if (controls) holdMs * 2 else holdMs
            override val holdThresholdMs: Long = 440
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalTimeouts provides doubled) {
                PicnicTheme(ThemeVariant.Day) { Box(Modifier.fillMaxSize()) { ToastHost(state) } }
            }
        }
        compose.runOnIdle { state.show(ToastMessage("Oat cereal torn out", kind = "tear", actionLabel = "Undo", onAction = {})) }
        advance(32L + 220L + 7800L)
        assertTrue("an action toast holds the recommended (doubled) time", state.current != null)
        advance(200L + 260L + 100L)
        assertNull(state.current)
    }

    @Test
    fun mergeAndQueue() {
        val state = ToastHostState()
        var restored = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { PicnicTheme(ThemeVariant.Day) { Box(Modifier.fillMaxSize()) { ToastHost(state) } } }
        compose.runOnIdle { state.show(ToastMessage("Oat cereal torn out", kind = "tear", actionLabel = "Undo", onAction = { restored++ })) }
        advance(32L + 220L + 3000L)
        // A second tear-off during the hold merges into it and restarts the hold; Undo restores both.
        compose.runOnIdle { state.show(ToastMessage("2 items torn out", kind = "tear", actionLabel = "Undo", onAction = { restored++ })) }
        advance(16)
        assertEquals("2 items torn out", state.current?.text)
        // Other toasts queue (at most 2; a newer info toast of the same kind replaces a queued one).
        compose.runOnIdle {
            state.show(ToastMessage("Sorted 6 items", kind = "sort"))
            state.show(ToastMessage("Sorted 7 items", kind = "sort"))
        }
        assertEquals(listOf("Sorted 7 items"), state.queue.map { it.text })
        compose.runOnIdle {
            state.show(ToastMessage("Added Milk to Dairy & eggs ♡", kind = "add", actionLabel = "Undo", onAction = {}))
            state.show(ToastMessage("Couldn't save. Try again.", kind = "error"))
        }
        assertEquals(listOf("Added Milk to Dairy & eggs ♡", "Couldn't save. Try again."), state.queue.map { it.text })
        // The merged Undo is never cut short: 4 s from the merge, still there just before the end.
        advance(3900)
        assertEquals("2 items torn out", state.current?.text)
        state.current!!.onAction!!.invoke()
        assertEquals(2, restored)
        advance(100L + 260L + 100L)
        assertEquals("Added Milk to Dairy & eggs ♡", state.current?.text)
    }

}

/** The letter-set sheet's entrance and handle slap (checklist 69). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class SheetTest {
    @get:Rule
    val compose = createComposeRule()

    /** Applies pending state changes (with autoAdvance off, time alone does not), then advances the frame clock. */
    private fun advance(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
    }

    @Test
    fun entranceTiming() {
        var visible by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                Box(Modifier.fillMaxSize()) {
                    LetterSetSheet(visible = visible, onDismissRequest = { visible = false }) { Text("Start a new trip?") }
                }
            }
        }
        compose.runOnIdle { visible = true }
        advance(16)
        val rootBottom = compose.onRoot().getBoundsInRoot().bottom
        val start = compose.onNodeWithTag(LETTERSET_TAG).getBoundsInRoot().top
        advance(120)
        val mid = compose.onNodeWithTag(LETTERSET_TAG).getBoundsInRoot().top
        advance(400)
        val end = compose.onNodeWithTag(LETTERSET_TAG).getBoundsInRoot().top
        assertTrue("starts below its rest position ($start vs $end)", start > end)
        assertTrue("moving at 136 ms ($mid between $start and $end)", mid < start && mid > end - 30.dp)
        assertTrue("rests inside the screen", end < rootBottom)
        // Settled by 280 ms: no further movement.
        advance(200)
        assertEquals(end.value, compose.onNodeWithTag(LETTERSET_TAG).getBoundsInRoot().top.value, 0.5f)
        // The washi handle has slapped down to 1×.
        val scale = compose.onRoot().fetchSemanticsNode().let { rootNode ->
            fun find(n: androidx.compose.ui.semantics.SemanticsNode): Float? =
                n.config.getOrNull(HandleScale) ?: n.children.firstNotNullOfOrNull { find(it) }
            find(rootNode)
        }
        assertEquals(1f, scale)
    }

    @Test
    fun handleSlapsAfterArrival() {
        var visible by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) { LetterSetSheet(visible = visible, onDismissRequest = {}) { Text("Edit") } }
        }
        fun handleScale(): Float? {
            fun find(n: androidx.compose.ui.semantics.SemanticsNode): Float? = n.config.getOrNull(HandleScale) ?: n.children.firstNotNullOfOrNull { find(it) }
            return find(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        }
        compose.runOnIdle { visible = true }
        advance(16)
        assertEquals("hidden while the sheet slides up", 0f, handleScale())
        advance(350)
        assertEquals("slaps on at 1.6× once the sheet has arrived (280 ms)", 1.6f, handleScale())
        advance(83)
        assertEquals("one pose later", 1.2f, handleScale()!!, 0.01f)
        advance(100)
        assertEquals(1f, handleScale())
    }

}

/** The primary pill drops onto its shadow in 2 steps (checklist 60). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ButtonTest {
    @get:Rule
    val compose = createComposeRule()

    /** Applies pending state changes (with autoAdvance off, time alone does not), then advances the frame clock. */
    private fun advance(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
    }

    @Test
    fun pressedTranslatesOntoShadow() {
        compose.mainClock.autoAdvance = false
        compose.setContent { PicnicTheme(ThemeVariant.Day) { PicnicButton("Start my list", {}) } }
        advance(16)
        fun offset(): DpOffset = compose.onNodeWithText("Start my list").fetchSemanticsNode().config[FaceOffset]
        assertEquals(DpOffset(0.dp, 0.dp), offset())
        compose.onNodeWithText("Start my list").performTouchInput { down(center) }
        advance(40)
        val half = offset()
        assertEquals(1.2f, half.x.value, 0.01f)
        assertEquals(1.7f, half.y.value, 0.01f)
        advance(100)
        assertEquals(DpOffset(2.4.dp, 3.4.dp), offset())
        compose.onNodeWithText("Start my list").performTouchInput { up() }
        advance(200)
        assertEquals(DpOffset(0.dp, 0.dp), offset())
    }

}

/** The app bar collapses on scroll; a long title shrinks, then ellipsizes (checklist 24, 51). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class AppBarTest {
    @get:Rule
    val compose = createComposeRule()

    /** Applies pending state changes (with autoAdvance off, time alone does not), then advances the frame clock. */
    private fun advance(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
    }

    @Test
    fun collapsesOnScroll() {
        val state = AppBarState()
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                Column(Modifier.fillMaxSize()) {
                    BinderTopBar(
                        "Saturday shop", Modifier.testTag("bar"), subtitle = "Hillcrest Market · 8 stops",
                        lcd = LcdState(LcdMode.Count, 10, 24), onMore = {}, state = state,
                    )
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).nestedScroll(state.nestedScrollConnection).testTag("list")) {
                        items(60) { Text("Row $it", Modifier.height(56.dp)) }
                    }
                }
            }
        }
        fun barHeight() = compose.onNodeWithTag("bar").getBoundsInRoot().let { (it.bottom - it.top).value }
        assertEquals(72f, barHeight(), 0.5f)
        compose.onNodeWithText("Hillcrest Market · 8 stops").assertExists()
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(state.collapsed)
        assertEquals(56f, barHeight(), 0.5f)
        compose.onNodeWithText("Hillcrest Market · 8 stops").assertDoesNotExist()
        // The LCD stays.
        compose.onNodeWithContentDescription("10 of 24 in the basket").assertExists()
        // Back at the top it snaps open.
        repeat(6) { compose.onNodeWithTag("list").performTouchInput { swipeDown() } }
        compose.waitForIdle()
        assertFalse(state.collapsed)
        assertEquals(72f, barHeight(), 0.5f)
    }

    @Test
    fun titleShrinksThenEllipsizes() {
        var width by mutableStateOf(400.dp)
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                Box(Modifier.width(width)) {
                    PuffyText("Saturday shop", PicnicTheme.type.screenTitle, PicnicTheme.colors.ink, 6.dp, minFontSize = 20.sp)
                }
            }
        }
        fun node() = compose.onNodeWithText("Saturday shop").fetchSemanticsNode()
        assertEquals(24f, node().config[PuffyFontSize])
        assertNull(node().config.getOrNull(TextOverflowed))
        // Narrower: it shrinks in 1 sp steps (to stay on one line) before it ellipsizes.
        var lastFit = 24f
        var shrank = false
        for (w in 200 downTo 100 step 4) {
            width = w.dp
            compose.waitForIdle()
            val size = node().config[PuffyFontSize]
            assertTrue(size <= lastFit)
            if (size < 24f && size > 20f) shrank = true
            if (node().config.getOrNull(TextOverflowed) == true) assertEquals("ellipsizes only at 20 sp", 20f, size)
            lastFit = size
        }
        assertTrue("passed through an intermediate size", shrank)
        width = 90.dp
        compose.waitForIdle()
        assertEquals(20f, node().config[PuffyFontSize])
        assertEquals(true, node().config[TextOverflowed])
    }

}

/** Puffy titles never clip their stroke or shadow, fitting or ellipsized (checklist 334). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class PuffyTextBoundsTest {
    @get:Rule
    val compose = createComposeRule()

    /** Applies pending state changes (with autoAdvance off, time alone does not), then advances the frame clock. */
    private fun advance(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
    }

    @Test
    fun puffyTextNeverClips() {
        val titles = listOf("Saturday shop", "Kirakira Basket", "Everything else", "List complete!", "NEXT")
        val screen = SwappableContent(compose)
        for (variant in listOf(ThemeVariant.Day, ThemeVariant.Glow)) for (fontScale in listOf(1f, 2f)) for (narrow in listOf(false, true)) {
            screen.show {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(3f, fontScale),
                ) {
                    PicnicTheme(variant) {
                        Column {
                            for (t in titles) {
                                Box(Modifier.width(if (narrow) 120.dp else 360.dp)) {
                                    PuffyText(t, PicnicTheme.type.screenTitle, PicnicTheme.colors.ink, 9.dp, minFontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            for (t in titles) {
                val n = compose.onNodeWithText(t).fetchSemanticsNode()
                val ink = n.config[PuffyInkBounds]
                val box = n.boundsInRoot
                assertTrue("$t ($variant, ×$fontScale, narrow=$narrow): ink $ink outside ${box.size}", ink.left >= 0f && ink.top >= 0f)
                assertTrue("$t ($variant, ×$fontScale, narrow=$narrow): ink ${ink.right}×${ink.bottom} vs ${box.width}×${box.height}", ink.right <= box.width + 1f && ink.bottom <= box.height + 1f)
                assertTrue(abs(ink.width - box.width) <= 1.5f || ink.width <= box.width)
            }
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEqualsZero() {
    assertEquals(0, fetchSemanticsNodes().size)
}

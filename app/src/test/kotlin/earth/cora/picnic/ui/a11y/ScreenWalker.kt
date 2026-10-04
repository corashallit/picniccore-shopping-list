package earth.cora.picnic.ui.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.cancel
import kotlin.math.abs

/*
 * The screen-fixture walker (docs/DESIGN_STICKER.md §21 WP10 "Provides"): every screen of the app and its main states
 * (A11yScreens), rendered in Day and Glow at font scale 1.0 and 2.0 on one compose rule, each walked page by page
 * through every scroll container so the audits see every node the screen can show, not only the first viewport.
 * The audits (TouchTargetAuditTest, TalkBackCopyTest, DecorSemanticsTest, KeyboardNavTest, FocusTest) read what it
 * shows; none of them picks its own sample. Owner: WP10.
 */

/** A theme variant at a font scale: one of the four looks every screen is walked in. */
enum class Look(val variant: ThemeVariant, val fontScale: Float) {
    Day1(ThemeVariant.Day, 1f),
    Day2(ThemeVariant.Day, 2f),
    Glow1(ThemeVariant.Glow, 1f),
    Glow2(ThemeVariant.Glow, 2f),
}

/**
 * One visit's stage, built fresh for each visit (a new view model, a new clock): what to show and how to reach the state
 * once it is on screen ([reach] runs after the first frame, on the auto-advancing clock unless it holds it itself).
 */
class Stage(
    val reducedMotion: Boolean = false,
    /** Run when the visit ends (a rig's scope). */
    val dispose: () -> Unit = {},
    val reach: StageScope.() -> Unit = {},
    /** Run on the rule before the next visit replaces this one (a held touch let go). */
    val leave: StageScope.() -> Unit = {},
    val content: @Composable () -> Unit,
)

/** What a stage's [Stage.reach] can do: the rule, and frames on a held clock. */
class StageScope(val compose: ComposeContentTestRule) {
    /** Holds the clock and runs [n] frames (the view models' work runs between them). */
    fun frames(n: Int) {
        compose.mainClock.autoAdvance = false
        repeat(n) {
            Snapshot.sendApplyNotifications()
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
        }
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
    }

    /** Holds the clock and runs frames until [done] or [maxMs] of frame time (bounded: never hangs). */
    fun framesUntil(maxMs: Long, done: () -> Boolean): Boolean {
        var t = 0L
        while (t <= maxMs) {
            if (done()) return true
            frames(1)
            t += 16
        }
        return done()
    }
}

/** A screen state the walker visits. [group] names the screen ("list", "settings", …), [name] the state. */
class ScreenFixture(val group: String, val name: String, val build: (ComposeContentTestRule) -> Stage) {
    val id: String get() = "$group/$name"

    override fun toString(): String = id
}

/**
 * Shows [ScreenFixture]s one after another on [compose] (a rule's content can be set once per test, so the walker swaps
 * what it shows), each in a fresh composition in its [Look].
 */
class ScreenWalker(val compose: ComposeContentTestRule) {
    private class Shown(val n: Int, val look: Look, val stage: Stage)

    /** The keyboard on what the walker shows (FocusTest, KeyboardNavTest). */
    val keys: Keyboard = Keyboard(compose)

    private var shown: Shown? by mutableStateOf(null)
    private var started = false
    private var count = 0
    private var last: Stage? = null

    /** Shows [fixture] in [look], reaches its state and hands the [Visit] to [block]. */
    fun <T> visit(fixture: ScreenFixture, look: Look, block: Visit.() -> T): T {
        end()
        compose.mainClock.autoAdvance = true
        val stage = fixture.build(compose)
        last = stage
        shown = Shown(++count, look, stage)
        if (!started) {
            started = true
            compose.setContent { Host() }
        }
        compose.waitForIdle()
        stage.reach(StageScope(compose))
        if (compose.mainClock.autoAdvance) compose.waitForIdle()
        return Visit(this, fixture, look).block()
    }

    /** Every fixture of [fixtures] in every look of [looks]. */
    fun walk(fixtures: List<ScreenFixture>, looks: List<Look>, block: Visit.() -> Unit) {
        for (look in looks) for (f in fixtures) visit(f, look, block)
        finish()
    }

    /** Ends the last visit (its rig's scope). */
    fun finish() = end()

    private fun end() {
        val stage = last ?: return
        last = null
        stage.leave(StageScope(compose))
        stage.dispose()
    }

    @Composable
    private fun Host() {
        keys.Capture()
        val s = shown ?: return
        key(s.n) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, s.look.fontScale)) {
                PicnicTheme(variant = s.look.variant, reducedMotion = s.stage.reducedMotion) {
                    Box(Modifier.fillMaxSize()) { s.stage.content() }
                }
            }
        }
    }
}

/** One fixture on screen in one look. */
class Visit(val walker: ScreenWalker, val fixture: ScreenFixture, val look: Look) {
    val compose: ComposeContentTestRule get() = walker.compose

    /** "list/mid-trip@Day1": where a finding was seen. */
    val where: String get() = "${fixture.id}@${look.name}"

    val density: Float get() = compose.density.density

    /**
     * The merged trees' roots (what TalkBack and the keyboard work with): the screen's window first, then any popup's
     * (a menu, a pop card), each with its own coordinates.
     */
    fun roots(): List<SemanticsNode> = compose.onAllNodes(isRoot(), useUnmergedTree = false).fetchSemanticsNodes()

    /** The screen window's merged root. */
    fun root(): SemanticsNode = roots().first()

    /** The unmerged trees' roots (every node, decor markers included). */
    fun unmergedRoots(): List<SemanticsNode> = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()

    /** Every node of the merged trees, in traversal order (depth first), every window. */
    fun nodes(): List<SemanticsNode> = roots().flatMap { it.all() }

    /** Every node of the unmerged trees. */
    fun unmergedNodes(): List<SemanticsNode> = unmergedRoots().flatMap { it.all() }

    /** Settles after an action: on a held clock, a few frames; else until idle. */
    fun settle() {
        if (compose.mainClock.autoAdvance) {
            compose.waitForIdle()
        } else {
            StageScope(compose).frames(40)
        }
    }

    /**
     * Runs [block] on every page of the screen: the state as reached, then each scroll container (outermost first)
     * from its start to its end in steps of three quarters of its size, so every node the screen holds is wholly on
     * screen on some page. Returns the number of pages.
     */
    fun pages(maxStepsPerContainer: Int = 14, block: (page: Int) -> Unit): Int {
        var page = 0
        block(page++)
        val seen = HashSet<Int>()
        // Re-found after every container: scrolling one can compose another (a sheet's column inside a lazy item).
        while (true) {
            val container = scrollContainers().firstOrNull { it.id !in seen } ?: break
            seen += container.id
            val vertical = container.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.takeIf { it.maxValue() > 0f }
            val horizontal = container.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.takeIf { it.maxValue() > 0f }
            val axisVertical = vertical != null
            val range = vertical ?: horizontal ?: continue
            val step = (if (axisVertical) container.size.height else container.size.width) * 0.75f
            if (step <= 1f) continue
            // To its start, then a page per step until it stops moving.
            if (range.value() > 0.5f) {
                scroll(container.id, if (axisVertical) 0f else -1e6f, if (axisVertical) -1e6f else 0f)
                block(page++)
            }
            var steps = 0
            while (steps++ < maxStepsPerContainer) {
                val before = rangeOf(container.id, axisVertical)?.value() ?: break
                scroll(container.id, if (axisVertical) 0f else step, if (axisVertical) step else 0f)
                val after = rangeOf(container.id, axisVertical)?.value() ?: break
                if (abs(after - before) < 0.5f) break
                block(page++)
            }
        }
        return page
    }

    private fun rangeOf(id: Int, vertical: Boolean): ScrollAxisRange? {
        val n = nodes().firstOrNull { it.id == id } ?: return null
        return n.config.getOrNull(if (vertical) SemanticsProperties.VerticalScrollAxisRange else SemanticsProperties.HorizontalScrollAxisRange)
    }

    private fun scroll(id: Int, dx: Float, dy: Float) {
        compose.onNode(SemanticsMatcher("node $id") { it.id == id }).performSemanticsAction(SemanticsActions.ScrollBy) { it(dx, dy) }
        settle()
    }

    /** The scroll containers on screen that can scroll, outermost first. */
    fun scrollContainers(): List<SemanticsNode> = nodes().filter { n ->
        n.config.getOrNull(SemanticsActions.ScrollBy) != null &&
            (
                (n.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue() ?: 0f) > 0f ||
                    (n.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.maxValue() ?: 0f) > 0f
                ) &&
            shownAtAll(n)
    }

    /** The bounds of the window [n] is in (its root's). */
    fun window(n: SemanticsNode): Rect = n.root().boundsInRoot

    /** Whether any part of [n] is on screen. */
    fun shownAtAll(n: SemanticsNode): Boolean {
        val b = n.boundsInRoot
        return b.width > 0f && b.height > 0f && b.overlaps(window(n))
    }

    /**
     * Whether [n] is wholly on screen: inside the window and cut by no scrolling parent or clip (its bounds in the root
     * are its whole laid-out size). A node cut here is wholly on screen on another page.
     */
    fun wholly(n: SemanticsNode): Boolean {
        val b = n.boundsInRoot
        val w = window(n)
        if (b.width <= 0f || b.height <= 0f) return false
        if (b.left < w.left - 0.5f || b.top < w.top - 0.5f || b.right > w.right + 0.5f || b.bottom > w.bottom + 0.5f) return false
        return b.width >= n.size.width - 1f && b.height >= n.size.height - 1f
    }
}

/** The root of the window this node is in. */
fun SemanticsNode.root(): SemanticsNode {
    var r = this
    while (true) r = r.parent ?: return r
}

/** This node and every node under it, depth first. */
fun SemanticsNode.all(): List<SemanticsNode> {
    val out = ArrayList<SemanticsNode>()
    fun go(n: SemanticsNode) {
        out += n
        n.children.forEach(::go)
    }
    go(this)
    return out
}

/** What TalkBack would say for [n] (its label, text and state), for messages. */
fun SemanticsNode.spoken(): String {
    val c = config
    val parts = buildList {
        c.getOrNull(SemanticsProperties.ContentDescription)?.let { addAll(it) }
        c.getOrNull(SemanticsProperties.Text)?.let { t -> addAll(t.map { it.text }) }
        c.getOrNull(SemanticsProperties.EditableText)?.let { add(it.text) }
        c.getOrNull(SemanticsProperties.StateDescription)?.let { add(it) }
    }
    val tag = c.getOrNull(SemanticsProperties.TestTag)
    return parts.joinToString(" | ").ifEmpty { "<no label>" } + (tag?.let { " [$it]" } ?: "")
}

/** Whether the node can be acted on: a tap, a long press, a toggle or a selection. */
val SemanticsNode.actionable: Boolean
    get() = config.getOrNull(SemanticsActions.OnClick) != null ||
        config.getOrNull(SemanticsActions.OnLongClick) != null ||
        config.getOrNull(SemanticsProperties.ToggleableState) != null ||
        (config.getOrNull(SemanticsProperties.Selected) != null && config.getOrNull(SemanticsActions.OnClick) != null)

/** Cancels a rig's view-model scope at the end of its visit. */
fun disposeScope(scope: kotlinx.coroutines.CoroutineScope): () -> Unit = { scope.cancel() }

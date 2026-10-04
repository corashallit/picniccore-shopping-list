package earth.cora.picnic.ui.a11y

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.LIST_MENU_TAG
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import earth.cora.picnic.ui.theme.LETTERSET_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 287 (docs/DESIGN_STICKER.md §15 "Keyboard / switch access"): with a keyboard (or switch access, which moves the
 * same focus), on every screen fixture of the walker:
 * - every control on the layer in front (the screen, or the sheet, menu or pop card over it) is reached by Tab alone,
 *   from the first stop to the last, scrolling where it must;
 * - the keyboard never lands behind a sheet or menu;
 * - Escape and Back close every sheet, menu, pop card and overlay (the Next card, the celebration).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class KeyboardNavTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyControlIsReachedByTabDay1() = reached(Look.Day1)

    @Test
    fun everyControlIsReachedByTabGlow2() = reached(Look.Glow2)

    private fun reached(look: Look) {
        val problems = LinkedHashSet<String>()
        var stops = 0
        ScreenWalker(compose).walk(A11yScreens.all, listOf(look)) {
            val front = KeyRules.front(this)
            // From the top of the screen as reached: Tab forward round the screen (or to the end of a sheet, which
            // holds focus), then Shift+Tab back from where it started.
            toStart()
            val walk = tabWalk { f -> FocusStop(KeyRules.key(f), KeyRules.key(KeyRules.controlAt(f)), KeyRules.inFront(this, f, front()), f.spoken()) }
            stops += walk.size
            val before = problems.size
            for (n in walk) if (!n.inFront) problems += "$where: Tab landed behind the layer in front, on ${n.said}"
            val got = walk.map { it.control }.toSet()
            // What there is to reach: the controls on screen as the walk began, and those in its scrolling lists and
            // pages, scrolled through (the dock folding into the FAB as a list scrolls is another state, not more).
            val expected = LinkedHashMap<String, String>()
            pages { page ->
                val containers = if (page == 0) emptyList() else scrollContainers()
                for (n in KeyRules.controls(this, front())) {
                    if (page > 0 && containers.none { c -> generateSequence(n) { it.parent }.any { it.id == c.id } }) continue
                    expected.putIfAbsent(KeyRules.key(n), n.spoken())
                }
            }
            for ((key, said) in expected) if (key !in got) problems += "$where: not reached by Tab: $said"
            // The walk as it went, to read a failure by.
            if (problems.size > before) problems += "$where: the Tab stops were " + walk.joinToString(" > ") { it.key.take(32) }
            // A new state with something up over the screen joins the Escape and Back check below.
            if (KeyRules.layerUp(this) != null && fixture.id !in KeyRules.layered) {
                problems += "$where: ${KeyRules.layerUp(this)} is up, but escapeAndBackCloseEveryLayer does not visit it"
            }
        }
        assertTrue("focus stops: $stops", stops > 300)
        assertTrue("keyboard problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * Escape (from wherever focus is) and the phone's Back each close what is up over the screen: every sheet, the
     * overflow menu, the pop card, the Next card and the celebration. Each state is reached afresh for each key.
     */
    @Test
    fun escapeAndBackCloseEveryLayer() {
        val problems = ArrayList<String>()
        var closed = 0
        val fixtures = A11yScreens.all.filter { it.id in KeyRules.layered }
        assertTrue("layered fixtures: ${fixtures.map { it.id }}", fixtures.size == KeyRules.layered.size)
        val walker = ScreenWalker(compose)
        for (key in listOf("Escape", "Back")) for (fixture in fixtures) walker.visit(fixture, Look.Day1) {
                val up = KeyRules.layerUp(this)
                if (up == null) {
                    problems += "$where: nothing is up to close"
                    return@visit
                }
                val keys = walker.keys
                keys.on()
                if (key == "Escape") {
                    // From wherever focus is: on the layer, or on the screen under an overlay (which never takes it).
                    if (keys.focused() == null) keys.press(Key.Tab) { settle() }
                    keys.windowKey(android.view.KeyEvent.KEYCODE_ESCAPE) { }
                } else {
                    keys.back { }
                }
                // Its way out played (bounded).
                StageScope(compose).framesUntil(3000) { KeyRules.layerUp(this) == null }
                if (KeyRules.layerUp(this) != null) problems += "$where: $key left $up up" else closed++
        }
        walker.finish()
        assertTrue("keyboard problems:\n" + problems.joinToString("\n"), problems.isEmpty())
        assertTrue("closed: $closed", closed == fixtures.size * 2)
    }
}

/** What the keyboard must reach, and how a control is known between pages and Tab stops. */
object KeyRules {
    /** The layers that hold the keyboard while up: a letter-set sheet, the overflow menu. */
    private val layers = listOf(LETTERSET_TAG, LIST_MENU_TAG)

    /**
     * The overlays that never take focus (§15: the Next card and the celebration never steal it); Escape and Back close
     * them, from wherever focus is (KeyboardNavTest.escapeAndBackCloseEveryLayer).
     */
    private val neverFocused = listOf(NEXT_CARD_TAG, CELEBRATION_TAG)

    /** The screen states with something up over the screen (escapeAndBackCloseEveryLayer closes each). */
    val layered = setOf(
        "list/menu", "list/next-card", "list/celebration-summary",
        "sheet/trip-book", "sheet/new-trip", "sheet/switch-list", "sheet/edit",
        "sheet/add-write", "sheet/add-snap", "sheet/add-speak", "sheet/add-snap-read", "sheet/add-speak-recording", "sheet/add-recipe",
        "pantry/popcard", "album/book",
    )

    /** What is up over the screen, or null: a pop card, a sheet, the menu, the Next card or the celebration. */
    fun layerUp(v: Visit): String? {
        if (v.roots().size > 1) return "a pop card"
        val tags = v.unmergedNodes().mapNotNullTo(HashSet()) { it.config.getOrNull(SemanticsProperties.TestTag) }
        return (layers + neverFocused).firstOrNull { it in tags }
    }

    /** The layer in front: the newest popup window's root, else the last sheet or menu laid out, else null (the screen). */
    fun front(v: Visit): () -> SemanticsNode? = {
        val roots = v.roots()
        if (roots.size > 1) {
            roots.last()
        } else {
            v.nodes().lastOrNull { n -> n.config.getOrNull(SemanticsProperties.TestTag) in layers }
        }
    }

    fun inFront(v: Visit, n: SemanticsNode, front: SemanticsNode?): Boolean =
        front == null || generateSequence(n) { it.parent }.any { it.id == front.id }

    private fun acts(n: SemanticsNode): Boolean {
        val c = n.config
        return c.getOrNull(SemanticsActions.OnClick) != null || c.getOrNull(SemanticsActions.OnLongClick) != null ||
            c.getOrNull(SemanticsProperties.ToggleableState) != null || c.getOrNull(SemanticsProperties.EditableText) != null
    }

    /** A control: something to press, hold, toggle or type in, enabled, on screen, on the layer in front. */
    fun controls(v: Visit, front: SemanticsNode?): List<SemanticsNode> = v.nodes().filter { n ->
        acts(n) && n.config.getOrNull(SemanticsProperties.Disabled) == null && v.shownAtAll(n) && inFront(v, n, front) &&
            generateSequence(n) { it.parent }.none { it.config.getOrNull(SemanticsProperties.TestTag) in neverFocused }
    }

    /**
     * The control a focus stop is: the focused node when it acts, else the control around it (a tab whose focus sits on
     * an inner node), else the one inside it.
     */
    fun controlAt(f: SemanticsNode): SemanticsNode =
        generateSequence(f) { it.parent }.take(4).firstOrNull(::acts) ?: f.all().firstOrNull(::acts) ?: f

    /** A control's identity across scrolls and recompositions: its test tag, else its words and role. */
    fun key(n: SemanticsNode): String =
        n.config.getOrNull(SemanticsProperties.TestTag) ?: "${n.spoken()}|${n.config.getOrNull(SemanticsProperties.Role)}"
}

/** Scrolls every scroll container back to its start (a Tab walk starts at the top of the screen). */
fun Visit.toStart() {
    repeat(3) {
        for (c in scrollContainers()) {
            val vertical = (c.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f) > 0.5f
            val horizontal = (c.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.value?.invoke() ?: 0f) > 0.5f
            if (!vertical && !horizontal) continue
            compose.onNode(SemanticsMatcher("node ${c.id}") { it.id == c.id })
                .performSemanticsAction(SemanticsActions.ScrollBy) { it(if (horizontal) -1e6f else 0f, if (vertical) -1e6f else 0f) }
            settle()
        }
    }
}

/** One place the keyboard's focus landed, read as it landed (a lazy item may be gone by the walk's end). */
class FocusStop(val key: String, val control: String, val inFront: Boolean, val said: String)

/**
 * The keyboard's walk through the visit (the phone in keyboard mode): from what holds focus (a sheet takes it as it
 * opens) or from no focus, Tab until focus comes back round to its first stop or stops moving (a sheet holds it at its
 * ends), then, when it stopped, Shift+Tab back from the start the same way. Every stop, read by [read] as it lands.
 */
fun <T> Visit.tabWalk(max: Int = 120, read: (SemanticsNode) -> T): List<T> {
    val keys = walker.keys
    keys.on()
    val out = ArrayList<T>()
    val start = keys.focused()?.also { out += read(it) }
    val startKey = start?.let(KeyRules::key)
    fun go(shift: Boolean): Boolean {
        var first: String? = startKey
        var last: String? = startKey
        var moves = 0
        var misses = 0
        for (i in 0 until max) {
            keys.press(Key.Tab, shift) { if (compose.mainClock.autoAdvance) compose.waitForIdle() else StageScope(compose).frames(3) }
            val f = keys.focused()
            if (f == null) {
                if (++misses >= 3) return false
                continue
            }
            val k = KeyRules.key(f)
            // Back round to the first stop: wrapped (once it has moved); still where it was: it holds there.
            if (k == first) return moves > 0
            if (k == last) return false
            if (first == null) first = k
            last = k
            moves++
            out += read(f)
        }
        return false
    }
    val wrapped = go(shift = false)
    if (!wrapped && start != null) {
        // Back to where it started, then on backwards.
        runCatching { compose.onNode(SemanticsMatcher("start") { it.id == start.id }).performSemanticsAction(SemanticsActions.RequestFocus) }
        compose.waitForIdle()
        go(shift = true)
    }
    return out
}

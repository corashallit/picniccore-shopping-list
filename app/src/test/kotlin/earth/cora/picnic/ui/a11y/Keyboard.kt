package earth.cora.picnic.ui.a11y

import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown

/*
 * A hardware keyboard (or a switch-access device, which drives the same focus) on the app under test
 * (docs/DESIGN_STICKER.md §15 "Keyboard / switch access", "Focus"): the phone in keyboard mode, keys sent to whatever
 * holds focus, and what holds it read back. FocusTest and KeyboardNavTest use it.
 */
class Keyboard(private val compose: ComposeContentTestRule) {
    private var manager: InputModeManager? = null

    /** Put inside the content under test: it reads the window's input mode manager. */
    @Composable
    fun Capture() {
        val m = LocalInputModeManager.current
        SideEffect { manager = m }
    }

    /** The phone as a keyboard drives it: focus shows its ring and keys move it. */
    fun on() {
        compose.runOnIdle { manager!!.requestInputMode(InputMode.Keyboard) }
        compose.waitForIdle()
        focusFrontWindow()
    }

    /** The node holding focus, in the window in front when it holds one (a popup has the window focus), or null. */
    fun focused(): SemanticsNode? {
        val all = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
        val front = compose.onAllNodes(isRoot()).fetchSemanticsNodes().last()
        return all.firstOrNull { it.root().id == front.id } ?: all.firstOrNull()
    }

    /** The focused node's test tag, or null. */
    fun focusedTag(): String? = focused()?.config?.getOrNull(SemanticsProperties.TestTag)

    /**
     * Where a key goes: the window in front (a popup's, a pop card's, when one is up: it has the window focus), whose
     * focused node takes it, or its root when nothing there is focused.
     */
    private fun target(): SemanticsNodeInteraction {
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        return compose.onAllNodes(isRoot())[roots.size - 1]
    }

    /** Presses [key] (with Shift held when [shift]) where focus is, then lets the screen settle with [settle]. */
    fun press(key: Key, shift: Boolean = false, settle: () -> Unit = { compose.waitForIdle() }) {
        focusFrontWindow()
        target().performKeyInput { if (shift) withKeyDown(Key.ShiftLeft) { pressKey(key) } else pressKey(key) }
        settle()
    }

    /**
     * A focusable popup window (a pop card) takes the window focus from the screen as it opens, so keys go to it: the
     * phone's window manager does that; Robolectric's never does, so it is handed over here, as the phone would.
     */
    private fun focusFrontWindow() {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>).filter { it.isAttachedToWindow }
        if (views.size < 2) return
        val front = views.last()
        val params = front.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0 || front.hasWindowFocus()) return
        compose.runOnIdle {
            views.dropLast(1).forEach { it.dispatchWindowFocusChanged(false) }
            front.dispatchWindowFocusChanged(true)
            // Robolectric leaves the popup's window in touch mode: its view takes focus as the phone's would.
            composeView(front)?.requestFocusFromTouch()
        }
        compose.waitForIdle()
    }

    private fun composeView(v: View): View? {
        if (v.javaClass.name.endsWith("AndroidComposeView")) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) composeView(v.getChildAt(i))?.let { return it }
        return null
    }

    /** Holds [key] down for [millis] of the test clock before letting go (a long press from the keyboard). */
    fun hold(key: Key, millis: Long) {
        target().performKeyInput { keyDown(key) }
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
        target().performKeyInput { keyUp(key) }
        compose.waitForIdle()
    }
}

/** Whether [n] sits inside a node tagged [tag] (or is it). */
fun SemanticsNode.inside(tag: String): Boolean =
    generateSequence(this) { it.parent }.any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }

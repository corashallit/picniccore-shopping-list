package earth.cora.picnic.ui.a11y

import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.semantics
import earth.cora.picnic.platform.Announcer
import earth.cora.picnic.platform.NoOpAnnouncer

/*
 * Semantics helpers shared by every screen (docs/DESIGN_STICKER.md §15). Owner: WP10. The audits (NoClippedTextTest,
 * DecorSemanticsTest) find nodes by the keys below; neither key is reported to accessibility services.
 */

/**
 * Set to true on a text node whose text does not fit its bounds (PuffyText, LcdText, tabs; §19.2).
 * NoClippedTextTest walks every screen fixture and fails on any such node.
 */
val TextOverflowed: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("TextOverflowed")

var SemanticsPropertyReceiver.textOverflowed: Boolean by TextOverflowed

/**
 * Marks a numeral §2.7 lets go under the 13 sp floor because it repeats a number shown at 13 sp or more elsewhere: the
 * aisle chip's number circle and the book's "+n" stop counters (11 sp). MinTextSizeTest finds the words drawn inside
 * such a node; not reported to accessibility services.
 */
val RepeatedNumeral: SemanticsPropertyKey<Unit> = SemanticsPropertyKey("RepeatedNumeral")

/** [decorative], for a decorative numeral §2.7 sets at 11 sp ([RepeatedNumeral]: the book's "+n" stop counters). */
fun Modifier.decorativeNumeral(): Modifier = clearAndSetSemantics {
    this[Decorative] = Unit
    this[RepeatedNumeral] = Unit
}

/**
 * Marks a decorative node (§15 "Decor": page stickers, doodles, sparkles, washi, baskets' art, book twins,
 * critters, confetti). Set by [decorative]; DecorSemanticsTest checks that such nodes carry nothing TalkBack reads.
 */
val Decorative: SemanticsPropertyKey<Unit> = SemanticsPropertyKey("Decorative")

/**
 * Decor: no semantics for this node or anything inside it, so TalkBack never stops on it and a decorative
 * flourish is never a tappable look-alike. Only the test-visible [Decorative] marker remains. Put it first in
 * the modifier chain: it clears the semantics of the modifiers after it and of every child.
 */
fun Modifier.decorative(): Modifier = clearAndSetSemantics { this[Decorative] = Unit }

/**
 * True while TalkBack (touch exploration) is on, following changes live. With it on, the Next card doubles its
 * hold and the celebration waits for a tap instead of closing itself (§10, §11.3, §0.4 Q7).
 */
@Composable
fun isScreenReaderOn(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled -> on = enabled }
        manager?.addTouchExplorationStateChangeListener(listener)
        // A change between the first read and the listener's registration is not lost.
        on = manager?.isTouchExplorationEnabled == true
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * The app's [Announcer] for the pieces that speak a moment of their own (§15 Announcements: a toast as it drops in, the
 * result of its Undo). MainActivity provides the phone's; previews and goldens get the no-op; tests provide a
 * FakeAnnouncer.
 */
val LocalAnnouncer = staticCompositionLocalOf<Announcer> { NoOpAnnouncer }

/** One modal layer up: where the keyboard goes when it is handed to the layer. */
@Stable
class ModalLayer internal constructor() {
    internal val group = FocusRequester()
    internal val self = FocusRequester()

    /** The layer has nothing to focus inside it (the Trip book's pages): it holds focus itself, so Escape reaches it. */
    internal var holdsItself by mutableStateOf(false)

    /** Gives the keyboard to the layer: its first control, else the layer itself. */
    internal fun takeFocus(): Boolean {
        if (runCatching { group.requestFocus() }.getOrDefault(false)) return true
        holdsItself = true
        return runCatching { self.requestFocus() }.getOrDefault(false)
    }
}

/**
 * The modal layers up over one screen (a letter-set sheet, the overflow menu), newest last: the screen under them
 * ([underModal]) hands the keyboard to the newest. Provided by [ModalLayersHost].
 */
@Stable
class ModalLayers {
    internal val open = mutableStateListOf<ModalLayer>()

    /** The newest layer up, or null. */
    internal val top: ModalLayer? get() = open.lastOrNull()
}

val LocalModalLayers = staticCompositionLocalOf<ModalLayers?> { null }

/** Gives [content] one [ModalLayers] (the one already provided, when there is one). */
@Composable
fun ModalLayersHost(content: @Composable () -> Unit) {
    if (LocalModalLayers.current != null) {
        content()
    } else {
        CompositionLocalProvider(LocalModalLayers provides remember { ModalLayers() }, content = content)
    }
}

/**
 * A layer over the screen that holds the keyboard while it is up (§15 Focus, Keyboard / switch access): a letter-set
 * sheet, the overflow menu. Opened with the keyboard in use, it takes focus (its first control, or the layer itself when
 * it has none); Tab and the arrows stay inside it, never wandering onto the screen behind; Escape and the Back key do what
 * Back does (the innermost Back handler: a full-size receipt in the Trip book closes before the book; [onClose] where there
 * is no Back dispatcher). TalkBack is held by the scrim under it, which covers the screen behind.
 */
@Composable
fun Modifier.modalLayer(onClose: () -> Unit): Modifier {
    val layer = remember { ModalLayer() }
    var selfFocused by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    val backs = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val input = LocalInputModeManager.current
    val layers = LocalModalLayers.current
    DisposableEffect(layers, layer) {
        layers?.open?.add(layer)
        onDispose { layers?.open?.remove(layer) }
    }
    LaunchedEffect(Unit) {
        if (input.inputMode != InputMode.Keyboard) return@LaunchedEffect
        // Its controls are laid out on its first frame.
        if (runCatching { layer.group.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        withFrameMillis { }
        layer.takeFocus()
    }
    return this
        .onPreviewKeyEvent { e ->
            when {
                // Compose would take these keys as a move of focus out of the layer (and keep them from the activity's
                // Back): they go to the Back handlers instead.
                e.key == Key.Escape || e.key == Key.Back -> {
                    if (e.type == KeyEventType.KeyUp) {
                        if (backs?.hasEnabledCallbacks() == true) backs.onBackPressed() else close()
                    }
                    true
                }
                // Holding focus itself (nothing inside to move to), the layer keeps it: Tab never leaves for the screen
                // behind.
                selfFocused && e.movesFocus() -> true
                else -> false
            }
        }
        .focusRequester(layer.self)
        .onFocusChanged { selfFocused = it.isFocused }
        // Holding focus itself, the layer says so (the keyboard's focus is on it; switch access and tests see where).
        .then(if (layer.holdsItself) Modifier.semantics { focused = selfFocused } else Modifier)
        .focusProperties {
            canFocus = layer.holdsItself
            onExit = { cancelFocusChange() }
        }
        .focusTarget()
        .focusRequester(layer.group)
        .focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()
}

/**
 * Tab and Shift+Tab move the keyboard's focus on from a text field (§15 Keyboard / switch access): a field of more than
 * one line would otherwise type Tab as a character and keep the keyboard in it for good.
 */
@Composable
fun Modifier.tabLeavesField(): Modifier {
    val focus = LocalFocusManager.current
    return onPreviewKeyEvent { e ->
        if (e.key != Key.Tab) return@onPreviewKeyEvent false
        if (e.type == KeyEventType.KeyDown) focus.moveFocus(if (e.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
        true
    }
}

/** The keys that move focus (Tab, the arrows and the D-pad). */
private fun KeyEvent.movesFocus(): Boolean =
    key == Key.Tab || key == Key.DirectionUp || key == Key.DirectionDown || key == Key.DirectionLeft || key == Key.DirectionRight

/**
 * The screen under a modal layer ([modalLayer]): while [covered], the keyboard can't reach it (focus moving in, or
 * already in it from a touch, goes to the newest layer instead), and the control that had focus when the layer opened
 * (the book, the More button, a row) gets it back when the layer closes (§15: sheets trap focus and return it to the
 * opener). [restore] gives it back when the group cannot (an item of a lazy list, recomposed meanwhile).
 */
@Composable
fun Modifier.underModal(covered: Boolean, restore: () -> Boolean = { false }): Modifier {
    val requester = remember { FocusRequester() }
    val was = remember { booleanArrayOf(false) }
    val input = LocalInputModeManager.current
    val layers = LocalModalLayers.current
    val isCovered by rememberUpdatedState(covered)
    LaunchedEffect(covered) {
        // Only for the keyboard: under touch, closing a sheet never moves focus (it would raise the IME on the add bar).
        val keys = input.inputMode == InputMode.Keyboard
        if (covered && !was[0] && keys) runCatching { requester.saveFocusedChild() }
        if (!covered && was[0] && keys && !runCatching { requester.restoreFocusedChild() }.getOrDefault(false)) restore()
        was[0] = covered
    }
    return this
        .onPreviewKeyEvent { e ->
            // Focus left here by a touch (the add bar's field) while a layer is up: the keyboard's first move goes to the
            // layer, not along the screen behind it.
            if (!isCovered || !e.movesFocus()) return@onPreviewKeyEvent false
            val top = layers?.top ?: return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) top.takeFocus()
            true
        }
        .focusRequester(requester)
        .focusProperties {
            onEnter = {
                // Into the layer instead (Tab with nothing focused would start at the screen's first control): its
                // request redirects the move; with no layer to take it, the move is cancelled.
                if (isCovered && layers?.top?.takeFocus() != true) cancelFocusChange()
            }
        }
        .focusGroup()
}

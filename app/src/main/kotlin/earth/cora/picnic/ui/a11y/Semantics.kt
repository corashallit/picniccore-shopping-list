package earth.cora.picnic.ui.a11y

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
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

/**
 * A layer over the screen that holds the keyboard while it is up (§15 Focus, Keyboard / switch access): a letter-set
 * sheet, the overflow menu. Opened with the keyboard in use, it takes focus (its first control); Tab and the arrows stay
 * inside it, never wandering onto the screen behind; Escape closes it as Back does ([onClose]). TalkBack is held by the
 * scrim under it, which covers the screen behind.
 */
@Composable
fun Modifier.modalLayer(onClose: () -> Unit): Modifier {
    val requester = remember { FocusRequester() }
    val close by rememberUpdatedState(onClose)
    val input = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        if (input.inputMode == InputMode.Keyboard) runCatching { requester.requestFocus() }
    }
    return this
        .onPreviewKeyEvent { e ->
            if (e.key != Key.Escape) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyUp) close()
            true
        }
        .focusRequester(requester)
        .focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()
}

/**
 * The screen under a modal layer ([modalLayer]): while [covered], the keyboard can't move focus into it, and the control
 * that had focus when the layer opened (the book, the More button, a row) gets it back when the layer closes (§15:
 * sheets trap focus and return it to the opener).
 */
@Composable
fun Modifier.underModal(covered: Boolean): Modifier {
    val requester = remember { FocusRequester() }
    val was = remember { booleanArrayOf(false) }
    val input = LocalInputModeManager.current
    LaunchedEffect(covered) {
        // Only for the keyboard: under touch, closing a sheet never moves focus (it would raise the IME on the add bar).
        val keys = input.inputMode == InputMode.Keyboard
        if (covered && !was[0] && keys) runCatching { requester.saveFocusedChild() }
        if (!covered && was[0] && keys) runCatching { requester.restoreFocusedChild() }
        was[0] = covered
    }
    return this
        .focusRequester(requester)
        .focusProperties { onEnter = { if (covered) cancelFocusChange() } }
        .focusGroup()
}

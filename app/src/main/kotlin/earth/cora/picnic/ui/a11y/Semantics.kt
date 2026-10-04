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

package earth.cora.picnic.ui.a11y

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * Set to true on a text node whose text does not fit its bounds (PuffyText, LcdText, tabs; §19.2).
 * NoClippedTextTest walks every screen fixture and fails on any such node. Owner: WP10; phase-0 contract.
 */
val TextOverflowed: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("TextOverflowed")

var SemanticsPropertyReceiver.textOverflowed: Boolean by TextOverflowed
